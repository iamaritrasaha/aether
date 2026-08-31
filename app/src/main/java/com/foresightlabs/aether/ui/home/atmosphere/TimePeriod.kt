package com.foresightlabs.aether.ui.home.atmosphere

/**
 * Conceptual time-of-day periods for the Aether Home atmosphere.
 *
 * The atmosphere shifts continuously through these 6 periods:
 * PRE_DAWN -> MORNING -> NOON -> AFTERNOON -> EVENING -> NIGHT -> PRE_DAWN.
 *
 * Exact clock boundaries are centralized here in seconds-of-day (0..86399).
 */
enum class TimePeriod(
    val displayName: String,
    val startSecondOfDay: Int,
    val endSecondOfDay: Int
) {
    /** 03:00:00 - 05:59:59 (10,800s - 21,599s) */
    PRE_DAWN(
        displayName = "Pre-Dawn",
        startSecondOfDay = 3 * 3600,
        endSecondOfDay = 6 * 3600
    ),

    /** 06:00:00 - 10:59:59 (21,600s - 39,599s) */
    MORNING(
        displayName = "Morning",
        startSecondOfDay = 6 * 3600,
        endSecondOfDay = 11 * 3600
    ),

    /** 11:00:00 - 13:59:59 (39,600s - 50,399s) */
    NOON(
        displayName = "Noon",
        startSecondOfDay = 11 * 3600,
        endSecondOfDay = 14 * 3600
    ),

    /** 14:00:00 - 17:59:59 (50,400s - 64,799s) */
    AFTERNOON(
        displayName = "Afternoon",
        startSecondOfDay = 14 * 3600,
        endSecondOfDay = 18 * 3600
    ),

    /** 18:00:00 - 21:59:59 (64,800s - 79,199s) */
    EVENING(
        displayName = "Evening",
        startSecondOfDay = 18 * 3600,
        endSecondOfDay = 22 * 3600
    ),

    /** 22:00:00 - 02:59:59 (79,200s - 10,799s, crosses midnight) */
    NIGHT(
        displayName = "Night",
        startSecondOfDay = 22 * 3600,
        endSecondOfDay = 3 * 3600
    );

    /** Returns the next logical period in the daily cycle. */
    fun next(): TimePeriod = when (this) {
        PRE_DAWN -> MORNING
        MORNING -> NOON
        NOON -> AFTERNOON
        AFTERNOON -> EVENING
        EVENING -> NIGHT
        NIGHT -> PRE_DAWN
    }

    companion object {
        const val SECONDS_IN_DAY = 86400
    }
}
