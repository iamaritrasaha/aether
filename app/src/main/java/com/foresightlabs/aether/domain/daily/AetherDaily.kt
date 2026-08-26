package com.foresightlabs.aether.domain.daily

import java.util.Calendar
import java.util.TimeZone

/**
 * Aether Daily: one line per local calendar day.
 *
 * Guarantees:
 * - fully offline, no quote API, no third-party text
 * - deterministic for a given local date
 * - identical for the entire day, changing exactly at local midnight
 *
 * Selection is seeded by the local date alone, never by the current atmosphere.
 * Category is a property of the corpus (it keeps the collection varied in tone),
 * not an input to the pick — biasing the pick by atmosphere would change the line
 * at each palette boundary, which contradicts the stable-for-the-day requirement.
 */
object AetherDaily {

    private const val MILLIS_PER_DAY = 86_400_000L

    fun lineForToday(
        now: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): DailyLine = lineForEpochDay(localEpochDay(now, timeZone))

    fun lineForEpochDay(epochDay: Long): DailyLine {
        val lines = AetherDailyLines.dailyPool
        return lines[indexForEpochDay(epochDay, lines.size)]
    }

    /**
     * Days since the epoch as observed in [timeZone], so the line turns over at the
     * user's midnight rather than UTC midnight.
     */
    fun localEpochDay(
        now: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = now }
        val offset = calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET)
        return Math.floorDiv(now + offset, MILLIS_PER_DAY)
    }

    /**
     * Maps a day to a line by walking a full-cycle permutation of the corpus.
     *
     * Because [strideFor] is coprime with the corpus size, each cycle of `size` days
     * visits every line exactly once — so nothing repeats until the whole collection
     * has been used — while the large stride keeps consecutive days far apart in the
     * list. Each cycle is rotated by one so successive cycles are not identical, and
     * the stride excludes `size - 1`, which is the only value that could make a line
     * repeat across a cycle boundary.
     */
    fun indexForEpochDay(epochDay: Long, size: Int): Int {
        require(size > 0) { "Aether Daily corpus is empty" }
        if (size == 1) return 0
        val stride = strideFor(size)
        val cycle = Math.floorDiv(epochDay, size.toLong())
        val position = Math.floorMod(epochDay, size.toLong())
        return Math.floorMod(position * stride + cycle, size.toLong()).toInt()
    }

    private val STRIDE_CANDIDATES = longArrayOf(
        137, 131, 127, 113, 101, 97, 89, 83, 79, 73, 71, 67, 61, 59, 53, 47, 43, 41,
        37, 31, 29, 23, 19, 17, 13, 11, 7, 5, 3, 2
    )

    private fun strideFor(size: Int): Long {
        val candidate = STRIDE_CANDIDATES.firstOrNull { stride ->
            stride < size && stride != size - 1L && gcd(stride, size.toLong()) == 1L
        }
        return candidate ?: 1L
    }

    private tailrec fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)

    /** Milliseconds until the local calendar date changes. */
    fun millisUntilLocalMidnight(
        now: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        val calendar = Calendar.getInstance(timeZone).apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return (calendar.timeInMillis - now).coerceAtLeast(1L)
    }
}
