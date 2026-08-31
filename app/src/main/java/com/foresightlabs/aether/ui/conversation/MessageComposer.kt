package com.foresightlabs.aether.ui.conversation
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardAlt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foresightlabs.aether.domain.messages.ConversationMotion
import com.foresightlabs.aether.domain.messages.MessageCapabilities
import com.foresightlabs.aether.domain.model.AnimationItem
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.StickerItem
import com.foresightlabs.aether.domain.model.StickerSetInfo
import com.foresightlabs.aether.domain.sharing.SharedAttachmentKind
import com.foresightlabs.aether.domain.text.AetherEntity
import com.foresightlabs.aether.domain.text.ComposerFormatting
import com.foresightlabs.aether.domain.text.ComposerLinkPreviewState
import com.foresightlabs.aether.domain.text.ComposerStyle
import com.foresightlabs.aether.domain.text.ReplyQuote
import com.foresightlabs.aether.ui.design.AetherAccent
import com.foresightlabs.aether.ui.design.AetherGlass
import com.foresightlabs.aether.ui.design.AetherIconButton
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class AttachmentOptionItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

/**
 * Attachment options as direct Curtain content: icon circles and labels, and no
 * container of their own — the Curtain is already the surface.
 */
@Composable
fun AttachmentCurtainContent(
    onSelectGallery: () -> Unit,
    onSelectCamera: () -> Unit,
    onSelectVideoNote: () -> Unit,
    onSelectFile: () -> Unit,
    onSelectAudio: () -> Unit,
    onSelectLocation: () -> Unit,
    onSelectVenue: () -> Unit,
    onSelectContact: () -> Unit,
    modifier: Modifier = Modifier
) {
    val itemFill = Color(0x18FFFFFF)
    val itemBorder = Color(0x1AFFFFFF)
    val itemIconTint = Color(0xFFF0F0F3)
    val itemLabelColor = Color(0xD0FFFFFF)

    val items = remember {
        listOf(
            AttachmentOptionItem("Gallery", Icons.Default.PhotoLibrary, onSelectGallery),
            AttachmentOptionItem("Camera", Icons.Default.CameraAlt, onSelectCamera),
            AttachmentOptionItem("Video note", Icons.Default.Videocam, onSelectVideoNote),
            AttachmentOptionItem("File", Icons.Default.Description, onSelectFile),
            AttachmentOptionItem("Audio", Icons.Default.Headphones, onSelectAudio),
            AttachmentOptionItem("Location", Icons.Default.LocationOn, onSelectLocation),
            AttachmentOptionItem("Venue", Icons.Default.Place, onSelectVenue),
            AttachmentOptionItem("Contact", Icons.Default.Person, onSelectContact)
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("curtain_attachment_content"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val rows = items.chunked(4)
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Top
            ) {
                rowItems.forEach { item ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = false, radius = 26.dp),
                                onClick = item.onClick
                            )
                            .padding(vertical = 4.dp)
                            .testTag("attachment_option_${item.label.lowercase().replace(" ", "_")}")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(itemFill)
                                .border(0.5.dp, itemBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = itemIconTint,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.label,
                            fontFamily = ManropeFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = itemLabelColor,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Reviewing one picked photo/video before it sends, with the View once toggle.
 *
 * A restrained control on the existing Curtain, not a second modal: a thumbnail,
 * one toggle row, and Cancel/Send -- the media-send composition state the picker
 * flows land in, mirroring [AttachmentCurtainContent]'s "direct Curtain content,
 * no container of its own" shape.
 */
@Composable
fun MediaPreviewCurtainContent(
    media: com.foresightlabs.aether.ui.conversation.PendingMedia,
    onToggleViewOnce: () -> Unit,
    onCancel: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ink = Color(0xFFF2F2F5)
    val hint = Color(0xFF9A9AA2)

    Column(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("curtain_media_preview_content"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x1AFFFFFF))
        ) {
            coil.compose.AsyncImage(
                model = media.path,
                contentDescription = if (media.isVideo) "Video to send" else "Photo to send",
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                    onClick = onToggleViewOnce
                )
                .padding(horizontal = 6.dp, vertical = 8.dp)
                .testTag("media_preview_view_once_toggle"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (media.viewOnce) AetherAccent.current else Color(0x18FFFFFF))
                    .border(0.5.dp, if (media.viewOnce) AetherAccent.current else Color(0x1AFFFFFF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (media.viewOnce) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color(0xFF0B0B0D),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "View once",
                    fontFamily = ManropeFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ink
                )
                Text(
                    text = "Opens once, then disappears",
                    fontFamily = ManropeFontFamily,
                    fontSize = 11.sp,
                    color = hint
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x14FFFFFF))
                    .clickable(onClick = onCancel)
                    .padding(vertical = 12.dp)
                    .testTag("media_preview_cancel"),
                contentAlignment = Alignment.Center
            ) {
                Text("Cancel", fontFamily = ManropeFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = ink)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AetherAccent.current)
                    .clickable(onClick = onSend)
                    .padding(vertical = 12.dp)
                    .testTag("media_preview_send"),
                contentAlignment = Alignment.Center
            ) {
                Text("Send", fontFamily = ManropeFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0B0B0D))
            }
        }
    }
}

