package com.foresightlabs.aether.calls.media

import io.github.pytgcalls.NTgCalls
import io.github.pytgcalls.VideoRotation
import io.github.pytgcalls.media.AudioDescription
import io.github.pytgcalls.media.DeviceInfo
import io.github.pytgcalls.media.MediaDescription
import io.github.pytgcalls.media.MediaDevices
import io.github.pytgcalls.media.MediaSource
import io.github.pytgcalls.media.StreamDevice
import io.github.pytgcalls.media.StreamMode
import io.github.pytgcalls.media.VideoDescription
import io.github.pytgcalls.p2p.RTCServer
import io.github.pytgcalls.ConnectionState as NtgConnectionState
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager

/**
 * Kotlin adapter between Aether's [CallMediaConfig] / [NativeCallEngineCallback]
 * contract and `io.github.pytgcalls.NTgCalls` -- the real Telegram-compatible
 * call transport (see docs/architecture/calling-native-stack.md).
 *
 * The underlying native engine is process-wide (one C++ object multiplexes
 * every call by id internally), so its instance, its registered callbacks and
 * the identity of the one call it is currently carrying all live in the
 * companion object rather than per-instance -- matching Aether's own "exactly
 * one canonical active-call coordinator" rule (see
 * docs/architecture/messaging-calls.md) instead of fighting it.
 */
class NativeTelegramCallMediaEngine {

    val isMediaTransportAvailable: Boolean
        get() = ensureNativeLoaded()

    fun init(callback: NativeCallEngineCallback) {
        Shared.callback = callback
        Shared.ensureListenersRegistered()
    }

    fun setContext(context: Context) = Shared.setContext(context)

    fun startCall(config: CallMediaConfig) = Shared.startCall(config)
    fun setCameraEnabled(enabled: Boolean) = Shared.setCameraEnabled(enabled)
    fun switchCamera() = Shared.switchCamera()
    fun submitSignalingData(callId: Long, data: ByteArray) = Shared.submitSignalingData(callId, data)
    fun setMuted(muted: Boolean) = Shared.setMuted(muted)

    /**
     * Audio output routing (earpiece/speaker/Bluetooth) is an Android
     * `AudioManager` concern, not something the media engine's own API
     * controls -- see [DefaultTelegramCallMediaEngine], which already does
     * this correctly. Kept as a method here only so callers do not need to
     * know that split.
     */
    fun setAudioRoute(route: AudioRoute) {}

    fun stopCall() = Shared.stopCall()

    companion object {

        private fun ensureNativeLoaded(): Boolean = Shared.ensureNativeLoaded()

        /** Queried once by the signalling layer to build TDLib's `CallProtocol`
         * from what the linked engine actually supports, instead of guessing. */
        fun supportedProtocol(): CallProtocolInfo? = Shared.supportedProtocol()
    }

    /** Process-wide native engine state. See the class doc above for why this is shared. */
    private object Shared {
        @Volatile private var loadAttempted = false
        @Volatile private var loaded = false
        @Volatile private var listenersRegistered = false
        @Volatile private var engineInstance: NTgCalls? = null

        /** Session counter, so a late callback can be attributed to its session. */
        @Volatile private var generationCounter: Long = 0L

        @Volatile var callback: NativeCallEngineCallback? = null
        @Volatile private var activeCallId: Long? = null
        @Volatile private var activeGeneration: Long = 0L
        @Volatile private var activeIsVideo: Boolean = false
        @Volatile private var activeCameraIsFront: Boolean = true

        /**
         * Whether the frame path may run at all for the current session.
         *
         * False for every voice call, and switched off permanently for a session
         * whose frames repeatedly fail validation -- the pixel-format contract is
         * an assumption (see [I420Converter]), so it is proven at runtime per
         * session rather than trusted. A session that cannot prove it degrades to
         * audio instead of rendering garbage or reading out of bounds.
         */
        @Volatile private var rendererEnabled: Boolean = false
        @Volatile private var rendererAttached: Boolean = false
        @Volatile private var malformedFrameStreak: Int = 0
        @Volatile private var lastFrameAtMillis: Long = 0L

