package com.foresightlabs.aether.ui.theme

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
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
import java.util.Locale

import com.foresightlabs.aether.data.preferences.ManualWeatherLocation
import com.foresightlabs.aether.data.preferences.WeatherLocationMode
import com.foresightlabs.aether.data.weather.ResolvedWeatherLocation
import com.foresightlabs.aether.data.weather.WeatherLocationRepository

/**
 * On-demand weather for Aether's living atmosphere.
 *
 * - Approximate (coarse) location only, read from last-known network/passive fixes
 * - No background tracking, no continuous polling, no location history stored
 * - Supports manual city selection without requiring location permission
 * - Approximate coordinates ARE sent to Open-Meteo to resolve the current condition;
 *   user-facing copy must say so rather than claiming location never leaves the device
 * - Result cached in memory for 30 minutes
 */
object AtmosphereWeatherService {

    private var lastFetchTime = 0L
    private var cachedData: WeatherData? = null
    private var cachedLocationKey: String? = null
    private const val CACHE_DURATION_MS = 30 * 60 * 1000L // 30 minutes

    /**
     * Reads local weather, or reports truthfully why it could not.
     *
     * Never substitutes a plausible-looking condition for a real one: an unresolved
     * read returns [WeatherReading.Unavailable] so the atmosphere runs time-only and
     * the UI can say what is missing.
     */
    suspend fun read(
        context: Context,
        locationMode: WeatherLocationMode = WeatherLocationMode.AUTOMATIC,
        manualLocation: ManualWeatherLocation? = null,
        forceRefresh: Boolean = false
    ): WeatherReading {
        val now = System.currentTimeMillis()
        val currentKey = if (locationMode == WeatherLocationMode.MANUAL && manualLocation != null) {
            "manual_${manualLocation.latitude}_${manualLocation.longitude}"
        } else {
            "auto"
        }

        val cached = cachedData
        if (!forceRefresh && cached != null && cachedLocationKey == currentKey && (now - lastFetchTime < CACHE_DURATION_MS)) {
            return WeatherReading.Known(cached.condition, cached)
        }

        if (locationMode == WeatherLocationMode.AUTOMATIC && !hasCoarseLocationPermission(context)) {
            return WeatherReading.Unavailable(WeatherUnavailableReason.LOCATION_PERMISSION)
        }

        return withContext(Dispatchers.IO) {
            try {
                val location = WeatherLocationRepository.resolveLocation(
                    context = context,
                    locationMode = locationMode,
                    manualLocation = manualLocation
                ) ?: return@withContext WeatherReading.Unavailable(WeatherUnavailableReason.NO_LOCATION)

                val weather = fetchFromOpenMeteo(
                    context = context,
                    lat = location.latitude,
                    lon = location.longitude,
                    fallbackLocationLabel = location.locationLabel
                ) ?: return@withContext WeatherReading.Unavailable(WeatherUnavailableReason.SERVICE)

                cachedData = weather
                cachedLocationKey = currentKey
                lastFetchTime = now
                WeatherReading.Known(weather.condition, weather)
            } catch (_: Exception) {
                WeatherReading.Unavailable(WeatherUnavailableReason.SERVICE)
            }
        }
    }

    fun hasCoarseLocationPermission(context: Context): Boolean =
        WeatherLocationRepository.hasCoarseLocationPermission(context)

    private fun fetchFromOpenMeteo(
        context: Context,
        lat: Double,
        lon: Double,
        fallbackLocationLabel: String? = null
    ): WeatherData? {
        var connection: HttpURLConnection? = null
        return try {
            val urlString = "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset&timezone=auto&forecast_days=2".format(Locale.US, lat, lon)
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
                val current = json.optJSONObject("current") ?: return null
                val code = current.optInt("weather_code", -1)
                val condition = mapWmoCodeToWeather(code)
                val temp = Math.round(current.optDouble("temperature_2m", 0.0)).toInt()
                val apparent = if (current.has("apparent_temperature")) {
                    Math.round(current.optDouble("apparent_temperature", 0.0)).toInt()
                } else null
                val humidity = if (current.has("relative_humidity_2m")) {
                    current.optInt("relative_humidity_2m")
                } else null
                val wind = if (current.has("wind_speed_10m")) {
                    Math.round(current.optDouble("wind_speed_10m", 0.0)).toInt()
                } else null

                val timezoneId = if (json.has("timezone") && !json.isNull("timezone")) json.optString("timezone", "") else null

                val daily = json.optJSONObject("daily")
                val maxArray = daily?.optJSONArray("temperature_2m_max")
                val minArray = daily?.optJSONArray("temperature_2m_min")
                val high = if (maxArray != null && maxArray.length() > 0) {
                    Math.round(maxArray.optDouble(0, 0.0)).toInt()
                } else null
                val low = if (minArray != null && minArray.length() > 0) {
                    Math.round(minArray.optDouble(0, 0.0)).toInt()
                } else null

                val sunriseArray = daily?.optJSONArray("sunrise")
                val sunsetArray = daily?.optJSONArray("sunset")
                val sunriseStr = if (sunriseArray != null && sunriseArray.length() > 0) sunriseArray.optString(0) else null
                val sunsetStr = if (sunsetArray != null && sunsetArray.length() > 0) sunsetArray.optString(0) else null
                val nextSunriseStr = if (sunriseArray != null && sunriseArray.length() > 1) sunriseArray.optString(1) else null
                val sunriseMillis = parseIsoDateTimeToEpochMillis(sunriseStr, timezoneId)
                val sunsetMillis = parseIsoDateTimeToEpochMillis(sunsetStr, timezoneId)
                val nextSunriseMillis = parseIsoDateTimeToEpochMillis(nextSunriseStr, timezoneId)

                val locationName = fallbackLocationLabel ?: WeatherLocationRepository.resolveLocalityName(context, lat, lon) ?: "Current location"

                WeatherData(
                    condition = condition,
                    temperatureC = temp,
                    apparentTemperatureC = apparent,
                    highTempC = high,
                    lowTempC = low,
                    humidityPercent = humidity,
                    windSpeedKmh = wind,
                    locationLabel = locationName,
                    sunriseEpochMillis = sunriseMillis,
                    sunsetEpochMillis = sunsetMillis,
                    nextSunriseEpochMillis = nextSunriseMillis,
                    timezoneId = timezoneId
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseIsoDateTimeToEpochMillis(isoString: String?, timezoneId: String?): Long? {
        if (isoString.isNullOrBlank()) return null
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
            if (!timezoneId.isNullOrBlank()) {
                sdf.timeZone = java.util.TimeZone.getTimeZone(timezoneId)
            }
            sdf.parse(isoString)?.time
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Maps standard WMO Weather interpretation codes (0-99) to Aether WeatherCondition.
     */
    fun mapWmoCodeToWeather(code: Int): WeatherCondition {
        return when (code) {
            0, 1 -> WeatherCondition.CLEAR
            2 -> WeatherCondition.PARTLY_CLOUDY
            3 -> WeatherCondition.CLOUDY
            45, 48 -> WeatherCondition.FOG
            51, 53, 55, 56, 57 -> WeatherCondition.DRIZZLE
            61, 63, 66, 67, 80, 81 -> WeatherCondition.RAIN
            65, 82 -> WeatherCondition.HEAVY_RAIN
            71, 73, 75, 77, 85, 86 -> WeatherCondition.SNOW
            95, 96, 99 -> WeatherCondition.STORM
            else -> WeatherCondition.UNKNOWN
        }
    }
}