/**
 * Reviewing what another application shared, before any of it is sent.
 *
 * The share-shaped sibling of [MediaPreviewCurtainContent]: the same direct
 * Curtain content, the same Cancel/Send pair, and no surface, card or sheet of
 * its own. It carries no View once toggle -- that is a decision about a photo
 * someone picked here, not something to apply to a file another application
 * handed over.
 */
@Composable
fun SharedContentCurtainContent(
    share: PendingShare,
    onCancel: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ink = Color(0xFFF2F2F5)
    val hint = Color(0xFF9A9AA2)
    val first = share.attachments.firstOrNull()
    val headline = when {
        share.attachments.size > 1 -> "${share.attachments.size} shared items"
        first?.kind == SharedAttachmentKind.IMAGE -> "Shared photo"
        first?.kind == SharedAttachmentKind.VIDEO -> "Shared video"
        else -> first?.name ?: "Shared file"
    }

    Column(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("curtain_share_preview_content"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val visual = share.attachments.firstOrNull {
            it.kind == SharedAttachmentKind.IMAGE || it.kind == SharedAttachmentKind.VIDEO
        }
        if (visual != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x1AFFFFFF))
            ) {
                coil.compose.AsyncImage(
                    model = visual.path,
                    contentDescription = headline,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(30.dp)
                    .clip(CircleShape)
                    .background(AetherAccent.current)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = headline,
                    fontFamily = ManropeFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = share.caption.ifBlank { "Ready to send" },
                    fontFamily = ManropeFontFamily,
                    fontSize = 11.sp,
                    color = hint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x14FFFFFF))
                    .clickable(onClick = onCancel)
                    .padding(vertical = 12.dp)
                    .testTag("share_preview_cancel"),
                contentAlignment = Alignment.Center
            ) {
                Text("Cancel", fontFamily = ManropeFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = ink)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AetherAccent.current)
                    .clickable(onClick = onSend)
                    .padding(vertical = 12.dp)
                    .testTag("share_preview_send"),
                contentAlignment = Alignment.Center
            ) {
                Text("Send", fontFamily = ManropeFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0B0B0D))
            }
        }
    }
}

/**
 * The content of the Conversation Curtain, in every state it can be in.
 *
 * Everything here is content passed to [AetherConversationCurtain], which owns the
 * one surface it all sits on:
 * - COMPOSER: [ + ] Your Message… [ 🙂 ] [ Mic/Send ], with the reply, edit and
 *   selection strips folding into the same row.
 * - ATTACHMENTS: the attachment options, directly on the Curtain.
 * - EMOJI / STICKERS / GIFS: the unified picker.
 * - FORWARDING: the input controls give way to forwarding, in the same surface.
 *
 * A new bottom interaction belongs here as another state — never as a sheet,
 * panel or card of its own.
 */
