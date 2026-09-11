package com.foresightlabs.aether.calls.media

enum class MediaConnectionState {
    IDLE,
    INITIALIZING,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    FAILED,
    UNAVAILABLE,
    STOPPED
}

enum class AudioRoute {
    EARPIECE,
    SPEAKER,
    BLUETOOTH,
    WIRED_HEADSET
}

/** Which side of a video pipeline a decoded frame belongs to. */
enum class VideoFrameOrigin {
    /** Our own outgoing camera capture, echoed back for local preview. */
    LOCAL,

    /** The other party's incoming camera video. */
    REMOTE
}

/**
 * One decoded video frame, already converted to a renderer-ready pixel
 * buffer.
 *
 * The native engine hands frames back as raw planar buffers with no explicit
 * pixel-format field; [pixels] is produced by converting that buffer under
 * the assumption that it is planar I420, the format every video path in the
 * native engine's own source converts through internally. That assumption is
 * documented, not verified against real device output -- see
 * docs/architecture/calling-native-stack.md.
 */
data class DecodedVideoFrame(
    val origin: VideoFrameOrigin,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val pixels: IntArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DecodedVideoFrame) return false
        return origin == other.origin &&
            width == other.width &&
            height == other.height &&
            rotationDegrees == other.rotationDegrees &&
            pixels.contentEquals(other.pixels)
    }

    override fun hashCode(): Int {
        var result = origin.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + rotationDegrees
        result = 31 * result + pixels.contentHashCode()
        return result
    }
}

interface NativeCallEngineCallback {
    fun onConnectionStateChanged(stateOrdinal: Int)
    fun onSignalBarsChanged(bars: Int)
    fun onAudioLevelsChanged(localLevel: Float, remoteLevel: Float)
    fun onError(error: String)

    /** Signalling bytes the media engine needs delivered through TDLib. */
    fun onOutgoingSignalingData(data: ByteArray)

    /** A decoded video frame is ready to render. */
    fun onVideoFrame(frame: DecodedVideoFrame)
}
