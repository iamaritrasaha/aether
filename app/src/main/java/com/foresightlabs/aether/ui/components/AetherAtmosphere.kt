package com.foresightlabs.aether.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.AtmosphereMode
import com.foresightlabs.aether.ui.theme.AtmosphereWeatherService
import com.foresightlabs.aether.ui.theme.LocalAppThemeState

/**
 * Living atmospheric background system for Aether.
 * Renders warm and dynamic luminous gradients across Dawn, Day, Golden Hour, Evening, and Night,
 * with automatic weather modulation. Transitions are smooth and cinematic (2.5s duration).
 */
@Composable
fun AetherAtmosphericBackground(
    modifier: Modifier = Modifier,
    heroOnly: Boolean = false,
    content: @Composable () -> Unit
) {
    val themeState = LocalAppThemeState.current
    val context = LocalContext.current

    // Auto-fetch weather when in Time + Weather mode
    LaunchedEffect(themeState.atmosphereMode) {
        if (themeState.atmosphereMode == AtmosphereMode.TIME_AND_WEATHER) {
            val (weather, _) = AtmosphereWeatherService.fetchCurrentWeather(context)
            themeState.weatherCondition = weather
        }
    }

    val rawColors = themeState.resolvedAtmosphereColors()
    val rawGlow = themeState.resolvedGlowColor()
    val rawShadow = themeState.resolvedShadowColor()

    // Cinematic slow transition duration (2500ms) for soft, ambient palette shifts
    val animDuration = 2500
    val easingSpec = FastOutSlowInEasing

    val c0 by animateColorAsState(rawColors.getOrElse(0) { Color(0xFFFF9A4A) }, tween(animDuration, easing = easingSpec), label = "c0")
    val c1 by animateColorAsState(rawColors.getOrElse(1) { Color(0xFFFF7038) }, tween(animDuration, easing = easingSpec), label = "c1")
    val c2 by animateColorAsState(rawColors.getOrElse(2) { Color(0xFFF04425) }, tween(animDuration, easing = easingSpec), label = "c2")
    val c3 by animateColorAsState(rawColors.getOrElse(3) { Color(0xFFE92D27) }, tween(animDuration, easing = easingSpec), label = "c3")
    val c4 by animateColorAsState(rawColors.getOrElse(4) { Color(0xFFC90B27) }, tween(animDuration, easing = easingSpec), label = "c4")

    val glowColor by animateColorAsState(rawGlow, tween(animDuration, easing = easingSpec), label = "glow")
    val shadowColor by animateColorAsState(rawShadow, tween(animDuration, easing = easingSpec), label = "shadow")

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AetherEmber.Colors.Background)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            if (heroOnly) {
                // Upper Hero Region (top ~45%)
                val heroHeight = height * 0.52f

                // Base Linear Gradient
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(c0, c1, c2, c3, c4),
                        start = Offset(0f, 0f),
                        end = Offset(width * 1.1f, heroHeight)
                    ),
                    size = size.copy(height = heroHeight)
                )

                // Atmospheric radial light hotspot top-left
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glowColor.copy(alpha = 0.55f),
                            c0.copy(alpha = 0.25f),
                            Color.Transparent
                        ),
                        center = Offset(width * 0.25f, height * 0.12f),
                        radius = width * 0.75f
                    ),
                    radius = width * 0.75f,
                    center = Offset(width * 0.25f, height * 0.12f)
                )

                // Depth accent bottom-right of hero
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            shadowColor.copy(alpha = 0.65f),
                            shadowColor.copy(alpha = 0.25f),
                            Color.Transparent
                        ),
                        center = Offset(width * 0.95f, heroHeight * 0.85f),
                        radius = width * 0.6f
                    ),
                    radius = width * 0.6f,
                    center = Offset(width * 0.95f, heroHeight * 0.85f)
                )
            } else {
                // Full Screen Atmosphere (e.g. Conversation, Auth, Appearance)
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(c0, c1, c2, c3, c4),
                        start = Offset(width * 0.1f, 0f),
                        end = Offset(width, height * 1.05f)
                    )
                )

                // Upper ambient light glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glowColor.copy(alpha = 0.50f),
                            c1.copy(alpha = 0.22f),
                            Color.Transparent
                        ),
                        center = Offset(width * 0.35f, height * 0.15f),
                        radius = width * 0.85f
                    ),
                    radius = width * 0.85f,
                    center = Offset(width * 0.35f, height * 0.15f)
                )

                // Mid-screen warmth / atmospheric diffusion
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            c2.copy(alpha = 0.35f),
                            c4.copy(alpha = 0.18f),
                            Color.Transparent
                        ),
                        center = Offset(width * 0.8f, height * 0.55f),
                        radius = width * 0.9f
                    ),
                    radius = width * 0.9f,
                    center = Offset(width * 0.8f, height * 0.55f)
                )

                // Bottom shadow pool
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            shadowColor.copy(alpha = 0.70f),
                            shadowColor.copy(alpha = 0.30f),
                            Color.Transparent
                        ),
                        center = Offset(width * 0.5f, height * 0.95f),
                        radius = width * 0.75f
                    ),
                    radius = width * 0.75f,
                    center = Offset(width * 0.5f, height * 0.95f)
                )
            }
        }

        content()
    }
}
