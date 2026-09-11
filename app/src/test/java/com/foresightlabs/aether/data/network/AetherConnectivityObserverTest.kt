package com.foresightlabs.aether.data.network

import com.foresightlabs.aether.domain.model.ConnectionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class AetherConnectivityObserverTest {

    @Test
    fun unvalidatedAndroidNetworkResolvesToOffline() {
        val state = AetherConnectivityObserver.resolveState(
            isAndroidOk = false,
            tdlibStatus = ConnectionStatus.READY
        )
        assertEquals(AetherConnectivityState.OFFLINE, state)
    }

    @Test
    fun waitingForNetworkResolvesToOffline() {
        val state = AetherConnectivityObserver.resolveState(
            isAndroidOk = true,
            tdlibStatus = ConnectionStatus.WAITING_FOR_NETWORK
        )
        assertEquals(AetherConnectivityState.OFFLINE, state)
    }

    @Test
    fun validatedAndroidNetworkAndReadyTdlibResolvesToOnline() {
        val state = AetherConnectivityObserver.resolveState(
            isAndroidOk = true,
            tdlibStatus = ConnectionStatus.READY
        )
        assertEquals(AetherConnectivityState.ONLINE, state)
    }

    @Test
    fun validatedAndroidNetworkAndConnectingTdlibResolvesToReconnecting() {
        val state = AetherConnectivityObserver.resolveState(
            isAndroidOk = true,
            tdlibStatus = ConnectionStatus.CONNECTING
        )
        assertEquals(AetherConnectivityState.RECONNECTING, state)
    }

    @Test
    fun validatedAndroidNetworkAndUpdatingTdlibResolvesToReconnecting() {
        val state = AetherConnectivityObserver.resolveState(
            isAndroidOk = true,
            tdlibStatus = ConnectionStatus.UPDATING
        )
        assertEquals(AetherConnectivityState.RECONNECTING, state)
    }
}
