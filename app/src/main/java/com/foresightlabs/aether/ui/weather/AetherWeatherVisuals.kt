package com.foresightlabs.aether.ui.weather

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.foresightlabs.aether.ui.theme.LocalAtmosphere
import com.foresightlabs.aether.ui.theme.LocalReducedMotion
import com.foresightlabs.aether.ui.theme.TimeAtmospherePalette
import com.foresightlabs.aether.ui.theme.WeatherCondition
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Shared dynamic atmospheric weather animation engine with continuous frame clock.
 *
 * Layer 2 of the Aether spatial model. Renders rich, cinematic weather visual
 * phenomena directly on Canvas with zero per-frame heap allocations.
 *
 * Uses a true mutable Compose state frame clock driven by [withFrameNanos] that
 * invalidates the Canvas DrawScope on every vsync frame (60/90/120Hz).
 */
@Composable
fun AetherWeatherVisuals(
    condition: WeatherCondition,
    timeBand: TimeAtmospherePalette,
    revealProgress: Float,
    modifier: Modifier = Modifier,
    weatherState: WeatherHeroState? = null,
    testTimeSeconds: Float? = null,
    reducedMotion: Boolean = LocalReducedMotion.current
) {
    val atmosphere = LocalAtmosphere.current
    val isNight = timeBand == TimeAtmospherePalette.NIGHT
    val isGolden = timeBand == TimeAtmospherePalette.GOLDEN_HOUR || timeBand == TimeAtmospherePalette.DAWN
    val isInspection = androidx.compose.ui.platform.LocalInspectionMode.current

    // --- 1. Guaranteed Frame-by-Frame Compose State Clock ---------------------
    val frameTimeNanos = remember { mutableLongStateOf(0L) }

    LaunchedEffect(reducedMotion, isInspection, testTimeSeconds) {
        if (reducedMotion || isInspection || testTimeSeconds != null) {
            frameTimeNanos.longValue = 0L
            return@LaunchedEffect
        }

        var startTime = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (startTime == 0L) {
                    startTime = now
                }
                frameTimeNanos.longValue = now - startTime
            }
        }
    }

    // Celestial body progression along trajectory (0f = left horizon, 0.5f = peak, 1f = right horizon)
    val celestialProgress = weatherState?.celestialProgress ?: 0.5f
    val isNightCelestial = weatherState?.isNightCelestial ?: isNight

    // Pre-allocated pseudo-random particle tables
    val stars = remember { generateStars(count = 32) }
    val dustMotes = remember { generateDustMotes(count = 20) }
    val rainStreaks = remember { generateRainStreaks(count = 48) }
    val snowFlakes = remember { generateSnowFlakes(count = 30) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .clearAndSetSemantics { /* Decorative atmospheric visuals; no TalkBack traversal */ }
    ) {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@Canvas

        // CRITICAL: Read MutableLongState inside DrawScope so Compose invalidates
        // drawing on EVERY frame.
        val time = if (testTimeSeconds != null) {
            testTimeSeconds
        } else {
            frameTimeNanos.longValue / 1_000_000_000f
        }

        val primaryGlow = atmosphere.glow
        val primaryAccent = atmosphere.accent

        // Ambient visual intensity scales smoothly from resting Home (0.18) to full Hero (1.0)
        val ambientIntensity = lerp(0.18f, 1.0f, revealProgress)

        // --- 2. Dynamic Animated Background Gradient Field (All conditions) -----
        drawAnimatedGradientField(
            width = width,
            height = height,
            time = time,
            intensity = ambientIntensity,
            isNight = isNight,
            glowColor = primaryGlow,
            accentColor = primaryAccent
        )

        // Atmospheric Haze Bands (All conditions when hero is emerging)
        if (ambientIntensity > 0.2f) {
            drawAtmosphericHaze(
                width = width,
                height = height,
                time = time,
                ambientIntensity = ambientIntensity,
                isNight = isNight,
                glowColor = primaryGlow
            )
        }

        // --- 3. Dotted Celestial Trajectory (emerges 0.25 -> 0.70) --------------
        val trajectoryAlpha = ((revealProgress - 0.22f) / 0.48f).coerceIn(0f, 1f)
        if (trajectoryAlpha > 0.01f) {
            drawDottedCelestialTrajectory(
                width = width,
                height = height,
                time = time,
                celestialProgress = celestialProgress,
                trajectoryAlpha = trajectoryAlpha,
                isNight = isNightCelestial
            )
        }

        // --- 4. Weather Phenomena & Celestial Body Layer Ordering --------------
        when (condition) {
            WeatherCondition.CLEAR -> {
                if (isNightCelestial) {
                    drawTwinklingStars(width, height, time, ambientIntensity, stars)
                    drawCelestialMoon(
                        width = width,
                        height = height,
                        celestialProgress = celestialProgress,
                        revealProgress = revealProgress,
                        time = time,
                        glowColor = primaryGlow
                    )
                } else {
                    drawSuspendedMotes(width, height, time, ambientIntensity, dustMotes, primaryGlow)
                    drawCelestialSun(
                        width = width,
                        height = height,
                        celestialProgress = celestialProgress,
                        revealProgress = revealProgress,
                        time = time,
                        isGolden = isGolden,
                        glowColor = primaryGlow,
                        accentColor = primaryAccent
                    )
                }
            }

            WeatherCondition.PARTLY_CLOUDY -> {
                // Far clouds (behind sun/moon)
                drawCloudLayer(
                    width = width,
                    height = height,
                    time = time,
                    speedScale = 0.7f,
                    baseY = height * 0.16f,
                    cloudWidth = width * 1.3f,
                    cloudHeight = height * 0.26f,
                    alpha = (ambientIntensity * 0.35f).coerceIn(0f, 0.7f),
                    color = if (isNight) Color(0x35121828) else Color(0x28FFFFFF)
                )

                if (isNightCelestial) {
                    drawTwinklingStars(width, height, time, ambientIntensity * 0.6f, stars)
                    drawCelestialMoon(width, height, celestialProgress, revealProgress, time, primaryGlow)
                } else {
                    drawCelestialSun(width, height, celestialProgress, revealProgress, time, isGolden, primaryGlow, primaryAccent)
                }

                // Mid & near clouds (in front of sun/moon, occluding)
                drawCloudLayer(
                    width = width,
                    height = height,
                    time = time,
                    speedScale = 1.6f,
                    baseY = height * 0.24f,
                    cloudWidth = width * 1.1f,
                    cloudHeight = height * 0.30f,
                    alpha = (ambientIntensity * 0.50f).coerceIn(0f, 0.85f),
                    color = if (isNight) Color(0x45161E32) else Color(0x38FFFFFF)
                )
            }

            WeatherCondition.CLOUDY -> {
                // Dense layered cloud mass with sun/moon heavily diffused behind
                if (isNightCelestial) {
                    drawCelestialMoon(width, height, celestialProgress, revealProgress * 0.35f, time, primaryGlow)
                } else {
                    drawCelestialSun(width, height, celestialProgress, revealProgress * 0.35f, time, isGolden, primaryGlow, primaryAccent)
                }
                drawCloudLayer(
                    width = width, height = height, time = time, speedScale = 0.8f,
                    baseY = height * 0.14f, cloudWidth = width * 1.4f, cloudHeight = height * 0.30f,
                    alpha = (ambientIntensity * 0.55f).coerceIn(0f, 0.9f),
                    color = if (isNight) Color(0x50101626) else Color(0x45FFFFFF)
                )
                drawCloudLayer(
                    width = width, height = height, time = time, speedScale = 1.5f,
                    baseY = height * 0.28f, cloudWidth = width * 1.2f, cloudHeight = height * 0.34f,
                    alpha = (ambientIntensity * 0.70f).coerceIn(0f, 0.95f),
                    color = if (isNight) Color(0x650C101C) else Color(0x55FFFFFF)
                )
            }

            WeatherCondition.DRIZZLE -> {
                drawCloudLayer(
                    width = width, height = height, time = time, speedScale = 1.1f,
                    baseY = height * 0.18f, cloudWidth = width * 1.2f, cloudHeight = height * 0.28f,
                    alpha = (ambientIntensity * 0.45f).coerceIn(0f, 0.8f),
                    color = if (isNight) Color(0x40121828) else Color(0x35FFFFFF)
                )
                drawRainStreaks(
                    width = width, height = height, time = time,
                    ambientIntensity = ambientIntensity, streaks = rainStreaks,
                    density = 0.45f, speedMultiplier = 0.75f, streakScale = 0.65f
                )
            }

            WeatherCondition.RAIN -> {
                drawCloudLayer(
                    width = width, height = height, time = time, speedScale = 1.0f,
                    baseY = height * 0.20f, cloudWidth = width * 1.3f, cloudHeight = height * 0.32f,
                    alpha = (ambientIntensity * 0.60f).coerceIn(0f, 0.9f),
                    color = if (isNight) Color(0x550E1422) else Color(0x45FFFFFF)
                )
                drawRainStreaks(
                    width = width, height = height, time = time,
                    ambientIntensity = ambientIntensity, streaks = rainStreaks,
                    density = 0.80f, speedMultiplier = 1.0f, streakScale = 1.0f
                )
            }

            WeatherCondition.HEAVY_RAIN -> {
                drawCloudLayer(
                    width = width, height = height, time = time, speedScale = 1.3f,
                    baseY = height * 0.22f, cloudWidth = width * 1.4f, cloudHeight = height * 0.36f,
                    alpha = (ambientIntensity * 0.80f).coerceIn(0f, 0.95f),
                    color = if (isNight) Color(0x700A0E18) else Color(0x60FFFFFF)
                )
                drawRainStreaks(
                    width = width, height = height, time = time,
                    ambientIntensity = ambientIntensity, streaks = rainStreaks,
                    density = 1.0f, speedMultiplier = 1.35f, streakScale = 1.35f
                )
            }

            WeatherCondition.STORM -> {
                drawStormPhenomena(
                    width = width, height = height, time = time,
                    ambientIntensity = ambientIntensity, streaks = rainStreaks,
                    glowColor = primaryGlow
                )
            }

            WeatherCondition.FOG -> {
                drawFogVolumes(
                    width = width, height = height, time = time,
                    ambientIntensity = ambientIntensity, isNight = isNight,
                    glowColor = primaryGlow
                )
            }

            WeatherCondition.SNOW -> {
                drawCloudLayer(
                    width = width, height = height, time = time, speedScale = 0.7f,
                    baseY = height * 0.16f, cloudWidth = width * 1.3f, cloudHeight = height * 0.26f,
                    alpha = (ambientIntensity * 0.40f).coerceIn(0f, 0.8f),
                    color = if (isNight) Color(0x35121828) else Color(0x30FFFFFF)
                )
                drawSnowFlakes(
                    width = width, height = height, time = time,
                    ambientIntensity = ambientIntensity, flakes = snowFlakes
                )
            }

            WeatherCondition.UNKNOWN -> {
                // Neutral time-based atmosphere; dynamic animated background gradient field lives cleanly
            }
        }
    }
}

