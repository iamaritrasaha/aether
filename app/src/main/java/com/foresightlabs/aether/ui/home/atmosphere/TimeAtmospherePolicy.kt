package com.foresightlabs.aether.ui.home.atmosphere

import androidx.annotation.RequiresApi
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import java.time.Clock
import java.time.LocalTime
import java.time.ZoneId
import java.util.Calendar
import java.util.TimeZone

/**
 * Visual parameters defining the resolved time-of-day Home atmosphere at a specific moment.
 */
@Immutable
data class TimeAtmosphere(
    val period: TimePeriod,
    val nextPeriod: TimePeriod,
    val progress: Float,
    val primaryAccent: Color,
    val colors: List<Color>,
    val glowColor: Color,
    val shadowColor: Color,
    val glowIntensity: Float,
    val ambientMotionSpeed: Float,
    val depthIntensity: Float,
    val lineDensity: Float,
    val lineOpacity: Float,
    val lineLength: Float,
    val curvature: Float,
    val intersectionFrequency: Float,
    val warmth: Float
)

@Immutable
internal data class PeriodVisualProfile(
    val primaryAccent: Color,
    val colors: List<Color>,
    val glowColor: Color,
    val shadowColor: Color,
    val glowIntensity: Float,
    val ambientMotionSpeed: Float,
    val depthIntensity: Float,
    val lineDensity: Float,
    val lineOpacity: Float,
    val lineLength: Float,
    val curvature: Float,
    val intersectionFrequency: Float,
    val warmth: Float
)

/**
 * Pure, testable policy for determining the Home visual atmosphere based ONLY on local time of day.
 *
 * Interpolates smoothly between adjacent conceptual periods:
 * PRE_DAWN -> MORNING -> NOON -> AFTERNOON -> EVENING -> NIGHT -> PRE_DAWN.
 */
object TimeAtmospherePolicy {

    private val PRE_DAWN_PROFILE = PeriodVisualProfile(
        primaryAccent = Color(0xFF6E7694),
        colors = listOf(
            Color(0xFF232738),
            Color(0xFF1B1E2C),
            Color(0xFF141621),
            Color(0xFF0F1018),
            Color(0xFF0A0B10)
        ),
        glowColor = Color(0xFF2D334A),
        shadowColor = Color(0xFF0B0C12),
        glowIntensity = 0.10f,
        ambientMotionSpeed = 0.15f,
        depthIntensity = 0.12f,
        lineDensity = 0.20f,
        lineOpacity = 0.12f,
        lineLength = 0.30f,
        curvature = 0.25f,
        intersectionFrequency = 0.15f,
        warmth = 0.0f
    )

    private val MORNING_PROFILE = PeriodVisualProfile(
        primaryAccent = Color(0xFF7E97B4),
        colors = listOf(
            Color(0xFF4A5C70),
            Color(0xFF3D4C5C),
            Color(0xFF303B48),
            Color(0xFF222B34),
            Color(0xFF151A20)
        ),
        glowColor = Color(0xFF586C85),
        shadowColor = Color(0xFF13171C),
        glowIntensity = 0.30f,
        ambientMotionSpeed = 0.70f,
        depthIntensity = 0.20f,
        lineDensity = 0.65f,
        lineOpacity = 0.32f,
        lineLength = 0.60f,
        curvature = 0.45f,
        intersectionFrequency = 0.50f,
        warmth = 0.05f
    )

    private val NOON_PROFILE = PeriodVisualProfile(
        primaryAccent = Color(0xFF96A6B8),
        colors = listOf(
            Color(0xFF5C6F84),
            Color(0xFF4B5B6D),
            Color(0xFF3A4756),
            Color(0xFF2A333E),
            Color(0xFF181D23)
        ),
        glowColor = Color(0xFF6F8094),
        shadowColor = Color(0xFF161A20),
        glowIntensity = 0.42f,
        ambientMotionSpeed = 0.50f,
        depthIntensity = 0.25f,
        lineDensity = 0.85f,
        lineOpacity = 0.42f,
        lineLength = 0.80f,
        curvature = 0.65f,
        intersectionFrequency = 0.75f,
        warmth = 0.15f
    )

    private val AFTERNOON_PROFILE = PeriodVisualProfile(
        primaryAccent = Color(0xFFB09882),
        colors = listOf(
            Color(0xFF635344),
            Color(0xFF504336),
            Color(0xFF3E342A),
            Color(0xFF2B241D),
            Color(0xFF181410)
        ),
        glowColor = Color(0xFF7A6856),
        shadowColor = Color(0xFF191613),
        glowIntensity = 0.30f,
        ambientMotionSpeed = 0.35f,
        depthIntensity = 0.22f,
        lineDensity = 0.70f,
        lineOpacity = 0.35f,
        lineLength = 0.75f,
        curvature = 0.50f,
        intersectionFrequency = 0.60f,
        warmth = 0.75f
    )

    private val EVENING_PROFILE = PeriodVisualProfile(
        primaryAccent = Color(0xFF8B7DAA),
        colors = listOf(
            Color(0xFF4A3E66),
            Color(0xFF3B3152),
            Color(0xFF2E263F),
            Color(0xFF201B2B),
            Color(0xFF14111B)
        ),
        glowColor = Color(0xFF5B4F78),
        shadowColor = Color(0xFF14121A),
        glowIntensity = 0.22f,
        ambientMotionSpeed = 0.25f,
        depthIntensity = 0.28f,
        lineDensity = 0.50f,
        lineOpacity = 0.28f,
        lineLength = 0.90f,
        curvature = 0.35f,
        intersectionFrequency = 0.40f,
        warmth = 0.40f
    )

