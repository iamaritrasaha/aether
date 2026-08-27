package com.foresightlabs.aether.ui.components

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foresightlabs.aether.domain.messages.MessageCapabilities
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.MessageStatus
import com.foresightlabs.aether.domain.model.MessageType
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import com.foresightlabs.aether.ui.theme.SpaceGroteskFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Details Telegram actually exposes about one message.
 *
 * Every row here is derived from real state. In particular there is **no invented
 * read receipt**: Telegram only exposes a read date for some messages in some chats,
 * gated behind `canGetReadDate`, and where it is not available this sheet says
 * nothing rather than implying delivery information it does not have.
 */
@Composable
fun MessageInfoSheet(
    message: Message?,
    capabilities: MessageCapabilities,
    onDismiss: () -> Unit
) {
    if (message == null) return
    val colors = LocalAetherColors.current

    val rows = remember(message, capabilities) { infoRows(message, capabilities) }

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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AetherEmber.Shapes.L)
                .background(colors.surface)
                .border(1.dp, colors.border, AetherEmber.Shapes.L)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* keep taps inside the sheet */ }
                .padding(20.dp)
                .testTag("message_info_sheet")
        ) {
            Text(
                text = "Message info",
                fontFamily = SpaceGroteskFontFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))

            rows.forEach { (label, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = label,
                        fontFamily = ManropeFontFamily,
                        fontSize = 13.sp,
                        color = colors.textTertiary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = value,
                        fontFamily = ManropeFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary,
                        modifier = Modifier
                            .weight(1.6f)
                            .testTag("message_info_${label.lowercase().replace(' ', '_')}")
                    )
                }
            }
        }
    }
}

/**
 * The rows to show, built only from information that genuinely exists.
 *
 * A field Telegram does not provide produces no row at all — an empty or "unknown"
 * value would read as a fact about the message rather than an absence of data.
 */
internal fun infoRows(
    message: Message,
    capabilities: MessageCapabilities
): List<Pair<String, String>> = buildList {
    add("From" to message.senderName.ifBlank { "Unknown sender" })

    if (message.dateSeconds > 0) {
        add("Sent" to formatFullTimestamp(message.dateSeconds))
    } else if (message.timestamp.isNotBlank()) {
        add("Sent" to message.timestamp)
    }

    if (message.isEdited) {
        // Telegram exposes that a message was edited; the edit *time* is not
        // surfaced through the state Aether holds, so it is not claimed.
        add("Edited" to "Yes")
    }

    message.forwardedFrom?.takeIf { it.isNotBlank() }?.let {
        add("Forwarded from" to it)
    }

    if (message.isOutgoing) {
        add(
            "Delivery" to when (message.status) {
                MessageStatus.SENDING -> "Sending"
                MessageStatus.FAILED -> "Failed to send"
                MessageStatus.READ -> "Read"
                MessageStatus.SENT -> "Delivered"
            }
        )
    }

    // Only offered where Telegram says the detail is obtainable at all.
    if (capabilities.canGetViewers) add("Viewers" to "Available in Telegram")

    message.fileName?.takeIf { it.isNotBlank() }?.let { add("File" to it) }
    message.fileSize?.takeIf { it.isNotBlank() }?.let { add("Size" to it) }

    if ((message.type == MessageType.VOICE || message.type == MessageType.AUDIO) && message.voiceDurationSec > 0) {
        add("Duration" to formatDuration(message.voiceDurationSec))
    }

    if (message.selfDestructIn > 0.0) {
        add("Self-destruct in" to formatDuration(message.selfDestructIn.toInt()))
    } else if (message.autoDeleteIn > 0.0) {
        add("Auto-delete in" to formatDuration(message.autoDeleteIn.toInt()))
    }

    if (message.reactions.isNotEmpty()) {
        add("Reactions" to message.reactions.sumOf { it.count }.toString())
    }
}

private fun formatFullTimestamp(epochSeconds: Int): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
        .format(Date(epochSeconds.toLong() * 1000L))

private fun formatDuration(seconds: Int): String =
    "%d:%02d".format(seconds / 60, seconds % 60)
