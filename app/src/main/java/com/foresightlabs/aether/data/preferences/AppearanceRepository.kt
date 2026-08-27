package com.foresightlabs.aether.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.foresightlabs.aether.ui.theme.AccentColorChoice
import com.foresightlabs.aether.ui.theme.AppThemeMode
import com.foresightlabs.aether.ui.theme.AtmosphereMode
import com.foresightlabs.aether.ui.theme.MessageDensity
import com.foresightlabs.aether.ui.theme.TimeAtmospherePalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

import androidx.datastore.preferences.core.doublePreferencesKey
import com.foresightlabs.aether.data.preferences.ManualWeatherLocation
import com.foresightlabs.aether.data.preferences.WeatherLocationMode

private val Context.appearanceDataStore: DataStore<Preferences> by preferencesDataStore(name = "aether_appearance_prefs")

/**
 * Single authoritative repository for persistent global and per-chat appearance preferences.
 */
class AppearanceRepository(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ATMOSPHERE_MODE = stringPreferencesKey("atmosphere_mode")
        val MANUAL_ATMOSPHERE = stringPreferencesKey("manual_atmosphere")
        val USE_ATMOSPHERE_ACCENT = booleanPreferencesKey("use_atmosphere_accent")
        val ACCENT_CHOICE = stringPreferencesKey("accent_choice")
        val MESSAGE_DENSITY = stringPreferencesKey("message_density")
        val FONT_SCALE = floatPreferencesKey("font_scale")

        val WEATHER_LOCATION_MODE = stringPreferencesKey("weather_location_mode")
        val WEATHER_MANUAL_NAME = stringPreferencesKey("weather_manual_name")
        val WEATHER_MANUAL_ADMIN1 = stringPreferencesKey("weather_manual_admin1")
        val WEATHER_MANUAL_COUNTRY = stringPreferencesKey("weather_manual_country")
        val WEATHER_MANUAL_LAT = doublePreferencesKey("weather_manual_lat")
        val WEATHER_MANUAL_LON = doublePreferencesKey("weather_manual_lon")
        val WEATHER_MANUAL_TIMEZONE = stringPreferencesKey("weather_manual_timezone")

        fun chatKey(chatId: Long) = stringPreferencesKey("chat_appearance_$chatId")
    }

    val globalPreferences: StateFlow<AetherAppearancePreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs -> mapPreferences(prefs) }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = AetherAppearancePreferences()
        )

    private fun mapPreferences(prefs: Preferences): AetherAppearancePreferences {
        val themeMode = runCatching {
            prefs[PreferencesKeys.THEME_MODE]?.let { AppThemeMode.valueOf(it) }
        }.getOrNull() ?: AppThemeMode.SYSTEM

        val atmosphereMode = runCatching {
            prefs[PreferencesKeys.ATMOSPHERE_MODE]?.let { AtmosphereMode.valueOf(it) }
        }.getOrNull() ?: AtmosphereMode.TIME_AND_WEATHER

        val manualAtmosphere = runCatching {
            prefs[PreferencesKeys.MANUAL_ATMOSPHERE]?.let { TimeAtmospherePalette.valueOf(it) }
        }.getOrNull() ?: TimeAtmospherePalette.GOLDEN_HOUR

        val useAtmosphereAccent = prefs[PreferencesKeys.USE_ATMOSPHERE_ACCENT] ?: true

        val accentChoice = prefs[PreferencesKeys.ACCENT_CHOICE]?.let { savedId ->
            AccentColorChoice.fromId(savedId)
        } ?: AccentColorChoice.MIST_BLUE

        // If the saved choice is null but we had a value, it means the old accent is deprecated.
        // Fallback to atmosphere accent in that case.
        val resolvedUseAtmosphereAccent = if (useAtmosphereAccent) {
            true
        } else {
            // If they had a fixed accent, but it's not in the new palette (deprecated),
            // reset to following the atmosphere.
            val choice = prefs[PreferencesKeys.ACCENT_CHOICE]?.let { AccentColorChoice.fromId(it) }
            choice == null
        }

        val messageDensity = runCatching {
            prefs[PreferencesKeys.MESSAGE_DENSITY]?.let { MessageDensity.valueOf(it) }
        }.getOrNull() ?: MessageDensity.COMFORTABLE

        val fontScale = prefs[PreferencesKeys.FONT_SCALE] ?: 1.0f

        val weatherLocationMode = runCatching {
            prefs[PreferencesKeys.WEATHER_LOCATION_MODE]?.let { WeatherLocationMode.valueOf(it) }
        }.getOrNull() ?: WeatherLocationMode.AUTOMATIC

        val manualName = prefs[PreferencesKeys.WEATHER_MANUAL_NAME]
        val manualLat = prefs[PreferencesKeys.WEATHER_MANUAL_LAT]
        val manualLon = prefs[PreferencesKeys.WEATHER_MANUAL_LON]
        val manualLocation = if (!manualName.isNullOrBlank() && manualLat != null && manualLon != null) {
            ManualWeatherLocation(
                name = manualName,
                admin1 = prefs[PreferencesKeys.WEATHER_MANUAL_ADMIN1],
                country = prefs[PreferencesKeys.WEATHER_MANUAL_COUNTRY],
                latitude = manualLat,
                longitude = manualLon,
                timezone = prefs[PreferencesKeys.WEATHER_MANUAL_TIMEZONE]
            )
        } else null

        return AetherAppearancePreferences(
            themeMode = themeMode,
            atmosphereMode = atmosphereMode,
            manualAtmosphere = manualAtmosphere,
            useAtmosphereAccent = resolvedUseAtmosphereAccent,
            accentChoice = accentChoice,
            messageDensity = messageDensity,
            fontScale = fontScale,
            weatherLocationMode = weatherLocationMode,
            manualWeatherLocation = manualLocation
        )
    }

    suspend fun updateThemeMode(themeMode: AppThemeMode) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.THEME_MODE] = themeMode.name
        }
    }

    suspend fun updateAtmosphereMode(atmosphereMode: AtmosphereMode) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.ATMOSPHERE_MODE] = atmosphereMode.name
        }
    }

    suspend fun updateManualAtmosphere(palette: TimeAtmospherePalette) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.MANUAL_ATMOSPHERE] = palette.name
        }
    }

    suspend fun updateUseAtmosphereAccent(useAtmosphereAccent: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.USE_ATMOSPHERE_ACCENT] = useAtmosphereAccent
        }
    }

    suspend fun updateAccentChoice(choice: AccentColorChoice) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.ACCENT_CHOICE] = choice.id
        }
    }

    suspend fun updateMessageDensity(density: MessageDensity) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.MESSAGE_DENSITY] = density.name
        }
    }

    suspend fun updateFontScale(fontScale: Float) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.FONT_SCALE] = fontScale
        }
    }

    suspend fun updateWeatherLocationMode(mode: WeatherLocationMode) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.WEATHER_LOCATION_MODE] = mode.name
        }
    }

    suspend fun setManualWeatherLocation(location: ManualWeatherLocation) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.WEATHER_LOCATION_MODE] = WeatherLocationMode.MANUAL.name
            prefs[PreferencesKeys.WEATHER_MANUAL_NAME] = location.name
            if (location.admin1 != null) {
                prefs[PreferencesKeys.WEATHER_MANUAL_ADMIN1] = location.admin1
            } else {
                prefs.remove(PreferencesKeys.WEATHER_MANUAL_ADMIN1)
            }
            if (location.country != null) {
                prefs[PreferencesKeys.WEATHER_MANUAL_COUNTRY] = location.country
            } else {
                prefs.remove(PreferencesKeys.WEATHER_MANUAL_COUNTRY)
            }
            prefs[PreferencesKeys.WEATHER_MANUAL_LAT] = location.latitude
            prefs[PreferencesKeys.WEATHER_MANUAL_LON] = location.longitude
            if (location.timezone != null) {
                prefs[PreferencesKeys.WEATHER_MANUAL_TIMEZONE] = location.timezone
            } else {
                prefs.remove(PreferencesKeys.WEATHER_MANUAL_TIMEZONE)
            }
        }
    }

    suspend fun clearManualWeatherLocation() {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.WEATHER_LOCATION_MODE] = WeatherLocationMode.AUTOMATIC.name
            prefs.remove(PreferencesKeys.WEATHER_MANUAL_NAME)
            prefs.remove(PreferencesKeys.WEATHER_MANUAL_ADMIN1)
            prefs.remove(PreferencesKeys.WEATHER_MANUAL_COUNTRY)
            prefs.remove(PreferencesKeys.WEATHER_MANUAL_LAT)
            prefs.remove(PreferencesKeys.WEATHER_MANUAL_LON)
            prefs.remove(PreferencesKeys.WEATHER_MANUAL_TIMEZONE)
        }
    }

    suspend fun updateGlobalPreferences(transform: (AetherAppearancePreferences) -> AetherAppearancePreferences) {
        val current = globalPreferences.value
        val updated = transform(current)
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.THEME_MODE] = updated.themeMode.name
            prefs[PreferencesKeys.ATMOSPHERE_MODE] = updated.atmosphereMode.name
            prefs[PreferencesKeys.MANUAL_ATMOSPHERE] = updated.manualAtmosphere.name
            prefs[PreferencesKeys.USE_ATMOSPHERE_ACCENT] = updated.useAtmosphereAccent
            prefs[PreferencesKeys.ACCENT_CHOICE] = updated.accentChoice.name
            prefs[PreferencesKeys.MESSAGE_DENSITY] = updated.messageDensity.name
            prefs[PreferencesKeys.FONT_SCALE] = updated.fontScale
            prefs[PreferencesKeys.WEATHER_LOCATION_MODE] = updated.weatherLocationMode.name
            if (updated.manualWeatherLocation != null) {
                prefs[PreferencesKeys.WEATHER_MANUAL_NAME] = updated.manualWeatherLocation.name
                if (updated.manualWeatherLocation.admin1 != null) {
                    prefs[PreferencesKeys.WEATHER_MANUAL_ADMIN1] = updated.manualWeatherLocation.admin1
                }
                if (updated.manualWeatherLocation.country != null) {
                    prefs[PreferencesKeys.WEATHER_MANUAL_COUNTRY] = updated.manualWeatherLocation.country
                }
                prefs[PreferencesKeys.WEATHER_MANUAL_LAT] = updated.manualWeatherLocation.latitude
                prefs[PreferencesKeys.WEATHER_MANUAL_LON] = updated.manualWeatherLocation.longitude
                if (updated.manualWeatherLocation.timezone != null) {
                    prefs[PreferencesKeys.WEATHER_MANUAL_TIMEZONE] = updated.manualWeatherLocation.timezone
                }
            } else {
                prefs.remove(PreferencesKeys.WEATHER_MANUAL_NAME)
                prefs.remove(PreferencesKeys.WEATHER_MANUAL_ADMIN1)
                prefs.remove(PreferencesKeys.WEATHER_MANUAL_COUNTRY)
                prefs.remove(PreferencesKeys.WEATHER_MANUAL_LAT)
                prefs.remove(PreferencesKeys.WEATHER_MANUAL_LON)
                prefs.remove(PreferencesKeys.WEATHER_MANUAL_TIMEZONE)
            }
        }
    }

    // --- PER-CHAT APPEARANCE OVERRIDES ---

    fun getChatAppearanceFlow(chatId: Long): Flow<ChatAppearanceOverride> {
        val key = PreferencesKeys.chatKey(chatId)
        return dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs ->
                val serialized = prefs[key]
                parseChatAppearance(chatId, serialized)
            }
            .distinctUntilChanged()
    }

    fun getResolvedChatAppearanceFlow(chatId: Long): Flow<ResolvedChatAppearance> {
        return combine(globalPreferences, getChatAppearanceFlow(chatId)) { global, override ->
            resolveChatAppearance(chatId, global, override)
        }.distinctUntilChanged()
    }

    fun resolveChatAppearance(
        chatId: Long,
        global: AetherAppearancePreferences,
        override: ChatAppearanceOverride
    ): ResolvedChatAppearance {
        val isCustom = !override.inheritGlobal && override.palette != null
        val resolvedPalette = if (isCustom) {
            override.palette ?: global.manualAtmosphere
        } else {
            when (global.atmosphereMode) {
                AtmosphereMode.STATIC, AtmosphereMode.MANUAL -> global.manualAtmosphere
                AtmosphereMode.TIME_BASED, AtmosphereMode.TIME_AND_WEATHER -> TimeAtmospherePalette.fromCurrentHour()
            }
        }
        val bubbleStyle = if (isCustom) override.bubbleStyle else ChatBubbleStyle.ATMOSPHERE
        val useAtmosphereAccent = if (isCustom) true else global.useAtmosphereAccent
        val fixedAccent = if (isCustom || global.useAtmosphereAccent) null else global.accentChoice.primaryColor

        return ResolvedChatAppearance(
            chatId = chatId,
            isCustom = isCustom,
            palette = resolvedPalette,
            bubbleStyle = bubbleStyle,
            useAtmosphereAccent = useAtmosphereAccent,
            fixedAccent = fixedAccent
        )
    }

    suspend fun setChatAppearance(
        chatId: Long,
        inheritGlobal: Boolean,
        palette: TimeAtmospherePalette?,
        bubbleStyle: ChatBubbleStyle = ChatBubbleStyle.ATMOSPHERE
    ) {
        val key = PreferencesKeys.chatKey(chatId)
        val serialized = "${inheritGlobal}|${palette?.name ?: ""}|${bubbleStyle.name}|${System.currentTimeMillis()}"
        dataStore.edit { prefs ->
            prefs[key] = serialized
        }
    }

    suspend fun resetChatAppearance(chatId: Long) {
        val key = PreferencesKeys.chatKey(chatId)
        dataStore.edit { prefs ->
            prefs.remove(key)
        }
    }

    private fun parseChatAppearance(chatId: Long, serialized: String?): ChatAppearanceOverride {
        if (serialized.isNullOrBlank()) {
            return ChatAppearanceOverride(chatId = chatId, inheritGlobal = true)
        }
        val parts = serialized.split("|")
        val inheritGlobal = parts.getOrNull(0)?.toBooleanStrictOrNull() ?: true
        val paletteName = parts.getOrNull(1)
        val palette = if (!paletteName.isNullOrBlank()) {
            runCatching { TimeAtmospherePalette.valueOf(paletteName) }.getOrNull()
        } else null
        val bubbleStyleName = parts.getOrNull(2)
        val bubbleStyle = if (!bubbleStyleName.isNullOrBlank()) {
            runCatching { ChatBubbleStyle.valueOf(bubbleStyleName) }.getOrNull() ?: ChatBubbleStyle.ATMOSPHERE
        } else ChatBubbleStyle.ATMOSPHERE
        val updatedAt = parts.getOrNull(3)?.toLongOrNull() ?: System.currentTimeMillis()

        return ChatAppearanceOverride(
            chatId = chatId,
            inheritGlobal = inheritGlobal,
            palette = palette,
            bubbleStyle = bubbleStyle,
            updatedAt = updatedAt
        )
    }

    companion object {
        @Volatile
        private var INSTANCE: AppearanceRepository? = null

        fun getInstance(context: Context): AppearanceRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppearanceRepository(
                    dataStore = context.applicationContext.appearanceDataStore
                ).also { INSTANCE = it }
            }
        }
    }
}
