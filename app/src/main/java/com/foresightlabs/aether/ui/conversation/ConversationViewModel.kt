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
    private val chatId: Long
) : AndroidViewModel(application) {
    private val telegram = (application as AetherApplication).telegram

    private val _header = MutableStateFlow(telegram.chat(chatId))
    val header: StateFlow<Chat?> = _header.asStateFlow()

    val messages: StateFlow<List<Message>> = telegram.messagesFlow(chatId)

    private val _loadingOlder = MutableStateFlow(false)
    val loadingOlder: StateFlow<Boolean> = _loadingOlder.asStateFlow()

    private val _composerEnabled = MutableStateFlow(true)
    val composerEnabled: StateFlow<Boolean> = _composerEnabled.asStateFlow()

    private val _sendError = MutableStateFlow<String?>(null)
    val sendError: StateFlow<String?> = _sendError.asStateFlow()

    private var oldestId: Long = 0L
    private var historyComplete = false
    private var opened = false
    private var typingJob: Job? = null
    private var sendInFlight = false

    init {
        viewModelScope.launch {
            telegram.chatList.collect { list ->
                _header.value = list.firstOrNull { it.id == chatId.toString() } ?: telegram.chat(chatId)
                _composerEnabled.value = _header.value?.canSendText != false
            }
        }
        viewModelScope.launch { start() }
    }

    private suspend fun start() {
        telegram.ensureChatLoaded(chatId)
        telegram.openChat(chatId)
        opened = true
        loadInitial()
    }

    private suspend fun loadInitial() {
        val page = telegram.loadHistory(chatId, 0L)
        if (page.isNotEmpty()) {
            oldestId = page.first().id.toLongOrNull() ?: 0L
            telegram.upsertConversation(chatId, page, prepend = true)
            telegram.viewMessages(chatId, page.mapNotNull { it.id.toLongOrNull() }.toLongArray())
        }
        if (page.size < 20) historyComplete = true
    }

    fun loadOlder() {
        if (historyComplete || _loadingOlder.value || oldestId == 0L) return
        viewModelScope.launch {
            _loadingOlder.value = true
            val page = telegram.loadHistory(chatId, oldestId)
            if (page.isEmpty()) {
                historyComplete = true
            } else {
                oldestId = page.first().id.toLongOrNull() ?: oldestId
                telegram.upsertConversation(chatId, page, prepend = true)
            }
            _loadingOlder.value = false
        }
    }

    fun send(text: String, replyToId: String?) {
        val trimmedEnd = text.trimEnd()
        if (trimmedEnd.isEmpty() || sendInFlight) return
        sendInFlight = true
        viewModelScope.launch {
            val result = telegram.sendText(chatId, trimmedEnd, replyToId?.toLongOrNull())
            sendInFlight = false
            result.exceptionOrNull()?.message?.let { _sendError.value = it }
        }
    }

    fun retry(message: Message) {
        viewModelScope.launch {
            telegram.retrySend(chatId, message.id.toLongOrNull() ?: return@launch)
        }
    }

    fun delete(message: Message) {
        viewModelScope.launch {
            telegram.deleteMessages(chatId, longArrayOf(message.id.toLongOrNull() ?: return@launch))
        }
    }

    fun onComposerChanged(text: String) {
        if (text.isBlank()) return
        if (typingJob?.isActive == true) return
        typingJob = viewModelScope.launch {
            telegram.sendTyping(chatId)
            delay(4_000)
        }
    }

    fun markVisible(ids: List<String>) {
        viewModelScope.launch {
            val longs = ids.mapNotNull { it.toLongOrNull() }.toLongArray()
            telegram.viewMessages(chatId, longs)
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (opened) {
            telegram.closeChatAsync(chatId)
        }
    }

    class Factory(
        private val application: Application,
        private val chatId: Long
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ConversationViewModel(application, chatId) as T
        }
    }
}
