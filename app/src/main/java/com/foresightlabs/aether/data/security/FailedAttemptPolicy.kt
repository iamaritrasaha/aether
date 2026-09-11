package com.foresightlabs.aether.data.security

/**
 * Brute-force backoff for the Aether passcode.
 *
 * The first few wrong attempts retry immediately -- a fumbled digit shouldn't
 * feel punitive. Only sustained, repeated failure introduces a growing delay,
 * capped well short of anything that could look like a permanent lockout: this
 * guards the account, not against the account's own owner mistyping.
 */
object FailedAttemptPolicy {

    private const val ATTEMPTS_BEFORE_DELAY = 3
    private val DELAY_STEPS_SECONDS = listOf(5L, 15L, 30L, 60L)

    /** Seconds to block retrying after [attemptCount] consecutive wrong attempts. */
    fun delaySecondsFor(attemptCount: Int): Long {
        if (attemptCount <= ATTEMPTS_BEFORE_DELAY) return 0L
        val stepIndex = (attemptCount - ATTEMPTS_BEFORE_DELAY - 1).coerceIn(0, DELAY_STEPS_SECONDS.lastIndex)
        return DELAY_STEPS_SECONDS[stepIndex]
    }
}
