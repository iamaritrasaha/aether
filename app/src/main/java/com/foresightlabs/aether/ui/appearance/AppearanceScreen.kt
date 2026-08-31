package com.foresightlabs.aether.ui.appearance
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foresightlabs.aether.ui.design.AetherAtmosphericBackground
import com.foresightlabs.aether.ui.design.AetherBackButton
import com.foresightlabs.aether.ui.design.AetherFloatingHeader
import com.foresightlabs.aether.ui.design.aetherFloatingHeaderContentTopPadding
import com.foresightlabs.aether.ui.design.rememberAetherFloatingHeaderScrollFraction
import com.foresightlabs.aether.ui.design.rememberAetherFrostState
import com.foresightlabs.aether.ui.theme.AccentColorChoice
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.AtmosphereMode
import com.foresightlabs.aether.ui.theme.LocalAppThemeState
import com.foresightlabs.aether.ui.theme.LocalAtmosphere
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import com.foresightlabs.aether.ui.theme.MessageDensity
import com.foresightlabs.aether.ui.theme.TimeAtmospherePalette

@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeState = LocalAppThemeState.current
    val atmosphere = LocalAtmosphere.current
    val listState = rememberLazyListState()
    val headerScrollFraction = rememberAetherFloatingHeaderScrollFraction(listState)
    val frostState = rememberAetherFrostState()

    Box(modifier = modifier.fillMaxSize()) {
        AetherAtmosphericBackground(
            modifier = Modifier.fillMaxSize(),
            frostState = frostState
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = aetherFloatingHeaderContentTopPadding())
            ) {
                // Live Chat Bubble Preview Container
                item {
                    Text(
                        text = "LIVE ATMOSPHERE & BUBBLE PREVIEW",
                        fontFamily = ManropeFontFamily,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AetherEmber.Colors.AtmosphereTextSecondary,
                        letterSpacing = 1.0.sp,
                        modifier = Modifier.padding(
                            start = AetherEmber.Spacing.Space24,
                            top = AetherEmber.Spacing.Space16,
                            bottom = AetherEmber.Spacing.Space8
                        )
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AetherEmber.Spacing.Space16)
                            .clip(AetherEmber.Shapes.L)
                            .background(Color(0x35000000))
                            .border(1.dp, Color(0x28FFFFFF), AetherEmber.Shapes.L)
                            .padding(AetherEmber.Spacing.Space16)
                    ) {
                        // Incoming Bubble (Frosted Glass)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                            Box(
                                modifier = Modifier
                                    .clip(AetherEmber.Shapes.IncomingBubble)
                                    .background(AetherEmber.Colors.IncomingBubbleBg)
                                    .border(1.dp, AetherEmber.Colors.IncomingBubbleBorder, AetherEmber.Shapes.IncomingBubble)
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = "Current mood: ${atmosphere.palette.displayName}",
                                    fontFamily = ManropeFontFamily,
                                    color = Color.White,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space12))

                        // Outgoing Bubble (Active Accent gradient)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Box(
                                modifier = Modifier
                                    .clip(AetherEmber.Shapes.OutgoingBubble)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                if (themeState.useAtmosphereAccent) atmosphere.accent
                                                else themeState.accentChoice.primaryColor,
                                                if (themeState.useAtmosphereAccent) atmosphere.accentStrong
                                                else themeState.accentChoice.containerColor
                                            )
                                        )
                                    )
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = "Warm light glowing inside dark glass. ✨",
                                    fontFamily = ManropeFontFamily,
                                    color = Color.White,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Atmosphere System Controls
                item {
                    Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space24))
                    Text(
                        text = "ATMOSPHERE MODE",
                        fontFamily = ManropeFontFamily,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AetherEmber.Colors.AtmosphereTextSecondary,
                        letterSpacing = 1.0.sp,
                        modifier = Modifier.padding(start = AetherEmber.Spacing.Space24, bottom = AetherEmber.Spacing.Space8)
                    )

                    // Atmosphere Mode Pills (Static, Time-Based, Manual)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = AetherEmber.Spacing.Space16),
                        horizontalArrangement = Arrangement.spacedBy(AetherEmber.Spacing.Space8)
                    ) {
                        AtmosphereMode.entries.forEach { mode ->
                            val isSelected = themeState.atmosphereMode == mode
                            val bg = if (isSelected) atmosphere.accent else Color(0x35000000)
                            val textCol = if (isSelected) Color.White else AetherEmber.Colors.AtmosphereTextSecondary

                            Box(
                                modifier = Modifier
                                    .clip(AetherEmber.Shapes.Pill)
                                    .background(bg)
                                    .border(1.dp, if (isSelected) Color.Transparent else Color(0x28FFFFFF), AetherEmber.Shapes.Pill)
                                    .clickable {
                                        themeState.setAndPersistAtmosphereMode(mode)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mode.displayName,
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = textCol
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space8))

                    Text(
                        text = themeState.atmosphereMode.description,
                        fontFamily = ManropeFontFamily,
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = AetherEmber.Colors.AtmosphereTextTertiary,
                        modifier = Modifier.padding(horizontal = AetherEmber.Spacing.Space24)
                    )
                }

                // Time Atmosphere Palette Selection (When Manual or previewing)
                item {
                    Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space24))
                    Text(
                        text = "ATMOSPHERE PALETTE",
                        fontFamily = ManropeFontFamily,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AetherEmber.Colors.AtmosphereTextSecondary,
                        letterSpacing = 1.0.sp,
                        modifier = Modifier.padding(start = AetherEmber.Spacing.Space24, bottom = AetherEmber.Spacing.Space8)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = AetherEmber.Spacing.Space16),
                        horizontalArrangement = Arrangement.spacedBy(AetherEmber.Spacing.Space8)
                    ) {
                        TimeAtmospherePalette.entries.forEach { pal ->
                            val isSelected = atmosphere.palette == pal
                            PaletteCard(
                                palette = pal,
                                isSelected = isSelected,
                                onClick = {
                                    themeState.setAndPersistManualAtmosphere(pal)
                                    if (themeState.atmosphereMode == AtmosphereMode.TIME_BASED) {
                                        themeState.setAndPersistAtmosphereMode(AtmosphereMode.MANUAL)
                                    }
                                }
                            )
                        }
                    }
                }

                // Accent Color Section
                item {
                    Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space24))
                    Text(
                        text = "ACCENT PALETTE",
                        fontFamily = ManropeFontFamily,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AetherEmber.Colors.AtmosphereTextSecondary,
                        letterSpacing = 1.0.sp,
                        modifier = Modifier.padding(start = AetherEmber.Spacing.Space24, bottom = AetherEmber.Spacing.Space8)
                    )

                    Text(
                        text = "By default the accent follows the current atmosphere. " +
                            "Pick a curated aesthetic tone to pin it instead.",
                        fontFamily = ManropeFontFamily,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 17.sp,
                        color = AetherEmber.Colors.AtmosphereTextTertiary,
                        modifier = Modifier.padding(horizontal = AetherEmber.Spacing.Space24, vertical = 2.dp)
                    )

                    Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space12))

                    // Follow Atmosphere Option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AetherEmber.Spacing.Space16)
                            .clip(AetherEmber.Shapes.M)
                            .background(
                                if (themeState.useAtmosphereAccent) atmosphere.accent.copy(alpha = 0.28f)
                                else Color(0x35000000)
                            )
                            .border(
                                width = if (themeState.useAtmosphereAccent) 1.5.dp else 1.dp,
                                color = if (themeState.useAtmosphereAccent) atmosphere.accent else Color(0x28FFFFFF),
                                shape = AetherEmber.Shapes.M
                            )
                            .clickable { themeState.setAndPersistUseAtmosphereAccent(true) }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .testTag("accent_atmosphere"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(atmosphere.accent)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Follow Atmosphere",
                                fontFamily = ManropeFontFamily,
                                fontSize = 13.5.sp,
                                fontWeight = if (themeState.useAtmosphereAccent) FontWeight.Bold else FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                        if (themeState.useAtmosphereAccent) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space16))

                    // 2x6 Palette Grid
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AetherEmber.Spacing.Space16),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val entries = AccentColorChoice.entries
                        val rows = entries.chunked(6)
                        rows.forEach { rowEntries ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                rowEntries.forEach { choice ->
                                    val isSelected = !themeState.useAtmosphereAccent && themeState.accentChoice == choice
                                    AccentSwatch(
                                        choice = choice,
                                        isSelected = isSelected,
                                        onClick = {
                                            themeState.setAndPersistAccentChoice(choice)
                                            themeState.setAndPersistUseAtmosphereAccent(false)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Message Density
                item {
                    Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space24))
                    Text(
                        text = "MESSAGE DENSITY",
                        fontFamily = ManropeFontFamily,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AetherEmber.Colors.AtmosphereTextSecondary,
                        letterSpacing = 1.0.sp,
                        modifier = Modifier.padding(start = AetherEmber.Spacing.Space24, bottom = AetherEmber.Spacing.Space8)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AetherEmber.Spacing.Space16)
                            .clip(AetherEmber.Shapes.M)
                            .background(Color(0x35000000))
                            .border(1.dp, Color(0x28FFFFFF), AetherEmber.Shapes.M)
                            .padding(AetherEmber.Spacing.Space4)
                    ) {
                        DensityOption(
                            label = "Comfortable",
                            isSelected = themeState.messageDensity == MessageDensity.COMFORTABLE,
                            onClick = { themeState.setAndPersistMessageDensity(MessageDensity.COMFORTABLE) },
                            modifier = Modifier.weight(1f)
                        )
                        DensityOption(
                            label = "Compact",
                            isSelected = themeState.messageDensity == MessageDensity.COMPACT,
                            onClick = { themeState.setAndPersistMessageDensity(MessageDensity.COMPACT) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Font Size Slider
                item {
                    Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space24))
                    Text(
                        text = "TYPOGRAPHY SCALE",
                        fontFamily = ManropeFontFamily,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AetherEmber.Colors.AtmosphereTextSecondary,
                        letterSpacing = 1.0.sp,
                        modifier = Modifier.padding(start = AetherEmber.Spacing.Space24, bottom = AetherEmber.Spacing.Space8)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AetherEmber.Spacing.Space16)
                            .clip(AetherEmber.Shapes.L)
                            .background(Color(0x35000000))
                            .border(1.dp, Color(0x28FFFFFF), AetherEmber.Shapes.L)
                            .padding(horizontal = 16.dp, vertical = AetherEmber.Spacing.Space12)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "A", fontFamily = ManropeFontFamily, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AetherEmber.Colors.AtmosphereTextSecondary)
                            Text(
                                text = "${(themeState.fontScale * 100).toInt()}%",
                                fontFamily = ManropeFontFamily,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = LocalAtmosphere.current.accent
                            )
                            Text(text = "A", fontFamily = ManropeFontFamily, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = AetherEmber.Colors.AtmosphereTextSecondary)
                        }

                        Slider(
                            value = themeState.fontScale,
                            onValueChange = themeState::setAndPersistFontScale,
                            valueRange = 0.85f..1.25f,
                            steps = 4,
                            colors = SliderDefaults.colors(
                                thumbColor = LocalAtmosphere.current.accent,
                                activeTrackColor = LocalAtmosphere.current.accent,
                                inactiveTrackColor = Color(0x33FFFFFF)
                            )
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space48))
                }
            }
        }

        AetherFloatingHeader(
            title = "Appearance & Atmosphere",
            modifier = Modifier.align(Alignment.TopCenter),
            scrollFraction = headerScrollFraction,
            frostState = frostState,
            navigation = {
                AetherBackButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("appearance_back_button")
                )
            }
        )
    }
}

