package com.foresightlabs.aether.data.calls

import android.app.Application
import android.util.Log
import com.foresightlabs.aether.BuildConfig
import com.foresightlabs.aether.data.calls.media.TgCallsMediaEngine
import com.foresightlabs.aether.data.permissions.PermissionCoordinator
import com.foresightlabs.aether.data.telegram.TelegramCallMessageMapper
import com.foresightlabs.aether.data.telegram.TelegramClient
import com.foresightlabs.aether.domain.calls.AudioRoute
import com.foresightlabs.aether.domain.calls.CallsRepository
import com.foresightlabs.aether.domain.calls.MediaConnectionState
import com.foresightlabs.aether.domain.calls.TelegramCallMediaEngine
import com.foresightlabs.aether.domain.model.ActiveCall
import com.foresightlabs.aether.domain.model.CallHistoryItem
import com.foresightlabs.aether.domain.model.CallHistoryUiState
import com.foresightlabs.aether.domain.model.CallStateEnum
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

/** Raised when a call is attempted with no media transport to carry its audio. */
class CallMediaUnavailableException(message: String) : Exception(message)

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

    private val _historyState = MutableStateFlow<CallHistoryUiState>(CallHistoryUiState.Loading)
    override val historyState: StateFlow<CallHistoryUiState> = _historyState.asStateFlow()

    private var timerJob: Job? = null
    private var lastTdLibState: CallStateEnum? = null
    private var lastMediaState: MediaConnectionState? = null

    init {
        scope.launch {
            telegram.activeCallState.collect { call ->
                handleTdLibStateChange(call)
            }
        }

        scope.launch {
            telegram.latestRawCallState.collect { rawCall ->
                handleRawCallUpdate(rawCall)
            }
        }

        scope.launch {
            mediaEngine.state.collect { mediaState ->
                handleMediaStateChange(mediaState)
            }
        }
    }

    private fun handleRawCallUpdate(rawCall: TdApi.Call?) {
        if (rawCall == null) return
        val readyState = rawCall.state as? TdApi.CallStateReady ?: return

        if (mediaEngine.state.value == MediaConnectionState.IDLE ||
            mediaEngine.state.value == MediaConnectionState.STOPPED
        ) {
            if (BuildConfig.DEBUG) {
                Log.d("CallsRepository", "CallStateReady received. Initializing TgCalls media engine for call ${rawCall.id}")
            }
            val callerName = activeCallState.value?.user?.name ?: "Telegram Contact"
            CallService.startService(application, callerName, isConnected = false)

            scope.launch {
                mediaEngine.start(rawCall, readyState)
            }
        }
    }

    private fun handleTdLibStateChange(call: ActiveCall?) {
        val currentState = call?.state
        if (currentState == CallStateEnum.DISCARDED || currentState == CallStateEnum.ERROR) {
            stopTimer()
            mediaEngine.stop()
            CallService.stopService(application)
            scope.launch {
                delay(1000)
                refreshHistory()
            }
        }
        lastTdLibState = currentState
    }

    private fun handleMediaStateChange(mediaState: MediaConnectionState) {
        if (BuildConfig.DEBUG) {
            Log.d("CallsRepository", "TgCalls MediaState: $mediaState")
        }

        val currentCall = activeCallState.value
        if (currentCall != null) {
            val callerName = currentCall.user?.name ?: "Telegram Contact"

            when (mediaState) {
                MediaConnectionState.CONNECTED -> {
                    CallService.startService(application, callerName, isConnected = true)
                    startTimer()
                }
                MediaConnectionState.FAILED -> {
                    stopTimer()
                    CallService.stopService(application)
                    mediaEngine.stop()
                }
                MediaConnectionState.UNAVAILABLE -> {
                    // Signalling succeeded but nothing can carry audio. Ending the
                    // call is the honest outcome: leaving it up would show a
                    // connected-looking call that is silent for both people.
                    stopTimer()
                    CallService.stopService(application)
                    telegram.reportCallMediaUnavailable(NO_MEDIA_TRANSPORT)
                    scope.launch { discardCall(currentCall.callId) }
                }
                MediaConnectionState.STOPPED -> {
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

    override suspend fun initiateCall(userId: Long): Result<Int> {
        if (!mediaEngine.isMediaTransportAvailable) {
            return Result.failure(CallMediaUnavailableException(NO_MEDIA_TRANSPORT))
        }
        return telegram.createVoiceCall(userId)
    }

    override suspend fun acceptCall(callId: Int): Result<Unit> {
        if (!mediaEngine.isMediaTransportAvailable) {
            telegram.discardCall(callId)
            return Result.failure(CallMediaUnavailableException(NO_MEDIA_TRANSPORT))
        }
        return telegram.acceptCall(callId)
    }

    override suspend fun discardCall(callId: Int): Result<Unit> {
        stopTimer()
        mediaEngine.stop()
        CallService.stopService(application)
        return telegram.discardCall(callId)
    }

    override fun toggleMute() {
        val newMute = !mediaEngine.isMuted.value
        mediaEngine.setMicrophoneMuted(newMute)
        telegram.toggleCallMute()
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
