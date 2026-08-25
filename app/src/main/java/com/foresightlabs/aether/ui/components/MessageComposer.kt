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
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
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
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.ManropeFontFamily

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
    val hasText = text.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // Reply Strip
        AnimatedVisibility(
            visible = replyingTo != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            replyingTo?.let { replyMsg ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(AetherEmber.Shapes.M)
                        .background(AetherEmber.Colors.Surface)
                        .border(1.dp, AetherEmber.Colors.Border, AetherEmber.Shapes.M)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(30.dp)
                            .clip(CircleShape)
                            .background(AetherEmber.Colors.Accent)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Replying to ${replyMsg.senderName}",
                            fontFamily = ManropeFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AetherEmber.Colors.Accent
                        )
                        Text(
                            text = replyMsg.text.ifEmpty { "Attachment" },
                            fontFamily = ManropeFontFamily,
                            fontSize = 12.sp,
                            color = AetherEmber.Colors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = onDismissReply,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel Reply",
                            tint = AetherEmber.Colors.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Floating Near-Black Composer Outer Container (Reference Match)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AetherEmber.Shapes.XL)
                .background(AetherEmber.Colors.Surface)
                .border(1.dp, Color(0x22FFFFFF), AetherEmber.Shapes.XL)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Attachment Button
            IconButton(
                onClick = onOpenAttachmentSheet,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0x18FFFFFF))
                    .testTag("attachment_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = "Attach File",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Text Input Capsule
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(AetherEmber.Shapes.L)
                    .background(Color(0x14FFFFFF))
                    .border(0.5.dp, Color(0x18FFFFFF), AetherEmber.Shapes.L)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = "Your Message…",
                        fontFamily = ManropeFontFamily,
                        color = Color(0x75FFFFFF),
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
                        color = Color.White,
                        fontFamily = ManropeFontFamily,
                        fontSize = 14.5.sp,
                        lineHeight = 19.5.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(AetherEmber.Colors.Accent),
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

            // Send / Mic Morphing Action Button
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
                    // Send Button with Ember Vermilion / Crimson gradient
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(AetherEmber.Gradients.ActionButton)
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
                            modifier = Modifier.size(19.dp)
                        )
                    }
                } else {
                    // Circular Translucent Glass Voice / Mic Button
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0x28FFFFFF))
                            .clickable {
                                onVoiceNoteRecorded()
                            }
                            .testTag("voice_record_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Record Voice Note",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
