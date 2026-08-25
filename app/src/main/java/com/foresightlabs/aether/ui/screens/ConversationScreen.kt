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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.MediaItem
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.ui.components.AetherAtmosphericBackground
import com.foresightlabs.aether.ui.components.AetherAvatar
import com.foresightlabs.aether.ui.components.AttachmentSheet
import com.foresightlabs.aether.ui.components.MediaViewer
import com.foresightlabs.aether.ui.components.MessageBubble
import com.foresightlabs.aether.ui.components.MessageComposer
import com.foresightlabs.aether.ui.components.MessageContextMenu
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import com.foresightlabs.aether.ui.theme.OnlineGreen

@Composable
fun ConversationScreen(
    chat: Chat?,
    messages: List<Message>,
    canSend: Boolean,
    onBack: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onSendMessage: (String, Message?) -> Unit,
    onComposerChanged: (String) -> Unit,
    onLoadOlder: () -> Unit,
    onDeleteMessage: (Message) -> Unit,
    onRetryMessage: (Message) -> Unit,
    onVisibleMessages: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    var replyingToMessage by remember { mutableStateOf<Message?>(null) }
    var selectedContextMenuMessage by remember { mutableStateOf<Message?>(null) }
    var isContextMenuVisible by remember { mutableStateOf(false) }

    var selectedMediaForViewer by remember { mutableStateOf<MediaItem?>(null) }
    var isMediaViewerVisible by remember { mutableStateOf(false) }

    var isAttachmentSheetVisible by remember { mutableStateOf(false) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { index ->
                if (index <= 2) onLoadOlder()
                val visible = listState.layoutInfo.visibleItemsInfo.mapNotNull { info ->
                    messages.getOrNull(info.index - 1)?.id
                }
                if (visible.isNotEmpty()) onVisibleMessages(visible)
            }
    }

    if (chat == null) {
        Box(
            modifier.fillMaxSize().background(AetherEmber.Colors.Background),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Opening conversation…",
                fontFamily = ManropeFontFamily,
                color = AetherEmber.Colors.TextTertiary
            )
        }
        return
    }

    AetherAtmosphericBackground(
        modifier = modifier.fillMaxSize(),
        heroOnly = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // --- CONVERSATION HEADER (Translucent glass matching reference) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button (Circular translucent glass)
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0x28000000))
                        .border(1.dp, Color(0x20FFFFFF), CircleShape)
                        .clickable { onBack() }
                        .testTag("conversation_back_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Avatar & Contact Info
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(AetherEmber.Shapes.M)
                        .clickable { onNavigateToProfile() }
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                        .testTag("conversation_header_profile"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AetherAvatar(
                        initials = chat.avatarInitials,
                        gradient = chat.avatarGradient,
                        size = 42.dp,
                        isOnline = chat.directUser?.isOnline ?: false,
                        chatType = chat.type,
                        photoPath = chat.photoPath,
                        showGlowingRim = true
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        // Prominent contact title (Manrope Bold, White)
                        Text(
                            text = chat.title,
                            fontFamily = ManropeFontFamily,
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (chat.directUser?.isOnline == true) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(OnlineGreen)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "online",
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF90F0C0),
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                Text(
                                    text = chat.directUser?.lastSeenText ?: (if (chat.memberCount > 0) "${chat.memberCount} members" else "offline"),
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 11.5.sp,
                                    color = Color(0xD0FFFFFF)
                                )
                            }
                        }
                    }
                }

                // Header Action Buttons (Call, Info)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0x28000000))
                            .border(1.dp, Color(0x20FFFFFF), CircleShape)
                            .clickable { /* simulated call */ },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Audio Call",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0x28000000))
                            .border(1.dp, Color(0x20FFFFFF), CircleShape)
                            .clickable { onNavigateToProfile() }
                            .testTag("conversation_more_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            // --- MESSAGE STREAM AREA ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Date Divider Header (Translucent pill)
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(AetherEmber.Shapes.Pill)
                                    .background(Color(0x2E000000))
                                    .border(0.5.dp, Color(0x25FFFFFF), AetherEmber.Shapes.Pill)
                                    .padding(horizontal = 14.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Today",
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xEEFFFFFF)
                                )
                            }
                        }
                    }

                    // Messages (Frosted incoming & crimson outgoing)
                    items(messages, key = { it.id }) { msg ->
                        MessageBubble(
                            message = msg,
                            onSwipeToReply = { replyTarget ->
                                replyingToMessage = replyTarget
                            },
                            onLongPress = { targetMsg ->
                                selectedContextMenuMessage = targetMsg
                                isContextMenuVisible = true
                            },
                            onMediaClick = { media ->
                                selectedMediaForViewer = media
                                isMediaViewerVisible = true
                            },
                            onReactionClick = { _, _ -> }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            // --- FLOATING COMPOSER DOCK ---
            MessageComposer(
                replyingTo = replyingToMessage,
                onDismissReply = { replyingToMessage = null },
                onSendMessage = { text ->
                    onSendMessage(text, replyingToMessage)
                    replyingToMessage = null
                },
                onTextChanged = onComposerChanged,
                enabled = canSend,
                onOpenAttachmentSheet = {
                    isAttachmentSheetVisible = true
                },
                onVoiceNoteRecorded = { }
            )
        }

        // Attachment Sheet Overlay
        AttachmentSheet(
            isVisible = isAttachmentSheetVisible,
            onDismiss = { isAttachmentSheetVisible = false },
            onOptionSelected = { isAttachmentSheetVisible = false }
        )

        // Context Menu Overlay
        MessageContextMenu(
            message = selectedContextMenuMessage,
            isVisible = isContextMenuVisible,
            onDismiss = {
                isContextMenuVisible = false
                selectedContextMenuMessage = null
            },
            onReactionSelected = { },
            onReply = {
                selectedContextMenuMessage?.let { targetMsg ->
                    replyingToMessage = targetMsg
                }
            },
            onCopy = { },
            onForward = { },
            onEdit = { },
            onPin = { },
            onDelete = {
                selectedContextMenuMessage?.let(onDeleteMessage)
            }
        )

        // Full Screen Media Viewer Overlay
        MediaViewer(
            mediaItem = selectedMediaForViewer,
            senderName = chat.title,
            isVisible = isMediaViewerVisible,
            onClose = {
                isMediaViewerVisible = false
                selectedMediaForViewer = null
            }
        )
    }
}
