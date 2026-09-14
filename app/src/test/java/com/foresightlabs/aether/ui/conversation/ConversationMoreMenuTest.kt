package com.foresightlabs.aether.ui.conversation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ChatType
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.search.ConversationSearchState
import com.foresightlabs.aether.ui.theme.AetherTheme
import com.foresightlabs.aether.ui.theme.AppThemeState
import com.foresightlabs.aether.ui.theme.AtmosphereMode
import com.foresightlabs.aether.ui.theme.LocalAppThemeState
import com.foresightlabs.aether.ui.theme.TimeAtmospherePalette
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The whole path a single message takes to its full menu, on the real screen:
 * long-press selects it, the dock's More opens the menu. Composer-level tests
 * prove the dock fires its callback; this proves the screen answers it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi", application = Application::class)
class ConversationMoreMenuTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val chat = Chat(
        id = "103",
        title = "Ishani Roy",
        type = ChatType.DIRECT,
        lastMessageText = "Are we still on?",
        lastMessageTime = "10:42 AM",
        avatarInitials = "IR",
        avatarGradient = listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9))
    )

    private val message = Message(
        id = "1",
        chatId = "103",
        senderId = "103",
        senderName = "Ishani Roy",
        text = "Are we still doing the gallery review at 3pm?",
        timestamp = "10:30 AM",
        isOutgoing = false
    )

    @Test
    fun moreFromTheSelectionDockOpensATappableMessageMenu() {
        val bookmarked = mutableListOf<String>()
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
                        messages = listOf(message),
                        canSend = true,
                        onBack = {},
                        onNavigateToProfile = {},
                        onSendMessage = { _, _, _, _ -> },
                        onComposerChanged = {},
                        onLoadOlder = {},
                        onDeleteMessage = { _, _ -> },
                        onRetryMessage = {},
                        onVisibleMessages = {},
                        searchState = ConversationSearchState.Idle,
                        onToggleBookmark = { bookmarked += it.id }
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("message_bubble_1").performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("selection_action_more").performClick()
        composeRule.waitForIdle()

        // Existing is not enough: the menu once composed UNDER the canvas (no
        // z-index), present in the tree but invisible. A real tap is routed to
        // whatever is drawn on top, so it only reaches Bookmark when the menu
        // actually is.
        composeRule.onNodeWithTag("message_context_scrim").assertExists()
        composeRule.onNodeWithTag("message_action_bookmark", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("1"), bookmarked)
    }
}
