package com.foresightlabs.aether.domain.calls

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

    suspend fun start(
        call: TdApi.Call,
        ready: TdApi.CallStateReady
    )

    fun setMicrophoneMuted(muted: Boolean)
    fun setAudioOutput(route: AudioRoute)
    fun stop()
}
