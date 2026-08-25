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
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.MediaItem
import com.foresightlabs.aether.ui.components.AetherAtmosphericBackground
import com.foresightlabs.aether.ui.components.AetherAvatar
import com.foresightlabs.aether.ui.components.MediaViewer
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import com.foresightlabs.aether.ui.theme.OnlineGreen
import com.foresightlabs.aether.ui.theme.VerifiedBadge

@Composable
fun ProfileScreen(
    chat: Chat,
    onBack: () -> Unit,
    onNavigateToConversation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val user = chat.directUser
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Media", "Files", "Links", "Voice")

    var notificationsEnabled by remember { mutableStateOf(!chat.isMuted) }

    var selectedMediaItem by remember { mutableStateOf<MediaItem?>(null) }
    var isMediaViewerVisible by remember { mutableStateOf(false) }

    AetherAtmosphericBackground(
        modifier = modifier.fillMaxSize(),
        heroOnly = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // App Bar
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
                        .testTag("profile_back_button"),
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
                    text = "Profile Info",
                    fontFamily = ManropeFontFamily,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0x28000000))
                        .border(1.dp, Color(0x20FFFFFF), CircleShape)
                        .clickable { /* more options */ },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            // Hero Avatar & Name
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AetherAvatar(
                    initials = chat.avatarInitials,
                    gradient = chat.avatarGradient,
                    size = 88.dp,
                    isOnline = user?.isOnline == true,
                    chatType = chat.type,
                    photoPath = chat.photoPath,
                    showGlowingRim = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = chat.title,
                        fontFamily = ManropeFontFamily,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )

                    if (chat.isVerified) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Verified",
                            tint = VerifiedBadge,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = if (user?.isOnline == true) "online" else (user?.lastSeenText ?: chat.subtitle),
                    fontFamily = ManropeFontFamily,
                    fontSize = 13.5.sp,
                    color = if (user?.isOnline == true) Color(0xFF90F0C0) else Color(0xD0FFFFFF),
                    fontWeight = if (user?.isOnline == true) FontWeight.SemiBold else FontWeight.Normal
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Quick Action Buttons (Translucent dark/warm glass buttons)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    QuickActionButton(
                        icon = Icons.AutoMirrored.Filled.Message,
                        label = "Message",
                        onClick = onNavigateToConversation
                    )
                    QuickActionButton(
                        icon = Icons.Default.Call,
                        label = "Audio",
                        onClick = { /* simulated audio call */ }
                    )
                    QuickActionButton(
                        icon = Icons.Default.Videocam,
                        label = "Video",
                        onClick = { /* simulated video call */ }
                    )
                    QuickActionButton(
                        icon = Icons.Default.Search,
                        label = "Search",
                        onClick = { /* search in chat */ }
                    )
                }
            }

            // --- LOWER NEAR-BLACK RISING SHEET ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(AetherEmber.Shapes.RisingSheet)
                    .background(AetherEmber.Colors.Background)
                    .border(1.dp, Color(0x14FFFFFF), AetherEmber.Shapes.RisingSheet)
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    // Info Section Card
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                .clip(AetherEmber.Shapes.L)
                                .background(AetherEmber.Colors.SurfaceElevated)
                                .border(1.dp, AetherEmber.Colors.Border, AetherEmber.Shapes.L)
                                .padding(16.dp)
                        ) {
                            ProfileInfoRow(label = "Mobile", value = user?.phone.orEmpty().ifBlank { "Hidden" })
                            HorizontalDivider(
                                color = AetherEmber.Colors.BorderSubtle,
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                            ProfileInfoRow(label = "Username", value = user?.username.orEmpty().ifBlank { "—" })
                            if (!user?.bio.isNullOrEmpty()) {
                                HorizontalDivider(
                                    color = AetherEmber.Colors.BorderSubtle,
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(vertical = 10.dp)
                                )
                                ProfileInfoRow(label = "Bio", value = user?.bio.orEmpty())
                            }
                        }
                    }

                    // Settings & Security Card
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clip(AetherEmber.Shapes.L)
                                .background(AetherEmber.Colors.SurfaceElevated)
                                .border(1.dp, AetherEmber.Colors.Border, AetherEmber.Shapes.L)
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            // Notifications Switch
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = "Notifications",
                                        tint = AetherEmber.Colors.Accent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Notifications",
                                        fontFamily = ManropeFontFamily,
                                        fontSize = 15.sp,
                                        color = Color.White
                                    )
                                }

                                Switch(
                                    checked = notificationsEnabled,
                                    onCheckedChange = { notificationsEnabled = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = AetherEmber.Colors.Accent,
                                        uncheckedTrackColor = AetherEmber.Colors.SurfaceHighlight
                                    )
                                )
                            }

                            HorizontalDivider(color = AetherEmber.Colors.BorderSubtle, thickness = 0.5.dp)

                            // End to End Encryption
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Encryption",
                                        tint = OnlineGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "End-to-End Encryption",
                                            fontFamily = ManropeFontFamily,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Verified cryptographic signature active",
                                            fontFamily = ManropeFontFamily,
                                            fontSize = 12.sp,
                                            color = AetherEmber.Colors.TextTertiary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Shared Media / Docs Tab Header
                    item {
                        Spacer(modifier = Modifier.height(10.dp))
                        TabRow(
                            selectedTabIndex = selectedTabIndex,
                            containerColor = AetherEmber.Colors.Surface,
                            contentColor = AetherEmber.Colors.Accent,
                            indicator = { tabPositions ->
                                TabRowDefaults.SecondaryIndicator(
                                    Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                    color = AetherEmber.Colors.Accent
                                )
                            },
                            divider = {
                                HorizontalDivider(color = AetherEmber.Colors.BorderSubtle, thickness = 0.5.dp)
                            }
                        ) {
                            tabs.forEachIndexed { index, title ->
                                Tab(
                                    selected = selectedTabIndex == index,
                                    onClick = { selectedTabIndex = index },
                                    text = {
                                        Text(
                                            text = title,
                                            fontFamily = ManropeFontFamily,
                                            fontSize = 13.5.sp,
                                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                                            color = if (selectedTabIndex == index) AetherEmber.Colors.Accent else AetherEmber.Colors.TextSecondary
                                        )
                                    }
                                )
                            }
                        }
                    }

                    // Tab Content
                    when (selectedTabIndex) {
                        0 -> {
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp)
                                ) {
                                    Text(
                                        text = "Shared media is not loaded in this build.",
                                        fontFamily = ManropeFontFamily,
                                        fontSize = 13.sp,
                                        color = AetherEmber.Colors.TextTertiary
                                    )
                                }
                            }
                        }
                        1 -> {
                            item {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "No files loaded yet.",
                                        fontFamily = ManropeFontFamily,
                                        fontSize = 13.sp,
                                        color = AetherEmber.Colors.TextTertiary
                                    )
                                }
                            }
                        }
                        2 -> {
                            item {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    SharedLinkItem(
                                        url = "https://foresightlabs.com/aether",
                                        title = "Aether Messenger — High Performance Telegram Client"
                                    )
                                }
                            }
                        }
                        else -> {
                            item {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    SharedFileItem(name = "VoiceNote_Audio.m4a", size = "0:38 • Voice", date = "Today")
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(40.dp))
                    }
                }
            }
        }

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
            .clip(AetherEmber.Shapes.M)
            .clickable { onClick() }
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color(0x28000000))
                .border(1.dp, Color(0x24FFFFFF), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
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
private fun ProfileInfoRow(label: String, value: String) {
    Column {
        Text(
            text = value,
            fontFamily = ManropeFontFamily,
            fontSize = 15.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontFamily = ManropeFontFamily,
            fontSize = 12.5.sp,
            color = AetherEmber.Colors.TextTertiary
        )
    }
}

@Composable
private fun SharedFileItem(name: String, size: String, date: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AetherEmber.Shapes.M)
            .background(AetherEmber.Colors.SurfaceElevated)
            .border(1.dp, AetherEmber.Colors.Border, AetherEmber.Shapes.M)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(AetherEmber.Colors.AccentSubtle),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = AetherEmber.Colors.Accent,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontFamily = ManropeFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = size,
                fontFamily = ManropeFontFamily,
                fontSize = 12.sp,
                color = AetherEmber.Colors.TextTertiary
            )
        }

        Text(
            text = date,
            fontFamily = ManropeFontFamily,
            fontSize = 11.5.sp,
            color = AetherEmber.Colors.TextTertiary
        )
    }
}

@Composable
private fun SharedLinkItem(url: String, title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AetherEmber.Shapes.M)
            .background(AetherEmber.Colors.SurfaceElevated)
            .border(1.dp, AetherEmber.Colors.Border, AetherEmber.Shapes.M)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(AetherEmber.Colors.AccentSubtle),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = AetherEmber.Colors.Accent,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = ManropeFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = url,
                fontFamily = ManropeFontFamily,
                fontSize = 12.sp,
                color = AetherEmber.Colors.Accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
