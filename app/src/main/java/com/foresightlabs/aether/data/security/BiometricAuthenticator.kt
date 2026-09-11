package com.foresightlabs.aether.data.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

/**
 * Thin wrapper around the real AndroidX BiometricPrompt / BiometricManager
 * stack -- there is no bespoke fingerprint UI here, only Aether's own button
 * that invokes the system prompt.
 *
 * Scoped to [BIOMETRIC_STRONG] (Class 3): a device that only offers weaker
 * biometrics should not be able to gate Aether's App Lock with them, since
 * the Aether passcode is the trusted fallback either way.
 */
object BiometricAuthenticator {

    sealed interface Result {
        data object Success : Result
        data class Error(val message: String, val isLockout: Boolean) : Result
        data object Cancelled : Result
        data object Failed : Result
    }

    fun canAuthenticate(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        return manager.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Shows the system biometric prompt once and suspends for its outcome,
     * emitting exactly one [Result] before completing. Cancellation of the
     * collecting coroutine (e.g. the composable leaving composition) cancels
     * the prompt via [BiometricPrompt.cancelAuthentication].
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "Unlock Aether",
        subtitle: String? = null
    ) = callbackFlow {
        val executor = androidx.core.content.ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                trySend(Result.Success)
                close()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                val isLockout = errorCode == BiometricPrompt.ERROR_LOCKOUT ||
                    errorCode == BiometricPrompt.ERROR_LOCKOUT_PERMANENT
                trySend(if (cancelled) Result.Cancelled else Result.Error(errString.toString(), isLockout))
                close()
            }

            override fun onAuthenticationFailed() {
                // A single rejected biometric read (e.g. wrong finger) -- the
                // prompt stays open for another attempt, so this does not
                // close the flow; only a terminal success/error/cancel does.
                trySend(Result.Failed)
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { subtitle?.let(::setSubtitle) }
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setNegativeButtonText("Use passcode")
            .build()
        prompt.authenticate(info)

        awaitClose { prompt.cancelAuthentication() }
    }
}
