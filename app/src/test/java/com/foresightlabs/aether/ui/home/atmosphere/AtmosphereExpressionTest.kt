package com.foresightlabs.aether.ui.home.atmosphere

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Home and Conversation both render [AetherTimeAtmosphere] from the exact same
 * [TimeAtmosphere] -- resolved once, purely from local time. [AtmosphereExpression]
 * only scales how loudly that shared state is painted; it must never fork the
 * period, progress or palette into a second atmosphere path.
 */
class AtmosphereExpressionTest {

    @Test
    fun homeExpressionLeavesTheCanonicalStateUnchanged() {
        val atmosphere = TimeAtmospherePolicy.resolve(14, 30, 0)
        val home = atmosphere.scaledFor(AtmosphereExpression.HOME)
        assertEquals(atmosphere, home)
    }

    @Test
    fun conversationSharesTheSamePeriodProgressAndPalette() {
        TimePeriod.entries.forEach { period ->
            val secondOfDay = (period.startSecondOfDay + 1) % TimePeriod.SECONDS_IN_DAY
            val atmosphere = TimeAtmospherePolicy.resolve(secondOfDay)
            val conversation = atmosphere.scaledFor(AtmosphereExpression.CONVERSATION)

            // Same canonical time state: period, progress and every color stay identical.
            assertEquals(atmosphere.period, conversation.period)
            assertEquals(atmosphere.nextPeriod, conversation.nextPeriod)
            assertEquals(atmosphere.progress, conversation.progress, 0.0001f)
            assertEquals(atmosphere.colors, conversation.colors)
            assertEquals(atmosphere.primaryAccent, conversation.primaryAccent)
            assertEquals(atmosphere.glowColor, conversation.glowColor)
            assertEquals(atmosphere.shadowColor, conversation.shadowColor)
            assertEquals(atmosphere.warmth, conversation.warmth, 0.0001f)
        }
    }

    @Test
    fun conversationIsStrictlyLowerIntensityThanHomeAcrossEveryPeriod() {
        for (sec in 0 until TimePeriod.SECONDS_IN_DAY step 900) {
            val home = TimeAtmospherePolicy.resolve(sec)
            val conversation = home.scaledFor(AtmosphereExpression.CONVERSATION)

            assertTrue("lineDensity must be quieter at sec=$sec", conversation.lineDensity <= home.lineDensity)
            assertTrue("lineOpacity must be quieter at sec=$sec", conversation.lineOpacity <= home.lineOpacity)
            assertTrue("glowIntensity must be quieter at sec=$sec", conversation.glowIntensity <= home.glowIntensity)
            assertTrue("ambientMotionSpeed must be slower at sec=$sec", conversation.ambientMotionSpeed <= home.ambientMotionSpeed)

            // Strictly quieter whenever Home itself has any expression to turn down.
            if (home.lineDensity > 0f) assertTrue(conversation.lineDensity < home.lineDensity)
            if (home.lineOpacity > 0f) assertTrue(conversation.lineOpacity < home.lineOpacity)
            if (home.glowIntensity > 0f) assertTrue(conversation.glowIntensity < home.glowIntensity)
            if (home.ambientMotionSpeed > 0f) assertTrue(conversation.ambientMotionSpeed < home.ambientMotionSpeed)
        }
    }

    @Test
    fun conversationNeverGoesNegativeOrExceedsHome() {
        for (sec in 0 until TimePeriod.SECONDS_IN_DAY step 3600) {
            val home = TimeAtmospherePolicy.resolve(sec)
            val conversation = home.scaledFor(AtmosphereExpression.CONVERSATION)

            assertTrue(conversation.lineDensity >= 0f)
            assertTrue(conversation.lineOpacity >= 0f)
            assertTrue(conversation.glowIntensity >= 0f)
            assertTrue(conversation.ambientMotionSpeed >= 0f)
        }
    }
}
