package com.foresightlabs.aether.data.telegram

import android.app.Application
import android.os.Build
import com.foresightlabs.aether.BuildConfig
import com.foresightlabs.aether.domain.calls.MediaConnectionState
import com.foresightlabs.aether.domain.messages.MessageCapabilities
import com.foresightlabs.aether.domain.messages.SendOptions
import com.foresightlabs.aether.domain.messages.SendSchedule
import com.foresightlabs.aether.domain.text.ReplyQuote
import com.foresightlabs.aether.domain.model.AuthUiState
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ChatFolder
import com.foresightlabs.aether.domain.model.ForumTopicSummary
import com.foresightlabs.aether.domain.model.ConnectionStatus
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.MessageType
import com.foresightlabs.aether.domain.model.StickerItem
import com.foresightlabs.aether.domain.model.StickerSetInfo
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

    private val _activeCallState = MutableStateFlow<com.foresightlabs.aether.domain.model.ActiveCall?>(null)
    val activeCallState: StateFlow<com.foresightlabs.aether.domain.model.ActiveCall?> = _activeCallState.asStateFlow()

    val latestRawCallState = MutableStateFlow<TdApi.Call?>(null)

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

    suspend fun loadHistory(chatId: Long, fromMessageId: Long, limit: Int = 40): List<Message> {
        val result = send(TdApi.GetChatHistory(chatId, fromMessageId, 0, limit, false))
        val messages = (result as? TdApi.Messages)?.messages ?: return emptyList()
        val chat = chats[chatId]
        val lastReadOut = chat?.lastReadOutboxMessageId ?: 0L
        return messages.mapNotNull { td -> td?.let(::mapUiMessage) }.reversed()
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

    suspend fun sendText(
        chatId: Long,
        text: String,
        replyToMessageId: Long?,
        entities: Array<TdApi.TextEntity> = emptyArray(),
        forumTopicId: Int? = null,
        options: TdApi.MessageSendOptions? = null,
        quote: ReplyQuote? = null
    ): Result<TdApi.Message> {
        val content = TdApi.InputMessageText(
            TdApi.FormattedText(text, entities),
            null,
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
        forumTopicId: Int? = null
    ): Result<TdApi.Message> {
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
        forumTopicId: Int? = null
    ): Result<TdApi.Message> {
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

    suspend fun editMessage(chatId: Long, messageId: Long, newText: String): Result<TdApi.Message> {
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
                    null,
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

    /**
     * The TDLib messages behind the mapped ones, so a bubble can be re-mapped when
     * its media finishes downloading without another round trip.
     */
    private val rawMessages = ConcurrentHashMap<Long, TdApi.Message>()

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
            is TdApi.UpdateCall -> handleCallUpdate(update.call)
            is TdApi.UpdateAuthorizationState -> onAuth(update.authorizationState)
            is TdApi.UpdateConnectionState -> _connection.value = TelegramMappers.mapConnection(update.state)
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
                // Re-map through the full presentation path. Mapping only the preview
                // text here used to flatten a photo, poll or formatted message into a
                // plain text bubble the moment anything about it changed — which a
                // poll vote does on every single vote.
                val cached = rawMessages[update.messageId]
                if (cached != null && cached.chatId == update.chatId) {
                    cached.content = update.newContent
                    replaceMessage(update.chatId, update.messageId.toString(), mapUiMessage(cached))
                } else {
                    conversationFlows[update.chatId]?.update { list ->
                        list.map { current ->
                            if (current.id == update.messageId.toString()) {
                                val (text, type) = TelegramMappers.mapContent(update.newContent)
                                current.copy(text = text, type = type)
                            } else current
                        }
                    }
                }
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
        // A message waiting on this file now has something real to show.
        republishConversationsAwaitingMedia()
    }

    /**
     * Re-maps loaded conversations so bubbles blocked on a just-downloaded file pick
     * it up. Only messages still missing their media are re-mapped.
     */
    private fun republishConversationsAwaitingMedia() {
        conversationFlows.forEach { (chatId, flow) ->
            val pending = flow.value.any { it.needsMedia() }
            if (!pending) return@forEach
            val remapped = flow.value.map { existing ->
                if (!existing.needsMedia()) return@map existing
                val raw = rawMessages[existing.id.toLongOrNull() ?: return@map existing]
                    ?: return@map existing
                mapUiMessage(raw)
            }
            flow.value = remapped
        }
    }

    private fun Message.needsMedia(): Boolean = when (type) {
        com.foresightlabs.aether.domain.model.MessageType.IMAGE,
        com.foresightlabs.aether.domain.model.MessageType.ALBUM,
        com.foresightlabs.aether.domain.model.MessageType.ANIMATION,
        com.foresightlabs.aether.domain.model.MessageType.VIDEO_NOTE,
        com.foresightlabs.aether.domain.model.MessageType.AUDIO,
        com.foresightlabs.aether.domain.model.MessageType.STICKER -> mediaItems.isEmpty()
        else -> false
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
        return TelegramMappers.mapMessage(
            message = message,
            users = users,
            chats = chats,
            myUserId = myUserId,
            lastReadOutboxMessageId = lastRead,
            reply = replyPreview(message),
            resolvePath = ::resolveMediaPath
        )
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
        photoPaths["file:${file.id}"]?.let { return it }
        if (file.local?.canBeDownloaded == true && file.local?.isDownloadingActive != true) {
            scope.launch { downloadFile(file.id) }
        }
        return null
    }

    private fun replyPreview(message: TdApi.Message): Message? {
        val reply = message.replyTo as? TdApi.MessageReplyToMessage ?: return null
        val (text, type) = TelegramMappers.mapContent(reply.content)
        // A quote replaces the preview text: the point of quoting is that the reply
        // is about that span, not about the whole message.
        val quote = reply.quote
        val quoted = quote?.text?.text?.takeIf { it.isNotBlank() }
        return Message(
            id = reply.messageId.toString(),
            chatId = reply.chatId.toString(),
            senderId = "",
            senderName = "Message",
            text = quoted ?: text.ifBlank { "Message" },
            timestamp = "",
            isOutgoing = false,
            type = type,
            formatted = quote?.let { TelegramMappers.mapFormattedText(it.text) },
            isQuotedExcerpt = quoted != null
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
