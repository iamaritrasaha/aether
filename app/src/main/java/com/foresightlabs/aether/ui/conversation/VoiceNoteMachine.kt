package com.foresightlabs.aether.ui.conversation

import androidx.compose.runtime.Immutable

/** A finished recording on disk, waiting to be reviewed or sent. */
@Immutable
data class VoiceTake(
    val path: String,
    val durationMs: Long,
    /** Input levels (0..1) polled while recording -- the waveform's only source. */
    val levels: List<Float>,
    /** The message this note answers, fixed when the recording began. */
    val replyToMessageId: String?
)

/**
 * Where a conversation's voice note is. One value, never a set of flags.
 *
 * ```
 * Idle ──hold──▶ Holding ──release──▶ sent                 (the composer is the recorder)
 *                  ├─ slide past cancel, release ──▶ Idle
 *                  └─ slide up / TalkBack start ──▶ Locked  (the Curtain is the recorder)
 * Locked ⇄ paused · send ──▶ sent · delete ──▶ Idle · review ──▶ Review
 * Review ── send ──▶ sent · delete ──▶ Idle
 * ```
 *
 * [curtainState] is the Curtain state this phase needs, if any: Idle and Holding
 * live in the composer row, Locked and Review are Curtain states. The Curtain
 * follows this value; it never writes it.
 */
@Immutable
sealed interface VoiceNoteState {
    val curtainState: CurtainState? get() = null

    data object Idle : VoiceNoteState

    data class Holding(
        val startedAtMs: Long,
        val replyToMessageId: String?,
        /** Finger travel toward the cancel side, in dp. */
        val cancelDragDp: Float = 0f,
        /** Finger travel upward, in dp. */
        val lockDragDp: Float = 0f
    ) : VoiceNoteState {
        val cancelArmed: Boolean get() = cancelDragDp >= VoiceNoteMachine.CANCEL_THRESHOLD_DP
        val cancelFraction: Float get() = (cancelDragDp / VoiceNoteMachine.CANCEL_THRESHOLD_DP).coerceIn(0f, 1f)
        val lockFraction: Float get() = (lockDragDp / VoiceNoteMachine.LOCK_THRESHOLD_DP).coerceIn(0f, 1f)
    }

    data class Locked(
        val replyToMessageId: String?,
        val paused: Boolean = false,
        /** Back asked to leave: the controls give way to "Discard this voice message?". */
        val confirmingDiscard: Boolean = false
    ) : VoiceNoteState {
        override val curtainState: CurtainState get() = CurtainState.VOICE_RECORDING
    }

    data class Review(
        val take: VoiceTake,
        val trimStartMs: Long = 0L,
        val trimEndMs: Long = take.durationMs,
        val confirmingDiscard: Boolean = false,
        /** The last send did not reach Telegram; the take is still here to retry. */
        val sendFailed: Boolean = false
    ) : VoiceNoteState {
        override val curtainState: CurtainState get() = CurtainState.VOICE_REVIEW
        val selectedDurationMs: Long get() = trimEndMs - trimStartMs
        val isTrimmed: Boolean get() = trimStartMs > 0L || trimEndMs < take.durationMs
        val canTrim: Boolean get() = take.durationMs > VoiceNoteMachine.MIN_TRIM_MS
    }
}

enum class AfterStop { SEND, REVIEW }

/**
 * Why a recording is being interrupted. [TRANSIENT]: the Activity paused, the
 * device rotated, or another app took audio focus -- we may be right back.
 * [BACKGROUND]: the app went to the background or the conversation was left.
 */
enum class VoiceInterruption { TRANSIENT, BACKGROUND }

enum class VoiceHaptic { START, THRESHOLD, LOCK, TOGGLE, SEND }

enum class VoiceNotice { HOLD_TO_RECORD, TOO_SHORT, MIC_UNAVAILABLE, MIC_READY, MIC_DENIED, SEND_FAILED }

