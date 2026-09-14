package com.foresightlabs.aether.ui.conversation

import android.Manifest
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.data.media.AudioRecorderManager
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ChatType
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.MessageStatus
import com.foresightlabs.aether.domain.model.Presence
import com.foresightlabs.aether.domain.model.User
import com.foresightlabs.aether.ui.theme.AetherTheme
import com.foresightlabs.aether.ui.theme.AppThemeState
import com.foresightlabs.aether.ui.theme.AtmosphereMode
import com.foresightlabs.aether.ui.theme.LocalAppThemeState
import com.foresightlabs.aether.ui.theme.TimeAtmospherePalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The voice note on the real Conversation screen, driven the way a person
 * drives it: a finger down on the mic, moved, lifted; taps on the Curtain's
 * controls; system Back; a system-cancelled gesture; an Activity recreation.
 *
 * The recorder is a fake and the clock is ours, so "held for a second" is a
 * statement, not a sleep. Everything between the finger and the recorder --
 * the composer, the Curtain, the screen's wiring, the state machine -- is real.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi", application = Application::class)
class VoiceNoteInteractionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val tmp = TemporaryFolder()

    private data class SentNote(val path: String, val durationSec: Int, val replyToMessageId: String?)

    private var now = 10_000L
    private val sends = mutableListOf<SentNote>()
    private lateinit var takePath: String
    private lateinit var recorder: FakeVoiceRecorder
    private lateinit var controller: VoiceNoteController

    @Before
    fun setUp() {
        // A real file, so review playback has something to open.
        takePath = tmp.newFile("voice_take.m4a").apply { writeBytes(ByteArray(2_048) { 1 }) }.absolutePath
        recorder = FakeVoiceRecorder().apply {
            stopResult = AudioRecorderManager.RecordResult(takePath, durationSec = 3, durationMs = 3_200L)
        }
        controller = VoiceNoteController(
            recorder = recorder,
            // Never advanced: level polling is the controller test's business.
            scope = CoroutineScope(StandardTestDispatcher()),
            clock = { now },
            trimTake = { take, start, end -> take.copy(durationMs = end - start) },
            deleteFile = {}
        )
        setMicPermission(granted = true)
    }

    // --- IDLE -> HOLDING -> release ----------------------------------------------------

    @Test
    fun holdingTurnsTheComposerIntoTheRecorderAndReleasingSends() {
        showConversation()

        pressMic()
        composeRule.onNodeWithTag("voice_recording_rail").assertIsDisplayed()
        composeRule.onNodeWithTag("voice_lock_hint").assertExists()
        composeRule.onNodeWithTag("attachment_button").assertDoesNotExist()
        composeRule.onNodeWithTag("message_input_field").assertDoesNotExist()
        composeRule.onNodeWithTag("curtain_voice_content").assertDoesNotExist()
        composeRule.onAllNodesWithTag("conversation_curtain").assertCountEquals(1)
        assertTrue(recorder.isRecording)

        now += 1_200L
        lift()

        assertEquals(listOf(SentNote(takePath, 3, null)), sends)
        composeRule.onNodeWithTag("voice_recording_rail").assertDoesNotExist()
        composeRule.onNodeWithTag("message_input_field").assertIsDisplayed()
        composeRule.onNodeWithTag("attachment_button").assertIsDisplayed()
    }

    @Test
    fun aStabOfTheMicSaysHowInThePlaceholderInsteadOfSending() {
        showConversation()
        pressMic()
        now += 150L
        composeRule.mainClock.autoAdvance = false
        lift()
        composeRule.mainClock.advanceTimeBy(100L)

        assertTrue(sends.isEmpty())
        assertEquals(1, recorder.cancels)
        composeRule.onNodeWithTag("composer_placeholder").assertTextEquals("Hold to record, release to send")

        composeRule.mainClock.advanceTimeBy(3_000L)
        composeRule.onNodeWithTag("composer_placeholder").assertTextEquals("Your Message…")
    }

    // --- slide to cancel ------------------------------------------------------------------

    @Test
    fun slidingTowardCancelArmsItBackingOffWithdrawsItAndReleasingThereDiscards() {
        showConversation()
        pressMic()
        now += 1_000L

        moveFinger(dxDp = -170f)
        composeRule.onNodeWithText("Release to cancel").assertIsDisplayed()
        moveFinger(dxDp = 60f)
        composeRule.onNodeWithText("Slide to cancel").assertIsDisplayed()
        moveFinger(dxDp = -60f)
        composeRule.onNodeWithText("Release to cancel").assertIsDisplayed()
        lift()

        assertTrue(sends.isEmpty())
        assertEquals(1, recorder.cancels)
        assertFalse(recorder.isRecording)
        composeRule.onNodeWithTag("message_input_field").assertIsDisplayed()
    }

    // --- lock ---------------------------------------------------------------------------------

    @Test
    fun slidingUpLocksIntoTheOneCurtainAndLiftingTheFingerKeepsRecording() {
        showConversation()
        pressMic()
        now += 700L
        moveFinger(dyDp = -130f)

        composeRule.onNodeWithTag("curtain_voice_content").assertIsDisplayed()
        composeRule.onNodeWithTag("voice_locked_content").assertIsDisplayed()
        composeRule.onAllNodesWithTag("conversation_curtain").assertCountEquals(1)
        // One chrome owner: no title bar, close icon or instructions of its own.
        composeRule.onNodeWithText("Voice message", substring = true, ignoreCase = true).assertDoesNotExist()
        composeRule.onNodeWithText("Slide to cancel").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Cancel").assertDoesNotExist()

        lift()
        composeRule.onNodeWithTag("voice_locked_content").assertIsDisplayed()
        assertTrue(recorder.isRecording)
        assertTrue(sends.isEmpty())
    }

    @Test
    fun talkBackCanLockAHoldAndStartHandsFree() {
        showConversation()
        pressMic()
        composeRule.onNodeWithTag("voice_recording_rail").performCustomAccessibilityActionWithLabel("Lock recording")
        composeRule.onNodeWithTag("voice_locked_content").assertIsDisplayed()
        lift()
        composeRule.onNodeWithContentDescription("Discard voice message").performClick()

        // A double tap in TalkBack is the mic's click action: hands-free, no hold.
        composeRule.onNodeWithTag("voice_record_button").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("voice_locked_content").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Pause recording").assertExists()
        composeRule.onNodeWithContentDescription("Send voice message").assertExists()
    }

    // --- locked: pause / resume / discard ----------------------------------------------

    @Test
    fun aLockedRecordingPausesAndResumes() {
        showConversation()
        startHandsFree()

        composeRule.onNodeWithTag("voice_pause_button").performClick()
        assertShowsPaused()
        composeRule.onNodeWithContentDescription("Resume recording").assertExists()
        composeRule.onNodeWithTag("voice_review_button").assertIsDisplayed()
        assertEquals(1, recorder.pauses)
        assertTrue(recorder.isPaused)

        composeRule.onNodeWithTag("voice_pause_button").performClick()
        composeRule.onNodeWithTag("voice_paused_label", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Pause recording").assertExists()
        assertEquals(1, recorder.resumes)
        assertFalse(recorder.isPaused)
    }

    @Test
    fun discardingALockedRecordingRestoresTheComposerAndItsReply() {
        showConversation(draft = RestoredDraft(text = "", replyMessageId = 1L))
        composeRule.onNodeWithText("Replying to Ishani Roy").assertIsDisplayed()

        startHandsFree()
        composeRule.onNodeWithTag("voice_discard_button").performClick()

        composeRule.onNodeWithTag("curtain_voice_content").assertDoesNotExist()
        composeRule.onNodeWithTag("message_input_field").assertIsDisplayed()
        composeRule.onNodeWithText("Replying to Ishani Roy").assertIsDisplayed()
        assertEquals(1, recorder.cancels)
        assertTrue(sends.isEmpty())
    }

    // --- review -------------------------------------------------------------------------------

    @Test
    fun finishingOpensReviewInTheSameCurtain() {
        showConversation()
        startHandsFree()
        composeRule.onNodeWithTag("voice_pause_button").performClick()
        composeRule.onNodeWithTag("voice_review_button").performClick()

        composeRule.onNodeWithTag("voice_review_content").assertIsDisplayed()
        composeRule.onNodeWithTag("voice_locked_content").assertDoesNotExist()
        composeRule.onAllNodesWithTag("conversation_curtain").assertCountEquals(1)
        composeRule.onNodeWithTag("voice_review_duration").assertTextEquals("0:03")
        composeRule.onNodeWithTag("voice_trim_start").assertExists()
        composeRule.onNodeWithTag("voice_trim_end").assertExists()
        assertEquals(1, recorder.stops)
    }

    @Test
    fun reviewPlaysTheTakeThroughTheConversationsPlayer() {
        showConversation()
        openReview()

        composeRule.onNodeWithContentDescription("Play recording").performClick()
        // Position / selected length appears once the take is the active playback.
        composeRule.onNodeWithTag("voice_review_duration").assertTextContains(" / 0:03", substring = true)
    }

    @Test
    fun trimmingFromTalkBackMovesTheSelection() {
        showConversation()
        openReview()

        composeRule.onNodeWithTag("voice_trim_start").performCustomAccessibilityActionWithLabel("Move later")
        composeRule.onNodeWithTag("voice_trim_start").performCustomAccessibilityActionWithLabel("Move later")
        val review = controller.state as VoiceNoteState.Review
        assertEquals(1_000L, review.trimStartMs)
        composeRule.onNodeWithTag("voice_review_duration").assertTextEquals("0:02")
    }

    @Test
    fun sendingFromReviewSendsAndClosesTheCurtain() {
        showConversation()
        openReview()
        composeRule.onNodeWithTag("voice_send_button").performClick()

        assertEquals(listOf(SentNote(takePath, 3, null)), sends)
        composeRule.onNodeWithTag("curtain_voice_content").assertDoesNotExist()
        composeRule.onNodeWithTag("message_input_field").assertIsDisplayed()
    }

    // --- Back ---------------------------------------------------------------------------------

    @Test
    fun backPausesAndAsksAndOnlyAnExplicitDiscardDropsTheRecording() {
        showConversation()
        startHandsFree()

        pressBack()
        composeRule.onNodeWithTag("voice_discard_question").assertIsDisplayed()
        assertTrue("Back pauses, it does not stop", recorder.isRecording && recorder.isPaused)

        pressBack()
        composeRule.onNodeWithTag("voice_discard_question").assertDoesNotExist()
        assertShowsPaused()

        pressBack()
        composeRule.onNodeWithTag("voice_discard_keep").performClick()
        composeRule.onNodeWithTag("voice_locked_content").assertIsDisplayed()
        assertEquals(0, recorder.cancels)

        pressBack()
        composeRule.onNodeWithTag("voice_discard_confirm").performClick()
        composeRule.onNodeWithTag("curtain_voice_content").assertDoesNotExist()
        assertEquals(1, recorder.cancels)
        assertFalse(composeRule.activity.isFinishing)
    }

    @Test
    fun backWhileHoldingNeitherLeavesTheConversationNorDropsTheRecording() {
        showConversation()
        pressMic()
        pressBack()

        composeRule.onNodeWithTag("voice_recording_rail").assertIsDisplayed()
        assertTrue(recorder.isRecording)
        assertFalse(composeRule.activity.isFinishing)
        now += 1_000L
        lift()
        assertEquals(1, sends.size)
    }

    @Test
    fun backInReviewAsksBeforeDiscarding() {
        showConversation()
        openReview()
        pressBack()
        composeRule.onNodeWithTag("voice_discard_question").assertIsDisplayed()
        pressBack()
        composeRule.onNodeWithTag("voice_review_content").assertIsDisplayed()
        assertFalse(composeRule.activity.isFinishing)
    }

    // --- reply target ---------------------------------------------------------------------

    @Test
    fun theSentNoteRepliesToTheMessageBeingRepliedToAndUsesTheReplyUp() {
        showConversation(draft = RestoredDraft(text = "", replyMessageId = 1L))
        pressMic()
        now += 1_000L
        lift()

        assertEquals(listOf(SentNote(takePath, 3, "1")), sends)
        composeRule.onNodeWithText("Replying to Ishani Roy").assertDoesNotExist()
    }

    // --- interruptions, permission, recreation --------------------------------------------

    @Test
    fun aSystemCancelledHoldIsKeptPausedAndNotSent() {
        showConversation()
        pressMic()
        now += 1_500L
        composeRule.onRoot().performTouchInput { cancel() }
        composeRule.waitForIdle()

        assertTrue(sends.isEmpty())
        composeRule.onNodeWithTag("voice_locked_content").assertIsDisplayed()
        assertShowsPaused()
        assertTrue(recorder.isRecording && recorder.isPaused)
    }

    @Test
    fun withoutPermissionPressingTheMicRecordsNothing() {
        setMicPermission(granted = false)
        showConversation()
        pressMic()
        now += 1_000L
        lift()

        assertEquals(0, recorder.starts)
        assertTrue(sends.isEmpty())
        composeRule.onNodeWithTag("voice_recording_rail").assertDoesNotExist()
    }

    @Test
    fun rotationKeepsALockedRecordingPausedInItsCurtain() {
        showConversation()
        startHandsFree()

        composeRule.activityRule.scenario.recreate()
        // The recreated Activity starts empty; the controller is what a
        // ViewModel would have carried across.
        composeRule.runOnUiThread { composeRule.activity.setContent(content = conversation()) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("voice_locked_content").assertIsDisplayed()
        assertShowsPaused()
        assertTrue("rotation must not stop the recorder", recorder.isRecording)
        assertEquals(0, recorder.stops)
        assertEquals(0, recorder.cancels)
    }

    @Test
    fun leavingTheConversationFinalizesTheRecordingIntoReviewForLater() {
        var shown by mutableStateOf(true)
        showConversation(visible = { shown })
        startHandsFree()

        composeRule.runOnUiThread { shown = false }
        composeRule.waitForIdle()
        assertFalse("the mic is freed when the conversation is left", recorder.isRecording)
        assertEquals(1, recorder.stops)

        composeRule.runOnUiThread { shown = true }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("voice_review_content").assertIsDisplayed()
    }

    // --- helpers --------------------------------------------------------------------------

    /** Seen on screen, and heard: the status row is one merged TalkBack node with a state. */
    private fun assertShowsPaused() {
        composeRule.onNodeWithTag("voice_paused_label", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNode(hasStateDescription("Recording paused")).assertExists()
    }

    private fun pressMic() {
        composeRule.onNodeWithTag("voice_record_button").performTouchInput { down(center) }
        composeRule.waitForIdle()
    }

    private fun moveFinger(dxDp: Float = 0f, dyDp: Float = 0f) {
        val density = composeRule.density.density
        composeRule.onRoot().performTouchInput { moveBy(Offset(dxDp * density, dyDp * density)) }
        composeRule.waitForIdle()
    }

    /** Lifts wherever the finger is -- once locked, the mic it went down on is gone. */
    private fun lift() {
        composeRule.onRoot().performTouchInput { up() }
        composeRule.waitForIdle()
    }

    private fun startHandsFree() {
        composeRule.onNodeWithTag("voice_record_button").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("voice_locked_content").assertIsDisplayed()
    }

    private fun openReview() {
        startHandsFree()
        composeRule.onNodeWithTag("voice_pause_button").performClick()
        composeRule.onNodeWithTag("voice_review_button").performClick()
        composeRule.onNodeWithTag("voice_review_content").assertIsDisplayed()
    }

    private fun pressBack() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    private fun setMicPermission(granted: Boolean) {
        val app = Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>())
        if (granted) {
            app.grantPermissions(Manifest.permission.RECORD_AUDIO)
        } else {
            app.denyPermissions(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun showConversation(draft: RestoredDraft? = null, visible: () -> Boolean = { true }) {
        composeRule.setContent(conversation(draft, visible))
        composeRule.waitForIdle()
    }

    private fun conversation(
        draft: RestoredDraft? = null,
        visible: () -> Boolean = { true }
    ): @Composable () -> Unit {
        val theme = AppThemeState().apply {
            atmosphereMode = AtmosphereMode.MANUAL
            manualAtmosphere = TimeAtmospherePalette.DAY
        }
        return {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalAppThemeState provides theme
            ) {
                AetherTheme(themeState = theme) {
                    if (visible()) {
                        ConversationScreen(
                            chat = chat,
                            messages = messages,
                            canSend = true,
                            onBack = {},
                            onNavigateToProfile = {},
                            onSendMessage = { _, _, _, _ -> },
                            onSendVoiceNote = { path, durationSec, _, replyTo, onResult ->
                                sends += SentNote(path, durationSec, replyTo)
                                onResult(true)
                            },
                            onComposerChanged = {},
                            onLoadOlder = {},
                            onDeleteMessage = { _, _ -> },
                            onRetryMessage = {},
                            onVisibleMessages = {},
                            restoredDraft = draft,
                            voiceNoteController = controller
                        )
                    }
                }
            }
        }
    }

    private val user = User(
        id = "103",
        name = "Ishani Roy",
        username = "ishani",
        avatarInitials = "IR",
        avatarGradient = listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)),
        phone = "+1 555 0103",
        presence = Presence.ONLINE
    )

    private val chat = Chat(
        id = "103",
        title = "Ishani Roy",
        type = ChatType.DIRECT,
        lastMessageText = "See you at eight",
        lastMessageTime = "10:42 AM",
        avatarInitials = "IR",
        avatarGradient = listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)),
        directUser = user
    )

    private val messages = listOf(
        Message(
            id = "1",
            chatId = "103",
            senderId = "103",
            senderName = "Ishani Roy",
            text = "See you at eight",
            timestamp = "10:41 AM",
            isOutgoing = false,
            status = MessageStatus.SENT
        ),
        Message(
            id = "2",
            chatId = "103",
            senderId = "me",
            senderName = "You",
            text = "Perfect",
            timestamp = "10:42 AM",
            isOutgoing = true,
            status = MessageStatus.READ
        )
    )
}
