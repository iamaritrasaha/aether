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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import com.foresightlabs.aether.ui.components.ChatRow
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import com.foresightlabs.aether.domain.messages.ConversationEntry
import com.foresightlabs.aether.domain.messages.MessageGrouping
import com.foresightlabs.aether.ui.components.AlbumBubble
import com.foresightlabs.aether.ui.components.ContactShareSheet
import com.foresightlabs.aether.ui.components.MessageInfoSheet
import com.foresightlabs.aether.domain.model.StickerItem
import com.foresightlabs.aether.domain.model.StickerSetInfo
import com.foresightlabs.aether.ui.components.LiveLocationShareSheet
import com.foresightlabs.aether.ui.components.VenueShareSheet
import com.foresightlabs.aether.ui.components.StickerPickerSheet
import com.foresightlabs.aether.ui.components.ScheduledMessagesSheet
import com.foresightlabs.aether.ui.components.VideoNoteRecorderSheet
import androidx.compose.material.icons.filled.Schedule
import com.foresightlabs.aether.ui.components.LocationShareSheet
import android.content.Intent
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import com.foresightlabs.aether.domain.search.ConversationSearchState
import com.foresightlabs.aether.ui.components.AetherSearchPill
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import com.foresightlabs.aether.domain.messages.MessageActionPolicy
import kotlinx.coroutines.delay
import com.foresightlabs.aether.domain.text.AetherEntity
import com.foresightlabs.aether.domain.text.ReplyQuote
import com.foresightlabs.aether.domain.text.EntityAction
import com.foresightlabs.aether.domain.messages.MessageAction
import com.foresightlabs.aether.domain.messages.MessageCapabilities
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.MessageType
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
import com.foresightlabs.aether.ui.design.rememberAetherFrostState
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.LocalAtmosphere
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import com.foresightlabs.aether.ui.theme.OnlineGreen
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.runtime.rememberCoroutineScope
import com.foresightlabs.aether.data.media.AudioRecorderManager
import kotlinx.coroutines.launch
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
    onSendMessage: (String, Message?, List<AetherEntity>, ReplyQuote?) -> Unit,
    onSendPhoto: (String, String, Message?) -> Unit = { _, _, _ -> },
    onSendDocument: (String, String, Message?) -> Unit = { _, _, _ -> },
    onSendVoiceNote: (String, Int, ByteArray, Message?) -> Unit = { _, _, _, _ -> },
    onEditMessage: (Message, String) -> Unit = { _, _ -> },
    onAddReaction: (Message, String) -> Unit = { _, _ -> },
    onPinMessage: (Message) -> Unit = {},
    onComposerChanged: (String) -> Unit,
    onLoadOlder: () -> Unit,
    onDeleteMessage: (Message, Boolean) -> Unit,
    onForwardMessage: (Message, Long, Boolean, Boolean) -> Unit = { _, _, _, _ -> },
    forwardTargets: List<Chat> = emptyList(),
    messageCapabilities: Map<String, MessageCapabilities> = emptyMap(),
    onRequestCapabilities: (Message) -> Unit = {},
    onRetryMessage: (Message) -> Unit,
    onVisibleMessages: (List<String>) -> Unit,
    isResolving: Boolean = false,
    resolveError: String? = null,
    onRetryResolve: () -> Unit = {},
    onStartVoiceCall: () -> Unit = {},
    onOpenUsername: (String) -> Unit = {},
    searchState: ConversationSearchState = ConversationSearchState.Idle,
    onOpenSearch: () -> Unit = {},
    onCloseSearch: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onSearchOlder: () -> Unit = {},
    onSearchNewer: () -> Unit = {},
    onSendContact: (String, String, String, Message?) -> Unit = { _, _, _, _ -> },
    onSendLocation: (Double, Double, Message?) -> Unit = { _, _, _ -> },
    onSendLiveLocation: (Double, Double, Int, Message?) -> Unit = { _, _, _, _ -> },
    onStopLiveLocation: (Message) -> Unit = {},
    onSendVenue: (Double, Double, String, String, Message?) -> Unit = { _, _, _, _, _ -> },
    onSendVideoNote: (String, Int, Int, Message?) -> Unit = { _, _, _, _ -> },
    onSendSticker: (Int, String) -> Unit = { _, _ -> },
    onReplaceMedia: (Message, String, MessageType) -> Unit = { _, _, _ -> },
    installedStickerSets: List<StickerSetInfo> = emptyList(),
    recentStickers: List<StickerItem> = emptyList(),
    favoriteStickers: List<StickerItem> = emptyList(),
    onLoadStickers: () -> Unit = {},
    onLoadStickerSetDetails: (Long, (StickerSetInfo) -> Unit) -> Unit = { _, _ -> },
    onLoadScheduled: suspend () -> List<Message> = { emptyList() },
    onSendScheduledNow: (Message) -> Unit = {},
    onRescheduleMessage: (Message, Int) -> Unit = { _, _ -> },
    onPollVote: (Message, List<Int>) -> Unit = { _, _ -> },
    /** Pinned messages Telegram reports for this chat, beyond those loaded. */
    pinnedFromServer: List<Message> = emptyList(),
    onJumpToMessage: (String) -> Unit = {},
    onUnpinMessage: (Message) -> Unit = {},
    canUnpin: Boolean = false,
    jumpTarget: String? = null,
    onJumpConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val atmosphere = LocalAtmosphere.current
    val frostState = rememberAetherFrostState()
    val colors = LocalAetherColors.current

    var replyingToMessage by remember { mutableStateOf<Message?>(null) }
    var editingMessage by remember { mutableStateOf<Message?>(null) }
    var selectedContextMenuMessage by remember { mutableStateOf<Message?>(null) }
    var forwardingMessages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var replyQuote by remember { mutableStateOf<ReplyQuote?>(null) }
    var infoMessage by remember { mutableStateOf<Message?>(null) }
    var highlightedMessageId by remember { mutableStateOf<String?>(null) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    // Grouping is derived once per message-list change, not per frame.
    val entries = remember(messages) { MessageGrouping.group(messages) }
    var showContactSheet by remember { mutableStateOf(false) }
    var showLocationSheet by remember { mutableStateOf(false) }
    var showLiveLocationSheet by remember { mutableStateOf(false) }
    var showVenueSheet by remember { mutableStateOf(false) }
    var showStickerPicker by remember { mutableStateOf(false) }
    var showScheduledSheet by remember { mutableStateOf(false) }
    var replacingMediaMessage by remember { mutableStateOf<Message?>(null) }
    var resolvedLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var locationError by remember { mutableStateOf<String?>(null) }
    var isResolvingLocation by remember { mutableStateOf(false) }
    val isSelecting = selectedIds.isNotEmpty()

    // Brings a jump target into view and marks it, then hands the request back so a
    // repeat tap on the same result scrolls again.
    LaunchedEffect(jumpTarget, entries) {
        val target = jumpTarget ?: return@LaunchedEffect
        // Index within the list as it is actually laid out: album members collapse
        // into one row, and the intro card and date divider precede them.
        val entryIndex = entries.indexOfFirst { entry ->
            when (entry) {
                is ConversationEntry.Single -> entry.message.id == target
                is ConversationEntry.Album -> entry.messages.any { it.id == target }
            }
        }
        if (entryIndex < 0) return@LaunchedEffect
        val leadingItems = (if (messages.size <= 2) 1 else 0) + 1
        val index = entryIndex + leadingItems
        highlightedMessageId = target
        runCatching { listState.animateScrollToItem(index) }
        onJumpConsumed()
        delay(1_600)
        if (highlightedMessageId == target) highlightedMessageId = null
    }
    var isContextMenuVisible by remember { mutableStateOf(false) }

    var selectedMediaForViewer by remember { mutableStateOf<MediaItem?>(null) }
    var isMediaViewerVisible by remember { mutableStateOf(false) }

    var isAttachmentSheetVisible by remember { mutableStateOf(false) }
    var showVideoNoteRecorder by remember { mutableStateOf(false) }

    var cameraTempFile by remember { mutableStateOf<File?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraTempFile != null && cameraTempFile!!.length() > 0) {
            onSendPhoto(cameraTempFile!!.absolutePath, "", replyingToMessage)
            replyingToMessage = null
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val file = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
            cameraTempFile = file
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            cameraLauncher.launch(uri)
        } else {
            Toast.makeText(context, "Camera permission is required to take photos", Toast.LENGTH_SHORT).show()
        }
    }

    val audioRecorder = remember { AudioRecorderManager(context) }
    var isRecordingAudio by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            if (audioRecorder.startRecording()) {
                isRecordingAudio = true
                Toast.makeText(context, "Recording voice message… Tap mic again to send", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Microphone permission is required for voice notes", Toast.LENGTH_SHORT).show()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Continue straight into the share sheet the grant was requested for, rather
        // than acknowledging the permission and leaving the user to tap again.
        if (isGranted) {
            showLocationSheet = true
        } else {
            Toast.makeText(
                context,
                "Location access is needed to share where you are",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

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

    val replaceMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val file = copyUriToTempFile(context, uri, "replace_")
            if (file != null && replacingMediaMessage != null) {
                val mediaType = when (replacingMediaMessage!!.type) {
                    MessageType.IMAGE -> MessageType.IMAGE
                    MessageType.ANIMATION -> MessageType.ANIMATION
                    MessageType.AUDIO -> MessageType.AUDIO
                    MessageType.FILE -> MessageType.FILE
                    else -> MessageType.IMAGE
                }
                onReplaceMedia(replacingMediaMessage!!, file.absolutePath, mediaType)
                replacingMediaMessage = null
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
        Box(modifier = modifier.fillMaxSize()) {
            AetherAtmosphericBackground(
                modifier = Modifier.fillMaxSize(),
                heroFraction = 1f,
                frostState = frostState
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
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
                                text = "Unable to open conversation",
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
        return
    }

    // Pinned messages Telegram reports, newest first, cycled through by the banner.
    val pinnedMessages = remember(messages, pinnedFromServer) {
        val loaded = messages.filter { it.isPinned }
        val known = loaded.map { it.id }.toSet()
        (loaded + pinnedFromServer.filter { it.id !in known }).sortedByDescending {
            it.id.toLongOrNull() ?: 0L
        }
    }
    var pinnedCursor by remember(pinnedMessages.size) { mutableIntStateOf(0) }
    val pinnedMessage = pinnedMessages.getOrNull(pinnedCursor)
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier = modifier.fillMaxSize()) {
        AetherAtmosphericBackground(
            modifier = Modifier.fillMaxSize(),
            heroFraction = 1f,
            frostState = frostState
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
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

                // Message Bubbles, with grouped media collapsed into one cluster.
                items(entries, key = { it.key }) { entry ->
                    if (entry is ConversationEntry.Album) {
                        AlbumEntryRow(
                            album = entry,
                            onLongPress = {
                                onRequestCapabilities(entry.anchor)
                                selectedContextMenuMessage = entry.anchor
                                isContextMenuVisible = true
                            },
                            onMediaClick = { media ->
                                selectedMediaForViewer = media
                                isMediaViewerVisible = true
                            }
                        )
                        return@items
                    }
                    val msg = entry.anchor
                    MessageBubble(
                        message = msg,
                        onSwipeToReply = { replyTarget ->
                            replyingToMessage = replyTarget
                        },
                        onLongPress = { targetMsg ->
                            if (isSelecting) {
                                selectedIds = selectedIds.toggle(targetMsg.id)
                            } else {
                                // Ask Telegram what may be done with it before
                                // offering anything; the menu opens with whatever
                                // has arrived.
                                onRequestCapabilities(targetMsg)
                                selectedContextMenuMessage = targetMsg
                                isContextMenuVisible = true
                            }
                        },
                        isSelected = msg.id in selectedIds,
                        onSelectToggle = if (isSelecting) {
                            {
                                onRequestCapabilities(msg)
                                selectedIds = selectedIds.toggle(msg.id)
                            }
                        } else {
                            null
                        },
                        onMediaClick = { media ->
                            selectedMediaForViewer = media
                            isMediaViewerVisible = true
                        },
                        onReactionClick = { targetMsg, emoji ->
                            onAddReaction(targetMsg, emoji)
                        },
                        onEntityAction = { action -> handleEntityAction(context, action, onOpenUsername) },
                        isHighlighted = msg.id == highlightedMessageId,
                        onPollVote = onPollVote,
                        onStopLiveLocation = onStopLiveLocation
                    )
                }
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
                    icon = Icons.Default.Search,
                    contentDescription = "Search this conversation",
                    onClick = onOpenSearch,
                    modifier = Modifier.testTag("conversation_search_button")
                )
                AetherIconButton(
                    icon = Icons.Default.Palette,
                    contentDescription = "Chat appearance",
                    onClick = onNavigateToChatAppearance,
                    modifier = Modifier.testTag("conversation_appearance_button")
                )
                AetherIconButton(
                    icon = Icons.Default.Call,
                    contentDescription = "Audio Call",
                    onClick = onStartVoiceCall
                )
                AetherIconButton(
                    icon = Icons.Default.Schedule,
                    contentDescription = "Scheduled Messages",
                    onClick = { showScheduledSheet = true },
                    modifier = Modifier.testTag("conversation_scheduled_button")
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
                            .clickable {
                                // Jumping loads the surrounding window when the pin
                                // is older than anything currently held.
                                onJumpToMessage(pinnedMessage.id)
                                // A second tap moves to the next pin, the way
                                // Telegram cycles a stack of them.
                                if (pinnedMessages.size > 1) {
                                    pinnedCursor = (pinnedCursor + 1) % pinnedMessages.size
                                }
                            }
                            .testTag("pinned_banner")
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
                                text = if (pinnedMessages.size > 1) {
                                    "Pinned · ${pinnedCursor + 1} of ${pinnedMessages.size}"
                                } else {
                                    "Pinned message"
                                },
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
                        if (canUnpin) {
                            AetherIconButton(
                                icon = Icons.Default.Close,
                                contentDescription = "Unpin message",
                                onClick = { onUnpinMessage(pinnedMessage) },
                                modifier = Modifier.testTag("pinned_unpin")
                            )
                        }
                    }
                }
            }

            // --- LAYER 3 (BOTTOM): TRULY FLOATING COMPOSER DOCK ---
            MessageComposer(
                replyingTo = replyingToMessage,
                onDismissReply = {
                    replyingToMessage = null
                    replyQuote = null
                },
                replyQuote = replyQuote,
                onSendMessage = { text, formatting ->
                    onSendMessage(text, replyingToMessage, formatting, replyQuote)
                    replyingToMessage = null
                    replyQuote = null
                },
                onTextChanged = onComposerChanged,
                enabled = canSend,
                onOpenAttachmentSheet = {
                    isAttachmentSheetVisible = true
                },
                onOpenStickerPicker = {
                    onLoadStickers()
                    showStickerPicker = true
                },
                onVoiceNoteRecorded = {
                    if (isRecordingAudio) {
                        val recordResult = audioRecorder.stopRecording()
                        isRecordingAudio = false
                        if (recordResult != null) {
                            onSendVoiceNote(recordResult.filePath, recordResult.durationSec, ByteArray(0), replyingToMessage)
                            replyingToMessage = null
                        }
                    } else {
                        val hasMicPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        if (hasMicPermission) {
                            if (audioRecorder.startRecording()) {
                                isRecordingAudio = true
                                Toast.makeText(context, "Recording voice message… Tap mic again to send", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                },
                onOpenVideoNote = { showVideoNoteRecorder = true },
                modifier = Modifier.align(Alignment.BottomCenter)
            )

        // Attachment Sheet Overlay
        AttachmentSheet(
            isVisible = isAttachmentSheetVisible,
            onDismiss = { isAttachmentSheetVisible = false },
            onOptionSelected = { option ->
                when (option) {
                    "Gallery" -> photoPickerLauncher.launch("image/*")
                    "Camera" -> {
                        val hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                        if (hasCameraPermission) {
                            val file = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
                            cameraTempFile = file
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            cameraLauncher.launch(uri)
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                    "Video Message" -> showVideoNoteRecorder = true
                    "File", "Audio" -> docPickerLauncher.launch(arrayOf("*/*"))
                    "Contact" -> showContactSheet = true
                    "Location" -> {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            showLocationSheet = true
                        } else {
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                    }
                    "Live Location" -> {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            showLiveLocationSheet = true
                        } else {
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                    }
                    "Venue" -> {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            showVenueSheet = true
                        } else {
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                    }
                    else -> {}
                }
            }
        )

        MessageInfoSheet(
            message = infoMessage,
            capabilities = infoMessage
                ?.let { messageCapabilities[it.id] }
                ?: MessageCapabilities.Unknown,
            onDismiss = { infoMessage = null }
        )

        if (showContactSheet) {
            ContactShareSheet(
                onDismiss = { showContactSheet = false },
                onSend = { phone, first, last ->
                    onSendContact(phone, first, last, replyingToMessage)
                    replyingToMessage = null
                    showContactSheet = false
                }
            )
        }

        if (showLocationSheet) {
            // Resolved only after the sheet is open, so nothing is read from the
            // sensor until the user has actually asked to share a location.
            LaunchedEffect(Unit) {
                isResolvingLocation = true
                locationError = null
                val fix = lastKnownCoarseLocation(context)
                resolvedLocation = fix
                locationError = if (fix == null) {
                    "No recent location fix is available on this device yet."
                } else {
                    null
                }
                isResolvingLocation = false
            }
            LocationShareSheet(
                latitude = resolvedLocation?.first,
                longitude = resolvedLocation?.second,
                isResolving = isResolvingLocation,
                error = locationError,
                onDismiss = { showLocationSheet = false },
                onSend = { lat, lon ->
                    onSendLocation(lat, lon, replyingToMessage)
                    replyingToMessage = null
                    showLocationSheet = false
                }
            )
        }

        if (showLiveLocationSheet) {
            LaunchedEffect(Unit) {
                isResolvingLocation = true
                locationError = null
                val fix = lastKnownCoarseLocation(context)
                resolvedLocation = fix
                locationError = if (fix == null) "No recent location fix is available on this device yet." else null
                isResolvingLocation = false
            }
            LiveLocationShareSheet(
                latitude = resolvedLocation?.first,
                longitude = resolvedLocation?.second,
                isResolving = isResolvingLocation,
                error = locationError,
                onDismiss = { showLiveLocationSheet = false },
                onSendLive = { lat, lon, dur ->
                    onSendLiveLocation(lat, lon, dur, replyingToMessage)
                    replyingToMessage = null
                    showLiveLocationSheet = false
                }
            )
        }

        if (showVenueSheet) {
            LaunchedEffect(Unit) {
                isResolvingLocation = true
                locationError = null
                val fix = lastKnownCoarseLocation(context)
                resolvedLocation = fix
                locationError = if (fix == null) "No recent location fix is available on this device yet." else null
                isResolvingLocation = false
            }
            VenueShareSheet(
                latitude = resolvedLocation?.first,
                longitude = resolvedLocation?.second,
                isResolving = isResolvingLocation,
                error = locationError,
                onDismiss = { showVenueSheet = false },
                onSendVenue = { lat, lon, title, address ->
                    onSendVenue(lat, lon, title, address, replyingToMessage)
                    replyingToMessage = null
                    showVenueSheet = false
                }
            )
        }

        StickerPickerSheet(
            isVisible = showStickerPicker,
            installedSets = installedStickerSets,
            recentStickers = recentStickers,
            favoriteStickers = favoriteStickers,
            onLoadSetDetails = onLoadStickerSetDetails,
            onDismiss = { showStickerPicker = false },
            onSendSticker = { fileId, emoji ->
                onSendSticker(fileId, emoji)
            }
        )

        ScheduledMessagesSheet(
            isVisible = showScheduledSheet,
            onDismiss = { showScheduledSheet = false },
            onLoadScheduled = onLoadScheduled,
            onSendNow = onSendScheduledNow,
            onReschedule = onRescheduleMessage,
            onDelete = { msg -> onDeleteMessage(msg, false) }
        )

        VideoNoteRecorderSheet(
            isVisible = showVideoNoteRecorder,
            onDismiss = { showVideoNoteRecorder = false },
            onSendVideoNote = { filePath, duration, length ->
                onSendVideoNote(filePath, duration, length, replyingToMessage)
                replyingToMessage = null
                showVideoNoteRecorder = false
            }
        )

        if (isSelecting) {
            // Back leaves selection before it leaves the conversation.
            BackHandler { selectedIds = emptySet() }

            SelectionToolbar(
                selection = messages.filter { it.id in selectedIds },
                capabilities = messageCapabilities,
                onDismiss = { selectedIds = emptySet() },
                onAction = { action ->
                    val chosen = messages.filter { it.id in selectedIds }
                    when (action) {
                        MessageAction.COPY -> clipboardManager.setText(
                            AnnotatedString(chosen.joinToString("\n\n") { it.text })
                        )
                        MessageAction.FORWARD -> forwardingMessages = chosen
                        MessageAction.DELETE_FOR_ME -> chosen.forEach { onDeleteMessage(it, false) }
                        MessageAction.DELETE_FOR_EVERYONE -> chosen.forEach { onDeleteMessage(it, true) }
                        else -> Unit
                    }
                    selectedIds = emptySet()
                },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        if (searchState.isActive) {
            ConversationSearchBar(
                state = searchState,
                onQueryChange = onSearchQueryChange,
                onOlder = onSearchOlder,
                onNewer = onSearchNewer,
                onClose = onCloseSearch,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        // Context Menu Overlay
        MessageContextMenu(
            message = selectedContextMenuMessage,
            capabilities = selectedContextMenuMessage
                ?.let { messageCapabilities[it.id] }
                ?: MessageCapabilities.Unknown,
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
            onAction = { action ->
                val target = selectedContextMenuMessage ?: return@MessageContextMenu
                when (action) {
                    MessageAction.SELECT -> selectedIds = setOf(target.id)
                    MessageAction.REPLY -> {
                        replyingToMessage = target
                        replyQuote = null
                    }
                    MessageAction.QUOTE_REPLY -> {
                        replyingToMessage = target
                        // Quoting the whole text is the honest default until Aether
                        // offers in-bubble text selection; the position is real, so
                        // the quote stays attached if the original is edited.
                        replyQuote = ReplyQuote.from(target.richText, 0, target.text.length)
                    }
                    MessageAction.COPY -> clipboardManager.setText(AnnotatedString(target.text))
                    MessageAction.FORWARD -> forwardingMessages = listOf(target)
                    MessageAction.EDIT -> editingMessage = target
                    MessageAction.REPLACE_MEDIA -> {
                        replacingMediaMessage = target
                        val mime = when (target.type) {
                            MessageType.IMAGE -> "image/*"
                            MessageType.ANIMATION -> "image/gif"
                            MessageType.AUDIO -> "audio/*"
                            else -> "*/*"
                        }
                        replaceMediaLauncher.launch(mime)
                    }
                    MessageAction.PIN, MessageAction.UNPIN -> onPinMessage(target)
                    MessageAction.DELETE_FOR_ME -> onDeleteMessage(target, false)
                    MessageAction.DELETE_FOR_EVERYONE -> onDeleteMessage(target, true)
                    // Offered only when Telegram reports the capability, and only
                    // once Aether can actually carry them out.
                    MessageAction.INFO -> infoMessage = target
                    // Offered only when Telegram reports the capability, and only
                    // once Aether can actually carry them out.
                    MessageAction.SAVE,
                    MessageAction.COPY_LINK -> Unit
                }
            }
        )

        // Forward target picker
        if (forwardingMessages.isNotEmpty()) {
            ForwardTargetSheet(
                targets = forwardTargets,
                // Only offered where Telegram permits it: an attributed forward of
                // protected content is allowed, an unattributed copy is not.
                canSendCopy = forwardingMessages.all {
                    messageCapabilities[it.id]?.canBeSaved ?: false
                },
                hasCaption = forwardingMessages.any {
                    it.text.isNotBlank() && it.type != MessageType.TEXT
                },
                onDismiss = { forwardingMessages = emptyList() },
                onPick = { targetChat, sendCopy, removeCaption ->
                    val destination = targetChat.id.toLongOrNull()
                    if (destination != null) {
                        forwardingMessages.forEach { msg ->
                            onForwardMessage(msg, destination, sendCopy, removeCaption)
                        }
                    }
                    forwardingMessages = emptyList()
                }
            )
        }

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

/**
 * Chooses where a message is forwarded to.
 *
 * Kept deliberately plain: it lists the conversations Aether already has, and the
 * chosen one is forwarded to through the real TDLib forward operation. Nothing is
 * copied or re-sent as new text.
 */
@Composable
private fun ForwardTargetSheet(
    targets: List<Chat>,
    canSendCopy: Boolean,
    hasCaption: Boolean,
    onDismiss: () -> Unit,
    onPick: (Chat, Boolean, Boolean) -> Unit
) {
    val colors = LocalAetherColors.current
    var sendCopy by remember { mutableStateOf(false) }
    var removeCaption by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .clip(AetherEmber.Shapes.L)
                .background(colors.surface)
                .border(1.dp, colors.border, AetherEmber.Shapes.L)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* keep taps inside the sheet */ }
                .testTag("forward_target_sheet")
        ) {
            Text(
                text = "Forward to",
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = colors.textPrimary,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp)
            )

            if (canSendCopy) {
                ForwardOptionRow(
                    label = "Hide the original sender",
                    detail = "Forwards without attribution, as though you wrote it",
                    checked = sendCopy,
                    testTag = "forward_option_copy",
                    onToggle = {
                        sendCopy = !sendCopy
                        // Dropping a caption is only meaningful on a copy; an
                        // attributed forward keeps the original intact.
                        if (!sendCopy) removeCaption = false
                    }
                )
                if (sendCopy && hasCaption) {
                    ForwardOptionRow(
                        label = "Remove the caption",
                        detail = "Sends the media without its text",
                        checked = removeCaption,
                        testTag = "forward_option_remove_caption",
                        onToggle = { removeCaption = !removeCaption }
                    )
                }
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(targets, key = { it.id }) { target ->
                    ChatRow(
                        chat = target,
                        onClick = { onPick(target, sendCopy, removeCaption) },
                        modifier = Modifier.testTag("forward_target_${target.id}")
                    )
                }
            }
        }
    }
}

@Composable
private fun ForwardOptionRow(
    label: String,
    detail: String,
    checked: Boolean,
    testTag: String,
    onToggle: () -> Unit
) {
    val colors = LocalAetherColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontFamily = ManropeFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary
            )
            Text(
                text = detail,
                fontFamily = ManropeFontFamily,
                fontSize = 12.sp,
                color = colors.textTertiary
            )
        }
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(AetherEmber.Shapes.Pill)
                .background(if (checked) colors.accent else colors.surfaceHighlight)
        )
    }
}


/**
 * One album, presented with the same bubble chrome as a single media message.
 *
 * Long-press targets the album's anchor message, so the action policy answers about
 * a real message rather than about the synthetic group.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumEntryRow(
    album: ConversationEntry.Album,
    onLongPress: () -> Unit,
    onMediaClick: (MediaItem) -> Unit
) {
    val colors = LocalAetherColors.current
    val isOutgoing = album.anchor.isOutgoing
    val contentColor = if (isOutgoing) colors.bubbleOutgoingText else colors.bubbleIncomingText

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 2.dp),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .clip(AetherEmber.Shapes.L)
                .background(if (isOutgoing) colors.bubbleOutgoing else colors.bubbleIncoming)
                .combinedClickable(onClick = {}, onLongClick = onLongPress)
                .padding(4.dp)
        ) {
            AlbumBubble(
                album = album,
                contentColor = contentColor,
                onMediaClick = onMediaClick
            )
        }
    }
}

private fun Set<String>.toggle(id: String): Set<String> =
    if (id in this) this - id else this + id

/**
 * Carries out a tap on a formatted-text span.
 *
 * Every branch resolves to a real, safe Android intent or an in-app navigation. A
 * span whose action Aether cannot perform never reaches here — the resolver returns
 * null for those and the span renders as ordinary text.
 */
private fun handleEntityAction(
    context: android.content.Context,
    action: EntityAction,
    onOpenUsername: (String) -> Unit
) {
    fun view(uri: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    when (action) {
        is EntityAction.OpenUrl -> view(action.url)
        is EntityAction.ComposeEmail -> view("mailto:${action.address}")
        is EntityAction.DialPhone -> view("tel:${action.number}")
        is EntityAction.OpenUsername -> onOpenUsername(action.username)
        is EntityAction.OpenUser -> onOpenUsername(action.userId.toString())
        is EntityAction.CopyText -> {
            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
            clipboard?.setPrimaryClip(
                android.content.ClipData.newPlainText("Aether", action.text)
            )
        }
        // Hashtag search and media seeking are not wired to a destination yet, so
        // they do nothing rather than opening something unrelated.
        is EntityAction.SearchHashtag,
        is EntityAction.SeekMedia -> Unit
    }
}

/**
 * The most recent location fix Android already holds, if any.
 *
 * Deliberately does not start a fresh location request: a static share means "where
 * I am", and the last known fix answers that without switching on the radio or
 * subscribing to updates the user did not ask for. Returns null rather than a stale
 * guess when nothing is available.
 */
private fun lastKnownCoarseLocation(context: android.content.Context): Pair<Double, Double>? {
    val manager = context.getSystemService(android.location.LocationManager::class.java)
        ?: return null
    val providers = listOf(
        android.location.LocationManager.NETWORK_PROVIDER,
        android.location.LocationManager.GPS_PROVIDER
    )
    return providers.firstNotNullOfOrNull { provider ->
        runCatching { manager.getLastKnownLocation(provider) }
            .getOrNull()
            ?.let { it.latitude to it.longitude }
    }
}

/**
 * Actions for a multi-selection.
 *
 * The action list is the *intersection* of what every selected message supports, so
 * one protected message removes Forward for the whole selection rather than
 * producing a partial forward that silently drops it.
 *
 * A message whose capabilities have not arrived yet contributes nothing, which
 * correctly narrows the intersection rather than widening it on an assumption.
 */
@Composable
private fun SelectionToolbar(
    selection: List<Message>,
    capabilities: Map<String, MessageCapabilities>,
    onDismiss: () -> Unit,
    onAction: (MessageAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAetherColors.current
    val actions = remember(selection, capabilities) {
        MessageActionPolicy.actionsForSelection(
            selection.map { message ->
                message to (capabilities[message.id] ?: MessageCapabilities.Unknown)
            }
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(AetherEmber.Shapes.L)
            .background(colors.surface)
            .border(1.dp, colors.border, AetherEmber.Shapes.L)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("selection_toolbar"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AetherIconButton(
            icon = Icons.Default.Close,
            contentDescription = "Cancel selection",
            onClick = onDismiss,
            modifier = Modifier.testTag("selection_cancel")
        )
        Text(
            text = "${selection.size} selected",
            fontFamily = ManropeFontFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp)
                .testTag("selection_count")
        )
        actions.forEach { action ->
            AetherIconButton(
                icon = selectionIcon(action),
                contentDescription = selectionLabel(action),
                onClick = { onAction(action) },
                modifier = Modifier.testTag("selection_action_${action.name.lowercase()}")
            )
        }
    }
}

private fun selectionLabel(action: MessageAction): String = when (action) {
    MessageAction.COPY -> "Copy"
    MessageAction.FORWARD -> "Forward"
    MessageAction.SAVE -> "Save"
    MessageAction.DELETE_FOR_ME -> "Delete for me"
    MessageAction.DELETE_FOR_EVERYONE -> "Delete for everyone"
    else -> action.name.lowercase()
}

private fun selectionIcon(action: MessageAction) = when (action) {
    MessageAction.COPY -> Icons.Default.ContentCopy
    MessageAction.FORWARD -> Icons.AutoMirrored.Filled.Send
    MessageAction.SAVE -> Icons.Default.Download
    else -> Icons.Default.Delete
}

/**
 * Search bar for one conversation.
 *
 * Result counts and the position label come from the server's answer, so "3 of 128"
 * means there really are 128 matches — not 128 loaded rows. The empty state is shown
 * only once a search has actually completed.
 */
@Composable
private fun ConversationSearchBar(
    state: ConversationSearchState,
    onQueryChange: (String) -> Unit,
    onOlder: () -> Unit,
    onNewer: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAetherColors.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(AetherEmber.Shapes.L)
            .background(colors.surface)
            .border(1.dp, colors.border, AetherEmber.Shapes.L)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("conversation_search_bar")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AetherSearchPill(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = "Search in conversation",
                requestFocus = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("conversation_search_field")
            )
            AetherIconButton(
                icon = Icons.Default.Close,
                contentDescription = "Close search",
                onClick = onClose,
                modifier = Modifier.testTag("conversation_search_close")
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when {
                    state.isLoading -> "Searching…"
                    state.error != null -> state.error
                    state.isEmptyResult -> "No messages found"
                    else -> state.positionLabel.orEmpty()
                },
                fontFamily = ManropeFontFamily,
                fontSize = 12.5.sp,
                color = if (state.error != null) Color(0xFFEF4444) else colors.textSecondary,
                modifier = Modifier
                    .weight(1f)
                    .testTag("conversation_search_status")
            )
            AetherIconButton(
                icon = Icons.Default.KeyboardArrowUp,
                contentDescription = "Older result",
                onClick = onOlder,
                enabled = state.canGoOlder,
                modifier = Modifier.testTag("conversation_search_older")
            )
            AetherIconButton(
                icon = Icons.Default.KeyboardArrowDown,
                contentDescription = "Newer result",
                onClick = onNewer,
                enabled = state.canGoNewer,
                modifier = Modifier.testTag("conversation_search_newer")
            )
        }
    }
}
