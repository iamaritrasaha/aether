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
import androidx.compose.material.icons.filled.Check
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
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.MessageStatus
import com.foresightlabs.aether.domain.model.MessageType
import com.foresightlabs.aether.domain.model.Reaction
import com.foresightlabs.aether.ui.design.AetherAccent
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
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val colors = LocalAetherColors.current
    val offsetX = remember { Animatable(0f) }
    var reachedThreshold by remember { mutableStateOf(false) }

    val replyThreshold = -180f // Drag left to reply
    val isOutgoing = message.isOutgoing
    val contentColor = if (isOutgoing) colors.bubbleOutgoingText else colors.bubbleIncomingText
    val metaColor = if (isOutgoing) contentColor.copy(alpha = .78f) else colors.textTertiary

    val bubbleShape = if (isOutgoing) {
        RoundedCornerShape(
            topStart = 20.dp,
            topEnd = 20.dp,
            bottomStart = 20.dp,
            bottomEnd = 6.dp
        )
    } else {
        RoundedCornerShape(
            topStart = 20.dp,
            topEnd = 20.dp,
            bottomStart = 6.dp,
            bottomEnd = 20.dp
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 2.5.dp)
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
            horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start
        ) {
            Column(
                horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start,
                modifier = Modifier.widthIn(max = 310.dp)
            ) {
                // Main Bubble Container
                Box(
                    modifier = Modifier
                        .clip(bubbleShape)
                        .then(
                            if (isOutgoing) {
                                Modifier.background(Brush.linearGradient(listOf(colors.bubbleOutgoing, colors.bubbleOutgoingEnd)))
                            } else {
                                Modifier
                                    .background(colors.bubbleIncoming)
                                    .border(1.dp, colors.border, bubbleShape)
                            }
                        )
                        .pointerInput(message.id) {
                            detectTapGestures(
                                onLongPress = {
                                    onLongPress(message)
                                }
                            )
                        }
                        .padding(
                            if (message.type == MessageType.IMAGE) 4.dp else 12.dp
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
                            MessageType.LINK_PREVIEW -> {
                                Text(
                                    text = message.text,
                                    fontFamily = ManropeFontFamily,
                                    color = contentColor,
                                    fontSize = 15.sp,
                                    lineHeight = 20.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                message.linkPreview?.let { preview ->
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LinkPreviewCard(preview = preview, isOutgoing = isOutgoing)
                                }
                            }
                            else -> {
                                Text(
                                    text = message.text,
                                    fontFamily = ManropeFontFamily,
                                    color = contentColor,
                                    fontSize = 15.sp,
                                    lineHeight = 20.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Message Metadata (Timestamp, Edited, Read status)
                        if (message.type != MessageType.IMAGE) {
                            Spacer(modifier = Modifier.height(4.dp))
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
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = metaColor
                                )

                                if (isOutgoing) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    when (message.status) {
                                        MessageStatus.SENDING -> {
                                            Icon(
                                                imageVector = Icons.Default.Schedule,
                                                contentDescription = "Sending",
                                                tint = Color(0xCCFFFFFF),
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                        MessageStatus.SENT -> {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Sent",
                                                tint = Color(0xCCFFFFFF),
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                        MessageStatus.READ -> {
                                            Icon(
                                                imageVector = Icons.Default.DoneAll,
                                                contentDescription = "Read",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        MessageStatus.FAILED -> {
                                            Icon(
                                                imageVector = Icons.Default.Schedule,
                                                contentDescription = "Failed",
                                                tint = Color(0xFFFFD1D1),
                                                modifier = Modifier.size(12.dp)
                                            )
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
            .fillMaxWidth()
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
            .fillMaxWidth()
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

        Column(modifier = Modifier.weight(1f)) {
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
            .clip(RoundedCornerShape(16.dp))
            .clickable { onMediaClick(media) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(16.dp))
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(media.url)
                    .crossfade(true)
                    .build(),
                contentDescription = caption.ifEmpty { "Photo" },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(200.dp),
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
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
            .fillMaxWidth()
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
    val bg = if (reaction.userReacted) colors.accent else colors.surfaceElevated
    val borderColor = if (reaction.userReacted) colors.accent else colors.border

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, borderColor, CircleShape)
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