// ==============================================================================
// CELESTIAL TRAJECTORY & SUN / MOON VISUALS
// ==============================================================================

/**
 * Evaluates the broad cinematic curved trajectory arc across the sky.
 *
 * P0 = lower-left horizon (~8% width, ~50% height)
 * P1 = peak noon / midnight apex (~52% width, ~10% height)
 * P2 = lower-right horizon (~94% width, ~55% height)
 */
fun evaluateCelestialArc(progress: Float, width: Float, height: Float): Offset {
    val t = progress.coerceIn(0f, 1f)
    val p0x = width * 0.08f
    val p0y = height * 0.50f

    val p1x = width * 0.52f
    val p1y = height * 0.10f

    val p2x = width * 0.94f
    val p2y = height * 0.55f

    val inv = 1f - t
    val x = inv * inv * p0x + 2f * inv * t * p1x + t * t * p2x
    val y = inv * inv * p0y + 2f * inv * t * p1y + t * t * p2y
    return Offset(x, y)
}

/**
 * Draws discrete dots along the celestial orbital arc with localized breathing shimmer.
 */
private fun DrawScope.drawDottedCelestialTrajectory(
    width: Float,
    height: Float,
    time: Float,
    celestialProgress: Float,
    trajectoryAlpha: Float,
    isNight: Boolean
) {
    val dotCount = 30
    val dotColor = if (isNight) Color(0x75A0C0E0) else Color(0x70FFFFFF)

    for (i in 1..dotCount) {
        val t = i.toFloat() / (dotCount + 1).toFloat()
        val pos = evaluateCelestialArc(t, width, height)

        // Dots closer to the celestial body softly brighten/dim with animated phase
        val distToBody = abs(t - celestialProgress)
        val proximityBoost = (1f - (distToBody / 0.35f).coerceIn(0f, 1f))
        val wave = sin(time * 2.6f - distToBody * 9f)
        val shimmer = 0.82f + 0.18f * wave + proximityBoost * (0.22f + 0.16f * sin(time * 3.2f + i * 0.35f))
        val baseAlpha = lerp(0.20f, 0.92f, proximityBoost) * shimmer
        val alpha = (baseAlpha * trajectoryAlpha).coerceIn(0f, 1f)

        drawCircle(
            color = dotColor.copy(alpha = alpha),
            radius = 1.5.dp.toPx(),
            center = pos
        )
    }
}

