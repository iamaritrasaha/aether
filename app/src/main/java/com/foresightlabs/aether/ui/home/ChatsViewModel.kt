package com.foresightlabs.aether.ui.home
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.foresightlabs.aether.AetherApplication
import com.foresightlabs.aether.domain.chats.ChatAction
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ChatFolder
import com.foresightlabs.aether.domain.model.ConnectionStatus
import com.foresightlabs.aether.domain.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    /** The account's Telegram folders, main list included, in server order. */
    val folders: StateFlow<List<ChatFolder>> = telegram.chatFolders.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        telegram.chatFolders.value
    )

    private val _selectedFolder = MutableStateFlow(ChatFolder.Main)
    val selectedFolder: StateFlow<ChatFolder> = _selectedFolder.asStateFlow()

    /**
     * The chats of the selected folder.
     *
     * Membership and ordering are Telegram's. Aether's own People / Groups chips
     * filter whatever this produces; they are a view over a folder, never a
     * substitute for one.
     */
    val folderChats: StateFlow<List<Chat>> = combine(chats, _selectedFolder) { all, folder ->
        if (folder.isMainList) all else telegram.chatsInFolder(folder)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectFolder(folder: ChatFolder) {
        _selectedFolder.value = folder
    }

    private val _actionError = MutableStateFlow<String?>(null)

    /** The last chat-list operation that Telegram refused, for display. */
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    fun clearActionError() {
        _actionError.value = null
    }

    /**
     * Performs a chat-list action against Telegram.
     *
     * Every branch is a real server operation. Nothing here adjusts Aether's local
     * chat list — the list is rebuilt from the update Telegram sends back, so a
     * refused operation simply leaves the row as it was.
     */
    fun perform(chat: Chat, action: ChatAction) {
        val chatId = chat.id.toLongOrNull() ?: return
        viewModelScope.launch {
            val result = when (action) {
                ChatAction.MARK_READ -> telegram.readChat(chatId, chat.lastMessageId)
                    .also { if (chat.isMarkedAsUnread) telegram.setChatMarkedAsUnread(chatId, false) }
                ChatAction.MARK_UNREAD -> telegram.setChatMarkedAsUnread(chatId, true)
                ChatAction.PIN -> telegram.setChatPinned(chatId, true, chat.isArchived)
                ChatAction.UNPIN -> telegram.setChatPinned(chatId, false, chat.isArchived)
                ChatAction.MUTE -> telegram.setChatMuted(chatId, MUTE_FOREVER_SECONDS)
                ChatAction.UNMUTE -> telegram.setChatMuted(chatId, 0)
                ChatAction.ARCHIVE -> telegram.setChatArchived(chatId, true)
                ChatAction.UNARCHIVE -> telegram.setChatArchived(chatId, false)
                ChatAction.CLEAR_HISTORY -> telegram.deleteChatHistory(
                    chatId = chatId,
                    removeFromChatList = false,
                    revoke = false
                )
                ChatAction.DELETE_FOR_ME -> telegram.deleteChatHistory(
                    chatId = chatId,
                    removeFromChatList = true,
                    revoke = false
                )
                ChatAction.DELETE_FOR_EVERYONE -> telegram.deleteChatHistory(
                    chatId = chatId,
                    removeFromChatList = true,
                    revoke = true
                )
                ChatAction.LEAVE, ChatAction.CLOSE_SECRET_CHAT -> telegram.leaveChat(chatId)
                ChatAction.BLOCK -> chat.blockableUserId
                    ?.let { telegram.setUserBlocked(it, true) }
                    ?: Result.success(Unit)
                ChatAction.UNBLOCK -> chat.blockableUserId
                    ?.let { telegram.setUserBlocked(it, false) }
                    ?: Result.success(Unit)
                // Navigation, not a server operation.
                ChatAction.OPEN_PROFILE -> Result.success(Unit)
            }
            result.exceptionOrNull()?.message?.let { _actionError.value = it }
        }
    }

    fun createChatFolder(title: String) {
        viewModelScope.launch {
            val result = telegram.createChatFolder(title)
            result.exceptionOrNull()?.message?.let { _actionError.value = it }
        }
    }

    fun editChatFolder(folderId: Int, title: String) {
        viewModelScope.launch {
            val result = telegram.editChatFolder(folderId, title)
            result.exceptionOrNull()?.message?.let { _actionError.value = it }
        }
    }

    fun deleteChatFolder(folderId: Int) {
        viewModelScope.launch {
            val result = telegram.deleteChatFolder(folderId)
            result.exceptionOrNull()?.message?.let { _actionError.value = it }
            if (_selectedFolder.value.id == folderId) {
                _selectedFolder.value = ChatFolder.Main
            }
        }
    }

    fun reorderChatFolders(folderIds: List<Int>) {
        viewModelScope.launch {
            val result = telegram.reorderChatFolders(folderIds.toIntArray())
            result.exceptionOrNull()?.message?.let { _actionError.value = it }
        }
    }

    private companion object {
        /**
         * Telegram's own sentinel for an indefinite mute; anything smaller is a
         * timed mute that silently expires.
         */
        const val MUTE_FOREVER_SECONDS = 400 * 24 * 60 * 60
    }
}
