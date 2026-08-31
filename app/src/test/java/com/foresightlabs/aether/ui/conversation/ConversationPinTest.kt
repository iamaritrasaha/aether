package com.foresightlabs.aether.ui.conversation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ChatType
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.MessageStatus
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
import java.io.File

/**
 * Verifies that the Conversation header pin button has been removed from
 * ConversationScreen, while Home chat pinning and ViewModel functionality remain.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi", application = Application::class)
class ConversationPinTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun chat(isPinned: Boolean) = Chat(
        id = "103",
        title = "Ishani Roy",
        type = ChatType.DIRECT,
        lastMessageText = "See you at eight",
        lastMessageTime = "10:42 AM",
        isPinned = isPinned,
        avatarInitials = "IR",
        avatarGradient = listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9))
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
        )
    )

    private fun theme() = AppThemeState().apply {
        atmosphereMode = AtmosphereMode.MANUAL
        manualAtmosphere = TimeAtmospherePalette.DAY
    }

    private fun showConversation(initiallyPinned: Boolean) {
        val chatState = mutableStateOf(chat(initiallyPinned))
        val appTheme = theme()
        composeRule.setContent {
            val current by chatState
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalAppThemeState provides appTheme
            ) {
                AetherTheme(themeState = appTheme) {
                    ConversationScreen(
                        chat = current,
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

    /** Resolves a module-relative source path regardless of the test task's working directory. */
    private fun resolveSource(relativePath: String): File =
        File(relativePath).takeIf { it.exists() } ?: File("app", relativePath)

    @Test
    fun conversationHeaderDoesNotShowPinButtonForUnpinnedChat() {
        showConversation(initiallyPinned = false)
        composeRule.onNodeWithTag("conversation_pin_button").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Pin conversation").assertDoesNotExist()
    }

    @Test
    fun conversationHeaderDoesNotShowPinButtonForPinnedChat() {
        showConversation(initiallyPinned = true)
        composeRule.onNodeWithTag("conversation_pin_button").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Unpin conversation").assertDoesNotExist()
    }

    @Test
    fun conversationHeaderRetainsSearchButton() {
        showConversation(initiallyPinned = false)
        composeRule.onNodeWithTag("conversation_search_button").assertExists()
    }

    @Test
    fun noPinButtonIsIntroducedInHeader() {
        showConversation(initiallyPinned = false)
        composeRule.onAllNodesWithTag("conversation_pin_button").assertCountEquals(0)
    }

    @Test
    fun conversationViewModelTogglesTheCurrentConversationsChatIdOnly() {
        val file = resolveSource("src/main/java/com/foresightlabs/aether/ui/conversation/ConversationViewModel.kt")
        val text = file.readText()

        val toggleFunction = text.substringAfter("fun toggleChatPinned()").substringBefore("\n    fun ")
        assertTrue(
            "toggleChatPinned must target activeChatId, this conversation's own resolved chat",
            "activeChatId" in toggleFunction
        )
        assertTrue(
            "toggleChatPinned must not read a Home-selected chat id",
            "selectedChatIds" !in toggleFunction && "ChatsViewModel" !in toggleFunction
        )
    }

    @Test
    fun conversationScreenSourceNeverInstantiatesTheTdlibPinRequestDirectly() {
        val conversationSources = listOf(
            "src/main/java/com/foresightlabs/aether/ui/conversation/ConversationScreen.kt",
            "src/main/java/com/foresightlabs/aether/ui/conversation/ConversationViewModel.kt"
        )
        conversationSources.forEach { relativePath ->
            val file = resolveSource(relativePath)
            assertTrue("expected to find $relativePath near the module or repo root", file.exists())
            val text = file.readText()
            assertTrue(
                "$relativePath must not construct TdApi.ToggleChatIsPinned directly",
                "ToggleChatIsPinned" !in text
            )
        }
    }
}
