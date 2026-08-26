package com.foresightlabs.aether.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Aether themes change material semantics, not merely background colors.
// Atmosphere typography and foreground-surface typography resolve independently.
// Light mode uses graphite typography on porcelain surfaces while the atmosphere remains luminous.
// Aether Ember Core Dark Surfaces (Near-black, never blue-black)
val DarkBackground = Color(0xFF0A0A0B)
val DarkSurface = Color(0xFF101011)
val DarkSurfaceElevated = Color(0xFF171718)
val DarkSurfaceGlass = Color(0xEA1C1B1B)
val DarkSurfaceHighlight = Color(0xFF242323)
val DarkBorder = Color(0x1AFFFFFF)
val DarkBorderSubtle = Color(0x0EFFFFFF)

// Aether Ember Surface Typography (Near-black / dark glass foreground)
val DarkTextPrimary = Color(0xFFFFFFFF)
val DarkTextSecondary = Color(0xFFA6A6AC)
val DarkTextTertiary = Color(0xFF72727A)
val DarkTextMuted = Color(0xFF55555C)

// Aether Living Atmosphere Typography (Directly over dynamic gradients)
val AtmosphereTextPrimary = Color(0xFFFFFFFF)
val AtmosphereTextSecondary = Color(0xF0FFFFFF)
val AtmosphereTextTertiary = Color(0xD9FFFFFF)
val AtmosphereTextMuted = Color(0xB8FFFFFF)

// Message Bubble Colors
val DarkBubbleOutgoing = Color(0xFFF04425)
val DarkBubbleOutgoingEnd = Color(0xFFC90B27)
val DarkBubbleOutgoingText = Color(0xFFFFFFFF)
val DarkBubbleIncoming = Color(0x30FFFFFF)
val DarkBubbleIncomingBorder = Color(0x26FFFFFF)
val DarkBubbleIncomingText = Color(0xFFFFFFFF)

// Core OLED Palette
val OledBackground = Color(0xFF000000)
val OledSurface = Color(0xFF0A0A0B)
val OledSurfaceElevated = Color(0xFF101011)

// Core Light Theme Palette (Clean warm editorial fallback)
val LightBackground = Color(0xFFF7F6F3)
val LightSurface = Color(0xFFFBFAF8)
val LightSurfaceElevated = Color(0xFFF1F0ED)
val LightSurfaceHighlight = Color(0xFFECEAE6)
val LightBorder = Color(0x1F242329)
val LightBorderSubtle = Color(0x14242329)

// Porcelain surfaces need graphite, not dark-theme white typography.
val LightTextPrimary = Color(0xFF171719)
val LightTextSecondary = Color(0xFF4D4D52)
val LightTextTertiary = Color(0xFF74747C)
val LightTextMuted = Color(0xFF929299)

val LightBubbleOutgoing = Color(0xFFFF7038)
val LightBubbleOutgoingEnd = Color(0xFFF04425)
val LightBubbleOutgoingText = Color(0xFFFFFFFF)
val LightBubbleIncoming = Color(0xFFFFFFFF)
val LightBubbleIncomingText = Color(0xFF141416)

// Accent Colors - Ember default
val AccentEmber = Color(0xFFFF7038)
val AccentEmberSubtle = Color(0x29FF7038)
val AccentEmberVermilion = Color(0xFFF04425)
val OnlineGreen = Color(0xFF34D399)
val MutedIcon = Color(0xFFA6A6AC)
val VerifiedBadge = Color(0xFFFF9A4A)
val PinColor = Color(0xFFFF7038)
val UnreadBadge = Color(0xFFFF7038)

@Immutable
data class AetherColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceHighlight: Color,
    val surfaceGlass: Color = DarkSurfaceGlass,
    val border: Color,
    val borderSubtle: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textMuted: Color = DarkTextMuted,
    val input: Color = DarkSurfaceHighlight,
    val divider: Color = DarkBorderSubtle,
    val atmosphereTextPrimary: Color = AtmosphereTextPrimary,
    val atmosphereTextSecondary: Color = AtmosphereTextSecondary,
    val atmosphereTextTertiary: Color = AtmosphereTextTertiary,
    val atmosphereTextMuted: Color = AtmosphereTextMuted,
    val accent: Color,
    val accentSubtle: Color,
    val bubbleOutgoing: Color,
    val bubbleOutgoingEnd: Color = DarkBubbleOutgoingEnd,
    val bubbleOutgoingText: Color,
    val bubbleIncoming: Color,
    val bubbleIncomingText: Color,
    val isDark: Boolean,
    val isOled: Boolean = false
)

val LocalAetherColors = staticCompositionLocalOf {
    AetherColors(
        background = DarkBackground,
        surface = DarkSurface,
        surfaceElevated = DarkSurfaceElevated,
        surfaceHighlight = DarkSurfaceHighlight,
        surfaceGlass = DarkSurfaceGlass,
        border = DarkBorder,
        borderSubtle = DarkBorderSubtle,
        textPrimary = DarkTextPrimary,
        textSecondary = DarkTextSecondary,
        textTertiary = DarkTextTertiary,
        textMuted = DarkTextMuted,
        input = DarkSurfaceHighlight,
        divider = DarkBorderSubtle,
        atmosphereTextPrimary = AtmosphereTextPrimary,
        atmosphereTextSecondary = AtmosphereTextSecondary,
        atmosphereTextTertiary = AtmosphereTextTertiary,
        atmosphereTextMuted = AtmosphereTextMuted,
        accent = AccentEmber,
        accentSubtle = AccentEmberSubtle,
        bubbleOutgoing = DarkBubbleOutgoing,
        bubbleOutgoingEnd = DarkBubbleOutgoingEnd,
        bubbleOutgoingText = DarkBubbleOutgoingText,
        bubbleIncoming = DarkBubbleIncoming,
        bubbleIncomingText = DarkBubbleIncomingText,
        isDark = true
    )
}
