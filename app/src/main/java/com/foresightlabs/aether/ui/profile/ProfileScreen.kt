package com.foresightlabs.aether.ui.profile
import android.content.Intent
import android.net.Uri

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.foresightlabs.aether.data.telegram.TelegramClient
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.MessageType
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
import androidx.compose.ui.window.Dialog
import com.foresightlabs.aether.domain.chats.ChatAction
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ChatType
import com.foresightlabs.aether.domain.model.MediaItem
import com.foresightlabs.aether.ui.design.AetherAtmosphericScreen
import com.foresightlabs.aether.ui.design.AetherAvatar
import com.foresightlabs.aether.ui.common.MediaViewer
import com.foresightlabs.aether.ui.design.AetherAccent
import com.foresightlabs.aether.ui.design.AetherBackButton
import com.foresightlabs.aether.ui.design.AetherFloatingHeader
import com.foresightlabs.aether.ui.design.AetherGlass
import com.foresightlabs.aether.ui.design.aetherFloatingHeaderContentTopPadding
import com.foresightlabs.aether.ui.design.rememberAetherFloatingHeaderScrollFraction
import com.foresightlabs.aether.ui.design.rememberAetherFrostState
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import com.foresightlabs.aether.ui.theme.OnlineGreen
import com.foresightlabs.aether.ui.theme.SpaceGroteskFontFamily
import com.foresightlabs.aether.ui.theme.VerifiedBadge

/**
 * Modernized Aether Profile Screen.
 *
 * Exposes all legitimate conversation actions directly in-page organized into
 * clear, restrained sections with zero 3-dot overflow dumping ground:
 * - Profile Identity & Quick Actions
 * - Info (Phone, Username, Bio)
 * - Conversation Controls (Notifications toggle, Search conversation)
 * - Shared Media & Content
 * - Privacy & Security (MTProto cloud encryption info, Block/Unblock)
 * - Conversation Management (Clear history, Delete conversation with confirmation)
 */
