package com.foresightlabs.aether

import com.foresightlabs.aether.data.notifications.NotificationContentMapper
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationContentMapperTest {

    @Test
    fun testTextMessageMapping() {
        val textContent = TdApi.MessageText().apply {
            text = TdApi.FormattedText().apply { text = "Hello from Telegram!" }
        }
        val result = NotificationContentMapper.mapMessageContent(textContent, showPreview = true)
        assertEquals("Hello from Telegram!", result)
    }

    @Test
    fun testPrivacyHiddenPreview() {
        val textContent = TdApi.MessageText().apply {
            text = TdApi.FormattedText().apply { text = "Super secret password" }
        }
        val result = NotificationContentMapper.mapMessageContent(textContent, showPreview = false)
        assertEquals("New message", result)
    }

    @Test
    fun testPhotoWithAndWithoutCaption() {
        val photoWithCaption = TdApi.MessagePhoto().apply {
            caption = TdApi.FormattedText().apply { text = "Look at this view" }
        }
        assertEquals("Photo, Look at this view", NotificationContentMapper.mapMessageContent(photoWithCaption, true))

        val photoNoCaption = TdApi.MessagePhoto().apply {
            caption = TdApi.FormattedText().apply { text = "" }
        }
        assertEquals("Photo", NotificationContentMapper.mapMessageContent(photoNoCaption, true))
    }

    @Test
    fun testVideoWithAndWithoutCaption() {
        val videoWithCaption = TdApi.MessageVideo().apply {
            caption = TdApi.FormattedText().apply { text = "Check this clip" }
        }
        assertEquals("Video, Check this clip", NotificationContentMapper.mapMessageContent(videoWithCaption, true))

        val videoNoCaption = TdApi.MessageVideo().apply {
            caption = TdApi.FormattedText().apply { text = "" }
        }
        assertEquals("Video", NotificationContentMapper.mapMessageContent(videoNoCaption, true))
    }

    @Test
    fun testVoiceNoteDurationFormatting() {
        val vn1 = TdApi.MessageVoiceNote().apply {
            this.voiceNote = TdApi.VoiceNote().apply { duration = 45 }
        }
        assertEquals("Voice message (0:45)", NotificationContentMapper.mapMessageContent(vn1, true))

        val vn2 = TdApi.MessageVoiceNote().apply {
            this.voiceNote = TdApi.VoiceNote().apply { duration = 125 }
        }
        assertEquals("Voice message (2:05)", NotificationContentMapper.mapMessageContent(vn2, true))
    }

    @Test
    fun testVideoNote() {
        val videoNote = TdApi.MessageVideoNote()
        assertEquals("Video message", NotificationContentMapper.mapMessageContent(videoNote, true))
    }

    @Test
    fun testAudioWithTitleOrCaption() {
        val audioWithTitle = TdApi.MessageAudio().apply {
            audio = TdApi.Audio().apply { title = "Song Name" }
            caption = TdApi.FormattedText().apply { text = "" }
        }
        assertEquals("Audio: Song Name", NotificationContentMapper.mapMessageContent(audioWithTitle, true))

        val audioNoTitleWithCaption = TdApi.MessageAudio().apply {
            audio = TdApi.Audio().apply { title = "" }
            caption = TdApi.FormattedText().apply { text = "Listen to this lecture" }
        }
        assertEquals("Audio: Listen to this lecture", NotificationContentMapper.mapMessageContent(audioNoTitleWithCaption, true))

        val audioPlain = TdApi.MessageAudio().apply {
            audio = TdApi.Audio().apply { title = "" }
            caption = TdApi.FormattedText().apply { text = "" }
        }
        assertEquals("Audio file", NotificationContentMapper.mapMessageContent(audioPlain, true))
    }

    @Test
    fun testDocumentFileName() {
        val doc = TdApi.MessageDocument().apply {
            document = TdApi.Document().apply { fileName = "Quarterly_Report.pdf" }
            caption = TdApi.FormattedText().apply { text = "" }
        }
        assertEquals("Document: Quarterly_Report.pdf", NotificationContentMapper.mapMessageContent(doc, true))
    }

    @Test
    fun testStickerWithEmoji() {
        val sticker = TdApi.MessageSticker().apply {
            sticker = TdApi.Sticker().apply { emoji = "🎉" }
        }
        assertEquals("Sticker 🎉", NotificationContentMapper.mapMessageContent(sticker, true))
    }

    @Test
    fun testAnimationGif() {
        val animation = TdApi.MessageAnimation().apply {
            caption = TdApi.FormattedText().apply { text = "Haha hilarious" }
        }
        assertEquals("GIF, Haha hilarious", NotificationContentMapper.mapMessageContent(animation, true))
    }

    @Test
    fun testPollQuestion() {
        val poll = TdApi.MessagePoll().apply {
            poll = TdApi.Poll().apply {
                question = TdApi.FormattedText().apply { text = "Where should we have dinner?" }
            }
        }
        assertEquals("Poll: Where should we have dinner?", NotificationContentMapper.mapMessageContent(poll, true))
    }

    @Test
    fun testLocationAndVenue() {
        val location = TdApi.MessageLocation().apply {
            location = TdApi.Location().apply {
                latitude = 37.7749
                longitude = -122.4194
            }
        }
        assertEquals("Location", NotificationContentMapper.mapMessageContent(location, true))

        val venue = TdApi.MessageVenue().apply {
            venue = TdApi.Venue().apply { title = "Blue Bottle Coffee" }
        }
        assertEquals("Venue: Blue Bottle Coffee", NotificationContentMapper.mapMessageContent(venue, true))
    }

    @Test
    fun testContact() {
        val contact = TdApi.MessageContact().apply {
            contact = TdApi.Contact().apply {
                firstName = "Alice"
                lastName = "Smith"
            }
        }
        assertEquals("Contact: Alice Smith", NotificationContentMapper.mapMessageContent(contact, true))
    }

    @Test
    fun testPushContentMapping() {
        val textPush = TdApi.PushMessageContentText().apply { text = "Push preview text" }
        assertEquals("Push preview text", NotificationContentMapper.mapPushContent(textPush))

        val hiddenPush = TdApi.PushMessageContentHidden()
        assertEquals("New message", NotificationContentMapper.mapPushContent(hiddenPush))

        val stickerPush = TdApi.PushMessageContentSticker().apply { emoji = "🔥" }
        assertEquals("Sticker 🔥", NotificationContentMapper.mapPushContent(stickerPush))
    }
}
