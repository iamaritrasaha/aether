package com.foresightlabs.aether.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ChatType
import com.foresightlabs.aether.domain.model.MediaItem
import com.foresightlabs.aether.ui.components.AetherAtmosphericScreen
import com.foresightlabs.aether.ui.components.AetherAvatar
import com.foresightlabs.aether.ui.components.MediaViewer
import com.foresightlabs.aether.ui.design.AetherAccent
import com.foresightlabs.aether.ui.design.AetherBackButton
import com.foresightlabs.aether.ui.design.AetherFloatingHeader
import com.foresightlabs.aether.ui.design.AetherIconButton
import com.foresightlabs.aether.ui.design.aetherFloatingHeaderContentTopPadding
import com.foresightlabs.aether.ui.design.rememberAetherFloatingHeaderScrollFraction
import com.foresightlabs.aether.ui.design.rememberAetherFrostState
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.LocalAtmosphere
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import com.foresightlabs.aether.ui.theme.OnlineGreen
import com.foresightlabs.aether.ui.theme.SpaceGroteskFontFamily
import com.foresightlabs.aether.ui.theme.VerifiedBadge

/**
 * Modernized Aether Profile Info Screen.
 *
 * Layer 1: Continuous full-screen Living Atmosphere
 * Layer 2: Hero identity (Avatar, Name, Status, Glass Action Strip)
 * Layer 3: Elevated dark-glass information, privacy & shared media panels floating over atmosphere
 */
