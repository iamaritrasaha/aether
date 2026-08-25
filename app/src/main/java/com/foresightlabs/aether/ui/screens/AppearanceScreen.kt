package com.foresightlabs.aether.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foresightlabs.aether.ui.components.AetherAtmosphericBackground
import com.foresightlabs.aether.ui.theme.AccentColorChoice
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.AppThemeMode
import com.foresightlabs.aether.ui.theme.AtmosphereMode
import com.foresightlabs.aether.ui.theme.AtmosphereWeatherService
import com.foresightlabs.aether.ui.theme.LocalAppThemeState
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import com.foresightlabs.aether.ui.theme.MessageDensity
import com.foresightlabs.aether.ui.theme.TimeAtmospherePalette
import com.foresightlabs.aether.ui.theme.WeatherCondition
import kotlinx.coroutines.launch

@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeState = LocalAppThemeState.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isFetchingWeather by remember { mutableStateOf(false) }
    var weatherStatusLabel by remember { mutableStateOf<String?>(null) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            scope.launch {
                isFetchingWeather = true
                val (weather, loc) = AtmosphereWeatherService.fetchCurrentWeather(context, forceRefresh = true)
                themeState.weatherCondition = weather
                weatherStatusLabel = loc ?: "Weather updated: ${weather.displayName}"
                isFetchingWeather = false
            }
        }
    }

    AetherAtmosphericBackground(
        modifier = modifier.fillMaxSize(),
        heroOnly = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0x28000000))
                        .border(1.dp, Color(0x20FFFFFF), CircleShape)
                        .clickable { onBack() }
                        .testTag("appearance_back_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Text(
                    text = "Appearance & Atmosphere",
                    fontFamily = ManropeFontFamily,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.size(38.dp))
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                // Live Chat Bubble Preview Container
                item {
                    Text(
                        text = "LIVE ATMOSPHERE & BUBBLE PREVIEW",
                        fontFamily = ManropeFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AetherEmber.Colors.TextTertiary,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 8.dp)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(AetherEmber.Shapes.L)
                            .background(AetherEmber.Colors.SurfaceElevated)
                            .border(1.dp, AetherEmber.Colors.Border, AetherEmber.Shapes.L)
                            .padding(16.dp)
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
                                    text = "Current mood: ${themeState.activePalette().displayName}" +
                                            if (themeState.atmosphereMode == AtmosphereMode.TIME_AND_WEATHER) " • ${themeState.weatherCondition.displayName} ${themeState.weatherCondition.icon}" else "",
                                    fontFamily = ManropeFontFamily,
                                    color = Color.White,
                                    fontSize = (14.5f * themeState.fontScale).sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Outgoing Bubble (Active Accent gradient)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Box(
                                modifier = Modifier
                                    .clip(AetherEmber.Shapes.OutgoingBubble)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                themeState.accentChoice.primaryColor,
                                                themeState.accentChoice.containerColor
                                            )
                                        )
                                    )
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = "Warm light glowing inside dark glass. ✨",
                                    fontFamily = ManropeFontFamily,
                                    color = Color.White,
                                    fontSize = (14.5f * themeState.fontScale).sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Atmosphere System Controls
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "DYNAMIC ATMOSPHERE SYSTEM",
                        fontFamily = ManropeFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AetherEmber.Colors.TextTertiary,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(start = 24.dp, bottom = 8.dp)
                    )

                    // Atmosphere Mode Pills (Static, Time-Based, Time + Weather, Manual)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AtmosphereMode.entries.forEach { mode ->
                            val isSelected = themeState.atmosphereMode == mode
                            val bg = if (isSelected) AetherEmber.Colors.Accent else AetherEmber.Colors.SurfaceElevated
                            val textCol = if (isSelected) Color.White else AetherEmber.Colors.TextSecondary

                            Box(
                                modifier = Modifier
                                    .clip(AetherEmber.Shapes.Pill)
                                    .background(bg)
                                    .border(0.5.dp, if (isSelected) Color.Transparent else AetherEmber.Colors.Border, AetherEmber.Shapes.Pill)
                                    .clickable {
                                        themeState.atmosphereMode = mode
                                        if (mode == AtmosphereMode.TIME_AND_WEATHER) {
                                            locationPermissionLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                                )
                                            )
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mode.displayName,
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = textCol
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = themeState.atmosphereMode.description,
                        fontFamily = ManropeFontFamily,
                        fontSize = 12.sp,
                        color = AetherEmber.Colors.TextTertiary,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }

                // Automatic Weather Detection Bar
                if (themeState.atmosphereMode == AtmosphereMode.TIME_AND_WEATHER) {
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .clip(AetherEmber.Shapes.M)
                                .background(Color(0x22FFFFFF))
                                .border(1.dp, Color(0x30FFFFFF), AetherEmber.Shapes.M)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = themeState.weatherCondition.icon,
                                    fontSize = 18.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Auto Weather: ${themeState.weatherCondition.displayName}",
                                        fontFamily = ManropeFontFamily,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = weatherStatusLabel ?: "Modulating ${themeState.activePalette().displayName} mood",
                                        fontFamily = ManropeFontFamily,
                                        fontSize = 11.5.sp,
                                        color = AetherEmber.Colors.TextSecondary
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x20FFFFFF))
                                    .clickable(enabled = !isFetchingWeather) {
                                        scope.launch {
                                            isFetchingWeather = true
                                            val (weather, loc) = AtmosphereWeatherService.fetchCurrentWeather(context, forceRefresh = true)
                                            themeState.weatherCondition = weather
                                            weatherStatusLabel = loc ?: "Weather updated: ${weather.displayName}"
                                            isFetchingWeather = false
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isFetchingWeather) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Refresh weather",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Time Atmosphere Palette Selection (When Manual or previewing)
                item {
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "ATMOSPHERE PALETTE",
                        fontFamily = ManropeFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AetherEmber.Colors.TextTertiary,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(start = 24.dp, bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TimeAtmospherePalette.entries.forEach { pal ->
                            val isSelected = themeState.activePalette() == pal
                            PaletteCard(
                                palette = pal,
                                isSelected = isSelected,
                                onClick = {
                                    themeState.manualAtmosphere = pal
                                    if (themeState.atmosphereMode == AtmosphereMode.TIME_BASED) {
                                        themeState.atmosphereMode = AtmosphereMode.MANUAL
                                    }
                                }
                            )
                        }
                    }
                }

                // Weather Modulation Controls (When Time + Weather or to test override)
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "WEATHER OVERRIDE & TESTING",
                        fontFamily = ManropeFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AetherEmber.Colors.TextTertiary,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(start = 24.dp, bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WeatherCondition.entries.forEach { weather ->
                            val isSelected = themeState.weatherCondition == weather
                            val bg = if (isSelected) AetherEmber.Colors.AccentSubtle else AetherEmber.Colors.SurfaceElevated
                            val borderCol = if (isSelected) AetherEmber.Colors.Accent else AetherEmber.Colors.Border

                            Row(
                                modifier = Modifier
                                    .clip(AetherEmber.Shapes.M)
                                    .background(bg)
                                    .border(1.dp, borderCol, AetherEmber.Shapes.M)
                                    .clickable {
                                        themeState.weatherCondition = weather
                                        if (themeState.atmosphereMode == AtmosphereMode.STATIC) {
                                            themeState.atmosphereMode = AtmosphereMode.TIME_AND_WEATHER
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = weather.icon, fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = weather.displayName,
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else AetherEmber.Colors.TextSecondary
                                )
                            }
                        }
                    }
                }

                // Privacy Note
                item {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .clip(AetherEmber.Shapes.M)
                            .background(Color(0x18FFFFFF))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Privacy",
                            tint = Color(0xFF90F0C0),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Privacy first: Atmosphere runs locally without continuous tracking or storing coordinates.",
                            fontFamily = ManropeFontFamily,
                            fontSize = 11.5.sp,
                            color = Color(0xDDFFFFFF),
                            lineHeight = 16.sp
                        )
                    }
                }

                // Theme Mode Section (Dark, OLED, Light, Auto)
                item {
                    Spacer(modifier = Modifier.height(22.dp))
                    Text(
                        text = "THEME MODE",
                        fontFamily = ManropeFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AetherEmber.Colors.TextTertiary,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(start = 24.dp, bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeModeCard(
                            mode = AppThemeMode.DARK,
                            label = "Ember Dark",
                            icon = Icons.Default.DarkMode,
                            isSelected = themeState.themeMode == AppThemeMode.DARK,
                            onClick = { themeState.themeMode = AppThemeMode.DARK },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeModeCard(
                            mode = AppThemeMode.OLED,
                            label = "OLED",
                            icon = Icons.Default.DarkMode,
                            isSelected = themeState.themeMode == AppThemeMode.OLED,
                            onClick = { themeState.themeMode = AppThemeMode.OLED },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeModeCard(
                            mode = AppThemeMode.LIGHT,
                            label = "Light",
                            icon = Icons.Default.LightMode,
                            isSelected = themeState.themeMode == AppThemeMode.LIGHT,
                            onClick = { themeState.themeMode = AppThemeMode.LIGHT },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeModeCard(
                            mode = AppThemeMode.SYSTEM,
                            label = "Auto",
                            icon = Icons.Default.PhoneAndroid,
                            isSelected = themeState.themeMode == AppThemeMode.SYSTEM,
                            onClick = { themeState.themeMode = AppThemeMode.SYSTEM },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Accent Color Section
                item {
                    Spacer(modifier = Modifier.height(22.dp))
                    Text(
                        text = "ACCENT SPECTRUM",
                        fontFamily = ManropeFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AetherEmber.Colors.TextTertiary,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(start = 24.dp, bottom = 10.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        AccentColorChoice.entries.forEach { choice ->
                            val isSelected = themeState.accentChoice == choice
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(choice.primaryColor)
                                    .clickable { themeState.accentChoice = choice }
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(2.5.dp, Color.White, CircleShape)
                                        } else Modifier
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = choice.displayName,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Message Density
                item {
                    Spacer(modifier = Modifier.height(22.dp))
                    Text(
                        text = "MESSAGE DENSITY",
                        fontFamily = ManropeFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AetherEmber.Colors.TextTertiary,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(start = 24.dp, bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(AetherEmber.Shapes.M)
                            .background(AetherEmber.Colors.SurfaceElevated)
                            .border(1.dp, AetherEmber.Colors.Border, AetherEmber.Shapes.M)
                            .padding(4.dp)
                    ) {
                        DensityOption(
                            label = "Comfortable",
                            isSelected = themeState.messageDensity == MessageDensity.COMFORTABLE,
                            onClick = { themeState.messageDensity = MessageDensity.COMFORTABLE },
                            modifier = Modifier.weight(1f)
                        )
                        DensityOption(
                            label = "Compact",
                            isSelected = themeState.messageDensity == MessageDensity.COMPACT,
                            onClick = { themeState.messageDensity = MessageDensity.COMPACT },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Font Size Slider
                item {
                    Spacer(modifier = Modifier.height(22.dp))
                    Text(
                        text = "TYPOGRAPHY SCALE",
                        fontFamily = ManropeFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AetherEmber.Colors.TextTertiary,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(start = 24.dp, bottom = 6.dp)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(AetherEmber.Shapes.L)
                            .background(AetherEmber.Colors.SurfaceElevated)
                            .border(1.dp, AetherEmber.Colors.Border, AetherEmber.Shapes.L)
                            .padding(horizontal = 18.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "A", fontFamily = ManropeFontFamily, fontSize = 13.sp, color = AetherEmber.Colors.TextSecondary)
                            Text(
                                text = "${(themeState.fontScale * 100).toInt()}%",
                                fontFamily = ManropeFontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = AetherEmber.Colors.Accent
                            )
                            Text(text = "A", fontFamily = ManropeFontFamily, fontSize = 20.sp, color = AetherEmber.Colors.TextSecondary)
                        }

                        Slider(
                            value = themeState.fontScale,
                            onValueChange = { themeState.fontScale = it },
                            valueRange = 0.85f..1.25f,
                            steps = 4,
                            colors = SliderDefaults.colors(
                                thumbColor = AetherEmber.Colors.Accent,
                                activeTrackColor = AetherEmber.Colors.Accent,
                                inactiveTrackColor = AetherEmber.Colors.SurfaceHighlight
                            )
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }
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
            .background(AetherEmber.Colors.SurfaceElevated)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) AetherEmber.Colors.Accent else AetherEmber.Colors.Border,
                shape = AetherEmber.Shapes.M
            )
            .clickable { onClick() }
            .padding(10.dp)
    ) {
        // Gradient color swatch
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.linearGradient(palette.colors))
        )

        Spacer(modifier = Modifier.height(8.dp))

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
            fontSize = 10.5.sp,
            color = AetherEmber.Colors.TextTertiary
        )
    }
}

@Composable
private fun ThemeModeCard(
    mode: AppThemeMode,
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(AetherEmber.Shapes.M)
            .background(if (isSelected) AetherEmber.Colors.AccentSubtle else AetherEmber.Colors.SurfaceElevated)
            .border(
                width = if (isSelected) 1.5.dp else 0.5.dp,
                color = if (isSelected) AetherEmber.Colors.Accent else AetherEmber.Colors.Border,
                shape = AetherEmber.Shapes.M
            )
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) AetherEmber.Colors.Accent else AetherEmber.Colors.TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontFamily = ManropeFontFamily,
            fontSize = 11.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color.White else AetherEmber.Colors.TextPrimary
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
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontFamily = ManropeFontFamily,
            fontSize = 13.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color.White else AetherEmber.Colors.TextSecondary
        )
    }
}
