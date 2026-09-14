package com.foresightlabs.aether.ui.conversation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
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
 * Regression test for Video Note Curtain Chrome ownership:
 *
 * Asserts that when opening Video Note through Conversation -> Attachment -> Video Note:
 * 1. Exactly ONE "Video note" header title is displayed.
 * 2. NO secondary "Video Message" title exists.
 * 3. Exactly ONE Curtain-level dismiss affordance ("video_note_cancel") is displayed.
 * 4. Exactly ONE back affordance ("video_note_back") is displayed.
 * 5. Exactly ONE canonical surface ("conversation_curtain") exists.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi", application = Application::class)
class VideoNoteChromeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun videoNoteCurtainHasExactlyOneHeaderAndOneDismissAffordance() {
        val state = mutableStateOf(CurtainState.ATTACHMENTS)
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
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        // Real user flow: Attachment -> Video Note
        composeRule.onNodeWithTag("attachment_option_video_note").performClick()
        composeRule.waitForIdle()

        assertEquals(CurtainState.VIDEO_NOTE, state.value)
        composeRule.onNodeWithTag("curtain_video_note_content").assertIsDisplayed()

        // Verify ONE canonical surface
        composeRule.onAllNodesWithTag("conversation_curtain").assertCountEquals(1)

        // Verify exactly ONE "Video note" title exists
        composeRule.onAllNodesWithText("Video note").assertCountEquals(1)

        // Verify NO secondary "Video Message" title exists
        composeRule.onAllNodesWithText("Video Message").assertCountEquals(0)

        // Verify exactly ONE back affordance
        composeRule.onAllNodesWithTag("video_note_back").assertCountEquals(1)

        // Verify exactly ONE cancel/close affordance
        composeRule.onAllNodesWithTag("video_note_cancel").assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription("Cancel").assertCountEquals(1)

        // Verify back navigation returns to ATTACHMENTS
        composeRule.onNodeWithTag("video_note_back").performClick()
        composeRule.waitForIdle()
        assertEquals(CurtainState.ATTACHMENTS, state.value)

        // Verify cancel navigation closes to COMPOSER
        composeRule.onNodeWithTag("attachment_option_video_note").performClick()
        composeRule.waitForIdle()
        assertEquals(CurtainState.VIDEO_NOTE, state.value)

        composeRule.onNodeWithTag("video_note_cancel").performClick()
        composeRule.waitForIdle()
        assertEquals(CurtainState.COMPOSER, state.value)
    }
}
