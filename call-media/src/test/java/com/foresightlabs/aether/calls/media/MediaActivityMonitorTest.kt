package com.foresightlabs.aether.calls.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The media-activity monitor is what classifies a silent call (capture dead
 * vs playback dead vs both alive), so its emission behaviour is contract:
 * activity is always reported, silence is a bounded positive observation,
 * and a stale monitor never samples a session that replaced it.
 */
class MediaActivityMonitorTest {

    private val lines = mutableListOf<String>()
    private lateinit var original: (String) -> Unit

    @Before
    fun captureSink() {
        original = CallDiagnostics.sink
        CallDiagnostics.sink = { lines += it }
    }

    private fun monitor(): MediaActivityMonitor = MediaActivityMonitor(periodMillis = 5_000L)

    private companion object {
        const val CALL_ID = 42L
        const val GENERATION = 7L
    }

    private fun sample(
        capture: Long = 0,
        playback: Long = 0,
        muted: Boolean = false
    ): MediaActivitySample = MediaActivitySample(capture, playback, muted, false, false)

    @Test
    fun firstTickIsEmittedAsBaseline() {
        val m = monitor()
        m.tick(CALL_ID, GENERATION, { sample(capture = 0, playback = 0) }, { true })

        val line = lines.single()
        assertTrue(line.contains("stage=MEDIA_ACTIVITY"))
        assertTrue(line.contains("capture=0s(new)"))
        assertTrue(line.contains("playback=0s(new)"))
    }

    @Test
    fun growingCountersAreEmittedWithDeltas() {
        val m = monitor()
        m.tick(CALL_ID, GENERATION, { sample(capture = 0, playback = 0) }, { true })
        lines.clear()
        m.tick(CALL_ID, GENERATION, { sample(capture = 5, playback = 0) }, { true })

        val line = lines.single()
        assertTrue(line.contains("capture=5s(+5s)"))
        assertTrue(line.contains("playback=0s(+0s)"))
    }

    @Test
    fun captureActivityIsDistinguishableFromPlaybackActivity() {
        val m = monitor()
        m.tick(CALL_ID, GENERATION, { sample(capture = 0, playback = 0) }, { true })
        lines.clear()
        // Mic feeding the send path, but nothing ever decoded from remote.
        m.tick(CALL_ID, GENERATION, { sample(capture = 10, playback = 0) }, { true })

        val line = lines.single()
        assertTrue(line.contains("capture=10s(+10s)"))
        assertTrue(line.contains("playback=0s(+0s)"))
    }

    @Test
    fun aQuietTickStillEmitsOnceThenFallsBackToHeartbeat() {
        val m = monitor()
        m.tick(CALL_ID, GENERATION, { sample(capture = 5, playback = 5) }, { true })
        lines.clear()

        // First quiet tick: emitted, so silence is a positive observation.
        m.tick(CALL_ID, GENERATION, { sample(capture = 5, playback = 5) }, { true })
        assertTrue(lines.single().contains("capture=5s(+0s)"))
        lines.clear()

        // The next QUIET_HEARTBEAT_TICKS-1 quiet ticks are silent...
        repeat(10) { m.tick(CALL_ID, GENERATION, { sample(capture = 5, playback = 5) }, { true }) }
        assertTrue("no heartbeat expected yet", lines.isEmpty())

        // ...until the heartbeat boundary.
        m.tick(CALL_ID, GENERATION, { sample(capture = 5, playback = 5) }, { true })
        assertTrue(lines.single().contains("stage=MEDIA_ACTIVITY"))
    }

    @Test
    fun aFailingSamplerIsReportedThenThrottled() {
        val m = monitor()
        m.tick(CALL_ID, GENERATION, { null }, { true })
        assertTrue(lines.single().contains("sample=unavailable"))
        lines.clear()

        repeat(4) { m.tick(CALL_ID, GENERATION, { null }, { true }) }
        assertTrue("failures 2..5 must be throttled", lines.isEmpty())

        m.tick(CALL_ID, GENERATION, { null }, { true })
        assertTrue(lines.single().contains("failures=6"))
    }

    @Test
    fun aSampleThatRecoversResetsFailureAccounting() {
        val m = monitor()
        m.tick(CALL_ID, GENERATION, { null }, { true })
        lines.clear()
        m.tick(CALL_ID, GENERATION, { sample(capture = 3, playback = 3) }, { true })
        lines.clear()
        m.tick(CALL_ID, GENERATION, { null }, { true })

        assertTrue("recovered sampler restarts failure count", lines.single().contains("failures=1"))
    }

    @Test
    fun aStaleMonitorNeverEmits() {
        val m = monitor()
        m.tick(CALL_ID, GENERATION, { sample(capture = 1, playback = 1) }, { false })
        assertTrue(lines.isEmpty())
    }

    @Test
    fun mutedStateIsCarriedInEverySampleLine() {
        val m = monitor()
        m.tick(CALL_ID, GENERATION, { sample(muted = true) }, { true })
        assertTrue(lines.single().contains("muted=true"))
    }

    @Test
    fun stopReleasesTheSchedule() {
        val m = monitor()
        val scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
        try {
            val custom = MediaActivityMonitor(periodMillis = 10_000L, scheduler = scheduler)
            var sampled = false
            custom.start(1L, 1L, { sampled = true; sample() }, { true })
            custom.stop()
            // No direct way to observe the cancelled future; the contract is
            // that stop() does not throw and a second stop is idempotent.
            custom.stop()
            assertFalse(sampled)
        } finally {
            scheduler.shutdownNow()
        }
    }
}
