package com.foresightlabs.aether.ui.design
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.domain.chats.ChatAction
import com.foresightlabs.aether.domain.messages.MessageAction
import com.foresightlabs.aether.domain.messages.MessageCapabilities
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ChatType
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.MessageType
import com.foresightlabs.aether.domain.model.Presence
import com.foresightlabs.aether.domain.model.User
import com.foresightlabs.aether.ui.home.ChatActionSheet
import com.foresightlabs.aether.ui.conversation.MessageContextMenu
import com.foresightlabs.aether.ui.conversation.MessageInfoSheet
import com.foresightlabs.aether.ui.design.AetherGlassMenuDefaults
import com.foresightlabs.aether.ui.design.AetherGlassMenuItem
import com.foresightlabs.aether.ui.design.AetherGlassMenuSurface
import com.foresightlabs.aether.ui.design.AetherGlassPopup
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

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi", application = Application::class)
class AetherGlassMenuTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun testAetherGlassMenuDefaultsTokens() {
        assertEquals(22.dp, AetherGlassMenuDefaults.PopupRadius)
        assertEquals(28.dp, AetherGlassMenuDefaults.SheetRadius)
        assertEquals(48.dp, AetherGlassMenuDefaults.ItemMinHeight)
        assertEquals(22.dp, AetherGlassMenuDefaults.IconSize)
        assertEquals(14.dp, AetherGlassMenuDefaults.ItemSpacing)
        assertEquals(280.dp, AetherGlassMenuDefaults.PopupMaxWidth)
    }

    @Test
    fun testAetherGlassMenuItemInteractiveAndAccessible() {
        var clicked = false
        composeRule.setContent {
            val themeState = AppThemeState()
            CompositionLocalProvider(LocalAppThemeState provides themeState) {
                AetherTheme(themeState = themeState) {
                    AetherGlassMenuItem(
                        icon = Icons.Default.Info,
                        title = "Test Action",
                        testTag = "test_glass_menu_item",
                        onClick = { clicked = true }
                    )
                }
            }
        }

        composeRule.waitForIdle()

        val itemNode = composeRule.onNodeWithTag("test_glass_menu_item")
        itemNode.assertIsDisplayed()
        itemNode.assertHeightIsAtLeast(48.dp)
        itemNode.performClick()

        assertTrue(clicked)
    }

    @Test
    fun testAetherGlassPopupRendersAndDismisses() {
        var dismissed = false
        var itemClicked = false

        composeRule.setContent {
            val themeState = AppThemeState()
            CompositionLocalProvider(LocalAppThemeState provides themeState) {
                AetherTheme(themeState = themeState) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AetherGlassPopup(
                            isVisible = true,
                            onDismiss = { dismissed = true }
                        ) {
                            AetherGlassMenuItem(
                                icon = Icons.Default.ContentCopy,
                                title = "Copy item",
                                testTag = "popup_copy_item",
                                onClick = { itemClicked = true }
                            )
                        }
                    }
                }
            }
        }

        composeRule.waitForIdle()

        composeRule.onNodeWithTag("popup_copy_item").assertIsDisplayed()
        composeRule.onNodeWithTag("popup_copy_item").performClick()
        assertTrue(itemClicked)

        composeRule.onNodeWithTag("glass_popup_scrim").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun testMessageContextMenuWithGlassSurfaces() {
        val testUser = User(
            id = "101",
            name = "Aether Tester",
            username = "aethertest",
            avatarInitials = "AT",
            avatarGradient = listOf(Color.Blue, Color.Cyan),
            presence = Presence.ONLINE
        )
        val testMessage = Message(
            id = "msg-123",
            chatId = "1",
            senderId = "101",
            senderName = "Aether Tester",
            text = "Hello glass world",
            timestamp = "10:30 AM",
            isOutgoing = true,
            type = MessageType.TEXT
        )
        val capabilities = MessageCapabilities(
            canBeEdited = true,
            canBeDeletedOnlyForSelf = true,
            canBeDeletedForAllUsers = true,
            canBePinned = true,
            canBeCopied = true
        )

        var selectedReaction: String? = null
        var selectedAction: MessageAction? = null
        var dismissed = false

        composeRule.setContent {
            val themeState = AppThemeState().apply {
                atmosphereMode = AtmosphereMode.MANUAL
                manualAtmosphere = TimeAtmospherePalette.NIGHT
            }
            CompositionLocalProvider(LocalAppThemeState provides themeState) {
                AetherTheme(themeState = themeState) {
                    MessageContextMenu(
                        message = testMessage,
                        capabilities = capabilities,
                        isVisible = true,
                        onDismiss = { dismissed = true },
                        onReactionSelected = { selectedReaction = it },
                        onAction = { selectedAction = it }
                    )
                }
            }
        }

        composeRule.waitForIdle()

        // Verify reaction tray
        composeRule.onNodeWithTag("reaction_tray", useUnmergedTree = true).assertExists()

        // Verify copy action
        val copyAction = composeRule.onNodeWithTag("message_action_copy")
        copyAction.assertIsDisplayed()
        copyAction.assertHeightIsAtLeast(48.dp)
        copyAction.performClick()

        assertEquals(MessageAction.COPY, selectedAction)
    }

    @Test
    fun testChatActionSheetWithUnifiedGlass() {
        val chat = Chat(
            id = "200",
            type = ChatType.DIRECT,
            title = "Glass Chat",
            lastMessageText = "Hey",
            lastMessageTime = "11:00",
            avatarInitials = "GC",
            avatarGradient = listOf(Color.Blue, Color.Cyan),
            unreadCount = 2,
            isMuted = false,
            isPinned = false
        )

        var executedAction: ChatAction? = null
        var dismissed = false

        composeRule.setContent {
            val themeState = AppThemeState().apply {
                atmosphereMode = AtmosphereMode.MANUAL
                manualAtmosphere = TimeAtmospherePalette.DAY
            }
            CompositionLocalProvider(LocalAppThemeState provides themeState) {
                AetherTheme(themeState = themeState) {
                    ChatActionSheet(
                        chat = chat,
                        onDismiss = { dismissed = true },
                        onAction = { executedAction = it }
                    )
                }
            }
        }

        composeRule.waitForIdle()

        composeRule.onNodeWithTag("chat_action_sheet").assertIsDisplayed()
        composeRule.onNodeWithText("Glass Chat").assertIsDisplayed()

        val markReadNode = composeRule.onNodeWithTag("chat_action_mark_read")
        markReadNode.assertIsDisplayed()
        markReadNode.assertHeightIsAtLeast(48.dp)
        markReadNode.performClick()

        assertEquals(ChatAction.MARK_READ, executedAction)
    }

    @Test
    fun testMessageInfoSheetWithUnifiedGlass() {
        val testUser = User(
            id = "101",
            name = "Aether Tester",
            username = "aethertest",
            avatarInitials = "AT",
            avatarGradient = listOf(Color.Blue, Color.Cyan),
            presence = Presence.ONLINE
        )
        val testMessage = Message(
            id = "msg-999",
            chatId = "1",
            senderId = "101",
            senderName = "Aether Tester",
            text = "Testing Message Info",
            timestamp = "12:45 PM",
            dateSeconds = 1724835900,
            isOutgoing = true,
            type = MessageType.TEXT
        )

        var dismissed = false

        composeRule.setContent {
            val themeState = AppThemeState().apply {
                atmosphereMode = AtmosphereMode.MANUAL
                manualAtmosphere = TimeAtmospherePalette.NIGHT
            }
            CompositionLocalProvider(LocalAppThemeState provides themeState) {
                AetherTheme(themeState = themeState) {
                    MessageInfoSheet(
                        message = testMessage,
                        capabilities = MessageCapabilities(),
                        onDismiss = { dismissed = true }
                    )
                }
            }
        }

        composeRule.waitForIdle()

        composeRule.onNodeWithTag("message_info_sheet").assertIsDisplayed()
        composeRule.onNodeWithText("Message info", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("message_info_from", useUnmergedTree = true).assertExists()
    }
}
