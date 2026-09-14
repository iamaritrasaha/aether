package com.foresightlabs.aether.ui.conversation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
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
 * Verifies that all attachment flows (Gallery, Files, Music, Video Note,
 * Contact, Location, Venue) are first-class child states of the Conversation
 * Curtain.
 *
 * Each child state must back-navigate to [CurtainState.ATTACHMENTS], while
 * explicit Cancel dismisses directly to [CurtainState.COMPOSER].
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi", application = Application::class)
class ConversationCurtainInteractionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun galleryFlowBackNavigatesToAttachmentsAndCancelDismissesToComposer() {
        val state = showCurtain(CurtainState.ATTACHMENTS)

        composeRule.onNodeWithTag("attachment_option_gallery").performClick()
        composeRule.waitForIdle()

        assertEquals(CurtainState.GALLERY, state.value)
        composeRule.onNodeWithTag("curtain_gallery_content").assertIsDisplayed()
        composeRule.onAllNodesWithTag("conversation_curtain").assertCountEquals(1)

        // Back from Gallery returns to ATTACHMENTS
        composeRule.onNodeWithTag("gallery_back").performClick()
        composeRule.waitForIdle()

        assertEquals(CurtainState.ATTACHMENTS, state.value)
        composeRule.onNodeWithTag("curtain_attachment_content").assertIsDisplayed()

        // Open Gallery again, then Cancel dismisses directly to COMPOSER
        composeRule.onNodeWithTag("attachment_option_gallery").performClick()
        composeRule.waitForIdle()
        assertEquals(CurtainState.GALLERY, state.value)

        composeRule.onNodeWithTag("gallery_cancel").performClick()
        composeRule.waitForIdle()

