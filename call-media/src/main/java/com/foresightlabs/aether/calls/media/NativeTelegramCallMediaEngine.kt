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
import androidx.annotation.VisibleForTesting
import org.webrtc.PeerConnectionFactory

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
        get() = Shared.isTransportReady()

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

        @VisibleForTesting
        internal fun applyStreamSourcesForTesting(
            callId: Long,
            cameraEnabled: Boolean,
            deviceProvider: () -> MediaDevices?,
            streamSourceSetter: (Long, StreamMode, MediaDescription) -> Unit,
            cameraSelector: ((MediaDevices, Boolean) -> DeviceInfo?)? = null
        ): Boolean = Shared.applyStreamSourcesInternal(callId, cameraEnabled, deviceProvider, streamSourceSetter, cameraSelector)

        @VisibleForTesting
        internal fun startCallForTesting(
            config: CallMediaConfig,
            createSession: (Long) -> Unit = {},
            skipExchange: (Long, ByteArray, Boolean) -> Unit = { _, _, _ -> },
            applySources: (Long, Boolean) -> Boolean = { _, _ -> true },
            connectP2p: (Long, List<RTCServer>, List<String>, Boolean, String?) -> Unit = { _, _, _, _, _ -> },
            stopSession: (Long) -> Unit = {}
        ) = Shared.startCallInternal(config, createSession, skipExchange, applySources, connectP2p, stopSession)

        @VisibleForTesting
        internal fun getActiveCallIdForTesting(): Long? = Shared.activeCallId

        @VisibleForTesting
        internal fun resetStateForTesting() {
            Shared.activeCallId = null
            Shared.rendererEnabled = false
            Shared.rendererAttached = false
            Shared.webrtcContextInitialized = false
            Shared.applicationContext = null
        }

        /**
         * Testable seam for the WebRTC-Android-context init flag machinery,
         * with no Android/native dependency: [handle] is an opaque value
         * forwarded to [initializer] unexamined, so a plain unit test can
         * pass `Unit` and a lambda instead of a real [Context] and a real
         * `PeerConnectionFactory.initialize` call. Exercises exactly the
         * latch/retry logic [Shared.ensureWebRtcContextInitialized] uses in
         * production.
         */
        @VisibleForTesting
        internal fun <T> ensureWebRtcContextInitializedForTesting(
            handle: T,
            initializer: (T) -> Boolean
        ): Boolean = Shared.ensureWebRtcContextInitializedInternal(handle, initializer)

        /**
         * Testable seam for [startCall]'s WebRTC-readiness gate: proves that
         * when [webRtcReady] is false, no native call/session function
         * ([createSession] included) is ever invoked, and `FAILED` plus a
         * non-blank error reaches the registered callback instead.
         */
        @VisibleForTesting
        internal fun startCallWithWebRtcGateForTesting(
            config: CallMediaConfig,
            webRtcReady: Boolean,
            createSession: (Long) -> Unit = {},
            skipExchange: (Long, ByteArray, Boolean) -> Unit = { _, _, _ -> },
            applySources: (Long, Boolean) -> Boolean = { _, _ -> true },
            connectP2p: (Long, List<RTCServer>, List<String>, Boolean, String?) -> Unit = { _, _, _, _, _ -> },
            stopSession: (Long) -> Unit = {}
        ) = Shared.startCallWithWebRtcGateInternal(
            config, webRtcReady, createSession, skipExchange, applySources, connectP2p, stopSession
        )

        @VisibleForTesting
        internal fun toRtcServersForTesting(servers: List<CallServerEndpoint>): List<RTCServer> =
            Shared.toRtcServers(servers)
    }

    /** Process-wide native engine state. See the class doc above for why this is shared. */
    private object Shared {
        /** Latest evidence-based media health for the active call, if sampled. */
        val mediaHealth: CallMediaHealth?
            get() = mediaActivityMonitor.latestHealth

        @Volatile private var loadAttempted = false
        @Volatile private var loaded = false
        @Volatile private var listenersRegistered = false
        @Volatile private var engineInstance: NTgCalls? = null

        /**
         * Samples the native engine's own capture/playback frame counters
         * while a call is live -- the only media-activity evidence the
         * engine's public API exposes (see [MediaActivityMonitor]).
         */
        private val mediaActivityMonitor = MediaActivityMonitor()

        /** Session counter, so a late callback can be attributed to its session. */
        @Volatile private var generationCounter: Long = 0L

        @Volatile var callback: NativeCallEngineCallback? = null
        @Volatile var activeCallId: Long? = null
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
        @Volatile var rendererEnabled: Boolean = false
        @Volatile var rendererAttached: Boolean = false
        @Volatile private var malformedFrameStreak: Int = 0
        @Volatile private var lastFrameAtMillis: Long = 0L

        /** Set by the Android adapter before a video session is started. */
        @Volatile private var cameraManager: CameraManager? = null

        /** The application [Context] handed in via [setContext], persisted so a
         * later retry (e.g. from [isTransportReady] or [startCall]) does not
         * need one threaded through again. */
        @Volatile var applicationContext: Context? = null

        /**
         * True only once `PeerConnectionFactory.initialize(...)` has itself
         * returned successfully -- never set eagerly before that call, and
         * reset back to false (see [ensureWebRtcContextInitializedInternal])
         * whenever it fails or throws, so a later attempt can retry instead
         * of the engine believing forever that WebRTC's Android layer is
         * ready when it never actually finished initializing.
         */
        @Volatile var webrtcContextInitialized = false

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
            val appContext = context.applicationContext
            applicationContext = appContext
            cameraManager = appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ensureWebRtcContextInitialized(appContext)
        }

        /**
         * Whether the engine is actually usable right now: not merely "the
         * native `.so` loaded" (that only proves the JNI bridge exists), but
         * that WebRTC's Android layer has a real application `Context` too --
         * see [ensureWebRtcContextInitialized] for why that second condition
         * is not optional. Re-attempts initialization on every call so a
         * transient earlier failure (e.g. this queried before [setContext]
         * ever ran) does not permanently read as unavailable.
         */
        fun isTransportReady(): Boolean {
            if (!ensureNativeLoaded()) return false
            val context = applicationContext ?: return false
            return ensureWebRtcContextInitialized(context)
        }

        /**
         * Gives WebRTC's Android layer an application [Context].
         *
         * Physically proven necessary, not precautionary: without this call,
         * `org.webrtc.ApplicationContextProvider.getApplicationContext()`
         * returns null, and any path that reaches WebRTC's Android device
         * enumeration crashes the process --
         * `Camera2Enumerator.isSupported(null)` throws a `NullPointerException`
         * from inside `JavaVideoCapturerModule.getDevices()`
         * (`NTgCalls.getMediaDevices()`'s Java half), which JNI escalates to a
         * fatal `JNI DETECTED ERROR IN APPLICATION` abort -- captured on a
         * physical Samsung SM-M145F (Android 15) the moment a video call's
         * `applyStreamSources` called `NTgCalls.getMediaDevices()`.
         *
         * ntgcalls' own `JNI_OnLoad` (see
         * `call-media/third-party/ntgcalls/` and
         * `docs/architecture/calling-native-stack.md`) only calls
         * `webrtc::InitAndroid`/`webrtc::JVM::Initialize` -- the native-side
         * JNI plumbing. It never calls the Java-side
         * `PeerConnectionFactory.initialize(...)`, which is what actually
         * stores the application Context `ApplicationContextProvider` reads.
         * That call is Aether's responsibility as the embedding app, exactly
         * as every WebRTC Android integration requires; nothing upstream does
         * it implicitly.
         *
         * Fails closed, not latched: this only reports success once
         * `PeerConnectionFactory.initialize` has itself returned without
         * throwing (see [ensureWebRtcContextInitializedInternal]). A failure
         * is logged and leaves [webrtcContextInitialized] false, so the next
         * call -- another [setContext], or [isTransportReady]/[startCall]
         * querying readiness -- retries instead of the engine believing
         * forever that a transport it never actually got is ready.
         */
        private fun ensureWebRtcContextInitialized(context: Context): Boolean {
            // The JNI symbols PeerConnectionFactory.initialize needs live in
            // libntgcalls.so; it must already be loaded before this runs.
            if (!ensureNativeLoaded()) return false
            return ensureWebRtcContextInitializedInternal(context, ::defaultInitializeWebRtc)
        }

        /**
         * Pure latch/retry logic, deliberately independent of [Context] or
         * any native call so it can be unit-tested with a plain lambda (see
         * `NativeTelegramCallMediaEngine.ensureWebRtcContextInitializedForTesting`)
         * instead of a mutable production-only test hook.
         */
        @Synchronized
        @VisibleForTesting
        internal fun <T> ensureWebRtcContextInitializedInternal(
            handle: T,
            initializer: (T) -> Boolean
        ): Boolean {
            if (webrtcContextInitialized) return true
            val success = try {
                initializer(handle)
            } catch (t: Throwable) {
                // Not call-scoped (no generation exists yet at app/context
                // setup time) -- generation 0 is this call's diagnostic home.
                CallDiagnostics.failure(0L, CallStage.MEDIA_SESSION_CREATED, t)
                false
            }
            webrtcContextInitialized = success
            return success
        }

        /**
         * `setNativeLibraryLoader { true }` is required, not cosmetic:
         * WebRTC's default loader calls
         * `System.loadLibrary("jingle_peerconnection_so")`, a separate native
         * library this build does not ship -- WebRTC is statically linked
         * into `libntgcalls.so` instead, already loaded by
         * [ensureNativeLoaded]'s `NTgCalls.ping()`. Letting the default
         * loader run would fail looking for a library that was never built.
         */
        private fun defaultInitializeWebRtc(context: Context): Boolean {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context)
                    .setNativeLibraryLoader { true }
                    .createInitializationOptions()
            )
            return true
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
                    // Every native transition, not just CONNECTED -- proves
                    // whether ntgcalls itself ever moves off CONNECTING
                    // (FAILED/TIMEOUT/CLOSED) before a call ends, versus the
                    // call ending purely because TDLib signalling was
                    // discarded (a hangup) while native was still silently
                    // CONNECTING the whole time.
                    CallDiagnostics.stage(activeGeneration, CallStage.P2P_CONNECTING, "native state=${info.state?.name ?: "null"} kind=${info.kind?.name ?: "unknown"} mapped=${mapped.name}")
                    callback?.onConnectionStateChanged(mapped.ordinal)
                }
                ntg.onSignalingData { callId, data ->
                    if (callId == activeCallId) callback?.onOutgoingSignalingData(data)
                }
                ntg.onRemoteSourceChange { callId, source ->
                    // The remote peer's stream appearing (or changing state) is
                    // the proof that incoming media was actually negotiated:
                    // without this, "no remote audio" is ambiguous between
                    // "remote never sent" and "remote sent, we dropped it".
                    if (callId != activeCallId) return@onRemoteSourceChange
                    CallDiagnostics.stage(
                        activeGeneration,
                        CallStage.MEDIA_ACTIVITY,
                        "remote_source ssrc=${source.ssrc} state=${source.state?.name ?: "null"} device=${source.device?.name ?: "null"}"
                    )
                    if (source.device == StreamDevice.MICROPHONE) {
                        mediaActivityMonitor.onRemoteMicState(source.state?.name)
                    }
                }
                ntg.onStreamEnd { callId, type, device ->
                    // A capture/playback stream reaching EOF -- e.g. the Oboe
                    // device dying -- is silent otherwise and looks identical
                    // to "remote stopped talking".
                    if (callId != activeCallId) return@onStreamEnd
                    CallDiagnostics.stage(
                        activeGeneration,
                        CallStage.MEDIA_ACTIVITY,
                        "stream_end type=${type?.name ?: "null"} device=${device?.name ?: "null"}"
                    )
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
            emitCameraState()
        }

        /**
         * Reports the engine's actual camera state to the registered callback,
         * so every place local capture starts, stops, fails or flips facing
         * can keep UI state honest with one call instead of hand-rolled
         * per-site logic. No-op when nothing is listening.
         */
        private fun emitCameraState() {
            callback?.onCameraStateChanged(rendererEnabled, activeCameraIsFront)
        }

        private fun rotationDegrees(rotation: VideoRotation?): Int = when (rotation) {
            VideoRotation.VIDEO_ROTATION_90 -> 90
            VideoRotation.VIDEO_ROTATION_180 -> 180
            VideoRotation.VIDEO_ROTATION_270 -> 270
            else -> 0
        }

        fun startCall(config: CallMediaConfig) {
            val ntg = engine()
            if (ntg == null) {
                callback?.onConnectionStateChanged(MediaConnectionState.UNAVAILABLE.ordinal)
                callback?.onError("Official Telegram call transport is not available on this device/build")
                return
            }
            // Distinct from the engine() == null / UNAVAILABLE path above:
            // the native bridge loaded fine, but WebRTC's Android layer
            // never got a working application Context, so createP2pCall/
            // getMediaDevices are not safe to reach yet. See
            // ensureWebRtcContextInitialized's doc for the exact crash this
            // prevents.
            val context = applicationContext
            val webRtcReady = context != null && ensureWebRtcContextInitialized(context)
            startCallWithWebRtcGateInternal(
                config = config,
                webRtcReady = webRtcReady,
                createSession = { ntg.createP2pCall(it) },
                skipExchange = { id, key, out -> ntg.skipExchange(id, key, out) },
                applySources = { id, cam -> applyStreamSources(ntg, id, cam) },
                connectP2p = { id, servers, versions, allowP2p, customParams ->
                    ntg.connectP2p(id, servers, versions, allowP2p, customParams)
                },
                stopSession = { ntg.stop(it) }
            )
        }

        @VisibleForTesting
        internal fun startCallWithWebRtcGateInternal(
            config: CallMediaConfig,
            webRtcReady: Boolean,
            createSession: (Long) -> Unit,
            skipExchange: (Long, ByteArray, Boolean) -> Unit,
            applySources: (Long, Boolean) -> Boolean,
            connectP2p: (Long, List<RTCServer>, List<String>, Boolean, String?) -> Unit,
            stopSession: (Long) -> Unit
        ) {
            if (!webRtcReady) {
                callback?.onConnectionStateChanged(MediaConnectionState.FAILED.ordinal)
                callback?.onError("WebRTC Android context failed to initialize; call transport is not ready")
                return
            }
            startCallInternal(config, createSession, skipExchange, applySources, connectP2p, stopSession)
        }

        /**
         * Maps Aether's own [CallServerEndpoint] list to the exact
         * `io.github.pytgcalls.p2p.RTCServer` constructor argument order
         * (verified via `javap` against the shipped AAR, not assumed).
         *
         * [CallServerEndpoint.peerTag] is passed through exactly as-is --
         * `null` for a real `null`, never coerced to an empty array -- so a
         * genuine STUN/TURN (`CallServerTypeWebrtc`) server reaches
         * ntgcalls' native `RTCServer::to_rtc_servers()` with `peer_tag`
         * absent (`std::nullopt`) rather than being misread as a Telegram
         * reflector. Extracted out of [startCallInternal] specifically so
         * this mapping is directly unit-testable against the real
         * `io.github.pytgcalls.p2p.RTCServer` Java class (a plain data
         * holder with no native calls of its own) without needing a running
         * native call session.
         */
        @VisibleForTesting
        internal fun toRtcServers(servers: List<CallServerEndpoint>): List<RTCServer> = servers.map { server ->
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

        @VisibleForTesting
        internal fun startCallInternal(
            config: CallMediaConfig,
            createSession: (Long) -> Unit,
            skipExchange: (Long, ByteArray, Boolean) -> Unit,
            applySources: (Long, Boolean) -> Boolean,
            connectP2p: (Long, List<RTCServer>, List<String>, Boolean, String?) -> Unit,
            stopSession: (Long) -> Unit
        ) {
            val generation = ++generationCounter
            activeCallId = config.callId
            activeGeneration = generation
            activeIsVideo = config.videoCaptureEnabled
            activeCameraIsFront = true
            rendererEnabled = config.videoCaptureEnabled
            rendererAttached = false
            malformedFrameStreak = 0
            lastFrameAtMillis = 0L
            callback?.onConnectionStateChanged(MediaConnectionState.INITIALIZING.ordinal)
            emitCameraState()

            var nativeSessionCreated = false
            try {
                createSession(config.callId)
                nativeSessionCreated = true
                skipExchange(config.callId, config.encryptionKey, config.isOutgoing)
                CallDiagnostics.stage(generation, CallStage.MEDIA_SESSION_CREATED, "outgoing=${config.isOutgoing}")
                startMediaActivityMonitor(config.callId, generation)

                val rtcServers = toRtcServers(config.servers)
                CallDiagnostics.stage(generation, CallStage.P2P_CONNECTING, "servers=${rtcServers.size} p2p=${config.allowP2p}")

                // ntgcalls expects capture sources to exist before transport
                // negotiation starts. Starting P2P first creates a race where
                // the CONNECTED callback can arrive while the native session
                // still has no audio source; that was the connect-time crash
                // boundary in the interrupted implementation.
                if (!applySources(config.callId, config.videoCaptureEnabled)) {
                    cleanupFailedStartup(config.callId, generation, nativeSessionCreated, stopSession)
                    callback?.onConnectionStateChanged(MediaConnectionState.FAILED.ordinal)
                    return
                }

                connectP2p(
                    config.callId,
                    rtcServers,
                    config.protocol.libraryVersions,
                    config.allowP2p,
                    config.customParameters.ifBlank { null }
                )

            } catch (e: Throwable) {
                CallDiagnostics.failure(generation, CallStage.MEDIA_SESSION_CREATED, e)
                cleanupFailedStartup(config.callId, generation, nativeSessionCreated, stopSession)
                callback?.onConnectionStateChanged(MediaConnectionState.FAILED.ordinal)
                callback?.onError(e.message ?: "Call transport failed to start")
            }
        }

        private fun cleanupFailedStartup(
            callId: Long,
            generation: Long,
            nativeSessionCreated: Boolean,
            stopSession: (Long) -> Unit
        ) {
            mediaActivityMonitor.stop()
            if (nativeSessionCreated) {
                try {
                    stopSession(callId)
                } catch (t: Throwable) {
                    CallDiagnostics.failure(generation, CallStage.TEARDOWN, t)
                }
            }
            activeCallId = null
            rendererEnabled = false
            rendererAttached = false
            emitCameraState()
        }

        /**
         * Starts polling the native engine's media-activity counters for this
         * session (see [MediaActivityMonitor]). Uses [engineInstance] rather
         * than a parameter so the testable [startCallInternal] seam needs no
         * extra lambda: with no engine instance (unit tests, or a session
         * that never materialised) there is simply nothing to sample.
         */
        private fun startMediaActivityMonitor(callId: Long, generation: Long) {
            val ntg = engineInstance ?: return
            mediaActivityMonitor.start(
                callId = callId,
                generation = generation,
                sampler = {
                    val state = ntg.getState(callId)
                    MediaActivitySample(
                        captureSeconds = ntg.time(callId, StreamMode.CAPTURE),
                        playbackSeconds = ntg.time(callId, StreamMode.PLAYBACK),
                        muted = state.muted,
                        videoPaused = state.videoPaused,
                        videoStopped = state.videoStopped
                    )
                },
                stillActive = { activeCallId == callId && activeGeneration == generation }
            )
        }

        /**
         * Configures capture sources. Audio is resolved first and independently:
         * a camera that cannot be enumerated or opened leaves the microphone
         * description untouched, so a video call degrades to a working voice call
         * rather than losing both.
         */
        private fun applyStreamSources(ntg: NTgCalls, callId: Long, cameraEnabled: Boolean): Boolean {
            return applyStreamSourcesInternal(
                callId = callId,
                cameraEnabled = cameraEnabled,
                // Called for every call, voice included: ntgcalls' native
                // BaseDeviceModule constructor requires the microphone's real
                // `input` to be a JSON object containing `is_microphone`
                // (ntgcalls/src/media/devices/base_device_module.cpp -- it
                // does `json::parse(desc->input)` and throws
                // MediaDeviceError("Invalid device metadata") otherwise).
                // Only safe to call now that initializeWebRtcApplicationContext
                // has given WebRTC's Android layer a real application Context.
                deviceProvider = { NTgCalls.getMediaDevices() },
                streamSourceSetter = { cid, mode, desc -> ntg.setStreamSources(cid, mode, desc) }
            )
        }

        /**
         * Configures both CAPTURE (this device's mic/camera going out) and
         * PLAYBACK (the remote peer's audio/video coming in) sources.
         *
         * Configuring CAPTURE alone -- Aether's whole behaviour before this --
         * is exactly why native could reach CONNECTED while nobody heard or
         * saw anything: pinned rc02's own `StreamManager::optimize_sources`
         * (`ntgcalls/src/media/stream_manager.cpp`) only turns on incoming
         * audio/video on the peer connection once a PLAYBACK writer exists
         * (`writers_.contains(Microphone)` / `writers_.contains(Camera)`).
         * With no PLAYBACK call ever made, those writers never existed, so
         * ICE/DTLS could finish and native could still report CONNECTED with
         * incoming media permanently disabled at the peer-connection level.
         *
         * PLAYBACK failing here is never fatal to the call -- CAPTURE (this
         * device's own outgoing audio, and the call itself) must still
         * succeed with no speaker device enumerated; see
         * [applyPlaybackSources]'s own doc.
         */
        @VisibleForTesting
        internal fun applyStreamSourcesInternal(
            callId: Long,
            cameraEnabled: Boolean,
            deviceProvider: () -> MediaDevices?,
            streamSourceSetter: (Long, StreamMode, MediaDescription) -> Unit,
            cameraSelector: ((MediaDevices, Boolean) -> DeviceInfo?)? = null
        ): Boolean {
            val generation = activeGeneration
            val devices = try {
                deviceProvider()
            } catch (t: Throwable) {
                CallDiagnostics.failure(generation, CallStage.AUDIO_INITIALIZING, t)
                null
            }

            if (!applyCaptureSources(generation, callId, cameraEnabled, devices, streamSourceSetter, cameraSelector)) {
                return false
            }
            applyPlaybackSources(generation, callId, cameraEnabled, devices, streamSourceSetter)
            return true
        }

        private fun applyCaptureSources(
            generation: Long,
            callId: Long,
            cameraEnabled: Boolean,
            devices: MediaDevices?,
            streamSourceSetter: (Long, StreamMode, MediaDescription) -> Unit,
            cameraSelector: ((MediaDevices, Boolean) -> DeviceInfo?)?
        ): Boolean {
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
                    val chosen = if (cameraSelector != null) {
                        devices?.let { cameraSelector(it, activeCameraIsFront) }
                    } else {
                        devices?.let { selectCamera(it, front = activeCameraIsFront) }
                    }
                    CallDiagnostics.stage(
                        generation,
                        CallStage.VIDEO_INITIALIZING,
                        "camera_selected front=$activeCameraIsFront id=${chosen?.name ?: "none"}"
                    )
                    if (chosen == null) {
                        // A video-capable session with no enumerable matching
                        // camera (cameraless device, or metadata that resolves
                        // to nothing): capture may not silently pretend a
                        // camera exists -- the renderer stops and UI state
                        // follows reality.
                        disableRenderer("no_camera_device")
                    }
                    chosen?.let {
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
                streamSourceSetter(
                    callId,
                    StreamMode.CAPTURE,
                    MediaDescription(microphone, null, camera, null)
                )
                CallDiagnostics.stage(generation, CallStage.AUDIO_INITIALIZING, "CAPTURE_CONFIG audio=true camera=${camera != null}")
                return true
            } catch (t: Throwable) {
                CallDiagnostics.failure(generation, CallStage.AUDIO_INITIALIZING, t)
                if (camera != null) {
                    // Retry without the camera: prove whether video was the
                    // reason the whole capture description was rejected.
                    disableRenderer("stream_sources_rejected_with_camera")
                    try {
                        streamSourceSetter(
                            callId,
                            StreamMode.CAPTURE,
                            MediaDescription(microphone, null, null, null)
                        )
                        CallDiagnostics.stage(generation, CallStage.AUDIO_INITIALIZING, "CAPTURE_CONFIG audio=true camera=false")
                        return true
                    } catch (retry: Throwable) {
                        CallDiagnostics.failure(generation, CallStage.AUDIO_INITIALIZING, retry)
                        return false
                    }
                }
                return false
            }
        }

        /**
         * Enables incoming media on the peer connection: without this,
         * `StreamManager::optimize_sources` never turns on incoming
         * audio/video regardless of how healthy ICE/DTLS is (see this
         * function's caller for the exact native proof).
         *
         * Two different device slots, and neither is the "obvious" one --
         * proven against pinned rc02's own `stream_manager.cpp`, not
         * guessed:
         *
         * - Audio: `optimize_sources` checks `writers_.contains(Microphone)`,
         *   never `Speaker`. `set_stream_sources` maps
         *   `MediaDescription.microphone` to the `Microphone` device slot
         *   regardless of [StreamMode] -- so the real output/speaker device
         *   must be placed in the `.microphone` field of a PLAYBACK call, or
         *   `optimize_sources` never sees a writer there and leaves incoming
         *   audio permanently disabled. The device itself still comes from
         *   [MediaDevices.speaker] (a genuine output device, `is_microphone`
         *   false in its real metadata) -- never [MediaDevices.microphone],
         *   and never synthesized metadata.
         * - Video: `handle_playback_config` only accepts `MediaSource.EXTERNAL`
         *   for a PLAYBACK video slot (any other source throws
         *   `InvalidParams("Invalid input mode")`); that registers
         *   `Device::Camera` as an external writer and wires
         *   `setup_video_playback_callbacks`, which is how decoded remote
         *   frames reach `NTgCalls.onFrames` -- already plumbed through to
         *   [handleFrames] in this class. Width/height/fps are unused on this
         *   path (confirmed against `VideoSink::set_config`, which this
         *   playback-mode slot never even reaches) and are left at 0.
         *
         * Never fatal: a device that cannot carry PLAYBACK audio (no output
         * device enumerated) or PLAYBACK video must not fail a CAPTURE-only
         * call that otherwise succeeded -- see [applyStreamSourcesInternal].
         * A voice call (cameraEnabled=false) never configures playback video.
         */
        private fun applyPlaybackSources(
            generation: Long,
            callId: Long,
            cameraEnabled: Boolean,
            devices: MediaDevices?,
            streamSourceSetter: (Long, StreamMode, MediaDescription) -> Unit
        ) {
            val playbackAudio = try {
                devices?.speaker?.firstOrNull()?.let {
                    AudioDescription(MediaSource.DEVICE, 48000, 1, it.metadata, false)
                }
            } catch (t: Throwable) {
                CallDiagnostics.failure(generation, CallStage.AUDIO_INITIALIZING, t)
                null
            }
            if (playbackAudio == null) {
                CallDiagnostics.failure(
                    generation,
                    CallStage.AUDIO_INITIALIZING,
                    IllegalStateException("No speaker/output media device is available for playback")
                )
            }

            // A voice call never configures playback video: rendererEnabled
            // gates handleFrames the same way, but this keeps a video-typed
            // PLAYBACK slot from ever being opened for a call that has no
            // camera/renderer to begin with.
            val playbackVideo = if (cameraEnabled) {
                VideoDescription(MediaSource.EXTERNAL, 0, 0, 0, "", false)
            } else {
                null
            }

            try {
                streamSourceSetter(
                    callId,
                    StreamMode.PLAYBACK,
                    MediaDescription(playbackAudio, null, playbackVideo, null)
                )
                CallDiagnostics.stage(
                    generation,
                    CallStage.AUDIO_INITIALIZING,
                    "PLAYBACK_CONFIG audio=${playbackAudio != null} video=${playbackVideo != null}"
                )
            } catch (t: Throwable) {
                CallDiagnostics.failure(generation, CallStage.AUDIO_INITIALIZING, t)
            }
        }

        /**
         * Resolve facing from ntgcalls' own camera metadata (see
         * [CameraDeviceSelection] for why that is authoritative), with
         * Android's CameraCharacteristics as the fallback/cross-check for a
         * device whose metadata carries no facing. If nothing resolves, no
         * camera is selected and the call remains audio.
         */
        private fun selectCamera(devices: MediaDevices, front: Boolean): DeviceInfo? {
            val parsed = devices.camera.map { CameraDeviceSelection.parse(it) }
            return CameraDeviceSelection.select(parsed, front, facingOf = ::lensFacingFromCameraManager)
        }

        /** Camera2-id -> facing, or null when the id is not a camera2 id. */
        private fun lensFacingFromCameraManager(id: String): Boolean? {
            val manager = cameraManager ?: return null
            val facing = runCatching {
                manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
            }.getOrNull()
            return when (facing) {
                CameraCharacteristics.LENS_FACING_FRONT -> true
                CameraCharacteristics.LENS_FACING_BACK -> false
                else -> null
            }
        }

        fun setCameraEnabled(enabled: Boolean) {
            val ntg = engineInstance ?: return
            val callId = activeCallId ?: return
            // A voice-typed session (video never negotiated) cannot be talked
            // into adding a camera after the fact: turning it on requires the
            // call itself to be video-capable. A video-typed session whose
            // camera is off (no permission at start, renderer gave up, user
            // toggle) may legitimately re-attempt -- setStreamSources is the
            // same path switchCamera uses.
            if (enabled && !activeIsVideo) return
            if (!enabled && !rendererEnabled) return
            if (!enabled) {
                // Stopped optimistically: a rejected reconfiguration leaves the
                // camera description out of CAPTURE anyway.
                rendererEnabled = false
                rendererAttached = false
            }
            val applied = applyStreamSources(ntg, callId, cameraEnabled = enabled)
            if (applied) {
                rendererEnabled = enabled
                if (!enabled) rendererAttached = false
            }
            emitCameraState()
        }

        fun switchCamera() {
            val ntg = engineInstance ?: return
            val callId = activeCallId ?: return
            if (!activeIsVideo || !rendererEnabled) return
            activeCameraIsFront = !activeCameraIsFront
            val applied = applyStreamSources(ntg, callId, cameraEnabled = true)
            if (!applied) activeCameraIsFront = !activeCameraIsFront
            emitCameraState()
        }

        fun submitSignalingData(callId: Long, data: ByteArray) {
            // checkpoint=G: the native adapter boundary. Every possible exit
            // is logged explicitly -- a silent `return` here (engine not yet
            // created, or a callId that does not match the active session)
            // was previously indistinguishable from "ntgcalls accepted and
            // processed the packet" in the diagnostics, which is exactly the
            // ambiguity this investigation needs closed.
            val ntg = engineInstance
            if (ntg == null) {
                CallDiagnostics.stage(activeGeneration, CallStage.P2P_CONNECTING, "cp=G dropped_no_engine callId=$callId")
                return
            }
            if (callId != activeCallId) {
                CallDiagnostics.stage(activeGeneration, CallStage.P2P_CONNECTING, "cp=G dropped_callid_mismatch callId=$callId activeCallId=$activeCallId")
                return
            }
            try {
                ntg.sendSignalingData(callId, data)
                CallDiagnostics.stage(activeGeneration, CallStage.P2P_CONNECTING, "cp=G submitted_to_native callId=$callId bytes=${data.size}")
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
            emitCameraState()
            mediaActivityMonitor.stop()
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
