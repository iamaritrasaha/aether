package com.foresightlabs.aether.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.foresightlabs.aether.R

/**
 * Manrope Variable font family
 */
val ManropeFontFamily = FontFamily(
    Font(R.font.manrope, weight = FontWeight.Normal),
    Font(R.font.manrope, weight = FontWeight.Medium),
    Font(R.font.manrope, weight = FontWeight.SemiBold),
    Font(R.font.manrope, weight = FontWeight.Bold),
    Font(R.font.manrope, weight = FontWeight.ExtraBold)
)

/**
 * Space Grotesk Variable font family for optional hero editorial headings
 */
val SpaceGroteskFontFamily = FontFamily(
    Font(R.font.space_grotesk, weight = FontWeight.Normal),
    Font(R.font.space_grotesk, weight = FontWeight.Medium),
    Font(R.font.space_grotesk, weight = FontWeight.SemiBold),
    Font(R.font.space_grotesk, weight = FontWeight.Bold)
)

/** Semantic Aether type roles. Screens choose meaning, never arbitrary measurements. */
object AetherType {
    val Display = TextStyle(
        fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold,
        fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp
    )
    val ScreenTitle = TextStyle(
        fontFamily = ManropeFontFamily, fontWeight = FontWeight.Bold,
        fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.1).sp
    )
    val HeroTitle = TextStyle(
        fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold,
        fontSize = 23.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp
    )
    val SectionTitle = TextStyle(
        fontFamily = ManropeFontFamily, fontWeight = FontWeight.ExtraBold,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.2.sp
    )
    val Body = TextStyle(
        fontFamily = ManropeFontFamily, fontWeight = FontWeight.Medium,
        fontSize = 15.sp, lineHeight = 20.sp
    )
    val BodySecondary = TextStyle(
        fontFamily = ManropeFontFamily, fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp, lineHeight = 18.sp
    )
    val Label = TextStyle(
        fontFamily = ManropeFontFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.1.sp
    )
    val Metadata = TextStyle(
        fontFamily = ManropeFontFamily, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 14.sp
    )
    val Caption = TextStyle(
        fontFamily = ManropeFontFamily, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp
    )
}

val Typography = Typography(
    // Hero Headline (e.g., "Let's Stay Connected")
    displayLarge = TextStyle(
        fontFamily = ManropeFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.6).sp
    ),
    displayMedium = TextStyle(
        fontFamily = ManropeFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.4).sp
    ),
    displaySmall = TextStyle(
        fontFamily = ManropeFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp
    ),
    // Screen Title
    headlineLarge = TextStyle(
        fontFamily = ManropeFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = ManropeFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.1).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = ManropeFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    // Chat title / Contact name
    titleLarge = TextStyle(
        fontFamily = ManropeFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.1).sp
    ),
    titleMedium = TextStyle(
        fontFamily = ManropeFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.5.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = ManropeFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    ),
    // Message Body
    bodyLarge = TextStyle(
        fontFamily = ManropeFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.5.sp,
        letterSpacing = 0.1.sp
    ),
    // Secondary subtitle / last message
    bodyMedium = TextStyle(
        fontFamily = ManropeFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    ),
    bodySmall = TextStyle(
        fontFamily = ManropeFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.15.sp
    ),
    // Buttons & Chips
    labelLarge = TextStyle(
        fontFamily = ManropeFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = ManropeFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.2.sp
    ),
    // Timestamp & Badges
    labelSmall = TextStyle(
        fontFamily = ManropeFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp
    )
)