@Composable
fun ProfileScreen(
    chat: Chat,
    onBack: () -> Unit,
    onNavigateToConversation: () -> Unit,
    onSearchConversation: () -> Unit = onNavigateToConversation,
    onStartVoiceCall: () -> Unit = {},
    onStartVideoCall: () -> Unit = {},
    onChatAction: (Chat, ChatAction) -> Unit = { _, _ -> },
    onLoadSharedMedia: suspend (Long, TelegramClient.SharedMediaCategory, Long) -> TelegramClient.SharedMediaPage = { _, _, _ -> TelegramClient.SharedMediaPage(emptyList(), 0L, 0) },
    onRequestMediaDownload: (Int, Boolean) -> Unit = { _, _ -> },
    canCallAudio: Boolean = false,
    canCallVideo: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = LocalAetherColors.current
    val context = LocalContext.current
    val user = chat.directUser
    val isSecretChat = chat.type == ChatType.SECRET
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Media", "Files", "Links", "Voice")

    val chatIdLong = chat.id.toLongOrNull() ?: 0L
    var mediaItems by remember(chat.id) { mutableStateOf<List<MediaItem>>(emptyList()) }
    var fileItems by remember(chat.id) { mutableStateOf<List<Message>>(emptyList()) }
    var linkItems by remember(chat.id) { mutableStateOf<List<SharedLinkData>>(emptyList()) }
    var voiceItems by remember(chat.id) { mutableStateOf<List<Message>>(emptyList()) }

    var mediaNextOffset by remember(chat.id) { mutableLongStateOf(0L) }
    var filesNextOffset by remember(chat.id) { mutableLongStateOf(0L) }
    var linksNextOffset by remember(chat.id) { mutableLongStateOf(0L) }
    var voiceNextOffset by remember(chat.id) { mutableLongStateOf(0L) }

    var isLoadingMedia by remember(chat.id) { mutableStateOf(false) }

    LaunchedEffect(chatIdLong) {
        if (chatIdLong != 0L) {
            isLoadingMedia = true
            val page = onLoadSharedMedia(chatIdLong, TelegramClient.SharedMediaCategory.MEDIA, 0L)
            mediaItems = page.messages.flatMap { it.mediaItems }.distinctBy { it.id.ifBlank { it.fileId.toString() } }
            mediaNextOffset = page.nextFromMessageId
            isLoadingMedia = false
        }
    }

    LaunchedEffect(selectedTabIndex, chatIdLong) {
        if (chatIdLong == 0L) return@LaunchedEffect
        when (selectedTabIndex) {
            // Tab 0 (Media) is deliberately absent: the LaunchedEffect above already
            // owns loading it on entry. Duplicating it here fired two identical
            // SearchChatMessages requests on every profile open -- both effects start
            // at first composition, so neither guard stopped the other.
            1 -> {
                if (fileItems.isEmpty() && filesNextOffset == 0L) {
                    val page = onLoadSharedMedia(chatIdLong, TelegramClient.SharedMediaCategory.FILES, 0L)
                    fileItems = page.messages.distinctBy { it.id }
                    filesNextOffset = page.nextFromMessageId
                }
            }
            2 -> {
                if (linkItems.isEmpty() && linksNextOffset == 0L) {
                    val page = onLoadSharedMedia(chatIdLong, TelegramClient.SharedMediaCategory.LINKS, 0L)
                    linkItems = page.messages.mapNotNull { extractLinkFromMessage(it) }
                    linksNextOffset = page.nextFromMessageId
                }
            }
            3 -> {
                if (voiceItems.isEmpty() && voiceNextOffset == 0L) {
                    val page = onLoadSharedMedia(chatIdLong, TelegramClient.SharedMediaCategory.VOICE, 0L)
                    voiceItems = page.messages.distinctBy { it.id }
                    voiceNextOffset = page.nextFromMessageId
                }
            }
        }
    }

    var selectedMediaItem by remember { mutableStateOf<MediaItem?>(null) }
    var isMediaViewerVisible by remember { mutableStateOf(false) }

    // Pending destructive action for confirmation modal
    var pendingAction by remember { mutableStateOf<ChatAction?>(null) }

    val listState = rememberLazyListState()
    val headerScrollFraction = rememberAetherFloatingHeaderScrollFraction(listState)
    val frostState = rememberAetherFrostState()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier = modifier.fillMaxSize()) {
        AetherAtmosphericScreen(
            modifier = Modifier.fillMaxSize(),
            frostState = frostState
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("profile_lazy_column"),
                contentPadding = PaddingValues(
                    top = aetherFloatingHeaderContentTopPadding(),
                    bottom = bottomInset + 32.dp
                )
            ) {
                // --- SECTION 1: HERO IDENTITY ---
                item(key = "profile_hero") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Avatar with soft glowing rim
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

                        // Full Name with optional Verified badge
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
                                text = user?.lastSeenText ?: chat.subtitle.ifBlank { "offline" },
                                fontFamily = ManropeFontFamily,
                                fontSize = 13.sp,
                                color = Color(0xD8FFFFFF),
                                fontWeight = FontWeight.Normal
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Circular Glass Quick Action Strip
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            QuickActionButton(
                                icon = Icons.AutoMirrored.Filled.Message,
                                label = "Message",
                                onClick = onNavigateToConversation,
                                testTag = "profile_action_message"
                            )
                            if (com.foresightlabs.aether.AetherFeatureFlags.CALLS_ENABLED) {
                                QuickActionButton(
                                    icon = Icons.Default.Call,
                                    label = "Audio",
                                    onClick = onStartVoiceCall,
                                    enabled = canCallAudio,
                                    testTag = "profile_action_audio"
                                )
                                QuickActionButton(
                                    icon = Icons.Default.Videocam,
                                    label = "Video",
                                    onClick = onStartVideoCall,
                                    enabled = canCallVideo,
                                    testTag = "profile_action_video"
                                )
                            }
                            QuickActionButton(
                                icon = Icons.Default.Search,
                                label = "Search",
                                onClick = onSearchConversation,
                                testTag = "profile_action_search"
                            )
                        }
                    }
                }

                // --- SECTION 2: INFO CARD ---
                if (user?.phone?.isNotBlank() == true || user?.username?.isNotBlank() == true || !user?.bio.isNullOrBlank()) {
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

                            if (user?.phone?.isNotBlank() == true) {
                                ProfileInfoRow(
                                    icon = Icons.Default.Phone,
                                    label = "Mobile",
                                    value = user.phone
                                )
                            }

                            if (user?.username?.isNotBlank() == true) {
                                if (user.phone.isNotBlank()) {
                                    HorizontalDivider(
                                        color = colors.divider,
                                        thickness = 0.5.dp,
                                        modifier = Modifier.padding(vertical = 12.dp)
                                    )
                                }
                                ProfileInfoRow(
                                    icon = Icons.Default.AlternateEmail,
                                    label = "Username",
                                    value = "@${user.username}"
                                )
                            }

                            if (!user?.bio.isNullOrBlank()) {
                                HorizontalDivider(
                                    color = colors.divider,
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                                ProfileInfoRow(
                                    icon = Icons.Default.Info,
                                    label = "Bio",
                                    value = user.bio ?: ""
                                )
                            }
                        }
                    }
                }

                // --- SECTION 3: CONVERSATION CONTROLS ---
                item(key = "profile_conversation_card") {
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
                            text = "CONVERSATION",
                            fontFamily = ManropeFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.textTertiary,
                            letterSpacing = 1.5.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Notifications Switch Row
                        ProfileActionRow(
                            icon = if (chat.isMuted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                            title = "Notifications",
                            subtitle = if (chat.isMuted) "Muted" else "On",
                            onClick = {
                                onChatAction(
                                    chat,
                                    if (chat.isMuted) ChatAction.UNMUTE else ChatAction.MUTE
                                )
                            },
                            trailing = {
                                Switch(
                                    checked = !chat.isMuted,
                                    onCheckedChange = { checked ->
                                        onChatAction(
                                            chat,
                                            if (checked) ChatAction.UNMUTE else ChatAction.MUTE
                                        )
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = AetherAccent.current,
                                        uncheckedThumbColor = Color(0xAAFFFFFF),
                                        uncheckedTrackColor = Color(0x24FFFFFF)
                                    ),
                                    modifier = Modifier.testTag("profile_notifications_switch")
                                )
                            },
                            testTag = "profile_row_notifications"
                        )

                        HorizontalDivider(
                            color = colors.divider,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        // Search in Conversation Row
                        ProfileActionRow(
                            icon = Icons.Default.Search,
                            title = "Search conversation",
                            subtitle = "Find messages and media in this chat",
                            onClick = onSearchConversation,
                            testTag = "profile_row_search"
                        )
                    }
                }

                // --- SECTION 4: SHARED CONTENT & TABS ---
                if (mediaItems.isNotEmpty()) {
                    item(key = "profile_shared_media_preview") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clip(AetherEmber.Shapes.L)
                                .background(colors.surfaceElevated)
                                .border(1.dp, colors.border, AetherEmber.Shapes.L)
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SHARED MEDIA",
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = colors.textTertiary,
                                    letterSpacing = 1.5.sp
                                )
                                Text(
                                    text = "See all (${mediaItems.size})",
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AetherAccent.current,
                                    modifier = Modifier.clickable { selectedTabIndex = 0 }
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val previewSubset = mediaItems.take(4)
                                previewSubset.forEach { item ->
                                    SharedMediaThumbnail(
                                        mediaItem = item,
                                        onClick = {
                                            selectedMediaItem = item
                                            isMediaViewerVisible = true
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat((4 - previewSubset.size).coerceAtLeast(0)) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                item(key = "profile_tabs") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        AetherGlass(
                            frostState = frostState,
                            modifier = Modifier.fillMaxWidth(),
                            shape = AetherEmber.Shapes.Pill,
                            elevation = 2.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp)
                                    .testTag("profile_tab_bar"),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                tabs.forEachIndexed { index, tabTitle ->
                                    val isSelected = selectedTabIndex == index
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(AetherEmber.Shapes.Pill)
                                            .background(
                                                if (isSelected) AetherAccent.current.copy(alpha = 0.22f)
                                                else Color.Transparent
                                            )
                                            .clickable { selectedTabIndex = index }
                                            .padding(vertical = 8.dp)
                                            .testTag("profile_tab_${tabTitle.lowercase()}"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = tabTitle,
                                            fontFamily = ManropeFontFamily,
                                            fontSize = 12.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else colors.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "profile_tab_content") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(AetherEmber.Shapes.L)
                            .background(colors.surfaceElevated)
                            .border(1.dp, colors.border, AetherEmber.Shapes.L)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when (selectedTabIndex) {
                            0 -> {
                                if (mediaItems.isEmpty() && !isLoadingMedia) {
                                    DesignedEmptyState(
                                        icon = Icons.Default.PermMedia,
                                        title = "No Shared Media",
                                        description = "Photos and videos shared in this conversation will appear here."
                                    )
                                } else if (mediaItems.isEmpty() && isLoadingMedia) {
                                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = AetherAccent.current, strokeWidth = 2.dp)
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        mediaItems.chunked(3).forEach { rowItems ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                rowItems.forEach { item ->
                                                    SharedMediaThumbnail(
                                                        mediaItem = item,
                                                        onClick = {
                                                            selectedMediaItem = item
                                                            isMediaViewerVisible = true
                                                        },
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                                repeat(3 - rowItems.size) {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            1 -> {
                                if (fileItems.isEmpty()) {
                                    DesignedEmptyState(
                                        icon = Icons.Default.Description,
                                        title = "No Shared Files",
                                        description = "Documents, archives, and shared files will appear here."
                                    )
                                } else {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        fileItems.forEach { msg ->
                                            SharedFileRow(
                                                fileName = msg.fileName.orEmpty().ifBlank { "Document" },
                                                fileSize = msg.fileSize.orEmpty().ifBlank { "File" },
                                                timestamp = msg.timestamp
                                            )
                                        }
                                    }
                                }
                            }
                            2 -> {
                                if (linkItems.isEmpty()) {
                                    DesignedEmptyState(
                                        icon = Icons.Default.Link,
                                        title = "No Shared Links",
                                        description = "Links shared in this conversation will appear here."
                                    )
                                } else {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        linkItems.forEach { link ->
                                            SharedLinkItem(
                                                url = link.url,
                                                title = link.title,
                                                onClick = {
                                                    runCatching {
                                                        context.startActivity(
                                                            Intent(Intent.ACTION_VIEW, Uri.parse(link.url))
                                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        )
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            else -> {
                                if (voiceItems.isEmpty()) {
                                    DesignedEmptyState(
                                        icon = Icons.Default.Mic,
                                        title = "No Voice Notes",
                                        description = "Voice messages and audio recordings will appear here."
                                    )
                                } else {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        voiceItems.forEach { msg ->
                                            SharedVoiceRow(
                                                durationSec = msg.voiceDurationSec,
                                                timestamp = msg.timestamp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // --- SECTION 5: PRIVACY & SAFETY ---
                item(key = "profile_privacy_card") {
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
                            text = "PRIVACY & SAFETY",
                            fontFamily = ManropeFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.textTertiary,
                            letterSpacing = 1.5.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Truthful Security Row
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
                            Spacer(modifier = Modifier.width(14.dp))
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
                                        "Telegram cloud chat • Protected using Telegram MTProto protocol",
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 12.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }

                        // Block / Unblock Row (for direct 1:1 chats)
                        if (chat.type == ChatType.DIRECT && chat.blockableUserId != null) {
                            HorizontalDivider(
                                color = colors.divider,
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )

                            if (chat.isBlocked) {
                                ProfileActionRow(
                                    icon = Icons.Default.Block,
                                    title = "Unblock ${chat.title}",
                                    subtitle = "Allow messages and calls from this person",
                                    onClick = { onChatAction(chat, ChatAction.UNBLOCK) },
                                    testTag = "profile_row_block"
                                )
                            } else {
                                ProfileActionRow(
                                    icon = Icons.Default.Block,
                                    title = "Block ${chat.title}",
                                    subtitle = "Prevent messages and calls from this person",
                                    destructive = true,
                                    onClick = { pendingAction = ChatAction.BLOCK },
                                    testTag = "profile_row_block"
                                )
                            }
                        }
                    }
                }

                // --- SECTION 6: CONVERSATION MANAGEMENT (DESTRUCTIVE) ---
                item(key = "profile_management_card") {
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
                            text = "CONVERSATION MANAGEMENT",
                            fontFamily = ManropeFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.textTertiary,
                            letterSpacing = 1.5.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Clear History Row
                        ProfileActionRow(
                            icon = Icons.Default.DeleteSweep,
                            title = "Clear history",
                            subtitle = "Delete all messages from this chat",
                            destructive = true,
                            onClick = { pendingAction = ChatAction.CLEAR_HISTORY },
                            testTag = "profile_row_clear_history"
                        )

                        HorizontalDivider(
                            color = colors.divider,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        // Delete Conversation Row
                        ProfileActionRow(
                            icon = Icons.Default.Delete,
                            title = "Delete conversation",
                            subtitle = "Remove this chat and its history",
                            destructive = true,
                            onClick = {
                                pendingAction = if (chat.canRevokeHistory) ChatAction.DELETE_FOR_EVERYONE else ChatAction.DELETE_FOR_ME
                            },
                            testTag = "profile_row_delete"
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }

        // Clean Floating Header with Back affordance and NO 3-dot overflow menu
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
            }
        )

        // Coherent Confirmation Dialog for Destructive Actions
        pendingAction?.let { action ->
            when (action) {
                ChatAction.BLOCK -> {
                    ProfileConfirmationDialog(
                        title = "Block ${chat.title}?",
                        message = "Blocked contacts will no longer be able to send you messages or call you on Telegram.",
                        confirmLabel = "Block",
                        onConfirm = {
                            onChatAction(chat, ChatAction.BLOCK)
                            pendingAction = null
                        },
                        onDismiss = { pendingAction = null }
                    )
                }
                ChatAction.CLEAR_HISTORY -> {
                    ProfileConfirmationDialog(
                        title = "Clear chat history?",
                        message = "This will delete all messages in this conversation for you. This action cannot be undone.",
                        confirmLabel = "Clear History",
                        onConfirm = {
                            onChatAction(chat, ChatAction.CLEAR_HISTORY)
                            pendingAction = null
                        },
                        onDismiss = { pendingAction = null }
                    )
                }
                ChatAction.DELETE_FOR_ME, ChatAction.DELETE_FOR_EVERYONE -> {
                    ProfileConfirmationDialog(
                        title = "Delete conversation?",
                        message = "Are you sure you want to delete this conversation with ${chat.title}?",
                        confirmLabel = "Delete for me",
                        showDeleteForAllOption = chat.canRevokeHistory,
                        onDeleteForAll = if (chat.canRevokeHistory) {
                            {
                                onChatAction(chat, ChatAction.DELETE_FOR_EVERYONE)
                                pendingAction = null
                                onBack()
                            }
                        } else null,
                        onConfirm = {
                            onChatAction(chat, ChatAction.DELETE_FOR_ME)
                            pendingAction = null
                            onBack()
                        },
                        onDismiss = { pendingAction = null }
                    )
                }
                else -> {
                    pendingAction = null
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
            },
            onRequestDownload = onRequestMediaDownload
        )
    }
}

@Composable
private fun ProfileActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    destructive: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    testTag: String = ""
) {
    val colors = LocalAetherColors.current
    val contentColor = if (destructive) Color(0xFFEF4444) else colors.textPrimary
    val iconTint = if (destructive) Color(0xFFEF4444) else colors.textSecondary
    val iconBg = if (destructive) Color(0xFFEF4444).copy(alpha = 0.12f) else colors.input

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(AetherEmber.Shapes.M)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(19.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = ManropeFontFamily,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontFamily = ManropeFontFamily,
                    fontSize = 12.sp,
                    color = if (destructive) Color(0xFFEF4444).copy(alpha = 0.8f) else colors.textSecondary
                )
            }
        }

        if (trailing != null) {
            trailing()
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ProfileConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    showDeleteForAllOption: Boolean = false,
    onDeleteForAll: (() -> Unit)? = null
) {
    val colors = LocalAetherColors.current
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF1B1B22))
                .border(
                    0.5.dp,
                    Color(0x28FFFFFF),
                    RoundedCornerShape(24.dp)
                )
                .padding(20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = title,
                    fontFamily = ManropeFontFamily,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = message,
                    fontFamily = ManropeFontFamily,
                    fontSize = 13.5.sp,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                if (showDeleteForAllOption && onDeleteForAll != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFEF4444).copy(alpha = 0.15f))
                            .clickable { onDeleteForAll() }
                            .testTag("dialog_delete_for_everyone"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Delete for everyone",
                            fontFamily = ManropeFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFEF4444)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (showDeleteForAllOption) Color(0x18FFFFFF)
                            else Color(0xFFEF4444).copy(alpha = 0.15f)
                        )
                        .clickable { onConfirm() }
                        .testTag("dialog_confirm_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = confirmLabel,
                        fontFamily = ManropeFontFamily,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (showDeleteForAllOption) colors.textPrimary else Color(0xFFEF4444)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onDismiss() }
                        .testTag("dialog_cancel_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Cancel",
                        fontFamily = ManropeFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    testTag: String = ""
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(4.dp)
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (enabled) Color(0x22FFFFFF) else Color(0x0CFFFFFF))
                .border(BorderStroke(0.5.dp, if (enabled) Color(0x28FFFFFF) else Color(0x10FFFFFF)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) Color.White else Color(0x55FFFFFF),
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = label,
            fontFamily = ManropeFontFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) Color.White else Color(0x77FFFFFF)
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
private fun SharedLinkItem(
    url: String,
    title: String,
    onClick: () -> Unit = {}
) {
    val colors = LocalAetherColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AetherEmber.Shapes.S)
            .background(colors.input)
            .border(1.dp, colors.border, AetherEmber.Shapes.S)
            .clickable(onClick = onClick)
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

@Composable
private fun SharedMediaThumbnail(
    mediaItem: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAetherColors.current
    val previewBitmap = remember(mediaItem.previewBase64) {
        mediaItem.previewBase64?.let { encoded ->
            runCatching {
                val bytes = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
        }
    }

    val parsedSource: Any? = remember(mediaItem.url, mediaItem.hasLocalFile) {
        val url = mediaItem.url.trim()
        when {
            url.isBlank() -> null
            url.startsWith("content://") -> Uri.parse(url)
            url.startsWith("file://") -> {
                val file = java.io.File(url.removePrefix("file://"))
                if (file.exists() && file.length() > 0L) file else null
            }
            url.startsWith("/") -> {
                val file = java.io.File(url)
                if (file.exists() && file.length() > 0L) file else null
            }
            else -> {
                val file = java.io.File(url)
                if (file.exists() && file.length() > 0L) file else url
            }
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(AetherEmber.Shapes.M)
            .background(colors.input)
            .border(0.5.dp, colors.border, AetherEmber.Shapes.M)
            .clickable(onClick = onClick)
    ) {
        if (parsedSource != null) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(parsedSource)
                    .crossfade(true)
                    .build(),
                contentDescription = "Shared media",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    if (previewBitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = previewBitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize().background(colors.input))
                    }
                },
                error = {
                    if (previewBitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = previewBitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize().background(colors.input))
                    }
                }
            )
        } else if (previewBitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = previewBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(colors.input),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PermMedia,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun SharedFileRow(
    fileName: String,
    fileSize: String,
    timestamp: String,
    onClick: () -> Unit = {}
) {
    val colors = LocalAetherColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AetherEmber.Shapes.S)
            .background(colors.input)
            .border(1.dp, colors.border, AetherEmber.Shapes.S)
            .clickable(onClick = onClick)
            .padding(AetherEmber.Spacing.Space12),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(colors.surfaceHighlight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.InsertDriveFile,
                contentDescription = "File",
                tint = AetherAccent.current,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = fileName,
                fontFamily = ManropeFontFamily,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = fileSize,
                    fontFamily = ManropeFontFamily,
                    fontSize = 11.5.sp,
                    color = colors.textSecondary
                )
                if (timestamp.isNotBlank()) {
                    Text(
                        text = "•",
                        fontFamily = ManropeFontFamily,
                        fontSize = 11.5.sp,
                        color = colors.textTertiary
                    )
                    Text(
                        text = timestamp,
                        fontFamily = ManropeFontFamily,
                        fontSize = 11.5.sp,
                        color = colors.textTertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedVoiceRow(
    durationSec: Int,
    timestamp: String,
    onClick: () -> Unit = {}
) {
    val colors = LocalAetherColors.current
    val durationText = if (durationSec > 0) {
        val mins = durationSec / 60
        val secs = durationSec % 60
        String.format(java.util.Locale.US, "%d:%02d", mins, secs)
    } else "Voice Note"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AetherEmber.Shapes.S)
            .background(colors.input)
            .border(1.dp, colors.border, AetherEmber.Shapes.S)
            .clickable(onClick = onClick)
            .padding(AetherEmber.Spacing.Space12),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(colors.surfaceHighlight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Voice",
                tint = AetherAccent.current,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = durationText,
                fontFamily = ManropeFontFamily,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            if (timestamp.isNotBlank()) {
                Text(
                    text = timestamp,
                    fontFamily = ManropeFontFamily,
                    fontSize = 11.5.sp,
                    color = colors.textTertiary
                )
            }
        }
    }
}

private data class SharedLinkData(
    val url: String,
    val title: String,
    val dateText: String = ""
)

private fun extractLinkFromMessage(message: Message): SharedLinkData? {
    val urlRegex = Regex("(https?://[\\w\\d:#@%/;$()~_?\\+-=\\\\\\.&]+)")
    val match = urlRegex.find(message.text)
    if (match != null) {
        val url = match.value
        val title = message.text.lines().firstOrNull { it != url && it.isNotBlank() } ?: url
        return SharedLinkData(url = url, title = title, dateText = message.timestamp)
    }
    return null
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return String.format(java.util.Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