        /** Set by the Android adapter before a video session is started. */
        @Volatile private var cameraManager: CameraManager? = null

        /** Consecutive unreadable frames after which the renderer gives up for this session. */
        private const val MALFORMED_FRAME_LIMIT = 30

        /** Minimum spacing between converted frames, bounding conversion cost. */
        private const val MIN_FRAME_INTERVAL_MILLIS = 28L

        @Synchronized
        fun ensureNativeLoaded(): Boolean {
            if (loadAttempted) return loaded
            loadAttempted = true
            loaded = try {
                // Touching a static native method forces NTgCalls's static
                // initializer (System.loadLibrary("ntgcalls")) to run.
                NTgCalls.ping()
                true
            } catch (_: Throwable) {
                false
            }
            return loaded
        }

        fun supportedProtocol(): CallProtocolInfo? {
            if (!ensureNativeLoaded()) return null
            return try {
                val protocol = NTgCalls.getProtocol()
                CallProtocolInfo(
                    minLayer = protocol.min_layer,
                    maxLayer = protocol.max_layer,
                    udpP2p = protocol.udp_p2p,
                    udpReflector = protocol.udp_reflector,
                    libraryVersions = protocol.library_versions.toList()
                )
            } catch (_: Throwable) {
                null
            }
        }

        @Synchronized
        private fun engine(): NTgCalls? {
            engineInstance?.let { return it }
            if (!ensureNativeLoaded()) return null
            return try {
                NTgCalls().also {
                    engineInstance = it
                    registerListeners(it)
                }
            } catch (t: Throwable) {
                CallDiagnostics.failure(generationCounter, CallStage.MEDIA_SESSION_CREATED, t)
                null
            }
        }

        fun ensureListenersRegistered() {
            engine()
        }

        fun setContext(context: Context) {
            cameraManager = context.applicationContext
                .getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        }

        private fun registerListeners(ntg: NTgCalls) {
            if (listenersRegistered) return
            listenersRegistered = true
            try {
                ntg.onConnectionChange { callId, info ->
                    // Native thread. A callback for a call that is no longer the
                    // active one belongs to a session that has already ended and
                    // must never revive it.
                    if (callId != activeCallId) return@onConnectionChange
                    val mapped = when (info.state) {
                        NtgConnectionState.CONNECTING -> MediaConnectionState.CONNECTING
                        NtgConnectionState.CONNECTED -> MediaConnectionState.CONNECTED
                        NtgConnectionState.FAILED, NtgConnectionState.TIMEOUT -> MediaConnectionState.FAILED
                        NtgConnectionState.CLOSED -> MediaConnectionState.STOPPED
                        else -> MediaConnectionState.FAILED
                    }
                    if (mapped == MediaConnectionState.CONNECTED) {
                        CallDiagnostics.stage(activeGeneration, CallStage.MEDIA_CONNECTED, "kind=${info.kind?.name ?: "unknown"}")
                    }
                    callback?.onConnectionStateChanged(mapped.ordinal)
                }
                ntg.onSignalingData { callId, data ->
                    if (callId == activeCallId) callback?.onOutgoingSignalingData(data)
                }
                ntg.onFrames { callId, mode, device, frames ->
                    // Runs on a native engine thread. Everything it touches is
                    // either @Volatile or a thread-safe flow, and every frame it
                    // publishes is freshly allocated and never mutated again, so
                    // the main thread can read one without synchronisation.
                    try {
                        handleFrames(callId, mode, device, frames)
                    } catch (t: Throwable) {
                        // A decode-path failure must cost the video, never the call.
                        disableRenderer("frame_callback_error")
                        CallDiagnostics.failure(activeGeneration, CallStage.RENDERER_DETACHED, t)
                    }
                }
            } catch (t: Throwable) {
                CallDiagnostics.failure(generationCounter, CallStage.MEDIA_SESSION_CREATED, t)
            }
        }

