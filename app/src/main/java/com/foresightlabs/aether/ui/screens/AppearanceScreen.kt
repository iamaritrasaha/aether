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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foresightlabs.aether.ui.components.AetherAtmosphericBackground
import com.foresightlabs.aether.ui.design.AetherBackButton
import com.foresightlabs.aether.ui.design.AetherFloatingHeader
import com.foresightlabs.aether.ui.design.aetherFloatingHeaderContentTopPadding
import com.foresightlabs.aether.ui.design.rememberAetherFloatingHeaderScrollFraction
import com.foresightlabs.aether.ui.design.aetherFrostSource
import com.foresightlabs.aether.ui.design.rememberAetherFrostState
import com.foresightlabs.aether.ui.theme.AccentColorChoice
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.AppThemeMode
import com.foresightlabs.aether.ui.theme.AtmosphereMode
import com.foresightlabs.aether.ui.theme.AtmosphereWeatherService
import com.foresightlabs.aether.ui.theme.LocalAppThemeState
import com.foresightlabs.aether.ui.theme.LocalAtmosphere
import com.foresightlabs.aether.ui.theme.WeatherReading
import com.foresightlabs.aether.ui.theme.WeatherUnavailableReason
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
    val atmosphere = LocalAtmosphere.current
    val listState = rememberLazyListState()
    val headerScrollFraction = rememberAetherFloatingHeaderScrollFraction(listState)
    val frostState = rememberAetherFrostState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isFetchingWeather by remember { mutableStateOf(false) }

    fun refreshWeather() {
        scope.launch {
            isFetchingWeather = true
            themeState.weatherOverride = null
            themeState.weatherReading = AtmosphereWeatherService.read(context, forceRefresh = true)
            isFetchingWeather = false
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            refreshWeather()
        } else {
            themeState.weatherReading =
                WeatherReading.Unavailable(WeatherUnavailableReason.LOCATION_PERMISSION)
        }
    }

    AetherAtmosphericBackground(
        modifier = modifier.fillMaxSize(),
        frostState = frostState,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().aetherFrostSource(frostState),
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
                                    text = buildString {
                                        append("Current mood: ")
                                        append(atmosphere.palette.displayName)
                                        atmosphere.weatherCondition?.let {
                                             append(" • ")
                                             append(it.displayName)
                                             append(" ")
                                             append(it.icon)
                                        }
                                    },
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
                        text = "DYNAMIC ATMOSPHERE SYSTEM",
                        fontFamily = ManropeFontFamily,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AetherEmber.Colors.AtmosphereTextSecondary,
                        letterSpacing = 1.0.sp,
                        modifier = Modifier.padding(start = AetherEmber.Spacing.Space24, bottom = AetherEmber.Spacing.Space8)
                    )

                    // Atmosphere Mode Pills (Static, Time-Based, Time + Weather, Manual)
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
                                        if (mode == AtmosphereMode.TIME_AND_WEATHER) {
                                            locationPermissionLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                                )
                                            )
                                        }
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

                // Automatic weather status. States what Aether actually knows.
                if (themeState.atmosphereMode == AtmosphereMode.TIME_AND_WEATHER) {
                    item {
                        Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space16))
                        val reading = atmosphere.weather
                        val headline = when (reading) {
                            is WeatherReading.Known -> "Weather: ${reading.condition.displayName}"
                            is WeatherReading.Override -> "Weather override: ${reading.condition.displayName}"
                            WeatherReading.Loading -> "Checking local weather…"
                            is WeatherReading.Unavailable -> "Weather unavailable"
                            WeatherReading.Idle -> "Weather not checked yet"
                        }
                        val detail = when (reading) {
                            is WeatherReading.Known ->
                                "Modulating the ${atmosphere.palette.displayName} palette."
                            is WeatherReading.Override ->
                                "Manual selection. Tap refresh to return to automatic."
                            WeatherReading.Loading -> "One moment."
                            is WeatherReading.Unavailable ->
                                reading.reason.message + " Using time-only atmosphere."
                            WeatherReading.Idle -> "Using time-only atmosphere."
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AetherEmber.Spacing.Space16)
                                .clip(AetherEmber.Shapes.M)
                                .background(Color(0x35000000))
                                .border(1.dp, Color(0x28FFFFFF), AetherEmber.Shapes.M)
                                .padding(horizontal = 16.dp, vertical = AetherEmber.Spacing.Space12)
                                .testTag("weather_status"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = atmosphere.weatherCondition?.icon ?: "🕓",
                                    fontSize = 20.sp
                                )
                                Spacer(modifier = Modifier.width(AetherEmber.Spacing.Space12))
                                Column {
                                    Text(
                                        text = headline,
                                        fontFamily = ManropeFontFamily,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space4))
                                    Text(
                                        text = detail,
                                        fontFamily = ManropeFontFamily,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = AetherEmber.Colors.AtmosphereTextTertiary
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x25FFFFFF))
                                    .clickable(enabled = !isFetchingWeather) {
                                        if (AtmosphereWeatherService.hasCoarseLocationPermission(context)) {
                                            refreshWeather()
                                        } else {
                                            locationPermissionLauncher.launch(
                                                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
                                            )
                                        }
                                    }
                                    .semantics { contentDescription = "Refresh weather" },
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
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }
                    }
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

                // Weather Modulation Controls (When Time + Weather or to test override)
                item {
                    Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space24))
                    Text(
                        text = "WEATHER OVERRIDE (TESTING)",
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
                        WeatherCondition.entries.forEach { weather ->
                            val isSelected = themeState.weatherOverride == weather
                            val bg = if (isSelected) atmosphere.accent.copy(alpha = 0.28f) else Color(0x35000000)
                            val borderCol = if (isSelected) atmosphere.accent else Color(0x28FFFFFF)

                            Row(
                                modifier = Modifier
                                    .clip(AetherEmber.Shapes.M)
                                    .background(bg)
                                    .border(1.dp, borderCol, AetherEmber.Shapes.M)
                                    .clickable {
                                        // Toggle: selecting the active override clears it
                                        // and hands control back to the real reading.
                                        themeState.weatherOverride =
                                            if (isSelected) null else weather
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = weather.icon, fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(AetherEmber.Spacing.Space8))
                                Text(
                                    text = weather.displayName,
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSelected) Color.White else AetherEmber.Colors.AtmosphereTextSecondary
                                )
                            }
                        }
                    }
                }

                // Privacy Note
                item {
                    Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space16))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AetherEmber.Spacing.Space16)
                            .clip(AetherEmber.Shapes.M)
                            .background(Color(0x30000000))
                            .border(1.dp, Color(0x24FFFFFF), AetherEmber.Shapes.M)
                            .padding(AetherEmber.Spacing.Space16),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Privacy",
                            tint = Color(0xFF90F0C0),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(AetherEmber.Spacing.Space12))
                        Text(
                            text = "Weather uses your approximate location only when needed. " +
                                "Aether does not continuously track or store your location. " +
                                "Approximate coordinates are sent to Open-Meteo to look up the current conditions.",
                            fontFamily = ManropeFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = AetherEmber.Colors.AtmosphereTextSecondary,
                            lineHeight = 17.sp
                        )
                    }
                }

                // Theme Mode Section (Dark, OLED, Light, Auto)
                item {
                    Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space24))
                    Text(
                        text = "THEME MODE",
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
                            .padding(horizontal = AetherEmber.Spacing.Space16),
                        horizontalArrangement = Arrangement.spacedBy(AetherEmber.Spacing.Space8)
                    ) {
                        ThemeModeCard(
                            mode = AppThemeMode.DARK,
                            label = "Ember Dark",
                            icon = Icons.Default.DarkMode,
                            isSelected = themeState.themeMode == AppThemeMode.DARK,
                            onClick = { themeState.setAndPersistThemeMode(AppThemeMode.DARK) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeModeCard(
                            mode = AppThemeMode.OLED,
                            label = "OLED",
                            icon = Icons.Default.DarkMode,
                            isSelected = themeState.themeMode == AppThemeMode.OLED,
                            onClick = { themeState.setAndPersistThemeMode(AppThemeMode.OLED) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeModeCard(
                            mode = AppThemeMode.LIGHT,
                            label = "Light",
                            icon = Icons.Default.LightMode,
                            isSelected = themeState.themeMode == AppThemeMode.LIGHT,
                            onClick = { themeState.setAndPersistThemeMode(AppThemeMode.LIGHT) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeModeCard(
                            mode = AppThemeMode.SYSTEM,
                            label = "Auto",
                            icon = Icons.Default.PhoneAndroid,
                            isSelected = themeState.themeMode == AppThemeMode.SYSTEM,
                            onClick = { themeState.setAndPersistThemeMode(AppThemeMode.SYSTEM) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Accent Color Section
                item {
                    Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space24))
                    Text(
                        text = "ACCENT SPECTRUM",
                        fontFamily = ManropeFontFamily,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AetherEmber.Colors.AtmosphereTextSecondary,
                        letterSpacing = 1.0.sp,
                        modifier = Modifier.padding(start = AetherEmber.Spacing.Space24, bottom = AetherEmber.Spacing.Space8)
                    )

                    Text(
                        text = "By default the accent follows the current atmosphere. " +
                            "Pick a colour to pin it instead.",
                        fontFamily = ManropeFontFamily,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 17.sp,
                        color = AetherEmber.Colors.AtmosphereTextTertiary,
                        modifier = Modifier.padding(horizontal = AetherEmber.Spacing.Space24, vertical = 2.dp)
                    )

                    Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space8))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = AetherEmber.Spacing.Space16),
                        horizontalArrangement = Arrangement.spacedBy(AetherEmber.Spacing.Space8)
                    ) {
                        AccentSwatch(
                            color = atmosphere.accent,
                            label = "Follow atmosphere",
                            isSelected = themeState.useAtmosphereAccent,
                            onClick = { themeState.setAndPersistUseAtmosphereAccent(true) },
                            modifier = Modifier.testTag("accent_atmosphere")
                        )
                        AccentColorChoice.entries.forEach { choice ->
                            AccentSwatch(
                                color = choice.primaryColor,
                                label = choice.displayName,
                                isSelected = !themeState.useAtmosphereAccent &&
                                    themeState.accentChoice == choice,
                                onClick = {
                                    themeState.setAndPersistAccentChoice(choice)
                                    themeState.setAndPersistUseAtmosphereAccent(false)
                                }
                            )
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
            .background(
                if (isSelected) LocalAtmosphere.current.accent.copy(alpha = 0.28f)
                else Color(0x35000000)
            )
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) LocalAtmosphere.current.accent else Color(0x28FFFFFF),
                shape = AetherEmber.Shapes.M
            )
            .clickable { onClick() }
            .padding(vertical = AetherEmber.Spacing.Space12),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) LocalAtmosphere.current.accent else AetherEmber.Colors.AtmosphereTextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space8))
        Text(
            text = label,
            fontFamily = ManropeFontFamily,
            fontSize = 11.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = Color.White
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
    color: Color,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(color)
            .clickable { onClick() }
            .then(
                if (isSelected) Modifier.border(2.5.dp, Color.White, CircleShape) else Modifier
            )
            .semantics {
                contentDescription = label
                selected = isSelected
            },
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
