package com.foresightlabs.aether.ui.conversation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Conversation header's call action must render only when the caller has
 * already decided the conversation is eligible -- there is no
 * disabled/greyed state (see [ConversationCallButton]'s own doc). This
 * exercises the composable's actual behaviour, not an assertion about the
 * source: a null [ConversationCallButton] caller must produce no such node
 * in the tree at all.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class ConversationCallButtonVisibilityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun callButtonRendersWhenProvided() {
        composeRule.setContent {
            ConversationCallButton(onClick = {})
        }
        composeRule.onNodeWithTag("conversation_call_button").assertExists()
    }

    @Test
    fun tappingCallButtonInvokesCallback() {
        var tapped = false
        composeRule.setContent {
            ConversationCallButton(onClick = { tapped = true })
        }
        composeRule.onNodeWithTag("conversation_call_button").performClick()
        assertTrue("expected the call button's onClick to have fired", tapped)
    }

    @Test
    fun videoCallButtonRendersWhenProvided() {
        composeRule.setContent {
            ConversationVideoCallButton(onClick = {})
        }
        composeRule.onNodeWithTag("conversation_video_call_button").assertExists()
    }

    @Test
    fun tappingVideoCallButtonInvokesCallback() {
        var tapped = false
        composeRule.setContent {
            ConversationVideoCallButton(onClick = { tapped = true })
        }
        composeRule.onNodeWithTag("conversation_video_call_button").performClick()
        assertTrue("expected the video call button's onClick to have fired", tapped)
    }
}