        private fun handleFrames(
            callId: Long,
            mode: StreamMode,
            device: StreamDevice,
            frames: List<io.github.pytgcalls.models.Frame>
        ) {
            // A voice call never enters this path: rendererEnabled is false for
            // the whole session, so no camera frame is ever converted or held.
            if (!rendererEnabled) return
            if (callId != activeCallId) return
            if (device != StreamDevice.CAMERA) return
            if (frames.isEmpty()) return

            val now = System.currentTimeMillis()
            if (now - lastFrameAtMillis < MIN_FRAME_INTERVAL_MILLIS) return
            lastFrameAtMillis = now

            val origin = if (mode == StreamMode.CAPTURE) VideoFrameOrigin.LOCAL else VideoFrameOrigin.REMOTE

            // Only the newest frame is worth converting: an older one is already
            // stale by the time it would reach the screen.
            val frame = frames.last()
            val fd = frame.frame_data
            val data = frame.data
            if (data == null || fd == null) {
                noteMalformedFrame("null_frame")
                return
            }

            val image = I420Converter.convert(data, fd.width, fd.height)
            if (image == null) {
                noteMalformedFrame("geometry=${fd.width}x${fd.height} bytes=${data.size}")
                return
            }

            malformedFrameStreak = 0
            if (!rendererAttached) {
                rendererAttached = true
                CallDiagnostics.stage(activeGeneration, CallStage.RENDERER_ATTACHED, "origin=${origin.name}")
            }

            callback?.onVideoFrame(
                DecodedVideoFrame(
                    origin = origin,
                    width = image.width,
                    height = image.height,
                    rotationDegrees = rotationDegrees(fd.rotation),
                    pixels = image.pixels
                )
            )
        }

        /**
         * A frame that does not match the assumed format is dropped. Enough of
         * them in a row means the assumption is wrong on this device, so the
         * renderer stops for the session rather than dropping frames forever.
         */
        private fun noteMalformedFrame(detail: String) {
            malformedFrameStreak += 1
            if (malformedFrameStreak == 1) {
                CallDiagnostics.stage(activeGeneration, CallStage.VIDEO_INITIALIZING, "malformed_frame $detail")
            }
            if (malformedFrameStreak >= MALFORMED_FRAME_LIMIT) {
                disableRenderer("malformed_frames=$malformedFrameStreak")
            }
        }

        private fun disableRenderer(reason: String) {
            if (!rendererEnabled) return
            rendererEnabled = false
            rendererAttached = false
            CallDiagnostics.stage(activeGeneration, CallStage.RENDERER_DETACHED, "reason=$reason")
        }

        private fun rotationDegrees(rotation: VideoRotation?): Int = when (rotation) {
            VideoRotation.VIDEO_ROTATION_90 -> 90
            VideoRotation.VIDEO_ROTATION_180 -> 180
            VideoRotation.VIDEO_ROTATION_270 -> 270
            else -> 0
        }

