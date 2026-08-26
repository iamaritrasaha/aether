package com.foresightlabs.aether.ui.pulse

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.foresightlabs.aether.AetherApplication
import com.foresightlabs.aether.domain.model.StoryItem
import com.foresightlabs.aether.domain.model.StoryPrivacy
import com.foresightlabs.aether.domain.model.UserPulse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PulseViewerState(
    val pulse: UserPulse,
    val initialStoryIndex: Int = 0
)

class PulseViewModel(application: Application) : AndroidViewModel(application) {
    private val telegram = (application as AetherApplication).telegram

    val myPulse: StateFlow<UserPulse?> = telegram.myPulse.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        telegram.myPulse.value
    )

    val pulses: StateFlow<List<UserPulse>> = telegram.pulses.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        telegram.pulses.value
    )

    val canPostPulse: StateFlow<Boolean> = telegram.canPostPulse.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        telegram.canPostPulse.value
    )

    private val _viewerState = MutableStateFlow<PulseViewerState?>(null)
    val viewerState: StateFlow<PulseViewerState?> = _viewerState.asStateFlow()

    private val _isPosting = MutableStateFlow(false)
    val isPosting: StateFlow<Boolean> = _isPosting.asStateFlow()

    private val _postError = MutableStateFlow<String?>(null)
    val postError: StateFlow<String?> = _postError.asStateFlow()

    fun openViewer(pulse: UserPulse, startIndex: Int = 0) {
        _viewerState.value = PulseViewerState(pulse, startIndex)
        val currentStory = pulse.stories.getOrNull(startIndex)
        if (currentStory != null) {
            viewModelScope.launch {
                telegram.openStory(pulse.chatId, currentStory.id)
            }
        }
    }

    fun closeViewer() {
        val active = _viewerState.value
        if (active != null) {
            val story = active.pulse.stories.getOrNull(active.initialStoryIndex)
            if (story != null) {
                viewModelScope.launch {
                    telegram.closeStory(active.pulse.chatId, story.id)
                }
            }
        }
        _viewerState.value = null
    }

    fun onStoryChanged(pulse: UserPulse, storyIndex: Int) {
        _viewerState.value = PulseViewerState(pulse, storyIndex)
        val story = pulse.stories.getOrNull(storyIndex)
        if (story != null) {
            viewModelScope.launch {
                telegram.openStory(pulse.chatId, story.id)
            }
        }
    }

    fun sendReaction(chatId: Long, storyId: Int, emoji: String) {
        viewModelScope.launch {
            telegram.setStoryReaction(chatId, storyId, emoji)
        }
    }

    fun sendReply(chatId: Long, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            telegram.sendText(chatId, text, null)
        }
    }

    fun postPulse(photoPath: String, caption: String, privacy: StoryPrivacy, onComplete: () -> Unit) {
        viewModelScope.launch {
            _isPosting.value = true
            _postError.value = null
            val result = telegram.postStoryPhoto(photoPath, caption, privacy)
            _isPosting.value = false
            if (result.isSuccess) {
                onComplete()
            } else {
                _postError.value = result.exceptionOrNull()?.message ?: "Failed to post Pulse"
            }
        }
    }

    fun deletePulse(storyId: Int) {
        viewModelScope.launch {
            telegram.deleteStory(storyId)
        }
    }
}
