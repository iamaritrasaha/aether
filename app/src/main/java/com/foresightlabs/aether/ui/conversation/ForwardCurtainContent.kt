package com.foresightlabs.aether.ui.conversation

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foresightlabs.aether.BuildConfig
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.ui.design.AetherAvatar
import com.foresightlabs.aether.ui.design.AetherIconButton
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import com.foresightlabs.aether.ui.theme.aetherDuration

private const val FORWARD_LOG_TAG = "AetherTd"

sealed interface ForwardState {
    data object Idle : ForwardState
    data object Sending : ForwardState
    data object Success : ForwardState
    data class Error(val message: String) : ForwardState
}

/**
 * Forwarding as direct content of the persistent Conversation Curtain: a title
 * row, a search control, recipients, the copy option and the confirmation.
 *
 * It owns no surface, clipping, height, bottom inset or positioning of its own —
 * all of that belongs to [AetherConversationCurtain]. The rounded shapes here are
 * individual controls (the search field, the confirm target, avatars), never a
 * feature-sized background.
 */
@Composable
fun ForwardCurtainContent(
    messages: List<Message>,
    targets: List<Chat>,
    canSendCopy: Boolean,
    hasCaption: Boolean,
    state: ForwardState,
    onDismiss: () -> Unit,
    onForward: (Chat, Boolean, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAetherColors.current
    var query by remember { mutableStateOf("") }
    var selectedTargetId by remember { mutableStateOf<String?>(null) }
    var sendCopy by remember { mutableStateOf(false) }
    var removeCaption by remember { mutableStateOf(false) }
    var contentVisible by remember { mutableStateOf(false) }
    val isSending = state is ForwardState.Sending
    val normalizedQuery = query.trim().lowercase()
    val selectedTarget = targets.firstOrNull { it.id == selectedTargetId }
    val visibleTargets = remember(targets, normalizedQuery) {
        if (normalizedQuery.isBlank()) targets else targets.filter { target ->
            target.title.lowercase().contains(normalizedQuery) ||
                target.subtitle.lowercase().contains(normalizedQuery)
        }
    }
    val contentFadeDuration = aetherDuration(180)
    val contentLiftDuration = aetherDuration(220)
    val contentAlpha by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = tween(contentFadeDuration, easing = FastOutSlowInEasing),
        label = "forward_content_alpha"
    )
    val contentOffset by animateFloatAsState(
        targetValue = if (contentVisible) 0f else 12f,
        animationSpec = tween(contentLiftDuration, easing = FastOutSlowInEasing),
        label = "forward_content_lift"
    )

    LaunchedEffect(messages) {
        contentVisible = true
        if (BuildConfig.DEBUG) {
            Log.d(FORWARD_LOG_TAG, "FORWARD_CURTAIN_OPEN selectedCount=${messages.size}")
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = contentAlpha
                translationY = contentOffset
            }
            .testTag("curtain_forward_content")
    ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AetherIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = "Close forwarding",
                    onClick = onDismiss,
                    enabled = !isSending,
                    size = 48.dp,
                    iconSize = 18.dp,
                    tint = colors.textSecondary
                )
                Column(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                    Text(
                        text = "Forwarding ${messages.size} ${if (messages.size == 1) "message" else "messages"}",
                        color = colors.textPrimary,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(AetherEmber.Shapes.M)
                    .background(Color(0xFF181A21))
                    .border(0.5.dp, Color.White.copy(alpha = 0.055f), AetherEmber.Shapes.M)
                    .padding(horizontal = 13.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(18.dp)
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Search people",
                            color = colors.textTertiary,
                            fontFamily = ManropeFontFamily,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = colors.textPrimary,
                            fontFamily = ManropeFontFamily,
                            fontSize = 14.sp
                        ),
                        cursorBrush = SolidColor(colors.accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                            .semantics { contentDescription = "Search people" }
                            .testTag("forward_recipient_search")
                    )
                }
                if (query.isNotEmpty()) {
                    AetherIconButton(
                        icon = Icons.Default.Close,
                        contentDescription = "Clear recipient search",
                        onClick = { query = "" },
                        size = 48.dp,
                        iconSize = 17.dp,
                        tint = colors.textTertiary
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (visibleTargets.isEmpty()) {
                    item {
                        Text(
                            text = if (normalizedQuery.isBlank()) "No personal conversations available" else "No matching conversations",
                            color = colors.textTertiary,
                            fontFamily = ManropeFontFamily,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                        )
                    }
                }
                items(visibleTargets, key = { it.id }, contentType = { "forward_target" }) { target ->
                    ForwardRecipientRow(
                        chat = target,
                        isSelected = target.id == selectedTargetId,
                        onClick = {
                            if (!isSending) {
                                selectedTargetId = target.id
                                if (BuildConfig.DEBUG) {
                                    Log.d(
                                        FORWARD_LOG_TAG,
                                        "FORWARD_TARGET_SELECTED chatHash=${Integer.toHexString(target.id.hashCode())}"
                                    )
                                }
                            }
                        },
                        modifier = Modifier.testTag("forward_target_${target.id}")
                    )
                }
            }

            if (canSendCopy) {
                ForwardOptionRow(
                    label = "Send as copy",
                    detail = "Remove the original sender attribution",
                    checked = sendCopy,
                    enabled = !isSending,
                    testTag = "forward_option_copy",
                    onToggle = {
                        sendCopy = !sendCopy
                        if (!sendCopy) removeCaption = false
                    }
                )
                AnimatedVisibility(
                    visible = sendCopy && hasCaption,
                    enter = expandVertically(animationSpec = tween(aetherDuration(140))) +
                        fadeIn(animationSpec = tween(aetherDuration(120)))
                ) {
                    ForwardOptionRow(
                        label = "Remove caption",
                        detail = "Forward without the media caption",
                        checked = removeCaption,
                        enabled = !isSending,
                        testTag = "forward_option_remove_caption",
                        onToggle = { removeCaption = !removeCaption }
                    )
                }
            }

            (state as? ForwardState.Error)?.message?.let { error ->
                Text(
                    text = error,
                    color = colors.accent,
                    fontFamily = ManropeFontFamily,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            val canSubmit = selectedTarget != null && !isSending
            val actionLabel = selectedTarget?.let { "Forward to ${it.title}" } ?: "Choose a recipient"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AetherEmber.Shapes.Pill)
                        .background(if (canSubmit) Color(0xD91C1A24) else Color(0x8C111318))
                        .border(
                            0.5.dp,
                            if (canSubmit) colors.accent.copy(alpha = 0.36f) else Color.White.copy(alpha = 0.045f),
                            AetherEmber.Shapes.Pill
                        )
                        .clickable(enabled = canSubmit) {
                            selectedTarget?.let { target ->
                                if (BuildConfig.DEBUG) {
                                    Log.d(
                                        FORWARD_LOG_TAG,
                                        "FORWARD_SUBMIT messageCount=${messages.size} targetHash=${Integer.toHexString(target.id.hashCode())} sendCopy=$sendCopy removeCaption=$removeCaption"
                                    )
                                }
                                onForward(target, sendCopy, removeCaption)
                            }
                        }
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 16.dp, vertical = 13.dp)
                        .semantics { contentDescription = actionLabel }
                        .testTag("forward_submit"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        selectedTarget?.let { target ->
                            AetherAvatar(
                                initials = target.avatarInitials,
                                gradient = target.avatarGradient,
                                size = 24.dp,
                                chatType = target.type,
                                photoPath = target.photoPath,
                                showGlowingRim = true
                            )
                            Spacer(Modifier.width(9.dp))
                        }
                        Text(
                            text = actionLabel,
                            color = if (canSubmit) colors.textPrimary else colors.textTertiary,
                            fontFamily = ManropeFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = colors.accent,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = if (canSubmit) colors.accent else colors.textTertiary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
    }
}

@Composable
private fun ForwardRecipientRow(
    chat: Chat,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAetherColors.current
    val secondary = chat.subtitle.ifBlank { chat.lastMessageText }.ifBlank { "Conversation" }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) colors.accent.copy(alpha = 0.12f) else Color.Transparent)
            .selectable(
                selected = isSelected,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = 7.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "${chat.title}, $secondary"
            }
            .heightIn(min = 63.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AetherAvatar(
            initials = chat.avatarInitials,
            gradient = chat.avatarGradient,
            size = 46.dp,
            isOnline = chat.directUser?.isOnline == true && !isSelected,
            chatType = chat.type,
            photoPath = chat.photoPath,
            showGlowingRim = isSelected
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chat.title,
                color = colors.textPrimary,
                fontFamily = ManropeFontFamily,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = secondary,
                color = colors.textTertiary,
                fontFamily = ManropeFontFamily,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.24f))
                    .border(0.5.dp, colors.accent.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
private fun ForwardOptionRow(
    label: String,
    detail: String,
    checked: Boolean,
    enabled: Boolean,
    testTag: String,
    onToggle: () -> Unit
) {
    val colors = LocalAetherColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = { onToggle() }
            )
            .padding(horizontal = 20.dp, vertical = 7.dp)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = colors.textPrimary,
                fontFamily = ManropeFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = detail,
                color = colors.textTertiary,
                fontFamily = ManropeFontFamily,
                fontSize = 11.sp
            )
        }
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (checked) colors.accent.copy(alpha = 0.22f) else Color(0xFF15171C))
                .border(
                    0.5.dp,
                    if (checked) colors.accent.copy(alpha = 0.58f) else Color.White.copy(alpha = 0.08f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Enabled",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}
