package com.foresightlabs.aether.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import java.util.Calendar

enum class AppThemeMode {
    DARK,
    LIGHT,
    OLED,
    SYSTEM
}

enum class AtmosphereMode(val displayName: String, val description: String) {
    STATIC("Static", "Fixed palette of your choice"),
    TIME_BASED("Time of Day", "Automatically shifts from Dawn to Night"),
    TIME_AND_WEATHER("Time + Weather", "Time palette subtly modulated by weather"),
    MANUAL("Manual", "Select any atmospheric mood directly")
}

enum class TimeAtmospherePalette(
    val displayName: String,
    val timeLabel: String,
    val primaryAccent: Color,
    val colors: List<Color>,
    val glowColor: Color,
    val shadowColor: Color
) {
    DAWN(
        displayName = "Dawn",
        timeLabel = "05:00 - 08:00",
        primaryAccent = Color(0xFFFF8E72),
        colors = listOf(
            Color(0xFFFFB088),
            Color(0xFFFF8E72),
            Color(0xFFE56B8B),
            Color(0xFFB04B99),
            Color(0xFF6B2D5C)
        ),
        glowColor = Color(0xFFFFC2A6),
        shadowColor = Color(0xFF4A1838)
    ),
    DAY(
        displayName = "Day",
        timeLabel = "08:00 - 16:30",
        primaryAccent = Color(0xFF38BDF8),
        colors = listOf(
            Color(0xFF60A5FA),
            Color(0xFF38BDF8),
            Color(0xFF0284C7),
            Color(0xFF2563EB),
            Color(0xFF1E3A8A)
        ),
        glowColor = Color(0xFF93C5FD),
        shadowColor = Color(0xFF0F172A)
    ),
    GOLDEN_HOUR(
        displayName = "Golden Hour (Ember)",
        timeLabel = "16:30 - 19:30",
        primaryAccent = Color(0xFFFF7038),
        colors = listOf(
            Color(0xFFFF9A4A),
            Color(0xFFFF7038),
            Color(0xFFF04425),
            Color(0xFFE92D27),
            Color(0xFFC90B27)
        ),
        glowColor = Color(0xFFFFAA5C),
        shadowColor = Color(0xFF8B1225)
    ),
    EVENING(
        displayName = "Evening",
        timeLabel = "19:30 - 22:30",
        primaryAccent = Color(0xFFD946EF),
        colors = listOf(
            Color(0xFFE879F9),
            Color(0xFFC026D3),
            Color(0xFF8B5CF6),
            Color(0xFF6366F1),
            Color(0xFF1E1B4B)
        ),
        glowColor = Color(0xFFF0ABFC),
        shadowColor = Color(0xFF3B0764)
    ),
    NIGHT(
        displayName = "Night",
        timeLabel = "22:30 - 05:00",
        primaryAccent = Color(0xFF818CF8),
        colors = listOf(
            Color(0xFF818CF8),
            Color(0xFF6366F1),
            Color(0xFF4F46E5),
            Color(0xFF312E81),
            Color(0xFF0D0B18)
        ),
        glowColor = Color(0xFFA5B4FC),
        shadowColor = Color(0xFF030712)
    );

    companion object {
        fun fromCurrentHour(): TimeAtmospherePalette {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            return when (hour) {
                in 5..7 -> DAWN
                in 8..16 -> DAY
                in 17..19 -> GOLDEN_HOUR
                in 20..22 -> EVENING
                else -> NIGHT
            }
        }
    }
}

enum class WeatherCondition(val displayName: String, val icon: String, val description: String) {
    CLEAR("Clear", "☀️", "Brighter, vivid atmospheric saturation"),
    CLOUDY("Cloudy", "☁️", "Muted, subtle soft tones"),
    RAIN("Rain", "🌧️", "Cooler, oceanic cyan-blue tint"),
    STORM("Storm", "⚡", "Darker electric indigo shift"),
    FOG("Fog", "🌫️", "Soft desaturated lilac mist"),
    SNOW("Snow", "❄️", "Icy crystalline highlights")
}

enum class AccentColorChoice(val displayName: String, val primaryColor: Color, val containerColor: Color) {
    EMBER("Ember Orange", Color(0xFFFF7038), Color(0xFFF04425)),
    CRIMSON("Crimson", Color(0xFFE92D27), Color(0xFFC90B27)),
    AMBER("Warm Amber", Color(0xFFFF9A4A), Color(0xFFFF7038)),
    CORAL("Coral Glow", Color(0xFFFF5E4D), Color(0xFFDC2626)),
    COBALT("Electric Blue", Color(0xFF4DA3FF), Color(0xFF173252)),
    EMERALD("Emerald", Color(0xFF10B981), Color(0xFF064E3B))
}

enum class MessageDensity {
    COMFORTABLE,
    COMPACT
}

