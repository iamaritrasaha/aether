package com.foresightlabs.aether.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.foresightlabs.aether.ui.design.AetherFrostState
import com.foresightlabs.aether.ui.design.AetherGlassMenuItem
import com.foresightlabs.aether.ui.design.AetherGlassPopup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import com.foresightlabs.aether.ui.components.ScheduledMessagesSheet
import com.foresightlabs.aether.domain.model.AnimationItem
import com.foresightlabs.aether.ui.components.ComposerDockMode
import com.foresightlabs.aether.ui.components.VideoNoteRecorderSheet
import androidx.compose.material.icons.filled.Schedule
import com.foresightlabs.aether.ui.components.LocationShareSheet
import android.content.Intent
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import com.foresightlabs.aether.ui.design.AetherAccent
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.material3.ripple
import com.foresightlabs.aether.domain.text.EntityAction
import com.foresightlabs.aether.domain.messages.MessageAction
import com.foresightlabs.aether.domain.messages.MessageCapabilities
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.MessageType
import com.foresightlabs.aether.ui.components.AetherAtmosphericBackground
import com.foresightlabs.aether.ui.components.AetherAvatar
import com.foresightlabs.aether.ui.components.MediaViewer
import com.foresightlabs.aether.ui.components.MessageBubble
import com.foresightlabs.aether.ui.components.MessageComposer
import com.foresightlabs.aether.ui.components.MessageContextMenu
import com.foresightlabs.aether.ui.design.AetherFloatingHeader
import com.foresightlabs.aether.ui.design.AetherFloatingHeaderDefaults
import com.foresightlabs.aether.ui.design.AetherGlass
import com.foresightlabs.aether.ui.design.AetherIconButton
import com.foresightlabs.aether.ui.design.aetherChatAvatar
import com.foresightlabs.aether.ui.design.LocalSceneHeightCache
import com.foresightlabs.aether.ui.design.LocalSceneOwnsDock
import com.foresightlabs.aether.ui.design.LocalSceneTransitionProgress
import com.foresightlabs.aether.ui.design.edgePx
import com.foresightlabs.aether.ui.design.aetherFloatingHeaderContentTopPadding
import com.foresightlabs.aether.ui.design.aetherFrostSource
import com.foresightlabs.aether.ui.design.rememberAetherFrostState
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.LocalAtmosphere
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
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
    savedAnimations: List<AnimationItem> = emptyList(),
    onLoadSavedAnimations: () -> Unit = {},
    onSendAnimation: (Int) -> Unit = {},
    onLoadScheduled: suspend () -> List<Message> = { emptyList() },
    onSendScheduledNow: (Message) -> Unit = {},
    onRescheduleMessage: (Message, Int) -> Unit = { _, _ -> },
    onPollVote: (Message, List<Int>) -> Unit = { _, _ -> },
    onCopyMessageLink: (Message) -> Unit = {},
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
    var deleteConfirmMessages by remember { mutableStateOf<List<Message>?>(null) }
    // Grouping is derived once per message-list change, not per frame.
    val entries = remember(messages) { MessageGrouping.group(messages) }
    var showContactSheet by remember { mutableStateOf(false) }
    var showLocationSheet by remember { mutableStateOf(false) }
    var showLiveLocationSheet by remember { mutableStateOf(false) }
    var showVenueSheet by remember { mutableStateOf(false) }
    var showScheduledSheet by remember { mutableStateOf(false) }
    var dockMode by remember { mutableStateOf(ComposerDockMode.COLLAPSED) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
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
        // into one row, and the date divider precedes them at index 0.
        val entryIndex = entries.indexOfFirst { entry ->
            when (entry) {
                is ConversationEntry.Single -> entry.message.id == target
                is ConversationEntry.Album -> entry.messages.any { it.id == target }
            }
        }
        if (entryIndex < 0) return@LaunchedEffect
        val index = entryIndex + 1
        highlightedMessageId = target
        runCatching { listState.animateScrollToItem(index) }
        onJumpConsumed()
        delay(1_600)
        if (highlightedMessageId == target) highlightedMessageId = null
    }
    var isContextMenuVisible by remember { mutableStateOf(false) }

    var selectedMediaForViewer by remember { mutableStateOf<MediaItem?>(null) }
    var isMediaViewerVisible by remember { mutableStateOf(false) }
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
                frostState = frostState
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

    // Two physical regions with a real depth relationship.
    //
    // The footer is the base layer: it fills the whole window, including behind
    // the gesture inset, so nothing of the atmosphere is left below it. The
    // conversation canvas is drawn on top of it and stops short of the bottom,
    // with softly rounded lower corners — so the footer is genuinely *behind* the
    // chat rather than a band painted next to it, and the canvas reads as resting
    // over a recessed dark section.
    val sceneOwnsDock = LocalSceneOwnsDock.current
    val dockColor = LocalAetherColors.current.background
    val density = LocalDensity.current

    // Mirrors Home's structure exactly: a rear layer (here, just the composer
    // pinned to the bottom) and a foreground canvas panel drawn on top of it,
    // aligned TopCenter with its own zIndex — not a Column where the canvas
    // takes "whatever weight leaves" and the composer follows it. Two sibling
    // panels sharing one alignment scheme is what lets the canvas and Home's
    // hero read the same explicit height from the same shared formula during
    // the morph, rather than two shapes that only resemble each other.
    var dockHeightPx by remember { mutableIntStateOf(0) }
    val sceneProgress = LocalSceneTransitionProgress.current
    val heightCache = LocalSceneHeightCache.current
    val atRest = sceneProgress == null || sceneProgress > 0.98f

    // Staggered conversation entry: elements resolve continuously as progress moves
    // from 0.0 to 1.0 (Home to Conversation).
    val p = sceneProgress?.coerceIn(0f, 1f) ?: 1f

    // 1. Identity Header begins resolving into view once the lavender panel has
    // expanded past 0.30, gently descending to its rest position.
    val headerProgress = if (sceneProgress == null) 1f else ((p - 0.30f) / 0.50f).coerceIn(0f, 1f)
    val headerAlpha = headerProgress
    val headerTranslationY = if (sceneProgress == null) 0f else with(density) { (18.dp.toPx() * (1f - headerProgress)) }

    // 2. Message stream fades in and lifts into place from 0.45 to 0.95.
    val messagesProgress = if (sceneProgress == null) 1f else ((p - 0.45f) / 0.50f).coerceIn(0f, 1f)
    val messagesAlpha = messagesProgress
    val messagesTranslationY = if (sceneProgress == null) 0f else with(density) { (20.dp.toPx() * (1f - messagesProgress)) }

    // 3. Composer dock in the exposed rear layer resolves from 0.50 to 1.00.
    val composerProgress = if (sceneProgress == null) 1f else ((p - 0.50f) / 0.45f).coerceIn(0f, 1f)
    val composerAlpha = composerProgress
    val composerTranslationY = if (sceneProgress == null) 0f else with(density) { (14.dp.toPx() * (1f - composerProgress)) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            // Same surface as Home's, painted by the scene above the graph when
            // there is one. See LocalSceneOwnsDock.
            .then(if (sceneOwnsDock) Modifier else Modifier.background(dockColor))
            .imePadding()
    ) {
        val containerHeightPx = with(density) { maxHeight.toPx() }
        val morphedCanvasPx = if (!atRest && heightCache != null) {
            heightCache.edgePx(sceneProgress!!, containerHeightPx)
        } else null

        // --- The rear layer: the composer, pinned to the bottom ------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .graphicsLayer {
                    alpha = composerAlpha
                    translationY = composerTranslationY
                }
                .onSizeChanged { dockHeightPx = it.height }
                .testTag("conversation_dock")
        ) {
            MessageComposer(
                replyingTo = replyingToMessage,
                onDismissReply = {
                    replyingToMessage = null
                    replyQuote = null
                },
                replyQuote = replyQuote,
                dockMode = dockMode,
                onDockModeChange = { mode ->
                    if (mode.isExpanded) {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        if (mode.isPicker) {
                            onLoadStickers()
                            onLoadSavedAnimations()
                        }
                    }
                    dockMode = mode
                },
                onToggleAttachment = {
                    if (dockMode == ComposerDockMode.ATTACHMENTS) {
                        dockMode = ComposerDockMode.COLLAPSED
                    } else {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        dockMode = ComposerDockMode.ATTACHMENTS
                    }
                },
                onTogglePicker = {
                    if (dockMode.isPicker) {
                        dockMode = ComposerDockMode.COLLAPSED
                    } else {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        onLoadStickers()
                        onLoadSavedAnimations()
                        dockMode = ComposerDockMode.EMOJI
                    }
                },
                onInputFocus = {
                    dockMode = ComposerDockMode.COLLAPSED
                },
                onSelectGallery = {
                    dockMode = ComposerDockMode.COLLAPSED
                    photoPickerLauncher.launch("image/*")
                },
                onSelectCamera = {
                    dockMode = ComposerDockMode.COLLAPSED
                    val hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    if (hasCameraPermission) {
                        val file = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
                        cameraTempFile = file
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        cameraLauncher.launch(uri)
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                onSelectVideoNote = {
                    dockMode = ComposerDockMode.COLLAPSED
                    showVideoNoteRecorder = true
                },
                onSelectFile = {
                    dockMode = ComposerDockMode.COLLAPSED
                    docPickerLauncher.launch(arrayOf("*/*"))
                },
                onSelectAudio = {
                    dockMode = ComposerDockMode.COLLAPSED
                    docPickerLauncher.launch(arrayOf("audio/*"))
                },
                onSelectLocation = {
                    dockMode = ComposerDockMode.COLLAPSED
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        showLocationSheet = true
                    } else {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                },
                onSelectVenue = {
                    dockMode = ComposerDockMode.COLLAPSED
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        showVenueSheet = true
                    } else {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                },
                onSelectContact = {
                    dockMode = ComposerDockMode.COLLAPSED
                    showContactSheet = true
                },
                onSendMessage = { text, formatting ->
                    dockMode = ComposerDockMode.COLLAPSED
                    onSendMessage(text, replyingToMessage, formatting, replyQuote)
                    replyingToMessage = null
                    replyQuote = null
                },
                onTextChanged = onComposerChanged,
                enabled = canSend,
                installedStickerSets = installedStickerSets,
                recentStickers = recentStickers,
                favoriteStickers = favoriteStickers,
                onLoadStickerSetDetails = onLoadStickerSetDetails,
                onSendSticker = onSendSticker,
                savedAnimations = savedAnimations,
                onSendAnimation = onSendAnimation,
                onVoiceNoteRecorded = {
                    dockMode = ComposerDockMode.COLLAPSED
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
                onOpenVideoNote = {
                    dockMode = ComposerDockMode.COLLAPSED
                    showVideoNoteRecorder = true
                },
                selectedMessages = messages.filter { it.id in selectedIds },
                capabilities = messageCapabilities,
                onClearSelection = { selectedIds = emptySet() },
                onReplySelected = { msg ->
                    replyingToMessage = msg
                    replyQuote = null
                },
                onEditSelected = { msg ->
                    editingMessage = msg
                },
                onCopySelected = { chosen ->
                    clipboardManager.setText(
                        AnnotatedString(chosen.joinToString("\n\n") { it.text })
                    )
                },
                onForwardSelected = { chosen ->
                    forwardingMessages = chosen
                },
                onDeleteSelected = { chosen ->
                    deleteConfirmMessages = chosen
                }
            )
        }

        // --- The foreground layer: the canvas -------------------------
        // A panel lying over that rear layer, exactly like Home's hero —
        // same alignment, same zIndex, same rounded-bottom-corner shape
        // family, so the two read as one object at different heights
        // rather than two screens that merely look alike.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .zIndex(1f)
                .then(
                    if (morphedCanvasPx != null) {
                        Modifier.height(with(density) { morphedCanvasPx.toDp() })
                    } else {
                        Modifier.height(with(density) { (containerHeightPx - dockHeightPx).coerceAtLeast(0f).toDp() })
                            .onSizeChanged { heightCache?.conversationRestPx = it.height.toFloat() }
                    }
                )
                .clip(
                    RoundedCornerShape(
                        bottomStart = ConversationCanvasRadius,
                        bottomEnd = ConversationCanvasRadius
                    )
                )
                .testTag("conversation_canvas")
        ) {
        AetherAtmosphericBackground(
            modifier = Modifier.fillMaxSize(),
            heroFraction = 1f,
            frostState = frostState
        ) {}

        LazyColumn(
            state = listState,
            // The list's own bounds stay the canvas's full size — it is not
            // shrunk to start below the header. Only its *content* is inset
            // via contentPadding, so the viewport genuinely extends behind
            // the fixed frosted header and scrolled-past messages travel
            // under the glass instead of being clipped off at its edge.
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = messagesAlpha
                    translationY = messagesTranslationY
                }
                // Registered as its own haze source alongside the atmosphere
                // Box above, so the header's blur is a real composite of both
                // — messages passing underneath actually shape the glass,
                // not just the static gradient behind them.
                .aetherFrostSource(frostState)
                .testTag("conversation_message_list"),
            // Rests the first message below the header at rest, while still
            // letting it scroll up behind the header as the list moves.
            // Bottom padding clears the canvas's own rounded lower corners,
            // so the newest message is never nicked by the curve.
            contentPadding = PaddingValues(
                top = conversationContentTopPadding(hasPinnedBanner = pinnedMessage != null),
                bottom = ConversationCanvasRadius - AetherEmber.Spacing.Space8
            ),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {


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
                        onRequestCapabilities(targetMsg)
                        selectedIds = selectedIds.toggle(targetMsg.id)
                    },
                    isSelected = msg.id in selectedIds,
                    isSelectionActive = isSelecting,
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
                    onStopLiveLocation = onStopLiveLocation,
                    onRetry = onRetryMessage
                )
                } // items
            } // LazyColumn

        // --- The identity of the conversation, and nothing else ------------
        // A direct sibling of the atmosphere it frosts, not a sibling of the
        // canvas Box that wraps it — the glass header only ever reads the
        // living gradient correctly when it shares the same immediate parent
        // as the hazeSource registering that gradient. Putting the header
        // outside this Box (a cousin of the atmosphere instead of a sibling)
        // is what turned it into a flat, opaque panel before. It still stays
        // above the message column via its own zIndex — composition order
        // alone no longer decides it once any sibling sets zIndex explicitly.
        ConversationIdentityHeader(
            chat = chat,
            searchState = searchState,
            onOpenSearch = onOpenSearch,
            onCloseSearch = onCloseSearch,
            onSearchQueryChange = onSearchQueryChange,
            onSearchOlder = onSearchOlder,
            onSearchNewer = onSearchNewer,
            onOpenProfile = onNavigateToProfile,
            pinned = pinnedMessage,
            pinnedCount = pinnedMessages.size,
            pinnedIndex = pinnedCursor,
            canUnpin = canUnpin,
            onPinnedClick = {
                // Jumping loads the surrounding window when the pin is older than
                // anything currently held.
                pinnedMessage?.let { onJumpToMessage(it.id) }
                // A second tap moves to the next pin, the way Telegram cycles a
                // stack of them.
                if (pinnedMessages.size > 1) {
                    pinnedCursor = (pinnedCursor + 1) % pinnedMessages.size
                }
            },
            onUnpin = { pinnedMessage?.let(onUnpinMessage) },
            frostState = frostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(2f)
                .graphicsLayer {
                    alpha = headerAlpha
                    translationY = headerTranslationY
                }
        )
        } // canvas Box

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

        ScheduledMessagesSheet(
            isVisible = showScheduledSheet,
            onDismiss = { showScheduledSheet = false },
            onLoadScheduled = onLoadScheduled,
            onSendNow = onSendScheduledNow,
            onReschedule = onRescheduleMessage,
            onDelete = { msg -> onDeleteMessage(msg, false) }
        )

        BackHandler(enabled = dockMode != ComposerDockMode.COLLAPSED) {
            dockMode = ComposerDockMode.COLLAPSED
        }

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
        }

        BackHandler(enabled = searchState.isActive) { onCloseSearch() }

        // Context Menu Overlay for Message
        MessageContextMenu(
            message = selectedContextMenuMessage,
            capabilities = selectedContextMenuMessage?.let { messageCapabilities[it.id] } ?: MessageCapabilities.Unknown,
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
                    MessageAction.REPLY -> {
                        replyingToMessage = target
                        replyQuote = null
                    }
                    MessageAction.QUOTE_REPLY -> {
                        replyingToMessage = target
                        replyQuote = ReplyQuote.from(target.richText, 0, target.text.length)
                    }
                    MessageAction.COPY -> {
                        clipboardManager.setText(AnnotatedString(target.text))
                    }
                    MessageAction.FORWARD -> forwardingMessages = listOf(target)
                    MessageAction.SELECT -> selectedIds = setOf(target.id)
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
                    MessageAction.INFO -> infoMessage = target
                    MessageAction.COPY_LINK -> onCopyMessageLink(target)
                    MessageAction.SAVE -> Unit
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
                    selectedIds = emptySet()
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

        // Delete Confirmation Modal for Single/Multi Selection
        if (deleteConfirmMessages != null) {
            val toDelete = deleteConfirmMessages!!
            DeleteConfirmationModal(
                count = toDelete.size,
                canDeleteForAll = toDelete.any { messageCapabilities[it.id]?.canBeDeletedForAllUsers == true },
                onDismiss = { deleteConfirmMessages = null },
                onDeleteForMe = {
                    toDelete.forEach { onDeleteMessage(it, false) }
                    selectedIds = emptySet()
                    deleteConfirmMessages = null
                },
                onDeleteForEveryone = {
                    toDelete.forEach { onDeleteMessage(it, true) }
                    selectedIds = emptySet()
                    deleteConfirmMessages = null
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
    val hasLocationPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    if (!hasLocationPermission) return null

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
 * Clean, quiet deletion confirmation modal for single or multi-message deletion.
 */
@Composable
private fun DeleteConfirmationModal(
    count: Int,
    canDeleteForAll: Boolean,
    onDismiss: () -> Unit,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit
) {
    val colors = LocalAetherColors.current
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF1B1B22))
                .border(0.5.dp, Color(0x28FFFFFF), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = if (count == 1) "Delete Message" else "Delete $count Messages",
                    fontFamily = ManropeFontFamily,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )

                Text(
                    text = if (canDeleteForAll) {
                        "Are you sure you want to delete the selected message(s)?"
                    } else {
                        "Are you sure you want to delete the selected message(s) for yourself?"
                    },
                    fontFamily = ManropeFontFamily,
                    fontSize = 14.sp,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                if (canDeleteForAll) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFEF4444).copy(alpha = 0.15f))
                            .clickable {
                                onDeleteForEveryone()
                                onDismiss()
                            },
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
                        .background(if (canDeleteForAll) Color(0x18FFFFFF) else Color(0xFFEF4444).copy(alpha = 0.15f))
                        .clickable {
                            onDeleteForMe()
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Delete for me",
                        fontFamily = ManropeFontFamily,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (canDeleteForAll) colors.textPrimary else Color(0xFFEF4444)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onDismiss() },
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

/** How softly the conversation canvas turns its lower corners onto the footer. */
private val ConversationCanvasRadius = 28.dp
private val ConversationPinnedHeight = 44.dp

/**
 * Top inset the message stream needs so its newest content is never hidden behind
 * the header, or behind the pinned strip when one is showing.
 */
@Composable
private fun conversationContentTopPadding(hasPinnedBanner: Boolean): Dp =
    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
        AetherFloatingHeaderDefaults.TopGap +
        AetherFloatingHeaderDefaults.ExpandedHeight +
        (if (hasPinnedBanner) ConversationPinnedHeight + AetherEmber.Spacing.Space8 else 0.dp) +
        AetherEmber.Spacing.Space8

/**
 * Mac-like restrained optical control for in-conversation search.
 *
 * Compact circular control, thin/clean magnifying-glass icon, visually light,
 * low-contrast neutral backing, subtle depth border.
 * 48dp touch target, ~40dp visible circular lens, ~20dp icon.
 */
@Composable
fun ConversationSearchButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAetherColors.current
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 24.dp),
                onClick = onClick
            )
            .semantics { this.contentDescription = "Search in conversation" }
            .testTag("conversation_search_button"),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0x22FFFFFF))
                .border(
                    width = 0.5.dp,
                    color = Color(0x18FFFFFF),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = colors.atmosphereTextPrimary.copy(alpha = 0.88f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Frosted Floating Identity & Search Header.
 *
 * Uses Aether's canonical frosted glass primitive. The header smoothly morphs between
 * resting conversation identity mode and active in-chat search mode.
 * Leaving is handled cleanly by the system gesture and BackHandler.
 */
@Composable
fun ConversationIdentityHeader(
    chat: Chat,
    searchState: ConversationSearchState = ConversationSearchState.Idle,
    onOpenSearch: () -> Unit = {},
    onCloseSearch: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onSearchOlder: () -> Unit = {},
    onSearchNewer: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    pinned: Message? = null,
    pinnedCount: Int = 0,
    pinnedIndex: Int = 0,
    canUnpin: Boolean = false,
    onPinnedClick: () -> Unit = {},
    onUnpin: () -> Unit = {},
    frostState: AetherFrostState? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalAetherColors.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
            .padding(
                start = AetherFloatingHeaderDefaults.HorizontalMargin,
                top = AetherFloatingHeaderDefaults.TopGap,
                end = AetherFloatingHeaderDefaults.HorizontalMargin
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AetherGlass(
            frostState = frostState,
            modifier = Modifier
                .fillMaxWidth()
                .height(AetherFloatingHeaderDefaults.ExpandedHeight),
            shape = AetherFloatingHeaderDefaults.Shape,
            elevation = 6.dp
        ) {
            AnimatedContent(
                targetState = searchState.isActive,
                transitionSpec = {
                    fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(140))
                },
                label = "conversation_header_mode",
                modifier = Modifier.fillMaxSize()
            ) { isSearch ->
                if (!isSearch) {
                    // Normal Identity Mode
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 10.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Avatar + Title + Status (tapping opens Profile)
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(AetherEmber.Shapes.M)
                                .clickable { onOpenProfile() }
                                .padding(vertical = 4.dp, horizontal = 4.dp)
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
                                hasUnseenPulse = chat.hasUnseenPulse,
                                modifier = Modifier.aetherChatAvatar(chat.id)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = chat.title,
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 16.5.sp,
                                    lineHeight = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.4).sp,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = when {
                                        chat.directUser?.isOnline == true -> "online"
                                        chat.directUser?.lastSeenText != null -> chat.directUser.lastSeenText
                                        chat.memberCount > 0 -> "${chat.memberCount} members"
                                        else -> "offline"
                                    },
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Mac-like restrained optical search button
                        ConversationSearchButton(
                            onClick = onOpenSearch
                        )
                    }
                } else {
                    // Search Mode
                    val focusRequester = remember { FocusRequester() }
                    val keyboard = LocalSoftwareKeyboardController.current

                    LaunchedEffect(Unit) {
                        focusRequester.requestFocus()
                        keyboard?.show()
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 14.dp, end = 6.dp)
                            .testTag("conversation_search_bar"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))

                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchState.query.isEmpty()) {
                                Text(
                                    text = "Search in conversation",
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 14.5.sp,
                                    color = colors.textSecondary.copy(alpha = 0.65f)
                                )
                            }
                            BasicTextField(
                                value = searchState.query,
                                onValueChange = onSearchQueryChange,
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 14.5.sp,
                                    color = colors.textPrimary
                                ),
                                cursorBrush = SolidColor(AetherAccent.current),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                                    .testTag("conversation_search_field")
                            )
                        }

                        if (searchState.hasResults) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 2.dp)
                            ) {
                                Text(
                                    text = searchState.positionLabel.orEmpty(),
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textSecondary,
                                    modifier = Modifier.testTag("conversation_search_status")
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                AetherIconButton(
                                    icon = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Older result",
                                    onClick = onSearchOlder,
                                    enabled = searchState.canGoOlder,
                                    size = 34.dp,
                                    iconSize = 18.dp,
                                    modifier = Modifier.testTag("conversation_search_older")
                                )
                                AetherIconButton(
                                    icon = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Newer result",
                                    onClick = onSearchNewer,
                                    enabled = searchState.canGoNewer,
                                    size = 34.dp,
                                    iconSize = 18.dp,
                                    modifier = Modifier.testTag("conversation_search_newer")
                                )
                            }
                        } else if (searchState.isEmptyResult) {
                            Text(
                                text = "No messages found",
                                fontFamily = ManropeFontFamily,
                                fontSize = 12.sp,
                                color = colors.textSecondary,
                                modifier = Modifier
                                    .padding(horizontal = 6.dp)
                                    .testTag("conversation_search_status")
                            )
                        } else if (searchState.isLoading) {
                            Text(
                                text = "Searching…",
                                fontFamily = ManropeFontFamily,
                                fontSize = 12.sp,
                                color = colors.textSecondary,
                                modifier = Modifier
                                    .padding(horizontal = 6.dp)
                                    .testTag("conversation_search_status")
                            )
                        }

                        AetherIconButton(
                            icon = Icons.Default.Close,
                            contentDescription = "Close search",
                            onClick = {
                                keyboard?.hide()
                                onCloseSearch()
                            },
                            size = 40.dp,
                            iconSize = 18.dp,
                            modifier = Modifier.testTag("conversation_search_close")
                        )
                    }
                }
            }
        }

        // Pinned Message Banner directly beneath the frosted floating header
        if (pinned != null && !searchState.isActive) {
            Spacer(modifier = Modifier.height(6.dp))
            AetherGlass(
                frostState = frostState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ConversationPinnedHeight),
                shape = RoundedCornerShape(16.dp),
                elevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onPinnedClick() }
                        .padding(horizontal = 12.dp)
                        .testTag("pinned_banner"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Pinned",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (pinnedCount > 1) {
                            "Pinned ${pinnedIndex + 1} of $pinnedCount · ${pinned.text.ifBlank { "Media" }}"
                        } else {
                            pinned.text.ifBlank { "Pinned media" }
                        },
                        fontFamily = ManropeFontFamily,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (canUnpin) {
                        AetherIconButton(
                            icon = Icons.Default.Close,
                            contentDescription = "Unpin message",
                            onClick = onUnpin,
                            size = 32.dp,
                            iconSize = 16.dp,
                            tint = colors.textSecondary,
                            modifier = Modifier.testTag("pinned_unpin")
                        )
                    }
                }
            }
        }
    }
}
