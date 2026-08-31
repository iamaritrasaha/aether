package com.foresightlabs.aether.data.telegram

import android.app.Application
import android.os.Build
import androidx.core.content.edit
import com.foresightlabs.aether.BuildConfig
import com.foresightlabs.aether.domain.calls.MediaConnectionState
import com.foresightlabs.aether.domain.messages.MessageCapabilities
import com.foresightlabs.aether.domain.messages.MessageMotionEvent
import com.foresightlabs.aether.domain.messages.MessageMotionEventType
import com.foresightlabs.aether.domain.messages.ConversationMotion
import com.foresightlabs.aether.domain.messages.SendOptions
import com.foresightlabs.aether.domain.messages.SendSchedule
import com.foresightlabs.aether.domain.text.LinkPreviewCard
import com.foresightlabs.aether.domain.text.ReplyQuote
import com.foresightlabs.aether.domain.model.AnimationItem
import com.foresightlabs.aether.domain.model.AuthUiState
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ChatFolder
import com.foresightlabs.aether.domain.model.ForumTopicSummary
import com.foresightlabs.aether.domain.model.ConnectionStatus
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.MessageType
import com.foresightlabs.aether.domain.model.ReplyPreview
import com.foresightlabs.aether.domain.model.StickerItem
import com.foresightlabs.aether.domain.model.StickerSetInfo
import com.foresightlabs.aether.domain.model.User
import com.foresightlabs.aether.data.notifications.NotificationTiming
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.NativeLoader
import org.drinkless.tdlib.TdApi
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

