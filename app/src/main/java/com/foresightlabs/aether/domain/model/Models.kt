package com.foresightlabs.aether.domain.model

import androidx.compose.runtime.Immutable
import com.foresightlabs.aether.domain.messaging.ConversationClass
import com.foresightlabs.aether.domain.messaging.ConversationFacts
import com.foresightlabs.aether.domain.messaging.classifyConversation
import androidx.compose.ui.graphics.Color
import com.foresightlabs.aether.domain.calls.MediaConnectionState

enum class ChatType {
    DIRECT,
    GROUP,
    CHANNEL,
    SAVED_MESSAGES,
    SECRET
}

/**
 * What a conversation screen is showing.
 *
 * A forum topic is a distinct destination, not a filtered view of its chat: it has
 * its own history endpoint, its own draft, its own unread state, and messages sent
 * without its id land in the forum's root chat instead.
 */
@Immutable
sealed interface ConversationTarget {
    data class Chat(val chatId: Long) : ConversationTarget
    data class User(val userId: Long) : ConversationTarget
    data class Topic(val chatId: Long, val topicId: Int) : ConversationTarget

    /** The forum topic this target belongs to, or null for a plain conversation. */
    val forumTopicId: Int?
        get() = (this as? Topic)?.topicId
}

enum class MessageType {
    TEXT,
    IMAGE,
    /** A regular Telegram video message -- distinct from [VIDEO_NOTE]'s circular bubble. */
    VIDEO,
    ALBUM,
    VOICE,
    AUDIO,
    VIDEO_NOTE,
    FILE,
    FORWARDED,
    LINK_PREVIEW,
    STICKER,
    ANIMATION,
    POLL,
    CONTACT,
    LOCATION,
    VENUE,
    SERVICE,
    CALL,
    UNSUPPORTED
}

enum class MessageStatus {
    SENDING,
    SENT,
    READ,
    FAILED
}

@Immutable
data class StickerItem(
    val fileId: Int,
    val emoji: String = "",
    val width: Int = 512,
    val height: Int = 512,
    val isAnimated: Boolean = false,
    val isVideo: Boolean = false,
    val localPath: String? = null,
    val setId: Long = 0L
)

@Immutable
data class StickerSetInfo(
    val id: Long,
    val title: String,
    val name: String,
    val thumbnailPath: String? = null,
    val stickers: List<StickerItem> = emptyList()
)

@Immutable
data class AnimationItem(
    val fileId: Int,
    val width: Int = 0,
    val height: Int = 0,
    val duration: Int = 0,
    val fileName: String = "",
    val mimeType: String = "video/mp4",
    val thumbnailPath: String? = null,
    val localPath: String? = null
)

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

/**
 * A message's media, whose existence never depends on whether its bytes have
 * arrived yet. [url] is the best source Coil can load right now -- a real
 * local file path, or empty when nothing is available -- and [hasLocalFile]
 * says which. Until then [previewBase64] (TDLib's minithumbnail, when Telegram
 * sent one) lets a bubble show something better than a blank rectangle while
 * [isDownloading]/[isUploading] report real, ongoing TDLib file transfer state.
 */
@Immutable
data class MediaItem(
    val id: String,
    val url: String,
    val caption: String = "",
    val width: Int = 800,
    val height: Int = 600,
    val timestamp: String = "",
    val fileId: Int = 0,
    val hasLocalFile: Boolean = true,
    val isDownloading: Boolean = false,
    val downloadFailed: Boolean = false,
    val isUploading: Boolean = false,
    val uploadProgress: Float? = null,
    val previewBase64: String? = null,
    /**
     * True when [url] is a thumbnail standing in for playable video, not the
     * displayed image itself. The thumbnail's own file state ([fileId],
     * [hasLocalFile], [isDownloading], [downloadFailed]) is unaffected --
     * [videoFileId]/[videoLocalPath] describe the separate playable file.
     */
    val isVideo: Boolean = false,
    /** TDLib file id of the actual video content, distinct from the thumbnail's [fileId]. */
    val videoFileId: Int = 0,
    /** Local path of the video content, when already downloaded; blank otherwise. */
    val videoLocalPath: String = ""
)

