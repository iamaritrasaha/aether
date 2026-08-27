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

    @Test
    fun nativeEngineReportsUnavailableWhenOfficialTransportIsAbsent() {
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
        })

        val config = CallMediaConfig(
            callId = 1L,
            isOutgoing = true,
            encryptionKey = ByteArray(256),
            allowP2p = false,
            servers = emptyList(),
            configJson = "{}",
            customParameters = "{}",
            protocol = CallProtocolInfo()
        )

        engine.startCall(config)

        assertEquals(MediaConnectionState.UNAVAILABLE, emittedState)
        assertTrue(emittedError?.contains("Official Telegram tgcalls media transport is not compiled") == true)
    }
}
