package com.foresightlabs.aether.domain.text

import androidx.compose.runtime.Immutable

/**
 * A span of an original message, quoted in a reply.
 *
 * [position] is the quote's offset **in the original message's text**, in UTF-16 code
 * units. Telegram needs it to keep the quote attached when the original is edited —
 * a quote carrying only its text would detach the moment the original changed, and
 * would silently match the wrong occurrence when the same words appear twice.
 *
 * This is a real TDLib reply structure. Aether never fakes a quote by pasting the
 * quoted words into the body of the new message.
 */
@Immutable
data class ReplyQuote(
    val text: String,
    val position: Int,
    /** Formatting carried over from the quoted span. */
    val formatted: AetherText = AetherText(text)
) {
    val isEmpty: Boolean get() = text.isBlank()

    companion object {
        /**
         * Quotes `[start, end)` of [source], carrying whatever formatting overlapped.
         *
         * Entity offsets are rebased onto the extracted text and clipped to it, so a
         * span that only partly overlapped the selection quotes only the part inside.
         */
        fun from(source: AetherText, start: Int, end: Int): ReplyQuote? {
            val from = start.coerceIn(0, source.text.length)
            val to = end.coerceIn(from, source.text.length)
            if (to <= from) return null
            val slice = source.text.substring(from, to)
            if (slice.isBlank()) return null

            val rebased = source.entities.mapNotNull { entity ->
                val overlapStart = maxOf(entity.offset, from)
                val overlapEnd = minOf(entity.end, to)
                if (overlapEnd <= overlapStart) return@mapNotNull null
                ComposerFormatting
                    .sanitise(
                        listOf(shift(entity, overlapStart - from, overlapEnd - overlapStart)),
                        slice.length
                    )
                    .firstOrNull()
            }
            return ReplyQuote(
                text = slice,
                position = from,
                formatted = AetherText(slice, rebased)
            )
        }

        private fun shift(entity: AetherEntity, offset: Int, length: Int): AetherEntity =
            when (entity) {
                is AetherEntity.Bold -> AetherEntity.Bold(offset, length)
                is AetherEntity.Italic -> AetherEntity.Italic(offset, length)
                is AetherEntity.Underline -> AetherEntity.Underline(offset, length)
                is AetherEntity.Strikethrough -> AetherEntity.Strikethrough(offset, length)
                is AetherEntity.Spoiler -> AetherEntity.Spoiler(offset, length)
                is AetherEntity.Code -> AetherEntity.Code(offset, length)
                // Anything else is either server-classified or unrepresentable in a
                // quote, and is dropped rather than approximated.
                else -> AetherEntity.Bold(offset, 0)
            }
    }
}
