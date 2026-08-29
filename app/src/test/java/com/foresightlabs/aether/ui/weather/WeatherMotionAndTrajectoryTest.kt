package com.foresightlabs.aether.ui.weather
import androidx.compose.ui.geometry.Offset
import com.foresightlabs.aether.ui.theme.TimeAtmospherePalette
import com.foresightlabs.aether.ui.weather.computeCelestialProgress
import com.foresightlabs.aether.ui.weather.evaluateCelestialArc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class WeatherMotionAndTrajectoryTest {

    // --- 1. Celestial Arc Geometry Tests -------------------------------------

    @Test
    fun celestialArcProgressesSmoothlyFromLeftHorizonToRightHorizon() {
        val width = 1080f
        val height = 2400f

        val start = evaluateCelestialArc(0.0f, width, height)
        val peak = evaluateCelestialArc(0.5f, width, height)
        val end = evaluateCelestialArc(1.0f, width, height)

        // Sunrise / start is in lower-left region
        assertEquals(width * 0.08f, start.x, 1.0f)
        assertEquals(height * 0.50f, start.y, 1.0f)

        // Solar noon / peak is highest in the sky (lowest y value)
        assertTrue("Peak y must be higher than start y", peak.y < start.y)
        assertTrue("Peak y must be higher than end y", peak.y < end.y)
        assertTrue("Peak x must be near horizontal midpoint", peak.x > start.x && peak.x < end.x)

        // Sunset / end is in lower-right region
        assertEquals(width * 0.94f, end.x, 1.0f)
        assertEquals(height * 0.55f, end.y, 1.0f)

        // X coordinate must advance monotonically from left to right
        var prevX = start.x
        for (i in 1..20) {
            val t = i / 20f
            val pt = evaluateCelestialArc(t, width, height)
            assertTrue("x should advance monotonically: $pt vs prev $prevX", pt.x >= prevX)
            prevX = pt.x
        }
    }

    @Test
    fun celestialArcIsClampedBetweenZeroAndOne() {
        val width = 1000f
        val height = 2000f

        val atNegative = evaluateCelestialArc(-0.5f, width, height)
        val atZero = evaluateCelestialArc(0.0f, width, height)
        assertEquals(atZero.x, atNegative.x, 0.01f)
        assertEquals(atZero.y, atNegative.y, 0.01f)

        val atExcess = evaluateCelestialArc(1.5f, width, height)
        val atOne = evaluateCelestialArc(1.0f, width, height)
        assertEquals(atOne.x, atExcess.x, 0.01f)
        assertEquals(atOne.y, atExcess.y, 0.01f)
    }

    // --- 2. Real-Time Sunrise / Sunset Day & Night Calculations ---------------

    @Test
    fun daytimeProgressMapsToActualSunriseAndSunset() {
        val sunrise = 1724716800000L // 06:00:00
        val sunset = 1724760000000L  // 18:00:00 (12 hours later)
        val nextSunrise = 1724803200000L // 06:00:00 next day

        // At sunrise: progress = 0.0
        val (night0, p0) = computeCelestialProgress(
            currentTimeMillis = sunrise,
            sunriseMillis = sunrise,
            sunsetMillis = sunset,
            nextSunriseMillis = nextSunrise
        )
        assertFalse("Should be day at sunrise", night0)
        assertEquals(0.0f, p0, 0.01f)

        // At solar noon (12:00:00): progress = 0.5
        val noon = sunrise + 6 * 3600 * 1000L
        val (nightNoon, pNoon) = computeCelestialProgress(
            currentTimeMillis = noon,
            sunriseMillis = sunrise,
            sunsetMillis = sunset,
            nextSunriseMillis = nextSunrise
        )
        assertFalse("Should be day at solar noon", nightNoon)
        assertEquals(0.5f, pNoon, 0.01f)

        // At sunset: progress = 1.0
        val (night1, p1) = computeCelestialProgress(
            currentTimeMillis = sunset,
            sunriseMillis = sunrise,
            sunsetMillis = sunset,
            nextSunriseMillis = nextSunrise
        )
        assertFalse("Should be day at sunset", night1)
        assertEquals(1.0f, p1, 0.01f)
    }

    @Test
    fun nighttimeProgressMapsFromSunsetToNextSunrise() {
        val sunrise = 1724716800000L // 06:00:00 today
        val sunset = 1724760000000L  // 18:00:00 today
        val nextSunrise = 1724803200000L // 06:00:00 tomorrow (12h night)

        // 1 hour after sunset (19:00:00)
        val earlyNight = sunset + 1 * 3600 * 1000L
        val (nightEarly, pEarly) = computeCelestialProgress(
            currentTimeMillis = earlyNight,
            sunriseMillis = sunrise,
            sunsetMillis = sunset,
            nextSunriseMillis = nextSunrise
        )
        assertTrue("Should be night after sunset", nightEarly)
        assertEquals(1f / 12f, pEarly, 0.02f)

        // Midnight (00:00:00, 6 hours after sunset)
        val midnight = sunset + 6 * 3600 * 1000L
        val (nightMid, pMid) = computeCelestialProgress(
            currentTimeMillis = midnight,
            sunriseMillis = sunrise,
            sunsetMillis = sunset,
            nextSunriseMillis = nextSunrise
        )
        assertTrue("Should be night at midnight", nightMid)
        assertEquals(0.5f, pMid, 0.02f)

        // Pre-dawn (05:00:00, 11 hours after sunset)
        val preDawn = sunset + 11 * 3600 * 1000L
        val (nightDawn, pDawn) = computeCelestialProgress(
            currentTimeMillis = preDawn,
            sunriseMillis = sunrise,
            sunsetMillis = sunset,
            nextSunriseMillis = nextSunrise
        )
        assertTrue("Should be night before dawn", nightDawn)
        assertEquals(11f / 12f, pDawn, 0.02f)
    }

    // --- 3. Deterministic Animation Time Motion Difference Tests --------------

    @Test
    fun animationClockAdvancesAllPhenomenaBetweenT0AndT10() {
        val width = 1080f
        val height = 2400f

        // 1. Cloud drift
        val speedPxPerSec = (width / 45f) * 1.6f
        val cloudX0 = ((0f * speedPxPerSec) % width)
        val cloudX2 = ((2f * speedPxPerSec) % width)
        val cloudX10 = ((10f * speedPxPerSec) % width)
        assertNotEquals(cloudX0, cloudX2, 0.1f)
        assertNotEquals(cloudX2, cloudX10, 0.1f)

        // 2. Rain streak falling
        val rainSpeed = 700f
        val rainY0 = (0f * rainSpeed) % height
        val rainY2 = (2f * rainSpeed) % height
        val rainY10 = (10f * rainSpeed) % height
        assertNotEquals(rainY0, rainY2, 0.1f)
        assertNotEquals(rainY2, rainY10, 0.1f)

        // 3. Fog horizontal sinusoidal translation
        val fogX0 = sin(0f * 0.25f) * (width * 0.15f)
        val fogX2 = sin(2f * 0.25f) * (width * 0.15f)
        val fogX10 = sin(10f * 0.25f) * (width * 0.15f)
        assertNotEquals(fogX0, fogX2, 0.1f)
        assertNotEquals(fogX2, fogX10, 0.1f)

        // 4. Sun halo breathing and ray rotation
        val haloBreathe0 = 1f + 0.14f * sin(0f * 1.5f)
        val haloBreathe2 = 1f + 0.14f * sin(2f * 1.5f)
        val haloBreathe8 = 1f + 0.14f * sin(8f * 1.5f)
        assertNotEquals(haloBreathe0, haloBreathe2, 0.01f)
        assertNotEquals(haloBreathe2, haloBreathe8, 0.01f)

        val rayAngle0 = 0f * 0.12f
        val rayAngle2 = 2f * 0.12f
        val rayAngle8 = 8f * 0.12f
        assertNotEquals(rayAngle0, rayAngle2, 0.01f)
        assertNotEquals(rayAngle2, rayAngle8, 0.01f)

        // 5. Dynamic gradient field lobes (12-18% screen travel)
        val lobe1X0 = width * (0.28f + sin(0f * 0.32f) * 0.14f)
        val lobe1X2 = width * (0.28f + sin(2f * 0.32f) * 0.14f)
        val lobe1X8 = width * (0.28f + sin(8f * 0.32f) * 0.14f)
        assertNotEquals(lobe1X0, lobe1X2, 1.0f)
        assertNotEquals(lobe1X2, lobe1X8, 1.0f)
        assertTrue("Lobe 1 drift should be significant over 2s", kotlin.math.abs(lobe1X2 - lobe1X0) > width * 0.05f)

        // 6. Atmospheric haze drift
        val hazeDriftX0 = sin(0f * 0.28f) * (width * 0.18f)
        val hazeDriftX2 = sin(2f * 0.28f) * (width * 0.18f)
        val hazeDriftX8 = sin(8f * 0.28f) * (width * 0.18f)
        assertNotEquals(hazeDriftX0, hazeDriftX2, 1.0f)
        assertNotEquals(hazeDriftX2, hazeDriftX8, 1.0f)

        // 7. Trajectory dot shimmer
        val shimmer0 = 0.82f + 0.18f * sin(0f * 2.6f)
        val shimmer2 = 0.82f + 0.18f * sin(2f * 2.6f)
        val shimmer8 = 0.82f + 0.18f * sin(8f * 2.6f)
        assertNotEquals(shimmer0, shimmer2, 0.01f)
        assertNotEquals(shimmer2, shimmer8, 0.01f)
    }
}
