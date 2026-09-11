package com.foresightlabs.aether.ui.security

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.foresightlabs.aether.data.security.AppLockCoordinator
import com.foresightlabs.aether.data.security.BiometricAuthenticator
import com.foresightlabs.aether.domain.security.AppLockUiState
import com.foresightlabs.aether.domain.security.LockReason
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * A one-off "prove you know the passcode" check for a sensitive Settings
 * action (disabling App Lock, changing the passcode -- see section 40 of the
 * App Lock spec: someone holding an already-unlocked phone for a moment
 * should not be able to silently remove the lock).
 *
 * Deliberately reuses [AppLockViewModel.verifyPasscode] rather than a second,
 * independent check: it is the same verifier and the same brute-force
 * backoff, not a second unguarded place to guess the passcode. Aether is
 * already [AppLockUiState.Unlocked] the whole time this is showing, so a
 * successful verify here does not change anything app-wide -- it only calls
 * [onVerified].
 */
@Composable
fun AppLockReauthScreen(
    reason: String,
    onVerified: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: AppLockViewModel = viewModel()
    val lockState by viewModel.lockState.collectAsStateWithLifecycle()
    val pinLength by viewModel.pinLength.collectAsStateWithLifecycle()
    val biometricEnabled by viewModel.biometricEnabled.collectAsStateWithLifecycle()
    val activity = LocalActivity.current as? FragmentActivity
    val biometricAvailable = biometricEnabled && activity != null &&
        remember(activity) { activity?.let(BiometricAuthenticator::canAuthenticate) == true }
    val scope = rememberCoroutineScope()

    var pin by remember { mutableStateOf("") }
    var secondsRemaining by remember { mutableStateOf(0L) }
    var verified by remember { mutableStateOf(false) }

    BackHandler(onBack = onCancel)

    LaunchedEffect(lockState) {
        val blocked = lockState as? AppLockUiState.TemporarilyBlocked ?: return@LaunchedEffect
        while (true) {
            val remaining = (blocked.retryAtElapsedRealtimeMillis - android.os.SystemClock.elapsedRealtime())
            secondsRemaining = (remaining / 1000L).coerceAtLeast(0L) + 1L
            if (remaining <= 0L) break
            delay(500)
        }
    }

    // AppLockViewModel's lockState reflects the *app-wide* lock, which is
    // already Unlocked here -- this screen tracks its own local "verified"
    // flag instead of relying on that state to know when to proceed.
    LaunchedEffect(verified) {
        if (verified) onVerified()
    }

    val displayState = when {
        lockState is AppLockUiState.TemporarilyBlocked -> lockState
        else -> AppLockUiState.Locked(LockReason.MANUAL_LOCK)
    }

    AppLockScreen(
        state = displayState,
        pinLength = pinLength,
        pinEntered = pin.length,
        biometricAvailable = biometricAvailable,
        secondsRemaining = secondsRemaining,
        title = reason,
        modifier = modifier,
        onDigit = { digit ->
            if (lockState is AppLockUiState.TemporarilyBlocked) return@AppLockScreen
            if (pin.length >= pinLength) return@AppLockScreen
            pin += digit
            if (pin.length == pinLength) {
                val attempt = pin.toCharArray()
                pin = ""
                viewModel.verifyPasscode(attempt) { result ->
                    if (result is AppLockCoordinator.PasscodeResult.Correct) {
                        verified = true
                    }
                }
            }
        },
        onDelete = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
        onBiometricTap = {
            scope.launch {
                if (activity == null) return@launch
                viewModel.beginTrustedSystemUi()
                try {
                    BiometricAuthenticator.authenticate(activity).collect { result ->
                        if (result is BiometricAuthenticator.Result.Success) {
                            viewModel.onBiometricSuccess()
                            verified = true
                        }
                    }
                } finally {
                    viewModel.endTrustedSystemUi()
                }
            }
        }
    )
}
