package com.foresightlabs.aether.data.security

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import com.foresightlabs.aether.domain.security.AppLockUiState
import com.foresightlabs.aether.domain.security.LockReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * The one process-wide authority over whether the Aether UI is locked.
 *
 * Nothing else holds an independent "is locked" boolean: [MainActivity] gates
 * the whole navigation graph on [lockState], Settings asks this to disable
 * App Lock, and lifecycle tracking below is this class's own, kept separate
 * from [com.foresightlabs.aether.data.notifications.ActiveConversationTracker]
 * (a different concern -- notification suppression -- that happens to also
 * watch foreground/background).
 *
 * State machine: a fresh instance is [AppLockUiState.Loading] until
 * [AppLockRepository.settings] has emitted once, then either
 * [AppLockUiState.Unlocked] (App Lock off) or [AppLockUiState.Locked]
 * (App Lock on -- a new process always fails closed) and stays there until an
 * explicit [unlockWithBiometric] or successful [verifyPasscode]. From
 * [AppLockUiState.Unlocked], returning to the foreground after the
 * configured [com.foresightlabs.aether.domain.security.AutoLockDuration] has
 * elapsed moves back to [AppLockUiState.Locked].
 */
class AppLockCoordinator(
    private val repository: AppLockRepository,
    private val scope: CoroutineScope,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime
) {

    private data class RuntimeState(
        val unlockedThisProcess: Boolean = false,
        val verifying: Boolean = false,
        val failedAttempts: Int = 0,
        val blockedUntilElapsedMillis: Long? = null,
        val lockReason: LockReason = LockReason.FRESH_PROCESS
    )

    private val runtime = MutableStateFlow(RuntimeState())
    private var backgroundedAtElapsedMillis: Long? = null

    /** >0 while a trusted system prompt we launched ourselves (biometric, the one wrapped permission request) is in flight -- see [beginTrustedSystemUi]. */
    private var trustedSystemUiDepth = 0

    val lockState: StateFlow<AppLockUiState> = combine(repository.settings, runtime) { settings, rt ->
        when {
            settings == null -> AppLockUiState.Loading
            !settings.enabled -> AppLockUiState.Unlocked
            rt.blockedUntilElapsedMillis != null && rt.blockedUntilElapsedMillis > elapsedRealtime() ->
                AppLockUiState.TemporarilyBlocked(rt.blockedUntilElapsedMillis)
            rt.unlockedThisProcess -> AppLockUiState.Unlocked
            rt.verifying -> AppLockUiState.Verifying
            else -> AppLockUiState.Locked(rt.lockReason)
        }
    }.stateIn(scope, SharingStarted.Eagerly, AppLockUiState.Loading)

    /** Registers process-wide lifecycle tracking. Call exactly once, from [Application.onCreate]. */
    fun attachTo(application: Application) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var startedCount = 0

            override fun onActivityStarted(activity: Activity) {
                startedCount++
                if (startedCount == 1) onAppForegrounded()
            }

            override fun onActivityStopped(activity: Activity) {
                startedCount = (startedCount - 1).coerceAtLeast(0)
                if (startedCount == 0 && !activity.isChangingConfigurations) {
                    onAppBackgrounded()
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    /**
     * Marks a trusted, self-initiated system UI (BiometricPrompt, one
     * permission request) as in flight so the brief backgrounding it can
     * cause on some OS versions never reads as the user leaving Aether --
     * see the false-lock cases in the App Lock spec (rotation, permission
     * prompts, the biometric prompt itself). Always pair with
     * [endTrustedSystemUi], ideally in a `try`/`finally`.
     */
    fun beginTrustedSystemUi() {
        trustedSystemUiDepth++
    }

    fun endTrustedSystemUi() {
        trustedSystemUiDepth = (trustedSystemUiDepth - 1).coerceAtLeast(0)
    }

    /** internal, not private: exercised directly from [AppLockCoordinatorTest] without needing a real Activity -- the Activity lifecycle wiring itself is exercised separately in [attachTo]. */
    internal fun onAppBackgrounded() {
        if (trustedSystemUiDepth > 0) return
        if (!runtime.value.unlockedThisProcess) return // nothing to time out from
        backgroundedAtElapsedMillis = elapsedRealtime()
    }

    internal fun onAppForegrounded() {
        if (trustedSystemUiDepth > 0) return
        val settings = repository.settings.value ?: return
        if (!settings.enabled) return
        if (!runtime.value.unlockedThisProcess) return // already locked/verifying/blocked
        val backgroundedAt = backgroundedAtElapsedMillis ?: return
        val elapsed = elapsedRealtime() - backgroundedAt
        if (elapsed >= settings.autoLockDuration.millis) {
            runtime.update {
                it.copy(unlockedThisProcess = false, verifying = false, lockReason = LockReason.AUTO_LOCK_TIMEOUT)
            }
        }
    }

    /** "Lock Aether now" -- immediate, regardless of auto-lock policy. */
    fun lockNow() {
        backgroundedAtElapsedMillis = null
        runtime.update {
            it.copy(unlockedThisProcess = false, verifying = false, lockReason = LockReason.MANUAL_LOCK)
        }
    }

    sealed interface PasscodeResult {
        data object Correct : PasscodeResult
        data class Incorrect(val delaySeconds: Long) : PasscodeResult
        data object NoPasscodeConfigured : PasscodeResult
    }

    /**
     * Verifies [pin] against the stored verifier off the main thread (PBKDF2
     * at this work factor costs real CPU time). Resets the failure streak on
     * success; on failure, applies [FailedAttemptPolicy]'s growing delay --
     * never a permanent lockout.
     */
    suspend fun verifyPasscode(pin: CharArray): PasscodeResult {
        val verifier = repository.settings.value?.verifier ?: return PasscodeResult.NoPasscodeConfigured
        runtime.update { it.copy(verifying = true) }
        val correct = withContext(Dispatchers.Default) {
            PasscodeCrypto.verify(pin, verifier)
        }
        return if (correct) {
            onUnlocked()
            PasscodeResult.Correct
        } else {
            val attempts = runtime.value.failedAttempts + 1
            val delaySeconds = FailedAttemptPolicy.delaySecondsFor(attempts)
            val blockedUntil = if (delaySeconds > 0) elapsedRealtime() + delaySeconds * 1000L else null
            runtime.update {
                it.copy(verifying = false, failedAttempts = attempts, blockedUntilElapsedMillis = blockedUntil)
            }
            PasscodeResult.Incorrect(delaySeconds)
        }
    }

    /** Called after AndroidX BiometricPrompt reports success. */
    fun unlockWithBiometric() {
        onUnlocked()
    }

    /**
     * Called once a new passcode has just been created and confirmed (typed
     * correctly twice in a row, seconds ago). Without this, enabling App Lock
     * would immediately re-lock the app the instant `enabled` flips true --
     * demanding the same passcode a third time before letting the user back
     * into the app they were already using.
     */
    fun markUnlockedAfterPasscodeSetup() {
        onUnlocked()
    }

    private fun onUnlocked() {
        backgroundedAtElapsedMillis = null
        runtime.update {
            it.copy(unlockedThisProcess = true, verifying = false, failedAttempts = 0, blockedUntilElapsedMillis = null)
        }
    }
}
