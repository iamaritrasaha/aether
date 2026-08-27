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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import com.foresightlabs.aether.domain.text.AetherEntity
import com.foresightlabs.aether.domain.text.ComposerFormatting
import com.foresightlabs.aether.domain.text.ComposerStyle
import com.foresightlabs.aether.domain.text.ReplyQuote
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
    onSendMessage: (String, List<AetherEntity>) -> Unit,
    /** Quoted excerpt shown above the composer when replying to part of a message. */
    replyQuote: ReplyQuote? = null,
    onOpenAttachmentSheet: () -> Unit,
    onVoiceNoteRecorded: () -> Unit,
    enabled: Boolean = true,
    onTextChanged: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Held as a TextFieldValue so the selection is available for formatting.
    var field by remember { mutableStateOf(TextFieldValue("")) }
    var formatting by remember { mutableStateOf<List<AetherEntity>>(emptyList()) }
    val text = field.text
    val selection = field.selection
    val hasSelection = !selection.collapsed
    val activeStyles = remember(formatting, selection) {
        if (hasSelection) {
            ComposerFormatting.activeStyles(formatting, selection.min, selection.max)
        } else {
            emptySet()
        }
    }
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
                                text = if (replyQuote != null) {
                                    "Quoting ${replyMsg.senderName}"
                                } else {
                                    "Replying to ${replyMsg.senderName}"
                                },
                                fontFamily = ManropeFontFamily,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = AetherAccent.current
                            )
                            Text(
                                // The quote is what the reply is about, so it is
                                // what the preview shows.
                                text = replyQuote?.text ?: replyMsg.text.ifEmpty { "Attachment" },
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

                    if (hasSelection && enabled) {
                        AetherFormattingBar(
                            active = activeStyles,
                            onToggle = { style ->
                                formatting = ComposerFormatting.toggle(
                                    formatting,
                                    style,
                                    selection.min,
                                    selection.max
                                )
                            }
                        )
                    }

                    BasicTextField(
                        value = field,
                        onValueChange = { next ->
                            // Re-anchor the spans against the edit before adopting
                            // it, so styling stays on the characters it was applied
                            // to rather than drifting with every keystroke.
                            if (next.text != field.text) {
                                formatting = ComposerFormatting.sanitise(
                                    reanchorForEdit(field.text, next.text, formatting),
                                    next.text.length
                                )
                                onTextChanged(next.text)
                            }
                            field = next
                        },
                        visualTransformation = rememberFormattingTransformation(
                            formatting = formatting,
                            accent = AetherAccent.current,
                            codeBackground = colors.textPrimary.copy(alpha = 0.10f)
                        ),
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
                                        onSendMessage(text.trimEnd(), formatting)
                                        field = TextFieldValue("")
                                        formatting = emptyList()
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


/**
 * Formatting actions for the current selection.
 *
 * Appears only while text is selected, because that is the only time it can do
 * anything. Aether's own pill row rather than a floating Material toolbar.
 */
@Composable
private fun AetherFormattingBar(
    active: Set<ComposerStyle>,
    onToggle: (ComposerStyle) -> Unit
) {
    val colors = LocalAetherColors.current
    Row(
        modifier = Modifier
            .padding(bottom = 6.dp)
            .clip(AetherEmber.Shapes.Pill)
            .background(colors.surfaceHighlight)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .testTag("formatting_bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        ComposerStyle.entries.forEach { style ->
            val isActive = style in active
            Box(
                modifier = Modifier
                    .clip(AetherEmber.Shapes.Pill)
                    .background(
                        if (isActive) AetherAccent.current.copy(alpha = 0.28f) else Color.Transparent
                    )
                    .clickable { onToggle(style) }
                    // 44dp target once the 12dp horizontal padding is applied.
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("format_${style.name.lowercase()}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = styleLabel(style),
                    fontFamily = ManropeFontFamily,
                    fontSize = 13.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    color = if (isActive) colors.textPrimary else colors.textSecondary,
                    style = styleTextStyle(style)
                )
            }
        }
    }
}

private fun styleLabel(style: ComposerStyle): String = when (style) {
    ComposerStyle.BOLD -> "B"
    ComposerStyle.ITALIC -> "I"
    ComposerStyle.UNDERLINE -> "U"
    ComposerStyle.STRIKETHROUGH -> "S"
    ComposerStyle.SPOILER -> "◍"
    ComposerStyle.CODE -> "{ }"
}

private fun styleTextStyle(style: ComposerStyle): TextStyle = when (style) {
    ComposerStyle.ITALIC -> TextStyle(fontStyle = FontStyle.Italic)
    ComposerStyle.UNDERLINE -> TextStyle(textDecoration = TextDecoration.Underline)
    ComposerStyle.STRIKETHROUGH -> TextStyle(textDecoration = TextDecoration.LineThrough)
    else -> TextStyle()
}

/**
 * Draws the composer's own formatting as the user types.
 *
 * A visual transformation rather than a rewrite of the text: the underlying string
 * stays exactly what will be sent, so offsets never diverge from what the server
 * receives.
 */
@Composable
private fun rememberFormattingTransformation(
    formatting: List<AetherEntity>,
    accent: Color,
    codeBackground: Color
): VisualTransformation = remember(formatting, accent, codeBackground) {
    VisualTransformation { original ->
        val styled = buildAnnotatedString {
            append(original.text)
            formatting.forEach { entity ->
                val start = entity.offset.coerceIn(0, original.text.length)
                val end = entity.end.coerceIn(start, original.text.length)
                if (start == end) return@forEach
                val span = when (entity) {
                    is AetherEntity.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
                    is AetherEntity.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
                    is AetherEntity.Underline ->
                        SpanStyle(textDecoration = TextDecoration.Underline)
                    is AetherEntity.Strikethrough ->
                        SpanStyle(textDecoration = TextDecoration.LineThrough)
                    is AetherEntity.Code ->
                        SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)
                    // Shown marked rather than hidden: the writer needs to see what
                    // they have covered before they send it.
                    is AetherEntity.Spoiler -> SpanStyle(background = accent.copy(alpha = 0.22f))
                    else -> SpanStyle()
                }
                addStyle(span, start, end)
            }
        }
        TransformedText(styled, OffsetMapping.Identity)
    }
}

/**
 * Works out what changed between two composer strings and re-anchors spans.
 *
 * Compose gives the new text, not an edit description, so the common prefix and
 * suffix are compared to recover one. That is exact for the single contiguous edit a
 * keyboard actually produces.
 */
private fun reanchorForEdit(
    before: String,
    after: String,
    entities: List<AetherEntity>
): List<AetherEntity> {
    var prefix = 0
    val maxPrefix = minOf(before.length, after.length)
    while (prefix < maxPrefix && before[prefix] == after[prefix]) prefix++

    var suffix = 0
    while (
        suffix < maxPrefix - prefix &&
        before[before.length - 1 - suffix] == after[after.length - 1 - suffix]
    ) suffix++

    val removed = before.length - prefix - suffix
    val inserted = after.length - prefix - suffix
    return ComposerFormatting.reanchor(entities, prefix, removed, inserted)
}
