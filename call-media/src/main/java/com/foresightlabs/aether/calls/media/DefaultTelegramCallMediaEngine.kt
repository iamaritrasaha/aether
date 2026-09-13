package com.foresightlabs.aether.calls.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

interface TelegramCallMediaEngine {
    val isMediaTransportAvailable: Boolean
    val state: StateFlow<MediaConnectionState>
    val audioRoute: StateFlow<AudioRoute>
    val isMuted: StateFlow<Boolean>
    val signalBars: StateFlow<Int>
    val audioLevel: StateFlow<Float>

    /** The engine's ACTUAL local-camera state (not the last user request). */
    val isCameraActive: StateFlow<Boolean>

    /** Facing of the camera the engine actually selected (front by default). */
    val isFrontCamera: StateFlow<Boolean>

    /** Local/remote decoded video frames, for a video call's renderer to draw. */
    val videoFrames: SharedFlow<DecodedVideoFrame>

    /** Signalling bytes this engine needs delivered through TDLib's `SendCallSignalingData`. */
    val outgoingSignalingData: SharedFlow<ByteArray>

    suspend fun start(config: CallMediaConfig)
    fun setMicrophoneMuted(muted: Boolean)
    fun setAudioOutput(route: AudioRoute)
    fun setCameraEnabled(enabled: Boolean)
    fun switchCamera()

    /** TDLib's `UpdateNewCallSignalingData`, for this engine to consume. */
    fun submitIncomingSignalingData(callId: Long, data: ByteArray)
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

    private val _isCameraActive = MutableStateFlow(false)
    override val isCameraActive: StateFlow<Boolean> = _isCameraActive.asStateFlow()

    private val _isFrontCamera = MutableStateFlow(true)
    override val isFrontCamera: StateFlow<Boolean> = _isFrontCamera.asStateFlow()

    private val _signalBars = MutableStateFlow(0)
    override val signalBars: StateFlow<Int> = _signalBars.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    override val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    // extraBufferCapacity so a frame delivered before a Compose collector has
    // subscribed is not silently dropped -- the first rendered frame would
    // otherwise sometimes be lost depending on collection timing.
    private val _videoFrames = MutableSharedFlow<DecodedVideoFrame>(extraBufferCapacity = 2)
    override val videoFrames: SharedFlow<DecodedVideoFrame> = _videoFrames.asSharedFlow()

    private val _outgoingSignalingData = MutableSharedFlow<ByteArray>(extraBufferCapacity = 8)
    override val outgoingSignalingData: SharedFlow<ByteArray> = _outgoingSignalingData.asSharedFlow()

    private val nativeEngine = NativeTelegramCallMediaEngine()

    override val isMediaTransportAvailable: Boolean
        get() = nativeEngine.isMediaTransportAvailable

    private var focusRequest: AudioFocusRequest? = null

