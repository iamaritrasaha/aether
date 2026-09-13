package com.foresightlabs.aether.domain.messages

import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.MessageType
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Same-sender runs: consecutive messages that read as one utterance.
 *
 * The rule set is deliberately narrow. Over-merging is the failure mode that
 * matters -- a forwarded line or a reply quote read as their own statement
 * regardless of who sent them, so they must never dissolve into a run.
 */
class MessageSenderRunsTest {

    private val zone: TimeZone = TimeZone.getTimeZone("UTC")

    // Derived, never hand-written: an epoch constant that is off by hours
    // silently tests the wrong day boundary.
    private val noonDay1: Int = secondsAt(2026, 9, 14, 12, 0)

    private fun secondsAt(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0): Int {
        val calendar = java.util.Calendar.getInstance(zone)
        calendar.set(year, month - 1, day, hour, minute, second)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return (calendar.timeInMillis / 1000L).toInt()
    }

    private fun text(
        id: String,
        senderId: String = "alice",
        isOutgoing: Boolean = false,
        dateSeconds: Int = noonDay1,
        type: MessageType = MessageType.TEXT,
        forwardedFrom: String? = null,
        hasReply: Boolean = false
    ) = Message(
        id = id,
        chatId = "100",
        senderId = senderId,
        senderName = senderId,
        text = "m$id",
        timestamp = "12:00",
        dateSeconds = dateSeconds,
        isOutgoing = isOutgoing,
        type = type,
        forwardedFrom = forwardedFrom,
        replyPreview = if (hasReply) {
            com.foresightlabs.aether.domain.model.ReplyPreview(
                chatId = 100L, messageId = 1L, senderName = "Bob", text = "earlier"
            )
        } else {
            null
        }
    )

    private fun run(entries: List<ConversationEntry>) = MessageGrouping.senderRuns(entries, zone)

    @Test
    fun consecutiveSameSenderTextsFormOneRun() {
        val entries = MessageGrouping.group(
            listOf(text("1"), text("2"), text("3"))
        )
        val positions = run(entries)
        assertEquals(3, positions.size)
        assertTrue(positions.getValue("1").isFirstOfRun)
        assertFalse(positions.getValue("1").isLastOfRun)
        assertFalse(positions.getValue("2").isFirstOfRun)
        assertFalse(positions.getValue("2").isLastOfRun)
        assertFalse(positions.getValue("3").isFirstOfRun)
        assertTrue(positions.getValue("3").isLastOfRun)
    }

    @Test
    fun differentSendersNeverMerge() {
        val entries = MessageGrouping.group(
            listOf(text("1", senderId = "alice"), text("2", senderId = "bob"))
        )
        val positions = run(entries)
        // Each is a run of one -- head and tail, i.e. the untouched shape.
        assertTrue(positions.getValue("1").isFirstOfRun)
        assertTrue(positions.getValue("1").isLastOfRun)
        assertTrue(positions.getValue("2").isFirstOfRun)
        assertTrue(positions.getValue("2").isLastOfRun)
    }

    @Test
    fun outgoingAndIncomingFromTheSameIdentityDoNotMerge() {
        // senderId "1" for both, but one is the account's own message.
        val entries = MessageGrouping.group(
            listOf(text("1", senderId = "1"), text("2", senderId = "1", isOutgoing = true))
        )
        val positions = run(entries)
        assertTrue(positions.getValue("1").isFirstOfRun)
        assertTrue(positions.getValue("1").isLastOfRun)
        assertTrue(positions.getValue("2").isFirstOfRun)
        assertTrue(positions.getValue("2").isLastOfRun)
    }

    @Test
    fun aGapBeyondTheThresholdBreaksTheRun() {
        val entries = MessageGrouping.group(
            listOf(
                text("1", dateSeconds = noonDay1),
                text("2", dateSeconds = noonDay1 + MessageGrouping.DEFAULT_RUN_GAP_SECONDS + 1)
            )
        )
        val positions = run(entries)
        assertTrue(positions.getValue("1").isLastOfRun)
        assertTrue(positions.getValue("2").isFirstOfRun)
    }