    private val NIGHT_PROFILE = PeriodVisualProfile(
        primaryAccent = Color(0xFF656D8A),
        colors = listOf(
            Color(0xFF2A2E42),
            Color(0xFF212434),
            Color(0xFF191B26),
            Color(0xFF12131C),
            Color(0xFF0C0D13)
        ),
        glowColor = Color(0xFF3A405A),
        shadowColor = Color(0xFF0D0E14),
        glowIntensity = 0.12f,
        ambientMotionSpeed = 0.15f,
        depthIntensity = 0.15f,
        lineDensity = 0.25f,
        lineOpacity = 0.15f,
        lineLength = 0.40f,
        curvature = 0.20f,
        intersectionFrequency = 0.20f,
        warmth = 0.0f
    )

    internal fun profileFor(period: TimePeriod): PeriodVisualProfile = when (period) {
        TimePeriod.PRE_DAWN -> PRE_DAWN_PROFILE
        TimePeriod.MORNING -> MORNING_PROFILE
        TimePeriod.NOON -> NOON_PROFILE
        TimePeriod.AFTERNOON -> AFTERNOON_PROFILE
        TimePeriod.EVENING -> EVENING_PROFILE
        TimePeriod.NIGHT -> NIGHT_PROFILE
    }

    /**
     * Resolves the [TimeAtmosphere] for a specific second of the day (0..86399).
     */
    fun resolve(secondOfDayInput: Int): TimeAtmosphere {
        val totalSec = TimePeriod.SECONDS_IN_DAY
        val sec = ((secondOfDayInput % totalSec) + totalSec) % totalSec
        val period: TimePeriod
        val elapsed: Int
        val duration: Int

        if (sec >= 22 * 3600 || sec < 3 * 3600) {
            period = TimePeriod.NIGHT
            elapsed = if (sec >= 22 * 3600) sec - 22 * 3600 else (24 * 3600 - 22 * 3600) + sec
            duration = 5 * 3600
        } else if (sec < 6 * 3600) {
            period = TimePeriod.PRE_DAWN
            elapsed = sec - 3 * 3600
            duration = 3 * 3600
        } else if (sec < 11 * 3600) {
            period = TimePeriod.MORNING
            elapsed = sec - 6 * 3600
            duration = 5 * 3600
        } else if (sec < 14 * 3600) {
            period = TimePeriod.NOON
            elapsed = sec - 11 * 3600
            duration = 3 * 3600
        } else if (sec < 18 * 3600) {
            period = TimePeriod.AFTERNOON
            elapsed = sec - 14 * 3600
            duration = 4 * 3600
        } else {
            period = TimePeriod.EVENING
            elapsed = sec - 18 * 3600
            duration = 4 * 3600
        }

        val nextPeriod = period.next()
        val progress = (elapsed.toDouble() / duration.toDouble()).toFloat().coerceIn(0f, 1f)

        val profileA = profileFor(period)
        val profileB = profileFor(nextPeriod)

        val interpolatedColors = profileA.colors.zip(profileB.colors).map { (c1, c2) ->
            lerp(c1, c2, progress)
        }

        return TimeAtmosphere(
            period = period,
            nextPeriod = nextPeriod,
            progress = progress,
            primaryAccent = lerp(profileA.primaryAccent, profileB.primaryAccent, progress),
            colors = interpolatedColors,
            glowColor = lerp(profileA.glowColor, profileB.glowColor, progress),
            shadowColor = lerp(profileA.shadowColor, profileB.shadowColor, progress),
            glowIntensity = profileA.glowIntensity + progress * (profileB.glowIntensity - profileA.glowIntensity),
            ambientMotionSpeed = profileA.ambientMotionSpeed + progress * (profileB.ambientMotionSpeed - profileA.ambientMotionSpeed),
            depthIntensity = profileA.depthIntensity + progress * (profileB.depthIntensity - profileA.depthIntensity),
            lineDensity = profileA.lineDensity + progress * (profileB.lineDensity - profileA.lineDensity),
            lineOpacity = profileA.lineOpacity + progress * (profileB.lineOpacity - profileA.lineOpacity),
            lineLength = profileA.lineLength + progress * (profileB.lineLength - profileA.lineLength),
            curvature = profileA.curvature + progress * (profileB.curvature - profileA.curvature),
            intersectionFrequency = profileA.intersectionFrequency + progress * (profileB.intersectionFrequency - profileA.intersectionFrequency),
            warmth = profileA.warmth + progress * (profileB.warmth - profileA.warmth)
        )
    }

    fun resolve(hour: Int, minute: Int, second: Int = 0): TimeAtmosphere {
        return resolve(hour * 3600 + minute * 60 + second)
    }

    @RequiresApi(26)
    fun resolve(localTime: LocalTime): TimeAtmosphere {
        return resolve(localTime.toSecondOfDay())
    }

    @RequiresApi(26)
    fun resolve(clock: Clock, zoneId: ZoneId = ZoneId.systemDefault()): TimeAtmosphere {
        return resolve(clock.millis(), TimeZone.getTimeZone(zoneId.id))
    }

    fun resolve(calendar: Calendar): TimeAtmosphere {
        val secondOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 3600 +
            calendar.get(Calendar.MINUTE) * 60 +
            calendar.get(Calendar.SECOND)
        return resolve(secondOfDay)
    }

    fun resolve(timeMillis: Long = System.currentTimeMillis(), timeZone: TimeZone = TimeZone.getDefault()): TimeAtmosphere {
        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = timeMillis }
        return resolve(cal)
    }
}
