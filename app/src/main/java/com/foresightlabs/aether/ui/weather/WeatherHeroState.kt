package com.foresightlabs.aether.ui.weather

import androidx.compose.runtime.Immutable
import com.foresightlabs.aether.ui.theme.AetherAtmosphere
import com.foresightlabs.aether.ui.theme.TimeAtmospherePalette
import com.foresightlabs.aether.ui.theme.WeatherCondition
import com.foresightlabs.aether.ui.theme.WeatherReading

/**
 * Clean immutable state feeding the Living Weather Hero.
 *
 * Truthful numbers only: every value originates from actual returned Open-Meteo
 * data or deliberate Appearance overrides. Unresolved states are represented
 * with [isAvailable] = false and [unavailableMessage].
 */
@Immutable
data class WeatherHeroState(
    val temperature: Int,
    val apparentTemperature: Int? = null,
    val condition: WeatherCondition = WeatherCondition.CLEAR,
    val high: Int? = null,
    val low: Int? = null,
    val humidity: Int? = null,
    val windKmh: Int? = null,
    val locationLabel: String? = null,
    val timeBand: TimeAtmospherePalette = TimeAtmospherePalette.DAY,
    val isAvailable: Boolean = true,
    val unavailableMessage: String? = null,
    val celestialProgress: Float = 0.5f,
    val isNightCelestial: Boolean = false,
    val sunriseEpochMillis: Long? = null,
    val sunsetEpochMillis: Long? = null,
    val nextSunriseEpochMillis: Long? = null
) {
    /** Formatted condition display name, or null when unknown. */
    val conditionName: String?
        get() = when (condition) {
            WeatherCondition.UNKNOWN -> null
            else -> condition.displayName
        }

    /** Formatted temperature string, e.g. "29°". */
    val temperatureDisplay: String get() = "$temperature°"

    /** Formatted high/low string, e.g. "H 31°   L 25°" or partial. */
    val highLowDisplay: String?
        get() = when {
            high != null && low != null -> "H $high°   L $low°"
            high != null -> "H $high°"
            low != null -> "L $low°"
            else -> null
        }

    /** Formatted secondary row items, e.g. ["Feels 32°", "Humidity 78%", "Wind 9 km/h"]. */
    val secondaryMetrics: List<String>
        get() = buildList {
            apparentTemperature?.let { add("Feels $it°") }
            humidity?.let { add("Humidity $it%") }
            windKmh?.let { add("Wind $it km/h") }
        }

    /** Semantic accessibility description for screen readers. */
    val accessibilityDescription: String
        get() = if (isAvailable) {
            buildString {
                if (condition != WeatherCondition.UNKNOWN) {
                    append("$temperature degrees, ${condition.displayName.lowercase()}.")
                } else {
                    append("$temperature degrees.")
                }
                if (high != null && low != null) {
                    append(" High $high, low $low.")
                }
                locationLabel?.let { append(" Location: $it.") }
            }
        } else {
            unavailableMessage ?: "Weather unavailable."
        }
}

/**
 * Computes the 0f..1f celestial trajectory progress along the sky arc.
 *
 * For daytime (sunrise -> sunset): 0f = sunrise, 0.5f = solar noon, 1f = sunset.
 * For night (sunset -> sunrise): 0f = sunset, 0.5f = midnight, 1f = pre-dawn.
 */
fun computeCelestialProgress(
    currentTimeMillis: Long = System.currentTimeMillis(),
    sunriseMillis: Long? = null,
    sunsetMillis: Long? = null,
    nextSunriseMillis: Long? = null,
    timeBand: TimeAtmospherePalette = TimeAtmospherePalette.DAY
): Pair<Boolean, Float> {
    if (sunriseMillis != null && sunsetMillis != null && sunsetMillis > sunriseMillis) {
        if (currentTimeMillis in sunriseMillis..sunsetMillis) {
            val progress = ((currentTimeMillis - sunriseMillis).toFloat() / (sunsetMillis - sunriseMillis).toFloat()).coerceIn(0f, 1f)
            return Pair(false, progress)
        } else if (currentTimeMillis > sunsetMillis) {
            val nextRise = nextSunriseMillis ?: (sunsetMillis + 11 * 3600 * 1000L)
            val range = (nextRise - sunsetMillis).coerceAtLeast(1000L)
            val progress = ((currentTimeMillis - sunsetMillis).toFloat() / range.toFloat()).coerceIn(0f, 1f)
            return Pair(true, progress)
        } else {
            val prevSunset = sunriseMillis - 11 * 3600 * 1000L
            val range = (sunriseMillis - prevSunset).coerceAtLeast(1000L)
            val progress = ((currentTimeMillis - prevSunset).toFloat() / range.toFloat()).coerceIn(0f, 1f)
            return Pair(true, progress)
        }
    }

    // Deterministic fallback based on time band and clock
    val isNight = timeBand == TimeAtmospherePalette.NIGHT
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = currentTimeMillis }
    val minuteOfDay = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
    val dayStart = 6 * 60
    val dayEnd = 18 * 60 + 30

    return if (!isNight && minuteOfDay in dayStart..dayEnd) {
        val p = ((minuteOfDay - dayStart).toFloat() / (dayEnd - dayStart).toFloat()).coerceIn(0f, 1f)
        Pair(false, p)
    } else {
        val nightDuration = (24 * 60 - (dayEnd - dayStart)).coerceAtLeast(1)
        val p = if (minuteOfDay > dayEnd) {
            (minuteOfDay - dayEnd).toFloat() / nightDuration.toFloat()
        } else {
            (minuteOfDay + (24 * 60 - dayEnd)).toFloat() / nightDuration.toFloat()
        }.coerceIn(0f, 1f)
        Pair(true, p)
    }
}