sealed interface VoiceNoteEvent {
    /** The mic went down. */
    data class Press(val nowMs: Long, val hasPermission: Boolean, val replyToMessageId: String?) : VoiceNoteEvent
    /** TalkBack's "Start recording": no finger to hold, so straight to hands-free. */
    data class StartHandsFree(val hasPermission: Boolean, val replyToMessageId: String?) : VoiceNoteEvent
    /** Finger movement since the last event: toward the cancel side, and upward, in dp. */
    data class Drag(val towardCancelDp: Float, val upDp: Float) : VoiceNoteEvent
    data class Release(val nowMs: Long) : VoiceNoteEvent
    data object Lock : VoiceNoteEvent
    data object Cancel : VoiceNoteEvent
    data class PermissionResult(val granted: Boolean) : VoiceNoteEvent
    /** The recorder would not start. */
    data object RecorderFailed : VoiceNoteEvent
    /** The recorder died mid-recording. */
    data object RecorderError : VoiceNoteEvent
    data object TogglePause : VoiceNoteEvent
    data object PauseFailed : VoiceNoteEvent
    /** Stop recording and review the take. */
    data object Finish : VoiceNoteEvent
    data object Send : VoiceNoteEvent
    data object Discard : VoiceNoteEvent
    /** Dismiss the discard question and carry on. */
    data object Keep : VoiceNoteEvent
    data object Back : VoiceNoteEvent
    data class Trim(val startMs: Long, val endMs: Long) : VoiceNoteEvent
    /** The recorder stopped; [take] is null when nothing usable was captured. */
    data class Stopped(val take: VoiceTake?, val then: AfterStop) : VoiceNoteEvent
    data class Interrupted(val kind: VoiceInterruption, val nowMs: Long) : VoiceNoteEvent
    data class SendResult(val take: VoiceTake, val success: Boolean) : VoiceNoteEvent
}

sealed interface VoiceNoteEffect {
    data object RequestPermission : VoiceNoteEffect
    data object StartRecorder : VoiceNoteEffect
    data object PauseRecorder : VoiceNoteEffect
    data object ResumeRecorder : VoiceNoteEffect
    /** Stop, release the mic, delete the file. */
    data object DiscardRecorder : VoiceNoteEffect
    /** Stop and finalize the file; the result comes back as [VoiceNoteEvent.Stopped]. */
    data class StopRecorder(val then: AfterStop, val replyToMessageId: String?) : VoiceNoteEffect
    data class SendTake(val take: VoiceTake, val startMs: Long, val endMs: Long) : VoiceNoteEffect
    data class DeleteTake(val take: VoiceTake) : VoiceNoteEffect
    data object StopPlayback : VoiceNoteEffect
    data class Haptic(val kind: VoiceHaptic) : VoiceNoteEffect
    data class Notice(val kind: VoiceNotice) : VoiceNoteEffect
}

data class VoiceNoteTransition(
    val state: VoiceNoteState,
    val effects: List<VoiceNoteEffect> = emptyList()
)

/**
 * The voice-note rules, as a pure function of (state, event). Nothing here
 * touches a recorder, a file or a View -- [VoiceNoteController] performs the
 * effects -- so every rule is testable on the JVM.
 */
object VoiceNoteMachine {
    /** A shorter hold is a mis-tap, not a message. */
    const val MIN_HOLD_MS = 600L
    const val CANCEL_THRESHOLD_DP = 148f
    /** Past the threshold the drag stops counting, so a small drift back cannot disarm it. */
    const val CANCEL_TRAVEL_DP = CANCEL_THRESHOLD_DP + 16f
    const val LOCK_THRESHOLD_DP = 120f
    /** The shortest selection a trim may leave. */
    const val MIN_TRIM_MS = 1_000L

