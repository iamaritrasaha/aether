package com.foresightlabs.aether.ui.home.atmosphere

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.Calendar
import java.util.TimeZone

class TimeAtmospherePolicyTest {

    // --- 1. Every time period boundary & exact boundary behavior -----------------

    @Test
    fun exactPeriodBoundariesResolveCorrectly() {
        assertEquals(TimePeriod.PRE_DAWN, TimeAtmospherePolicy.resolve(3, 0, 0).period)
        assertEquals(TimePeriod.MORNING, TimeAtmospherePolicy.resolve(6, 0, 0).period)
        assertEquals(TimePeriod.NOON, TimeAtmospherePolicy.resolve(11, 0, 0).period)
        assertEquals(TimePeriod.AFTERNOON, TimeAtmospherePolicy.resolve(14, 0, 0).period)
        assertEquals(TimePeriod.EVENING, TimeAtmospherePolicy.resolve(18, 0, 0).period)
        assertEquals(TimePeriod.NIGHT, TimeAtmospherePolicy.resolve(22, 0, 0).period)
    }

    @Test
    fun exactBoundaryProgressStartsAtZero() {
        val preDawn = TimeAtmospherePolicy.resolve(3, 0, 0)
        assertEquals(0.0f, preDawn.progress, 0.0001f)
        assertEquals(TimePeriod.PRE_DAWN, preDawn.period)
        assertEquals(TimePeriod.MORNING, preDawn.nextPeriod)

        val morning = TimeAtmospherePolicy.resolve(6, 0, 0)
        assertEquals(0.0f, morning.progress, 0.0001f)
        assertEquals(TimePeriod.MORNING, morning.period)
        assertEquals(TimePeriod.NOON, morning.nextPeriod)

        val noon = TimeAtmospherePolicy.resolve(11, 0, 0)
        assertEquals(0.0f, noon.progress, 0.0001f)
        assertEquals(TimePeriod.NOON, noon.period)
        assertEquals(TimePeriod.AFTERNOON, noon.nextPeriod)

        val afternoon = TimeAtmospherePolicy.resolve(14, 0, 0)
        assertEquals(0.0f, afternoon.progress, 0.0001f)
        assertEquals(TimePeriod.AFTERNOON, afternoon.period)
        assertEquals(TimePeriod.EVENING, afternoon.nextPeriod)

        val evening = TimeAtmospherePolicy.resolve(18, 0, 0)
        assertEquals(0.0f, evening.progress, 0.0001f)
        assertEquals(TimePeriod.EVENING, evening.period)
        assertEquals(TimePeriod.NIGHT, evening.nextPeriod)

        val night = TimeAtmospherePolicy.resolve(22, 0, 0)
        assertEquals(0.0f, night.progress, 0.0001f)
        assertEquals(TimePeriod.NIGHT, night.period)
        assertEquals(TimePeriod.PRE_DAWN, night.nextPeriod)
    }

    // --- 2. Midnight rollover ---------------------------------------------------

    @Test
    fun midnightRolloverIsContinuousAndSmooth() {
        val beforeMidnight = TimeAtmospherePolicy.resolve(23, 59, 59)
        val atMidnight = TimeAtmospherePolicy.resolve(0, 0, 0)
        val afterMidnight = TimeAtmospherePolicy.resolve(2, 59, 59)

        assertEquals(TimePeriod.NIGHT, beforeMidnight.period)
        assertEquals(TimePeriod.PRE_DAWN, beforeMidnight.nextPeriod)
        assertEquals(TimePeriod.NIGHT, atMidnight.period)
        assertEquals(TimePeriod.PRE_DAWN, atMidnight.nextPeriod)
        assertEquals(TimePeriod.NIGHT, afterMidnight.period)

        // 22:00 to 03:00 is 5 hours (18,000s).
        // 23:59:59 is 7199s in -> 7199/18000 = ~0.39994
        // 00:00:00 is 7200s in -> 7200/18000 = 0.40000
        assertEquals(0.4000f, atMidnight.progress, 0.0001f)
        assertTrue(beforeMidnight.progress < atMidnight.progress)
        assertTrue(atMidnight.progress < afterMidnight.progress)
        assertEquals(1.0f, afterMidnight.progress, 0.0002f)
    }

    // --- 3 - 8. Specific Transitions --------------------------------------------

    @Test
    fun transitionPreDawnToMorning() {
        val halfway = TimeAtmospherePolicy.resolve(4, 30, 0)
        assertEquals(TimePeriod.PRE_DAWN, halfway.period)
        assertEquals(TimePeriod.MORNING, halfway.nextPeriod)
        assertEquals(0.5f, halfway.progress, 0.0001f)
    }

    @Test
    fun transitionMorningToNoon() {
        val halfway = TimeAtmospherePolicy.resolve(8, 30, 0)
        assertEquals(TimePeriod.MORNING, halfway.period)
        assertEquals(TimePeriod.NOON, halfway.nextPeriod)
        assertEquals(0.5f, halfway.progress, 0.0001f)
    }

