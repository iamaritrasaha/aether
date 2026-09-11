package com.foresightlabs.aether.ui.home.atmosphere

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private const val TAU = (2.0 * PI).toFloat()

/** Depth layer of a mathematical curve or node. */
enum class GeometryDepth { DISTANT, MIDDLE, FOREGROUND }

/** The parametric equation a single curve is drawn from. */
enum class EquationFamily {
    CIRCLE_ORBIT,
    ELLIPSE,
    ROSE_CURVE,
    LISSAJOUS,
    TROCHOID
}

/**
 * A curated scene composition.
 *
 * These are families, not fixed drawings: each one fixes *what the scene is*
 * -- which equations appear, how they relate, what is deliberately left
 * incomplete -- while the parameters within it vary per seed. That is the line
 * between intentional and random. The previous system chose arbitrary points
 * and joined them, which is why it read as rough.
 */
enum class CompositionFamily {
    /** A large off-centre orbit, an intersecting ellipse, an outer guide arc. */
    ORBITAL_I,

    /** Two incomplete concentric rings and one tangent elliptical arc. */
    ORBITAL_II,

    /** A large partial rose curve inside an outer orbit. */
    ROSE_FIELD,

    /** A single restrained Lissajous figure anchored by a circle. */
    LISSAJOUS_FIELD,

    /** One elegant trochoid with a sparse orbital guide. */
    TROCHOID_FIELD,

    /** A large ellipse running off-frame, a small body, a tangent path. */
    ECLIPSE;

    /**
     * Whether this composition reads as strictly circular/orbital. Selection is
     * weighted toward these -- the geometry is meant to look like an instrument,
     * not a plot.
     */
    val isOrbital: Boolean
        get() = this == ORBITAL_I || this == ORBITAL_II || this == ECLIPSE || this == ROSE_FIELD
}

/** Evaluated point along a parametric mathematical equation. */
data class CurvePoint(
    val x: Float,
    val y: Float,
    val t: Float
)

/**
 * A parametric curve in world space, where both axes run about `-1..1` and a
 * circle is a circle. Screen mapping is [GeometryCamera]'s job alone.
 *
 * `tStart`/`tEnd` are how a curve is made deliberately incomplete: an arc that
 * stops short is a design decision expressed in the equation's own domain, not
 * a clipped drawing.
 */
sealed class MathematicalCurve {
    abstract val family: EquationFamily
    abstract val depth: GeometryDepth
    abstract val tStart: Float
    abstract val tEnd: Float
    abstract fun evaluate(t: Float): CurvePoint

    /** Sampled once and cached: nothing re-evaluates these per frame. */
    val samples: List<CurvePoint> by lazy {
        val count = SAMPLE_COUNT
        (0..count).map { i ->
            val t = tStart + (tEnd - tStart) * (i.toFloat() / count)
            evaluate(t)
        }
    }

    private companion object {
        const val SAMPLE_COUNT = 220
    }
}

/** $x = c_x + r\cos t,\; y = c_y + r\sin t$ */
class CircleOrbitCurve(
    val cx: Float,
    val cy: Float,
    val radius: Float,
    override val depth: GeometryDepth,
    override val tStart: Float = 0f,
    override val tEnd: Float = TAU
) : MathematicalCurve() {
    override val family: EquationFamily = EquationFamily.CIRCLE_ORBIT
    override fun evaluate(t: Float): CurvePoint =
        CurvePoint(cx + radius * cos(t), cy + radius * sin(t), t)
}

/** $x = c_x + a\cos t\cos\theta - b\sin t\sin\theta$, rotated by $\theta$. */
class EllipseCurve(
    val cx: Float,
    val cy: Float,
    val a: Float,
    val b: Float,
    val rotation: Float,
    override val depth: GeometryDepth,
    override val tStart: Float = 0f,
    override val tEnd: Float = TAU
) : MathematicalCurve() {
    override val family: EquationFamily = EquationFamily.ELLIPSE
    override fun evaluate(t: Float): CurvePoint {
        val x0 = a * cos(t)
        val y0 = b * sin(t)
        return CurvePoint(
            cx + x0 * cos(rotation) - y0 * sin(rotation),
            cy + x0 * sin(rotation) + y0 * cos(rotation),
            t
        )
    }
}