@Composable
private fun PaletteCard(
    palette: TimeAtmospherePalette,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(115.dp)
            .clip(AetherEmber.Shapes.M)
            .background(Color(0x35000000))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) LocalAtmosphere.current.accent else Color(0x28FFFFFF),
                shape = AetherEmber.Shapes.M
            )
            .clickable { onClick() }
            .padding(AetherEmber.Spacing.Space8)
    ) {
        // Gradient color swatch
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.linearGradient(palette.colors))
        )

        Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space8))

        Text(
            text = palette.displayName,
            fontFamily = ManropeFontFamily,
            fontSize = 12.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
            color = Color.White
        )

        Text(
            text = palette.timeLabel,
            fontFamily = ManropeFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = AetherEmber.Colors.AtmosphereTextTertiary
        )
    }
}


@Composable
private fun DensityOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(AetherEmber.Shapes.M)
            .background(if (isSelected) AetherEmber.Colors.SurfaceHighlight else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = AetherEmber.Spacing.Space8),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontFamily = ManropeFontFamily,
            fontSize = 13.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color.White else AetherEmber.Colors.AtmosphereTextTertiary
        )
    }
}

@Composable
private fun AccentSwatch(
    choice: AccentColorChoice,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = choice.displayName
                selected = isSelected
            },
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape)
            )
        }

        Box(
            modifier = Modifier
                .size(if (isSelected) 32.dp else 36.dp)
                .clip(CircleShape)
                .background(choice.primaryColor),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = choice.onAccent,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
