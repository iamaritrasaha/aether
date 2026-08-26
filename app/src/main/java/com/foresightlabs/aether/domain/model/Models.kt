package com.foresightlabs.aether.domain.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

enum class ChatType {
    DIRECT,
    GROUP,
    CHANNEL,
    SAVED_MESSAGES,
    SECRET
}

@Immutable
sealed interface ConversationTarget {
    data class Chat(val chatId: Long) : ConversationTarget
    data class User(val userId: Long) : ConversationTarget
}

enum class MessageType {
    TEXT,
    IMAGE,
    ALBUM,
    VOICE,
    FILE,
    FORWARDED,
    LINK_PREVIEW,
    STICKER,
    UNSUPPORTED
}

enum class MessageStatus {
    SENDING,
    SENT,
    READ,
    FAILED
}

/**
 * Truthful presence, mapped one-to-one from TDLib's own user status.
 *
 * Telegram privacy settings routinely hide exact presence. [RECENTLY],
 * [WITHIN_WEEK] and [WITHIN_MONTH] are deliberately approximate and must never be
 * presented to the user as "Online".
 */
enum class Presence {
    /** TdApi.UserStatusOnline — exact and current. */
    ONLINE,
    /** TdApi.UserStatusOffline with a known last-seen time. */
    OFFLINE,
    /** TdApi.UserStatusRecently — approximate, privacy-limited. */
    RECENTLY,
    WITHIN_WEEK,
    WITHIN_MONTH,
    /** No status available at all. */
    UNKNOWN;

    val isExact: Boolean get() = this == ONLINE || this == OFFLINE

    val isApproximate: Boolean
        get() = this == RECENTLY || this == WITHIN_WEEK || this == WITHIN_MONTH
}

@Immutable
data class User(
    val id: String,
    val name: String,
    val username: String,
    val avatarInitials: String,
    val avatarGradient: List<Color>,
    val presence: Presence = Presence.UNKNOWN,
    val lastSeenText: String = "",
    val bio: String = "",
    val phone: String = "",
    val isVerified: Boolean = false,
    val isPremium: Boolean = false,
    val isBot: Boolean = false,
    val isContact: Boolean = false,
    val isDeleted: Boolean = false,
    val photoPath: String? = null
) {
    /** True only when Telegram reports this person as genuinely online right now. */
    val isOnline: Boolean get() = presence == Presence.ONLINE
}

@Immutable
data class Reaction(
    val emoji: String,
    val count: Int,
    val userReacted: Boolean = false
)

@Immutable
data class LinkPreview(
    val url: String,
    val title: String,
    val description: String,
    val siteName: String,
    val thumbnailUrl: String? = null
)

@Immutable
data class MediaItem(
    val id: String,
    val url: String,
    val caption: String = "",
    val width: Int = 800,
    val height: Int = 600,
    val timestamp: String = ""
)

@Immutable
data class Message(
    val id: String,
    val chatId: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val timestamp: String,
    val dateSeconds: Int = 0,
    val isOutgoing: Boolean,
    val status: MessageStatus = MessageStatus.SENT,
    val isEdited: Boolean = false,
    val type: MessageType = MessageType.TEXT,
    val replyToMessage: Message? = null,
    val forwardedFrom: String? = null,
    val voiceDurationSec: Int = 0,
    val voiceWaveform: List<Float> = emptyList(),
    val mediaItems: List<MediaItem> = emptyList(),
    val fileName: String? = null,
    val fileSize: String? = null,
    val fileExtension: String? = null,
    val linkPreview: LinkPreview? = null,
    val reactions: List<Reaction> = emptyList(),
    val isPinned: Boolean = false,
    val canRetry: Boolean = false
)

