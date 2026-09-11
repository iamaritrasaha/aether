package com.foresightlabs.aether.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FailedAttemptPolicyTest {

    @Test
    fun firstThreeAttemptsHaveNoDelay() {
        assertEquals(0L, FailedAttemptPolicy.delaySecondsFor(1))
        assertEquals(0L, FailedAttemptPolicy.delaySecondsFor(2))
        assertEquals(0L, FailedAttemptPolicy.delaySecondsFor(3))
    }

    @Test
    fun delayGrowsAfterRepeatedFailures() {
        val fourth = FailedAttemptPolicy.delaySecondsFor(4)
        val fifth = FailedAttemptPolicy.delaySecondsFor(5)
        val sixth = FailedAttemptPolicy.delaySecondsFor(6)
        assertTrue(fourth > 0)
        assertTrue(fifth >= fourth)
        assertTrue(sixth >= fifth)
    }

    @Test
    fun delayNeverExceedsCapEvenAfterManyFailures() {
        // A bug that lets this grow unbounded would look, to the account's own
        // owner, indistinguishable from a permanent lockout -- must never happen.
        val cap = FailedAttemptPolicy.delaySecondsFor(20)
        for (attempt in 20..200) {
            assertTrue(FailedAttemptPolicy.delaySecondsFor(attempt) <= cap)
        }
    }

    @Test
    fun delayIsMonotonicNonDecreasing() {
        var previous = 0L
        for (attempt in 1..30) {
            val current = FailedAttemptPolicy.delaySecondsFor(attempt)
            assertTrue("attempt $attempt: $current should be >= $previous", current >= previous)
            previous = current
        }
    }
}
