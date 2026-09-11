package com.foresightlabs.aether.ui.home.atmosphere

import android.os.SystemClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.foresightlabs.aether.data.network.AetherConnectivityState
import com.foresightlabs.aether.ui.design.AetherFloatingHeaderDefaults
import com.foresightlabs.aether.ui.design.AetherFrostState
import com.foresightlabs.aether.ui.design.aetherFrostSource
import com.foresightlabs.aether.ui.design.isReducedMotionEnabled
import com.foresightlabs.aether.ui.theme.AtmosphereMode
import com.foresightlabs.aether.ui.theme.LocalAppThemeState
import com.foresightlabs.aether.ui.theme.TimeAtmospherePalette
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

/** CompositionLocal holding the session seed for Hero geometry. */
val LocalHeroSeed = staticCompositionLocalOf { HERO_GEOMETRY_SEED }

/** CompositionLocal holding process-level connectivity state for Aether environment. */
val LocalAetherConnectivityState = staticCompositionLocalOf { AetherConnectivityState.ONLINE }

/**
 * The canonical time-of-day atmosphere layer, shared by Home and Conversation.
 *
 * Renders a purely time-of-day-based generative mathematical geometry atmosphere.
 * Features an equation-driven visual architecture derived from mathematical curve compositions
 * (circles, ellipses, rose curves, Lissajous, trochoids) with thicker layered strokes.
 */
@Composable
fun AetherTimeAtmosphere(
    modifier: Modifier = Modifier,
    heroFraction: Float = 1f,
    enableAmbientMotion: Boolean = true,
    expression: AtmosphereExpression = AtmosphereExpression.HOME,
    frostState: AetherFrostState? = null,
    timeAtmosphere: TimeAtmosphere = rememberCurrentTimeAtmosphere(),
    seed: Long = LocalHeroSeed.current,
    connectivityState: AetherConnectivityState = LocalAetherConnectivityState.current
) {
    val context = LocalContext.current
    val reducedMotion = remember(context) { isReducedMotionEnabled(context) }
    val isInspection = LocalInspectionMode.current

    val isOffline = connectivityState == AetherConnectivityState.OFFLINE
    val onlineEnergy by animateFloatAsState(
        targetValue = if (isOffline) 0f else 1f,
        animationSpec = tween(
            durationMillis = if (isOffline) 450 else 650,
            easing = FastOutSlowInEasing
        ),
        label = "atmosphere_online_energy"
    )

    val effectiveAtmosphere = remember(timeAtmosphere, expression) {
        timeAtmosphere.scaledFor(expression)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "time_atmosphere_geometry_drift")
    val driftPhase by if (enableAmbientMotion && !reducedMotion && !isInspection && onlineEnergy > 0.01f) {
        val durationMillis = (24000 / effectiveAtmosphere.ambientMotionSpeed.coerceAtLeast(0.1f)).toInt()
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 6.28318530718f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "time_atmosphere_drift_phase"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    // One deterministic mathematical scene, shared across a session and across
    // Home and Conversation: identical equations, identical parameters,
    // identical nodes. Only the camera differs.
    val topology = remember(seed) { GeometryTopology.forSeed(seed) }

    // Built once, in world coordinates, and never rebuilt -- not per frame and
    // not per scroll. The camera is applied as a transform at draw time, so
    // moving or animating it costs nothing and cannot reshape a path.
    val worldPaths = remember(topology) { topology.curves.map { it.toWorldPath() } }

    val restingCamera = remember(topology, expression) { GeometryCamera.framing(topology, expression) }
    // Conversation opens by moving the camera from Home's framing to its own:
    // entering the structure rather than re-drawing it at another size.
    val entryProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "atmosphere_camera_entry"
    )
    val homeCamera = remember(topology) { GeometryCamera.framing(topology, AtmosphereExpression.HOME) }
    val camera = remember(homeCamera, restingCamera, entryProgress) {
        if (expression == AtmosphereExpression.HOME) {
            restingCamera
        } else {
            GeometryCamera.lerp(homeCamera, restingCamera, entryProgress)
        }
    }

    val startElapsedMillis = remember { SystemClock.elapsedRealtime() }

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

            val onlineGradientColors = listOf(
                timeAtmosphere.colors.first(),
                timeAtmosphere.colors.getOrElse(2) { timeAtmosphere.shadowColor },
                timeAtmosphere.shadowColor
            )
            val offlineGradientColors = listOf(
                Color(0xFF14161C),
                Color(0xFF0E0F14),
                Color(0xFF08090C)
            )
            val activeGradientColors = onlineGradientColors.zip(offlineGradientColors).map { (online, offline) ->
                lerp(offline, online, onlineEnergy)
            }

            drawRect(
                brush = Brush.verticalGradient(
                    colors = activeGradientColors,
                    startY = 0f,
                    endY = luminousHeight
                ),
                size = size
            )

            val glowCenter = Offset(
                width * (0.30f + sin(driftPhase) * 0.03f),
                luminousHeight * (0.20f + cos(driftPhase * 0.7f) * 0.02f)
            )
            val glowRadius = width * 0.85f
            val glowAlpha = (effectiveAtmosphere.glowIntensity * 0.85f * onlineEnergy).coerceIn(0.0f, 0.45f)
            if (glowAlpha > 0.001f) {
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
            }

            val signalsEnabled = enableAmbientMotion && !reducedMotion && !isInspection && onlineEnergy > 0.01f
            val elapsedMillis = if (signalsEnabled) {
                SystemClock.elapsedRealtime() - startElapsedMillis
            } else {
                0L
            }

            drawMathematicalGeometry(
                camera = camera,
                elapsedMillis = elapsedMillis,
                signalsEnabled = signalsEnabled,
                onlineEnergy = onlineEnergy,
                atmosphere = effectiveAtmosphere,
                topology = topology,
                worldPaths = worldPaths
            )

            drawAtmosphereContrastBed()
        }
    }
}

