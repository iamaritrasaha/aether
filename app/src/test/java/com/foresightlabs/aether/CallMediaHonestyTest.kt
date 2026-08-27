package com.foresightlabs.aether

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.calls.media.CallMediaConfig
import com.foresightlabs.aether.calls.media.CallProtocolInfo
import com.foresightlabs.aether.calls.media.CallServerEndpoint
import com.foresightlabs.aether.calls.media.MediaConnectionState as NativeMediaState
import com.foresightlabs.aether.calls.media.NativeCallEngineCallback
import com.foresightlabs.aether.calls.media.NativeTelegramCallMediaEngine
import com.foresightlabs.aether.data.calls.TgCallsAdapter
import com.foresightlabs.aether.domain.calls.MediaConnectionState as DomainMediaState
import com.foresightlabs.aether.domain.model.ActiveCall
import com.foresightlabs.aether.domain.model.CallStateEnum
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A call must never claim to be connected on the strength of signalling alone.
 *
 * In host JVM tests or builds where the official tgcalls engine is absent,
 * the engine reports UNAVAILABLE rather than fabricating a CONNECTED state.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class CallMediaHonestyTest {

    private class Recorder : NativeCallEngineCallback {
        val states = mutableListOf<NativeMediaState>()
        val bars = mutableListOf<Int>()
        val errors = mutableListOf<String>()
        override fun onConnectionStateChanged(stateOrdinal: Int) {
            val state = NativeMediaState.entries.getOrElse(stateOrdinal) { NativeMediaState.FAILED }
            states += state
        }
        override fun onSignalBarsChanged(bars: Int) { this.bars += bars }
        override fun onAudioLevelsChanged(localLevel: Float, remoteLevel: Float) {}
        override fun onError(error: String) { errors += error }
    }

    private fun config() = CallMediaConfig(
        callId = 101L,
        isOutgoing = true,
        encryptionKey = ByteArray(256),
        allowP2p = true,
        servers = listOf(
            CallServerEndpoint(
                id = 1L,
                ipAddress = "149.154.167.50",
                ipv6Address = "2001:67c:4e8:f004::9",
                port = 443,
                peerTag = byteArrayOf(1, 2, 3),
                isTcp = false
            )
        ),
        configJson = "{}",
        customParameters = "{\"p2p\":true}",
        protocol = CallProtocolInfo(
            minLayer = 65,
            maxLayer = 92,
            udpP2p = true,
            udpReflector = true,
            libraryVersions = listOf("1.0.0")
        )
    )

    @Test
    fun withNoOfficialMediaTransportTheEngineReportsUnavailableRatherThanConnected() {
        val engine = NativeTelegramCallMediaEngine()
        assertFalse(engine.isMediaTransportAvailable)

        val recorder = Recorder()
        engine.init(recorder)
        engine.startCall(config())

        assertEquals(listOf(NativeMediaState.UNAVAILABLE), recorder.states)
        assertTrue(
            "A call with no media transport must not report itself connected",
            NativeMediaState.CONNECTED !in recorder.states
        )
    }

    @Test
    fun noSignalQualityIsInventedWithoutATransportToMeasure() {
        val recorder = Recorder()
        NativeTelegramCallMediaEngine().apply { init(recorder) }.startCall(config())
        assertTrue("Signal bars were reported with no transport: ${recorder.bars}", recorder.bars.isEmpty())
    }

    @Test
    fun theUnavailableStateCarriesAReasonTheUserCanBeShown() {
        val recorder = Recorder()
        NativeTelegramCallMediaEngine().apply { init(recorder) }.startCall(config())
        assertEquals(1, recorder.errors.size)
        assertTrue(recorder.errors.single().isNotBlank())
    }

    @Test
    fun tdLibReadyStateDoesNotImplyMediaConnected() {
        val call = TdApi.Call().apply {
            id = 101
            isOutgoing = true
        }
        val server = TdApi.CallServer(
            1L, "149.154.167.50", "2001:67c:4e8:f004::9", 443,
            TdApi.CallServerTypeTelegramReflector(byteArrayOf(1, 2, 3), false)
        )
        val ready = TdApi.CallStateReady().apply {
            protocol = TdApi.CallProtocol(true, true, 65, 92, arrayOf("1.0.0"))
            servers = arrayOf(server)
            config = "{}"
            encryptionKey = ByteArray(256)
            allowP2p = true
            customParameters = "{}"
        }

        val mediaConfig = TgCallsAdapter.buildMediaConfig(call, ready)
        assertEquals(101L, mediaConfig.callId)
        assertEquals(1, mediaConfig.servers.size)

        val activeCall = ActiveCall(
            callId = 101,
            userId = 202L,
            state = CallStateEnum.READY,
            mediaState = DomainMediaState.IDLE,
            durationSec = 0
        )
        assertEquals(CallStateEnum.READY, activeCall.state)
        assertEquals(DomainMediaState.IDLE, activeCall.mediaState)
        assertEquals(0, activeCall.durationSec)
    }
}
