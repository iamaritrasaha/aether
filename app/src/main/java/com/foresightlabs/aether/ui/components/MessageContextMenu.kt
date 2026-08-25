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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.ManropeFontFamily

@Composable
fun MessageContextMenu(
    message: Message?,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onReactionSelected: (String) -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onForward: () -> Unit,
    onEdit: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit
) {
    if (message == null || !isVisible) return

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
                Row(
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

                Spacer(modifier = Modifier.height(16.dp))

                // Actions Menu Box (24dp rounded dark surface)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AetherEmber.Shapes.L)
                        .background(AetherEmber.Colors.Surface)
                        .border(1.dp, AetherEmber.Colors.Border, AetherEmber.Shapes.L)
                        .padding(vertical = 6.dp)
                ) {
                    ContextMenuActionItem(
                        icon = Icons.AutoMirrored.Filled.Reply,
                        title = "Reply",
                        onClick = {
                            onReply()
                            onDismiss()
                        }
                    )

                    ContextMenuActionItem(
                        icon = Icons.Default.ContentCopy,
                        title = "Copy Text",
                        onClick = {
                            onCopy()
                            onDismiss()
                        }
                    )

                    ContextMenuActionItem(
                        icon = Icons.AutoMirrored.Filled.Send,
                        title = "Forward",
                        onClick = {
                            onForward()
                            onDismiss()
                        }
                    )

                    if (message.isOutgoing) {
                        ContextMenuActionItem(
                            icon = Icons.Default.Edit,
                            title = "Edit Message",
                            onClick = {
                                onEdit()
                                onDismiss()
                            }
                        )
                    }

                    ContextMenuActionItem(
                        icon = Icons.Default.PushPin,
                        title = if (message.isPinned) "Unpin Message" else "Pin Message",
                        onClick = {
                            onPin()
                            onDismiss()
                        }
                    )

                    HorizontalDivider(
                        color = AetherEmber.Colors.BorderSubtle,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )

                    ContextMenuActionItem(
                        icon = Icons.Default.Delete,
                        title = "Delete",
                        tint = Color(0xFFEF4444),
                        onClick = {
                            onDelete()
                            onDismiss()
                        }
                    )
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
    onClick: () -> Unit
) {
    val itemTint = tint ?: Color.White

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("context_action_${title.lowercase().replace(" ", "_")}")
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