/** $r = a\cos(kt + \varphi)$, drawn as $(r\cos t, r\sin t)$. */
class RoseCurve(
    val cx: Float,
    val cy: Float,
    val a: Float,
    val k: Float,
    val phase: Float,
    override val depth: GeometryDepth,
    override val tStart: Float = 0f,
    override val tEnd: Float = TAU
) : MathematicalCurve() {
    override val family: EquationFamily = EquationFamily.ROSE_CURVE
    override fun evaluate(t: Float): CurvePoint {
        val r = a * cos(k * t + phase)
        return CurvePoint(cx + r * cos(t), cy + r * sin(t), t)
    }
}

/** $x = c_x + A\sin(at + \delta),\; y = c_y + B\sin(bt)$ */
class LissajousCurve(
    val cx: Float,
    val cy: Float,
    val amplitudeX: Float,
    val amplitudeY: Float,
    val freqX: Float,
    val freqY: Float,
    val delta: Float,
    override val depth: GeometryDepth,
    override val tStart: Float = 0f,
    override val tEnd: Float = TAU
) : MathematicalCurve() {
    override val family: EquationFamily = EquationFamily.LISSAJOUS
    override fun evaluate(t: Float): CurvePoint = CurvePoint(
        cx + amplitudeX * sin(freqX * t + delta),
        cy + amplitudeY * sin(freqY * t),
        t
    )
}

/** Epitrochoid / hypotrochoid, in their standard forms. */
class TrochoidCurve(
    val cx: Float,
    val cy: Float,
    val outerR: Float,
    val innerR: Float,
    val d: Float,
    val isEpitrochoid: Boolean,
    override val depth: GeometryDepth,
    override val tStart: Float = 0f,
    override val tEnd: Float = TAU
) : MathematicalCurve() {
    override val family: EquationFamily = EquationFamily.TROCHOID
    override fun evaluate(t: Float): CurvePoint {
        return if (isEpitrochoid) {
            val sum = outerR + innerR
            val k = sum / innerR
            CurvePoint(cx + sum * cos(t) - d * cos(k * t), cy + sum * sin(t) - d * sin(k * t), t)
        } else {
            val diff = outerR - innerR
            val k = diff / innerR
            CurvePoint(cx + diff * cos(t) + d * cos(k * t), cy + diff * sin(t) - d * sin(k * t), t)
        }
    }
}

/** Why a node exists. Nodes are never scattered: each one marks something real. */
enum class NodeOrigin { INTERSECTION, EXTREMUM, TANGENT, PHASE }

/** A node derived from the equations themselves, in world space. */
data class GeometryNode(
    val id: Int,
    val x: Float,
    val y: Float,
    val depth: GeometryDepth,
    val origin: NodeOrigin
) {
    /** Intersections are the structural nodes; the rest are quieter marks. */
    val isHub: Boolean get() = origin == NodeOrigin.INTERSECTION
}

/** A pulse travelling along one curve's parameter domain. */
data class EquationSignalPath(
    val curveIndex: Int,
    val tStart: Float,
    val tEnd: Float,
    val cyclePeriodMillis: Long,
    val phaseOffsetMillis: Long,
    val activeFraction: Float = 0.22f
)

/** Metadata describing the mathematical scene composition. */
data class EquationMetadata(
    val composition: CompositionFamily,
    val primaryFamily: EquationFamily,
    val description: String,
    val curveCount: Int,
    val nodeCount: Int
)

/**
 * One deterministic scene: curves, the nodes their equations imply, and the
 * signals that travel along them.
 */
