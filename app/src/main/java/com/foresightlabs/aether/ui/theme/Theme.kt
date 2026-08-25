package com.foresightlabs.aether.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

@Composable
fun AetherTheme(
    themeState: AppThemeState = LocalAppThemeState.current,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeState.themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.OLED -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> systemDark
    }
    val isOled = themeState.themeMode == AppThemeMode.OLED

    val accent = themeState.accentChoice.primaryColor
    val accentSubtle = themeState.accentChoice.primaryColor.copy(alpha = 0.18f)

    val aetherColors = when {
        isOled -> AetherColors(
            background = OledBackground,
            surface = OledSurface,
            surfaceElevated = OledSurfaceElevated,
            surfaceHighlight = DarkSurfaceHighlight,
            surfaceGlass = DarkSurfaceGlass,
            border = Color(0x18FFFFFF),
            borderSubtle = Color(0x0CFFFFFF),
            textPrimary = DarkTextPrimary,
            textSecondary = DarkTextSecondary,
            textTertiary = DarkTextTertiary,
            accent = accent,
            accentSubtle = accentSubtle,
            bubbleOutgoing = themeState.accentChoice.containerColor,
            bubbleOutgoingEnd = DarkBubbleOutgoingEnd,
            bubbleOutgoingText = DarkTextPrimary,
            bubbleIncoming = DarkBubbleIncoming,
            bubbleIncomingText = DarkBubbleIncomingText,
            isDark = true
        )
        isDark -> AetherColors(
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
            accent = accent,
            accentSubtle = accentSubtle,
            bubbleOutgoing = themeState.accentChoice.containerColor,
            bubbleOutgoingEnd = DarkBubbleOutgoingEnd,
            bubbleOutgoingText = DarkBubbleOutgoingText,
            bubbleIncoming = DarkBubbleIncoming,
            bubbleIncomingText = DarkBubbleIncomingText,
            isDark = true
        )
        else -> AetherColors(
            background = LightBackground,
            surface = LightSurface,
            surfaceElevated = LightSurfaceElevated,
            surfaceHighlight = LightSurfaceHighlight,
            surfaceGlass = Color(0xFFF2EFEA),
            border = LightBorder,
            borderSubtle = LightBorderSubtle,
            textPrimary = LightTextPrimary,
            textSecondary = LightTextSecondary,
            textTertiary = LightTextTertiary,
            accent = accent,
            accentSubtle = accentSubtle,
            bubbleOutgoing = LightBubbleOutgoing,
            bubbleOutgoingEnd = LightBubbleOutgoingEnd,
            bubbleOutgoingText = LightBubbleOutgoingText,
            bubbleIncoming = LightBubbleIncoming,
            bubbleIncomingText = LightBubbleIncomingText,
            isDark = false
        )
    }

    val materialColorScheme = if (isDark) {
        darkColorScheme(
            primary = aetherColors.accent,
            onPrimary = Color.White,
            background = aetherColors.background,
            onBackground = aetherColors.textPrimary,
            surface = aetherColors.surface,
            onSurface = aetherColors.textPrimary,
            surfaceVariant = aetherColors.surfaceElevated,
            onSurfaceVariant = aetherColors.textSecondary,
            outline = aetherColors.border
        )
    } else {
        lightColorScheme(
            primary = aetherColors.accent,
            onPrimary = Color.White,
            background = aetherColors.background,
            onBackground = aetherColors.textPrimary,
            surface = aetherColors.surface,
            onSurface = aetherColors.textPrimary,
            surfaceVariant = aetherColors.surfaceElevated,
            onSurfaceVariant = aetherColors.textSecondary,
            outline = aetherColors.border
        )
    }

    CompositionLocalProvider(
        LocalAetherColors provides aetherColors,
        LocalAppThemeState provides themeState
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = Typography,
            content = content
        )
    }
}
