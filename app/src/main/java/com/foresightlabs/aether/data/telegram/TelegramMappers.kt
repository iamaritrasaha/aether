package com.foresightlabs.aether.data.telegram

import androidx.compose.ui.graphics.Color
import com.foresightlabs.aether.domain.model.AuthUiState
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ChatType
import com.foresightlabs.aether.domain.model.ConnectionStatus
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.MessageStatus
import com.foresightlabs.aether.domain.model.MessageType
import com.foresightlabs.aether.domain.model.User
import org.drinkless.tdlib.TdApi
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TelegramMappers {
    private val avatarPalette = listOf(
        listOf(Color(0xFF4DA3FF), Color(0xFF1D4ED8)),
        listOf(Color(0xFF10B981), Color(0xFF047857)),
        listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)),
        listOf(Color(0xFFF59E0B), Color(0xFFB45309)),
        listOf(Color(0xFFF43F5E), Color(0xFFBE123C)),
        listOf(Color(0xFF06B6D4), Color(0xFF0E7490))
    )

    fun mapAuthState(state: TdApi.AuthorizationState): AuthUiState {
        return when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> AuthUiState.Initializing
            is TdApi.AuthorizationStateWaitPhoneNumber -> AuthUiState.Phone()
            is TdApi.AuthorizationStateWaitCode -> AuthUiState.Code(
                phoneNumber = state.codeInfo?.phoneNumber.orEmpty(),
                codeLength = codeLength(state.codeInfo?.type),
                hint = codeHint(state.codeInfo)
            )
            is TdApi.AuthorizationStateWaitPassword -> AuthUiState.Password(
                hint = state.passwordHint?.takeIf { it.isNotBlank() }
            )
            is TdApi.AuthorizationStateWaitRegistration -> AuthUiState.Registration(
                termsOfServiceText = state.termsOfService?.text?.text,
                minAge = state.termsOfService?.minUserAge ?: 0
            )
            is TdApi.AuthorizationStateWaitOtherDeviceConfirmation ->
                AuthUiState.OtherDevice(state.link.orEmpty())
            is TdApi.AuthorizationStateWaitEmailAddress ->
                AuthUiState.Unsupported("Telegram asked for an email address. That step isn't in this Aether build yet.")
            is TdApi.AuthorizationStateWaitEmailCode ->
                AuthUiState.Unsupported("Telegram sent an email code. That step isn't in this Aether build yet.")
            is TdApi.AuthorizationStateWaitPremiumPurchase ->
                AuthUiState.Unsupported("Telegram requires a Premium purchase to continue on this account.")
            is TdApi.AuthorizationStateReady -> AuthUiState.Ready
            is TdApi.AuthorizationStateLoggingOut -> AuthUiState.LoggingOut
            is TdApi.AuthorizationStateClosing -> AuthUiState.Closing
            is TdApi.AuthorizationStateClosed -> AuthUiState.Phone()
            else -> AuthUiState.Unsupported("Telegram reported authorization state ${state.javaClass.simpleName}.")
        }
    }

    fun mapConnection(state: TdApi.ConnectionState?): ConnectionStatus {
        return when (state) {
            is TdApi.ConnectionStateWaitingForNetwork -> ConnectionStatus.WAITING_FOR_NETWORK
            is TdApi.ConnectionStateConnecting -> ConnectionStatus.CONNECTING
            is TdApi.ConnectionStateConnectingToProxy -> ConnectionStatus.CONNECTING_PROXY
            is TdApi.ConnectionStateUpdating -> ConnectionStatus.UPDATING
            is TdApi.ConnectionStateReady -> ConnectionStatus.READY
            else -> ConnectionStatus.UNKNOWN
        }
    }

    fun mapChatType(type: TdApi.ChatType?, myUserId: Long): ChatType {
        return when (type) {
            is TdApi.ChatTypePrivate -> {
                if (type.userId == myUserId) ChatType.SAVED_MESSAGES else ChatType.DIRECT
            }
            is TdApi.ChatTypeBasicGroup -> ChatType.GROUP
            is TdApi.ChatTypeSupergroup -> if (type.isChannel) ChatType.CHANNEL else ChatType.GROUP
            is TdApi.ChatTypeSecret -> ChatType.DIRECT
            else -> ChatType.DIRECT
        }
    }

    fun mapUser(user: TdApi.User, photoPath: String? = null): User {
        val name = listOf(user.firstName, user.lastName)
            .filter { !it.isNullOrBlank() }
            .joinToString(" ")
            .ifBlank { "Deleted account" }
        val username = user.usernames?.activeUsernames?.firstOrNull()?.let { "@$it" }.orEmpty()
        return User(
            id = user.id.toString(),
            name = name,
            username = username,
            avatarInitials = initials(name),
            avatarGradient = gradientFor(user.id),
            isOnline = user.status is TdApi.UserStatusOnline,
            lastSeenText = formatUserStatus(user.status),
            phone = user.phoneNumber.orEmpty(),
            isVerified = user.verificationStatus?.isVerified == true,
            isPremium = user.isPremium,
            photoPath = photoPath ?: localPath(user.profilePhoto?.small)
        )
    }

    fun mapChat(
        chat: TdApi.Chat,
        myUserId: Long,
        users: Map<Long, TdApi.User>,
        photoPath: String? = null,
        typingText: String? = null
    ): Chat {
        val position = ChatOrdering.mainPosition(chat.positions)
        val type = mapChatType(chat.type, myUserId)
        val privateUser = (chat.type as? TdApi.ChatTypePrivate)?.userId?.let { users[it] }
        val mappedUser = privateUser?.let { mapUser(it) }
        val title = when {
            type == ChatType.SAVED_MESSAGES -> "Saved Messages"
            chat.title.isNullOrBlank() && mappedUser != null -> mappedUser.name
            chat.title.isNullOrBlank() -> "Deleted account"
            else -> chat.title
        }
        val last = chat.lastMessage
        val muted = chat.notificationSettings?.let {
            !it.useDefaultMuteFor && it.muteFor > 0
        } == true
        return Chat(
            id = chat.id.toString(),
            title = title,
            type = type,
            lastMessageText = lastMessagePreview(last, users, myUserId),
            lastMessageTime = last?.date?.let { formatTimestamp(it) }.orEmpty(),
            unreadCount = chat.unreadCount,
            unreadMentionCount = chat.unreadMentionCount,
            isMuted = muted,
            isVerified = mappedUser?.isVerified == true ||
                privateUser?.verificationStatus?.isVerified == true,
            isPinned = position?.isPinned == true,
            isTyping = typingText != null,
            typingText = typingText,
            draftText = draftText(chat.draftMessage),
            avatarInitials = initials(title),
            avatarGradient = gradientFor(chat.id),
            memberCount = 0,
            subtitle = mappedUser?.lastSeenText.orEmpty(),
            lastMessageStatus = last?.let { mapMessageStatus(it, chat.lastReadOutboxMessageId) } ?: MessageStatus.SENT,
            isLastMessageOutgoing = last?.isOutgoing == true,
            directUser = mappedUser,
            photoPath = photoPath ?: localPath(chat.photo?.small) ?: mappedUser?.photoPath,
            order = position?.order ?: 0L,
            canSendText = chat.permissions?.canSendBasicMessages != false
        )
    }

    fun mapMessage(
        message: TdApi.Message,
        users: Map<Long, TdApi.User>,
        chats: Map<Long, TdApi.Chat>,
        myUserId: Long,
        lastReadOutboxMessageId: Long,
        reply: Message? = null
    ): Message {
        val senderName = senderName(message.senderId, users, chats, message.isOutgoing)
        val (text, type) = mapContent(message.content)
        return Message(
            id = message.id.toString(),
            chatId = message.chatId.toString(),
            senderId = senderId(message.senderId),
            senderName = senderName,
            text = text,
            timestamp = formatTimestamp(message.date),
            dateSeconds = message.date,
            isOutgoing = message.isOutgoing,
            status = mapMessageStatus(message, lastReadOutboxMessageId),
            isEdited = message.editDate > 0,
            type = type,
            replyToMessage = reply,
            forwardedFrom = forwardOrigin(message.forwardInfo, users, chats),
            isPinned = message.isPinned,
            canRetry = message.sendingState is TdApi.MessageSendingStateFailed
        )
    }

    fun mapMessageStatus(message: TdApi.Message, lastReadOutboxMessageId: Long): MessageStatus {
        return when (message.sendingState) {
            is TdApi.MessageSendingStatePending -> MessageStatus.SENDING
            is TdApi.MessageSendingStateFailed -> MessageStatus.FAILED
            else -> {
                if (message.isOutgoing && message.id <= lastReadOutboxMessageId && message.id != 0L) {
                    MessageStatus.READ
                } else {
                    MessageStatus.SENT
                }
            }
        }
    }

    fun lastMessagePreview(
        message: TdApi.Message?,
        users: Map<Long, TdApi.User>,
        myUserId: Long
    ): String {
        if (message == null) return ""
        val (text, _) = mapContent(message.content)
        val prefix = if (message.isOutgoing) {
            "You: "
        } else {
            val name = senderName(message.senderId, users, emptyMap(), false)
            if (name.isNotBlank() && message.chatId != myUserId) "$name: " else ""
        }
        return prefix + text
    }

    fun mapContent(content: TdApi.MessageContent?): Pair<String, MessageType> {
        return when (content) {
            is TdApi.MessageText -> content.text?.text.orEmpty() to MessageType.TEXT
            is TdApi.MessagePhoto -> (content.caption?.text?.ifBlank { "Photo" } ?: "Photo") to MessageType.UNSUPPORTED
            is TdApi.MessageVideo -> (content.caption?.text?.ifBlank { "Video" } ?: "Video") to MessageType.UNSUPPORTED
            is TdApi.MessageDocument -> (content.document?.fileName ?: "Document") to MessageType.UNSUPPORTED
            is TdApi.MessageAudio -> (content.audio?.title ?: "Audio") to MessageType.UNSUPPORTED
            is TdApi.MessageVoiceNote -> "Voice message" to MessageType.UNSUPPORTED
            is TdApi.MessageVideoNote -> "Video message" to MessageType.UNSUPPORTED
            is TdApi.MessageSticker -> (content.sticker?.emoji?.let { "Sticker $it" } ?: "Sticker") to MessageType.UNSUPPORTED
            is TdApi.MessageAnimation -> "GIF" to MessageType.UNSUPPORTED
            is TdApi.MessagePoll -> (content.poll?.question?.text ?: "Poll") to MessageType.UNSUPPORTED
            is TdApi.MessageCall -> "Call" to MessageType.UNSUPPORTED
            is TdApi.MessageLocation -> "Location" to MessageType.UNSUPPORTED
            is TdApi.MessageContact -> "Contact" to MessageType.UNSUPPORTED
            is TdApi.MessageVenue -> "Venue" to MessageType.UNSUPPORTED
            is TdApi.MessageDice -> (content.emoji ?: "Dice") to MessageType.UNSUPPORTED
            null -> "" to MessageType.UNSUPPORTED
            else -> {
                val label = content.javaClass.simpleName
                    .removePrefix("Message")
                    .replace(Regex("([a-z])([A-Z])"), "$1 $2")
                label.ifBlank { "Unsupported message" } to MessageType.UNSUPPORTED
            }
        }
    }

    fun codeLength(type: TdApi.AuthenticationCodeType?): Int? {
        return when (type) {
            is TdApi.AuthenticationCodeTypeTelegramMessage -> type.length
            is TdApi.AuthenticationCodeTypeSms -> type.length
            is TdApi.AuthenticationCodeTypeCall -> type.length
            is TdApi.AuthenticationCodeTypeFlashCall -> null
            is TdApi.AuthenticationCodeTypeMissedCall -> type.length
            is TdApi.AuthenticationCodeTypeFragment -> type.length
            is TdApi.AuthenticationCodeTypeFirebaseAndroid -> type.length
            is TdApi.AuthenticationCodeTypeFirebaseIos -> type.length
            else -> null
        }.takeIf { it != null && it > 0 }
    }

    fun localPath(file: TdApi.File?): String? {
        val path = file?.local?.path
        return path?.takeIf { file.local?.isDownloadingCompleted == true && it.isNotBlank() }
    }

    fun initials(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.isEmpty()) return "?"
        return parts.take(2).joinToString("") { it.first().uppercase() }
    }

    fun gradientFor(id: Long): List<Color> {
        val index = (kotlin.math.abs(id) % avatarPalette.size).toInt()
        return avatarPalette[index]
    }

    fun formatTimestamp(unixSeconds: Int): String {
        if (unixSeconds <= 0) return ""
        val date = Date(unixSeconds * 1000L)
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { time = date }
        val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
        return if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
        ) {
            time
        } else {
            SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
        }
    }

    private fun codeHint(info: TdApi.AuthenticationCodeInfo?): String {
        val type = info?.type ?: return "Enter the verification code from Telegram."
        return when (type) {
            is TdApi.AuthenticationCodeTypeTelegramMessage ->
                "Telegram sent a code in the Telegram app."
            is TdApi.AuthenticationCodeTypeSms ->
                "Telegram sent an SMS code."
            is TdApi.AuthenticationCodeTypeCall ->
                "Telegram will call with a code."
            is TdApi.AuthenticationCodeTypeMissedCall ->
                "Telegram will miss-call you with a code."
            is TdApi.AuthenticationCodeTypeFragment ->
                "Enter the code from Fragment."
            else -> "Enter the verification code from Telegram."
        }
    }

    private fun draftText(draft: TdApi.DraftMessage?): String? {
        val content = draft?.inputMessageText as? TdApi.InputMessageText ?: return null
        return content.text?.text?.takeIf { it.isNotBlank() }
    }

    private fun senderId(sender: TdApi.MessageSender?): String {
        return when (sender) {
            is TdApi.MessageSenderUser -> sender.userId.toString()
            is TdApi.MessageSenderChat -> sender.chatId.toString()
            else -> ""
        }
    }

    private fun senderName(
        sender: TdApi.MessageSender?,
        users: Map<Long, TdApi.User>,
        chats: Map<Long, TdApi.Chat>,
        outgoing: Boolean
    ): String {
        if (outgoing) return "You"
        return when (sender) {
            is TdApi.MessageSenderUser -> {
                val user = users[sender.userId]
                if (user == null) "User" else listOf(user.firstName, user.lastName)
                    .filter { !it.isNullOrBlank() }
                    .joinToString(" ")
                    .ifBlank { "Deleted account" }
            }
            is TdApi.MessageSenderChat -> chats[sender.chatId]?.title ?: "Chat"
            else -> ""
        }
    }

    private fun forwardOrigin(
        info: TdApi.MessageForwardInfo?,
        users: Map<Long, TdApi.User>,
        chats: Map<Long, TdApi.Chat>
    ): String? {
        val origin = info?.origin ?: return null
        return when (origin) {
            is TdApi.MessageOriginUser -> {
                val user = users[origin.senderUserId]
                user?.let { listOf(it.firstName, it.lastName).filter { p -> p.isNotBlank() }.joinToString(" ") }
                    ?: "Forwarded"
            }
            is TdApi.MessageOriginChat -> chats[origin.senderChatId]?.title ?: "Forwarded"
            is TdApi.MessageOriginChannel -> origin.authorSignature?.ifBlank { "Channel" } ?: "Channel"
            is TdApi.MessageOriginHiddenUser -> origin.senderName
            else -> "Forwarded"
        }
    }

    private fun formatUserStatus(status: TdApi.UserStatus?): String {
        return when (status) {
            is TdApi.UserStatusOnline -> "online"
            is TdApi.UserStatusRecently -> "last seen recently"
            is TdApi.UserStatusLastWeek -> "last seen within a week"
            is TdApi.UserStatusLastMonth -> "last seen within a month"
            is TdApi.UserStatusOffline -> {
                if (status.wasOnline <= 0) "offline"
                else "last seen ${formatTimestamp(status.wasOnline)}"
            }
            else -> ""
        }
    }
}
