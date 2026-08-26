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
import com.foresightlabs.aether.data.preferences.AetherAppearancePreferences
import com.foresightlabs.aether.data.preferences.AppearanceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
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
        /**
         * Canonical Aether time bands, resolved at minute precision so the
         * half-hour boundaries (16:30, 19:30, 22:30) are honoured exactly.
         */
        fun forMinuteOfDay(minuteOfDay: Int): TimeAtmospherePalette {
            return when (minuteOfDay) {
                in 300 until 480 -> DAWN          // 05:00 - 07:59
                in 480 until 990 -> DAY           // 08:00 - 16:29
                in 990 until 1170 -> GOLDEN_HOUR  // 16:30 - 19:29
                in 1170 until 1350 -> EVENING     // 19:30 - 22:29
                else -> NIGHT                     // 22:30 - 04:59
            }
        }

        fun fromCurrentHour(): TimeAtmospherePalette {
            val calendar = Calendar.getInstance()
            return forMinuteOfDay(
                calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
            )
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
class AppThemeState(
    private val repository: AppearanceRepository? = null,
    private val coroutineScope: CoroutineScope? = null
) {
    var themeMode by mutableStateOf(AppThemeMode.SYSTEM)
    var atmosphereMode by mutableStateOf(AtmosphereMode.TIME_AND_WEATHER)
    var manualAtmosphere by mutableStateOf(TimeAtmospherePalette.GOLDEN_HOUR)
    var weatherReading by mutableStateOf<WeatherReading>(WeatherReading.Idle)
    var weatherOverride by mutableStateOf<WeatherCondition?>(null)
    var useAtmosphereAccent by mutableStateOf(true)
    var accentChoice by mutableStateOf(AccentColorChoice.EMBER)
    var messageDensity by mutableStateOf(MessageDensity.COMFORTABLE)
    var fontScale by mutableFloatStateOf(1.0f)
    var useAmoledBlack by mutableStateOf(false)

    init {
        repository?.let { repo ->
            val initial = repo.globalPreferences.value
            applyPreferences(initial)

            coroutineScope?.launch {
                repo.globalPreferences.collectLatest { prefs ->
                    applyPreferences(prefs)
                }
            }
        }
    }

    private fun applyPreferences(prefs: AetherAppearancePreferences) {
        themeMode = prefs.themeMode
        atmosphereMode = prefs.atmosphereMode
        manualAtmosphere = prefs.manualAtmosphere
        useAtmosphereAccent = prefs.useAtmosphereAccent
        accentChoice = prefs.accentChoice
        messageDensity = prefs.messageDensity
        fontScale = prefs.fontScale
    }

    fun setAndPersistThemeMode(mode: AppThemeMode) {
        themeMode = mode
        coroutineScope?.launch {
            repository?.updateThemeMode(mode)
        }
    }

    fun setAndPersistAtmosphereMode(mode: AtmosphereMode) {
        atmosphereMode = mode
        coroutineScope?.launch {
            repository?.updateAtmosphereMode(mode)
        }
    }

    fun setAndPersistManualAtmosphere(palette: TimeAtmospherePalette) {
        manualAtmosphere = palette
        coroutineScope?.launch {
            repository?.updateManualAtmosphere(palette)
        }
    }

    fun setAndPersistUseAtmosphereAccent(useAtmosphere: Boolean) {
        useAtmosphereAccent = useAtmosphere
        coroutineScope?.launch {
            repository?.updateUseAtmosphereAccent(useAtmosphere)
        }
    }

    fun setAndPersistAccentChoice(choice: AccentColorChoice) {
        accentChoice = choice
        coroutineScope?.launch {
            repository?.updateAccentChoice(choice)
        }
    }

    fun setAndPersistMessageDensity(density: MessageDensity) {
        messageDensity = density
        coroutineScope?.launch {
            repository?.updateMessageDensity(density)
        }
    }

    fun setAndPersistFontScale(scale: Float) {
        fontScale = scale
        coroutineScope?.launch {
            repository?.updateFontScale(scale)
        }
    }

    /**
     * Resolves the current active atmospheric palette based on mode.
     * Prefer [LocalAtmosphere] in composables — this exists for non-composable callers.
     */
    fun activePalette(): TimeAtmospherePalette {
        return when (atmosphereMode) {
            AtmosphereMode.STATIC, AtmosphereMode.MANUAL -> manualAtmosphere
            AtmosphereMode.TIME_BASED, AtmosphereMode.TIME_AND_WEATHER -> TimeAtmospherePalette.fromCurrentHour()
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
val LocalAppearanceRepository = staticCompositionLocalOf<AppearanceRepository> {
    error("AppearanceRepository not provided")
}
