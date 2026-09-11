package com.foresightlabs.aether.ui.conversation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Delete confirmation moved from a [androidx.compose.ui.window.Dialog] (a
 * genuinely separate Android window) into the Curtain as
 * [CurtainState.DELETE_CONFIRM] -- see [DeleteConfirmCurtainContent]'s own
 * doc for why. These exercise the content directly, the same way
 * [MediaPreviewCurtainContent]'s sibling states are tested elsewhere.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class DeleteConfirmCurtainContentTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun deleteForEveryoneHiddenWhenNotAllowed() {
        composeRule.setContent {
            DeleteConfirmCurtainContent(
                count = 1,
                canDeleteForAll = false,
                onCancel = {}, onDeleteForMe = {}, onDeleteForEveryone = {}
            )
        }
        composeRule.onNodeWithTag("delete_confirm_for_everyone").assertDoesNotExist()
        composeRule.onNodeWithTag("delete_confirm_for_me").assertExists()
    }

    @Test
    fun deleteForEveryoneShownWhenAllowed() {
        composeRule.setContent {
            DeleteConfirmCurtainContent(
                count = 1,
                canDeleteForAll = true,
                onCancel = {}, onDeleteForMe = {}, onDeleteForEveryone = {}
            )
        }
        composeRule.onNodeWithTag("delete_confirm_for_everyone").assertExists()
    }

    @Test
    fun tappingDeleteForMeInvokesCallbackExactlyOnce() {
        var calls = 0
        composeRule.setContent {
            DeleteConfirmCurtainContent(
                count = 1,
                canDeleteForAll = true,
                onCancel = {}, onDeleteForMe = { calls++ }, onDeleteForEveryone = {}
            )
        }
        composeRule.onNodeWithTag("delete_confirm_for_me").performClick()
        composeRule.onNodeWithTag("delete_confirm_for_me").performClick() // simulates a duplicate/queued tap
        assertEquals(1, calls)
    }

    @Test
    fun tappingDeleteForEveryoneInvokesCallbackExactlyOnce() {
        var calls = 0
        composeRule.setContent {
            DeleteConfirmCurtainContent(
                count = 1,
                canDeleteForAll = true,
                onCancel = {}, onDeleteForMe = {}, onDeleteForEveryone = { calls++ }
            )
        }
        composeRule.onNodeWithTag("delete_confirm_for_everyone").performClick()
        composeRule.onNodeWithTag("delete_confirm_for_everyone").performClick()
        assertEquals(1, calls)
    }

    @Test
    fun cancelInvokesCallback() {
        var cancelled = false
        composeRule.setContent {
            DeleteConfirmCurtainContent(
                count = 2,
                canDeleteForAll = false,
                onCancel = { cancelled = true }, onDeleteForMe = {}, onDeleteForEveryone = {}
            )
        }
        composeRule.onNodeWithTag("delete_confirm_cancel").performClick()
        assertEquals(true, cancelled)
    }
}