class TelegramClient(private val application: Application) {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Default)
    private val mainHandler = Dispatchers.Main.immediate

    private var client: Client? = null

    private val chats = ConcurrentHashMap<Long, TdApi.Chat>()
    private val users = ConcurrentHashMap<Long, TdApi.User>()
    private val typing = ConcurrentHashMap<Long, String>()
    private val photoPaths = ConcurrentHashMap<String, String>()
    private val requestedFiles = ConcurrentHashMap<Int, Boolean>()
    /** File ids TDLib is currently, actively downloading -- tracked so a later
     * UpdateFile that is neither active nor completed can be told apart from
     * "never started" (nothing to report) versus "genuinely stopped without
     * finishing" (a real download failure worth a retry affordance). */
    private val activeDownloads = ConcurrentHashMap<Int, Boolean>()
    /** File ids whose download stopped without completing. Cleared on retry or
     * on a fresh completion; consulted so [resolveMediaPath] does not spin in
     * an automatic retry loop against a file TDLib has already given up on. */
    private val failedDownloads = ConcurrentHashMap<Int, Boolean>()
    private val mediaReferenceIndex = MediaReferenceIndex()
    private val chatAvatarFileByChat = ConcurrentHashMap<Long, Int>()
    private val chatsByAvatarFile = ConcurrentHashMap<Int, MutableSet<Long>>()
    private val userAvatarFileByUser = ConcurrentHashMap<Long, Int>()
    private val usersByAvatarFile = ConcurrentHashMap<Int, MutableSet<Long>>()
    private val activeStories = ConcurrentHashMap<Long, TdApi.ChatActiveStories>()
    private val storiesCache = ConcurrentHashMap<String, com.foresightlabs.aether.domain.model.StoryItem>()

    private val _authState = MutableStateFlow<AuthUiState>(AuthUiState.Initializing)
    val authState: StateFlow<AuthUiState> = _authState.asStateFlow()

    private val _connection = MutableStateFlow(ConnectionStatus.UNKNOWN)
    val connection: StateFlow<ConnectionStatus> = _connection.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _chatList = MutableStateFlow<List<Chat>>(emptyList())
    val chatList: StateFlow<List<Chat>> = _chatList.asStateFlow()

    private val conversationEvents = MutableSharedFlow<MessageMotionEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    private val conversationEventToken = AtomicLong(0L)

    fun messageEvents(chatId: Long): Flow<MessageMotionEvent> =
        conversationEvents.asSharedFlow().filter { it.chatId == chatId }

    private fun publishMessageEvent(chatId: Long, messageId: Long, type: MessageMotionEventType) {
        conversationEvents.tryEmit(
            MessageMotionEvent(chatId, messageId.toString(), type, conversationEventToken.incrementAndGet())
        )
    }

    private val _pulses = MutableStateFlow<List<com.foresightlabs.aether.domain.model.UserPulse>>(emptyList())
    val pulses: StateFlow<List<com.foresightlabs.aether.domain.model.UserPulse>> = _pulses.asStateFlow()

    private val _myPulse = MutableStateFlow<com.foresightlabs.aether.domain.model.UserPulse?>(null)
    val myPulse: StateFlow<com.foresightlabs.aether.domain.model.UserPulse?> = _myPulse.asStateFlow()

    private val _canPostPulse = MutableStateFlow(true)
    val canPostPulse: StateFlow<Boolean> = _canPostPulse.asStateFlow()

    private val _isLoadingChats = MutableStateFlow(false)
    val isLoadingChats: StateFlow<Boolean> = _isLoadingChats.asStateFlow()

    private val _activeCallState = MutableStateFlow<com.foresightlabs.aether.domain.model.ActiveCall?>(null)
    val activeCallState: StateFlow<com.foresightlabs.aether.domain.model.ActiveCall?> = _activeCallState.asStateFlow()

    val latestRawCallState = MutableStateFlow<TdApi.Call?>(null)

    var notificationManager: com.foresightlabs.aether.data.notifications.AetherNotificationManager? = null

    @Volatile private var myUserId: Long = 0L

    fun getMyUserId(): Long = myUserId
    @Volatile private var chatsFullyLoaded = false
    private val chatLoadMutex = Mutex()
    private var publishChatsJob: Job? = null
    private var publishPulsesJob: Job? = null
    private val notificationOptionsConfigured = AtomicBoolean(false)
    @Volatile private var desiredOnline: Boolean? = null
    @Volatile private var appliedOnline: Boolean? = null
    private var onlineWriteJob: Job? = null

    fun start() {
        if (!BuildConfig.HAS_TELEGRAM_CREDENTIALS) {
            _authState.value = AuthUiState.MissingCredentials
            // Readiness is resolved, not granted: nothing will ever apply
            // parameters, so anything waiting on them -- a push, above all --
            // must find that out now rather than sit out its whole timeout.
            parametersApplied.complete(Unit)
            return
        }
        if (client != null) return
        try {
            NativeLoader.load()
        } catch (error: UnsatisfiedLinkError) {
            // Aether ships arm64-v8a TDLib binaries only. On any other ABI the app
            // must say so plainly instead of crashing on launch with no explanation.
            if (BuildConfig.DEBUG) {
                android.util.Log.e(TAG, "TDLib native library unavailable", error)
            }
            _authState.value = AuthUiState.Unsupported(
                "Aether can't run on this device: the Telegram engine is built for " +
                    "64-bit ARM (arm64-v8a) and this device reports " +
                    "${Build.SUPPORTED_ABIS.joinToString().ifBlank { "an unsupported ABI" }}."
            )
            parametersApplied.complete(Unit)
            return
        }
        val verbosity = if (BuildConfig.DEBUG) 1 else 0
        try {
            Client.execute(TdApi.SetLogVerbosityLevel(verbosity))
        } catch (_: Client.ExecutionException) {
        }
        client = Client.create(
            { update -> dispatchUpdate(update) },
            { error -> if (BuildConfig.DEBUG) android.util.Log.w(TAG, "TDLib update handler error", error) },
            { error -> if (BuildConfig.DEBUG) android.util.Log.w(TAG, "TDLib handler error", error) }
        )
    }

    private val parametersApplied = CompletableDeferred<Unit>()

    /**
     * Notification updates are handled one at a time, in arrival order, and a
     * push can wait for that work to actually finish -- see
     * [NotificationWorkQueue][com.foresightlabs.aether.data.push.NotificationWorkQueue]
     * for why both properties matter. Every other update keeps its own
     * coroutine, as before.
     */
    private val notificationWork = com.foresightlabs.aether.data.push.NotificationWorkQueue(
        scope = scope,
        onError = { error ->
            if (BuildConfig.DEBUG) android.util.Log.w(TAG, "NOTIFICATION_RENDER_FAILED", error)
        }
    )

    private fun dispatchUpdate(update: TdApi.Object) {
        if (update is TdApi.UpdateNotificationGroup ||
            update is TdApi.UpdateNotification ||
            update is TdApi.UpdateActiveNotifications
        ) {
            notificationWork.submit { handleUpdate(update) }
        } else {
            scope.launch { handleUpdate(update) }
        }
    }

    /** Mirrors actual app visibility to TDLib without repeating equivalent writes. */
    @Synchronized
    fun setOnline(online: Boolean) {
        desiredOnline = online
        if (appliedOnline == online && onlineWriteJob?.isActive != true) return
        if (onlineWriteJob?.isActive == true) return
        onlineWriteJob = scope.launch {
            while (true) {
                val desired = desiredOnline ?: break
                if (appliedOnline == desired) break
                when (val result = send(TdApi.SetOption("online", TdApi.OptionValueBoolean(desired)))) {
                    is TdApi.Ok -> {
                        appliedOnline = desired
                        if (BuildConfig.DEBUG) {
                            android.util.Log.d(TAG, "TDLIB_ONLINE_OPTION applied=$desired")
                        }
                    }
                    is TdApi.Error -> {
                        if (BuildConfig.DEBUG) {
                            android.util.Log.w(TAG, "TDLIB_ONLINE_OPTION_ERROR code=${result.code}")
                        }
                        break
                    }
                    else -> break
                }
            }
        }
    }

    suspend fun submitPhoneNumber(phone: String): Result<Unit> {
        val settings = TdApi.PhoneNumberAuthenticationSettings(
            false,
            false,
            false,
            false,
            false,
            null,
            emptyArray()
        )
        return sendExpectOk(TdApi.SetAuthenticationPhoneNumber(phone, settings))
    }

    suspend fun submitCode(code: String): Result<Unit> {
        return sendExpectOk(TdApi.CheckAuthenticationCode(code))
    }

    suspend fun submitPassword(password: String): Result<Unit> {
        return sendExpectOk(TdApi.CheckAuthenticationPassword(password))
    }

    suspend fun submitPasswordRecoveryCode(code: String): Result<Unit> {
        return sendExpectOk(TdApi.CheckAuthenticationPasswordRecoveryCode(code))
    }

    suspend fun requestPasswordRecovery(): Result<Unit> {
        return sendExpectOk(TdApi.RequestAuthenticationPasswordRecovery())
    }

    suspend fun submitEmailAddress(email: String): Result<Unit> {
        return sendExpectOk(TdApi.SetAuthenticationEmailAddress(email))
    }

    suspend fun submitEmailCode(code: String): Result<Unit> {
        return sendExpectOk(
            TdApi.CheckAuthenticationEmailCode(TdApi.EmailAddressAuthenticationCode(code))
        )
    }

    suspend fun resetAuthenticationEmailAddress(): Result<Unit> {
        return sendExpectOk(TdApi.ResetAuthenticationEmailAddress())
    }

    suspend fun requestQrCodeAuthentication(): Result<Unit> {
        return sendExpectOk(TdApi.RequestQrCodeAuthentication(longArrayOf()))
    }

    suspend fun getAuthenticationPasskeyParameters(): Result<String> {
        return when (val result = send(TdApi.GetAuthenticationPasskeyParameters())) {
            is TdApi.Text -> Result.success(result.text)
            is TdApi.Error -> Result.failure(IllegalStateException(TdErrors.userMessage(result)))
            else -> Result.failure(IllegalStateException("A passkey sign-in request could not be prepared."))
        }
    }

    suspend fun submitPasskey(
        credentialId: String,
        clientData: String,
        authenticatorData: ByteArray,
        signature: ByteArray,
        userHandle: ByteArray
    ): Result<Unit> {
        return sendExpectOk(
            TdApi.CheckAuthenticationPasskey(
                credentialId,
                clientData,
                authenticatorData,
                signature,
                userHandle
            )
        )
    }

    suspend fun registerUser(firstName: String, lastName: String): Result<Unit> {
        return sendExpectOk(TdApi.RegisterUser(firstName, lastName, false))
    }

    suspend fun resendCode(): Result<Unit> {
        return sendExpectOk(TdApi.ResendAuthenticationCode(null))
    }

    suspend fun logOut(): Result<Unit> {
        notificationManager?.clearAllNotifications()
        _authState.value = AuthUiState.LoggingOut
        return sendExpectOk(TdApi.LogOut())
    }

    fun chat(chatId: Long): Chat? = _chatList.value.firstOrNull { it.id == chatId.toString() }
        ?: chats[chatId]?.let { mapUiChat(it) }

    suspend fun ensureChatLoaded(chatId: Long): Chat? {
        chats[chatId]?.let { return mapUiChat(it) }

        return when (val result = send(TdApi.GetChat(chatId))) {
            is TdApi.Chat -> {
                chats[result.id] = result
                requestChatPhoto(result)
                publishChats(immediate = true)
                mapUiChat(result)
            }
            else -> null
        }
    }

    suspend fun createPrivateChat(userId: Long): Result<Chat> {
        return when (val result = send(TdApi.CreatePrivateChat(userId, false))) {
            is TdApi.Chat -> {
                chats[result.id] = result
                requestChatPhoto(result)
                publishChats(immediate = true)
                Result.success(mapUiChat(result))
            }
            is TdApi.Error -> Result.failure(IllegalStateException(TdErrors.userMessage(result)))
            else -> Result.failure(IllegalStateException("Failed to open chat with user"))
        }
    }

    /**
     * History for one forum topic.
     *
     * A forum topic has its own history endpoint. Reading the chat's history instead
     * returns every topic's messages interleaved, which is what "flattening a forum"
     * looks like from the user's side.
     */
    suspend fun loadTopicHistory(
        chatId: Long,
        forumTopicId: Int,
        fromMessageId: Long,
        limit: Int = 40
    ): List<Message> {
        val result = send(
            TdApi.GetForumTopicHistory(chatId, forumTopicId, fromMessageId, 0, limit)
        )
        val messages = (result as? TdApi.Messages)?.messages ?: return emptyList()
        return messages.mapNotNull { td -> td?.let(::mapUiMessage) }.reversed()
    }

    /**
     * Result of a local-first history page request.
     *
     * [endOfHistory] is only meaningful when a network-capable request was actually
     * attempted (i.e. only when the caller allowed network AND local history ran
     * out) — TDLib may legitimately hand back fewer than the requested count while
     * more history still exists, so a short page never implies completeness on its
     * own. [oldestId] is the next pagination boundary regardless of how the page
     * was satisfied.
     */
    data class HistoryPage(
        val messages: List<Message>,
        val oldestId: Long,
        val usedNetwork: Boolean,
        val endOfHistory: Boolean
    )

    /**
     * One page of chat history, local-first.
     *
     * Two independent reasons a single call can't be trusted at face value:
     *
     * 1. TDLib's onlyLocal=true call can legitimately return fewer than [limit]
     *    messages even though its local database holds more — its own local
     *    pagination isn't guaranteed to fill a page in one shot.
     * 2. GetChatHistory with offset=0 starts *at* fromMessageId, not after it —
     *    the boundary message itself may legitimately reappear in the next
     *    round's batch. That is overlap, not a new/older message, and must not
     *    be counted as progress or it would silently re-add the same message
     *    forever. Aether tolerates the overlap by deduping on stable message id
     *    and explicitly excluding the requested boundary id from the "did this
     *    round make progress" count.
     *
     * So each round computes newUniqueCount (messages neither already collected
     * nor equal to the id we just requested from) and only advances the cursor
     * to the oldest of those. A round with newUniqueCount == 0 means the local
     * database has nothing further at this boundary — it stops immediately
     * rather than re-requesting the same boundary. Only once local rounds are
     * exhausted (by that condition, by hitting [limit], or by the defensive
     * round cap) and [allowNetwork] is true does this fall through to one
     * network-capable request to close the remaining gap — so an initial,
     * non-blocking render can pass allowNetwork=false and never wait on the
     * network merely to show what's already cached.
     */
    private suspend fun collectHistoryPage(
        chatId: Long,
        fromMessageId: Long,
        limit: Int,
        allowNetwork: Boolean,
        reason: String
    ): HistoryPage {
        val collected = LinkedHashMap<Long, TdApi.Message>()
        var boundary = fromMessageId
        var rounds = 0
        while (collected.size < limit && rounds < MAX_LOCAL_FILL_ROUNDS) {
            rounds++
            val requestBoundary = boundary
            val remaining = limit - collected.size
            val localResult = send(TdApi.GetChatHistory(chatId, requestBoundary, 0, remaining, true))
            val batch = (localResult as? TdApi.Messages)?.messages?.filterNotNull() ?: emptyList()
            val (newUnique, newOldest) = mergeBatch(collected, batch, requestBoundary)
            val boundaryAfter = if (newUnique > 0) newOldest else requestBoundary
            logHistoryRequest(chatId, "LOCAL", reason, requestBoundary, remaining, batch.size, newUnique, boundaryAfter, (localResult as? TdApi.Messages)?.totalCount ?: -1)
            // newUnique == 0 means the local database has nothing further at
            // this boundary (LOCAL CACHE EXHAUSTED AT THIS BOUNDARY) -- not
            // proof the server has no more history. Stop local rounds here
            // rather than re-requesting the same boundary.
            if (newUnique == 0) break
            boundary = newOldest
        }
        var usedNetwork = false
        var endOfHistory = false
        if (collected.size < limit && allowNetwork) {
            usedNetwork = true
            val requestBoundary = boundary
            val remaining = limit - collected.size
            val networkResult = send(TdApi.GetChatHistory(chatId, requestBoundary, 0, remaining, false))
            val batch = (networkResult as? TdApi.Messages)?.messages?.filterNotNull() ?: emptyList()
            val (newUnique, newOldest) = mergeBatch(collected, batch, requestBoundary)
            val boundaryAfter = if (newUnique > 0) newOldest else requestBoundary
            logHistoryRequest(chatId, "NETWORK_CAPABLE", reason, requestBoundary, remaining, batch.size, newUnique, boundaryAfter, (networkResult as? TdApi.Messages)?.totalCount ?: -1)
            if (newUnique == 0) {
                // Chosen client-side exhaustion rule, not a TDLib guarantee: a
                // network-capable request that adds no new unique older
                // message is treated as the practical end of this chat's
                // history for pagination purposes.
                endOfHistory = true
            } else {
                boundary = newOldest
            }
        }
        val messages = collected.values.mapNotNull(::mapUiMessage).reversed()
        return HistoryPage(messages, boundary, usedNetwork, endOfHistory)
    }

    /**
     * Merges [batch] into [collected] (keyed by stable message id), skipping the
     * message matching [requestBoundary] -- GetChatHistory's offset=0 starts
     * exactly at that id, so it may reappear without being a genuinely new
     * older message. Returns the count of messages this round actually added
     * and the oldest id among them (0L if none were added).
     */
    private fun mergeBatch(
        collected: LinkedHashMap<Long, TdApi.Message>,
        batch: List<TdApi.Message>,
        requestBoundary: Long
    ): Pair<Int, Long> {
        var newUnique = 0
        var newOldest = 0L
        for (m in batch) {
            if (m.id == requestBoundary) continue
            val isNew = !collected.containsKey(m.id)
            collected[m.id] = m
            if (isNew) {
                newUnique++
                if (newOldest == 0L || m.id < newOldest) newOldest = m.id
            }
        }
        return newUnique to newOldest
    }

    private fun logHistoryRequest(
        chatId: Long,
        requestType: String,
        reason: String,
        boundaryBefore: Long,
        requestedLimit: Int,
        returnedCount: Int,
        newUniqueCount: Int,
        boundaryAfter: Long,
        approxTotalCount: Int
    ) {
        if (!BuildConfig.DEBUG) return
        android.util.Log.d(
            TAG,
            "history chat=${chatId.hashCode()} type=$requestType reason=$reason " +
                "boundaryBefore=$boundaryBefore limit=$requestedLimit returned=$returnedCount " +
                "newUnique=$newUniqueCount boundaryAfter=$boundaryAfter approxTotal=$approxTotalCount"
        )
    }

    /**
     * Local-first page of chat history. See [collectHistoryPage] for the fill
     * algorithm; [allowNetwork] should be false for a non-blocking initial render
     * and true for user-driven pagination that is allowed to wait on the network.
     */
    suspend fun loadHistory(
        chatId: Long,
        fromMessageId: Long,
        limit: Int = 40,
        allowNetwork: Boolean = true,
        reason: String = "PAGINATION"
    ): HistoryPage = collectHistoryPage(chatId, fromMessageId, limit, allowNetwork, reason)

    suspend fun openChat(chatId: Long) {
        send(TdApi.OpenChat(chatId))
    }

    suspend fun closeChat(chatId: Long) {
        send(TdApi.CloseChat(chatId))
    }

    fun closeChatAsync(chatId: Long) {
        scope.launch { closeChat(chatId) }
    }

    suspend fun viewMessages(chatId: Long, messageIds: LongArray, forceRead: Boolean = true) {
        if (messageIds.isEmpty()) return
        send(TdApi.ViewMessages(chatId, messageIds, null, forceRead))
    }

    suspend fun getRawChat(chatId: Long): TdApi.Chat? {
        return chats[chatId] ?: when (val res = send(TdApi.GetChat(chatId))) {
            is TdApi.Chat -> {
                chats[res.id] = res
                res
            }
            else -> null
        }
    }

    suspend fun getRawUser(userId: Long): TdApi.User? {
        return users[userId] ?: when (val res = send(TdApi.GetUser(userId))) {
            is TdApi.User -> {
                users[res.id] = res
                res
            }
            else -> null
        }
    }

    suspend fun send(chatId: Long, text: String, replyToMessageId: Long? = null): Result<TdApi.Message> {
        return sendText(chatId, text, replyToMessageId)
    }

    suspend fun removeNotification(notificationGroupId: Int, notificationId: Int): Result<Unit> {
        return sendExpectOk(TdApi.RemoveNotification(notificationGroupId, notificationId))
    }

    suspend fun removeNotificationGroup(notificationGroupId: Int, maxNotificationId: Int): Result<Unit> {
        return sendExpectOk(TdApi.RemoveNotificationGroup(notificationGroupId, maxNotificationId))
    }

    suspend fun getMessageLink(chatId: Long, messageId: Long): Result<String> {
        return when (val res = send(TdApi.GetMessageLink(chatId, messageId, 0, 0, null, false, false))) {
            is TdApi.MessageLink -> Result.success(res.link)
            is TdApi.Error -> Result.failure(Exception(res.message))
            else -> Result.failure(Exception("Failed to get message link"))
        }
    }

    /**
     * Telegram's own preview for the first link in [text].
     *
     * `TdApi.GetLinkPreview` is the whole implementation: Telegram generates the
     * preview, Aether displays what comes back. No page is fetched, and no site
     * content is read here or anywhere else in this feature.
     *
     * Returns null whenever Telegram has nothing to show -- TDLib answers a text
     * with no previewable link with a 404 -- so the caller fails quietly rather
     * than reporting an error the user cannot act on.
     */
    suspend fun linkPreview(text: String): LinkPreviewCard? {
        if (text.isBlank()) return null
        val preview = send(
            TdApi.GetLinkPreview(TdApi.FormattedText(text, emptyArray()), null)
        ) as? TdApi.LinkPreview ?: return null
        val thumbnailId = LinkPreviewSupport.thumbnailFileId(preview)
        val thumbnailPath = if (thumbnailId != 0) awaitThumbnail(thumbnailId) else null
        return LinkPreviewSupport.cardOf(preview) { file ->
            if (file != null && file.id == thumbnailId) thumbnailPath else null
        }
    }

    /**
     * The bytes of one preview thumbnail.
     *
     * Synchronous because a preview thumbnail is a few kilobytes and the caller
     * is already waiting on the preview: the alternative is a card that pops a
     * picture in a moment after it appears. Telegram's embedded thumbnail covers
     * the case where these bytes never arrive.
     */
    private suspend fun awaitThumbnail(fileId: Int): String? {
        TelegramMappers.localPath(rawFile(fileId))?.let { return it }
        val file = send(TdApi.DownloadFile(fileId, THUMBNAIL_PRIORITY, 0, 0, true)) as? TdApi.File
            ?: return null
        return TelegramMappers.localPath(file)?.also { photoPaths["file:$fileId"] = it }
    }

    private fun rawFile(fileId: Int): TdApi.File? {
        val cached = photoPaths["file:$fileId"] ?: return null
        if (cached.isBlank()) return null
        return TdApi.File().apply {
            id = fileId
            local = TdApi.LocalFile().apply {
                path = cached
                isDownloadingCompleted = true
            }
        }
    }

    suspend fun sendText(
        chatId: Long,
        text: String,
        replyToMessageId: Long?,
        entities: Array<TdApi.TextEntity> = emptyArray(),
        forumTopicId: Int? = null,
        options: TdApi.MessageSendOptions? = null,
        quote: ReplyQuote? = null,
        // Null is TDLib's default handling, which is what every message without
        // a link -- and every link the user left alone -- still sends.
        linkPreviewOptions: TdApi.LinkPreviewOptions? = null
    ): Result<TdApi.Message> {
        val content = TdApi.InputMessageText(
            TdApi.FormattedText(text, entities),
            linkPreviewOptions,
            // Sending clears the draft this text came from.
            true
        )
        return sendContent(chatId, content, replyToMessageId, forumTopicId, options, quote)
    }

    suspend fun sendPhoto(
        chatId: Long,
        photoPath: String,
        caption: String,
        replyToMessageId: Long?,
        forumTopicId: Int? = null,
        // TdApi.InputMessagePhoto.selfDestructType, private chats only. Real
        // TDLib/Telegram view-once semantics -- MessageSelfDestructTypeImmediately
        // means "can be opened only once and will be self-destructed once closed" --
        // never a client-side imitation (no local delete/hide/timer).
        viewOnce: Boolean = false
    ): Result<TdApi.Message> {
        val content = MediaSendContent.photo(photoPath, caption, viewOnce)
        return sendContent(chatId, content, replyToMessageId, forumTopicId)
    }

    /**
     * Sends several photos as one Telegram album.
     *
     * A single [TdApi.SendMessageAlbum] rather than a burst of separate sends, so the
     * recipient sees one grouped cluster. Telegram captions the group from its first
     * member, which is why only that one carries [caption].
     */
    suspend fun sendPhotoAlbum(
        chatId: Long,
        photoPaths: List<String>,
        caption: String = "",
        replyToMessageId: Long? = null,
        forumTopicId: Int? = null
    ): Result<List<TdApi.Message>> {
        if (photoPaths.isEmpty()) return Result.success(emptyList())
        if (photoPaths.size == 1) {
            return sendPhoto(chatId, photoPaths.single(), caption, replyToMessageId, forumTopicId)
                .map { listOf(it) }
        }
        val contents: Array<TdApi.InputMessageContent> = photoPaths
            .take(ALBUM_LIMIT)
            .mapIndexed { index, path ->
                TdApi.InputMessagePhoto(
                    TdApi.InputFileLocal(path),
                    null,
                    null,
                    intArrayOf(),
                    0,
                    0,
                    TdApi.FormattedText(if (index == 0) caption else "", emptyArray()),
                    false,
                    null,
                    false
                ) as TdApi.InputMessageContent
            }
            .toTypedArray()

        return when (
            val result = send(
                TdApi.SendMessageAlbum(
                    chatId,
                    topicOf(forumTopicId),
                    replyTo(replyToMessageId),
                    null,
                    contents
                )
            )
        ) {
            is TdApi.Messages -> Result.success(result.messages?.filterNotNull().orEmpty())
            is TdApi.Error -> Result.failure(IllegalStateException(TdErrors.userMessage(result)))
            else -> Result.failure(IllegalStateException("Album could not be sent"))
        }
    }

    /**
     * Sends a contact card.
     *
     * The contact is whatever the caller passed — either typed by the user or picked
     * from the device address book after an explicit permission grant. Aether never
     * uploads the address book, and nothing is sent that the user did not choose.
     */
    suspend fun sendContact(
        chatId: Long,
        phoneNumber: String,
        firstName: String,
        lastName: String = "",
        replyToMessageId: Long? = null,
        forumTopicId: Int? = null
    ): Result<TdApi.Message> {
        val contact = TdApi.Contact(phoneNumber, firstName, lastName, "", 0L)
        return sendContent(
            chatId,
            TdApi.InputMessageContact(contact),
            replyToMessageId,
            forumTopicId
        )
    }

    /**
     * Sends a static point on the map.
     *
     * `livePeriod` is zero: this is a one-off location, not a live share. Live
     * location is a different feature with its own lifecycle, and sending a
     * zero-period message is the honest way to say "here, now".
     */
    suspend fun sendLocation(
        chatId: Long,
        latitude: Double,
        longitude: Double,
        accuracyMetres: Double = 0.0,
        replyToMessageId: Long? = null,
        forumTopicId: Int? = null
    ): Result<TdApi.Message> {
        val location = TdApi.Location(latitude, longitude, accuracyMetres)
        return sendContent(
            chatId,
            // A zero live period is Telegram's own way of saying "a point, now".
            TdApi.InputMessageLocation(location, 0, 0, 0),
            replyToMessageId,
            forumTopicId
        )
    }

    /**
     * The single path every outgoing message takes.
     *
     * Consolidated deliberately. When each send built its own `SendMessage` there
     * were six places that had to remember to pass the forum topic, and every one of
     * them passed null — which silently posted into a forum's root chat instead of
     * the topic the user was looking at.
     *
     * @param forumTopicId the forum topic to post into, or null for the chat itself
     */
    private suspend fun sendContent(
        chatId: Long,
        content: TdApi.InputMessageContent,
        replyToMessageId: Long?,
        forumTopicId: Int? = null,
        options: TdApi.MessageSendOptions? = null,
        quote: ReplyQuote? = null
    ): Result<TdApi.Message> {
        val function = TdApi.SendMessage(
            chatId,
            topicOf(forumTopicId),
            replyTo(replyToMessageId, quote),
            options,
            null,
            content
        )
        return when (val result = send(function)) {
            is TdApi.Message -> Result.success(result)
            is TdApi.Error -> Result.failure(IllegalStateException(TdErrors.userMessage(result)))
            else -> Result.failure(IllegalStateException("Message could not be sent"))
        }
    }

    /**
     * Translates Aether's delivery options into TDLib's.
     *
     * Returns null for the default, so the overwhelmingly common case sends exactly
     * the request it did before this existed.
     */
    fun sendOptionsOf(options: SendOptions): TdApi.MessageSendOptions? {
        if (options.isDefault) return null
        val scheduling: TdApi.MessageSchedulingState? = when (val schedule = options.schedule) {
            SendSchedule.Now -> null
            is SendSchedule.At -> TdApi.MessageSchedulingStateSendAtDate(schedule.epochSeconds, 0)
            SendSchedule.WhenOnline -> TdApi.MessageSchedulingStateSendWhenOnline()
        }
        return TdApi.MessageSendOptions().apply {
            disableNotification = options.silent
            schedulingState = scheduling
        }
    }

    /**
     * Wraps a forum topic id in the pinned [TdApi.MessageTopic] shape.
     *
     * Null means the chat itself, which is what every non-forum chat wants and what
     * TDLib expects there.
     */
    /**
     * Builds the reply, carrying a quote when the user selected one.
     *
     * A quote is a real TDLib structure with the quoted text *and its position in the
     * original*. Telegram needs the position to keep the quote attached when the
     * original is edited; a quote reconstructed from text alone would detach.
     */
    private fun replyTo(
        replyToMessageId: Long?,
        quote: ReplyQuote? = null
    ): TdApi.InputMessageReplyToMessage? {
        val messageId = replyToMessageId?.takeIf { it != 0L } ?: return null
        val inputQuote = quote?.let {
            TdApi.InputTextQuote(
                TdApi.FormattedText(it.text, TelegramMappers.toTdEntities(it.formatted)),
                it.position
            )
        }
        return TdApi.InputMessageReplyToMessage(messageId, inputQuote, 0, null)
    }

    private fun topicOf(forumTopicId: Int?): TdApi.MessageTopic? =
        forumTopicId?.takeIf { it != 0 }?.let { TdApi.MessageTopicForum(it) }

    suspend fun sendVideo(
        chatId: Long,
        videoPath: String,
        caption: String,
        duration: Int = 0,
        width: Int = 0,
        height: Int = 0,
        replyToMessageId: Long? = null,
        forumTopicId: Int? = null,
        // TdApi.InputMessageVideo.selfDestructType, private chats only -- same
        // real view-once semantics as sendPhoto's viewOnce param.
        viewOnce: Boolean = false
    ): Result<TdApi.Message> {
        val content = MediaSendContent.video(videoPath, caption, duration, width, height, viewOnce)
        return sendContent(chatId, content, replyToMessageId, forumTopicId)
    }

    suspend fun sendVoiceNote(
        chatId: Long,
        voicePath: String,
        duration: Int,
        waveform: ByteArray = ByteArray(0),
        replyToMessageId: Long? = null,
        forumTopicId: Int? = null
    ): Result<TdApi.Message> {
        val content = TdApi.InputMessageVoiceNote(
            TdApi.InputFileLocal(voicePath),
            duration,
            waveform,
            TdApi.FormattedText("", emptyArray()),
            null
        )
        return sendContent(chatId, content, replyToMessageId, forumTopicId)
    }

    suspend fun sendDocument(
        chatId: Long,
        docPath: String,
        caption: String = "",
        replyToMessageId: Long? = null,
        forumTopicId: Int? = null
    ): Result<TdApi.Message> {
        val content = TdApi.InputMessageDocument(
            TdApi.InputFileLocal(docPath),
            null,
            false,
            TdApi.FormattedText(caption, emptyArray())
        )
        return sendContent(chatId, content, replyToMessageId, forumTopicId)
    }

    suspend fun sendAnimation(
        chatId: Long,
        animationPath: String,
        caption: String = "",
        duration: Int = 0,
        width: Int = 0,
        height: Int = 0,
        replyToMessageId: Long? = null,
        forumTopicId: Int? = null
    ): Result<TdApi.Message> {
        val content = TdApi.InputMessageAnimation(
            TdApi.InputFileLocal(animationPath),
            null,
            intArrayOf(),
            duration,
            width,
            height,
            TdApi.FormattedText(caption, emptyArray()),
            false,
            false
        )
        return sendContent(chatId, content, replyToMessageId, forumTopicId)
    }

    suspend fun sendAnimationFile(
        chatId: Long,
        fileId: Int,
        caption: String = "",
        replyToMessageId: Long? = null,
        forumTopicId: Int? = null
    ): Result<TdApi.Message> {
        val content = TdApi.InputMessageAnimation(
            TdApi.InputFileId(fileId),
            null,
            intArrayOf(),
            0,
            0,
            0,
            TdApi.FormattedText(caption, emptyArray()),
            false,
            false
        )
        return sendContent(chatId, content, replyToMessageId, forumTopicId)
    }

    suspend fun sendSticker(
        chatId: Long,
        stickerPath: String,
        emoji: String = "",
        width: Int = 0,
        height: Int = 0,
        replyToMessageId: Long? = null,
        forumTopicId: Int? = null
    ): Result<TdApi.Message> {
        val content = TdApi.InputMessageSticker(
            TdApi.InputFileLocal(stickerPath),
            null,
            width,
            height,
            emoji
        )
        return sendContent(chatId, content, replyToMessageId, forumTopicId)
    }

    suspend fun sendStickerFile(
        chatId: Long,
        fileId: Int,
        emoji: String = "",
        replyToMessageId: Long? = null,
        forumTopicId: Int? = null
    ): Result<TdApi.Message> {
        val content = TdApi.InputMessageSticker(
            TdApi.InputFileId(fileId),
            null,
            0,
            0,
            emoji
        )
        return sendContent(chatId, content, replyToMessageId, forumTopicId)
    }

    suspend fun sendVideoNote(
        chatId: Long,
        videoPath: String,
        duration: Int = 0,
        length: Int = 240,
        replyToMessageId: Long? = null,
        forumTopicId: Int? = null
    ): Result<TdApi.Message> {
        val content = TdApi.InputMessageVideoNote(
            TdApi.InputFileLocal(videoPath),
            null,
            duration,
            length,
            null
        )
        return sendContent(chatId, content, replyToMessageId, forumTopicId)
    }

    suspend fun sendLiveLocation(
        chatId: Long,
        latitude: Double,
        longitude: Double,
        livePeriod: Int,
        heading: Int = 0,
        replyToMessageId: Long? = null,
        forumTopicId: Int? = null
    ): Result<TdApi.Message> {
        val location = TdApi.Location(latitude, longitude, 0.0)
        val content = TdApi.InputMessageLocation(location, livePeriod, heading, 0)
        return sendContent(chatId, content, replyToMessageId, forumTopicId)
    }

    suspend fun editLiveLocation(
        chatId: Long,
        messageId: Long,
        latitude: Double,
        longitude: Double,
        livePeriod: Int = 0,
        heading: Int = 0
    ): Result<TdApi.Message> {
        val function = TdApi.EditMessageLiveLocation(
            chatId,
            messageId,
            null,
            TdApi.Location(latitude, longitude, 0.0),
            livePeriod,
            heading,
            0
        )
        return when (val result = send(function)) {
            is TdApi.Message -> Result.success(result)
            is TdApi.Error -> Result.failure(IllegalStateException(TdErrors.userMessage(result)))
            else -> Result.failure(IllegalStateException("Failed to edit live location"))
        }
    }

    suspend fun stopLiveLocation(chatId: Long, messageId: Long): Result<TdApi.Message> {
        val function = TdApi.EditMessageLiveLocation(
            chatId,
            messageId,
            null,
            null,
            0,
            0,
            0
        )
        return when (val result = send(function)) {
            is TdApi.Message -> Result.success(result)
            is TdApi.Error -> Result.failure(IllegalStateException(TdErrors.userMessage(result)))
            else -> Result.failure(IllegalStateException("Failed to stop live location"))
        }
    }

    suspend fun sendVenue(
        chatId: Long,
        latitude: Double,
        longitude: Double,
        title: String,
        address: String,
        replyToMessageId: Long? = null,
        forumTopicId: Int? = null
    ): Result<TdApi.Message> {
        val venue = TdApi.Venue(
            TdApi.Location(latitude, longitude, 0.0),
            title,
            address,
            "",
            "",
            ""
        )
        return sendContent(chatId, TdApi.InputMessageVenue(venue), replyToMessageId, forumTopicId)
    }

    suspend fun replaceMedia(
        chatId: Long,
        messageId: Long,
        mediaPath: String,
        type: MessageType,
        caption: String = ""
    ): Result<TdApi.Message> {
        val inputContent: TdApi.InputMessageContent = when (type) {
            MessageType.IMAGE -> TdApi.InputMessagePhoto(
                TdApi.InputFileLocal(mediaPath),
                null, null, intArrayOf(), 0, 0,
                TdApi.FormattedText(caption, emptyArray()),
                false, null, false
            )
            MessageType.VIDEO -> MediaSendContent.video(
                mediaPath, caption, duration = 0, width = 0, height = 0, viewOnce = false
            )
            MessageType.VIDEO_NOTE -> TdApi.InputMessageVideoNote(
                TdApi.InputFileLocal(mediaPath),
                null, 0, 240, null
            )
            MessageType.ANIMATION -> TdApi.InputMessageAnimation(
                TdApi.InputFileLocal(mediaPath),
                null, intArrayOf(), 0, 0, 0,
                TdApi.FormattedText(caption, emptyArray()),
                false, false
            )
            MessageType.AUDIO -> TdApi.InputMessageAudio(
                TdApi.InputFileLocal(mediaPath),
                null, 0, "", "",
                TdApi.FormattedText(caption, emptyArray())
            )
            else -> TdApi.InputMessageDocument(
                TdApi.InputFileLocal(mediaPath),
                null, false,
                TdApi.FormattedText(caption, emptyArray())
            )
        }
        val function = TdApi.EditMessageMedia(chatId, messageId, null, inputContent)
        return when (val result = send(function)) {
            is TdApi.Message -> Result.success(result)
            is TdApi.Error -> Result.failure(IllegalStateException(TdErrors.userMessage(result)))
            else -> Result.failure(IllegalStateException("Failed to replace media"))
        }
    }

    suspend fun getInstalledStickerSets(): Result<List<StickerSetInfo>> {
        val result = send(TdApi.GetInstalledStickerSets(TdApi.StickerTypeRegular()))
        return when (result) {
            is TdApi.StickerSets -> {
                val sets = result.sets.orEmpty().filterNotNull().map { info ->
                    StickerSetInfo(
                        id = info.id,
                        title = info.title.orEmpty(),
                        name = info.name.orEmpty(),
                        thumbnailPath = info.thumbnail?.file?.local?.path
                    )
                }
                Result.success(sets)
            }
            is TdApi.Error -> Result.failure(IllegalStateException(TdErrors.userMessage(result)))
            else -> Result.failure(IllegalStateException("Failed to get installed sticker sets"))
        }
    }

    suspend fun getStickerSet(setId: Long): Result<StickerSetInfo> {
        val result = send(TdApi.GetStickerSet(setId))
        return when (result) {
            is TdApi.StickerSet -> {
                val items = result.stickers.orEmpty().filterNotNull().map { mapStickerItem(it) }
                Result.success(
                    StickerSetInfo(
                        id = result.id,
                        title = result.title.orEmpty(),
                        name = result.name.orEmpty(),
                        thumbnailPath = result.thumbnail?.file?.local?.path,
                        stickers = items
                    )
                )
            }
            is TdApi.Error -> Result.failure(IllegalStateException(TdErrors.userMessage(result)))
            else -> Result.failure(IllegalStateException("Failed to get sticker set"))
        }
    }

    suspend fun getRecentStickers(): Result<List<StickerItem>> {
        val result = send(TdApi.GetRecentStickers(false))
        return when (result) {
            is TdApi.Stickers -> {
                val items = result.stickers.orEmpty().filterNotNull().map { mapStickerItem(it) }
                Result.success(items)
            }
            is TdApi.Error -> Result.failure(IllegalStateException(TdErrors.userMessage(result)))
            else -> Result.failure(IllegalStateException("Failed to get recent stickers"))
        }
    }

    suspend fun getFavoriteStickers(): Result<List<StickerItem>> {
        val result = send(TdApi.GetFavoriteStickers())
        return when (result) {
            is TdApi.Stickers -> {
                val items = result.stickers.orEmpty().filterNotNull().map { mapStickerItem(it) }
                Result.success(items)
            }
            is TdApi.Error -> Result.failure(IllegalStateException(TdErrors.userMessage(result)))
            else -> Result.failure(IllegalStateException("Failed to get favorite stickers"))
        }
    }

    suspend fun searchStickerSets(query: String): Result<List<StickerSetInfo>> {
        val result = send(TdApi.SearchStickerSets(TdApi.StickerTypeRegular(), query))
        return when (result) {
            is TdApi.StickerSets -> {
                val sets = result.sets.orEmpty().filterNotNull().map { info ->
                    StickerSetInfo(
                        id = info.id,
                        title = info.title.orEmpty(),
                        name = info.name.orEmpty()
                    )
                }
                Result.success(sets)
            }
            is TdApi.Error -> Result.failure(IllegalStateException(TdErrors.userMessage(result)))
            else -> Result.failure(IllegalStateException("Failed to search sticker sets"))
        }
    }

    private fun mapStickerItem(sticker: TdApi.Sticker): StickerItem {
        val file = sticker.sticker
        if (file != null && !file.local.isDownloadingCompleted && file.local.canBeDownloaded) {
            scope.launch { downloadFile(file.id) }
        }
        return StickerItem(
            fileId = file?.id ?: 0,
            emoji = sticker.emoji.orEmpty(),
            width = sticker.width,
            height = sticker.height,
            isAnimated = sticker.format is TdApi.StickerFormatTgs,
            isVideo = sticker.format is TdApi.StickerFormatWebm,
            localPath = file?.local?.path?.takeIf { it.isNotBlank() },
            setId = sticker.setId
        )
    }

    suspend fun getSavedAnimations(): Result<List<AnimationItem>> {
        val result = send(TdApi.GetSavedAnimations())
        return when (result) {
            is TdApi.Animations -> {
                val items = result.animations.orEmpty().filterNotNull().map { mapAnimationItem(it) }
                Result.success(items)
            }
            is TdApi.Error -> Result.failure(IllegalStateException(TdErrors.userMessage(result)))
            else -> Result.failure(IllegalStateException("Failed to get saved animations"))
        }
    }

    private fun mapAnimationItem(anim: TdApi.Animation): AnimationItem {
        val file = anim.animation
        if (file != null && !file.local.isDownloadingCompleted && file.local.canBeDownloaded) {
            scope.launch { downloadFile(file.id) }
        }
        val thumbFile = anim.thumbnail?.file
        if (thumbFile != null && !thumbFile.local.isDownloadingCompleted && thumbFile.local.canBeDownloaded) {
            scope.launch { downloadFile(thumbFile.id) }
        }
        return AnimationItem(
            fileId = file?.id ?: 0,
            width = anim.width,
            height = anim.height,
            duration = anim.duration,
            fileName = anim.fileName.orEmpty(),
            mimeType = anim.mimeType.orEmpty().ifBlank { "video/mp4" },
            thumbnailPath = thumbFile?.local?.path?.takeIf { it.isNotBlank() },
            localPath = file?.local?.path?.takeIf { it.isNotBlank() }
        )
    }

    suspend fun createChatFolder(
        title: String,
        includedChatIds: LongArray = LongArray(0),
        excludedChatIds: LongArray = LongArray(0)
    ): Result<TdApi.ChatFolderInfo> {
        val folder = TdApi.ChatFolder(
            TdApi.ChatFolderName(TdApi.FormattedText(title, emptyArray<TdApi.TextEntity>()), false),
            null,
            -1,
            false,
            LongArray(0),
            includedChatIds,
            excludedChatIds,
            false, false, false, false, false, false, false, false
        )
        return when (val result = send(TdApi.CreateChatFolder(folder))) {
            is TdApi.ChatFolderInfo -> Result.success(result)
            is TdApi.Error -> Result.failure(IllegalStateException(TdErrors.userMessage(result)))
            else -> Result.failure(IllegalStateException("Failed to create chat folder"))
        }
    }

    suspend fun editChatFolder(
        folderId: Int,
        title: String,
        includedChatIds: LongArray = LongArray(0),
        excludedChatIds: LongArray = LongArray(0)
    ): Result<TdApi.ChatFolderInfo> {
        val folder = TdApi.ChatFolder(
            TdApi.ChatFolderName(TdApi.FormattedText(title, emptyArray<TdApi.TextEntity>()), false),
            null,
            -1,
            false,
            LongArray(0),
            includedChatIds,
            excludedChatIds,
            false, false, false, false, false, false, false, false
        )
        return when (val result = send(TdApi.EditChatFolder(folderId, folder))) {
            is TdApi.ChatFolderInfo -> Result.success(result)
            is TdApi.Error -> Result.failure(IllegalStateException(TdErrors.userMessage(result)))
            else -> Result.failure(IllegalStateException("Failed to edit chat folder"))
        }
    }

    suspend fun deleteChatFolder(folderId: Int): Result<Unit> {
        return sendExpectOk(TdApi.DeleteChatFolder(folderId, LongArray(0)))
    }

    suspend fun reorderChatFolders(folderIds: IntArray): Result<Unit> {
        return sendExpectOk(TdApi.ReorderChatFolders(folderIds, 0))
    }

    suspend fun editMessage(
        chatId: Long,
        messageId: Long,
        newText: String,
        linkPreviewOptions: TdApi.LinkPreviewOptions? = null
    ): Result<TdApi.Message> {
        val raw = rawMessages[messageId]
        val function = when (raw?.content) {
            is TdApi.MessagePhoto,
            is TdApi.MessageVideo,
            is TdApi.MessageAnimation,
            is TdApi.MessageDocument,
            is TdApi.MessageAudio,
            is TdApi.MessageVoiceNote -> {
                TdApi.EditMessageCaption(
                    chatId,
                    messageId,
                    null,
                    TdApi.FormattedText(newText, emptyArray()),
                    false
                )
            }
            else -> {
                val content = TdApi.InputMessageText(
                    TdApi.FormattedText(newText, emptyArray()),
                    linkPreviewOptions,
                    true
                )
                TdApi.EditMessageText(chatId, messageId, null, content)
            }
        }
        return when (val result = send(function)) {
            is TdApi.Message -> Result.success(result)
            is TdApi.Error -> Result.failure(IllegalStateException(TdErrors.userMessage(result)))
            else -> Result.failure(IllegalStateException("Unexpected edit message result"))
        }
    }

    /**
     * Toggles this account's reaction.
     *
     * Telegram has no single toggle, so the direction is decided from the state the
     * server last reported — never from an optimistic local flag, which is how a
     * double-tap ends up adding a reaction the user meant to remove.
     */
    suspend fun toggleReaction(
        chatId: Long,
        messageId: Long,
        emoji: String,
        isCurrentlyChosen: Boolean
    ): Result<Unit> {
        return if (isCurrentlyChosen) {
            removeReaction(chatId, messageId, emoji)
        } else {
            addReaction(chatId, messageId, emoji)
        }
    }

    suspend fun removeReaction(chatId: Long, messageId: Long, emoji: String): Result<Unit> {
        val reaction = TdApi.ReactionTypeEmoji(emoji)
        return sendExpectOk(TdApi.RemoveMessageReaction(chatId, messageId, reaction))
    }

    suspend fun addReaction(chatId: Long, messageId: Long, emoji: String): Result<Unit> {
        val reaction = TdApi.ReactionTypeEmoji(emoji)
        return sendExpectOk(TdApi.AddMessageReaction(chatId, messageId, reaction, false, true))
    }

    suspend fun pinMessage(chatId: Long, messageId: Long, onlyForSelf: Boolean = false): Result<Unit> {
        return sendExpectOk(TdApi.PinChatMessage(chatId, messageId, false, onlyForSelf))
    }

    suspend fun unpinMessage(chatId: Long, messageId: Long): Result<Unit> {
        return sendExpectOk(TdApi.UnpinChatMessage(chatId, messageId))
    }

    suspend fun unpinAllMessages(chatId: Long): Result<Unit> {
        return sendExpectOk(TdApi.UnpinAllChatMessages(chatId))
    }

    suspend fun createVoiceCall(userId: Long): Result<Int> {
        val protocol = TdApi.CallProtocol(true, true, 65, 92, arrayOf("1.0.0"))
        return when (val result = send(TdApi.CreateCall(userId, protocol, false))) {
            is TdApi.CallId -> {
                val targetUser = getUser(userId)
                _activeCallState.value = com.foresightlabs.aether.domain.model.ActiveCall(
                    callId = result.id,
                    userId = userId,
                    user = targetUser,
                    isOutgoing = true,
                    isVideo = false,
                    state = com.foresightlabs.aether.domain.model.CallStateEnum.PENDING,
                    isMuted = false,
                    isSpeakerOn = false,
                    isMinimized = false
                )
                Result.success(result.id)
            }
            is TdApi.Error -> Result.failure(IllegalStateException(TdErrors.userMessage(result)))
            else -> Result.failure(IllegalStateException("Failed to initiate voice call"))
        }
    }

    suspend fun acceptCall(callId: Int): Result<Unit> {
        val protocol = TdApi.CallProtocol(true, true, 65, 92, arrayOf("1.0.0"))
        return sendExpectOk(TdApi.AcceptCall(callId, protocol))
    }

    suspend fun discardCall(callId: Int): Result<Unit> {
        return sendExpectOk(TdApi.DiscardCall(callId, false, "", 0, false, 0))
    }

    fun toggleCallMute() {
        val current = _activeCallState.value ?: return
        val updatedMute = !current.isMuted
        _activeCallState.value = current.copy(isMuted = updatedMute)
        updateAudioHardware(isMuted = updatedMute, isSpeakerOn = current.isSpeakerOn)
    }

    fun toggleCallSpeaker() {
        val current = _activeCallState.value ?: return
        val updatedSpeaker = !current.isSpeakerOn
        _activeCallState.value = current.copy(isSpeakerOn = updatedSpeaker)
        updateAudioHardware(isMuted = current.isMuted, isSpeakerOn = updatedSpeaker)
    }

    fun setCallMinimized(minimized: Boolean) {
        val current = _activeCallState.value ?: return
        _activeCallState.value = current.copy(isMinimized = minimized)
    }

    /**
     * Records that the call could not carry audio, so the call screen can say so
     * instead of showing a connected call the user cannot hear.
     */
    fun reportCallMediaUnavailable(reason: String) {
        val current = _activeCallState.value ?: return
        _activeCallState.value = current.copy(
            mediaState = MediaConnectionState.UNAVAILABLE,
            errorMessage = reason
        )
    }

    fun updateCallDuration(durationSec: Int) {
        val current = _activeCallState.value ?: return
        _activeCallState.value = current.copy(durationSec = durationSec)
    }

    suspend fun getUser(userId: Long): User? {
        val cached = users[userId]
        if (cached != null) return TelegramMappers.mapUser(cached)
        return when (val result = send(TdApi.GetUser(userId))) {
            is TdApi.User -> {
                users[userId] = result
                TelegramMappers.mapUser(result)
            }
            else -> null
        }
    }

    suspend fun searchCallMessages(offset: String = "", limit: Int = 50, onlyMissed: Boolean = false): Result<TdApi.FoundMessages> {
        return when (val result = send(TdApi.SearchCallMessages(offset, limit, onlyMissed))) {
            is TdApi.FoundMessages -> Result.success(result)
            is TdApi.Error -> Result.failure(IllegalStateException(TdErrors.userMessage(result)))
            else -> Result.failure(IllegalStateException("Failed to search call messages"))
        }
    }

    private fun handleCallUpdate(call: TdApi.Call) {
        latestRawCallState.value = call
        val (stateEnum, errorMsg) = when (val state = call.state) {
            is TdApi.CallStatePending -> com.foresightlabs.aether.domain.model.CallStateEnum.PENDING to null
            is TdApi.CallStateExchangingKeys -> com.foresightlabs.aether.domain.model.CallStateEnum.EXCHANGING_KEYS to null
            is TdApi.CallStateReady -> com.foresightlabs.aether.domain.model.CallStateEnum.READY to null
            is TdApi.CallStateHangingUp -> com.foresightlabs.aether.domain.model.CallStateEnum.HANGING_UP to null
            is TdApi.CallStateDiscarded -> com.foresightlabs.aether.domain.model.CallStateEnum.DISCARDED to null
            is TdApi.CallStateError -> com.foresightlabs.aether.domain.model.CallStateEnum.ERROR to TdErrors.userMessage(state.error)
            else -> com.foresightlabs.aether.domain.model.CallStateEnum.PENDING to null
        }

        if (stateEnum == com.foresightlabs.aether.domain.model.CallStateEnum.READY) {
            updateAudioHardware(
                isMuted = _activeCallState.value?.isMuted ?: false,
                isSpeakerOn = _activeCallState.value?.isSpeakerOn ?: false
            )
        } else if (stateEnum == com.foresightlabs.aether.domain.model.CallStateEnum.DISCARDED ||
            stateEnum == com.foresightlabs.aether.domain.model.CallStateEnum.ERROR
        ) {
            resetAudioHardware()
        }

        val currentCall = _activeCallState.value
        val cachedUser = users[call.userId]?.let { TelegramMappers.mapUser(it) }

        val updated = com.foresightlabs.aether.domain.model.ActiveCall(
            callId = call.id,
            userId = call.userId,
            user = cachedUser ?: currentCall?.user,
            isOutgoing = call.isOutgoing,
            isVideo = call.isVideo,
            state = stateEnum,
            isMuted = currentCall?.isMuted ?: false,
            isSpeakerOn = currentCall?.isSpeakerOn ?: false,
            durationSec = if (stateEnum == com.foresightlabs.aether.domain.model.CallStateEnum.READY) (currentCall?.durationSec ?: 0) else 0,
            isMinimized = currentCall?.isMinimized ?: false,
            errorMessage = errorMsg
        )
        _activeCallState.value = updated

        if (cachedUser == null && currentCall?.user == null) {
            scope.launch {
                val fetchedUser = getUser(call.userId)
                if (fetchedUser != null && _activeCallState.value?.callId == call.id) {
                    _activeCallState.value = _activeCallState.value?.copy(user = fetchedUser)
                }
            }
        }

        if (stateEnum == com.foresightlabs.aether.domain.model.CallStateEnum.DISCARDED ||
            stateEnum == com.foresightlabs.aether.domain.model.CallStateEnum.ERROR
        ) {
            scope.launch {
                delay(2000)
                if (_activeCallState.value?.callId == call.id) {
                    _activeCallState.value = null
                }
            }
        }
    }

    private fun resetAudioHardware() {
        try {
            val audioManager = application.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
            audioManager?.mode = android.media.AudioManager.MODE_NORMAL
            audioManager?.isMicrophoneMute = false
            audioManager?.isSpeakerphoneOn = false
        } catch (_: Exception) {}
    }

    private fun updateAudioHardware(isMuted: Boolean, isSpeakerOn: Boolean) {
        try {
            val audioManager = application.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
            audioManager?.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
            audioManager?.isMicrophoneMute = isMuted
            audioManager?.isSpeakerphoneOn = isSpeakerOn
        } catch (_: Exception) {}
    }



    /**
     * Forwards one or more messages.
     *
     * [sendCopy] forwards without attribution — the message appears as though the
     * forwarder wrote it. [removeCaption] drops media captions and is only meaningful
     * alongside a copy, since an attributed forward keeps the original intact.
     *
     * Both are real TDLib options rather than client-side rewriting: Aether never
     * re-sends content as a new message to imitate a forward.
     */
    suspend fun forwardMessages(
        toChatId: Long,
        fromChatId: Long,
        messageIds: LongArray,
        sendCopy: Boolean = false,
        removeCaption: Boolean = false,
        toForumTopicId: Int? = null
    ): Result<Unit> {
        val function = TdApi.ForwardMessages(
            toChatId,
            topicOf(toForumTopicId),
            fromChatId,
            messageIds,
            null,
            sendCopy,
            removeCaption && sendCopy
        )
        return when (val result = send(function)) {
            is TdApi.Messages -> Result.success(Unit)
            is TdApi.Error -> Result.failure(IllegalStateException(TdErrors.userMessage(result)))
            else -> Result.failure(IllegalStateException("Messages could not be forwarded"))
        }
    }

    // --- chat folders -----------------------------------------------------------

    /**
     * The most recent unacknowledged service notification from Telegram, or null.
     * Cleared by [acknowledgeServiceNotice] once the user has seen it.
     */
    private val _serviceNotice = MutableStateFlow<com.foresightlabs.aether.domain.messaging.ServiceNotice?>(null)
    val serviceNotice: kotlinx.coroutines.flow.StateFlow<com.foresightlabs.aether.domain.messaging.ServiceNotice?> = _serviceNotice

    fun acknowledgeServiceNotice() {
        _serviceNotice.value = null
    }

    private val _chatFolders = MutableStateFlow<List<ChatFolder>>(listOf(ChatFolder.Main))

    /**
     * The account's Telegram folders, with the main list in its server-defined
     * position among them.
     */
    val chatFolders: StateFlow<List<ChatFolder>> = _chatFolders.asStateFlow()

    /**
     * The chats belonging to [folder], in that folder's own order.
     *
     * Ordering comes from the folder's [TdApi.ChatPosition], not from re-sorting the
     * main list, because a folder's order is server state other clients agree on.
     */
    fun chatsInFolder(folder: ChatFolder): List<Chat> {
        if (folder.isMainList) return _chatList.value
        return chats.values
            .mapNotNull { raw ->
                val position = ChatOrdering.folderPosition(raw.positions, folder.id)
                    ?: return@mapNotNull null
                mapUiChat(raw).copy(
                    order = position.order,
                    isPinned = position.isPinned
                )
            }
            .sortedWith(
                compareByDescending<Chat> { it.isPinned }
                    .thenByDescending { it.order }
            )
    }

    // --- forum topics -----------------------------------------------------------

    /** Whether this chat is a forum supergroup, per Telegram. */
    fun isForum(chatId: Long): Boolean {
        val type = chats[chatId]?.type as? TdApi.ChatTypeSupergroup ?: return false
        return supergroups[type.supergroupId]?.isForum == true
    }

    /** The topics of a forum supergroup, in Telegram's own order. */
    suspend fun forumTopics(chatId: Long, limit: Int = 50): List<ForumTopicSummary> {
        val result = send(TdApi.GetForumTopics(chatId, "", 0, 0L, 0, limit))
        val topics = (result as? TdApi.ForumTopics)?.topics ?: return emptyList()
        return topics.filterNotNull().map { topic -> mapForumTopic(chatId, topic) }
    }

    private fun mapForumTopic(chatId: Long, topic: TdApi.ForumTopic): ForumTopicSummary {
        val info = topic.info
        return ForumTopicSummary(
            chatId = chatId,
            topicId = info?.forumTopicId ?: 0,
            name = info?.name.orEmpty().ifBlank { "General" },
            isGeneral = info?.isGeneral == true,
            isClosed = info?.isClosed == true,
            isHidden = info?.isHidden == true,
            isPinned = topic.isPinned,
            unreadCount = topic.unreadCount,
            unreadMentionCount = topic.unreadMentionCount,
            order = topic.order,
            lastMessagePreview = topic.lastMessage
                ?.let { TelegramMappers.mapContent(it.content).first }
                .orEmpty(),
            draftText = TelegramMappers.draftText(topic.draftMessage),
            isMuted = topic.notificationSettings?.let {
                !it.useDefaultMuteFor && it.muteFor > 0
            } == true
        )
    }

    suspend fun createForumTopic(chatId: Long, name: String): Result<Int> {
        return when (val result = send(TdApi.CreateForumTopic(chatId, name, false, null))) {
            is TdApi.ForumTopicInfo -> Result.success(result.forumTopicId)
            is TdApi.Error -> Result.failure(IllegalStateException(TdErrors.userMessage(result)))
            else -> Result.failure(IllegalStateException("Topic could not be created"))
        }
    }

    suspend fun renameForumTopic(chatId: Long, topicId: Int, name: String): Result<Unit> =
        sendExpectOk(TdApi.EditForumTopic(chatId, topicId, name, false, 0L))

    suspend fun setForumTopicClosed(chatId: Long, topicId: Int, closed: Boolean): Result<Unit> =
        sendExpectOk(TdApi.ToggleForumTopicIsClosed(chatId, topicId, closed))

    suspend fun setForumTopicPinned(chatId: Long, topicId: Int, pinned: Boolean): Result<Unit> =
        sendExpectOk(TdApi.ToggleForumTopicIsPinned(chatId, topicId, pinned))

    suspend fun deleteForumTopic(chatId: Long, topicId: Int): Result<Unit> =
        sendExpectOk(TdApi.DeleteForumTopic(chatId, topicId))

    suspend fun getContacts(): List<User> {
        return when (val result = send(TdApi.GetContacts())) {
            is TdApi.Users -> {
                val list = mutableListOf<User>()
                for (id in result.userIds) {
                    val tdUser = users[id] ?: (send(TdApi.GetUser(id)) as? TdApi.User)?.also { users[id] = it }
                    if (tdUser != null) {
                        list.add(TelegramMappers.mapUser(tdUser))
                    }
                }
                list
            }
            else -> emptyList()
        }
    }

    /**
     * Contacts matching [query], presented as conversations so they can be opened
     * directly. A contact with no chat yet still resolves — a private chat is created
     * on open, which is what tapping the row means.
     */
    suspend fun searchContactChats(query: String, limit: Int = 30): List<Chat> {
        return searchContacts(query, limit).mapNotNull { user ->
            val userId = user.id.toLongOrNull() ?: return@mapNotNull null
            chat(userId) ?: chats[userId]?.let { mapUiChat(it) } ?: TelegramMappers.chatForUser(user)
        }
    }

    suspend fun searchContacts(query: String, limit: Int = 50): List<User> {
        return when (val result = send(TdApi.SearchContacts(query, limit))) {
            is TdApi.Users -> {
                val list = mutableListOf<User>()
                for (id in result.userIds) {
                    val tdUser = users[id] ?: (send(TdApi.GetUser(id)) as? TdApi.User)?.also { users[id] = it }
                    if (tdUser != null) {
                        list.add(TelegramMappers.mapUser(tdUser))
                    }
                }
                list
            }
            else -> emptyList()
        }
    }

    suspend fun importContacts(contactsList: List<TdApi.ImportedContact>): List<Long> {
        return when (val result = send(TdApi.ImportContacts(contactsList.toTypedArray()))) {
            is TdApi.ImportedContacts -> result.userIds.toList()
            else -> emptyList()
        }
    }

    suspend fun retrySend(chatId: Long, messageId: Long): Result<Unit> {
        return sendExpectOk(TdApi.ResendMessages(chatId, longArrayOf(messageId), null, 0))
    }

    /**
     * Deletes messages at the scope the caller asked for.
     *
     * [revoke] is the difference between removing a message from this account's copy
     * of the chat and removing it from everybody's, so it is never defaulted — the
     * caller must have resolved which one the user chose.
     */
    suspend fun deleteMessages(
        chatId: Long,
        messageIds: LongArray,
        revoke: Boolean
    ): Result<Unit> {
        return sendExpectOk(TdApi.DeleteMessages(chatId, messageIds, revoke))
    }

    /**
     * Asks Telegram what the current account may actually do with a message.
     *
     * Aether never infers this. The server's answer already accounts for the edit
     * window, content protection, the account's rights in the chat and the message's
     * age — none of which can be derived from the message alone.
     */
    suspend fun messageCapabilities(chatId: Long, messageId: Long): MessageCapabilities {
        val result = send(TdApi.GetMessageProperties(chatId, messageId))
        val properties = result as? TdApi.MessageProperties ?: return MessageCapabilities.Unknown
        return MessageCapabilities(
            canBeEdited = properties.canBeEdited,
            canEditMedia = properties.canEditMedia,
            canBeDeletedOnlyForSelf = properties.canBeDeletedOnlyForSelf,
            canBeDeletedForAllUsers = properties.canBeDeletedForAllUsers,
            canBeForwarded = properties.canBeForwarded,
            canBeReplied = properties.canBeReplied,
            canBePinned = properties.canBePinned,
            canBeCopied = properties.canBeCopied,
            canBeSaved = properties.canBeSaved,
            canGetLink = properties.canGetLink,
            canGetReadDate = properties.canGetReadDate,
            canGetViewers = properties.canGetViewers,
            canDeleteReactions = properties.canDeleteReactions
        )
    }

    // --- chat-list operations ------------------------------------------------
    //
    // Every one of these is a real TDLib call whose result Telegram then reports
    // back through the update stream. None of them touch Aether's local chat list
    // directly: the list is redrawn from the update, so what Aether shows is what
    // Telegram actually did.

    /**
     * Pins or unpins a chat in a chat list. This is chat-list pinning, and has
     * nothing to do with pinning a message inside a chat — see [pinMessage].
     */
    suspend fun setChatPinned(chatId: Long, pinned: Boolean, archived: Boolean = false): Result<Unit> {
        val list: TdApi.ChatList =
            if (archived) TdApi.ChatListArchive() else TdApi.ChatListMain()
        return sendExpectOk(TdApi.ToggleChatIsPinned(list, chatId, pinned))
    }

    /** Marks a chat unread, or clears that mark, on the server. */
    suspend fun setChatMarkedAsUnread(chatId: Long, markedAsUnread: Boolean): Result<Unit> {
        return sendExpectOk(TdApi.ToggleChatIsMarkedAsUnread(chatId, markedAsUnread))
    }

    /** Reads the chat up to and including [upToMessageId] on the server. */
    suspend fun readChat(chatId: Long, upToMessageId: Long): Result<Unit> {
        if (upToMessageId == 0L) return Result.success(Unit)
        return sendExpectOk(TdApi.ViewMessages(chatId, longArrayOf(upToMessageId), null, true))
    }

    /**
     * Mutes or unmutes a chat through its Telegram notification settings.
     *
     * [muteForSeconds] of zero unmutes. Anything else mutes for that long, which is
     * how Telegram itself expresses both "mute" and "mute for an hour".
     */
    suspend fun setChatMuted(chatId: Long, muteForSeconds: Int): Result<Unit> {
        val existing = chats[chatId]?.notificationSettings
        val settings = TdApi.ChatNotificationSettings().apply {
            useDefaultMuteFor = false
            muteFor = muteForSeconds
            useDefaultSound = existing?.useDefaultSound ?: true
            soundId = existing?.soundId ?: 0L
            useDefaultShowPreview = existing?.useDefaultShowPreview ?: true
            showPreview = existing?.showPreview ?: true
            useDefaultMuteStories = existing?.useDefaultMuteStories ?: true
            muteStories = existing?.muteStories ?: false
            useDefaultStorySound = existing?.useDefaultStorySound ?: true
            storySoundId = existing?.storySoundId ?: 0L
            useDefaultShowStoryPoster = existing?.useDefaultShowStoryPoster ?: true
            showStoryPoster = existing?.showStoryPoster ?: false
            useDefaultDisablePinnedMessageNotifications =
                existing?.useDefaultDisablePinnedMessageNotifications ?: true
            disablePinnedMessageNotifications =
                existing?.disablePinnedMessageNotifications ?: false
            useDefaultDisableMentionNotifications =
                existing?.useDefaultDisableMentionNotifications ?: true
            disableMentionNotifications = existing?.disableMentionNotifications ?: false
        }
        return sendExpectOk(TdApi.SetChatNotificationSettings(chatId, settings))
    }

    /** Moves a chat between the main and archive chat lists on the server. */
    suspend fun setChatArchived(chatId: Long, archived: Boolean): Result<Unit> {
        val list: TdApi.ChatList =
            if (archived) TdApi.ChatListArchive() else TdApi.ChatListMain()
        return sendExpectOk(TdApi.AddChatToList(chatId, list))
    }

    /**
     * Clears a chat's history.
     *
     * [removeFromChatList] decides whether the conversation also leaves the list, and
     * [revoke] whether the history is cleared for the other side too. Both are the
     * caller's explicit choice because they are three different user-facing
     * operations wearing one TDLib function.
     */
    suspend fun deleteChatHistory(
        chatId: Long,
        removeFromChatList: Boolean,
        revoke: Boolean
    ): Result<Unit> {
        return sendExpectOk(TdApi.DeleteChatHistory(chatId, removeFromChatList, revoke))
    }

    /** Leaves a group, supergroup or channel. */
    suspend fun leaveChat(chatId: Long): Result<Unit> {
        return sendExpectOk(TdApi.LeaveChat(chatId))
    }

    /** Blocks or unblocks a user. */
    suspend fun setUserBlocked(userId: Long, blocked: Boolean): Result<Unit> {
        val sender: TdApi.MessageSender = TdApi.MessageSenderUser(userId)
        val blockList: TdApi.BlockList? = if (blocked) TdApi.BlockListMain() else null
        return sendExpectOk(TdApi.SetMessageSenderBlockList(sender, blockList))
    }

    /**
     * Stores a draft on the server so it follows the account to other clients.
     *
     * A blank draft clears it rather than storing an empty one.
     */
    suspend fun setChatDraft(
        chatId: Long,
        text: String,
        replyToMessageId: Long?,
        forumTopicId: Int? = null
    ): Result<Unit> {
        val draft = if (text.isBlank()) {
            null
        } else {
            TdApi.DraftMessage().apply {
                replyTo = replyToMessageId?.takeIf { it != 0L }?.let {
                    TdApi.InputMessageReplyToMessage(it, null, 0, null)
                }
                date = (System.currentTimeMillis() / 1000).toInt()
                inputMessageText = TdApi.InputMessageText(
                    TdApi.FormattedText(text, emptyArray()),
                    null,
                    false
                )
            }
        }
        return sendExpectOk(
            TdApi.SetChatDraftMessage(chatId, topicOf(forumTopicId), draft)
        )
    }

    /** Telegram's own ceiling on how many items one album may hold. */
    private val ALBUM_LIMIT = 10

    /** Fire-and-forget draft store, for teardown paths that cannot suspend. */
    fun setChatDraftAsync(
        chatId: Long,
        text: String,
        replyToMessageId: Long?,
        forumTopicId: Int? = null
    ) {
        scope.launch { setChatDraft(chatId, text, replyToMessageId, forumTopicId) }
    }

    // --- polls ------------------------------------------------------------------

    /**
     * Casts or changes this account's vote.
     *
     * An empty [optionIndices] retracts the vote, which Telegram permits only when
     * the poll allows revoting — the policy that decides whether to offer it lives
     * in [PollPresentation].
     */
    suspend fun setPollAnswer(
        chatId: Long,
        messageId: Long,
        optionIndices: IntArray
    ): Result<Unit> {
        return sendExpectOk(TdApi.SetPollAnswer(chatId, messageId, optionIndices))
    }

    /** Stops a poll, so no further votes are accepted. */
    suspend fun stopPoll(chatId: Long, messageId: Long): Result<Unit> {
        return sendExpectOk(TdApi.StopPoll(chatId, messageId, null))
    }

    // --- scheduled messages -----------------------------------------------------

    suspend fun getScheduledMessages(chatId: Long): List<Message> {
        val result = send(TdApi.GetChatScheduledMessages(chatId))
        val messages = (result as? TdApi.Messages)?.messages ?: return emptyList()
        return messages.mapNotNull { td -> td?.let(::mapUiMessage) }
    }

    suspend fun sendScheduledMessageNow(chatId: Long, messageId: Long): Result<Unit> {
        return sendExpectOk(TdApi.EditMessageSchedulingState(chatId, messageId, null))
    }

    suspend fun rescheduleMessage(chatId: Long, messageId: Long, dateSeconds: Int): Result<Unit> {
        val state = TdApi.MessageSchedulingStateSendAtDate(dateSeconds, 0)
        return sendExpectOk(TdApi.EditMessageSchedulingState(chatId, messageId, state))
    }

    /**
     * What Aether is currently doing in a chat, as Telegram models it.
     *
     * The distinction matters to the other side: "recording a voice message" and
     * "typing" are different pieces of information, and sending the latter while
     * doing the former is a small lie the recipient acts on.
     */
    enum class OutgoingChatAction {
        TYPING,
        RECORDING_VOICE,
        RECORDING_VIDEO,
        UPLOADING_PHOTO,
        UPLOADING_VIDEO,
        UPLOADING_DOCUMENT,
        UPLOADING_VOICE,
        CHOOSING_STICKER,
        CHOOSING_CONTACT,
        CHOOSING_LOCATION,

        /** Clears whatever action was showing. */
        CANCEL
    }

    /**
     * Tells the chat what this account is doing.
     *
     * Telegram expires an action after a few seconds on its own, so this is repeated
     * by the caller while the activity continues and sent once as [OutgoingChatAction.CANCEL]
     * when it stops — leaving a stale "typing…" on someone else's screen is worse
     * than showing nothing.
     */
    suspend fun sendChatAction(chatId: Long, action: OutgoingChatAction) {
        val td: TdApi.ChatAction = when (action) {
            OutgoingChatAction.TYPING -> TdApi.ChatActionTyping()
            OutgoingChatAction.RECORDING_VOICE -> TdApi.ChatActionRecordingVoiceNote()
            OutgoingChatAction.RECORDING_VIDEO -> TdApi.ChatActionRecordingVideo()
            OutgoingChatAction.UPLOADING_PHOTO -> TdApi.ChatActionUploadingPhoto(0)
            OutgoingChatAction.UPLOADING_VIDEO -> TdApi.ChatActionUploadingVideo(0)
            OutgoingChatAction.UPLOADING_DOCUMENT -> TdApi.ChatActionUploadingDocument(0)
            OutgoingChatAction.UPLOADING_VOICE -> TdApi.ChatActionUploadingVoiceNote(0)
            OutgoingChatAction.CHOOSING_STICKER -> TdApi.ChatActionChoosingSticker()
            OutgoingChatAction.CHOOSING_CONTACT -> TdApi.ChatActionChoosingContact()
            OutgoingChatAction.CHOOSING_LOCATION -> TdApi.ChatActionChoosingLocation()
            OutgoingChatAction.CANCEL -> TdApi.ChatActionCancel()
        }
        send(TdApi.SendChatAction(chatId, null, null, td))
    }

    suspend fun sendTyping(chatId: Long) {
        sendChatAction(chatId, OutgoingChatAction.TYPING)
    }

    // --- search ---------------------------------------------------------------

    /**
     * Searches one conversation on the server.
     *
     * Server-backed rather than a filter over whatever the list happens to have
     * loaded, so a match a thousand messages back is found. [fromMessageId] of zero
     * starts from the newest message; the returned [FoundChatMessages.nextFromMessageId]
     * continues the search.
     */
    suspend fun searchChatMessages(
        chatId: Long,
        query: String,
        fromMessageId: Long = 0L,
        limit: Int = 50,
        forumTopicId: Int? = null
    ): Result<TdApi.FoundChatMessages> {
        if (query.isBlank()) return Result.failure(IllegalArgumentException("Empty query"))
        val function = TdApi.SearchChatMessages(
            chatId,
            // Scoped to the open topic, so a forum search answers about what the
            // user is actually reading.
            topicOf(forumTopicId),
            query,
            null,
            fromMessageId,
            0,
            limit,
            null
        )
        return when (val result = send(function)) {
            is TdApi.FoundChatMessages -> Result.success(result)
            is TdApi.Error -> Result.failure(IllegalStateException(TdErrors.userMessage(result)))
            else -> Result.failure(IllegalStateException("Search failed"))
        }
    }

    enum class SharedMediaCategory {
        MEDIA, FILES, LINKS, VOICE
    }

    data class SharedMediaPage(
        val messages: List<com.foresightlabs.aether.domain.model.Message>,
        val nextFromMessageId: Long,
        val totalCount: Int
    )

    /**
     * Queries TDLib directly for shared media in a chat using SearchChatMessages with pinned filters.
     */
    suspend fun getSharedMedia(
        chatId: Long,
        category: SharedMediaCategory,
        fromMessageId: Long = 0L,
        limit: Int = 50
    ): SharedMediaPage {
        val filter: TdApi.SearchMessagesFilter = when (category) {
            SharedMediaCategory.MEDIA -> TdApi.SearchMessagesFilterPhotoAndVideo()
            SharedMediaCategory.FILES -> TdApi.SearchMessagesFilterDocument()
            SharedMediaCategory.LINKS -> TdApi.SearchMessagesFilterUrl()
            SharedMediaCategory.VOICE -> TdApi.SearchMessagesFilterVoiceAndVideoNote()
        }
        val function = TdApi.SearchChatMessages(
            chatId,
            null,
            "",
            null,
            fromMessageId,
            0,
            limit,
            filter
        )
        return when (val result = send(function)) {
            is TdApi.FoundChatMessages -> {
                val mapped = result.messages.orEmpty().filterNotNull().map { mapUiMessage(it) }
                SharedMediaPage(
                    messages = mapped,
                    nextFromMessageId = result.nextFromMessageId,
                    totalCount = result.totalCount
                )
            }
            else -> SharedMediaPage(emptyList(), 0L, 0)
        }
    }

    /**
     * Searches messages across every chat the account can see.
     *
     * Paginated through TDLib's own opaque offset — Aether never invents one.
     */
    suspend fun searchMessagesGlobally(
        query: String,
        offset: String = "",
        limit: Int = 40
    ): Result<TdApi.FoundMessages> {
        if (query.isBlank()) return Result.failure(IllegalArgumentException("Empty query"))
        val function = TdApi.SearchMessages(
            TdApi.ChatListMain(),
            query,
            offset,
            limit,
            null,
            null,
            0,
            0
        )
        return when (val result = send(function)) {
            is TdApi.FoundMessages -> Result.success(result)
            is TdApi.Error -> Result.failure(IllegalStateException(TdErrors.userMessage(result)))
            else -> Result.failure(IllegalStateException("Search failed"))
        }
    }

    /**
     * Loads the window of history around [messageId] so a search or reply target can
     * be scrolled to even when it was never loaded.
     *
     * TDLib's negative offset returns messages newer than the anchor as well as
     * older ones, which is what makes the target land mid-viewport rather than at
     * the very top.
     */
    suspend fun loadHistoryAround(
        chatId: Long,
        messageId: Long,
        limit: Int = 40
    ): List<Message> {
        val result = send(
            TdApi.GetChatHistory(chatId, messageId, -limit / 2, limit, false)
        )
        val messages = (result as? TdApi.Messages)?.messages ?: return emptyList()
        return messages.mapNotNull { td -> td?.let(::mapUiMessage) }.reversed()
    }

    /**
     * Every pinned message in a chat, newest first.
     *
     * Uses Telegram's own pinned filter rather than scanning loaded history, so a
     * message pinned long ago is found even when it is thousands of messages back.
     */
    suspend fun pinnedMessages(
        chatId: Long,
        limit: Int = 20,
        forumTopicId: Int? = null
    ): List<Message> {
        val function = TdApi.SearchChatMessages(
            chatId,
            topicOf(forumTopicId),
            "",
            null,
            0L,
            0,
            limit,
            TdApi.SearchMessagesFilterPinned()
        )
        val found = send(function) as? TdApi.FoundChatMessages ?: return emptyList()
        return found.messages.filterNotNull().map(::mapUiMessage)
    }

    /** Maps a TDLib message found by search into Aether's model. */
    fun mapFoundMessage(message: TdApi.Message): Message = mapUiMessage(message)

    suspend fun searchChats(query: String): List<Chat> {
        if (query.isBlank()) return _chatList.value
        val local = send(TdApi.SearchChats(query, 40))
        val ids = (local as? TdApi.Chats)?.chatIds ?: longArrayOf()
        return ids.map { id -> chat(id) ?: chats[id]?.let { mapUiChat(it) } }.filterNotNull()
    }

    suspend fun downloadFile(fileId: Int, priority: Int = 16) {
        if (requestedFiles.putIfAbsent(fileId, true) != null) return
        send(TdApi.DownloadFile(fileId, priority, 0, 0, false))
    }

    /**
     * Re-requests a file TDLib previously gave up on.
     *
     * Clears the failure so [resolveMediaPath] stops treating this file as a
     * dead end, then re-issues the download -- this is the only path that
     * downloads a previously-failed file again; auto-retry deliberately does
     * not do this on its own.
     */
    fun retryMediaDownload(fileId: Int) {
        if (fileId == 0) return
        failedDownloads.remove(fileId)
        requestedFiles.remove(fileId)
        activeDownloads[fileId] = true
        scope.launch { downloadFile(fileId, priority = 32) }
    }

    /**
     * High-priority request for a file's full bytes, used when the user
     * actually opens the viewer on it. Bypasses the de-duplication that
     * protects the timeline's background prefetch from redundant requests --
     * TDLib treats a repeat DownloadFile as a priority bump, not an error.
     */
    fun requestFullMediaDownload(fileId: Int) {
        if (fileId == 0) return
        failedDownloads.remove(fileId)
        if (activeDownloads[fileId] == true) return
        activeDownloads[fileId] = true
        requestedFiles[fileId] = true
        scope.launch { send(TdApi.DownloadFile(fileId, 32, 0, 0, false)) }
    }

    fun messagesFlow(chatId: Long): StateFlow<List<Message>> {
        return conversationFlows.getOrPut(chatId) {
            MutableStateFlow(emptyList())
        }.asStateFlow()
    }

    fun upsertConversation(chatId: Long, incoming: List<Message>, prepend: Boolean) {
        val normalized = incoming.map { mapped ->
            rawMessages[mapped.id.toLongOrNull() ?: return@map mapped]
                ?.takeIf { it.chatId == chatId }
                ?.let(::mapUiMessage)
                ?: mapped
        }
        conversationFlows.getOrPut(chatId) { MutableStateFlow(emptyList()) }.update { current ->
            // A lookup built once, rather than current.firstOrNull{} per entry below --
            // that scan-per-entry made a single incoming message an O(n^2) update over
            // the whole loaded history instead of the O(n) it needs to be.
            val previousById = current.associateBy { it.id }
            val byId = LinkedHashMap<String, Message>()
            if (prepend) {
                normalized.forEach { byId[it.id] = it }
                current.forEach { byId.putIfAbsent(it.id, it) }
            } else {
                current.forEach { byId[it.id] = it }
                normalized.forEach { byId[it.id] = it }
            }
            byId.mapValues { (id, message) ->
                val previous = previousById[id]
                if (message.presentationKey == null && previous?.presentationKey != null) {
                    message.copy(presentationKey = previous.presentationKey)
                } else message
            }.values.sortedBy { it.dateSeconds.toLong() * 1_000_000 + (it.id.toLongOrNull() ?: 0L) }
        }
    }

    fun replaceMessage(chatId: Long, oldId: String, newMessage: Message) {
        conversationFlows[chatId]?.update { list ->
            val old = list.firstOrNull { it.id == oldId }
            val reconciled = if (newMessage.presentationKey == null && old?.presentationKey != null) {
                newMessage.copy(presentationKey = old.presentationKey)
            } else {
                newMessage
            }
            val without = list.filterNot { it.id == oldId || it.id == newMessage.id }
            (without + reconciled).sortedBy { it.dateSeconds.toLong() * 1_000_000 + (it.id.toLongOrNull() ?: 0L) }
        }
    }

    fun removeMessages(chatId: Long, ids: Set<String>) {
        conversationFlows[chatId]?.update { list -> list.filterNot { it.id in ids } }
    }

    private val conversationFlows = ConcurrentHashMap<Long, MutableStateFlow<List<Message>>>()

    /**
     * The TDLib messages behind the mapped ones, so a bubble can be re-mapped when
     * its media finishes downloading without another round trip.
     */
    private val rawMessages = ConcurrentHashMap<Long, TdApi.Message>()

    private data class ReplyTarget(val chatId: Long, val messageId: Long)

    private val replyResolutionJobs = ConcurrentHashMap<ReplyTarget, Job>()
    private val unavailableReplyTargets = ConcurrentHashMap.newKeySet<ReplyTarget>()

    /** Supergroup records, which carry the forum flag and member counts. */
    private val supergroups = ConcurrentHashMap<Long, TdApi.Supergroup>()
    private val basicGroups = ConcurrentHashMap<Long, TdApi.BasicGroup>()
    private val secretChats = ConcurrentHashMap<Int, TdApi.SecretChat>()

    private val _forumTopicRevision = MutableStateFlow(0)

    /**
     * Bumped whenever Telegram reports a forum topic change.
     *
     * An open topic list observes this and re-reads, so a topic created, renamed or
     * closed elsewhere appears without the user pulling to refresh.
     */
    val forumTopicRevision: StateFlow<Int> = _forumTopicRevision.asStateFlow()

    private suspend fun handleUpdate(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateNotificationGroup -> notificationManager?.onUpdateNotificationGroup(update)
            is TdApi.UpdateNotification -> notificationManager?.onUpdateNotification(update)
            is TdApi.UpdateActiveNotifications -> notificationManager?.onUpdateActiveNotifications(update)
            is TdApi.UpdateCall -> handleCallUpdate(update.call)
            is TdApi.UpdateAuthorizationState -> onAuth(update.authorizationState)
            is TdApi.UpdateConnectionState -> {
                val previous = _connection.value
                val next = TelegramMappers.mapConnection(update.state)
                if (BuildConfig.DEBUG) {
                    if (previous != next) {
                        android.util.Log.d(
                            TAG,
                            "TDLIB_CONNECTION_STATE from=${previous.name} to=${next.name} " +
                                "elapsedRealtime=${android.os.SystemClock.elapsedRealtime()}"
                        )
                    }
                }
                _connection.value = next
                if (next == com.foresightlabs.aether.domain.model.ConnectionStatus.READY &&
                    previous != com.foresightlabs.aether.domain.model.ConnectionStatus.READY
                ) {
                    onConnectionReady()
                }
            }
            is TdApi.UpdateChatFolders -> {
                // Telegram decides where the main list sits among the folders; it is
                // inserted at that index rather than assumed to come first.
                val folders = update.chatFolders.orEmpty().filterNotNull().map { info ->
                    ChatFolder(
                        id = info.id,
                        title = info.name?.text?.text.orEmpty().ifBlank { "Folder" }
                    )
                }
                val position = update.mainChatListPosition.coerceIn(0, folders.size)
                _chatFolders.value = folders.toMutableList().apply {
                    add(position, ChatFolder.Main)
                }
            }
            // --- chat state that Aether's own actions change ----------------
            // Each of these is the server's confirmation of an action the user just
            // took. Without them the row keeps showing the state it had before, and
            // the action looks as though it silently failed.
            is TdApi.UpdateChatIsMarkedAsUnread -> {
                chats[update.chatId]?.isMarkedAsUnread = update.isMarkedAsUnread
                publishChats()
            }
            is TdApi.UpdateChatBlockList -> {
                chats[update.chatId]?.blockList = update.blockList
                publishChats()
            }
            is TdApi.UpdateChatPermissions -> {
                chats[update.chatId]?.permissions = update.permissions
                publishChats()
            }
            is TdApi.UpdateChatUnreadMentionCount -> {
                chats[update.chatId]?.unreadMentionCount = update.unreadMentionCount
                publishChats()
            }
            is TdApi.UpdateChatUnreadReactionCount -> {
                chats[update.chatId]?.unreadReactionCount = update.unreadReactionCount
                publishChats()
            }
            is TdApi.UpdateChatAddedToList, is TdApi.UpdateChatRemovedFromList -> {
                // Archive and folder membership are carried by chat positions, which
                // arrive separately; this simply refreshes what is already held.
                publishChats()
            }
            is TdApi.UpdateChatHasProtectedContent -> {
                chats[update.chatId]?.hasProtectedContent = update.hasProtectedContent
                publishChats()
            }
            is TdApi.UpdateChatMessageAutoDeleteTime -> {
                chats[update.chatId]?.messageAutoDeleteTime = update.messageAutoDeleteTime
                publishChats()
            }
            is TdApi.UpdateBasicGroup -> {
                basicGroups[update.basicGroup.id] = update.basicGroup
                publishChats()
            }
            is TdApi.UpdateSecretChat -> {
                secretChats[update.secretChat.id] = update.secretChat
                publishChats()
            }
            is TdApi.UpdateForumTopicInfo -> {
                // A topic was created or renamed; the open topic list re-reads it.
                _forumTopicRevision.value = _forumTopicRevision.value + 1
            }
            is TdApi.UpdateForumTopic -> {
                _forumTopicRevision.value = _forumTopicRevision.value + 1
            }
            is TdApi.UpdateMessageMentionRead -> {
                chats[update.chatId]?.unreadMentionCount = update.unreadMentionCount
                publishChats()
            }
            is TdApi.UpdateMessageUnreadReactions -> {
                chats[update.chatId]?.unreadReactionCount = update.unreadReactionCount
                publishChats()
            }
            is TdApi.UpdateMessageContentOpened -> {
                // Self-destructing media has been viewed; its content will follow in
                // an UpdateMessageContent, so nothing is guessed here.
                rawMessages[update.messageId]?.let { raw ->
                    replaceMessage(update.chatId, update.messageId.toString(), mapUiMessage(raw))
                }
            }
            is TdApi.UpdateSupergroup -> {
                // Carries the forum flag, which decides whether a chat opens as a
                // topic list or as a single conversation.
                supergroups[update.supergroup.id] = update.supergroup
                publishChats()
            }
            is TdApi.UpdateUser -> {
                users[update.user.id] = update.user
                indexUserAvatar(update.user)
                requestUserPhoto(update.user)
                if (update.user.id == myUserId) publishMe()
                publishChats()
                refreshAllReplyPreviews()
            }
            is TdApi.UpdateUserStatus -> {
                users[update.userId]?.let { existing ->
                    existing.status = update.status
                    users[update.userId] = existing
                }
                if (update.userId == myUserId) publishMe()
                publishChats()
            }
            is TdApi.UpdateNewChat -> {
                chats[update.chat.id] = update.chat
                indexChatAvatar(update.chat)
                requestChatPhoto(update.chat)
                publishChats()
                refreshAllReplyPreviews()
            }
            is TdApi.UpdateChatLastMessage -> {
                chats[update.chatId]?.let {
                    it.lastMessage = update.lastMessage
                    it.positions = update.positions
                    chats[update.chatId] = it
                }
                publishChats()
            }
            is TdApi.UpdateChatPosition -> applyPosition(update.chatId, update.position)
            is TdApi.UpdateChatTitle -> {
                chats[update.chatId]?.title = update.title
                publishChats()
                refreshAllReplyPreviews()
            }
            is TdApi.UpdateChatPhoto -> {
                chats[update.chatId]?.photo = update.photo
                chats[update.chatId]?.let(::indexChatAvatar)
                chats[update.chatId]?.let { requestChatPhoto(it) }
                publishChats()
            }
            is TdApi.UpdateChatReadInbox -> {
                chats[update.chatId]?.let {
                    it.lastReadInboxMessageId = update.lastReadInboxMessageId
                    it.unreadCount = update.unreadCount
                }
                publishChats()
            }
            is TdApi.UpdateChatReadOutbox -> {
                chats[update.chatId]?.lastReadOutboxMessageId = update.lastReadOutboxMessageId
                publishChats()
                refreshConversationStatuses(update.chatId)
            }
            is TdApi.UpdateChatDraftMessage -> {
                chats[update.chatId]?.let {
                    it.draftMessage = update.draftMessage
                    it.positions = update.positions
                }
                publishChats()
            }
            is TdApi.UpdateChatNotificationSettings -> {
                chats[update.chatId]?.notificationSettings = update.notificationSettings
                publishChats()
            }
            is TdApi.UpdateChatAction -> {
                val action = update.action
                if (action is TdApi.ChatActionTyping) {
                    typing[update.chatId] = "typing..."
                } else if (action is TdApi.ChatActionCancel) {
                    typing.remove(update.chatId)
                }
                publishChats()
            }
            is TdApi.UpdateNewMessage -> {
                val msg = update.message
                if (BuildConfig.DEBUG) {
                    NotificationTiming.markNewMessage(msg.id)
                    val chatHash = Integer.toHexString(msg.chatId.hashCode())
                    android.util.Log.d(TAG, "TDLIB_UPDATE_NEW_MESSAGE chatHash=$chatHash msgId=${msg.id} isOutgoing=${msg.isOutgoing} date=${msg.date} elapsedRealtime=${android.os.SystemClock.elapsedRealtime()}")
                }
                chats[msg.chatId]?.lastMessage = msg
                upsertConversation(msg.chatId, listOf(mapUiMessage(msg)), prepend = false)
                publishMessageEvent(
                    msg.chatId,
                    msg.id,
                    if (msg.isOutgoing) MessageMotionEventType.NEW_OUTGOING else MessageMotionEventType.NEW_INCOMING
                )
                publishChats()
            }
            is TdApi.UpdateMessageContent -> {
                // Re-map through the full presentation path. Mapping only the preview
                // text here used to flatten a photo, poll or formatted message into a
                // plain text bubble the moment anything about it changed — which a
                // poll vote does on every single vote.
                val cached = rawMessages[update.messageId]
                if (cached != null && cached.chatId == update.chatId) {
                    cached.content = update.newContent
                    val mapped = mapUiMessage(cached)
                    replaceMessage(update.chatId, update.messageId.toString(), mapped)
                    val type = when (mapped.type) {
                        MessageType.IMAGE,
                        MessageType.ALBUM,
                        MessageType.ANIMATION,
                        MessageType.VIDEO_NOTE,
                        MessageType.AUDIO,
                        MessageType.STICKER,
                        MessageType.FILE -> MessageMotionEventType.MEDIA_UPDATED
                        else -> MessageMotionEventType.EDITED
                    }
                    publishMessageEvent(update.chatId, update.messageId, type)
                } else {
                    conversationFlows[update.chatId]?.update { list ->
                        list.map { current ->
                            if (current.id == update.messageId.toString()) {
                                val (text, type) = TelegramMappers.mapContent(update.newContent)
                                current.copy(text = text, type = type)
                            } else current
                        }
                    }
                    publishMessageEvent(update.chatId, update.messageId, MessageMotionEventType.EDITED)
                }
                refreshReplyingMessages(ReplyTarget(update.chatId, update.messageId))
            }
            is TdApi.UpdateMessageInteractionInfo -> {
                // Reaction counts and the "you reacted" flag, straight from Telegram.
                val reactions = TelegramMappers.mapReactions(update.interactionInfo)
                rawMessages[update.messageId]?.interactionInfo = update.interactionInfo
                conversationFlows[update.chatId]?.update { list ->
                    list.map { current ->
                        if (current.id == update.messageId.toString()) {
                            current.copy(reactions = reactions)
                        } else current
                    }
                }
                publishMessageEvent(update.chatId, update.messageId, MessageMotionEventType.REACTION_UPDATED)
            }
            is TdApi.UpdateMessageIsPinned -> {
                rawMessages[update.messageId]?.isPinned = update.isPinned
                conversationFlows[update.chatId]?.update { list ->
                    list.map { current ->
                        if (current.id == update.messageId.toString()) {
                            current.copy(isPinned = update.isPinned)
                        } else current
                    }
                }
            }
            is TdApi.UpdateMessageEdited -> {
                conversationFlows[update.chatId]?.update { list ->
                    list.map {
                        if (it.id == update.messageId.toString()) it.copy(isEdited = update.editDate > 0) else it
                    }
                }
                publishMessageEvent(update.chatId, update.messageId, MessageMotionEventType.EDITED)
                refreshReplyingMessages(ReplyTarget(update.chatId, update.messageId))
            }
            is TdApi.UpdateMessageSendSucceeded -> {
                replaceMessage(update.message.chatId, update.oldMessageId.toString(), mapUiMessage(update.message))
                publishMessageEvent(update.message.chatId, update.message.id, MessageMotionEventType.SEND_CONFIRMED)
                chats[update.message.chatId]?.lastMessage = update.message
                publishChats()
            }
            is TdApi.UpdateMessageSendFailed -> {
                replaceMessage(update.message.chatId, update.oldMessageId.toString(), mapUiMessage(update.message))
                publishMessageEvent(update.message.chatId, update.message.id, MessageMotionEventType.FAILED)
            }
            is TdApi.UpdateDeleteMessages -> {
                if (update.fromCache) return
                update.messageIds.forEach { messageId ->
                    if (rawMessages[messageId]?.chatId == update.chatId) {
                        rawMessages.remove(messageId)
                        mediaReferenceIndex.remove(MessageMediaReference(update.chatId, messageId))
                    }
                    unavailableReplyTargets.add(ReplyTarget(update.chatId, messageId))
                    refreshReplyingMessages(ReplyTarget(update.chatId, messageId))
                }
                update.messageIds.forEach { publishMessageEvent(update.chatId, it, MessageMotionEventType.DELETED) }
                // Keep the confirmed row alive for the short exit transition. The
                // server has already confirmed deletion; this only gives the UI time
                // to close the space without a visual snap.
                scope.launch {
                    delay(ConversationMotion.FAST_MS.toLong() + 80L)
                    removeMessages(update.chatId, update.messageIds.map { it.toString() }.toSet())
                }
            }
            is TdApi.UpdateServiceNotification -> {
                // Telegram's own account talking to the client directly. TDLib
                // documents these as requiring the application to show the
                // content, so they are surfaced rather than dropped -- Aether's
                // people-first filtering has no business discarding an account
                // security notice. The text is rendered with the same mapper
                // ordinary message content uses.
                val text = com.foresightlabs.aether.data.notifications.NotificationContentMapper
                    .mapMessageContent(update.content, showPreview = true)
                val notice = com.foresightlabs.aether.domain.messaging.buildServiceNotice(update.type, text)
                if (notice != null) {
                    if (BuildConfig.DEBUG) {
                        // The notice body can contain a login code. Only its shape
                        // is logged, never its content.
                        android.util.Log.d(TAG, "SERVICE_NOTIFICATION_RECEIVED authKeyDrop=${notice.requiresAuthKeyDropPrompt}")
                    }
                    _serviceNotice.value = notice
                }
            }
            is TdApi.UpdateFile -> onFile(update.file)
            is TdApi.UpdateHavePendingNotifications -> {
                if (BuildConfig.DEBUG) {
                    val isPending = update.haveDelayedNotifications || update.haveUnreceivedNotifications
                    val elapsed = android.os.SystemClock.elapsedRealtime()
                    android.util.Log.d(TAG, if (isPending) "PENDING_UPDATE_TRUE haveDelayed=${update.haveDelayedNotifications} haveUnreceived=${update.haveUnreceivedNotifications} elapsedRealtime=$elapsed" else "PENDING_UPDATE_FALSE haveDelayed=${update.haveDelayedNotifications} haveUnreceived=${update.haveUnreceivedNotifications} elapsedRealtime=$elapsed")
                }
                pushPendingGate.onUpdate(update.haveDelayedNotifications, update.haveUnreceivedNotifications)
            }
            is TdApi.UpdateChatActiveStories -> {
                val active = update.activeStories
                if (active != null) {
                    activeStories[active.chatId] = active
                    scope.launch { fetchStoriesFor(active) }
                }
                publishChats()
                publishPulses()
            }
            is TdApi.UpdateStory -> {
                val story = update.story
                val senderChat = chats[story.posterChatId]
                val senderUser = users[story.posterChatId]
                val senderName = senderChat?.title ?: senderUser?.let { listOf(it.firstName, it.lastName).filter { n -> !n.isNullOrBlank() }.joinToString(" ") } ?: "Contact"
                val item = TelegramMappers.mapStory(story, senderName)
                storiesCache["${story.posterChatId}_${story.id}"] = item
                publishPulses()
            }
            is TdApi.UpdateStoryDeleted -> {
                storiesCache.remove("${update.storyPosterChatId}_${update.storyId}")
                publishPulses()
            }
            is TdApi.UpdateStoryPostSucceeded -> {
                val story = update.story
                val me = users[myUserId]
                val name = me?.let { listOf(it.firstName, it.lastName).filter { n -> !n.isNullOrBlank() }.joinToString(" ") } ?: "You"
                storiesCache["${story.posterChatId}_${story.id}"] = TelegramMappers.mapStory(story, name)
                publishPulses()
            }
            is TdApi.UpdateStoryPostFailed -> {
                if (BuildConfig.DEBUG) {
                    android.util.Log.w(TAG, "Story post failed: ${update.error.message}")
                }
            }
        }
    }

    // --- Real background push (TDLib + FCM) ---
    //
    // FCM exists only to wake TDLib and hand it the raw push so TDLib can
    // decide what happened and emit the same UpdateNotificationGroup /
    // UpdateNotification / UpdateActiveNotifications updates a live connection
    // would have produced. Android notifications are never built directly from
    // an FCM payload.
    /**
     * Which token still needs registering with Telegram, and whether a failed
     * attempt is worth repeating -- see
     * [PushRegistration][com.foresightlabs.aether.data.push.PushRegistration].
     */
    private val pushRegistration = com.foresightlabs.aether.data.push.PushRegistration()

    /**
     * The identifier TDLib returned from the RegisterDevice call this client
     * made. TDLib's own documented push flow is: GetPushReceiverId(payload) on
     * every incoming push, then route to whichever client registered that id
     * -- meant for a device receiving pushes for several accounts/clients at
     * once. Aether has exactly one TelegramClient, so there is only ever one
     * id to route to, but the routing check itself is kept: a receiver id
     * that is neither 0 (no id could be extracted -- process anyway, there is
     * nowhere else for it to go) nor this client's own id means the push was
     * meant for a registration Aether does not currently hold (most likely a
     * stale token from a previous login), and processing it would be acting
     * on a push this session was never told about.
     *
     * Persisted in app-private SharedPreferences (not TDLib's own database --
     * this is Aether's own bookkeeping) and loaded eagerly, so a process
     * started fresh by the push itself still has it available for the
     * mismatch check rather than starting blind every cold start. Cleared on
     * logout in [clearSession] so a stale id from a previous account can
     * never suppress or misroute a push after re-login.
     */
    @Volatile private var pushReceiverId: Long? = loadPersistedPushReceiverId()

    private val pushPrefs by lazy {
        application.getSharedPreferences("aether_push_state", android.content.Context.MODE_PRIVATE)
    }

    private fun loadPersistedPushReceiverId(): Long? {
        val prefs = application.getSharedPreferences("aether_push_state", android.content.Context.MODE_PRIVATE)
        return prefs.getLong(PREF_PUSH_RECEIVER_ID, 0L).takeIf { prefs.contains(PREF_PUSH_RECEIVER_ID) }
    }

    private fun persistPushReceiverId(id: Long?) {
        pushPrefs.edit {
            if (id == null) remove(PREF_PUSH_RECEIVER_ID) else putLong(PREF_PUSH_RECEIVER_ID, id)
        }
    }

    /** Race-safe tracker for TDLib's UpdateHavePendingNotifications state -- see [PushPendingGate]. */
    private val pushPendingGate = com.foresightlabs.aether.data.push.PushPendingGate()

    /**
     * Registers this device's FCM token with Telegram, so the server knows
     * where to push while the connection is not live.
     *
     * Idempotent: a token equal to the last one successfully registered is a
     * no-op, so a Service re-delivering the same token (or a caller invoking
     * this from more than one place) never re-registers on every call.
     * RegisterDevice requires an authorized session, so a token that arrives
     * first is held and flushed once [AuthUiState.Ready] is reached.
     */
    fun registerFcmToken(token: String) {
        if (!pushRegistration.onTokenAvailable(token)) return
        if (BuildConfig.DEBUG) android.util.Log.d(TAG, "FCM_TOKEN_AVAILABLE")
        if (_authState.value is AuthUiState.Ready) {
            scope.launch { flushPendingFcmToken() }
        }
    }

    /**
     * Re-attempts a registration that failed for a reason repeating it could
     * fix (see [PushRegistration][com.foresightlabs.aether.data.push.PushRegistration]).
     *
     * Driven by the connection coming back, not by a timer: without this, a
     * device whose first attempt happened during a bad moment on the network
     * would hold an unregistered token -- and therefore receive no pushes at
     * all -- until the process next started. Bounded by the registration's own
     * attempt budget, so this is a reaction to an event, not a retry loop.
     */
    private fun onConnectionReady() {
        if (_authState.value !is AuthUiState.Ready) return
        if (pushRegistration.tokenToRetryOnReconnect() == null) return
        if (BuildConfig.DEBUG) android.util.Log.d(TAG, "REGISTER_DEVICE_RETRY_ON_CONNECT")
        scope.launch { flushPendingFcmToken() }
    }

    private val registrationMutex = Mutex()

    private suspend fun flushPendingFcmToken() = registrationMutex.withLock {
        val token = pushRegistration.beginAttempt() ?: return@withLock
        if (BuildConfig.DEBUG) android.util.Log.d(TAG, "REGISTER_DEVICE_REQUEST")
        // encrypt=true: per TDLib's Notification API docs, an FCM push
        // registered without encryption carries no message content at all
        // (TDLib gets woken up but must still reach Telegram's server to find
        // out what happened) -- exactly the dependency on post-wake
        // connectivity this milestone exists to reduce. With encryption
        // enabled TDLib generates and holds its own key material locally
        // (nothing for Aether to generate, store, or expose here) and can
        // produce real NewPushMessage notification content straight from the
        // push payload.
        val deviceToken = TdApi.DeviceTokenFirebaseCloudMessaging(token, true)
        when (val result = send(TdApi.RegisterDevice(deviceToken, LongArray(0)))) {
            is TdApi.PushReceiverId -> {
                pushRegistration.onRegistered(token)
                pushReceiverId = result.id
                persistPushReceiverId(result.id)
                if (BuildConfig.DEBUG) android.util.Log.d(TAG, "REGISTER_DEVICE_SUCCESS")
            }
            is TdApi.Error -> {
                val failure = pushRegistration.onAttemptFailed(result.code)
                if (BuildConfig.DEBUG) {
                    // The symbol Telegram answers with is what distinguishes a
                    // token problem from the server-side application having no
                    // push credentials configured (APP_PUSH_APIKEY_MISSING),
                    // which is not something the client can resolve. It names a
                    // server-side condition and carries nothing private -- no
                    // token, no credential, no message content.
                    android.util.Log.w(
                        TAG,
                        "REGISTER_DEVICE_ERROR code=${result.code} reason=${result.message} disposition=$failure"
                    )
                }
            }
            else -> {}
        }
    }

    /**
     * Hands a raw FCM data payload to TDLib, following TDLib's documented
     * push flow: GetPushReceiverId first to decide whether this push is
     * actually for this client, then ProcessPushNotification.
     *
     * TDLib alone knows how to turn the payload into real Telegram semantics
     * (fetch the update, decrypt if needed, decide what notification state
     * changed); this never inspects the payload itself. The resulting
     * UpdateNotificationGroup/UpdateNotification/UpdateActiveNotifications
     * updates flow through the same canonical path a live connection uses, so
     * there is exactly one place Android notifications get built.
     *
     * TDLib supports calling ProcessPushNotification before authorization,
     * and the client here is created in Application.onCreate() -- which runs
     * before this is ever invoked, even when nothing else in the app is
     * running -- so no extra process bootstrapping is needed for a push-only
     * wakeup.
     *
     * Result handling is not uniform, because TDLib documents ProcessPushNotification
     * as returning Ok only *after* every update the push caused has already
     * been sent -- so a successful call needs no further waiting at all, and
     * this returns immediately. Error 406 means the opposite: the push alone
     * could not tell TDLib enough, and a live server connection is needed to
     * fetch what actually changed -- that is real, possibly slow network work
     * this function does not block on; it hands it to [PushFetchWorker]
     * instead and returns. Any other error is logged and otherwise dropped;
     * there is nothing more to try.
     */
    suspend fun processPushNotification(payload: String): String =
        pushDelivery.deliver(payload).name

    /**
     * The push sequence itself, wired to this client. Every step it needs is
     * supplied here; the ordering and the lifetime rules live in
     * [PushDelivery][com.foresightlabs.aether.data.push.PushDelivery] so they
     * can be exercised without TDLib.
     */
    private val pushDelivery by lazy {
        com.foresightlabs.aether.data.push.PushDelivery(
            resolveReceiverId = { payload -> getPushReceiverId(payload) },
            registeredReceiverId = { pushReceiverId },
            awaitTdlibReady = {
                start()
                withTimeoutOrNull(PUSH_PARAMETERS_TIMEOUT_MS) { parametersApplied.await() }
                Unit
            },
            processPush = { payload ->
                when (val result = send(TdApi.ProcessPushNotification(payload))) {
                    is TdApi.Ok -> com.foresightlabs.aether.data.push.PushDelivery.ProcessResult.Ok
                    is TdApi.Error ->
                        if (result.code == 406) {
                            com.foresightlabs.aether.data.push.PushDelivery.ProcessResult.NeedsLiveFetch
                        } else {
                            com.foresightlabs.aether.data.push.PushDelivery.ProcessResult.Failed(result.code)
                        }
                    else -> com.foresightlabs.aether.data.push.PushDelivery.ProcessResult.Failed(0)
                }
            },
            awaitNotificationWork = { timeoutMs -> notificationWork.awaitDrained(timeoutMs) },
            handOffToLiveFetch = { enqueuePushFetchWork() },
            log = { line -> if (BuildConfig.DEBUG) android.util.Log.d(TAG, line) }
        )
    }

    /**
     * The only continuation path a push can hand off to, and only for error
     * 406.
     *
     * Uses an ordinary (non-expedited) OneTimeWorkRequest: no setExpedited(),
     * no setForeground(), no ForegroundInfo. This push continuation is
     * intentionally non-expedited to avoid introducing a foreground-service
     * requirement on older Android versions.
     *
     * ExistingWorkPolicy.KEEP, not REPLACE: this worker's job is "connect
     * TDLib and fetch whatever notifications are pending", not "process this
     * one push's payload" -- it doesn't even receive the payload (TDLib
     * already saw it via ProcessPushNotification; there is nothing
     * push-specific left to carry, so nothing is put in the WorkRequest's
     * input data). Several 406s arriving close together all want the exact
     * same outcome, so if one is already connecting, replacing it would
     * cancel real in-flight progress and restart from scratch for no reason.
     * One active worker is enough to satisfy every pending 406.
     */
    private fun enqueuePushFetchWork() {
        val workManager = androidx.work.WorkManager.getInstance(application)
        if (BuildConfig.DEBUG) {
            val alreadyRunning = try {
                workManager.getWorkInfosForUniqueWork(PUSH_FETCH_WORK_NAME)
                    .get(200, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .any { it.state == androidx.work.WorkInfo.State.ENQUEUED || it.state == androidx.work.WorkInfo.State.RUNNING }
            } catch (_: Exception) {
                false
            }
            android.util.Log.d(TAG, if (alreadyRunning) "PUSH_FETCH_ALREADY_RUNNING" else "PUSH_FETCH_ENQUEUED")
        }
        val request = androidx.work.OneTimeWorkRequestBuilder<com.foresightlabs.aether.data.push.PushFetchWorker>()
            .setConstraints(
                androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                androidx.work.WorkRequest.MIN_BACKOFF_MILLIS,
                java.util.concurrent.TimeUnit.MILLISECONDS
            )
            .build()
        workManager.enqueueUniqueWork(PUSH_FETCH_WORK_NAME, androidx.work.ExistingWorkPolicy.KEEP, request)
    }

    /**
     * Called only from [PushFetchWorker], for the error-406 case: ensures
     * TDLib is running and waits (bounded) for it to report the fetch a push
     * implied is done, via the race-safe [pushPendingGate]. A timeout is
     * reported as [PushPendingGate.Outcome.TIMED_OUT], never treated as
     * completion -- the Worker maps that to Result.retry() under WorkManager's
     * own backoff, not an internal loop here.
     */
    suspend fun awaitPushFetchCompletion(timeoutMs: Long = PUSH_PENDING_TIMEOUT_MS): com.foresightlabs.aether.data.push.PushPendingGate.Outcome {
        val startGeneration = pushPendingGate.currentGeneration()
        start()
        if (BuildConfig.DEBUG) android.util.Log.d(TAG, "PUSH_FETCH_STARTED")
        val outcome = pushPendingGate.awaitCompletion(startGeneration, timeoutMs)
        if (BuildConfig.DEBUG) {
            android.util.Log.d(
                TAG,
                if (outcome == com.foresightlabs.aether.data.push.PushPendingGate.Outcome.COMPLETED) {
                    "PUSH_FETCH_COMPLETED"
                } else {
                    "PUSH_FETCH_TIMEOUT_RETRY"
                }
            )
        }
        return outcome
    }

    /**
     * GetPushReceiverId is documented as callable synchronously, without a
     * running TDLib instance -- it is pure computation over the payload, not
     * a request to a live client -- so this uses Client.execute directly
     * rather than the async send() queue.
     */
    private fun getPushReceiverId(payload: String): Long {
        return try {
            val res = Client.execute(TdApi.GetPushReceiverId(payload)) as? TdApi.PushReceiverId
            val id = res?.id ?: 0L
            if (BuildConfig.DEBUG && id != 0L) {
                android.util.Log.d(TAG, "PUSH_RECEIVER_ID_RESOLVED id=$id")
            }
            id
        } catch (_: Client.ExecutionException) {
            0L
        } catch (error: Throwable) {
            // This runs before anything else in a push, in a process the push
            // itself may have started -- including on a device where the native
            // library could not load at all. An unroutable push means "process
            // it, there is nowhere else for it to go"; it must never mean an
            // uncaught throw out of the Firebase callback.
            if (BuildConfig.DEBUG) {
                android.util.Log.w(TAG, "PUSH_RECEIVER_ID_UNAVAILABLE error=${error.javaClass.simpleName}")
            }
            0L
        }
    }

    private suspend fun onAuth(state: TdApi.AuthorizationState) {
        if (state !is TdApi.AuthorizationStateWaitTdlibParameters) {
            parametersApplied.complete(Unit)
        }
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> applyParameters()
            is TdApi.AuthorizationStateClosed -> {
                clearSession()
                _authState.value = AuthUiState.Phone()
            }
            is TdApi.AuthorizationStateReady -> {
                _authState.value = AuthUiState.Ready
                afterReady()
                flushPendingFcmToken()
            }
            is TdApi.AuthorizationStateLoggingOut -> {
                _authState.value = AuthUiState.LoggingOut
            }
            else -> {
                withContext(mainHandler) {
                    _authState.value = TelegramMappers.mapAuthState(state)
                }
            }
        }
        if (state is TdApi.AuthorizationStateWaitPhoneNumber ||
            state is TdApi.AuthorizationStateWaitCode ||
            state is TdApi.AuthorizationStateWaitPassword
        ) {
            // keep mapped state as-is except overlay loading flags from UI
        }
    }

    private suspend fun applyParameters() {
        val files = application.filesDir
        val dbDir = files.resolve("tdlib").apply { mkdirs() }.absolutePath
        val filesDir = files.resolve("tdlib-files").apply { mkdirs() }.absolutePath
        val params = TdApi.SetTdlibParameters(
            false,
            dbDir,
            filesDir,
            ByteArray(0),
            true,
            true,
            true,
            true,
            BuildConfig.TELEGRAM_API_ID,
            BuildConfig.TELEGRAM_API_HASH,
            Locale.getDefault().toLanguageTag().ifBlank { "en" },
            Build.MODEL.ifBlank { "Android" },
            "Android ${Build.VERSION.RELEASE}",
            "Aether ${BuildConfig.VERSION_NAME}"
        )
        when (val result = send(params)) {
            is TdApi.Ok -> {
                parametersApplied.complete(Unit)
            }
            is TdApi.Error -> {
                if (BuildConfig.DEBUG) {
                    android.util.Log.e(TAG, "SetTdlibParameters failed: ${result.code}")
                }
                parametersApplied.complete(Unit)
                _authState.value = AuthUiState.Unsupported(TdErrors.userMessage(result))
            }
            else -> {
                parametersApplied.complete(Unit)
            }
        }
        send(TdApi.SetNetworkType(TdApi.NetworkTypeOther()))
        configureNotificationOptions()
    }

    private suspend fun configureNotificationOptions() {
        if (!notificationOptionsConfigured.compareAndSet(false, true)) return
        when (val countRes = send(TdApi.SetOption("notification_group_count_max", TdApi.OptionValueInteger(5)))) {
            is TdApi.Ok -> {
                if (BuildConfig.DEBUG) android.util.Log.d(TAG, "NOTIFICATION_OPTION_GROUP_COUNT_OK")
            }
            is TdApi.Error -> {
                if (BuildConfig.DEBUG) android.util.Log.e(TAG, "NOTIFICATION_OPTION_GROUP_COUNT_ERROR code=${countRes.code} msg=${countRes.message}")
            }
            else -> {}
        }

        when (val sizeRes = send(TdApi.SetOption("notification_group_size_max", TdApi.OptionValueInteger(10)))) {
            is TdApi.Ok -> {
                if (BuildConfig.DEBUG) android.util.Log.d(TAG, "NOTIFICATION_OPTION_GROUP_SIZE_OK")
            }
            is TdApi.Error -> {
                if (BuildConfig.DEBUG) android.util.Log.e(TAG, "NOTIFICATION_OPTION_GROUP_SIZE_ERROR code=${sizeRes.code} msg=${sizeRes.message}")
            }
            else -> {}
        }
    }

    private suspend fun afterReady() {
        configureNotificationOptions()
        when (val me = send(TdApi.GetMe())) {
            is TdApi.User -> {
                myUserId = me.id
                users[me.id] = me
                requestUserPhoto(me)
                publishMe()
            }
        }
        chatsFullyLoaded = false
        loadAllChats()
    }

    private suspend fun loadAllChats() {
        chatLoadMutex.withLock {
            _isLoadingChats.value = true
            try {
                var guard = 0
                while (!chatsFullyLoaded && guard < 50) {
                    guard++
                    when (val result = send(TdApi.LoadChats(TdApi.ChatListMain(), 100))) {
                        is TdApi.Error -> {
                            if (result.code == 404) {
                                chatsFullyLoaded = true
                            } else if (BuildConfig.DEBUG) {
                                android.util.Log.w(TAG, "LoadChats ${result.code}")
                            }
                            break
                        }
                        else -> delay(50)
                    }
                }
            } finally {
                _isLoadingChats.value = false
                publishChats()
            }
        }
    }

    private fun applyPosition(chatId: Long, position: TdApi.ChatPosition) {
        val chat = chats[chatId] ?: return
        val existing = chat.positions?.toMutableList() ?: mutableListOf()
        existing.removeAll { it.list?.javaClass == position.list?.javaClass }
        if (position.order != 0L) {
            existing.add(position)
        }
        chat.positions = existing.toTypedArray()
        publishChats()
    }

    private fun onFile(file: TdApi.File) {
        val local = file.local
        val path = local?.path
        val hasDiskFile = !path.isNullOrBlank() && java.io.File(path).let { it.exists() && it.length() > 0L }
        val isComplete = local?.isDownloadingCompleted == true || hasDiskFile
        if (BuildConfig.DEBUG) {
            val hasLocal = isComplete && !path.isNullOrBlank()
            android.util.Log.d(TAG, "MEDIA_FILE_UPDATE fileId=${file.id} hasLocal=$hasLocal size=${file.size} expectedSize=${file.expectedSize} elapsedRealtime=${android.os.SystemClock.elapsedRealtime()}")
        }
        when {
            isComplete && !path.isNullOrBlank() -> {
                photoPaths["file:${file.id}"] = path
                failedDownloads.remove(file.id)
                activeDownloads.remove(file.id)
            }
            local?.isDownloadingActive == true -> {
                activeDownloads[file.id] = true
                failedDownloads.remove(file.id)
            }
            activeDownloads.remove(file.id) != null -> {
                // Was actively downloading and now is neither active nor
                // completed -- TDLib genuinely stopped without finishing, not
                // merely "hasn't started yet".
                failedDownloads[file.id] = true
                requestedFiles.remove(file.id)
            }
        }
        val affectedUsers = usersByAvatarFile[file.id].orEmpty()
        affectedUsers.forEach { userId ->
            if (userId == myUserId) publishMe()
        }
        if (affectedUsers.isNotEmpty() || chatsByAvatarFile[file.id].orEmpty().isNotEmpty()) publishChats()

        // A file update belongs only to the messages in this reverse index. This
        // keeps an image download from walking every loaded conversation.
        val affectedByChat = mediaReferenceIndex.referencesFor(file.id).groupBy { it.chatId }
        affectedByChat.forEach { (chatId, references) ->
            val ids = references.map { it.messageId }.toSet()
            conversationFlows[chatId]?.update { current ->
                current.map { existing ->
                    val messageId = existing.id.toLongOrNull()
                    if (messageId == null || messageId !in ids) {
                        existing
                    } else {
                        rawMessages[messageId]?.let { raw ->
                            mapUiMessage(raw).copy(presentationKey = existing.presentationKey)
                        } ?: existing
                    }
                }
            }
            references.forEach { publishMessageEvent(chatId, it.messageId, MessageMotionEventType.MEDIA_UPDATED) }
        }
    }

    private fun requestChatPhoto(chat: TdApi.Chat) {
        val file = chat.photo?.small ?: return
        if (TelegramMappers.localPath(file) != null) return
        if (file.local?.canBeDownloaded == true) {
            scope.launch { downloadFile(file.id) }
        }
    }

    private fun requestUserPhoto(user: TdApi.User) {
        val file = user.profilePhoto?.small ?: return
        if (TelegramMappers.localPath(file) != null) return
        if (file.local?.canBeDownloaded == true) {
            scope.launch { downloadFile(file.id) }
        }
    }

    private fun photoPathForChat(chat: TdApi.Chat): String? {
        val file = chat.photo?.small
        return TelegramMappers.localPath(file) ?: file?.id?.let { photoPaths["file:$it"] }
    }

    private fun indexChatAvatar(chat: TdApi.Chat) {
        val fileId = chat.photo?.small?.id ?: 0
        val previous = chatAvatarFileByChat.put(chat.id, fileId)
        if (previous != null && previous != 0 && previous != fileId) {
            chatsByAvatarFile[previous]?.remove(chat.id)
        }
        if (fileId != 0) {
            chatsByAvatarFile.computeIfAbsent(fileId) { ConcurrentHashMap.newKeySet() }.add(chat.id)
        }
    }

    private fun indexUserAvatar(user: TdApi.User) {
        val fileId = user.profilePhoto?.small?.id ?: 0
        val previous = userAvatarFileByUser.put(user.id, fileId)
        if (previous != null && previous != 0 && previous != fileId) {
            usersByAvatarFile[previous]?.remove(user.id)
        }
        if (fileId != 0) {
            usersByAvatarFile.computeIfAbsent(fileId) { ConcurrentHashMap.newKeySet() }.add(user.id)
        }
    }

    private fun publishMe() {
        val me = users[myUserId] ?: return
        indexUserAvatar(me)
        _currentUser.value = TelegramMappers.mapUser(me, TelegramMappers.localPath(me.profilePhoto?.small) ?: photoPaths["file:${me.profilePhoto?.small?.id}"])
    }

    private fun publishChats(immediate: Boolean = false) {
        if (immediate) {
            doPublishChats()
            return
        }
        if (publishChatsJob?.isActive == true) return
        publishChatsJob = scope.launch {
            delay(40)
            doPublishChats()
        }
    }

    private fun doPublishChats() {
        val mapped = chats.values.map { mapUiChat(it) }
            .filter { ChatOrdering.isInMainList(it.order) }
            .sortedWith { a, b -> ChatOrdering.compare(a.order, b.order) }
        _chatList.value = mapped
    }

    private fun mapUiChat(chat: TdApi.Chat): Chat {
        indexChatAvatar(chat)
        (chat.type as? TdApi.ChatTypePrivate)?.userId?.let { users[it]?.let(::indexUserAvatar) }
        val hasUnseen = activeStories[chat.id]?.let { stories ->
            stories.stories.any { it.storyId > stories.maxReadStoryId }
        } ?: false
        return TelegramMappers.mapChat(
            chat,
            myUserId,
            users,
            photoPathForChat(chat),
            typing[chat.id],
            hasUnseenPulse = hasUnseen,
            isForum = isForum(chat.id)
        )
    }

    private suspend fun fetchStoriesFor(active: TdApi.ChatActiveStories) {
        val chatId = active.chatId
        for (info in active.stories) {
            val key = "${chatId}_${info.storyId}"
            if (storiesCache[key] == null) {
                when (val result = send(TdApi.GetStory(chatId, info.storyId, false))) {
                    is TdApi.Story -> {
                        val senderChat = chats[chatId]
                        val senderUser = users[chatId]
                        val senderName = senderChat?.title ?: senderUser?.let {
                            listOf(it.firstName, it.lastName).filter { n -> !n.isNullOrBlank() }.joinToString(" ")
                        } ?: "Contact"
                        storiesCache[key] = TelegramMappers.mapStory(result, senderName)
                    }
                    else -> {}
                }
            }
        }
        publishPulses()
    }

    private fun publishPulses(immediate: Boolean = false) {
        if (immediate) {
            doPublishPulses()
            return
        }
        if (publishPulsesJob?.isActive == true) return
        publishPulsesJob = scope.launch {
            delay(40)
            doPublishPulses()
        }
    }

    private fun doPublishPulses() {
        val allPulses = mutableListOf<com.foresightlabs.aether.domain.model.UserPulse>()
        var mine: com.foresightlabs.aether.domain.model.UserPulse? = null

        activeStories.forEach { (chatId, active) ->
            val chat = chats[chatId]
            val user = users[chatId]
            val name = chat?.title ?: user?.let {
                listOf(it.firstName, it.lastName).filter { n -> !n.isNullOrBlank() }.joinToString(" ")
            } ?: "Contact"
            val username = user?.usernames?.activeUsernames?.firstOrNull()?.let { "@$it" }.orEmpty()
            val photoPath = photoPathForChat(chat ?: TdApi.Chat().apply {
                id = chatId
                this.photo = user?.profilePhoto?.let { p -> TdApi.ChatPhotoInfo().apply { small = p.small } }
            })
            val isOnline = user?.status is TdApi.UserStatusOnline
            val isMine = chatId == myUserId

            val items = active.stories.mapNotNull { info ->
                storiesCache["${chatId}_${info.storyId}"] ?: com.foresightlabs.aether.domain.model.StoryItem(
                    id = info.storyId,
                    senderChatId = chatId,
                    senderName = name,
                    dateSeconds = info.date,
                    expiresInSeconds = 86400,
                    isForCloseFriends = info.isForCloseFriends
                )
            }.sortedBy { it.dateSeconds }

            val pulse = com.foresightlabs.aether.domain.model.UserPulse(
                chatId = chatId,
                name = name,
                username = username,
                avatarInitials = TelegramMappers.initials(name),
                avatarGradient = TelegramMappers.gradientFor(chatId),
                photoPath = photoPath,
                isOnline = isOnline,
                stories = items,
                maxReadStoryId = active.maxReadStoryId,
                isMine = isMine
            )

            if (isMine) {
                mine = pulse
            } else if (items.isNotEmpty()) {
                allPulses.add(pulse)
            }
        }

        _myPulse.value = mine
        _pulses.value = allPulses.sortedWith(
            compareByDescending<com.foresightlabs.aether.domain.model.UserPulse> { it.hasUnseen }
                .thenByDescending { it.latestStory?.dateSeconds ?: 0 }
        )
    }

    suspend fun openStory(chatId: Long, storyId: Int) {
        send(TdApi.OpenStory(chatId, storyId))
    }

    suspend fun closeStory(chatId: Long, storyId: Int) {
        send(TdApi.CloseStory(chatId, storyId))
    }

    suspend fun setStoryReaction(chatId: Long, storyId: Int, emoji: String): Result<Unit> {
        val reactionType = TdApi.ReactionTypeEmoji(emoji)
        return sendExpectOk(TdApi.SetStoryReaction(chatId, storyId, reactionType, false))
    }

    suspend fun postStoryPhoto(
        photoPath: String,
        caption: String,
        privacy: com.foresightlabs.aether.domain.model.StoryPrivacy
    ): Result<com.foresightlabs.aether.domain.model.StoryItem> {
        val content = TdApi.InputStoryContentPhoto(
            TdApi.InputFileLocal(photoPath),
            intArrayOf()
        )
        val privacySettings: TdApi.StoryPrivacySettings = when (privacy) {
            com.foresightlabs.aether.domain.model.StoryPrivacy.EVERYONE -> TdApi.StoryPrivacySettingsEveryone()
            com.foresightlabs.aether.domain.model.StoryPrivacy.CONTACTS -> TdApi.StoryPrivacySettingsContacts()
            com.foresightlabs.aether.domain.model.StoryPrivacy.CLOSE_FRIENDS -> TdApi.StoryPrivacySettingsCloseFriends()
        }
        val formatted = TdApi.FormattedText(caption, emptyArray())
        val post = TdApi.PostStory(
            myUserId,
            content,
            null,
            formatted,
            privacySettings,
            intArrayOf(),
            86400,
            null,
            false,
            false
        )
        return when (val result = send(post)) {
            is TdApi.Story -> {
                val item = TelegramMappers.mapStory(result, "You")
                storiesCache["${myUserId}_${result.id}"] = item
                publishPulses()
                Result.success(item)
            }
            is TdApi.Error -> Result.failure(IllegalStateException(TdErrors.userMessage(result)))
            else -> Result.failure(IllegalStateException("Failed to post story"))
        }
    }

    suspend fun deleteStory(storyId: Int): Result<Unit> {
        return sendExpectOk(TdApi.DeleteStory(myUserId, storyId))
    }

    suspend fun checkCanPostStory(): Boolean {
        return when (send(TdApi.CanPostStory(myUserId))) {
            is TdApi.CanPostStoryResultOk -> true
            else -> false
        }
    }

    private fun mapUiMessage(message: TdApi.Message): Message {
        rawMessages[message.id] = message
        val lastRead = chats[message.chatId]?.lastReadOutboxMessageId ?: 0L
        val mapped = TelegramMappers.mapMessage(
            message = message,
            users = users,
            chats = chats,
            myUserId = myUserId,
            lastReadOutboxMessageId = lastRead,
            reply = replyPreview(message),
            resolvePath = ::resolveMediaPath,
            isDownloadFailed = { failedDownloads[it] == true }
        )
        mediaReferenceIndex.replace(
            MessageMediaReference(message.chatId, message.id),
            mapped.mediaItems.mapNotNull { it.fileId.takeIf { fileId -> fileId != 0 } }.toSet()
        )
        return mapped
    }

    /**
     * Local path for a message's media, requesting the download if it is missing.
     *
     * Returning null is the honest answer while a file is still arriving — the
     * bubble shows its own pending state rather than a placeholder pretending to be
     * the content.
     */
    private fun resolveMediaPath(file: TdApi.File?): String? {
        if (file == null) return null
        TelegramMappers.localPath(file)?.let { return it }
        val cached = photoPaths["file:${file.id}"]
        if (!cached.isNullOrBlank() && java.io.File(cached).let { it.exists() && it.length() > 0L }) {
            return cached
        }
        // A file TDLib already gave up on is not retried automatically -- that
        // would spin forever against a dead file. The UI offers a real retry
        // affordance instead; see retryMediaDownload.
        if (failedDownloads[file.id] != true &&
            file.local?.canBeDownloaded == true &&
            file.local?.isDownloadingActive != true
        ) {
            scope.launch { downloadFile(file.id) }
        }
        return null
    }

    private fun replyPreview(message: TdApi.Message): ReplyPreview? {
        val reply = message.replyTo as? TdApi.MessageReplyToMessage ?: return null
        if (reply.messageId == 0L) return null
        val targetChatId = reply.chatId.takeIf { it != 0L } ?: message.chatId
        val target = rawMessages[reply.messageId]?.takeIf { it.chatId == targetChatId }
        val targetKey = ReplyTarget(targetChatId, reply.messageId)
        if (target == null && targetKey !in unavailableReplyTargets) {
            requestReplyTarget(targetKey)
        }
        return TelegramMappers.mapReplyPreview(
            reply = reply,
            target = target,
            users = users,
            chats = chats,
            myUserId = myUserId,
            fallbackChatId = targetChatId,
            isResolving = target == null && targetKey !in unavailableReplyTargets,
            isNavigable = targetChatId == message.chatId
        )
    }

    /** Fetches each missing reply target at most once for the lifetime of this client. */
    private fun requestReplyTarget(target: ReplyTarget) {
        if (target.chatId == 0L || target.messageId == 0L) return
        if (replyResolutionJobs.containsKey(target)) return
        val job = scope.launch {
            val result = send(TdApi.GetMessage(target.chatId, target.messageId))
            if (result is TdApi.Message) {
                rawMessages[result.id] = result
                unavailableReplyTargets.remove(target)
            } else {
                unavailableReplyTargets.add(target)
            }
            refreshReplyingMessages(target)
            replyResolutionJobs.remove(target)
        }
        replyResolutionJobs.putIfAbsent(target, job)?.let { job.cancel() }
    }

    /** Re-maps visible parents after a target arrives or is known to be unavailable. */
    private fun refreshReplyingMessages(target: ReplyTarget) {
        conversationFlows.forEach { (chatId, flow) ->
            flow.update { messages ->
                messages.map { current ->
                    val parent = rawMessages[current.id.toLongOrNull() ?: return@map current]
                        ?.takeIf { it.chatId == chatId }
                    val reply = parent?.replyTo as? TdApi.MessageReplyToMessage
                    val parentTarget = reply?.let {
                        ReplyTarget(it.chatId.takeIf { id -> id != 0L } ?: chatId, it.messageId)
                    }
                    if (parent != null && parentTarget == target) mapUiMessage(parent) else current
                }
            }
        }
    }

    private fun refreshAllReplyPreviews() {
        conversationFlows.forEach { (chatId, flow) ->
            flow.update { messages ->
                messages.map { current ->
                    val raw = rawMessages[current.id.toLongOrNull() ?: return@map current]
                        ?.takeIf { it.chatId == chatId }
                    if (raw?.replyTo is TdApi.MessageReplyToMessage) mapUiMessage(raw) else current
                }
            }
        }
    }

    private fun refreshConversationStatuses(chatId: Long) {
        val lastRead = chats[chatId]?.lastReadOutboxMessageId ?: return
        conversationFlows[chatId]?.update { list ->
            list.map { msg ->
                if (msg.isOutgoing && (msg.id.toLongOrNull() ?: 0L) <= lastRead && msg.status != com.foresightlabs.aether.domain.model.MessageStatus.FAILED) {
                    msg.copy(status = com.foresightlabs.aether.domain.model.MessageStatus.READ)
                } else msg
            }
        }
    }

    private fun clearSession() {
        chats.clear()
        users.clear()
        typing.clear()
        photoPaths.clear()
        requestedFiles.clear()
        conversationFlows.clear()
        rawMessages.clear()
        mediaReferenceIndex.clear()
        chatAvatarFileByChat.clear()
        chatsByAvatarFile.clear()
        userAvatarFileByUser.clear()
        usersByAvatarFile.clear()
        replyResolutionJobs.values.forEach { it.cancel() }
        replyResolutionJobs.clear()
        unavailableReplyTargets.clear()
        myUserId = 0L
        chatsFullyLoaded = false
        _currentUser.value = null
        _chatList.value = emptyList()
        // The account this token was registered against no longer has a
        // session; a future login must register fresh rather than trusting
        // this as already-done.
        pushRegistration.onSessionCleared()
        pushReceiverId = null
        persistPushReceiverId(null)
    }

    private suspend fun sendExpectOk(function: TdApi.Function<*>): Result<Unit> {
        return when (val result = send(function)) {
            is TdApi.Ok -> Result.success(Unit)
            is TdApi.Error -> Result.failure(IllegalStateException(TdErrors.userMessage(result)))
            else -> Result.success(Unit)
        }
    }

    private suspend fun send(function: TdApi.Function<*>): TdApi.Object {
        val active = client ?: return TdApi.Error(400, "TDLib is not running")
        return suspendCancellableCoroutine { cont ->
            active.send(function) { result ->
                if (cont.isActive) cont.resume(result)
            }
        }
    }

    companion object {
        private const val TAG = "AetherTd"

        /** Low: a preview thumbnail must never outrank the media the user opened. */
        private const val THUMBNAIL_PRIORITY = 8
        // Defensive-only guard for the local-fill loop in collectHistoryPage().
        // The loop's real stopping conditions are "collected enough" and "a
        // round added zero new unique messages" -- both fire well before this
        // in normal operation, since local db batches are typically dozens of
        // messages, not one at a time. This cap exists only to bound the
        // pathological case where local rounds keep making some progress (so
        // the zero-new-unique check never fires) without ever reaching the
        // page size; it is generous relative to typical page sizes so it does
        // not cut off local history that is still genuinely being found and
        // force an unnecessary network round-trip.
        private const val MAX_LOCAL_FILL_ROUNDS = 20
        // Bounds how long awaitPushFetchCompletion (run from PushFetchWorker,
        // only for the error-406 case) waits for UpdateHavePendingNotifications
        // to clear before returning -- long enough for a normal fetch to
        // land, short enough that a stuck fetch does not hold the background
        // work open indefinitely. One wait, one timeout: this is not a retry
        // loop, and it is never used on the ProcessPushNotification success
        // path, which needs no waiting at all.
        private const val PUSH_PENDING_TIMEOUT_MS = 8_000L
        // How long a push waits for TDLib parameters to be applied before
        // sending ProcessPushNotification. In a process the push itself
        // started, initialization has only just begun; this bounds that wait
        // so a stuck start-up cannot hold the push callback open.
        private const val PUSH_PARAMETERS_TIMEOUT_MS = 5_000L
        private const val PREF_PUSH_RECEIVER_ID = "push_receiver_id"
        private const val PUSH_FETCH_WORK_NAME = "aether_push_fetch"
    }
}