class GeometryTopology(
    val composition: CompositionFamily,
    val primaryFamily: EquationFamily,
    val seed: Long,
    val curves: List<MathematicalCurve>,
    val nodes: List<GeometryNode>,
    val signalPaths: List<EquationSignalPath>,
    val metadata: EquationMetadata
) {
    val family: EquationFamily get() = primaryFamily

    companion object {
        private val cache = mutableMapOf<Long, GeometryTopology>()

        /** Strictly circular scenes are chosen this often. */
        const val ORBITAL_WEIGHT = 0.8f

        fun forSeed(seed: Long): GeometryTopology = synchronized(cache) {
            cache.getOrPut(seed) { build(seed) }
        }

        fun build(seed: Long): GeometryTopology {
            val random = Random(seed)
            val orbital = CompositionFamily.entries.filter { it.isOrbital }
            val other = CompositionFamily.entries.filterNot { it.isOrbital }
            val composition = if (random.nextFloat() < ORBITAL_WEIGHT) {
                orbital[random.nextInt(orbital.size)]
            } else {
                other[random.nextInt(other.size)]
            }
            return buildComposition(composition, seed, random)
        }

        internal fun buildComposition(
            composition: CompositionFamily,
            seed: Long,
            random: Random
        ): GeometryTopology {
            val curves = buildCurves(composition, random)
            val nodes = deriveNodes(curves)
            val signalPaths = buildSignalPaths(curves, random)
            val primary = curves.first().family

            return GeometryTopology(
                composition = composition,
                primaryFamily = primary,
                seed = seed,
                curves = curves,
                nodes = nodes,
                signalPaths = signalPaths,
                metadata = EquationMetadata(
                    composition = composition,
                    primaryFamily = primary,
                    description = composition.name,
                    curveCount = curves.size,
                    nodeCount = nodes.size
                )
            )
        }

        /**
         * The world radius the compositions are authored against.
         *
         * Sized so a scene spans most of a tall conversation canvas at the
         * conversation camera, and overruns a short hero at the home camera --
         * which is what makes Home read as a crop of something larger instead of
         * a diagram shrunk to fit.
         */
        internal const val SCENE = 1.45f

        /** A fraction of a full turn, as a parameter range starting at [from]. */
        private fun arc(from: Float, turns: Float): Pair<Float, Float> = from to (from + TAU * turns)

        private fun buildCurves(
            composition: CompositionFamily,
            random: Random
        ): List<MathematicalCurve> {
            // Scene centre wanders a little, but always stays near the origin so
            // the camera framings stay meaningful.
            val cx = (random.nextFloat() - 0.5f) * 0.30f * SCENE
            val cy = (random.nextFloat() - 0.5f) * 0.24f * SCENE
            val spin = random.nextFloat() * TAU

            return when (composition) {
                CompositionFamily.ORBITAL_I -> {
                    val r = (0.62f + random.nextFloat() * 0.18f) * SCENE
                    val (s1, e1) = arc(spin, 0.82f)
                    listOf(
                        CircleOrbitCurve(cx, cy, r, GeometryDepth.FOREGROUND, s1, e1),
                        EllipseCurve(
                            cx = cx + r * 0.34f,
                            cy = cy - r * 0.20f,
                            a = r * 0.74f,
                            b = r * 0.42f,
                            rotation = spin * 0.5f + 0.6f,
                            depth = GeometryDepth.MIDDLE
                        ),
                        CircleOrbitCurve(
                            cx, cy, r * 1.42f, GeometryDepth.DISTANT,
                            spin + 1.1f, spin + 1.1f + TAU * 0.55f
                        )
                    )
                }

                CompositionFamily.ORBITAL_II -> {
                    val r = (0.48f + random.nextFloat() * 0.14f) * SCENE
                    val (s1, e1) = arc(spin, 0.66f)
                    val (s2, e2) = arc(spin + 2.2f, 0.78f)
                    listOf(
                        CircleOrbitCurve(cx, cy, r, GeometryDepth.FOREGROUND, s1, e1),
                        CircleOrbitCurve(
                            cx + 0.06f * SCENE, cy + 0.04f * SCENE, r * 1.46f, GeometryDepth.MIDDLE, s2, e2
                        ),
                        // Tangent elliptical arc, sitting against the outer ring.
                        EllipseCurve(
                            cx = cx - r * 0.9f,
                            cy = cy + r * 0.55f,
                            a = r * 1.15f,
                            b = r * 0.5f,
                            rotation = -0.7f,
                            depth = GeometryDepth.DISTANT,
                            tStart = spin,
                            tEnd = spin + TAU * 0.45f
                        )
                    )
                }

                CompositionFamily.ROSE_FIELD -> {
                    // Few petals, and drawn complete: a rose truncated mid-petal
                    // reads as an unfinished line rather than a deliberate arc,
                    // so incompleteness is expressed by the orbit around it.
                    val k = listOf(2f, 3f, 4f)[random.nextInt(3)]
                    val a = (0.34f + random.nextFloat() * 0.10f) * SCENE
                    listOf(
                        RoseCurve(cx, cy, a, k, spin, GeometryDepth.FOREGROUND),
                        CircleOrbitCurve(
                            cx, cy, a * 2.05f, GeometryDepth.DISTANT,
                            spin + 0.8f, spin + 0.8f + TAU * 0.62f
                        )
                    )
                }

                CompositionFamily.LISSAJOUS_FIELD -> {
                    val (fx, fy) = listOf(1f to 2f, 2f to 3f, 3f to 4f, 3f to 2f)[random.nextInt(4)]
                    val ampX = (0.58f + random.nextFloat() * 0.14f) * SCENE
                    val ampY = ampX * (0.66f + random.nextFloat() * 0.26f)
                    listOf(
                        LissajousCurve(cx, cy, ampX, ampY, fx, fy, random.nextFloat() * 1.57f, GeometryDepth.FOREGROUND),
                        CircleOrbitCurve(
                            cx, cy, maxOf(ampX, ampY) * 1.24f, GeometryDepth.DISTANT,
                            spin, spin + TAU * 0.5f
                        )
                    )
                }

                CompositionFamily.TROCHOID_FIELD -> {
                    val outer = (0.42f + random.nextFloat() * 0.10f) * SCENE
                    val ratio = listOf(3f, 4f, 5f)[random.nextInt(3)]
                    val inner = outer / ratio
                    val d = inner * (0.85f + random.nextFloat() * 0.35f)
                    val epi = random.nextBoolean()
                    listOf(
                        TrochoidCurve(cx, cy, outer, inner, d, epi, GeometryDepth.FOREGROUND),
                        CircleOrbitCurve(
                            cx, cy, (if (epi) outer + inner + d else outer) * 1.18f,
                            GeometryDepth.DISTANT, spin, spin + TAU * 0.4f
                        )
                    )
                }

                CompositionFamily.ECLIPSE -> {
                    // Deliberately larger than the frame: the camera crops it,
                    // which is the intended reading.
                    val a = (0.72f + random.nextFloat() * 0.17f) * SCENE
                    val b = a * (0.62f + random.nextFloat() * 0.18f)
                    val bodyR = (0.14f + random.nextFloat() * 0.05f) * SCENE
                    listOf(
                        EllipseCurve(cx - 0.25f * SCENE, cy, a, b, spin * 0.25f, GeometryDepth.FOREGROUND),
                        CircleOrbitCurve(cx + a * 0.42f, cy - b * 0.34f, bodyR, GeometryDepth.MIDDLE),
                        // Tangent path the small body appears to travel.
                        EllipseCurve(
                            cx = cx - 0.25f * SCENE,
                            cy = cy,
                            a = a * 0.72f,
                            b = b * 1.18f,
                            rotation = spin * 0.25f + 1.1f,
                            depth = GeometryDepth.DISTANT,
                            tStart = spin,
                            tEnd = spin + TAU * 0.58f
                        )
                    )
                }
            }
        }

        /**
         * Nodes come from the equations, never from a scatter: where two curves
         * actually meet, where a curve reaches an extreme of its own domain, and
         * where an arc ends. Kept deliberately sparse.
         */
        private fun deriveNodes(curves: List<MathematicalCurve>): List<GeometryNode> {
            val nodes = mutableListOf<GeometryNode>()
            var nextId = 0

            fun add(x: Float, y: Float, depth: GeometryDepth, origin: NodeOrigin) {
                if (nodes.any { hypot(it.x - x, it.y - y) < MIN_NODE_SEPARATION }) return
                nodes += GeometryNode(nextId++, x, y, depth, origin)
            }

            // Intersections: the structural nodes.
            for (i in curves.indices) {
                for (j in i + 1 until curves.size) {
                    val a = curves[i].samples
                    val b = curves[j].samples
                    var best: Pair<CurvePoint, CurvePoint>? = null
                    var bestDistance = INTERSECTION_TOLERANCE
                    for (pa in a) {
                        for (pb in b) {
                            val d = hypot(pa.x - pb.x, pa.y - pb.y)
                            if (d < bestDistance) {
                                bestDistance = d
                                best = pa to pb
                            }
                        }
                    }
                    best?.let { (pa, pb) ->
                        add((pa.x + pb.x) / 2f, (pa.y + pb.y) / 2f, curves[i].depth, NodeOrigin.INTERSECTION)
                    }
                }
            }

            // Where a deliberately incomplete arc stops. These sit on the arcs
            // themselves, so they are the nodes most likely to be in frame --
            // added before extrema, which by definition sit at the scene's
            // outer edge and are often outside the camera's crop.
            for (curve in curves) {
                if (curve.tEnd - curve.tStart >= TAU - 0.01f) continue
                curve.samples.firstOrNull()?.let { add(it.x, it.y, curve.depth, NodeOrigin.TANGENT) }
                curve.samples.lastOrNull()?.let { add(it.x, it.y, curve.depth, NodeOrigin.TANGENT) }
            }

            // One extremum per curve: its furthest point from the scene origin.
            for (curve in curves) {
                val extremum = curve.samples.maxByOrNull { hypot(it.x, it.y) } ?: continue
                add(extremum.x, extremum.y, curve.depth, NodeOrigin.EXTREMUM)
            }

            return nodes.take(MAX_NODES)
        }

        private const val MIN_NODE_SEPARATION = 0.18f
        private const val INTERSECTION_TOLERANCE = 0.07f
        private const val MAX_NODES = 6

        /**
         * Signals travel a curve's own parameter domain, so a pulse always
         * follows the equation rather than cutting across the scene.
         */
        private fun buildSignalPaths(
            curves: List<MathematicalCurve>,
            random: Random
        ): List<EquationSignalPath> {
            return curves.indices.map { index ->
                val curve = curves[index]
                val span = curve.tEnd - curve.tStart
                val start = curve.tStart + random.nextFloat() * span * 0.25f
                val end = (start + span * (0.55f + random.nextFloat() * 0.35f)).coerceAtMost(curve.tEnd)
                EquationSignalPath(
                    curveIndex = index,
                    tStart = start,
                    tEnd = end,
                    cyclePeriodMillis = 6_400L + index * 1_500L,
                    phaseOffsetMillis = index * 2_100L,
                    activeFraction = 0.26f
                )
            }
        }
    }
}