/**
 * Draws the layered, living Sun celestial body along its real-time trajectory.
 */
private fun DrawScope.drawCelestialSun(
    width: Float,
    height: Float,
    celestialProgress: Float,
    revealProgress: Float,
    time: Float,
    isGolden: Boolean,
    glowColor: Color,
    accentColor: Color
) {
    val bodyAlpha = ((revealProgress - 0.28f) / 0.45f).coerceIn(0f, 1f)
    if (bodyAlpha <= 0.01f) return

    val basePos = evaluateCelestialArc(celestialProgress, width, height)
    // Organic micro-movement of center
    val sunCenter = Offset(
        x = basePos.x + sin(time * 0.95f) * 4.0f,
        y = basePos.y + cos(time * 0.78f) * 3.0f
    )

    // Ambient breathing - noticeable 14% oscillation over ~3.5s
    val haloBreathe = 1f + 0.14f * sin(time * 1.5f)
    val haloAlphaShift = (0.10f * sin(time * 1.2f))
    val outerHaloRadius = (width * (if (isGolden) 0.46f else 0.40f)) * haloBreathe
    val innerCoronaRadius = 30.dp.toPx() * (0.88f + 0.12f * sin(time * 1.7f))
    val coreRadius = 14.5.dp.toPx()

    // 1. Large Outer Volumetric Halo / Bloom
    val haloColor = if (isGolden) Color(0xFFFFB366) else glowColor
    val outerAlpha = (0.42f + haloAlphaShift).coerceIn(0.22f, 0.60f) * bodyAlpha
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                haloColor.copy(alpha = outerAlpha),
                accentColor.copy(alpha = outerAlpha * 0.60f),
                Color.Transparent
            ),
            center = sunCenter,
            radius = outerHaloRadius
        ),
        center = sunCenter,
        radius = outerHaloRadius
    )

    // 2. Volumetric Rotating Rays (10 rays rotating slowly and diffusing)
    val rayCount = 10
    val rayRotation = time * 0.12f
    val rayLength = innerCoronaRadius * 2.8f
    for (i in 0 until rayCount) {
        val angle = rayRotation + (i.toFloat() * (2f * PI.toFloat() / rayCount))
        val rayEnd = Offset(
            x = sunCenter.x + cos(angle) * rayLength,
            y = sunCenter.y + sin(angle) * rayLength
        )
        val rayAlpha = (0.28f + 0.08f * sin(time * 2.2f + i * 0.7f)) * bodyAlpha
        drawLine(
            color = haloColor.copy(alpha = rayAlpha.coerceIn(0.12f, 0.40f)),
            start = sunCenter,
            end = rayEnd,
            strokeWidth = 2.8.dp.toPx()
        )
    }

    // 3. Inner Diffuse Corona Ring
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFFFFF).copy(alpha = 0.90f * bodyAlpha),
                haloColor.copy(alpha = 0.65f * bodyAlpha),
                Color.Transparent
            ),
            center = sunCenter,
            radius = innerCoronaRadius
        ),
        center = sunCenter,
        radius = innerCoronaRadius
    )

    // 4. Luminous Warm Sun Core (Ivory/Gold)
    val coreGradient = if (isGolden) {
        listOf(Color(0xFFFFFDF5), Color(0xFFFFCC80), Color(0xFFFF9E3D))
    } else {
        listOf(Color(0xFFFFFFFF), Color(0xFFFFE8B2), Color(0xFFFFD166))
    }
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                coreGradient[0].copy(alpha = 0.98f * bodyAlpha),
                coreGradient[1].copy(alpha = 0.92f * bodyAlpha),
                coreGradient[2].copy(alpha = 0.85f * bodyAlpha)
            ),
            center = sunCenter,
            radius = coreRadius
        ),
        center = sunCenter,
        radius = coreRadius
    )
}

