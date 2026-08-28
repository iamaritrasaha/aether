package com.foresightlabs.aether.data.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ActiveConversationTracker {

    private val _isForeground = MutableStateFlow(false)
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    private val _activeChatId = MutableStateFlow<Long?>(null)
    val activeChatId: StateFlow<Long?> = _activeChatId.asStateFlow()

    private val _activeTopicId = MutableStateFlow<Int?>(null)
    val activeTopicId: StateFlow<Int?> = _activeTopicId.asStateFlow()

    private val _pendingNavigationChatId = MutableStateFlow<Long?>(null)
    val pendingNavigationChatId: StateFlow<Long?> = _pendingNavigationChatId.asStateFlow()

    fun setAppForeground(foreground: Boolean) {
        _isForeground.value = foreground
    }

    fun setActiveConversation(chatId: Long?, topicId: Int? = null) {
        _activeChatId.value = chatId
        _activeTopicId.value = topicId
    }

    fun setPendingNavigationChatId(chatId: Long?) {
        _pendingNavigationChatId.value = chatId
    }

    fun consumePendingNavigationChatId(): Long? {
        val target = _pendingNavigationChatId.value
        _pendingNavigationChatId.value = null
        return target
    }

    fun shouldSuppressNotification(chatId: Long, topicId: Int? = null): Boolean {
        if (!_isForeground.value) {
            // App is in the background, never suppress system notifications
            return false
        }
        val currentChat = _activeChatId.value ?: return false
        if (currentChat != chatId) {
            // Different chat is open in foreground, allow notification
            return false
        }
        val currentTopic = _activeTopicId.value
        if (topicId != null && currentTopic != null && topicId != currentTopic) {
            // Different topic in the same forum supergroup is open, allow notification
            return false
        }
        // Exactly the same conversation is currently open and visible on screen
        return true
    }
}
