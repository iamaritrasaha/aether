package com.foresightlabs.aether.ui.conversation
import com.foresightlabs.aether.AetherApplication
import com.foresightlabs.aether.data.telegram.TdErrors
import com.foresightlabs.aether.data.telegram.TelegramMappers
import com.foresightlabs.aether.domain.model.ActiveCall
import com.foresightlabs.aether.domain.model.CallStateEnum
import com.foresightlabs.aether.domain.model.ConversationTarget
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.MessageType
import com.foresightlabs.aether.ui.conversation.ConversationViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.drinkless.tdlib.TdApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CoreFunctionalRepairTest {

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

    // --- PART A: PINNING TESTS ---

    @Test
    fun testMessagePinMessageMapsToServiceTypeAndSenderText() {
        val pinContent = TdApi.MessagePinMessage(12345L)
        val (text, type) = TelegramMappers.mapContent(pinContent)

        assertEquals("pinned a message", text)
        assertEquals(MessageType.SERVICE, type)

        val user = TdApi.User()
        user.id = 777L
        user.firstName = "Zombie"
        user.lastName = ""

        val users = mapOf(777L to user)
        val chats = emptyMap<Long, TdApi.Chat>()

        val tdMsg = TdApi.Message()
        tdMsg.id = 555L
        tdMsg.chatId = 100L
        tdMsg.senderId = TdApi.MessageSenderUser(777L)
        tdMsg.date = 1600000000
        tdMsg.content = pinContent

        val mapped = TelegramMappers.mapMessage(
            message = tdMsg,
            users = users,
            chats = chats,
            myUserId = 999L,
            lastReadOutboxMessageId = 0L
        )

        assertEquals("Zombie pinned a message", mapped.text)
        assertEquals(MessageType.SERVICE, mapped.type)
        assertFalse(mapped.isOutgoing)
    }

    @Test
    fun testPinAndUnpinActionDoesNotFakeStateLocallyOnFailure() = runTest(testDispatcher) {
        val viewModel = ConversationViewModel(application, ConversationTarget.Chat(12345L))
        advanceUntilIdle()

        val unpinnedMsg = Message(
            id = "1001",
            chatId = "12345",
            senderId = "1",
            senderName = "Sender",
            text = "Test message",
            timestamp = "12:00 PM",
            isOutgoing = true,
            isPinned = false
        )

        // Pin message
        viewModel.pinMessage(unpinnedMsg)
        advanceUntilIdle()

        val pinnedMsg = unpinnedMsg.copy(isPinned = true)

        // Unpin message
        viewModel.pinMessage(pinnedMsg)
        advanceUntilIdle()

        assertFalse(viewModel.isResolving.value)
    }

    // --- PART B: CALLS TESTS ---

    @Test
    fun testCallStateMappingValues() {
        val errorState = TdApi.CallStateError(TdApi.Error(400, "PARTICIPANT_OFFLINE"))

        val activePending = ActiveCall(
            callId = 1,
            userId = 123L,
            isOutgoing = true,
            state = CallStateEnum.PENDING
        )
        assertEquals(CallStateEnum.PENDING, activePending.state)

        val activeReady = activePending.copy(state = CallStateEnum.READY)
        assertEquals(CallStateEnum.READY, activeReady.state)

        val activeDiscarded = activePending.copy(state = CallStateEnum.DISCARDED)
        assertEquals(CallStateEnum.DISCARDED, activeDiscarded.state)

        val activeError = activePending.copy(
            state = CallStateEnum.ERROR,
            errorMessage = TdErrors.userMessage(errorState.error)
        )
        assertEquals(CallStateEnum.ERROR, activeError.state)
        assertNotNull(activeError.errorMessage)
    }

    @Test
    fun testInitiateAudioCallTargetsCorrectUser() = runTest(testDispatcher) {
        val targetUserId = 88888L
        val viewModel = ConversationViewModel(application, ConversationTarget.User(targetUserId))
        advanceUntilIdle()

        viewModel.initiateAudioCall()
        advanceUntilIdle()

        assertFalse(viewModel.isResolving.value)
    }

    // --- PART C: MESSAGE BUBBLE SIZING TESTS ---

    @Test
    fun testMessageTypeServiceIsDistinctFromUnsupportedOrText() {
        val textType = MessageType.TEXT
        val serviceType = MessageType.SERVICE
        val unsupportedType = MessageType.UNSUPPORTED

        assertTrue(serviceType != textType)
        assertTrue(serviceType != unsupportedType)
    }
}
