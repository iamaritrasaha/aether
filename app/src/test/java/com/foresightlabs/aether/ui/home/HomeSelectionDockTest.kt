package com.foresightlabs.aether.ui.home

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.domain.chats.ChatAction
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ChatType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pin/Unpin is an action of the existing Home chat-selection dock, not a new
 * surface -- these tests are what keeps that true: exactly one dock, gated on
 * the selected chat's real (TDLib-reported) [Chat.isPinned], visible only for
 * a single selection because [org.drinkless.tdlib.TdApi.ToggleChatIsPinned]
 * itself only ever targets one chat.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi", application = Application::class)
class HomeSelectionDockTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun chat(id: String, isPinned: Boolean = false) = Chat(
        id = id,
        title = "Chat $id",
        type = ChatType.DIRECT,
        lastMessageText = "hi",
        lastMessageTime = "12:00",
        isPinned = isPinned,
        avatarInitials = "C",
        avatarGradient = listOf(Color.Red, Color.Blue)
    )

    private data class RecordedAction(val chat: Chat, val action: ChatAction)

    private fun show(selectedChats: List<Chat>): MutableList<RecordedAction> {
        val recorded = mutableListOf<RecordedAction>()
        composeRule.setContent {
            HomeSelectionDock(
                selectedChats = selectedChats,
                onClearSelection = {},
                onChatAction = { chat, action -> recorded.add(RecordedAction(chat, action)) }
            )
        }
        composeRule.waitForIdle()
        return recorded
    }

    // --- visibility: exactly one selection ------------------------------------

    @Test
    fun unpinnedSingleSelectionExposesPin() {
        show(listOf(chat("1", isPinned = false)))
        composeRule.onNodeWithTag("home_selection_action_pin").assertExists()
    }

    @Test
    fun pinnedSingleSelectionExposesUnpin() {
        show(listOf(chat("1", isPinned = true)))
        composeRule.onNodeWithTag("home_selection_action_pin").assertExists()
    }

    @Test
    fun multiSelectionHidesThePinAction() {
        // TdApi.ToggleChatIsPinned targets exactly one chatId; there is no safe
        // bulk pin/unpin, so the action must not appear to claim there is one.
        show(listOf(chat("1", isPinned = false), chat("2", isPinned = true)))
        composeRule.onAllNodesWithTag("home_selection_action_pin").assertCountEquals(0)
    }

    @Test
    fun emptySelectionHasNoPinAction() {
        show(emptyList())
        composeRule.onAllNodesWithTag("home_selection_action_pin").assertCountEquals(0)
    }

    // --- the action targets the selected chat with the right ChatAction -------

    @Test
    fun tappingPinOnAnUnpinnedChatDispatchesPinForThatChatId() {
        val target = chat("42", isPinned = false)
        val recorded = show(listOf(target))

        composeRule.onNodeWithTag("home_selection_action_pin").performClick()
        composeRule.waitForIdle()

        assertEquals(1, recorded.size)
        assertEquals("42", recorded.single().chat.id)
        assertEquals(ChatAction.PIN, recorded.single().action)
    }

    @Test
    fun tappingUnpinOnAPinnedChatDispatchesUnpinForThatChatId() {
        val target = chat("7", isPinned = true)
        val recorded = show(listOf(target))

        composeRule.onNodeWithTag("home_selection_action_pin").performClick()
        composeRule.waitForIdle()

        assertEquals(1, recorded.size)
        assertEquals("7", recorded.single().chat.id)
        assertEquals(ChatAction.UNPIN, recorded.single().action)
    }

    // --- the dock stays the one surface ---------------------------------------

    @Test
    fun theDockRemainsTheOnlySelectionSurfaceWithPinPresent() {
        show(listOf(chat("1", isPinned = false)))
        composeRule.onAllNodesWithTag("home_selection_dock").assertCountEquals(1)
    }

    @Test
    fun everyExistingSelectionActionStillCoexistsWithPin() {
        // Pin must be additive: Read/Unread, Mute/Unmute and Delete stay exactly
        // where they were, in the same one dock.
        show(listOf(chat("1", isPinned = false)))
        composeRule.onNodeWithTag("home_selection_action_read").assertExists()
        composeRule.onNodeWithTag("home_selection_action_mute").assertExists()
        composeRule.onNodeWithTag("home_selection_action_delete").assertExists()
        composeRule.onNodeWithTag("home_selection_action_pin").assertExists()
        composeRule.onAllNodesWithTag("home_selection_dock").assertCountEquals(1)
    }
}
