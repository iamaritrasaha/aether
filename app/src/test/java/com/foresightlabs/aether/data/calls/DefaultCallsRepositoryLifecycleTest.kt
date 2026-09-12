package com.foresightlabs.aether.data.calls

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.foresightlabs.aether.calls.media.DecodedVideoFrame
import com.foresightlabs.aether.data.permissions.PermissionCoordinator
import com.foresightlabs.aether.data.telegram.TelegramClient
import com.foresightlabs.aether.domain.calls.AudioRoute
import com.foresightlabs.aether.domain.model.CallStateEnum
import com.foresightlabs.aether.domain.calls.MediaConnectionState
import com.foresightlabs.aether.domain.calls.TelegramCallMediaEngine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Pins the call-orchestration behaviours the physical-call investigation
 * depends on: mute reaches the engine, the ever-connected flag is sticky for
 * reconnecting presentation, teardown resets session identity, and a stale
 * media emission after teardown cannot revive anything.
 */
@RunWith(RobolectricTestRunner::class)
class DefaultCallsRepositoryLifecycleTest {

    private class FakeCallMediaEngine : TelegramCallMediaEngine {
        override val isMediaTransportAvailable = true
        private val _state = MutableStateFlow(MediaConnectionState.IDLE)
        override val state: StateFlow<MediaConnectionState> = _state
        override val audioRoute = MutableStateFlow(AudioRoute.EARPIECE)
        override val isMuted = MutableStateFlow(false)
        override val videoFrames = MutableSharedFlow<DecodedVideoFrame>()
        override val outgoingSignalingData = MutableSharedFlow<ByteArray>()

        var muteRequests = mutableListOf<Boolean>()
        var stopCount = 0

        override suspend fun start(call: TdApi.Call, ready: TdApi.CallStateReady, videoCaptureEnabled: Boolean) {}
        override fun setMicrophoneMuted(muted: Boolean) {
            muteRequests.add(muted)
            isMuted.value = muted
        }
        override fun setAudioOutput(route: AudioRoute) {}
        override fun setCameraEnabled(enabled: Boolean) {}
        override fun switchCamera() {}
        override fun submitIncomingSignalingData(callId: Long, data: ByteArray) {}
        override fun stop() {
            stopCount++
            _state.value = MediaConnectionState.STOPPED
        }
        override fun failConnectTimeout() {
            stopCount++
            _state.value = MediaConnectionState.FAILED
        }

        fun setState(newState: MediaConnectionState) {
            _state.value = newState
        }
    }

    private fun readyCall(id: Int): TdApi.Call =
        TdApi.Call(id, 0L, 7L, true, false, TdApi.CallStateReady())

    private class Harness {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val telegram = TelegramClient(application)
        val engine = FakeCallMediaEngine()
        val repository = DefaultCallsRepository(telegram, application, PermissionCoordinator(application), engine)

        /** Polls up to a few seconds for [condition] to become true, since the repository's collectors run on real background coroutines. */
        fun awaitUntil(timeoutMillis: Long = 3000, condition: () -> Boolean) {
            val deadline = System.currentTimeMillis() + timeoutMillis
            while (System.currentTimeMillis() < deadline) {
                if (condition()) return
                Thread.sleep(50)
            }
            assertTrue("Condition was never met within ${timeoutMillis}ms", condition())
        }
    }