@Immutable
data class ReplyPreview(
    val chatId: Long,
    val messageId: Long,
    val senderName: String,
    val text: String,
    val type: MessageType = MessageType.TEXT,
    val isQuotedExcerpt: Boolean = false,
    val isAvailable: Boolean = true,
    val isResolving: Boolean = false,
    val isNavigable: Boolean = true
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
    val replyPreview: ReplyPreview? = null,
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
    val canRetry: Boolean = false,
    val autoDeleteIn: Double = 0.0,
    val selfDestructIn: Double = 0.0,
    val liveLocationExpiresIn: Int = 0,
    val isLiveLocation: Boolean = false,
    val venueTitle: String? = null,
    val venueAddress: String? = null,
    val stickerFormat: String? = null,
    /** Present only for poll messages; every count in it is Telegram's. */
    val poll: PollPresentation? = null,
    /**
     * Telegram's album grouping id, or 0 when the message stands alone.
     *
     * Messages sharing a non-zero id were sent together and must be shown as one
     * cluster rather than as a column of independent bubbles.
     */
    val mediaAlbumId: Long = 0L,
    /**
     * [text] together with the formatting Telegram attached to it.
     *
     * Defaults to the plain text so a message constructed without entities still
     * renders identically; nothing has to opt in.
     */
    val formatted: com.foresightlabs.aether.domain.text.AetherText? = null,
    /**
     * True when this is a reply preview showing a *quoted span* rather than the
     * whole original message, so the UI can mark it as an excerpt.
     */
    val isQuotedExcerpt: Boolean = false,
    /** UI-only identity bridge used while TDLib replaces a temporary send id. */
    val presentationKey: String? = null
) {
    /** The message's text with its entities, falling back to plain text. */
    val richText: com.foresightlabs.aether.domain.text.AetherText
        get() = formatted ?: com.foresightlabs.aether.domain.text.AetherText(text)
}

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
    val hasUnseenPulse: Boolean = false,
    /** Whether Telegram currently holds this chat in the archive list. */
    val isArchived: Boolean = false,
    /** Whether the account has explicitly marked the chat unread on the server. */
    val isMarkedAsUnread: Boolean = false,
    /** Whether the account may leave this chat, per its membership status. */
    val canLeave: Boolean = false,
    /** Whether the account may delete this chat's history for everyone. */
    val canRevokeHistory: Boolean = false,
    /** Whether the account may clear this chat's history for itself only. */
    val canDeleteOnlyForSelf: Boolean = false,
    /** The other party, when this is a private chat that can be blocked. */
    val blockableUserId: Long? = null,
    /** Whether the other party is currently blocked. */
    val isBlocked: Boolean = false,
    /** Id of the newest message, for marking the chat read. */
    val lastMessageId: Long = 0L,
    /**
     * Whether this is a forum supergroup.
     *
     * A forum opens as a list of topics rather than as one conversation; its
     * messages belong to topics, and routing them through the chat interleaves
     * every topic together.
     */
    val isForum: Boolean = false
) {
    /**
     * What this conversation is, per the one canonical rule set in
     * [classifyConversation]. Everything below is a reading of this, so the chat
     * list, search and the notification path cannot disagree about a chat.
     */
    val conversationClass: ConversationClass
        get() {
            val isOneToOne = type == ChatType.DIRECT || type == ChatType.SECRET
            return classifyConversation(
                ConversationFacts(
                    isOneToOne = isOneToOne,
                    isForum = isForum,
                    isSavedMessages = type == ChatType.SAVED_MESSAGES,
                    // Only meaningful for a one-to-one chat; a group's own id is
                    // not a user id and must never be matched against one.
                    counterpartUserId = if (isOneToOne) blockableUserId ?: id.toLongOrNull() else null,
                    isBot = directUser?.isBot == true,
                    isDeleted = directUser?.isDeleted == true,
                    // This model is only ever built from a chat TDLib already
                    // resolved, so the counterpart is known by construction. The
                    // UNKNOWN outcome belongs to the notification path, which
                    // classifies from live lookups that can genuinely fail.
                    isCounterpartKnown = true
                )
            )
        }

    /**
     * Whether this chat is a 1:1 personal conversation between real human users.
     *
     * Home's primary feed is built from these plus [isTelegramService]. Groups,
     * supergroups, channels, forums, bots, Saved Messages / self chat and deleted
     * accounts are not personal chats.
     *
     * Telegram's own service account is not a personal chat either -- it is not a
     * person -- but it is emphatically not filtered out; see [isTelegramService].
     */
    val isPersonalChat: Boolean
        get() = conversationClass == ConversationClass.PERSONAL_HUMAN

    /**
     * Whether this is Telegram's own service/notification account (login codes,
     * security notices). Never hidden by personal-chat filtering.
     */
    val isTelegramService: Boolean
        get() = conversationClass == ConversationClass.TELEGRAM_SERVICE

    /** Whether Aether surfaces this chat in its primary messaging surfaces at all. */
    val isDeliverableConversation: Boolean
        get() = conversationClass.isDeliverable
}

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

