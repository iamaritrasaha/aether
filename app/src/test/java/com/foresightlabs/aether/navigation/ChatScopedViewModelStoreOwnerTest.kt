package com.foresightlabs.aether.navigation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Regression coverage for the tablet Conversation pane's ViewModel scoping.
 *
 * Tablet layout hosts Conversation outside NavHost's own back-stack scoping (see
 * [AetherApp]'s tablet branch), so without [ChatScopedViewModelStoreOwner] every
 * chat ever selected keeps its ConversationViewModel -- and that ViewModel's
 * long-lived collectors -- alive for the rest of the process. These tests assert
 * the store a chat's content is scoped to actually gets cleared when the selected
 * chat id changes, and that a fresh chat id gets a fresh ViewModel instance.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class ChatScopedViewModelStoreOwnerTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun changingKeyClearsThePreviousChatsStoreAndCreatesAFreshViewModel() {
        var key by mutableStateOf<Any>("chat-1")
        var firstVm: MarkerViewModel? = null
        var secondVm: MarkerViewModel? = null

        composeRule.setContent {
            ChatScopedViewModelStoreOwner(key = key) {
                val vm: MarkerViewModel = viewModel()
                if (key == "chat-1") firstVm = vm else secondVm = vm
            }
        }
        composeRule.waitForIdle()
        val capturedFirst = firstVm
        assertNotNull("expected a ViewModel for chat-1", capturedFirst)
        assertFalse(capturedFirst!!.cleared)

        composeRule.runOnUiThread { key = "chat-2" }
        composeRule.waitForIdle()

        assertTrue(
            "switching chats must clear the previous chat's ViewModelStore, " +
                "not leak its collectors for the rest of the process",
            capturedFirst.cleared
        )
        assertNotNull("expected a ViewModel for chat-2", secondVm)
        assertNotSame(
            "the new chat must get its own ViewModel, not reuse the old one",
            capturedFirst,
            secondVm
        )
        assertFalse(secondVm!!.cleared)
    }

    @Test
    fun sameKeyAcrossRecompositionKeepsTheSameViewModel() {
        val key: Any = "chat-1"
        var firstVm: MarkerViewModel? = null
        var recomposeTrigger by mutableStateOf(0)

        composeRule.setContent {
            recomposeTrigger.let { /* read to force recomposition below */ }
            ChatScopedViewModelStoreOwner(key = key) {
                val vm: MarkerViewModel = viewModel()
                firstVm = vm
            }
        }
        composeRule.waitForIdle()
        val captured = firstVm

        composeRule.runOnUiThread { recomposeTrigger++ }
        composeRule.waitForIdle()

        assertFalse("an unrelated recomposition must not clear a live chat's store", captured!!.cleared)
        assertTrue("the same key must keep resolving to the same ViewModel instance", firstVm === captured)
    }
}

/** Top-level (not nested) so the default [ViewModelProvider.Factory] can reflectively construct it. */
class MarkerViewModel : ViewModel() {
    var cleared = false
        private set

    override fun onCleared() {
        cleared = true
    }
}
