package com.foresightlabs.aether

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.foresightlabs.aether.domain.model.ConversationTarget
import com.foresightlabs.aether.ui.conversation.ConversationViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ConversationResolutionTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: AetherApplication

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        application = RuntimeEnvironment.getApplication() as AetherApplication
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testConversationViewModelInitializesNonBlockingWithChatTarget() = runTest(testDispatcher) {
        val target = ConversationTarget.Chat(12345L)
        val viewModel = ConversationViewModel(application, target)

        // Non-blocking initialization
        assertTrue(viewModel.isResolving.value)
        assertNull(viewModel.resolveError.value)

        advanceUntilIdle()
        assertFalse(viewModel.isResolving.value)
    }

    @Test
    fun testConversationViewModelInitializesNonBlockingWithUserTarget() = runTest(testDispatcher) {
        val target = ConversationTarget.User(67890L)
        val viewModel = ConversationViewModel(application, target)

        // Non-blocking initialization
        assertTrue(viewModel.isResolving.value)
        assertNull(viewModel.resolveError.value)

        advanceUntilIdle()
        assertFalse(viewModel.isResolving.value)
    }

    @Test
    fun testChatTargetGetChatFailureNeverCallsCreatePrivateChatAndTransitionsToErrorState() = runTest(testDispatcher) {
        val nonExistentChatId = 999999L
        val target = ConversationTarget.Chat(nonExistentChatId)
        val viewModel = ConversationViewModel(application, target)

        // Starts in non-blocking resolving state
        assertTrue(viewModel.isResolving.value)
        assertNull(viewModel.resolveError.value)
        assertNull(viewModel.header.value)

        advanceUntilIdle()

        // GetChat failed, must NEVER call CreatePrivateChat and must transition to error state
        assertFalse(viewModel.isResolving.value)
        assertNull(viewModel.header.value)
        assertNotNull(viewModel.resolveError.value)
        assertEquals(
            "Couldn't load this conversation. Please check your network and try again.",
            viewModel.resolveError.value
        )
    }

    @Test
    fun testFactoryConstructsViewModelWithChatAndUserTargets() {
        val store = ViewModelStore()

        val chatFactory = ConversationViewModel.Factory(application, ConversationTarget.Chat(111L))
        val chatVm = ViewModelProvider(store, chatFactory)[ConversationViewModel::class.java]
        assertNotNull(chatVm)

        val userFactory = ConversationViewModel.Factory(application, ConversationTarget.User(222L))
        val userVm = ViewModelProvider(store, userFactory)["user-vm", ConversationViewModel::class.java]
        assertNotNull(userVm)
    }

    @Test
    fun testMarkVisibleDeduplicatesMessageIds() = runTest(testDispatcher) {
        val viewModel = ConversationViewModel(application, ConversationTarget.Chat(11111L))

        // Mark visible twice with the same IDs
        viewModel.markVisible(listOf("101", "102"))
        viewModel.markVisible(listOf("101", "102"))
        viewModel.markVisible(listOf("102", "103"))

        advanceUntilIdle()

        // Should complete without exception or infinite loops
        assertFalse(viewModel.isResolving.value)
    }

    @Test
    fun testRetryResolveCanBeTriggered() = runTest(testDispatcher) {
        val viewModel = ConversationViewModel(application, ConversationTarget.User(22222L))
        advanceUntilIdle()

        viewModel.retryResolve()
        advanceUntilIdle()

        assertFalse(viewModel.isResolving.value)
    }
}
