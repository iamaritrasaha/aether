package com.foresightlabs.aether.domain.daily
import com.foresightlabs.aether.domain.daily.AetherDaily
import com.foresightlabs.aether.domain.daily.AetherDailyLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class AetherDailyTest {

    @Test
    fun corpusHasAtLeastAYearOfOriginalLines() {
        assertTrue(
            "Aether Daily needs at least 365 lines, has ${AetherDailyLines.all.size}",
            AetherDailyLines.all.size >= 365
        )
    }

    @Test
    fun theDailyPoolCoversAFullYearWithoutRepeating() {
        assertTrue(
            "daily pool is only ${AetherDailyLines.dailyPool.size}",
            AetherDailyLines.dailyPool.size >= 365
        )
    }

    @Test
    fun noDailyLineAssertsTheReadersTimeOfDay() {
        // A line chosen once per date must stay true from midnight to midnight.
        val hourWords = Regex(
            "\\b(morning|sunrise|dawn|afternoon|midday|noon|evening|sunset|dusk|" +
                "tonight|goodnight|good night|midnight|2am|asleep|wake up|woke up)\\b",
            RegexOption.IGNORE_CASE
        )
        val offenders = AetherDailyLines.dailyPool
            .map { it.text }
            .filter { hourWords.containsMatchIn(it) }
        assertTrue("hour-anchored lines in the daily pool: $offenders", offenders.isEmpty())
    }

    @Test
    fun everyLineIsDistinctAndShort() {
        val texts = AetherDailyLines.all.map { it.text }
        assertEquals("duplicate Aether Daily lines", texts.size, texts.toSet().size)
        val tooLong = texts.filter { it.length > 120 }
        assertTrue("micro-lines must stay short: $tooLong", tooLong.isEmpty())
        assertTrue(texts.none { it.isBlank() })
    }

    @Test
    fun lineIsStableForTheWholeLocalDay() {
        val zone = TimeZone.getTimeZone("Asia/Kolkata")
        val day = Calendar.getInstance(zone).apply {
            set(2026, Calendar.AUGUST, 26, 0, 0, 1)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = day.timeInMillis
        val expected = AetherDaily.lineForToday(startOfDay, zone)

        // Every hour of the same local day resolves to the same line.
        for (hour in 0..23) {
            val at = startOfDay + hour * 3_600_000L
            assertEquals(
                "Aether Daily changed at hour $hour",
                expected,
                AetherDaily.lineForToday(at, zone)
            )
        }
    }

    @Test
    fun lineChangesAtLocalMidnight() {
        val zone = TimeZone.getTimeZone("Asia/Kolkata")
        val day = AetherDaily.localEpochDay(0L, zone)
        // Consecutive days must not repeat, and the corpus must not walk in order.
        var repeats = 0
        for (offset in 0 until 2000) {
            val today = AetherDaily.lineForEpochDay(day + offset)
            val tomorrow = AetherDaily.lineForEpochDay(day + offset + 1)
            if (today == tomorrow) repeats++
        }
        assertEquals("consecutive days produced the same line", 0, repeats)
    }

    @Test
    fun selectionIsDeterministicAndInRange() {
        val size = AetherDailyLines.dailyPool.size
        for (day in -500L..500L) {
            val index = AetherDaily.indexForEpochDay(day, size)
            assertTrue("index $index out of range", index in 0 until size)
            assertEquals(index, AetherDaily.indexForEpochDay(day, size))
        }
    }

    @Test
    fun aFullCycleUsesEveryLineExactlyOnce() {
        val size = AetherDailyLines.dailyPool.size
        val seen = (0L until size.toLong()).map { AetherDaily.indexForEpochDay(it, size) }
        assertEquals("a full cycle must cover the whole corpus", size, seen.toSet().size)
    }

    @Test
    fun consecutiveDaysAreFarApartInTheCorpus() {
        val size = AetherDailyLines.dailyPool.size
        val gaps = (0L until 400L).map {
            val a = AetherDaily.indexForEpochDay(it, size)
            val b = AetherDaily.indexForEpochDay(it + 1, size)
            kotlin.math.abs(a - b)
        }
        // Never adjacent in the list, so the collection does not read in order.
        assertTrue("selection walked the corpus sequentially", gaps.none { it <= 1 })
    }

    @Test
    fun localEpochDayFollowsTheUsersTimeZone() {
        val kolkata = TimeZone.getTimeZone("Asia/Kolkata")   // UTC+5:30
        val honolulu = TimeZone.getTimeZone("Pacific/Honolulu") // UTC-10
        // 2026-08-26T19:00Z is already the 27th in Kolkata and still the 26th in Honolulu.
        val instant = 1787770800000L
        assertNotEquals(
            AetherDaily.localEpochDay(instant, kolkata),
            AetherDaily.localEpochDay(instant, honolulu)
        )
    }

    @Test
    fun millisUntilLocalMidnightIsWithinADay() {
        val zone = TimeZone.getTimeZone("Asia/Kolkata")
        val remaining = AetherDaily.millisUntilLocalMidnight(System.currentTimeMillis(), zone)
        assertTrue(remaining in 1..86_400_000L)
    }
}