enum class CallStateEnum {
    PENDING,
    EXCHANGING_KEYS,
    READY,
    HANGING_UP,
    DISCARDED,
    ERROR
}

@Immutable
data class ActiveCall(
    val callId: Int,
    val userId: Long,
    val user: User? = null,
    val isOutgoing: Boolean = true,
    val isVideo: Boolean = false,
    val state: CallStateEnum = CallStateEnum.PENDING,
    val mediaState: MediaConnectionState = MediaConnectionState.IDLE,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val durationSec: Int = 0,
    val isMinimized: Boolean = false,
    val errorMessage: String? = null
)

enum class CallOutcome {
    COMPLETED,
    MISSED,
    DECLINED,
    CANCELLED,
    FAILED
}

@Immutable
data class CallHistoryItem(
    val id: String,
    val messageId: Long,
    val chatId: Long,
    val userId: Long,
    val user: User? = null,
    val isOutgoing: Boolean = false,
    val isVideo: Boolean = false,
    val outcome: CallOutcome = CallOutcome.COMPLETED,
    val durationSeconds: Int = 0,
    val timestampSeconds: Int = 0,
    val formattedTimestamp: String = "",
    val formattedDuration: String = ""
)

sealed interface CallHistoryUiState {
    data object Loading : CallHistoryUiState
    data class Content(
        val items: List<CallHistoryItem>,
        val hasMore: Boolean = false,
        val isLoadingMore: Boolean = false,
        val nextOffset: String = ""
    ) : CallHistoryUiState
    data class Error(val message: String) : CallHistoryUiState
    data object Empty : CallHistoryUiState
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
        val isNumeric: Boolean = true,
        val isLoading: Boolean = false,
        val error: String? = null
    ) : AuthUiState
    data class Password(
        val hint: String?,
        val hasRecoveryEmailAddress: Boolean = false,
        val recoveryEmailAddressPattern: String? = null,
        val isLoading: Boolean = false,
        val error: String? = null
    ) : AuthUiState
    data class EmailAddress(
        val allowAppleId: Boolean = false,
        val allowGoogleId: Boolean = false,
        val isLoading: Boolean = false,
        val error: String? = null
    ) : AuthUiState
    data class EmailCode(
        val addressPattern: String,
        val codeLength: Int?,
        val canReset: Boolean = false,
        val resetWaitSeconds: Int? = null,
        val isLoading: Boolean = false,
        val error: String? = null
    ) : AuthUiState
    data class Registration(
        val termsOfServiceText: String? = null,
        val minAge: Int = 0,
        val showPopup: Boolean = false,
        val isLoading: Boolean = false,
        val error: String? = null
    ) : AuthUiState
    data class OtherDevice(val link: String) : AuthUiState
    data class Unsupported(val description: String) : AuthUiState
    data object LoggingOut : AuthUiState
    data object Closing : AuthUiState
    data object Ready : AuthUiState
}
