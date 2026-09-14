package com.foresightlabs.aether.ui.conversation

import com.foresightlabs.aether.ui.conversation.VoiceNoteEffect.DeleteTake
import com.foresightlabs.aether.ui.conversation.VoiceNoteEffect.DiscardRecorder
import com.foresightlabs.aether.ui.conversation.VoiceNoteEffect.Haptic
import com.foresightlabs.aether.ui.conversation.VoiceNoteEffect.Notice
import com.foresightlabs.aether.ui.conversation.VoiceNoteEffect.PauseRecorder
import com.foresightlabs.aether.ui.conversation.VoiceNoteEffect.RequestPermission
import com.foresightlabs.aether.ui.conversation.VoiceNoteEffect.ResumeRecorder
import com.foresightlabs.aether.ui.conversation.VoiceNoteEffect.SendTake
import com.foresightlabs.aether.ui.conversation.VoiceNoteEffect.StartRecorder
import com.foresightlabs.aether.ui.conversation.VoiceNoteEffect.StopPlayback
import com.foresightlabs.aether.ui.conversation.VoiceNoteEffect.StopRecorder
import com.foresightlabs.aether.ui.conversation.VoiceNoteEvent.Back
import com.foresightlabs.aether.ui.conversation.VoiceNoteEvent.Discard
import com.foresightlabs.aether.ui.conversation.VoiceNoteEvent.Drag
import com.foresightlabs.aether.ui.conversation.VoiceNoteEvent.Finish
import com.foresightlabs.aether.ui.conversation.VoiceNoteEvent.Interrupted
import com.foresightlabs.aether.ui.conversation.VoiceNoteEvent.Keep
import com.foresightlabs.aether.ui.conversation.VoiceNoteEvent.PermissionResult
import com.foresightlabs.aether.ui.conversation.VoiceNoteEvent.Press
import com.foresightlabs.aether.ui.conversation.VoiceNoteEvent.Release
import com.foresightlabs.aether.ui.conversation.VoiceNoteEvent.Send
import com.foresightlabs.aether.ui.conversation.VoiceNoteEvent.SendResult
import com.foresightlabs.aether.ui.conversation.VoiceNoteEvent.Stopped
import com.foresightlabs.aether.ui.conversation.VoiceNoteEvent.TogglePause
import com.foresightlabs.aether.ui.conversation.VoiceNoteEvent.Trim
import com.foresightlabs.aether.ui.conversation.VoiceNoteState.Holding
import com.foresightlabs.aether.ui.conversation.VoiceNoteState.Idle
import com.foresightlabs.aether.ui.conversation.VoiceNoteState.Locked
import com.foresightlabs.aether.ui.conversation.VoiceNoteState.Review
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The voice-note rules, one event at a time. [VoiceNoteMachine] is pure, so
 * every transition -- including the ones that would lose a recording if they
 * were wrong -- is pinned here without a recorder, a file or a device.
 */
class VoiceNoteMachineTest {

    private val take = VoiceTake("/cache/voice_1.m4a", 12_000L, List(120) { 0.2f }, replyToMessageId = "42")

    private fun reduce(state: VoiceNoteState, event: VoiceNoteEvent) = VoiceNoteMachine.reduce(state, event)

    /** Applies [events] in order, returning the final transition and every effect along the way. */
    private fun run(start: VoiceNoteState, vararg events: VoiceNoteEvent): Pair<VoiceNoteState, List<VoiceNoteEffect>> {
        var state = start
        val effects = mutableListOf<VoiceNoteEffect>()
        for (event in events) {
            val transition = reduce(state, event)
            state = transition.state
            effects += transition.effects
        }
        return state to effects
    }

    private fun holding(reply: String? = "42") = Holding(startedAtMs = 1_000L, replyToMessageId = reply)

    // --- IDLE -> HOLDING -------------------------------------------------------

    @Test
    fun pressingTheMicWithPermissionHoldsAndStartsTheRecorder() {
        val t = reduce(Idle, Press(nowMs = 1_000L, hasPermission = true, replyToMessageId = "42"))
        assertEquals(holding(), t.state)
        assertEquals(listOf(StartRecorder, Haptic(VoiceHaptic.START)), t.effects)
        assertNull("holding lives in the composer, not the Curtain", t.state.curtainState)
    }

