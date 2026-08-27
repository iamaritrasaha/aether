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
import com.foresightlabs.aether.data.preferences.ManualWeatherLocation
import com.foresightlabs.aether.data.preferences.WeatherLocationMode
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
    PARTLY_CLOUDY("Partly Cloudy", "⛅", "Soft drifting cloud layers with warm light"),
    CLOUDY("Cloudy", "☁️", "Muted, subtle soft tones"),
    DRIZZLE("Drizzle", "🌦️", "Gentle translucent mist and light rain"),
    RAIN("Rain", "🌧️", "Cooler, oceanic cyan-blue tint"),
    HEAVY_RAIN("Heavy Rain", "🌧️", "Deep atmospheric depth and dense rain"),
    STORM("Storm", "⚡", "Darker electric indigo shift"),
    FOG("Fog", "🌫️", "Soft desaturated lilac mist"),
    SNOW("Snow", "❄️", "Icy crystalline highlights"),
    UNKNOWN("Unknown", "🌤️", "Atmospheric time-based sky")
}

enum class AccentColorChoice(
    val id: String,
    val displayName: String,
    val primaryColor: Color,
    val containerColor: Color,
    val onAccent: Color = Color.White
) {
    MIST_BLUE("mist_blue", "Mist Blue", Color(0xFF8FAFC4), Color(0xFF718EA3), Color(0xFF171719)),
    DUSTY_DENIM("dusty_denim", "Dusty Denim", Color(0xFF718EA3), Color(0xFF5A7283)),
    SAGE("sage", "Sage", Color(0xFFAEB8A0), Color(0xFF8B9481), Color(0xFF171719)),
    EUCALYPTUS("eucalyptus", "Eucalyptus", Color(0xFF809A88), Color(0xFF667B6D)),
    DUSTY_ROSE("dusty_rose", "Dusty Rose", Color(0xFFC28F99), Color(0xFF9B727A), Color(0xFF171719)),
    MAUVE("mauve", "Mauve", Color(0xFFA58A9D), Color(0xFF846E7E)),
    SOFT_LAVENDER("soft_lavender", "Soft Lavender", Color(0xFF9690B5), Color(0xFF787391)),
    PLUM_DUST("plum_dust", "Plum Dust", Color(0xFF806879), Color(0xFF665361)),
    CLAY("clay", "Clay", Color(0xFFB6816C), Color(0xFF926756)),
    TERRACOTTA("terracotta", "Terracotta", Color(0xFFAD705F), Color(0xFF8A594C)),
    MUTED_GOLD("muted_gold", "Muted Gold", Color(0xFFB8A06D), Color(0xFF938057), Color(0xFF171719)),
    MUSHROOM_TAUPE("mushroom_taupe", "Mushroom Taupe", Color(0xFF968A80), Color(0xFF786E66));

    companion object {
        fun fromId(id: String?): AccentColorChoice? {
            return entries.find { it.id == id || it.name == id }
        }
    }
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
    var accentChoice by mutableStateOf(AccentColorChoice.MIST_BLUE)
    var messageDensity by mutableStateOf(MessageDensity.COMFORTABLE)
    var fontScale by mutableFloatStateOf(1.0f)
    var useAmoledBlack by mutableStateOf(false)
    var weatherLocationMode by mutableStateOf(WeatherLocationMode.AUTOMATIC)
    var manualWeatherLocation by mutableStateOf<ManualWeatherLocation?>(null)

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
        weatherLocationMode = prefs.weatherLocationMode
        manualWeatherLocation = prefs.manualWeatherLocation
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

    fun setAndPersistWeatherLocationMode(mode: WeatherLocationMode) {
        weatherLocationMode = mode
        coroutineScope?.launch {
            repository?.updateWeatherLocationMode(mode)
        }
    }

    fun setAndPersistManualWeatherLocation(location: ManualWeatherLocation) {
        weatherLocationMode = WeatherLocationMode.MANUAL
        manualWeatherLocation = location
        coroutineScope?.launch {
            repository?.setManualWeatherLocation(location)
        }
    }

    fun clearManualWeatherLocation() {
        weatherLocationMode = WeatherLocationMode.AUTOMATIC
        manualWeatherLocation = null
        coroutineScope?.launch {
            repository?.clearManualWeatherLocation()
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
        WeatherCondition.PARTLY_CLOUDY -> {
            val gray = (c.red * 0.3f + c.green * 0.59f + c.blue * 0.11f)
            Color(
                red = (c.red * 0.92f + gray * 0.08f).coerceIn(0f, 1f),
                green = (c.green * 0.92f + gray * 0.08f).coerceIn(0f, 1f),
                blue = (c.blue * 0.92f + gray * 0.08f).coerceIn(0f, 1f),
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
        WeatherCondition.DRIZZLE -> {
            Color(
                red = (c.red * 0.86f).coerceIn(0f, 1f),
                green = (c.green * 0.92f + 0.02f).coerceIn(0f, 1f),
                blue = (c.blue * 0.96f + 0.05f).coerceIn(0f, 1f),
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
        WeatherCondition.HEAVY_RAIN -> {
            Color(
                red = (c.red * 0.74f).coerceIn(0f, 1f),
                green = (c.green * 0.82f + 0.04f).coerceIn(0f, 1f),
                blue = (c.blue * 0.92f + 0.10f).coerceIn(0f, 1f),
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
        WeatherCondition.UNKNOWN -> c
    }
}

val LocalAppThemeState = staticCompositionLocalOf { AppThemeState() }
val LocalAppearanceRepository = staticCompositionLocalOf<AppearanceRepository> {
    error("AppearanceRepository not provided")
}