/** Evaluated state of a signal travelling along an equation parameter. */
data class EquationSignalState(
    val pathIndex: Int,
    val curveIndex: Int,
    val currentPoint: CurvePoint,
    val progress: Float
) {
    /**
     * Brightness envelope: a pulse blooms as it arrives and fades as it leaves,
     * rather than the whole line flashing.
     */
    val intensity: Float
        get() = sin(progress.coerceIn(0f, 1f) * PI.toFloat())
}

/** Scheduler that evaluates signals travelling along equation parameters. */
object GeometrySignalScheduler {
    fun activeSignals(topology: GeometryTopology, elapsedMillis: Long): List<EquationSignalState> {
        if (elapsedMillis < 0L) return emptyList()
        val result = mutableListOf<EquationSignalState>()

        topology.signalPaths.forEachIndexed { index, path ->
            val cyclePosition = (elapsedMillis + path.phaseOffsetMillis) % path.cyclePeriodMillis
            val cycleFraction = cyclePosition.toFloat() / path.cyclePeriodMillis.toFloat()
            if (cycleFraction <= path.activeFraction) {
                val progress = (cycleFraction / path.activeFraction).coerceIn(0f, 1f)
                val t = path.tStart + progress * (path.tEnd - path.tStart)
                val curve = topology.curves.getOrNull(path.curveIndex) ?: return@forEachIndexed
                result += EquationSignalState(
                    pathIndex = index,
                    curveIndex = path.curveIndex,
                    currentPoint = curve.evaluate(t),
                    progress = progress
                )
            }
        }
        return result
    }
}