    @Test
    fun pressingWithoutPermissionAsksAndStaysIdle() {
        val t = reduce(Idle, Press(1_000L, hasPermission = false, replyToMessageId = null))
        assertEquals(Idle, t.state)
        assertEquals(listOf(RequestPermission), t.effects)
    }

    @Test
    fun permissionGrantedAfterTheFingerLeftNeverStartsRecording() {
        val (state, effects) = run(
            Idle,
            Press(1_000L, hasPermission = false, replyToMessageId = null),
            Release(1_300L), // the system dialog took the release
            PermissionResult(granted = true)
        )
        assertEquals(Idle, state)
        assertFalse("nothing may start without a live gesture", effects.any { it == StartRecorder })
        assertEquals(Notice(VoiceNotice.MIC_READY), effects.last())
    }

    // --- HOLDING release ---------------------------------------------------------

    @Test
    fun releasingAfterAHoldStopsAndSendsTheWholeTake() {
        val released = reduce(holding(), Release(2_400L))
        assertEquals(Idle, released.state)
        assertEquals(listOf(StopRecorder(AfterStop.SEND, "42")), released.effects)

        val stopped = reduce(released.state, Stopped(take, AfterStop.SEND))
        assertEquals(Idle, stopped.state)
        assertEquals(listOf(SendTake(take, 0L, take.durationMs), Haptic(VoiceHaptic.SEND)), stopped.effects)
    }

    @Test
    fun aStabOfTheMicIsDiscardedWithAHintNotSent() {
        val t = reduce(holding(), Release(1_000L + VoiceNoteMachine.MIN_HOLD_MS - 1))
        assertEquals(Idle, t.state)
        assertEquals(listOf(DiscardRecorder, Notice(VoiceNotice.HOLD_TO_RECORD)), t.effects)
    }

    @Test
    fun aRecorderThatCapturedNothingSaysSoInsteadOfSendingNothing() {
        val t = reduce(Idle, Stopped(take = null, then = AfterStop.SEND))
        assertEquals(Idle, t.state)
        assertEquals(listOf(Notice(VoiceNotice.TOO_SHORT)), t.effects)
    }

    // --- HOLDING slide to cancel -------------------------------------------------

    @Test
    fun releasingPastTheCancelThresholdDiscards() {
        val (armed, dragEffects) = run(holding(), Drag(towardCancelDp = 150f, upDp = 0f))
        assertTrue((armed as Holding).cancelArmed)
        assertEquals(listOf(Haptic(VoiceHaptic.THRESHOLD)), dragEffects)

        val t = reduce(armed, Release(3_000L))
        assertEquals(Idle, t.state)
        assertEquals(listOf(DiscardRecorder), t.effects)
    }

    @Test
    fun slidingBackOutOfTheCancelZoneWithdrawsTheIntent() {
        val (state, _) = run(holding(), Drag(150f, 0f), Drag(-40f, 0f))
        assertFalse((state as Holding).cancelArmed)
        assertEquals(listOf(StopRecorder(AfterStop.SEND, "42")), reduce(state, Release(3_000L)).effects)
    }

    @Test
    fun theCancelThresholdBuzzesOnEntryOnly() {
        val (_, effects) = run(
            holding(),
            Drag(150f, 0f), // enter: buzz
            Drag(30f, 0f),  // deeper: clamped, quiet
            Drag(-45f, 0f), // leave: quiet
            Drag(45f, 0f)   // enter again: buzz
        )
        assertEquals(listOf(Haptic(VoiceHaptic.THRESHOLD), Haptic(VoiceHaptic.THRESHOLD)), effects)
    }

    @Test
    fun cancelTravelIsClampedSoASmallDriftBackCannotDisarm() {
        val (state, _) = run(holding(), Drag(1_000f, 0f), Drag(-10f, 0f))
        assertTrue((state as Holding).cancelArmed)
    }

    // --- HOLDING -> LOCKED ---------------------------------------------------------

