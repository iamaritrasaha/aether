package com.foresightlabs.aether.data.telegram

import com.foresightlabs.aether.domain.model.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One open chat's sole owner of history boundaries and request state. */
class ConversationHistoryManager(
    private val pageSize: Int,
    private val localFirst: Boolean,
    private val load: suspend (fromMessageId: Long, limit: Int, onlyLocal: Boolean, reason: String) -> List<Message>,
    private val current: () -> List<Message>,
    private val publish: (List<Message>) -> Unit,
    private val onState: (State) -> Unit = {}
) {
    enum class State { INITIAL, LOADING, READY, LOADING_OLDER, END_REACHED, FAILED, DESTROYED }

    private val mutex = Mutex()
    private val acceptedIds = LinkedHashSet<String>()
    private val _state = MutableStateFlow(State.INITIAL)
    private val _messages = MutableStateFlow<List<Message>>(emptyList())

    val state: StateFlow<State> = _state.asStateFlow()
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()
    val requestInFlight: Boolean
        get() = _state.value == State.LOADING || _state.value == State.LOADING_OLDER

    var oldestMessageId: Long = 0L
        private set
    var newestMessageId: Long = 0L
        private set
    var localEndReached: Boolean = false
        private set
    var serverEndReached: Boolean = false
        private set

    suspend fun initialize() = mutex.withLock {
        if (_state.value == State.DESTROYED) return@withLock
        acceptedIds.clear()
        sync(current())
        transition(State.LOADING)
        try {
            if (localFirst) {
                val local = load(0L, pageSize, true, "INITIAL_LOCAL")
                val localAccepted = accept(local, boundary = 0L)
                localEndReached = local.size < pageSize
                if (localAccepted.isNotEmpty()) publish(localAccepted)
                sync(current())
                if (!localEndReached) {
                    transition(State.READY)
                    return@withLock
                }
            }

            // Empty/short local data exhausts only TDLib's cache. The same
            // manager immediately continues with a server-capable request.
            val boundary = oldestMessageId
            val server = load(boundary, pageSize + if (boundary != 0L) 1 else 0, false, "INITIAL_SERVER")
            val accepted = accept(server, boundary)
            if (accepted.isNotEmpty()) publish(accepted)
            sync(current())
            serverEndReached = accepted.isEmpty()
            transition(if (serverEndReached) State.END_REACHED else State.READY)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (_state.value != State.DESTROYED) transition(State.FAILED)
        }
    }

    suspend fun loadOlder() = mutex.withLock {
        if (_state.value == State.DESTROYED || _state.value == State.END_REACHED || oldestMessageId == 0L) return@withLock
        transition(State.LOADING_OLDER)
        try {
            val startBoundary = oldestMessageId
            if (localFirst && !localEndReached) {
                val local = load(startBoundary, pageSize + 1, true, "OLDER_LOCAL")
                val accepted = accept(local, startBoundary)
                localEndReached = accepted.size < pageSize
                if (accepted.isNotEmpty()) publish(accepted)
                sync(current())
            }
            if (!localFirst || localEndReached) {
                val boundary = oldestMessageId
                val server = load(boundary, pageSize + 1, false, "OLDER_SERVER")
                val accepted = accept(server, boundary)
                if (accepted.isNotEmpty()) publish(accepted)
                sync(current())
                serverEndReached = accepted.isEmpty()
            }
            transition(if (serverEndReached) State.END_REACHED else State.READY)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (_state.value != State.DESTROYED) transition(State.FAILED)
        }
    }

    /** Includes a jump window without letting it create an independent cursor. */
    fun include(messages: List<Message>) {
        accept(messages, boundary = 0L)
        sync(current())
    }

    fun sync(messages: List<Message>) {
        val ordered = messages.distinctBy { it.id }.sortedWith(messageOrder)
        _messages.value = ordered
        acceptedIds.addAll(ordered.map { it.id })
        val ids = ordered.mapNotNull { it.id.toLongOrNull() }
        if (ids.isNotEmpty()) {
            oldestMessageId = ids.min()
            newestMessageId = ids.max()
        }
    }

    fun destroy() {
        transition(State.DESTROYED)
    }

    private fun accept(incoming: List<Message>, boundary: Long): List<Message> {
        val accepted = incoming
            .filter { it.id.toLongOrNull() != boundary }
            .distinctBy { it.id }
            .filter { acceptedIds.add(it.id) }
            .sortedWith(messageOrder)
        val ids = accepted.mapNotNull { it.id.toLongOrNull() }
        if (ids.isNotEmpty()) {
            val pageOldest = ids.min()
            val pageNewest = ids.max()
            oldestMessageId = if (oldestMessageId == 0L) pageOldest else minOf(oldestMessageId, pageOldest)
            newestMessageId = maxOf(newestMessageId, pageNewest)
        }
        return accepted
    }

    private fun transition(next: State) {
        _state.value = next
        onState(next)
    }

    private companion object {
        val messageOrder = compareBy<Message>({ it.dateSeconds }, { it.id.toLongOrNull() ?: 0L })
    }
}
