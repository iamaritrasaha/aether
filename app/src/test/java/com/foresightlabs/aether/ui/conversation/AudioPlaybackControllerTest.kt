package com.foresightlabs.aether.ui.conversation

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The voice-note path that replaced "a play button that animated a timer with
 * no audio behind it": tapping a note with no local bytes must route to the
 * download request and remember it is pending, and the completed download
 * must start playback exactly once. ExoPlayer start itself needs real media
 * codecs -- here only the download-pending state machine is exercised.
 */
@RunWith(AndroidJUnit4::class)
class AudioPlaybackControllerTest {

    @Test
    fun tappingANoteWithoutLocalBytesRequestsItsDownloadAndMarksItPending() {
        val controller = AudioPlaybackController(ApplicationProvider.getApplicationContext())
        try {
            var requested = false
            controller.toggle("voice:m1:42", null) { requested = true }

            assertTrue("the download must be requested", requested)
            assertEquals("voice:m1:42", controller.pendingDownloadKey.value)
            assertNull("no playback can exist without bytes", controller.playback.value)
        } finally {
            controller.release()
        }
    }

    @Test
    fun aPathArrivalForAnotherNoteDoesNotStartPlayback() {
        val controller = AudioPlaybackController(ApplicationProvider.getApplicationContext())
        try {
            controller.toggle("voice:m1:42", null) { }
            controller.onPathArrived("voice:m999:1", "/tmp/other.ogg")

            assertEquals(
                "only the requested note's arrival may clear the pending key",
                "voice:m1:42", controller.pendingDownloadKey.value
            )
        } finally {
            controller.release()
        }
    }

    @Test
    fun releaseForgetsAnyPendingDownload() {
        val controller = AudioPlaybackController(ApplicationProvider.getApplicationContext())
        controller.toggle("voice:m1:42", null) { }
        controller.release()
        assertNull(controller.pendingDownloadKey.value)
    }

    @Test
    fun tappingANoteWithNoBytesAndNoDownloadRequestIsInert() {
        val controller = AudioPlaybackController(ApplicationProvider.getApplicationContext())
        try {
            controller.toggle("voice:m1:42", null, requestDownload = null)
            assertNull("no download requested, nothing pending", controller.pendingDownloadKey.value)
            assertNull(controller.playback.value)
        } finally {
            controller.release()
        }
    }

}
