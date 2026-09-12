package com.foresightlabs.aether.calls.media

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.foresightlabs.aether.calls.media.NativeTelegramCallMediaEngine
import io.github.pytgcalls.ConnectionChangeCallback
import io.github.pytgcalls.ConnectionInfo
import io.github.pytgcalls.NTgCalls
import io.github.pytgcalls.RemoteSource
import io.github.pytgcalls.RemoteSourceChangeCallback
import io.github.pytgcalls.StreamEndCallback
import io.github.pytgcalls.media.StreamType
import io.github.pytgcalls.media.AudioDescription
import io.github.pytgcalls.media.MediaDescription
import io.github.pytgcalls.media.MediaSource
import io.github.pytgcalls.media.StreamMode
import io.github.pytgcalls.media.StreamDevice
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device native smoke test for the full media-engine seam, up to (but not
 * including) transport negotiation -- everything a real call does before and
 * around the network path, exercised against real hardware:
 *
 * 1. libntgcalls.so loads and `NTgCalls.ping()` answers (JNI_OnLoad ran).
 * 2. WebRTC's Android layer initialises (PeerConnectionFactory.initialize
 *    with a real application Context -- the historically fatal boundary).
 * 3. `getMediaDevices()` enumerates real microphone and speaker devices with
 *    the `is_microphone` metadata JSON the native device modules require.
 * 4. A real native P2P session is created, and CAPTURE (microphone) and
 *    PLAYBACK (speaker) stream sources are configured with the REAL device
 *    metadata -- the exact code path a voice call runs, including Oboe
 *    stream construction. (Streams do not START until a connection is
 *    established; construction alone proves the device layer accepts our
 *    descriptions.)
 * 5. The engine's media-state and time() seams answer for the session.
 * 6. `stop()` tears the session down cleanly with no crash and no leftover
 *    session in `NTgCalls.calls()`.
 *
 * This is deliberately NOT a Telegram-interop test: no signalling, no
 * network, no second participant. It proves the native boundaries that
 * physical two-way audio additionally depends on.
 */
@RunWith(AndroidJUnit4::class)
class CallMediaNativeSmokeTest {

    private val fakeCallId = 990_001L
    private val dummyKey = ByteArray(32) { it.toByte() }

    /**
     * RECORD_AUDIO must be granted before this suite runs
     * (`adb shell pm grant com.foresightlabs.aether android.permission.RECORD_AUDIO`)
     * -- the capture-source construction builds a real Oboe AudioRecord.
     */
    @Before
    fun assertRecordAudioGranted() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        org.junit.Assume.assumeTrue(
            "RECORD_AUDIO not granted; run `adb shell pm grant ${context.packageName} android.permission.RECORD_AUDIO`",
            context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    private fun engine(): NativeTelegramCallMediaEngine {
        val engine = NativeTelegramCallMediaEngine()
        engine.setContext(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext)
        return engine
    }

    @Test
    fun nativeLibraryLoadsAndAnswersPing() {
        assertEquals("pong", NTgCalls.ping())
    }

    @Test
    fun webrtcAndroidContextInitialisesAndTransportBecomesAvailable() {
        val engine = engine()
        assertTrue(
            "isMediaTransportAvailable must be true after setContext (WebRTC context initialised)",
            engine.isMediaTransportAvailable
        )
    }

    @Test
    fun supportedProtocolMatchesTelegramCallingContract() {
        val protocol = NativeTelegramCallMediaEngine.supportedProtocol()
        assertNotNull(protocol)
        assertTrue(protocol!!.minLayer > 0)
        assertTrue(protocol.maxLayer >= protocol.minLayer)
        assertTrue(protocol.libraryVersions.isNotEmpty())
    }

    @Test
    fun mediaDevicesEnumerateRealMicrophoneAndSpeaker() {
        engine()

        val devices = NTgCalls.getMediaDevices()
        assertNotNull(devices)
        assertTrue("at least one microphone must be enumerable", devices!!.microphone.isNotEmpty())
        assertTrue("at least one speaker/output must be enumerable", devices.speaker.isNotEmpty())

        val mic = devices.microphone.first()
        val micMetadata = JSONObject(mic.metadata)
        assertTrue(
            "microphone metadata must be the JSON object the native device module requires",
            micMetadata.has("is_microphone")
        )
        assertTrue(micMetadata.optBoolean("is_microphone", false))

        val speaker = devices.speaker.first()
        val speakerMetadata = JSONObject(speaker.metadata)
        assertEquals(
            "output device must declare is_microphone=false",
            false,
            speakerMetadata.optBoolean("is_microphone", true)
        )
    }

    @Test
    fun realNativeSessionAcceptsCaptureAndPlaybackSourcesAndTearsDownCleanly() {
        engine()
        val devices = NTgCalls.getMediaDevices()
        assertNotNull(devices)
        val mic = devices!!.microphone.first()
        val speaker = devices.speaker.first()

        val ntg = NTgCalls()
        try {
            ntg.createP2pCall(fakeCallId)
            // TDLib normally completed the DH exchange; skipExchange hands the
            // transport the key directly. Key contents are not validated here
            // (no negotiation happens), only its presence/shape.
            ntg.skipExchange(fakeCallId, dummyKey, true)

            // CAPTURE with the real microphone description -- constructs the
            // Oboe capture stream object (not started until CONNECTED).
            ntg.setStreamSources(
                fakeCallId,
                StreamMode.CAPTURE,
                MediaDescription(
                    AudioDescription(MediaSource.DEVICE, 48000, 1, mic.metadata, false),
                    null, null, null
                )
            )
            // PLAYBACK with the real output description in the .microphone
            // slot -- the documented rc02 requirement for incoming audio
            // (optimize_sources gates on a Microphone writer).
            ntg.setStreamSources(
                fakeCallId,
                StreamMode.PLAYBACK,
                MediaDescription(
                    AudioDescription(MediaSource.DEVICE, 48000, 1, speaker.metadata, false),
                    null, null, null
                )
            )

            val state = ntg.getState(fakeCallId)
            assertNotNull(state)
            assertFalse("a fresh session is not muted", state.muted)
            // Native get_state(): videoStopped == !has_device(Capture, Camera)
            // -- TRUE for this voice-only session, exactly as a real voice
            // call should report.
            assertTrue("voice-only session must report videoStopped", state.videoStopped)

            assertEquals(0L, ntg.time(fakeCallId, StreamMode.CAPTURE))
            assertEquals(0L, ntg.time(fakeCallId, StreamMode.PLAYBACK))
        } finally {
            ntg.stop(fakeCallId)
        }

        assertFalse(
            "session must be gone after stop()",
            ntg.calls().containsKey(fakeCallId)
        )
    }

    @Test
    fun streamEndAndRemoteSourceCallbacksAreRegisteredWithoutCrash() {
        val ntg = NTgCalls()
        try {
            ntg.onStreamEnd(object : StreamEndCallback {
                override fun onStreamEnd(callId: Long, type: StreamType?, device: StreamDevice?) {}
            })
            ntg.onRemoteSourceChange(object : RemoteSourceChangeCallback {
                override fun onRemoteSourceChange(callId: Long, source: RemoteSource?) {}
            })
            ntg.onConnectionChange(object : ConnectionChangeCallback {
                override fun onConnectionChange(callId: Long, info: ConnectionInfo?) {}
            })
        } finally {
            // No session was created; stop of an unknown id must be safe.
            try {
                ntg.stop(fakeCallId)
            } catch (_: Throwable) {
                // stop of a nonexistent session may throw; that is acceptable
                // and must not crash the process.
            }
        }
        assertTrue(true)
    }
}
