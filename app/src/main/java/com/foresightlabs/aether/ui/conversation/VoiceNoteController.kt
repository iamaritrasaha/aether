package com.foresightlabs.aether.ui.conversation

import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.foresightlabs.aether.data.media.AudioRecorderManager
import com.foresightlabs.aether.data.media.VoiceNoteTrimmer
import com.foresightlabs.aether.data.media.VoiceRecorder
import com.foresightlabs.aether.data.media.VoiceWaveform
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What voice-note UI reads. [VoiceNoteController] is the live one. */
@Stable
interface VoiceNoteUiState {
    val state: VoiceNoteState
    /** Audio captured so far in this recording; paused stretches are not counted. */
    val elapsedMs: Long
    /** Levels polled so far in this recording (0..1), oldest first. */
    val levels: List<Float>

    object Idle : VoiceNoteUiState {
        override val state: VoiceNoteState = VoiceNoteState.Idle
        override val elapsedMs: Long = 0L
        override val levels: List<Float> = emptyList()
    }
}

/** What the controller needs the screen to do: things only a View or the composition can. */
sealed interface VoiceNoteCommand {
    data object RequestPermission : VoiceNoteCommand
    data object StopPlayback : VoiceNoteCommand
    data class Haptic(val kind: VoiceHaptic) : VoiceNoteCommand
    data class Notice(val kind: VoiceNotice) : VoiceNoteCommand
    /** Hand [take] to Telegram; report back with [VoiceNoteEvent.SendResult]. */
    class Send(val take: VoiceTake, val durationSec: Int, val waveform: ByteArray) : VoiceNoteCommand
}

/** Exclusive audio focus while the mic is open, so music pauses and a call interrupts us. */
interface VoiceAudioFocus {
    var onLoss: (() -> Unit)?
    fun request()
    fun abandon()
}

/**
 * Runs [VoiceNoteMachine]: holds the state, performs the effects against the
 * recorder, and polls levels while audio is being captured.
 *
 * Lives in [VoiceNoteViewModel] so a recording outlives rotation -- the
 * MediaRecorder, its file and the levels survive the Activity; only the
 * gesture does not.
 */
@Stable
class VoiceNoteController(
    private val recorder: VoiceRecorder,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val trimTake: suspend (take: VoiceTake, startMs: Long, endMs: Long) -> VoiceTake?,
    private val probeDurationMs: (path: String) -> Long? = { null },
    private val audioFocus: VoiceAudioFocus? = null,
    private val deleteFile: (path: String) -> Unit = { File(it).delete() }
) : VoiceNoteUiState {

    override var state: VoiceNoteState by mutableStateOf(VoiceNoteState.Idle)
        private set
    override var elapsedMs: Long by mutableLongStateOf(0L)
        private set
    private val liveLevels = mutableStateListOf<Float>()
    override val levels: List<Float> get() = liveLevels

    /** True while audio is actually being captured -- held, or locked and not paused. */
    val isCapturing: Boolean
        get() = when (val current = state) {
            is VoiceNoteState.Holding -> true
            is VoiceNoteState.Locked -> !current.paused
            else -> false
        }

    private val outbox = Channel<VoiceNoteCommand>(Channel.UNLIMITED)

    /** Collected by the screen; buffered, so nothing is lost across rotation. */
    val commands: Flow<VoiceNoteCommand> = outbox.receiveAsFlow()

    private var poller: Job? = null
    private val queue = ArrayDeque<VoiceNoteEvent>()
    private var dispatching = false

    init {
        recorder.onError = { dispatch(VoiceNoteEvent.RecorderError) }
        audioFocus?.onLoss = { dispatch(VoiceNoteEvent.Interrupted(VoiceInterruption.TRANSIENT, clock())) }
    }

    fun now(): Long = clock()

    /**
     * Applies [event]. Effects may feed events back (a stopped recorder reports
     * its take); those queue behind the current one instead of re-entering it.
     */
    fun dispatch(event: VoiceNoteEvent) {
        queue.addLast(event)
        if (dispatching) return
        dispatching = true
        try {
            while (queue.isNotEmpty()) {
                val transition = VoiceNoteMachine.reduce(state, queue.removeFirst())
                state = transition.state
                transition.effects.forEach(::perform)
            }
        } finally {
            dispatching = false
        }
        syncPoller()
    }

    private fun perform(effect: VoiceNoteEffect) {
        when (effect) {
            VoiceNoteEffect.RequestPermission -> emit(VoiceNoteCommand.RequestPermission)
            VoiceNoteEffect.StartRecorder -> {
                liveLevels.clear()
                elapsedMs = 0L
                if (recorder.startRecording()) {
                    audioFocus?.request()
                } else {
                    dispatch(VoiceNoteEvent.RecorderFailed)
                }
            }
            VoiceNoteEffect.PauseRecorder -> when {
                recorder.pauseRecording() -> elapsedMs = recorder.elapsedMs()
                recorder.isRecording && !recorder.isPaused -> dispatch(VoiceNoteEvent.PauseFailed)
            }
            VoiceNoteEffect.ResumeRecorder -> if (recorder.resumeRecording()) {
                audioFocus?.request()
            } else {
                // Keep what was said rather than pretend to record.
                dispatch(VoiceNoteEvent.Finish)
            }
            VoiceNoteEffect.DiscardRecorder -> {
                recorder.cancelRecording()
                audioFocus?.abandon()
                liveLevels.clear()
                elapsedMs = 0L
            }
            is VoiceNoteEffect.StopRecorder -> {
                val captured = liveLevels.toList()
                val result = recorder.stopRecording()
                audioFocus?.abandon()
                liveLevels.clear()
                elapsedMs = 0L
                val take = result?.let {
                    VoiceTake(
                        path = it.filePath,
                        durationMs = probeDurationMs(it.filePath)?.takeIf { probed -> probed > 0L } ?: it.durationMs,
                        levels = captured,
                        replyToMessageId = effect.replyToMessageId
                    )
                }
                dispatch(VoiceNoteEvent.Stopped(take, effect.then))
            }
            is VoiceNoteEffect.SendTake -> send(effect)
            is VoiceNoteEffect.DeleteTake -> deleteFile(effect.take.path)
            VoiceNoteEffect.StopPlayback -> emit(VoiceNoteCommand.StopPlayback)
            is VoiceNoteEffect.Haptic -> emit(VoiceNoteCommand.Haptic(effect.kind))
            is VoiceNoteEffect.Notice -> emit(VoiceNoteCommand.Notice(effect.kind))
        }
    }

    private fun send(effect: VoiceNoteEffect.SendTake) {
        val take = effect.take
        if (effect.startMs <= 0L && effect.endMs >= take.durationMs) {
            emitSend(take)
            return
        }
        scope.launch {
            val trimmed = runCatching { trimTake(take, effect.startMs, effect.endMs) }.getOrNull()
            if (trimmed == null) {
                // Never fall back to sending audio the person cut out.
                dispatch(VoiceNoteEvent.SendResult(take, success = false))
            } else {
                deleteFile(take.path)
                emitSend(trimmed)
            }
        }
    }

    private fun emitSend(take: VoiceTake) {
        emit(
            VoiceNoteCommand.Send(
                take = take,
                durationSec = ((take.durationMs + 500L) / 1000L).toInt().coerceAtLeast(1),
                waveform = VoiceWaveform.encode(take.levels)
            )
        )
    }

    private fun emit(command: VoiceNoteCommand) {
        outbox.trySend(command)
    }

    private fun syncPoller() {
        if (isCapturing && recorder.isRecording) {
            if (poller?.isActive == true) return
            poller = scope.launch {
                while (true) {
                    elapsedMs = recorder.elapsedMs()
                    liveLevels.add(recorder.amplitudeFraction())
                    delay(LEVEL_POLL_MS)
                }
            }
        } else {
            poller?.cancel()
            poller = null
            if (recorder.isRecording) elapsedMs = recorder.elapsedMs()
        }
    }

    /** Frees the mic and deletes anything unsent. The controller is inert afterwards. */
    fun release() {
        poller?.cancel()
        poller = null
        if (recorder.isRecording) recorder.cancelRecording()
        (state as? VoiceNoteState.Review)?.let { deleteFile(it.take.path) }
        audioFocus?.abandon()
        audioFocus?.onLoss = null
        recorder.onError = null
        state = VoiceNoteState.Idle
        liveLevels.clear()
        elapsedMs = 0L
    }

    companion object {
        const val LEVEL_POLL_MS = 100L
    }
}