@Immutable
data class Chat(
    val id: String,
    val title: String,
    val type: ChatType,
    val lastMessageText: String,
    val lastMessageTime: String,
    val unreadCount: Int = 0,
    val unreadMentionCount: Int = 0,
    val isMuted: Boolean = false,
    val isVerified: Boolean = false,
    val isPinned: Boolean = false,
    val isTyping: Boolean = false,
    val typingText: String? = null,
    val draftText: String? = null,
    val avatarInitials: String,
    val avatarGradient: List<Color>,
    val memberCount: Int = 0,
    val onlineCount: Int = 0,
    val subtitle: String = "",
    val lastMessageStatus: MessageStatus = MessageStatus.READ,
    val isLastMessageOutgoing: Boolean = false,
    val directUser: User? = null,
    val photoPath: String? = null,
    val order: Long = 0L,
    val canSendText: Boolean = true,
    val hasUnseenPulse: Boolean = false
)

@Immutable
data class StoryItem(
    val id: Int,
    val senderChatId: Long,
    val senderName: String,
    val dateSeconds: Int,
    val expiresInSeconds: Int = 86400,
    val isForCloseFriends: Boolean = false,
    val caption: String = "",
    val mediaUrl: String? = null,
    val isVideo: Boolean = false,
    val videoDuration: Double = 0.0,
    val isSeen: Boolean = false,
    val reactionEmoji: String? = null
)

enum class StoryPrivacy(val label: String, val description: String) {
    EVERYONE("Everyone", "All Telegram users who can see your profile"),
    CONTACTS("My Contacts", "Only people saved in your contacts"),
    CLOSE_FRIENDS("Close Friends", "Your selected close friends list")
}

@Immutable
data class UserPulse(
    val chatId: Long,
    val name: String,
    val username: String = "",
    val avatarInitials: String,
    val avatarGradient: List<Color>,
    val photoPath: String? = null,
    val isOnline: Boolean = false,
    val stories: List<StoryItem> = emptyList(),
    val maxReadStoryId: Int = 0,
    val isMine: Boolean = false
) {
    val hasUnseen: Boolean get() = stories.any { it.id > maxReadStoryId }
    val latestStory: StoryItem? get() = stories.lastOrNull()
}

@Immutable
data class CallRecord(
    val id: String,
    val user: User,
    val isIncoming: Boolean,
    val isMissed: Boolean,
    val isVideo: Boolean,
    val timestamp: String,
    val durationText: String
)

/**
 * People-first filtering. People is the default home context; groups and channels
 * remain fully present but secondary. There is deliberately no dominant "All" tab.
 */
enum class ChatFilterCategory(val label: String) {
    PEOPLE("People"),
    GROUPS("Groups"),
    CHANNELS("Channels"),
    UNREAD("Unread");

    fun matches(chat: Chat): Boolean = when (this) {
        PEOPLE -> chat.type == ChatType.DIRECT || chat.type == ChatType.SAVED_MESSAGES || chat.type == ChatType.SECRET
        GROUPS -> chat.type == ChatType.GROUP
        CHANNELS -> chat.type == ChatType.CHANNEL
        UNREAD -> chat.unreadCount > 0 || chat.unreadMentionCount > 0
    }
}

enum class ConnectionStatus(val label: String) {
    WAITING_FOR_NETWORK("Waiting for network"),
    CONNECTING("Connecting"),
    CONNECTING_PROXY("Connecting via proxy"),
    UPDATING("Updating"),
    READY("Connected"),
    UNKNOWN("")
}

sealed interface AuthUiState {
    data object Initializing : AuthUiState
    data object MissingCredentials : AuthUiState
    data class Phone(
        val isLoading: Boolean = false,
        val error: String? = null
    ) : AuthUiState
    data class Code(
        val phoneNumber: String,
        val codeLength: Int?,
        val hint: String,
        val isLoading: Boolean = false,
        val error: String? = null
    ) : AuthUiState
    data class Password(
        val hint: String?,
        val isLoading: Boolean = false,
        val error: String? = null
    ) : AuthUiState
    data class Registration(
        val termsOfServiceText: String? = null,
        val minAge: Int = 0,
        val isLoading: Boolean = false,
        val error: String? = null
    ) : AuthUiState
    data class OtherDevice(val link: String) : AuthUiState
    data class Unsupported(val description: String) : AuthUiState
    data object LoggingOut : AuthUiState
    data object Closing : AuthUiState
    data object Ready : AuthUiState
}
