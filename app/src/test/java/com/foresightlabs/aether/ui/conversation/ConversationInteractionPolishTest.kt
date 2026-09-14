package com.foresightlabs.aether.ui.conversation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.domain.messages.MessageCapabilities
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.MessageStatus
import com.foresightlabs.aether.ui.theme.AetherTheme
import com.foresightlabs.aether.ui.theme.AppThemeState
import com.foresightlabs.aether.ui.theme.AtmosphereMode
import com.foresightlabs.aether.ui.theme.LocalAppThemeState
import com.foresightlabs.aether.ui.theme.TimeAtmospherePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The conversation's interaction polish: the selection dock's More entry into
 * the full message menu, the menu staying reachable on a short screen, the
 * failed-send retry, and the TalkBack path to reply without a swipe.
 *
 * Narrow phone geometry on purpose: a dock or menu that only fits a wide
 * test window is the bug these guard against.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi", application = Application::class)
class ConversationInteractionPolishTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val everything = MessageCapabilities(
        canBeReplied = true,
        canBeEdited = true,
        canBeCopied = true,
        canBeForwarded = true,
        canBePinned = true,
        canBeSaved = true,
        canGetLink = true,
        canGetReadDate = true,
        canBeDeletedOnlyForSelf = true,
        canBeDeletedForAllUsers = true
    )

    private fun message(
        id: String,
        status: MessageStatus = MessageStatus.SENT,
        isOutgoing: Boolean = false
    ) = Message(
        id = id,
        chatId = "103",
        senderId = "103",
        senderName = "Ishani Roy",
        text = "Here is the important information.",
        timestamp = "10:41 AM",
        isOutgoing = isOutgoing,
        status = status
    )

    private fun host(content: @Composable () -> Unit) {
        val appTheme = AppThemeState().apply {
            atmosphereMode = AtmosphereMode.MANUAL
            manualAtmosphere = TimeAtmospherePalette.DAY
        }
        composeRule.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalAppThemeState provides appTheme
            ) {
                AetherTheme(themeState = appTheme) { content() }
            }
        }
        composeRule.waitForIdle()
    }

    private fun showSelection(
        selection: List<Message>,
        capabilities: Map<String, MessageCapabilities>,
        onMore: ((Message) -> Unit)?
    ) = host {
        MessageComposer(
            replyingTo = null,
            onDismissReply = {},
            onSendMessage = { _, _ -> },
            selectedMessages = selection,
            capabilities = capabilities,
            onMoreSelected = onMore
        )
    }

    // --- selection dock: More -------------------------------------------------

    @Test
    fun moreIsOfferedForASingleSelectionAndTargetsExactlyIt() {
        val opened = mutableListOf<String>()
        showSelection(listOf(message("4242")), mapOf("4242" to everything)) { opened += it.id }

        composeRule.onNodeWithTag("selection_action_more").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("4242"), opened)
    }

    @Test
    fun moreIsNeverOfferedForAMultiSelection() {
        showSelection(
            listOf(message("1"), message("2")),
            mapOf("1" to everything, "2" to everything)
        ) { }
        composeRule.onAllNodesWithTag("selection_action_more").assertCountEquals(0)
    }

    @Test
    fun withoutAHandlerTheDockHasNoMore() {
        showSelection(listOf(message("1")), mapOf("1" to everything), onMore = null)
        composeRule.onAllNodesWithTag("selection_action_more").assertCountEquals(0)
    }

    @Test
    fun aFullDockOnANarrowPhoneKeepsEveryControlOnScreen() {
        showSelection(listOf(message("1")), mapOf("1" to everything)) { }
        for (tag in listOf(
            "selection_clear", "selection_count", "selection_action_edit",
            "selection_action_copy", "selection_action_forward", "selection_action_pin",
            "selection_action_more", "selection_action_delete"
        )) {
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
        }
    }

    // --- message menu stays reachable ------------------------------------------

    @Test
    @Config(qualifiers = "w360dp-h420dp-xhdpi")
    fun theMenuScrollsToItsLastActionOnAShortScreen() {
        host {
            MessageContextMenu(
                message = message("7", isOutgoing = true),
                capabilities = everything,
                isVisible = true,
                onDismiss = {},
                onReactionSelected = {},
                onAction = {}
            )
        }
        composeRule.onNodeWithTag("message_action_delete_for_everyone")
            .performScrollTo()
            .assertIsDisplayed()
    }

    // --- failed send -------------------------------------------------------------

    @Test
    fun aFailedMessageOffersOneRetryThatTargetsIt() {
        val retried = mutableListOf<String>()
        host {
            val context = LocalContext.current
            val playback = remember { AudioPlaybackController(context) }
            MessageBubble(
                message = message("55", status = MessageStatus.FAILED, isOutgoing = true),
                onSwipeToReply = {},
                onLongPress = {},
                onMediaClick = {},
                audioPlayback = playback,
                onReactionClick = { _, _ -> },
                onRetry = { retried += it.id }
            )
        }
        composeRule.onNodeWithTag("message_retry_55", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("55"), retried)
    }

    // --- accessibility -------------------------------------------------------------

    @Test
    fun talkBackCanReplyAndSelectWithoutAGesture() {
        val replied = mutableListOf<String>()
        val selected = mutableListOf<String>()
        host {
            val context = LocalContext.current
            val playback = remember { AudioPlaybackController(context) }
            MessageBubble(
                message = message("9"),
                onSwipeToReply = { replied += it.id },
                onLongPress = { selected += it.id },
                onMediaClick = {},
                audioPlayback = playback,
                onReactionClick = { _, _ -> }
            )
        }
        val actions = composeRule.onNodeWithTag("message_bubble_9")
            .fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
        val labels = actions.map { it.label }
        assertTrue("bubble must expose Reply and Select, had $labels", labels.containsAll(listOf("Reply", "Select")))

        composeRule.runOnIdle {
            actions.first { it.label == "Reply" }.action()
            actions.first { it.label == "Select" }.action()
        }

        assertEquals(listOf("9"), replied)
        assertEquals(listOf("9"), selected)
    }
}
