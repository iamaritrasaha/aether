package com.foresightlabs.aether.data.push

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Tracks TDLib's `UpdateHavePendingNotifications` state and answers, safely,
 * whether a fetch a caller just kicked off has actually completed.
 *
 * A plain `StateFlow<Boolean>` gated with `first { !it }` is NOT safe for
 * this: TDLib's last known state can already be "not pending" left over from
 * an earlier, unrelated cycle -- e.g. nothing was pending before this push
 * arrived at all. `first { !it }` would then report completion instantly,
 * having observed nothing from the cycle this call actually cares about.
 *
 * Every update instead carries a monotonically increasing generation.
 * [awaitCompletion] requires a generation strictly greater than the one the
 * caller recorded (via [currentGeneration]) before starting its fetch, so a
 * value merely left over from before the wait began can never satisfy it --
 * only a genuinely new update, observed after the cycle started, can.
 */
class PushPendingGate {
    data class State(
        val generation: Long = 0L,
        val haveDelayed: Boolean = false,
        val haveUnreceived: Boolean = false
    )

    enum class Outcome { COMPLETED, TIMED_OUT }

    private val state = MutableStateFlow(State())

    /** Call on every `TdApi.UpdateHavePendingNotifications`, with its two fields kept separate. */
    fun onUpdate(haveDelayedNotifications: Boolean, haveUnreceivedNotifications: Boolean) {
        state.update { current ->
            current.copy(
                generation = current.generation + 1,
                haveDelayed = haveDelayedNotifications,
                haveUnreceived = haveUnreceivedNotifications
            )
        }
    }

    /** Record this BEFORE starting the fetch whose completion [awaitCompletion] will wait for. */
    fun currentGeneration(): Long = state.value.generation

    /**
     * Suspends until an update newer than [startGeneration] reports neither
     * delayed nor unreceived notifications, or [timeoutMs] elapses. A timeout
     * is [Outcome.TIMED_OUT], never treated as if it were completion.
     */
    suspend fun awaitCompletion(startGeneration: Long, timeoutMs: Long): Outcome {
        val completed = withTimeoutOrNull(timeoutMs) {
            state.first { it.generation > startGeneration && !it.haveDelayed && !it.haveUnreceived }
        } != null
        return if (completed) Outcome.COMPLETED else Outcome.TIMED_OUT
    }
}
