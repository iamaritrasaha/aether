package com.foresightlabs.aether.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.foresightlabs.aether.AetherApplication
import com.foresightlabs.aether.domain.model.Chat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import com.foresightlabs.aether.domain.search.GlobalMessageHit
import com.foresightlabs.aether.domain.search.GlobalSearchState
import kotlinx.coroutines.launch

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val telegram = (application as AetherApplication).telegram

    private val _state = MutableStateFlow(GlobalSearchState.Idle)
    val state: StateFlow<GlobalSearchState> = _state.asStateFlow()

    /** Conversations only, for callers that still take a flat list. */
    val results: StateFlow<List<Chat>> = _state
        .map { it.chats }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var job: Job? = null

    /**
     * Searches Telegram for [text] across conversations, people and messages.
     *
     * The three sources are queried concurrently and land independently, so results
     * appear as they arrive rather than waiting on the slowest one. Each category
     * clears its own loading flag, which is what lets "no results" stay honest.
     */
    fun query(text: String) {
        job?.cancel()
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            _state.value = GlobalSearchState(query = text)
            return
        }
        _state.value = _state.value.copy(
            query = text,
            isLoadingChats = true,
            isLoadingMessages = true,
            error = null
        )
        job = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)

            launch {
                val chats = telegram.searchChats(trimmed).filter { it.isPersonalChat }
                val contacts = telegram.searchContactChats(trimmed).filter { it.isPersonalChat }
                _state.update { current ->
                    if (current.query.trim() != trimmed) return@update current
                    current.copy(
                        chats = chats,
                        // A conversation already listed above is not repeated here.
                        contacts = contacts.filter { contact -> chats.none { it.id == contact.id } },
                        isLoadingChats = false
                    )
                }
            }

            launch {
                telegram.searchMessagesGlobally(trimmed).fold(
                    onSuccess = { found ->
                        val hits = found.messages.filterNotNull().map { raw ->
                            GlobalMessageHit(
                                message = telegram.mapFoundMessage(raw),
                                chat = telegram.chat(raw.chatId)
                            )
                        }.filter { it.chat?.isPersonalChat != false }
                        _state.update { current ->
                            if (current.query.trim() != trimmed) return@update current
                            current.copy(
                                messages = hits,
                                messagesTotal = found.totalCount,
                                messagesCursor = found.nextOffset.orEmpty(),
                                hasMoreMessages = !found.nextOffset.isNullOrBlank() && hits.isNotEmpty(),
                                isLoadingMessages = false
                            )
                        }
                    },
                    onFailure = { error ->
                        _state.update { current ->
                            if (current.query.trim() != trimmed) return@update current
                            current.copy(
                                isLoadingMessages = false,
                                error = error.message ?: "Couldn't search messages"
                            )
                        }
                    }
                )
            }
        }
    }

    /** Pages in more message results using TDLib's own continuation offset. */
    fun loadMoreMessages() {
        val current = _state.value
        if (!current.hasMoreMessages || current.isLoadingMessages) return
        val trimmed = current.query.trim()
        if (trimmed.isBlank()) return

        _state.value = current.copy(isLoadingMessages = true)
        viewModelScope.launch {
            telegram.searchMessagesGlobally(trimmed, offset = current.messagesCursor).fold(
                onSuccess = { found ->
                    val more = found.messages.filterNotNull().map { raw ->
                        GlobalMessageHit(
                            message = telegram.mapFoundMessage(raw),
                            chat = telegram.chat(raw.chatId)
                        )
                    }.filter { it.chat?.isPersonalChat != false }
                    _state.update { latest ->
                        val known = latest.messages.map { it.message.id }.toSet()
                        latest.copy(
                            messages = latest.messages + more.filter { it.message.id !in known },
                            messagesCursor = found.nextOffset.orEmpty(),
                            hasMoreMessages = !found.nextOffset.isNullOrBlank() && more.isNotEmpty(),
                            isLoadingMessages = false
                        )
                    }
                },
                onFailure = {
                    _state.update { it.copy(isLoadingMessages = false, hasMoreMessages = false) }
                }
            )
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 220L
    }
}