    @Test
    fun transitionNoonToAfternoon() {
        val halfway = TimeAtmospherePolicy.resolve(12, 30, 0)
        assertEquals(TimePeriod.NOON, halfway.period)
        assertEquals(TimePeriod.AFTERNOON, halfway.nextPeriod)
        assertEquals(0.5f, halfway.progress, 0.0001f)
    }

    @Test
    fun transitionAfternoonToEvening() {
        val halfway = TimeAtmospherePolicy.resolve(16, 0, 0)
        assertEquals(TimePeriod.AFTERNOON, halfway.period)
        assertEquals(TimePeriod.EVENING, halfway.nextPeriod)
        assertEquals(0.5f, halfway.progress, 0.0001f)
    }

    @Test
    fun transitionEveningToNight() {
        val halfway = TimeAtmospherePolicy.resolve(20, 0, 0)
        assertEquals(TimePeriod.EVENING, halfway.period)
        assertEquals(TimePeriod.NIGHT, halfway.nextPeriod)
        assertEquals(0.5f, halfway.progress, 0.0001f)
    }

    @Test
    fun transitionNightToPreDawn() {
        // Night is 22:00 to 03:00 (5 hours). Halfway is 00:30:00 (2.5 hours in).
        val halfway = TimeAtmospherePolicy.resolve(0, 30, 0)
        assertEquals(TimePeriod.NIGHT, halfway.period)
        assertEquals(TimePeriod.PRE_DAWN, halfway.nextPeriod)
        assertEquals(0.5f, halfway.progress, 0.0001f)
    }

    // --- 9. Intermediate interpolation values ----------------------------------

    @Test
    fun intermediateInterpolationValuesAt1150And1210() {
        // 11:50 is 50 minutes into NOON (3 hours = 180 min duration)
        val at1150 = TimeAtmospherePolicy.resolve(11, 50, 0)
        assertEquals(TimePeriod.NOON, at1150.period)
        assertEquals(TimePeriod.AFTERNOON, at1150.nextPeriod)
        assertEquals(50f / 180f, at1150.progress, 0.001f)

        // 12:10 is 70 minutes into NOON
        val at1210 = TimeAtmospherePolicy.resolve(12, 10, 0)
        assertEquals(TimePeriod.NOON, at1210.period)
        assertEquals(TimePeriod.AFTERNOON, at1210.nextPeriod)
        assertEquals(70f / 180f, at1210.progress, 0.001f)

        assertTrue(at1150.progress < at1210.progress)
    }

    @Test
    fun intermediateInterpolationValuesAt1850And1910() {
        // 18:50 is 50 minutes into EVENING (4 hours = 240 min duration)
        val at1850 = TimeAtmospherePolicy.resolve(18, 50, 0)
        assertEquals(TimePeriod.EVENING, at1850.period)
        assertEquals(TimePeriod.NIGHT, at1850.nextPeriod)
        assertEquals(50f / 240f, at1850.progress, 0.001f)

        // 19:10 is 70 minutes into EVENING
        val at1910 = TimeAtmospherePolicy.resolve(19, 10, 0)
        assertEquals(TimePeriod.EVENING, at1910.period)
        assertEquals(TimePeriod.NIGHT, at1910.nextPeriod)
        assertEquals(70f / 240f, at1910.progress, 0.001f)

        assertTrue(at1850.progress < at1910.progress)
    }

    @Test
    fun colorAndIntensityInterpolationIsLinear() {
        val start = TimeAtmospherePolicy.resolve(6, 0, 0) // MORNING start
        val mid = TimeAtmospherePolicy.resolve(8, 30, 0)  // MORNING mid (50%)
        val end = TimeAtmospherePolicy.resolve(11, 0, 0)  // MORNING end (NOON start)

        val expectedGlowIntensity = (start.glowIntensity + end.glowIntensity) / 2f
        assertEquals(expectedGlowIntensity, mid.glowIntensity, 0.001f)

        val expectedMotionSpeed = (start.ambientMotionSpeed + end.ambientMotionSpeed) / 2f
        assertEquals(expectedMotionSpeed, mid.ambientMotionSpeed, 0.001f)
    }

    // --- 10. Exact boundary behavior --------------------------------------------

    @Test
    fun secondBySecondBoundaryTransitionsAreMonotonic() {
        val lastSecondPreDawn = TimeAtmospherePolicy.resolve(5, 59, 59)
        val firstSecondMorning = TimeAtmospherePolicy.resolve(6, 0, 0)

        assertEquals(TimePeriod.PRE_DAWN, lastSecondPreDawn.period)
        assertEquals(TimePeriod.MORNING, firstSecondMorning.period)
        assertTrue(lastSecondPreDawn.progress > 0.999f)
        assertEquals(0.0f, firstSecondMorning.progress, 0.0001f)
    }

