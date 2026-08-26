package com.foresightlabs.aether.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.foresightlabs.aether.ui.theme.LocalAetherColors

/**
 * The single source of truth for Aether's accent in composables.
 *
 * The resolved accent already accounts for the current atmosphere and any explicit
 * user override, so no screen or component should reach for a fixed colour constant.
 */
object AetherAccent {

    val current: Color
        @Composable @ReadOnlyComposable
        get() = LocalAetherColors.current.accent

    val subtle: Color
        @Composable @ReadOnlyComposable
        get() = LocalAetherColors.current.accentSubtle

    /** Gradient for primary actions and send buttons. */
    val actionBrush: Brush
        @Composable @ReadOnlyComposable
        get() {
            val colors = LocalAetherColors.current
            return Brush.linearGradient(listOf(colors.accent, colors.bubbleOutgoingEnd))
        }

    /** Gradient for outgoing message bubbles. */
    val outgoingBubbleBrush: Brush
        @Composable @ReadOnlyComposable
        get() {
            val colors = LocalAetherColors.current
            return Brush.linearGradient(listOf(colors.bubbleOutgoing, colors.bubbleOutgoingEnd))
        }

    /** Sweep used for a glowing avatar rim. */
    val avatarRimBrush: Brush
        @Composable @ReadOnlyComposable
        get() {
            val colors = LocalAetherColors.current
            return Brush.sweepGradient(
                listOf(
                    colors.accent,
                    colors.bubbleOutgoingEnd,
                    colors.accent.copy(alpha = 0.7f),
                    colors.accent
                )
            )
        }
}
