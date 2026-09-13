package com.foresightlabs.aether.calls.media

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The health model is what turns counters into a named failure class during a
 * physical call; every classification branch is pinned here so the letter
 * classes stay stable across refactors.
 */
class CallMediaHealthTest {

    private fun health(
        capture: Long = 0,
        playback: Long = 0,
        remoteMic: Boolean = false
    ) = CallMediaHealth(
        generation = 1,
        muted = false,
        videoPaused = false,
        videoStopped = true,
        captureSeconds = capture,
        playbackSeconds = playback,
        lastCaptureActivityMs = null,
        lastPlaybackActivityMs = null,
        remoteMicActive = remoteMic,
        remoteVideoSourcePresent = false,
        localCameraIsFront = true
    )

    @Test
    fun bothCountersGrowingMeansFullPipelineAlive() {
        assertEquals(MediaFlowClassification.BOTH_ALIVE, health(capture = 12, playback = 10).classify(false, false))
    }

    @Test
    fun captureAliveWithoutRemoteMicMeansPlaybackDead() {
        assertEquals(MediaFlowClassification.PLAYBACK_DEAD, health(capture = 12).classify(false, false))
    }

    @Test
    fun captureAliveWithActiveRemoteMicMeansIncomingBlocked() {
        // Remote mic is ACTIVE per signalling, so the remote IS sending; the
        // block is in transit (their RTP path, our receive/decrypt path).
        assertEquals(MediaFlowClassification.INCOMING_BLOCKED, health(capture = 12, remoteMic = true).classify(false, false))
    }

    @Test
    fun playbackAliveWithoutCaptureMeansCaptureDead() {
        assertEquals(MediaFlowClassification.CAPTURE_DEAD, health(playback = 9).classify(false, false))
    }

    @Test
    fun countersThatWereActiveButAreNowZeroClassifyAsStalled() {
        assertEquals(MediaFlowClassification.STALLED, health().classify(captureWasActive = true, playbackWasActive = false))
        assertEquals(MediaFlowClassification.STALLED, health().classify(captureWasActive = false, playbackWasActive = true))
    }

    @Test
    fun noEvidenceClassifiesAsUnknown() {
        assertEquals(MediaFlowClassification.UNKNOWN, health().classify(false, false))
    }
}