    @Test
    fun slidingUpPastTheLockThresholdLocksHandsFreeIntoTheCurtain() {
        val t = reduce(holding(), Drag(towardCancelDp = 0f, upDp = VoiceNoteMachine.LOCK_THRESHOLD_DP + 1f))
        assertEquals(Locked(replyToMessageId = "42"), t.state)
        assertEquals(listOf(Haptic(VoiceHaptic.LOCK)), t.effects)
        assertEquals(CurtainState.VOICE_RECORDING, t.state.curtainState)
    }

    @Test
    fun liftingTheFingerAfterLockingDoesNothing() {
        val t = reduce(Locked("42"), Release(9_000L))
        assertEquals(Locked("42"), t.state)
        assertTrue(t.effects.isEmpty())
    }

    @Test
    fun talkBackStartGoesStraightToHandsFree() {
        val t = reduce(Idle, VoiceNoteEvent.StartHandsFree(hasPermission = true, replyToMessageId = null))
        assertEquals(Locked(replyToMessageId = null), t.state)
        assertEquals(listOf(StartRecorder, Haptic(VoiceHaptic.START)), t.effects)
    }

    @Test
    fun talkBackLockAndCancelActionsWorkWhileHolding() {
        assertEquals(Locked("42"), reduce(holding(), VoiceNoteEvent.Lock).state)
        val cancelled = reduce(holding(), VoiceNoteEvent.Cancel)
        assertEquals(Idle, cancelled.state)
        assertEquals(listOf(DiscardRecorder), cancelled.effects)
    }

    // --- LOCKED ----------------------------------------------------------------------

    @Test
    fun lockedPausesAndResumes() {
        val paused = reduce(Locked("42"), TogglePause)
        assertEquals(Locked("42", paused = true), paused.state)
        assertEquals(listOf(PauseRecorder, Haptic(VoiceHaptic.TOGGLE)), paused.effects)

        val resumed = reduce(paused.state, TogglePause)
        assertEquals(Locked("42", paused = false), resumed.state)
        assertEquals(listOf(ResumeRecorder, Haptic(VoiceHaptic.TOGGLE)), resumed.effects)
    }

    @Test
    fun aRefusedPauseIsNotShownAsPaused() {
        val t = reduce(Locked("42", paused = true), VoiceNoteEvent.PauseFailed)
        assertEquals(Locked("42", paused = false), t.state)
    }

    @Test
    fun lockedDiscardDropsTheRecording() {
        val t = reduce(Locked("42", paused = true), Discard)
        assertEquals(Idle, t.state)
        assertEquals(listOf(DiscardRecorder), t.effects)
    }

    @Test
    fun lockedSendStopsAndSends() {
        val t = reduce(Locked("42"), Send)
        assertEquals(Idle, t.state)
        assertEquals(listOf(StopRecorder(AfterStop.SEND, "42")), t.effects)
    }

    @Test
    fun finishingALockedRecordingOpensReviewInTheSameCurtain() {
        val finishing = reduce(Locked("42", paused = true), Finish)
        assertEquals(listOf(StopRecorder(AfterStop.REVIEW, "42")), finishing.effects)

        val review = reduce(finishing.state, Stopped(take, AfterStop.REVIEW))
        assertEquals(Review(take), review.state)
        assertEquals(CurtainState.VOICE_REVIEW, review.state.curtainState)
    }

    // --- REVIEW ----------------------------------------------------------------------

    @Test
    fun reviewSendSendsExactlyTheSelectedStretch() {
        val t = reduce(Review(take, trimStartMs = 2_000L, trimEndMs = 9_000L), Send)
        assertEquals(Idle, t.state)
        assertEquals(listOf(StopPlayback, SendTake(take, 2_000L, 9_000L), Haptic(VoiceHaptic.SEND)), t.effects)
    }

    @Test
    fun reviewDiscardStopsPlaybackAndDeletesTheTake() {
        val t = reduce(Review(take), Discard)
        assertEquals(Idle, t.state)
        assertEquals(listOf(StopPlayback, DeleteTake(take)), t.effects)
    }