        fun startCall(config: CallMediaConfig) {
            val generation = ++generationCounter
            val ntg = engine()
            if (ntg == null) {
                callback?.onConnectionStateChanged(MediaConnectionState.UNAVAILABLE.ordinal)
                callback?.onError("Official Telegram call transport is not available on this device/build")
                return
            }

            activeCallId = config.callId
            activeGeneration = generation
            activeIsVideo = config.videoCaptureEnabled
            activeCameraIsFront = true
            rendererEnabled = config.videoCaptureEnabled
            rendererAttached = false
            malformedFrameStreak = 0
            lastFrameAtMillis = 0L
            callback?.onConnectionStateChanged(MediaConnectionState.INITIALIZING.ordinal)

            try {
                ntg.createP2pCall(config.callId)
                ntg.skipExchange(config.callId, config.encryptionKey, config.isOutgoing)
                CallDiagnostics.stage(generation, CallStage.MEDIA_SESSION_CREATED, "outgoing=${config.isOutgoing}")

                val rtcServers = config.servers.map { server ->
                    RTCServer(
                        server.id,
                        server.ipAddress,
                        server.ipv6Address,
                        server.port,
                        server.username,
                        server.password,
                        server.supportsTurn,
                        server.supportsStun,
                        server.isTcp,
                        server.peerTag
                    )
                }
                CallDiagnostics.stage(generation, CallStage.P2P_CONNECTING, "servers=${rtcServers.size} p2p=${config.allowP2p}")
                // ntgcalls expects capture sources to exist before transport
                // negotiation starts. Starting P2P first creates a race where
                // the CONNECTED callback can arrive while the native session
                // still has no audio source; that was the connect-time crash
                // boundary in the interrupted implementation.
                if (!applyStreamSources(ntg, config.callId, cameraEnabled = config.videoCaptureEnabled)) {
                    activeCallId = null
                    rendererEnabled = false
                    callback?.onConnectionStateChanged(MediaConnectionState.FAILED.ordinal)
                    return
                }
                ntg.connectP2p(
                    config.callId,
                    rtcServers,
                    config.protocol.libraryVersions,
                    config.allowP2p,
                    config.customParameters.ifBlank { null }
                )

            } catch (e: Throwable) {
                CallDiagnostics.failure(generation, CallStage.MEDIA_SESSION_CREATED, e)
                activeCallId = null
                rendererEnabled = false
                callback?.onConnectionStateChanged(MediaConnectionState.FAILED.ordinal)
                callback?.onError(e.message ?: "Call transport failed to start")
            }
        }

        /**
         * Configures capture sources. Audio is resolved first and independently:
         * a camera that cannot be enumerated or opened leaves the microphone
         * description untouched, so a video call degrades to a working voice call
         * rather than losing both.
         */
        private fun applyStreamSources(ntg: NTgCalls, callId: Long, cameraEnabled: Boolean): Boolean {
            val generation = activeGeneration
            val devices = try {
                NTgCalls.getMediaDevices()
            } catch (t: Throwable) {
                CallDiagnostics.failure(generation, CallStage.AUDIO_INITIALIZING, t)
                null
            }

            CallDiagnostics.stage(generation, CallStage.AUDIO_INITIALIZING, "devices=${devices?.microphone?.size ?: -1}")
            val microphone = try {
                devices?.microphone?.firstOrNull()?.let {
                    AudioDescription(MediaSource.DEVICE, 48000, 1, it.metadata, false)
                }
            } catch (t: Throwable) {
                CallDiagnostics.failure(generation, CallStage.AUDIO_INITIALIZING, t)
                null
            }

            // A native session without a microphone is not a voice call. Fail
            // this session through the normal FAILED state instead of passing a
            // null audio source into JNI and risking an engine-side crash.
            if (microphone == null) {
                CallDiagnostics.failure(
                    generation,
                    CallStage.AUDIO_INITIALIZING,
                    IllegalStateException("No microphone media device is available")
                )
                return false
            }

            val camera = if (!cameraEnabled) {
                null
            } else {
                CallDiagnostics.stage(generation, CallStage.VIDEO_INITIALIZING, "front=$activeCameraIsFront")
                try {
                    devices?.let { selectCamera(it, front = activeCameraIsFront) }?.let {
                        VideoDescription(MediaSource.DEVICE, 1280, 720, 30, it.metadata, false)
                    }
                } catch (t: Throwable) {
                    // Video failing to initialise must not take audio with it.
                    disableRenderer("camera_init_failed")
                    CallDiagnostics.failure(generation, CallStage.VIDEO_INITIALIZING, t)
                    null
                }
            }

            try {
                ntg.setStreamSources(
                    callId,
                    StreamMode.CAPTURE,
                    MediaDescription(microphone, null, camera, null)
                )
            } catch (t: Throwable) {
                CallDiagnostics.failure(generation, CallStage.AUDIO_INITIALIZING, t)
                if (camera != null) {
                    // Retry without the camera: prove whether video was the
                    // reason the whole capture description was rejected.
                    disableRenderer("stream_sources_rejected_with_camera")
                    try {
                        ntg.setStreamSources(
                            callId,
                            StreamMode.CAPTURE,
                            MediaDescription(microphone, null, null, null)
                        )
                    } catch (retry: Throwable) {
                        CallDiagnostics.failure(generation, CallStage.AUDIO_INITIALIZING, retry)
                    }
                }
            }
            return true
        }

