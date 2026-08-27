package com.foresightlabs.aether.ui.forum

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.foresightlabs.aether.AetherApplication
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ForumTopicSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The topics of one forum supergroup.
 *
 * Ordering, unread counts and drafts are Telegram's — the list is never sorted or
 * filtered locally, because a topic's order is server state other clients agree on.
 */
class ForumTopicsViewModel(
    application: Application,
    private val chatId: Long
) : AndroidViewModel(application) {

    private val telegram = (application as AetherApplication).telegram

    private val _topics = MutableStateFlow<List<ForumTopicSummary>>(emptyList())
    val topics: StateFlow<List<ForumTopicSummary>> = _topics.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val chat: Chat? get() = telegram.chat(chatId)

    init {
        refresh()
        // Topic changes made anywhere — including on another client — re-read the
        // list rather than leaving it stale until the user pulls to refresh.
        viewModelScope.launch {
            telegram.forumTopicRevision.collect { refresh() }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _topics.value = telegram.forumTopics(chatId)
            _isLoading.value = false
        }
    }

    fun createTopic(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            telegram.createForumTopic(chatId, name.trim())
                .onFailure { _error.value = it.message }
            refresh()
        }
    }

    fun renameTopic(topic: ForumTopicSummary, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            telegram.renameForumTopic(chatId, topic.topicId, name.trim())
                .onFailure { _error.value = it.message }
            refresh()
        }
    }

    fun setClosed(topic: ForumTopicSummary, closed: Boolean) {
        viewModelScope.launch {
            telegram.setForumTopicClosed(chatId, topic.topicId, closed)
                .onFailure { _error.value = it.message }
            refresh()
        }
    }

    fun setPinned(topic: ForumTopicSummary, pinned: Boolean) {
        viewModelScope.launch {
            telegram.setForumTopicPinned(chatId, topic.topicId, pinned)
                .onFailure { _error.value = it.message }
            refresh()
        }
    }

    fun deleteTopic(topic: ForumTopicSummary) {
        // Telegram's General topic cannot be removed, so it is never offered.
        if (topic.isGeneral) return
        viewModelScope.launch {
            telegram.deleteForumTopic(chatId, topic.topicId)
                .onFailure { _error.value = it.message }
            refresh()
        }
    }

    fun clearError() {
        _error.value = null
    }

    class Factory(
        private val application: Application,
        private val chatId: Long
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ForumTopicsViewModel(application, chatId) as T
    }
}
