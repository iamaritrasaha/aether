package com.foresightlabs.aether.data.calls.media

import android.util.Log
import com.foresightlabs.aether.BuildConfig
import com.foresightlabs.aether.data.calls.TgCallsConfig
import com.foresightlabs.aether.domain.calls.MediaConnectionState

interface NativeCallEngineCallback {
    fun onConnectionStateChanged(state: MediaConnectionState)
    fun onSignalBarsChanged(bars: Int)
    fun onError(error: String)
}

/**
 * Kotlin side of the Telegram call media transport.
 *
 * The transport itself is a native library. When it is not present in the APK this
 * class reports [MediaConnectionState.UNAVAILABLE] and does nothing else — it never
 * synthesises a connected state, because a call that reports connected without a
 * media path is worse than a call that refuses to start.
 */
class NativeTelegramCallEngine {

    /** Whether this build actually ships a media transport. */
    val isMediaTransportAvailable: Boolean get() = isNativeLibLoaded

    private var callback: NativeCallEngineCallback? = null
    @Volatile private var isEngineActive = false
    @Volatile private var isNativeMuted = false

    companion object {
        private const val TAG = "NativeTgCallsEngine"
        private var isNativeLibLoaded = false

        init {
            // Nothing in class initialisation may throw: whether a media transport
            // exists is the one thing the rest of the call stack must be able to ask
            // about, in every environment this class can be loaded in.
            isNativeLibLoaded = try {
                System.loadLibrary("tgcalls")
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    fun init(callback: NativeCallEngineCallback) {
        this.callback = callback
    }

    fun startCall(config: TgCallsConfig) {
        isEngineActive = true
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "TDLib -> TgCalls Start: callId=${config.callId}, p2p=${config.allowP2p}, servers=${config.servers.size}")
        }

        if (!isNativeLibLoaded) {
            // Without a media transport there is no audio path, in either direction.
            // Reporting anything else here would put a running timer and a
            // "Connected" label on a call that carries silence.
            isEngineActive = false
            callback?.onConnectionStateChanged(MediaConnectionState.UNAVAILABLE)
            callback?.onError("No call media transport is available in this build")
            return
        }

        callback?.onConnectionStateChanged(MediaConnectionState.CONNECTING)

        try {
            nativeStart(config.callId, config.encryptionKey, config.allowP2p)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Native tgcalls start error", e)
            }
            isEngineActive = false
            callback?.onConnectionStateChanged(MediaConnectionState.FAILED)
            return
        }

        // CONNECTED is reported by the native transport when audio is actually
        // flowing, never here. See nativeOnConnectionStateChanged.
    }

    fun setMuted(muted: Boolean) {
        isNativeMuted = muted
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "TgCalls setMuted: $muted")
        }
        if (isNativeLibLoaded) {
            try {
                nativeSetMuted(muted)
            } catch (_: Exception) {}
        }
    }

    fun stopCall() {
        if (!isEngineActive) return
        isEngineActive = false
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "TgCalls Stop")
        }
        if (isNativeLibLoaded) {
            try {
                nativeStop()
            } catch (_: Exception) {}
        }
        callback?.onConnectionStateChanged(MediaConnectionState.STOPPED)
    }

    /** Invoked from the native transport when the real connection state changes. */
    @Suppress("unused")
    private fun nativeOnConnectionStateChanged(ordinal: Int) {
        val state = MediaConnectionState.entries.getOrNull(ordinal) ?: return
        callback?.onConnectionStateChanged(state)
    }

    /** Invoked from the native transport with the measured signal quality. */
    @Suppress("unused")
    private fun nativeOnSignalBarsChanged(bars: Int) {
        callback?.onSignalBarsChanged(bars)
    }

    private external fun nativeStart(callId: Int, encryptionKey: ByteArray, allowP2p: Boolean)
    private external fun nativeSetMuted(muted: Boolean)
    private external fun nativeStop()
}
