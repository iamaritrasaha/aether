package com.foresightlabs.aether.ui.conversation

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.foresightlabs.aether.AetherApplication
import com.foresightlabs.aether.BuildConfig
import com.foresightlabs.aether.data.telegram.TelegramClient
import com.foresightlabs.aether.data.telegram.TelegramMappers
import com.foresightlabs.aether.domain.text.AetherEntity
import com.foresightlabs.aether.domain.text.AetherText
import com.foresightlabs.aether.data.telegram.LinkPreviewSupport
import com.foresightlabs.aether.domain.text.ComposerFormatting
import com.foresightlabs.aether.domain.text.ComposerLinkPreviewCoordinator
import com.foresightlabs.aether.domain.text.ComposerLinkPreviewState
import com.foresightlabs.aether.domain.text.ReplyQuote
import com.foresightlabs.aether.domain.messages.MessageCapabilities
import com.foresightlabs.aether.domain.messages.MessageMotionEvent
import com.foresightlabs.aether.domain.messages.SendOptions
import com.foresightlabs.aether.domain.search.ConversationSearchState
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.MessageType
import com.foresightlabs.aether.domain.model.AnimationItem
import com.foresightlabs.aether.domain.model.StickerItem
import com.foresightlabs.aether.domain.model.StickerSetInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.foresightlabs.aether.domain.sharing.SharedAttachmentKind
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A draft Telegram already holds for this chat, offered back to the composer
 * when the conversation opens. The reply target rides along: [replyMessageId]
 * is 0 when the draft does not reply to anything.
 */
data class RestoredDraft(
    val text: String,
    val replyMessageId: Long = 0L
)

