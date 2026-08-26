package com.foresightlabs.aether.data.preferences

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.foresightlabs.aether.ui.theme.AccentColorChoice
import com.foresightlabs.aether.ui.theme.AppThemeMode
import com.foresightlabs.aether.ui.theme.AtmosphereMode
import com.foresightlabs.aether.ui.theme.MessageDensity
import com.foresightlabs.aether.ui.theme.TimeAtmospherePalette

/**
 * Bubble styling presets for custom conversation appearance.
 */
enum class ChatBubbleStyle(val displayName: String, val description: String) {
    ATMOSPHERE("Atmosphere", "Adapts dynamically to the ambient atmosphere"),
    GLASS("Glass", "Frosted translucent glass with luminous border"),
    MIDNIGHT("Midnight", "Deep near-black panels with subtle highlights"),
    EMBER("Ember", "Warm ember-infused gradient surfaces")
}

/**
 * Persistent global appearance configuration for Aether.
 */
@Immutable
data class AetherAppearancePreferences(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val atmosphereMode: AtmosphereMode = AtmosphereMode.TIME_AND_WEATHER,
    val manualAtmosphere: TimeAtmospherePalette = TimeAtmospherePalette.GOLDEN_HOUR,
    val useAtmosphereAccent: Boolean = true,
    val accentChoice: AccentColorChoice = AccentColorChoice.EMBER,
    val messageDensity: MessageDensity = MessageDensity.COMFORTABLE,
    val fontScale: Float = 1.0f
)

/**
 * Per-chat local appearance override.
 * Stored locally and keyed by Telegram chatId.
 */
@Immutable
data class ChatAppearanceOverride(
    val chatId: Long,
    val inheritGlobal: Boolean = true,
    val palette: TimeAtmospherePalette? = null,
    val bubbleStyle: ChatBubbleStyle = ChatBubbleStyle.ATMOSPHERE,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Fully resolved appearance for a conversation, combining global preferences
 * with any active per-chat override.
 */
@Immutable
data class ResolvedChatAppearance(
    val chatId: Long,
    val isCustom: Boolean,
    val palette: TimeAtmospherePalette,
    val bubbleStyle: ChatBubbleStyle,
    val useAtmosphereAccent: Boolean,
    val fixedAccent: Color?
)
