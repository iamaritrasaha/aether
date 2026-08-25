package com.foresightlabs.aether.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * AETHER EMBER DESIGN SYSTEM TOKENS
 *
 * Visual signature:
 * - Luminous warm orange/vermilion/crimson spectrum glowing inside dark glass
 * - Deep near-black lower surfaces (never blue-black)
 * - Strongly rounded phone-like panels and floating translucent controls
 * - Compact, refined Manrope typography
 */
object AetherEmber {

    // --- COLOR PALETTE ---
    object Colors {
        // Ember Warm Spectrum
        val Amber = Color(0xFFFF9A4A)
        val BrightOrange = Color(0xFFFF7038)
        val Vermilion = Color(0xFFF04425)
        val CoralRed = Color(0xFFE92D27)
        val DeepCrimson = Color(0xFFC90B27)
        val SoftGlow = Color(0xFFFFB070)

        // Near-Black Surfaces (Pure neutral/warm darks, NEVER blue-tinted)
        val Background = Color(0xFF0A0A0B)
        val Surface = Color(0xFF101011)
        val SurfaceElevated = Color(0xFF171718)
        val SurfaceGlass = Color(0xFF1C1B1B)
        val SurfaceHighlight = Color(0xFF242323)

        // Translucent & Glass Overlays
        val GlassPill = Color(0x38000000)
        val GlassPillBorder = Color(0x26FFFFFF)
        val GlassCard = Color(0x22FFFFFF)
        val GlassCardBorder = Color(0x2BFFFFFF)
        val TranslucentWarmGlow = Color(0x29FF7038)

        // Message Bubble Colors
        val IncomingBubbleBg = Color(0x30FFFFFF)
        val IncomingBubbleBorder = Color(0x26FFFFFF)
        val IncomingBubbleText = Color(0xFFFFFFFF)
        val IncomingBubbleMeta = Color(0xB8FFFFFF)

        val OutgoingBubbleStart = Color(0xFFF04425)
        val OutgoingBubbleEnd = Color(0xFFC90B27)
        val OutgoingBubbleText = Color(0xFFFFFFFF)
        val OutgoingBubbleMeta = Color(0xCCFFFFFF)

        // Text Hierarchy
        val TextPrimary = Color(0xFFFFFFFF)
        val TextSecondary = Color(0xFFA6A6AC)
        val TextTertiary = Color(0xFF72727A)
        val TextMuted = Color(0xFF55555C)

        // Accents & Badges
        val Accent = Color(0xFFFF7038)
        val AccentSubtle = Color(0x29FF7038)
        val Online = Color(0xFF34D399)
        val UnreadBadge = Color(0xFFFF7038)
        val Pin = Color(0xFFFF7038)
        val Error = Color(0xFFEF4444)

        // Subtle Borders
        val Border = Color(0x1AFFFFFF)
        val BorderSubtle = Color(0x0EFFFFFF)
    }

    // --- MULTI-STOP GRADIENTS ---
    object Gradients {
        /**
         * Primary Ember Hero Gradient - Warm amber at top-left flowing to deep crimson at bottom-right
         */
        val HeroEmber = Brush.linearGradient(
            colors = listOf(
                Colors.Amber,
                Colors.BrightOrange,
                Colors.Vermilion,
                Colors.CoralRed,
                Colors.DeepCrimson
            ),
            start = Offset(0f, 0f),
            end = Offset(1000f, 1000f)
        )

        /**
         * Full Conversation Screen Atmospheric Gradient
         */
        val ConversationAtmosphere = Brush.verticalGradient(
            colors = listOf(
                Colors.Amber,
                Colors.BrightOrange,
                Colors.Vermilion,
                Colors.CoralRed,
                Colors.DeepCrimson
            )
        )

        /**
         * Outgoing Message Bubble Gradient
         */
        val OutgoingBubble = Brush.linearGradient(
            colors = listOf(
                Colors.OutgoingBubbleStart,
                Colors.OutgoingBubbleEnd
            ),
            start = Offset(0f, 0f),
            end = Offset(300f, 300f)
        )

        /**
         * Subtle Warm Glowing Avatar Rim
         */
        val GlowingAvatarRim = Brush.sweepGradient(
            colors = listOf(
                Colors.Amber,
                Colors.BrightOrange,
                Colors.CoralRed,
                Colors.Amber
            )
        )

        /**
         * Warm Accent Button Gradient
         */
        val ActionButton = Brush.linearGradient(
            colors = listOf(
                Colors.BrightOrange,
                Colors.Vermilion
            )
        )
    }

    // --- STRICT RADIUS HIERARCHY ---
    object Radii {
        val XS: Dp = 10.dp       // Badges, micro tags, small indicators
        val S: Dp = 14.dp        // Small buttons, chips, preview cards
        val M: Dp = 18.dp        // Message bubbles, search inputs, inner containers
        val L: Dp = 24.dp        // Standard cards, bubble corners, action buttons
        val XL: Dp = 30.dp       // Hero search pill, floating composer capsule
        val XXL: Dp = 36.dp      // Major rising dark sheet corners, hero panels
        val Pill: Dp = 999.dp    // Full pills, circular avatars, round icon buttons
    }

    // --- SHAPES ---
    object Shapes {
        val XS = RoundedCornerShape(Radii.XS)
        val S = RoundedCornerShape(Radii.S)
        val M = RoundedCornerShape(Radii.M)
        val L = RoundedCornerShape(Radii.L)
        val XL = RoundedCornerShape(Radii.XL)
        val XXL = RoundedCornerShape(Radii.XXL)
        val Pill = RoundedCornerShape(Radii.Pill)
        val Circle = CircleShape

        // Rising bottom sheet with strongly rounded top corners
        val RisingSheet = RoundedCornerShape(
            topStart = Radii.XXL,
            topEnd = Radii.XXL,
            bottomStart = 0.dp,
            bottomEnd = 0.dp
        )

        // Incoming message bubble shape (directionally softened)
        val IncomingBubble = RoundedCornerShape(
            topStart = Radii.L,
            topEnd = Radii.L,
            bottomStart = 4.dp,
            bottomEnd = Radii.L
        )

        // Outgoing message bubble shape (directionally softened)
        val OutgoingBubble = RoundedCornerShape(
            topStart = Radii.L,
            topEnd = Radii.L,
            bottomStart = Radii.L,
            bottomEnd = 4.dp
        )
    }

    // --- SPACING CONSTITUTION (4dp base grid) ---
    object Spacing {
        val Space4: Dp = 4.dp
        val Space8: Dp = 8.dp
        val Space12: Dp = 12.dp
        val Space16: Dp = 16.dp
        val Space20: Dp = 20.dp
        val Space24: Dp = 24.dp
        val Space32: Dp = 32.dp
        val Space40: Dp = 40.dp

        val ScreenHorizontal: Dp = 16.dp
        val ChatListHorizontal: Dp = 16.dp
        val BubbleHorizontal: Dp = 14.dp
    }

    // --- MOTION DYNAMICS ---
    object Motion {
        val springPhysical = spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        )
        val springTactile = spring<Float>(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
        val springStiff = spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessHigh
        )
    }
}