    @Test
    fun incomingPendingCallPostsRingingNotificationAndResolveCancels() {
        val h = Harness()
        // Grant BEFORE the call update: the notifier fires from the
        // repository's collector and posts nothing if POST_NOTIFICATIONS is
        // denied (Android 13+ semantics, which Robolectric enforces).
        org.robolectric.Shadows.shadowOf(h.application).grantPermissions(
            android.Manifest.permission.POST_NOTIFICATIONS
        )
        // An INCOMING call still pending (not yet answered): isOutgoing=false.
        h.telegram.handleCallUpdateForTesting(
            TdApi.Call(9, 0L, 7L, false, false, TdApi.CallStatePending())
        )
        h.awaitUntil { h.telegram.activeCallState.value?.callId == 9 }

        val manager = h.application.getSystemService(android.app.NotificationManager::class.java)

        // The ringer is posted from the repository's collector coroutine, so
        // wait for it rather than asserting immediately.
        h.awaitUntil {
            manager.activeNotifications.any { it.id == IncomingCallNotifier.NOTIFICATION_ID }
        }

        // The call resolving (declined here) must cancel the ringer.
        h.telegram.handleCallUpdateForTesting(
            TdApi.Call(9, 0L, 7L, false, false, TdApi.CallStateDiscarded())
        )
        h.awaitUntil {
            manager.activeNotifications.none { it.id == IncomingCallNotifier.NOTIFICATION_ID }
        }
    }

    @Test
    fun toggleMuteReachesTheEngineAndStateFlipsTogether() {
        val h = Harness()
        h.telegram.handleCallUpdateForTesting(readyCall(id = 1))
        h.awaitUntil { h.telegram.activeCallState.value?.callId == 1 }

        h.repository.toggleMute()
        assertEquals(listOf(true), h.engine.muteRequests)
        assertTrue(h.telegram.activeCallState.value?.isMuted == true)

        h.repository.toggleMute()
        assertEquals(listOf(true, false), h.engine.muteRequests)
        assertFalse(h.telegram.activeCallState.value?.isMuted == true)
    }

    @Test
    fun everConnectedIsStickyAcrossAConnectThenDrop() {
        val h = Harness()
        h.telegram.handleCallUpdateForTesting(readyCall(id = 2))
        h.awaitUntil { h.telegram.activeCallState.value?.callId == 2 }

        h.engine.setState(MediaConnectionState.CONNECTING)
        h.awaitUntil { h.telegram.activeCallState.value?.mediaEverConnected == false }

        h.engine.setState(MediaConnectionState.CONNECTED)
        h.awaitUntil { h.telegram.activeCallState.value?.mediaEverConnected == true }

        // Transient drop: native reports plain CONNECTING; the flag must stay
        // sticky so the presenter can show RECONNECTING rather than a first
        // "Connecting".
        h.engine.setState(MediaConnectionState.CONNECTING)
        h.awaitUntil { h.telegram.activeCallState.value?.mediaState == MediaConnectionState.CONNECTING }
        assertTrue(
            "everConnected must remain sticky after a drop",
            h.telegram.activeCallState.value?.mediaEverConnected == true
        )
    }

    @Test
    fun tdLibDiscardTearsTheEngineDownAndResetsSessionIdentity() {
        val h = Harness()
        h.telegram.handleCallUpdateForTesting(readyCall(id = 3))
        h.awaitUntil { h.telegram.activeCallState.value?.callId == 3 }

        h.engine.setState(MediaConnectionState.CONNECTED)
        h.awaitUntil { h.telegram.activeCallState.value?.mediaEverConnected == true }

        // Remote (or local) hangup lands at the signalling layer.
        h.telegram.handleCallUpdateForTesting(readyCall(id = 3).copy(state = TdApi.CallStateDiscarded()))
        h.awaitUntil { h.engine.stopCount > 0 }

        // Once signalling has terminated, the ActiveCall snapshot is frozen:
        // a late native callback (engine teardown racing the discard) must
        // not mutate it -- that is the exact contract of the DISCARDED/ERROR
        // guard in updateCallMediaState.
        val frozen = h.telegram.activeCallState.value
        org.junit.Assert.assertEquals(CallStateEnum.DISCARDED, frozen?.state)
        h.engine.setState(MediaConnectionState.CONNECTED)
        Thread.sleep(200)
        org.junit.Assert.assertEquals(
            "a stale CONNECTED after teardown must not mutate the ended call",
            frozen,
            h.telegram.activeCallState.value
        )
    }
}

private fun TdApi.Call.copy(state: TdApi.CallState): TdApi.Call =
    TdApi.Call(id, uniqueId, userId, isOutgoing, isVideo, state)
