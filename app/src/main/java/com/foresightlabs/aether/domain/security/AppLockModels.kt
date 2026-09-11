package com.foresightlabs.aether.domain.security

/**
 * How long Aether may sit in the background, unlocked, before the next return
 * to foreground must re-lock it. [millis] of 0 means any genuine backgrounding
 * re-locks -- see [com.foresightlabs.aether.data.security.AppLockCoordinator].
 */
enum class AutoLockDuration(val millis: Long, val displayName: String) {
    IMMEDIATELY(0L, "Immediately"),
    ONE_MINUTE(60_000L, "After 1 minute"),
    FIVE_MINUTES(5 * 60_000L, "After 5 minutes"),
    THIRTY_MINUTES(30 * 60_000L, "After 30 minutes"),
    ONE_HOUR(60 * 60_000L, "After 1 hour");

    companion object {
        fun fromId(id: String?): AutoLockDuration =
            entries.find { it.name == id } ?: IMMEDIATELY
    }
}

/**
 * The one-way PBKDF2 verifier for the Aether passcode. Never holds the raw PIN.
 *
 * [version] lets the derivation algorithm or work factor change later without
 * breaking verifiers already on disk -- an unrecognised version simply fails
 * verification rather than crashing, forcing a passcode reset instead of a
 * silent security downgrade.
 */
data class AppLockVerifier(
    val version: Int,
    val saltBase64: String,
    val hashBase64: String,
    val iterations: Int,
    /** Digit count chosen at setup (4-8) -- lets the lock screen show the right number of dots and auto-submit without guessing. */
    val pinLength: Int
)

/** Persisted App Lock configuration -- see [com.foresightlabs.aether.data.security.AppLockRepository]. */
data class AppLockSettings(
    val enabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val autoLockDuration: AutoLockDuration = AutoLockDuration.IMMEDIATELY,
    val verifier: AppLockVerifier? = null
)

/** Why the lock is currently showing, kept only for the "Try again" copy. */
enum class LockReason {
    FRESH_PROCESS,
    AUTO_LOCK_TIMEOUT,
    MANUAL_LOCK
}

/**
 * The runtime state machine driving the lock screen. See
 * [com.foresightlabs.aether.data.security.AppLockCoordinator] for the single
 * authority that transitions between these.
 */
sealed interface AppLockUiState {
    /** Settings have not finished loading from disk yet -- render nothing protected. */
    data object Loading : AppLockUiState

    /** No gate: either App Lock was never configured, or it is currently unlocked. */
    data object Unlocked : AppLockUiState

    data class Locked(val reason: LockReason) : AppLockUiState

    /** A PIN or biometric attempt is being checked. */
    data object Verifying : AppLockUiState

    /** Too many wrong PIN attempts; retry is disabled until [retryAtElapsedRealtimeMillis]. */
    data class TemporarilyBlocked(val retryAtElapsedRealtimeMillis: Long) : AppLockUiState
}
