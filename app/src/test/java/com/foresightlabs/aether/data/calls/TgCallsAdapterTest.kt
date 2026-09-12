package com.foresightlabs.aether.data.calls

import com.foresightlabs.aether.calls.media.CallProtocolInfo
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Physically reproduced on hardware as ntgcalls' own `reflector_port.cpp`:
 * "Allocation can't be started without setting the peer tag." Root cause,
 * proven against the pinned rc02 source, not guessed:
 *
 * - `ntgcalls::p2p::RTCServer::peer_tag` is `std::optional<bytes::binary>`
 *   (`ntgcalls/include/ntgcalls/p2p/rtc_server.hpp`).
 * - Its JNI binding's `parseOptional` (`targets/android/app/src/main/jni/
 *   utils.hpp.tpl`) converts a Java field to `std::nullopt` only when that
 *   field is literally `null` -- an empty-but-non-null `byte[]` still
 *   becomes a *present* optional.
 * - `RTCServer::to_rtc_servers()` (`ntgcalls/src/p2p/rtc_server.cpp`)
 *   branches on `if (server.peer_tag)`: present means "Telegram reflector",
 *   building a `push_phone` entry (`login = "reflector"`) instead of a real
 *   STUN/TURN entry.
 *
 * So a `CallServerEndpoint.peerTag` that defaults to (or is coerced to) an
 * empty `ByteArray` for a genuine WebRTC/STUN/TURN server silently
 * misclassifies it as a reflector. These tests prove `TgCallsAdapter` never
 * does that: absence reaches [CallServerEndpoint] as a real `null`.
 */
class TgCallsAdapterTest {

    private fun readyWith(servers: List<TdApi.CallServer>): TdApi.CallStateReady = TdApi.CallStateReady(
        TdApi.CallProtocol(true, true, 65, 92, arrayOf("1.0.0")),
        servers.toTypedArray(),
        "{}",
        ByteArray(32),
        arrayOf("a", "b", "c", "d"),
        true,
        false,
        "{}"
    )

    private fun call(isOutgoing: Boolean = true) =
        TdApi.Call(1, 0L, 42L, isOutgoing, false, TdApi.CallStatePending())

    @Test
    fun telegramReflectorServer_peerTagPresentAndWebRtcFieldsNotSynthesized() {
        val peerTagBytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val server = TdApi.CallServer(
            7L, "149.154.167.50", "2001:67c:4e8:f004::9", 443,
            TdApi.CallServerTypeTelegramReflector(peerTagBytes, true)
        )

        val config = TgCallsAdapter.buildMediaConfig(call(), readyWith(listOf(server)))
        val endpoint = config.servers.single()

        assertTrue("Telegram reflector must carry a non-null peerTag", endpoint.peerTag != null)
        assertEquals("peerTag length must be preserved exactly, no truncation/padding", 8, endpoint.peerTag!!.size)
        assertArrayEquals(peerTagBytes, endpoint.peerTag)
        assertTrue("isTcp must be preserved from CallServerTypeTelegramReflector", endpoint.isTcp)
        assertEquals("WebRTC username must never be synthesized for a reflector server", "", endpoint.username)
        assertEquals("WebRTC password must never be synthesized for a reflector server", "", endpoint.password)
        assertFalse("supportsTurn must never be synthesized for a reflector server", endpoint.supportsTurn)
        assertFalse("supportsStun must never be synthesized for a reflector server", endpoint.supportsStun)
    }

    @Test
    fun webrtcServer_peerTagIsNullNotEmptyArray() {
        val server = TdApi.CallServer(
            9L, "8.8.8.8", "2001:4860:4860::8888", 3478,
            TdApi.CallServerTypeWebrtc("stun-user", "stun-pass", true, true)
        )

        val config = TgCallsAdapter.buildMediaConfig(call(), readyWith(listOf(server)))
        val endpoint = config.servers.single()

        assertNull("A WebRTC/STUN/TURN server's peerTag must be null, never a synthesized empty array -- see class doc for exactly why", endpoint.peerTag)
        assertEquals("stun-user", endpoint.username)
        assertEquals("stun-pass", endpoint.password)
        assertTrue(endpoint.supportsTurn)
        assertTrue(endpoint.supportsStun)
        assertFalse("A WebRTC server is never TCP-reflector-flagged", endpoint.isTcp)
    }

    @Test
    fun mixedServerList_idsAddressesAndPortsUnchangedForBothTypes() {
        val reflector = TdApi.CallServer(
            1L, "1.1.1.1", "::1", 1000,
            TdApi.CallServerTypeTelegramReflector(byteArrayOf(9, 9), false)
        )
        val webrtc = TdApi.CallServer(
            2L, "2.2.2.2", "::2", 2000,
            TdApi.CallServerTypeWebrtc("u", "p", true, false)
        )

        val config = TgCallsAdapter.buildMediaConfig(call(), readyWith(listOf(reflector, webrtc)))

        assertEquals(2, config.servers.size)
        val mappedReflector = config.servers.first { it.id == 1L }
        val mappedWebrtc = config.servers.first { it.id == 2L }

        assertEquals("1.1.1.1", mappedReflector.ipAddress)
        assertEquals("::1", mappedReflector.ipv6Address)
        assertEquals(1000, mappedReflector.port)
        assertTrue(mappedReflector.peerTag != null)

        assertEquals("2.2.2.2", mappedWebrtc.ipAddress)
        assertEquals("::2", mappedWebrtc.ipv6Address)
        assertEquals(2000, mappedWebrtc.port)
        assertNull(mappedWebrtc.peerTag)
    }

    @Test
    fun callProtocolBuild_fallsBackToConservativeDefaultsWhenNoSupportedProtocol() {
        val protocol = TgCallsAdapter.buildCallProtocol(null)

        assertTrue(protocol.udpP2p)
        assertTrue(protocol.udpReflector)
        assertEquals(65, protocol.minLayer)
        assertEquals(92, protocol.maxLayer)
    }

    @Test
    fun callProtocolBuild_usesSupportedProtocolWhenAvailable() {
        val supported = CallProtocolInfo(minLayer = 70, maxLayer = 95, udpP2p = false, udpReflector = true, libraryVersions = listOf("2.0.0"))

        val protocol = TgCallsAdapter.buildCallProtocol(supported)

        assertFalse(protocol.udpP2p)
        assertTrue(protocol.udpReflector)
        assertEquals(70, protocol.minLayer)
        assertEquals(95, protocol.maxLayer)
        assertArrayEquals(arrayOf("2.0.0"), protocol.libraryVersions)
    }
}
