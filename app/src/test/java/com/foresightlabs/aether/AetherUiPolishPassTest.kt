package com.foresightlabs.aether

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.domain.chats.ChatAction
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ChatType
import com.foresightlabs.aether.domain.model.Presence
import com.foresightlabs.aether.domain.model.User
import com.foresightlabs.aether.ui.conversation.AttachmentSheet
import com.foresightlabs.aether.ui.conversation.MessageComposer
import com.foresightlabs.aether.ui.design.AetherFloatingHeaderDefaults
import com.foresightlabs.aether.ui.design.AetherMinTouchTarget
import com.foresightlabs.aether.ui.design.AetherNavPillDefaults
import com.foresightlabs.aether.ui.profile.ProfileScreen
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
class AetherUiPolishPassTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun testHeaderAndNavPillTokens() {
        assertEquals(48.dp, AetherMinTouchTarget)
        assertEquals(64.dp, AetherFloatingHeaderDefaults.ExpandedHeight)
        assertEquals(60.dp, AetherFloatingHeaderDefaults.CompactHeight)

        assertEquals(62.dp, AetherNavPillDefaults.Height)
        assertEquals(48.dp, AetherNavPillDefaults.DestinationSlotSize)
        assertEquals(44.dp, AetherNavPillDefaults.SelectionLensSize)
        assertEquals(22.dp, AetherNavPillDefaults.IconSize)
        assertEquals(36.dp, AetherNavPillDefaults.OuterHorizontalPadding)
    }

    @Test
    fun testAttachmentSheet4ColumnLayoutAndVenueRegressionOnNarrowViewport() {
        var selectedOption: String? = null
        var dismissed = false

        composeRule.setContent {
            val themeState = AppThemeState().apply {
                atmosphereMode = AtmosphereMode.MANUAL
                manualAtmosphere = TimeAtmospherePalette.DAY
            }
            CompositionLocalProvider(LocalAppThemeState provides themeState) {
                AetherTheme(themeState = themeState) {
                    Box(modifier = Modifier.width(360.dp)) {
                        AttachmentSheet(
                            isVisible = true,
                            onDismiss = { dismissed = true },
                            onOptionSelected = { selectedOption = it }
                        )
                    }
                }
            }
        }

        composeRule.waitForIdle()

        // Verify sheet content is displayed
        composeRule.onNodeWithTag("attachment_sheet_content").assertIsDisplayed()
        composeRule.onNodeWithText("Share Content").assertIsDisplayed()

        // Verify enabled attachment items exist and are displayed
        val expectedTags = buildList {
            add("attachment_gallery")
            add("attachment_camera")
            add("attachment_video_message")
            add("attachment_file")
            add("attachment_audio")
            add("attachment_location")
            if (com.foresightlabs.aether.AetherFeatureFlags.LIVE_LOCATION_ENABLED) {
                add("attachment_live_location")
            }
            add("attachment_venue")
            add("attachment_contact")
        }
        for (tag in expectedTags) {
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
        }

        // Specifically test Venue click and regression
        composeRule.onNodeWithTag("attachment_venue").performClick()
        assertEquals("Venue", selectedOption)
        assertTrue(dismissed)
    }

    @Test
    fun testMessageComposerTouchTargetsAndMorph() {
        composeRule.setContent {
            val themeState = AppThemeState().apply {
                atmosphereMode = AtmosphereMode.MANUAL
                manualAtmosphere = TimeAtmospherePalette.DAY
            }
            CompositionLocalProvider(LocalAppThemeState provides themeState) {
                AetherTheme(themeState = themeState) {
                    MessageComposer(
                        replyingTo = null,
                        onDismissReply = {},
                        onSendMessage = { _, _ -> },
                        onOpenAttachmentSheet = {},
                        onVoiceNoteRecorded = {}
                    )
                }
            }
        }

        composeRule.waitForIdle()

        // Check 48dp touch targets on attachment & sticker buttons
        composeRule.onNodeWithTag("attachment_button")
            .assertIsDisplayed()
            .assertWidthIsEqualTo(48.dp)
            .assertHeightIsEqualTo(48.dp)

        composeRule.onNodeWithTag("sticker_button")
            .assertIsDisplayed()
            .assertWidthIsEqualTo(48.dp)
            .assertHeightIsEqualTo(48.dp)

        // Initial state: empty text -> Voice Record button is displayed (48dp x 48dp)
        composeRule.onNodeWithTag("voice_record_button")
            .assertIsDisplayed()
            .assertWidthIsEqualTo(48.dp)
            .assertHeightIsEqualTo(48.dp)
    }

    @Test
    fun testProfileScreenOverflowTriggersChatActionSheet() {
        val testUser = User(
            id = "123",
            name = "Test User",
            username = "testuser",
            avatarInitials = "TU",
            avatarGradient = listOf(Color.Blue, Color.Cyan),
            presence = Presence.ONLINE
        )
        val testChat = Chat(
            id = "123",
            title = "Test User",
            type = ChatType.DIRECT,
            lastMessageText = "Hello",
            lastMessageTime = "12:00",
            avatarInitials = "TU",
            avatarGradient = listOf(Color.Blue, Color.Cyan),
            directUser = testUser,
            unreadCount = 0
        )
        var actionInvoked: ChatAction? = null

        composeRule.setContent {
            val themeState = AppThemeState().apply {
                atmosphereMode = AtmosphereMode.MANUAL
                manualAtmosphere = TimeAtmospherePalette.DAY
            }
            CompositionLocalProvider(LocalAppThemeState provides themeState) {
                AetherTheme(themeState = themeState) {
                    ProfileScreen(
                        chat = testChat,
                        onBack = {},
                        onNavigateToConversation = {},
                        onChatAction = { _, action -> actionInvoked = action },
                        canCallAudio = true,
                        canCallVideo = false
                    )
                }
            }
        }

        composeRule.waitForIdle()

        // Hero actions
        composeRule.onNodeWithTag("profile_action_message").assertIsDisplayed()
        if (com.foresightlabs.aether.AetherFeatureFlags.CALLS_ENABLED) {
            composeRule.onNodeWithTag("profile_action_audio").assertIsDisplayed()
            composeRule.onNodeWithTag("profile_action_video").assertIsDisplayed()
        }
        composeRule.onNodeWithTag("profile_action_search").assertIsDisplayed()

        // Tabs
        composeRule.onNodeWithTag("profile_tab_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("profile_tab_media").assertIsDisplayed()
        composeRule.onNodeWithTag("profile_tab_files").assertIsDisplayed()

        // Verify no 3-dot more button exists in Profile
        composeRule.onAllNodesWithTag("profile_more_button", useUnmergedTree = true)
            .assertCountEquals(0)

        // Check that inline Notifications switch works and triggers Mute
        composeRule.onNodeWithTag("profile_notifications_switch").assertIsDisplayed().performClick()
        assertEquals(ChatAction.MUTE, actionInvoked)
    }

    @Test
    fun testConversationHeaderMacSearchButtonAndNoOverflow() {
        var profileClicked = false
        var searchClicked = false
        val testChat = Chat(
            id = "456",
            title = "Evelyn Reed",
            type = ChatType.DIRECT,
            lastMessageText = "See you soon",
            lastMessageTime = "14:20",
            avatarInitials = "ER",
            avatarGradient = listOf(Color.Blue, Color.Cyan)
        )

        composeRule.setContent {
            val themeState = AppThemeState().apply {
                atmosphereMode = AtmosphereMode.MANUAL
                manualAtmosphere = TimeAtmospherePalette.DAY
            }
            CompositionLocalProvider(LocalAppThemeState provides themeState) {
                AetherTheme(themeState = themeState) {
                    com.foresightlabs.aether.ui.conversation.ConversationIdentityHeader(
                        chat = testChat,
                        onOpenProfile = { profileClicked = true },
                        onOpenSearch = { searchClicked = true },
                        pinned = null,
                        pinnedCount = 0,
                        pinnedIndex = 0,
                        canUnpin = false,
                        onPinnedClick = {},
                        onUnpin = {}
                    )
                }
            }
        }

        composeRule.waitForIdle()

        // 1. Search button must be displayed with 48dp touch target
        composeRule.onNodeWithTag("conversation_search_button")
            .assertIsDisplayed()
            .assertWidthIsEqualTo(48.dp)
            .assertHeightIsEqualTo(48.dp)

        // 2. Search button tap triggers onOpenSearch
        composeRule.onNodeWithTag("conversation_search_button").performClick()
        assertTrue("Search callback should be invoked", searchClicked)

        // 3. Profile click triggers onOpenProfile
        composeRule.onNodeWithTag("conversation_header_profile").performClick()
        assertTrue("Profile callback should be invoked", profileClicked)

        // 4. Overlow/More button must NOT exist in the Conversation header
        composeRule.onNodeWithTag("conversation_more_button").assertDoesNotExist()
        composeRule.onNodeWithTag("conversation_overflow_menu").assertDoesNotExist()
    }

    @Test
    fun testExpandingComposerDockRevealsAttachmentOptions() {
        var isExpanded by mutableStateOf(false)
        var gallerySelected = false
        var cameraSelected = false
        var videoNoteSelected = false
        var fileSelected = false
        var audioSelected = false
        var locationSelected = false
        var venueSelected = false
        var contactSelected = false

        composeRule.setContent {
            val themeState = AppThemeState().apply {
                atmosphereMode = AtmosphereMode.MANUAL
                manualAtmosphere = TimeAtmospherePalette.DAY
            }
            CompositionLocalProvider(LocalAppThemeState provides themeState) {
                AetherTheme(themeState = themeState) {
                    MessageComposer(
                        replyingTo = null,
                        onDismissReply = {},
                        onSendMessage = { _, _ -> },
                        isAttachmentExpanded = isExpanded,
                        onToggleAttachment = { isExpanded = !isExpanded },
                        onSelectGallery = { gallerySelected = true },
                        onSelectCamera = { cameraSelected = true },
                        onSelectVideoNote = { videoNoteSelected = true },
                        onSelectFile = { fileSelected = true },
                        onSelectAudio = { audioSelected = true },
                        onSelectLocation = { locationSelected = true },
                        onSelectVenue = { venueSelected = true },
                        onSelectContact = { contactSelected = true },
                        onVoiceNoteRecorded = {}
                    )
                }
            }
        }

        composeRule.waitForIdle()

        // Initially collapsed: attachment options are not visible
        composeRule.onNodeWithTag("attachment_option_gallery").assertDoesNotExist()
        composeRule.onNodeWithTag("attachment_option_camera").assertDoesNotExist()

        // Tap the [+] button to expand the composer dock
        composeRule.onNodeWithTag("attachment_button").performClick()
        composeRule.waitForIdle()
        assertTrue("isExpanded should be true", isExpanded)

        // Verify all 8 real attachment options are revealed with proper test tags
        composeRule.onNodeWithTag("attachment_option_gallery").assertIsDisplayed().performClick()
        assertTrue("Gallery should be selected", gallerySelected)

        composeRule.onNodeWithTag("attachment_option_camera").assertIsDisplayed().performClick()
        assertTrue("Camera should be selected", cameraSelected)

        composeRule.onNodeWithTag("attachment_option_video_note").assertIsDisplayed().performClick()
        assertTrue("Video note should be selected", videoNoteSelected)

        composeRule.onNodeWithTag("attachment_option_file").assertIsDisplayed().performClick()
        assertTrue("File should be selected", fileSelected)

        composeRule.onNodeWithTag("attachment_option_audio").assertIsDisplayed().performClick()
        assertTrue("Audio should be selected", audioSelected)

        composeRule.onNodeWithTag("attachment_option_location").assertIsDisplayed().performClick()
        assertTrue("Location should be selected", locationSelected)

        composeRule.onNodeWithTag("attachment_option_venue").assertIsDisplayed().performClick()
        assertTrue("Venue should be selected", venueSelected)

        composeRule.onNodeWithTag("attachment_option_contact").assertIsDisplayed().performClick()
        assertTrue("Contact should be selected", contactSelected)
    }
}
