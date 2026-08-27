package com.foresightlabs.aether.domain.messages

import androidx.compose.runtime.Immutable
import com.foresightlabs.aether.domain.model.Message

/**
 * One row of a conversation: either a single message, or an album of them.
 *
 * Albums exist because Telegram sends grouped media as several messages that are
 * meant to be read as one. Rendering each as its own full-width bubble is not just
 * ugly — it misrepresents what was sent, and it multiplies the caption.
 */
@Immutable
sealed interface ConversationEntry {
    val key: String

    /** The message that anchors this entry, for actions and ordering. */
    val anchor: Message

    @Immutable
    data class Single(val message: Message) : ConversationEntry {
        override val key: String get() = message.id
        override val anchor: Message get() = message
    }

    @Immutable
    data class Album(
        val albumId: Long,
        val messages: List<Message>
    ) : ConversationEntry {
        override val key: String get() = "album_$albumId"

        /** The oldest message in the album, which is the one Telegram captions. */
        override val anchor: Message get() = messages.first()

        /**
         * The album's caption.
         *
         * Telegram puts the caption on exactly one member, so the first non-blank
         * one is the caption for the whole group — concatenating them all would
         * repeat text the sender wrote once.
         */
        val caption: String
            get() = messages.firstOrNull { it.text.isNotBlank() }?.text.orEmpty()
    }
}

object MessageGrouping {

    /**
     * Groups a conversation into rows, collapsing albums.
     *
     * Only *adjacent* messages are grouped. Telegram guarantees album members arrive
     * consecutively, and requiring adjacency means a message that happens to reuse an
     * id after a gap cannot pull two unrelated clusters together.
     */
    fun group(messages: List<Message>): List<ConversationEntry> {
        if (messages.isEmpty()) return emptyList()
        val entries = mutableListOf<ConversationEntry>()
        var index = 0
        while (index < messages.size) {
            val current = messages[index]
            val albumId = current.mediaAlbumId
            if (albumId == 0L) {
                entries += ConversationEntry.Single(current)
                index++
                continue
            }
            val run = mutableListOf(current)
            var next = index + 1
            while (next < messages.size && messages[next].mediaAlbumId == albumId) {
                run += messages[next]
                next++
            }
            entries += if (run.size == 1) {
                // A lone member is not an album — it is one message that happens to
                // carry a grouping id, which is what a partly-deleted album leaves.
                ConversationEntry.Single(run.single())
            } else {
                ConversationEntry.Album(albumId, run.toList())
            }
            index = next
        }
        return entries
    }
}
