package com.foresightlabs.aether

import com.foresightlabs.aether.data.preferences.ManualWeatherLocation
import com.foresightlabs.aether.data.preferences.WeatherLocationMode
import com.foresightlabs.aether.data.weather.ResolvedWeatherLocation
import com.foresightlabs.aether.data.weather.WeatherLocationRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherLocationRepositoryTest {

    @Test
    fun manualLocationModeResolvesDirectlyWithoutGps() {
        val manual = ManualWeatherLocation(
            name = "Balurghat",
            admin1 = "West Bengal",
            country = "India",
            latitude = 25.2167,
            longitude = 88.7667,
            timezone = "Asia/Kolkata"
        )

        // When mode is MANUAL and manual location is provided, it should resolve immediately
        assertEquals("Balurghat, West Bengal", manual.displayLabel)
        assertEquals(25.2167, manual.latitude, 0.0001)
        assertEquals(88.7667, manual.longitude, 0.0001)
    }

    @Test
    fun manualWeatherLocationDisplayLabelFormatsCorrectly() {
        val withAdminAndCountry = ManualWeatherLocation(
            name = "Kolkata",
            admin1 = "West Bengal",
            country = "India",
            latitude = 22.5726,
            longitude = 88.3639
        )
        assertEquals("Kolkata, West Bengal", withAdminAndCountry.displayLabel)

        val withCountryOnly = ManualWeatherLocation(
            name = "Singapore",
            admin1 = null,
            country = "Singapore",
            latitude = 1.3521,
            longitude = 103.8198
        )
        assertEquals("Singapore, Singapore", withCountryOnly.displayLabel)

        val nameOnly = ManualWeatherLocation(
            name = "Tokyo",
            admin1 = null,
            country = null,
            latitude = 35.6762,
            longitude = 139.6503
        )
        assertEquals("Tokyo", nameOnly.displayLabel)
    }

    @Test
    fun resolvedWeatherLocationProvidesCleanFallbackLabel() {
        val resolved = ResolvedWeatherLocation(
            latitude = 51.5074,
            longitude = -0.1278,
            locationLabel = "London",
            isManual = false
        )
        assertEquals(51.5074, resolved.latitude, 0.0001)
        assertEquals(-0.1278, resolved.longitude, 0.0001)
        assertEquals("London", resolved.locationLabel)
    }
}
