package com.foresightlabs.aether.ui.forum
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import com.foresightlabs.aether.ui.design.aetherFloatingHeaderContentTopPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foresightlabs.aether.domain.model.ForumTopicSummary
import com.foresightlabs.aether.ui.design.AetherAtmosphericBackground
import com.foresightlabs.aether.ui.design.AetherEmptyState
import com.foresightlabs.aether.ui.design.rememberAetherFrostState
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import com.foresightlabs.aether.ui.theme.SpaceGroteskFontFamily

/**
 * The topic list of a forum supergroup.
 *
 * A forum is not one conversation. Opening it lands here, and each topic opens as
 * its own conversation with its own history, draft and unread state — which is what
 * Telegram means by a topic and what routing everything through the chat destroys.
 */
@Composable
fun ForumTopicsScreen(
    title: String,
    topics: List<ForumTopicSummary>,
    isLoading: Boolean,
    onTopicClick: (ForumTopicSummary) -> Unit,
    onTopicLongPress: (ForumTopicSummary) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAetherColors.current
    val frostState = rememberAetherFrostState()

    Box(modifier = modifier.fillMaxSize()) {
        AetherAtmosphericBackground(
            modifier = Modifier.fillMaxSize(),
            frostState = frostState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = aetherFloatingHeaderContentTopPadding())
            ) {
                Text(
                    text = title,
                    fontFamily = SpaceGroteskFontFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(AetherEmber.Shapes.RisingSheet)
                        .background(colors.background)
                        .border(1.dp, colors.border, AetherEmber.Shapes.RisingSheet)
                ) {
                    when {
                        isLoading && topics.isEmpty() ->
                            AetherEmptyState(title = "Loading topics…")
                        topics.isEmpty() ->
                            AetherEmptyState(title = "This forum has no topics yet")
                        else -> LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("forum_topic_list"),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(topics, key = { it.topicId }) { topic ->
                                ForumTopicRow(
                                    topic = topic,
                                    onClick = { onTopicClick(topic) },
                                    onLongPress = { onTopicLongPress(topic) }
                                )
                                HorizontalDivider(
                                    color = colors.divider,
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(start = 20.dp, end = 16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ForumTopicRow(
    topic: ForumTopicSummary,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val colors = LocalAetherColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .testTag("forum_topic_${topic.topicId}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (topic.isPinned) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Pinned topic",
                        tint = colors.accent,
                        modifier = Modifier.size(13.dp)
                    )
                }
                if (topic.isClosed) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Closed topic",
                        tint = colors.textTertiary,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Text(
                    text = topic.name,
                    fontFamily = ManropeFontFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                // A draft belongs to the topic, so it previews here rather than on
                // the forum's own row.
                text = topic.draftText?.let { "Draft: $it" }
                    ?: topic.lastMessagePreview.ifBlank { "No messages yet" },
                fontFamily = ManropeFontFamily,
                fontSize = 13.sp,
                color = if (topic.draftText != null) colors.accent else colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (topic.hasUnread) {
            Box(
                modifier = Modifier
                    .clip(AetherEmber.Shapes.Pill)
                    .background(if (topic.isMuted) colors.surfaceHighlight else colors.accent)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .testTag("forum_topic_unread_${topic.topicId}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = topic.unreadCount.toString(),
                    fontFamily = ManropeFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            }
        }
    }
}