    // --- 11. Device-local timezone behavior -------------------------------------

    @Test
    fun timezoneHandlingIsCorrectForSuppliedTimeZone() {
        // Fixed UTC Instant corresponding to 2026-08-31T12:00:00Z
        val fixedInstant = Instant.parse("2026-08-31T12:00:00Z")

        // UTC: 12:00:00 -> NOON
        val utcClock = Clock.fixed(fixedInstant, ZoneId.of("UTC"))
        val utcAtmosphere = TimeAtmospherePolicy.resolve(utcClock, ZoneId.of("UTC"))
        assertEquals(TimePeriod.NOON, utcAtmosphere.period)

        // New York (UTC-4): 08:00:00 -> MORNING
        val nyZone = ZoneId.of("America/New_York")
        val nyClock = Clock.fixed(fixedInstant, nyZone)
        val nyAtmosphere = TimeAtmospherePolicy.resolve(nyClock, nyZone)
        assertEquals(TimePeriod.MORNING, nyAtmosphere.period)

        // Tokyo (UTC+9): 21:00:00 -> EVENING
        val tokyoZone = ZoneId.of("Asia/Tokyo")
        val tokyoClock = Clock.fixed(fixedInstant, tokyoZone)
        val tokyoAtmosphere = TimeAtmospherePolicy.resolve(tokyoClock, tokyoZone)
        assertEquals(TimePeriod.EVENING, tokyoAtmosphere.period)
    }

    // --- 12. Deterministic output for supplied clock/time ------------------------

    @Test
    fun deterministicOutputForSuppliedLocalTimeAndClock() {
        val fixedInstant = Instant.parse("2026-08-31T18:00:00Z")
        val clock = Clock.fixed(fixedInstant, ZoneId.of("UTC"))

        val atmosphereFromClock = TimeAtmospherePolicy.resolve(clock, ZoneId.of("UTC"))
        val atmosphereFromLocalTime = TimeAtmospherePolicy.resolve(LocalTime.of(18, 0, 0))
        val atmosphereFromCalendar = TimeAtmospherePolicy.resolve(
            Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = fixedInstant.toEpochMilli()
            }
        )

        assertEquals(TimePeriod.EVENING, atmosphereFromClock.period)
        assertEquals(atmosphereFromClock.period, atmosphereFromLocalTime.period)
        assertEquals(atmosphereFromClock.period, atmosphereFromCalendar.period)
        assertEquals(atmosphereFromClock.progress, atmosphereFromLocalTime.progress, 0.0001f)
        assertEquals(atmosphereFromClock.progress, atmosphereFromCalendar.progress, 0.0001f)
    }

    // --- 13. Geometry Parameters Resolution and Continuous Interpolation ---------

    @Test
    fun geometryParametersInterpolateContinuouslyAcrossPeriods() {
        val noon = TimeAtmospherePolicy.resolve(11, 0, 0)
        val afternoonHalfway = TimeAtmospherePolicy.resolve(16, 0, 0)
        val evening = TimeAtmospherePolicy.resolve(18, 0, 0)

        // NOON has high density & opacity
        assertEquals(0.85f, noon.lineDensity, 0.001f)
        assertEquals(0.42f, noon.lineOpacity, 0.001f)
        assertEquals(0.15f, noon.warmth, 0.001f)

        // AFTERNOON has highest warmth
        val afternoon = TimeAtmospherePolicy.resolve(14, 0, 0)
        assertEquals(0.75f, afternoon.warmth, 0.001f)

        // Halfway through AFTERNOON (16:00 is 2h into 4h AFTERNOON)
        val expectedWarmth = (afternoon.warmth + evening.warmth) / 2f
        assertEquals(expectedWarmth, afternoonHalfway.warmth, 0.001f)

        val expectedDensity = (afternoon.lineDensity + evening.lineDensity) / 2f
        assertEquals(expectedDensity, afternoonHalfway.lineDensity, 0.001f)
    }

    @Test
    fun geometryParametersStayInBoundedRanges() {
        for (sec in 0 until 86400 step 1800) {
            val atmosphere = TimeAtmospherePolicy.resolve(sec)
            assertTrue("lineDensity out of bounds at sec=$sec", atmosphere.lineDensity in 0.0f..1.0f)
            assertTrue("lineOpacity out of bounds at sec=$sec", atmosphere.lineOpacity in 0.0f..1.0f)
            assertTrue("lineLength out of bounds at sec=$sec", atmosphere.lineLength in 0.0f..1.0f)
            assertTrue("curvature out of bounds at sec=$sec", atmosphere.curvature in 0.0f..1.0f)
            assertTrue("intersectionFrequency out of bounds at sec=$sec", atmosphere.intersectionFrequency in 0.0f..1.0f)
            assertTrue("warmth out of bounds at sec=$sec", atmosphere.warmth in 0.0f..1.0f)
        }
    }
}
