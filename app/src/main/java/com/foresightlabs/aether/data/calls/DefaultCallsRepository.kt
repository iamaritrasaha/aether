package com.foresightlabs.aether.data.calls

import android.app.Application
import com.foresightlabs.aether.calls.media.CallDiagnostics
import com.foresightlabs.aether.calls.media.CallStage
import com.foresightlabs.aether.calls.media.DecodedVideoFrame
import com.foresightlabs.aether.calls.media.NativeTelegramCallMediaEngine
import com.foresightlabs.aether.data.calls.media.TgCallsMediaEngine
import com.foresightlabs.aether.data.permissions.PermissionCoordinator
import com.foresightlabs.aether.data.telegram.TelegramCallMessageMapper
import com.foresightlabs.aether.data.telegram.TelegramClient
import com.foresightlabs.aether.domain.calls.AudioRoute
import com.foresightlabs.aether.domain.calls.CallPermissions
import com.foresightlabs.aether.domain.calls.CallsRepository
import com.foresightlabs.aether.domain.calls.MediaConnectionState
import com.foresightlabs.aether.domain.calls.TelegramCallMediaEngine
import com.foresightlabs.aether.domain.model.ActiveCall
import com.foresightlabs.aether.domain.model.CallHistoryItem
import com.foresightlabs.aether.domain.model.CallHistoryUiState
import com.foresightlabs.aether.domain.model.CallStateEnum
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

/** Raised when a call is attempted with no media transport to carry its audio. */
class CallMediaUnavailableException(message: String) : Exception(message)

/** The diagnostic stage a media-transport state corresponds to. */
private fun MediaConnectionState.toStage(): CallStage = when (this) {
    MediaConnectionState.IDLE -> CallStage.TEARDOWN
    MediaConnectionState.INITIALIZING -> CallStage.MEDIA_SESSION_CREATED
    MediaConnectionState.CONNECTING -> CallStage.P2P_CONNECTING
    MediaConnectionState.CONNECTED -> CallStage.MEDIA_CONNECTED
    MediaConnectionState.RECONNECTING -> CallStage.P2P_CONNECTING
    MediaConnectionState.FAILED, MediaConnectionState.UNAVAILABLE -> CallStage.FAILED
    MediaConnectionState.STOPPED -> CallStage.TEARDOWN
}

