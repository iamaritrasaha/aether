package com.foresightlabs.aether.ui.design
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.foresightlabs.aether.domain.text.AetherEntity
import com.foresightlabs.aether.domain.text.AetherText
import com.foresightlabs.aether.domain.text.EntityAction
import com.foresightlabs.aether.domain.text.EntityActions

/** Tag used to carry an entity's index through the annotated string. */
private const val ENTITY_TAG = "aether:entity"

/**
 * Lets a single ancestor gesture handler (the message bubble) resolve taps
 * against this text's entities, instead of the text installing its own
 * pointer input. Two independent gesture detectors racing on the same touch
 * stream is what made long-press selection unreliable over message text —
 * the bubble is now the sole owner of pointer geometry, and this controller
 * is just its window into text layout and entity hit-testing.
 */
class AetherRichTextController {
    var layoutResult: TextLayoutResult? = null
        internal set
    var textCoordinates: LayoutCoordinates? = null
        internal set
    internal var tapHandler: ((Int) -> Boolean)? = null

    /** Resolve a tap at the given character offset (from [layoutResult].getOffsetForPosition). Returns true if handled. */
    fun handleTap(charOffset: Int): Boolean {
        return tapHandler?.invoke(charOffset) ?: false
    }
}

/**
 * Renders message text with the formatting Telegram actually attached to it.
 *
 * Spoilers are the one span whose *content* is affected: until the reader reveals
 * it, the covered text is drawn in the surrounding colour on the surrounding
 * colour, so it occupies its true width and the message does not reflow when
 * revealed. Reveal state is local to this reader and this composition — it never
 * touches the message on the server.
 *
 * This text is deliberately non-interactive on its own (no ClickableText, no
 * pointerInput): a [controller] lets the enclosing bubble resolve taps against
 * entities so tap, long-press and swipe all belong to one gesture owner.
 */
@Composable
fun AetherRichText(
    value: AetherText,
    style: TextStyle,
    color: Color,
    accentColor: Color,
    spoilerCover: Color,
    modifier: Modifier = Modifier,
    codeBackground: Color = spoilerCover,
    controller: AetherRichTextController? = null,
    onAction: (EntityAction) -> Unit = {}
) {
    var revealedSpoilers by remember(value) { mutableStateOf(emptySet<Int>()) }

    val ordered = remember(value) { value.entities.sortedBy { it.offset } }

    val annotated = buildEntityString(
        value = value,
        ordered = ordered,
        color = color,
        accentColor = accentColor,
        spoilerCover = spoilerCover,
        codeBackground = codeBackground,
        revealedSpoilers = revealedSpoilers
    )

    if (controller != null) {
        controller.tapHandler = handler@{ position ->
            val hit = annotated
                .getStringAnnotations(ENTITY_TAG, position, position)
                .firstOrNull()
                ?.item
                ?.toIntOrNull()
                ?: return@handler false
            val entity = ordered.getOrNull(hit) ?: return@handler false

            if (entity is AetherEntity.Spoiler) {
                // Revealing is a local reading choice, not an edit.
                revealedSpoilers = revealedSpoilers + hit
                return@handler true
            }
            EntityActions.resolve(entity, value.text)?.let(onAction)
            return@handler true
        }
    }

    BasicText(
        text = annotated,
        style = style.copy(color = color),
        modifier = if (controller != null) {
            modifier.onGloballyPositioned { controller.textCoordinates = it }
        } else {
            modifier
        },
        onTextLayout = { result -> controller?.layoutResult = result }
    )
}

/**
 * Builds the styled string.
 *
 * Spans are applied in order and are allowed to overlap, because Telegram permits
 * overlapping entities — bold-inside-a-link is ordinary. Each span also carries its
 * index as an annotation so a tap can be resolved back to the entity that produced
 * it.
 */
internal fun buildEntityString(
    value: AetherText,
    ordered: List<AetherEntity>,
    color: Color,
    accentColor: Color,
    spoilerCover: Color,
    codeBackground: Color,
    revealedSpoilers: Set<Int>
): AnnotatedString = buildAnnotatedString {
    append(value.text)
    val limit = value.text.length

    ordered.forEachIndexed { index, entity ->
        val start = entity.offset.coerceIn(0, limit)
        val end = entity.end.coerceIn(start, limit)
        if (start == end) return@forEachIndexed

        val span = when (entity) {
            is AetherEntity.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
            is AetherEntity.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
            is AetherEntity.Underline -> SpanStyle(textDecoration = TextDecoration.Underline)
            is AetherEntity.Strikethrough ->
                SpanStyle(textDecoration = TextDecoration.LineThrough)
            is AetherEntity.Code, is AetherEntity.Pre -> SpanStyle(
                fontFamily = FontFamily.Monospace,
                background = codeBackground
            )
            is AetherEntity.Spoiler -> if (index in revealedSpoilers) {
                SpanStyle()
            } else {
                // Same colour foreground and background: the text is unreadable but
                // still occupies its real width, so revealing does not reflow.
                SpanStyle(color = spoilerCover, background = spoilerCover)
            }
            is AetherEntity.BlockQuote -> SpanStyle(color = color.copy(alpha = 0.82f))
            is AetherEntity.Url,
            is AetherEntity.TextUrl,
            is AetherEntity.Mention,
            is AetherEntity.MentionName,
            is AetherEntity.Hashtag,
            is AetherEntity.Cashtag,
            is AetherEntity.Email,
            is AetherEntity.Phone,
            is AetherEntity.BotCommand,
            is AetherEntity.MediaTimestamp -> SpanStyle(color = accentColor)
            is AetherEntity.BankCard -> SpanStyle(color = accentColor)
            is AetherEntity.CustomEmoji -> SpanStyle()
        }
        addStyle(span, start, end)

        if (isInteractive(entity)) {
            addStringAnnotation(ENTITY_TAG, index.toString(), start, end)
        }
    }
}

/** Whether tapping this span does anything. */
internal fun isInteractive(entity: AetherEntity): Boolean = when (entity) {
    is AetherEntity.Bold,
    is AetherEntity.Italic,
    is AetherEntity.Underline,
    is AetherEntity.Strikethrough,
    is AetherEntity.BlockQuote,
    is AetherEntity.CustomEmoji -> false
    else -> true
}

/** Convenience for call sites that only have plain text. */
@Composable
fun AetherRichText(
    text: String,
    style: TextStyle,
    color: Color,
    accentColor: Color,
    spoilerCover: Color,
    modifier: Modifier = Modifier,
    onAction: (EntityAction) -> Unit = {}
) = AetherRichText(
    value = AetherText.plain(text),
    style = style,
    color = color,
    accentColor = accentColor,
    spoilerCover = spoilerCover,
    modifier = modifier,
    onAction = onAction
)
