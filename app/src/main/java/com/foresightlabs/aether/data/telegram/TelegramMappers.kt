package com.foresightlabs.aether.data.telegram

import androidx.compose.ui.graphics.Color
import com.foresightlabs.aether.domain.model.AuthUiState
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ChatType
import com.foresightlabs.aether.domain.model.ConnectionStatus
import com.foresightlabs.aether.domain.model.MediaItem
import com.foresightlabs.aether.domain.model.Reaction
import com.foresightlabs.aether.domain.model.PollChoice
import com.foresightlabs.aether.domain.model.PollKind
import com.foresightlabs.aether.domain.model.PollPresentation
import com.foresightlabs.aether.domain.text.AetherEntity
import com.foresightlabs.aether.domain.text.AetherText
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.MessageStatus
import com.foresightlabs.aether.domain.model.MessageType
import com.foresightlabs.aether.domain.model.Presence
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
            is TdApi.ChatTypeSecret -> ChatType.SECRET
            else -> ChatType.DIRECT
        }
    }

    /**
     * A conversation row standing for a contact with no chat open yet.
     *
     * Everything shown comes from the user record; nothing about a conversation is
     * invented, because there is not one — opening the row is what creates it.
     */
    fun chatForUser(user: User): Chat = Chat(
        id = user.id,
        title = user.name,
        type = ChatType.DIRECT,
        lastMessageText = "",
        lastMessageTime = "",
        avatarInitials = user.avatarInitials,
        avatarGradient = user.avatarGradient,
        subtitle = user.lastSeenText,
        directUser = user,
        photoPath = user.photoPath,
        blockableUserId = user.id.toLongOrNull()
    )

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
            presence = mapPresence(user.status),
            lastSeenText = formatUserStatus(user.status),
            phone = user.phoneNumber.orEmpty(),
            isVerified = user.verificationStatus?.isVerified == true,
            isPremium = user.isPremium,
            isBot = user.type is TdApi.UserTypeBot,
            isContact = user.isContact,
            isDeleted = user.type is TdApi.UserTypeDeleted,
            photoPath = photoPath ?: localPath(user.profilePhoto?.small)
        )
    }

    /**
     * Maps TDLib user status straight through. Approximate statuses stay approximate —
     * nothing here promotes "recently" to "online".
     */
    fun mapPresence(status: TdApi.UserStatus?): Presence {
        return when (status) {
            is TdApi.UserStatusOnline -> Presence.ONLINE
            is TdApi.UserStatusOffline -> Presence.OFFLINE
            is TdApi.UserStatusRecently -> Presence.RECENTLY
            is TdApi.UserStatusLastWeek -> Presence.WITHIN_WEEK
            is TdApi.UserStatusLastMonth -> Presence.WITHIN_MONTH
            else -> Presence.UNKNOWN
        }
    }

    fun mapChat(
        chat: TdApi.Chat,
        myUserId: Long,
        users: Map<Long, TdApi.User>,
        photoPath: String? = null,
        typingText: String? = null,
        hasUnseenPulse: Boolean = false,
        isForum: Boolean = false
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
            canSendText = chat.permissions?.canSendBasicMessages != false,
            hasUnseenPulse = hasUnseenPulse,
            isArchived = ChatOrdering.isArchived(chat.positions),
            isMarkedAsUnread = chat.isMarkedAsUnread,
            canLeave = type == ChatType.GROUP || type == ChatType.CHANNEL,
            canRevokeHistory = chat.canBeDeletedForAllUsers,
            canDeleteOnlyForSelf = chat.canBeDeletedOnlyForSelf,
            blockableUserId = (chat.type as? TdApi.ChatTypePrivate)?.userId,
            isBlocked = chat.blockList is TdApi.BlockListMain,
            lastMessageId = last?.id ?: 0L,
            isForum = isForum
        )
    }

    fun mapStory(story: TdApi.Story, senderName: String): com.foresightlabs.aether.domain.model.StoryItem {
        val (mediaUrl, isVideo, duration) = when (val content = story.content) {
            is TdApi.StoryContentPhoto -> {
                val path = localPath(content.photo?.sizes?.maxByOrNull { it.photo.size }?.photo)
                StoryMedia(url = path, isVideo = false)
            }
            is TdApi.StoryContentVideo -> {
                val path = localPath(content.video?.video)
                StoryMedia(
                    url = path,
                    isVideo = true,
                    duration = content.video?.duration ?: 0.0
                )
            }
            else -> StoryMedia()
        }
        return com.foresightlabs.aether.domain.model.StoryItem(
            id = story.id,
            senderChatId = story.posterChatId,
            senderName = senderName,
            dateSeconds = story.date,
            expiresInSeconds = 86400,
            isForCloseFriends = story.privacySettings is TdApi.StoryPrivacySettingsCloseFriends,
            caption = story.caption?.text.orEmpty(),
            mediaUrl = mediaUrl,
            isVideo = isVideo,
            videoDuration = duration,
            isSeen = (story.interactionInfo?.viewCount ?: 0) > 0,
            reactionEmoji = (story.chosenReactionType as? TdApi.ReactionTypeEmoji)?.emoji
        )
    }

    private data class StoryMedia(
        val url: String? = null,
        val isVideo: Boolean = false,
        val duration: Double = 0.0
    )

    fun mapMessage(
        message: TdApi.Message,
        users: Map<Long, TdApi.User>,
        chats: Map<Long, TdApi.Chat>,
        myUserId: Long,
        lastReadOutboxMessageId: Long,
        reply: Message? = null,
        resolvePath: (TdApi.File?) -> String? = { localPath(it) }
    ): Message {
        val senderName = senderName(message.senderId, users, chats, message.isOutgoing)
        val presentation = mapPresentation(message.content, message.id, resolvePath)
        val type = presentation.type
        val text = if (type == MessageType.SERVICE) {
            ServiceMessages.describe(message.content, senderName).trim()
        } else {
            presentation.text
        }
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
            mediaItems = presentation.mediaItems,
            fileName = presentation.fileName,
            fileSize = presentation.fileSize,
            fileExtension = presentation.fileExtension,
            voiceDurationSec = presentation.voiceDurationSec,
            voiceWaveform = presentation.voiceWaveform,
            formatted = presentation.formatted,
            poll = presentation.poll,
            mediaAlbumId = message.mediaAlbumId,
            reactions = mapReactions(message.interactionInfo),
            isPinned = message.isPinned,
            canRetry = message.sendingState is TdApi.MessageSendingStateFailed,
            autoDeleteIn = message.autoDeleteIn,
            selfDestructIn = message.selfDestructIn,
            liveLocationExpiresIn = (message.content as? TdApi.MessageLocation)?.expiresIn ?: 0,
            isLiveLocation = (message.content as? TdApi.MessageLocation)?.livePeriod?.let { it > 0 } ?: false,
            venueTitle = (message.content as? TdApi.MessageVenue)?.venue?.title,
            venueAddress = (message.content as? TdApi.MessageVenue)?.venue?.address,
            stickerFormat = when ((message.content as? TdApi.MessageSticker)?.sticker?.format) {
                is TdApi.StickerFormatTgs -> "tgs"
                is TdApi.StickerFormatWebm -> "webm"
                else -> "webp"
            }
        )
    }


    /**
     * How a message's content should be presented, resolved from the real
     * [TdApi.MessageContent] rather than from a text label.
     *
     * [mediaPath] is null until the file is on disk. A media bubble with no path
     * shows its own loading state; it never renders a stand-in for content that has
     * not arrived.
     */
    data class MediaPresentation(
        val text: String,
        val type: MessageType,
        /** Present only for poll messages. */
        val poll: PollPresentation? = null,
        /** The same text with the spans Telegram attached to it. */
        val formatted: AetherText = AetherText(text),
        val mediaItems: List<MediaItem> = emptyList(),
        val fileName: String? = null,
        val fileSize: String? = null,
        val fileExtension: String? = null,
        val voiceDurationSec: Int = 0,
        val voiceWaveform: List<Float> = emptyList()
    )

    /**
     * Resolves a message's presentation, including the local path of its media.
     *
     * [resolvePath] is supplied by the client, which owns the download cache; this
     * keeps the mapper free of I/O while still producing a real path when there is
     * one.
     */
    fun mapPresentation(
        content: TdApi.MessageContent?,
        messageId: Long,
        resolvePath: (TdApi.File?) -> String?
    ): MediaPresentation {
        fun mediaItem(file: TdApi.File?, caption: String, width: Int, height: Int): List<MediaItem> {
            val path = resolvePath(file) ?: return emptyList()
            return listOf(
                MediaItem(
                    id = "$messageId:${file?.id ?: 0}",
                    url = path,
                    caption = caption,
                    width = width.coerceAtLeast(1),
                    height = height.coerceAtLeast(1)
                )
            )
        }

        if (ServiceMessages.isServiceEvent(content)) {
            // Described with the sender in mapMessage, which knows who acted.
            return MediaPresentation(
                text = ServiceMessages.describe(content, ""),
                type = MessageType.SERVICE
            )
        }
        return when (content) {
            is TdApi.MessageText -> {
                val formatted = mapFormattedText(content.text)
                MediaPresentation(
                    text = formatted.text,
                    type = MessageType.TEXT,
                    formatted = formatted
                )
            }
            is TdApi.MessagePhoto -> {
                val captionText = mapFormattedText(content.caption)
                val caption = captionText.text
                // Largest size TDLib offers; anything smaller would be shown scaled up.
                val best = content.photo?.sizes?.maxByOrNull { it.width * it.height }
                MediaPresentation(
                    text = caption,
                    type = MessageType.IMAGE,
                    formatted = captionText,
                    mediaItems = mediaItem(best?.photo, caption, best?.width ?: 0, best?.height ?: 0)
                )
            }
            is TdApi.MessageVideo -> {
                val captionText = mapFormattedText(content.caption)
                val caption = captionText.text
                val video = content.video
                MediaPresentation(
                    text = caption,
                    type = MessageType.IMAGE,
                    formatted = captionText,
                    mediaItems = mediaItem(
                        video?.thumbnail?.file ?: video?.video,
                        caption,
                        video?.width ?: 0,
                        video?.height ?: 0
                    )
                )
            }
            is TdApi.MessageDocument -> {
                val document = content.document
                val name = document?.fileName?.takeIf { it.isNotBlank() } ?: "Document"
                MediaPresentation(
                    text = content.caption?.text.orEmpty(),
                    type = MessageType.FILE,
                    fileName = name,
                    fileSize = formatFileSize(document?.document?.size ?: 0L),
                    fileExtension = name.substringAfterLast('.', "").uppercase().ifBlank { null }
                )
            }
            is TdApi.MessageAudio -> {
                val audio = content.audio
                val name = listOfNotNull(
                    audio?.performer?.takeIf { it.isNotBlank() },
                    audio?.title?.takeIf { it.isNotBlank() }
                ).joinToString(" — ").ifBlank { audio?.fileName ?: "Audio" }
                val captionText = mapFormattedText(content.caption)
                MediaPresentation(
                    text = captionText.text.ifBlank { name },
                    type = MessageType.AUDIO,
                    formatted = captionText,
                    fileName = name,
                    fileSize = formatFileSize(audio?.audio?.size ?: 0L),
                    fileExtension = "AUDIO",
                    voiceDurationSec = audio?.duration ?: 0,
                    mediaItems = mediaItem(
                        audio?.albumCoverThumbnail?.file ?: audio?.audio,
                        name,
                        audio?.albumCoverThumbnail?.width ?: 0,
                        audio?.albumCoverThumbnail?.height ?: 0
                    )
                )
            }
            is TdApi.MessageVoiceNote -> {
                val voice = content.voiceNote
                MediaPresentation(
                    text = content.caption?.text.orEmpty(),
                    type = MessageType.VOICE,
                    voiceDurationSec = voice?.duration ?: 0,
                    voiceWaveform = decodeWaveform(voice?.waveform)
                )
            }
            is TdApi.MessageContact -> {
                val contact = content.contact
                val name = listOfNotNull(
                    contact?.firstName?.takeIf { it.isNotBlank() },
                    contact?.lastName?.takeIf { it.isNotBlank() }
                ).joinToString(" ").ifBlank { "Contact" }
                MediaPresentation(
                    text = name,
                    type = MessageType.CONTACT,
                    fileName = name,
                    fileSize = contact?.phoneNumber?.takeIf { it.isNotBlank() }
                )
            }
            is TdApi.MessageLocation -> {
                val location = content.location
                val isLive = content.livePeriod > 0
                MediaPresentation(
                    text = if (isLive) "Live Location" else "Location",
                    type = MessageType.LOCATION,
                    fileName = location?.let { formatCoordinates(it.latitude, it.longitude) }
                )
            }
            is TdApi.MessageVenue -> {
                val venue = content.venue
                MediaPresentation(
                    text = venue?.title.orEmpty().ifBlank { "Venue" },
                    type = MessageType.VENUE,
                    fileName = venue?.address.orEmpty().ifBlank {
                        venue?.location?.let { formatCoordinates(it.latitude, it.longitude) }
                    }
                )
            }
            is TdApi.MessageVideoNote -> {
                val videoNote = content.videoNote
                MediaPresentation(
                    text = "",
                    type = MessageType.VIDEO_NOTE,
                    voiceDurationSec = videoNote?.duration ?: 0,
                    voiceWaveform = decodeWaveform(videoNote?.waveform),
                    mediaItems = mediaItem(
                        videoNote?.video ?: videoNote?.thumbnail?.file,
                        "",
                        videoNote?.length ?: 240,
                        videoNote?.length ?: 240
                    ),
                    fileName = "Video message"
                )
            }
            is TdApi.MessageAnimatedEmoji -> {
                // A large animated emoji. Aether cannot play the sticker behind it,
                // but the emoji itself is the message and reads correctly on its own.
                val emoji = content.emoji.orEmpty()
                MediaPresentation(
                    text = emoji,
                    type = MessageType.TEXT,
                    formatted = AetherText(emoji)
                )
            }
            is TdApi.MessageChecklist -> {
                val list = content.list
                val title = list?.title?.text.orEmpty().ifBlank { "Checklist" }
                val done = list?.tasks?.count { it?.completedBy != null } ?: 0
                val total = list?.tasks?.size ?: 0
                MediaPresentation(
                    text = title,
                    type = MessageType.FILE,
                    fileName = title,
                    fileSize = "$done of $total done"
                )
            }
            is TdApi.MessageStory -> MediaPresentation(
                text = if (content.viaMention) "Mentioned you in a story" else "Shared a story",
                type = MessageType.UNSUPPORTED
            )
            is TdApi.MessagePaidMedia -> MediaPresentation(
                // Aether does not purchase or unlock paid media; saying what it is
                // beats presenting an empty bubble.
                text = content.caption?.text.orEmpty().ifBlank { "Paid media" },
                type = MessageType.UNSUPPORTED
            )
            is TdApi.MessageGiveaway -> MediaPresentation(
                text = "Giveaway · ${content.winnerCount} winners",
                type = MessageType.UNSUPPORTED
            )
            is TdApi.MessagePoll -> {
                val poll = mapPoll(content.poll)
                MediaPresentation(
                    text = poll?.question.orEmpty(),
                    type = if (poll == null) MessageType.UNSUPPORTED else MessageType.POLL,
                    poll = poll
                )
            }
            is TdApi.MessageSticker -> {
                val sticker = content.sticker
                val format = when (sticker?.format) {
                    is TdApi.StickerFormatTgs -> "TGS"
                    is TdApi.StickerFormatWebm -> "WEBM"
                    else -> "WEBP"
                }
                MediaPresentation(
                    text = sticker?.emoji.orEmpty(),
                    type = MessageType.STICKER,
                    mediaItems = mediaItem(
                        sticker?.sticker ?: sticker?.thumbnail?.file,
                        sticker?.emoji.orEmpty(),
                        sticker?.width ?: 0,
                        sticker?.height ?: 0
                    ),
                    fileExtension = format
                )
            }
            is TdApi.MessageAnimation -> {
                val animation = content.animation
                val captionText = mapFormattedText(content.caption)
                MediaPresentation(
                    text = captionText.text,
                    type = MessageType.ANIMATION,
                    formatted = captionText,
                    mediaItems = mediaItem(
                        animation?.animation ?: animation?.thumbnail?.file,
                        captionText.text,
                        animation?.width ?: 0,
                        animation?.height ?: 0
                    ),
                    fileName = animation?.fileName?.takeIf { it.isNotBlank() } ?: "GIF",
                    voiceDurationSec = animation?.duration ?: 0
                )
            }
            else -> {
                val (text, type) = mapContent(content)
                MediaPresentation(text = text, type = type, formatted = AetherText(text))
            }
        }
    }

    /**
     * Maps the reactions Telegram holds against a message.
     *
     * Counts and the "you reacted" flag are the server's. Aether previously wrote
     * reactions without ever reading them back, so a reaction it sent was invisible
     * until the conversation was reloaded — and reactions from anyone else never
     * appeared at all.
     *
     * Custom-emoji reactions are kept with an empty emoji rather than dropped: the
     * count is real and belongs in the total, even though Aether cannot yet draw the
     * glyph.
     */
    fun mapReactions(info: TdApi.MessageInteractionInfo?): List<Reaction> {
        val reactions = info?.reactions?.reactions ?: return emptyList()
        return reactions.mapNotNull { reaction ->
            if (reaction == null) return@mapNotNull null
            val emoji = when (val type = reaction.type) {
                is TdApi.ReactionTypeEmoji -> type.emoji.orEmpty()
                // A premium custom emoji Aether cannot render yet.
                is TdApi.ReactionTypeCustomEmoji -> ""
                else -> ""
            }
            Reaction(
                emoji = emoji,
                count = reaction.totalCount,
                userReacted = reaction.isChosen
            )
        }
    }

    /**
     * Maps a TDLib poll, including its per-option state.
     *
     * The correct answers of a quiz are only known once Telegram sends them — that
     * is, after the account has answered — so [PollChoice.isCorrect] is false until
     * then rather than being guessed.
     */
    fun mapPoll(poll: TdApi.Poll?): PollPresentation? {
        if (poll == null) return null
        val quiz = poll.type as? TdApi.PollTypeQuiz
        val correct = quiz?.correctOptionIds?.toSet() ?: emptySet()
        val options = poll.options ?: emptyArray()
        return PollPresentation(
            id = poll.id,
            question = poll.question?.text.orEmpty(),
            choices = options.mapIndexed { index, option ->
                PollChoice(
                    index = index,
                    text = option?.text?.text.orEmpty(),
                    voterCount = option?.voterCount ?: 0,
                    votePercentage = option?.votePercentage ?: 0,
                    isChosen = option?.isChosen == true,
                    isBeingChosen = option?.isBeingChosen == true,
                    isCorrect = index in correct
                )
            },
            totalVoterCount = poll.totalVoterCount,
            kind = if (quiz != null) PollKind.QUIZ else PollKind.REGULAR,
            isAnonymous = poll.isAnonymous,
            allowsMultipleAnswers = poll.allowsMultipleAnswers,
            allowsRevoting = poll.allowsRevoting,
            isClosed = poll.isClosed,
            explanation = quiz?.explanation?.text?.takeIf { it.isNotBlank() }
        )
    }

    /**
     * Unpacks TDLib's voice-note waveform.
     *
     * Telegram stores it as 5-bit samples packed end to end, most significant bit
     * first. Anything else drawn in a voice bubble would be decoration, not audio,
     * so an absent waveform yields an empty list rather than invented amplitudes.
     */
    fun decodeWaveform(packed: ByteArray?): List<Float> {
        if (packed == null || packed.isEmpty()) return emptyList()
        val bitCount = packed.size * 8
        val sampleCount = bitCount / 5
        val samples = ArrayList<Float>(sampleCount)
        for (index in 0 until sampleCount) {
            val bitOffset = index * 5
            var value = 0
            for (bit in 0 until 5) {
                val absolute = bitOffset + bit
                val byte = packed[absolute / 8].toInt() and 0xFF
                val bitValue = (byte shr (7 - (absolute % 8))) and 1
                value = (value shl 1) or bitValue
            }
            samples += value / 31f
        }
        return samples
    }

    /** Coordinates at roughly street precision, which is all a static point needs. */
    fun formatCoordinates(latitude: Double, longitude: Double): String =
        String.format(java.util.Locale.US, "%.5f, %.5f", latitude, longitude)

    private fun formatFileSize(bytes: Long): String? {
        if (bytes <= 0L) return null
        val units = listOf("B", "KB", "MB", "GB")
        var size = bytes.toDouble()
        var unit = 0
        while (size >= 1024 && unit < units.lastIndex) {
            size /= 1024
            unit++
        }
        return if (unit == 0) "$bytes B" else String.format(java.util.Locale.US, "%.1f %s", size, units[unit])
    }


    /**
     * Maps a TDLib formatted text into Aether's own entity model.
     *
     * Entity types Aether has no representation for are dropped rather than
     * approximated: an unmapped span renders as ordinary text, which is correct,
     * whereas guessing a style would misrepresent what the sender wrote.
     */
    fun mapFormattedText(formatted: TdApi.FormattedText?): AetherText {
        val text = formatted?.text.orEmpty()
        val raw = formatted?.entities ?: return AetherText(text)
        val entities = raw.mapNotNull { entity -> mapEntity(entity) }
        return AetherText(text = text, entities = entities)
    }

    private fun mapEntity(entity: TdApi.TextEntity?): AetherEntity? {
        if (entity == null) return null
        val offset = entity.offset
        val length = entity.length
        if (length <= 0) return null
        return when (val type = entity.type) {
            is TdApi.TextEntityTypeBold -> AetherEntity.Bold(offset, length)
            is TdApi.TextEntityTypeItalic -> AetherEntity.Italic(offset, length)
            is TdApi.TextEntityTypeUnderline -> AetherEntity.Underline(offset, length)
            is TdApi.TextEntityTypeStrikethrough -> AetherEntity.Strikethrough(offset, length)
            is TdApi.TextEntityTypeSpoiler -> AetherEntity.Spoiler(offset, length)
            is TdApi.TextEntityTypeCode -> AetherEntity.Code(offset, length)
            is TdApi.TextEntityTypePre -> AetherEntity.Pre(offset, length)
            is TdApi.TextEntityTypePreCode -> AetherEntity.Pre(offset, length, type.language)
            is TdApi.TextEntityTypeBlockQuote -> AetherEntity.BlockQuote(offset, length)
            is TdApi.TextEntityTypeExpandableBlockQuote ->
                AetherEntity.BlockQuote(offset, length, isExpandable = true)
            is TdApi.TextEntityTypeUrl -> AetherEntity.Url(offset, length)
            is TdApi.TextEntityTypeTextUrl -> AetherEntity.TextUrl(offset, length, type.url.orEmpty())
            is TdApi.TextEntityTypeMention -> AetherEntity.Mention(offset, length)
            is TdApi.TextEntityTypeMentionName -> AetherEntity.MentionName(offset, length, type.userId)
            is TdApi.TextEntityTypeHashtag -> AetherEntity.Hashtag(offset, length)
            is TdApi.TextEntityTypeCashtag -> AetherEntity.Cashtag(offset, length)
            is TdApi.TextEntityTypeEmailAddress -> AetherEntity.Email(offset, length)
            is TdApi.TextEntityTypePhoneNumber -> AetherEntity.Phone(offset, length)
            is TdApi.TextEntityTypeBankCardNumber -> AetherEntity.BankCard(offset, length)
            is TdApi.TextEntityTypeBotCommand -> AetherEntity.BotCommand(offset, length)
            is TdApi.TextEntityTypeCustomEmoji ->
                AetherEntity.CustomEmoji(offset, length, type.customEmojiId)
            is TdApi.TextEntityTypeMediaTimestamp ->
                AetherEntity.MediaTimestamp(offset, length, type.mediaTimestamp)
            // DateTime carries no action Aether performs, so it renders as plain text.
            else -> null
        }
    }

    /** Converts Aether's entities back into TDLib's, for sending and editing. */
    fun toTdEntities(text: AetherText): Array<TdApi.TextEntity> =
        text.entities.mapNotNull { entity ->
            val type: TdApi.TextEntityType = when (entity) {
                is AetherEntity.Bold -> TdApi.TextEntityTypeBold()
                is AetherEntity.Italic -> TdApi.TextEntityTypeItalic()
                is AetherEntity.Underline -> TdApi.TextEntityTypeUnderline()
                is AetherEntity.Strikethrough -> TdApi.TextEntityTypeStrikethrough()
                is AetherEntity.Spoiler -> TdApi.TextEntityTypeSpoiler()
                is AetherEntity.Code -> TdApi.TextEntityTypeCode()
                is AetherEntity.Pre -> entity.language
                    ?.let { TdApi.TextEntityTypePreCode(it) }
                    ?: TdApi.TextEntityTypePre()
                is AetherEntity.BlockQuote -> if (entity.isExpandable) {
                    TdApi.TextEntityTypeExpandableBlockQuote()
                } else {
                    TdApi.TextEntityTypeBlockQuote()
                }
                is AetherEntity.TextUrl -> TdApi.TextEntityTypeTextUrl(entity.url)
                is AetherEntity.CustomEmoji -> TdApi.TextEntityTypeCustomEmoji(entity.customEmojiId)
                is AetherEntity.MentionName -> TdApi.TextEntityTypeMentionName(entity.userId)
                // The rest are recognised by the server from the text itself; sending
                // them back would be asserting a classification Aether did not make.
                else -> return@mapNotNull null
            }
            TdApi.TextEntity(entity.offset, entity.length, type)
        }.toTypedArray()

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

    fun mapContent(content: TdApi.MessageContent?, isOutgoing: Boolean = false): Pair<String, MessageType> {
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
            is TdApi.MessageCall -> TelegramCallMessageMapper.formatCallMessagePresentation(content, isOutgoing) to MessageType.CALL
            is TdApi.MessageLocation -> "Location" to MessageType.UNSUPPORTED
            is TdApi.MessageContact -> "Contact" to MessageType.UNSUPPORTED
            is TdApi.MessageVenue -> "Venue" to MessageType.UNSUPPORTED
            is TdApi.MessageDice -> (content.emoji ?: "Dice") to MessageType.UNSUPPORTED
            null -> "" to MessageType.UNSUPPORTED
            else -> if (ServiceMessages.isServiceEvent(content)) {
                ServiceMessages.describe(content, "").trim() to MessageType.SERVICE
            } else {
                // Genuinely unknown, non-service content. Named from its own type so
                // the preview says what it is instead of pretending it is text.
                ServiceMessages.fallbackDescription(content) to MessageType.UNSUPPORTED
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

    fun draftText(draft: TdApi.DraftMessage?): String? {
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
