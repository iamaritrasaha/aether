package com.foresightlabs.aether.ui.conversation
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.domain.chats.ChatAction
import com.foresightlabs.aether.domain.messages.MessageCapabilities
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ChatType
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.MessageStatus
import com.foresightlabs.aether.domain.model.Presence
import com.foresightlabs.aether.domain.model.User
import com.foresightlabs.aether.ui.common.ChatRow
import com.foresightlabs.aether.ui.home.HomeSelectionDock
import com.foresightlabs.aether.ui.conversation.MessageBubble
import com.foresightlabs.aether.ui.conversation.MessageComposer
import com.foresightlabs.aether.ui.theme.AetherTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi", application = Application::class)
class SelectionInteractionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun testUser(id: String, name: String, isOnline: Boolean = false) = User(
        id = id,
        name = name,
        username = name.lowercase(),
        avatarInitials = name.take(1),
        avatarGradient = listOf(Color(0xFF6B7280), Color(0xFF374151)),
        presence = if (isOnline) Presence.ONLINE else Presence.OFFLINE
    )

    private fun testPersonalChat(
        id: String,
        title: String,
        unreadCount: Int = 0,
        isMuted: Boolean = false,
        isPinned: Boolean = false
    ) = Chat(
        id = id,
        title = title,
        type = ChatType.DIRECT,
        lastMessageText = "Hello",
        lastMessageTime = "12:00",
        unreadCount = unreadCount,
        isMuted = isMuted,
        isPinned = isPinned,
        avatarInitials = title.take(1),
        avatarGradient = listOf(Color(0xFF6B7280), Color(0xFF374151)),
        directUser = testUser(id, title)
    )

    private fun testMessage(
        id: String,
        chatId: String = "1",
        text: String = "Hello Aether",
        isOutgoing: Boolean = false,
        status: MessageStatus = MessageStatus.READ
    ) = Message(
        id = id,
        chatId = chatId,
        senderId = "100",
        senderName = "Sender",
        text = text,
        timestamp = "12:00",
        isOutgoing = isOutgoing,
        status = status
    )

    // =========================================================================
    // Part A: Home Chat Selection Tests
    // =========================================================================

    @Test
    fun homeSelectionDock_displaysCount_andClearsOnClose() {
        val chats = listOf(
            testPersonalChat("1", "Alice"),
            testPersonalChat("2", "Bob")
        )
        var cleared = false

        composeRule.setContent {
            AetherTheme {
                HomeSelectionDock(
                    selectedChats = chats,
                    onClearSelection = { cleared = true },
                    onChatAction = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag("home_selection_dock").assertIsDisplayed()
        composeRule.onNodeWithText("2 selected").assertIsDisplayed()

        composeRule.onNodeWithTag("home_selection_clear").performClick()
        assertTrue(cleared)
    }

    @Test
    fun homeSelectionDock_executesActionsOnSelectedChats() {
        val chat1 = testPersonalChat("1", "Alice", unreadCount = 2, isMuted = false)
        val chat2 = testPersonalChat("2", "Bob", unreadCount = 0, isMuted = false)
        val selectedChats = listOf(chat1, chat2)

        val executedActions = mutableListOf<Pair<String, ChatAction>>()
        var cleared = false

        composeRule.setContent {
            AetherTheme {
                HomeSelectionDock(
                    selectedChats = selectedChats,
                    onClearSelection = { cleared = true },
                    onChatAction = { chat, action -> executedActions.add(chat.id to action) }
                )
            }
        }

        // Click Read action
        composeRule.onNodeWithTag("home_selection_action_read").performClick()
        assertTrue(cleared)
        assertEquals(2, executedActions.size)
        // Since chat1 has unreadCount > 0, the dock offered MARK_READ
        assertEquals(ChatAction.MARK_READ, executedActions[0].second)
        assertEquals(ChatAction.MARK_READ, executedActions[1].second)
    }

    @Test
    fun homeChatRow_showsCheckBadge_whenSelected() {
        val chat = testPersonalChat("1", "Alice")

        composeRule.setContent {
            AetherTheme {
                ChatRow(
                    chat = chat,
                    onClick = {},
                    isSelected = true,
                    isSelectionActive = true
                )
            }
        }

        composeRule.onNodeWithTag("chat_row_check_1", useUnmergedTree = true).assertIsDisplayed()
    }

    // =========================================================================
    // Part B: Conversation Message Selection Tests
    // =========================================================================

    @Test
    fun messageComposer_morphsToSelectionDock_whenMessagesSelected() {
        val msg1 = testMessage("m1", text = "First message", isOutgoing = false)
        val msg2 = testMessage("m2", text = "Second message", isOutgoing = true)
        val selection = listOf(msg1, msg2)

        var cleared = false
        var copiedMessages = emptyList<Message>()

        composeRule.setContent {
            AetherTheme {
                MessageComposer(
                    replyingTo = null,
                    onDismissReply = {},
                    onSendMessage = { _, _ -> },
                    selectedMessages = selection,
                    onClearSelection = { cleared = true },
                    onCopySelected = { copiedMessages = it }
                )
            }
        }

        // Normal text field should not be visible in selection mode
        composeRule.onNodeWithTag("message_input_field").assertDoesNotExist()

        // Selection dock should be visible
        composeRule.onNodeWithTag("message_selection_dock").assertIsDisplayed()
        composeRule.onNodeWithText("2 selected").assertIsDisplayed()

        // Clicking Copy dispatches copied messages and clears selection
        composeRule.onNodeWithTag("selection_action_copy").performClick()
        assertEquals(2, copiedMessages.size)
        assertTrue(cleared)
    }

    @Test
    fun messageComposer_showsEditAction_forSingleEditableMessage() {
        val editableMsg = testMessage("m1", text = "Editable text", isOutgoing = true)
        val capabilities = mapOf(
            "m1" to MessageCapabilities(
                canBeEdited = true,
                canBeReplied = true,
                canBeCopied = true,
                canBeForwarded = true,
                canBeDeletedForAllUsers = true
            )
        )

        var editedMessage: Message? = null
        var cleared = false

        composeRule.setContent {
            AetherTheme {
                MessageComposer(
                    replyingTo = null,
                    onDismissReply = {},
                    onSendMessage = { _, _ -> },
                    selectedMessages = listOf(editableMsg),
                    capabilities = capabilities,
                    onClearSelection = { cleared = true },
                    onEditSelected = { editedMessage = it }
                )
            }
        }

        composeRule.onNodeWithTag("selection_action_edit").assertIsDisplayed()
        composeRule.onNodeWithTag("selection_action_edit").performClick()

        assertEquals(editableMsg.id, editedMessage?.id)
        assertTrue(cleared)
    }

    @Test
    fun messageComposer_showsReplyAction_forSingleIncomingMessage() {
        val incomingMsg = testMessage("m1", text = "Incoming text", isOutgoing = false)
        val capabilities = mapOf(
            "m1" to MessageCapabilities(
                canBeEdited = false,
                canBeReplied = true,
                canBeCopied = true,
                canBeForwarded = true,
                canBeDeletedOnlyForSelf = true
            )
        )

        var repliedMessage: Message? = null
        var cleared = false

        composeRule.setContent {
            AetherTheme {
                MessageComposer(
                    replyingTo = null,
                    onDismissReply = {},
                    onSendMessage = { _, _ -> },
                    selectedMessages = listOf(incomingMsg),
                    capabilities = capabilities,
                    onClearSelection = { cleared = true },
                    onReplySelected = { repliedMessage = it }
                )
            }
        }

        composeRule.onNodeWithTag("selection_action_reply").assertIsDisplayed()
        composeRule.onNodeWithTag("selection_action_reply").performClick()

        assertEquals(incomingMsg.id, repliedMessage?.id)
        assertTrue(cleared)
    }

    @Test
    fun messageBubble_rendersMarginCheck_whenSelected() {
        val incoming = testMessage("in1", isOutgoing = false)

        composeRule.setContent {
            AetherTheme {
                MessageBubble(
                    message = incoming,
                    onSwipeToReply = {},
                    onLongPress = {},
                    onMediaClick = {},
                    onReactionClick = { _, _ -> },
                    isSelected = true,
                    isSelectionActive = true
                )
            }
        }

        composeRule.onNodeWithTag("message_check_in1", useUnmergedTree = true).assertIsDisplayed()
    }
}
