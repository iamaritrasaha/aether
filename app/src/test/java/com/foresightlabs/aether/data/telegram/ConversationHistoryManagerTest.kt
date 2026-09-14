package com.foresightlabs.aether.data.telegram

import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.MessageStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationHistoryManagerTest {
    @Test
    fun emptyLocalFallsBackToServerBeforeEnd() = runTest {
        val calls = mutableListOf<Boolean>()
        var visible = emptyList<Message>()
        val manager = manager(
            load = { _, _, onlyLocal, _ ->
                calls += onlyLocal
                if (onlyLocal) emptyList() else listOf(message(10), message(11))
            },
            current = { visible },
            publish = { visible = (visible + it).distinctBy(Message::id) }
        )

        manager.initialize()

        assertEquals(listOf(true, false), calls)
        assertEquals(ConversationHistoryManager.State.READY, manager.state.value)
        assertEquals(listOf("10", "11"), visible.map { it.id })
        assertFalse(manager.serverEndReached)
    }

    @Test
    fun boundaryOverlapIsDeduplicatedAndOnlyAcceptedResultsAdvance() = runTest {
        var visible = emptyList<Message>()
        var serverCalls = 0
        val manager = manager(
            pageSize = 2,
            load = { boundary, _, onlyLocal, _ ->
                when {
                    onlyLocal && boundary == 0L -> listOf(message(20), message(21))
                    onlyLocal -> listOf(message(20)) // boundary only, no progress
                    else -> {
                        serverCalls++
                        listOf(message(18), message(19), message(20))
                    }
                }
            },
            current = { visible },
            publish = { visible = (visible + it).distinctBy(Message::id).sortedBy { m -> m.id.toLong() } }
        )
        manager.initialize()
        manager.loadOlder()

        assertEquals(listOf("18", "19", "20", "21"), visible.map { it.id })
        assertEquals(18L, manager.oldestMessageId)
        assertEquals(1, serverCalls)
    }

    @Test
    fun concurrentOlderRequestsAreSerializedByOneManager() = runTest {
        var visible = listOf(message(50), message(51))
        var active = 0
        var maxActive = 0
        val manager = manager(
            pageSize = 2,
            load = { boundary, _, onlyLocal, _ ->
                active++
                maxActive = maxOf(maxActive, active)
                kotlinx.coroutines.yield()
                active--
                when {
                    boundary == 0L && onlyLocal -> visible
                    onlyLocal -> emptyList()
                    else -> emptyList()
                }
            },
            current = { visible },
            publish = { visible = (visible + it).distinctBy(Message::id) }
        )
        manager.initialize()
        val first = async { manager.loadOlder() }
        val second = async { manager.loadOlder() }
        first.await()
        second.await()

        assertEquals(1, maxActive)
        assertTrue(manager.serverEndReached)
    }

    private fun manager(
        pageSize: Int = 40,
        load: suspend (Long, Int, Boolean, String) -> List<Message>,
        current: () -> List<Message>,
        publish: (List<Message>) -> Unit
    ) = ConversationHistoryManager(pageSize, true, load, current, publish)

    private fun message(id: Long) = Message(
        id = id.toString(),
        chatId = "1",
        senderId = "2",
        senderName = "Sender",
        text = "m$id",
        timestamp = "",
        dateSeconds = id.toInt(),
        isOutgoing = false,
        status = MessageStatus.SENT
    )
}