internal const val ConversationDensityScale = 0.55f
internal const val ConversationOpacityScale = 0.55f
internal const val ConversationIntensityScale = 0.60f
internal const val ConversationMotionScale = 0.55f

internal fun TimeAtmosphere.scaledFor(expression: AtmosphereExpression): TimeAtmosphere {
    if (expression == AtmosphereExpression.HOME) return this
    return copy(
        glowIntensity = glowIntensity * ConversationIntensityScale,
        ambientMotionSpeed = ambientMotionSpeed * ConversationMotionScale,
        lineDensity = lineDensity * ConversationDensityScale,
        lineOpacity = lineOpacity * ConversationOpacityScale
    )
}

private const val HERO_GEOMETRY_SEED = 20260908L

/**
 * Stroke hierarchy. The geometry must read without being stared at, and must
 * not read as neon -- these are the weights that separate "engineered" from
 * "faint scratch".
 */
private object GeometryStroke {
    val Secondary = 1.5.dp
    val Main = 2.0.dp
    val Primary = 2.4.dp
    val Signal = 3.0.dp
    val Glow = 4.5.dp
}

/** A curve's sampled points as a Path in world coordinates, built once. */
private fun MathematicalCurve.toWorldPath(): Path {
    val path = Path()
    samples.forEachIndexed { index, point ->
        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    return path
}

/**
 * Draws the scene through the camera.
 *
 * The entire projection is one `translate`/`scale(s, s)`/`translate` transform
 * with a single scale value, so there is no axis that can disagree with another
 * and a circle cannot become an ellipse. Stroke widths and radii are divided by
 * that scale so they stay in device-independent pixels rather than growing with
 * the zoom.
 */
private fun DrawScope.drawMathematicalGeometry(
    camera: GeometryCamera,
    elapsedMillis: Long,
    signalsEnabled: Boolean,
    onlineEnergy: Float,
    atmosphere: TimeAtmosphere,
    topology: GeometryTopology,
    worldPaths: List<Path>
) {
    val scale = camera.scaleFor(size.width)
    if (scale <= 0f || !scale.isFinite()) return

    val warmTone = Color(0xFFC88C6C)
    val onlineLineColor = lerp(atmosphere.primaryAccent, warmTone, atmosphere.warmth * 0.55f)
    // Offline keeps every equation, every parameter and every node exactly as
    // they are, and only drains the colour and stops the firing.
    val offlineLineColor = Color(0xFF565A66)
    val baseLineColor = lerp(offlineLineColor, onlineLineColor, onlineEnergy)

    val onlineGlowColor = lerp(atmosphere.glowColor, warmTone, atmosphere.warmth * 0.55f)
    val baseGlowColor = lerp(Color.Transparent, onlineGlowColor, onlineEnergy)

    val loudness = atmosphere.lineDensity.coerceIn(0.15f, 1.0f)

    /** Vertical fade so messages stay legible, with no hard cutoff anywhere. */
    fun attenuationAt(worldY: Float): Float {
        val screenY = camera.projectY(worldY, size.width, size.height)
        val fraction = (screenY / size.height).coerceIn(0f, 1f)
        return when {
            fraction <= 0.22f -> 1.0f
            fraction <= 0.50f -> 1.0f - 0.42f * ((fraction - 0.22f) / 0.28f)
            else -> 0.58f - 0.14f * ((fraction - 0.50f) / 0.50f)
        }.coerceIn(0.30f, 1.0f)
    }

    withTransform({
        translate(size.width / 2f, size.height / 2f)
        scale(scale, scale, pivot = Offset.Zero)
        translate(-camera.centerX, -camera.centerY)
    }) {
        topology.curves.forEachIndexed { index, curve ->
            val path = worldPaths.getOrNull(index) ?: return@forEachIndexed
            val centroidY = curve.samples.fold(0f) { acc, p -> acc + p.y } / curve.samples.size
            val attenuation = attenuationAt(centroidY)

            val (weight, alphaFactor) = when (curve.depth) {
                GeometryDepth.DISTANT -> GeometryStroke.Secondary to 0.42f
                GeometryDepth.MIDDLE -> GeometryStroke.Main to 0.70f
                GeometryDepth.FOREGROUND -> GeometryStroke.Primary to 0.88f
            }

            val coreAlpha = (atmosphere.lineOpacity * alphaFactor * loudness * attenuation)
                .coerceIn(0.10f, 0.94f)
            val glowAlpha = (atmosphere.glowIntensity * 0.30f * loudness * attenuation * onlineEnergy)
                .coerceIn(0f, 0.30f)

            if (glowAlpha > 0.001f) {
                drawPath(
                    path = path,
                    color = baseGlowColor.copy(alpha = glowAlpha),
                    style = Stroke(width = GeometryStroke.Glow.toPx() / scale, cap = StrokeCap.Round)
                )
            }
            drawPath(
                path = path,
                color = baseLineColor.copy(alpha = coreAlpha),
                style = Stroke(width = weight.toPx() / scale, cap = StrokeCap.Round)
            )
        }

        // Nodes mark intersections, extrema and where an arc deliberately stops.
        // They are sparse by construction (see GeometryTopology.deriveNodes).
        for (node in topology.nodes) {
            val attenuation = attenuationAt(node.y)
            val nodeAlpha = (atmosphere.lineOpacity * 0.85f * attenuation).coerceIn(0.10f, 0.92f)
            val radiusPx = if (node.isHub) 3.1.dp.toPx() else 2.0.dp.toPx()

            if (onlineEnergy > 0.01f && node.isHub) {
                val haloAlpha = (atmosphere.glowIntensity * 0.28f * attenuation * onlineEnergy)
                    .coerceIn(0f, 0.28f)
                if (haloAlpha > 0.001f) {
                    drawCircle(
                        color = baseGlowColor.copy(alpha = haloAlpha),
                        radius = 7.dp.toPx() / scale,
                        center = Offset(node.x, node.y)
                    )
                }
            }
            drawCircle(
                color = baseLineColor.copy(alpha = nodeAlpha),
                radius = radiusPx / scale,
                center = Offset(node.x, node.y)
            )
        }

        // Firing travels along a curve's own parameter domain, blooming as it
        // passes rather than flashing a whole line.
        if (signalsEnabled) {
            for (signal in GeometrySignalScheduler.activeSignals(topology, elapsedMillis)) {
                val point = signal.currentPoint
                val attenuation = attenuationAt(point.y)
                val intensity = signal.intensity * attenuation * onlineEnergy
                if (intensity <= 0.01f) continue

                val bloomAlpha = (atmosphere.glowIntensity * 0.55f * intensity).coerceIn(0f, 0.55f)
                if (bloomAlpha > 0.001f) {
                    drawCircle(
                        color = baseGlowColor.copy(alpha = bloomAlpha),
                        radius = 13.dp.toPx() / scale,
                        center = Offset(point.x, point.y)
                    )
                }
                drawCircle(
                    color = baseLineColor.copy(alpha = (0.92f * intensity).coerceIn(0f, 0.95f)),
                    radius = GeometryStroke.Signal.toPx() / scale,
                    center = Offset(point.x, point.y)
                )
            }
        }
    }
}

@Composable
fun rememberCurrentTimeAtmosphere(
    timeZoneProvider: () -> TimeZone = { TimeZone.getDefault() },
    clockMillis: () -> Long = { System.currentTimeMillis() }
): TimeAtmosphere {
    val themeState = LocalAppThemeState.current
    return when (themeState.atmosphereMode) {
        AtmosphereMode.TIME_BASED ->
            rememberClockDrivenTimeAtmosphere(timeZoneProvider, clockMillis)
        AtmosphereMode.STATIC,
        AtmosphereMode.MANUAL ->
            remember(themeState.manualAtmosphere) {
                val palette = themeState.manualAtmosphere
                val (hour, minute) = palette.representativeTimeOfDay()
                TimeAtmospherePolicy.resolve(hour, minute).copy(
                    primaryAccent = palette.primaryAccent,
                    colors = palette.colors,
                    glowColor = palette.glowColor,
                    shadowColor = palette.shadowColor
                )
            }
    }
}

private fun TimeAtmospherePalette.representativeTimeOfDay(): Pair<Int, Int> {
    return when (this) {
        TimeAtmospherePalette.DAWN -> 6 to 30
        TimeAtmospherePalette.DAY -> 12 to 0
        TimeAtmospherePalette.GOLDEN_HOUR -> 18 to 0
        TimeAtmospherePalette.EVENING -> 21 to 0
        TimeAtmospherePalette.NIGHT -> 1 to 45
    }
}

@Composable
private fun rememberClockDrivenTimeAtmosphere(
    timeZoneProvider: () -> TimeZone = { TimeZone.getDefault() },
    clockMillis: () -> Long = { System.currentTimeMillis() }
): TimeAtmosphere {
    var atmosphere by remember {
        mutableStateOf(TimeAtmospherePolicy.resolve(timeMillis = clockMillis(), timeZone = timeZoneProvider()))
    }
    val inspection = LocalInspectionMode.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val updated = TimeAtmospherePolicy.resolve(timeMillis = clockMillis(), timeZone = timeZoneProvider())
                if (updated != atmosphere) {
                    atmosphere = updated
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(inspection) {
        if (inspection) return@LaunchedEffect
        while (true) {
            val updated = TimeAtmospherePolicy.resolve(timeMillis = clockMillis(), timeZone = timeZoneProvider())
            if (updated != atmosphere) {
                atmosphere = updated
            }
            delay(30_000L)
        }
    }

    return atmosphere
}

@Composable
fun rememberCurrentTimeAtmosphere(
    timeZone: TimeZone
): TimeAtmosphere = rememberCurrentTimeAtmosphere(timeZoneProvider = { timeZone })

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
