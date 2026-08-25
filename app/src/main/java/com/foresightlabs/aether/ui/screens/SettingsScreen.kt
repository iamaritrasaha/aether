package com.foresightlabs.aether.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foresightlabs.aether.BuildConfig
import com.foresightlabs.aether.domain.model.User
import com.foresightlabs.aether.ui.components.AetherAtmosphericBackground
import com.foresightlabs.aether.ui.components.AetherAvatar
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.ManropeFontFamily

@Composable
fun SettingsScreen(
    currentUser: User?,
    confirmLogout: Boolean,
    onBack: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onRequestLogout: () -> Unit,
    onConfirmLogout: () -> Unit,
    onDismissLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                        .testTag("settings_back_button"),
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
                    text = "Settings",
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
                // Profile Card (Ember glass styled)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .clip(AetherEmber.Shapes.L)
                            .background(AetherEmber.Colors.SurfaceElevated)
                            .border(1.dp, AetherEmber.Colors.Border, AetherEmber.Shapes.L)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AetherAvatar(
                            initials = currentUser?.avatarInitials ?: "A",
                            gradient = currentUser?.avatarGradient ?: listOf(AetherEmber.Colors.Amber, AetherEmber.Colors.BrightOrange),
                            size = 60.dp,
                            isOnline = currentUser?.isOnline == true,
                            photoPath = currentUser?.photoPath,
                            showGlowingRim = true
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentUser?.name ?: "Telegram User",
                                fontFamily = ManropeFontFamily,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            if (!currentUser?.phone.isNullOrBlank()) {
                                Text(
                                    text = currentUser?.phone.orEmpty(),
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 13.5.sp,
                                    color = AetherEmber.Colors.TextSecondary
                                )
                            }
                            Text(
                                text = currentUser?.username?.ifBlank { "No username" } ?: "",
                                fontFamily = ManropeFontFamily,
                                fontSize = 13.sp,
                                color = AetherEmber.Colors.Accent
                            )
                            if (currentUser?.isPremium == true) {
                                Text(
                                    text = "Telegram Premium",
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 12.sp,
                                    color = AetherEmber.Colors.Amber
                                )
                            }
                        }
                    }
                }

                // Preferences Group
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "PREFERENCES",
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
                    ) {
                        SettingsRowItem(
                            icon = Icons.Default.Palette,
                            title = "Appearance & Atmosphere",
                            subtitle = "Dynamic Palettes, Weather Modulation, OLED, Accents",
                            onClick = onNavigateToAppearance,
                            testTag = "settings_appearance_item"
                        )
                    }
                }

                // System & Account Group
                item {
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "ACCOUNT & LEGAL",
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
                    ) {
                        SettingsRowItem(
                            icon = Icons.Default.Info,
                            title = "About Aether",
                            subtitle = "Version ${BuildConfig.VERSION_NAME} • TDLib ${BuildConfig.TDLIB_COMMIT.take(8)}",
                            onClick = { }
                        )
                        HorizontalDivider(
                            color = AetherEmber.Colors.BorderSubtle,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(start = 56.dp)
                        )
                        SettingsRowItem(
                            icon = Icons.Default.Person,
                            title = "Log Out",
                            subtitle = "Sign out of this Telegram account",
                            onClick = onRequestLogout
                        )
                    }
                }

                // Explicit Legal and Creator Attribution Section
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "Aether",
                            fontFamily = ManropeFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "© 2026 Aritra Saha / Foresight Labs. All rights reserved.",
                            fontFamily = ManropeFontFamily,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AetherEmber.Colors.TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Aether is an independent third-party client that uses the Telegram API. Aether is not affiliated with or endorsed by Telegram.",
                            fontFamily = ManropeFontFamily,
                            fontSize = 11.5.sp,
                            color = AetherEmber.Colors.TextTertiary,
                            lineHeight = 17.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Includes bundled open source software: TDLib (Boost Software License 1.0), Manrope Variable Font (SIL Open Font License 1.1), Space Grotesk (SIL Open Font License 1.1).",
                            fontFamily = ManropeFontFamily,
                            fontSize = 11.sp,
                            color = AetherEmber.Colors.TextTertiary.copy(alpha = 0.8f),
                            lineHeight = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }

        // Custom Logout Confirmation Modal
        if (confirmLogout) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable(onClick = onDismissLogout)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AetherEmber.Shapes.L)
                        .background(AetherEmber.Colors.Surface)
                        .border(1.dp, Color(0x28FFFFFF), AetherEmber.Shapes.L)
                        .clickable(enabled = false) {}
                        .padding(22.dp)
                ) {
                    Text(
                        text = "Log out of Aether?",
                        fontFamily = ManropeFontFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "This signs this device out of Telegram through TDLib. Your chats and media stay safe on Telegram's cloud servers.",
                        fontFamily = ManropeFontFamily,
                        fontSize = 13.5.sp,
                        color = AetherEmber.Colors.TextSecondary,
                        lineHeight = 19.sp
                    )

                    Spacer(modifier = Modifier.height(22.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismissLogout) {
                            Text(
                                "Cancel",
                                fontFamily = ManropeFontFamily,
                                color = AetherEmber.Colors.TextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            modifier = Modifier
                                .clip(AetherEmber.Shapes.Pill)
                                .background(Color(0xFFEF4444))
                                .clickable(onClick = onConfirmLogout)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "Log Out",
                                fontFamily = ManropeFontFamily,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String = ""
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(AetherEmber.Colors.AccentSubtle),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = AetherEmber.Colors.Accent,
                modifier = Modifier.size(19.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = ManropeFontFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = subtitle,
                fontFamily = ManropeFontFamily,
                fontSize = 12.sp,
                color = AetherEmber.Colors.TextTertiary
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = AetherEmber.Colors.TextTertiary.copy(alpha = 0.6f),
            modifier = Modifier.size(12.dp)
        )
    }
}
