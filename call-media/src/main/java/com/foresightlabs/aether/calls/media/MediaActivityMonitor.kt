package com.foresightlabs.aether.calls.media

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * One sample of the native engine's own media-pipeline counters for the
 * active call.
 *
 * - [captureSeconds]: total microphone media-time handed to the WebRTC send
 *   path so far (`StreamManager::time(Capture)` = frames x frame-time, in
 *   seconds -- `AudioStreamer::sendData` increments the same counter). A
 *   value that grows proves capture -> encoder input is alive; it does NOT
 *   by itself prove RTP left the device.
 * - [playbackSeconds]: total decoded remote media-time delivered toward the
 *   playback writer (`StreamManager::time(Playback)`; `AudioReceiver`
 *   increments the same counter). A value that grows proves remote RTP is
 *   arriving, decrypting and decoding.
 * - The muted/paused flags are the native engine's own [io.github.pytgcalls.media.MediaState].
 */
data class MediaActivitySample(
    val captureSeconds: Long,
    val playbackSeconds: Long,
    val muted: Boolean,
    val videoPaused: Boolean,
    val videoStopped: Boolean
)

/**
 * Polls the active call's [MediaActivitySample] on a fixed schedule and emits
 * throttled [CallStage.MEDIA_ACTIVITY] diagnostics lines.
 *
 * Native CONNECTED only proves ICE/DTLS writability -- it says nothing about
 * media. These counters are the only media-activity evidence the engine's
 * public API exposes, and polling them (rather than logging per packet or
 * per frame) keeps the log to one line per [periodMillis] while still
 * classifying a silent call into: capture dead, playback dead, or both alive.
 *
 * A sampler returning null (native call failed -- e.g. the session vanished
 * mid-poll) is reported, throttled, rather than silently skipped: "the
 * engine stopped answering" is itself a diagnostic fact.
 *
 * Ticks are generation-guarded through [stillActive], so a monitor whose
 * stop was missed can never sample a call that replaced this one.
 */