@Stable
class AppThemeState {
    var themeMode by mutableStateOf(AppThemeMode.DARK)
    var atmosphereMode by mutableStateOf(AtmosphereMode.TIME_BASED)
    var manualAtmosphere by mutableStateOf(TimeAtmospherePalette.GOLDEN_HOUR)
    var weatherCondition by mutableStateOf(WeatherCondition.CLEAR)
    var accentChoice by mutableStateOf(AccentColorChoice.EMBER)
    var messageDensity by mutableStateOf(MessageDensity.COMFORTABLE)
    var fontScale by mutableFloatStateOf(1.0f)
    var useAmoledBlack by mutableStateOf(false)

    /**
     * Resolves the current active atmospheric palette based on mode and optional weather.
     */
    fun activePalette(): TimeAtmospherePalette {
        return when (atmosphereMode) {
            AtmosphereMode.STATIC, AtmosphereMode.MANUAL -> manualAtmosphere
            AtmosphereMode.TIME_BASED, AtmosphereMode.TIME_AND_WEATHER -> TimeAtmospherePalette.fromCurrentHour()
        }
    }

    /**
     * Resolves the active modulated colors.
     */
    fun resolvedAtmosphereColors(): List<Color> {
        val base = activePalette().colors
        return if (atmosphereMode == AtmosphereMode.TIME_AND_WEATHER) {
            modulatePaletteWithWeather(base, weatherCondition)
        } else {
            base
        }
    }

    fun resolvedGlowColor(): Color {
        val baseGlow = activePalette().glowColor
        return if (atmosphereMode == AtmosphereMode.TIME_AND_WEATHER) {
            modulateSingleColor(baseGlow, weatherCondition)
        } else {
            baseGlow
        }
    }

    fun resolvedShadowColor(): Color {
        val baseShadow = activePalette().shadowColor
        return if (atmosphereMode == AtmosphereMode.TIME_AND_WEATHER) {
            modulateSingleColor(baseShadow, weatherCondition)
        } else {
            baseShadow
        }
    }
}

fun modulatePaletteWithWeather(baseColors: List<Color>, weather: WeatherCondition): List<Color> {
    return baseColors.map { modulateSingleColor(it, weather) }
}

fun modulateSingleColor(c: Color, weather: WeatherCondition): Color {
    return when (weather) {
        WeatherCondition.CLEAR -> {
            Color(
                red = (c.red * 1.04f).coerceIn(0f, 1f),
                green = (c.green * 1.04f).coerceIn(0f, 1f),
                blue = (c.blue * 1.04f).coerceIn(0f, 1f),
                alpha = c.alpha
            )
        }
        WeatherCondition.CLOUDY -> {
            val gray = (c.red * 0.3f + c.green * 0.59f + c.blue * 0.11f)
            Color(
                red = (c.red * 0.82f + gray * 0.18f).coerceIn(0f, 1f),
                green = (c.green * 0.82f + gray * 0.18f).coerceIn(0f, 1f),
                blue = (c.blue * 0.82f + gray * 0.18f).coerceIn(0f, 1f),
                alpha = c.alpha
            )
        }
        WeatherCondition.RAIN -> {
            Color(
                red = (c.red * 0.82f).coerceIn(0f, 1f),
                green = (c.green * 0.90f + 0.03f).coerceIn(0f, 1f),
                blue = (c.blue * 0.95f + 0.08f).coerceIn(0f, 1f),
                alpha = c.alpha
            )
        }
        WeatherCondition.STORM -> {
            Color(
                red = (c.red * 0.80f + 0.04f).coerceIn(0f, 1f),
                green = (c.green * 0.70f).coerceIn(0f, 1f),
                blue = (c.blue * 0.92f + 0.09f).coerceIn(0f, 1f),
                alpha = c.alpha
            )
        }
        WeatherCondition.FOG -> {
            val gray = (c.red * 0.3f + c.green * 0.59f + c.blue * 0.11f)
            Color(
                red = (c.red * 0.75f + gray * 0.22f + 0.03f).coerceIn(0f, 1f),
                green = (c.green * 0.75f + gray * 0.22f).coerceIn(0f, 1f),
                blue = (c.blue * 0.78f + gray * 0.18f + 0.05f).coerceIn(0f, 1f),
                alpha = c.alpha
            )
        }
        WeatherCondition.SNOW -> {
            Color(
                red = (c.red * 0.88f + 0.08f).coerceIn(0f, 1f),
                green = (c.green * 0.92f + 0.10f).coerceIn(0f, 1f),
                blue = (c.blue * 0.95f + 0.14f).coerceIn(0f, 1f),
                alpha = c.alpha
            )
        }
    }
}

val LocalAppThemeState = staticCompositionLocalOf { AppThemeState() }
