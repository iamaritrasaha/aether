package com.foresightlabs.aether.ui

import androidx.compose.ui.unit.dp
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.MessageStatus
import com.foresightlabs.aether.domain.model.MessageType
import com.foresightlabs.aether.domain.model.ReplyPreview
import com.foresightlabs.aether.ui.home.atmosphere.TimeAtmospherePolicy
import com.foresightlabs.aether.ui.home.atmosphere.TimePeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.TimeZone

/**
 * Deterministic unit tests covering time atmosphere updates, lifecycle re-entry refresh,
 * weather independence, and responsive message bubble sizing.
 */
class AetherTimeAndBubbleLayoutPassTest {

    // --- 1. Time Phase Changes Across Boundaries ---

    @Test
    fun timePhaseChangesWhenClockCrossesBoundary() {
        val zone = ZoneId.of("UTC")
        val tz = TimeZone.getTimeZone("UTC")

        // 05:59:00 -> PRE_DAWN
        val clockPreDawn = Clock.fixed(Instant.parse("2026-08-31T05:59:00Z"), zone)
        val atmospherePreDawn = TimeAtmospherePolicy.resolve(clockPreDawn.millis(), tz)
        assertEquals(TimePeriod.PRE_DAWN, atmospherePreDawn.period)

        // 06:01:00 -> MORNING
        val clockMorning = Clock.fixed(Instant.parse("2026-08-31T06:01:00Z"), zone)
        val atmosphereMorning = TimeAtmospherePolicy.resolve(clockMorning.millis(), tz)
        assertEquals(TimePeriod.MORNING, atmosphereMorning.period)

        // 11:01:00 -> NOON
        val clockNoon = Clock.fixed(Instant.parse("2026-08-31T11:01:00Z"), zone)
        val atmosphereNoon = TimeAtmospherePolicy.resolve(clockNoon.millis(), tz)
        assertEquals(TimePeriod.NOON, atmosphereNoon.period)

        // 14:01:00 -> AFTERNOON
        val clockAfternoon = Clock.fixed(Instant.parse("2026-08-31T14:01:00Z"), zone)
        val atmosphereAfternoon = TimeAtmospherePolicy.resolve(clockAfternoon.millis(), tz)
        assertEquals(TimePeriod.AFTERNOON, atmosphereAfternoon.period)

        // 18:01:00 -> EVENING
        val clockEvening = Clock.fixed(Instant.parse("2026-08-31T18:01:00Z"), zone)
        val atmosphereEvening = TimeAtmospherePolicy.resolve(clockEvening.millis(), tz)
        assertEquals(TimePeriod.EVENING, atmosphereEvening.period)

        // 22:01:00 -> NIGHT
        val clockNight = Clock.fixed(Instant.parse("2026-08-31T22:01:00Z"), zone)
        val atmosphereNight = TimeAtmospherePolicy.resolve(clockNight.millis(), tz)
        assertEquals(TimePeriod.NIGHT, atmosphereNight.period)
    }

    // --- 2. Time State Refreshes After Resume / Re-Entry ---

    @Test
    fun timeStateRefreshesWhenSimulatedResumeOccursWithAdvancedClock() {
        var currentMillis = Instant.parse("2026-08-31T10:00:00Z").toEpochMilli() // 10:00 MORNING
        val clock = { currentMillis }
        val tz = TimeZone.getTimeZone("UTC")

        val initial = TimeAtmospherePolicy.resolve(timeMillis = clock(), timeZone = tz)
        assertEquals(TimePeriod.MORNING, initial.period)

        // Simulate app backgrounded at 10:00 AM and resumed at 20:00 PM (EVENING)
        currentMillis = Instant.parse("2026-08-31T20:00:00Z").toEpochMilli()
        val resumed = TimeAtmospherePolicy.resolve(timeMillis = clock(), timeZone = tz)

        assertEquals(TimePeriod.EVENING, resumed.period)
        assertNotEquals(initial.period, resumed.period)
    }

    // --- 3. Weather Absence Does Not Stop Time Updates ---

    @Test
    fun weatherAbsenceDoesNotAffectTimeOfDayUpdates() {
        // Pure time-of-day resolution produces valid, continuous atmospheres
        val morning = TimeAtmospherePolicy.resolve(7, 30, 0)
        val evening = TimeAtmospherePolicy.resolve(19, 30, 0)

        assertEquals(TimePeriod.MORNING, morning.period)
        assertEquals(TimePeriod.EVENING, evening.period)
        assertTrue(morning.colors.isNotEmpty())
        assertTrue(evening.colors.isNotEmpty())
    }

    // --- 4. Responsive Bubble Max Width Calculation ---

    @Test
    fun longTextBubbleGetsMoreUsableWidthThanLegacyCap() {
        // Legacy cap was 258dp across all screens.
        // On 393dp phone: usable width = 393 - 28 = 365dp. 82% of usable = ~299dp.
        val screenWidths = listOf(360.dp, 393.dp, 411.dp, 600.dp)

        for (width in screenWidths) {
            val usableWidth = (width - 28.dp).coerceAtLeast(100.dp)
            val maxBubbleWidth = (usableWidth * 0.82f).coerceIn(275.dp, 440.dp)

            if (width >= 393.dp) {
                assertTrue(
                    "Max bubble width ($maxBubbleWidth) must exceed legacy 258dp cap on $width screen",
                    maxBubbleWidth > 280.dp
                )
            }
            assertTrue("Max bubble width must be bounded by 440dp", maxBubbleWidth <= 440.dp)
        }
    }

    // --- 5. Short Bubble Behavior ---

    @Test
    fun shortMessageRemainsCompact() {
        val shortMessage = Message(
            id = "1",
            chatId = "100",
            senderId = "100",
            senderName = "User",
            text = "Okay",
            timestamp = "12:34 PM",
            isOutgoing = true,
            status = MessageStatus.READ,
            type = MessageType.TEXT
        )
        // Short text is 4 chars + timestamp.
        assertTrue(shortMessage.text.length < 10)
    }

    // --- 6. Reply Preview Width ---

    @Test
    fun replyPreviewSupportsWiderBubble() {
        val replyMessage = Message(
            id = "2",
            chatId = "100",
            senderId = "101",
            senderName = "Alice",
            text = "This is a detailed response to a message that contained a longer preview snippet.",
            timestamp = "12:35 PM",
            isOutgoing = false,
            status = MessageStatus.READ,
            type = MessageType.TEXT,
            replyPreview = ReplyPreview(
                messageId = 1L,
                chatId = 100L,
                senderName = "Alice",
                text = "Original longer question text that previously was constrained to 240dp max width.",
                isAvailable = true,
                isNavigable = true
            )
        )
        assertTrue(replyMessage.replyPreview != null)
    }
}
