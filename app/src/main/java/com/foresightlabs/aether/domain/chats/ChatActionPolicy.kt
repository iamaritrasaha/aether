package com.foresightlabs.aether.domain.chats

import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ChatType

/**
 * A chat-list action, and what it means when it is performed.
 *
 * These are deliberately named for the Telegram operation they carry out rather than
 * for a generic word like "delete". "Delete chat" covers at least three different
 * server operations, and labelling a local hide as a delete is how a user loses
 * something they thought they had removed — or keeps something they thought was gone.
 */
enum class ChatAction {
    MARK_READ,
    MARK_UNREAD,
    PIN,
    UNPIN,
    MUTE,
    UNMUTE,
    ARCHIVE,
    UNARCHIVE,
    /** Empties the history; the conversation stays in the list. */
    CLEAR_HISTORY,
    /** Removes the conversation from this account only. */
    DELETE_FOR_ME,
    /** Removes the conversation from both sides. */
    DELETE_FOR_EVERYONE,
    /** Leaves a group, supergroup or channel. */
    LEAVE,
    /** Closes a secret chat, ending the encrypted session. */
    CLOSE_SECRET_CHAT,
    BLOCK,
    UNBLOCK,
    OPEN_PROFILE
}

/**
 * The single place that decides which chat-list actions a chat offers.
 *
 * Every action here maps to a real TDLib call whose effect Telegram reports back
 * through the update stream. Nothing in this policy hides or filters a chat locally
 * to imitate an operation.
 */
object ChatActionPolicy {

    fun actionsFor(chat: Chat): List<ChatAction> = buildList {
        addAll(readActions(chat))
        add(if (chat.isPinned) ChatAction.UNPIN else ChatAction.PIN)
        add(if (chat.isMuted) ChatAction.UNMUTE else ChatAction.MUTE)
        add(if (chat.isArchived) ChatAction.UNARCHIVE else ChatAction.ARCHIVE)
        add(ChatAction.OPEN_PROFILE)
        addAll(blockActions(chat))
        addAll(destructiveActions(chat))
    }

    /** The destructive actions, separated so a menu can set them apart. */
    fun destructiveActions(chat: Chat): List<ChatAction> = buildList {
        when (chat.type) {
            ChatType.SECRET -> add(ChatAction.CLOSE_SECRET_CHAT)
            ChatType.GROUP, ChatType.CHANNEL -> {
                if (chat.canDeleteOnlyForSelf || chat.canRevokeHistory) {
                    add(ChatAction.CLEAR_HISTORY)
                }
                if (chat.canLeave) add(ChatAction.LEAVE)
            }
            ChatType.DIRECT, ChatType.SAVED_MESSAGES -> {
                add(ChatAction.CLEAR_HISTORY)
                if (chat.canDeleteOnlyForSelf) add(ChatAction.DELETE_FOR_ME)
                if (chat.canRevokeHistory) add(ChatAction.DELETE_FOR_EVERYONE)
            }
        }
    }

    private fun readActions(chat: Chat): List<ChatAction> {
        val looksUnread = chat.unreadCount > 0 || chat.isMarkedAsUnread
        return listOf(if (looksUnread) ChatAction.MARK_READ else ChatAction.MARK_UNREAD)
    }

    private fun blockActions(chat: Chat): List<ChatAction> {
        // Only a private conversation has a single other party to block, and Saved
        // Messages is the account talking to itself.
        if (chat.type != ChatType.DIRECT) return emptyList()
        if (chat.blockableUserId == null) return emptyList()
        return listOf(if (chat.isBlocked) ChatAction.UNBLOCK else ChatAction.BLOCK)
    }

    /**
     * What a destructive action will actually do, for the confirmation sheet.
     *
     * The wording states the scope plainly, because the difference between clearing
     * a history and removing a conversation for both people is not recoverable.
     */
    fun confirmation(chat: Chat, action: ChatAction): ChatConfirmation? = when (action) {
        ChatAction.CLEAR_HISTORY -> ChatConfirmation(
            title = "Clear history",
            body = "Every message in ${chat.title} will be removed from this chat. " +
                "The conversation stays in your list.",
            confirmLabel = "Clear"
        )
        ChatAction.DELETE_FOR_ME -> ChatConfirmation(
            title = "Delete conversation",
            body = "${chat.title} will be removed from your chat list and its history " +
                "deleted for you. The other person keeps their copy.",
            confirmLabel = "Delete"
        )
        ChatAction.DELETE_FOR_EVERYONE -> ChatConfirmation(
            title = "Delete for everyone",
            body = "${chat.title} and its whole history will be deleted for both of " +
                "you. This cannot be undone.",
            confirmLabel = "Delete for everyone",
            isSevere = true
        )
        ChatAction.LEAVE -> ChatConfirmation(
            title = "Leave ${chat.title}",
            body = "You will stop receiving messages from this chat.",
            confirmLabel = "Leave"
        )
        ChatAction.CLOSE_SECRET_CHAT -> ChatConfirmation(
            title = "Close secret chat",
            body = "This ends the encrypted session with ${chat.title}. Its messages " +
                "are deleted on both devices.",
            confirmLabel = "Close",
            isSevere = true
        )
        ChatAction.BLOCK -> ChatConfirmation(
            title = "Block ${chat.title}",
            body = "They will not be able to message or call you.",
            confirmLabel = "Block"
        )
        else -> null
    }
}

/** Plain-language description of what a destructive chat action will do. */
data class ChatConfirmation(
    val title: String,
    val body: String,
    val confirmLabel: String,
    val isSevere: Boolean = false
)
