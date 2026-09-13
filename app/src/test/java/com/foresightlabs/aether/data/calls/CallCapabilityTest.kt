package com.foresightlabs.aether.data.calls

import com.foresightlabs.aether.AetherFeatureFlags
import com.foresightlabs.aether.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The release capability contract for calling (AetherFeatureFlags.CALLS_ENABLED
 * and friends). The Play release must compile calling OUT; internal/debug
 * builds keep it IN. These tests pin that boundary in BOTH variants: the
 * release-only assertions run under testReleaseUnitTest and skip elsewhere.
 */
class CallCapabilityTest {

    @Test
    fun `master capability is the conjunction of the per-backend capabilities`() {
        // A release exposing one backend while hiding the other would
        // advertise a half feature; the build script compiles all three from
        // one switch, so they can only disagree by an editing mistake.
        assertEquals(
            AetherFeatureFlags.AETHER_CALLS_ENABLED && AetherFeatureFlags.TELEGRAM_CALLS_ENABLED,
            AetherFeatureFlags.CALLS_ENABLED
        )
    }

    @Test
    fun `standard debug build compiles calling in`() {
        // Debug-only: under testReleaseUnitTest this must SKIP, not assert --
        // the release variant's assertions live in the test below.
        assumeTrue("release variant assertions live in the test below", BuildConfig.DEBUG)
        assertTrue(BuildConfig.CALLING_ENABLED)
        assertTrue(AetherFeatureFlags.CALLS_ENABLED)
    }

    @Test
    fun `play release compiles calling out`() {
        assumeFalse(BuildConfig.DEBUG) // only meaningful under testReleaseUnitTest
        assertFalse(BuildConfig.CALLING_ENABLED)
        assertFalse(BuildConfig.AETHER_CALLS_ENABLED)
        assertFalse(BuildConfig.TELEGRAM_CALLS_ENABLED)
        assertFalse(AetherFeatureFlags.CALLS_ENABLED)
        assertFalse(AetherFeatureFlags.AETHER_CALLS_ENABLED)
        assertFalse(AetherFeatureFlags.TELEGRAM_CALLS_ENABLED)
    }
}