/**
 * Draws the layered, living Moon celestial body along its real-time night trajectory.
 */
private fun DrawScope.drawCelestialMoon(
    width: Float,
    height: Float,
    celestialProgress: Float,
    revealProgress: Float,
    time: Float,
    glowColor: Color
) {
    val bodyAlpha = ((revealProgress - 0.28f) / 0.45f).coerceIn(0f, 1f)
    if (bodyAlpha <= 0.01f) return

    val basePos = evaluateCelestialArc(celestialProgress, width, height)
    val moonCenter = Offset(
        x = basePos.x + sin(time * 0.65f) * 2.5f,
        y = basePos.y + cos(time * 0.52f) * 2.0f
    )

    val breathe = 1f + 0.10f * sin(time * 1.3f)
    val outerBloomRadius = width * 0.38f * breathe
    val innerCoronaRadius = 28.dp.toPx() * (0.92f + 0.08f * sin(time * 1.5f))
    val discRadius = 13.5.dp.toPx()

    // 1. Cool Lunar Atmospheric Bloom
    val bloomAlpha = (0.35f + 0.08f * sin(time * 0.95f)).coerceIn(0.18f, 0.50f) * bodyAlpha
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0x556090D0).copy(alpha = bloomAlpha),
                Color(0x28304060).copy(alpha = bloomAlpha * 0.5f),
                Color.Transparent
            ),
            center = moonCenter,
            radius = outerBloomRadius
        ),
        center = moonCenter,
        radius = outerBloomRadius
    )

    // 2. Inner Moonlight Corona
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0x80D0E8FF).copy(alpha = 0.70f * bodyAlpha),
                Color(0x356090D0).copy(alpha = 0.35f * bodyAlpha),
                Color.Transparent
            ),
            center = moonCenter,
            radius = innerCoronaRadius
        ),
        center = moonCenter,
        radius = innerCoronaRadius
    )

    // 3. Ivory/Silver Lunar Disc with subtle surface gradient
    drawCircle(
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFFF6F8FA).copy(alpha = 0.96f * bodyAlpha),
                Color(0xFFBAC8D8).copy(alpha = 0.88f * bodyAlpha)
            ),
            start = Offset(moonCenter.x - discRadius, moonCenter.y - discRadius),
            end = Offset(moonCenter.x + discRadius, moonCenter.y + discRadius)
        ),
        center = moonCenter,
        radius = discRadius
    )
}

