package com.foresightlabs.aether.calls.media

import io.github.pytgcalls.media.DeviceInfo
import io.github.pytgcalls.media.MediaDescription
import io.github.pytgcalls.media.MediaDevices
import io.github.pytgcalls.media.StreamMode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallMediaModuleTest {

    @Test
    fun callMediaConfigSecretWipeClearsEncryptionKey() {
        val key = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val config = CallMediaConfig(
            callId = 12345L,
            isOutgoing = true,
            videoCaptureEnabled = false,
            encryptionKey = key,
            allowP2p = false,
            servers = emptyList(),
            configJson = "{}",
            customParameters = "{}",
            protocol = CallProtocolInfo()
        )

        assertEquals(12345L, config.callId)
        assertTrue(config.isOutgoing)
        assertFalse(config.allowP2p)

        config.wipeSecrets()
        val expectedZeros = ByteArray(8)
        assertArrayEquals(expectedZeros, config.encryptionKey)
    }

    @Test
    fun callServerEndpointEqualityAndHashcode() {
        val server1 = CallServerEndpoint(
            id = 101L,
            ipAddress = "149.154.167.50",
            ipv6Address = "",
            port = 443,
            peerTag = byteArrayOf(10, 20),
            isTcp = true,
            username = "user",
            password = "pwd",
            supportsTurn = true,
            supportsStun = true
        )
        val server2 = CallServerEndpoint(
            id = 101L,
            ipAddress = "149.154.167.50",
            ipv6Address = "",
            port = 443,
            peerTag = byteArrayOf(10, 20),
            isTcp = true,
            username = "user",
            password = "pwd",
            supportsTurn = true,
            supportsStun = true
        )
        assertEquals(server1, server2)
        assertEquals(server1.hashCode(), server2.hashCode())
    }

    @Test
    fun mediaConnectionStateTransitions() {
        val states = MediaConnectionState.entries
        assertTrue(states.contains(MediaConnectionState.IDLE))
        assertTrue(states.contains(MediaConnectionState.INITIALIZING))
        assertTrue(states.contains(MediaConnectionState.CONNECTING))
        assertTrue(states.contains(MediaConnectionState.CONNECTED))
        assertTrue(states.contains(MediaConnectionState.RECONNECTING))
        assertTrue(states.contains(MediaConnectionState.FAILED))
        assertTrue(states.contains(MediaConnectionState.UNAVAILABLE))
        assertTrue(states.contains(MediaConnectionState.STOPPED))
    }

    /**
     * `io.github.pytgcalls:ntgcalls` ships a real `arm64-v8a` native library
     * (see docs/architecture/calling-native-stack.md) -- but that ELF binary
     * cannot load on the host JVM this test runs on, so `System.loadLibrary`
     * fails here exactly as it would on an unsupported device. That is the
     * behaviour under test: a call must report UNAVAILABLE, never a fabricated
     * CONNECTED, whenever the real transport cannot actually load.
     */
    @Test
    fun nativeEngineReportsUnavailableWhenTheRealTransportCannotLoad() {
        val engine = NativeTelegramCallMediaEngine()
        assertFalse(engine.isMediaTransportAvailable)

        var emittedState: MediaConnectionState? = null
        var emittedError: String? = null

        engine.init(object : NativeCallEngineCallback {
            override fun onConnectionStateChanged(stateOrdinal: Int) {
                emittedState = MediaConnectionState.entries.getOrNull(stateOrdinal)
            }

            override fun onSignalBarsChanged(bars: Int) {}

            override fun onAudioLevelsChanged(localLevel: Float, remoteLevel: Float) {}

            override fun onError(error: String) {
                emittedError = error
            }

            override fun onOutgoingSignalingData(data: ByteArray) {}

            override fun onVideoFrame(frame: DecodedVideoFrame) {}
        })

        val config = CallMediaConfig(
            callId = 1L,
            isOutgoing = true,
            videoCaptureEnabled = false,
            encryptionKey = ByteArray(256),
            allowP2p = false,
            servers = emptyList(),
            configJson = "{}",
            customParameters = "{}",
            protocol = CallProtocolInfo()
        )

        engine.startCall(config)

        assertEquals(MediaConnectionState.UNAVAILABLE, emittedState)
        assertTrue(emittedError?.isNotBlank() == true)
    }

    /**
     * On the host JVM the real `arm64-v8a` native library cannot load (see
     * [nativeEngineReportsUnavailableWhenTheRealTransportCannotLoad]), so
     * querying what protocol it supports must fail closed to null rather than
     * fabricating a capability the engine never actually reported.
     */
    @Test
    fun supportedProtocolIsNullWhenTheRealTransportCannotLoad() {
        assertEquals(null, NativeTelegramCallMediaEngine.supportedProtocol())
    }

    @Test
    fun i420ConverterRejectsATruncatedBuffer() {
        val tooShort = ByteArray(4)
        assertEquals(null, I420Converter.convert(tooShort, 4, 4))
    }

    @Test
    fun i420ConverterProducesOnePixelPerInputPixel() {
        val width = 2
        val height = 2
        val y = ByteArray(width * height) { 235.toByte() }
        val chroma = ByteArray(1) { 128.toByte() } // 1x1 chroma plane for a 2x2 frame
        val image = I420Converter.convert(y + chroma + chroma, width, height)

        assertEquals(width, image?.width)
        assertEquals(height, image?.height)
        assertEquals(width * height, image?.pixels?.size)
        // Neutral chroma at max luma lands very close to opaque white.
        val argb = image!!.pixels[0]
        assertEquals(0xFF, (argb shr 24) and 0xFF)
        assertTrue((argb and 0xFF) > 240)
    }

    // =========================================================================
    // applyStreamSources Truthfulness Tests (rc02 API contract)
    // =========================================================================

    private val sampleDevices = MediaDevices(
        listOf(DeviceInfo("Built-in Mic", "mic_0")),
        listOf(DeviceInfo("Speaker", "spk_0")),
        listOf(DeviceInfo("Front Camera", "cam_front")),
        emptyList()
    )

    @Test
    fun applyStreamSources_voiceCall_succeedsWhenNativeCallSucceeds() {
        val invocations = mutableListOf<MediaDescription>()
        val result = NativeTelegramCallMediaEngine.applyStreamSourcesForTesting(
            callId = 42L,
            cameraEnabled = false,
            deviceProvider = { sampleDevices },
            streamSourceSetter = { callId, mode, desc ->
                assertEquals(42L, callId)
                assertEquals(StreamMode.CAPTURE, mode)
                invocations.add(desc)
            }
        )

        assertTrue("Voice call must succeed when setStreamSources succeeds", result)
        assertEquals(1, invocations.size)
        assertEquals("mic_0", invocations[0].microphone?.input)
        assertEquals(null, invocations[0].speaker)
        assertEquals(null, invocations[0].camera)
        assertEquals(null, invocations[0].screen)
    }

    @Test
    fun applyStreamSources_voiceCall_returnsFalseWhenNativeCallThrows() {
        val result = NativeTelegramCallMediaEngine.applyStreamSourcesForTesting(
            callId = 42L,
            cameraEnabled = false,
            deviceProvider = { sampleDevices },
            streamSourceSetter = { _, _, _ ->
                throw RuntimeException("Simulated native setStreamSources failure")
            }
        )

        assertFalse("Voice call must return false when setStreamSources throws", result)
    }

    @Test
    fun applyStreamSources_failsWhenNoMicrophoneDeviceAvailable() {
        var setterCalled = false
        val noMicDevices = MediaDevices(emptyList(), emptyList(), emptyList(), emptyList())
        val result = NativeTelegramCallMediaEngine.applyStreamSourcesForTesting(
            callId = 42L,
            cameraEnabled = false,
            deviceProvider = { noMicDevices },
            streamSourceSetter = { _, _, _ -> setterCalled = true }
        )

        assertFalse("Must fail when no microphone is enumerated", result)
        assertFalse("Native stream sources must never be called without a microphone", setterCalled)
    }

    @Test
    fun applyStreamSources_videoCall_succeedsWithBothWhenNativeCallSucceeds() {
        val invocations = mutableListOf<MediaDescription>()
        val result = NativeTelegramCallMediaEngine.applyStreamSourcesForTesting(
            callId = 42L,
            cameraEnabled = true,
            deviceProvider = { sampleDevices },
            streamSourceSetter = { _, _, desc -> invocations.add(desc) },
            cameraSelector = { devices, _ -> devices.camera.firstOrNull() }
        )

        assertTrue("Video call must succeed when dual-stream setup succeeds", result)
        assertEquals(1, invocations.size)
        assertEquals("mic_0", invocations[0].microphone?.input)
        assertEquals(null, invocations[0].speaker)
        assertEquals("cam_front", invocations[0].camera?.input)
        assertEquals(null, invocations[0].screen)
    }

    @Test
    fun applyStreamSources_videoCall_degradesToAudioWhenCameraAttemptThrowsAndAudioSucceeds() {
        val invocations = mutableListOf<MediaDescription>()
        var attemptCount = 0
        val result = NativeTelegramCallMediaEngine.applyStreamSourcesForTesting(
            callId = 42L,
            cameraEnabled = true,
            deviceProvider = { sampleDevices },
            streamSourceSetter = { _, _, desc ->
                invocations.add(desc)
                attemptCount++
                if (attemptCount == 1) {
                    throw RuntimeException("Simulated camera stream rejection by WebRTC")
                }
            },
            cameraSelector = { devices, _ -> devices.camera.firstOrNull() }
        )

        assertTrue("Video call must succeed by falling back to audio when camera setup fails", result)
        assertEquals(2, invocations.size)
        // First attempt had both mic and camera
        assertEquals("mic_0", invocations[0].microphone?.input)
        assertEquals("cam_front", invocations[0].camera?.input)
        // Second attempt degraded to audio only
        assertEquals("mic_0", invocations[1].microphone?.input)
        assertEquals(null, invocations[1].speaker)
        assertEquals(null, invocations[1].camera)
        assertEquals(null, invocations[1].screen)
    }

    @Test
    fun applyStreamSources_videoCall_returnsFalseWhenBothCameraAndAudioRetryThrow() {
        val invocations = mutableListOf<MediaDescription>()
        val result = NativeTelegramCallMediaEngine.applyStreamSourcesForTesting(
            callId = 42L,
            cameraEnabled = true,
            deviceProvider = { sampleDevices },
            streamSourceSetter = { _, _, desc ->
                invocations.add(desc)
                throw RuntimeException("Both attempts fail")
            },
            cameraSelector = { devices, _ -> devices.camera.firstOrNull() }
        )

        assertFalse("Video call must return false when both dual and fallback attempts throw", result)
        assertEquals(2, invocations.size)
    }

    @Test
    fun applyStreamSources_videoCall_fallsBackToAudioOnlyWhenNoCameraHardwareAvailable() {
        val invocations = mutableListOf<MediaDescription>()
        val devicesWithoutCamera = MediaDevices(
            listOf(DeviceInfo("Built-in Mic", "mic_0")),
            emptyList(),
            emptyList(),
            emptyList()
        )
        val result = NativeTelegramCallMediaEngine.applyStreamSourcesForTesting(
            callId = 42L,
            cameraEnabled = true,
            deviceProvider = { devicesWithoutCamera },
            streamSourceSetter = { _, _, desc -> invocations.add(desc) },
            cameraSelector = { devices, _ -> devices.camera.firstOrNull() }
        )

        assertTrue("Video call without camera device must fall back to audio and succeed", result)
        assertEquals(1, invocations.size)
        assertEquals("mic_0", invocations[0].microphone?.input)
        assertEquals(null, invocations[0].speaker)
        assertEquals(null, invocations[0].camera)
        assertEquals(null, invocations[0].screen)
    }
}
