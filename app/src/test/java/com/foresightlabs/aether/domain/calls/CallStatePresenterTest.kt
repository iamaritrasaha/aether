package com.foresightlabs.aether.domain.calls

import com.foresightlabs.aether.domain.model.CallStateEnum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallStatePresenterTest {

    @Test
    fun outgoingBeforeAcceptShowsOutgoingRequest() {
        assertEquals(
            CallPresentationState.OUTGOING_REQUEST,
            CallStatePresenter.present(CallStateEnum.PENDING, MediaConnectionState.IDLE, isOutgoing = true)
        )
    }

    @Test
    fun incomingBeforeAcceptShowsRinging() {
        assertEquals(
            CallPresentationState.RINGING,
            CallStatePresenter.present(CallStateEnum.PENDING, MediaConnectionState.IDLE, isOutgoing = false)
        )
    }

    @Test
    fun readySignallingWithoutConnectedMediaIsConnectingNotActive() {
        assertEquals(
            CallPresentationState.CONNECTING,
            CallStatePresenter.present(CallStateEnum.READY, MediaConnectionState.CONNECTING, isOutgoing = true)
        )
        assertEquals(
            CallPresentationState.CONNECTING,
            CallStatePresenter.present(CallStateEnum.READY, MediaConnectionState.INITIALIZING, isOutgoing = true)
        )
        assertEquals(
            CallPresentationState.CONNECTING,
            CallStatePresenter.present(CallStateEnum.READY, MediaConnectionState.IDLE, isOutgoing = true)
        )
    }

    @Test
    fun activeIsReachableOnlyWithBothReadySignallingAndConnectedMedia() {
        assertEquals(
            CallPresentationState.ACTIVE,
            CallStatePresenter.present(CallStateEnum.READY, MediaConnectionState.CONNECTED, isOutgoing = true)
        )
    }

    @Test
    fun noCombinationOtherThanReadyPlusConnectedProducesActive() {
        for (signalling in CallStateEnum.entries) {
            for (media in MediaConnectionState.entries) {
                if (signalling == CallStateEnum.READY && media == MediaConnectionState.CONNECTED) continue
                val result = CallStatePresenter.present(signalling, media, isOutgoing = true)
                assertFalse(
                    "signalling=$signalling media=$media must not present as ACTIVE",
                    result == CallPresentationState.ACTIVE
                )
            }
        }
    }

    @Test
    fun readyWithReconnectingMediaShowsReconnecting() {
        assertEquals(
            CallPresentationState.RECONNECTING,
            CallStatePresenter.present(CallStateEnum.READY, MediaConnectionState.RECONNECTING, isOutgoing = true)
        )
    }

    @Test
    fun mediaFailureEndsCallAsFailedRegardlessOfSignalling() {
        assertEquals(
            CallPresentationState.FAILED,
            CallStatePresenter.present(CallStateEnum.READY, MediaConnectionState.FAILED, isOutgoing = true)
        )
        assertEquals(
            CallPresentationState.FAILED,
            CallStatePresenter.present(CallStateEnum.PENDING, MediaConnectionState.UNAVAILABLE, isOutgoing = true)
        )
    }

    @Test
    fun discardedAndHangingUpAreEnded() {
        assertEquals(
            CallPresentationState.ENDED,
            CallStatePresenter.present(CallStateEnum.DISCARDED, MediaConnectionState.CONNECTED, isOutgoing = true)
        )
        assertEquals(
            CallPresentationState.ENDED,
            CallStatePresenter.present(CallStateEnum.HANGING_UP, MediaConnectionState.CONNECTED, isOutgoing = true)
        )
    }

    @Test
    fun readyWithStoppedMediaIsEndedNotConnecting() {
        // A media engine that has stopped itself (e.g. after a connect
        // watchdog timeout or a FAILED-triggered teardown) must never be
        // read back as "still connecting" just because TDLib's own discard
        // of the call has not landed yet -- that gap is exactly what left a
        // call screen showing "Connecting..." forever after media had
        // already given up.
        assertEquals(
            CallPresentationState.ENDED,
            CallStatePresenter.present(CallStateEnum.READY, MediaConnectionState.STOPPED, isOutgoing = true)
        )
    }

    @Test
    fun durationRunsOnlyWhileActive() {
        for (state in CallPresentationState.entries) {
            assertEquals(state == CallPresentationState.ACTIVE, CallStatePresenter.durationShouldRun(state))
        }
    }
}
