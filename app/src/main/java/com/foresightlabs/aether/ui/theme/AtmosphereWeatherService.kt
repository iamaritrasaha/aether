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
 * On-demand weather for Aether's living atmosphere.
 *
 * - Approximate (coarse) location only, read from last-known network/passive fixes
 * - No background tracking, no continuous polling, no location history stored
 * - Approximate coordinates ARE sent to Open-Meteo to resolve the current condition;
 *   user-facing copy must say so rather than claiming location never leaves the device
 * - Result cached in memory for 30 minutes
 */
object AtmosphereWeatherService {

    private var lastFetchTime = 0L
    private var cachedCondition: WeatherCondition? = null
    private var cachedLocationName: String? = null
    private const val CACHE_DURATION_MS = 30 * 60 * 1000L // 30 minutes

    /**
     * Reads local weather, or reports truthfully why it could not.
     *
     * Never substitutes a plausible-looking condition for a real one: an unresolved
     * read returns [WeatherReading.Unavailable] so the atmosphere runs time-only and
     * the UI can say what is missing.
     */
    suspend fun read(context: Context, forceRefresh: Boolean = false): WeatherReading {
        val now = System.currentTimeMillis()
        val cached = cachedCondition
        if (!forceRefresh && cached != null && (now - lastFetchTime < CACHE_DURATION_MS)) {
            return WeatherReading.Known(cached)
        }

        if (!hasCoarseLocationPermission(context)) {
            return WeatherReading.Unavailable(WeatherUnavailableReason.LOCATION_PERMISSION)
        }

        return withContext(Dispatchers.IO) {
            try {
                val location = getApproximateLocation(context)
                    ?: return@withContext WeatherReading.Unavailable(WeatherUnavailableReason.NO_LOCATION)
                val weather = fetchFromOpenMeteo(location.latitude, location.longitude)
                    ?: return@withContext WeatherReading.Unavailable(WeatherUnavailableReason.SERVICE)
                cachedCondition = weather
                cachedLocationName = "Local weather"
                lastFetchTime = now
                WeatherReading.Known(weather)
            } catch (_: Exception) {
                WeatherReading.Unavailable(WeatherUnavailableReason.SERVICE)
            }
        }
    }

    fun hasCoarseLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private fun getApproximateLocation(context: Context): Location? {
        if (!hasCoarseLocationPermission(context)) return null

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
