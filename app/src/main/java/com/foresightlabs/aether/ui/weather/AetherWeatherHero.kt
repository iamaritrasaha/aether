package com.foresightlabs.aether.ui.weather

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.foresightlabs.aether.ui.design.aetherReveal
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import com.foresightlabs.aether.ui.theme.SpaceGroteskFontFamily
import kotlin.math.roundToInt

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip

/**
 * Living Weather Hero revealed by pulling down the Home sheet.
 *
 * Layer 3 weather typography and information. Performs a continuous physical
 * transformation driven by [revealProgress]:
 * - 0.0 (Resting): Ambient temperature only at top-left (~26sp)
 * - 0.2: Temperature enlarges subtly, atmospheric depth deepens
 * - 0.4: Weather condition name begins to materialize
 * - 0.6: High/low and location information emerge
 * - 0.8: Secondary metrics row (Feels like, Humidity, Wind) fades in
 * - 1.0: Full atmospheric Weather Hero
 */
@Composable
fun AetherWeatherHero(
    weatherState: WeatherHeroState,
    revealProgress: Float,
    modifier: Modifier = Modifier,
    onLocationClick: () -> Unit = {}
) {
    if (!weatherState.isAvailable && revealProgress <= 0.05f) {
        // Truthful degradation: hide cleanly when weather is unavailable at resting state
        return
    }

    val density = LocalDensity.current

    // Progressive revelation alphas
    val conditionAlpha = ((revealProgress - 0.30f) / 0.25f).coerceIn(0f, 1f)
    val highLowAlpha = ((revealProgress - 0.50f) / 0.22f).coerceIn(0f, 1f)
    val locationAlpha = ((revealProgress - 0.60f) / 0.20f).coerceIn(0f, 1f)
    val secondaryAlpha = ((revealProgress - 0.68f) / 0.22f).coerceIn(0f, 1f)
    val fallbackAlpha = ((revealProgress - 0.35f) / 0.25f).coerceIn(0f, 1f)

    // Morph geometry tokens
    val startPaddingX = 20.dp
    val endPaddingX = 24.dp
    val startPaddingTop = 8.dp
    val endPaddingTop = 44.dp

    val currentPaddingX = lerp(startPaddingX, endPaddingX, revealProgress)
    val currentPaddingTop = lerp(startPaddingTop, endPaddingTop, revealProgress)

    // Temperature typography morph
    val startFontSize = 26.sp
    val endFontSize = 64.sp
    val currentFontSize = lerp(startFontSize, endFontSize, revealProgress)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .semantics {
                contentDescription = weatherState.accessibilityDescription
            }
    ) {
        if (weatherState.isAvailable) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = currentPaddingX, end = 24.dp, top = currentPaddingTop)
            ) {
                // Morphing Temperature: continuous shared element floating directly in atmosphere
                Text(
                    text = weatherState.temperatureDisplay,
                    fontFamily = ManropeFontFamily,
                    fontSize = currentFontSize,
                    lineHeight = currentFontSize * 1.05f,
                    fontWeight = if (revealProgress > 0.4f) FontWeight.Bold else FontWeight.Medium,
                    letterSpacing = if (revealProgress > 0.4f) (-1.5).sp else (-0.2).sp,
                    color = AetherEmber.Colors.AtmosphereTextPrimary,
                    modifier = Modifier.testTag("weather_hero_temperature")
                )

                // Condition display name (e.g. "Partly cloudy")
                weatherState.conditionName?.let { condName ->
                    if (conditionAlpha > 0.01f) {
                        Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space4))
                        Text(
                            text = condName,
                            fontFamily = ManropeFontFamily,
                            fontSize = 22.sp,
                            lineHeight = 26.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.2).sp,
                            color = AetherEmber.Colors.AtmosphereTextPrimary,
                            modifier = Modifier
                                .graphicsLayer {
                                    alpha = conditionAlpha
                                    translationY = (1f - conditionAlpha) * 12f
                                }
                                .testTag("weather_hero_condition")
                        )
                    }
                }

                // Location & High/Low Row
                if (highLowAlpha > 0.01f || (locationAlpha > 0.01f && weatherState.locationLabel != null)) {
                    Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space8))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AetherEmber.Spacing.Space12),
                        modifier = Modifier.graphicsLayer {
                            alpha = highLowAlpha
                            translationY = (1f - highLowAlpha) * 10f
                        }
                    ) {
                        weatherState.locationLabel?.let { loc ->
                            if (locationAlpha > 0.01f) {
                                Text(
                                    text = loc,
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = AetherEmber.Colors.AtmosphereTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .aetherReveal(alpha = locationAlpha)
                                        .clickable { onLocationClick() }
                                        .testTag("weather_hero_location")
                                )
                            }
                        }

                        weatherState.highLowDisplay?.let { hl ->
                            Text(
                                text = hl,
                                fontFamily = ManropeFontFamily,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.4.sp,
                                color = AetherEmber.Colors.AtmosphereTextSecondary,
                                modifier = Modifier.testTag("weather_hero_high_low")
                            )
                        }
                    }
                }

                // Secondary Metrics Row (Feels like, Humidity, Wind)
                if (secondaryAlpha > 0.01f && weatherState.secondaryMetrics.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space16))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AetherEmber.Spacing.Space8),
                        modifier = Modifier
                            .graphicsLayer {
                                alpha = secondaryAlpha
                                translationY = (1f - secondaryAlpha) * 8f
                            }
                            .testTag("weather_hero_secondary_metrics")
                    ) {
                        weatherState.secondaryMetrics.forEachIndexed { index, metric ->
                            if (index > 0) {
                                Text(
                                    text = "•",
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AetherEmber.Colors.AtmosphereTextTertiary
                                )
                            }
                            Text(
                                text = metric,
                                fontFamily = ManropeFontFamily,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = AetherEmber.Colors.AtmosphereTextSecondary
                            )
                        }
                    }
                }
            }
        } else if (fallbackAlpha > 0.01f) {
            // Truthful fallback when user pulls sheet down but weather is unavailable
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = endPaddingTop)
                    .aetherReveal(
                        alpha = fallbackAlpha,
                        verticalShiftPx = (1f - fallbackAlpha) * 12f
                    )
            ) {
                Text(
                    text = "Weather unavailable",
                    fontFamily = SpaceGroteskFontFamily,
                    fontSize = 24.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = AetherEmber.Colors.AtmosphereTextPrimary
                )
                Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space4))
                Text(
                    text = weatherState.unavailableMessage ?: "Using time-only atmosphere.",
                    fontFamily = ManropeFontFamily,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Medium,
                    color = AetherEmber.Colors.AtmosphereTextSecondary
                )
                Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space16))
                Box(
                    modifier = Modifier
                        .clip(AetherEmber.Shapes.Pill)
                        .background(Color(0x35000000))
                        .border(1.dp, Color(0x28FFFFFF), AetherEmber.Shapes.Pill)
                        .clickable { onLocationClick() }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("weather_hero_choose_location"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Choose location",
                        fontFamily = ManropeFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
