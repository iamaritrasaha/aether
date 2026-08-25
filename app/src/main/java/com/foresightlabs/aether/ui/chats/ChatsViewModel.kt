package com.foresightlabs.aether.ui.chats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.foresightlabs.aether.AetherApplication
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ConnectionStatus
import com.foresightlabs.aether.domain.model.User
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ChatsViewModel(application: Application) : AndroidViewModel(application) {
    private val telegram = (application as AetherApplication).telegram

    val chats: StateFlow<List<Chat>> = telegram.chatList.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        telegram.chatList.value
    )
    val currentUser: StateFlow<User?> = telegram.currentUser.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        telegram.currentUser.value
    )
    val connection: StateFlow<ConnectionStatus> = telegram.connection.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        telegram.connection.value
    )
    val isLoading: StateFlow<Boolean> = telegram.isLoadingChats.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        telegram.isLoadingChats.value
    )
}
