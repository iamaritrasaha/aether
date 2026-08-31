package com.foresightlabs.aether.ui.home.atmosphere

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.foresightlabs.aether.ui.design.AetherFloatingHeaderDefaults
import com.foresightlabs.aether.ui.design.AetherFrostState
import com.foresightlabs.aether.ui.design.aetherFrostSource
import com.foresightlabs.aether.ui.design.isReducedMotionEnabled
import kotlinx.coroutines.delay
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.sin

/**
 * How expressively [AetherTimeAtmosphere] renders the shared canonical time-of-day
 * state. Both values read the exact same [TimeAtmosphere] -- same palette, same
 * period, same progress -- so Home and Conversation are always the same moment in
 * the same environment; only how loudly that moment is painted differs.
 */
enum class AtmosphereExpression {
    /** Full expression: the strongest visual presence, on Home. */
    HOME,

    /** The same environment, entered more deeply: quieter, so messages stay legible. */
    CONVERSATION
}

/**
 * The canonical time-of-day atmosphere layer, shared by Home and Conversation.
 *
 * Renders a purely time-of-day-based generative geometry atmosphere that interpolates smoothly across:
 * PRE_DAWN -> MORNING -> NOON -> AFTERNOON -> EVENING -> NIGHT -> PRE_DAWN.
 *
 * Features an Aether-specific visual language based on three depth layers of slowly moving geometric
 * trajectories and architectural line structures (distant, middle, foreground). [expression] scales
 * the geometry's density, opacity, glow and motion speed without ever touching the underlying
 * [timeAtmosphere] state or its palette -- Home and Conversation stay two views of one environment.
 *
 * Performance isolated:
 * - Time parameters sampled periodically (30s timer & lifecycle boundaries) without per-frame recomposition.
 * - Animation drift isolated to the draw phase inside Canvas with zero per-frame heap object allocations.
 */
@Composable
fun AetherTimeAtmosphere(
    modifier: Modifier = Modifier,
    heroFraction: Float = 1f,
    enableAmbientMotion: Boolean = true,
    expression: AtmosphereExpression = AtmosphereExpression.HOME,
    frostState: AetherFrostState? = null,
    timeAtmosphere: TimeAtmosphere = rememberCurrentTimeAtmosphere()
) {
    val context = LocalContext.current
    val reducedMotion = remember(context) { isReducedMotionEnabled(context) }
    val isInspection = LocalInspectionMode.current

    // Same palette and period, quieter geometry -- never a second atmosphere system.
    val effectiveAtmosphere = remember(timeAtmosphere, expression) {
        timeAtmosphere.scaledFor(expression)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "time_atmosphere_geometry_drift")
    val driftPhase by if (enableAmbientMotion && !reducedMotion && !isInspection) {
        val durationMillis = (24000 / effectiveAtmosphere.ambientMotionSpeed.coerceAtLeast(0.1f)).toInt()
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 6.28318530718f, // 2 * PI
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "time_atmosphere_drift_phase"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    // Preallocated Path & PathMeasure objects to ensure zero per-frame heap allocations during drawing
    val paths = remember { Array(TRAJECTORY_COUNT) { Path() } }
    val pathMeasures = remember { Array(TRAJECTORY_COUNT) { PathMeasure() } }
    val trimmedPaths = remember { Array(TRAJECTORY_COUNT) { Path() } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(if (frostState != null) Modifier.aetherFrostSource(frostState) else Modifier)
            .background(timeAtmosphere.shadowColor)
            .testTag("aether_time_atmosphere")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            if (width <= 0f || height <= 0f) return@Canvas

            val luminousHeight = height * heroFraction.coerceIn(0.15f, 1f)

            // Very dark graphite/near-black base background gradient
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        timeAtmosphere.colors.first(),
                        timeAtmosphere.colors.getOrElse(2) { timeAtmosphere.shadowColor },
                        timeAtmosphere.shadowColor
                    ),
                    startY = 0f,
                    endY = luminousHeight
                ),
                size = size
            )

            // Restrained ambient glow bloom in upper background
            val glowCenter = Offset(
                width * (0.30f + sin(driftPhase) * 0.03f),
                luminousHeight * (0.20f + cos(driftPhase * 0.7f) * 0.02f)
            )
            val glowRadius = width * 0.85f
            val glowAlpha = (effectiveAtmosphere.glowIntensity * 0.85f).coerceIn(0.05f, 0.45f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        timeAtmosphere.glowColor.copy(alpha = glowAlpha),
                        timeAtmosphere.colors.getOrElse(1) { timeAtmosphere.primaryAccent }.copy(alpha = glowAlpha * 0.35f),
                        Color.Transparent
                    ),
                    center = glowCenter,
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = glowCenter
            )

            // Render Generative Geometry Trajectories across Distant, Middle, and Foreground layers
            drawGenerativeGeometry(
                width = width,
                height = luminousHeight,
                driftPhase = driftPhase,
                atmosphere = effectiveAtmosphere,
                paths = paths,
                pathMeasures = pathMeasures,
                trimmedPaths = trimmedPaths
            )

            // Contrast bed for floating headers/controls
            drawAtmosphereContrastBed()
        }
    }
}

