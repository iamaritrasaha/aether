package com.foresightlabs.aether.data.calls.media

import android.content.Context
import com.foresightlabs.aether.calls.media.AudioRoute as NativeAudioRoute
import com.foresightlabs.aether.calls.media.DefaultTelegramCallMediaEngine
import com.foresightlabs.aether.calls.media.MediaConnectionState as NativeMediaConnectionState
import com.foresightlabs.aether.data.calls.TgCallsAdapter
import com.foresightlabs.aether.domain.calls.AudioRoute
import com.foresightlabs.aether.domain.calls.MediaConnectionState
import com.foresightlabs.aether.domain.calls.TelegramCallMediaEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

class TgCallsMediaEngine(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) : TelegramCallMediaEngine {

    private val delegate = DefaultTelegramCallMediaEngine(context)

    private val _state = MutableStateFlow(MediaConnectionState.IDLE)
    override val state: StateFlow<MediaConnectionState> = _state.asStateFlow()

    private val _audioRoute = MutableStateFlow(AudioRoute.EARPIECE)
    override val audioRoute: StateFlow<AudioRoute> = _audioRoute.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    override val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    override val isMediaTransportAvailable: Boolean
        get() = delegate.isMediaTransportAvailable

    init {
        scope.launch {
            delegate.state.collect { nativeState ->
                _state.value = mapNativeState(nativeState)
            }
        }
        scope.launch {
            delegate.audioRoute.collect { nativeRoute ->
                _audioRoute.value = mapNativeRoute(nativeRoute)
            }
        }
        scope.launch {
            delegate.isMuted.collect { muted ->
                _isMuted.value = muted
            }
        }
    }

    private fun mapNativeState(nativeState: NativeMediaConnectionState): MediaConnectionState {
        return when (nativeState) {
            NativeMediaConnectionState.IDLE -> MediaConnectionState.IDLE
            NativeMediaConnectionState.INITIALIZING -> MediaConnectionState.INITIALIZING
            NativeMediaConnectionState.CONNECTING -> MediaConnectionState.CONNECTING
            NativeMediaConnectionState.CONNECTED -> MediaConnectionState.CONNECTED
            NativeMediaConnectionState.RECONNECTING -> MediaConnectionState.RECONNECTING
            NativeMediaConnectionState.FAILED -> MediaConnectionState.FAILED
            NativeMediaConnectionState.UNAVAILABLE -> MediaConnectionState.UNAVAILABLE
            NativeMediaConnectionState.STOPPED -> MediaConnectionState.STOPPED
        }
    }

    private fun mapDomainRoute(route: AudioRoute): NativeAudioRoute {
        return when (route) {
            AudioRoute.EARPIECE -> NativeAudioRoute.EARPIECE
            AudioRoute.SPEAKER -> NativeAudioRoute.SPEAKER
            AudioRoute.BLUETOOTH -> NativeAudioRoute.BLUETOOTH
            AudioRoute.WIRED_HEADSET -> NativeAudioRoute.WIRED_HEADSET
        }
    }

    private fun mapNativeRoute(nativeRoute: NativeAudioRoute): AudioRoute {
        return when (nativeRoute) {
            NativeAudioRoute.EARPIECE -> AudioRoute.EARPIECE
            NativeAudioRoute.SPEAKER -> AudioRoute.SPEAKER
            NativeAudioRoute.BLUETOOTH -> AudioRoute.BLUETOOTH
            NativeAudioRoute.WIRED_HEADSET -> AudioRoute.WIRED_HEADSET
        }
    }

    override suspend fun start(call: TdApi.Call, ready: TdApi.CallStateReady) {
        val config = TgCallsAdapter.buildMediaConfig(call, ready)
        delegate.start(config)
    }

    override fun setMicrophoneMuted(muted: Boolean) {
        delegate.setMicrophoneMuted(muted)
    }

    override fun setAudioOutput(route: AudioRoute) {
        delegate.setAudioOutput(mapDomainRoute(route))
    }

    override fun stop() {
        delegate.stop()
    }
}
