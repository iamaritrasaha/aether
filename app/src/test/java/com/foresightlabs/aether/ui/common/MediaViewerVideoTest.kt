package com.foresightlabs.aether.ui.common

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.domain.model.MediaItem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The full-screen viewer must pick a renderer by [MediaItem.isVideo] rather
 * than assuming every item is a photo -- see the root-cause audit in
 * [com.foresightlabs.aether.ui.conversation.MessageBubbleVideoTest]'s sibling
 * mapper tests. These cover the failure modes that must not crash the viewer:
 * an undownloaded video, a video with no thumbnail at all, and that the
 * existing photo path is unaffected by video's presence.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi", application = Application::class)
class MediaViewerVideoTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun show(item: MediaItem, onRequestDownload: (Int, Boolean) -> Unit = { _, _ -> }) {
        composeRule.setContent {
            MediaViewer(
                mediaItem = item,
                senderName = "Ishani Roy",
                isVisible = true,
                onClose = {},
                onRequestDownload = onRequestDownload
            )
        }
        composeRule.waitForIdle()
    }

    @Test
    fun anUndownloadedVideoDoesNotCrashAndRequestsItsOwnFile() {
        val requested = mutableListOf<Int>()
        val item = MediaItem(
            id = "1:11",
            url = "",
            fileId = 11,
            hasLocalFile = true, // thumbnail already there -- content is not
            isVideo = true,
            videoFileId = 22,
            videoLocalPath = ""
        )

        show(item, onRequestDownload = { fileId, _ -> requested.add(fileId) })

        // Requests the video's own content file (22), not the thumbnail (11)
        // it already treated as ready.
        assert(22 in requested) { "expected the video content file (22) to be requested, got $requested" }
    }

    @Test
    fun aVideoWithNoThumbnailAndNoContentFileDoesNotCrash() {
        val item = MediaItem(
            id = "1:0",
            url = "",
            fileId = 0,
            hasLocalFile = false,
            previewBase64 = null,
            isVideo = true,
            videoFileId = 0,
            videoLocalPath = ""
        )

        // No exception is the assertion: a message with no thumbnail file and
        // no resolvable video file id must render the shell placeholder, not throw.
        show(item)
    }

    @Test
    fun playbackFailureLookingLikeAMissingFileOnDiskDoesNotCrash() {
        // Simulates a video whose reported local path does not actually exist
        // on disk (e.g. TDLib's cache was cleared after the message loaded) --
        // the viewer must fall back to its shell rather than handing ExoPlayer
        // a dead path and crashing.
        val item = MediaItem(
            id = "1:11",
            url = "",
            fileId = 11,
            hasLocalFile = true,
            isVideo = true,
            videoFileId = 22,
            videoLocalPath = "/nonexistent/path/does-not-exist.mp4"
        )

        show(item)
    }

    @Test
    fun theExistingPhotoPathIsUnaffectedByVideoSupport() {
        val photo = MediaItem(
            id = "1:33",
            url = "",
            fileId = 33,
            hasLocalFile = false,
            isVideo = false
        )

        show(photo)
    }
}