// Conversation reads as the same environment, entered more deeply: quieter on
// every axis so message readability always wins, never a second atmosphere.
internal const val ConversationDensityScale = 0.55f
internal const val ConversationOpacityScale = 0.55f
internal const val ConversationIntensityScale = 0.60f
internal const val ConversationMotionScale = 0.55f

/**
 * Applies [expression]'s intensity to this canonical, time-derived state without
 * changing the period, progress or palette -- Home and Conversation always share
 * the exact same moment; only the loudness of its geometry differs.
 */
internal fun TimeAtmosphere.scaledFor(expression: AtmosphereExpression): TimeAtmosphere {
    if (expression == AtmosphereExpression.HOME) return this
    return copy(
        glowIntensity = glowIntensity * ConversationIntensityScale,
        ambientMotionSpeed = ambientMotionSpeed * ConversationMotionScale,
        lineDensity = lineDensity * ConversationDensityScale,
        lineOpacity = lineOpacity * ConversationOpacityScale
    )
}

private const val TRAJECTORY_COUNT = 12

// Predefined deterministic trajectory definitions (normalized 0..1 coordinates)
private val TRAJECTORY_SEEDS = arrayOf(
    // DISTANT LAYER (0..3) - Extremely faint, slow, broad trajectories
    TrajectorySeed(0.05f, 0.12f, 0.35f, 0.02f, 0.70f, 0.28f, 0.95f, 0.18f, LayerDepth.DISTANT),
    TrajectorySeed(-0.10f, 0.45f, 0.25f, 0.22f, 0.65f, 0.58f, 1.10f, 0.80f, LayerDepth.DISTANT),
    TrajectorySeed(0.15f, -0.05f, 0.40f, 0.25f, 0.60f, 0.12f, 0.88f, 0.35f, LayerDepth.DISTANT),
    TrajectorySeed(0.02f, 0.75f, 0.38f, 0.55f, 0.72f, 0.85f, 0.98f, 0.65f, LayerDepth.DISTANT),

    // MIDDLE LAYER (4..8) - Primary flowing geometric paths
    TrajectorySeed(-0.05f, 0.18f, 0.28f, 0.08f, 0.68f, 0.42f, 0.92f, 0.62f, LayerDepth.MIDDLE),
    TrajectorySeed(0.12f, 0.82f, 0.32f, 0.48f, 0.75f, 0.18f, 0.98f, 0.08f, LayerDepth.MIDDLE),
    TrajectorySeed(-0.08f, 0.35f, 0.22f, 0.62f, 0.62f, 0.15f, 1.05f, 0.42f, LayerDepth.MIDDLE),
    TrajectorySeed(0.18f, 0.02f, 0.48f, 0.38f, 0.82f, 0.72f, 0.92f, 0.92f, LayerDepth.MIDDLE),
    TrajectorySeed(0.08f, 0.60f, 0.42f, 0.28f, 0.78f, 0.65f, 1.02f, 0.25f, LayerDepth.MIDDLE),

    // FOREGROUND LAYER (9..11) - Sparse, slightly brighter segments with restrained glow
    TrajectorySeed(0.08f, 0.22f, 0.38f, 0.14f, 0.72f, 0.48f, 0.88f, 0.58f, LayerDepth.FOREGROUND),
    TrajectorySeed(0.22f, 0.08f, 0.52f, 0.32f, 0.80f, 0.22f, 0.95f, 0.48f, LayerDepth.FOREGROUND),
    TrajectorySeed(0.02f, 0.52f, 0.32f, 0.72f, 0.68f, 0.35f, 1.02f, 0.78f, LayerDepth.FOREGROUND)
)

private enum class LayerDepth { DISTANT, MIDDLE, FOREGROUND }

private class TrajectorySeed(
    val x0: Float, val y0: Float,
    val cx1: Float, val cy1: Float,
    val cx2: Float, val cy2: Float,
    val x3: Float, val y3: Float,
    val depth: LayerDepth
)

/**
 * Draws the generative geometry system using preallocated Path objects. Zero per-frame allocations.
 */
