package com.foresightlabs.aether.domain.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

enum class ChatType {
    DIRECT,
    GROUP,
    CHANNEL,
    SAVED_MESSAGES
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

@Immutable
data class User(
    val id: String,
    val name: String,
    val username: String,
    val avatarInitials: String,
    val avatarGradient: List<Color>,
    val isOnline: Boolean = false,
    val lastSeenText: String = "",
    val bio: String = "",
    val phone: String = "",
    val isVerified: Boolean = false,
    val isPremium: Boolean = false,
    val photoPath: String? = null
)

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
    val canSendText: Boolean = true
)

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

enum class ChatFilterCategory(val label: String) {
    ALL("All"),
    DIRECT("Direct"),
    GROUPS("Groups"),
    CHANNELS("Channels"),
    UNREAD("Unread")
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