    @Test
    fun trimHandlesGiveWayAtTheOtherHandleLeavingTheMinimum() {
        val review = Review(take, trimStartMs = 2_000L, trimEndMs = 9_000L)

        val startPushedPastEnd = reduce(review, Trim(startMs = 11_500L, endMs = 9_000L)).state as Review
        assertEquals(9_000L - VoiceNoteMachine.MIN_TRIM_MS, startPushedPastEnd.trimStartMs)
        assertEquals(9_000L, startPushedPastEnd.trimEndMs)

        val endPulledPastStart = reduce(review, Trim(startMs = 2_000L, endMs = 500L)).state as Review
        assertEquals(2_000L, endPulledPastStart.trimStartMs)
        assertEquals(2_000L + VoiceNoteMachine.MIN_TRIM_MS, endPulledPastStart.trimEndMs)

        val beyondTheEnds = reduce(reduce(review, Trim(-500L, 9_000L)).state, Trim(0L, 99_000L)).state as Review
        assertEquals(0L, beyondTheEnds.trimStartMs)
        assertEquals(take.durationMs, beyondTheEnds.trimEndMs)
        assertFalse(beyondTheEnds.isTrimmed)
    }

    @Test
    fun changingTheSelectionStopsPlaybackOfTheOldOne() {
        val t = reduce(Review(take), Trim(1_000L, take.durationMs))
        assertEquals(listOf(StopPlayback), t.effects)
        assertTrue((t.state as Review).isTrimmed)
    }

    @Test
    fun aNoteTooShortToTrimCannotBeTrimmed() {
        val short = Review(take.copy(durationMs = VoiceNoteMachine.MIN_TRIM_MS))
        assertFalse(short.canTrim)
        assertEquals(short, reduce(short, Trim(300L, short.trimEndMs)).state)
    }

    // --- Back -------------------------------------------------------------------------

    @Test
    fun backWhileHoldingIsSwallowed() {
        val t = reduce(holding(), Back)
        assertEquals(holding(), t.state)
        assertTrue(t.effects.isEmpty())
    }

    @Test
    fun backWhileRecordingPausesAndAsksAndASecondBackKeeps() {
        val asked = reduce(Locked("42"), Back)
        assertEquals(Locked("42", paused = true, confirmingDiscard = true), asked.state)
        assertEquals(listOf(PauseRecorder), asked.effects)

        val kept = reduce(asked.state, Back)
        assertEquals(Locked("42", paused = true, confirmingDiscard = false), kept.state)
        assertTrue("keeping must not touch the recorder", kept.effects.isEmpty())
    }

    @Test
    fun keepAnswersTheDiscardQuestionWithoutLosingAnything() {
        val t = reduce(Locked("42", paused = true, confirmingDiscard = true), Keep)
        assertEquals(Locked("42", paused = true), t.state)
        assertTrue(t.effects.isEmpty())
    }

    @Test
    fun backInReviewAsksThenKeeps() {
        val asked = reduce(Review(take), Back)
        assertTrue((asked.state as Review).confirmingDiscard)
        assertEquals(listOf(StopPlayback), asked.effects)
        assertEquals(Review(take), reduce(asked.state, Back).state)
    }

    @Test
    fun pauseIsIgnoredWhileTheDiscardQuestionIsOpen() {
        val asking = Locked("42", paused = true, confirmingDiscard = true)
        assertEquals(asking, reduce(asking, TogglePause).state)
    }

    // --- Interruptions ----------------------------------------------------------------

    @Test
    fun aTransientInterruptionPausesALockedRecording() {
        val t = reduce(Locked("42"), Interrupted(VoiceInterruption.TRANSIENT, 9_000L))
        assertEquals(Locked("42", paused = true), t.state)
        assertEquals(listOf(PauseRecorder), t.effects)
    }

    @Test
    fun aHoldThatLosesItsFingerBecomesAPausedHandsFreeRecording() {
        val t = reduce(holding(), Interrupted(VoiceInterruption.TRANSIENT, 5_000L))
        assertEquals(Locked("42", paused = true), t.state)
        assertEquals(listOf(PauseRecorder), t.effects)
    }

