package com.foresightlabs.aether.ui.conversation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Conversation bottom region is one persistent rear Curtain, and these tests
 * are what keeps it that way.
 *
 * Attachments and Forwarding have each drifted back into being a panel, a card or
 * a bottom sheet more than once. The recurring shape of that mistake is always the
 * same: a feature mounts a second feature-sized surface of its own, rounds its own
 * top edge at the seam, and ends up in front of the conversation. So rather than
 * pinning padding values, these assert the invariants that would have caught it —
 * one surface root, the foreground in front of it, and the seam owned above.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi", application = Application::class)
class ConversationCurtainArchitectureTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** Roots that only ever existed because a feature owned its own surface. */
    private val legacyFeatureSurfaceTags = listOf(
        "attachment_sheet_content",
        "attachment_sheet_scrim",
        "forward_dock_content",
        "forward_target_sheet",
        "forward_panel"
    )

    // --- the whole screen ----------------------------------------------------

    @Test
    fun atRestThereIsExactlyOneCurtainAndTheComposerLivesInIt() {
        showConversation()

        composeRule.onAllNodesWithTag("conversation_curtain").assertCountEquals(1)
        composeRule.onNodeWithTag("message_composer").assertIsDisplayed()

        val curtain = boundsOf("conversation_curtain")
        val composer = boundsOf("message_composer")
        assertTrue(
            "The composer has to be content of the Curtain, not a sibling of it",
            composer.top >= curtain.top - 1f && composer.bottom <= curtain.bottom + 1f
        )
        assertNoLegacyFeatureSurfaces()
    }

    /**
     * The seam test, as a number: the conversation ends, and the Curtain begins
     * where it ended. A Curtain overlapping the foreground would mean it had risen
     * in front of the conversation, which is the failure this whole architecture
     * is about.
     */
    @Test
    fun theForegroundEndsExactlyWhereTheCurtainBeginsInEveryState() {
        showConversation()
        assertSeamIsClean("composer")

        openAttachments()
        assertSeamIsClean("attachments")
    }

    /**
     * A doubled bottom inset shows up as the Curtain floating clear of the
     * physical bottom edge, in whichever state introduced the second one.
     */
    @Test
    fun theCurtainStaysAnchoredToTheBottomEdgeInEveryState() {
        showConversation()
        val rootHeight = composeRule.onRoot().fetchSemanticsNode().size.height.toFloat()

        assertTrue(
            "At rest the Curtain left a gap under it",
            boundsOf("conversation_curtain").bottom >= rootHeight - 1f
        )

        openAttachments()
        assertTrue(
            "With attachments open the Curtain left a gap under it",
            boundsOf("conversation_curtain").bottom >= rootHeight - 1f
        )
    }

    /**
     * Opening attachments must expose more of the one Curtain, not mount a second
     * surface inside it — the "two dark layers" defect.
     */
    @Test
    fun attachmentsAreContentOfTheSameCurtainRatherThanASurfaceOfTheirOwn() {
        showConversation()
        val restingCurtain = boundsOf("conversation_curtain")

        openAttachments()

        composeRule.onAllNodesWithTag("conversation_curtain").assertCountEquals(1)
        composeRule.onNodeWithTag("curtain_attachment_content").assertIsDisplayed()
        assertNoLegacyFeatureSurfaces()

        val curtain = boundsOf("conversation_curtain")
        val options = boundsOf("curtain_attachment_content")
        assertTrue(
            "Attachment options must sit directly on the Curtain",
            options.top >= curtain.top - 1f && options.bottom <= curtain.bottom + 1f
        )
        assertTrue(
            "Opening attachments should expose more Curtain, not the same amount",
            curtain.height > restingCurtain.height + 1f
        )
    }

    /**
     * The transient expansion may change what is exposed now; it may not become
     * the resting geometry. Closing has to return the same composition, not a
     * conversation permanently retracted into a smaller card.
     */
    @Test
    fun expandingAndCollapsingTheCurtainRestoresTheRestingComposition() {
        showConversation()
        val restingCurtain = boundsOf("conversation_curtain")
        val restingForeground = boundsOf("conversation_foreground")

        openAttachments()
        composeRule.onNodeWithTag("attachment_button").performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag("conversation_curtain").assertCountEquals(1)
        val curtain = boundsOf("conversation_curtain")
        val foreground = boundsOf("conversation_foreground")
        assertTrue(
            "The Curtain settled at ${curtain.height}px instead of its resting " +
                "${restingCurtain.height}px",
            kotlin.math.abs(curtain.height - restingCurtain.height) <= 2f
        )
        assertTrue(
            "The conversation settled at ${foreground.height}px instead of its " +
                "resting ${restingForeground.height}px",
            kotlin.math.abs(foreground.height - restingForeground.height) <= 2f
        )
    }

    // --- the Curtain in isolation, across every state ------------------------

    @Test
    fun forwardingIsContentOfTheSameCurtainRatherThanASurfaceOfItsOwn() {
        val state = showCurtain(CurtainState.COMPOSER)

        composeRule.onAllNodesWithTag("conversation_curtain").assertCountEquals(1)
        val resting = boundsOf("conversation_curtain")

        composeRule.runOnUiThread { state.value = CurtainState.FORWARDING }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag("conversation_curtain").assertCountEquals(1)
        composeRule.onNodeWithTag("curtain_forward_content").assertIsDisplayed()
        assertNoLegacyFeatureSurfaces()

        val curtain = boundsOf("conversation_curtain")
        val content = boundsOf("curtain_forward_content")
        assertTrue(
            "Forward content must sit directly on the Curtain",
            content.top >= curtain.top - 1f && content.bottom <= curtain.bottom + 1f
        )
        assertTrue(
            "Forwarding should expose more of the Curtain than the composer does",
            curtain.height > resting.height + 1f
        )
        assertTrue(
            "The Curtain's lower edge must not move when a state opens",
            kotlin.math.abs(curtain.bottom - resting.bottom) <= 1f
        )
    }

    @Test
    fun everyStateTransitionReusesTheOneCurtainRoot() {
        val state = showCurtain(CurtainState.COMPOSER)

        listOf(
            CurtainState.ATTACHMENTS,
            CurtainState.COMPOSER,
            CurtainState.FORWARDING,
            CurtainState.COMPOSER,
            CurtainState.EMOJI,
            CurtainState.COMPOSER
        ).forEach { next ->
            composeRule.runOnUiThread { state.value = next }
            composeRule.waitForIdle()
            composeRule.onAllNodesWithTag("conversation_curtain").assertCountEquals(1)
            assertNoLegacyFeatureSurfaces()
        }
    }

    // --- helpers -------------------------------------------------------------

    private fun assertSeamIsClean(state: String) {
        val foreground = boundsOf("conversation_foreground")
        val curtain = boundsOf("conversation_curtain")
        assertTrue(
            "In the $state state the Curtain overlaps the conversation by " +
                "${foreground.bottom - curtain.top}px, so it is in front of it",
            foreground.bottom <= curtain.top + 1f
        )
        assertTrue(
            "In the $state state a ${curtain.top - foreground.bottom}px band of " +
                "neither layer shows through at the seam",
            curtain.top <= foreground.bottom + 1f
        )
    }

    private fun assertNoLegacyFeatureSurfaces() {
        legacyFeatureSurfaceTags.forEach { tag ->
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    private fun openAttachments() {
        composeRule.onNodeWithTag("attachment_button").performClick()
        composeRule.waitForIdle()
    }

    private fun boundsOf(tag: String): Rect =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun theme() = AppThemeState().apply {
        atmosphereMode = AtmosphereMode.MANUAL
        manualAtmosphere = TimeAtmospherePalette.DAY
    }

    private fun showConversation() {
        val theme = theme()
        composeRule.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalAppThemeState provides theme
            ) {
                AetherTheme(themeState = theme) {
                    ConversationScreen(
                        chat = chat,
                        messages = messages,
                        canSend = true,
                        onBack = {},
                        onNavigateToProfile = {},
                        onSendMessage = { _, _, _, _ -> },
                        onComposerChanged = {},
                        onLoadOlder = {},
                        onDeleteMessage = { _, _ -> },
                        onRetryMessage = {},
                        onVisibleMessages = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun showCurtain(initial: CurtainState): androidx.compose.runtime.MutableState<CurtainState> {
        val state = mutableStateOf(initial)
        val theme = theme()
        composeRule.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalAppThemeState provides theme
            ) {
                AetherTheme(themeState = theme) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        MessageComposer(
                            replyingTo = null,
                            onDismissReply = {},
                            onSendMessage = { _, _ -> },
                            curtainState = state.value,
                            forwardMessages = messages.take(1),
                            forwardTargets = listOf(chat),
                            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        return state
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