/**
 * Draws flowing horizontal atmospheric haze bands across the sky.
 */
private fun DrawScope.drawAtmosphericHaze(
    width: Float,
    height: Float,
    time: Float,
    ambientIntensity: Float,
    isNight: Boolean,
    glowColor: Color
) {
    val baseAlpha = (ambientIntensity * (if (isNight) 0.28f else 0.38f)).coerceIn(0f, 0.65f)
    if (baseAlpha <= 0.01f) return

    val hazeColor = if (isNight) Color(0x356080A0) else glowColor.copy(alpha = 0.35f)
    val layerCount = 3

    for (i in 0 until layerCount) {
        val driftX = sin(time * 0.28f + i * 1.8f) * (width * 0.18f)
        val driftY = cos(time * 0.22f + i * 1.4f) * (height * 0.04f)
        val centerY = height * (0.16f + i * 0.14f) + driftY
        val bandWidth = width * (1.35f + 0.15f * sin(time * 0.35f + i * 2.2f))
        val bandHeight = height * (0.18f + 0.04f * cos(time * 0.30f + i * 1.6f))

        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(hazeColor.copy(alpha = baseAlpha * 0.45f), Color.Transparent),
                center = Offset(width * 0.5f + driftX, centerY),
                radius = bandWidth * 0.5f
            ),
            topLeft = Offset(width * 0.5f + driftX - bandWidth * 0.5f, centerY - bandHeight * 0.5f),
            size = Size(bandWidth, bandHeight)
        )
    }
}

// ==============================================================================
// DYNAMIC ANIMATED BACKGROUND GRADIENT FIELD
// ==============================================================================

/**
 * Renders 4 large organic moving radial gradient lobes across the sky to ensure the
 * environment visibly breathes and evolves continuously.
 */