private fun DrawScope.drawGenerativeGeometry(
    width: Float,
    height: Float,
    driftPhase: Float,
    atmosphere: TimeAtmosphere,
    paths: Array<Path>,
    pathMeasures: Array<PathMeasure>,
    trimmedPaths: Array<Path>
) {
    val activeDensityCount = (TRAJECTORY_COUNT * atmosphere.lineDensity.coerceIn(0.15f, 1.0f)).toInt().coerceIn(2, TRAJECTORY_COUNT)

    // Base color selection with subtle warm shift during afternoon/evening
    val warmTone = Color(0xFFC88C6C)
    val baseLineColor = lerp(atmosphere.primaryAccent, warmTone, atmosphere.warmth * 0.55f)
    val baseGlowColor = lerp(atmosphere.glowColor, warmTone, atmosphere.warmth * 0.55f)

    for (i in 0 until activeDensityCount) {
        val seed = TRAJECTORY_SEEDS[i]
        val path = paths[i]
        path.reset()

        // Calculate slow bending and organic drift for control points
        val bendFactor = atmosphere.curvature * 0.12f
        val drift1X = sin(driftPhase + i * 1.3f) * (width * bendFactor)
        val drift1Y = cos(driftPhase * 0.8f + i * 0.9f) * (height * bendFactor)
        val drift2X = cos(driftPhase * 1.1f + i * 1.7f) * (width * bendFactor)
        val drift2Y = sin(driftPhase * 0.9f + i * 1.1f) * (height * bendFactor)

        val px0 = seed.x0 * width
        val py0 = seed.y0 * height
        val pcx1 = seed.cx1 * width + drift1X
        val pcy1 = seed.cy1 * height + drift1Y
        val pcx2 = seed.cx2 * width + drift2X
        val pcy2 = seed.cy2 * height + drift2Y
        val px3 = seed.x3 * width
        val py3 = seed.y3 * height

        path.moveTo(px0, py0)
        path.cubicTo(pcx1, pcy1, pcx2, pcy2, px3, py3)

        when (seed.depth) {
            LayerDepth.DISTANT -> {
                val distantAlpha = (atmosphere.lineOpacity * 0.30f).coerceIn(0.03f, 0.20f)
                drawPath(
                    path = path,
                    color = baseLineColor.copy(alpha = distantAlpha),
                    style = Stroke(width = 1.0.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            LayerDepth.MIDDLE -> {
                val middleAlpha = (atmosphere.lineOpacity * 0.70f).coerceIn(0.08f, 0.40f)
                drawPath(
                    path = path,
                    color = baseLineColor.copy(alpha = middleAlpha),
                    style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            LayerDepth.FOREGROUND -> {
                // Foreground highlights use PathMeasure trimming to draw flowing segments with restrained glow
                val pathMeasure = pathMeasures[i]
                pathMeasure.setPath(path, false)
                val totalLength = pathMeasure.length
                if (totalLength > 0f) {
                    val segmentSpan = totalLength * atmosphere.lineLength.coerceIn(0.20f, 0.85f)
                    val travelProgress = ((sin(driftPhase * 0.6f + i * 2.1f) + 1f) / 2f)
                    val startDist = (totalLength - segmentSpan) * travelProgress
                    val stopDist = startDist + segmentSpan

                    val trimmedPath = trimmedPaths[i]
                    trimmedPath.reset()
                    pathMeasure.getSegment(startDist, stopDist, trimmedPath, true)

                    val fgAlpha = (atmosphere.lineOpacity * 0.95f).coerceIn(0.12f, 0.55f)
                    val glowAlpha = (atmosphere.glowIntensity * 0.25f * fgAlpha).coerceIn(0.02f, 0.20f)

                    // 1. Soft restrained glow halo
                    drawPath(
                        path = trimmedPath,
                        color = baseGlowColor.copy(alpha = glowAlpha),
                        style = Stroke(width = 6.5.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // 2. Primary foreground line segment
                    drawPath(
                        path = trimmedPath,
                        color = baseLineColor.copy(alpha = fgAlpha),
                        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}

/**
 * Remembers the current time atmosphere, updating on periodic ticks and lifecycle ON_RESUME.
 */
@Composable
fun rememberCurrentTimeAtmosphere(
    timeZone: TimeZone = TimeZone.getDefault()
): TimeAtmosphere {
    var atmosphere by remember(timeZone) { mutableStateOf(TimeAtmospherePolicy.resolve(timeZone = timeZone)) }
    val inspection = LocalInspectionMode.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, timeZone) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                atmosphere = TimeAtmospherePolicy.resolve(timeZone = timeZone)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(timeZone, inspection) {
        if (inspection) return@LaunchedEffect
        while (true) {
            delay(30_000L)
            val updated = TimeAtmospherePolicy.resolve(timeZone = timeZone)
            if (updated != atmosphere) {
                atmosphere = updated
            }
        }
    }

    return atmosphere
}

private fun DrawScope.drawAtmosphereContrastBed() {
    val bedHeight = (
        AetherFloatingHeaderDefaults.TopGap +
            AetherFloatingHeaderDefaults.ExpandedHeight +
            40.dp
        ).toPx().coerceAtMost(size.height)

    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0x40000000),
                Color(0x26000000),
                Color(0x12000000),
                Color.Transparent
            ),
            startY = 0f,
            endY = bedHeight
        ),
        size = size.copy(height = bedHeight)
    )
}
