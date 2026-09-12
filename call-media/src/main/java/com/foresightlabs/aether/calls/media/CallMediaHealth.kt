package com.foresightlabs.aether.calls.media

/**
 * Evidence-based snapshot of what the media pipeline is actually doing for
 * the active call, assembled from the engine's own counters and callbacks.
 *
 * This is diagnostic/health evidence ONLY. It must never be used to promote
 * a call to CONNECTED -- signalling ([MediaConnectionState]) remains the sole
 * source of truth for call state. Counters moving prove media is flowing;
 * counters frozen prove it is not; neither says whose fault it is.
 */
data class CallMediaHealth(
    val generation: Long,
    /** Native engine's own media-state flags at sample time. */
    val muted: Boolean,
    val videoPaused: Boolean,
    val videoStopped: Boolean,
    /** Accumulated microphone media-time fed to the WebRTC send path (seconds). */
    val captureSeconds: Long,
    /** Accumulated decoded remote media-time delivered to the speaker writer (seconds). */
    val playbackSeconds: Long,
    /** Wall-clock (epoch ms) of the last sample where each counter grew; null = never. */
    val lastCaptureActivityMs: Long?,
    val lastPlaybackActivityMs: Long?,
    /** True once the remote peer's microphone stream was reported Active. */
    val remoteMicActive: Boolean
) {
    /**
     * Failure classification from counters alone (the letter classes used by
     * the physical-call investigation). [UNKNOWN] covers "no samples yet".
     */
    fun classify(captureWasActive: Boolean, playbackWasActive: Boolean): MediaFlowClassification = when {
        captureSeconds > 0 && playbackSeconds > 0 -> MediaFlowClassification.BOTH_ALIVE
        captureSeconds > 0 && playbackSeconds == 0L ->
            if (remoteMicActive) MediaFlowClassification.INCOMING_BLOCKED
            else MediaFlowClassification.PLAYBACK_DEAD
        captureSeconds == 0L && playbackSeconds > 0 -> MediaFlowClassification.CAPTURE_DEAD
        captureWasActive || playbackWasActive -> MediaFlowClassification.STALLED
        else -> MediaFlowClassification.UNKNOWN
    }
}

enum class MediaFlowClassification {
    /** Both directions flowing: full pipeline alive. */
    BOTH_ALIVE,

    /** Mic media-time grew but playback never did, and no remote mic source. */
    PLAYBACK_DEAD,

    /** Mic media-time grew, playback dead, but the remote mic IS active: incoming media blocked in transit. */
    INCOMING_BLOCKED,

    /** Playback flowing but capture never produced media. */
    CAPTURE_DEAD,

    /** Counters moved at some point but are frozen in the latest sample window. */
    STALLED,

    /** No usable sample yet. */
    UNKNOWN
}