private fun DrawScope.drawAnimatedGradientField(
    width: Float,
    height: Float,
    time: Float,
    intensity: Float,
    isNight: Boolean,
    glowColor: Color,
    accentColor: Color
) {
    if (intensity <= 0.01f) return

    // Lobe 1: Upper-left primary glow drift (12-18% screen travel over 6-8s)
    val l1Center = Offset(
        x = width * (0.28f + sin(time * 0.32f) * 0.14f),
        y = height * (0.20f + cos(time * 0.25f) * 0.10f)
    )
    val l1Radius = width * 0.70f
    val l1Alpha = (if (isNight) 0.32f else 0.48f) * intensity

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(glowColor.copy(alpha = l1Alpha), Color.Transparent),
            center = l1Center,
            radius = l1Radius
        ),
        center = l1Center,
        radius = l1Radius
    )

    // Lobe 2: Mid-right accent wave
    val l2Center = Offset(
        x = width * (0.72f + cos(time * 0.23f) * 0.15f),
        y = height * (0.40f + sin(time * 0.29f) * 0.12f)
    )
    val l2Radius = width * 0.65f
    val l2Alpha = (if (isNight) 0.28f else 0.42f) * intensity

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(accentColor.copy(alpha = l2Alpha), Color.Transparent),
            center = l2Center,
            radius = l2Radius
        ),
        center = l2Center,
        radius = l2Radius
    )

    // Lobe 3: Lower-mid atmospheric depth
    val l3Center = Offset(
        x = width * (0.48f + sin(time * 0.18f + 1.3f) * 0.12f),
        y = height * (0.65f + cos(time * 0.21f) * 0.08f)
    )
    val l3Radius = width * 0.68f
    val l3Alpha = (if (isNight) 0.24f else 0.36f) * intensity

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(glowColor.copy(alpha = l3Alpha), Color.Transparent),
            center = l3Center,
            radius = l3Radius
        ),
        center = l3Center,
        radius = l3Radius
    )

    // Lobe 4: Upper-center radiant wave
    val l4Center = Offset(
        x = width * (0.42f + cos(time * 0.35f + 2.0f) * 0.16f),
        y = height * (0.15f + sin(time * 0.28f) * 0.08f)
    )
    val l4Radius = width * 0.55f
    val l4Alpha = (if (isNight) 0.22f else 0.34f) * intensity

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(accentColor.copy(alpha = l4Alpha), Color.Transparent),
            center = l4Center,
            radius = l4Radius
        ),
        center = l4Center,
        radius = l4Radius
    )
}

// ==============================================================================
// CLOUD, RAIN, SNOW, FOG, & STORM RENDERING
// ==============================================================================

/**
 * Renders an animated cloud layer that drifts horizontally and wraps seamlessly.
 */
private fun DrawScope.drawCloudLayer(
    width: Float,
    height: Float,
    time: Float,
    speedScale: Float,
    baseY: Float,
    cloudWidth: Float,
    cloudHeight: Float,
    alpha: Float,
    color: Color
) {
    if (alpha <= 0.01f) return

    // Speed: moves across screen in ~28s - 65s
    val speedPxPerSec = (width / 42f) * speedScale
    val wrapWidth = width + cloudWidth
    val currentX = ((time * speedPxPerSec) % wrapWidth) - cloudWidth * 0.5f

    drawCloudMass(
        center = Offset(currentX, baseY),
        width = cloudWidth,
        height = cloudHeight,
        color = color.copy(alpha = alpha)
    )
    // Wrap-around clone
    drawCloudMass(
        center = Offset(currentX + wrapWidth, baseY),
        width = cloudWidth,
        height = cloudHeight,
        color = color.copy(alpha = alpha)
    )
    drawCloudMass(
        center = Offset(currentX - wrapWidth, baseY),
        width = cloudWidth,
        height = cloudHeight,
        color = color.copy(alpha = alpha)
    )
}

private fun DrawScope.drawCloudMass(
    center: Offset,
    width: Float,
    height: Float,
    color: Color
) {
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(color, color.copy(alpha = color.alpha * 0.4f), Color.Transparent),
            center = center,
            radius = width * 0.5f
        ),
        topLeft = Offset(center.x - width * 0.5f, center.y - height * 0.5f),
        size = Size(width, height)
    )
}

/**
 * Draws animated continuous rain streaks across multi-layer depth planes.
 */
private fun DrawScope.drawRainStreaks(
    width: Float,
    height: Float,
    time: Float,
    ambientIntensity: Float,
    streaks: List<AtmosphericParticle>,
    density: Float,
    speedMultiplier: Float,
    streakScale: Float
) {
    val baseAlpha = (ambientIntensity * 0.85f * density).coerceIn(0f, 0.90f)
    if (baseAlpha <= 0.02f) return

    val angleRad = 12f * (PI.toFloat() / 180f)
    val dx = sin(angleRad)
    val dy = cos(angleRad)

    val count = (streaks.size * density).toInt().coerceAtLeast(8)
    for (i in 0 until count) {
        val streak = streaks[i]
        val speed = (750f + streak.speedX * 350f) * speedMultiplier
        val fallHeight = height + 100f
        val currentY = ((streak.relY * fallHeight + time * speed) % fallHeight) - 50f
        val currentX = (streak.relX * width + currentY * (dx / dy)) % width

        val length = (16.dp.toPx() + streak.radiusPx * 8f) * streakScale
        val streakAlpha = (baseAlpha * streak.baseAlpha).coerceIn(0f, 0.85f)

        drawLine(
            color = Color(0xC0DDEEFF).copy(alpha = streakAlpha),
            start = Offset(currentX, currentY),
            end = Offset(currentX + dx * length, currentY + dy * length),
            strokeWidth = 1.25.dp.toPx()
        )
    }
}

