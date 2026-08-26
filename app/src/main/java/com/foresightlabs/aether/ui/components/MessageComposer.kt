package com.foresightlabs.aether.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.ui.design.AetherAccent
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import com.foresightlabs.aether.ui.theme.LocalAetherColors

/**
 * Aether Truly Floating Chat Composer Dock.
 *
 * Appears as a refined, self-contained dark-glass pill dock elevated above the conversation
 * stream with visible margins, floating geometry, and fluid keyboard insets.
 */
@Composable
fun MessageComposer(
    replyingTo: Message?,
    onDismissReply: () -> Unit,
    onSendMessage: (String) -> Unit,
    onOpenAttachmentSheet: () -> Unit,
    onVoiceNoteRecorded: () -> Unit,
    enabled: Boolean = true,
    onTextChanged: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    val colors = LocalAetherColors.current
    val hasText = text.isNotBlank()
    val dockShape = RoundedCornerShape(26.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Floating Near-Black Pill Container
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(dockShape)
                .background(if (colors.isDark) Color(0xF0121214) else colors.surfaceElevated)
                .border(1.dp, colors.border, dockShape)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            // Integrated Reply Strip (Inside the floating pill dock)
            AnimatedVisibility(
                visible = replyingTo != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                replyingTo?.let { replyMsg ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.input)
                            .border(0.5.dp, colors.border, RoundedCornerShape(14.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(26.dp)
                                .clip(CircleShape)
                                .background(AetherAccent.current)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Replying to ${replyMsg.senderName}",
                                fontFamily = ManropeFontFamily,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = AetherAccent.current
                            )
                            Text(
                                text = replyMsg.text.ifEmpty { "Attachment" },
                                fontFamily = ManropeFontFamily,
                                fontSize = 11.5.sp,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .clickable { onDismissReply() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel Reply",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            // Controls Row (Attachment, Text Field, Action Button)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Attachment Button (+ icon or paperclip)
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(colors.input)
                        .clickable(enabled = enabled) { onOpenAttachmentSheet() }
                        .testTag("attachment_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "Attach File",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Text Input Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.surface)
                        .border(0.5.dp, colors.border, RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (text.isEmpty()) {
                        Text(
                            text = "Message…",
                            fontFamily = ManropeFontFamily,
                            color = colors.textTertiary,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }

                    BasicTextField(
                        value = text,
                        onValueChange = {
                            text = it
                            onTextChanged(it)
                        },
                        textStyle = TextStyle(
                            color = colors.textPrimary,
                            fontFamily = ManropeFontFamily,
                            fontSize = 14.5.sp,
                            lineHeight = 19.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(AetherAccent.current),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Default
                        ),
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("message_input_field")
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Action Button (Morphing between Mic & Send)
                AnimatedContent(
                    targetState = hasText,
                    transitionSpec = {
                        (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                                expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMedium)))
                            .togetherWith(
                                fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                                        shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMedium))
                            )
                    },
                    label = "composer_action_morph"
                ) { isTextPresent ->
                    if (isTextPresent) {
                        // Send Action Button (Luminous Ember Gradient)
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(AetherAccent.actionBrush)
                                .clickable(enabled = enabled) {
                                    if (text.isNotBlank() && enabled) {
                                        onSendMessage(text.trimEnd())
                                        text = ""
                                    }
                                }
                                .testTag("send_message_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        // Voice Note Action Button
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(colors.input)
                                .clickable(enabled = enabled) { onVoiceNoteRecorded() }
                                .testTag("voice_record_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Record Voice Note",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
