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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import kotlinx.coroutines.delay
import java.util.Calendar

/**
 * Real atmospheric weather payload retrieved from Open-Meteo or supplied in tests/overrides.
 */
@Immutable
data class WeatherData(
    val condition: WeatherCondition,
    val temperatureC: Int,
    val apparentTemperatureC: Int? = null,
    val highTempC: Int? = null,
    val lowTempC: Int? = null,
    val humidityPercent: Int? = null,
    val windSpeedKmh: Int? = null,
    val locationLabel: String? = null,
    val sunriseEpochMillis: Long? = null,
    val sunsetEpochMillis: Long? = null,
    val nextSunriseEpochMillis: Long? = null,
    val timezoneId: String? = null
)

/**
 * What Aether actually knows about local weather right now.
 *
 * Weather is never invented. When it is unknown the atmosphere falls back to a
 * time-only palette and the UI says so rather than showing a guessed condition.
 */
@Immutable
sealed interface WeatherReading {
    /** No attempt made yet, or none possible in this mode. */
    data object Idle : WeatherReading

    data object Loading : WeatherReading

    /** A real reading from the weather service. */
    data class Known(
        val condition: WeatherCondition,
        val data: WeatherData? = null
    ) : WeatherReading

    /** Truthful reason the atmosphere is running time-only. */
    data class Unavailable(val reason: WeatherUnavailableReason) : WeatherReading

    /** Explicitly chosen in Appearance for testing or as a deliberate fallback. */
    data class Override(
        val condition: WeatherCondition,
        val data: WeatherData? = null
    ) : WeatherReading

    val conditionOrNull: WeatherCondition?
        get() = when (this) {
            is Known -> condition
            is Override -> condition
            else -> null
        }

    val dataOrNull: WeatherData?
        get() = when (this) {
            is Known -> data
            is Override -> data
            else -> null
        }
}

enum class WeatherUnavailableReason(val message: String) {
    LOCATION_PERMISSION("Weather needs approximate location access."),
    NO_LOCATION("No approximate location available yet."),
    SERVICE("Weather service unreachable.")
}

/**
 * The resolved Living Atmosphere for the current moment.
 *
 * Layer 1 of the Aether spatial model. Everything tinted by the environment —
 * accents, selected states, glow, focus — reads from here rather than hardcoding
 * a brand colour.
 */
@Immutable
data class AetherAtmosphere(
    val palette: TimeAtmospherePalette,
    val weather: WeatherReading,
    val colors: List<Color>,
    val glow: Color,
    val shadow: Color,
    val accent: Color,
    val accentSubtle: Color,
    val accentStrong: Color
) {
    val weatherCondition: WeatherCondition? get() = weather.conditionOrNull
    val isWeatherModulated: Boolean get() = weatherCondition != null
}

private val DefaultAtmosphere = buildAtmosphere(
    palette = TimeAtmospherePalette.GOLDEN_HOUR,
    weather = WeatherReading.Idle
)

val LocalAtmosphere = staticCompositionLocalOf { DefaultAtmosphere }

fun buildAtmosphere(
    palette: TimeAtmospherePalette,
    weather: WeatherReading
): AetherAtmosphere {
    val condition = weather.conditionOrNull
    val colors = if (condition != null) {
        modulatePaletteWithWeather(palette.colors, condition)
    } else {
        palette.colors
    }
    val glow = condition?.let { modulateSingleColor(palette.glowColor, it) } ?: palette.glowColor
    val shadow = condition?.let { modulateSingleColor(palette.shadowColor, it) } ?: palette.shadowColor
    val accent = condition?.let { modulateSingleColor(palette.primaryAccent, it) } ?: palette.primaryAccent
    return AetherAtmosphere(
        palette = palette,
        weather = weather,
        colors = colors,
        glow = glow,
        shadow = shadow,
        accent = accent,
        accentSubtle = accent.copy(alpha = 0.20f),
        accentStrong = colors.getOrElse(2) { accent }
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
 * Resolves and provides the active atmosphere, including automatic weather when the
 * user is in Time + Weather mode. Weather is fetched on demand and cached; failures
 * degrade silently to a time-only palette with a truthful reason attached.
 */
@Composable
fun rememberAtmosphere(themeState: AppThemeState): AetherAtmosphere {
    val minuteOfDay = rememberLocalMinuteOfDay()
    val context = LocalContext.current
    val inspecting = LocalInspectionMode.current

    val palette = when (themeState.atmosphereMode) {
        AtmosphereMode.STATIC, AtmosphereMode.MANUAL -> themeState.manualAtmosphere
        AtmosphereMode.TIME_BASED, AtmosphereMode.TIME_AND_WEATHER ->
            TimeAtmospherePalette.forMinuteOfDay(minuteOfDay)
    }

    val wantsWeather = themeState.atmosphereMode == AtmosphereMode.TIME_AND_WEATHER

    // A manual override always wins so Appearance can exercise every condition.
    val override = themeState.weatherOverride

    val locationMode = themeState.weatherLocationMode
    val manualLocation = themeState.manualWeatherLocation

    LaunchedEffect(
        wantsWeather,
        override,
        locationMode,
        manualLocation,
        minuteOfDay / WEATHER_REFRESH_MINUTES,
        inspecting
    ) {
        if (inspecting) return@LaunchedEffect
        if (!wantsWeather || override != null) return@LaunchedEffect
        if (themeState.weatherReading !is WeatherReading.Known) {
            themeState.weatherReading = WeatherReading.Loading
        }
        themeState.weatherReading = AtmosphereWeatherService.read(
            context = context,
            locationMode = locationMode,
            manualLocation = manualLocation
        )
    }

    val weather = when {
        override != null -> WeatherReading.Override(override)
        !wantsWeather -> WeatherReading.Idle
        else -> themeState.weatherReading
    }

    return remember(palette, weather) { buildAtmosphere(palette, weather) }
}

private const val TICK_MILLIS = 30_000L
private const val WEATHER_REFRESH_MINUTES = 30

private fun currentMinuteOfDay(): Int {
    val calendar = Calendar.getInstance()
    return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
}
