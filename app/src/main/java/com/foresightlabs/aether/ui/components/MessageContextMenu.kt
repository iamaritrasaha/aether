package com.foresightlabs.aether.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foresightlabs.aether.domain.messages.MessageAction
import com.foresightlabs.aether.domain.messages.MessageActionPolicy
import com.foresightlabs.aether.domain.messages.MessageCapabilities
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.ManropeFontFamily

/**
 * Long-press actions for a message.
 *
 * The menu renders whatever [MessageActionPolicy] resolved from Telegram's answer for
 * this message and nothing else. It has no opinion of its own about who may edit,
 * pin or delete what — an action visible here is an action the server has already
 * said will succeed.
 */
@Composable
fun MessageContextMenu(
    message: Message?,
    capabilities: MessageCapabilities,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onReactionSelected: (String) -> Unit,
    onAction: (MessageAction) -> Unit,
    canReact: Boolean = true,
    allowSelect: Boolean = true
) {
    if (message == null || !isVisible) return

    val actions = remember(message, capabilities, allowSelect) {
        MessageActionPolicy.actionsFor(message, capabilities, allowSelect = allowSelect)
    }
    val showReactionTray = remember(message, canReact) {
        MessageActionPolicy.isReactionTrayAvailable(message, canReact)
    }

    val reactions = listOf("❤️", "🔥", "👍", "😂", "👏", "🚀", "⚡")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = scaleIn(
                initialScale = 0.85f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
            ) + fadeIn(),
            exit = scaleOut(targetScale = 0.9f) + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* prevent backdrop tap */ },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Emoji Reaction Strip
                if (showReactionTray) Row(
                    modifier = Modifier
                        .clip(AetherEmber.Shapes.Pill)
                        .background(AetherEmber.Colors.Surface)
                        .border(1.dp, AetherEmber.Colors.Border, AetherEmber.Shapes.Pill)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    reactions.forEach { emoji ->
                        var isPressed by remember { mutableStateOf(false) }
                        val scale by animateFloatAsState(
                            targetValue = if (isPressed) 1.35f else 1.0f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "reaction_scale"
                        )

                        Text(
                            text = emoji,
                            fontSize = 24.sp,
                            modifier = Modifier
                                .scale(scale)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    isPressed = true
                                    onReactionSelected(emoji)
                                    onDismiss()
                                }
                                .padding(4.dp)
                        )
                    }
                }

                if (showReactionTray) Spacer(modifier = Modifier.height(16.dp))

                // Actions Menu Box (24dp rounded dark surface)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AetherEmber.Shapes.L)
                        .background(AetherEmber.Colors.Surface)
                        .border(1.dp, AetherEmber.Colors.Border, AetherEmber.Shapes.L)
                        .padding(vertical = 6.dp)
                ) {
                    actions.forEachIndexed { index, action ->
                        if (action == MessageAction.DELETE_FOR_ME ||
                            action == MessageAction.DELETE_FOR_EVERYONE
                        ) {
                            val isFirstDestructive = actions
                                .indexOfFirst { it == MessageAction.DELETE_FOR_ME || it == MessageAction.DELETE_FOR_EVERYONE } == index
                            if (isFirstDestructive && index > 0) {
                                HorizontalDivider(
                                    color = AetherEmber.Colors.BorderSubtle,
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }
                        }
                        ContextMenuActionItem(
                            icon = iconFor(action),
                            title = titleFor(action),
                            tint = if (action == MessageAction.DELETE_FOR_ME ||
                                action == MessageAction.DELETE_FOR_EVERYONE
                            ) DestructiveTint else null,
                            testTag = "message_action_${action.name.lowercase()}",
                            onClick = {
                                onAction(action)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextMenuActionItem(
    icon: ImageVector,
    title: String,
    tint: Color? = null,
    testTag: String,
    onClick: () -> Unit
) {
    val itemTint = tint ?: Color.White

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = itemTint,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = title,
            fontFamily = ManropeFontFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = itemTint
        )
    }
}

private val DestructiveTint = Color(0xFFEF4444)

private fun titleFor(action: MessageAction): String = when (action) {
    MessageAction.REPLY -> "Reply"
    MessageAction.QUOTE_REPLY -> "Reply with quote"
    MessageAction.COPY -> "Copy text"
    MessageAction.FORWARD -> "Forward"
    MessageAction.EDIT -> "Edit message"
    MessageAction.PIN -> "Pin message"
    MessageAction.UNPIN -> "Unpin message"
    MessageAction.SAVE -> "Save to downloads"
    MessageAction.COPY_LINK -> "Copy link"
    MessageAction.INFO -> "Message info"
    MessageAction.SELECT -> "Select"
    MessageAction.DELETE_FOR_ME -> "Delete for me"
    MessageAction.DELETE_FOR_EVERYONE -> "Delete for everyone"
}

private fun iconFor(action: MessageAction): ImageVector = when (action) {
    MessageAction.REPLY -> Icons.AutoMirrored.Filled.Reply
    MessageAction.QUOTE_REPLY -> Icons.Default.FormatQuote
    MessageAction.COPY -> Icons.Default.ContentCopy
    MessageAction.FORWARD -> Icons.AutoMirrored.Filled.Send
    MessageAction.EDIT -> Icons.Default.Edit
    MessageAction.PIN, MessageAction.UNPIN -> Icons.Default.PushPin
    MessageAction.SAVE -> Icons.Default.Download
    MessageAction.COPY_LINK -> Icons.Default.Link
    MessageAction.INFO -> Icons.Default.Info
    MessageAction.SELECT -> Icons.Default.CheckCircle
    MessageAction.DELETE_FOR_ME, MessageAction.DELETE_FOR_EVERYONE -> Icons.Default.Delete
}
