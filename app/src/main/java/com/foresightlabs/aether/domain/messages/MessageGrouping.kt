package com.foresightlabs.aether.domain.messages

import androidx.compose.runtime.Immutable
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.MessageType
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

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
        override val key: String get() = message.presentationKey ?: message.id
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

    /** Where a message sits inside a same-sender run of consecutive messages. */
    @Immutable
    data class SenderRunPosition(
        val isFirstOfRun: Boolean,
        val isLastOfRun: Boolean
    )

    /**
     * Which consecutive messages read as one sender's utterance.
     *
     * Two adjacent messages join a run only when they come from the same sender
     * in the same direction, are no more than [maxGapSeconds] apart, fall on the
     * same calendar day, and neither carries its own identity header: a
     * forwarded label, a reply quote, a service or call line, or non-text
     * content each break the run, because they are read as their own statement
     * regardless of who sent them. Everything the bubble renders as attached
     * chrome (a reaction, an edit stamp) does NOT break the run.
     *
     * Albums never participate: an album is already one row with its own
     * anchor, and merging a text bubble into it would misstate the grouping
     * Telegram itself made.
     *
     * Returns positions keyed by message id; ids not present stand alone and
     * the bubble renders exactly as it does today.
     */
    fun senderRuns(
        entries: List<ConversationEntry>,
        zone: TimeZone,
        maxGapSeconds: Int = DEFAULT_RUN_GAP_SECONDS
    ): Map<String, SenderRunPosition> {
        if (entries.size < 2) return emptyMap()
        val positions = HashMap<String, SenderRunPosition>()
        var runIds = mutableListOf<String>()
        var previous: ConversationEntry.Single? = null

        fun flush() {
            if (runIds.isEmpty()) return
            for ((i, id) in runIds.withIndex()) {
                positions[id] = SenderRunPosition(isFirstOfRun = i == 0, isLastOfRun = i == runIds.lastIndex)
            }
            runIds = mutableListOf()
        }

        for (entry in entries) {
            val current = entry as? ConversationEntry.Single
            if (current == null || !joinsIntoRun(current)) {
                flush()
                previous = null
                continue
            }
            val continues = previous != null && joinsRun(previous!!, current, zone, maxGapSeconds)
            if (!continues) flush()
            runIds += current.message.id
            previous = current
        }
        flush()
        return positions
    }

    /** Whether [current] continues a run that [previous] is already part of. */
    private fun joinsRun(
        previous: ConversationEntry.Single,
        current: ConversationEntry.Single,
        zone: TimeZone,
        maxGapSeconds: Int
    ): Boolean {
        val a = previous.message
        val b = current.message
        if (a.senderId != b.senderId || a.isOutgoing != b.isOutgoing) return false
        val gap = b.dateSeconds - a.dateSeconds
        if (gap < 0 || gap > maxGapSeconds) return false
        return dayNumber(a.dateSeconds, zone) == dayNumber(b.dateSeconds, zone)
    }

    /** Same calendar day, Calendar-based so it is safe on every supported API level. */
    private fun dayNumber(dateSeconds: Int, zone: TimeZone): Int {
        val calendar = Calendar.getInstance(zone, Locale.getDefault())
        calendar.timeInMillis = dateSeconds * 1000L
        return calendar.get(Calendar.YEAR) * 10_000 +
            (calendar.get(Calendar.MONTH) + 1) * 100 +
            calendar.get(Calendar.DAY_OF_MONTH)
    }

    /** Whether a message may join a run at all (as head, tail or middle). */
    private fun joinsIntoRun(entry: ConversationEntry.Single): Boolean {
        val message = entry.message
        if (message.forwardedFrom != null) return false
        if (message.replyPreview != null) return false
        return when (message.type) {
            MessageType.TEXT, MessageType.LINK_PREVIEW -> true
            else -> false
        }
    }

    /** Sender messages further apart than this read as separate utterances. */
    const val DEFAULT_RUN_GAP_SECONDS = 300
}