/** Keeps a conversation's [VoiceNoteController] across Activity recreation. */
class VoiceNoteViewModel(application: Application) : AndroidViewModel(application) {

    val controller = VoiceNoteController(
        recorder = AudioRecorderManager(application),
        scope = viewModelScope,
        clock = SystemClock::elapsedRealtime,
        trimTake = { take, startMs, endMs ->
            withContext(Dispatchers.IO) { trimToNewFile(application.cacheDir, take, startMs, endMs) }
        },
        probeDurationMs = ::probeDurationMs,
        audioFocus = SystemVoiceAudioFocus(application)
    )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            AudioRecorderManager.deleteStaleRecordings(application.cacheDir)
        }
    }

    override fun onCleared() {
        controller.release()
    }
}

@Composable
fun rememberVoiceNoteController(): VoiceNoteController {
    val application = LocalContext.current.applicationContext as Application
    val factory = remember(application) {
        viewModelFactory { initializer { VoiceNoteViewModel(application) } }
    }
    return viewModel<VoiceNoteViewModel>(factory = factory).controller
}

private fun trimToNewFile(cacheDir: File, take: VoiceTake, startMs: Long, endMs: Long): VoiceTake? {
    val output = File(cacheDir, "${AudioRecorderManager.FILE_PREFIX}trim_${System.currentTimeMillis()}.m4a")
    if (!VoiceNoteTrimmer.trim(File(take.path), output, startMs, endMs)) return null
    return VoiceTake(
        path = output.absolutePath,
        durationMs = probeDurationMs(output.absolutePath)?.takeIf { it > 0L } ?: (endMs - startMs),
        levels = VoiceNoteMachine.sliceLevels(take.levels, take.durationMs, startMs, endMs),
        replyToMessageId = take.replyToMessageId
    )
}

private fun probeDurationMs(path: String): Long? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(path)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
    } catch (_: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

private class SystemVoiceAudioFocus(context: Context) : VoiceAudioFocus {
    override var onLoss: (() -> Unit)? = null

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var held = false

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        // A ducking request (a notification sound) is no reason to stop talking.
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            held = false
            onLoss?.invoke()
        }
    }

    private val focusRequest: AudioFocusRequest? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener(listener, Handler(Looper.getMainLooper()))
            .build()
    } else {
        null
    }

    override fun request() {
        if (held) return
        val manager = audioManager ?: return
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.requestAudioFocus(focusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
        }
        held = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    override fun abandon() {
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.abandonAudioFocusRequest(focusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(listener)
        }
        held = false
    }
}