        /**
         * Resolve facing from Android's authoritative camera characteristics.
         * The ntgcalls device metadata is used as the CameraManager id; if that
         * mapping is not valid, no camera is selected and the call remains audio.
         */
        private fun selectCamera(devices: MediaDevices, front: Boolean): DeviceInfo? {
            val manager = cameraManager ?: return null
            val candidates = devices.camera.mapNotNull { device ->
                val id = device.metadata
                val facing = runCatching {
                    manager.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.LENS_FACING)
                }.getOrNull()
                val wanted = if (front) CameraCharacteristics.LENS_FACING_FRONT
                else CameraCharacteristics.LENS_FACING_BACK
                if (facing == wanted) device else null
            }
            // Camera IDs are stable platform identifiers; sorting makes the
            // selection deterministic without inferring facing from list order.
            return candidates.sortedBy { it.metadata }.firstOrNull()
        }

        fun setCameraEnabled(enabled: Boolean) {
            val ntg = engineInstance ?: return
            val callId = activeCallId ?: return
            // A session that never had camera permission, or whose renderer gave
            // up, cannot be talked into opening the camera from the UI.
            if (enabled && !rendererEnabled) return
            activeIsVideo = enabled
            applyStreamSources(ntg, callId, cameraEnabled = enabled)
        }

        fun switchCamera() {
            val ntg = engineInstance ?: return
            val callId = activeCallId ?: return
            if (!activeIsVideo || !rendererEnabled) return
            activeCameraIsFront = !activeCameraIsFront
            applyStreamSources(ntg, callId, cameraEnabled = true)
        }

        fun submitSignalingData(callId: Long, data: ByteArray) {
            val ntg = engineInstance ?: return
            if (callId != activeCallId) return
            try {
                ntg.sendSignalingData(callId, data)
            } catch (t: Throwable) {
                CallDiagnostics.failure(activeGeneration, CallStage.P2P_CONNECTING, t)
            }
        }

        fun setMuted(muted: Boolean) {
            val ntg = engineInstance ?: return
            val callId = activeCallId ?: return
            try {
                if (muted) ntg.mute(callId) else ntg.unmute(callId)
            } catch (t: Throwable) {
                CallDiagnostics.failure(activeGeneration, CallStage.AUDIO_INITIALIZING, t)
            }
        }

        fun stopCall() {
            val ntg = engineInstance
            val callId = activeCallId
            val generation = activeGeneration
            // Cleared first: from here on, any callback still in flight from the
            // native side fails its activeCallId check and is discarded.
            activeCallId = null
            rendererEnabled = false
            rendererAttached = false
            CallDiagnostics.stage(generation, CallStage.TEARDOWN, "had_session=${callId != null}")
            if (ntg != null && callId != null) {
                try {
                    ntg.stop(callId)
                } catch (t: Throwable) {
                    CallDiagnostics.failure(generation, CallStage.TEARDOWN, t)
                }
            }
            callback?.onConnectionStateChanged(MediaConnectionState.STOPPED.ordinal)
        }
    }
}