@Composable
fun ProfileScreen(
    chat: Chat,
    onBack: () -> Unit,
    onNavigateToConversation: () -> Unit,
    onStartVoiceCall: () -> Unit = {},
    onStartVideoCall: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LocalAetherColors.current
    val user = chat.directUser
    val isSecretChat = chat.type == ChatType.SECRET
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Media", "Files", "Links", "Voice")

    var notificationsEnabled by remember { mutableStateOf(!chat.isMuted) }
    var selectedMediaItem by remember { mutableStateOf<MediaItem?>(null) }
    var isMediaViewerVisible by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val headerScrollFraction = rememberAetherFloatingHeaderScrollFraction(listState)
    val frostState = rememberAetherFrostState()

    Box(modifier = modifier.fillMaxSize()) {
        AetherAtmosphericScreen(
            modifier = Modifier.fillMaxSize(),
            frostState = frostState
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = aetherFloatingHeaderContentTopPadding())
            ) {
                // --- HERO IDENTITY SECTION ---
                item(key = "profile_hero") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Prominent Glowing Avatar
                        AetherAvatar(
                            initials = chat.avatarInitials,
                            gradient = chat.avatarGradient,
                            size = 96.dp,
                            isOnline = user?.isOnline == true,
                            chatType = chat.type,
                            photoPath = chat.photoPath,
                            showGlowingRim = true
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Full Name with Verified badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        ) {
                            Text(
                                text = chat.title,
                                fontFamily = SpaceGroteskFontFamily,
                                fontSize = 23.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (chat.isVerified) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = "Verified",
                                    tint = VerifiedBadge,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Status Badge / Last Seen
                        if (user?.isOnline == true) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(AetherEmber.Shapes.Pill)
                                    .background(Color(0x30000000))
                                    .border(1.dp, Color(0x2234D399), AetherEmber.Shapes.Pill)
                                    .padding(horizontal = 10.dp, vertical = 3.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(OnlineGreen)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "online",
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 12.sp,
                                    color = Color(0xFF90F0C0),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Text(
                                text = user?.lastSeenText ?: chat.subtitle,
                                fontFamily = ManropeFontFamily,
                                fontSize = 13.sp,
                                color = Color(0xD8FFFFFF),
                                fontWeight = FontWeight.Normal
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Refined Circular Glass Action Strip
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 28.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            QuickActionButton(
                                icon = Icons.AutoMirrored.Filled.Message,
                                label = "Message",
                                onClick = onNavigateToConversation
                            )
                            QuickActionButton(
                                icon = Icons.Default.Call,
                                label = "Audio",
                                onClick = onStartVoiceCall
                            )
                            QuickActionButton(
                                icon = Icons.Default.Videocam,
                                label = "Video",
                                onClick = onStartVideoCall
                            )
                            QuickActionButton(
                                icon = Icons.Default.Search,
                                label = "Search",
                                onClick = { /* search in chat */ }
                            )
                        }
                    }
                }

                // --- LAYER 3: ELEVATED DETAILS PANELS FLOATING OVER CONTINUOUS ATMOSPHERE ---
                item(key = "profile_details_card") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(AetherEmber.Shapes.L)
                            .background(colors.surfaceElevated)
                            .border(1.dp, colors.border, AetherEmber.Shapes.L)
                            .padding(18.dp)
                    ) {
                        Text(
                            text = "INFO",
                            fontFamily = ManropeFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.textTertiary,
                            letterSpacing = 1.5.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        ProfileInfoRow(
                            icon = Icons.Default.Phone,
                            label = "Mobile",
                            value = user?.phone.orEmpty().ifBlank { "Hidden" }
                        )

                        HorizontalDivider(
                            color = colors.divider,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )

                        ProfileInfoRow(
                            icon = Icons.Default.AlternateEmail,
                            label = "Username",
                            value = user?.username.orEmpty().ifBlank { "—" }
                        )

                        if (!user?.bio.isNullOrEmpty()) {
                            HorizontalDivider(
                                color = Color(0x14FFFFFF),
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                            ProfileInfoRow(
                                icon = Icons.Default.Info,
                                label = "Bio",
                                value = user?.bio.orEmpty()
                            )
                        }
                    }
                }

                // Settings & Privacy Card
                item(key = "profile_settings_card") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(AetherEmber.Shapes.L)
                            .background(colors.surfaceElevated)
                            .border(1.dp, colors.border, AetherEmber.Shapes.L)
                            .padding(18.dp)
                    ) {
                        // Notifications Switch Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(colors.input),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = "Notifications",
                                        tint = AetherAccent.current,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Notifications",
                                        fontFamily = ManropeFontFamily,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = if (notificationsEnabled) "Sound and alerts enabled" else "Muted",
                                        fontFamily = ManropeFontFamily,
                                        fontSize = 12.sp,
                                        color = colors.textSecondary
                                    )
                                }
                            }

                            Switch(
                                checked = notificationsEnabled,
                                onCheckedChange = { notificationsEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AetherAccent.current,
                                    uncheckedThumbColor = Color(0xAAFFFFFF),
                                    uncheckedTrackColor = Color(0x24FFFFFF)
                                )
                            )
                        }

                        HorizontalDivider(
                            color = colors.divider,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )

                        // Truthful Security Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isSecretChat) Color(0x1A34D399) else colors.input),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Security",
                                    tint = if (isSecretChat) OnlineGreen else colors.textSecondary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isSecretChat) "End-to-End Encryption" else "Security",
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = if (isSecretChat)
                                        "Telegram Secret Chat with device-to-device encryption"
                                    else
                                        "Telegram cloud chat • Protected using Telegram’s MTProto protocol",
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 12.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }

                // --- SHARED CONTENT SECTION & MODERN TABS ---
                item(key = "profile_tabs") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        // Pill Segmented Tab Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(AetherEmber.Shapes.Pill)
                                .background(colors.surfaceElevated)
                                .border(1.dp, colors.border, AetherEmber.Shapes.Pill)
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            tabs.forEachIndexed { index, title ->
                                val isSelected = selectedTabIndex == index
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .clip(AetherEmber.Shapes.Pill)
                                        .background(
                                            if (isSelected) AetherAccent.current else Color.Transparent
                                        )
                                        .clickable { selectedTabIndex = index },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = title,
                                        fontFamily = ManropeFontFamily,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected && colors.isDark) Color.White else colors.textPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                // Tab Content with Polished Truthful Empty States
                item(key = "tab_content_$selectedTabIndex") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(AetherEmber.Shapes.L)
                            .background(colors.surfaceElevated)
                            .border(1.dp, colors.border, AetherEmber.Shapes.L)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when (selectedTabIndex) {
                            0 -> DesignedEmptyState(
                                icon = Icons.Default.PermMedia,
                                title = "No Shared Media",
                                description = "Photos and videos shared in this conversation will be listed here."
                            )
                            1 -> DesignedEmptyState(
                                icon = Icons.Default.Description,
                                title = "No Shared Files",
                                description = "Documents, archives, and shared files will appear here."
                            )
                            2 -> Column(modifier = Modifier.fillMaxWidth()) {
                                SharedLinkItem(
                                    url = "https://telegram.org",
                                    title = "Telegram Official Platform & API"
                                )
                            }
                            else -> DesignedEmptyState(
                                icon = Icons.Default.Mic,
                                title = "No Voice Notes",
                                description = "Voice messages and audio recordings will appear here."
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }

        AetherFloatingHeader(
            title = "Profile Info",
            modifier = Modifier.align(Alignment.TopCenter),
            scrollFraction = headerScrollFraction,
            frostState = frostState,
            navigation = {
                AetherBackButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("profile_back_button")
                )
            },
            actions = {
                AetherIconButton(
                    icon = Icons.Default.MoreVert,
                    contentDescription = "More",
                    onClick = { /* more options */ }
                )
            }
        )

        // Media Viewer Overlay
        MediaViewer(
            mediaItem = selectedMediaItem,
            senderName = chat.title,
            isVisible = isMediaViewerVisible,
            onClose = {
                isMediaViewerVisible = false
                selectedMediaItem = null
            }
        )
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0x30000000))
                .border(1.dp, Color(0x25FFFFFF), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(21.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = label,
            fontFamily = ManropeFontFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}

@Composable
private fun ProfileInfoRow(icon: ImageVector, label: String, value: String) {
    val colors = LocalAetherColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(colors.input),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = colors.textSecondary,
                modifier = Modifier.size(17.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = value,
                fontFamily = ManropeFontFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            Text(
                text = label,
                fontFamily = ManropeFontFamily,
                fontSize = 11.5.sp,
                color = colors.textTertiary
            )
        }
    }
}

@Composable
private fun DesignedEmptyState(
    icon: ImageVector,
    title: String,
    description: String
) {
    val colors = LocalAetherColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AetherEmber.Spacing.Space16),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AetherEmber.Spacing.Space8)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(colors.input)
                .border(1.dp, colors.border, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = colors.textTertiary,
                modifier = Modifier.size(24.dp)
            )
        }

        Text(
            text = title,
            fontFamily = SpaceGroteskFontFamily,
            fontSize = 15.5.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary
        )

        Text(
            text = description,
            fontFamily = ManropeFontFamily,
            fontSize = 12.5.sp,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun SharedLinkItem(url: String, title: String) {
    val colors = LocalAetherColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AetherEmber.Shapes.S)
            .background(colors.input)
            .border(1.dp, colors.border, AetherEmber.Shapes.S)
            .padding(AetherEmber.Spacing.Space12),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(colors.surfaceHighlight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Link,
                contentDescription = "Link",
                tint = AetherAccent.current,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = ManropeFontFamily,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = url,
                fontFamily = ManropeFontFamily,
                fontSize = 11.5.sp,
                color = AetherAccent.current,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = "Open",
            tint = colors.textTertiary,
            modifier = Modifier.size(16.dp)
        )
    }
}
