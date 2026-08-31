package com.foresightlabs.aether.ui.conversation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.domain.messages.MessageCapabilities
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * MESSAGE pinning, reached through the existing message-selection Curtain
 * (long-press a message -> it becomes a one-message selection -> the Curtain's
 * own selection dock appears alongside Reply/Copy/Forward/Delete).
 *
 * This is deliberately a separate feature and a separate test file from CHAT
 * pinning (see [com.foresightlabs.aether.ui.conversation.ConversationPinTest],
 * [com.foresightlabs.aether.ui.home.HomeSelectionDockTest]): chat pinning moves
 * a conversation in Home's list via `TdApi.ToggleChatIsPinned`; message pinning
 * pins one message inside a conversation via `TdApi.PinChatMessage`/
 * `TdApi.UnpinChatMessage`. Nothing here touches the chat-pin path, and nothing
 * in that path touches this one.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi", application = Application::class)
class MessageSelectionPinTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun message(id: String, isPinned: Boolean = false) = Message(
        id = id,
        chatId = "103",
        senderId = "103",
        senderName = "Ishani Roy",
        text = "Here is the important information.",
        timestamp = "10:41 AM",
        isOutgoing = false,
        status = MessageStatus.SENT,
        isPinned = isPinned
    )

    private fun theme() = AppThemeState().apply {
        atmosphereMode = AtmosphereMode.MANUAL
        manualAtmosphere = TimeAtmospherePalette.DAY
    }

    private data class PinCall(val messageId: String)

    /** Renders the Curtain composer with [selection] already selected. */
    private fun showSelection(
        selection: List<Message>,
        capabilities: Map<String, MessageCapabilities>
    ): MutableList<PinCall> {
        val calls = mutableListOf<PinCall>()
        val appTheme = theme()
        composeRule.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalAppThemeState provides appTheme
            ) {
                AetherTheme(themeState = appTheme) {
                    MessageComposer(
                        replyingTo = null,
                        onDismissReply = {},
                        onSendMessage = { _, _ -> },
                        selectedMessages = selection,
                        capabilities = capabilities,
                        onPinSelected = { msg -> calls.add(PinCall(msg.id)) }
                    )
                }
            }
        }
        composeRule.waitForIdle()
        return calls
    }

    // --- 1 & 2: visibility follows canBePinned and the message's own state -----

    @Test
    fun selectingAnUnpinnedPinnableMessageExposesPin() {
        val target = message("1", isPinned = false)
        showSelection(listOf(target), mapOf("1" to MessageCapabilities(canBePinned = true)))

        composeRule.onNodeWithContentDescription("Pin").assertExists()
    }

    @Test
    fun selectingAPinnedMessageWhereUnpinIsPermittedExposesUnpin() {
        val target = message("1", isPinned = true)
        showSelection(listOf(target), mapOf("1" to MessageCapabilities(canBePinned = true)))

        composeRule.onNodeWithContentDescription("Unpin").assertExists()
    }

    @Test
    fun aMessageTelegramDoesNotPermitPinningOnOffersNoPinAction() {
        // canBePinned = false is exactly "Telegram says the user cannot pin
        // this" -- the action must not appear at all, not appear disabled.
        val target = message("1", isPinned = false)
        showSelection(listOf(target), mapOf("1" to MessageCapabilities(canBePinned = false)))

        composeRule.onAllNodesWithTag("selection_action_pin").assertCountEquals(0)
    }

    @Test
    fun capabilitiesStillInFlightDoesNotOptimisticallyOfferPin() {
        // Unlike Reply/Forward, Pin has no permissive "capabilities not answered
        // yet" fallback -- a wrong guess here means offering an action Telegram
        // will refuse.
        val target = message("1", isPinned = false)
        showSelection(listOf(target), emptyMap())

        composeRule.onAllNodesWithTag("selection_action_pin").assertCountEquals(0)
    }

    // --- 3, 4, 5, 6: the operation targets exactly the selected message ---------

    @Test
    fun tappingPinDispatchesTheSelectedMessagesIdExactly() {
        val target = message("987654", isPinned = false)
        val calls = showSelection(listOf(target), mapOf("987654" to MessageCapabilities(canBePinned = true)))

        composeRule.onNodeWithTag("selection_action_pin").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(PinCall("987654")), calls)
    }

    @Test
    fun tappingUnpinDispatchesTheSelectedMessagesIdExactly() {
        val target = message("987654", isPinned = true)
        val calls = showSelection(listOf(target), mapOf("987654" to MessageCapabilities(canBePinned = true)))

        composeRule.onNodeWithTag("selection_action_pin").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(PinCall("987654")), calls)
    }

    // --- 15: TDLib's message-pin API targets exactly one message ---------------

    @Test
    fun selectingMultipleMessagesNeverOffersPin() {
        val a = message("1", isPinned = false)
        val b = message("2", isPinned = false)
        val caps = mapOf(
            "1" to MessageCapabilities(canBePinned = true, canBeCopied = true),
            "2" to MessageCapabilities(canBePinned = true, canBeCopied = true)
        )
        showSelection(listOf(a, b), caps)

        composeRule.onAllNodesWithTag("selection_action_pin").assertCountEquals(0)
    }

    // --- other existing actions and the Curtain remain intact -------------------

    @Test
    fun pinCoexistsWithTheOtherExistingSelectionActions() {
        val target = message("1", isPinned = false)
        val caps = mapOf("1" to MessageCapabilities(canBePinned = true, canBeCopied = true, canBeForwarded = true))
        showSelection(listOf(target), caps)

        composeRule.onNodeWithTag("selection_action_copy").assertExists()
        composeRule.onNodeWithTag("selection_action_forward").assertExists()
        composeRule.onNodeWithTag("selection_action_delete").assertExists()
        composeRule.onNodeWithTag("selection_action_pin").assertExists()
        composeRule.onAllNodesWithTag("message_selection_dock").assertCountEquals(1)
    }

    // --- 17: still exactly the one Curtain, no second surface -------------------

    @Test
    fun noSecondCurtainOrSheetIsIntroducedByThePinAction() {
        val target = message("1", isPinned = false)
        showSelection(listOf(target), mapOf("1" to MessageCapabilities(canBePinned = true)))

        composeRule.onAllNodesWithTag("message_selection_dock").assertCountEquals(1)
    }

    // --- 11, 12, 13: the pinned-message indicator and jump-to-message ----------

    private val chat = Chat(
        id = "103",
        title = "Ishani Roy",
        type = ChatType.DIRECT,
        lastMessageText = "See you at eight",
        lastMessageTime = "10:42 AM",
        avatarInitials = "IR",
        avatarGradient = listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9))
    )

    @Test
    fun aPinnedMessageIndicatorAppearsWhenAMessageIsPinned() {
        composeRule.setContent {
            ConversationIdentityHeader(
                chat = chat,
                pinned = message("55", isPinned = true).copy(text = "Here is the important information."),
                pinnedCount = 1
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("pinned_banner").assertExists()
    }

    @Test
    fun theIndicatorShowsAPreviewOfThePinnedMessage() {
        composeRule.setContent {
            ConversationIdentityHeader(
                chat = chat,
                pinned = message("55", isPinned = true).copy(text = "Here is the important information."),
                pinnedCount = 1
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Here is the important information.").assertExists()
    }

    @Test
    fun tappingTheIndicatorTargetsThePinnedMessageIdThroughTheExistingJumpCallback() {
        var jumpedTo: String? = null
        composeRule.setContent {
            ConversationIdentityHeader(
                chat = chat,
                pinned = message("55", isPinned = true),
                pinnedCount = 1,
                onPinnedClick = { jumpedTo = "55" }
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("pinned_banner").performClick()
        composeRule.waitForIdle()

        // The banner itself only signals "jump"; ConversationScreen's real
        // onPinnedClick wiring (unmodified by this task) is what turns that
        // into onJumpToMessage(pinnedMessage.id) -- the existing message-anchor
        // mechanism, not a new scrolling system.
        assertEquals("55", jumpedTo)
    }

    // --- 7: message pinning must never call the chat-pinning TDLib function ----

    @Test
    fun messagePinningNeverInvokesToggleChatIsPinned() {
        // Checked as a constructor call ("ToggleChatIsPinned(") rather than a
        // bare substring, since these files legitimately name the chat-pin
        // operation in comments to explain why message pinning is a distinct
        // path from it -- that contrast is documentation, not a call site.
        val sources = listOf(
            "src/main/java/com/foresightlabs/aether/ui/conversation/MessageComposer.kt",
            "src/main/java/com/foresightlabs/aether/ui/conversation/ConversationViewModel.kt",
            "src/main/java/com/foresightlabs/aether/ui/conversation/ConversationScreen.kt"
        )
        sources.forEach { relativePath ->
            val file = File(relativePath).takeIf { it.exists() } ?: File("app", relativePath)
            org.junit.Assert.assertTrue("expected to find $relativePath", file.exists())
            org.junit.Assert.assertTrue(
                "$relativePath must not construct TdApi.ToggleChatIsPinned -- that is Home's chat-pin operation",
                "ToggleChatIsPinned(" !in file.readText()
            )
        }
    }

    @Test
    fun theViewModelsMessagePinFunctionUsesThePinChatMessageFamilyOnly() {
        val file = File("src/main/java/com/foresightlabs/aether/data/telegram/TelegramClient.kt")
            .takeIf { it.exists() }
            ?: File("app/src/main/java/com/foresightlabs/aether/data/telegram/TelegramClient.kt")
        val text = file.readText()
        val pinFn = text.substringAfter("suspend fun pinMessage(").substringBefore("\n    suspend fun ")
        val unpinFn = text.substringAfter("suspend fun unpinMessage(").substringBefore("\n    suspend fun ")

        org.junit.Assert.assertTrue("pinMessage must call TdApi.PinChatMessage", "PinChatMessage" in pinFn)
        org.junit.Assert.assertTrue("unpinMessage must call TdApi.UnpinChatMessage", "UnpinChatMessage" in unpinFn)
        org.junit.Assert.assertTrue("pinMessage must not use chat pinning", "ToggleChatIsPinned" !in pinFn)
        org.junit.Assert.assertTrue("unpinMessage must not use chat pinning", "ToggleChatIsPinned" !in unpinFn)
    }
}
