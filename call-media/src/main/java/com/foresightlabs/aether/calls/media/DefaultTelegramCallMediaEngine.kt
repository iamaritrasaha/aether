package com.foresightlabs.aether.calls.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface TelegramCallMediaEngine {
    val isMediaTransportAvailable: Boolean
    val state: StateFlow<MediaConnectionState>
    val audioRoute: StateFlow<AudioRoute>
    val isMuted: StateFlow<Boolean>
    val signalBars: StateFlow<Int>
    val audioLevel: StateFlow<Float>

    suspend fun start(config: CallMediaConfig)
    fun setMicrophoneMuted(muted: Boolean)
    fun setAudioOutput(route: AudioRoute)
    fun stop()
}

class DefaultTelegramCallMediaEngine(
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

    private val _signalBars = MutableStateFlow(0)
    override val signalBars: StateFlow<Int> = _signalBars.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    override val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val nativeEngine = NativeTelegramCallMediaEngine()

    override val isMediaTransportAvailable: Boolean
        get() = nativeEngine.isMediaTransportAvailable

    private var focusRequest: AudioFocusRequest? = null

    init {
        nativeEngine.init(object : NativeCallEngineCallback {
            override fun onConnectionStateChanged(stateOrdinal: Int) {
                val newState = MediaConnectionState.entries.getOrElse(stateOrdinal) { MediaConnectionState.FAILED }
                _state.value = newState
            }

            override fun onSignalBarsChanged(bars: Int) {
                _signalBars.value = bars
            }

            override fun onAudioLevelsChanged(localLevel: Float, remoteLevel: Float) {
                _audioLevel.value = localLevel
            }

            override fun onError(error: String) {
                Log.e("CallMediaEngine", "Native engine error: $error")
                if (_state.value != MediaConnectionState.UNAVAILABLE) {
                    _state.value = MediaConnectionState.FAILED
                }
            }
        })
    }

    override suspend fun start(config: CallMediaConfig) {
        requestAudioFocus()
        setupAudioHardware()

        _state.value = MediaConnectionState.INITIALIZING
        nativeEngine.startCall(config)
    }

    override fun setMicrophoneMuted(muted: Boolean) {
        _isMuted.value = muted
        nativeEngine.setMuted(muted)
        try {
            audioManager.isMicrophoneMute = muted
        } catch (_: Throwable) {}
    }

    override fun setAudioOutput(route: AudioRoute) {
        _audioRoute.value = route
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val devices = audioManager.availableCommunicationDevices
                val targetType = when (route) {
                    AudioRoute.SPEAKER -> AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    AudioRoute.EARPIECE -> AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                    AudioRoute.BLUETOOTH -> AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                    AudioRoute.WIRED_HEADSET -> AudioDeviceInfo.TYPE_WIRED_HEADSET
                }
                val device = devices.find { it.type == targetType }
                if (device != null) {
                    audioManager.setCommunicationDevice(device)
                } else if (route == AudioRoute.SPEAKER) {
                    @Suppress("DEPRECATION")
                    audioManager.isSpeakerphoneOn = true
                }
            } else {
                @Suppress("DEPRECATION")
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
            }
        } catch (_: Throwable) {}
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
        } catch (_: Throwable) {}
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (_: Throwable) {}
    }

    private fun setupAudioHardware() {
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            setAudioOutput(_audioRoute.value)
            audioManager.isMicrophoneMute = _isMuted.value
        } catch (_: Throwable) {}
    }

    private fun resetAudioHardware() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isMicrophoneMute = false
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
            @Suppress("DEPRECATION")
            if (audioManager.isBluetoothScoOn) {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
            }
        } catch (_: Throwable) {}
    }
}
