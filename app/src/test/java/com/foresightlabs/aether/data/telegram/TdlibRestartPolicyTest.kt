package com.foresightlabs.aether.data.telegram

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AuthorizationStateClosed always means "this client is dead, create a new
 * one" -- required after an ordinary LogOut() so the next phone submission
 * has somewhere to go. The one thing that must not happen is restarting
 * forever if the fresh client closes again before ever reaching Ready.
 */
class TdlibRestartPolicyTest {

    @Test
    fun firstCloseRestarts() {
        val policy = TdlibRestartPolicy()
        assertTrue(policy.onClosed())
    }

    @Test
    fun secondCloseWithoutInterveningReadyDoesNotRestart() {
        val policy = TdlibRestartPolicy()
        assertTrue(policy.onClosed())
        assertFalse(policy.onClosed())
        assertFalse(policy.onClosed())
    }

    @Test
    fun readyClearsTheGuardForItsOwnFutureClose() {
        val policy = TdlibRestartPolicy()
        assertTrue(policy.onClosed())
        policy.onReady()
        assertTrue(policy.onClosed())
    }

    @Test
    fun repeatedReadyCloseCyclesEachRestartExactlyOnce() {
        val policy = TdlibRestartPolicy()
        repeat(5) {
            assertTrue(policy.onClosed())
            policy.onReady()
        }
    }
}
