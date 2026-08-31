package com.foresightlabs.aether.ui.conversation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.domain.model.MediaItem
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.MessageStatus
import com.foresightlabs.aether.domain.model.MessageType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A video message must render through the video renderer, never the photo one --
 * see [com.foresightlabs.aether.data.telegram.TelegramMappingTest] for the mapper
 * half of this guarantee. These tests cover the bubble's own dispatch on
 * [MessageType], the part that decides which composable a mapped message
 * actually reaches.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi", application = Application::class)
class MessageBubbleVideoTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun mediaItem(isVideo: Boolean) = MediaItem(
        id = "1:11",
        url = "",
        width = 640,
        height = 360,
        fileId = 11,
        hasLocalFile = false,
        isVideo = isVideo,
        videoFileId = if (isVideo) 22 else 0
    )

    private fun message(type: MessageType, mediaItems: List<MediaItem>) = Message(
        id = "1",
        chatId = "103",
        senderId = "103",
        senderName = "Ishani Roy",
        text = "",
        timestamp = "10:41 AM",
        isOutgoing = false,
        status = MessageStatus.SENT,
        type = type,
        mediaItems = mediaItems,
        voiceDurationSec = if (type == MessageType.VIDEO) 47 else 0
    )

    private fun show(message: Message) {
        composeRule.setContent {
            MessageBubble(
                message = message,
                onSwipeToReply = {},
                onLongPress = {},
                onMediaClick = {},
                onReactionClick = { _, _ -> }
            )
        }
        composeRule.waitForIdle()
    }

    @Test
    fun videoMessageRoutesToTheVideoRendererNotThePhotoOne() {
        show(message(MessageType.VIDEO, listOf(mediaItem(isVideo = true))))

        composeRule.onAllNodesWithTag("video_attachment_content").assertCountEquals(1)
        composeRule.onAllNodesWithTag("image_attachment_content").assertCountEquals(0)
    }

    @Test
    fun photoMessageStillRoutesToTheImageRenderer() {
        // Regression guard: adding VIDEO must not disturb the existing photo path.
        show(message(MessageType.IMAGE, listOf(mediaItem(isVideo = false))))

        composeRule.onAllNodesWithTag("image_attachment_content").assertCountEquals(1)
        composeRule.onAllNodesWithTag("video_attachment_content").assertCountEquals(0)
    }

    @Test
    fun aVideoMessageWithNoMediaItemsYetDoesNotCrash() {
        // The moment a video message exists before TDLib has reported any file at
        // all (mediaItems still empty) must render as an empty content slot, not throw.
        show(message(MessageType.VIDEO, emptyList()))

        composeRule.onAllNodesWithTag("video_attachment_content").assertCountEquals(0)
    }

    @Test
    fun aVideoWithNoLocalThumbnailAndNoDurationStillRendersWithoutCrashing() {
        val item = MediaItem(
            id = "1:0",
            url = "",
            width = 0,
            height = 0,
            fileId = 0,
            hasLocalFile = false,
            previewBase64 = null,
            isVideo = true,
            videoFileId = 22,
            videoLocalPath = ""
        )
        show(message(MessageType.VIDEO, listOf(item)).copy(voiceDurationSec = 0))

        composeRule.onAllNodesWithTag("video_attachment_content").assertCountEquals(1)
    }
}
