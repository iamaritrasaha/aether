package com.foresightlabs.aether.ui.home.atmosphere

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The geometry is a designed mathematical system, not a generated one.
 *
 * The previous system produced arbitrary graph edges between arbitrary nodes,
 * which is why it read as rough. These tests hold the properties that make the
 * replacement deliberate: every visible line comes from an equation, every node
 * marks something that equation actually does, and a seed always yields the
 * same curated composition.
 */
class GeometryTopologyTest {

    private val seed = 20260911L

    @Test
    fun topologyIsDeterministicForTheSameSeed() {
        val a = GeometryTopology.build(seed)
        val b = GeometryTopology.build(seed)

        assertEquals(a.composition, b.composition)
        assertEquals(a.curves.size, b.curves.size)
        a.curves.zip(b.curves).forEach { (ca, cb) ->
            assertEquals(ca.family, cb.family)
            assertEquals(ca.tStart, cb.tStart, 1e-6f)
            assertEquals(ca.tEnd, cb.tEnd, 1e-6f)
            ca.samples.zip(cb.samples).forEach { (pa, pb) ->
                assertEquals(pa.x, pb.x, 1e-6f)
                assertEquals(pa.y, pb.y, 1e-6f)
            }
        }
    }

    @Test
    fun everyCurveHasAnEquationFamilyAndAParameterDomain() {
        for (s in 0L until 60L) {
            val topology = GeometryTopology.build(s)
            assertTrue("a composition must contain curves", topology.curves.isNotEmpty())
            for (curve in topology.curves) {
                assertNotNull(curve.family)
                assertTrue("t domain must be non-empty", curve.tEnd > curve.tStart)
                assertTrue("samples come from the equation", curve.samples.size > 2)
            }
            assertEquals(topology.curves.size, topology.metadata.curveCount)
            assertEquals(topology.composition, topology.metadata.composition)
        }
    }

    @Test
    fun everyCompositionFamilyIsReachableAndBuildable() {
        for (family in CompositionFamily.entries) {
            val topology = GeometryTopology.buildComposition(family, seed, Random(seed))
            assertEquals(family, topology.composition)
            assertTrue(topology.curves.isNotEmpty())
            assertTrue(topology.signalPaths.isNotEmpty())
        }
    }

    /**
     * Round, orbital scenes are what this geometry is for. Selection is weighted
     * toward them rather than picking uniformly from every family.
     */
    @Test
    fun compositionSelectionIsWeightedTowardOrbitalScenes() {
        val sampled = (0L until 400L).map { GeometryTopology.build(it).composition }
        val orbitalShare = sampled.count { it.isOrbital }.toFloat() / sampled.size
        assertTrue(
            "orbital share was $orbitalShare, expected near ${GeometryTopology.ORBITAL_WEIGHT}",
            orbitalShare > 0.70f
        )
    }

    @Test
    fun differentSeedsReachDifferentCompositions() {
        val distinct = (0L until 200L).map { GeometryTopology.build(it).composition }.toSet()
        assertTrue("expected several curated compositions, got $distinct", distinct.size >= 3)
    }

    /**
     * Nodes are derived, never scattered: each one sits on a curve the scene
     * actually contains (or, for an intersection, midway between two samples of
     * the curves that meet).
     */
    @Test
    fun nodesSitOnTheCurvesTheyWereDerivedFrom() {
        for (s in 0L until 40L) {
            val topology = GeometryTopology.build(s)
            for (node in topology.nodes) {
                val nearest = topology.curves
                    .flatMap { it.samples }
                    .minOf { hypot(it.x - node.x, it.y - node.y) }
                assertTrue(
                    "node ${node.id} (${node.origin}) was ${nearest} from any curve",
                    nearest < 0.06f
                )
            }
        }
    }

    @Test
    fun nodesStaySparse() {
        for (s in 0L until 60L) {
            val topology = GeometryTopology.build(s)
            assertTrue("scattered nodes: ${topology.nodes.size}", topology.nodes.size <= 6)
        }
    }

    /** A signal always evaluates to a point on its own curve's parameterisation. */
    @Test
    fun signalsFollowTheirCurvesParameterisation() {
        val topology = GeometryTopology.build(seed)
        for (elapsed in 0L..20_000L step 137L) {
            for (signal in GeometrySignalScheduler.activeSignals(topology, elapsed)) {
                val curve = topology.curves[signal.curveIndex]
                val path = topology.signalPaths[signal.pathIndex]
                val t = path.tStart + signal.progress * (path.tEnd - path.tStart)
                val expected = curve.evaluate(t)
                assertEquals(expected.x, signal.currentPoint.x, 1e-4f)
                assertEquals(expected.y, signal.currentPoint.y, 1e-4f)
                assertTrue(t >= curve.tStart - 1e-4f && t <= curve.tEnd + 1e-4f)
            }
        }
    }

    /** A pulse blooms and fades; it never simply lights a whole line. */
    @Test
    fun signalIntensityRisesAndFallsAcrossItsTravel() {
        val topology = GeometryTopology.build(seed)
        val path = topology.signalPaths.first()
        val mid = EquationSignalState(0, path.curveIndex, topology.curves[path.curveIndex].evaluate(path.tStart), 0.5f)
        val start = EquationSignalState(0, path.curveIndex, topology.curves[path.curveIndex].evaluate(path.tStart), 0f)
        val end = EquationSignalState(0, path.curveIndex, topology.curves[path.curveIndex].evaluate(path.tEnd), 1f)

        assertTrue(mid.intensity > start.intensity)
        assertTrue(mid.intensity > end.intensity)
        assertTrue(abs(start.intensity) < 0.01f)
        assertTrue(abs(end.intensity) < 0.01f)
    }

    @Test
    fun negativeElapsedTimeProducesNoSignals() {
        val topology = GeometryTopology.build(seed)
        assertTrue(GeometrySignalScheduler.activeSignals(topology, -1L).isEmpty())
    }

    @Test
    fun sameInstantAlwaysProducesTheSameSignals() {
        val topology = GeometryTopology.build(seed)
        val first = GeometrySignalScheduler.activeSignals(topology, 4_200L)
        val second = GeometrySignalScheduler.activeSignals(topology, 4_200L)
        assertEquals(first.size, second.size)
        first.zip(second).forEach { (a, b) ->
            assertEquals(a.currentPoint.x, b.currentPoint.x, 1e-6f)
            assertEquals(a.progress, b.progress, 1e-6f)
        }
    }

    @Test
    fun forSeedCachesRatherThanRebuilding() {
        assertSame(GeometryTopology.forSeed(seed), GeometryTopology.forSeed(seed))
    }

    /**
     * The scene is authored in a world where both axes mean the same thing.
     * Nothing may be authored in screen or fraction-of-height space, because
     * that is what let the old renderer stretch it.
     */
    @Test
    fun geometryIsAuthoredInASymmetricWorldSpace() {
        for (s in 0L until 60L) {
            val topology = GeometryTopology.build(s)
            val points = topology.curves.flatMap { it.samples }
            val maxRadius = points.maxOf { hypot(it.x, it.y) }
            assertTrue("scene radius $maxRadius is implausible for world space", maxRadius < 2.5f)
            // A world authored in 0..1 (the old fraction-of-height space) would
            // never produce negative coordinates.
            assertTrue("world space should straddle the origin", points.any { it.x < 0f } || points.any { it.y < 0f })
        }
    }
}
