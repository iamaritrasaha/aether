package com.foresightlabs.aether.data.telegram

import android.app.Application
import android.os.Build
import com.foresightlabs.aether.BuildConfig
import com.foresightlabs.aether.domain.model.AuthUiState
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ConnectionStatus
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.User
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.NativeLoader
import org.drinkless.tdlib.TdApi
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
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

    private val _pulses = MutableStateFlow<List<com.foresightlabs.aether.domain.model.UserPulse>>(emptyList())
    val pulses: StateFlow<List<com.foresightlabs.aether.domain.model.UserPulse>> = _pulses.asStateFlow()

    private val _myPulse = MutableStateFlow<com.foresightlabs.aether.domain.model.UserPulse?>(null)
    val myPulse: StateFlow<com.foresightlabs.aether.domain.model.UserPulse?> = _myPulse.asStateFlow()

    private val _canPostPulse = MutableStateFlow(true)
    val canPostPulse: StateFlow<Boolean> = _canPostPulse.asStateFlow()

    private val _isLoadingChats = MutableStateFlow(false)
    val isLoadingChats: StateFlow<Boolean> = _isLoadingChats.asStateFlow()

    @Volatile private var myUserId: Long = 0L
    @Volatile private var chatsFullyLoaded = false
    private val chatLoadMutex = Mutex()
    private var publishChatsJob: Job? = null
    private var publishPulsesJob: Job? = null

    fun start() {
        if (!BuildConfig.HAS_TELEGRAM_CREDENTIALS) {
            _authState.value = AuthUiState.MissingCredentials
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
            return
        }
        val verbosity = if (BuildConfig.DEBUG) 1 else 0
        try {
            Client.execute(TdApi.SetLogVerbosityLevel(verbosity))
        } catch (_: Client.ExecutionException) {
        }
        client = Client.create(
            { update -> scope.launch { handleUpdate(update) } },
            { error -> if (BuildConfig.DEBUG) android.util.Log.w(TAG, "TDLib update handler error", error) },
            { error -> if (BuildConfig.DEBUG) android.util.Log.w(TAG, "TDLib handler error", error) }
        )
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

    suspend fun registerUser(firstName: String, lastName: String): Result<Unit> {
        return sendExpectOk(TdApi.RegisterUser(firstName, lastName, false))
    }

    suspend fun resendCode(): Result<Unit> {
        return sendExpectOk(TdApi.ResendAuthenticationCode(null))
    }

    suspend fun logOut(): Result<Unit> {
        _authState.value = AuthUiState.LoggingOut
        return sendExpectOk(TdApi.LogOut())
    }

    fun resetAuthToPhone() {
        _authState.value = AuthUiState.Phone()
    }

    fun chat(chatId: Long): Chat? = _chatList.value.firstOrNull { it.id == chatId.toString() }
        ?: chats[chatId]?.let { TelegramMappers.mapChat(it, myUserId, users, photoPathForChat(it), typing[chatId]) }

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

    suspend fun loadHistory(chatId: Long, fromMessageId: Long, limit: Int = 40): List<Message> {
        val result = send(TdApi.GetChatHistory(chatId, fromMessageId, 0, limit, false))
        val messages = (result as? TdApi.Messages)?.messages ?: return emptyList()
        val chat = chats[chatId]
        val lastReadOut = chat?.lastReadOutboxMessageId ?: 0L
        return messages.mapNotNull { td ->
            td ?: return@mapNotNull null
            TelegramMappers.mapMessage(
                message = td,
                users = users,
                chats = chats,
                myUserId = myUserId,
                lastReadOutboxMessageId = lastReadOut,
                reply = replyPreview(td)
            )
        }.reversed()
    }

    suspend fun openChat(chatId: Long) {
        send(TdApi.OpenChat(chatId))
    }

    suspend fun closeChat(chatId: Long) {
        send(TdApi.CloseChat(chatId))
    }

    fun closeChatAsync(chatId: Long) {
        scope.launch { closeChat(chatId) }
    }

    suspend fun viewMessages(chatId: Long, messageIds: LongArray) {
        if (messageIds.isEmpty()) return
        send(TdApi.ViewMessages(chatId, messageIds, null, true))
    }

    suspend fun sendText(chatId: Long, text: String, replyToMessageId: Long?): Result<TdApi.Message> {
        val reply = replyToMessageId?.takeIf { it != 0L }?.let {
            TdApi.InputMessageReplyToMessage(it, null, 0, "")
        }
        val content = TdApi.InputMessageText(
            TdApi.FormattedText(text, emptyArray()),
            null,
            true
        )
        return when (val result = send(TdApi.SendMessage(chatId, null, reply, null, null, content))) {
            is TdApi.Message -> Result.success(result)
            is TdApi.Error -> Result.failure(IllegalStateException(TdErrors.userMessage(result)))
            else -> Result.failure(IllegalStateException("Unexpected send result"))
        }
    }

    suspend fun sendPhoto(chatId: Long, photoPath: String, caption: String, replyToMessageId: Long?): Result<TdApi.Message> {
        val reply = replyToMessageId?.takeIf { it != 0L }?.let {
            TdApi.InputMessageReplyToMessage(it, null, 0, "")
        }
        val content = TdApi.InputMessagePhoto(
            TdApi.InputFileLocal(photoPath),
            null,
            null,
            intArrayOf(),
            0,
            0,
            TdApi.FormattedText(caption, emptyArray()),
            false,
            null,
            false
        )
        return when (val result = send(TdApi.SendMessage(chatId, null, reply, null, null, content))) {
            is TdApi.Message -> Result.success(result)
            is TdApi.Error -> Result.failure(IllegalStateException(TdErrors.userMessage(result)))
            else -> Result.failure(IllegalStateException("Unexpected send photo result"))
        }
    }

    suspend fun sendVideo(
        chatId: Long,
        videoPath: String,
        caption: String,
        duration: Int = 0,
        width: Int = 0,
        height: Int = 0,
        replyToMessageId: Long? = null
    ): Result<TdApi.Message> {
        val reply = replyToMessageId?.takeIf { it != 0L }?.let {
            TdApi.InputMessageReplyToMessage(it, null, 0, "")
        }
        val content = TdApi.InputMessageVideo(
            TdApi.InputFileLocal(videoPath),
            null,
            null,
            0,
            intArrayOf(),
            duration,
            width,
            height,
            true,
            TdApi.FormattedText(caption, emptyArray()),
            false,
            null,
            false
        )
        return when (val result = send(TdApi.SendMessage(chatId, null, reply, null, null, content))) {
            is TdApi.Message -> Result.success(result)
            is TdApi.Error -> Result.failure(IllegalStateException(TdErrors.userMessage(result)))
            else -> Result.failure(IllegalStateException("Unexpected send video result"))
        }
    }

    suspend fun sendVoiceNote(
        chatId: Long,
        voicePath: String,
        duration: Int,
        waveform: ByteArray = ByteArray(0),
        replyToMessageId: Long? = null
    ): Result<TdApi.Message> {
        val reply = replyToMessageId?.takeIf { it != 0L }?.let {
            TdApi.InputMessageReplyToMessage(it, null, 0, "")
        }
        val content = TdApi.InputMessageVoiceNote(
            TdApi.InputFileLocal(voicePath),
            duration,
            waveform,
            TdApi.FormattedText("", emptyArray()),
            null
        )
        return when (val result = send(TdApi.SendMessage(chatId, null, reply, null, null, content))) {
            is TdApi.Message -> Result.success(result)
            is TdApi.Error -> Result.failure(IllegalStateException(TdErrors.userMessage(result)))
            else -> Result.failure(IllegalStateException("Unexpected send voice note result"))
        }
    }

    suspend fun sendDocument(
        chatId: Long,
        docPath: String,
        caption: String = "",
        replyToMessageId: Long? = null
    ): Result<TdApi.Message> {
        val reply = replyToMessageId?.takeIf { it != 0L }?.let {
            TdApi.InputMessageReplyToMessage(it, null, 0, "")
        }
        val content = TdApi.InputMessageDocument(
            TdApi.InputFileLocal(docPath),
            null,
            false,
            TdApi.FormattedText(caption, emptyArray())
        )
        return when (val result = send(TdApi.SendMessage(chatId, null, reply, null, null, content))) {
            is TdApi.Message -> Result.success(result)
            is TdApi.Error -> Result.failure(IllegalStateException(TdErrors.userMessage(result)))
            else -> Result.failure(IllegalStateException("Unexpected send document result"))
        }
    }

    suspend fun editMessage(chatId: Long, messageId: Long, newText: String): Result<TdApi.Message> {
        val content = TdApi.InputMessageText(
            TdApi.FormattedText(newText, emptyArray()),
            null,
            true
        )
        return when (val result = send(TdApi.EditMessageText(chatId, messageId, null, content))) {
            is TdApi.Message -> Result.success(result)
            is TdApi.Error -> Result.failure(IllegalStateException(TdErrors.userMessage(result)))
            else -> Result.failure(IllegalStateException("Unexpected edit message result"))
        }
    }

    suspend fun addReaction(chatId: Long, messageId: Long, emoji: String): Result<Unit> {
        val reaction = TdApi.ReactionTypeEmoji(emoji)
        return sendExpectOk(TdApi.AddMessageReaction(chatId, messageId, reaction, false, true))
    }

    suspend fun pinMessage(chatId: Long, messageId: Long, onlyForSelf: Boolean = false): Result<Unit> {
        return sendExpectOk(TdApi.PinChatMessage(chatId, messageId, false, onlyForSelf))
    }

    suspend fun forwardMessages(toChatId: Long, fromChatId: Long, messageIds: LongArray): Result<Unit> {
        return when (send(TdApi.ForwardMessages(toChatId, null, fromChatId, messageIds, null, false, false))) {
            is TdApi.Messages -> Result.success(Unit)
            is TdApi.Error -> Result.failure(IllegalStateException("Failed to forward"))
            else -> Result.failure(IllegalStateException("Unexpected forward result"))
        }
    }

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

    suspend fun deleteMessages(chatId: Long, messageIds: LongArray): Result<Unit> {
        return sendExpectOk(TdApi.DeleteMessages(chatId, messageIds, true))
    }

    suspend fun sendTyping(chatId: Long) {
        send(TdApi.SendChatAction(chatId, null, null, TdApi.ChatActionTyping()))
    }

    suspend fun searchChats(query: String): List<Chat> {
        if (query.isBlank()) return _chatList.value
        val local = send(TdApi.SearchChats(query, 40))
        val ids = (local as? TdApi.Chats)?.chatIds ?: longArrayOf()
        return ids.map { id -> chat(id) ?: chats[id]?.let { mapUiChat(it) } }.filterNotNull()
    }

    suspend fun downloadFile(fileId: Int) {
        if (requestedFiles.putIfAbsent(fileId, true) != null) return
        send(TdApi.DownloadFile(fileId, 16, 0, 0, false))
    }

    fun messagesFlow(chatId: Long): StateFlow<List<Message>> {
        return conversationFlows.getOrPut(chatId) {
            MutableStateFlow(emptyList())
        }.asStateFlow()
    }

    fun upsertConversation(chatId: Long, incoming: List<Message>, prepend: Boolean) {
        conversationFlows.getOrPut(chatId) { MutableStateFlow(emptyList()) }.update { current ->
            val byId = LinkedHashMap<String, Message>()
            if (prepend) {
                incoming.forEach { byId[it.id] = it }
                current.forEach { byId.putIfAbsent(it.id, it) }
            } else {
                current.forEach { byId[it.id] = it }
                incoming.forEach { byId[it.id] = it }
            }
            byId.values.sortedBy { it.dateSeconds.toLong() * 1_000_000 + (it.id.toLongOrNull() ?: 0L) }
        }
    }

    fun replaceMessage(chatId: Long, oldId: String, newMessage: Message) {
        conversationFlows[chatId]?.update { list ->
            val without = list.filterNot { it.id == oldId || it.id == newMessage.id }
            (without + newMessage).sortedBy { it.dateSeconds.toLong() * 1_000_000 + (it.id.toLongOrNull() ?: 0L) }
        }
    }

    fun removeMessages(chatId: Long, ids: Set<String>) {
        conversationFlows[chatId]?.update { list -> list.filterNot { it.id in ids } }
    }

    private val conversationFlows = ConcurrentHashMap<Long, MutableStateFlow<List<Message>>>()

    private suspend fun handleUpdate(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateAuthorizationState -> onAuth(update.authorizationState)
            is TdApi.UpdateConnectionState -> _connection.value = TelegramMappers.mapConnection(update.state)
            is TdApi.UpdateUser -> {
                users[update.user.id] = update.user
                requestUserPhoto(update.user)
                if (update.user.id == myUserId) publishMe()
                publishChats()
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
                requestChatPhoto(update.chat)
                publishChats()
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
            }
            is TdApi.UpdateChatPhoto -> {
                chats[update.chatId]?.photo = update.photo
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
                chats[msg.chatId]?.lastMessage = msg
                upsertConversation(msg.chatId, listOf(mapUiMessage(msg)), prepend = false)
                publishChats()
            }
            is TdApi.UpdateMessageContent -> {
                conversationFlows[update.chatId]?.update { list ->
                    list.map { current ->
                        if (current.id == update.messageId.toString()) {
                            val (text, type) = TelegramMappers.mapContent(update.newContent)
                            current.copy(text = text, type = type, isEdited = true)
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
            }
            is TdApi.UpdateMessageSendSucceeded -> {
                replaceMessage(update.message.chatId, update.oldMessageId.toString(), mapUiMessage(update.message))
                chats[update.message.chatId]?.lastMessage = update.message
                publishChats()
            }
            is TdApi.UpdateMessageSendFailed -> {
                replaceMessage(update.message.chatId, update.oldMessageId.toString(), mapUiMessage(update.message))
            }
            is TdApi.UpdateDeleteMessages -> {
                if (update.fromCache) return
                removeMessages(update.chatId, update.messageIds.map { it.toString() }.toSet())
            }
            is TdApi.UpdateFile -> onFile(update.file)
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

    private suspend fun onAuth(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> applyParameters()
            is TdApi.AuthorizationStateClosed -> {
                clearSession()
                _authState.value = AuthUiState.Phone()
            }
            is TdApi.AuthorizationStateReady -> {
                _authState.value = AuthUiState.Ready
                afterReady()
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
            is TdApi.Error -> {
                if (BuildConfig.DEBUG) {
                    android.util.Log.e(TAG, "SetTdlibParameters failed: ${result.code}")
                }
                _authState.value = AuthUiState.Unsupported(TdErrors.userMessage(result))
            }
        }
        send(TdApi.SetNetworkType(TdApi.NetworkTypeOther()))
    }

    private suspend fun afterReady() {
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
        val path = TelegramMappers.localPath(file) ?: return
        photoPaths["file:${file.id}"] = path
        users.values.filter { it.profilePhoto?.small?.id == file.id }.forEach { publishMe() }
        publishChats()
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

    private fun publishMe() {
        val me = users[myUserId] ?: return
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
        val hasUnseen = activeStories[chat.id]?.let { stories ->
            stories.stories.any { it.storyId > stories.maxReadStoryId }
        } ?: false
        return TelegramMappers.mapChat(chat, myUserId, users, photoPathForChat(chat), typing[chat.id], hasUnseenPulse = hasUnseen)
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
        val lastRead = chats[message.chatId]?.lastReadOutboxMessageId ?: 0L
        return TelegramMappers.mapMessage(message, users, chats, myUserId, lastRead, replyPreview(message))
    }

    private fun replyPreview(message: TdApi.Message): Message? {
        val reply = message.replyTo as? TdApi.MessageReplyToMessage ?: return null
        val (text, type) = TelegramMappers.mapContent(reply.content)
        return Message(
            id = reply.messageId.toString(),
            chatId = reply.chatId.toString(),
            senderId = "",
            senderName = "Message",
            text = text.ifBlank { "Message" },
            timestamp = "",
            isOutgoing = false,
            type = type
        )
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
        myUserId = 0L
        chatsFullyLoaded = false
        _currentUser.value = null
        _chatList.value = emptyList()
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
    }
}
