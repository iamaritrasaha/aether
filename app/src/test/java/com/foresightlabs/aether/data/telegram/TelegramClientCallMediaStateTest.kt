package com.foresightlabs.aether.data.telegram

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.foresightlabs.aether.domain.calls.MediaConnectionState
import com.foresightlabs.aether.domain.model.CallStateEnum
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Proven bug this guards: native reaching CONNECTED could never turn into
 * `CallPresentationState.ACTIVE` because nothing ever wrote
 * [com.foresightlabs.aether.domain.model.ActiveCall.mediaState] --
 * `handleCallUpdate` rebuilt `ActiveCall` from a fresh TDLib `Call` on every
 * signalling update, defaulting `mediaState` back to IDLE each time, and
 * `DefaultCallsRepository.handleMediaStateChange` never called
 * [TelegramClient.updateCallMediaState] at all. The call really connected
 * (native CONNECTED, ICE/DTLS writable) while the UI stayed on "Connecting…"
 * forever. See docs/architecture/calling-native-stack.md.
 *
 * Runs under Robolectric: TelegramClient needs a real [Application] for its
 * (try/catch-guarded) AudioManager calls in updateAudioHardware/
 * resetAudioHardware, which the plain JVM unit-test stub jar cannot provide.
 */
@RunWith(RobolectricTestRunner::class)
class TelegramClientCallMediaStateTest {

    private fun newClient(): TelegramClient =
        TelegramClient(ApplicationProvider.getApplicationContext<Application>())

    private fun readyCall(id: Int, userId: Long = 7L): TdApi.Call =
        TdApi.Call(id, 0L, userId, true, false, TdApi.CallStateReady())

    @Test
    fun nativeConnectedUpdatesActiveCallMediaState() {
        val client = newClient()
        client.handleCallUpdateForTesting(readyCall(id = 1))
        assertEquals(CallStateEnum.READY, client.activeCallState.value?.state)
        assertEquals(MediaConnectionState.IDLE, client.activeCallState.value?.mediaState)

        client.updateCallMediaState(1, MediaConnectionState.CONNECTED)

        assertEquals(MediaConnectionState.CONNECTED, client.activeCallState.value?.mediaState)
    }

    @Test
    fun subsequentTdLibReadyUpdateCannotResetConnectedMediaState() {
        val client = newClient()
        client.handleCallUpdateForTesting(readyCall(id = 1))
        client.updateCallMediaState(1, MediaConnectionState.CONNECTED)
        assertEquals(MediaConnectionState.CONNECTED, client.activeCallState.value?.mediaState)

        // A second CallStateReady for the SAME call (TDLib re-delivers this
        // update; ntgcalls' own reconnection also re-lands here) must never
        // silently reset the media state a real native CONNECTED already
        // established.
        client.handleCallUpdateForTesting(readyCall(id = 1))

        assertEquals(
            "A repeated TDLib update for the same call must preserve the already-connected media state",
            MediaConnectionState.CONNECTED,
            client.activeCallState.value?.mediaState
        )
    }

    @Test
    fun aNewCallAtTheSameSlotStartsMediaStateAtIdleNotThePreviousCallsState() {
        val client = newClient()
        client.handleCallUpdateForTesting(readyCall(id = 1))
        client.updateCallMediaState(1, MediaConnectionState.CONNECTED)

        // Call 1 ends and a new call (a different TDLib call id) begins.
        client.handleCallUpdateForTesting(readyCall(id = 2))

        assertEquals(
            "A different call id must never inherit the previous call's media state",
            MediaConnectionState.IDLE,
            client.activeCallState.value?.mediaState
        )
    }

    @Test
    fun staleCallMediaCallbackCannotMutateANewerCall() {
        val client = newClient()
        client.handleCallUpdateForTesting(readyCall(id = 1))
        client.handleCallUpdateForTesting(readyCall(id = 2))
        assertEquals(2, client.activeCallState.value?.callId)

        // A late media callback for call 1 -- already replaced at this
        // slot -- must be a no-op, never a mutation of call 2's state.
        client.updateCallMediaState(1, MediaConnectionState.CONNECTED)

        assertEquals(
            "A stale call's media callback must never mutate the call that succeeded it",
            MediaConnectionState.IDLE,
            client.activeCallState.value?.mediaState
        )
        assertEquals(2, client.activeCallState.value?.callId)
    }

    @Test
    fun updateCallMediaStateIsANoOpWhenNoCallIsActive() {
        val client = newClient()
        assertNull(client.activeCallState.value)

        client.updateCallMediaState(1, MediaConnectionState.CONNECTED)

        assertNull(client.activeCallState.value)
    }
}