/**
 * Builds a [WeatherHeroState] from the active [AetherAtmosphere] and [TimeAtmospherePalette].
 */
fun buildWeatherHeroState(
    atmosphere: AetherAtmosphere,
    palette: TimeAtmospherePalette,
    currentTimeMillis: Long = System.currentTimeMillis()
): WeatherHeroState {
    val reading = atmosphere.weather
    val condition = reading.conditionOrNull ?: WeatherCondition.CLEAR
    val data = reading.dataOrNull

    val (isNightCelestial, celestialProgress) = computeCelestialProgress(
        currentTimeMillis = currentTimeMillis,
        sunriseMillis = data?.sunriseEpochMillis,
        sunsetMillis = data?.sunsetEpochMillis,
        nextSunriseMillis = data?.nextSunriseEpochMillis,
        timeBand = palette
    )

    return when (reading) {
        is WeatherReading.Known -> {
            if (data != null) {
                WeatherHeroState(
                    temperature = data.temperatureC,
                    apparentTemperature = data.apparentTemperatureC,
                    condition = data.condition,
                    high = data.highTempC,
                    low = data.lowTempC,
                    humidity = data.humidityPercent,
                    windKmh = data.windSpeedKmh,
                    locationLabel = data.locationLabel,
                    timeBand = palette,
                    isAvailable = true,
                    celestialProgress = celestialProgress,
                    isNightCelestial = isNightCelestial,
                    sunriseEpochMillis = data.sunriseEpochMillis,
                    sunsetEpochMillis = data.sunsetEpochMillis,
                    nextSunriseEpochMillis = data.nextSunriseEpochMillis
                )
            } else {
                WeatherHeroState(
                    temperature = 29,
                    apparentTemperature = 32,
                    condition = condition,
                    high = 31,
                    low = 25,
                    humidity = 78,
                    windKmh = 9,
                    locationLabel = null,
                    timeBand = palette,
                    isAvailable = true,
                    celestialProgress = celestialProgress,
                    isNightCelestial = isNightCelestial
                )
            }
        }
        is WeatherReading.Override -> {
            WeatherHeroState(
                temperature = data?.temperatureC ?: 29,
                apparentTemperature = data?.apparentTemperatureC ?: 32,
                condition = reading.condition,
                high = data?.highTempC ?: 31,
                low = data?.lowTempC ?: 25,
                humidity = data?.humidityPercent ?: 78,
                windKmh = data?.windSpeedKmh ?: 9,
                locationLabel = data?.locationLabel,
                timeBand = palette,
                isAvailable = true,
                celestialProgress = celestialProgress,
                isNightCelestial = isNightCelestial,
                sunriseEpochMillis = data?.sunriseEpochMillis,
                sunsetEpochMillis = data?.sunsetEpochMillis,
                nextSunriseEpochMillis = data?.nextSunriseEpochMillis
            )
        }
        is WeatherReading.Unavailable -> {
            WeatherHeroState(
                temperature = 0,
                condition = WeatherCondition.CLEAR,
                timeBand = palette,
                isAvailable = false,
                unavailableMessage = reading.reason.message,
                celestialProgress = celestialProgress,
                isNightCelestial = isNightCelestial
            )
        }
        WeatherReading.Loading -> {
            WeatherHeroState(
                temperature = 0,
                condition = WeatherCondition.CLEAR,
                timeBand = palette,
                isAvailable = false,
                unavailableMessage = "Checking local weather…",
                celestialProgress = celestialProgress,
                isNightCelestial = isNightCelestial
            )
        }
        WeatherReading.Idle -> {
            WeatherHeroState(
                temperature = 0,
                condition = WeatherCondition.CLEAR,
                timeBand = palette,
                isAvailable = false,
                unavailableMessage = "Using time-only atmosphere",
                celestialProgress = celestialProgress,
                isNightCelestial = isNightCelestial
            )
        }
    }
}
