package com.foresightlabs.aether.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Atmosphere typography and foreground-surface typography resolve independently.
// Aether Ember Core Dark Surfaces — the controlled dark atmosphere's base family
// (deep base / primary graphite / raised graphite), never blue-black.
val DarkBackground = Color(0xFF090A0D)
val DarkSurface = Color(0xFF111318)
val DarkSurfaceElevated = Color(0xFF181A21)
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

// The two directions are told apart by tone, not by colour: incoming is a pale
// mist the atmosphere still shows through, outgoing is smoky graphite. Neither
// is an accent slab, and both stay neutral so they belong to the sky behind them.
val DarkBubbleOutgoing = Color(0xDB2C2C36)
val DarkBubbleOutgoingEnd = Color(0xDB2C2C36)
val DarkBubbleOutgoingText = Color(0xFFF4F4F7)
val DarkBubbleIncoming = Color(0xC7EDECF2)
val DarkBubbleIncomingBorder = Color(0x14FFFFFF)
val DarkBubbleIncomingText = Color(0xFF1B1B21)

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
    val background: Color = DarkBackground,
    val surface: Color = DarkSurface,
    val surfaceElevated: Color = DarkSurfaceElevated,
    val surfaceHighlight: Color = DarkSurfaceHighlight,
    val surfaceGlass: Color = DarkSurfaceGlass,
    val border: Color = DarkBorder,
    val borderSubtle: Color = DarkBorderSubtle,
    val textPrimary: Color = DarkTextPrimary,
    val textSecondary: Color = DarkTextSecondary,
    val textTertiary: Color = DarkTextTertiary,
    val textMuted: Color = DarkTextMuted,
    val input: Color = DarkSurfaceHighlight,
    val divider: Color = DarkBorderSubtle,
    val atmosphereTextPrimary: Color = AtmosphereTextPrimary,
    val atmosphereTextSecondary: Color = AtmosphereTextSecondary,
    val atmosphereTextTertiary: Color = AtmosphereTextTertiary,
    val atmosphereTextMuted: Color = AtmosphereTextMuted,
    val accent: Color = AccentEmber,
    val accentSubtle: Color = AccentEmberSubtle,
    val bubbleOutgoing: Color = DarkBubbleOutgoing,
    val bubbleOutgoingEnd: Color = DarkBubbleOutgoingEnd,
    val bubbleOutgoingText: Color = DarkBubbleOutgoingText,
    val bubbleIncoming: Color = DarkBubbleIncoming,
    val bubbleIncomingText: Color = DarkBubbleIncomingText,
    val isDark: Boolean = true
)

val LocalAetherColors = staticCompositionLocalOf {
    AetherColors()
}
