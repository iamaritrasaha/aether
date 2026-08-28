package com.foresightlabs.aether.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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

    val aetherColors = remember(accent, accentSubtle) {
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
            accent = accent,
            accentSubtle = accentSubtle,
            bubbleOutgoing = DarkBubbleOutgoing,
            bubbleOutgoingEnd = DarkBubbleOutgoingEnd,
            bubbleOutgoingText = DarkBubbleOutgoingText,
            bubbleIncoming = DarkBubbleIncoming,
            bubbleIncomingText = DarkBubbleIncomingText,
            isDark = true
        )
    }

    val materialColorScheme = remember(aetherColors) {
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
    }

    // Aether's own typography scale composes with the system font scale, so every
    // sp value in the app responds to both without per-screen multiplication.
    val baseDensity = LocalDensity.current
    val scaledDensity = remember(baseDensity, themeState.fontScale) {
        Density(baseDensity.density, baseDensity.fontScale * themeState.fontScale)
    }

    val textSelectionColors = remember(aetherColors.accent) {
        androidx.compose.foundation.text.selection.TextSelectionColors(
            handleColor = aetherColors.accent,
            backgroundColor = aetherColors.accent.copy(alpha = 0.30f)
        )
    }

    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
        LocalAetherColors provides aetherColors,
        LocalAppThemeState provides themeState,
        LocalAtmosphere provides atmosphere,
        LocalReducedMotion provides reducedMotion,
        androidx.compose.foundation.text.selection.LocalTextSelectionColors provides textSelectionColors
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = Typography,
            content = content
        )
    }
}
