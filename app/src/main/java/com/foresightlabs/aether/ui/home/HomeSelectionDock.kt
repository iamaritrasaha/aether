package com.foresightlabs.aether.ui.home
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.MarkChatUnread
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foresightlabs.aether.domain.chats.ChatAction
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.ManropeFontFamily

/**
 * Compact selection action dock for Home, emerging from the physical bottom
 * of the existing black conversations layer.
 */
@Composable
fun HomeSelectionDock(
    selectedChats: List<Chat>,
    onClearSelection: () -> Unit,
    onChatAction: (Chat, ChatAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val count = selectedChats.size
    val colors = LocalAetherColors.current

    val hasUnread = selectedChats.any { it.unreadCount > 0 || it.isMarkedAsUnread }
    val hasUnmuted = selectedChats.any { !it.isMuted }

    val readAction = if (hasUnread) ChatAction.MARK_READ else ChatAction.MARK_UNREAD
    val readIcon = if (hasUnread) Icons.Default.DoneAll else Icons.Default.MarkChatUnread
    val readLabel = if (hasUnread) "Mark as read" else "Mark as unread"

    val muteAction = if (hasUnmuted) ChatAction.MUTE else ChatAction.UNMUTE
    val muteIcon = if (hasUnmuted) Icons.Default.NotificationsOff else Icons.Default.Notifications
    val muteLabel = if (hasUnmuted) "Mute" else "Unmute"

    // TdApi.ToggleChatIsPinned pins or unpins exactly one chat; there is no bulk
    // variant (TdApi.SetPinnedChats replaces the whole pinned set, which is a
    // different, riskier operation than toggling a selection). So Pin only ever
    // appears for a single selected chat, matching what the action actually does.
    val singleSelectedChat = selectedChats.singleOrNull()
    val pinAction = singleSelectedChat?.let { if (it.isPinned) ChatAction.UNPIN else ChatAction.PIN }
    val pinLabel = if (singleSelectedChat?.isPinned == true) "Unpin" else "Pin"

    val dockFill = Color(0xFF141418)
    val ink = Color(0xFFF2F2F5)
    val control = Color(0xFFB6B6BE)
    val subtleBorder = Color(0x22FFFFFF)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("home_selection_dock")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(dockFill)
                .border(0.5.dp, subtleBorder, RoundedCornerShape(22.dp))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Dismiss button (×)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { onClearSelection() }
                    .testTag("home_selection_clear")
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
                    .testTag("home_selection_count")
            )

            // Read / Unread Action
            SelectionDockActionButton(
                icon = readIcon,
                label = readLabel,
                tint = control,
                onClick = {
                    selectedChats.forEach { chat ->
                        onChatAction(chat, readAction)
                    }
                    onClearSelection()
                },
                testTag = "home_selection_action_read"
            )

            // Pin / Unpin Action -- only meaningful for a single chat; see
            // pinAction above for why a multi-selection never shows this.
            if (singleSelectedChat != null && pinAction != null) {
                Spacer(modifier = Modifier.width(4.dp))
                SelectionDockActionButton(
                    icon = Icons.Default.PushPin,
                    label = pinLabel,
                    tint = control,
                    onClick = {
                        onChatAction(singleSelectedChat, pinAction)
                        onClearSelection()
                    },
                    testTag = "home_selection_action_pin"
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Mute / Unmute Action
            SelectionDockActionButton(
                icon = muteIcon,
                label = muteLabel,
                tint = control,
                onClick = {
                    selectedChats.forEach { chat ->
                        onChatAction(chat, muteAction)
                    }
                    onClearSelection()
                },
                testTag = "home_selection_action_mute"
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Delete Action
            SelectionDockActionButton(
                icon = Icons.Default.Delete,
                label = "Delete",
                tint = Color(0xFFEF4444).copy(alpha = 0.9f),
                onClick = {
                    selectedChats.forEach { chat ->
                        onChatAction(chat, ChatAction.DELETE_FOR_ME)
                    }
                    onClearSelection()
                },
                testTag = "home_selection_action_delete"
            )
        }
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
            modifier = Modifier.size(20.dp)
        )
    }
}