    /**
     * Keeps [_audioRoute] reflecting Android's ACTUAL communication route
     * rather than the last user request: a Bluetooth headset connecting or a
     * wired headset being unplugged mid-call changes the route behind our
     * back, and UI state that only mirrors user intent would then lie.
     */
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            syncRouteFromSystem("devices_added")
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            syncRouteFromSystem("devices_removed")
        }
    }

    init {
        try {
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
        } catch (_: Throwable) {}
        nativeEngine.setContext(appContext)
        syncRouteFromSystem("init")
    }

    /**
     * Reads Android's actual communication route and, if it differs from the
     * UI-visible state, adopts it. Evidence, not intent: after this runs,
     * [_audioRoute] is what Android really selected (or auto-selected on a
     * device change mid-call), and the change is diagnosable.
     */
    private fun syncRouteFromSystem(reason: String) {
        val actual = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mapDeviceTypeToRoute(audioManager.communicationDevice?.type ?: return)
            } else {
                when {
                    audioManager.isWiredHeadsetOn -> AudioRoute.WIRED_HEADSET
                    audioManager.isBluetoothScoOn -> AudioRoute.BLUETOOTH
                    audioManager.isSpeakerphoneOn -> AudioRoute.SPEAKER
                    else -> AudioRoute.EARPIECE
                }
            }
        } catch (_: Throwable) {
            return
        }
        if (actual != null && actual != _audioRoute.value) {
            _audioRoute.value = actual
            CallDiagnostics.stage(0L, CallStage.AUDIO_INITIALIZING, "route_changed reason=$reason route=${actual.name}")
        }
    }

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

            override fun onOutgoingSignalingData(data: ByteArray) {
                _outgoingSignalingData.tryEmit(data)
            }

            override fun onVideoFrame(frame: DecodedVideoFrame) {
                _videoFrames.tryEmit(frame)
            }

            override fun onCameraStateChanged(active: Boolean, isFront: Boolean) {
                _isCameraActive.value = active
                _isFrontCamera.value = isFront
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

    override fun setCameraEnabled(enabled: Boolean) {
        nativeEngine.setCameraEnabled(enabled)
    }

    override fun switchCamera() {
        nativeEngine.switchCamera()
    }

    override fun submitIncomingSignalingData(callId: Long, data: ByteArray) {
        nativeEngine.submitSignalingData(callId, data)
    }

    override fun setAudioOutput(route: AudioRoute) {
        _audioRoute.value = route
        nativeEngine.setAudioRoute(route)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val devices = audioManager.availableCommunicationDevices
                val targetTypes = when (route) {
                    AudioRoute.SPEAKER -> intArrayOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BLE_SPEAKER)
                    AudioRoute.EARPIECE -> intArrayOf(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)
                    AudioRoute.BLUETOOTH -> intArrayOf(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET)
                    AudioRoute.WIRED_HEADSET -> intArrayOf(AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET)
                }
                val device = devices.firstOrNull { d -> targetTypes.any { it == d.type } }
                val applied = device != null && audioManager.setCommunicationDevice(device)
                CallDiagnostics.stage(0L, CallStage.AUDIO_INITIALIZING, "route_request requested=${route.name} applied=$applied deviceType=${device?.type ?: -1}")
                if (!applied && route == AudioRoute.SPEAKER) {
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
        // A ended call leaves no state behind: a stale muted flag here would
        // start the next call muted-at-hardware-level while its fresh UI
        // showed unmuted (the engine object is process-wide).
        _isMuted.value = false
        _isCameraActive.value = false
        _state.value = MediaConnectionState.STOPPED
    }

    /**
     * Called by the call orchestration layer's connection watchdog when
     * native media has not reached [MediaConnectionState.CONNECTED] within
     * the allowed negotiation window (see `DefaultCallsRepository`).
     *
     * Tears down the native session exactly like [stop], but the engine is
     * left in [MediaConnectionState.FAILED], not [MediaConnectionState.STOPPED]:
     * `CallStatePresenter` treats FAILED as an unconditional failure
     * regardless of signalling state, which is what turns a stuck Connecting
     * screen into a real, user-visible failure instead of leaving the UI
     * inferring CONNECTING from a state it doesn't otherwise recognise.
     */
    fun failConnectTimeout() {
        nativeEngine.stopCall()
        abandonAudioFocus()
        resetAudioHardware()
        _isMuted.value = false
        _isCameraActive.value = false
        _state.value = MediaConnectionState.FAILED
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
            // Routing evidence for the physical-call diagnostics: which device
            // Android actually selected for communication audio. Type codes
            // only -- device labels/addresses never enter the log.
            val deviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.communicationDevice?.type ?: -1
            } else {
                if (audioManager.isSpeakerphoneOn) AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                else AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            }
            CallDiagnostics.stage(0L, CallStage.AUDIO_INITIALIZING, "audio_hw mode=${audioManager.mode} commDeviceType=$deviceType micMute=${audioManager.isMicrophoneMute}")
        } catch (t: Throwable) {
            CallDiagnostics.failure(0L, CallStage.AUDIO_INITIALIZING, t)
        }
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

    /** Device-type -> route mapping, pure so it can be unit-tested. */
    companion object {
        fun mapDeviceTypeToRoute(type: Int?): AudioRoute? = when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> AudioRoute.EARPIECE
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE,
            AudioDeviceInfo.TYPE_BLE_SPEAKER -> AudioRoute.SPEAKER
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET -> AudioRoute.BLUETOOTH
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET -> AudioRoute.WIRED_HEADSET
            else -> null
        }
    }
}
