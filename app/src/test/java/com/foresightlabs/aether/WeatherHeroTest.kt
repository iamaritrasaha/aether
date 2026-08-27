package com.foresightlabs.aether

import com.foresightlabs.aether.ui.theme.TimeAtmospherePalette
import com.foresightlabs.aether.ui.theme.WeatherCondition
import com.foresightlabs.aether.ui.theme.WeatherData
import com.foresightlabs.aether.ui.theme.WeatherReading
import com.foresightlabs.aether.ui.theme.WeatherUnavailableReason
import com.foresightlabs.aether.ui.theme.buildAtmosphere
import com.foresightlabs.aether.ui.weather.WeatherHeroState
import com.foresightlabs.aether.ui.weather.buildWeatherHeroState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherHeroTest {

    @Test
    fun truthfulWeatherStatePopulatesAllAvailableFields() {
        val data = WeatherData(
            condition = WeatherCondition.PARTLY_CLOUDY,
            temperatureC = 29,
            apparentTemperatureC = 32,
            highTempC = 31,
            lowTempC = 25,
            humidityPercent = 78,
            windSpeedKmh = 9,
            locationLabel = "Balurghat"
        )
        val atmosphere = buildAtmosphere(
            palette = TimeAtmospherePalette.DAY,
            weather = WeatherReading.Known(WeatherCondition.PARTLY_CLOUDY, data)
        )
        val hero = buildWeatherHeroState(atmosphere, TimeAtmospherePalette.DAY)

        assertTrue(hero.isAvailable)
        assertEquals(29, hero.temperature)
        assertEquals("29°", hero.temperatureDisplay)
        assertEquals(WeatherCondition.PARTLY_CLOUDY, hero.condition)
        assertEquals("Partly Cloudy", hero.conditionName)
        assertEquals(32, hero.apparentTemperature)
        assertEquals("H 31°   L 25°", hero.highLowDisplay)
        assertEquals("Balurghat", hero.locationLabel)
        assertEquals(listOf("Feels 32°", "Humidity 78%", "Wind 9 km/h"), hero.secondaryMetrics)
    }

    @Test
    fun locationIsOmittedWhenNotTruthfullyResolved() {
        val data = WeatherData(
            condition = WeatherCondition.RAIN,
            temperatureC = 21,
            apparentTemperatureC = 20,
            highTempC = 23,
            lowTempC = 18,
            humidityPercent = 88,
            windSpeedKmh = 14,
            locationLabel = null
        )
        val atmosphere = buildAtmosphere(
            palette = TimeAtmospherePalette.DAY,
            weather = WeatherReading.Known(WeatherCondition.RAIN, data)
        )
        val hero = buildWeatherHeroState(atmosphere, TimeAtmospherePalette.DAY)

        assertNull(hero.locationLabel)
        assertEquals("21°", hero.temperatureDisplay)
        assertEquals("Rain", hero.conditionName)
    }

    @Test
    fun unavailableWeatherDegradesCleanlyWithoutFakeNumbers() {
        val atmosphere = buildAtmosphere(
            palette = TimeAtmospherePalette.GOLDEN_HOUR,
            weather = WeatherReading.Unavailable(WeatherUnavailableReason.LOCATION_PERMISSION)
        )
        val hero = buildWeatherHeroState(atmosphere, TimeAtmospherePalette.GOLDEN_HOUR)

        assertFalse(hero.isAvailable)
        assertEquals(WeatherUnavailableReason.LOCATION_PERMISSION.message, hero.unavailableMessage)
    }

    @Test
    fun idleWeatherReportsTimeOnlyFallback() {
        val atmosphere = buildAtmosphere(
            palette = TimeAtmospherePalette.NIGHT,
            weather = WeatherReading.Idle
        )
        val hero = buildWeatherHeroState(atmosphere, TimeAtmospherePalette.NIGHT)

        assertFalse(hero.isAvailable)
        assertEquals("Using time-only atmosphere", hero.unavailableMessage)
    }

    @Test
    fun highLowFormatsGracefullyWhenPartial() {
        val stateBoth = WeatherHeroState(temperature = 20, high = 25, low = 15)
        assertEquals("H 25°   L 15°", stateBoth.highLowDisplay)

        val stateHighOnly = WeatherHeroState(temperature = 20, high = 25, low = null)
        assertEquals("H 25°", stateHighOnly.highLowDisplay)

        val stateLowOnly = WeatherHeroState(temperature = 20, high = null, low = 15)
        assertEquals("L 15°", stateLowOnly.highLowDisplay)

        val stateNeither = WeatherHeroState(temperature = 20, high = null, low = null)
        assertNull(stateNeither.highLowDisplay)
    }

    @Test
    fun unknownConditionDoesNotFabricateConditionName() {
        val data = WeatherData(
            condition = WeatherCondition.UNKNOWN,
            temperatureC = 24,
            apparentTemperatureC = 25,
            highTempC = 27,
            lowTempC = 20,
            humidityPercent = 65,
            windSpeedKmh = 10,
            locationLabel = "Local Area"
        )
        val atmosphere = buildAtmosphere(
            palette = TimeAtmospherePalette.DAY,
            weather = WeatherReading.Known(WeatherCondition.UNKNOWN, data)
        )
        val hero = buildWeatherHeroState(atmosphere, TimeAtmospherePalette.DAY)

        assertTrue(hero.isAvailable)
        assertEquals(24, hero.temperature)
        assertEquals("24°", hero.temperatureDisplay)
        assertNull(hero.conditionName)
        assertEquals("H 27°   L 20°", hero.highLowDisplay)
        val description = hero.accessibilityDescription
        assertTrue(description.contains("24 degrees"))
        assertFalse(description.contains("unknown", ignoreCase = true))
        assertFalse(description.contains("clear", ignoreCase = true))
        assertFalse(description.contains("sunny", ignoreCase = true))
    }

    @Test
    fun accessibilityDescriptionProvidesClearSummary() {
        val state = WeatherHeroState(
            temperature = 29,
            condition = WeatherCondition.PARTLY_CLOUDY,
            high = 31,
            low = 25,
            locationLabel = "Balurghat"
        )
        val description = state.accessibilityDescription
        assertTrue(description.contains("29 degrees"))
        assertTrue(description.contains("partly cloudy"))
        assertTrue(description.contains("High 31, low 25"))
        assertTrue(description.contains("Balurghat"))
    }
}