class DefaultCallsRepository(
    private val telegram: TelegramClient,
    private val application: Application,
    private val permissionCoordinator: PermissionCoordinator,
    val mediaEngine: TelegramCallMediaEngine = TgCallsMediaEngine(application)
) : CallsRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val isCallMediaAvailable: Boolean
        get() = mediaEngine.isMediaTransportAvailable

    override val callMediaUnavailableReason: String?
        get() = if (isCallMediaAvailable) null else NO_MEDIA_TRANSPORT

    override val activeCallState: StateFlow<ActiveCall?> = telegram.activeCallState
    override val videoFrames: SharedFlow<DecodedVideoFrame> = mediaEngine.videoFrames

    private val _historyState = MutableStateFlow<CallHistoryUiState>(CallHistoryUiState.Loading)
    override val historyState: StateFlow<CallHistoryUiState> = _historyState.asStateFlow()

    private var timerJob: Job? = null
    private var lastTdLibState: CallStateEnum? = null
    private var lastMediaState: MediaConnectionState? = null

    /**
     * Bounds how long a call may sit in CONNECTING before it is treated as
     * failed.
     *
     * ICE connectivity checks against ntgcalls' P2P/reflector servers
     * normally resolve within a few seconds on a functioning network; this
     * window is sized to also cover a reflector (TURN-equivalent) fallback
     * on a slow or restrictive network without making a genuinely broken
     * call look like it is still trying to connect for the better part of a
     * minute. It intentionally does not attempt to match a specific ntgcalls
     * internal ICE timeout constant -- none is part of its public contract --
     * and instead bounds what Aether itself is willing to show the user.
     */
    private val connectTimeoutMillis: Long = 20_000L

    /** The one connection watchdog in flight, if any -- see [startConnectWatchdog]. */
    private var watchdogJob: Job? = null

    private val outgoingSignalSeq = AtomicLong(0)
    private val incomingSignalSeq = AtomicLong(0)

    /**
     * Monotonic id for the one media session this repository owns.
     *
     * Bumped every time a media session is started or torn down, so a late
     * callback, a stale service intent or a delayed coroutine can be attributed
     * to the session it belongs to instead of silently acting on the one that
     * replaced it.
     */
    private val callGeneration = AtomicLong(0L)

    /** The call id whose media session is currently started, if any. */
    @Volatile
    private var startedMediaCallId: Int? = null

    // Guards against a doubled Accept/End tap (or a second tap landing while
    // the first is still suspended on TDLib) driving the native engine or a
    // TDLib request twice for the same call. Cleared once the call actually
    // ends -- see handleTdLibStateChange -- so these never grow unbounded.
    private val acceptedCallIds = Collections.synchronizedSet(mutableSetOf<Int>())
    private val discardedCallIds = Collections.synchronizedSet(mutableSetOf<Int>())

    init {
        // Every collector body is guarded: an exception raised while reacting to
        // a call update must not cancel the collector (which would silently stop
        // all later call handling) and must never reach the process's uncaught
        // handler. A call is exactly the moment a crash costs the most.
        scope.launch {
            telegram.activeCallState.collect { call ->
                guarded(CallStage.TDLIB_READY) { handleTdLibStateChange(call) }
            }
        }

        scope.launch {
            telegram.latestRawCallState.collect { rawCall ->
                guarded(CallStage.TDLIB_READY) { handleRawCallUpdate(rawCall) }
            }
        }

        scope.launch {
            mediaEngine.state.collect { mediaState ->
                guarded(CallStage.MEDIA_CONNECTED) { handleMediaStateChange(mediaState) }
            }
        }

        // Both signalling directions TDLib and the media engine need to
        // exchange for the call to connect beyond the initial key/server
        // handshake -- see docs/architecture/messaging-calls.md.
        scope.launch {
            mediaEngine.outgoingSignalingData.collect { data ->
                val callId = activeCallState.value?.callId ?: return@collect
                val seq = outgoingSignalSeq.incrementAndGet()
                runCatching { telegram.sendCallSignalingData(callId, data) }
                    .onSuccess {
                        CallDiagnostics.stage(callGeneration.get(), CallStage.P2P_CONNECTING, "signal=out seq=$seq bytes=${data.size} tdlib=ok")
                    }
                    .onFailure {
                        CallDiagnostics.stage(callGeneration.get(), CallStage.P2P_CONNECTING, "signal=out seq=$seq bytes=${data.size} tdlib=fail")
                        CallDiagnostics.failure(callGeneration.get(), CallStage.P2P_CONNECTING, it)
                    }
            }
        }
        scope.launch {
            telegram.callSignalingDataFlow.collect { update ->
                val payload = update.data ?: return@collect
                val seq = incomingSignalSeq.incrementAndGet()
                // checkpoint=E: reached the repository's own collector at all.
                // The activeCallState comparison proves whether this update
                // even belongs to the call this repository currently thinks
                // is live, independent of whatever callId filtering the
                // native layer applies further downstream.
                val currentCallId = activeCallState.value?.callId
                val callIdMatches = currentCallId != null && update.callId == currentCallId
                CallDiagnostics.stage(
                    callGeneration.get(),
                    CallStage.P2P_CONNECTING,
                    "cp=E repository_collector signal=in seq=$seq bytes=${payload.size} callIdMatch=$callIdMatches"
                )
                guarded(CallStage.P2P_CONNECTING) {
                    // checkpoint=F: about to call into the media engine. Any
                    // exception here is already surfaced by `guarded` as a
                    // FAILED diagnostic at this same generation/stage.
                    CallDiagnostics.stage(callGeneration.get(), CallStage.P2P_CONNECTING, "cp=F submitting_to_media_engine seq=$seq")
                    mediaEngine.submitIncomingSignalingData(update.callId.toLong(), payload)
                }
            }
        }
    }

    /**
     * Bounds a media session's time in CONNECTING. Generation-aware so a
     * timer left over from a call that already ended or was replaced can
     * never act on the call that succeeded it -- see [callGeneration].
     */
    private fun startConnectWatchdog(generation: Long) {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            delay(connectTimeoutMillis)
            if (callGeneration.get() != generation) return@launch
            if (mediaEngine.state.value == MediaConnectionState.CONNECTED) return@launch

            CallDiagnostics.failure(
                generation,
                CallStage.P2P_CONNECTING,
                java.util.concurrent.TimeoutException(
                    "Media did not reach CONNECTED within ${connectTimeoutMillis}ms"
                )
            )
            val callId = activeCallState.value?.callId
            stopTimer()
            mediaEngine.failConnectTimeout()
            CallService.stopService(application)
            CallDiagnostics.stage(generation, CallStage.SERVICE_STOPPED, "reason=connect_timeout")
            if (callId != null) {
                discardCall(callId)
            }
        }
    }

    private fun stopConnectWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = null
    }

    private inline fun guarded(stage: CallStage, body: () -> Unit) {
        try {
            body()
        } catch (cancellation: CancellationException) {
            // Cancellation is how a coroutine is told to stop, not a failure to
            // absorb: swallowing it here would leave the collector believing it
            // is still running.
            throw cancellation
        } catch (t: Throwable) {
            CallDiagnostics.failure(callGeneration.get(), stage, t)
        }
    }

    /**
     * The call protocol Aether actually asks TDLib to negotiate, built from
     * what the linked media engine really supports rather than a guessed
     * layer/version list. Falls back to conservative, TDLib-documented
     * defaults only when no transport is loaded at all -- a path
     * [isCallMediaAvailable] should already keep [initiateCall]/[acceptCall]
     * from reaching.
     */
    private fun negotiatedProtocol(): TdApi.CallProtocol =
        TgCallsAdapter.buildCallProtocol(NativeTelegramCallMediaEngine.supportedProtocol())

    /**
     * Records the actual negotiated call protocol and server topology,
     * sanitised: layer/version/flag counts only, never an IP, port,
     * username, password or peerTag byte -- section 4/10 of the incoming-
     * signalling forensics investigation asked for this to be proven, not
     * inferred from [NativeTelegramCallMediaEngine.supportedProtocol] alone.
     */
    private fun logNegotiatedProtocol(generation: Long, ready: TdApi.CallStateReady) {
        val requested = NativeTelegramCallMediaEngine.supportedProtocol()
        CallDiagnostics.stage(
            generation,
            CallStage.TDLIB_READY,
            "requestedProtocol minLayer=${requested?.minLayer} maxLayer=${requested?.maxLayer} " +
                "udpP2p=${requested?.udpP2p} udpReflector=${requested?.udpReflector} versions=${requested?.libraryVersions}"
        )
        val readyProtocol = ready.protocol
        CallDiagnostics.stage(
            generation,
            CallStage.TDLIB_READY,
            "readyProtocol minLayer=${readyProtocol?.minLayer} maxLayer=${readyProtocol?.maxLayer} " +
                "udpP2p=${readyProtocol?.udpP2p} udpReflector=${readyProtocol?.udpReflector} versions=${readyProtocol?.libraryVersions?.toList()}"
        )
        val servers = ready.servers.orEmpty()
        val reflectorCount = servers.count { it.type is TdApi.CallServerTypeTelegramReflector }
        val webrtcCount = servers.count { it.type is TdApi.CallServerTypeWebrtc }
        val tcpReflectorCount = servers.count { (it.type as? TdApi.CallServerTypeTelegramReflector)?.isTcp == true }
        val stunCount = servers.count { (it.type as? TdApi.CallServerTypeWebrtc)?.supportsStun == true }
        val turnCount = servers.count { (it.type as? TdApi.CallServerTypeWebrtc)?.supportsTurn == true }
        CallDiagnostics.stage(
            generation,
            CallStage.TDLIB_READY,
            "allowP2p=${ready.allowP2p} servers=${servers.size} reflector=$reflectorCount webrtc=$webrtcCount " +
                "tcpReflector=$tcpReflectorCount stun=$stunCount turn=$turnCount " +
                "configLen=${ready.config?.length ?: -1} customParamsLen=${ready.customParameters?.length ?: -1}"
        )
    }

    private fun handleRawCallUpdate(rawCall: TdApi.Call?) {
        if (rawCall == null) return
        val readyState = rawCall.state as? TdApi.CallStateReady ?: return

        // One media session per call, ever. Re-delivery of CallStateReady (TDLib
        // repeats the update, and does so again after a process restart) must not
        // start a second session on top of a live one.
        if (startedMediaCallId == rawCall.id) return
        val mediaState = mediaEngine.state.value
        if (mediaState != MediaConnectionState.IDLE && mediaState != MediaConnectionState.STOPPED) return

        startedMediaCallId = rawCall.id
        val generation = callGeneration.incrementAndGet()
        outgoingSignalSeq.set(0)
        incomingSignalSeq.set(0)
        CallDiagnostics.stage(generation, CallStage.TDLIB_READY, "video=${rawCall.isVideo} outgoing=${rawCall.isOutgoing}")
        logNegotiatedProtocol(generation, readyState)

        // Camera capture is a separate capability from the call itself: a video
        // call whose camera permission was refused still connects, as audio.
        // Video initialisation must never be on the path a voice call takes.
        val videoCaptureEnabled = rawCall.isVideo && permissionCoordinator.isGranted(CallPermissions.CAMERA)
        if (rawCall.isVideo && !videoCaptureEnabled) {
            CallDiagnostics.stage(generation, CallStage.VIDEO_INITIALIZING, "skipped=no_camera_permission")
        }

        val callerName = activeCallState.value?.user?.name ?: "Telegram Contact"
        CallService.startService(
            application,
            callerName,
            isConnected = false,
            isVideo = videoCaptureEnabled,
            generation = generation
        )

        startConnectWatchdog(generation)
        scope.launch {
            guarded(CallStage.MEDIA_SESSION_CREATED) {
                mediaEngine.start(rawCall, readyState, videoCaptureEnabled)
            }
        }
    }

    private fun handleTdLibStateChange(call: ActiveCall?) {
        val currentState = call?.state
        if (currentState == CallStateEnum.DISCARDED || currentState == CallStateEnum.ERROR) {
            CallDiagnostics.stage(callGeneration.get(), CallStage.TEARDOWN, "tdlib=${currentState.name}")
            stopTimer()
            stopConnectWatchdog()
            startedMediaCallId = null
            callGeneration.incrementAndGet()
            mediaEngine.stop()
            CallService.stopService(application)
            CallDiagnostics.stage(callGeneration.get(), CallStage.SERVICE_STOPPED, "reason=call_ended")
            call?.let {
                acceptedCallIds.remove(it.callId)
                discardedCallIds.remove(it.callId)
            }
            scope.launch {
                delay(1000)
                guarded(CallStage.TEARDOWN) { refreshHistory() }
            }
        }
        lastTdLibState = currentState
    }

    private fun handleMediaStateChange(mediaState: MediaConnectionState) {
        val generation = callGeneration.get()
        CallDiagnostics.stage(generation, mediaState.toStage(), "media=${mediaState.name}")

        val currentCall = activeCallState.value
        if (currentCall != null) {
            val callerName = currentCall.user?.name ?: "Telegram Contact"

            when (mediaState) {
                MediaConnectionState.CONNECTED -> {
                    // Real media is up: the watchdog's only job -- catching a
                    // session that never gets here -- is done.
                    stopConnectWatchdog()
                    CallService.startService(
                        application,
                        callerName,
                        isConnected = true,
                        // Only claim the camera service type when the camera is
                        // genuinely in use for this call.
                        isVideo = currentCall.isVideo && permissionCoordinator.isGranted(CallPermissions.CAMERA),
                        generation = generation
                    )
                    startTimer()
                }
                MediaConnectionState.FAILED -> {
                    // A media-layer failure (ICE/connectivity failure, native
                    // TIMEOUT, or this repository's own connect watchdog) must
                    // end the call at the signalling layer too. Stopping only
                    // the local media here and never discarding the TDLib call
                    // left signalling sitting at READY indefinitely -- and
                    // with no CallPresentationState case for a stopped/failed
                    // media engine while signalling is still READY, the call
                    // screen kept showing Connecting forever even though the
                    // media engine had already given up.
                    stopConnectWatchdog()
                    stopTimer()
                    CallService.stopService(application)
                    mediaEngine.stop()
                    scope.launch { discardCall(currentCall.callId) }
                }
                MediaConnectionState.UNAVAILABLE -> {
                    // Signalling succeeded but nothing can carry audio. Ending the
                    // call is the honest outcome: leaving it up would show a
                    // connected-looking call that is silent for both people.
                    stopConnectWatchdog()
                    stopTimer()
                    CallService.stopService(application)
                    telegram.reportCallMediaUnavailable(NO_MEDIA_TRANSPORT)
                    scope.launch { discardCall(currentCall.callId) }
                }
                MediaConnectionState.STOPPED -> {
                    stopConnectWatchdog()
                    stopTimer()
                    CallService.stopService(application)
                }
                else -> {}
            }
        }
        lastMediaState = mediaState
    }

    private var elapsedCallSeconds = 0

    private fun startTimer() {
        if (timerJob?.isActive == true) return
        timerJob = scope.launch {
            while (true) {
                delay(1000)
                val state = mediaEngine.state.value
                if (state == MediaConnectionState.CONNECTED) {
                    elapsedCallSeconds++
                    telegram.updateCallDuration(elapsedCallSeconds)
                } else if (state == MediaConnectionState.RECONNECTING) {
                    // Media temporarily reconnecting: preserve elapsed duration
                } else {
                    break
                }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        elapsedCallSeconds = 0
    }

    private companion object {
        const val NO_MEDIA_TRANSPORT =
            "Calls can't connect in this build — no call audio engine is included."
    }

    override suspend fun initiateCall(userId: Long, isVideo: Boolean): Result<Int> {
        if (!mediaEngine.isMediaTransportAvailable) {
            return Result.failure(CallMediaUnavailableException(NO_MEDIA_TRANSPORT))
        }
        return telegram.createCall(userId, isVideo, negotiatedProtocol())
    }

    override suspend fun acceptCall(callId: Int): Result<Unit> {
        if (!mediaEngine.isMediaTransportAvailable) {
            telegram.discardCall(callId)
            return Result.failure(CallMediaUnavailableException(NO_MEDIA_TRANSPORT))
        }
        if (!acceptedCallIds.add(callId)) {
            // Already accepted -- a second tap, or a tap that landed while the
            // first was still suspended on TDLib. Neither should ask TDLib or
            // the native engine to accept the same call twice.
            return Result.success(Unit)
        }
        return telegram.acceptCall(callId, negotiatedProtocol())
    }

    override suspend fun discardCall(callId: Int): Result<Unit> {
        if (!discardedCallIds.add(callId)) {
            return Result.success(Unit)
        }
        stopTimer()
        stopConnectWatchdog()
        mediaEngine.stop()
        CallService.stopService(application)
        return telegram.discardCall(callId)
    }

    override fun toggleMute() {
        val newMute = !mediaEngine.isMuted.value
        mediaEngine.setMicrophoneMuted(newMute)
        telegram.toggleCallMute()
    }

    override fun setCameraEnabled(enabled: Boolean) {
        mediaEngine.setCameraEnabled(enabled)
    }

    override fun switchCamera() {
        mediaEngine.switchCamera()
    }

    override fun toggleSpeaker() {
        val newRoute = if (mediaEngine.audioRoute.value == AudioRoute.SPEAKER) AudioRoute.EARPIECE else AudioRoute.SPEAKER
        mediaEngine.setAudioOutput(newRoute)
        telegram.toggleCallSpeaker()
    }

    override fun setMinimized(minimized: Boolean) {
        telegram.setCallMinimized(minimized)
    }

    override suspend fun loadInitialHistory() {
        _historyState.value = CallHistoryUiState.Loading
        fetchHistory(offset = "")
    }

    override suspend fun loadNextPageHistory() {
        val current = _historyState.value as? CallHistoryUiState.Content ?: return
        if (!current.hasMore || current.isLoadingMore) return

        _historyState.value = current.copy(isLoadingMore = true)
        fetchHistory(offset = current.nextOffset, existingItems = current.items)
    }

    override suspend fun refreshHistory() {
        fetchHistory(offset = "", existingItems = emptyList())
    }

    private suspend fun fetchHistory(offset: String, existingItems: List<CallHistoryItem> = emptyList()) {
        val result = telegram.searchCallMessages(offset = offset, limit = 50, onlyMissed = false)
        result.fold(
            onSuccess = { foundMessages ->
                val newItems = mutableListOf<CallHistoryItem>()
                for (msg in foundMessages.messages) {
                    val callContent = msg.content as? TdApi.MessageCall ?: continue
                    val targetUserId = if (msg.isOutgoing) {
                        msg.chatId
                    } else {
                        (msg.senderId as? TdApi.MessageSenderUser)?.userId ?: msg.chatId
                    }
                    val user = telegram.getUser(targetUserId)
                    val item = TelegramCallMessageMapper.mapToCallHistoryItem(msg, user)
                    if (item != null) {
                        newItems.add(item)
                    }
                }

                val allItems = existingItems + newItems
                val nextOffset = foundMessages.nextOffset

                if (allItems.isEmpty()) {
                    _historyState.value = CallHistoryUiState.Empty
                } else {
                    _historyState.value = CallHistoryUiState.Content(
                        items = allItems,
                        hasMore = nextOffset.isNotBlank(),
                        isLoadingMore = false,
                        nextOffset = nextOffset
                    )
                }
            },
            onFailure = { error ->
                if (existingItems.isEmpty()) {
                    _historyState.value = CallHistoryUiState.Error(error.message ?: "Failed to load call history")
                } else {
                    val current = _historyState.value as? CallHistoryUiState.Content
                    if (current != null) {
                        _historyState.value = current.copy(isLoadingMore = false)
                    }
                }
            }
        )
    }
}
