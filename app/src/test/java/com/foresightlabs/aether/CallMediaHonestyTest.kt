package com.foresightlabs.aether

import com.foresightlabs.aether.data.calls.TgCallsConfig
import com.foresightlabs.aether.data.calls.media.NativeCallEngineCallback
import com.foresightlabs.aether.data.calls.media.NativeTelegramCallEngine
import com.foresightlabs.aether.domain.calls.MediaConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.robolectric.annotation.Config

/**
 * A call must never claim to be connected on the strength of signalling alone.
 *
 * The engine previously emitted [MediaConnectionState.CONNECTED] and four signal
 * bars unconditionally — whether or not a media transport had loaded, and without
 * a single audio packet having been carried. That put a running duration timer and
 * a "Connected" label on a call that was silent in both directions.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class CallMediaHonestyTest {

    private class Recorder : NativeCallEngineCallback {
        val states = mutableListOf<MediaConnectionState>()
        val bars = mutableListOf<Int>()
        val errors = mutableListOf<String>()
        override fun onConnectionStateChanged(state: MediaConnectionState) { states += state }
        override fun onSignalBarsChanged(bars: Int) { this.bars += bars }
        override fun onError(error: String) { errors += error }
    }

    private fun config() = TgCallsConfig(
        callId = 101,
        isOutgoing = true,
        encryptionKey = ByteArray(256),
        allowP2p = true,
        servers = emptyList(),
        configJson = "{}",
        customParameters = "",
        maxLayer = 92,
        minLayer = 65,
        versions = listOf("11.0.0")
    )

    @Test
    fun withNoMediaTransportTheEngineReportsUnavailableRatherThanConnected() {
        val engine = NativeTelegramCallEngine()
        // The JVM test runtime has no libtgcalls, which is exactly the condition
        // the old code papered over.
        assertFalse(engine.isMediaTransportAvailable)

        val recorder = Recorder()
        engine.init(recorder)
        engine.startCall(config())

        assertEquals(listOf(MediaConnectionState.UNAVAILABLE), recorder.states)
        assertTrue(
            "A call with no media transport must not report itself connected",
            MediaConnectionState.CONNECTED !in recorder.states
        )
    }

    @Test
    fun noSignalQualityIsInventedWithoutATransportToMeasure() {
        val recorder = Recorder()
        NativeTelegramCallEngine().apply { init(recorder) }.startCall(config())
        assertTrue("Signal bars were reported with no transport: ${recorder.bars}", recorder.bars.isEmpty())
    }

    @Test
    fun theUnavailableStateCarriesAReasonTheUserCanBeShown() {
        val recorder = Recorder()
        NativeTelegramCallEngine().apply { init(recorder) }.startCall(config())
        assertEquals(1, recorder.errors.size)
        assertTrue(recorder.errors.single().isNotBlank())
    }
}
