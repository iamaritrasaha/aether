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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ChatType
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.MessageStatus
import com.foresightlabs.aether.domain.model.Presence
import com.foresightlabs.aether.domain.model.User
import com.foresightlabs.aether.domain.text.ComposerLinkPreviewState
import com.foresightlabs.aether.domain.text.LinkPreviewCard
import com.foresightlabs.aether.ui.theme.AetherTheme
import com.foresightlabs.aether.ui.theme.AppThemeState
import com.foresightlabs.aether.ui.theme.AtmosphereMode
import com.foresightlabs.aether.ui.theme.TimeAtmospherePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The link preview as Composer content.
 *
 * These assert the product rules the preview had to honour: it lives inside the
 * one Conversation Curtain rather than on a surface of its own, dismissing it
 * leaves the draft alone, and nothing it does costs the composer its multiline
 * behaviour, its reply and edit strips, or a reachable send button.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi", application = Application::class)
class ComposerLinkPreviewUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun aResolvedPreviewShowsInsideTheOneCurtain() {
        showComposer(preview = resolved())

        composeRule.onNodeWithTag(ComposerLinkPreviewTags.Strip).assertIsDisplayed()
        composeRule.onNodeWithTag(ComposerLinkPreviewTags.Title).assertIsDisplayed()
        // One Curtain, still: the preview is content in it, not a second surface.
        composeRule.onAllNodesWithTag(AetherCurtain.TestTag).assertCountEquals(1)

