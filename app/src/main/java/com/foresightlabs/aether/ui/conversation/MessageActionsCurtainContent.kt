package com.foresightlabs.aether.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foresightlabs.aether.domain.messages.MessageAction
import com.foresightlabs.aether.domain.messages.MessageActionPolicy
import com.foresightlabs.aether.domain.messages.MessageCapabilities
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.ManropeFontFamily

/**
 * Everything that can be done with one message, as Curtain content -- see
 * [CurtainState.MESSAGE_ACTIONS]. Plain layout only: the Curtain root owns the
 * surface, height and inset, exactly as for [DeleteConfirmCurtainContent].
 *
 * Offers precisely what [MessageActionPolicy] resolved from Telegram's answer
 * for this message. The two delete scopes collapse into one Delete row that
 * hands over to the Curtain's delete confirmation, so a destructive action is
 * never one tap from here. The action list scrolls on its own when it outgrows
 * the space a phone can give it, leaving the message line, reactions and
 * Cancel always in reach.
 */
@Composable
fun MessageActionsCurtainContent(
    message: Message,
    capabilities: MessageCapabilities,
    onReaction: (String) -> Unit,
    onAction: (MessageAction) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    /** Whether THIS message is already bookmarked on this device -- relabels the action. */
    isBookmarked: Boolean = false,
    canReact: Boolean = true
) {
    val colors = LocalAetherColors.current
    val ink = Color(0xFFF2F2F5)
    val hint = Color(0xFF9A9AA2)
    val actions = remember(message, capabilities) {
        MessageActionPolicy.actionsFor(message, capabilities, allowSelect = true)
    }
    val primary = actions.filterNot { it.isDelete }
    val canDelete = actions.any { it.isDelete }
    val showReactions = remember(message, canReact) {
        MessageActionPolicy.isReactionTrayAvailable(message, canReact)
    }
    // One decision per open: a second tap while the first is being handled
    // must not fire a second action.
    var acted by remember(message.id) { mutableStateOf(false) }
    val maxListHeight = (LocalConfiguration.current.screenHeightDp * 0.42f).dp

    Column(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("curtain_message_actions_content")
    ) {
        // Which message this is about -- the bubble above is highlighted too.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(30.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.accent)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (message.isOutgoing) "You" else message.senderName.ifBlank { "Message" },
                    fontFamily = ManropeFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = message.text.ifBlank { "Media message" },
                    fontFamily = ManropeFontFamily,
                    fontSize = 13.sp,
                    color = hint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (showReactions) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reaction_tray"),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickReactions.forEach { emoji ->
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0x0FFFFFFF))
                            .clickable(enabled = !acted) {
                                acted = true
                                onReaction(emoji)
                            }
                            .semantics { contentDescription = "React with $emoji" },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = emoji, fontSize = 22.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxListHeight)
                .verticalScroll(rememberScrollState())
        ) {
            primary.forEach { action ->
                val bookmarked = action == MessageAction.BOOKMARK && isBookmarked
                ActionRow(
                    icon = if (bookmarked) Icons.Default.BookmarkBorder else action.icon,
                    title = if (bookmarked) "Remove bookmark" else action.title,
                    tint = ink,
                    enabled = !acted,
                    testTag = "message_action_${action.name.lowercase()}"
                ) {
                    acted = true
                    onAction(action)
                }
            }
            if (canDelete) {
                ActionRow(
                    icon = Icons.Default.Delete,
                    title = "Delete…",
                    tint = Color(0xFFEF4444),
                    enabled = !acted,
                    testTag = "message_action_delete"
                ) {
                    acted = true
                    onDelete()
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onCancel)
                .testTag("message_actions_cancel"),
            contentAlignment = Alignment.Center
        ) {
            Text("Cancel", fontFamily = ManropeFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = hint)
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    tint: Color,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Text(text = title, fontFamily = ManropeFontFamily, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = tint)
    }
}

/** The one-tap reactions offered in the Curtain. */
private val QuickReactions = listOf("❤️", "🔥", "👍", "😂", "👏", "🚀", "⚡")

private val MessageAction.isDelete: Boolean
    get() = this == MessageAction.DELETE_FOR_ME || this == MessageAction.DELETE_FOR_EVERYONE

private val MessageAction.title: String
    get() = when (this) {
        MessageAction.REPLY -> "Reply"
        MessageAction.QUOTE_REPLY -> "Reply with quote"
        MessageAction.COPY -> "Copy text"
        MessageAction.FORWARD -> "Forward"
        MessageAction.EDIT -> "Edit message"
        MessageAction.REPLACE_MEDIA -> "Replace media"
        MessageAction.PIN -> "Pin message"
        MessageAction.UNPIN -> "Unpin message"
        MessageAction.SAVE -> "Save to downloads"
        MessageAction.COPY_LINK -> "Copy link"
        MessageAction.INFO -> "Message info"
        MessageAction.SELECT -> "Select"
        MessageAction.BOOKMARK -> "Bookmark"
        MessageAction.DELETE_FOR_ME -> "Delete for me"
        MessageAction.DELETE_FOR_EVERYONE -> "Delete for everyone"
    }

private val MessageAction.icon: ImageVector
    get() = when (this) {
        MessageAction.REPLY -> Icons.AutoMirrored.Filled.Reply
        MessageAction.QUOTE_REPLY -> Icons.Default.FormatQuote
        MessageAction.COPY -> Icons.Default.ContentCopy
        MessageAction.FORWARD -> Icons.AutoMirrored.Filled.Send
        MessageAction.EDIT -> Icons.Default.Edit
        MessageAction.REPLACE_MEDIA -> Icons.Default.Edit
        MessageAction.PIN, MessageAction.UNPIN -> Icons.Default.PushPin
        MessageAction.SAVE -> Icons.Default.Download
        MessageAction.COPY_LINK -> Icons.Default.Link
        MessageAction.INFO -> Icons.Default.Info
        MessageAction.SELECT -> Icons.Default.CheckCircle
        MessageAction.BOOKMARK -> Icons.Default.Bookmark
        MessageAction.DELETE_FOR_ME, MessageAction.DELETE_FOR_EVERYONE -> Icons.Default.Delete
    }