    @Test
    fun anInterruptedStabIsDropped() {
        val t = reduce(holding(), Interrupted(VoiceInterruption.TRANSIENT, 1_100L))
        assertEquals(Idle, t.state)
        assertEquals(listOf(DiscardRecorder), t.effects)
    }

    @Test
    fun goingToTheBackgroundFinalizesIntoReviewAndFreesTheMic() {
        val locked = reduce(Locked("42", paused = true), Interrupted(VoiceInterruption.BACKGROUND, 9_000L))
        assertEquals(listOf(StopRecorder(AfterStop.REVIEW, "42")), locked.effects)

        val held = reduce(holding(), Interrupted(VoiceInterruption.BACKGROUND, 5_000L))
        assertEquals(listOf(StopRecorder(AfterStop.REVIEW, "42")), held.effects)
        assertEquals(Review(take), reduce(held.state, Stopped(take, AfterStop.REVIEW)).state)
    }

    @Test
    fun anInterruptionInReviewOnlyStopsPlayback() {
        val t = reduce(Review(take), Interrupted(VoiceInterruption.BACKGROUND, 9_000L))
        assertEquals(Review(take), t.state)
        assertEquals(listOf(StopPlayback), t.effects)
    }

    @Test
    fun aRecorderThatDiesMidRecordingIsReleasedWithANotice() {
        val t = reduce(Locked("42"), VoiceNoteEvent.RecorderError)
        assertEquals(Idle, t.state)
        assertEquals(listOf(DiscardRecorder, Notice(VoiceNotice.MIC_UNAVAILABLE)), t.effects)
    }

    @Test
    fun aRecorderThatWillNotStartReturnsToIdleWithANotice() {
        val t = reduce(holding(), VoiceNoteEvent.RecorderFailed)
        assertEquals(Idle, t.state)
        assertEquals(listOf(Notice(VoiceNotice.MIC_UNAVAILABLE)), t.effects)
    }

    // --- Send failure -------------------------------------------------------------------

    @Test
    fun aFailedSendBringsTheTakeBackToRetry() {
        val t = reduce(Idle, SendResult(take, success = false))
        assertEquals(Review(take, sendFailed = true), t.state)
        assertTrue(t.effects.isEmpty())
    }

    @Test
    fun aSuccessfulSendChangesNothing() {
        assertEquals(VoiceNoteTransition(Idle), reduce(Idle, SendResult(take, success = true)))
    }

    @Test
    fun aFailedSendWhileSomethingNewIsRecordingSaysSoAndFreesTheFile() {
        val t = reduce(holding(), SendResult(take, success = false))
        assertEquals(holding(), t.state)
        assertEquals(listOf(DeleteTake(take), Notice(VoiceNotice.SEND_FAILED)), t.effects)
    }

    // --- Reply target --------------------------------------------------------------------

    @Test
    fun theReplyTargetAtPressTimeTravelsToTheSentNoteOnEveryPath() {
        val (_, fast) = run(Idle, Press(1_000L, true, "77"), Release(3_000L))
        assertTrue(StopRecorder(AfterStop.SEND, "77") in fast)

        val (_, locked) = run(Idle, Press(1_000L, true, "77"), Drag(0f, 200f), TogglePause, Send)
        assertTrue(StopRecorder(AfterStop.SEND, "77") in locked)

        val (_, reviewed) = run(Idle, Press(1_000L, true, "77"), VoiceNoteEvent.Lock, Finish)
        assertTrue(StopRecorder(AfterStop.REVIEW, "77") in reviewed)
    }

    // --- Levels ----------------------------------------------------------------------------

    @Test
    fun theTrimmedWaveformIsCutFromTheSameStretchAsTheAudio() {
        val levels = List(100) { it / 100f }
        val slice = VoiceNoteMachine.sliceLevels(levels, durationMs = 10_000L, startMs = 2_000L, endMs = 5_000L)
        assertEquals(30, slice.size)
        assertEquals(0.20f, slice.first(), 0.0001f)
        assertEquals(0.49f, slice.last(), 0.0001f)
        assertTrue(VoiceNoteMachine.sliceLevels(emptyList(), 10_000L, 0L, 5_000L).isEmpty())
    }
}
