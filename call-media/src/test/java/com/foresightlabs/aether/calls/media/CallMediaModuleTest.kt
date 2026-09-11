package com.foresightlabs.aether.calls.media

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
}
