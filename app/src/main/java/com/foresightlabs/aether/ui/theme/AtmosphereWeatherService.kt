package com.foresightlabs.aether.ui.theme

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Privacy-conscious, on-demand automatic weather service for Aether's living atmosphere system.
 * - No background tracking
 * - No continuous polling
 * - No location history stored
 * - Coarse/approximate location used
 * - Caches result in-memory for 30 minutes
 */
object AtmosphereWeatherService {

    private var lastFetchTime = 0L
    private var cachedCondition: WeatherCondition? = null
    private var cachedLocationName: String? = null
    private const val CACHE_DURATION_MS = 30 * 60 * 1000L // 30 minutes

    suspend fun fetchCurrentWeather(context: Context, forceRefresh: Boolean = false): Pair<WeatherCondition, String?> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedCondition != null && (now - lastFetchTime < CACHE_DURATION_MS)) {
            return Pair(cachedCondition!!, cachedLocationName)
        }

        return withContext(Dispatchers.IO) {
            try {
                val loc = getApproximateLocation(context)
                if (loc != null) {
                    val weather = fetchFromOpenMeteo(loc.latitude, loc.longitude)
                    if (weather != null) {
                        cachedCondition = weather
                        cachedLocationName = "Local Weather"
                        lastFetchTime = now
                        return@withContext Pair(weather, cachedLocationName)
                    }
                }
                // Fallback: Default to Clear / Time-only modulation
                val fallback = cachedCondition ?: WeatherCondition.CLEAR
                Pair(fallback, null)
            } catch (_: Exception) {
                val fallback = cachedCondition ?: WeatherCondition.CLEAR
                Pair(fallback, null)
            }
        }
    }

    private fun getApproximateLocation(context: Context): Location? {
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasCoarse) return null

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

        // Try network / passive / coarse provider first (fast and privacy friendly)
        var bestLocation: Location? = null
        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

        for (provider in providers) {
            if (locationManager.isProviderEnabled(provider)) {
                try {
                    val loc = locationManager.getLastKnownLocation(provider)
                    if (loc != null && (bestLocation == null || loc.time > bestLocation.time)) {
                        bestLocation = loc
                    }
                } catch (_: SecurityException) {
                }
            }
        }

        return bestLocation
    }

    private fun fetchFromOpenMeteo(lat: Double, lon: Double): WeatherCondition? {
        var connection: HttpURLConnection? = null
        return try {
            val urlString = "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=weather_code".format(lat, lon)
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "Aether-Atmosphere/1.0")
            }

            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.use { it.readText() }
                val json = JSONObject(response)
                val current = json.optJSONObject("current")
                val code = current?.optInt("weather_code", -1) ?: -1
                mapWmoCodeToWeather(code)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Maps standard WMO Weather interpretation codes (0-99) to Aether WeatherCondition.
     */
    fun mapWmoCodeToWeather(code: Int): WeatherCondition {
        return when (code) {
            0 -> WeatherCondition.CLEAR
            1, 2, 3 -> WeatherCondition.CLOUDY
            45, 48 -> WeatherCondition.FOG
            51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> WeatherCondition.RAIN
            71, 73, 75, 77, 85, 86 -> WeatherCondition.SNOW
            95, 96, 99 -> WeatherCondition.STORM
            else -> WeatherCondition.CLEAR
        }
    }
}
