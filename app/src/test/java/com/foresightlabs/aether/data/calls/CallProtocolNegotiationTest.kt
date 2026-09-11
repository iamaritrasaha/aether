package com.foresightlabs.aether.data.calls

import com.foresightlabs.aether.calls.media.CallProtocolInfo
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The call protocol Aether hands TDLib's real `CreateCall`/`AcceptCall` must
 * come from what the linked media engine actually supports, not a guess --
 * see docs/architecture/calling-native-stack.md.
 */
class CallProtocolNegotiationTest {

    @Test
    fun withNoLinkedTransportFallsBackToConservativeTdlibDocumentedDefaults() {
        val protocol = TgCallsAdapter.buildCallProtocol(null)

        assertTrue(protocol.udpP2p)
        assertTrue(protocol.udpReflector)
        assertEquals(65, protocol.minLayer)
        assertEquals(92, protocol.maxLayer)
        assertTrue(protocol.libraryVersions.isNotEmpty())
    }

    @Test
    fun withALinkedTransportUsesExactlyWhatItReportsSupporting() {
        val supported = CallProtocolInfo(
            minLayer = 92,
            maxLayer = 100,
            udpP2p = false,
            udpReflector = true,
            libraryVersions = listOf("5.4.0", "5.3.0")
        )

        val protocol = TgCallsAdapter.buildCallProtocol(supported)

        assertEquals(false, protocol.udpP2p)
        assertEquals(true, protocol.udpReflector)
        assertEquals(92, protocol.minLayer)
        assertEquals(100, protocol.maxLayer)
        assertArrayEquals(arrayOf("5.4.0", "5.3.0"), protocol.libraryVersions)
    }

    /**
     * Compiles against the real generated `TdApi.java` constructors pinned at
     * `tdlib/src/main/java/org/drinkless/tdlib/TdApi.java` -- if any of these
     * shapes drift from the pinned TDLib revision, this test fails to compile
     * rather than silently using a stale signature.
     */
    @Test
    fun callSignallingUsesTheExactPinnedTdlibConstructors() {
        val protocol = TdApi.CallProtocol(true, true, 65, 92, arrayOf("2.9.5"))
        val createCall = TdApi.CreateCall(12345L, protocol, true)
        assertEquals(12345L, createCall.userId)
        assertTrue(createCall.isVideo)
        assertEquals(protocol, createCall.protocol)

        val acceptCall = TdApi.AcceptCall(7, protocol)
        assertEquals(7, acceptCall.callId)

        val signalingOut = TdApi.SendCallSignalingData(7, byteArrayOf(1, 2, 3))
        assertEquals(7, signalingOut.callId)
        assertArrayEquals(byteArrayOf(1, 2, 3), signalingOut.data)

        val signalingIn = TdApi.UpdateNewCallSignalingData(7, byteArrayOf(4, 5, 6))
        assertEquals(7, signalingIn.callId)
        assertArrayEquals(byteArrayOf(4, 5, 6), signalingIn.data)

        val discardCall = TdApi.DiscardCall(7, false, "", 0, false, 0)
        assertEquals(7, discardCall.callId)
    }

    @Test
    fun buildMediaConfigCarriesWhetherTheCameraMayActuallyOpen() {
        val videoCall = TdApi.Call().apply {
            id = 55
            isOutgoing = false
            isVideo = true
        }
        val ready = TdApi.CallStateReady().apply {
            protocol = TdApi.CallProtocol(true, true, 65, 92, arrayOf("1.0.0"))
            servers = emptyArray()
            config = "{}"
            encryptionKey = ByteArray(256)
            allowP2p = true
            customParameters = "{}"
        }

        // A video call whose camera permission was refused still connects --
        // as audio. The flag the engine acts on is the capture permission, not
        // the call's kind.
        val granted = TgCallsAdapter.buildMediaConfig(videoCall, ready, videoCaptureEnabled = true)
        assertTrue(granted.videoCaptureEnabled)
        assertEquals(55L, granted.callId)

        val refused = TgCallsAdapter.buildMediaConfig(videoCall, ready, videoCaptureEnabled = false)
        assertFalse(refused.videoCaptureEnabled)
    }
}
