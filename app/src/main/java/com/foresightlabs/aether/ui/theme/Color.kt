package com.foresightlabs.aether.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Aether Ember Core Dark Surfaces (Near-black, never blue-black)
val DarkBackground = Color(0xFF0A0A0B)
val DarkSurface = Color(0xFF101011)
val DarkSurfaceElevated = Color(0xFF171718)
val DarkSurfaceGlass = Color(0xFF1C1B1B)
val DarkSurfaceHighlight = Color(0xFF242323)
val DarkBorder = Color(0x1AFFFFFF)
val DarkBorderSubtle = Color(0x0EFFFFFF)

val DarkTextPrimary = Color(0xFFFFFFFF)
val DarkTextSecondary = Color(0xFFA6A6AC)
val DarkTextTertiary = Color(0xFF72727A)

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
val LightBackground = Color(0xFFF7F5F3)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceElevated = Color(0xFFEFECE9)
val LightSurfaceHighlight = Color(0xFFE5E0DC)
val LightBorder = Color(0x14000000)
val LightBorderSubtle = Color(0x0A000000)

val LightTextPrimary = Color(0xFF141416)
val LightTextSecondary = Color(0xFF6E6E75)
val LightTextTertiary = Color(0xFF9696A0)

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
    val accent: Color,
    val accentSubtle: Color,
    val bubbleOutgoing: Color,
    val bubbleOutgoingEnd: Color = DarkBubbleOutgoingEnd,
    val bubbleOutgoingText: Color,
    val bubbleIncoming: Color,
    val bubbleIncomingText: Color,
    val isDark: Boolean
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
