package com.foresightlabs.aether.domain.messages

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * What the message list lays out: a message entry, or a boundary between two
 * calendar days of the conversation.
 *
 * Dividers carry the epoch seconds of the day they open -- not a preformatted
 * label -- so formatting stays a UI concern and this model stays trivially
 * testable against plain timestamps.
 */
sealed interface ConversationRow {
    val key: String

    /** Opens a calendar day. [dateSeconds] belongs to that day's first entry. */
    data class DayDivider(val dateSeconds: Int) : ConversationRow {
        override val key: String get() = "day_$dateSeconds"
    }

    data class Entry(val entry: ConversationEntry) : ConversationRow {
        override val key: String get() = entry.key
    }

    /**
     * Marks where previously-unread incoming traffic begins. Session-local:
     * the screen decides where it goes and when it retires; the server is
     * never told anything because of it.
     */
    data object UnreadBoundary : ConversationRow {
        override val key: String get() = "unread_boundary"
    }
}

object ConversationRows {

    /**
     * Inserts a [ConversationRow.UnreadBoundary] immediately before the entry
     * whose key is [boundaryEntryKey], or returns [rows] unchanged when null or
     * when that entry is not in the materialized window.
     */
    fun withUnreadBoundary(rows: List<ConversationRow>, boundaryEntryKey: String?): List<ConversationRow> {
        if (boundaryEntryKey == null) return rows
        val index = rows.indexOfFirst { it is ConversationRow.Entry && it.entry.key == boundaryEntryKey }
        if (index <= 0) return rows
        return rows.toMutableList().apply { add(index, ConversationRow.UnreadBoundary) }
    }

    /**
     * Interleaves day dividers into the entry list, one before the first entry
     * of each distinct calendar day.
     *
     * The first entry is always preceded by a divider, including when every
     * loaded message is from one day -- the top of the list then honestly
     * labels the day the loaded window begins on, rather than implying the
     * conversation started "Today".
     */
    fun withDayDividers(entries: List<ConversationEntry>, zone: TimeZone): List<ConversationRow> {
        if (entries.isEmpty()) return emptyList()
        val rows = mutableListOf<ConversationRow>()
        var currentDay: Int? = null
        for (entry in entries) {
            val day = dayNumber(entryDaySeconds(entry), zone)
            if (day != currentDay) {
                currentDay = day
                rows += ConversationRow.DayDivider(entryDaySeconds(entry))
            }
            rows += ConversationRow.Entry(entry)
        }
        return rows
    }

    /** The timestamp that decides which day an entry belongs to. */
    fun entryDaySeconds(entry: ConversationEntry): Int = when (entry) {
        is ConversationEntry.Single -> entry.message.dateSeconds
        is ConversationEntry.Album -> entry.anchor.dateSeconds
    }

    /**
     * Human label for a day boundary: "Today" / "Yesterday" for the two most
     * recent days, a bare weekday within the past week, "MMM d" within the
     * same year, and "MMM d, yyyy" beyond that.
     *
     * [nowSeconds] and [zone] are injected so tests (and any future
     * non-default zone) resolve labels the same way the running app does.
     */
    fun dayLabel(dateSeconds: Int, nowSeconds: Int, zone: TimeZone): String {
        val day = dayNumber(dateSeconds, zone)
        val today = dayNumber(nowSeconds, zone)
        return when {
            day == today -> "Today"
            day == today - 1 -> "Yesterday"
            day > today - 7 -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(dateSeconds * 1000L))
            // The year matches the label's own day, read in the same zone.
            SimpleDateFormat("yyyy", Locale.getDefault()).format(Date(dateSeconds * 1000L)) ==
                SimpleDateFormat("yyyy", Locale.getDefault()).format(Date(nowSeconds * 1000L)) ->
                SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(dateSeconds * 1000L))
            else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(dateSeconds * 1000L))
        }
    }

    /**
     * Days-since-epoch style ordinal of [dateSeconds] in [zone] -- equality of
     * two ordinals is "same calendar day". Calendar-based so it is safe on
     * every supported API level (java.time needs 26 or desugaring).
     */
    private fun dayNumber(dateSeconds: Int, zone: TimeZone): Int {
        val calendar = Calendar.getInstance(zone, Locale.getDefault())
        calendar.timeInMillis = dateSeconds * 1000L
        return calendar.get(Calendar.YEAR) * 10_000 +
            (calendar.get(Calendar.MONTH) + 1) * 100 +
            calendar.get(Calendar.DAY_OF_MONTH)
    }
}