/**
 * Draws animated snowfall with sinusoidal horizontal swaying.
 */
private fun DrawScope.drawSnowFlakes(
    width: Float,
    height: Float,
    time: Float,
    ambientIntensity: Float,
    flakes: List<AtmosphericParticle>
) {
    val baseAlpha = (ambientIntensity * 0.85f).coerceIn(0f, 0.85f)
    if (baseAlpha <= 0.02f) return

    flakes.forEach { flake ->
        val fallSpeed = 70f + flake.speedX * 45f
        val fallHeight = height + 40f
        val currentY = ((flake.relY * fallHeight + time * fallSpeed) % fallHeight) - 20f
        val sway = sin((time * 1.5f + flake.phase) * PI.toFloat()) * (12.dp.toPx() * flake.speedX)
        val currentX = (flake.relX * width + sway) % width

        val fAlpha = (baseAlpha * flake.baseAlpha).coerceIn(0f, 0.90f)

        drawCircle(
            color = Color.White.copy(alpha = fAlpha),
            radius = (flake.radiusPx * 1.3f).coerceIn(1.5f, 4.5f),
            center = Offset(currentX, currentY)
        )
    }
}

/**
 * Draws animated translucent fog mist volumes drifting horizontally.
 */
private fun DrawScope.drawFogVolumes(
    width: Float,
    height: Float,
    time: Float,
    ambientIntensity: Float,
    isNight: Boolean,
    glowColor: Color
) {
    val baseAlpha = (ambientIntensity * 0.70f).coerceIn(0f, 0.85f)
    if (baseAlpha <= 0.02f) return

    val fogColor = if (isNight) Color(0x35182030) else glowColor.copy(alpha = 0.25f)
    val layerCount = 3

    for (i in 0 until layerCount) {
        val phase = i * 2.1f
        val driftX = sin(time * 0.30f + phase) * (width * 0.15f)
        val centerY = height * (0.20f + i * 0.16f)
        val bandWidth = width * (1.4f + 0.15f * sin(time * 0.38f + phase))
        val bandHeight = height * (0.22f + 0.05f * cos(time * 0.32f + phase))

        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(fogColor.copy(alpha = baseAlpha * 0.45f), Color.Transparent),
                center = Offset(width * 0.5f + driftX, centerY),
                radius = bandWidth * 0.5f
            ),
            topLeft = Offset(width * 0.5f + driftX - bandWidth * 0.5f, centerY - bandHeight * 0.5f),
            size = Size(bandWidth, bandHeight)
        )
    }
}

/**
 * Draws storm phenomena: moving deep clouds, dense rain streaks, and rare diffuse lightning pulse.
 */
private fun DrawScope.drawStormPhenomena(
    width: Float,
    height: Float,
    time: Float,
    ambientIntensity: Float,
    streaks: List<AtmosphericParticle>,
    glowColor: Color
) {
    // 1. Moving heavy storm clouds
    drawCloudLayer(
        width = width, height = height, time = time, speedScale = 1.4f,
        baseY = height * 0.20f, cloudWidth = width * 1.5f, cloudHeight = height * 0.38f,
        alpha = (ambientIntensity * 0.85f).coerceIn(0f, 0.95f),
        color = Color(0x75080C14)
    )

    // 2. Heavy rain
    drawRainStreaks(
        width = width, height = height, time = time,
        ambientIntensity = ambientIntensity, streaks = streaks,
        density = 1.0f, speedMultiplier = 1.4f, streakScale = 1.3f
    )

    // 3. Rare diffuse whole-sky lightning pulse (every ~16 seconds)
    val cycle = time % 16f
    val flashAlpha = when {
        cycle in 0.0f..0.15f -> (cycle / 0.15f) * 0.22f * ambientIntensity
        cycle in 0.15f..0.45f -> (1f - (cycle - 0.15f) / 0.30f) * 0.22f * ambientIntensity
        else -> 0f
    }

    if (flashAlpha > 0.005f) {
        drawRect(
            color = Color(0xFFD0E0FF).copy(alpha = flashAlpha),
            size = Size(width, height)
        )
    }
}

private fun DrawScope.drawTwinklingStars(
    width: Float,
    height: Float,
    time: Float,
    ambientIntensity: Float,
    stars: List<AtmosphericParticle>
) {
    val baseAlpha = (ambientIntensity * 0.80f).coerceIn(0f, 0.85f)
    if (baseAlpha <= 0.02f) return

    stars.forEach { star ->
        val sx = star.relX * width
        val sy = star.relY * (height * 0.52f)
        val twinkle = 0.35f + 0.65f * sin((time * 2.5f * star.speedX + star.phase) * PI.toFloat())
        val sAlpha = (baseAlpha * star.baseAlpha * twinkle).coerceIn(0f, 0.90f)

        drawCircle(
            color = Color.White.copy(alpha = sAlpha),
            radius = star.radiusPx,
            center = Offset(sx, sy)
        )
    }
}

