package com.foresightlabs.aether.ui.conversation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Pin/Unpin the whole conversation, from inside Conversation itself.
 *
 * The canonical operation and the canonical [Chat.isPinned] state are Home's --
 * see [com.foresightlabs.aether.ui.home.HomeSelectionDockTest] and
 * [com.foresightlabs.aether.data.telegram.TelegramMappingTest] for that half.
 * These tests cover only what Conversation adds: a button that reads the same
 * [Chat.isPinned] Home reads and invokes the same operation Home invokes,
 * without becoming a second surface -- exactly one Curtain, no dialog, no
 * sheet, no locally-invented pinned flag.
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

    /** Renders Conversation for a chat whose pinned state can be flipped live, mid-test. */
    private fun showConversation(initiallyPinned: Boolean): Pair<androidx.compose.runtime.MutableState<Chat>, MutableList<Unit>> {
        val chatState = mutableStateOf(chat(initiallyPinned))
        val toggleCalls = mutableListOf<Unit>()
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
                        onTogglePin = {
                            toggleCalls.add(Unit)
                            chatState.value = chatState.value.copy(isPinned = !chatState.value.isPinned)
                        },
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
        return chatState to toggleCalls
    }

    private fun boundsOf(tag: String): Rect =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    /** Resolves a module-relative source path regardless of the test task's working directory. */
    private fun resolveSource(relativePath: String): File =
        File(relativePath).takeIf { it.exists() } ?: File("app", relativePath)

    // --- 1 & 2: state drives the label -----------------------------------------

    @Test
    fun conversationShowsPinForAnUnpinnedChat() {
        showConversation(initiallyPinned = false)
        composeRule.onNodeWithContentDescription("Pin conversation").assertExists()
    }

    @Test
    fun conversationShowsUnpinForAPinnedChat() {
        showConversation(initiallyPinned = true)
        composeRule.onNodeWithContentDescription("Unpin conversation").assertExists()
    }

    // --- 3 & 4: tapping invokes the canonical operation exactly once -----------

    @Test
    fun tappingPinInvokesTheToggleOperationOnAnUnpinnedChat() {
        val (_, calls) = showConversation(initiallyPinned = false)

        composeRule.onNodeWithTag("conversation_pin_button").performClick()
        composeRule.waitForIdle()

        assertEquals(1, calls.size)
    }

    @Test
    fun tappingUnpinInvokesTheToggleOperationOnAPinnedChat() {
        val (_, calls) = showConversation(initiallyPinned = true)

        composeRule.onNodeWithTag("conversation_pin_button").performClick()
        composeRule.waitForIdle()

        assertEquals(1, calls.size)
    }

    // --- 5: the button reflects authoritative state, not a local flag ----------

    @Test
    fun conversationObservesThePinnedStateChangingUnderIt() {
        // Simulates what happens once Telegram's own UpdateChatPosition lands:
        // the chat passed in changes, and the button must follow it -- exactly
        // as it would if fed by ConversationViewModel's `header`, which is
        // itself fed by the same telegram.chatList Home reads.
        val (chatState, _) = showConversation(initiallyPinned = false)
        composeRule.onNodeWithContentDescription("Pin conversation").assertExists()

        composeRule.runOnUiThread { chatState.value = chatState.value.copy(isPinned = true) }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Unpin conversation").assertExists()
    }

    @Test
    fun tappingPinTogglesTheDisplayedStateThroughTheSameChatObject() {
        // The button never keeps its own pinned flag: this exercises the full
        // loop (tap -> onTogglePin -> chat.isPinned changes -> button updates)
        // the same way a real ConversationViewModel.header update would.
        val (chatState, _) = showConversation(initiallyPinned = false)

        composeRule.onNodeWithTag("conversation_pin_button").performClick()
        composeRule.waitForIdle()

        assertTrue(chatState.value.isPinned)
        composeRule.onNodeWithContentDescription("Unpin conversation").assertExists()
    }

    // --- 7: a refused operation must not fake a permanent state change ---------

    @Test
    fun aRefusedToggleLeavesTheDisplayedStateUnchanged() {
        // onTogglePin that never mutates the chat -- standing in for a failed
        // TelegramClient.setChatPinned result -- must not move the button off
        // its starting label. The button has no local pinned flag of its own
        // to have drifted in the first place.
        val chatState = mutableStateOf(chat(isPinned = false))
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
                        onTogglePin = { /* refused: chat state intentionally left untouched */ },
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

        composeRule.onNodeWithTag("conversation_pin_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Pin conversation").assertExists()
    }

    // --- 8 & 9: compact, matches the header's existing controls, not giant -----

    @Test
    fun thePinControlIsTheSameSizeAsTheExistingSearchControl() {
        // "Compact and consistent with the existing header controls, not a
        // giant surface" as a measurement rather than an opinion: the pin
        // button's touch target and glass circle must match the search
        // button's exactly, since both are built from the same geometry.
        showConversation(initiallyPinned = false)

        val pinBounds = boundsOf("conversation_pin_button")
        val searchBounds = boundsOf("conversation_search_button")

        assertEquals(
            "pin button touch target must match the search button's, not dominate the header",
            searchBounds.width, pinBounds.width, 0.5f
        )
        assertEquals(searchBounds.height, pinBounds.height, 0.5f)

        // Neither approaches even a modest fraction of the header's own width --
        // a "giant" control would visibly break this bound.
        val header = boundsOf("conversation_header_profile")
        assertTrue(
            "pin button (${pinBounds.width}px) must stay a small fraction of the header (${header.width}px)",
            pinBounds.width < header.width * 0.35f
        )
    }

    // --- 9 & 10: additive only, one Curtain, no new surface ---------------------

    @Test
    fun noSecondCurtainOrNewSurfaceIsIntroducedByThePinButton() {
        showConversation(initiallyPinned = false)
        composeRule.onAllNodesWithTag("conversation_curtain").assertCountEquals(1)
        composeRule.onAllNodesWithTag("conversation_pin_button").assertCountEquals(1)
    }

    // --- 5 & 6: the operation targets the current chat, never a Home selection --

    @Test
    fun conversationViewModelTogglesTheCurrentConversationsChatIdOnly() {
        // Structural guard for "current chatId is authoritative, no Home
        // selection state substitutes for it": toggleChatPinned must read
        // activeChatId (this screen's own resolved target) and must never
        // reference Home's selection state or ChatsViewModel.
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
        // Structural guard for "no duplicate pinning implementation": the one
        // place allowed to build TdApi.ToggleChatIsPinned is TelegramClient.
        val conversationSources = listOf(
            "src/main/java/com/foresightlabs/aether/ui/conversation/ConversationScreen.kt",
            "src/main/java/com/foresightlabs/aether/ui/conversation/ConversationViewModel.kt"
        )
        conversationSources.forEach { relativePath ->
            val file = resolveSource(relativePath)
            assertTrue("expected to find $relativePath near the module or repo root", file.exists())
            val text = file.readText()
            assertTrue(
                "$relativePath must not construct TdApi.ToggleChatIsPinned directly -- " +
                    "it must call TelegramClient.setChatPinned like Home does",
                "ToggleChatIsPinned" !in text
            )
        }
    }
}
