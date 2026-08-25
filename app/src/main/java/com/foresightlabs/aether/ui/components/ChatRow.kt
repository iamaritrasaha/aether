package com.foresightlabs.aether.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ChatType
import com.foresightlabs.aether.domain.model.MessageStatus
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import com.foresightlabs.aether.ui.theme.VerifiedBadge

@Composable
fun ChatRow(
    chat: Chat,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 11.dp)
            .testTag("chat_row_${chat.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar with optional Pinned Star Badge overlay
        Box(
            modifier = Modifier.size(50.dp),
            contentAlignment = Alignment.Center
        ) {
            AetherAvatar(
                initials = chat.avatarInitials,
                gradient = chat.avatarGradient,
                size = 50.dp,
                isOnline = chat.directUser?.isOnline ?: false,
                chatType = chat.type,
                photoPath = chat.photoPath
            )

            if (chat.isPinned) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 2.dp, y = 2.dp)
                        .size(17.dp)
                        .clip(CircleShape)
                        .background(AetherEmber.Colors.Accent)
                        .border(1.5.dp, AetherEmber.Colors.Background, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Pinned",
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Text & Metadata
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // Title & Timestamp Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = chat.title,
                        fontFamily = ManropeFontFamily,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AetherEmber.Colors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (chat.isVerified) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Verified",
                            tint = VerifiedBadge,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    if (chat.isMuted) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.NotificationsOff,
                            contentDescription = "Muted",
                            tint = AetherEmber.Colors.TextTertiary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    if (chat.isLastMessageOutgoing) {
                        when (chat.lastMessageStatus) {
                            MessageStatus.READ -> {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Read",
                                    tint = AetherEmber.Colors.Accent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                            }
                            MessageStatus.SENT -> {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Sent",
                                    tint = AetherEmber.Colors.TextTertiary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                            }
                            else -> {}
                        }
                    }

                    Text(
                        text = chat.lastMessageTime,
                        fontFamily = ManropeFontFamily,
                        fontSize = 11.5.sp,
                        color = if (chat.unreadCount > 0) AetherEmber.Colors.Accent else AetherEmber.Colors.TextTertiary,
                        fontWeight = if (chat.unreadCount > 0) FontWeight.SemiBold else FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Subtitle & Unread Badge Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Subtitle / Typing / Draft
                Box(modifier = Modifier.weight(1f)) {
                    when {
                        chat.isTyping -> {
                            TypingIndicator(typingText = chat.typingText ?: "typing...")
                        }
                        chat.draftText != null -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Draft: ",
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFEF4444)
                                )
                                Text(
                                    text = chat.draftText,
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 13.5.sp,
                                    color = AetherEmber.Colors.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        chat.type == ChatType.GROUP && chat.lastMessageText.contains(":") -> {
                            val parts = chat.lastMessageText.split(":", limit = 2)
                            val senderName = parts[0]
                            val msgBody = if (parts.size > 1) parts[1] else ""

                            val annotatedString = buildAnnotatedString {
                                withStyle(
                                    SpanStyle(
                                        color = AetherEmber.Colors.Accent,
                                        fontFamily = ManropeFontFamily,
                                        fontWeight = FontWeight.Medium
                                    )
                                ) {
                                    append("$senderName:")
                                }
                                withStyle(
                                    SpanStyle(
                                        color = AetherEmber.Colors.TextSecondary,
                                        fontFamily = ManropeFontFamily
                                    )
                                ) {
                                    append(msgBody)
                                }
                            }
                            Text(
                                text = annotatedString,
                                fontSize = 13.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        else -> {
                            Text(
                                text = chat.lastMessageText,
                                fontFamily = ManropeFontFamily,
                                fontSize = 13.5.sp,
                                color = AetherEmber.Colors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Unread Badge
                if (chat.unreadCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .height(20.dp)
                            .clip(AetherEmber.Shapes.Pill)
                            .background(AetherEmber.Colors.Accent)
                            .padding(horizontal = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString(),
                            fontFamily = ManropeFontFamily,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingIndicator(typingText: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing_dots")

    val dot1Scale by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 0),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )

    val dot2Scale by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )

    val dot3Scale by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(end = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .scale(dot1Scale)
                    .clip(CircleShape)
                    .background(AetherEmber.Colors.Accent)
            )
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .scale(dot2Scale)
                    .clip(CircleShape)
                    .background(AetherEmber.Colors.Accent)
            )
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .scale(dot3Scale)
                    .clip(CircleShape)
                    .background(AetherEmber.Colors.Accent)
            )
        }

        Text(
            text = typingText,
            fontFamily = ManropeFontFamily,
            fontSize = 13.5.sp,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Medium,
            color = AetherEmber.Colors.Accent
        )
    }
}
