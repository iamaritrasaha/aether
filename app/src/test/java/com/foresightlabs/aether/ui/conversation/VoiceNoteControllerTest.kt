package com.foresightlabs.aether.ui.conversation

import com.foresightlabs.aether.data.media.VoiceWaveform
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [VoiceNoteController] against a fake recorder and a virtual clock: the
 * recorder calls, the level polling, the files it deletes and the commands it
 * hands the screen, for the paths that matter.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceNoteControllerTest {

    private class FakeFocus : VoiceAudioFocus {
        override var onLoss: (() -> Unit)? = null
        var requests = 0
        var abandons = 0
        override fun request() { requests++ }
        override fun abandon() { abandons++ }
    }

    private class Harness(scope: TestScope) {
        val recorder = FakeVoiceRecorder()
        val focus = FakeFocus()
        val deleted = mutableListOf<String>()
        val commands = mutableListOf<VoiceNoteCommand>()
        var trimResult: ((VoiceTake, Long, Long) -> VoiceTake?) = { take, start, end ->
            take.copy(path = "/cache/voice_trim_1.m4a", durationMs = end - start)
        }
        val controller = VoiceNoteController(
            recorder = recorder,
            scope = scope.backgroundScope,
            clock = { scope.testScheduler.currentTime },
            trimTake = { take, start, end -> trimResult(take, start, end) },
            probeDurationMs = { null },
            audioFocus = focus,
            deleteFile = { deleted += it }
        )

        init {
            scope.backgroundScope.launch { controller.commands.collect { commands += it } }
        }

        fun sends() = commands.filterIsInstance<VoiceNoteCommand.Send>()
        fun notices() = commands.filterIsInstance<VoiceNoteCommand.Notice>().map { it.kind }
    }

    private fun TestScope.press(h: Harness, reply: String? = "42", permitted: Boolean = true) {
        h.controller.dispatch(VoiceNoteEvent.Press(testScheduler.currentTime, permitted, reply))
        runCurrent()
    }

    @Test
    fun holdSpeakReleaseSendsTheTakeWithItsReplyAndRealWaveform() = runTest {
        val h = Harness(this)
        press(h)
        assertTrue(h.recorder.isRecording)
        assertEquals(1, h.focus.requests)

        h.recorder.elapsed = 1_000L
        advanceTimeBy(1_050L)
        assertTrue("levels are polled while holding", h.controller.levels.size >= 10)
        assertEquals(1_000L, h.controller.elapsedMs)

        h.controller.dispatch(VoiceNoteEvent.Release(testScheduler.currentTime))
        runCurrent()

        assertEquals(VoiceNoteState.Idle, h.controller.state)
        assertEquals(1, h.recorder.stops)
        assertEquals(1, h.focus.abandons)
        val send = h.sends().single()
        assertEquals("/cache/voice_1.m4a", send.take.path)
        assertEquals("42", send.take.replyToMessageId)
        assertEquals(3, send.durationSec)
        assertEquals(63, send.waveform.size)
        assertTrue("the waveform is packed from the polled levels", VoiceWaveform.decode(send.waveform).any { it > 0f })
        assertTrue("a sent file must outlive the send", h.deleted.isEmpty())
    }

    @Test
    fun slideToCancelReleasesTheMicAndSendsNothing() = runTest {
        val h = Harness(this)
        press(h)
        advanceTimeBy(900L)
        h.controller.dispatch(VoiceNoteEvent.Drag(towardCancelDp = 160f, upDp = 0f))
        h.controller.dispatch(VoiceNoteEvent.Release(testScheduler.currentTime))
        runCurrent()

        assertEquals(VoiceNoteState.Idle, h.controller.state)
        assertEquals(1, h.recorder.cancels)
        assertFalse(h.recorder.isRecording)
        assertTrue(h.sends().isEmpty())
        assertTrue(VoiceNoteCommand.Haptic(VoiceHaptic.THRESHOLD) in h.commands)
    }

    @Test
    fun pausingStopsTheLevelsAndTheClockAndResumingContinuesThem() = runTest {
        val h = Harness(this)
        press(h)
        h.controller.dispatch(VoiceNoteEvent.Drag(0f, 130f))
        assertTrue(h.controller.state is VoiceNoteState.Locked)
        advanceTimeBy(350L)
        val beforePause = h.controller.levels.size

        h.controller.dispatch(VoiceNoteEvent.TogglePause)
        runCurrent()
        assertEquals(1, h.recorder.pauses)
        assertFalse(h.controller.isCapturing)
        advanceTimeBy(2_000L)
        assertEquals("no levels while paused", beforePause, h.controller.levels.size)

        h.controller.dispatch(VoiceNoteEvent.TogglePause)
        runCurrent()
        assertEquals(1, h.recorder.resumes)
        advanceTimeBy(350L)
        assertTrue(h.controller.levels.size > beforePause)
    }

    @Test
    fun finishingOpensReviewWithTheRecordedTakeAndItsLevels() = runTest {
        val h = Harness(this)
        press(h)
        h.controller.dispatch(VoiceNoteEvent.Lock)
        advanceTimeBy(550L)
        val captured = h.controller.levels.toList()
        h.controller.dispatch(VoiceNoteEvent.Finish)
        runCurrent()

        val review = h.controller.state as VoiceNoteState.Review
        assertEquals("/cache/voice_1.m4a", review.take.path)
        assertEquals(3_200L, review.take.durationMs)
        assertEquals(captured, review.take.levels)
        assertEquals("42", review.take.replyToMessageId)
        assertTrue("review is not a sending state", h.sends().isEmpty())
        assertTrue("the live levels are cleared once they belong to the take", h.controller.levels.isEmpty())
    }

    @Test
    fun sendingATrimmedReviewSendsTheCutAndDeletesTheOriginal() = runTest {
        val h = Harness(this)
        press(h)
        h.controller.dispatch(VoiceNoteEvent.Lock)
        advanceTimeBy(500L)
        h.controller.dispatch(VoiceNoteEvent.Finish)
        h.controller.dispatch(VoiceNoteEvent.Trim(startMs = 1_000L, endMs = 3_200L))
        h.controller.dispatch(VoiceNoteEvent.Send)
        runCurrent()

        val send = h.sends().single()
        assertEquals("/cache/voice_trim_1.m4a", send.take.path)
        assertEquals(2, send.durationSec)
        assertEquals(listOf("/cache/voice_1.m4a"), h.deleted)
        assertEquals(VoiceNoteState.Idle, h.controller.state)
    }

    @Test
    fun aTrimThatFailsKeepsTheOriginalAndNeverSendsTheUncutAudio() = runTest {
        val h = Harness(this)
        h.trimResult = { _, _, _ -> null }
        press(h)
        h.controller.dispatch(VoiceNoteEvent.Lock)
        h.controller.dispatch(VoiceNoteEvent.Finish)
        h.controller.dispatch(VoiceNoteEvent.Trim(1_000L, 3_200L))
        h.controller.dispatch(VoiceNoteEvent.Send)
        runCurrent()

        assertTrue(h.sends().isEmpty())
        assertTrue(h.deleted.isEmpty())
        val review = h.controller.state as VoiceNoteState.Review
        assertTrue(review.sendFailed)
        assertEquals("/cache/voice_1.m4a", review.take.path)
    }

    @Test
    fun aSendTelegramRejectedComesBackToReview() = runTest {
        val h = Harness(this)
        press(h)
        advanceTimeBy(800L)
        h.controller.dispatch(VoiceNoteEvent.Release(testScheduler.currentTime))
        runCurrent()
        val take = h.sends().single().take

        h.controller.dispatch(VoiceNoteEvent.SendResult(take, success = false))
        assertEquals(VoiceNoteState.Review(take, sendFailed = true), h.controller.state)
        assertEquals(CurtainState.VOICE_REVIEW, h.controller.state.curtainState)
    }

    @Test
    fun permissionGrantedAfterTheFingerLeftDoesNotStartTheRecorder() = runTest {
        val h = Harness(this)
        press(h, permitted = false)
        assertTrue(VoiceNoteCommand.RequestPermission in h.commands)
        h.controller.dispatch(VoiceNoteEvent.Release(testScheduler.currentTime))
        h.controller.dispatch(VoiceNoteEvent.PermissionResult(granted = true))
        runCurrent()

        assertEquals(0, h.recorder.starts)
        assertFalse(h.recorder.isRecording)
        assertEquals(VoiceNoteState.Idle, h.controller.state)
        assertEquals(listOf(VoiceNotice.MIC_READY), h.notices())
    }

    @Test
    fun aMicThatWillNotStartReturnsToIdleAndSaysSo() = runTest {
        val h = Harness(this)
        h.recorder.startResult = false
        press(h)
        assertEquals(VoiceNoteState.Idle, h.controller.state)
        assertEquals(listOf(VoiceNotice.MIC_UNAVAILABLE), h.notices())
    }

    @Test
    fun aRecorderErrorMidRecordingFreesTheMic() = runTest {
        val h = Harness(this)
        press(h)
        h.recorder.onError!!.invoke()
        runCurrent()
        assertEquals(VoiceNoteState.Idle, h.controller.state)
        assertEquals(1, h.recorder.cancels)
        assertEquals(listOf(VoiceNotice.MIC_UNAVAILABLE), h.notices())
    }

    @Test
    fun losingAudioFocusPausesALockedRecordingInsteadOfLosingIt() = runTest {
        val h = Harness(this)
        press(h)
        advanceTimeBy(700L)
        h.controller.dispatch(VoiceNoteEvent.Lock)
        h.focus.onLoss!!.invoke()
        runCurrent()

        assertEquals(VoiceNoteState.Locked("42", paused = true), h.controller.state)
        assertEquals(1, h.recorder.pauses)
        assertTrue(h.recorder.isRecording)

        h.controller.dispatch(VoiceNoteEvent.TogglePause)
        assertEquals("resuming asks for focus again", 2, h.focus.requests)
    }

    @Test
    fun goingToTheBackgroundFinalizesIntoReviewAndFreesTheMic() = runTest {
        val h = Harness(this)
        press(h)
        advanceTimeBy(700L)
        h.controller.dispatch(VoiceNoteEvent.Interrupted(VoiceInterruption.TRANSIENT, testScheduler.currentTime))
        h.controller.dispatch(VoiceNoteEvent.Interrupted(VoiceInterruption.BACKGROUND, testScheduler.currentTime))
        runCurrent()

        assertTrue(h.controller.state is VoiceNoteState.Review)
        assertFalse(h.recorder.isRecording)
        assertEquals(1, h.recorder.stops)
        assertTrue(h.focus.abandons >= 1)
    }

    @Test
    fun discardingAReviewDeletesItsFile() = runTest {
        val h = Harness(this)
        press(h)
        h.controller.dispatch(VoiceNoteEvent.Lock)
        h.controller.dispatch(VoiceNoteEvent.Finish)
        h.controller.dispatch(VoiceNoteEvent.Discard)
        runCurrent()
        assertEquals(VoiceNoteState.Idle, h.controller.state)
        assertEquals(listOf("/cache/voice_1.m4a"), h.deleted)
    }

    @Test
    fun releaseCleansUpWhateverIsInFlight() = runTest {
        val recording = Harness(this)
        press(recording)
        recording.controller.release()
        assertEquals(1, recording.recorder.cancels)
        assertEquals(VoiceNoteState.Idle, recording.controller.state)

        val reviewing = Harness(this)
        press(reviewing)
        reviewing.controller.dispatch(VoiceNoteEvent.Lock)
        reviewing.controller.dispatch(VoiceNoteEvent.Finish)
        reviewing.controller.release()
        assertEquals(listOf("/cache/voice_1.m4a"), reviewing.deleted)
        assertTrue("a released controller must not hear from the recorder", reviewing.recorder.onError == null)
    }
}
