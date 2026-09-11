package com.foresightlabs.aether.ui.home.atmosphere

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AetherTimeAtmosphereOfflineTest {

    private val seed = 20260908L

    @Test
    fun topologyIdentityIsIdenticalWhenOffline() {
        val onlineTopo = GeometryTopology.forSeed(seed)
        val offlineTopo = GeometryTopology.forSeed(seed)

        assertEquals(onlineTopo.family, offlineTopo.family)
        assertEquals(onlineTopo.primaryFamily, offlineTopo.primaryFamily)
        assertEquals(onlineTopo.metadata, offlineTopo.metadata)
        assertEquals(onlineTopo.curves.size, offlineTopo.curves.size)
        assertEquals(onlineTopo.nodes, offlineTopo.nodes)
        assertEquals(onlineTopo.signalPaths, offlineTopo.signalPaths)
    }

    @Test
    fun signalSchedulerInvariantsHoldDuringOnline() {
        val topo = GeometryTopology.forSeed(seed)
        val active = GeometrySignalScheduler.activeSignals(topo, 500L)
        assertTrue(active.isNotEmpty())
        for (signal in active) {
            assertTrue(signal.progress in 0f..1f)
            assertTrue(signal.currentPoint.x.isFinite())
            assertTrue(signal.currentPoint.y.isFinite())
        }
    }

    @Test
    fun negativeElapsedTimeProducesNoSignals() {
        val topo = GeometryTopology.forSeed(seed)
        val active = GeometrySignalScheduler.activeSignals(topo, -100L)
        assertTrue(active.isEmpty())
    }
}