        assertEquals(CurtainState.COMPOSER, state.value)
    }

    @Test
    fun filesFlowBackNavigatesToAttachmentsAndCancelDismissesToComposer() {
        val state = showCurtain(CurtainState.ATTACHMENTS)

        composeRule.onNodeWithTag("attachment_option_file").performClick()
        composeRule.waitForIdle()

        assertEquals(CurtainState.FILES, state.value)
        composeRule.onNodeWithTag("curtain_files_content").assertIsDisplayed()
        composeRule.onAllNodesWithTag("conversation_curtain").assertCountEquals(1)

        // Back from Files returns to ATTACHMENTS
        composeRule.onNodeWithTag("files_back").performClick()
        composeRule.waitForIdle()

        assertEquals(CurtainState.ATTACHMENTS, state.value)
        composeRule.onNodeWithTag("curtain_attachment_content").assertIsDisplayed()

        // Open Files again, then Cancel dismisses to COMPOSER
        composeRule.onNodeWithTag("attachment_option_file").performClick()
        composeRule.waitForIdle()
        assertEquals(CurtainState.FILES, state.value)

        composeRule.onNodeWithTag("files_cancel").performClick()
        composeRule.waitForIdle()

        assertEquals(CurtainState.COMPOSER, state.value)
    }

    @Test
    fun musicFlowBackNavigatesToAttachmentsAndCancelDismissesToComposer() {
        val state = showCurtain(CurtainState.ATTACHMENTS)

        composeRule.onNodeWithTag("attachment_option_audio").performClick()
        composeRule.waitForIdle()

        assertEquals(CurtainState.MUSIC, state.value)
        composeRule.onNodeWithTag("curtain_music_content").assertIsDisplayed()
        composeRule.onAllNodesWithTag("conversation_curtain").assertCountEquals(1)

        // Back from Music returns to ATTACHMENTS
        composeRule.onNodeWithTag("music_back").performClick()
        composeRule.waitForIdle()

        assertEquals(CurtainState.ATTACHMENTS, state.value)
        composeRule.onNodeWithTag("curtain_attachment_content").assertIsDisplayed()

        // Open Music again, then Cancel dismisses to COMPOSER
        composeRule.onNodeWithTag("attachment_option_audio").performClick()
        composeRule.waitForIdle()
        assertEquals(CurtainState.MUSIC, state.value)

        composeRule.onNodeWithTag("music_cancel").performClick()
        composeRule.waitForIdle()

        assertEquals(CurtainState.COMPOSER, state.value)
    }

    @Test
    fun videoNoteFlowBackNavigatesToAttachmentsAndCancelDismissesToComposer() {
        val state = showCurtain(CurtainState.ATTACHMENTS)

        composeRule.onNodeWithTag("attachment_option_video_note").performClick()
        composeRule.waitForIdle()

        assertEquals(CurtainState.VIDEO_NOTE, state.value)
        composeRule.onNodeWithTag("curtain_video_note_content").assertIsDisplayed()
        composeRule.onAllNodesWithTag("conversation_curtain").assertCountEquals(1)

        // Back from Video note returns to ATTACHMENTS
        composeRule.onNodeWithTag("video_note_back").performClick()
        composeRule.waitForIdle()

        assertEquals(CurtainState.ATTACHMENTS, state.value)
        composeRule.onNodeWithTag("curtain_attachment_content").assertIsDisplayed()

        // Open Video note again, then Cancel dismisses to COMPOSER
        composeRule.onNodeWithTag("attachment_option_video_note").performClick()
        composeRule.waitForIdle()
        assertEquals(CurtainState.VIDEO_NOTE, state.value)

        composeRule.onNodeWithTag("video_note_cancel").performClick()
        composeRule.waitForIdle()

        assertEquals(CurtainState.COMPOSER, state.value)
    }

    @Test
    fun contactFlowBackNavigatesToAttachmentsAndCancelDismissesToComposer() {
        val state = showCurtain(CurtainState.ATTACHMENTS)

        composeRule.onNodeWithTag("attachment_option_contact").performClick()
        composeRule.waitForIdle()

        assertEquals(CurtainState.CONTACT, state.value)
        composeRule.onNodeWithTag("contact_share_sheet").assertIsDisplayed()
        composeRule.onAllNodesWithTag("conversation_curtain").assertCountEquals(1)

        // Back from Contact returns to ATTACHMENTS
        composeRule.onNodeWithTag("contact_share_sheet_back").performClick()
        composeRule.waitForIdle()

        assertEquals(CurtainState.ATTACHMENTS, state.value)
        composeRule.onNodeWithTag("curtain_attachment_content").assertIsDisplayed()

        // Open Contact again, then Cancel dismisses to COMPOSER
        composeRule.onNodeWithTag("attachment_option_contact").performClick()
        composeRule.waitForIdle()
        assertEquals(CurtainState.CONTACT, state.value)

        composeRule.onNodeWithTag("contact_share_sheet_cancel").performClick()
        composeRule.waitForIdle()

        assertEquals(CurtainState.COMPOSER, state.value)
    }

    @Test
    fun locationFlowBackNavigatesToAttachmentsAndCancelDismissesToComposer() {
        val state = showCurtain(CurtainState.ATTACHMENTS)

        composeRule.onNodeWithTag("attachment_option_location").performClick()
        composeRule.waitForIdle()

        assertEquals(CurtainState.LOCATION, state.value)
        composeRule.onNodeWithTag("location_share_sheet").assertIsDisplayed()
        composeRule.onAllNodesWithTag("conversation_curtain").assertCountEquals(1)

        // Back from Location returns to ATTACHMENTS
        composeRule.onNodeWithTag("location_share_sheet_back").performClick()
        composeRule.waitForIdle()

        assertEquals(CurtainState.ATTACHMENTS, state.value)
        composeRule.onNodeWithTag("curtain_attachment_content").assertIsDisplayed()

        // Open Location again, then Cancel dismisses to COMPOSER
        composeRule.onNodeWithTag("attachment_option_location").performClick()
        composeRule.waitForIdle()
        assertEquals(CurtainState.LOCATION, state.value)

        composeRule.onNodeWithTag("location_share_sheet_cancel").performClick()
        composeRule.waitForIdle()

        assertEquals(CurtainState.COMPOSER, state.value)
    }

    @Test
    fun venueFlowBackNavigatesToAttachmentsAndCancelDismissesToComposer() {
        val state = showCurtain(CurtainState.ATTACHMENTS)

        composeRule.onNodeWithTag("attachment_option_venue").performClick()
        composeRule.waitForIdle()

        assertEquals(CurtainState.VENUE, state.value)
        composeRule.onNodeWithTag("venue_share_sheet").assertIsDisplayed()
        composeRule.onAllNodesWithTag("conversation_curtain").assertCountEquals(1)

        // Back from Venue returns to ATTACHMENTS
        composeRule.onNodeWithTag("venue_share_sheet_back").performClick()
        composeRule.waitForIdle()

        assertEquals(CurtainState.ATTACHMENTS, state.value)
        composeRule.onNodeWithTag("curtain_attachment_content").assertIsDisplayed()

        // Open Venue again, then Cancel dismisses to COMPOSER
        composeRule.onNodeWithTag("attachment_option_venue").performClick()
        composeRule.waitForIdle()
        assertEquals(CurtainState.VENUE, state.value)

        composeRule.onNodeWithTag("venue_share_sheet_cancel").performClick()
        composeRule.waitForIdle()

        assertEquals(CurtainState.COMPOSER, state.value)
    }

    private fun showCurtain(initial: CurtainState): androidx.compose.runtime.MutableState<CurtainState> {
        val state = mutableStateOf(initial)
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
                    Box(modifier = Modifier.fillMaxSize()) {
                        MessageComposer(
                            replyingTo = null,
                            onDismissReply = {},
                            onSendMessage = { _, _ -> },
                            curtainState = state.value,
                            onCurtainStateChange = { state.value = it },
                            onSelectGallery = { state.value = CurtainState.GALLERY },
                            onSelectFile = { state.value = CurtainState.FILES },
                            onSelectAudio = { state.value = CurtainState.MUSIC },
                            onSelectVideoNote = { state.value = CurtainState.VIDEO_NOTE },
                            onSelectLocation = { state.value = CurtainState.LOCATION },
                            onSelectVenue = { state.value = CurtainState.VENUE },
                            onSelectContact = { state.value = CurtainState.CONTACT },
                            onCancelContact = { state.value = CurtainState.COMPOSER },
                            onCancelLocation = { state.value = CurtainState.COMPOSER },
                            onCancelVenue = { state.value = CurtainState.COMPOSER },
                            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        return state
    }
}
