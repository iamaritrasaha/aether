package com.foresightlabs.aether.domain.messages

import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.MessageType
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Day dividers in the conversation stream.
 *
 * A conversation rendered without calendar boundaries makes yesterday's plan
 * read like today's answer. The dividers must appear exactly when the
 * calendar day of the stream changes, and never depend on anything but the
 * messages' own timestamps.
 */
class ConversationRowsTest {

    // Fixed, DST-free zone so day boundaries are exact in every environment.
    private val zone: TimeZone = TimeZone.getTimeZone("UTC")

    private fun nowSeconds(): Int = secondsAt(12, 14)

    private fun secondsAt(hour: Int, day: Int, month: Int = 9): Int =
        calendarSeconds(zone, 2026, month, day, hour, 0)

    private fun calendarSeconds(
        zone: java.util.TimeZone,
        year: Int, month: Int, day: Int, hour: Int, minute: Int
    ): Int {
        val calendar = java.util.Calendar.getInstance(zone)
        calendar.set(year, month - 1, day, hour, minute, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return (calendar.timeInMillis / 1000L).toInt()
    }

    private fun message(id: String, dateSeconds: Int) = Message(
        id = id,
        chatId = "100",
        senderId = "1",
        senderName = "Sam",
        text = "m$id",
        timestamp = "12:00",
        dateSeconds = dateSeconds,
        isOutgoing = false,
        type = MessageType.TEXT
    )

    private fun entriesOf(vararg messages: Message) = MessageGrouping.group(messages.toList())

    @Test
    fun theFirstEntryIsAlwaysPrecededByADivider() {
        val rows = ConversationRows.withDayDividers(
            entriesOf(message("1", secondsAt(10, 14))),
            zone
        )
        assertEquals(2, rows.size)
        assertTrue(rows[0] is ConversationRow.DayDivider)
        assertTrue(rows[1] is ConversationRow.Entry)
    }

    @Test
    fun sameDayMessagesShareOneDivider() {
        val rows = ConversationRows.withDayDividers(
            entriesOf(
                message("1", secondsAt(9, 14)),
                message("2", secondsAt(10, 14)),
                message("3", secondsAt(23, 14))
            ),
            zone
        )
        assertEquals(4, rows.size)
        assertEquals(1, rows.count { it is ConversationRow.DayDivider })
    }

    @Test
    fun aDayChangeInsertsExactlyOneDivider() {
        val rows = ConversationRows.withDayDividers(
            entriesOf(
                message("1", secondsAt(23, 13)),
                message("2", secondsAt(0, 14))
            ),
            zone
        )
        assertEquals(4, rows.size)
        assertEquals(2, rows.count { it is ConversationRow.DayDivider })
        // The second divider carries the later day's timestamp.
        val divider = rows.last { it is ConversationRow.DayDivider } as ConversationRow.DayDivider
        assertEquals(secondsAt(0, 14), divider.dateSeconds)
    }

    @Test
    fun dividersSplitEvenWhenSecondsAreEqualAcrossMidnightIsImpossible() {
        // Two messages, one minute apart, same day: one divider only.
        val rows = ConversationRows.withDayDividers(
            entriesOf(
                message("1", secondsAt(9, 1)),
                message("2", secondsAt(9, 1) + 60)
            ),
            zone
        )
        assertEquals(1, rows.count { it is ConversationRow.DayDivider })
    }

    @Test
    fun albumMembersSpanningMidnightDivideByTheirAnchorsDay() {
        // An album's anchor decides the row's day, since the album is one row.
        val entries = MessageGrouping.group(
            listOf(
                message("1", secondsAt(23, 13)).copy(mediaAlbumId = 7L),
                message("2", secondsAt(0, 14)).copy(mediaAlbumId = 7L)
            )
        )
        val rows = ConversationRows.withDayDividers(entries, zone)
        // One album row preceded by one divider -- the members' day difference
        // is internal to the album and does not split it.
        assertEquals(2, rows.size)
        assertEquals(secondsAt(23, 13), (rows.first() as ConversationRow.DayDivider).dateSeconds)
    }

    @Test
    fun entriesWithoutDatesAllShareOneDivider() {
        // dateSeconds defaults to 0 (epoch) for anything not carrying one.
        val rows = ConversationRows.withDayDividers(
            entriesOf(message("1", 0), message("2", 0)),
            zone
        )
        assertEquals(3, rows.size)
        assertEquals(1, rows.count { it is ConversationRow.DayDivider })
    }

    @Test
    fun anEmptyConversationHasNoRows() {
        assertTrue(ConversationRows.withDayDividers(emptyList(), zone).isEmpty())
    }

    @Test
    fun dividerKeysAreStableAndDistinctFromEntryKeys() {
        val rows = ConversationRows.withDayDividers(
            entriesOf(
                message("1", secondsAt(9, 13)),
                message("2", secondsAt(9, 14))
            ),
            zone
        )
        val keys = rows.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(keys.none { it.startsWith("day_") && it == rows.last().key })
    }

    // --- unread boundary ------------------------------------------------------

    @Test
    fun unreadBoundaryInsertsBeforeItsEntry() {
        val rows = ConversationRows.withDayDividers(
            entriesOf(
                message("1", secondsAt(9, 13)),
                message("2", secondsAt(9, 14))
            ),
            zone
        )
        val withBoundary = ConversationRows.withUnreadBoundary(rows, MessageGrouping.group(
            listOf(message("2", secondsAt(9, 14)))
        ).single().key)
        // [day13, entry1, day14, UNREAD, entry2]
        assertEquals(5, withBoundary.size)
        assertTrue(withBoundary[3] is ConversationRow.UnreadBoundary)
        assertEquals("The entry the boundary marks follows it",
            ConversationRow.Entry::class, withBoundary[4]::class)
    }

    @Test
    fun unreadBoundaryWithoutAWindowInsertsNothing() {
        val rows = ConversationRows.withDayDividers(
            entriesOf(message("1", secondsAt(9, 14))), zone
        )
        assertEquals(rows, ConversationRows.withUnreadBoundary(rows, "nope"))
        assertEquals(rows, ConversationRows.withUnreadBoundary(rows, null))
    }

    @Test
    fun unreadBoundaryKeysStayUnique() {
        val rows = ConversationRows.withDayDividers(
            entriesOf(
                message("1", secondsAt(9, 13)),
                message("2", secondsAt(9, 14))
            ),
            zone
        )
        val withBoundary = ConversationRows.withUnreadBoundary(rows, MessageGrouping.group(
            listOf(message("2", secondsAt(9, 14)))
        ).single().key)
        val keys = withBoundary.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    // --- day labels -----------------------------------------------------------

    @Test
    fun labelsCoverTodayYesterdayWeekdayMonthAndYear() {
        val nowSeconds = nowSeconds()
        assertEquals("Today", ConversationRows.dayLabel(nowSeconds, nowSeconds, zone))
        assertEquals(
            "Yesterday",
            ConversationRows.dayLabel(nowSeconds - 86_400, nowSeconds, zone)
        )
        // Three days ago: a bare weekday. (2026-09-11 is a Friday; the test's
        // "now" is Monday 2026-09-14.)
        assertEquals(
            "Friday",
            ConversationRows.dayLabel(secondsAt(12, 11), nowSeconds, zone)
        )
        // Earlier this year: month and day.
        assertEquals(
            "Jul 2",
            ConversationRows.dayLabel(secondsAt(12, 2, 7), nowSeconds, zone)
        )
        // Another year: month, day and year.
        val decLastYear = calendarSeconds(zone, 2025, 12, 31, 12, 0)
        assertEquals(
            "Dec 31, 2025",
            ConversationRows.dayLabel(decLastYear, nowSeconds, zone)
        )
    }

    @Test
    fun lateNightLabelsFollowTheZoneNotUtc() {
        // One instant, two zones, two different labels. The message lands on
        // 2026-09-14 20:00 UTC -- 2026-09-15 05:00 in Tokyo. Read by a Tokyo
        // account it is Today; read by a UTC account it is Yesterday. The
        // label must follow the reader's zone, not the epoch.
        val tokyo = TimeZone.getTimeZone("Asia/Tokyo")
        val messageSeconds = calendarSeconds(TimeZone.getTimeZone("UTC"), 2026, 9, 14, 20, 0)
        val nowTokyo = calendarSeconds(tokyo, 2026, 9, 15, 23, 0)
        val nowUtc = calendarSeconds(TimeZone.getTimeZone("UTC"), 2026, 9, 15, 14, 0)
        assertEquals("Today", ConversationRows.dayLabel(messageSeconds, nowTokyo, tokyo))
        assertEquals("Yesterday", ConversationRows.dayLabel(messageSeconds, nowUtc, TimeZone.getTimeZone("UTC")))
    }
}