    fun reduce(state: VoiceNoteState, event: VoiceNoteEvent): VoiceNoteTransition = when (event) {
        is VoiceNoteEvent.Press -> when {
            state !is VoiceNoteState.Idle -> stay(state)
            !event.hasPermission -> VoiceNoteTransition(state, listOf(VoiceNoteEffect.RequestPermission))
            else -> VoiceNoteTransition(
                VoiceNoteState.Holding(event.nowMs, event.replyToMessageId),
                listOf(VoiceNoteEffect.StartRecorder, VoiceNoteEffect.Haptic(VoiceHaptic.START))
            )
        }

        is VoiceNoteEvent.StartHandsFree -> when {
            state !is VoiceNoteState.Idle -> stay(state)
            !event.hasPermission -> VoiceNoteTransition(state, listOf(VoiceNoteEffect.RequestPermission))
            else -> VoiceNoteTransition(
                VoiceNoteState.Locked(event.replyToMessageId),
                listOf(VoiceNoteEffect.StartRecorder, VoiceNoteEffect.Haptic(VoiceHaptic.START))
            )
        }

        is VoiceNoteEvent.Drag -> if (state is VoiceNoteState.Holding) drag(state, event) else stay(state)

        is VoiceNoteEvent.Release -> when {
            state !is VoiceNoteState.Holding -> stay(state) // hands-free: the finger leaving is the point
            state.cancelArmed -> VoiceNoteTransition(VoiceNoteState.Idle, listOf(VoiceNoteEffect.DiscardRecorder))
            event.nowMs - state.startedAtMs < MIN_HOLD_MS -> VoiceNoteTransition(
                VoiceNoteState.Idle,
                listOf(VoiceNoteEffect.DiscardRecorder, VoiceNoteEffect.Notice(VoiceNotice.HOLD_TO_RECORD))
            )
            else -> VoiceNoteTransition(
                VoiceNoteState.Idle,
                listOf(VoiceNoteEffect.StopRecorder(AfterStop.SEND, state.replyToMessageId))
            )
        }

        VoiceNoteEvent.Lock -> if (state is VoiceNoteState.Holding) lock(state) else stay(state)

        VoiceNoteEvent.Cancel -> if (state is VoiceNoteState.Holding) {
            VoiceNoteTransition(VoiceNoteState.Idle, listOf(VoiceNoteEffect.DiscardRecorder))
        } else {
            stay(state)
        }

        // Never starts anything: the finger that asked for the permission has
        // already let go, and a recording needs a live gesture (or TalkBack's
        // explicit start) to end it.
        is VoiceNoteEvent.PermissionResult -> VoiceNoteTransition(
            state,
            listOf(VoiceNoteEffect.Notice(if (event.granted) VoiceNotice.MIC_READY else VoiceNotice.MIC_DENIED))
        )

        VoiceNoteEvent.RecorderFailed -> when (state) {
            is VoiceNoteState.Holding, is VoiceNoteState.Locked -> VoiceNoteTransition(
                VoiceNoteState.Idle,
                listOf(VoiceNoteEffect.Notice(VoiceNotice.MIC_UNAVAILABLE))
            )
            else -> stay(state)
        }

        VoiceNoteEvent.RecorderError -> when (state) {
            is VoiceNoteState.Holding, is VoiceNoteState.Locked -> VoiceNoteTransition(
                VoiceNoteState.Idle,
                listOf(VoiceNoteEffect.DiscardRecorder, VoiceNoteEffect.Notice(VoiceNotice.MIC_UNAVAILABLE))
            )
            else -> stay(state)
        }

        VoiceNoteEvent.TogglePause -> if (state is VoiceNoteState.Locked && !state.confirmingDiscard) {
            VoiceNoteTransition(
                state.copy(paused = !state.paused),
                listOf(
                    if (state.paused) VoiceNoteEffect.ResumeRecorder else VoiceNoteEffect.PauseRecorder,
                    VoiceNoteEffect.Haptic(VoiceHaptic.TOGGLE)
                )
            )
        } else {
            stay(state)
        }

        VoiceNoteEvent.PauseFailed -> if (state is VoiceNoteState.Locked) stay(state.copy(paused = false)) else stay(state)

        VoiceNoteEvent.Finish -> if (state is VoiceNoteState.Locked) {
            VoiceNoteTransition(
                state.copy(confirmingDiscard = false),
                listOf(VoiceNoteEffect.StopRecorder(AfterStop.REVIEW, state.replyToMessageId))
            )
        } else {
            stay(state)
        }

        VoiceNoteEvent.Send -> when (state) {
            is VoiceNoteState.Locked -> VoiceNoteTransition(
                VoiceNoteState.Idle,
                listOf(VoiceNoteEffect.StopRecorder(AfterStop.SEND, state.replyToMessageId))
            )
            is VoiceNoteState.Review -> VoiceNoteTransition(
                VoiceNoteState.Idle,
                listOf(
                    VoiceNoteEffect.StopPlayback,
                    VoiceNoteEffect.SendTake(state.take, state.trimStartMs, state.trimEndMs),
                    VoiceNoteEffect.Haptic(VoiceHaptic.SEND)
                )
            )
            else -> stay(state)
        }

        VoiceNoteEvent.Discard -> when (state) {
            is VoiceNoteState.Holding, is VoiceNoteState.Locked ->
                VoiceNoteTransition(VoiceNoteState.Idle, listOf(VoiceNoteEffect.DiscardRecorder))
            is VoiceNoteState.Review -> VoiceNoteTransition(
                VoiceNoteState.Idle,
                listOf(VoiceNoteEffect.StopPlayback, VoiceNoteEffect.DeleteTake(state.take))
            )
            VoiceNoteState.Idle -> stay(state)
        }

        VoiceNoteEvent.Keep -> when (state) {
            is VoiceNoteState.Locked -> stay(state.copy(confirmingDiscard = false))
            is VoiceNoteState.Review -> stay(state.copy(confirmingDiscard = false))
            else -> stay(state)
        }

        VoiceNoteEvent.Back -> back(state)

        is VoiceNoteEvent.Trim -> if (state is VoiceNoteState.Review) trim(state, event) else stay(state)

        is VoiceNoteEvent.Stopped -> when {
            event.take == null -> VoiceNoteTransition(
                VoiceNoteState.Idle,
                listOf(VoiceNoteEffect.Notice(VoiceNotice.TOO_SHORT))
            )
            event.then == AfterStop.SEND -> VoiceNoteTransition(
                VoiceNoteState.Idle,
                listOf(
                    VoiceNoteEffect.SendTake(event.take, 0L, event.take.durationMs),
                    VoiceNoteEffect.Haptic(VoiceHaptic.SEND)
                )
            )
            else -> VoiceNoteTransition(VoiceNoteState.Review(event.take))
        }

        is VoiceNoteEvent.Interrupted -> interrupted(state, event)

        is VoiceNoteEvent.SendResult -> when {
            event.success -> stay(state)
            // The note never left: bring it back rather than lose it.
            state is VoiceNoteState.Idle -> VoiceNoteTransition(VoiceNoteState.Review(event.take, sendFailed = true))
            // Something new is already underway; say so, and don't leak the file.
            else -> VoiceNoteTransition(
                state,
                listOf(VoiceNoteEffect.DeleteTake(event.take), VoiceNoteEffect.Notice(VoiceNotice.SEND_FAILED))
            )
        }
    }

