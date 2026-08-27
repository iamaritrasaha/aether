package com.foresightlabs.aether.data.calls.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.foresightlabs.aether.BuildConfig
import com.foresightlabs.aether.data.calls.TgCallsAdapter
import com.foresightlabs.aether.domain.calls.AudioRoute
import com.foresightlabs.aether.domain.calls.MediaConnectionState
import com.foresightlabs.aether.domain.calls.TelegramCallMediaEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.TdApi

class TgCallsMediaEngine(
    context: Context
) : TelegramCallMediaEngine {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _state = MutableStateFlow(MediaConnectionState.IDLE)
    override val state: StateFlow<MediaConnectionState> = _state.asStateFlow()

    private val _audioRoute = MutableStateFlow(AudioRoute.EARPIECE)
    override val audioRoute: StateFlow<AudioRoute> = _audioRoute.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    override val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val nativeEngine = NativeTelegramCallEngine()

    override val isMediaTransportAvailable: Boolean
        get() = nativeEngine.isMediaTransportAvailable
    private var focusRequest: AudioFocusRequest? = null

    init {
        nativeEngine.init(object : NativeCallEngineCallback {
            override fun onConnectionStateChanged(state: MediaConnectionState) {
                _state.value = state
            }

            override fun onSignalBarsChanged(bars: Int) {
                if (BuildConfig.DEBUG) {
                    Log.d("TgCallsMediaEngine", "Signal bars: $bars")
                }
            }

            override fun onError(error: String) {
                if (BuildConfig.DEBUG) {
                    Log.e("TgCallsMediaEngine", "TgCalls media error: $error")
                }
                _state.value = MediaConnectionState.FAILED
            }
        })
    }

    override suspend fun start(call: TdApi.Call, ready: TdApi.CallStateReady) {
        requestAudioFocus()
        setupAudioHardware()

        _state.value = MediaConnectionState.INITIALIZING
        val config = TgCallsAdapter.buildConfig(call, ready)

        if (BuildConfig.DEBUG) {
            Log.d("TgCallsMediaEngine", "Starting TgCalls Media Engine for call ${call.id}")
        }

        nativeEngine.startCall(config)
    }

    override fun setMicrophoneMuted(muted: Boolean) {
        _isMuted.value = muted
        nativeEngine.setMuted(muted)
        try {
            audioManager.isMicrophoneMute = muted
        } catch (_: Exception) {}
    }

    override fun setAudioOutput(route: AudioRoute) {
        _audioRoute.value = route
        try {
            when (route) {
                AudioRoute.SPEAKER -> {
                    audioManager.isSpeakerphoneOn = true
                }
                AudioRoute.EARPIECE -> {
                    audioManager.isSpeakerphoneOn = false
                }
                AudioRoute.BLUETOOTH -> {
                    audioManager.isSpeakerphoneOn = false
                    if (!audioManager.isBluetoothScoOn) {
                        audioManager.startBluetoothSco()
                        audioManager.isBluetoothScoOn = true
                    }
                }
                AudioRoute.WIRED_HEADSET -> {
                    audioManager.isSpeakerphoneOn = false
                }
            }
        } catch (_: Exception) {}
    }

    override fun stop() {
        nativeEngine.stopCall()
        abandonAudioFocus()
        resetAudioHardware()
        _state.value = MediaConnectionState.STOPPED
    }

    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(attributes)
                    .setOnAudioFocusChangeListener { focusChange ->
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                            stop()
                        }
                    }
                    .build()

                focusRequest = request
                audioManager.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    { focusChange -> if (focusChange == AudioManager.AUDIOFOCUS_LOSS) stop() },
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                )
            }
        } catch (_: Exception) {}
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (_: Exception) {}
    }

    private fun setupAudioHardware() {
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = (_audioRoute.value == AudioRoute.SPEAKER)
            audioManager.isMicrophoneMute = _isMuted.value
        } catch (_: Exception) {}
    }

    private fun resetAudioHardware() {
        try {
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isMicrophoneMute = false
            audioManager.isSpeakerphoneOn = false
            if (audioManager.isBluetoothScoOn) {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
            }
        } catch (_: Exception) {}
    }
}
