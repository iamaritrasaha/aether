package com.foresightlabs.aether.ui.conversation
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ChatType
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.MessageStatus
import com.foresightlabs.aether.domain.model.Presence
import com.foresightlabs.aether.domain.model.User
import com.foresightlabs.aether.ui.conversation.ConversationScreen
import com.foresightlabs.aether.ui.theme.AetherTheme
import com.foresightlabs.aether.ui.theme.AppThemeState
import com.foresightlabs.aether.ui.theme.AtmosphereMode
import com.foresightlabs.aether.ui.theme.LocalAppThemeState
import com.foresightlabs.aether.ui.theme.TimeAtmospherePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The frosted identity header is mounted to the screen, not to the message
 * list — it must hold the exact same screen coordinates whether the list is
 * resting or mid-scroll, and the list's own layout bounds must never be
 * shrunk to start below it. Shrinking the list's bounds (rather than only
 * insetting its *content*) is what silently defeats "messages pass behind
 * the glass": a scrollable clips to its own bounds, so a list which starts
 * below the header can never draw anything behind it, scroll position aside.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi", application = Application::class)
class ConversationHeaderPinningTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() {
        val theme = AppThemeState().apply {
            atmosphereMode = AtmosphereMode.MANUAL
            manualAtmosphere = TimeAtmospherePalette.DAY
        }
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

    /**
     * The list's own layout box — not its content padding, its actual measured
     * bounds — must span the same top edge as the canvas it sits in. If it
     * were shrunk to start below the header (the old `Modifier.padding(top=…)`
     * bug), messages could never be drawn behind the glass at any scroll
     * position, because a scrollable clips to its own bounds.
     */
    @Test
    fun theMessageListsOwnBoundsAreNotShrunkToStartBelowTheHeader() {
        val canvas = boundsOf("conversation_foreground")
        val list = boundsOf("conversation_message_list")

        assertTrue(
            "The message list's own top is ${list.top}px, ${list.top - canvas.top}px " +
                "below the canvas it should fill — its bounds have been shrunk to " +
                "start below the header instead of only insetting its content",
            list.top <= canvas.top + 1f
        )
    }

    @Test
    fun theHeaderStaysAtTheSameScreenPositionWhileTheListScrolls() {
        val before = boundsOf("conversation_header_profile")

        composeRule.onNodeWithTag("conversation_message_list")
            .performScrollToIndex(messages.size - 1)
        composeRule.waitForIdle()

        val afterScrollingToTheEnd = boundsOf("conversation_header_profile")
        assertEquals(
            "The header moved after scrolling to the newest message",
            before, afterScrollingToTheEnd
        )

        composeRule.onNodeWithTag("conversation_message_list")
            .performScrollToIndex(0)
        composeRule.waitForIdle()

        val afterScrollingBack = boundsOf("conversation_header_profile")
        assertEquals(
            "The header moved after scrolling back to the oldest message",
            before, afterScrollingBack
        )
    }

    @Test
    fun theHeaderIsNotAnItemInTheMessageList() {
        val header = composeRule.onNodeWithTag("conversation_header_profile", useUnmergedTree = true)
            .fetchSemanticsNode()
        var node = header.parent
        while (node != null) {
            assertTrue(
                "The header is nested inside conversation_message_list — it must " +
                    "be a fixed sibling of the list, never one of its items",
                node.config.getOrNull(SemanticsProperties.TestTag) != "conversation_message_list"
            )
            node = node.parent
        }
    }

    // --- helpers -----------------------------------------------------------

    private fun boundsOf(tag: String): Rect =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private val chat = Chat(
        id = "103",
        title = "Ishani Roy",
        type = ChatType.DIRECT,
        lastMessageText = "Let's review the new studio proofs together.",
        lastMessageTime = "10:42 AM",
        avatarInitials = "IR",
        avatarGradient = listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)),
        directUser = User(
            id = "103",
            name = "Ishani Roy",
            username = "ishani",
            avatarInitials = "IR",
            avatarGradient = listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)),
            phone = "+1 555 0103",
            presence = Presence.ONLINE
        )
    )

    private val messages = (1..60).map { n ->
        Message(
            id = n.toString(),
            chatId = "103",
            senderId = if (n % 2 == 0) "me" else "103",
            senderName = if (n % 2 == 0) "You" else "Ishani Roy",
            text = "Message number $n in a long scrolling conversation.",
            timestamp = "10:${(n % 60).toString().padStart(2, '0')} AM",
            isOutgoing = n % 2 == 0,
            status = MessageStatus.READ
        )
    }
}
