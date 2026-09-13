package com.foresightlabs.aether.data.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File

class AudioRecorderManager(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var startTimeMs: Long = 0L
    var isRecording: Boolean = false
        private set

    fun startRecording(): Boolean {
        return try {
            val outputFile = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
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
                prepare()
                start()
            }

            mediaRecorder = recorder
            startTimeMs = SystemClock.elapsedRealtime()
            isRecording = true
            true
        } catch (e: Exception) {
            e.printStackTrace()
            cleanUp()
            false
        }
    }

    fun stopRecording(): RecordResult? {
        if (!isRecording) return null

        val recorder = mediaRecorder ?: return null
        val file = currentOutputFile ?: return null
        val duration = ((SystemClock.elapsedRealtime() - startTimeMs) / 1000L).toInt().coerceAtLeast(1)

        return try {
            recorder.stop()
            recorder.release()
            mediaRecorder = null
            isRecording = false
            currentOutputFile = null

            if (file.exists() && file.length() > 0) {
                RecordResult(filePath = file.absolutePath, durationSec = duration)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            cleanUp()
            null
        }
    }

    fun cancelRecording() {
        cleanUp()
    }

    /**
     * Loudest sample since the previous call, normalized to 0..1 -- the UI's
     * level feedback. Poll this on a fixed cadence while recording; between
     * polls [MediaRecorder.getMaxAmplitude] reports the peak, so nothing is
     * missed, and any stored audio never leaves this object's file.
     */
    fun amplitudeFraction(): Float {
        val recorder = mediaRecorder ?: return 0f
        if (!isRecording) return 0f
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
        currentOutputFile?.delete()
        currentOutputFile = null
    }

    data class RecordResult(
        val filePath: String,
        val durationSec: Int
    )
}
