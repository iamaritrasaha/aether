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


    private fun createTempDirectory(prefix: String): java.io.File {
        val dir = java.io.File(java.nio.file.Files.createTempDirectory(prefix).toString())
        return dir
    }

    // --- a minimal but VALID wav file, so ExoPlayer actually prepares -------

    private fun writeWav(file: java.io.File, seconds: Float = 0.2f) {
        val sampleRate = 8000
        val totalSamples = (sampleRate * seconds).toInt()
        val dataSize = totalSamples * 2
        java.io.DataOutputStream(java.io.BufferedOutputStream(file.outputStream())).use { out ->
            out.writeBytes("RIFF"); out.writeIntLe(36 + dataSize); out.writeBytes("WAVE")
            out.writeBytes("fmt "); out.writeIntLe(16); out.writeShortLe(1); out.writeShortLe(1)
            out.writeIntLe(sampleRate); out.writeIntLe(sampleRate * 2); out.writeShortLe(2); out.writeShortLe(16)
            out.writeBytes("data"); out.writeIntLe(dataSize)
            for (i in 0 until totalSamples) {
                out.writeShortLe((Math.sin(2.0 * Math.PI * 440.0 * i / sampleRate) * 8000).toInt())
            }
        }
    }

    private fun java.io.DataOutputStream.writeIntLe(v: Int) {
        write(v); write(v ushr 8); write(v ushr 16); write(v ushr 24)
    }
    private fun java.io.DataOutputStream.writeShortLe(v: Int) {
        write(v); write(v ushr 8)
    }

    @Test
    fun selectingAnotherNoteReplacesTheFirstAndSameKeyDoesNotRestart() {
        val dir = createTempDirectory("audio")
        val a = java.io.File(dir, "a.wav").also { writeWav(it) }
        val b = java.io.File(dir, "b.wav").also { writeWav(it) }
        val controller = AudioPlaybackController(ApplicationProvider.getApplicationContext())
        val looper = android.os.Looper.getMainLooper()
        val idle = { org.robolectric.Shadows.shadowOf(looper).idle() }
        try {
            controller.toggle("voice:m1:1", a.absolutePath, null)
            idle()
            val first = controller.playback.value
            assertTrue("first note must become the active playback (the publish path must not deadlock at null)", first != null && first.key == "voice:m1:1")

            controller.toggle("voice:m2:2", b.absolutePath, null)
            idle()
            val second = controller.playback.value
            assertTrue("selecting another note replaces the previous one", second != null && second.key == "voice:m2:2")

            // Pause and resume on the SAME key must keep the same playback
            // (no player rebuild -- that would restart the note from zero).
            // Actual audibility is device-verified; Robolectric's ExoPlayer
            // does not run a real audio sink, so isPlaying itself is not
            // asserted here.
            controller.toggle("voice:m2:2", b.absolutePath, null) // pause
            idle()
            assertEquals("voice:m2:2", controller.playback.value?.key)
            controller.toggle("voice:m2:2", b.absolutePath, null) // resume
            idle()
            assertEquals("voice:m2:2", controller.playback.value?.key)
            assertTrue(controller.isActive("voice:m2:2"))
            assertTrue(!controller.isActive("voice:m1:1"))

            controller.release()
            assertNull("release clears the active playback", controller.playback.value)
            assertTrue(!controller.isActive("voice:m2:2"))
        } finally {
            a.delete(); b.delete(); dir.delete()
        }
    }

    @Test
    fun aNewPendingRequestReplacesAnOldOneAndFailureDisarmsOnlyItself() {
        val controller = AudioPlaybackController(ApplicationProvider.getApplicationContext())
        try {
            var requestedA = false
            var requestedB = false
            controller.toggle("voice:m1:1", null) { requestedA = true }
            controller.toggle("voice:m2:2", null) { requestedB = true }
            assertTrue(requestedA && requestedB)
            assertEquals("only the latest pending note may autoplay", "voice:m2:2", controller.pendingDownloadKey.value)

            controller.onDownloadFailed("voice:m1:1")
            assertEquals("failure of another note must not disarm the pending one", "voice:m2:2", controller.pendingDownloadKey.value)
            controller.onDownloadFailed("voice:m2:2")
            assertNull(controller.pendingDownloadKey.value)
        } finally {
            controller.release()
        }
    }
}