internal class MediaActivityMonitor(
    private val periodMillis: Long = DEFAULT_PERIOD_MILLIS,
    private val scheduler: ScheduledExecutorService = SharedScheduler.executor
) {
    private val lock = Any()
    private var future: ScheduledFuture<*>? = null
    private var callId: Long = -1L
    private var generation: Long = -1L
    private var sampler: (() -> MediaActivitySample?)? = null
    private var stillActive: (() -> Boolean)? = null
    private var lastCapture: Long = -1L
    private var lastPlayback: Long = -1L
    private var quietTicks: Int = 0
    private var sampleFailures: Int = 0
    private var lastCaptureActivityMs: Long? = null
    private var lastPlaybackActivityMs: Long? = null
    private var remoteMicActive: Boolean = false
    private var remoteVideoActive: Boolean = false
    private var localCameraIsFront: Boolean = true

    /** Latest evidence snapshot; null until the first usable sample. */
    @Volatile
    var latestHealth: CallMediaHealth? = null
        private set

    /** Feeds remote-source evidence (from the engine's remote-source callback). */
    fun onRemoteMicState(state: String?) {
        remoteMicActive = state == "ACTIVE"
        if (remoteMicActive) {
            latestHealth?.let { h ->
                latestHealth = h.copy(remoteMicActive = true)
            }
        }
    }

    /**
     * Feeds remote CAMERA source evidence. Any non-ACTIVE status (Paused,
     * Idling, or an unexpected/unknown value) counts as absent: the video
     * is not currently flowing.
     */
    fun onRemoteCameraState(state: String?) {
        remoteVideoActive = state == "ACTIVE"
        latestHealth?.let { h ->
            latestHealth = h.copy(remoteVideoSourcePresent = remoteVideoActive)
        }
    }

    /** Facing of the local camera the engine actually selected. */
    fun onLocalCameraFacing(isFront: Boolean) {
        localCameraIsFront = isFront
        latestHealth?.let { h ->
            latestHealth = h.copy(localCameraIsFront = isFront)
        }
    }

    /**
     * Begins sampling [callId]. [sampler] runs on the monitor's thread and
     * must tolerate the native session disappearing at any moment (return
     * null in that case). [stillActive] re-checks, per tick, that this
     * generation is still the live session.
     */
    fun start(
        callId: Long,
        generation: Long,
        sampler: () -> MediaActivitySample?,
        stillActive: () -> Boolean
    ) {
        stop()
        synchronized(lock) {
            this.callId = callId
            this.generation = generation
            this.sampler = sampler
            this.stillActive = stillActive
            lastCapture = -1L
            lastPlayback = -1L
            quietTicks = 0
            sampleFailures = 0
            lastCaptureActivityMs = null
            lastPlaybackActivityMs = null
            remoteMicActive = false
            remoteVideoActive = false
            latestHealth = null
            future = scheduler.scheduleWithFixedDelay(
                { scheduledTick() },
                periodMillis,
                periodMillis,
                TimeUnit.MILLISECONDS
            )
        }
    }

    fun stop() {
        synchronized(lock) {
            future?.cancel(false)
            future = null
            callId = -1L
            generation = -1L
            sampler = null
            stillActive = null
        }
    }

    private fun scheduledTick() {
        val (id, gen, sampleFn, activeCheck) = synchronized(lock) {
            TickContext(callId, generation, sampler, stillActive)
        }
        tick(id, gen, sampleFn, activeCheck)
    }

    private data class TickContext(
        val id: Long,
        val generation: Long,
        val sampler: (() -> MediaActivitySample?)?,
        val stillActive: (() -> Boolean)?
    )

    /**
     * One poll, with the session passed explicitly so the emission logic is
     * directly unit-testable; scheduled ticks forward the closures registered
     * by [start]. A null sampler/stillActive (never armed) is a no-op.
     */
    internal fun tick(
        callId: Long,
        generation: Long,
        sampler: (() -> MediaActivitySample?)?,
        stillActive: (() -> Boolean)?
    ) {
        if (callId < 0 || generation < 0) return
        val activeCheck = stillActive ?: return
        val sampleFn = sampler ?: return
        if (!activeCheck()) return

        val sample = try {
            sampleFn()
        } catch (_: Throwable) {
            // The sampler is native-facing; a disappearing session surfaces
            // here. Counted and reported below rather than escaping.
            null
        }

        if (sample == null) {
            val failures = synchronized(lock) { ++sampleFailures }
            if (failures == 1 || failures % FAILED_SAMPLE_REPORT_EVERY == 0) {
                CallDiagnostics.stage(
                    generation,
                    CallStage.MEDIA_ACTIVITY,
                    "sample=unavailable callId=$callId failures=$failures"
                )
            }
            return
        }

        val line = synchronized(lock) {
            val now = System.currentTimeMillis()
            val captureDelta = if (lastCapture >= 0) sample.captureSeconds - lastCapture else -1
            val playbackDelta = if (lastPlayback >= 0) sample.playbackSeconds - lastPlayback else -1
            if (captureDelta > 0) lastCaptureActivityMs = now
            if (playbackDelta > 0) lastPlaybackActivityMs = now
            val changed = lastCapture != sample.captureSeconds || lastPlayback != sample.playbackSeconds
            lastCapture = sample.captureSeconds
            lastPlayback = sample.playbackSeconds
            quietTicks = if (changed) 0 else quietTicks + 1
            sampleFailures = 0

            latestHealth = CallMediaHealth(
                generation = generation,
                muted = sample.muted,
                videoPaused = sample.videoPaused,
                videoStopped = sample.videoStopped,
                captureSeconds = sample.captureSeconds,
                playbackSeconds = sample.playbackSeconds,
                lastCaptureActivityMs = lastCaptureActivityMs,
                lastPlaybackActivityMs = lastPlaybackActivityMs,
                remoteMicActive = remoteMicActive,
                remoteVideoSourcePresent = remoteVideoActive,
                localCameraIsFront = localCameraIsFront
            )

            // Emit when there is activity, on the first sample, and as a
            // periodic heartbeat while fully quiet -- so "silence" is a
            // positive, bounded observation, not an absence of logs.
            val shouldEmit = changed || quietTicks == 1 || quietTicks % QUIET_HEARTBEAT_TICKS == 0
            if (shouldEmit) {
                val capturePart = formatCounter("capture", sample.captureSeconds, captureDelta)
                val playbackPart = formatCounter("playback", sample.playbackSeconds, playbackDelta)
                "media_activity callId=$callId $capturePart $playbackPart " +
                    "muted=${sample.muted} videoPaused=${sample.videoPaused} videoStopped=${sample.videoStopped}"
            } else {
                null
            }
        }
        if (line != null) {
            CallDiagnostics.stage(generation, CallStage.MEDIA_ACTIVITY, line)
        }
    }

    private fun formatCounter(name: String, total: Long, delta: Long): String =
        if (delta < 0) "$name=${total}s(new)" else "$name=${total}s(+${delta}s)"

    private companion object {
        const val DEFAULT_PERIOD_MILLIS = 5_000L

        /** Heartbeat spacing while nothing changes: 12 x 5s = one line a minute. */
        const val QUIET_HEARTBEAT_TICKS = 12

        /** Repeated sampler failures are reported at 1, then every 6th. */
        const val FAILED_SAMPLE_REPORT_EVERY = 6
    }
}

/** One daemon scheduler for the process; monitors only cancel their own task. */
private object SharedScheduler {
    val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "AetherCallMediaMonitor").apply { isDaemon = true }
    }
}