@Composable
fun MessageComposer(
    replyingTo: Message?,
    onDismissReply: () -> Unit,
    onSendMessage: (String, List<AetherEntity>) -> Unit,
    /** Quoted excerpt shown above the composer when replying to part of a message. */
    replyQuote: ReplyQuote? = null,
    curtainState: CurtainState = CurtainState.COMPOSER,
    onCurtainStateChange: (CurtainState) -> Unit = {},
    onToggleAttachment: () -> Unit = {
        if (curtainState == CurtainState.ATTACHMENTS) {
            onCurtainStateChange(CurtainState.COMPOSER)
        } else {
            onCurtainStateChange(CurtainState.ATTACHMENTS)
        }
    },
    onTogglePicker: () -> Unit = {
        if (curtainState.isPicker) {
            onCurtainStateChange(CurtainState.COMPOSER)
        } else {
            onCurtainStateChange(CurtainState.EMOJI)
        }
    },
    onSelectGallery: () -> Unit = {},
    onSelectCamera: () -> Unit = {},
    onSelectVideoNote: () -> Unit = {},
    onSelectFile: () -> Unit = {},
    onSelectAudio: () -> Unit = {},
    onSelectLocation: () -> Unit = {},
    onSelectVenue: () -> Unit = {},
    onSelectContact: () -> Unit = {},
    onInputFocus: () -> Unit = {},
    onVoiceNoteRecorded: () -> Unit = {},
    onOpenVideoNote: () -> Unit = {},
    installedStickerSets: List<StickerSetInfo> = emptyList(),
    recentStickers: List<StickerItem> = emptyList(),
    favoriteStickers: List<StickerItem> = emptyList(),
    onLoadStickerSetDetails: (Long, (StickerSetInfo) -> Unit) -> Unit = { _, _ -> },
    onSendSticker: (fileId: Int, emoji: String) -> Unit = { _, _ -> },
    savedAnimations: List<AnimationItem> = emptyList(),
    onSendAnimation: (fileId: Int) -> Unit = {},
    selectedMessages: List<Message> = emptyList(),
    capabilities: Map<String, MessageCapabilities> = emptyMap(),
    onClearSelection: () -> Unit = {},
    onReplySelected: (Message) -> Unit = {},
    onEditSelected: (Message) -> Unit = {},
    onCopySelected: (List<Message>) -> Unit = {},
    onForwardSelected: (List<Message>) -> Unit = {},
    /** Toggles pin state for the one selected message -- see [MessageSelectionRow]'s canPin. */
    onPinSelected: (Message) -> Unit = {},
    onDeleteSelected: (List<Message>) -> Unit = {},
    forwardMessages: List<Message> = emptyList(),
    forwardTargets: List<Chat> = emptyList(),
    forwardState: ForwardState = ForwardState.Idle,
    onDismissForward: () -> Unit = {},
    onSubmitForward: (Chat, Boolean, Boolean) -> Unit = { _, _, _ -> },
    editingMessage: Message? = null,
    onDismissEdit: () -> Unit = {},
    onSaveEdit: (Message, String, List<AetherEntity>) -> Unit = { _, _, _ -> },
    pendingMedia: PendingMedia? = null,
    onToggleViewOnce: () -> Unit = {},
    onCancelPendingMedia: () -> Unit = {},
    onSendPendingMedia: () -> Unit = {},
    /** Files another application shared into this conversation, awaiting review. */
    pendingShare: PendingShare? = null,
    onCancelPendingShare: () -> Unit = {},
    onSendPendingShare: () -> Unit = {},
    /**
     * Text to put in the composer, from a share or another hand-off. Applied
     * once per distinct value, so recomposition never retypes it.
     */
    prefillText: String? = null,
    enabled: Boolean = true,
    onTextChanged: (String) -> Unit = {},
    /** Telegram's preview for the link in the draft; see [ComposerLinkPreviewStrip]. */
    linkPreview: ComposerLinkPreviewState = ComposerLinkPreviewState.Empty,
    onDismissLinkPreview: () -> Unit = {},
    /** Reported by the shared Curtain root; see [CurtainHeights]. */
    onCurtainHeightChanged: (CurtainHeights) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Held as a TextFieldValue so the selection is available for formatting.
    var field by remember { mutableStateOf(TextFieldValue("")) }
    var formatting by remember { mutableStateOf<List<AetherEntity>>(emptyList()) }
    var sendingTransition by remember { mutableStateOf(false) }
    val composerScope = rememberCoroutineScope()

    LaunchedEffect(prefillText) {
        val incoming = prefillText
        if (!incoming.isNullOrEmpty()) {
            field = TextFieldValue(text = incoming, selection = TextRange(incoming.length))
            // Reported like typing, so everything keyed on the draft -- the link
            // preview above all -- sees it exactly as it would a typed message.
            onTextChanged(incoming)
        }
    }

    LaunchedEffect(editingMessage?.id) {
        if (editingMessage != null) {
            field = TextFieldValue(
                text = editingMessage.text,
                selection = TextRange(editingMessage.text.length)
            )
        }
    }
    val text = field.text
    val selection = field.selection
    val hasSelection = !selection.collapsed
    val activeStyles = remember(formatting, selection) {
        if (hasSelection) {
            ComposerFormatting.activeStyles(formatting, selection.min, selection.max)
        } else {
            emptySet()
        }
    }
    val colors = LocalAetherColors.current
    val hasText = text.isNotBlank()
    val composerTextAlpha by animateFloatAsState(
        targetValue = if (sendingTransition) 0f else 1f,
        animationSpec = tween(ConversationMotion.COMPOSER_TEXT_FADE_MS),
        label = "composer_send_text_fade"
    )
    val ink = Color(0xFFF2F2F5)
    val control = Color(0xFFB6B6BE)
    val hint = Color(0xFF77777F)

    val plusRotation by animateFloatAsState(
        targetValue = if (curtainState == CurtainState.ATTACHMENTS) 45f else 0f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "plus_to_cross_rotation"
    )

    // Every state below is content inside the one shared Curtain root, which owns
    // the surface, the seam-facing top edge, the bottom inset and the height.
    AetherConversationCurtain(
        state = curtainState,
        onHeightChanged = onCurtainHeightChanged,
        modifier = modifier
    ) {
        if (curtainState == CurtainState.FORWARDING) {
            ForwardCurtainContent(
                messages = forwardMessages,
                targets = forwardTargets,
                canSendCopy = forwardMessages.all { capabilities[it.id]?.canBeForwarded == true },
                // A link preview is still a text message: its text is the
                // message, not a caption that could be dropped.
                hasCaption = forwardMessages.any {
                    it.text.isNotBlank() &&
                        it.type != com.foresightlabs.aether.domain.model.MessageType.TEXT &&
                        it.type != com.foresightlabs.aether.domain.model.MessageType.LINK_PREVIEW
                },
                state = forwardState,
                onDismiss = onDismissForward,
                onForward = onSubmitForward,
                modifier = Modifier.fillMaxSize()
            )
        } else if (curtainState == CurtainState.SHARE_PREVIEW && pendingShare != null) {
            SharedContentCurtainContent(
                share = pendingShare,
                onCancel = onCancelPendingShare,
                onSend = onSendPendingShare,
                modifier = Modifier.fillMaxWidth()
            )
        } else if (curtainState == CurtainState.MEDIA_PREVIEW && pendingMedia != null) {
            MediaPreviewCurtainContent(
                media = pendingMedia,
                onToggleViewOnce = onToggleViewOnce,
                onCancel = onCancelPendingMedia,
                onSend = onSendPendingMedia,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
        // Expanded content remains inside the continuous Curtain.
        AnimatedVisibility(
            visible = curtainState.isExpanded,
            enter = expandVertically(
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
            ) + fadeIn(
                animationSpec = tween(durationMillis = 200, delayMillis = 50)
            ),
            exit = shrinkVertically(
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
            ) + fadeOut(
                animationSpec = tween(durationMillis = 150)
            )
        ) {
            AnimatedContent(
                targetState = if (curtainState == CurtainState.ATTACHMENTS) "attachments" else "picker",
                transitionSpec = {
                    fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(140))
                },
                label = "curtain_expanded_content"
            ) { expandedContent ->
                if (expandedContent == "attachments") {
                    AttachmentCurtainContent(
                        onSelectGallery = onSelectGallery,
                        onSelectCamera = onSelectCamera,
                        onSelectVideoNote = onSelectVideoNote,
                        onSelectFile = onSelectFile,
                        onSelectAudio = onSelectAudio,
                        onSelectLocation = onSelectLocation,
                        onSelectVenue = onSelectVenue,
                        onSelectContact = onSelectContact,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                } else {
                    val currentPickerTab = when (curtainState) {
                        CurtainState.STICKERS -> PickerTab.STICKERS
                        CurtainState.GIFS -> PickerTab.GIFS
                        else -> PickerTab.EMOJI
                    }
                    EmojiStickerGifPanel(
                        activeTab = currentPickerTab,
                        onTabChange = { tab ->
                            val targetMode = when (tab) {
                                PickerTab.EMOJI -> CurtainState.EMOJI
                                PickerTab.STICKERS -> CurtainState.STICKERS
                                PickerTab.GIFS -> CurtainState.GIFS
                            }
                            onCurtainStateChange(targetMode)
                        },
                        onInsertEmoji = { emoji ->
                            val start = field.selection.min.coerceIn(0, field.text.length)
                            val end = field.selection.max.coerceIn(0, field.text.length)
                            val newText = field.text.replaceRange(start, end, emoji)
                            val newCursor = start + emoji.length
                            field = field.copy(
                                text = newText,
                                selection = TextRange(newCursor)
                            )
                            onTextChanged(newText)
                        },
                        installedStickerSets = installedStickerSets,
                        recentStickers = recentStickers,
                        favoriteStickers = favoriteStickers,
                        onLoadStickerSetDetails = onLoadStickerSetDetails,
                        onSendSticker = onSendSticker,
                        savedAnimations = savedAnimations,
                        onSendAnimation = onSendAnimation,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = tween(ConversationMotion.STANDARD_MS, easing = FastOutSlowInEasing)
                )
                .testTag("message_composer")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 6.dp)
            ) {
                // Integrated Edit & Reply strip inside the same Curtain.
                AnimatedVisibility(
                    visible = editingMessage != null || replyingTo != null,
                    enter = expandVertically(tween(ConversationMotion.STANDARD_MS)) + fadeIn(tween(ConversationMotion.FAST_MS)),
                    exit = shrinkVertically(tween(ConversationMotion.STANDARD_MS)) + fadeOut(tween(ConversationMotion.FAST_MS))
                ) {
                    if (editingMessage != null) {
                        val editMsg = editingMessage
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                // Direct Curtain content: the accent bar marks it,
                                // not a second background inside the surface.
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(26.dp)
                                    .clip(CircleShape)
                                    .background(AetherAccent.current)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Editing message",
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AetherAccent.current
                                )
                                Text(
                                    text = editMsg.text.ifEmpty { "Message" },
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 11.5.sp,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        onDismissEdit()
                                        field = TextFieldValue("")
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel Edit",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    } else if (replyingTo != null) {
                        val replyMsg = replyingTo
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                // Direct Curtain content: the accent bar marks it,
                                // not a second background inside the surface.
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(26.dp)
                                    .clip(CircleShape)
                                    .background(AetherAccent.current)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (replyQuote != null) {
                                        "Quoting ${replyMsg.senderName}"
                                    } else {
                                        "Replying to ${replyMsg.senderName}"
                                    },
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AetherAccent.current
                                )
                                Text(
                                    // The quote is what the reply is about, so it is
                                    // what the preview shows.
                                    text = replyQuote?.text ?: replyMsg.text.ifEmpty { "Attachment" },
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 11.5.sp,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .clickable { onDismissReply() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel Reply",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }

                // The draft's link preview, directly above the text it belongs
                // to and inside the same Curtain content column. Never its own
                // surface; see [ComposerLinkPreviewStrip].
                AnimatedVisibility(
                    visible = linkPreview.isVisible && selectedMessages.isEmpty(),
                    enter = expandVertically(tween(ConversationMotion.STANDARD_MS)) + fadeIn(tween(ConversationMotion.FAST_MS)),
                    exit = shrinkVertically(tween(ConversationMotion.STANDARD_MS)) + fadeOut(tween(ConversationMotion.FAST_MS))
                ) {
                    ComposerLinkPreviewStrip(
                        state = linkPreview,
                        onDismiss = onDismissLinkPreview
                    )
                }

                // One row: attach, what you are writing, and the action, OR selection action dock when selecting.
                AnimatedContent(
                    targetState = selectedMessages.isNotEmpty(),
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = tween(180)))
                            .togetherWith(fadeOut(animationSpec = tween(140)) + shrinkVertically(animationSpec = tween(140)))
                    },
                    label = "composer_selection_morph"
                ) { isSelecting ->
                    if (isSelecting) {
                        MessageSelectionRow(
                            selection = selectedMessages,
                            capabilities = capabilities,
                            onClearSelection = onClearSelection,
                            onReply = onReplySelected,
                            onEdit = onEditSelected,
                            onCopy = onCopySelected,
                            onForward = onForwardSelected,
                            onPin = onPinSelected,
                            onDelete = onDeleteSelected,
                            ink = ink,
                            control = control
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = ComposerRowHeight)
                                .padding(horizontal = 2.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable(enabled = enabled) {
                                        onToggleAttachment()
                                    }
                                    .testTag("attachment_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = if (curtainState == CurtainState.ATTACHMENTS) "Close attachments" else "Attach media",
                                    tint = if (curtainState == CurtainState.ATTACHMENTS) ink else control,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .graphicsLayer { rotationZ = plusRotation }
                                )
                            }

                            // Text Input Area
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 6.dp)
                                    .align(Alignment.CenterVertically),
                                contentAlignment = Alignment.TopStart
                            ) {
                                if (text.isEmpty()) {
                                    Text(
                                        text = "Your Message…",
                                        fontFamily = ManropeFontFamily,
                                        color = hint,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Normal
                                    )
                                }

                                if (hasSelection && enabled) {
                                    AetherFormattingBar(
                                        active = activeStyles,
                                        onToggle = { style ->
                                            formatting = ComposerFormatting.toggle(
                                                formatting,
                                                style,
                                                selection.min,
                                                selection.max
                                            )
                                        }
                                    )
                                }

                                BasicTextField(
                                    value = field,
                                    onValueChange = { next ->
                                        if (curtainState.isExpanded) {
                                            onInputFocus()
                                        }
                                        if (next.text != field.text) {
                                            formatting = ComposerFormatting.sanitise(
                                                reanchorForEdit(field.text, next.text, formatting),
                                                next.text.length
                                            )
                                            onTextChanged(next.text)
                                        }
                                        field = next
                                    },
                                    visualTransformation = rememberFormattingTransformation(
                                        formatting = formatting,
                                        accent = AetherAccent.current,
                                        codeBackground = colors.textPrimary.copy(alpha = 0.10f)
                                    ),
                                    textStyle = TextStyle(
                                        color = ink,
                                        fontFamily = ManropeFontFamily,
                                        fontSize = 15.sp,
                                        lineHeight = 20.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    cursorBrush = SolidColor(AetherAccent.current),
                                    keyboardOptions = KeyboardOptions(
                                        capitalization = KeyboardCapitalization.Sentences,
                                        imeAction = ImeAction.Default
                                    ),
                                    maxLines = 6,
                                    minLines = 1,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer { alpha = composerTextAlpha }
                                        .onFocusChanged {
                                            if (it.isFocused) {
                                                onInputFocus()
                                            }
                                        }
                                        .testTag("message_input_field")
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable(enabled = enabled) { onTogglePicker() }
                                    .testTag("sticker_button")
                                    .semantics {
                                        contentDescription = if (curtainState.isPicker) "Keyboard" else "Emoji and stickers"
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (curtainState.isPicker) Icons.Filled.KeyboardAlt else Icons.Default.EmojiEmotions,
                                    contentDescription = null,
                                    tint = if (curtainState.isPicker) colors.accent else hint,
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            // Action Button (Morphing between Mic, Send & Save Edit)
                            val actionState = when {
                                editingMessage != null -> "edit"
                                hasText -> "send"
                                else -> "mic"
                            }

                            AnimatedContent(
                                targetState = actionState,
                                transitionSpec = {
                                    (fadeIn(tween(ConversationMotion.FAST_MS)) +
                                            scaleIn(tween(ConversationMotion.FAST_MS), initialScale = 0.9f))
                                        .togetherWith(
                                            fadeOut(tween(ConversationMotion.FAST_MS)) +
                                                    scaleOut(tween(ConversationMotion.FAST_MS), targetScale = 0.9f)
                                        )
                                },
                                label = "composer_action_morph"
                            ) { mode ->
                                when (mode) {
                                    "edit" -> {
                                        val editMsg = editingMessage
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(AetherAccent.actionBrush)
                                                .clickable(enabled = enabled && text.isNotBlank() && editMsg != null) {
                                                    if (editMsg != null && text.isNotBlank() && enabled) {
                                                        onSaveEdit(editMsg, text.trimEnd(), formatting)
                                                        field = TextFieldValue("")
                                                        formatting = emptyList()
                                                    }
                                                }
                                                .testTag("save_edit_button"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Save edit",
                                                tint = Color.White,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                    "send" -> {
                                        // Send Action Button
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(AetherAccent.actionBrush)
                                                .clickable(enabled = enabled) {
                                                    if (text.isNotBlank() && enabled) {
                                                        sendingTransition = true
                                                        onSendMessage(text.trimEnd(), formatting)
                                                        composerScope.launch {
                                                            delay(ConversationMotion.COMPOSER_TEXT_FADE_MS.toLong())
                                                            field = TextFieldValue("")
                                                            formatting = emptyList()
                                                            sendingTransition = false
                                                        }
                                                    }
                                                }
                                                .testTag("send_message_button"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Send,
                                                contentDescription = "Send",
                                                tint = Color.White,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                    else -> {
                                        // Voice Note / Video Note Action Button
                                        var isVideoNoteMode by remember { mutableStateOf(false) }
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(Color(0x14FFFFFF))
                                                .clickable(enabled = enabled) {
                                                    if (isVideoNoteMode) {
                                                        onOpenVideoNote()
                                                    } else {
                                                        onVoiceNoteRecorded()
                                                    }
                                                }
                                                .testTag(if (isVideoNoteMode) "video_note_button" else "voice_record_button"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isVideoNoteMode) Icons.Default.Videocam else Icons.Default.Mic,
                                                contentDescription = if (isVideoNoteMode) "Record Video Message" else "Record Voice Note",
                                                tint = control,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }
}


/**
 * Formatting actions for the current selection.
 *
 * Appears only while text is selected, because that is the only time it can do
 * anything. Aether's own pill row rather than a floating Material toolbar.
 */
@Composable
private fun AetherFormattingBar(
    active: Set<ComposerStyle>,
    onToggle: (ComposerStyle) -> Unit
) {
    val colors = LocalAetherColors.current
    AetherGlass(
        frostState = null,
        shape = AetherEmber.Shapes.Pill,
        elevation = 6.dp,
        emphasis = 0.2f,
        modifier = Modifier
            .padding(bottom = 6.dp)
            .testTag("formatting_bar")
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            ComposerStyle.entries.forEach { style ->
                val isActive = style in active
                Box(
                    modifier = Modifier
                        .clip(AetherEmber.Shapes.Pill)
                        .background(
                            if (isActive) AetherAccent.current.copy(alpha = 0.28f) else Color.Transparent
                        )
                        .clickable { onToggle(style) }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("format_${style.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = styleLabel(style),
                        fontFamily = ManropeFontFamily,
                        fontSize = 13.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        color = if (isActive) colors.textPrimary else colors.textSecondary,
                        style = styleTextStyle(style)
                    )
                }
            }
        }
    }
}

private fun styleLabel(style: ComposerStyle): String = when (style) {
    ComposerStyle.BOLD -> "B"
    ComposerStyle.ITALIC -> "I"
    ComposerStyle.UNDERLINE -> "U"
    ComposerStyle.STRIKETHROUGH -> "S"
    ComposerStyle.SPOILER -> "◍"
    ComposerStyle.CODE -> "{ }"
}

private fun styleTextStyle(style: ComposerStyle): TextStyle = when (style) {
    ComposerStyle.ITALIC -> TextStyle(fontStyle = FontStyle.Italic)
    ComposerStyle.UNDERLINE -> TextStyle(textDecoration = TextDecoration.Underline)
    ComposerStyle.STRIKETHROUGH -> TextStyle(textDecoration = TextDecoration.LineThrough)
    else -> TextStyle()
}

/**
 * Draws the composer's own formatting as the user types.
 *
 * A visual transformation rather than a rewrite of the text: the underlying string
 * stays exactly what will be sent, so offsets never diverge from what the server
 * receives.
 */
@Composable
private fun rememberFormattingTransformation(
    formatting: List<AetherEntity>,
    accent: Color,
    codeBackground: Color
): VisualTransformation = remember(formatting, accent, codeBackground) {
    VisualTransformation { original ->
        val styled = buildAnnotatedString {
            append(original.text)
            formatting.forEach { entity ->
                val start = entity.offset.coerceIn(0, original.text.length)
                val end = entity.end.coerceIn(start, original.text.length)
                if (start == end) return@forEach
                val span = when (entity) {
                    is AetherEntity.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
                    is AetherEntity.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
                    is AetherEntity.Underline ->
                        SpanStyle(textDecoration = TextDecoration.Underline)
                    is AetherEntity.Strikethrough ->
                        SpanStyle(textDecoration = TextDecoration.LineThrough)
                    is AetherEntity.Code ->
                        SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)
                    // Shown marked rather than hidden: the writer needs to see what
                    // they have covered before they send it.
                    is AetherEntity.Spoiler -> SpanStyle(background = accent.copy(alpha = 0.22f))
                    else -> SpanStyle()
                }
                addStyle(span, start, end)
            }
        }
        TransformedText(styled, OffsetMapping.Identity)
    }
}

/**
 * Works out what changed between two composer strings and re-anchors spans.
 *
 * Compose gives the new text, not an edit description, so the common prefix and
 * suffix are compared to recover one. That is exact for the single contiguous edit a
 * keyboard actually produces.
 */
private fun reanchorForEdit(
    before: String,
    after: String,
    entities: List<AetherEntity>
): List<AetherEntity> {
    var prefix = 0
    val maxPrefix = minOf(before.length, after.length)
    while (prefix < maxPrefix && before[prefix] == after[prefix]) prefix++

    var suffix = 0
    while (
        suffix < maxPrefix - prefix &&
        before[before.length - 1 - suffix] == after[after.length - 1 - suffix]
    ) suffix++

    val removed = before.length - prefix - suffix
    val inserted = after.length - prefix - suffix
    return ComposerFormatting.reanchor(entities, prefix, removed, inserted)
}

/** Resting Curtain geometry: comfortable to hit, quiet to look at. */
private val ComposerRadius = 26.dp
private val ComposerRowHeight = 52.dp

@Composable
private fun MessageSelectionRow(
    selection: List<Message>,
    capabilities: Map<String, MessageCapabilities>,
    onClearSelection: () -> Unit,
    onReply: (Message) -> Unit,
    onEdit: (Message) -> Unit,
    onCopy: (List<Message>) -> Unit,
    onForward: (List<Message>) -> Unit,
    /**
     * Toggles pin state for the one selected message. Pinning one message inside
     * a conversation ([TdApi.PinChatMessage]/[TdApi.UnpinChatMessage]) is a
     * completely different Telegram operation from pinning the chat itself in
     * Home ([TdApi.ToggleChatIsPinned]) -- this dock never touches the latter.
     */
    onPin: (Message) -> Unit,
    onDelete: (List<Message>) -> Unit,
    ink: Color,
    control: Color,
    modifier: Modifier = Modifier
) {
    val count = selection.size
    val isSingle = count == 1
    val single = selection.firstOrNull()
    val singleCaps = single?.let { capabilities[it.id] } ?: MessageCapabilities.Unknown

    val canReply = isSingle && single != null && (singleCaps.canBeReplied || singleCaps == MessageCapabilities.Unknown)
    val canEdit = isSingle && single != null && singleCaps.canBeEdited
    val canCopy = selection.any { it.text.isNotBlank() }
    val canForward = isSingle && (singleCaps.canBeForwarded || singleCaps == MessageCapabilities.Unknown) ||
            (!isSingle && selection.any { capabilities[it.id]?.canBeForwarded != false })
    // Telegram's message-pin API (PinChatMessage/UnpinChatMessage) targets one
    // message; a multi-selection is never offered this action, matching
    // MessageActionPolicy.actionsForSelection dropping PIN/UNPIN from any
    // selection wider than one. No local guess when capabilities have not
    // answered yet -- unlike Reply/Forward, a wrong assumption here means
    // exposing an action Telegram will refuse.
    val canPin = isSingle && single != null && singleCaps.canBePinned

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ComposerRowHeight)
            .padding(horizontal = 4.dp)
            .testTag("message_selection_dock"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Dismiss button (×)
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable { onClearSelection() }
                .testTag("selection_clear")
                .semantics { contentDescription = "Clear selection" },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = control,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Selection count
        Text(
            text = "$count selected",
            fontFamily = ManropeFontFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = ink,
            modifier = Modifier
                .weight(1f)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag("selection_count")
        )

        // Actions: Edit (priority when editable) or Reply
        if (canEdit && single != null) {
            SelectionDockActionButton(
                icon = Icons.Default.Edit,
                label = "Edit",
                tint = control,
                onClick = {
                    onEdit(single)
                    onClearSelection()
                },
                testTag = "selection_action_edit"
            )
            Spacer(modifier = Modifier.width(2.dp))
        } else if (canReply && single != null) {
            SelectionDockActionButton(
                icon = Icons.AutoMirrored.Filled.Reply,
                label = "Reply",
                tint = control,
                onClick = {
                    onReply(single)
                    onClearSelection()
                },
                testTag = "selection_action_reply"
            )
            Spacer(modifier = Modifier.width(2.dp))
        }

        // Copy
        if (canCopy) {
            SelectionDockActionButton(
                icon = Icons.Default.ContentCopy,
                label = "Copy",
                tint = control,
                onClick = {
                    onCopy(selection)
                    onClearSelection()
                },
                testTag = "selection_action_copy"
            )
            Spacer(modifier = Modifier.width(2.dp))
        }

        // Forward
        if (canForward) {
            SelectionDockActionButton(
                icon = Icons.AutoMirrored.Filled.Forward,
                label = "Forward",
                tint = control,
                onClick = {
                    onForward(selection)
                    onClearSelection()
                },
                testTag = "selection_action_forward"
            )
            Spacer(modifier = Modifier.width(2.dp))
        }

        // Pin / Unpin -- pins this one message inside the conversation, never
        // the chat itself (that is Home's separate ToggleChatIsPinned action).
        if (canPin && single != null) {
            SelectionDockActionButton(
                icon = Icons.Default.PushPin,
                label = if (single.isPinned) "Unpin" else "Pin",
                tint = control,
                onClick = {
                    onPin(single)
                    onClearSelection()
                },
                testTag = "selection_action_pin"
            )
            Spacer(modifier = Modifier.width(2.dp))
        }

        // Delete
        SelectionDockActionButton(
            icon = Icons.Default.Delete,
            label = "Delete",
            tint = Color(0xFFEF4444).copy(alpha = 0.9f),
            onClick = {
                onDelete(selection)
            },
            testTag = "selection_action_delete"
        )
    }
}

@Composable
private fun SelectionDockActionButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable { onClick() }
            .testTag(testTag)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(19.dp)
        )
    }
}
