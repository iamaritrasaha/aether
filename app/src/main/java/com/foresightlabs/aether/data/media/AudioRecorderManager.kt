package com.foresightlabs.aether.data.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File

/**
 * What the voice-note flow needs from a microphone recorder. [AudioRecorderManager]
 * is the real one; tests drive the flow with a fake.
 */
interface VoiceRecorder {
    val isRecording: Boolean
    val isPaused: Boolean
    fun startRecording(): Boolean
    fun pauseRecording(): Boolean
    fun resumeRecording(): Boolean
    fun stopRecording(): AudioRecorderManager.RecordResult?
    fun cancelRecording()
    fun amplitudeFraction(): Float
    /** Audio actually captured so far -- paused stretches are not counted. */
    fun elapsedMs(): Long
    /** Called when the recorder fails mid-recording (the mic was taken, the media server died). */
    var onError: (() -> Unit)?
}

class AudioRecorderManager(private val context: Context) : VoiceRecorder {

    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null

    // Captured time is the closed segments plus the open one, so pausing
    // stops the clock exactly where the audio stops.
    private var closedSegmentsMs: Long = 0L
    private var segmentStartMs: Long = 0L

    override var isRecording: Boolean = false
        private set
    override var isPaused: Boolean = false
        private set
    override var onError: (() -> Unit)? = null

    override fun startRecording(): Boolean {
        return try {
            val outputFile = File(context.cacheDir, "$FILE_PREFIX${System.currentTimeMillis()}.m4a")
            currentOutputFile = outputFile

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile.absolutePath)
                setOnErrorListener { _, _, _ -> onError?.invoke() }
                prepare()
                start()
            }

            mediaRecorder = recorder
            closedSegmentsMs = 0L
            segmentStartMs = SystemClock.elapsedRealtime()
            isRecording = true
            isPaused = false
            true
        } catch (e: Exception) {
            e.printStackTrace()
            cleanUp()
            false
        }
    }

    /**
     * Pauses capture without finalizing the file (MediaRecorder has supported
     * this since API 24, Aether's minSdk). False when there is nothing to pause
     * or the recorder refused -- the caller must then treat it as still recording.
     */
    override fun pauseRecording(): Boolean {
        val recorder = mediaRecorder ?: return false
        if (!isRecording || isPaused) return false
        return try {
            recorder.pause()
            closedSegmentsMs += SystemClock.elapsedRealtime() - segmentStartMs
            isPaused = true
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun resumeRecording(): Boolean {
        val recorder = mediaRecorder ?: return false
        if (!isRecording || !isPaused) return false
        return try {
            recorder.resume()
            segmentStartMs = SystemClock.elapsedRealtime()
            isPaused = false
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun elapsedMs(): Long {
        if (!isRecording) return 0L
        return closedSegmentsMs + if (isPaused) 0L else SystemClock.elapsedRealtime() - segmentStartMs
    }

    override fun stopRecording(): RecordResult? {
        if (!isRecording) return null

        val recorder = mediaRecorder ?: return null
        val file = currentOutputFile ?: return null
        val durationMs = elapsedMs()

        return try {
            recorder.stop()
            recorder.release()
            mediaRecorder = null
            isRecording = false
            isPaused = false
            currentOutputFile = null

            if (file.exists() && file.length() > 0) {
                RecordResult(
                    filePath = file.absolutePath,
                    durationSec = ((durationMs + 500) / 1000L).toInt().coerceAtLeast(1),
                    durationMs = durationMs
                )
            } else {
                file.delete()
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            cleanUp()
            null
        }
    }

    override fun cancelRecording() {
        cleanUp()
    }

    /**
     * Loudest sample since the previous call, normalized to 0..1 -- the UI's
     * level feedback. Poll this on a fixed cadence while recording; between
     * polls [MediaRecorder.getMaxAmplitude] reports the peak, so nothing is
     * missed, and any stored audio never leaves this object's file.
     */
    override fun amplitudeFraction(): Float {
        val recorder = mediaRecorder ?: return 0f
        if (!isRecording || isPaused) return 0f
        return runCatching {
            (recorder.maxAmplitude / 32767f).coerceIn(0f, 1f)
        }.getOrDefault(0f)
    }

    private fun cleanUp() {
        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {}
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {}
        mediaRecorder = null
        isRecording = false
        isPaused = false
        currentOutputFile?.delete()
        currentOutputFile = null
    }

    data class RecordResult(
        val filePath: String,
        val durationSec: Int,
        val durationMs: Long = durationSec * 1000L
    )

    companion object {
        /** Every voice-note temp file starts with this, so stale ones can be found. */
        const val FILE_PREFIX = "voice_"

        /**
         * A sent note's file must outlive the send -- TDLib uploads from the path
         * afterwards, and re-reads it to retry -- so sent files cannot be deleted
         * on the spot. Anything older than this has long since uploaded or been
         * abandoned (a process death mid-recording leaves one behind).
         */
        const val STALE_AFTER_MS = 3L * 24 * 60 * 60 * 1000

        /** Deletes voice-note temp files in [dir] last modified before [nowMs] - [STALE_AFTER_MS]. */
        fun deleteStaleRecordings(dir: File, nowMs: Long = System.currentTimeMillis()): Int {
            val stale = dir.listFiles { file ->
                file.isFile && file.name.startsWith(FILE_PREFIX) && nowMs - file.lastModified() > STALE_AFTER_MS
            } ?: return 0
            return stale.count { it.delete() }
        }
    }
}
