package com.foresightlabs.aether.ui.conversation

import com.foresightlabs.aether.domain.calls.CallPresentationState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The truthful label shown for each call state -- covers exactly the states
 * ConversationCallIdentityBanner can be asked to render, so a state this
 * function does not handle fails to compile rather than falling through to a
 * misleading default.
 */
class ConversationCallUiTest {

    @Test
    fun idleHasNoLabel() {
        assertEquals("", callStatusLabel(CallPresentationState.IDLE, durationSec = 0))
    }

    @Test
    fun outgoingRequestReadsCallingNotConnected() {
        assertEquals("Calling", callStatusLabel(CallPresentationState.OUTGOING_REQUEST, durationSec = 0))
    }

    /** RINGING is only ever reached by an *incoming* call, so it says so. */
    @Test
    fun ringingReadsIncomingCall() {
        assertEquals("Incoming call", callStatusLabel(CallPresentationState.RINGING, durationSec = 0))
    }

    @Test
    fun connectingReadsConnectingNotActive() {
        assertEquals("Connecting", callStatusLabel(CallPresentationState.CONNECTING, durationSec = 0))
    }

    @Test
    fun reconnectingReadsReconnecting() {
        assertEquals("Reconnecting", callStatusLabel(CallPresentationState.RECONNECTING, durationSec = 42))
    }

    @Test
    fun endedAndFailedReadDistinctTruthfulLabels() {
        assertEquals("Call ended", callStatusLabel(CallPresentationState.ENDED, durationSec = 30))
        assertEquals("Call failed", callStatusLabel(CallPresentationState.FAILED, durationSec = 0))
    }

    @Test
    fun activeShowsFormattedDurationNotAPlaceholder() {
        assertEquals("0:00", callStatusLabel(CallPresentationState.ACTIVE, durationSec = 0))
        assertEquals("1:05", callStatusLabel(CallPresentationState.ACTIVE, durationSec = 65))
        assertEquals("12:03", callStatusLabel(CallPresentationState.ACTIVE, durationSec = 723))
    }

    @Test
    fun onlyActiveEverProducesADurationLookingLabel() {
        for (state in CallPresentationState.entries) {
            val label = callStatusLabel(state, durationSec = 90)
            val looksLikeDuration = label.matches(Regex("""\d+:\d{2}"""))
            assertEquals(
                "state=$state label=$label",
                state == CallPresentationState.ACTIVE,
                looksLikeDuration
            )
        }
    }
}
