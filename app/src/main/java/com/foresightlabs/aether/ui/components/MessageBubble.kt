package com.foresightlabs.aether.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.foresightlabs.aether.data.media.TgsDecompressor
import java.io.File
import com.foresightlabs.aether.ui.theme.OnlineGreen
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.foresightlabs.aether.domain.model.MediaItem
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.text.TextStyle
import com.foresightlabs.aether.domain.text.EntityAction
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.MessageStatus
import com.foresightlabs.aether.domain.model.MessageType
import com.foresightlabs.aether.domain.model.Reaction
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun MessageBubble(
    message: Message,
    onSwipeToReply: (Message) -> Unit,
    onLongPress: (Message) -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    onReactionClick: (Message, String) -> Unit,
    modifier: Modifier = Modifier,
    onEntityAction: (EntityAction) -> Unit = {},
    /** Briefly marked after a jump, so the reader can find what they landed on. */
    isHighlighted: Boolean = false,
    onPollVote: (Message, List<Int>) -> Unit = { _, _ -> },
    isSelected: Boolean = false,
    isSelectionActive: Boolean = false,
    /** Non-null only while a multi-selection is running. */
    onSelectToggle: (() -> Unit)? = null,
    onStopLiveLocation: ((Message) -> Unit)? = null,
    onRetry: ((Message) -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val colors = LocalAetherColors.current
    val offsetX = remember { Animatable(0f) }
    var reachedThreshold by remember { mutableStateOf(false) }

    val replyThreshold = -180f // Drag left to reply
    val isOutgoing = message.isOutgoing
    val contentColor = if (isOutgoing) colors.bubbleOutgoingText else colors.bubbleIncomingText
    // Both bubbles carry graphite type on a light tone, so the timestamp is the
    // same ink held back rather than a second colour.
    val metaColor = contentColor.copy(alpha = .45f)

    val bubbleShape = if (isOutgoing) {
        RoundedCornerShape(
            topStart = 15.dp,
            topEnd = 15.dp,
            bottomStart = 15.dp,
            bottomEnd = 5.dp
        )
    } else {
        RoundedCornerShape(
            topStart = 15.dp,
            topEnd = 15.dp,
            bottomStart = 5.dp,
            bottomEnd = 15.dp
        )
    }

    if (message.type == MessageType.CALL) {
        val isMissed = "Missed" in message.text || "Declined" in message.text || "Cancelled" in message.text || "Failed" in message.text
        val iconColor = if (isMissed) Color(0xFFEF4444) else OnlineGreen

        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .clip(AetherEmber.Shapes.Pill)
                    .background(Color(0x35000000))
                    .border(0.5.dp, Color(0x28FFFFFF), AetherEmber.Shapes.Pill)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = message.text,
                    fontFamily = ManropeFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xF5FFFFFF),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        return
    }

    if (message.type == MessageType.SERVICE) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .clip(AetherEmber.Shapes.Pill)
                    .background(Color(0x35000000))
                    .border(0.5.dp, Color(0x28FFFFFF), AetherEmber.Shapes.Pill)
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                Text(
                    text = message.text,
                    fontFamily = ManropeFontFamily,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xF5FFFFFF),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        return
    }

    // A jump highlight fades rather than flashing, and settles to nothing so the
    // marker cannot outlive the moment the reader needed it.
    val highlightAlpha by animateFloatAsState(
        targetValue = if (isHighlighted) 1f else 0f,
        animationSpec = tween(durationMillis = if (isHighlighted) 140 else 520),
        label = "jump_highlight"
    )

    val selectionAlpha by animateFloatAsState(
        targetValue = if (isSelectionActive && !isSelected) 0.65f else 1f,
        animationSpec = tween(durationMillis = 180),
        label = "message_selection_alpha"
    )

    val focusBorderModifier = if (isSelected) {
        val borderColor = if (isOutgoing) Color(0x40FFFFFF) else Color(0x32000000)
        Modifier.border(0.75.dp, borderColor, bubbleShape)
    } else Modifier

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 1.dp)
            .graphicsLayer {
                alpha = selectionAlpha
                if (isSelected) {
                    translationY = -1.5f
                }
            }
            .semantics {
                selected = isSelected
                stateDescription = if (isSelected) "Selected" else "Not selected"
            }
            .then(
                if (highlightAlpha > 0.01f) {
                    Modifier
                        .clip(AetherEmber.Shapes.L)
                        .background(colors.accent.copy(alpha = 0.16f * highlightAlpha))
                } else {
                    Modifier
                }
            )
    ) {
        // Reply indicator on drag
        val replyIconAlpha = (-offsetX.value / 120f).coerceIn(0f, 1f)
        val replyIconScale = (-offsetX.value / 120f).coerceIn(0.5f, 1.15f)

        if (offsetX.value < -20f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .size(36.dp)
                    .scale(replyIconScale)
                    .clip(CircleShape)
                    .background(Color(0x35FFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Reply,
                    contentDescription = "Reply",
                    tint = Color.White.copy(alpha = replyIconAlpha),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Swipable bubble container
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(message.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            coroutineScope.launch {
                                if (offsetX.value <= replyThreshold) {
                                    onSwipeToReply(message)
                                }
                                offsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                                reachedThreshold = false
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                offsetX.animateTo(0f)
                                reachedThreshold = false
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            coroutineScope.launch {
                                val newOffset = (offsetX.value + dragAmount).coerceIn(-240f, 0f)
                                offsetX.snapTo(newOffset)
                                if (newOffset <= replyThreshold && !reachedThreshold) {
                                    reachedThreshold = true
                                }
                            }
                        }
                    )
                },
            horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isOutgoing && isSelected) {
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2C2C34))
                        .border(1.dp, colors.background, CircleShape)
                        .testTag("message_check_${message.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color(0xFFF2F2F5),
                        modifier = Modifier.size(11.dp)
                    )
                }
            }

            Column(
                horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start,
                modifier = Modifier.widthIn(max = 258.dp)
            ) {
                // Main Bubble Container
                Box(
                    modifier = Modifier
                        .clip(bubbleShape)
                        .then(
                            if (isOutgoing) {
                                Modifier.background(colors.bubbleOutgoing)
                            } else {
                                Modifier.background(colors.bubbleIncoming)
                            }
                        )
                        .then(focusBorderModifier)
                        .pointerInput(message.id) {
                            detectTapGestures(
                                onTap = {
                                    if (onSelectToggle != null) {
                                        onSelectToggle()
                                    }
                                },
                                onLongPress = {
                                    onLongPress(message)
                                }
                            )
                        }
                        .then(
                            if (message.type == MessageType.IMAGE) {
                                Modifier.padding(4.dp)
                            } else {
                                Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                            }
                        )
                        .testTag("message_bubble_${message.id}")
                ) {
                    Column {
                        // Forwarded header if present
                        if (message.forwardedFrom != null) {
                            Text(
                                text = "Forwarded from ${message.forwardedFrom}",
                                fontFamily = ManropeFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isOutgoing) contentColor.copy(alpha = .86f) else colors.accent,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }

                        // Reply preview quote snippet
                        if (message.replyToMessage != null) {
                            ReplySnippet(
                                replyMessage = message.replyToMessage,
                                isOutgoingParent = isOutgoing
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        // Message Content by Type
                        when (message.type) {
                            MessageType.VOICE -> {
                                VoiceMessagePlayer(
                                    durationSec = message.voiceDurationSec,
                                    waveform = message.voiceWaveform,
                                    isOutgoing = isOutgoing
                                )
                            }
                            MessageType.AUDIO -> {
                                AudioAttachmentContent(
                                    title = message.fileName ?: message.text.ifBlank { "Audio" },
                                    fileSize = message.fileSize ?: "",
                                    durationSec = message.voiceDurationSec,
                                    isOutgoing = isOutgoing
                                )
                            }
                            MessageType.FILE -> {
                                FileAttachmentContent(
                                    fileName = message.fileName ?: "Document",
                                    fileSize = message.fileSize ?: "",
                                    fileExtension = message.fileExtension ?: "FILE",
                                    isOutgoing = isOutgoing
                                )
                            }
                            MessageType.IMAGE -> {
                                val firstMedia = message.mediaItems.firstOrNull()
                                if (firstMedia != null) {
                                    ImageAttachmentContent(
                                        media = firstMedia,
                                        caption = message.text,
                                        onMediaClick = onMediaClick,
                                        isOutgoing = isOutgoing
                                    )
                                }
                            }
                            MessageType.STICKER -> {
                                val stickerMedia = message.mediaItems.firstOrNull()
                                StickerContent(
                                    media = stickerMedia,
                                    emoji = message.text,
                                    stickerFormat = message.stickerFormat,
                                    onMediaClick = onMediaClick,
                                    isOutgoing = isOutgoing
                                )
                            }
                            MessageType.VIDEO_NOTE -> {
                                val videoMedia = message.mediaItems.firstOrNull()
                                VideoNoteContent(
                                    media = videoMedia,
                                    durationSec = message.voiceDurationSec,
                                    isOutgoing = isOutgoing,
                                    onMediaClick = onMediaClick
                                )
                            }
                            MessageType.ANIMATION -> {
                                val animMedia = message.mediaItems.firstOrNull()
                                AnimationAttachmentContent(
                                    media = animMedia,
                                    caption = message.text,
                                    durationSec = message.voiceDurationSec,
                                    onMediaClick = onMediaClick,
                                    isOutgoing = isOutgoing
                                )
                            }
                            MessageType.CONTACT -> {
                                ContactAttachmentContent(
                                    name = message.text,
                                    phone = message.fileName.orEmpty(),
                                    isOutgoing = isOutgoing
                                )
                            }
                            MessageType.LOCATION -> {
                                LocationAttachmentContent(
                                    label = message.text,
                                    coordinates = message.fileName.orEmpty(),
                                    isLive = message.isLiveLocation,
                                    expiresIn = message.liveLocationExpiresIn,
                                    isOutgoing = isOutgoing,
                                    onStopLive = { onStopLiveLocation?.invoke(message) }
                                )
                            }
                            MessageType.VENUE -> {
                                VenueAttachmentContent(
                                    title = message.venueTitle ?: message.text,
                                    address = message.venueAddress ?: message.fileName.orEmpty(),
                                    isOutgoing = isOutgoing
                                )
                            }
                            MessageType.POLL -> {
                                message.poll?.let { poll ->
                                    PollBubble(
                                        poll = poll,
                                        contentColor = contentColor,
                                        metaColor = metaColor,
                                        onVote = { options -> onPollVote(message, options) }
                                    )
                                }
                            }
                            MessageType.LINK_PREVIEW -> {
                                Text(
                                    text = message.text,
                                    fontFamily = ManropeFontFamily,
                                    color = contentColor,
                                    fontSize = 13.5.sp,
                                    lineHeight = 18.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                message.linkPreview?.let { preview ->
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LinkPreviewCard(preview = preview, isOutgoing = isOutgoing)
                                }
                            }
                            MessageType.UNSUPPORTED -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = metaColor,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = message.text.ifBlank { "Unsupported message" },
                                        fontFamily = ManropeFontFamily,
                                        color = contentColor.copy(alpha = 0.85f),
                                        fontSize = 13.sp,
                                        lineHeight = 17.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            else -> {
                                AetherRichText(
                                    value = message.richText,
                                    style = TextStyle(
                                        fontFamily = ManropeFontFamily,
                                        fontSize = 13.5.sp,
                                        lineHeight = 18.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = contentColor,
                                    accentColor = colors.accent,
                                    spoilerCover = metaColor.copy(alpha = 0.55f),
                                    codeBackground = contentColor.copy(alpha = 0.10f),
                                    onAction = onEntityAction
                                )
                            }
                        }

                        // Message Metadata (Timestamp, Edited, Read status)
                        if (message.type != MessageType.IMAGE) {
                            // The stamp tucks up against the last line rather than
                            // sitting on a row of its own, which is what keeps a
                            // one-word message a one-word bubble.
                            Spacer(modifier = Modifier.height(1.dp))
                            Row(
                                modifier = Modifier.align(Alignment.End),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (message.isEdited) {
                                    Text(
                                        text = "edited",
                                        fontFamily = ManropeFontFamily,
                                        fontSize = 10.5.sp,
                                        color = metaColor,
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                }
                                Text(
                                    text = message.timestamp,
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = metaColor
                                )

                                if (isOutgoing) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    when (message.status) {
                                        MessageStatus.SENDING -> {
                                            Icon(
                                                imageVector = Icons.Default.Schedule,
                                                contentDescription = "Sending",
                                                tint = metaColor,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                        MessageStatus.SENT -> {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Sent",
                                                tint = metaColor,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                        MessageStatus.READ -> {
                                            Icon(
                                                imageVector = Icons.Default.DoneAll,
                                                contentDescription = "Read",
                                                tint = colors.accent,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        MessageStatus.FAILED -> {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .clip(AetherEmber.Shapes.Pill)
                                                    .clickable(enabled = onRetry != null) {
                                                        onRetry?.invoke(message)
                                                    }
                                                    .padding(horizontal = 2.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Schedule,
                                                    contentDescription = "Failed, tap to retry",
                                                    tint = Color(0xFFEF4444),
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Reactions Row
                if (message.reactions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        modifier = Modifier.padding(
                            start = if (isOutgoing) 0.dp else 4.dp,
                            end = if (isOutgoing) 4.dp else 0.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        message.reactions.forEach { reaction ->
                            ReactionBadge(
                                reaction = reaction,
                                isOutgoing = isOutgoing,
                                onClick = { onReactionClick(message, reaction.emoji) }
                            )
                        }
                    }
                }
            }

            if (isOutgoing && isSelected) {
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2C2C34))
                        .border(1.dp, colors.background, CircleShape)
                        .testTag("message_check_${message.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color(0xFFF2F2F5),
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ReplySnippet(
    replyMessage: Message,
    isOutgoingParent: Boolean
) {
    val colors = LocalAetherColors.current
    val contentColor = if (isOutgoingParent) colors.bubbleOutgoingText else colors.bubbleIncomingText
    val barColor = if (isOutgoingParent) contentColor else colors.accent

    Row(
        modifier = Modifier
            .widthIn(max = 240.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isOutgoingParent) contentColor.copy(alpha = 0.15f) else colors.surfaceHighlight)
            .padding(vertical = 4.dp, horizontal = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(28.dp)
                .clip(CircleShape)
                .background(barColor)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column {
            Text(
                text = replyMessage.senderName,
                fontFamily = ManropeFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = barColor
            )
            Text(
                text = replyMessage.text.ifEmpty { "Attachment" },
                fontFamily = ManropeFontFamily,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = contentColor.copy(alpha = 0.84f)
            )
        }
    }
}

@Composable
private fun FileAttachmentContent(
    fileName: String,
    fileSize: String,
    fileExtension: String,
    isOutgoing: Boolean
) {
    val colors = LocalAetherColors.current
    val contentColor = if (isOutgoing) colors.bubbleOutgoingText else colors.bubbleIncomingText

    Row(
        modifier = Modifier
            .widthIn(max = 240.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isOutgoing) contentColor.copy(alpha = 0.15f) else colors.surfaceHighlight)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(if (isOutgoing) contentColor.copy(alpha = 0.25f) else colors.accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = "File",
                tint = if (isOutgoing) contentColor else colors.surface,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = fileName,
                fontFamily = ManropeFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = contentColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$fileSize • $fileExtension",
                fontFamily = ManropeFontFamily,
                fontSize = 11.sp,
                color = contentColor.copy(alpha = 0.72f)
            )
        }
    }
}

@Composable
private fun ImageAttachmentContent(
    media: MediaItem,
    caption: String,
    onMediaClick: (MediaItem) -> Unit,
    isOutgoing: Boolean
) {
    val colors = LocalAetherColors.current
    val contentColor = if (isOutgoing) colors.bubbleOutgoingText else colors.bubbleIncomingText

    Column(
        modifier = Modifier
            .width(230.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onMediaClick(media) }
    ) {
        Box(
            modifier = Modifier
                .width(230.dp)
                .height(170.dp)
                .clip(RoundedCornerShape(16.dp))
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(media.url)
                    .crossfade(true)
                    .build(),
                contentDescription = caption.ifEmpty { "Photo" },
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(230.dp).height(170.dp),
                loading = {
                    Box(
                        modifier = Modifier
                            .width(230.dp)
                            .height(170.dp)
                            .background(colors.surfaceHighlight),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = colors.accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            )
        }

        if (caption.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = caption,
                fontFamily = ManropeFontFamily,
                color = contentColor,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun LinkPreviewCard(
    preview: com.foresightlabs.aether.domain.model.LinkPreview,
    isOutgoing: Boolean
) {
    val colors = LocalAetherColors.current
    val contentColor = if (isOutgoing) colors.bubbleOutgoingText else colors.bubbleIncomingText

    Column(
        modifier = Modifier
            .widthIn(max = 250.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isOutgoing) contentColor.copy(alpha = 0.15f) else colors.surfaceHighlight)
            .padding(8.dp)
    ) {
        preview.thumbnailUrl?.let { thumb ->
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(thumb)
                    .crossfade(true)
                    .build(),
                contentDescription = preview.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        Text(
            text = preview.siteName.uppercase(),
            fontFamily = ManropeFontFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (isOutgoing) contentColor.copy(alpha = 0.85f) else colors.accent
        )

        Text(
            text = preview.title,
            fontFamily = ManropeFontFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = preview.description,
            fontFamily = ManropeFontFamily,
            fontSize = 12.sp,
            color = contentColor.copy(alpha = 0.72f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ReactionBadge(
    reaction: Reaction,
    isOutgoing: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalAetherColors.current
    val contentColor = if (isOutgoing) colors.bubbleOutgoingText else colors.bubbleIncomingText
    val bg = if (reaction.userReacted) colors.accent else colors.bubbleIncoming

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = reaction.emoji, fontSize = 12.sp)
        if (reaction.count > 1) {
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = reaction.count.toString(),
                fontFamily = ManropeFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (reaction.userReacted) colors.surface else contentColor
            )
        }
    }
}

@Composable
private fun AudioAttachmentContent(
    title: String,
    fileSize: String,
    durationSec: Int,
    isOutgoing: Boolean
) {
    val colors = LocalAetherColors.current
    val contentColor = if (isOutgoing) colors.bubbleOutgoingText else colors.bubbleIncomingText

    Row(
        modifier = Modifier
            .widthIn(max = 240.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isOutgoing) contentColor.copy(alpha = 0.20f) else colors.accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Headphones,
                contentDescription = "Audio",
                tint = if (isOutgoing) contentColor else colors.surface,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = title,
                fontFamily = ManropeFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = contentColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            val durationLabel = if (durationSec > 0) formatDuration(durationSec) else ""
            val meta = listOfNotNull(durationLabel.takeIf { it.isNotBlank() }, fileSize.takeIf { it.isNotBlank() }).joinToString(" • ")
            Text(
                text = meta.ifBlank { "Audio" },
                fontFamily = ManropeFontFamily,
                fontSize = 11.sp,
                color = contentColor.copy(alpha = 0.72f)
            )
        }
    }
}

@Composable
private fun StickerContent(
    media: MediaItem?,
    emoji: String,
    stickerFormat: String?,
    onMediaClick: (MediaItem) -> Unit,
    isOutgoing: Boolean
) {
    val path = media?.url
    if (!path.isNullOrBlank()) {
        val isTgs = stickerFormat.equals("tgs", ignoreCase = true) || path.endsWith(".tgs", ignoreCase = true)
        val isWebm = stickerFormat.equals("webm", ignoreCase = true) || path.endsWith(".webm", ignoreCase = true)

        if (isTgs) {
            val file = remember(path) { File(path) }
            val lottieJson = remember(path) {
                TgsDecompressor.decompressFile(file)
            }
            if (lottieJson != null) {
                val composition by rememberLottieComposition(LottieCompositionSpec.JsonString(lottieJson))
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .clickable { onMediaClick(media) },
                    contentAlignment = Alignment.Center
                ) {
                    LottieAnimation(
                        composition = composition,
                        iterations = LottieConstants.IterateForever,
                        modifier = Modifier.size(150.dp)
                    )
                }
                return
            }
        }

        if (isWebm) {
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clickable { onMediaClick(media) },
                contentAlignment = Alignment.Center
            ) {
                LoopingVideoSticker(
                    filePath = path,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
            return
        }

        Box(
            modifier = Modifier
                .size(150.dp)
                .clickable { onMediaClick(media) },
            contentAlignment = Alignment.Center
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(path)
                    .crossfade(true)
                    .build(),
                contentDescription = emoji.ifBlank { "Sticker" },
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = emoji.ifBlank { "🎨" }, fontSize = 42.sp)
                    }
                }
            )
        }
    } else {
        Text(
            text = emoji.ifBlank { "🎨" },
            fontSize = 48.sp,
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Composable
private fun VideoNoteContent(
    media: MediaItem?,
    durationSec: Int,
    isOutgoing: Boolean,
    onMediaClick: (MediaItem) -> Unit
) {
    val colors = LocalAetherColors.current

    Box(
        modifier = Modifier
            .size(180.dp)
            .clip(CircleShape)
            .border(2.dp, colors.accent.copy(alpha = 0.6f), CircleShape)
            .background(colors.surfaceElevated),
        contentAlignment = Alignment.Center
    ) {
        if (media?.url?.isNotBlank() == true) {
            VideoNotePlayer(
                filePath = media.url,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = colors.accent,
                strokeWidth = 2.dp
            )
        }

        if (durationSec > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .clip(AetherEmber.Shapes.Pill)
                    .background(Color(0x80000000))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = formatDuration(durationSec),
                    fontFamily = ManropeFontFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun AnimationAttachmentContent(
    media: MediaItem?,
    caption: String,
    durationSec: Int,
    onMediaClick: (MediaItem) -> Unit,
    isOutgoing: Boolean
) {
    val colors = LocalAetherColors.current
    val contentColor = if (isOutgoing) colors.bubbleOutgoingText else colors.bubbleIncomingText

    Column(
        modifier = Modifier
            .width(230.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(if (media != null) Modifier.clickable { onMediaClick(media) } else Modifier)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            val url = media?.url
            if (!url.isNullOrBlank()) {
                val isVideoFormat = url.endsWith(".mp4", ignoreCase = true) || url.endsWith(".webm", ignoreCase = true)
                if (isVideoFormat) {
                    LoopingVideoSticker(
                        filePath = url,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(url)
                            .crossfade(true)
                            .build(),
                        contentDescription = "GIF",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = colors.accent,
                    strokeWidth = 2.dp
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(AetherEmber.Shapes.Pill)
                    .background(Color(0x80000000))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "GIF" + if (durationSec > 0) " · ${formatDuration(durationSec)}" else "",
                    fontFamily = ManropeFontFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        if (caption.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = caption,
                fontFamily = ManropeFontFamily,
                color = contentColor,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun ContactAttachmentContent(
    name: String,
    phone: String,
    isOutgoing: Boolean
) {
    val colors = LocalAetherColors.current
    val contentColor = if (isOutgoing) colors.bubbleOutgoingText else colors.bubbleIncomingText

    Row(
        modifier = Modifier
            .widthIn(max = 240.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isOutgoing) contentColor.copy(alpha = 0.20f) else colors.accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Contact",
                tint = if (isOutgoing) contentColor else colors.surface,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = name.ifBlank { "Contact" },
                fontFamily = ManropeFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = contentColor
            )
            if (phone.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = phone,
                    fontFamily = ManropeFontFamily,
                    fontSize = 12.sp,
                    color = contentColor.copy(alpha = 0.72f)
                )
            }
        }
    }
}

@Composable
private fun LocationAttachmentContent(
    label: String,
    coordinates: String,
    isLive: Boolean,
    expiresIn: Int,
    isOutgoing: Boolean,
    onStopLive: () -> Unit = {}
) {
    val colors = LocalAetherColors.current
    val contentColor = if (isOutgoing) colors.bubbleOutgoingText else colors.bubbleIncomingText

    Column(
        modifier = Modifier
            .widthIn(max = 260.dp)
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isLive) OnlineGreen.copy(alpha = 0.25f) else (if (isOutgoing) contentColor.copy(alpha = 0.20f) else colors.accent)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isLive) Icons.Default.NearMe else Icons.Default.LocationOn,
                    contentDescription = if (isLive) "Live Location" else "Location",
                    tint = if (isLive) OnlineGreen else (if (isOutgoing) contentColor else colors.surface),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = if (isLive) "Live Location" else label.ifBlank { "Location" },
                    fontFamily = ManropeFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = contentColor
                )
                if (isLive && expiresIn > 0) {
                    val mins = expiresIn / 60
                    val timeLabel = if (mins > 0) "$mins min left" else "$expiresIn sec left"
                    Text(
                        text = "Active · $timeLabel",
                        fontFamily = ManropeFontFamily,
                        fontSize = 11.sp,
                        color = OnlineGreen,
                        fontWeight = FontWeight.Medium
                    )
                } else if (coordinates.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = coordinates,
                        fontFamily = ManropeFontFamily,
                        fontSize = 11.sp,
                        color = contentColor.copy(alpha = 0.72f)
                    )
                }
            }
        }

        if (isLive && isOutgoing) {
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AetherEmber.Shapes.Pill)
                    .background(Color(0xFFEF4444).copy(alpha = 0.18f))
                    .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f), AetherEmber.Shapes.Pill)
                    .clickable { onStopLive() }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Stop Sharing",
                    fontFamily = ManropeFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFEF4444)
                )
            }
        }
    }
}

@Composable
private fun VenueAttachmentContent(
    title: String,
    address: String,
    isOutgoing: Boolean
) {
    val colors = LocalAetherColors.current
    val contentColor = if (isOutgoing) colors.bubbleOutgoingText else colors.bubbleIncomingText

    Row(
        modifier = Modifier
            .widthIn(max = 260.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isOutgoing) contentColor.copy(alpha = 0.20f) else colors.accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Place,
                contentDescription = "Venue",
                tint = if (isOutgoing) contentColor else colors.surface,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = title.ifBlank { "Venue" },
                fontFamily = ManropeFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = contentColor
            )
            if (address.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = address,
                    fontFamily = ManropeFontFamily,
                    fontSize = 12.sp,
                    color = contentColor.copy(alpha = 0.72f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatDuration(seconds: Int): String =
    "%d:%02d".format(seconds / 60, seconds % 60)
