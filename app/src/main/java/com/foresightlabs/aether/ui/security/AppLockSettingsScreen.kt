package com.foresightlabs.aether.ui.security

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.foresightlabs.aether.data.security.BiometricAuthenticator
import com.foresightlabs.aether.domain.security.AutoLockDuration
import com.foresightlabs.aether.ui.design.AetherAccent
import com.foresightlabs.aether.ui.design.AetherAtmosphericBackground
import com.foresightlabs.aether.ui.design.AetherBackButton
import com.foresightlabs.aether.ui.design.AetherFloatingHeader
import com.foresightlabs.aether.ui.design.aetherFloatingHeaderContentTopPadding
import com.foresightlabs.aether.ui.design.rememberAetherFloatingHeaderScrollFraction
import com.foresightlabs.aether.ui.design.rememberAetherFrostState
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import androidx.compose.ui.platform.LocalContext

/**
 * Settings > App Lock. Does not redesign Settings -- same section/card/row
 * language as [com.foresightlabs.aether.ui.settings.SettingsScreen].
 */
@Composable
fun AppLockSettingsScreen(
    onBack: () -> Unit,
    onRequestSetup: () -> Unit,
    onRequestChangePasscode: () -> Unit,
    onRequestDisable: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: AppLockSettingsViewModel = viewModel()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val colors = LocalAetherColors.current
    val listState = rememberLazyListState()
    val headerScrollFraction = rememberAetherFloatingHeaderScrollFraction(listState)
    val frostState = rememberAetherFrostState()
    val context = LocalContext.current
    val biometricSupported = remember(context) { BiometricAuthenticator.canAuthenticate(context) }
    var showAutoLockPicker by remember { mutableStateOf(false) }

    val enabled = settings?.enabled == true

    Box(modifier = modifier.fillMaxSize()) {
        AetherAtmosphericBackground(modifier = Modifier.fillMaxSize(), frostState = frostState) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag("app_lock_settings_list"),
                contentPadding = PaddingValues(top = aetherFloatingHeaderContentTopPadding(), bottom = 40.dp)
            ) {
                item {
                    Section(title = "PROTECT AETHER") {
                        ToggleRow(
                            title = "App Lock",
                            subtitle = if (enabled) "On" else "Off",
                            checked = enabled,
                            testTag = "app_lock_toggle",
                            onCheckedChange = { turnOn ->
                                if (turnOn) onRequestSetup() else onRequestDisable()
                            }
                        )
                    }
                }

                if (enabled) {
                    item {
                        Spacer(modifier = Modifier.height(18.dp))
                        Section(title = "PASSCODE") {
                            SettingsActionRow(title = "Change passcode", onClick = onRequestChangePasscode, testTag = "app_lock_change_passcode")
                            if (biometricSupported) {
                                Divider(colors)
                                ToggleRow(
                                    title = "Unlock with biometrics",
                                    subtitle = "Use your device's fingerprint or face unlock",
                                    checked = settings?.biometricEnabled == true,
                                    testTag = "app_lock_biometric_toggle",
                                    onCheckedChange = viewModel::setBiometricEnabled
                                )
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(18.dp))
                        Section(title = "AUTO-LOCK") {
                            SettingsActionRow(
                                title = "Auto-lock",
                                subtitle = (settings?.autoLockDuration ?: AutoLockDuration.IMMEDIATELY).displayName,
                                onClick = { showAutoLockPicker = true },
                                testTag = "app_lock_auto_lock_row"
                            )
                            Divider(colors)
                            SettingsActionRow(title = "Lock Aether now", onClick = viewModel::lockNow, testTag = "app_lock_now_row")
                        }
                    }
                }
            }
        }

        AetherFloatingHeader(
            title = "App Lock",
            modifier = Modifier.align(Alignment.TopCenter),
            scrollFraction = headerScrollFraction,
            frostState = frostState,
            navigation = { AetherBackButton(onClick = onBack) }
        )

        if (showAutoLockPicker) {
            AutoLockPicker(
                current = settings?.autoLockDuration ?: AutoLockDuration.IMMEDIATELY,
                onSelect = { duration ->
                    viewModel.setAutoLockDuration(duration)
                    showAutoLockPicker = false
                },
                onDismiss = { showAutoLockPicker = false }
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val colors = LocalAetherColors.current
    Text(
        text = title,
        fontFamily = ManropeFontFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        color = AetherEmber.Colors.AtmosphereTextSecondary,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 24.dp, bottom = 6.dp)
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(AetherEmber.Shapes.L)
            .background(colors.surfaceElevated)
            .border(1.dp, colors.border, AetherEmber.Shapes.L)
    ) {
        content()
    }
}

@Composable
private fun Divider(colors: com.foresightlabs.aether.ui.theme.AetherColors) {
    HorizontalDivider(color = colors.divider, thickness = 0.5.dp, modifier = Modifier.padding(start = 20.dp))
}

@Composable
private fun SettingsActionRow(title: String, subtitle: String? = null, onClick: () -> Unit, testTag: String) {
    val colors = LocalAetherColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontFamily = ManropeFontFamily, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            subtitle?.let {
                Spacer(modifier = Modifier.height(1.dp))
                Text(it, fontFamily = ManropeFontFamily, fontSize = 12.sp, color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = LocalAetherColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontFamily = ManropeFontFamily, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            Spacer(modifier = Modifier.height(1.dp))
            Text(subtitle, fontFamily = ManropeFontFamily, fontSize = 12.sp, color = colors.textSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
            colors = SwitchDefaults.colors(checkedTrackColor = AetherAccent.current)
        )
    }
}

@Composable
private fun AutoLockPicker(
    current: AutoLockDuration,
    onSelect: (AutoLockDuration) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalAetherColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(onClick = onDismiss)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false) {}
                .clip(AetherEmber.Shapes.L)
                .background(colors.surfaceElevated)
                .border(1.dp, colors.border, AetherEmber.Shapes.L)
                .padding(vertical = 8.dp)
                .testTag("app_lock_auto_lock_picker")
        ) {
            AutoLockDuration.entries.forEach { duration ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(duration) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = duration.displayName,
                        fontFamily = ManropeFontFamily,
                        fontSize = 15.sp,
                        fontWeight = if (duration == current) FontWeight.Bold else FontWeight.Normal,
                        color = if (duration == current) AetherAccent.current else colors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
