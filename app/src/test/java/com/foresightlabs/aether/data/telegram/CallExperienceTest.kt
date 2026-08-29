package com.foresightlabs.aether.data.telegram
import com.foresightlabs.aether.data.telegram.TelegramCallMessageMapper
import com.foresightlabs.aether.domain.model.ActiveCall
import com.foresightlabs.aether.domain.model.CallHistoryItem
import com.foresightlabs.aether.domain.model.CallHistoryUiState
import com.foresightlabs.aether.domain.model.CallOutcome
import com.foresightlabs.aether.domain.model.CallStateEnum
import com.foresightlabs.aether.domain.model.User
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallExperienceTest {

    @Test
    fun callOutcomeMappingCorrect() {
        assertEquals(
            CallOutcome.MISSED,
            TelegramCallMessageMapper.mapOutcome(TdApi.CallDiscardReasonMissed(), isOutgoing = false, duration = 0)
        )
        assertEquals(
            CallOutcome.CANCELLED,
            TelegramCallMessageMapper.mapOutcome(TdApi.CallDiscardReasonMissed(), isOutgoing = true, duration = 0)
        )
        assertEquals(
            CallOutcome.DECLINED,
            TelegramCallMessageMapper.mapOutcome(TdApi.CallDiscardReasonDeclined(), isOutgoing = true, duration = 0)
        )
        assertEquals(
            CallOutcome.COMPLETED,
            TelegramCallMessageMapper.mapOutcome(TdApi.CallDiscardReasonHungUp(), isOutgoing = false, duration = 120)
        )
        assertEquals(
            CallOutcome.FAILED,
            TelegramCallMessageMapper.mapOutcome(TdApi.CallDiscardReasonDisconnected(), isOutgoing = false, duration = 0)
        )
    }

    @Test
    fun callHistoryItemMapping() {
        val user = User(
            id = "12345",
            name = "Aritra Saha",
            username = "@aritra",
            avatarInitials = "AS",
            avatarGradient = emptyList()
        )

        val message = TdApi.Message().apply {
            id = 999L
            chatId = 12345L
            isOutgoing = true
            date = 1724750000
            content = TdApi.MessageCall(111L, false, TdApi.CallDiscardReasonHungUp(), 240)
        }

        val item = TelegramCallMessageMapper.mapToCallHistoryItem(message, user)
        assertNotNull(item)
        assertEquals("999", item?.id)
        assertEquals(12345L, item?.chatId)
        assertEquals(12345L, item?.userId)
        assertEquals("Aritra Saha", item?.user?.name)
        assertTrue(item?.isOutgoing == true)
        assertFalse(item?.isVideo == true)
        assertEquals(CallOutcome.COMPLETED, item?.outcome)
        assertEquals(240, item?.durationSeconds)
        assertEquals("4 min", item?.formattedDuration)
    }

    @Test
    fun durationFormatting() {
        assertEquals("", TelegramCallMessageMapper.formatDuration(0))
        assertEquals("45 sec", TelegramCallMessageMapper.formatDuration(45))
        assertEquals("2 min 18 sec", TelegramCallMessageMapper.formatDuration(138))
        assertEquals("1 hr 5 min", TelegramCallMessageMapper.formatDuration(3900))
    }

    @Test
    fun callMessagePresentationFormatting() {
        val call = TdApi.MessageCall(222L, false, TdApi.CallDiscardReasonHungUp(), 138)
        val outgoingPresentation = TelegramCallMessageMapper.formatCallMessagePresentation(call, isOutgoing = true)
        assertTrue(outgoingPresentation.contains("Outgoing voice call"))
        assertTrue(outgoingPresentation.contains("2 min 18 sec"))

        val missedCall = TdApi.MessageCall(223L, false, TdApi.CallDiscardReasonMissed(), 0)
        val missedPresentation = TelegramCallMessageMapper.formatCallMessagePresentation(missedCall, isOutgoing = false)
        assertTrue(missedPresentation.contains("Missed voice call"))
    }

    @Test
    fun activeCallStateTransitions() {
        val call = ActiveCall(
            callId = 1,
            userId = 1001L,
            isOutgoing = true,
            state = CallStateEnum.PENDING
        )
        assertEquals(CallStateEnum.PENDING, call.state)
        assertEquals(0, call.durationSec)

        val connectedCall = call.copy(state = CallStateEnum.READY, durationSec = 15)
        assertEquals(CallStateEnum.READY, connectedCall.state)
        assertEquals(15, connectedCall.durationSec)

        val endedCall = connectedCall.copy(state = CallStateEnum.DISCARDED)
        assertEquals(CallStateEnum.DISCARDED, endedCall.state)
    }

    @Test
    fun callHistoryUiStates() {
        val loading = CallHistoryUiState.Loading
        assertTrue(loading is CallHistoryUiState.Loading)

        val empty = CallHistoryUiState.Empty
        assertTrue(empty is CallHistoryUiState.Empty)

        val error = CallHistoryUiState.Error("Failed to fetch")
        assertEquals("Failed to fetch", error.message)

        val item = CallHistoryItem(
            id = "1",
            messageId = 1L,
            chatId = 1001L,
            userId = 1001L,
            isOutgoing = true,
            outcome = CallOutcome.COMPLETED,
            durationSeconds = 60
        )
        val content = CallHistoryUiState.Content(items = listOf(item), hasMore = true, nextOffset = "offset_1")
        assertEquals(1, content.items.size)
        assertTrue(content.hasMore)
        assertEquals("offset_1", content.nextOffset)
    }
}
