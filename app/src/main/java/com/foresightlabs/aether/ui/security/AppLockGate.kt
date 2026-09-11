package com.foresightlabs.aether.ui.security

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.activity.compose.LocalActivity
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.foresightlabs.aether.data.security.BiometricAuthenticator
import com.foresightlabs.aether.domain.security.AppLockUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import android.os.SystemClock

/**
 * The root security gate: while [AppLockViewModel.lockState] is anything but
 * [AppLockUiState.Unlocked], this renders [AppLockScreen] instead of
 * [content] -- [content] is never composed at all while locked, so there is
 * no protected frame to accidentally leak. See
 * [com.foresightlabs.aether.navigation.AetherApp] for where this wraps the
 * whole navigation graph.
 */
@Composable
fun AppLockGate(content: @Composable () -> Unit) {
    val viewModel: AppLockViewModel = viewModel()
    val state by viewModel.lockState.collectAsStateWithLifecycle()

    RecentsPrivacyEffect()

    when (val current = state) {
        AppLockUiState.Loading -> {
            // Settings haven't loaded yet -- render nothing protected, not
            // even a spinner over content, until we know whether to lock.
        }
        AppLockUiState.Unlocked -> content()
        else -> LockedContent(viewModel = viewModel, state = current)
    }
}

@Composable
private fun LockedContent(viewModel: AppLockViewModel, state: AppLockUiState) {
    val activity = LocalActivity.current as? FragmentActivity
    val scope = rememberCoroutineScope()
    val pinLength by viewModel.pinLength.collectAsStateWithLifecycle()
    val biometricEnabled by viewModel.biometricEnabled.collectAsStateWithLifecycle()
    val biometricAvailable = biometricEnabled && activity != null &&
        remember(activity) { activity?.let(BiometricAuthenticator::canAuthenticate) == true }

    var pin by remember { mutableStateOf("") }
    var secondsRemaining by remember { mutableLongStateOf(0L) }
    var autoPromptedForThisLock by remember { mutableStateOf(false) }

    // A fresh Locked arrival (not Verifying/Blocked, which are transient
    // sub-states of the same lock episode) resets the one-shot biometric
    // flag, so a NEW lock (auto-lock timeout, manual lock, fresh process)
    // gets exactly one automatic prompt again.
    LaunchedEffect(state is AppLockUiState.Locked) {
        if (state is AppLockUiState.Locked) autoPromptedForThisLock = false
    }

    LaunchedEffect(state, biometricAvailable, autoPromptedForThisLock) {
        if (state is AppLockUiState.Locked && biometricAvailable && !autoPromptedForThisLock) {
            autoPromptedForThisLock = true
            requestBiometric(activity, viewModel)
        }
    }

    LaunchedEffect(state) {
        val blocked = state as? AppLockUiState.TemporarilyBlocked ?: return@LaunchedEffect
        while (true) {
            val remainingMillis = blocked.retryAtElapsedRealtimeMillis - SystemClock.elapsedRealtime()
            secondsRemaining = (remainingMillis / 1000L).coerceAtLeast(0L) + 1L
            if (remainingMillis <= 0L) break
            delay(500)
        }
    }

    AppLockScreen(
        state = state,
        pinLength = pinLength,
        pinEntered = pin.length,
        biometricAvailable = biometricAvailable,
        secondsRemaining = secondsRemaining,
        onDigit = { digit ->
            if (state is AppLockUiState.TemporarilyBlocked || state is AppLockUiState.Verifying) return@AppLockScreen
            if (pin.length >= pinLength) return@AppLockScreen
            pin += digit
            if (pin.length == pinLength) {
                val attempt = pin.toCharArray()
                pin = ""
                viewModel.verifyPasscode(attempt) { /* lockState transition handles the UI */ }
            }
        },
        onDelete = {
            if (pin.isNotEmpty()) pin = pin.dropLast(1)
        },
        onBiometricTap = {
            scope.launch { requestBiometric(activity, viewModel) }
        }
    )
}

/**
 * Keeps Aether out of the Android Recents/task-switcher thumbnail for the
 * entire time App Lock is enabled -- not just while currently locked.
 *
 * The task snapshot is captured the instant the app leaves the foreground,
 * which is *before* [AppLockCoordinator] ever transitions to Locked (that
 * happens later, on return -- see its lifecycle policy). Gating this on the
 * momentary lock state would leave exactly the snapshot that matters
 * unprotected, so it tracks "App Lock is on" instead.
 *
 * [android.app.Activity.setRecentsScreenshotEnabled] (API 31+) hides only the
 * task-switcher preview -- ordinary user screenshots (volume+power) are
 * untouched, so unlocked, everyday use of Aether is not affected. Below API
 * 33 that call does not exist; [android.view.WindowManager.LayoutParams.FLAG_SECURE]
 * is applied only while the lock screen itself is showing, a narrower and
 * imperfect but non-regressive fallback -- see the App Lock report's
 * "remaining risks" for what this does not cover on API 24-32.
 */
@Composable
private fun RecentsPrivacyEffect() {
    val activity = LocalActivity.current as? FragmentActivity ?: return
    val app = activity.applicationContext as com.foresightlabs.aether.AetherApplication
    val settings by app.appLockRepository.settings.collectAsStateWithLifecycle(initialValue = null)
    val lockState by app.appLockCoordinator.lockState.collectAsStateWithLifecycle()
    val enabled = settings?.enabled == true

    DisposableEffect(enabled, lockState) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            activity.setRecentsScreenshotEnabled(!enabled)
        } else {
            val showingLockScreen = enabled && lockState != AppLockUiState.Unlocked
            if (showingLockScreen) {
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
        onDispose {}
    }
}

private suspend fun requestBiometric(activity: FragmentActivity?, viewModel: AppLockViewModel) {
    if (activity == null) return
    viewModel.beginTrustedSystemUi()
    try {
        BiometricAuthenticator.authenticate(activity).collect { result ->
            when (result) {
                BiometricAuthenticator.Result.Success -> viewModel.onBiometricSuccess()
                else -> Unit // cancelled/error/failed: stay locked, PIN entry remains available
            }
        }
    } finally {
        viewModel.endTrustedSystemUi()
    }
}
