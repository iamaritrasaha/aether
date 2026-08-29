package com.foresightlabs.aether.ui.design
import android.content.Context
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.AetherMotion
import com.foresightlabs.aether.ui.theme.LocalAtmosphere
import com.foresightlabs.aether.ui.theme.aetherDuration
import com.foresightlabs.aether.ui.design.AetherFloatingHeaderDefaults
import com.foresightlabs.aether.ui.design.AetherFrostState
import com.foresightlabs.aether.ui.design.aetherFrostSource

/**
 * Checks if the user or device has requested reduced motion.
 */
fun isReducedMotionEnabled(context: Context): Boolean {
    return try {
        val durationScale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        val transitionScale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
            1f
        )
        durationScale == 0f || transitionScale == 0f
    } catch (_: Exception) {
        false
    }
}

/**
 * Layer 1 of the Aether spatial model: the Living Atmosphere.
 *
 * Renders the resolved time-of-day palette, weather-modulated when a real reading is
 * available. The atmosphere is continuous across the entire viewport.
 *
 * @param heroFraction how much of the container the luminous region occupies before it
 * falls off into the near-black base. Callers derive this from real measurements
 * (for example Home sheet's resting anchor). For full-screen atmospheric screens
 * (Profile, Auth, Conversation, Settings, Contacts), heroFraction is 1f.
 * @param enableAmbientMotion enables subtle organic environmental drift (e.g. on Auth).
 */
@Composable
fun AetherAtmosphericBackground(
    modifier: Modifier = Modifier,
    heroFraction: Float = 1f,
    enableAmbientMotion: Boolean = false,
    frostState: AetherFrostState? = null,
    content: @Composable () -> Unit
) {
    val atmosphere = LocalAtmosphere.current
    val context = LocalContext.current
    val reducedMotion = remember(context) { isReducedMotionEnabled(context) }

    val duration = aetherDuration(AetherMotion.AtmosphereMillis)
    val spec = tween<Color>(duration, easing = AetherMotion.AtmosphereEasing)

    val colors = atmosphere.colors
    val c0 by animateColorAsState(colors.getOrElse(0) { atmosphere.accent }, spec, label = "atmosphere_c0")
    val c1 by animateColorAsState(colors.getOrElse(1) { atmosphere.accent }, spec, label = "atmosphere_c1")
    val c2 by animateColorAsState(colors.getOrElse(2) { atmosphere.accent }, spec, label = "atmosphere_c2")
    val c3 by animateColorAsState(colors.getOrElse(3) { atmosphere.shadow }, spec, label = "atmosphere_c3")
    val c4 by animateColorAsState(colors.getOrElse(4) { atmosphere.shadow }, spec, label = "atmosphere_c4")
    val glow by animateColorAsState(atmosphere.glow, spec, label = "atmosphere_glow")
    val shadow by animateColorAsState(atmosphere.shadow, spec, label = "atmosphere_shadow")

    val isInspection = LocalInspectionMode.current
    // Slow ambient environmental drift when enabled and motion is allowed
    val infiniteTransition = rememberInfiniteTransition(label = "ambient_atmosphere_drift")
    val driftOffset by if (enableAmbientMotion && !reducedMotion && !isInspection) {
        infiniteTransition.animateFloat(
            initialValue = -0.04f,
            targetValue = 0.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(16000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "atmosphere_drift"
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(if (frostState != null) Modifier.aetherFrostSource(frostState) else Modifier)
            .background(atmosphere.shadow)
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val width = size.width
            val height = size.height
            if (width <= 0f || height <= 0f) return@Canvas

            val luminousHeight = height * heroFraction.coerceIn(0.15f, 1f)

            // Primary atmospheric gradient
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(c0, c1, c2, c3, c4),
                    start = Offset(width * (0.08f + driftOffset), 0f),
                    end = Offset(width * (1.05f - driftOffset), luminousHeight)
                ),
                size = size
            )

            // Ambient light source, upper-left.
            val glowCenter = Offset(
                width * (0.28f + driftOffset * 0.5f),
                luminousHeight * (0.16f + driftOffset * 0.3f)
            )
            val glowRadius = width * 0.85f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glow.copy(alpha = 0.52f),
                        c1.copy(alpha = 0.22f),
                        Color.Transparent
                    ),
                    center = glowCenter,
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = glowCenter
            )

            // A trace of atmospheric depth toward the falloff edge. Held light on
            // purpose: the message canvas is meant to stay luminous the whole way
            // down rather than sinking into a dark corner.
            val depthCenter = Offset(
                width * (0.92f - driftOffset * 0.5f),
                luminousHeight * (0.88f - driftOffset * 0.3f)
            )
            val depthRadius = width * 0.7f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        shadow.copy(alpha = 0.22f),
                        shadow.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = depthCenter,
                    radius = depthRadius
                ),
                radius = depthRadius,
                center = depthCenter
            )

            // The gradient always covers the viewport. heroFraction compresses the
            // luminous transition behind Home's sheet; it never creates a dark seam.
            drawAetherContrastBed()
        }

        content()
    }
}

/**
 * The controlled-dark-atmosphere contrast bed.
 *
 * Aether's glass stays strictly transparent — it never tints itself to stay
 * readable. Instead, every atmospheric backdrop darkens itself locally in the
 * band where floating frosted chrome (headers, Home's hero controls) always
 * sits, so text and icons drawn on that glass keep enough contrast no matter
 * how luminous the palette or weather underneath gets. It fades out well
 * before the region typically reads as a rectangle, so it blends into the
 * atmosphere rather than reading as a panel.
 *
 * This is drawn as part of the same source Haze captures for the frost above
 * it, so the darkening is exactly what the glass ends up showing through —
 * not a separate layer the blur can miss.
 */
private fun DrawScope.drawAetherContrastBed() {
    val bedHeight = (
        AetherFloatingHeaderDefaults.TopGap +
            AetherFloatingHeaderDefaults.ExpandedHeight +
            40.dp
        ).toPx().coerceAtMost(size.height)

    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0x40000000), // ~25% at the very top edge, behind chrome
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

/**
 * Canonical full-screen atmospheric screen container.
 *
 * Covers the entire viewport continuously with the Living Atmosphere without
 * any vertical cutoffs, splits, or dark background seams.
 */
@Composable
fun AetherAtmosphericScreen(
    modifier: Modifier = Modifier,
    enableAmbientMotion: Boolean = false,
    frostState: AetherFrostState? = null,
    content: @Composable BoxScope.() -> Unit
) {
    AetherAtmosphericBackground(
        modifier = modifier.fillMaxSize(),
        heroFraction = 1f,
        enableAmbientMotion = enableAmbientMotion,
        frostState = frostState
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            content = content
        )
    }
}
