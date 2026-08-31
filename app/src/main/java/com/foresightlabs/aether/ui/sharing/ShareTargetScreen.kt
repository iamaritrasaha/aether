package com.foresightlabs.aether.ui.sharing

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Videocam
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.sharing.SharedAttachment
import com.foresightlabs.aether.domain.sharing.SharedAttachmentKind
import com.foresightlabs.aether.domain.sharing.SharedContent
import com.foresightlabs.aether.ui.conversation.AetherConversationCurtain
import com.foresightlabs.aether.ui.conversation.CurtainState
import com.foresightlabs.aether.ui.conversation.RecipientRow
import com.foresightlabs.aether.ui.conversation.RecipientSearchField
import com.foresightlabs.aether.ui.conversation.RecipientSubmitRow
import com.foresightlabs.aether.ui.design.AetherAccent
import com.foresightlabs.aether.ui.design.AetherIconButton
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.ManropeFontFamily

/** Test handles for the share target experience. */
object ShareTargetTags {
    const val Screen = "share_target_screen"
    const val Preview = "share_target_preview"
    const val Search = "share_recipient_search"
    const val Submit = "share_submit"
    fun target(chatId: String) = "share_target_$chatId"
}

/**
 * Choosing who a share from another application goes to.
 *
 * The same composition Conversation uses: the persistent dark ground, what is
 * being shared stated above it, and the Curtain -- the one bottom surface --
 * carrying the recipient list. The list, its search field and its confirmation
 * are the very composables forwarding uses, so there is one way to choose a
 * person in Aether rather than a second, share-shaped one.
 *
 * Nothing is sent from here. Choosing a recipient opens that conversation with
 * the share waiting in the Composer, where the person sends it themselves.
 */
@Composable
fun ShareTargetScreen(
    content: SharedContent,
    targets: List<Chat>,
    onDismiss: () -> Unit,
    onChooseRecipient: (Chat) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAetherColors.current
    var query by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val normalized = query.trim().lowercase()
    val visibleTargets = remember(targets, normalized) {
        if (normalized.isBlank()) targets else targets.filter { target ->
            target.title.lowercase().contains(normalized) ||
                target.subtitle.lowercase().contains(normalized)
        }
    }
    val selected = targets.firstOrNull { it.id == selectedId }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag(ShareTargetTags.Screen)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .align(Alignment.TopStart)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Share to",
                        color = AetherAccent.current,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp
                    )
                    Text(
                        text = "Choose a conversation",
                        color = colors.textPrimary,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    )
                }
                AetherIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = "Cancel sharing",
                    onClick = onDismiss,
                    size = 44.dp,
                    iconSize = 18.dp,
                    tint = colors.textSecondary
                )
            }

            Spacer(Modifier.height(12.dp))
            SharedContentPreview(content)
        }

        // The Curtain, exactly as Conversation uses it: pinned to the bottom,
        // behind everything above it, owning its own height and bottom inset.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .zIndex(0f)
        ) {
            AetherConversationCurtain(state = CurtainState.FORWARDING) {
                RecipientSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    testTag = ShareTargetTags.Search,
                    modifier = Modifier.padding(top = 10.dp)
                )

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
                                text = if (normalized.isBlank()) {
                                    "No personal conversations available"
                                } else {
                                    "No matching conversations"
                                },
                                color = colors.textTertiary,
                                fontFamily = ManropeFontFamily,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                            )
                        }
                    }
                    items(visibleTargets, key = { it.id }, contentType = { "share_target" }) { target ->
                        RecipientRow(
                            chat = target,
                            isSelected = target.id == selectedId,
                            onClick = { selectedId = target.id },
                            modifier = Modifier.testTag(ShareTargetTags.target(target.id))
                        )
                    }
                }

                RecipientSubmitRow(
                    target = selected,
                    // "Continue", not "Send": the send itself happens in the
                    // conversation, by the person, on the Composer they know.
                    label = selected?.let { "Continue to ${it.title}" } ?: "Choose a recipient",
                    enabled = selected != null,
                    busy = false,
                    testTag = ShareTargetTags.Submit,
                    onSubmit = onChooseRecipient
                )
            }
        }
    }
}

/**
 * What is about to be shared, stated plainly.
 *
 * Images and videos show themselves, read straight from the shared URI through
 * the content resolver; anything else is named. No container of its own -- this
 * sits on the same ground the rest of the screen does.
 */
@Composable
private fun SharedContentPreview(content: SharedContent) {
    val colors = LocalAetherColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ShareTargetTags.Preview),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(38.dp)
                .clip(CircleShape)
                .background(AetherAccent.current)
        )
        Spacer(Modifier.width(10.dp))
        val visuals = content.attachments.take(3)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = when (content) {
                    is SharedContent.Text -> if (content.link != null) "Link" else "Text"
                    is SharedContent.Attachments -> if (content.isMultiple) {
                        "${content.items.size} attachments"
                    } else {
                        content.items.firstOrNull()?.kind.label()
                    }
                },
                color = colors.textSecondary,
                fontFamily = ManropeFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
            Text(
                text = content.summary,
                color = colors.textPrimary,
                fontFamily = ManropeFontFamily,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        visuals.forEach { attachment ->
            Spacer(Modifier.width(8.dp))
            SharedAttachmentThumbnail(attachment)
        }
    }
}

@Composable
private fun SharedAttachmentThumbnail(attachment: SharedAttachment) {
    val colors = LocalAetherColors.current
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Color(0x14FFFFFF)),
        contentAlignment = Alignment.Center
    ) {
        when (attachment.kind) {
            SharedAttachmentKind.IMAGE, SharedAttachmentKind.VIDEO -> AsyncImage(
                // Coil reads the content URI through the resolver; the shared
                // grant is what makes this legible, not a filesystem path.
                model = attachment.uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            SharedAttachmentKind.FILE -> Unit
        }
        if (attachment.kind != SharedAttachmentKind.IMAGE) {
            Icon(
                imageVector = if (attachment.kind == SharedAttachmentKind.VIDEO) {
                    Icons.Default.Videocam
                } else {
                    Icons.AutoMirrored.Filled.InsertDriveFile
                },
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private fun SharedAttachmentKind?.label(): String = when (this) {
    SharedAttachmentKind.IMAGE -> "Photo"
    SharedAttachmentKind.VIDEO -> "Video"
    else -> "File"
}
