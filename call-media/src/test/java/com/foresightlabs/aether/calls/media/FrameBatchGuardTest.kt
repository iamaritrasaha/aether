package com.foresightlabs.aether.calls.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stale-frame contract: a decoded frame batch from an old call
 * generation, a voice call, a disabled renderer, or a throttled window is
 * never admitted for conversion/rendering.
 */
class FrameBatchGuardTest {

    private fun process(
        rendererEnabled: Boolean = true,
        callIdMatches: Boolean = true,
        isCamera: Boolean = true,
        frames: Int = 1,
        now: Long = 10_000L,
        last: Long = 0L,
        minInterval: Long = 28L
    ) = FrameBatchGuard.shouldProcess(rendererEnabled, callIdMatches, isCamera, frames, now, last, minInterval)

    @Test
    fun aHealthyBatchIsAdmitted() {
        assertTrue(process(last = 0L))
        assertTrue(process(now = 10_050, last = 10_000))
    }

    @Test
    fun aBatchFromAnOldGenerationIsNeverAdmitted() {
        assertFalse("stale callId must never render into the new call", process(callIdMatches = false))
    }

    @Test
    fun voiceCallsAndDisabledRenderersNeverConvertFrames() {
        assertFalse(process(rendererEnabled = false))
    }

    @Test
    fun nonCameraAndEmptyBatchesAreAdmissionRefused() {
        assertFalse(process(isCamera = false))
        assertFalse(process(frames = 0))
    }

    @Test
    fun theThrottleWindowDropsTooEarlyBatches() {
        assertFalse(process(now = 10_010, last = 10_000))
        assertTrue(process(now = 10_028, last = 10_000))
    }
}
