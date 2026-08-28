package com.foresightlabs.aether.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import com.foresightlabs.aether.domain.text.AetherEntity
import com.foresightlabs.aether.domain.text.ComposerFormatting
import com.foresightlabs.aether.domain.text.ComposerStyle
import com.foresightlabs.aether.domain.text.ReplyQuote
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.KeyboardAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import com.foresightlabs.aether.domain.messages.MessageCapabilities
import com.foresightlabs.aether.domain.model.AnimationItem
import com.foresightlabs.aether.domain.model.StickerItem
import com.foresightlabs.aether.domain.model.StickerSetInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.ripple
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.ui.design.AetherAccent
import com.foresightlabs.aether.ui.design.AetherGlass
import com.foresightlabs.aether.ui.design.AetherIconButton
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import com.foresightlabs.aether.ui.theme.LocalAetherColors

private data class AttachmentOptionItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
fun AttachmentOptionsGrid(
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
            .padding(horizontal = 4.dp, vertical = 6.dp),
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
 * Unified state model for the continuous bottom composer dock.
 */
enum class ComposerDockMode {
    COLLAPSED,
    ATTACHMENTS,
    EMOJI,
    STICKERS,
    GIFS;

    val isExpanded: Boolean get() = this != COLLAPSED
    val isPicker: Boolean get() = this == EMOJI || this == STICKERS || this == GIFS
}

/**
 * The message input, which lives inside the screen's footer region rather than
 * floating over the conversation.
 *
 * The bottom composer dock is a continuous rear surface with multiple modes:
 * - COLLAPSED: [ + ] [ 🙂 ] Your Message... [ Mic/Send ]
 * - ATTACHMENTS: reveals the clean attachment options grid.
 * - EMOJI / STICKERS / GIFS: reveals the unified emoji/sticker/GIF picker.
 */
@Composable
fun MessageComposer(
    replyingTo: Message?,
    onDismissReply: () -> Unit,
    onSendMessage: (String, List<AetherEntity>) -> Unit,
    /** Quoted excerpt shown above the composer when replying to part of a message. */
    replyQuote: ReplyQuote? = null,
    dockMode: ComposerDockMode = ComposerDockMode.COLLAPSED,
    onDockModeChange: (ComposerDockMode) -> Unit = {},
    isAttachmentExpanded: Boolean = dockMode == ComposerDockMode.ATTACHMENTS,
    onToggleAttachment: () -> Unit = {
        if (dockMode == ComposerDockMode.ATTACHMENTS) {
            onDockModeChange(ComposerDockMode.COLLAPSED)
        } else {
            onDockModeChange(ComposerDockMode.ATTACHMENTS)
        }
    },
    onTogglePicker: () -> Unit = {
        if (dockMode.isPicker) {
            onDockModeChange(ComposerDockMode.COLLAPSED)
        } else {
            onDockModeChange(ComposerDockMode.EMOJI)
        }
    },
    onOpenAttachmentSheet: () -> Unit = onToggleAttachment,
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
    onOpenStickerPicker: () -> Unit = onTogglePicker,
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
    onDeleteSelected: (List<Message>) -> Unit = {},
    enabled: Boolean = true,
    onTextChanged: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Held as a TextFieldValue so the selection is available for formatting.
    var field by remember { mutableStateOf(TextFieldValue("")) }
    var formatting by remember { mutableStateOf<List<AetherEntity>>(emptyList()) }
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
    val fieldShape = RoundedCornerShape(ComposerRadius)
    // A recessed control: a shade off the dock behind it, and nothing else.
    val fieldFill = Color(0xFF17171C)
    val ink = Color(0xFFF2F2F5)
    val control = Color(0xFFB6B6BE)
    val hint = Color(0xFF77777F)

    val effectiveDockMode = if (dockMode != ComposerDockMode.COLLAPSED) {
        dockMode
    } else if (isAttachmentExpanded) {
        ComposerDockMode.ATTACHMENTS
    } else {
        ComposerDockMode.COLLAPSED
    }

    val plusRotation by animateFloatAsState(
        targetValue = if (effectiveDockMode == ComposerDockMode.ATTACHMENTS) 45f else 0f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "plus_to_cross_rotation"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("message_composer")
            // The footer owns the gesture inset; the input sits just above it.
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp)
    ) {
        // Expanded Content inside the continuous dark composer dock
        AnimatedVisibility(
            visible = effectiveDockMode.isExpanded,
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
                targetState = if (effectiveDockMode == ComposerDockMode.ATTACHMENTS) "attachments" else "picker",
                transitionSpec = {
                    fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(140))
                },
                label = "dock_expanded_mode_content"
            ) { expandedSurface ->
                if (expandedSurface == "attachments") {
                    AttachmentOptionsGrid(
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
                    val currentPickerTab = when (effectiveDockMode) {
                        ComposerDockMode.STICKERS -> PickerTab.STICKERS
                        ComposerDockMode.GIFS -> PickerTab.GIFS
                        else -> PickerTab.EMOJI
                    }
                    EmojiStickerGifPanel(
                        activeTab = currentPickerTab,
                        onTabChange = { tab ->
                            val targetMode = when (tab) {
                                PickerTab.EMOJI -> ComposerDockMode.EMOJI
                                PickerTab.STICKERS -> ComposerDockMode.STICKERS
                                PickerTab.GIFS -> ComposerDockMode.GIFS
                            }
                            onDockModeChange(targetMode)
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
                .clip(fieldShape)
                .background(fieldFill)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                // Integrated Reply Strip (Inside the floating pill dock)
                AnimatedVisibility(
                    visible = replyingTo != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    replyingTo?.let { replyMsg ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0x1AFFFFFF))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
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
                            onDelete = onDeleteSelected,
                            ink = ink,
                            control = control
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(ComposerRowHeight)
                                .padding(horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
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
                                    contentDescription = if (effectiveDockMode == ComposerDockMode.ATTACHMENTS) "Close attachments" else "Attach media",
                                    tint = if (effectiveDockMode == ComposerDockMode.ATTACHMENTS) ink else control,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .graphicsLayer { rotationZ = plusRotation }
                                )
                            }

                            // Text Input Area
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 6.dp),
                                contentAlignment = Alignment.CenterStart
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
                                        if (effectiveDockMode.isExpanded) {
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
                                    maxLines = 5,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged {
                                            if (it.isFocused && effectiveDockMode.isExpanded) {
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
                                        contentDescription = if (effectiveDockMode.isPicker) "Keyboard" else "Emoji and stickers"
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (effectiveDockMode.isPicker) Icons.Filled.KeyboardAlt else Icons.Default.EmojiEmotions,
                                    contentDescription = null,
                                    tint = if (effectiveDockMode.isPicker) colors.accent else hint,
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            // Action Button (Morphing between Mic & Send)
                            AnimatedContent(
                                targetState = hasText,
                                transitionSpec = {
                                    (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                                            expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMedium)))
                                        .togetherWith(
                                            fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                                                    shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMedium))
                                        )
                                },
                                label = "composer_action_morph"
                            ) { isTextPresent ->
                                if (isTextPresent) {
                                    // Send Action Button
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(AetherAccent.actionBrush)
                                            .clickable(enabled = enabled) {
                                                if (text.isNotBlank() && enabled) {
                                                    onSendMessage(text.trimEnd(), formatting)
                                                    field = TextFieldValue("")
                                                    formatting = emptyList()
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
                                } else {
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

/** The composer is one recessed field: comfortable to hit, quiet to look at. */
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
