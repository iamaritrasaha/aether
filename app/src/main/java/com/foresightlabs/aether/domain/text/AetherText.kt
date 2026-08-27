package com.foresightlabs.aether.domain.text

import androidx.compose.runtime.Immutable

/**
 * A span of formatting or interactivity over a message's text.
 *
 * Offsets and lengths are in UTF-16 code units, matching TDLib exactly, so a span
 * lands on the same characters Telegram meant it to. Converting to code points, or
 * to characters, silently breaks every message containing an emoji.
 */
@Immutable
sealed interface AetherEntity {
    val offset: Int
    val length: Int

    val end: Int get() = offset + length

    // --- pure styling ---------------------------------------------------------

    data class Bold(override val offset: Int, override val length: Int) : AetherEntity
    data class Italic(override val offset: Int, override val length: Int) : AetherEntity
    data class Underline(override val offset: Int, override val length: Int) : AetherEntity
    data class Strikethrough(override val offset: Int, override val length: Int) : AetherEntity

    /** Hidden until the reader chooses to reveal it. */
    data class Spoiler(override val offset: Int, override val length: Int) : AetherEntity

    data class Code(override val offset: Int, override val length: Int) : AetherEntity
    data class Pre(
        override val offset: Int,
        override val length: Int,
        val language: String? = null
    ) : AetherEntity

    data class BlockQuote(
        override val offset: Int,
        override val length: Int,
        val isExpandable: Boolean = false
    ) : AetherEntity

    /**
     * A Telegram premium custom emoji.
     *
     * Rendered as the underlying text until Aether can fetch and play the sticker
     * the id points at; the characters it covers are always meaningful on their own,
     * which is why this degrades cleanly rather than leaving a gap.
     */
    data class CustomEmoji(
        override val offset: Int,
        override val length: Int,
        val customEmojiId: Long
    ) : AetherEntity

    // --- interactive ----------------------------------------------------------

    /** A link whose text is the URL itself. */
    data class Url(override val offset: Int, override val length: Int) : AetherEntity

    /** A link whose text differs from its destination. */
    data class TextUrl(
        override val offset: Int,
        override val length: Int,
        val url: String
    ) : AetherEntity

    /** An @username mention. */
    data class Mention(override val offset: Int, override val length: Int) : AetherEntity

    /** A mention of a user with no public username. */
    data class MentionName(
        override val offset: Int,
        override val length: Int,
        val userId: Long
    ) : AetherEntity

    data class Hashtag(override val offset: Int, override val length: Int) : AetherEntity
    data class Cashtag(override val offset: Int, override val length: Int) : AetherEntity
    data class Email(override val offset: Int, override val length: Int) : AetherEntity
    data class Phone(override val offset: Int, override val length: Int) : AetherEntity
    data class BankCard(override val offset: Int, override val length: Int) : AetherEntity
    data class BotCommand(override val offset: Int, override val length: Int) : AetherEntity

    /** A timestamp into the message's own media, e.g. `1:23`. */
    data class MediaTimestamp(
        override val offset: Int,
        override val length: Int,
        val seconds: Int
    ) : AetherEntity
}

/** Message text together with the spans Telegram attached to it. */
@Immutable
data class AetherText(
    val text: String,
    val entities: List<AetherEntity> = emptyList()
) {
    val isPlain: Boolean get() = entities.isEmpty()

    /** Whether any part of this text is hidden behind a spoiler. */
    val hasSpoiler: Boolean get() = entities.any { it is AetherEntity.Spoiler }

    companion object {
        val Empty = AetherText("")

        fun plain(text: String) = AetherText(text)
    }
}

/**
 * What tapping an interactive span should do.
 *
 * Resolved in the domain rather than in the composable so the UI never has to
 * assemble a URL, and so a span type Aether does not act on simply produces null
 * instead of an unpredictable intent.
 */
sealed interface EntityAction {
    data class OpenUrl(val url: String) : EntityAction
    data class OpenUsername(val username: String) : EntityAction
    data class OpenUser(val userId: Long) : EntityAction
    data class SearchHashtag(val tag: String) : EntityAction
    data class ComposeEmail(val address: String) : EntityAction
    data class DialPhone(val number: String) : EntityAction
    data class SeekMedia(val seconds: Int) : EntityAction
    data class CopyText(val text: String) : EntityAction
}

object EntityActions {

    /**
     * The action for the span at [entity], or null when Aether does not act on it.
     *
     * A span with no action is still rendered with its styling — it simply is not
     * clickable, which is the truthful outcome for something Aether cannot follow.
     */
    fun resolve(entity: AetherEntity, fullText: String): EntityAction? {
        val slice = fullText.substringSafe(entity.offset, entity.end)
        return when (entity) {
            is AetherEntity.Url -> EntityAction.OpenUrl(normaliseUrl(slice))
            is AetherEntity.TextUrl -> EntityAction.OpenUrl(normaliseUrl(entity.url))
            is AetherEntity.Mention -> EntityAction.OpenUsername(slice.removePrefix("@"))
            is AetherEntity.MentionName -> EntityAction.OpenUser(entity.userId)
            is AetherEntity.Hashtag -> EntityAction.SearchHashtag(slice)
            is AetherEntity.Cashtag -> EntityAction.SearchHashtag(slice)
            is AetherEntity.Email -> EntityAction.ComposeEmail(slice)
            is AetherEntity.Phone -> EntityAction.DialPhone(slice)
            is AetherEntity.MediaTimestamp -> EntityAction.SeekMedia(entity.seconds)
            is AetherEntity.Code, is AetherEntity.Pre -> EntityAction.CopyText(slice)
            is AetherEntity.BankCard -> EntityAction.CopyText(slice)
            else -> null
        }
    }

    /**
     * Gives a bare host a scheme so it resolves as a web address.
     *
     * Telegram marks `example.com` as a URL entity without a scheme; handing that
     * straight to an intent either fails or resolves somewhere unintended.
     */
    fun normaliseUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return trimmed
        val hasScheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(trimmed)
        return if (hasScheme) trimmed else "https://$trimmed"
    }

    private fun String.substringSafe(start: Int, end: Int): String {
        if (start < 0 || start >= length) return ""
        return substring(start, end.coerceAtMost(length))
    }
}
