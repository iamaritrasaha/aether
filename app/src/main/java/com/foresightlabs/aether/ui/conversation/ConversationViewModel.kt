package com.foresightlabs.aether.ui.conversation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.foresightlabs.aether.AetherApplication
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.Message
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ConversationViewModel(
    application: Application,
    private val target: com.foresightlabs.aether.domain.model.ConversationTarget
) : AndroidViewModel(application) {

    constructor(application: Application, chatId: Long) : this(
        application,
        com.foresightlabs.aether.domain.model.ConversationTarget.Chat(chatId)
    )

    private val telegram = (application as AetherApplication).telegram

    private var activeChatId: Long = when (target) {
        is com.foresightlabs.aether.domain.model.ConversationTarget.Chat -> target.chatId
        is com.foresightlabs.aether.domain.model.ConversationTarget.User -> target.userId
    }

    private val _isResolving = MutableStateFlow(
        when (target) {
            is com.foresightlabs.aether.domain.model.ConversationTarget.Chat -> telegram.chat(target.chatId) == null
            is com.foresightlabs.aether.domain.model.ConversationTarget.User -> true
        }
    )
    val isResolving: StateFlow<Boolean> = _isResolving.asStateFlow()

    private val _resolveError = MutableStateFlow<String?>(null)
    val resolveError: StateFlow<String?> = _resolveError.asStateFlow()

    private val _header = MutableStateFlow<Chat?>(
        when (target) {
            is com.foresightlabs.aether.domain.model.ConversationTarget.Chat -> telegram.chat(target.chatId)
            is com.foresightlabs.aether.domain.model.ConversationTarget.User -> null
        }
    )
    val header: StateFlow<Chat?> = _header.asStateFlow()

    val messages: StateFlow<List<Message>> = telegram.messagesFlow(activeChatId)

    private val _loadingOlder = MutableStateFlow(false)
    val loadingOlder: StateFlow<Boolean> = _loadingOlder.asStateFlow()

    private val _composerEnabled = MutableStateFlow(true)
    val composerEnabled: StateFlow<Boolean> = _composerEnabled.asStateFlow()

    private val _sendError = MutableStateFlow<String?>(null)
    val sendError: StateFlow<String?> = _sendError.asStateFlow()

    private val viewedIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private var oldestId: Long = 0L
    private var historyComplete = false
    private var opened = false
    private var typingJob: Job? = null
    private var sendInFlight = false

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
                is com.foresightlabs.aether.domain.model.ConversationTarget.Chat -> {
                    activeChatId = target.chatId
                    val resolvedChat = telegram.ensureChatLoaded(target.chatId)
                    if (resolvedChat != null) {
                        _header.value = resolvedChat
                        telegram.openChat(target.chatId)
                        opened = true
                        loadInitial()
                    } else {
                        _resolveError.value = "Couldn't load this conversation. Please check your network and try again."
                    }
                }
                is com.foresightlabs.aether.domain.model.ConversationTarget.User -> {
                    val result = telegram.createPrivateChat(target.userId)
                    result.fold(
                        onSuccess = { resolvedChat ->
                            _header.value = resolvedChat
                            val chatId = resolvedChat.id.toLongOrNull() ?: target.userId
                            activeChatId = chatId
                            telegram.openChat(chatId)
                            opened = true
                            loadInitial()
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

    private suspend fun loadInitial() {
        val page = telegram.loadHistory(activeChatId, 0L)
        if (page.isNotEmpty()) {
            oldestId = page.first().id.toLongOrNull() ?: 0L
            telegram.upsertConversation(activeChatId, page, prepend = true)
            markVisible(page.map { it.id })
        }
        if (page.size < 20) historyComplete = true
    }

    fun loadOlder() {
        if (historyComplete || _loadingOlder.value || oldestId == 0L) return
        viewModelScope.launch {
            _loadingOlder.value = true
            val page = telegram.loadHistory(activeChatId, oldestId)
            if (page.isEmpty()) {
                historyComplete = true
            } else {
                oldestId = page.first().id.toLongOrNull() ?: oldestId
                telegram.upsertConversation(activeChatId, page, prepend = true)
            }
            _loadingOlder.value = false
        }
    }

    fun send(text: String, replyToId: String?) {
        val trimmedEnd = text.trimEnd()
        if (trimmedEnd.isEmpty() || sendInFlight) return
        sendInFlight = true
        viewModelScope.launch {
            val result = telegram.sendText(activeChatId, trimmedEnd, replyToId?.toLongOrNull())
            sendInFlight = false
            result.exceptionOrNull()?.message?.let { _sendError.value = it }
        }
    }

    fun sendPhoto(photoPath: String, caption: String = "", replyToId: String? = null) {
        viewModelScope.launch {
            val result = telegram.sendPhoto(activeChatId, photoPath, caption, replyToId?.toLongOrNull())
            result.exceptionOrNull()?.message?.let { _sendError.value = it }
        }
    }

    fun sendVideo(videoPath: String, caption: String = "", duration: Int = 0, replyToId: String? = null) {
        viewModelScope.launch {
            val result = telegram.sendVideo(activeChatId, videoPath, caption, duration, 0, 0, replyToId?.toLongOrNull())
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

    fun editMessage(message: Message, newText: String) {
        viewModelScope.launch {
            val messageId = message.id.toLongOrNull() ?: return@launch
            val result = telegram.editMessage(activeChatId, messageId, newText)
            result.exceptionOrNull()?.message?.let { _sendError.value = it }
        }
    }

    fun addReaction(message: Message, emoji: String) {
        viewModelScope.launch {
            val messageId = message.id.toLongOrNull() ?: return@launch
            telegram.addReaction(activeChatId, messageId, emoji)
        }
    }

    fun pinMessage(message: Message) {
        viewModelScope.launch {
            val messageId = message.id.toLongOrNull() ?: return@launch
            telegram.pinMessage(activeChatId, messageId)
        }
    }

    fun forwardMessage(message: Message, toChatId: Long) {
        viewModelScope.launch {
            val messageId = message.id.toLongOrNull() ?: return@launch
            telegram.forwardMessages(toChatId, activeChatId, longArrayOf(messageId))
        }
    }

    fun retry(message: Message) {
        viewModelScope.launch {
            telegram.retrySend(activeChatId, message.id.toLongOrNull() ?: return@launch)
        }
    }

    fun delete(message: Message) {
        viewModelScope.launch {
            telegram.deleteMessages(activeChatId, longArrayOf(message.id.toLongOrNull() ?: return@launch))
        }
    }

    fun onComposerChanged(text: String) {
        if (text.isBlank()) return
        if (typingJob?.isActive == true) return
        typingJob = viewModelScope.launch {
            telegram.sendTyping(activeChatId)
            delay(4_000)
        }
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

    override fun onCleared() {
        super.onCleared()
        if (opened) {
            telegram.closeChatAsync(activeChatId)
        }
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