    private fun drag(state: VoiceNoteState.Holding, event: VoiceNoteEvent.Drag): VoiceNoteTransition {
        val lockDrag = (state.lockDragDp + event.upDp).coerceAtLeast(0f)
        if (lockDrag >= LOCK_THRESHOLD_DP) return lock(state)
        val next = state.copy(
            cancelDragDp = (state.cancelDragDp + event.towardCancelDp).coerceIn(0f, CANCEL_TRAVEL_DP),
            lockDragDp = lockDrag
        )
        // Only crossing INTO the cancel zone buzzes; drifting inside it, or
        // backing out of it, stays quiet.
        val effects = if (!state.cancelArmed && next.cancelArmed) {
            listOf(VoiceNoteEffect.Haptic(VoiceHaptic.THRESHOLD))
        } else {
            emptyList()
        }
        return VoiceNoteTransition(next, effects)
    }

    private fun lock(state: VoiceNoteState.Holding) = VoiceNoteTransition(
        VoiceNoteState.Locked(state.replyToMessageId),
        listOf(VoiceNoteEffect.Haptic(VoiceHaptic.LOCK))
    )

    /**
     * Back never loses a recording in one press. While holding it is swallowed
     * (the finger decides); otherwise it pauses and asks, and a second Back
     * answers "keep".
     */
    private fun back(state: VoiceNoteState): VoiceNoteTransition = when (state) {
        VoiceNoteState.Idle, is VoiceNoteState.Holding -> stay(state)
        is VoiceNoteState.Locked -> when {
            state.confirmingDiscard -> stay(state.copy(confirmingDiscard = false))
            state.paused -> stay(state.copy(confirmingDiscard = true))
            else -> VoiceNoteTransition(
                state.copy(paused = true, confirmingDiscard = true),
                listOf(VoiceNoteEffect.PauseRecorder)
            )
        }
        is VoiceNoteState.Review -> if (state.confirmingDiscard) {
            stay(state.copy(confirmingDiscard = false))
        } else {
            VoiceNoteTransition(state.copy(confirmingDiscard = true), listOf(VoiceNoteEffect.StopPlayback))
        }
    }

