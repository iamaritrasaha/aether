package com.foresightlabs.aether.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Unarchive
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foresightlabs.aether.domain.chats.ChatAction
import com.foresightlabs.aether.domain.chats.ChatActionPolicy
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import com.foresightlabs.aether.ui.theme.SpaceGroteskFontFamily

import com.foresightlabs.aether.ui.design.AetherGlassMenuDefaults
import com.foresightlabs.aether.ui.design.AetherGlassMenuDivider
import com.foresightlabs.aether.ui.design.AetherGlassMenuItem
import com.foresightlabs.aether.ui.design.AetherGlassMenuSurface
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * Long-press actions for a conversation row.
 *
 * The list comes from [ChatActionPolicy], so an action appears only where the
 * account may genuinely perform it. Anything destructive routes through a
 * confirmation that states its real scope before it is carried out.
 */
@Composable
fun ChatActionSheet(
    chat: Chat?,
    onDismiss: () -> Unit,
    onAction: (ChatAction) -> Unit
) {
    if (chat == null) return

    val colors = LocalAetherColors.current
    var pendingAction by remember(chat.id) { mutableStateOf<ChatAction?>(null) }

    val actions = remember(chat) { ChatActionPolicy.actionsFor(chat) }
    val destructive = remember(chat) { ChatActionPolicy.destructiveActions(chat).toSet() }
    val confirmation = pendingAction?.let { ChatActionPolicy.confirmation(chat, it) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
            .padding(horizontal = 24.dp)
            .testTag("chat_action_scrim"),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = true,
            enter = scaleIn(
                initialScale = 0.95f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
            ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)),
            exit = scaleOut(targetScale = 0.96f) + fadeOut()
        ) {
            AetherGlassMenuSurface(
                frostState = null,
                shape = RoundedCornerShape(AetherGlassMenuDefaults.SheetRadius),
                elevation = 10.dp,
                emphasis = 0.25f,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* keep taps inside the sheet */ }
                    .testTag("chat_action_sheet")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = chat.title,
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = colors.textPrimary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )

                    if (confirmation != null) {
                        Text(
                            text = confirmation.body,
                            fontFamily = ManropeFontFamily,
                            fontSize = 14.sp,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                        )
                        AetherGlassMenuItem(
                            icon = iconFor(pendingAction!!),
                            title = confirmation.confirmLabel,
                            isDestructive = true,
                            testTag = "chat_action_confirm",
                            onClick = {
                                onAction(pendingAction!!)
                                onDismiss()
                            }
                        )
                        AetherGlassMenuItem(
                            icon = Icons.Default.Person,
                            title = "Cancel",
                            testTag = "chat_action_cancel",
                            onClick = { pendingAction = null }
                        )
                    } else {
                        var dividerDrawn = false
                        actions.forEach { action ->
                            if (action in destructive && !dividerDrawn) {
                                dividerDrawn = true
                                AetherGlassMenuDivider()
                            }
                            AetherGlassMenuItem(
                                icon = iconFor(action),
                                title = titleFor(action, chat),
                                isDestructive = action in destructive,
                                testTag = "chat_action_${action.name.lowercase()}",
                                onClick = {
                                    if (ChatActionPolicy.confirmation(chat, action) != null) {
                                        pendingAction = action
                                    } else {
                                        onAction(action)
                                        onDismiss()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun titleFor(action: ChatAction, chat: Chat): String = when (action) {
    ChatAction.MARK_READ -> "Mark as read"
    ChatAction.MARK_UNREAD -> "Mark as unread"
    ChatAction.PIN -> "Pin to top"
    ChatAction.UNPIN -> "Unpin"
    ChatAction.MUTE -> "Mute notifications"
    ChatAction.UNMUTE -> "Unmute notifications"
    ChatAction.ARCHIVE -> "Archive"
    ChatAction.UNARCHIVE -> "Move out of archive"
    ChatAction.CLEAR_HISTORY -> "Clear history"
    ChatAction.DELETE_FOR_ME -> "Delete conversation"
    ChatAction.DELETE_FOR_EVERYONE -> "Delete for everyone"
    ChatAction.LEAVE -> "Leave ${chat.title}"
    ChatAction.CLOSE_SECRET_CHAT -> "Close secret chat"
    ChatAction.BLOCK -> "Block"
    ChatAction.UNBLOCK -> "Unblock"
    ChatAction.OPEN_PROFILE -> "Open profile"
}

private fun iconFor(action: ChatAction): ImageVector = when (action) {
    ChatAction.MARK_READ -> Icons.Default.MarkEmailRead
    ChatAction.MARK_UNREAD -> Icons.Default.MarkEmailUnread
    ChatAction.PIN, ChatAction.UNPIN -> Icons.Default.PushPin
    ChatAction.MUTE -> Icons.Default.NotificationsOff
    ChatAction.UNMUTE -> Icons.Default.NotificationsActive
    ChatAction.ARCHIVE -> Icons.Default.Archive
    ChatAction.UNARCHIVE -> Icons.Default.Unarchive
    ChatAction.CLEAR_HISTORY -> Icons.Default.DeleteSweep
    ChatAction.DELETE_FOR_ME, ChatAction.DELETE_FOR_EVERYONE -> Icons.Default.Delete
    ChatAction.LEAVE -> Icons.Default.ExitToApp
    ChatAction.CLOSE_SECRET_CHAT -> Icons.Default.Lock
    ChatAction.BLOCK, ChatAction.UNBLOCK -> Icons.Default.Block
    ChatAction.OPEN_PROFILE -> Icons.Default.Person
}
