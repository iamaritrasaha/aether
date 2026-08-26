package com.foresightlabs.aether.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import com.foresightlabs.aether.ui.design.AetherBackButton
import com.foresightlabs.aether.ui.design.AetherFloatingHeader
import com.foresightlabs.aether.ui.design.AetherFloatingHeaderDefaults
import com.foresightlabs.aether.ui.design.AetherGlass
import com.foresightlabs.aether.ui.design.AetherIconButton
import com.foresightlabs.aether.ui.design.aetherFloatingHeaderContentTopPadding
import com.foresightlabs.aether.ui.design.aetherFrostSource
import com.foresightlabs.aether.ui.design.rememberAetherFrostState
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.LocalAtmosphere
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import com.foresightlabs.aether.ui.theme.OnlineGreen
import com.foresightlabs.aether.ui.theme.SpaceGroteskFontFamily
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.File
import java.io.FileOutputStream

@Composable
fun ConversationScreen(
    chat: Chat?,
    messages: List<Message>,
    canSend: Boolean,
    onBack: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToChatAppearance: () -> Unit = {},
    onSendMessage: (String, Message?) -> Unit,
    onSendPhoto: (String, String, Message?) -> Unit = { _, _, _ -> },
    onSendDocument: (String, String, Message?) -> Unit = { _, _, _ -> },
    onEditMessage: (Message, String) -> Unit = { _, _ -> },
    onAddReaction: (Message, String) -> Unit = { _, _ -> },
    onPinMessage: (Message) -> Unit = {},
    onComposerChanged: (String) -> Unit,
    onLoadOlder: () -> Unit,
    onDeleteMessage: (Message) -> Unit,
    onRetryMessage: (Message) -> Unit,
    onVisibleMessages: (List<String>) -> Unit,
    isResolving: Boolean = false,
    resolveError: String? = null,
    onRetryResolve: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val atmosphere = LocalAtmosphere.current
    val frostState = rememberAetherFrostState()
    val colors = LocalAetherColors.current

    var replyingToMessage by remember { mutableStateOf<Message?>(null) }
    var editingMessage by remember { mutableStateOf<Message?>(null) }
    var selectedContextMenuMessage by remember { mutableStateOf<Message?>(null) }
    var isContextMenuVisible by remember { mutableStateOf(false) }

    var selectedMediaForViewer by remember { mutableStateOf<MediaItem?>(null) }
    var isMediaViewerVisible by remember { mutableStateOf(false) }

    var isAttachmentSheetVisible by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val file = copyUriToTempFile(context, uri, "photo_")
            if (file != null) {
                onSendPhoto(file.absolutePath, "", replyingToMessage)
                replyingToMessage = null
            }
        }
    }

    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val file = copyUriToTempFile(context, uri, "doc_")
            if (file != null) {
                onSendDocument(file.absolutePath, "", replyingToMessage)
                replyingToMessage = null
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                if (index <= 1 && messages.size >= 15) onLoadOlder()
                val visible = listState.layoutInfo.visibleItemsInfo.mapNotNull { info ->
                    messages.getOrNull(info.index - 1)?.id
                }
                if (visible.isNotEmpty()) onVisibleMessages(visible)
            }
    }

    // --- NON-BLOCKING RESOLVING / ERROR FALLBACK ---
    if (chat == null) {
        AetherAtmosphericBackground(
            modifier = modifier.fillMaxSize(),
            heroFraction = 1f,
            frostState = frostState
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .aetherFrostSource(frostState)
                        .padding(top = aetherFloatingHeaderContentTopPadding()),
                    contentAlignment = Alignment.Center
                ) {
                    if (resolveError != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.padding(horizontal = 32.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x30000000))
                                    .border(1.dp, Color(0x28FFFFFF), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Error",
                                    tint = AetherEmber.Colors.CoralRed,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Text(
                                text = "Couldn't open this conversation",
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = resolveError,
                                fontFamily = ManropeFontFamily,
                                fontSize = 13.5.sp,
                                color = Color(0xD0FFFFFF),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .height(44.dp)
                                    .clip(AetherEmber.Shapes.Pill)
                                    .background(atmosphere.accent)
                                    .clickable { onRetryResolve() }
                                    .padding(horizontal = 28.dp)
                                    .testTag("retry_open_chat_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Retry",
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(34.dp),
                                color = atmosphere.accent,
                                strokeWidth = 2.5.dp
                            )
                            Text(
                                text = "Opening conversation…",
                                fontFamily = ManropeFontFamily,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xEEFFFFFF)
                            )
                        }
                    }
                }

                AetherFloatingHeader(
                    title = if (resolveError == null) "Opening conversation" else "Conversation unavailable",
                    modifier = Modifier.align(Alignment.TopCenter),
                    frostState = frostState,
                    navigation = {
                        AetherBackButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("conversation_back_button")
                        )
                    }
                )
            }
        }
        return
    }

    val pinnedMessage = remember(messages) { messages.lastOrNull { it.isPinned } }

    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    AetherAtmosphericBackground(
        modifier = modifier.fillMaxSize(),
        heroFraction = 1f,
        frostState = frostState
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // --- LAYER 2: SCROLLABLE MESSAGE STREAM ---
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .aetherFrostSource(frostState)
                    .padding(
                        top = aetherFloatingHeaderContentTopPadding(
                            extraGap = if (pinnedMessage == null) {
                                AetherFloatingHeaderDefaults.ContentGap
                            } else {
                                60.dp
                            }
                        )
                    ),
                contentPadding = PaddingValues(
                    bottom = bottomInset + 88.dp
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Atmospheric Conversation Intro Card (if message count is small)
                if (messages.size <= 2) {
                    item(key = "conversation_intro_card") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AetherAvatar(
                                initials = chat.avatarInitials,
                                gradient = chat.avatarGradient,
                                size = 68.dp,
                                isOnline = chat.directUser?.isOnline ?: false,
                                chatType = chat.type,
                                photoPath = chat.photoPath,
                                showGlowingRim = true
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = chat.title,
                                fontFamily = SpaceGroteskFontFamily,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = chat.directUser?.username?.ifBlank { "" }?.let { "@$it" }
                                    ?: (if (chat.memberCount > 0) "${chat.memberCount} members" else "Direct Chat"),
                                fontFamily = ManropeFontFamily,
                                fontSize = 13.sp,
                                color = Color(0xCCFFFFFF)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            val isSecret = chat.type == com.foresightlabs.aether.domain.model.ChatType.SECRET
                            Row(
                                modifier = Modifier
                                    .clip(AetherEmber.Shapes.Pill)
                                    .background(Color(0x35000000))
                                    .border(1.dp, Color(0x22FFFFFF), AetherEmber.Shapes.Pill)
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Security",
                                    tint = if (isSecret) OnlineGreen else Color(0xDDFFFFFF),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (isSecret) "End-to-End Encrypted" else "Telegram Cloud Chat",
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xEEFFFFFF)
                                )
                            }
                        }
                    }
                }

                // Date Divider Header
                item(key = "date_divider") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(AetherEmber.Shapes.Pill)
                                .background(Color(0x35000000))
                                .border(0.5.dp, Color(0x28FFFFFF), AetherEmber.Shapes.Pill)
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Today",
                                fontFamily = ManropeFontFamily,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xF5FFFFFF)
                            )
                        }
                    }
                }

                // Message Bubbles
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
                        onReactionClick = { targetMsg, emoji ->
                            onAddReaction(targetMsg, emoji)
                        }
                    )
                }
            }

            // --- LAYER 3 (TOP): SHARED FLOATING CONVERSATION HEADER ---
            AetherFloatingHeader(
                modifier = Modifier.align(Alignment.TopCenter),
                frostState = frostState
            ) {
                AetherBackButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("conversation_back_button")
                )

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(AetherEmber.Shapes.S)
                        .clickable { onNavigateToProfile() }
                        .padding(horizontal = 4.dp)
                        .testTag("conversation_header_profile"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AetherAvatar(
                        initials = chat.avatarInitials,
                        gradient = chat.avatarGradient,
                        size = 40.dp,
                        isOnline = chat.directUser?.isOnline ?: false,
                        chatType = chat.type,
                        photoPath = chat.photoPath,
                        hasUnseenPulse = chat.hasUnseenPulse
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = chat.title,
                            fontFamily = ManropeFontFamily,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (chat.directUser?.isOnline == true) {
                                Box(
                                    modifier = Modifier.size(6.dp).clip(CircleShape).background(OnlineGreen)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "online",
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 12.sp,
                                    color = OnlineGreen,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                Text(
                                    text = chat.directUser?.lastSeenText
                                        ?: if (chat.memberCount > 0) "${chat.memberCount} members" else "offline",
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 12.sp,
                                    color = colors.textTertiary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                AetherIconButton(
                    icon = Icons.Default.Palette,
                    contentDescription = "Chat appearance",
                    onClick = onNavigateToChatAppearance,
                    modifier = Modifier.testTag("conversation_appearance_button")
                )
                AetherIconButton(
                    icon = Icons.Default.Call,
                    contentDescription = "Audio Call",
                    onClick = { /* Audio Call */ }
                )
                AetherIconButton(
                    icon = Icons.Default.Info,
                    contentDescription = "Info",
                    onClick = onNavigateToProfile,
                    modifier = Modifier.testTag("conversation_more_button")
                )
            }

            if (pinnedMessage != null) {
                AetherGlass(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(
                            start = AetherFloatingHeaderDefaults.HorizontalMargin,
                            end = AetherFloatingHeaderDefaults.HorizontalMargin,
                            top = AetherFloatingHeaderDefaults.TopGap +
                                AetherFloatingHeaderDefaults.ExpandedHeight +
                                AetherFloatingHeaderDefaults.ContentGap
                        )
                        .fillMaxWidth(),
                    shape = AetherEmber.Shapes.M
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { /* scroll to pinned message */ }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            tint = colors.accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Pinned Message",
                                fontFamily = ManropeFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.accent
                            )
                            Text(
                                text = pinnedMessage.text.ifBlank { "Media" },
                                fontFamily = ManropeFontFamily,
                                fontSize = 12.sp,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // --- LAYER 3 (BOTTOM): TRULY FLOATING COMPOSER DOCK ---
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
                onVoiceNoteRecorded = { },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // Attachment Sheet Overlay
        AttachmentSheet(
            isVisible = isAttachmentSheetVisible,
            onDismiss = { isAttachmentSheetVisible = false },
            onOptionSelected = { option ->
                when (option) {
                    "Gallery", "Camera" -> photoPickerLauncher.launch("image/*")
                    "File", "Audio" -> docPickerLauncher.launch(arrayOf("*/*"))
                    else -> {}
                }
            }
        )

        // Context Menu Overlay
        MessageContextMenu(
            message = selectedContextMenuMessage,
            isVisible = isContextMenuVisible,
            onDismiss = {
                isContextMenuVisible = false
                selectedContextMenuMessage = null
            },
            onReactionSelected = { emoji ->
                selectedContextMenuMessage?.let { targetMsg ->
                    onAddReaction(targetMsg, emoji)
                }
            },
            onReply = {
                selectedContextMenuMessage?.let { targetMsg ->
                    replyingToMessage = targetMsg
                }
            },
            onCopy = {
                selectedContextMenuMessage?.let { targetMsg ->
                    clipboardManager.setText(AnnotatedString(targetMsg.text))
                }
            },
            onForward = { },
            onEdit = {
                selectedContextMenuMessage?.let { targetMsg ->
                    editingMessage = targetMsg
                }
            },
            onPin = {
                selectedContextMenuMessage?.let { targetMsg ->
                    onPinMessage(targetMsg)
                }
            },
            onDelete = {
                selectedContextMenuMessage?.let(onDeleteMessage)
            }
        )

        // Edit Message Dialog
        if (editingMessage != null) {
            EditMessageModal(
                initialText = editingMessage!!.text,
                onDismiss = { editingMessage = null },
                onSave = { newText ->
                    editingMessage?.let { onEditMessage(it, newText) }
                    editingMessage = null
                }
            )
        }

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

@Composable
private fun EditMessageModal(
    initialText: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialText) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x99000000))
                .clickable { onDismiss() }
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AetherEmber.Shapes.XL)
                    .background(AetherEmber.Colors.SurfaceElevated)
                    .border(1.dp, AetherEmber.Colors.Border, AetherEmber.Shapes.XL)
                    .clickable(enabled = false) { }
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Edit Message",
                        fontFamily = ManropeFontFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AetherEmber.Colors.Accent,
                        unfocusedBorderColor = AetherEmber.Colors.Border
                    ),
                    shape = AetherEmber.Shapes.M
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Box(
                        modifier = Modifier
                            .height(40.dp)
                            .clip(AetherEmber.Shapes.Pill)
                            .background(AetherEmber.Colors.Accent)
                            .clickable {
                                if (text.isNotBlank()) onSave(text)
                            }
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Save",
                            fontFamily = ManropeFontFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

private fun copyUriToTempFile(context: android.content.Context, uri: Uri, prefix: String): File? {
    return try {
        val extension = context.contentResolver.getType(uri)?.substringAfterLast('/') ?: "tmp"
        val tempFile = File.createTempFile(prefix, ".$extension", context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        tempFile
    } catch (e: Exception) {
        null
    }
}
