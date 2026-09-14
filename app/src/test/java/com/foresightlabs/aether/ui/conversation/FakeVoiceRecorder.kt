package com.foresightlabs.aether.ui.conversation

import com.foresightlabs.aether.data.media.AudioRecorderManager
import com.foresightlabs.aether.data.media.VoiceRecorder

/** A recorder that records nothing but keeps honest count of what it was asked to do. */
internal class FakeVoiceRecorder : VoiceRecorder {
    override var isRecording = false
    override var isPaused = false
    override var onError: (() -> Unit)? = null
    var startResult = true
    var level = 0.25f
    var elapsed = 0L
    var stopResult: AudioRecorderManager.RecordResult? =
        AudioRecorderManager.RecordResult("/cache/voice_1.m4a", durationSec = 3, durationMs = 3_200L)
    var starts = 0
    var pauses = 0
    var resumes = 0
    var stops = 0
    var cancels = 0

    override fun startRecording(): Boolean {
        starts++
        if (startResult) {
            isRecording = true
            isPaused = false
        }
        return startResult
    }

    override fun pauseRecording(): Boolean {
        if (!isRecording || isPaused) return false
        pauses++
        isPaused = true
        return true
    }

    override fun resumeRecording(): Boolean {
        if (!isRecording || !isPaused) return false
        resumes++
        isPaused = false
        return true
    }

    override fun stopRecording(): AudioRecorderManager.RecordResult? {
        if (!isRecording) return null
        stops++
        isRecording = false
        isPaused = false
        return stopResult
    }

    override fun cancelRecording() {
        cancels++
        isRecording = false
        isPaused = false
    }

    override fun amplitudeFraction(): Float = if (isRecording && !isPaused) level else 0f
    override fun elapsedMs(): Long = elapsed
}
