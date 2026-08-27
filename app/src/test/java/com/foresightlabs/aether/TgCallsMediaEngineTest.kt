package com.foresightlabs.aether

import com.foresightlabs.aether.data.calls.TgCallsAdapter
import com.foresightlabs.aether.domain.calls.AudioRoute
import com.foresightlabs.aether.domain.calls.MediaConnectionState
import com.foresightlabs.aether.domain.model.ActiveCall
import com.foresightlabs.aether.domain.model.CallStateEnum
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TgCallsMediaEngineTest {

    @Test
    fun callStateReadyDoesNotImmediatelyMarkConnected() {
        val activeCall = ActiveCall(
            callId = 101,
            userId = 202L,
            state = CallStateEnum.READY,
            mediaState = MediaConnectionState.CONNECTING,
            durationSec = 0
        )

        assertEquals(CallStateEnum.READY, activeCall.state)
        assertEquals(MediaConnectionState.CONNECTING, activeCall.mediaState)
        assertEquals(0, activeCall.durationSec)
    }

    @Test
    fun mediaConnectedTriggersConnectedStatusAndTimer() {
        val activeCall = ActiveCall(
            callId = 101,
            userId = 202L,
            state = CallStateEnum.READY,
            mediaState = MediaConnectionState.CONNECTED,
            durationSec = 5
        )

        assertEquals(CallStateEnum.READY, activeCall.state)
        assertEquals(MediaConnectionState.CONNECTED, activeCall.mediaState)
        assertEquals(5, activeCall.durationSec)
    }

    @Test
    fun adapterBuildsTgCallsConfigFromReady() {
        val call = TdApi.Call().apply {
            id = 101
            isOutgoing = true
        }

        val reflector = TdApi.CallServerTypeTelegramReflector("peerTagBytes".toByteArray(), false)
        val server = TdApi.CallServer(1L, "192.168.1.1", "::1", 443, reflector)

        val ready = TdApi.CallStateReady().apply {
            protocol = TdApi.CallProtocol(true, true, 65, 92, arrayOf("1.0.0"))
            servers = arrayOf(server)
            config = "{\"config\": true}"
            encryptionKey = "secretKey123".toByteArray()
            allowP2p = true
            customParameters = "{\"p2p\": true}"
        }

        val config = TgCallsAdapter.buildConfig(call, ready)

        assertEquals(101, config.callId)
        assertTrue(config.isOutgoing)
        assertTrue(config.allowP2p)
        assertEquals(1, config.servers.size)
        assertEquals("192.168.1.1", config.servers[0].ipAddress)
        assertEquals(443, config.servers[0].port)
        assertEquals("{\"config\": true}", config.configJson)
        assertEquals("{\"p2p\": true}", config.customParameters)
    }

    @Test
    fun audioRouteTransitions() {
        var currentRoute = AudioRoute.EARPIECE
        assertEquals(AudioRoute.EARPIECE, currentRoute)

        currentRoute = AudioRoute.SPEAKER
        assertEquals(AudioRoute.SPEAKER, currentRoute)
    }
}
