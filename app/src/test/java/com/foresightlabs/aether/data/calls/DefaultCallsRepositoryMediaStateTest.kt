package com.foresightlabs.aether.data.calls

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.foresightlabs.aether.calls.media.DecodedVideoFrame
import com.foresightlabs.aether.data.permissions.PermissionCoordinator
import com.foresightlabs.aether.data.telegram.TelegramClient
import com.foresightlabs.aether.domain.calls.AudioRoute
import com.foresightlabs.aether.domain.calls.MediaConnectionState
import com.foresightlabs.aether.domain.calls.TelegramCallMediaEngine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Proven bug this guards: `DefaultCallsRepository`'s duration timer already
 * gated correctly on `mediaEngine.state.value == CONNECTED` internally, but
 * with `mediaState` never reaching `ActiveCall` (see
 * `TelegramClientCallMediaStateTest`), the call screen had no way to show
 * that duration even while it was ticking underneath -- `CallStatePresenter`
 * stayed on CONNECTING regardless. This proves the observable, end-to-end
 * behaviour: duration is exactly zero before native CONNECTED and starts
 * incrementing once [DefaultCallsRepository] actually sees it.
 *
 * Runs under Robolectric for the same reason as
 * `TelegramClientCallMediaStateTest` -- `TelegramClient` needs a real
 * `Application` for its guarded `AudioManager` calls.
 */
@RunWith(RobolectricTestRunner::class)
class DefaultCallsRepositoryMediaStateTest {

    private class FakeCallMediaEngine : TelegramCallMediaEngine {
        override val isMediaTransportAvailable = true
        private val _state = MutableStateFlow(MediaConnectionState.IDLE)
        override val state: StateFlow<MediaConnectionState> = _state
        override val audioRoute = MutableStateFlow(AudioRoute.EARPIECE)
        override val isMuted = MutableStateFlow(false)
        override val isCameraActive = MutableStateFlow(false)
        override val isFrontCamera = MutableStateFlow(true)
        override val videoFrames = MutableSharedFlow<DecodedVideoFrame>()
        override val outgoingSignalingData = MutableSharedFlow<ByteArray>()

        override suspend fun start(call: TdApi.Call, ready: TdApi.CallStateReady, videoCaptureEnabled: Boolean) {}
        override fun setMicrophoneMuted(muted: Boolean) {}
        override fun setAudioOutput(route: AudioRoute) {}
        override fun setCameraEnabled(enabled: Boolean) {}
        override fun switchCamera() {}
        override fun submitIncomingSignalingData(callId: Long, data: ByteArray) {}
        override fun stop() {}
        override fun failConnectTimeout() {}

        fun setState(newState: MediaConnectionState) {
            _state.value = newState
        }
    }

    private fun readyCall(id: Int): TdApi.Call =
        TdApi.Call(id, 0L, 7L, true, false, TdApi.CallStateReady())

    /** Polls up to a few seconds for [condition] to become true, since the repository's timer runs on a real background coroutine. */
    private fun awaitUntil(timeoutMillis: Long = 3000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        assertTrue("Condition was never met within ${timeoutMillis}ms", condition())
    }

    @Test
    fun durationStaysZeroWhileConnectingAndStartsOnlyAtConnected() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val telegram = TelegramClient(application)
        val permissionCoordinator = PermissionCoordinator(application)
        val engine = FakeCallMediaEngine()
        DefaultCallsRepository(telegram, application, permissionCoordinator, engine)

        telegram.handleCallUpdateForTesting(readyCall(id = 1))
        awaitUntil { telegram.activeCallState.value?.callId == 1 }

        engine.setState(MediaConnectionState.CONNECTING)
        Thread.sleep(1200)
        assertEquals(
            "Duration must not advance before native CONNECTED",
            0,
            telegram.activeCallState.value?.durationSec
        )

        engine.setState(MediaConnectionState.CONNECTED)
        awaitUntil { (telegram.activeCallState.value?.durationSec ?: 0) > 0 }

        assertEquals(MediaConnectionState.CONNECTED, telegram.activeCallState.value?.mediaState)
    }
}
