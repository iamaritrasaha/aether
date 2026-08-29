package com.foresightlabs.aether.ui.weather
import com.foresightlabs.aether.ui.weather.GeocodingPlace
import com.foresightlabs.aether.ui.weather.parseGeocodingResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AetherLocationPickerTest {

    @Test
    fun parseGeocodingResponseExtractsValidPlaces() {
        val sampleJson = """
            {
                "results": [
                    {
                        "id": 1277335,
                        "name": "Balurghat",
                        "latitude": 25.2167,
                        "longitude": 88.7667,
                        "country": "India",
                        "admin1": "West Bengal",
                        "timezone": "Asia/Kolkata"
                    },
                    {
                        "id": 2643743,
                        "name": "London",
                        "latitude": 51.50853,
                        "longitude": -0.12574,
                        "country": "United Kingdom",
                        "admin1": "England",
                        "timezone": "Europe/London"
                    }
                ]
            }
        """.trimIndent()

        val results = parseGeocodingResponse(sampleJson)
        assertEquals(2, results.size)

        val place1 = results[0]
        assertEquals(1277335L, place1.id)
        assertEquals("Balurghat", place1.name)
        assertEquals("West Bengal", place1.admin1)
        assertEquals("India", place1.country)
        assertEquals("West Bengal, India", place1.subtitle)
        assertEquals(25.2167, place1.latitude, 0.0001)
        assertEquals(88.7667, place1.longitude, 0.0001)
        assertEquals("Asia/Kolkata", place1.timezone)

        val place2 = results[1]
        assertEquals(2643743L, place2.id)
        assertEquals("London", place2.name)
        assertEquals("England, United Kingdom", place2.subtitle)
    }

    @Test
    fun parseGeocodingResponseHandlesEmptyOrMalformedJsonSafely() {
        val emptyJson = "{}"
        val resultsEmpty = parseGeocodingResponse(emptyJson)
        assertTrue(resultsEmpty.isEmpty())

        val emptyResults = """{"results": []}"""
        val resultsList = parseGeocodingResponse(emptyResults)
        assertTrue(resultsList.isEmpty())

        val invalidJson = "not a json string"
        val resultsInvalid = parseGeocodingResponse(invalidJson)
        assertTrue(resultsInvalid.isEmpty())
    }

    @Test
    fun geocodingPlaceSubtitleOmitsNullOrBlankValues() {
        val place = GeocodingPlace(
            id = 1,
            name = "Singapore",
            admin1 = null,
            country = "Singapore",
            latitude = 1.35,
            longitude = 103.82
        )
        assertEquals("Singapore", place.subtitle)

        val placeNoLocation = GeocodingPlace(
            id = 2,
            name = "Unknown Island",
            admin1 = null,
            country = null,
            latitude = 0.0,
            longitude = 0.0
        )
        assertEquals("", placeNoLocation.subtitle)
    }
}
