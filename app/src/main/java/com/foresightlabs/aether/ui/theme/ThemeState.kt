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



enum class AtmosphereMode(val displayName: String, val description: String) {
    STATIC("Static", "Fixed palette of your choice"),
    TIME_BASED("Time of Day", "Automatically shifts from Dawn to Night"),
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
    // Every palette's gradient now runs from a restrained lavender/mist bloom
    // (colors[0], glow) down through graphite (colors[3], colors[4]) toward the
    // deep base the rest of Aether sits on — controlled dark atmosphere, not the
    // pastel-to-near-white ramps these used to be. Time-of-day is told apart by
    // hue, not by how bright the sky is allowed to get.
    DAWN(
        displayName = "Dawn",
        timeLabel = "05:00 - 08:00",
        primaryAccent = Color(0xFFB79294),
        colors = listOf(
            Color(0xFF7C5A5F),
            Color(0xFF65494D),
            Color(0xFF503A3D),
            Color(0xFF37292B),
            Color(0xFF211A1B)
        ),
        glowColor = Color(0xFF916E73),
        shadowColor = Color(0xFF1D1617)
    ),
    DAY(
        displayName = "Day",
        timeLabel = "08:00 - 16:30",
        primaryAccent = Color(0xFF7E97B4),
        colors = listOf(
            Color(0xFF546A83),
            Color(0xFF44556A),
            Color(0xFF364454),
            Color(0xFF27303A),
            Color(0xFF181D22)
        ),
        glowColor = Color(0xFF687E97),
        shadowColor = Color(0xFF15191E)
    ),
    GOLDEN_HOUR(
        displayName = "Golden Hour",
        timeLabel = "16:30 - 19:30",
        primaryAccent = Color(0xFFB89478),
        colors = listOf(
            Color(0xFF836854),
            Color(0xFF6A5444),
            Color(0xFF544336),
            Color(0xFF3A2F27),
            Color(0xFF221D18)
        ),
        glowColor = Color(0xFF977C68),
        shadowColor = Color(0xFF1E1915)
    ),
    EVENING(
        displayName = "Evening",
        timeLabel = "19:30 - 22:30",
        primaryAccent = Color(0xFF9186B0),
        colors = listOf(
            Color(0xFF605483),
            Color(0xFF4E446A),
            Color(0xFF3E3654),
            Color(0xFF2C273A),
            Color(0xFF1B1822)
        ),
        glowColor = Color(0xFF746897),
        shadowColor = Color(0xFF18151E)
    ),
    NIGHT(
        displayName = "Night",
        timeLabel = "22:30 - 05:00",
        primaryAccent = Color(0xFF7C85A8),
        colors = listOf(
            Color(0xFF515A85),
            Color(0xFF42496C),
            Color(0xFF343A55),
            Color(0xFF26293B),
            Color(0xFF181A23)
        ),
        glowColor = Color(0xFF656E9A),
        shadowColor = Color(0xFF15161E)
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
    var atmosphereMode by mutableStateOf(AtmosphereMode.TIME_BASED)
    var manualAtmosphere by mutableStateOf(TimeAtmospherePalette.GOLDEN_HOUR)
    var useAtmosphereAccent by mutableStateOf(true)
    var accentChoice by mutableStateOf(AccentColorChoice.MIST_BLUE)
    var messageDensity by mutableStateOf(MessageDensity.COMFORTABLE)
    var fontScale by mutableFloatStateOf(1.0f)

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
        atmosphereMode = prefs.atmosphereMode
        manualAtmosphere = prefs.manualAtmosphere
        useAtmosphereAccent = prefs.useAtmosphereAccent
        accentChoice = prefs.accentChoice
        messageDensity = prefs.messageDensity
        fontScale = prefs.fontScale
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
            AtmosphereMode.TIME_BASED -> TimeAtmospherePalette.fromCurrentHour()
        }
    }
}

val LocalAppThemeState = staticCompositionLocalOf { AppThemeState() }
val LocalAppearanceRepository = staticCompositionLocalOf<AppearanceRepository> {
    error("AppearanceRepository not provided")
}
