package com.foresightlabs.aether.ui.conversation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
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
 * Regression tests for the adaptive multiline Composer Curtain.
 *
 * The Composer Curtain is content-responsive: as the draft gains lines the
 * Curtain grows upward, and when lines are deleted it shrinks back. After a
 * sensible maximum the text field scrolls internally. These tests verify
 * that measurement path end-to-end, without asserting fragile pixel values.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi", application = Application::class)
class ComposerMultilineTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // -----------------------------------------------------------------------
    // 1. Empty / single-line Composer has compact height
    // -----------------------------------------------------------------------

    @Test
    fun emptyComposerHasCompactHeight() {
        showComposer()

        val curtain = boundsOf("conversation_curtain")
        val composer = boundsOf("message_composer")

        // The composer should be displayed and have a reasonable compact height.
        composeRule.onNodeWithTag("message_composer").assertIsDisplayed()
        assertTrue(
            "Compact composer should have a modest height, was ${composer.height}px",
            composer.height in 30f..200f
        )
        assertTrue(
            "The composer must sit inside the Curtain",
            composer.top >= curtain.top - 1f && composer.bottom <= curtain.bottom + 1f
        )
    }

    // -----------------------------------------------------------------------
    // 2. A wrapped multiline draft makes the same Curtain root taller
    // -----------------------------------------------------------------------

    @Test
    fun multilineDraftMakesCurtainTaller() {
        showComposer()

        val compactCurtainHeight = boundsOf("conversation_curtain").height

        // Type several lines of text.
        composeRule.onNodeWithTag("message_input_field")
            .performTextInput("Line one\nLine two\nLine three")
        composeRule.waitForIdle()

        val expandedCurtainHeight = boundsOf("conversation_curtain").height
        assertTrue(
            "A 3-line draft should make the Curtain taller than compact " +
                "(compact=$compactCurtainHeight, expanded=$expandedCurtainHeight)",
            expandedCurtainHeight > compactCurtainHeight + 1f
        )
    }

    // -----------------------------------------------------------------------
    // 3. 3-4 line draft remains fully visible (not clipped by foreground)
    // -----------------------------------------------------------------------

    @Test
    fun threeLineDraftIsFullyVisibleWithinCurtain() {
        showComposer()

        composeRule.onNodeWithTag("message_input_field")
            .performTextInput("First line\nSecond line\nThird line")
        composeRule.waitForIdle()

        val curtain = boundsOf("conversation_curtain")
        val textField = boundsOf("message_input_field")
        assertTrue(
            "The text field top (${textField.top}) must be at or below the Curtain " +
                "top (${curtain.top})",
            textField.top >= curtain.top - 1f
        )
        assertTrue(
            "The text field bottom (${textField.bottom}) must be at or above the " +
                "Curtain bottom (${curtain.bottom})",
            textField.bottom <= curtain.bottom + 1f
        )
    }

    // -----------------------------------------------------------------------
    // 4. Very long draft stops growing after the defined maximum
    // -----------------------------------------------------------------------

    @Test
    fun veryLongDraftStopsGrowingAtMaximum() {
        showComposer()

        // Type 6 lines (which reaches maxLines = 6).
        composeRule.onNodeWithTag("message_input_field")
            .performTextInput("Line 1\nLine 2\nLine 3\nLine 4\nLine 5\nLine 6")
        composeRule.waitForIdle()
        val sixLineCurtain = boundsOf("conversation_curtain").height

        // Now append more lines well beyond maxLines (12 lines total).
        composeRule.onNodeWithTag("message_input_field")
            .performTextInput("\nLine 7\nLine 8\nLine 9\nLine 10\nLine 11\nLine 12")
        composeRule.waitForIdle()
        val twelveLineCurtain = boundsOf("conversation_curtain").height

        // The Curtain must not grow beyond maxLines.
        assertTrue(
            "Draft beyond maxLines should not grow further (6 lines=$sixLineCurtain, 12 lines=$twelveLineCurtain)",
            kotlin.math.abs(twelveLineCurtain - sixLineCurtain) <= 1f
        )
    }

    // -----------------------------------------------------------------------
    // 5. Long draft text field is scrollable internally
    // -----------------------------------------------------------------------

    @Test
    fun longDraftTextFieldRemainsAccessible() {
        showComposer()

        // Type beyond maxLines.
        composeRule.onNodeWithTag("message_input_field")
            .performTextInput(
                "Line A\nLine B\nLine C\nLine D\nLine E\n" +
                    "Line F\nLine G\nLine H"
            )
        composeRule.waitForIdle()

        // The text field should still be displayed and accessible.
        composeRule.onNodeWithTag("message_input_field").assertIsDisplayed()

        // The text field bounds should remain within the curtain.
        val curtain = boundsOf("conversation_curtain")
        val field = boundsOf("message_input_field")
        assertTrue(
            "Text field must remain within the Curtain even with many lines",
            field.top >= curtain.top - 1f && field.bottom <= curtain.bottom + 1f
        )
    }

    // -----------------------------------------------------------------------
    // 6. Deleting content shrinks the Composer again
    // -----------------------------------------------------------------------

    @Test
    fun deletingContentShrinksComposerBackToCompact() {
        showComposer()

        val compactHeight = boundsOf("conversation_curtain").height

        // Grow it.
        composeRule.onNodeWithTag("message_input_field")
            .performTextInput("Line 1\nLine 2\nLine 3\nLine 4")
        composeRule.waitForIdle()
        val expandedHeight = boundsOf("conversation_curtain").height
        assertTrue("Should have grown", expandedHeight > compactHeight + 1f)

        // Clear all text.
        composeRule.onNodeWithTag("message_input_field").performTextClearance()
        composeRule.waitForIdle()

        val afterClearHeight = boundsOf("conversation_curtain").height
        assertTrue(
            "After clearing, the Curtain ($afterClearHeight) should return close " +
                "to compact ($compactHeight)",
            kotlin.math.abs(afterClearHeight - compactHeight) <= 3f
        )
    }

    // -----------------------------------------------------------------------
    // 7. Reply/Edit + multiline draft do not clip
    // -----------------------------------------------------------------------

    @Test
    fun replyPlusMultilineDraftBothFitWithinCurtain() {
        val replyMsg = messages.first()
        showComposerWithReply(replyMsg)

        // Type multiline text.
        composeRule.onNodeWithTag("message_input_field")
            .performTextInput("Reply line 1\nReply line 2\nReply line 3")
        composeRule.waitForIdle()

        val curtain = boundsOf("conversation_curtain")
        val composer = boundsOf("message_composer")
        val field = boundsOf("message_input_field")

        // Both the composer (which includes the reply strip) and the text field
        // must be fully inside the curtain.
        assertTrue(
            "The composer (including reply strip) must fit within the Curtain. " +
                "Composer top=${composer.top}, Curtain top=${curtain.top}",
            composer.top >= curtain.top - 1f
        )
        assertTrue(
            "The text field must fit within the Curtain. " +
                "Field bottom=${field.bottom}, Curtain bottom=${curtain.bottom}",
            field.bottom <= curtain.bottom + 1f
        )
    }

    // -----------------------------------------------------------------------
    // 8. The same conversation_curtain root remains present throughout
    // -----------------------------------------------------------------------

    @Test
    fun singleCurtainRootThroughoutMultilineLifecycle() {
        showComposer()

        // Start: compact.
        composeRule.onAllNodesWithTag("conversation_curtain").assertCountEquals(1)

        // Type multiline.
        composeRule.onNodeWithTag("message_input_field")
            .performTextInput("Line 1\nLine 2\nLine 3")
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("conversation_curtain").assertCountEquals(1)

        // Type even more.
        composeRule.onNodeWithTag("message_input_field")
            .performTextInput("\nLine 4\nLine 5\nLine 6\nLine 7")
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("conversation_curtain").assertCountEquals(1)

        // Clear back to empty.
        composeRule.onNodeWithTag("message_input_field").performTextClearance()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("conversation_curtain").assertCountEquals(1)
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun boundsOf(tag: String): Rect =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot

    private fun theme() = AppThemeState().apply {
        atmosphereMode = AtmosphereMode.MANUAL
        manualAtmosphere = TimeAtmospherePalette.DAY
    }

    private fun showComposer() {
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
                            curtainState = CurtainState.COMPOSER,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun showComposerWithReply(replyMsg: Message) {
        val theme = theme()
        composeRule.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalAppThemeState provides theme
            ) {
                AetherTheme(themeState = theme) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        MessageComposer(
                            replyingTo = replyMsg,
                            onDismissReply = {},
                            onSendMessage = { _, _ -> },
                            curtainState = CurtainState.COMPOSER,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private val user = User(
        id = "200",
        name = "Test User",
        username = "testuser",
        avatarInitials = "TU",
        avatarGradient = listOf(Color(0xFF4F46E5), Color(0xFF7C3AED)),
        phone = "+1 555 0200",
        presence = Presence.ONLINE
    )

    private val chat = Chat(
        id = "200",
        title = "Test User",
        type = ChatType.DIRECT,
        lastMessageText = "Hello",
        lastMessageTime = "3:00 PM",
        avatarInitials = "TU",
        avatarGradient = listOf(Color(0xFF4F46E5), Color(0xFF7C3AED)),
        directUser = user
    )

    private val messages = listOf(
        Message(
            id = "1",
            chatId = "200",
            senderId = "200",
            senderName = "Test User",
            text = "Hello there",
            timestamp = "3:00 PM",
            isOutgoing = false,
            status = MessageStatus.SENT
        ),
        Message(
            id = "2",
            chatId = "200",
            senderId = "me",
            senderName = "You",
            text = "Hi!",
            timestamp = "3:01 PM",
            isOutgoing = true,
            status = MessageStatus.READ
        )
    )
}