private fun DrawScope.drawSuspendedMotes(
    width: Float,
    height: Float,
    time: Float,
    ambientIntensity: Float,
    motes: List<AtmosphericParticle>,
    glowColor: Color
) {
    val baseAlpha = (ambientIntensity * 0.60f).coerceIn(0f, 0.65f)
    if (baseAlpha <= 0.02f) return

    motes.forEach { mote ->
        val mx = ((mote.relX * width + time * (12f * mote.speedX)) % width)
        val my = (mote.relY * (height * 0.55f) + sin((time * 0.8f + mote.phase) * PI.toFloat()) * 12.dp.toPx())
        val mAlpha = (baseAlpha * mote.baseAlpha * (0.6f + 0.4f * sin((time * 1.5f + mote.phase) * PI.toFloat()))).coerceIn(0f, 0.8f)

        drawCircle(
            color = glowColor.copy(alpha = mAlpha),
            radius = mote.radiusPx,
            center = Offset(mx, my)
        )
    }
}

// ==============================================================================
// PARTICLE TABLE GENERATORS
// ==============================================================================

@Immutable
data class AtmosphericParticle(
    val relX: Float,
    val relY: Float,
    val speedX: Float,
    val radiusPx: Float,
    val baseAlpha: Float,
    val phase: Float
)

private fun generateStars(count: Int): List<AtmosphericParticle> {
    return List(count) { i ->
        val pseudoRand1 = ((i * 17 + 7) % 97) / 97f
        val pseudoRand2 = ((i * 31 + 13) % 89) / 89f
        val pseudoRand3 = ((i * 23 + 5) % 73) / 73f
        AtmosphericParticle(
            relX = pseudoRand1,
            relY = pseudoRand2 * 0.85f,
            speedX = 0.4f + pseudoRand3 * 0.8f,
            radiusPx = 1.0f + pseudoRand3 * 1.5f,
            baseAlpha = 0.35f + pseudoRand2 * 0.55f,
            phase = pseudoRand1 * 2f
        )
    }
}

private fun generateDustMotes(count: Int): List<AtmosphericParticle> {
    return List(count) { i ->
        val pseudoRand1 = ((i * 19 + 11) % 97) / 97f
        val pseudoRand2 = ((i * 29 + 17) % 89) / 89f
        val pseudoRand3 = ((i * 13 + 3) % 73) / 73f
        AtmosphericParticle(
            relX = pseudoRand1,
            relY = pseudoRand2 * 0.90f,
            speedX = 0.3f + pseudoRand3 * 0.7f,
            radiusPx = 1.2f + pseudoRand3 * 1.8f,
            baseAlpha = 0.25f + pseudoRand2 * 0.45f,
            phase = pseudoRand1 * 2f
        )
    }
}

private fun generateRainStreaks(count: Int): List<AtmosphericParticle> {
    return List(count) { i ->
        val pseudoRand1 = ((i * 23 + 7) % 97) / 97f
        val pseudoRand2 = ((i * 37 + 19) % 89) / 89f
        val pseudoRand3 = ((i * 17 + 11) % 73) / 73f
        AtmosphericParticle(
            relX = pseudoRand1,
            relY = pseudoRand2,
            speedX = 0.5f + pseudoRand3 * 0.7f,
            radiusPx = 1.0f + pseudoRand3 * 1.2f,
            baseAlpha = 0.30f + pseudoRand2 * 0.60f,
            phase = pseudoRand1 * 2f
        )
    }
}

private fun generateSnowFlakes(count: Int): List<AtmosphericParticle> {
    return List(count) { i ->
        val pseudoRand1 = ((i * 29 + 13) % 97) / 97f
        val pseudoRand2 = ((i * 41 + 23) % 89) / 89f
        val pseudoRand3 = ((i * 19 + 7) % 73) / 73f
        AtmosphericParticle(
            relX = pseudoRand1,
            relY = pseudoRand2,
            speedX = 0.4f + pseudoRand3 * 0.8f,
            radiusPx = 1.5f + pseudoRand3 * 2.0f,
            baseAlpha = 0.40f + pseudoRand2 * 0.50f,
            phase = pseudoRand1 * 2f
        )
    }
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + fraction * (stop - start)
}
