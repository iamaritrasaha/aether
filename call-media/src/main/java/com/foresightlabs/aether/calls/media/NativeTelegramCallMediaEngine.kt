package com.foresightlabs.aether.calls.media

import android.util.Log

class NativeTelegramCallMediaEngine {

    val isMediaTransportAvailable: Boolean
        get() = isNativeLibLoaded && hasRealTelegramTransport()

    private var callback: NativeCallEngineCallback? = null
    @Volatile private var isEngineActive = false

    companion object {
        private const val TAG = "NativeCallMediaEngine"
        private var isNativeLibLoaded = false

        init {
            isNativeLibLoaded = try {
                System.loadLibrary("callmedia")
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    private fun hasRealTelegramTransport(): Boolean {
        if (!isNativeLibLoaded) return false
        return try {
            nativeHasRealTelegramTransport()
        } catch (_: Throwable) {
            false
        }
    }

    fun init(callback: NativeCallEngineCallback) {
        this.callback = callback
        if (isNativeLibLoaded) {
            try {
                nativeInit(callback)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to initialize native engine callback", e)
            }
        }
    }

    fun startCall(config: CallMediaConfig) {
        isEngineActive = true

        if (!isMediaTransportAvailable) {
            isEngineActive = false
            callback?.onConnectionStateChanged(MediaConnectionState.UNAVAILABLE.ordinal)
            callback?.onError("Official Telegram tgcalls media transport is not compiled into this build")
            return
        }

        callback?.onConnectionStateChanged(MediaConnectionState.INITIALIZING.ordinal)

        try {
            nativeStart(
                config.callId,
                config.isOutgoing,
                config.encryptionKey,
                config.allowP2p,
                config.servers.toTypedArray(),
                config.configJson,
                config.customParameters,
                config.protocol.minLayer,
                config.protocol.maxLayer,
                config.protocol.libraryVersions.toTypedArray()
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Native call start failed", e)
            isEngineActive = false
            callback?.onConnectionStateChanged(MediaConnectionState.FAILED.ordinal)
        }
    }

    fun setMuted(muted: Boolean) {
        if (isNativeLibLoaded) {
            try {
                nativeSetMuted(muted)
            } catch (_: Throwable) {}
        }
    }

    fun stopCall() {
        if (!isEngineActive) return
        isEngineActive = false
        if (isNativeLibLoaded) {
            try {
                nativeStop()
            } catch (_: Throwable) {}
        }
        callback?.onConnectionStateChanged(MediaConnectionState.STOPPED.ordinal)
    }

    private external fun nativeHasRealTelegramTransport(): Boolean
    private external fun nativeInit(callback: NativeCallEngineCallback)
    private external fun nativeStart(
        callId: Long,
        isOutgoing: Boolean,
        encryptionKey: ByteArray,
        allowP2p: Boolean,
        servers: Array<CallServerEndpoint>,
        configJson: String,
        customParams: String,
        minLayer: Int,
        maxLayer: Int,
        libraryVersions: Array<String>
    )
    private external fun nativeSetMuted(muted: Boolean)
    private external fun nativeStop()
}