    private fun trim(state: VoiceNoteState.Review, event: VoiceNoteEvent.Trim): VoiceNoteTransition {
        if (!state.canTrim) return stay(state)
        val duration = state.take.durationMs
        // Whichever handle moved gives way at the other one, MIN_TRIM_MS short of it.
        val (start, end) = if (event.startMs != state.trimStartMs) {
            event.startMs.coerceIn(0L, state.trimEndMs - MIN_TRIM_MS) to state.trimEndMs
        } else {
            state.trimStartMs to event.endMs.coerceIn(state.trimStartMs + MIN_TRIM_MS, duration)
        }
        if (start == state.trimStartMs && end == state.trimEndMs) return stay(state)
        return VoiceNoteTransition(
            state.copy(trimStartMs = start, trimEndMs = end, sendFailed = false),
            listOf(VoiceNoteEffect.StopPlayback)
        )
    }

    /**
     * An interruption keeps whatever was said. A transient one pauses (a hold
     * that lost its finger becomes a paused hands-free recording, so it can
     * still be sent or deleted); going to the background finalizes the file and
     * frees the mic, leaving the take in review. Only a mis-tap-length hold is
     * dropped.
     */
    private fun interrupted(state: VoiceNoteState, event: VoiceNoteEvent.Interrupted): VoiceNoteTransition = when (state) {
        VoiceNoteState.Idle -> stay(state)
        is VoiceNoteState.Holding -> when {
            event.nowMs - state.startedAtMs < MIN_HOLD_MS ->
                VoiceNoteTransition(VoiceNoteState.Idle, listOf(VoiceNoteEffect.DiscardRecorder))
            event.kind == VoiceInterruption.TRANSIENT -> VoiceNoteTransition(
                VoiceNoteState.Locked(state.replyToMessageId, paused = true),
                listOf(VoiceNoteEffect.PauseRecorder)
            )
            else -> VoiceNoteTransition(
                VoiceNoteState.Locked(state.replyToMessageId, paused = true),
                listOf(VoiceNoteEffect.StopRecorder(AfterStop.REVIEW, state.replyToMessageId))
            )
        }
        is VoiceNoteState.Locked -> when {
            event.kind == VoiceInterruption.BACKGROUND -> VoiceNoteTransition(
                state.copy(confirmingDiscard = false),
                listOf(VoiceNoteEffect.StopRecorder(AfterStop.REVIEW, state.replyToMessageId))
            )
            state.paused -> stay(state)
            else -> VoiceNoteTransition(state.copy(paused = true), listOf(VoiceNoteEffect.PauseRecorder))
        }
        is VoiceNoteState.Review -> VoiceNoteTransition(state, listOf(VoiceNoteEffect.StopPlayback))
    }

    /**
     * The levels that belong to `[startMs, endMs)` of a take -- the trimmed
     * note's waveform is drawn from exactly the audio it keeps.
     */
    fun sliceLevels(levels: List<Float>, durationMs: Long, startMs: Long, endMs: Long): List<Float> {
        if (levels.isEmpty() || durationMs <= 0L) return emptyList()
        val from = (startMs * levels.size / durationMs).toInt().coerceIn(0, levels.size - 1)
        val until = (endMs * levels.size / durationMs).toInt().coerceIn(from + 1, levels.size)
        return levels.subList(from, until).toList()
    }

    private fun stay(state: VoiceNoteState) = VoiceNoteTransition(state)
}
