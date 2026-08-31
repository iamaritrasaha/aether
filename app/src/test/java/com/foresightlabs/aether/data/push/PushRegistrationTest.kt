package com.foresightlabs.aether.data.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registration is where background delivery is won or lost: an unregistered
 * token means Telegram never sends a push at all.
 *
 * TDLib error codes stand in for the real answers -- 400 is what Telegram
 * returns when the request is refused outright (an invalid token, or the
 * server-side application having no push credentials configured, which the
 * client cannot fix by asking again), while transport-level and server-side
 * failures leave the request genuinely unanswered.
 */
class PushRegistrationTest {

    @Test
    fun a_fresh_token_is_registered_and_a_repeat_of_it_is_not() {
        val registration = PushRegistration()

        assertTrue(registration.onTokenAvailable("token-a"))
        val attempt = registration.beginAttempt()
        assertEquals("token-a", attempt)
        registration.onRegistered("token-a")

        assertEquals("token-a", registration.currentlyRegisteredToken)
        assertNull("A registered token must not be pending", registration.tokenAwaitingRegistration)
        assertFalse(
            "The same token arriving again must not cause a second RegisterDevice",
            registration.onTokenAvailable("token-a")
        )
        assertNull(registration.beginAttempt())
    }

    @Test
    fun a_blank_token_is_never_registered() {
        val registration = PushRegistration()
        assertFalse(registration.onTokenAvailable(""))
        assertFalse(registration.onTokenAvailable("   "))
        assertNull(registration.beginAttempt())
    }

    @Test
    fun a_refused_request_is_not_repeated_in_this_process() {
        val registration = PushRegistration()
        registration.onTokenAvailable("token-a")
        assertEquals("token-a", registration.beginAttempt())

        // 400: the server answered and refused. Repeating the identical
        // request cannot change the answer -- e.g. the application's push
        // credentials are missing server-side.
        assertEquals(PushRegistration.Failure.REJECTED, registration.onAttemptFailed(400))

        assertNull(registration.tokenToRetryOnReconnect())
        assertNull(registration.beginAttempt())
    }

    @Test
    fun an_unanswered_request_is_retried_when_the_connection_returns() {
        val registration = PushRegistration()
        registration.onTokenAvailable("token-a")
        registration.beginAttempt()

        assertEquals(PushRegistration.Failure.RETRYABLE, registration.onAttemptFailed(500))

        assertEquals("token-a", registration.tokenToRetryOnReconnect())
        assertEquals("token-a", registration.beginAttempt())
        registration.onRegistered("token-a")
        assertEquals("token-a", registration.currentlyRegisteredToken)
    }

    @Test
    fun retries_are_bounded_rather_than_endless() {
        val registration = PushRegistration()
        registration.onTokenAvailable("token-a")

        repeat(PushRegistration.MAX_RETRYABLE_ATTEMPTS) {
            assertEquals("token-a", registration.beginAttempt())
            assertEquals(PushRegistration.Failure.RETRYABLE, registration.onAttemptFailed(-1))
        }

        assertNull(
            "Attempts must stop once the budget is spent",
            registration.tokenToRetryOnReconnect()
        )
        assertNull(registration.beginAttempt())
    }

    @Test
    fun a_new_token_is_a_new_request_and_gets_its_own_attempts() {
        val registration = PushRegistration()
        registration.onTokenAvailable("token-a")
        registration.beginAttempt()
        registration.onAttemptFailed(400)
        assertNull(registration.beginAttempt())

        assertTrue(registration.onTokenAvailable("token-b"))
        assertEquals("token-b", registration.beginAttempt())
    }

    @Test
    fun logging_out_clears_the_registration_so_a_new_session_registers_again() {
        val registration = PushRegistration()
        registration.onTokenAvailable("token-a")
        registration.beginAttempt()
        registration.onRegistered("token-a")

        registration.onSessionCleared()

        assertNull(registration.currentlyRegisteredToken)
        assertTrue(
            "After logout the same token must be registered against the new session",
            registration.onTokenAvailable("token-a")
        )
        assertEquals("token-a", registration.beginAttempt())
    }
}