        val curtain = boundsOf(AetherCurtain.TestTag)
        val strip = boundsOf(ComposerLinkPreviewTags.Strip)
        assertTrue(
            "The preview must sit inside the Curtain (strip=$strip, curtain=$curtain)",
            strip.top >= curtain.top - 1f && strip.bottom <= curtain.bottom + 1f
        )
        val input = boundsOf("message_input_field")
        assertTrue(
            "The preview belongs above the text it describes",
            strip.bottom <= input.bottom + 1f
        )
    }

    @Test
    fun loadingIsSubtleAndCarriesNoCard() {
        showComposer(preview = ComposerLinkPreviewState(url = "https://example.com", isLoading = true))

        composeRule.onNodeWithTag(ComposerLinkPreviewTags.Loading).assertIsDisplayed()
        composeRule.onAllNodesWithTag(ComposerLinkPreviewTags.Title).assertCountEquals(0)
        composeRule.onAllNodesWithTag(ComposerLinkPreviewTags.Thumbnail).assertCountEquals(0)
        composeRule.onAllNodesWithTag(AetherCurtain.TestTag).assertCountEquals(1)
    }

    @Test
    fun anUnavailablePreviewShowsNothingAtAll() {
        showComposer(preview = ComposerLinkPreviewState(url = "https://example.com"))

        composeRule.onAllNodesWithTag(ComposerLinkPreviewTags.Strip).assertCountEquals(0)
        composeRule.onNodeWithTag("message_input_field").assertIsDisplayed()
    }

    @Test
    fun dismissingLeavesTheUrlInTheDraft() {
        var dismissals = 0
        var state by mutableStateOf(resolved())
        showComposer(previewProvider = { state }, onDismiss = {
            dismissals++
            state = ComposerLinkPreviewState(dismissedUrl = state.url)
        })

        composeRule.onNodeWithTag("message_input_field").performTextInput("read https://example.com")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ComposerLinkPreviewTags.Dismiss).performClick()
        composeRule.waitForIdle()

        assertEquals(1, dismissals)
        composeRule.onAllNodesWithTag(ComposerLinkPreviewTags.Strip).assertCountEquals(0)
        assertEquals(
            "Dismissing removes the preview, never the link",
            "read https://example.com",
            typedText()
        )
    }

    @Test
    fun replyingStillWorksWithAPreviewShowing() {
        showComposer(preview = resolved(), replyingTo = message)

        composeRule.onNodeWithTag(ComposerLinkPreviewTags.Strip).assertIsDisplayed()
        composeRule.onNodeWithTag("message_input_field").assertIsDisplayed()
        composeRule.onAllNodesWithTag(AetherCurtain.TestTag).assertCountEquals(1)

        val curtain = boundsOf(AetherCurtain.TestTag)
        val composer = boundsOf("message_composer")
        assertTrue(
            "Reply strip and preview together must still fit the Curtain",
            composer.top >= curtain.top - 1f && composer.bottom <= curtain.bottom + 1f
        )
    }

    @Test
    fun editingStillWorksWithAPreviewShowing() {
        showComposer(preview = resolved(), editing = message)

        composeRule.onNodeWithTag(ComposerLinkPreviewTags.Strip).assertIsDisplayed()
        composeRule.onNodeWithTag("save_edit_button").assertIsDisplayed()
    }

    @Test
    fun theMultilineComposerIsUnchangedByAPreview() {
        showComposer(preview = resolved())

        composeRule.onNodeWithTag("message_input_field")
            .performTextInput("https://example.com\nLine 2\nLine 3\nLine 4\nLine 5\nLine 6")
        composeRule.waitForIdle()
        val sixLines = boundsOf(AetherCurtain.TestTag).height

        composeRule.onNodeWithTag("message_input_field")
            .performTextInput("\nLine 7\nLine 8\nLine 9\nLine 10\nLine 11\nLine 12")
        composeRule.waitForIdle()
        val twelveLines = boundsOf(AetherCurtain.TestTag).height

        assertTrue(
            "The six-line cap must still hold with a preview showing " +
                "(6=$sixLines, 12=$twelveLines)",
            kotlin.math.abs(twelveLines - sixLines) <= 1f
        )
        val curtain = boundsOf(AetherCurtain.TestTag)
        val field = boundsOf("message_input_field")
        assertTrue(
            "The field must stay inside the Curtain",
            field.top >= curtain.top - 1f && field.bottom <= curtain.bottom + 1f
        )
    }

    @Test
    fun theSendButtonStaysReachable() {
        var sent: String? = null
        showComposer(preview = resolved(), onSend = { text -> sent = text })

        composeRule.onNodeWithTag("message_input_field").performTextInput("read https://example.com")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("send_message_button").assertIsDisplayed()
        val send = boundsOf("send_message_button")
        val curtain = boundsOf(AetherCurtain.TestTag)
        assertTrue(
            "Send must remain inside the Curtain and fully visible",
            send.top >= curtain.top - 1f && send.bottom <= curtain.bottom + 1f
        )

        composeRule.onNodeWithTag("send_message_button").performClick()
        composeRule.waitForIdle()
        // The preview does not come between the draft and sending it; what the
        // send then tells Telegram is asserted in the send-path tests.
        assertEquals("read https://example.com", sent)
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun boundsOf(tag: String): Rect =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot

    private fun typedText(): String =
        composeRule.onNodeWithTag("message_input_field", useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.EditableText]
            .text

    private fun resolved() = ComposerLinkPreviewState(
        url = "https://example.com",
        card = LinkPreviewCard(
            url = "https://example.com",
            displayUrl = "example.com",
            siteName = "Example",
            title = "Example title",
            description = "A short description"
        )
    )

    private fun theme() = AppThemeState().apply {
        atmosphereMode = AtmosphereMode.MANUAL
        manualAtmosphere = TimeAtmospherePalette.DAY
    }

    private fun showComposer(
        preview: ComposerLinkPreviewState = ComposerLinkPreviewState.Empty,
        previewProvider: (() -> ComposerLinkPreviewState)? = null,
        onDismiss: () -> Unit = {},
        replyingTo: Message? = null,
        editing: Message? = null,
        onSend: (String) -> Unit = {}
    ) {
        val theme = theme()
        composeRule.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                com.foresightlabs.aether.ui.theme.LocalAppThemeState provides theme
            ) {
                AetherTheme(themeState = theme) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        MessageComposer(
                            replyingTo = replyingTo,
                            onDismissReply = {},
                            onSendMessage = { text, _ -> onSend(text) },
                            editingMessage = editing,
                            curtainState = CurtainState.COMPOSER,
                            linkPreview = previewProvider?.invoke() ?: preview,
                            onDismissLinkPreview = onDismiss,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private val user = User(
        id = "300",
        name = "Preview User",
        username = "previewuser",
        avatarInitials = "PU",
        avatarGradient = listOf(Color(0xFF4F46E5), Color(0xFF7C3AED)),
        phone = "+1 555 0300",
        presence = Presence.ONLINE
    )

    private val chat = Chat(
        id = "300",
        title = "Preview User",
        type = ChatType.DIRECT,
        lastMessageText = "Hello",
        lastMessageTime = "3:00 PM",
        avatarInitials = "PU",
        avatarGradient = listOf(Color(0xFF4F46E5), Color(0xFF7C3AED)),
        directUser = user
    )

    private val message = Message(
        id = "1",
        chatId = chat.id,
        senderId = user.id,
        senderName = user.name,
        text = "Have a look at this",
        timestamp = "3:00 PM",
        isOutgoing = false,
        status = MessageStatus.READ
    )
}
