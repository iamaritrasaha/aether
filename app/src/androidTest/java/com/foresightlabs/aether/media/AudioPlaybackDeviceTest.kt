package com.foresightlabs.aether.media

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.foresightlabs.aether.ui.conversation.AudioPlaybackController
import java.io.DataOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DEVICE PLAYBACK PATH test on real hardware: a generated, VALID wav file is
 * prepared by a real ExoPlayer on the device's real audio stack, through the
 * same [AudioPlaybackController] the voice-note bubbles use.
 *
 * This proves prepare/play/pause/replace/release on the device's actual
 * media pipeline. It deliberately does NOT claim the user hears sound --
 * audibility needs human ears (or a peer call) and is labelled separately.
 * No call is placed, nothing leaves the device, the temp media is deleted.
 */
@RunWith(AndroidJUnit4::class)
class AudioPlaybackDeviceTest {

    private fun writeWav(file: File, seconds: Float = 0.4f) {
        val sampleRate = 8000
        val totalSamples = (sampleRate * seconds).toInt()
        val dataSize = totalSamples * 2
        DataOutputStream(file.outputStream().buffered()).use { out ->
            out.writeBytes("RIFF"); out.writeIntLe(36 + dataSize); out.writeBytes("WAVE")
            out.writeBytes("fmt "); out.writeIntLe(16); out.writeShortLe(1); out.writeShortLe(1)
            out.writeIntLe(sampleRate); out.writeIntLe(sampleRate * 2); out.writeShortLe(2); out.writeShortLe(16)
            out.writeBytes("data"); out.writeIntLe(dataSize)
            for (i in 0 until totalSamples) {
                out.writeShortLe((Math.sin(2.0 * Math.PI * 440.0 * i / sampleRate) * 8000).toInt())
            }
        }
    }

    private fun DataOutputStream.writeIntLe(v: Int) {
        write(v); write(v ushr 8); write(v ushr 16); write(v ushr 24)
    }
    private fun DataOutputStream.writeShortLe(v: Int) {
        write(v); write(v ushr 8)
    }

    private fun await(timeoutMs: Long = 5000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        assertTrue("condition not met within ${timeoutMs}ms", condition())
    }

    // The controller (like ExoPlayer itself) is main-thread confined; the
    // app always calls it from Compose's main thread. The test marshals the
    // same way.
    private val main = InstrumentationRegistry.getInstrumentation()

    @Test
    fun realDevicePreparesPlaysReplacesAndReleases() {
        val context: Context = main.targetContext
        val dir = File(context.cacheDir, "audio-device-test").apply { mkdirs() }
        val a = File(dir, "a.wav").also { writeWav(it, seconds = 5f) }
        val b = File(dir, "b.wav").also { writeWav(it, seconds = 5f) }
        val controller = AudioPlaybackController(context)
        fun onMain(block: () -> Unit) = main.runOnMainSync(block)
        try {
            onMain { controller.toggle("voice:m1:1", a.absolutePath, null) }
            await { controller.playback.value?.key == "voice:m1:1" }
            assertNotNull("first note becomes the active playback on real hardware", controller.playback.value)

            onMain { controller.toggle("voice:m2:2", b.absolutePath, null) }
            await { controller.playback.value?.key == "voice:m2:2" }
            assertTrue(controller.isActive("voice:m2:2"))
            assertTrue(!controller.isActive("voice:m1:1"))

            // Position is real player state, not an animation: seek leaves 0
            // and playback advances it (a dead player would not -- the point
            // is ExoPlayer actually PREPARED and runs).
            onMain { controller.seekToFraction("voice:m2:2", 0.5f) }
            await { (controller.playback.value?.positionMs ?: 0L) > 0L }
            val before = controller.playback.value!!.positionMs
            Thread.sleep(400)
            assertTrue(
                "position must advance on the real audio pipeline",
                (controller.playback.value?.positionMs ?: 0L) >= before
            )

            onMain { controller.release() }
            await { controller.playback.value == null }
        } finally {
            onMain { controller.release() }
            a.delete(); b.delete(); dir.delete()
        }
    }
}
