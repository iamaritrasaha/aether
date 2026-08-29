package com.foresightlabs.aether

import com.foresightlabs.aether.data.push.PushPendingGate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The race this class exists to close: TDLib's last known
 * "have pending notifications" state can already be false before a new push
 * cycle even starts (e.g. nothing was pending beforehand). A naive
 * `StateFlow<Boolean>` gated with `first { !it }` would treat that stale
 * value as proof the new cycle finished, without ever having observed
 * anything from it. [PushPendingGate] closes that with a generation counter:
 * completion requires an update strictly newer than the one recorded when
 * the wait began.
 */
class PushPendingGateTest {

    @Test
    fun aStaleFalseStateRecordedBeforeTheWaitDoesNotCompleteItImmediately() = runTest {
        val gate = PushPendingGate()
        // The gate starts at generation 0 with haveDelayed=false,
        // haveUnreceived=false -- exactly the "already looks done" state a
        // naive boolean check would treat as completion. No onUpdate call
        // follows: nothing new ever happens after the wait begins.
        val startGeneration = gate.currentGeneration()

        val outcome = gate.awaitCompletion(startGeneration, timeoutMs = 50L)

        assertEquals(PushPendingGate.Outcome.TIMED_OUT, outcome)
    }

    @Test
    fun aNewFalseUpdateAfterTheWaitStartsDoesCompleteIt() = runTest {
        val gate = PushPendingGate()
        val startGeneration = gate.currentGeneration()

        gate.onUpdate(haveDelayedNotifications = false, haveUnreceivedNotifications = false)

        val outcome = gate.awaitCompletion(startGeneration, timeoutMs = 1_000L)

        assertEquals(PushPendingGate.Outcome.COMPLETED, outcome)
    }

    @Test
    fun trueThenFalseCompletes() = runTest {
        val gate = PushPendingGate()
        val startGeneration = gate.currentGeneration()

        gate.onUpdate(haveDelayedNotifications = true, haveUnreceivedNotifications = false)
        gate.onUpdate(haveDelayedNotifications = false, haveUnreceivedNotifications = false)

        val outcome = gate.awaitCompletion(startGeneration, timeoutMs = 1_000L)

        assertEquals(PushPendingGate.Outcome.COMPLETED, outcome)
    }

    @Test
    fun stayingPendingTimesOutRatherThanCompleting() = runTest {
        val gate = PushPendingGate()
        val startGeneration = gate.currentGeneration()

        gate.onUpdate(haveDelayedNotifications = true, haveUnreceivedNotifications = false)

        val outcome = gate.awaitCompletion(startGeneration, timeoutMs = 50L)

        assertEquals(PushPendingGate.Outcome.TIMED_OUT, outcome)
    }

    @Test
    fun eitherFieldAloneCountsAsStillPending() = runTest {
        val gate = PushPendingGate()
        val startGeneration = gate.currentGeneration()

        // haveUnreceivedNotifications alone, haveDelayedNotifications false --
        // still pending, must not be read as completion.
        gate.onUpdate(haveDelayedNotifications = false, haveUnreceivedNotifications = true)

        val outcome = gate.awaitCompletion(startGeneration, timeoutMs = 50L)

        assertEquals(PushPendingGate.Outcome.TIMED_OUT, outcome)
    }

    @Test
    fun anOlderCyclesCompletionCannotSatisfyANewerWait() = runTest {
        val gate = PushPendingGate()
        // Simulate a prior, already-finished cycle.
        gate.onUpdate(haveDelayedNotifications = true, haveUnreceivedNotifications = false)
        gate.onUpdate(haveDelayedNotifications = false, haveUnreceivedNotifications = false)

        // A new cycle starts only after that -- its start generation is
        // therefore already past the old completion.
        val startGeneration = gate.currentGeneration()

        val outcome = gate.awaitCompletion(startGeneration, timeoutMs = 50L)

        assertEquals(PushPendingGate.Outcome.TIMED_OUT, outcome)
    }
}
