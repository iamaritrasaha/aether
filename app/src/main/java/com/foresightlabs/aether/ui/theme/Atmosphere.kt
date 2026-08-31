package com.foresightlabs.aether.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import kotlinx.coroutines.delay
import java.util.Calendar

/**
 * The resolved Living Atmosphere for the current moment.
 *
 * Layer 1 of the Aether spatial model. Everything tinted by the environment —
 * accents, selected states, glow, focus — reads from here rather than hardcoding
 * a brand colour. Purely a function of time of day; there is no weather input.
 */
@Immutable
data class AetherAtmosphere(
    val palette: TimeAtmospherePalette,
    val colors: List<Color>,
    val glow: Color,
    val shadow: Color,
    val accent: Color,
    val accentSubtle: Color,
    val accentStrong: Color
)

private val DefaultAtmosphere = buildAtmosphere(palette = TimeAtmospherePalette.GOLDEN_HOUR)

val LocalAtmosphere = staticCompositionLocalOf { DefaultAtmosphere }

fun buildAtmosphere(palette: TimeAtmospherePalette): AetherAtmosphere {
    val accent = palette.primaryAccent
    return AetherAtmosphere(
        palette = palette,
        colors = palette.colors,
        glow = palette.glowColor,
        shadow = palette.shadowColor,
        accent = accent,
        accentSubtle = accent.copy(alpha = 0.20f),
        accentStrong = palette.colors.getOrElse(2) { accent }
    )
}

/**
 * Local wall-clock minute of day, recomputed on a slow ticker so the palette
 * actually moves through the day without relying on incidental recomposition.
 */
@Composable
fun rememberLocalMinuteOfDay(): Int {
    var minute by remember { mutableStateOf(currentMinuteOfDay()) }
    val inspecting = LocalInspectionMode.current
    LaunchedEffect(inspecting) {
        if (inspecting) return@LaunchedEffect
        while (true) {
            delay(TICK_MILLIS)
            val now = currentMinuteOfDay()
            if (now != minute) minute = now
        }
    }
    return minute
}

/**
 * Resolves and provides the active atmosphere: purely a function of time of day
 * (or a manually chosen palette), recomputed on a slow ticker.
 */
@Composable
fun rememberAtmosphere(themeState: AppThemeState): AetherAtmosphere {
    val minuteOfDay = rememberLocalMinuteOfDay()

    val palette = when (themeState.atmosphereMode) {
        AtmosphereMode.STATIC, AtmosphereMode.MANUAL -> themeState.manualAtmosphere
        AtmosphereMode.TIME_BASED -> TimeAtmospherePalette.forMinuteOfDay(minuteOfDay)
    }

    return remember(palette) { buildAtmosphere(palette) }
}

private const val TICK_MILLIS = 30_000L

private fun currentMinuteOfDay(): Int {
    val calendar = Calendar.getInstance()
    return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
}
