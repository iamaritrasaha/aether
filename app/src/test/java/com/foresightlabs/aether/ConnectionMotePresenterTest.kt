package com.foresightlabs.aether

import com.foresightlabs.aether.domain.model.ConnectionStatus
import com.foresightlabs.aether.ui.design.AetherConnectionMoteState
import com.foresightlabs.aether.ui.design.ConnectionMoteMotion
import com.foresightlabs.aether.ui.design.ConnectionMotePresenter
import com.foresightlabs.aether.ui.design.toAetherConnectionMoteState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionMotePresenterTest {
    @Test
    fun rawStatesMapToTruthfulDisplayStates() {
        val expected = mapOf(
            ConnectionStatus.UNKNOWN to AetherConnectionMoteState.STARTING,
            ConnectionStatus.WAITING_FOR_NETWORK to AetherConnectionMoteState.OFFLINE,
            ConnectionStatus.CONNECTING to AetherConnectionMoteState.CONNECTING,
            ConnectionStatus.CONNECTING_PROXY to AetherConnectionMoteState.CONNECTING,
            ConnectionStatus.UPDATING to AetherConnectionMoteState.SYNCING,
            ConnectionStatus.READY to AetherConnectionMoteState.CONNECTED
        )

        expected.forEach { (raw, display) ->
            assertEquals(display, raw.toAetherConnectionMoteState())
        }
    }

    @Test
    fun connectingAndSyncingExpandOnlyAfterTheirShortStabilization() {
        val presenter = ConnectionMotePresenter()

        assertFalse(presenter.present(ConnectionStatus.CONNECTING, 0L).expanded)
        assertTrue(presenter.present(ConnectionStatus.CONNECTING, 500L).expanded)

        val syncing = ConnectionMotePresenter()
        assertFalse(syncing.present(ConnectionStatus.UPDATING, 0L).expanded)
        assertTrue(syncing.present(ConnectionStatus.UPDATING, 700L).expanded)
    }

    @Test
    fun quickConnectingReadyDoesNotFlashAnAcknowledgement() {
        val presenter = ConnectionMotePresenter()
        presenter.present(ConnectionStatus.CONNECTING, 0L)

        val ready = presenter.present(ConnectionStatus.READY, 120L)

        assertEquals(AetherConnectionMoteState.CONNECTED, ready.state)
        assertFalse(ready.expanded)
    }

    @Test
    fun persistentProblemAcknowledgesRecoveryThenCollapses() {
        val presenter = ConnectionMotePresenter()
        presenter.present(ConnectionStatus.WAITING_FOR_NETWORK, 0L)
        presenter.present(ConnectionStatus.WAITING_FOR_NETWORK, 400L)

        val acknowledged = presenter.present(ConnectionStatus.READY, 500L)
        assertTrue(acknowledged.expanded)
        assertEquals("Connected", acknowledged.label)

        val collapsed = presenter.present(ConnectionStatus.READY, 1_701L)
        assertFalse(collapsed.expanded)
    }

    @Test
    fun offlineRemainsExpandedAndConnectedTapTemporarilyExpands() {
        val presenter = ConnectionMotePresenter()
        assertFalse(presenter.present(ConnectionStatus.WAITING_FOR_NETWORK, 0L).expanded)
        assertTrue(presenter.present(ConnectionStatus.WAITING_FOR_NETWORK, 350L).expanded)
        assertTrue(presenter.present(ConnectionStatus.WAITING_FOR_NETWORK, 2_000L).expanded)

        val connected = ConnectionMotePresenter()
        connected.present(ConnectionStatus.READY, 0L)
        assertTrue(connected.tap(10L).expanded)
        assertFalse(connected.present(ConnectionStatus.READY, 2_011L).expanded)
    }

    @Test
    fun motionOnlyExistsForActiveConnectingOrSyncingState() {
        val presenter = ConnectionMotePresenter()

        assertEquals(ConnectionMoteMotion.STATIC, presenter.present(ConnectionStatus.UNKNOWN, 0L).motion)
        assertEquals(ConnectionMoteMotion.CONNECTING, presenter.present(ConnectionStatus.CONNECTING, 1L).motion)
        assertEquals(ConnectionMoteMotion.SYNCING, presenter.present(ConnectionStatus.UPDATING, 2L).motion)
        assertEquals(ConnectionMoteMotion.STATIC, presenter.present(ConnectionStatus.READY, 3L).motion)
    }
}
