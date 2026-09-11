package com.foresightlabs.aether.domain.calls

import com.foresightlabs.aether.calls.media.DecodedVideoFrame
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.drinkless.tdlib.TdApi

/**
 * State of the *media* transport, which is deliberately separate from TDLib's
 * signalling state.
 *
 * TDLib reaching `CallStateReady` means the two sides have agreed on servers and an
 * encryption key. It is not evidence that a single audio packet has flowed. Only
 * this enum may be treated as evidence of that, and only [CONNECTED] means audio is
 * actually running in both directions.
 */
enum class MediaConnectionState {
    IDLE,
    INITIALIZING,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    FAILED,

    /**
     * No media transport is available in this build, so no audio can be carried.
     *
     * Signalling may still succeed and the remote device may still ring. The call is
     * nonetheless not a usable call, and must be presented as such rather than shown
     * as connected.
     */
    UNAVAILABLE,
    STOPPED
}

enum class AudioRoute {
    EARPIECE,
    SPEAKER,
    BLUETOOTH,
    WIRED_HEADSET
}

interface TelegramCallMediaEngine {

    /**
     * Whether this build ships a media transport that can actually carry call audio.
     *
     * When false, signalling still works — the remote device will ring — but no audio
     * can flow in either direction, so a call must not be presented as usable.
     */
    val isMediaTransportAvailable: Boolean

    val state: StateFlow<MediaConnectionState>
    val audioRoute: StateFlow<AudioRoute>
    val isMuted: StateFlow<Boolean>

    /** Local/remote decoded video frames, for a video call's renderer to draw. */
    val videoFrames: SharedFlow<DecodedVideoFrame>

    /** Signalling bytes this engine needs delivered through TDLib's `SendCallSignalingData`. */
    val outgoingSignalingData: SharedFlow<ByteArray>

    /**
     * @param videoCaptureEnabled whether the camera may actually be opened for
     *   this call. Deliberately separate from `call.isVideo`: a video call whose
     *   camera permission was refused still connects as audio, and no camera or
     *   renderer code may run on a voice call's path at all.
     */
    suspend fun start(
        call: TdApi.Call,
        ready: TdApi.CallStateReady,
        videoCaptureEnabled: Boolean
    )

    fun setMicrophoneMuted(muted: Boolean)
    fun setAudioOutput(route: AudioRoute)
    fun setCameraEnabled(enabled: Boolean)
    fun switchCamera()

    /** TDLib's `UpdateNewCallSignalingData`, for this engine to consume. */
    fun submitIncomingSignalingData(callId: Long, data: ByteArray)
    fun stop()

    /**
     * Called by the call orchestration layer's connection watchdog when
     * media has not reached [MediaConnectionState.CONNECTED] within the
     * allowed negotiation window. Tears the session down like [stop], but
     * ends in [MediaConnectionState.FAILED] so the call is presented as a
     * real failure instead of leaving the UI reading an indefinite
     * Connecting state.
     */
    fun failConnectTimeout()
}
