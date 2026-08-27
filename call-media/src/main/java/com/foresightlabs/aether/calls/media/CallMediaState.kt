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

interface NativeCallEngineCallback {
    fun onConnectionStateChanged(stateOrdinal: Int)
    fun onSignalBarsChanged(bars: Int)
    fun onAudioLevelsChanged(localLevel: Float, remoteLevel: Float)
    fun onError(error: String)
}