class ConversationViewModel(
    application: Application,
    private val target: com.foresightlabs.aether.domain.model.ConversationTarget
) : AndroidViewModel(application) {

    constructor(application: Application, chatId: Long) : this(
        application,
        com.foresightlabs.aether.domain.model.ConversationTarget.Chat(chatId)
    )

    private val telegram = (application as AetherApplication).telegram

    // Calls have exactly one entry point. Reaching past this into TelegramClient
    // would skip the media-availability check and ring someone for nothing.
    // Lazy, and only ever touched behind AetherFeatureFlags.CALLS_ENABLED:
    // building the repository initializes the call stack.
    private val calls by lazy { (application as AetherApplication).callsRepository }

    private var activeChatId: Long = when (target) {
        is com.foresightlabs.aether.domain.model.ConversationTarget.Chat -> target.chatId
        is com.foresightlabs.aether.domain.model.ConversationTarget.User -> target.userId
        is com.foresightlabs.aether.domain.model.ConversationTarget.Topic -> target.chatId
    }

    /**
     * The forum topic this screen is showing, or null for a plain conversation.
     *
     * Threaded through every send, draft and search below. It is a field rather than
     * a parameter on each call so a new operation cannot be added that forgets it —
     * the previous shape of this code had six send paths and all six dropped it.
     */
    private val forumTopicId: Int? = target.forumTopicId

    private val _isResolving = MutableStateFlow(
        when (target) {
            is com.foresightlabs.aether.domain.model.ConversationTarget.Chat -> telegram.chat(target.chatId) == null
            is com.foresightlabs.aether.domain.model.ConversationTarget.Topic -> false
            is com.foresightlabs.aether.domain.model.ConversationTarget.User -> true
        }
    )
    val isResolving: StateFlow<Boolean> = _isResolving.asStateFlow()

    private val _resolveError = MutableStateFlow<String?>(null)
    val resolveError: StateFlow<String?> = _resolveError.asStateFlow()

    private val _header = MutableStateFlow<Chat?>(
        when (target) {
            is com.foresightlabs.aether.domain.model.ConversationTarget.Chat -> telegram.chat(target.chatId)
            is com.foresightlabs.aether.domain.model.ConversationTarget.Topic -> telegram.chat(target.chatId)
            is com.foresightlabs.aether.domain.model.ConversationTarget.User -> null
        }
    )
    val header: StateFlow<Chat?> = _header.asStateFlow()

    val messages: StateFlow<List<Message>> = telegram.messagesFlow(activeChatId)

    private val _loadingOlder = MutableStateFlow(false)
    val loadingOlder: StateFlow<Boolean> = _loadingOlder.asStateFlow()

    private val _composerEnabled = MutableStateFlow(true)
    val composerEnabled: StateFlow<Boolean> = _composerEnabled.asStateFlow()

    private val liveLocationCoordinator = (application as? com.foresightlabs.aether.AetherApplication)?.liveLocationCoordinator
        ?: com.foresightlabs.aether.data.location.LiveLocationCoordinator(
            context = application,
            locationProvider = com.foresightlabs.aether.data.location.SystemLocationProvider(application),
            gateway = com.foresightlabs.aether.data.location.TelegramLiveLocationGateway(telegram),
            scope = viewModelScope
        )

    private val _sendError = MutableStateFlow<String?>(null)
    val sendError: StateFlow<String?> = _sendError.asStateFlow()

    /** Dismisses the current error banner, whether by timeout or user tap -- see [ConversationScreen]. */
    fun consumeSendError() {
        _sendError.value = null
    }

    // --- local-only bookmarks --------------------------------------------------

    private val bookmarkStore = (application as AetherApplication).bookmarkStore

    /**
     * Messages of THIS chat the account has bookmarked locally. Purely
     * on-device state -- see [com.foresightlabs.aether.data.local.BookmarkStore];
     * nothing here is sent to Telegram.
     */
    val bookmarkedMessageIds: StateFlow<Set<String>> = bookmarkStore.bookmarksFor(telegram.getMyUserId())
        .map { list -> list.filter { it.chatId == activeChatId }.map { it.messageId.toString() }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun toggleBookmark(message: Message) {
        val messageId = message.id.toLongOrNull() ?: return
        val accountId = telegram.getMyUserId()
        if (accountId == 0L) return
        viewModelScope.launch { bookmarkStore.toggle(accountId, activeChatId, messageId) }
    }

    private val _forwardState = MutableStateFlow<ForwardState>(ForwardState.Idle)
    val forwardState: StateFlow<ForwardState> = _forwardState.asStateFlow()

    private val _capabilities = MutableStateFlow<Map<String, MessageCapabilities>>(emptyMap())

    /** Telegram's per-message capability answers, keyed by message id. */
    val capabilities: StateFlow<Map<String, MessageCapabilities>> = _capabilities.asStateFlow()

    private val _search = MutableStateFlow(ConversationSearchState.Idle)

    /** State of the in-conversation search. */
    val search: StateFlow<ConversationSearchState> = _search.asStateFlow()

    private val _pinnedMessages = MutableStateFlow<List<Message>>(emptyList())

    /** Pinned messages Telegram reports for this chat, beyond those loaded. */
    val pinnedMessages: StateFlow<List<Message>> = _pinnedMessages.asStateFlow()

    /** Message the list should scroll to and briefly highlight, if any. */
    private val _jumpTarget = MutableStateFlow<String?>(null)
    val jumpTarget: StateFlow<String?> = _jumpTarget.asStateFlow()

    /**
     * Telegram's inbox read boundary as it stood when this conversation opened:
     * incoming messages above this id were unread at open. 0 when the chat was
     * fully read. Captured BEFORE openChat marks everything read, so the
     * New-messages divider can honestly mark where the unread traffic began.
     * The server's later state is irrelevant to it -- this is orientation for
     * the reader, not a report back to Telegram.
     */
    private val _unreadBoundaryId = MutableStateFlow(0L)
    val unreadBoundaryId: StateFlow<Long> = _unreadBoundaryId.asStateFlow()

    private fun captureUnreadBoundary(chat: Chat?) {
        _unreadBoundaryId.value =
            if (chat != null && chat.unreadCount > 0) chat.lastReadInboxMessageId else 0L
    }

    /**
     * The server draft, offered to the composer exactly once per open. The
     * screen decides when (and whether) to seed it; re-offering after the
     * user cleared or sent the text would resurrect a message they are done
     * with.
     */
    private val _restoredDraft = MutableStateFlow<RestoredDraft?>(null)
    val restoredDraft: StateFlow<RestoredDraft?> = _restoredDraft.asStateFlow()

    private fun captureServerDraft(chat: Chat?) {
        val text = chat?.draftText?.takeIf { it.isNotBlank() } ?: return
        _restoredDraft.value = RestoredDraft(text, chat.draftReplyMessageId)
    }

    /**
     * The reply the composer is currently holding, recorded so [onCleared]
     * stores the draft WITH its reply context instead of dropping it (it used
     * to, which made every cross-client draft lose what it was answering).
     */
    private var draftReplyId: Long? = null

    fun onDraftReplyChanged(messageId: String?) {
        draftReplyId = messageId?.toLongOrNull()
    }

    /** One-shot server events consumed by visible message rows for motion only. */
    private val _messageMotionEvents = MutableStateFlow<Map<String, MessageMotionEvent>>(emptyMap())
    val messageMotionEvents: StateFlow<Map<String, MessageMotionEvent>> = _messageMotionEvents.asStateFlow()

    /** Conversations a message may be forwarded into, excluding this one. */
    val forwardTargets: StateFlow<List<Chat>> = telegram.chatList
        .onEach { chats ->
            if (BuildConfig.DEBUG) {
                Log.d(FORWARD_LOG_TAG, "FORWARD_TARGETS_RECEIVED count=${chats.size}")
            }
        }
        .map { chats ->
            chats.filter { it.id != activeChatId.toString() && it.canSendText && it.isPersonalChat }
                .also { personalTargets ->
                    if (BuildConfig.DEBUG) {
                        Log.d(
                            FORWARD_LOG_TAG,
                            "FORWARD_TARGET_FILTERED personalCount=${personalTargets.size}"
                        )
                    }
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val viewedIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private var oldestId: Long = 0L
    private var historyComplete = false
    private var opened = false
    private var typingJob: Job? = null
    private var sendInFlight = false
    /** Guards sendPhoto/sendPhotoAlbum/sendVideo the way [sendInFlight] guards text -- a duplicate tap (or duplicate touch event) on Send must not become two TDLib sends. */
    private var mediaSendInFlight = false
    private var pendingDraft: String = ""
    private var searchJob: Job? = null
    private var activeAction: TelegramClient.OutgoingChatAction? = null
    /**
     * Telegram's preview for the link in the draft, if there is one.
     *
     * Held here rather than in composition so that recomposition -- of which
     * typing produces a great deal -- can never restart a request.
     */
    private val linkPreviews = ComposerLinkPreviewCoordinator(viewModelScope) { draft ->
        telegram.linkPreview(draft)
    }

    val linkPreview: StateFlow<ComposerLinkPreviewState> = linkPreviews.state

    init {
        viewModelScope.launch {
            telegram.chatList.collect { list ->
                val found = list.firstOrNull { it.id == activeChatId.toString() } ?: telegram.chat(activeChatId)
                if (found != null) {
                    _header.value = found
                    _composerEnabled.value = found.canSendText
                }
            }
        }
        viewModelScope.launch {
            telegram.messageEvents(activeChatId).collect { event ->
                _messageMotionEvents.update { current ->
                    (current + (event.messageId to event)).let { updated ->
                        if (updated.size <= 80) updated else updated.entries.drop(updated.size - 80).associate { it.key to it.value }
                    }
                }
                delay(1_000)
                _messageMotionEvents.update { current ->
                    if (current[event.messageId]?.token == event.token) current - event.messageId else current
                }
            }
        }
        viewModelScope.launch { start() }
    }

    fun retryResolve() {
        if (_isResolving.value) return
        viewModelScope.launch { start() }
    }

    private suspend fun start() {
        _isResolving.value = true
        _resolveError.value = null
        try {
            when (target) {
                is com.foresightlabs.aether.domain.model.ConversationTarget.Topic -> {
                    activeChatId = target.chatId
                    val resolvedChat = telegram.ensureChatLoaded(target.chatId)
                    if (resolvedChat != null) {
                        _header.value = resolvedChat
                        captureUnreadBoundary(resolvedChat)
                        captureServerDraft(resolvedChat)
                        telegram.openChat(target.chatId)
                        opened = true
                        loadInitial()
                        runPendingJump()
                        refreshPinned()
                    } else {
                        _resolveError.value =
                            "Couldn't load this topic. Please check your network and try again."
                    }
                }
                is com.foresightlabs.aether.domain.model.ConversationTarget.Chat -> {
                    activeChatId = target.chatId
                    val resolvedChat = telegram.ensureChatLoaded(target.chatId)
                    if (resolvedChat != null) {
                        _header.value = resolvedChat
                        captureUnreadBoundary(resolvedChat)
                        captureServerDraft(resolvedChat)
                        telegram.openChat(target.chatId)
                        opened = true
                        loadInitial()
                        runPendingJump()
                        refreshPinned()
                    } else {
                        _resolveError.value = "Couldn't load this conversation. Please check your network and try again."
                    }
                }
                is com.foresightlabs.aether.domain.model.ConversationTarget.User -> {
                    val result = telegram.createPrivateChat(target.userId)
                    result.fold(
                        onSuccess = { resolvedChat ->
                            _header.value = resolvedChat
                            captureUnreadBoundary(resolvedChat)
                            captureServerDraft(resolvedChat)
                            val chatId = resolvedChat.id.toLongOrNull() ?: target.userId
                            activeChatId = chatId
                            telegram.openChat(chatId)
                            opened = true
                            loadInitial()
                            runPendingJump()
                        },
                        onFailure = { error ->
                            _resolveError.value = error.message ?: "Couldn't open chat with this contact. Please try again."
                        }
                    )
                }
            }
        } catch (e: Exception) {
            _resolveError.value = e.message ?: "Failed to open conversation"
        } finally {
            _isResolving.value = false
        }
    }

    /**
     * Renders whatever TDLib's local database already has for this chat, with no
     * network wait -- a short local page does not mean history is short, only
     * that this page was thin, so any shortfall is topped up in the background
     * afterward rather than blocking the first render on it.
     */
    private suspend fun loadInitial() {
        val topic = forumTopicId
        if (topic != null) {
            // GetForumTopicHistory has no onlyLocal option in this TDLib build,
            // so a forum topic's initial load stays network-capable.
            val page = telegram.loadTopicHistory(activeChatId, topic, 0L, HISTORY_PAGE_SIZE)
            if (page.isNotEmpty()) {
                oldestId = page.first().id.toLongOrNull() ?: 0L
                telegram.upsertConversation(activeChatId, page, prepend = true)
                markVisible(page.map { it.id })
            }
            // Conservative fallback for topics only: TDLib can legitimately return
            // a short page without history being exhausted, but there is no
            // onlyLocal signal here to tell the two apart, so this may mark
            // completion a little early for large topics with sparse pages.
            if (page.size < HISTORY_PAGE_SIZE) historyComplete = true
            return
        }
        val local = telegram.loadHistory(
            activeChatId,
            0L,
            HISTORY_PAGE_SIZE,
            allowNetwork = false,
            reason = "INITIAL"
        )
        if (local.messages.isNotEmpty()) {
            oldestId = local.oldestId
            telegram.upsertConversation(activeChatId, local.messages, prepend = true)
            markVisible(local.messages.map { it.id })
        }
        if (local.messages.size >= HISTORY_PAGE_SIZE) return
        // Local cache didn't fill the first page. Top up over the network in the
        // background: whatever was just rendered from cache stays on screen the
        // whole time -- upsertConversation merges by id, it never clears the list.
        viewModelScope.launch {
            val filled = telegram.loadHistory(
                activeChatId,
                local.oldestId,
                HISTORY_PAGE_SIZE - local.messages.size,
                allowNetwork = true,
                reason = "INITIAL_TOPUP"
            )
            if (filled.messages.isNotEmpty()) {
                oldestId = filled.oldestId
                telegram.upsertConversation(activeChatId, filled.messages, prepend = true)
            }
            if (filled.endOfHistory) historyComplete = true
        }
    }

    fun loadOlder() {
        if (historyComplete || _loadingOlder.value || oldestId == 0L) return
        val topic = forumTopicId
        viewModelScope.launch {
            _loadingOlder.value = true
            if (topic != null) {
                val page = telegram.loadTopicHistory(activeChatId, topic, oldestId, HISTORY_PAGE_SIZE)
                if (page.isEmpty()) {
                    historyComplete = true
                } else {
                    oldestId = page.first().id.toLongOrNull() ?: oldestId
                    telegram.upsertConversation(activeChatId, page, prepend = true)
                }
            } else {
                // Local-first fill, network-capable fallback only once the cache
                // truly has nothing left at this boundary. endOfHistory is only
                // ever set once a network-capable request returns no new unique
                // messages -- a short page on its own never sets it.
                val page = telegram.loadHistory(
                    activeChatId,
                    oldestId,
                    HISTORY_PAGE_SIZE,
                    allowNetwork = true,
                    reason = "PAGINATION"
                )
                if (page.messages.isNotEmpty()) {
                    oldestId = page.oldestId
                    telegram.upsertConversation(activeChatId, page.messages, prepend = true)
                }
                if (page.endOfHistory) historyComplete = true
            }
            _loadingOlder.value = false
        }
    }

    /**
     * Sends text, carrying whatever formatting the composer produced.
     *
     * Entities are sanitised against the trimmed text first: trailing whitespace is
     * removed before sending, and a span that pointed into it would otherwise run
     * past the end of the message.
     */
    fun send(
        text: String,
        replyToId: String?,
        formatting: List<AetherEntity> = emptyList(),
        quote: ReplyQuote? = null,
        options: SendOptions = SendOptions.Default
    ) {
        val trimmedEnd = text.trimEnd()
        if (trimmedEnd.isEmpty() || sendInFlight) return
        val entities = TelegramMappers.toTdEntities(
            AetherText(
                text = trimmedEnd,
                entities = ComposerFormatting.sanitise(formatting, trimmedEnd.length)
            )
        )
        val previewOptions = LinkPreviewSupport.optionsFor(linkPreviews.intentFor(trimmedEnd))
        sendInFlight = true
        viewModelScope.launch {
            val result = telegram.sendText(
                chatId = activeChatId,
                text = trimmedEnd,
                replyToMessageId = replyToId?.toLongOrNull(),
                entities = entities,
                forumTopicId = forumTopicId,
                options = telegram.sendOptionsOf(options),
                quote = quote?.takeIf { !it.isEmpty },
                linkPreviewOptions = previewOptions
            )
            sendInFlight = false
            if (result.isSuccess) clearPendingDraft()
            result.exceptionOrNull()?.message?.let { _sendError.value = it }
        }
    }

    /** High-priority request for a media file's full bytes, used when the viewer opens on it. */
    fun requestFullMediaDownload(fileId: Int) {
        if (fileId == 0) return
        telegram.requestFullMediaDownload(fileId)
    }

    /** Re-requests a media file whose download TDLib already gave up on. */
    fun retryMediaDownload(fileId: Int) {
        if (fileId == 0) return
        telegram.retryMediaDownload(fileId)
    }

    fun sendPhoto(photoPath: String, caption: String = "", replyToId: String? = null, viewOnce: Boolean = false) {
        if (mediaSendInFlight) return
        mediaSendInFlight = true
        viewModelScope.launch {
            val result = telegram.sendPhoto(activeChatId, photoPath, caption, replyToId?.toLongOrNull(), forumTopicId, viewOnce)
            mediaSendInFlight = false
            result.exceptionOrNull()?.message?.let { _sendError.value = it }
        }
    }

    /**
     * Several photos as one Telegram album.
     *
     * The album path TelegramClient already owns; nothing about a share needed a
     * second one. A single photo goes through the ordinary photo send inside it.
     */
    fun sendPhotoAlbum(photoPaths: List<String>, caption: String = "", replyToId: String? = null) {
        if (photoPaths.isEmpty() || mediaSendInFlight) return
        mediaSendInFlight = true
        viewModelScope.launch {
            val result = telegram.sendPhotoAlbum(
                activeChatId,
                photoPaths,
                caption,
                replyToId?.toLongOrNull(),
                forumTopicId
            )
            mediaSendInFlight = false
            result.exceptionOrNull()?.message?.let { _sendError.value = it }
        }
    }

    /**
     * Sends a mixed share (photos + videos + files) as one guarded batch.
     *
     * The per-item senders' `mediaSendInFlight` guard blocks CONCURRENT calls,
     * which is correct for a double-tapped send button but silently dropped
     * every item after the first when the share path issued its sends
     * back-to-back. Here the guard is taken ONCE and the items are sent
     * sequentially inside the same coroutine; a per-item failure is reported
     * through the ordinary send-error flow and does not abort the rest.
     */
    fun sendSharedBatch(items: List<Pair<String, SharedAttachmentKind>>, caption: String, replyToId: String?) {
        if (items.isEmpty() || mediaSendInFlight) return
        mediaSendInFlight = true
        viewModelScope.launch {
            try {
                sendShareBatch(
                    items = items.map { (path, kind) -> ShareBatchItem(path, kind) },
                    caption = caption,
                    replyToMessageId = replyToId?.toLongOrNull(),
                    send = { item, itemCaption, reply ->
                        when (item.kind) {
                            SharedAttachmentKind.IMAGE -> telegram.sendPhoto(activeChatId, item.path, itemCaption, reply, forumTopicId, false)
                            SharedAttachmentKind.VIDEO -> telegram.sendVideo(activeChatId, item.path, itemCaption, 0, 0, 0, reply, forumTopicId, false)
                            SharedAttachmentKind.FILE -> telegram.sendDocument(activeChatId, item.path, itemCaption, reply)
                        }
                    },
                    onError = { _sendError.value = it }
                )
            } finally {
                mediaSendInFlight = false
            }
        }
    }

    /**
     * Exact re-resolution of a composer reply/edit target after recreation.
     * See TelegramClient.resolveReplyEditTarget for the Missing-vs-
     * Unavailable distinction this must preserve.
     */
    suspend fun resolveReplyEditTarget(messageId: String): ReplyEditTargetOutcome {
        val id = messageId.toLongOrNull() ?: return ReplyEditTargetOutcome.Missing
        val chatId = activeChatId
        if (chatId == 0L) return ReplyEditTargetOutcome.Unavailable
        return telegram.resolveReplyEditTarget(chatId, id)
    }

    fun sendVideo(videoPath: String, caption: String = "", duration: Int = 0, replyToId: String? = null, viewOnce: Boolean = false) {
        if (mediaSendInFlight) return
        mediaSendInFlight = true
        viewModelScope.launch {
            val result = telegram.sendVideo(activeChatId, videoPath, caption, duration, 0, 0, replyToId?.toLongOrNull(), forumTopicId, viewOnce)
            mediaSendInFlight = false
            result.exceptionOrNull()?.message?.let { _sendError.value = it }
        }
    }

    fun sendVoiceNote(voicePath: String, duration: Int, waveform: ByteArray = ByteArray(0), replyToId: String? = null) {
        viewModelScope.launch {
            val result = telegram.sendVoiceNote(activeChatId, voicePath, duration, waveform, replyToId?.toLongOrNull())
            result.exceptionOrNull()?.message?.let { _sendError.value = it }
        }
    }

    fun sendDocument(docPath: String, caption: String = "", replyToId: String? = null) {
        viewModelScope.launch {
            val result = telegram.sendDocument(activeChatId, docPath, caption, replyToId?.toLongOrNull())
            result.exceptionOrNull()?.message?.let { _sendError.value = it }
        }
    }

    fun sendAudio(
        audioPath: String,
        title: String = "",
        performer: String = "",
        duration: Int = 0,
        caption: String = "",
        replyToId: String? = null
    ) {
        viewModelScope.launch {
            val result = telegram.sendAudio(
                chatId = activeChatId,
                audioPath = audioPath,
                title = title,
                performer = performer,
                duration = duration,
                caption = caption,
                replyToMessageId = replyToId?.toLongOrNull(),
                forumTopicId = forumTopicId
            )
            result.exceptionOrNull()?.message?.let { _sendError.value = it }
        }
    }

    fun sendAnimation(animationPath: String, caption: String = "", replyToId: String? = null) {
        viewModelScope.launch {
            val result = telegram.sendAnimation(
                chatId = activeChatId,
                animationPath = animationPath,
                caption = caption,
                replyToMessageId = replyToId?.toLongOrNull(),
                forumTopicId = forumTopicId
            )
            result.exceptionOrNull()?.message?.let { _sendError.value = it }
        }
    }

    fun sendSticker(stickerPath: String, emoji: String = "", replyToId: String? = null) {
        viewModelScope.launch {
            val result = telegram.sendSticker(
                chatId = activeChatId,
                stickerPath = stickerPath,
                emoji = emoji,
                replyToMessageId = replyToId?.toLongOrNull(),
                forumTopicId = forumTopicId
            )
            result.exceptionOrNull()?.message?.let { _sendError.value = it }
        }
    }

    fun sendStickerFile(fileId: Int, emoji: String = "", replyToId: String? = null) {
        viewModelScope.launch {
            val result = telegram.sendStickerFile(
                chatId = activeChatId,
                fileId = fileId,
                emoji = emoji,
                replyToMessageId = replyToId?.toLongOrNull(),
                forumTopicId = forumTopicId
            )
            result.exceptionOrNull()?.message?.let { _sendError.value = it }
        }
    }

    fun sendAnimationFile(fileId: Int, caption: String = "", replyToId: String? = null) {
        viewModelScope.launch {
            val result = telegram.sendAnimationFile(
                chatId = activeChatId,
                fileId = fileId,
                caption = caption,
                replyToMessageId = replyToId?.toLongOrNull(),
                forumTopicId = forumTopicId
            )
            result.exceptionOrNull()?.message?.let { _sendError.value = it }
        }
    }

    fun sendAnimation(
        animationPath: String,
        caption: String = "",
        duration: Int = 0,
        width: Int = 0,
        height: Int = 0,
        replyToId: String? = null
    ) {
        viewModelScope.launch {
            val result = telegram.sendAnimation(
                chatId = activeChatId,
                animationPath = animationPath,
                caption = caption,
                duration = duration,
                width = width,
                height = height,
                replyToMessageId = replyToId?.toLongOrNull(),
                forumTopicId = forumTopicId
            )
            result.exceptionOrNull()?.message?.let { _sendError.value = it }
        }
    }

    fun sendVideoNote(
        videoPath: String,
        duration: Int = 0,
        length: Int = 240,
        replyToId: String? = null
    ) {
        viewModelScope.launch {
            val result = telegram.sendVideoNote(
                chatId = activeChatId,
                videoPath = videoPath,
                duration = duration,
                length = length,
                replyToMessageId = replyToId?.toLongOrNull(),
                forumTopicId = forumTopicId
            )
            result.exceptionOrNull()?.message?.let { _sendError.value = it }
        }
    }

    fun replaceMedia(
        message: Message,
        mediaPath: String,
        type: MessageType,
        caption: String = ""
    ) {
        viewModelScope.launch {
            val messageId = message.id.toLongOrNull() ?: return@launch
            val result = telegram.replaceMedia(
                chatId = activeChatId,
                messageId = messageId,
                mediaPath = mediaPath,
                type = type,
                caption = caption
            )
            result.exceptionOrNull()?.message?.let { _sendError.value = it }
        }
    }

    fun editMessage(message: Message, newText: String) {
        val previewOptions = LinkPreviewSupport.optionsFor(linkPreviews.intentFor(newText))
        viewModelScope.launch {
            val messageId = message.id.toLongOrNull() ?: return@launch
            val result = telegram.editMessage(activeChatId, messageId, newText, previewOptions)
            result.exceptionOrNull()?.message?.let { _sendError.value = it }
        }
    }

    /** Messages with a reaction toggle currently in flight -- a second tap on the same
     * message is ignored until the first round-trips, so a fast double-tap (typically
     * the user retrying because nothing visibly happened yet) can't race the server's
     * response and leave the bubble in a state neither tap actually asked for. */
    private val reactionsInFlight = mutableSetOf<String>()

    /**
     * Adds or removes this account's reaction.
     *
     * The direction comes from the message as Telegram last reported it, so tapping
     * a reaction the account already gave removes it rather than adding a second.
     * The bubble updates immediately (optimistic), then either stays as-is once
     * Telegram confirms it, or is rolled back to its prior state if the request fails.
     */
    fun addReaction(message: Message, emoji: String) {
        if (message.id in reactionsInFlight) return
        val messageId = message.id.toLongOrNull() ?: return
        val alreadyChosen = message.reactions
            .any { it.emoji == emoji && it.userReacted }
        reactionsInFlight += message.id
        val previousReactions = telegram.applyOptimisticReaction(activeChatId, messageId, emoji, adding = !alreadyChosen)
        viewModelScope.launch {
            try {
                val result = telegram.toggleReaction(activeChatId, messageId, emoji, alreadyChosen)
                result.exceptionOrNull()?.let { error ->
                    _sendError.value = error.message
                    if (previousReactions != null) {
                        telegram.revertReaction(activeChatId, messageId, previousReactions)
                    }
                }
            } finally {
                reactionsInFlight -= message.id
            }
        }
    }

    /** Reloads the pinned stack from Telegram. */
    fun refreshPinned() {
        viewModelScope.launch {
            _pinnedMessages.value = telegram.pinnedMessages(activeChatId, forumTopicId = forumTopicId)
        }
    }

    /** Unpins one message, then reconciles the banner with the server. */
    fun unpinMessage(message: Message) {
        viewModelScope.launch {
            val messageId = message.id.toLongOrNull() ?: return@launch
            val result = telegram.unpinMessage(activeChatId, messageId)
            result.exceptionOrNull()?.message?.let { _sendError.value = it }
            refreshPinned()
        }
    }

    fun pinMessage(message: Message) {
        viewModelScope.launch {
            val messageId = message.id.toLongOrNull() ?: return@launch
            val result = if (message.isPinned) {
                telegram.unpinMessage(activeChatId, messageId)
            } else {
                telegram.pinMessage(activeChatId, messageId)
            }
            result.exceptionOrNull()?.let {
                _sendError.value = if (message.isPinned) "Couldn't unpin message" else "Couldn't pin message"
            }
        }
    }

    /**
     * Pins or unpins this whole conversation in the chat list -- the same
     * chat-list operation Home's selection dock performs, through the same
     * [TelegramClient.setChatPinned]. Distinct from [pinMessage]/[unpinMessage],
     * which pin a message inside the conversation.
     *
     * [header] is fed by the same `telegram.chatList` collector Home reads, so
     * once Telegram reports the change this toggles against, this screen's own
     * Pin/Unpin control updates from that one authoritative state -- nothing
     * here is set optimistically.
     */
    fun toggleChatPinned() {
        val chat = header.value ?: return
        viewModelScope.launch {
            val result = telegram.setChatPinned(activeChatId, !chat.isPinned, chat.isArchived)
            result.exceptionOrNull()?.let {
                _sendError.value = if (chat.isPinned) "Couldn't unpin chat" else "Couldn't pin chat"
            }
        }
    }

    /**
     * The other party's user id for this conversation, where there is exactly
     * one -- the same resolution [initiateAudioCall] uses, factored out so
     * [activeCallForThisChat] can match the repository's [ActiveCall] against
     * it without a second, drifting copy of the logic.
     */
    private fun resolveCallTargetUserId(): Long = when (target) {
        is com.foresightlabs.aether.domain.model.ConversationTarget.User -> target.userId
        // A forum has no single other party to call; the header resolves to
        // nothing and the repository refuses, which is the truthful outcome.
        is com.foresightlabs.aether.domain.model.ConversationTarget.Topic,
        is com.foresightlabs.aether.domain.model.ConversationTarget.Chat -> header.value?.directUser?.id?.toLongOrNull() ?: activeChatId
    }

    fun initiateAudioCall() {
        if (!com.foresightlabs.aether.AetherFeatureFlags.CALLS_ENABLED) return
        val targetUserId = resolveCallTargetUserId()
        viewModelScope.launch {
            val result = calls.initiateCall(targetUserId, isVideo = false)
            result.exceptionOrNull()?.message?.let { _sendError.value = it }
        }
    }

    fun initiateVideoCall() {
        if (!com.foresightlabs.aether.AetherFeatureFlags.CALLS_ENABLED) return
        val targetUserId = resolveCallTargetUserId()
        viewModelScope.launch {
            val result = calls.initiateCall(targetUserId, isVideo = true)
            result.exceptionOrNull()?.message?.let { _sendError.value = it }
        }
    }

    /** Whether a call started right now for this conversation could actually carry audio. */
    val isCallMediaAvailable: Boolean
        get() = com.foresightlabs.aether.AetherFeatureFlags.CALLS_ENABLED && calls.isCallMediaAvailable

    /**
     * The repository's single [ActiveCall], surfaced here only when it belongs
     * to THIS conversation's counterpart -- a call for a different chat must
     * never hijack this Conversation's header/Curtain/banner.
     */
    val activeCallForThisChat: StateFlow<com.foresightlabs.aether.domain.model.ActiveCall?> =
        if (com.foresightlabs.aether.AetherFeatureFlags.CALLS_ENABLED) {
            calls.activeCallState
                .map { call -> call?.takeIf { it.userId == resolveCallTargetUserId() } }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
        } else {
            // Calling held: there is never a call to show, and the call stack
            // is never built to find that out.
            MutableStateFlow(null)
        }

    fun toggleCallMute() = calls.toggleMute()
    fun toggleCallSpeaker() = calls.toggleSpeaker()
    fun setCallCameraEnabled(enabled: Boolean) = calls.setCameraEnabled(enabled)
    fun switchCallCamera() = calls.switchCamera()

    fun endActiveCall() {
        val callId = activeCallForThisChat.value?.callId ?: return
        viewModelScope.launch { calls.discardCall(callId) }
    }

    fun copyMessageLink(message: Message, onLinkResolved: (String) -> Unit) {
        viewModelScope.launch {
            val messageId = message.id.toLongOrNull() ?: return@launch
            val result = telegram.getMessageLink(activeChatId, messageId)
            val link = result.getOrNull() ?: run {
                val formattedChatId = if (activeChatId < 0) {
                    val raw = activeChatId.toString()
                    if (raw.startsWith("-100")) raw.removePrefix("-100") else raw.removePrefix("-")
                } else activeChatId.toString()
                "https://t.me/c/$formattedChatId/$messageId"
            }
            onLinkResolved(link)
        }
    }

    /**
     * Forwards a message, with the options Telegram actually supports.
     *
     * [sendCopy] drops attribution; [removeCaption] drops a media caption and only
     * applies to a copy. Both are real TDLib flags — Aether never re-sends content
     * as a new message to imitate a forward.
     */
    fun unpinAllMessages() {
        viewModelScope.launch {
            val result = telegram.unpinAllMessages(activeChatId)
            result.exceptionOrNull()?.message?.let { _sendError.value = it }
            refreshPinned()
        }
    }

    /**
     * Forwards a message, with the options Telegram actually supports.
     *
     * [sendCopy] drops attribution; [removeCaption] drops a media caption and only
     * applies to a copy. Both are real TDLib flags — Aether never re-sends content
     * as a new message to imitate a forward.
     */
    fun forwardMessage(
        message: Message,
        toChatId: Long,
        sendCopy: Boolean = false,
        removeCaption: Boolean = false
    ) {
        forwardMessages(listOf(message), toChatId, sendCopy, removeCaption)
    }

    /**
     * Forwards multiple messages together, preserving the server order.
     */
    fun forwardMessages(
        messages: List<Message>,
        toChatId: Long,
        sendCopy: Boolean = false,
        removeCaption: Boolean = false
    ) {
        if (messages.isEmpty()) return
        _forwardState.value = ForwardState.Sending
        viewModelScope.launch {
            val messageIds = messages.mapNotNull { it.id.toLongOrNull() }.toLongArray()
            if (messageIds.isEmpty()) {
                if (BuildConfig.DEBUG) {
                    Log.d(FORWARD_LOG_TAG, "FORWARD_RESULT_ERROR code=EMPTY_MESSAGE_IDS")
                }
                _forwardState.value = ForwardState.Error("These messages are no longer available.")
                return@launch
            }
            val result = telegram.forwardMessages(
                toChatId = toChatId,
                fromChatId = activeChatId,
                messageIds = messageIds,
                sendCopy = sendCopy,
                removeCaption = removeCaption
            )
            result.fold(
                onSuccess = {
                    if (BuildConfig.DEBUG) {
                        Log.d(FORWARD_LOG_TAG, "FORWARD_RESULT_OK")
                    }
                    _forwardState.value = ForwardState.Success
                },
                onFailure = { error ->
                    if (BuildConfig.DEBUG) {
                        Log.d(
                            FORWARD_LOG_TAG,
                            "FORWARD_RESULT_ERROR class=${error::class.java.simpleName}"
                        )
                    }
                    val message = error.message?.takeIf { it.isNotBlank() } ?: "Forwarding failed. Try again."
                    _forwardState.value = ForwardState.Error(message)
                }
            )
        }
    }

    fun consumeForwardState() {
        if (_forwardState.value !is ForwardState.Sending) {
            _forwardState.value = ForwardState.Idle
        }
    }

    suspend fun getScheduledMessages(): List<Message> {
        return telegram.getScheduledMessages(activeChatId)
    }

    fun sendScheduledMessageNow(message: Message) {
        viewModelScope.launch {
            val messageId = message.id.toLongOrNull() ?: return@launch
            val result = telegram.sendScheduledMessageNow(activeChatId, messageId)
            result.exceptionOrNull()?.message?.let { _sendError.value = it }
        }
    }

    fun rescheduleMessage(message: Message, dateSeconds: Int) {
        viewModelScope.launch {
            val messageId = message.id.toLongOrNull() ?: return@launch
            val result = telegram.rescheduleMessage(activeChatId, messageId, dateSeconds)
            result.exceptionOrNull()?.message?.let { _sendError.value = it }
        }
    }

    fun retry(message: Message) {
        viewModelScope.launch {
            telegram.retrySend(activeChatId, message.id.toLongOrNull() ?: return@launch)
        }
    }

    /**
     * Deletes a message at an explicitly chosen scope.
     *
     * The scope is resolved from the action the user picked, which the policy only
     * offered because Telegram said it was permitted for this message.
     */
    fun delete(message: Message, forEveryone: Boolean) {
        viewModelScope.launch {
            val messageId = message.id.toLongOrNull() ?: return@launch
            val result = telegram.deleteMessages(
                chatId = activeChatId,
                messageIds = longArrayOf(messageId),
                revoke = forEveryone
            )
            result.exceptionOrNull()?.message?.let { _sendError.value = it }
        }
    }

    /**
     * Loads Telegram's answer for what may be done with [message], for the menu.
     *
     * Held per message id rather than per menu instance so a menu reopened on the
     * same message does not flash an empty action list.
     */
    fun loadCapabilities(message: Message) {
        val messageId = message.id.toLongOrNull() ?: return
        if (_capabilities.value.containsKey(message.id)) return
        viewModelScope.launch {
            val capabilities = telegram.messageCapabilities(activeChatId, messageId)
            _capabilities.value = _capabilities.value + (message.id to capabilities)
        }
    }

    fun onComposerChanged(text: String) {
        // Held so leaving the conversation can store it as a real Telegram draft,
        // which then follows the account to its other clients.
        pendingDraft = text
        updateLinkPreview(text)
        if (text.isBlank()) {
            clearChatAction()
            return
        }
        reportActivity(TelegramClient.OutgoingChatAction.TYPING)
    }

    /**
     * Asks Telegram about the draft's link, or stops showing one.
     *
     * The decision is [ComposerLinkPreviewPolicy]'s; this only carries it out.
     * A request is debounced and the previous one cancelled, so typing a URL out
     * character by character costs one round trip rather than thirty, and an
     * answer for a link the draft has moved off is discarded rather than shown.
     */
    private fun updateLinkPreview(text: String) {
        linkPreviews.onDraftChanged(text)
    }

    /**
     * Closes the preview without touching the draft.
     *
     * The link stays where the user typed it; only the preview goes, and the
     * message then sends with previews disabled for that link.
     */
    fun dismissLinkPreview() {
        linkPreviews.dismiss()
    }

    /** Reports that the user has started recording a voice message. */
    fun onVoiceRecordingStarted() {
        reportActivity(TelegramClient.OutgoingChatAction.RECORDING_VOICE)
    }

    /** Reports that a recording or upload has finished or been abandoned. */
    fun onActivityEnded() {
        clearChatAction()
    }

    /**
     * Keeps a chat action alive while the activity continues.
     *
     * Telegram expires an action after roughly five seconds, so it is re-sent on a
     * slightly shorter cadence and stops the moment the activity does. The job is
     * bounded rather than looping forever, so a composer left open with text in it
     * cannot keep telling the other side that someone is typing.
     */
    private fun reportActivity(action: TelegramClient.OutgoingChatAction) {
        if (activeAction == action && typingJob?.isActive == true) return
        typingJob?.cancel()
        activeAction = action
        typingJob = viewModelScope.launch {
            repeat(ACTION_REPEATS) {
                telegram.sendChatAction(activeChatId, action)
                delay(ACTION_REFRESH_MS)
            }
            // The activity outlived the window Aether is willing to assert it for.
            clearChatAction()
        }
    }

    private fun clearChatAction() {
        // Nothing was being reported, so there is nothing to withdraw.
        if (activeAction == null) return
        typingJob?.cancel()
        typingJob = null
        activeAction = null
        viewModelScope.launch {
            telegram.sendChatAction(activeChatId, TelegramClient.OutgoingChatAction.CANCEL)
        }
    }

    /** Records that the composer's contents were sent, so no draft is left behind. */
    private fun clearPendingDraft() {
        pendingDraft = ""
        linkPreviews.reset()
    }

    fun markVisible(ids: List<String>) {
        if (ids.isEmpty()) return
        val newIds = ids.filter { viewedIds.add(it) }
        if (newIds.isEmpty()) return
        viewModelScope.launch {
            val longs = newIds.mapNotNull { it.toLongOrNull() }.toLongArray()
            telegram.viewMessages(activeChatId, longs)
        }
    }

    /**
     * Call when a message's actual content is opened -- a photo/video shown
     * full-screen, a voice note played -- not merely scrolled past. See
     * [com.foresightlabs.aether.data.telegram.TelegramClient.openMessageContent]:
     * this is what a view-once photo or video is waiting on before it can
     * begin self-destructing.
     */
    fun openMessageContent(messageId: String) {
        val id = messageId.toLongOrNull() ?: return
        viewModelScope.launch {
            telegram.openMessageContent(activeChatId, id)
        }
    }

    /**
     * Sends a contact card the user composed.
     *
     * Nothing is read from the device address book to build it — see the share sheet.
     */
    fun sendContact(phone: String, firstName: String, lastName: String, replyToId: String?) {
        viewModelScope.launch {
            val result = telegram.sendContact(
                chatId = activeChatId,
                phoneNumber = phone,
                firstName = firstName,
                lastName = lastName,
                replyToMessageId = replyToId?.toLongOrNull(),
                forumTopicId = forumTopicId
            )
            result.exceptionOrNull()?.message?.let { _sendError.value = it }
        }
    }

    /** Sends a one-off static location the user explicitly confirmed. */
    fun sendLocation(latitude: Double, longitude: Double, replyToId: String?) {
        viewModelScope.launch {
            val result = telegram.sendLocation(
                chatId = activeChatId,
                latitude = latitude,
                longitude = longitude,
                replyToMessageId = replyToId?.toLongOrNull(),
                forumTopicId = forumTopicId
            )
            result.exceptionOrNull()?.message?.let { _sendError.value = it }
        }
    }

    fun sendLiveLocation(
        latitude: Double,
        longitude: Double,
        livePeriod: Int,
        heading: Int = 0,
        replyToId: String? = null
    ) {
        viewModelScope.launch {
            val result = telegram.sendLiveLocation(
                chatId = activeChatId,
                latitude = latitude,
                longitude = longitude,
                livePeriod = livePeriod,
                heading = heading,
                replyToMessageId = replyToId?.toLongOrNull(),
                forumTopicId = forumTopicId
            )
            result.onSuccess { msg ->
                liveLocationCoordinator.startLiveSharing(activeChatId, msg.id, livePeriod)
            }.onFailure {
                _sendError.value = it.message
            }
        }
    }

    fun editLiveLocation(
        message: Message,
        latitude: Double,
        longitude: Double,
        livePeriod: Int = 0,
        heading: Int = 0
    ) {
        viewModelScope.launch {
            val messageId = message.id.toLongOrNull() ?: return@launch
            val result = telegram.editLiveLocation(
                chatId = activeChatId,
                messageId = messageId,
                latitude = latitude,
                longitude = longitude,
                livePeriod = livePeriod,
                heading = heading
            )
            result.exceptionOrNull()?.message?.let { _sendError.value = it }
        }
    }

    fun stopLiveLocation(message: Message) {
        val messageId = message.id.toLongOrNull() ?: return
        liveLocationCoordinator.stopLiveSharing(activeChatId, messageId)
    }

    fun sendVenue(
        latitude: Double,
        longitude: Double,
        title: String,
        address: String,
        replyToId: String? = null
    ) {
        viewModelScope.launch {
            val result = telegram.sendVenue(
                chatId = activeChatId,
                latitude = latitude,
                longitude = longitude,
                title = title,
                address = address,
                replyToMessageId = replyToId?.toLongOrNull(),
                forumTopicId = forumTopicId
            )
            result.exceptionOrNull()?.message?.let { _sendError.value = it }
        }
    }

    // --- Stickers browser ----------------------------------------------------

    private val _installedStickerSets = MutableStateFlow<List<StickerSetInfo>>(emptyList())
    val installedStickerSets: StateFlow<List<StickerSetInfo>> = _installedStickerSets.asStateFlow()

    private val _recentStickers = MutableStateFlow<List<StickerItem>>(emptyList())
    val recentStickers: StateFlow<List<StickerItem>> = _recentStickers.asStateFlow()

    private val _favoriteStickers = MutableStateFlow<List<StickerItem>>(emptyList())
    val favoriteStickers: StateFlow<List<StickerItem>> = _favoriteStickers.asStateFlow()

    fun loadStickers() {
        viewModelScope.launch {
            telegram.getInstalledStickerSets().onSuccess { _installedStickerSets.value = it }
            telegram.getRecentStickers().onSuccess { _recentStickers.value = it }
            telegram.getFavoriteStickers().onSuccess { _favoriteStickers.value = it }
        }
    }

    fun loadStickerSetDetails(setId: Long, onLoaded: (StickerSetInfo) -> Unit) {
        viewModelScope.launch {
            telegram.getStickerSet(setId).onSuccess { set ->
                onLoaded(set)
            }
        }
    }

    // --- Saved Animations / GIFs ----------------------------------------------

    private val _savedAnimations = MutableStateFlow<List<AnimationItem>>(emptyList())
    val savedAnimations: StateFlow<List<AnimationItem>> = _savedAnimations.asStateFlow()

    fun loadSavedAnimations() {
        viewModelScope.launch {
            telegram.getSavedAnimations().onSuccess { _savedAnimations.value = it }
        }
    }

    /**
     * Casts this account's vote on a poll.
     *
     * Nothing is updated locally: the result arrives as an `updateMessageContent`
     * carrying Telegram's own counts, so the bubble can never show a tally the
     * server has not agreed to.
     */
    fun voteOnPoll(message: Message, optionIndices: List<Int>) {
        val messageId = message.id.toLongOrNull() ?: return
        viewModelScope.launch {
            val result = telegram.setPollAnswer(activeChatId, messageId, optionIndices.toIntArray())
            result.exceptionOrNull()?.message?.let { _sendError.value = it }
        }
    }

    /** Stops a poll so it accepts no further votes. */
    fun stopPoll(message: Message) {
        val messageId = message.id.toLongOrNull() ?: return
        viewModelScope.launch {
            val result = telegram.stopPoll(activeChatId, messageId)
            result.exceptionOrNull()?.message?.let { _sendError.value = it }
        }
    }

    // --- in-conversation search ---------------------------------------------

    fun openSearch() {
        _search.value = ConversationSearchState(isActive = true)
    }

    fun closeSearch() {
        searchJob?.cancel()
        _search.value = ConversationSearchState.Idle
    }

    /**
     * Runs a server-side search for [query].
     *
     * Debounced, and each keystroke cancels the previous request, so a fast typist
     * does not see results for a prefix land after results for the whole query.
     */
    fun searchMessages(query: String) {
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _search.value = ConversationSearchState(query = query, isActive = true)
            return
        }
        _search.value = _search.value.copy(query = query, isActive = true, isLoading = true, error = null)
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val result = telegram.searchChatMessages(activeChatId, trimmed, forumTopicId = forumTopicId)
            result.fold(
                onSuccess = { found ->
                    val mapped = found.messages
                        .filterNotNull()
                        .map(telegram::mapFoundMessage)
                    _search.value = _search.value.copy(
                        isLoading = false,
                        results = mapped,
                        selectedIndex = if (mapped.isEmpty()) -1 else 0,
                        totalCount = found.totalCount,
                        cursor = found.nextFromMessageId,
                        hasMore = found.nextFromMessageId != 0L && mapped.isNotEmpty(),
                        error = null
                    )
                    mapped.firstOrNull()?.let { jumpTo(it.id) }
                },
                onFailure = { error ->
                    _search.value = _search.value.copy(
                        isLoading = false,
                        results = emptyList(),
                        selectedIndex = -1,
                        error = error.message ?: "Couldn't search this conversation"
                    )
                }
            )
        }
    }

    /** Steps to the next older result, paging the server when the page runs out. */
    fun searchOlder() {
        val state = _search.value
        if (!state.canGoOlder) return
        if (state.selectedIndex < state.results.lastIndex) {
            select(state.selectedIndex + 1)
            return
        }
        if (!state.hasMore || state.isLoading) return
        _search.value = state.copy(isLoading = true)
        viewModelScope.launch {
            val result = telegram.searchChatMessages(
                chatId = activeChatId,
                query = state.query.trim(),
                fromMessageId = state.cursor,
                forumTopicId = forumTopicId
            )
            result.fold(
                onSuccess = { found ->
                    val more = found.messages.filterNotNull().map(telegram::mapFoundMessage)
                    val combined = state.results + more.filter { new ->
                        state.results.none { it.id == new.id }
                    }
                    _search.value = state.copy(
                        isLoading = false,
                        results = combined,
                        selectedIndex = (state.selectedIndex + 1).coerceAtMost(combined.lastIndex),
                        cursor = found.nextFromMessageId,
                        hasMore = found.nextFromMessageId != 0L && more.isNotEmpty()
                    )
                    combined.getOrNull(state.selectedIndex + 1)?.let { jumpTo(it.id) }
                },
                onFailure = {
                    _search.value = state.copy(isLoading = false, hasMore = false)
                }
            )
        }
    }

    /** Steps back to the next newer result. */
    fun searchNewer() {
        val state = _search.value
        if (!state.canGoNewer) return
        select(state.selectedIndex - 1)
    }

    private fun select(index: Int) {
        val state = _search.value
        val target = state.results.getOrNull(index) ?: return
        _search.value = state.copy(selectedIndex = index)
        jumpTo(target.id)
    }

    /**
     * Brings [messageId] into view, loading the surrounding window first when the
     * message is not among those already held.
     */
    fun jumpTo(messageId: String) {
        val id = messageId.toLongOrNull() ?: return
        // A jump asked for before the chat is open (a bookmark or shared-content
        // entry) waits for the initial page: jumping first would let the
        // initial settle-to-latest scroll straight past it.
        if (!opened) {
            pendingJumpId = messageId
            return
        }
        val alreadyLoaded = telegram.messagesFlow(activeChatId).value.any { it.id == messageId }
        if (alreadyLoaded) {
            _jumpTarget.value = messageId
            return
        }
        viewModelScope.launch {
            val window = runCatching { telegram.loadHistoryAround(activeChatId, id) }.getOrDefault(emptyList())
            if (window.isNotEmpty()) {
                telegram.upsertConversation(activeChatId, window, prepend = true)
                window.firstOrNull()?.id?.toLongOrNull()?.let { first ->
                    if (first < oldestId || oldestId == 0L) oldestId = first
                }
            }
            // GetChatHistory answers with the neighbourhood even when the
            // message itself is gone, so landing is decided by membership --
            // never by a non-empty window. A target that never arrives would
            // otherwise sit armed forever, waiting for a row that cannot come.
            if (window.any { it.id == messageId }) {
                _jumpTarget.value = messageId
            } else {
                _jumpFailures.tryEmit(messageId)
            }
        }
    }

    /** A jump requested before the conversation opened; see [jumpTo]. */
    private var pendingJumpId: String? = null

    private fun runPendingJump() {
        val pending = pendingJumpId ?: return
        pendingJumpId = null
        jumpTo(pending)
    }

    private val _jumpFailures = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 4)

    /**
     * Ids whose jump could not land: the message was deleted, or Telegram could
     * not be reached for it. One-shot, so the screen reports it exactly once.
     */
    val jumpFailures: kotlinx.coroutines.flow.SharedFlow<String> = _jumpFailures



    /** Clears the highlight once the list has scrolled to it. */
    fun consumeJumpTarget() {
        _jumpTarget.value = null
    }

    override fun onCleared() {
        super.onCleared()
        if (!opened) return

        // The draft is stored server-side rather than in a private Aether table, so
        // typing here and opening Telegram elsewhere shows the same unsent text.
        clearChatAction()
        val draft = pendingDraft
        val chatId = activeChatId
        val replyTo = draftReplyId
        telegram.setChatDraftAsync(chatId, draft, replyTo, forumTopicId)
        telegram.closeChatAsync(chatId)
    }

    private companion object {
        const val FORWARD_LOG_TAG = "AetherTd"

        /** Long enough to skip intermediate prefixes, short enough to feel direct. */
        const val SEARCH_DEBOUNCE_MS = 220L

        /** Slightly under Telegram's own action expiry, so it never gaps. */
        const val ACTION_REFRESH_MS = 4_000L

        /** Roughly two minutes: long enough for real activity, bounded regardless. */
        const val ACTION_REPEATS = 30

        /** Messages per history page, initial load and each older-history fetch. */
        const val HISTORY_PAGE_SIZE = 40
    }

    class Factory(
        private val application: Application,
        private val target: com.foresightlabs.aether.domain.model.ConversationTarget
    ) : ViewModelProvider.Factory {
        constructor(application: Application, chatId: Long) : this(
            application,
            com.foresightlabs.aether.domain.model.ConversationTarget.Chat(chatId)
        )

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ConversationViewModel(application, target) as T
        }
    }
}
