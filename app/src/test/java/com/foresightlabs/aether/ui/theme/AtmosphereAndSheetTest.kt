package com.foresightlabs.aether.ui.theme
import com.foresightlabs.aether.ui.design.SheetAnchor
import com.foresightlabs.aether.ui.design.SheetAnchors
import com.foresightlabs.aether.ui.theme.AtmosphereWeatherService
import com.foresightlabs.aether.ui.theme.TimeAtmospherePalette
import com.foresightlabs.aether.ui.theme.WeatherCondition
import com.foresightlabs.aether.ui.theme.WeatherReading
import com.foresightlabs.aether.ui.theme.buildAtmosphere
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AtmosphereAndSheetTest {

    // --- time bands honour the half-hour boundaries ---------------------------

    @Test
    fun timeBandsMatchTheCanonicalSchedule() {
        fun at(h: Int, m: Int) = TimeAtmospherePalette.forMinuteOfDay(h * 60 + m)

        assertEquals(TimeAtmospherePalette.NIGHT, at(4, 59))
        assertEquals(TimeAtmospherePalette.DAWN, at(5, 0))
        assertEquals(TimeAtmospherePalette.DAWN, at(7, 59))
        assertEquals(TimeAtmospherePalette.DAY, at(8, 0))
        assertEquals(TimeAtmospherePalette.DAY, at(16, 29))
        assertEquals(TimeAtmospherePalette.GOLDEN_HOUR, at(16, 30))
        assertEquals(TimeAtmospherePalette.GOLDEN_HOUR, at(19, 29))
        assertEquals(TimeAtmospherePalette.EVENING, at(19, 30))
        assertEquals(TimeAtmospherePalette.EVENING, at(22, 29))
        assertEquals(TimeAtmospherePalette.NIGHT, at(22, 30))
        assertEquals(TimeAtmospherePalette.NIGHT, at(0, 0))
    }

    @Test
    fun everyMinuteOfTheDayResolvesToAPalette() {
        for (minute in 0 until 1440) {
            TimeAtmospherePalette.forMinuteOfDay(minute)
        }
    }

    @Test
    fun everyPaletteCarriesAFullRamp() {
        TimeAtmospherePalette.entries.forEach { palette ->
            assertTrue(
                "${palette.name} needs a multi-stop ramp",
                palette.colors.size >= 4
            )
        }
        assertEquals("16:30 - 19:30", TimeAtmospherePalette.GOLDEN_HOUR.timeLabel)
        assertEquals("08:00 - 16:30", TimeAtmospherePalette.DAY.timeLabel)
        assertEquals("22:30 - 05:00", TimeAtmospherePalette.NIGHT.timeLabel)
    }

    // --- atmosphere owns the accent ------------------------------------------

    @Test
    fun accentFollowsTheTimePalette() {
        val day = buildAtmosphere(TimeAtmospherePalette.DAY, WeatherReading.Idle)
        val golden = buildAtmosphere(TimeAtmospherePalette.GOLDEN_HOUR, WeatherReading.Idle)
        val night = buildAtmosphere(TimeAtmospherePalette.NIGHT, WeatherReading.Idle)

        assertNotEquals(day.accent, golden.accent)
        assertNotEquals(golden.accent, night.accent)
        // Day is a cool palette: it must not resolve to an orange accent.
        assertTrue("day accent should be cool", day.accent.blue > day.accent.red)
        assertTrue("golden accent should be warm", golden.accent.red > golden.accent.blue)
        assertTrue("night accent should be cool", night.accent.blue > night.accent.red)
    }

    @Test
    fun weatherModulatesButOnlyWhenActuallyKnown() {
        val plain = buildAtmosphere(TimeAtmospherePalette.DAY, WeatherReading.Idle)
        val rainy = buildAtmosphere(
            TimeAtmospherePalette.DAY,
            WeatherReading.Known(WeatherCondition.RAIN)
        )
        assertNotEquals(plain.accent, rainy.accent)
        assertTrue(rainy.isWeatherModulated)

        // Unavailable and loading readings must fall back to time-only, unmodulated.
        val unavailable = buildAtmosphere(
            TimeAtmospherePalette.DAY,
            WeatherReading.Unavailable(
                com.foresightlabs.aether.ui.theme.WeatherUnavailableReason.SERVICE
            )
        )
        assertEquals(plain.colors, unavailable.colors)
        assertNull(unavailable.weatherCondition)
        assertEquals(plain.colors, buildAtmosphere(TimeAtmospherePalette.DAY, WeatherReading.Loading).colors)
    }

    @Test
    fun wmoCodesMapToConditions() {
        assertEquals(WeatherCondition.CLEAR, AtmosphereWeatherService.mapWmoCodeToWeather(0))
        assertEquals(WeatherCondition.CLEAR, AtmosphereWeatherService.mapWmoCodeToWeather(1))
        assertEquals(WeatherCondition.PARTLY_CLOUDY, AtmosphereWeatherService.mapWmoCodeToWeather(2))
        assertEquals(WeatherCondition.CLOUDY, AtmosphereWeatherService.mapWmoCodeToWeather(3))
        assertEquals(WeatherCondition.FOG, AtmosphereWeatherService.mapWmoCodeToWeather(45))
        assertEquals(WeatherCondition.FOG, AtmosphereWeatherService.mapWmoCodeToWeather(48))
        assertEquals(WeatherCondition.DRIZZLE, AtmosphereWeatherService.mapWmoCodeToWeather(51))
        assertEquals(WeatherCondition.RAIN, AtmosphereWeatherService.mapWmoCodeToWeather(61))
        assertEquals(WeatherCondition.HEAVY_RAIN, AtmosphereWeatherService.mapWmoCodeToWeather(65))
        assertEquals(WeatherCondition.SNOW, AtmosphereWeatherService.mapWmoCodeToWeather(71))
        assertEquals(WeatherCondition.STORM, AtmosphereWeatherService.mapWmoCodeToWeather(95))
        assertEquals(WeatherCondition.UNKNOWN, AtmosphereWeatherService.mapWmoCodeToWeather(999))
        assertEquals(WeatherCondition.UNKNOWN, AtmosphereWeatherService.mapWmoCodeToWeather(-1))
    }

    // --- sheet anchors are derived, never a fixed screen split ----------------

    @Test
    fun anchorsFollowMeasuredHeroContent() {
        val short = SheetAnchors.derive(
            containerHeightPx = 2000f, heroBottomPx = 600f, topInsetPx = 60f,
            minChatViewportPx = 700f, minAtmosphereRevealPx = 200f, relaxedExtraPx = 240f
        )
        val tall = SheetAnchors.derive(
            containerHeightPx = 2000f, heroBottomPx = 900f, topInsetPx = 60f,
            minChatViewportPx = 700f, minAtmosphereRevealPx = 200f, relaxedExtraPx = 240f
        )
        // A taller hero pushes the resting anchor down; it is not a constant fraction.
        assertEquals(600f, short.resting, 0.01f)
        assertEquals(900f, tall.resting, 0.01f)
        assertNotEquals(short.resting / 2000f, tall.resting / 2000f)
    }

    @Test
    fun anchorsAreOrderedAndClamped() {
        val anchors = SheetAnchors.derive(
            containerHeightPx = 2000f, heroBottomPx = 900f, topInsetPx = 60f,
            minChatViewportPx = 700f, minAtmosphereRevealPx = 200f, relaxedExtraPx = 240f
        )
        assertTrue(anchors.expanded <= anchors.resting)
        assertTrue(anchors.resting <= anchors.peek)
        assertTrue(anchors.isResolved)
        // The conversation viewport is never smaller than the stated minimum.
        assertTrue(2000f - anchors.peek >= 700f - 0.01f)
        // Some atmosphere always stays visible when expanded.
        assertTrue(anchors.expanded >= 260f - 0.01f)
    }

    @Test
    fun collapsedLipAnchorLeavesHandleVisibleForWeatherHero() {
        val anchors = SheetAnchors.derive(
            containerHeightPx = 1000f,
            heroBottomPx = 300f,
            topInsetPx = 60f,
            minChatViewportPx = 400f,
            minAtmosphereRevealPx = 100f,
            relaxedExtraPx = 120f,
            collapsedLipHeightPx = 150f
        )
        assertEquals(850f, anchors.peek, 0.01f)
        assertTrue(anchors.resting < anchors.peek)
        assertEquals(150f, 1000f - anchors.peek, 0.01f)
    }

    @Test
    fun anchorsCollapseSafelyOnVeryShortScreens() {
        val anchors = SheetAnchors.derive(
            containerHeightPx = 640f, heroBottomPx = 900f, topInsetPx = 60f,
            minChatViewportPx = 700f, minAtmosphereRevealPx = 200f, relaxedExtraPx = 240f
        )
        // No negative or inverted anchors even when nothing fits.
        assertTrue(anchors.expanded >= 0f)
        assertTrue(anchors.expanded <= anchors.resting)
        assertTrue(anchors.resting <= anchors.peek)
    }

    @Test
    fun offsetForReturnsEachAnchor() {
        val anchors = SheetAnchors(expanded = 100f, resting = 500f, peek = 700f)
        assertEquals(100f, anchors.offsetFor(SheetAnchor.EXPANDED), 0.01f)
        assertEquals(500f, anchors.offsetFor(SheetAnchor.RESTING), 0.01f)
        assertEquals(700f, anchors.offsetFor(SheetAnchor.PEEK), 0.01f)
    }

    @Test
    fun unresolvedContainerYieldsUnresolvedAnchors() {
        val anchors = SheetAnchors.derive(0f, 0f, 0f, 0f, 0f, 0f)
        assertTrue(!anchors.isResolved)
    }
}
