package com.foresightlabs.aether.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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

    val atmosphere = rememberAtmosphere(themeState)
    val reducedMotion = rememberSystemReducedMotion()

    // Atmosphere owns the accent. Selected states, focus and glow follow the
    // environment unless the user has explicitly pinned a fixed accent.
    val targetAccent = if (themeState.useAtmosphereAccent) {
        atmosphere.accent
    } else {
        themeState.accentChoice.primaryColor
    }
    val accent by animateColorAsState(
        targetValue = targetAccent,
        animationSpec = tween(
            durationMillis = if (reducedMotion) 0 else AetherMotion.AtmosphereMillis,
            easing = AetherMotion.AtmosphereEasing
        ),
        label = "atmosphere_accent"
    )
    val accentSubtle = accent.copy(alpha = 0.18f)
    val accentContainer = if (themeState.useAtmosphereAccent) {
        atmosphere.accentStrong
    } else {
        themeState.accentChoice.containerColor
    }

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
            textMuted = DarkTextMuted,
            input = DarkSurfaceHighlight,
            divider = Color(0x0CFFFFFF),
            accent = accent,
            accentSubtle = accentSubtle,
            bubbleOutgoing = accent,
            bubbleOutgoingEnd = accentContainer,
            bubbleOutgoingText = DarkTextPrimary,
            bubbleIncoming = DarkBubbleIncoming,
            bubbleIncomingText = DarkBubbleIncomingText,
            isDark = true,
            isOled = true
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
            textMuted = DarkTextMuted,
            input = DarkSurfaceHighlight,
            divider = DarkBorderSubtle,
            accent = accent,
            accentSubtle = accentSubtle,
            bubbleOutgoing = accent,
            bubbleOutgoingEnd = accentContainer,
            bubbleOutgoingText = DarkBubbleOutgoingText,
            bubbleIncoming = DarkBubbleIncoming,
            bubbleIncomingText = DarkBubbleIncomingText,
            isDark = true,
            isOled = false
        )
        else -> AetherColors(
            background = LightBackground,
            surface = LightSurface,
            surfaceElevated = LightSurfaceElevated,
            surfaceHighlight = LightSurfaceHighlight,
            surfaceGlass = Color(0xF2F2EFEA),
            border = LightBorder,
            borderSubtle = LightBorderSubtle,
            textPrimary = LightTextPrimary,
            textSecondary = LightTextSecondary,
            textTertiary = LightTextTertiary,
            textMuted = LightTextMuted,
            input = LightSurfaceElevated,
            divider = LightBorderSubtle,
            accent = accent,
            accentSubtle = accentSubtle,
            bubbleOutgoing = LightBubbleOutgoing,
            bubbleOutgoingEnd = LightBubbleOutgoingEnd,
            bubbleOutgoingText = LightBubbleOutgoingText,
            bubbleIncoming = LightBubbleIncoming,
            bubbleIncomingText = LightBubbleIncomingText,
            isDark = false,
            isOled = false
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

    // Aether's own typography scale composes with the system font scale, so every
    // sp value in the app responds to both without per-screen multiplication.
    val baseDensity = LocalDensity.current
    val scaledDensity = remember(baseDensity, themeState.fontScale) {
        Density(baseDensity.density, baseDensity.fontScale * themeState.fontScale)
    }

    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
        LocalAetherColors provides aetherColors,
        LocalAppThemeState provides themeState,
        LocalAtmosphere provides atmosphere,
        LocalReducedMotion provides reducedMotion
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = Typography,
            content = content
        )
    }
}
