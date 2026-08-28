package com.foresightlabs.aether.data.notifications

import org.drinkless.tdlib.TdApi

object NotificationContentMapper {

    fun mapMessageContent(content: TdApi.MessageContent, showPreview: Boolean): String {
        if (!showPreview) {
            return "New message"
        }

        return when (content) {
            is TdApi.MessageText -> content.text?.text.orEmpty().ifBlank { "Message" }
            is TdApi.MessagePhoto -> {
                val caption = content.caption?.text.orEmpty().trim()
                if (caption.isNotBlank()) "Photo, $caption" else "Photo"
            }
            is TdApi.MessageVideo -> {
                val caption = content.caption?.text.orEmpty().trim()
                if (caption.isNotBlank()) "Video, $caption" else "Video"
            }
            is TdApi.MessageVoiceNote -> {
                val duration = content.voiceNote?.duration ?: 0
                if (duration > 0) "Voice message (${formatDuration(duration)})" else "Voice message"
            }
            is TdApi.MessageVideoNote -> "Video message"
            is TdApi.MessageAudio -> {
                val title = content.audio?.title.orEmpty().trim()
                val caption = content.caption?.text.orEmpty().trim()
                when {
                    title.isNotBlank() -> "Audio: $title"
                    caption.isNotBlank() -> "Audio: $caption"
                    else -> "Audio file"
                }
            }
            is TdApi.MessageDocument -> {
                val fileName = content.document?.fileName.orEmpty().trim()
                if (fileName.isNotBlank()) "Document: $fileName" else "Document"
            }
            is TdApi.MessageSticker -> {
                val emoji = content.sticker?.emoji.orEmpty().trim()
                if (emoji.isNotBlank()) "Sticker $emoji" else "Sticker"
            }
            is TdApi.MessageAnimation -> {
                val caption = content.caption?.text.orEmpty().trim()
                if (caption.isNotBlank()) "GIF, $caption" else "GIF"
            }
            is TdApi.MessagePoll -> {
                val question = content.poll?.question?.text.orEmpty().trim()
                if (question.isNotBlank()) "Poll: $question" else "Poll"
            }
            is TdApi.MessageLocation -> "Location"
            is TdApi.MessageVenue -> {
                val title = content.venue?.title.orEmpty().trim()
                if (title.isNotBlank()) "Venue: $title" else "Venue"
            }
            is TdApi.MessageContact -> {
                val name = listOfNotNull(
                    content.contact?.firstName?.trim()?.takeIf { it.isNotEmpty() },
                    content.contact?.lastName?.trim()?.takeIf { it.isNotEmpty() }
                ).joinToString(" ").trim()
                if (name.isNotBlank()) "Contact: $name" else "Contact"
            }
            is TdApi.MessageCall -> "Call"
            is TdApi.MessagePinMessage -> "Pinned a message"
            is TdApi.MessageChatAddMembers -> "Joined the group"
            is TdApi.MessageChatDeleteMember -> "Left the group"
            is TdApi.MessageChatChangeTitle -> "Changed group name to \"${content.title}\""
            is TdApi.MessageBasicGroupChatCreate -> "Group created"
            is TdApi.MessageSupergroupChatCreate -> "Group created"
            else -> "Message"
        }
    }

    fun mapPushContent(content: TdApi.PushMessageContent): String {
        return when (content) {
            is TdApi.PushMessageContentHidden -> "New message"
            is TdApi.PushMessageContentText -> content.text.orEmpty().ifBlank { "Message" }
            is TdApi.PushMessageContentPhoto -> {
                val caption = content.caption.orEmpty().trim()
                if (caption.isNotBlank()) "Photo, $caption" else "Photo"
            }
            is TdApi.PushMessageContentVideo -> {
                val caption = content.caption.orEmpty().trim()
                if (caption.isNotBlank()) "Video, $caption" else "Video"
            }
            is TdApi.PushMessageContentVoiceNote -> "Voice message"
            is TdApi.PushMessageContentVideoNote -> "Video message"
            is TdApi.PushMessageContentAudio -> "Audio file"
            is TdApi.PushMessageContentDocument -> "Document"
            is TdApi.PushMessageContentSticker -> {
                val emoji = content.emoji.orEmpty().trim()
                if (emoji.isNotBlank()) "Sticker $emoji" else "Sticker"
            }
            is TdApi.PushMessageContentAnimation -> "GIF"
            is TdApi.PushMessageContentPoll -> {
                val question = content.question.orEmpty().trim()
                if (question.isNotBlank()) "Poll: $question" else "Poll"
            }
            is TdApi.PushMessageContentLocation -> "Location"
            is TdApi.PushMessageContentContact -> "Contact"
            is TdApi.PushMessageContentChatAddMembers -> "Joined the group"
            is TdApi.PushMessageContentChatDeleteMember -> "Left the group"
            is TdApi.PushMessageContentChatChangeTitle -> "Changed chat title"
            is TdApi.PushMessageContentMediaAlbum -> "Photo/Video album"
            is TdApi.PushMessageContentMessageForwards -> "Forwarded message"
            else -> "Message"
        }
    }

    private fun formatDuration(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return "%d:%02d".format(mins, secs)
    }
}