    @Test
    fun aGapWithinTheThresholdKeepsTheRun() {
        val entries = MessageGrouping.group(
            listOf(
                text("1", dateSeconds = noonDay1),
                text("2", dateSeconds = noonDay1 + MessageGrouping.DEFAULT_RUN_GAP_SECONDS)
            )
        )
        val positions = run(entries)
        assertFalse(positions.getValue("1").isLastOfRun)
        assertFalse(positions.getValue("2").isFirstOfRun)
    }

    @Test
    fun aMidnightBoundaryBreaksTheRunEvenWhenTheClockGapIsSmall() {
        val justBeforeMidnight = secondsAt(2026, 9, 14, 23, 59, 30)
        val justAfterMidnight = secondsAt(2026, 9, 15, 0, 0, 30)
        val entries = MessageGrouping.group(
            listOf(text("1", dateSeconds = justBeforeMidnight), text("2", dateSeconds = justAfterMidnight))
        )
        val positions = run(entries)
        assertTrue(positions.getValue("1").isLastOfRun)
        assertTrue(positions.getValue("2").isFirstOfRun)
    }

    @Test
    fun forwardedMessagesBreakTheRunAroundThem() {
        val entries = MessageGrouping.group(
            listOf(
                text("1"),
                text("2", forwardedFrom = "Carol"),
                text("3")
            )
        )
        val positions = run(entries)
        assertTrue(positions.getValue("1").isLastOfRun)
        assertNull(positions["2"])
        assertTrue(positions.getValue("3").isFirstOfRun)
        assertTrue(positions.getValue("3").isLastOfRun)
    }

    @Test
    fun replyQuotesBreakTheRunAroundThem() {
        val entries = MessageGrouping.group(
            listOf(text("1"), text("2", hasReply = true), text("3"))
        )
        val positions = run(entries)
        assertTrue(positions.getValue("1").isLastOfRun)
        assertNull(positions["2"])
        assertTrue(positions.getValue("3").isFirstOfRun)
    }

    @Test
    fun mediaAndServiceMessagesBreakTheRunAroundThem() {
        val entries = MessageGrouping.group(
            listOf(
                text("1"),
                text("2", type = MessageType.IMAGE),
                text("3", type = MessageType.SERVICE),
                text("4")
            )
        )
        val positions = run(entries)
        assertTrue(positions.getValue("1").isLastOfRun)
        assertNull(positions["2"])
        assertNull(positions["3"])
        assertTrue(positions.getValue("4").isFirstOfRun)
        assertTrue(positions.getValue("4").isLastOfRun)
    }

    @Test
    fun linkPreviewMessagesJoinTheirSendersRun() {
        val entries = MessageGrouping.group(
            listOf(text("1"), text("2", type = MessageType.LINK_PREVIEW))
        )
        val positions = run(entries)
        assertFalse(positions.getValue("1").isLastOfRun)
        assertFalse(positions.getValue("2").isFirstOfRun)
    }

    @Test
    fun albumsSitBetweenTwoRunsOfTheSameSender() {
        val photo = { id: String ->
            text(id, type = MessageType.IMAGE).copy(mediaAlbumId = 5L)
        }
        val entries = MessageGrouping.group(
            listOf(text("1"), photo("2"), photo("3"), text("4"))
        )
        val positions = run(entries)
        assertTrue(positions.getValue("1").isFirstOfRun)
        assertTrue(positions.getValue("1").isLastOfRun)
        assertNull(positions["2"])
        assertNull(positions["3"])
        assertTrue(positions.getValue("4").isFirstOfRun)
        assertTrue(positions.getValue("4").isLastOfRun)
    }

    @Test
    fun twoRunsFromTheSameSenderWithAnInterruptionBetweenStayApart() {
        val entries = MessageGrouping.group(
            listOf(text("1"), text("2", senderId = "bob"), text("3"))
        )
        val positions = run(entries)
        assertTrue(positions.getValue("1").isFirstOfRun)
        assertTrue(positions.getValue("1").isLastOfRun)
        assertTrue(positions.getValue("3").isFirstOfRun)
        assertTrue(positions.getValue("3").isLastOfRun)
    }

    @Test
    fun aShortConversationNeedsNoRuns() {
        assertTrue(run(MessageGrouping.group(listOf(text("1")))).isEmpty())
        assertTrue(run(emptyList()).isEmpty())
    }
}
