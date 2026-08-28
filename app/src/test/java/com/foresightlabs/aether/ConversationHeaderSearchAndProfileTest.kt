package com.foresightlabs.aether

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.domain.chats.ChatAction
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ChatType
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.MessageStatus
import com.foresightlabs.aether.domain.model.Presence
import com.foresightlabs.aether.domain.model.User
import com.foresightlabs.aether.domain.search.ConversationSearchState
import com.foresightlabs.aether.ui.screens.ConversationScreen
import com.foresightlabs.aether.ui.screens.ProfileScreen
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
class ConversationHeaderSearchAndProfileTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val testUser = User(
        id = "201",
        name = "Kavita Rao",
        username = "kavita",
        avatarInitials = "KR",
        avatarGradient = listOf(Color(0xFF6366F1), Color(0xFF4F46E5)),
        phone = "+1 555 0201",
        presence = Presence.ONLINE
    )

    private val testChat = Chat(
        id = "201",
        title = "Kavita Rao",
        type = ChatType.DIRECT,
        lastMessageText = "See you tomorrow!",
        lastMessageTime = "11:00 AM",
        avatarInitials = "KR",
        avatarGradient = listOf(Color(0xFF6366F1), Color(0xFF4F46E5)),
        directUser = testUser,
        blockableUserId = 201L,
        isBlocked = false,
        canRevokeHistory = true,
        isMuted = false
    )

    private val testMessages = listOf(
        Message(
            id = "101",
            chatId = "201",
            senderId = "201",
            senderName = "Kavita Rao",
            text = "Welcome to Aether design discussion",
            timestamp = "10:55 AM",
            isOutgoing = false
        ),
        Message(
            id = "102",
            chatId = "201",
            senderId = "me",
            senderName = "You",
            text = "Sounds great!",
            timestamp = "10:58 AM",
            isOutgoing = true,
            status = MessageStatus.READ
        )
    )

    // --- CONVERSATION HEADER TESTS -------------------------------------------

    @Test
    fun conversationHeaderRendersRestingIdentityWithMacLikeSearchButton() {
        var profileOpened = false
        var searchOpened = false

        composeRule.setContent {
            val theme = AppThemeState().apply {
                atmosphereMode = AtmosphereMode.MANUAL
                manualAtmosphere = TimeAtmospherePalette.DAY
            }
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalAppThemeState provides theme
            ) {
                AetherTheme(themeState = theme) {
                    ConversationScreen(
                        chat = testChat,
                        messages = testMessages,
                        canSend = true,
                        onBack = {},
                        onNavigateToProfile = { profileOpened = true },
                        onSendMessage = { _, _, _, _ -> },
                        onComposerChanged = {},
                        onLoadOlder = {},
                        onDeleteMessage = { _, _ -> },
                        onRetryMessage = {},
                        onVisibleMessages = {},
                        onOpenSearch = { searchOpened = true },
                        searchState = ConversationSearchState.Idle
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Avatar + Name + Status are clickable to open profile
        composeRule.onNodeWithTag("conversation_header_profile").assertIsDisplayed()
        composeRule.onNodeWithText("Kavita Rao").assertIsDisplayed()
        composeRule.onNodeWithText("online").assertIsDisplayed()

        // Search button exists
        composeRule.onNodeWithTag("conversation_search_button").assertIsDisplayed()

        // No 3-dot overflow button anywhere in conversation
        composeRule.onAllNodesWithContentDescription("More options", useUnmergedTree = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithTag("conversation_more_button", useUnmergedTree = true)
            .assertCountEquals(0)

        // No visible Back button in conversation
        composeRule.onAllNodesWithTag("conversation_back_button", useUnmergedTree = true)
            .assertCountEquals(0)

        // Test clicks
        composeRule.onNodeWithTag("conversation_header_profile").performClick()
        assertTrue("Tapping header identity must open profile", profileOpened)

        composeRule.onNodeWithTag("conversation_search_button").performClick()
        assertTrue("Tapping search button must open search", searchOpened)
    }

    // --- CONVERSATION SEARCH TESTS -------------------------------------------

    @Test
    fun conversationHeaderMorphsToSearchModeWhenActive() {
        val searchState = mutableStateOf(ConversationSearchState.Idle)
        var queryReceived = ""
        var closed = false

        composeRule.setContent {
            val theme = AppThemeState().apply {
                atmosphereMode = AtmosphereMode.MANUAL
                manualAtmosphere = TimeAtmospherePalette.DAY
            }
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalAppThemeState provides theme
            ) {
                AetherTheme(themeState = theme) {
                    ConversationScreen(
                        chat = testChat,
                        messages = testMessages,
                        canSend = true,
                        onBack = {},
                        onNavigateToProfile = {},
                        onSendMessage = { _, _, _, _ -> },
                        onComposerChanged = {},
                        onLoadOlder = {},
                        onDeleteMessage = { _, _ -> },
                        onRetryMessage = {},
                        onVisibleMessages = {},
                        onOpenSearch = {
                            searchState.value = ConversationSearchState(isActive = true)
                        },
                        onCloseSearch = {
                            searchState.value = ConversationSearchState.Idle
                            closed = true
                        },
                        onSearchQueryChange = { query ->
                            queryReceived = query
                            searchState.value = searchState.value.copy(query = query)
                        },
                        searchState = searchState.value
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Activate search mode
        composeRule.runOnUiThread {
            searchState.value = ConversationSearchState(
                isActive = true,
                query = "",
                isLoading = false,
                results = emptyList()
            )
        }
        composeRule.waitForIdle()

        // Search bar & input field are displayed
        composeRule.onNodeWithTag("conversation_search_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("conversation_search_field").assertIsDisplayed()
        composeRule.onNodeWithTag("conversation_search_close").assertIsDisplayed()

        // Enter query
        composeRule.onNodeWithTag("conversation_search_field").performTextInput("discussion")
        assertEquals("discussion", queryReceived)

        // Close search
        composeRule.onNodeWithTag("conversation_search_close").performClick()
        assertTrue("Tapping close button must close search", closed)
    }

    @Test
    fun conversationSearchShowsResultNavigationWhenResultsExist() {
        val searchState = ConversationSearchState(
            isActive = true,
            query = "design",
            results = listOf(testMessages[0]),
            selectedIndex = 0,
            totalCount = 1
        )

        composeRule.setContent {
            val theme = AppThemeState().apply {
                atmosphereMode = AtmosphereMode.MANUAL
                manualAtmosphere = TimeAtmospherePalette.DAY
            }
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalAppThemeState provides theme
            ) {
                AetherTheme(themeState = theme) {
                    ConversationScreen(
                        chat = testChat,
                        messages = testMessages,
                        canSend = true,
                        onBack = {},
                        onNavigateToProfile = {},
                        onSendMessage = { _, _, _, _ -> },
                        onComposerChanged = {},
                        onLoadOlder = {},
                        onDeleteMessage = { _, _ -> },
                        onRetryMessage = {},
                        onVisibleMessages = {},
                        searchState = searchState
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("conversation_search_status").assertIsDisplayed()
        composeRule.onNodeWithText("1 of 1").assertIsDisplayed()
        composeRule.onNodeWithTag("conversation_search_older").assertIsDisplayed()
        composeRule.onNodeWithTag("conversation_search_newer").assertIsDisplayed()
    }

    @Test
    fun conversationSearchShowsEmptyStateWhenNoMessagesFound() {
        val searchState = ConversationSearchState(
            isActive = true,
            query = "nonexistent",
            results = emptyList(),
            selectedIndex = -1,
            totalCount = 0
        )

        composeRule.setContent {
            val theme = AppThemeState().apply {
                atmosphereMode = AtmosphereMode.MANUAL
                manualAtmosphere = TimeAtmospherePalette.DAY
            }
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalAppThemeState provides theme
            ) {
                AetherTheme(themeState = theme) {
                    ConversationScreen(
                        chat = testChat,
                        messages = testMessages,
                        canSend = true,
                        onBack = {},
                        onNavigateToProfile = {},
                        onSendMessage = { _, _, _, _ -> },
                        onComposerChanged = {},
                        onLoadOlder = {},
                        onDeleteMessage = { _, _ -> },
                        onRetryMessage = {},
                        onVisibleMessages = {},
                        searchState = searchState
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("conversation_search_status").assertIsDisplayed()
        composeRule.onNodeWithText("No messages found").assertIsDisplayed()
    }

    // --- PROFILE SCREEN TESTS ------------------------------------------------

    @Test
    fun profileScreenHasNoThreeDotMenuAndExposesAllActionsInline() {
        var dispatchedAction: ChatAction? = null
        var backClicked = false
        var searchClicked = false

        composeRule.setContent {
            val theme = AppThemeState().apply {
                atmosphereMode = AtmosphereMode.MANUAL
                manualAtmosphere = TimeAtmospherePalette.DAY
            }
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalAppThemeState provides theme
            ) {
                AetherTheme(themeState = theme) {
                    ProfileScreen(
                        chat = testChat,
                        onBack = { backClicked = true },
                        onNavigateToConversation = {},
                        onSearchConversation = { searchClicked = true },
                        onChatAction = { _, action -> dispatchedAction = action }
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // 1. Floating Header Back button exists
        composeRule.onNodeWithTag("profile_back_button").assertIsDisplayed()

        // 2. ZERO 3-dot overflow menu in Profile
        composeRule.onAllNodesWithTag("profile_more_button", useUnmergedTree = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("More options", useUnmergedTree = true)
            .assertCountEquals(0)

        // 3. Hero Identity & Quick actions
        composeRule.onNodeWithText("Kavita Rao").assertIsDisplayed()
        composeRule.onNodeWithTag("profile_action_message").assertIsDisplayed()
        composeRule.onNodeWithTag("profile_action_search").assertIsDisplayed()

        // 4. Info section
        composeRule.onNodeWithText("INFO").assertIsDisplayed()
        composeRule.onNodeWithText("+1 555 0201").assertIsDisplayed()
        composeRule.onNodeWithText("@kavita").assertIsDisplayed()

        // 5. Conversation section
        composeRule.onNodeWithText("CONVERSATION").assertIsDisplayed()
        composeRule.onNodeWithTag("profile_row_notifications").assertIsDisplayed()
        composeRule.onNodeWithTag("profile_notifications_switch").assertIsDisplayed()
        composeRule.onNodeWithTag("profile_row_search").assertIsDisplayed()

        // 6. Shared content section & tabs
        composeRule.onNodeWithText("Media").assertIsDisplayed()
        composeRule.onNodeWithText("Files").assertIsDisplayed()
        composeRule.onNodeWithText("Links").assertIsDisplayed()
        composeRule.onNodeWithText("Voice").assertIsDisplayed()

        // 7. Privacy & Safety section (scrolled into view using LazyColumn)
        composeRule.onNodeWithTag("profile_lazy_column")
            .performScrollToNode(hasText("PRIVACY & SAFETY"))
        composeRule.onNodeWithText("PRIVACY & SAFETY").assertIsDisplayed()

        composeRule.onNodeWithTag("profile_lazy_column")
            .performScrollToNode(hasTestTag("profile_row_block"))
        composeRule.onNodeWithTag("profile_row_block").assertIsDisplayed()

        // 8. Conversation Management section (scrolled into view using LazyColumn)
        composeRule.onNodeWithTag("profile_lazy_column")
            .performScrollToNode(hasText("CONVERSATION MANAGEMENT"))
        composeRule.onNodeWithText("CONVERSATION MANAGEMENT").assertIsDisplayed()

        composeRule.onNodeWithTag("profile_lazy_column")
            .performScrollToNode(hasTestTag("profile_row_clear_history"))
        composeRule.onNodeWithTag("profile_row_clear_history").assertIsDisplayed()

        composeRule.onNodeWithTag("profile_lazy_column")
            .performScrollToNode(hasTestTag("profile_row_delete"))
        composeRule.onNodeWithTag("profile_row_delete").assertIsDisplayed()

        // Test Notifications Switch Toggle
        composeRule.onNodeWithTag("profile_lazy_column")
            .performScrollToNode(hasTestTag("profile_notifications_switch"))
        composeRule.onNodeWithTag("profile_notifications_switch").performClick()
        assertEquals(ChatAction.MUTE, dispatchedAction)

        // Test Search action
        composeRule.onNodeWithTag("profile_lazy_column")
            .performScrollToNode(hasTestTag("profile_row_search"))
        composeRule.onNodeWithTag("profile_row_search").performClick()
        assertTrue("Tapping search row must trigger search navigation", searchClicked)
    }

    @Test
    fun profileScreenDestructiveActionsTriggerConfirmationDialogs() {
        var dispatchedAction: ChatAction? = null

        composeRule.setContent {
            val theme = AppThemeState().apply {
                atmosphereMode = AtmosphereMode.MANUAL
                manualAtmosphere = TimeAtmospherePalette.DAY
            }
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalAppThemeState provides theme
            ) {
                AetherTheme(themeState = theme) {
                    ProfileScreen(
                        chat = testChat,
                        onBack = {},
                        onNavigateToConversation = {},
                        onChatAction = { _, action -> dispatchedAction = action }
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // 1. Block Confirmation
        composeRule.onNodeWithTag("profile_lazy_column")
            .performScrollToNode(hasTestTag("profile_row_block"))
        composeRule.onNodeWithTag("profile_row_block").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Block Kavita Rao?").assertIsDisplayed()
        composeRule.onNodeWithTag("dialog_confirm_button").performClick()
        assertEquals(ChatAction.BLOCK, dispatchedAction)

        // 2. Clear History Confirmation
        dispatchedAction = null
        composeRule.onNodeWithTag("profile_lazy_column")
            .performScrollToNode(hasTestTag("profile_row_clear_history"))
        composeRule.onNodeWithTag("profile_row_clear_history").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Clear chat history?").assertIsDisplayed()
        composeRule.onNodeWithTag("dialog_confirm_button").performClick()
        assertEquals(ChatAction.CLEAR_HISTORY, dispatchedAction)

        // 3. Delete Conversation Confirmation (with Delete for everyone option)
        dispatchedAction = null
        composeRule.onNodeWithTag("profile_lazy_column")
            .performScrollToNode(hasTestTag("profile_row_delete"))
        composeRule.onNodeWithTag("profile_row_delete").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Delete conversation?").assertIsDisplayed()
        composeRule.onNodeWithTag("dialog_delete_for_everyone").assertIsDisplayed()
        composeRule.onNodeWithTag("dialog_delete_for_everyone").performClick()
        assertEquals(ChatAction.DELETE_FOR_EVERYONE, dispatchedAction)
    }
}
