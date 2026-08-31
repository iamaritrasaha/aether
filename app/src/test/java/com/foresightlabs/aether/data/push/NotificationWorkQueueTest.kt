package com.foresightlabs.aether.data.push

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

/**
 * The two properties the background push path depends on: notification work
 * runs one at a time in arrival order, and a caller can wait for it to be
 * genuinely finished.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationWorkQueueTest {

    @Test
    fun submissions_run_one_at_a_time_and_in_arrival_order() = runTest {
        val queue = NotificationWorkQueue(backgroundScope)
        val events = Collections.synchronizedList(mutableListOf<String>())

        repeat(4) { index ->
            queue.submit {
                events += "start-$index"
                // A suspension point mid-work: with concurrent handling this is
                // exactly where a second update would interleave and touch the
                // same notification group state.
                yield()
                events += "end-$index"
            }
        }
        assertTrue(queue.awaitDrained(timeoutMs = 1_000L))

        assertEquals(
            listOf(
                "start-0", "end-0",
                "start-1", "end-1",
                "start-2", "end-2",
                "start-3", "end-3"
            ),
            events.toList()
        )
    }

    @Test
    fun draining_waits_for_work_submitted_while_the_wait_is_already_running() = runTest {
        val queue = NotificationWorkQueue(backgroundScope)
        val firstReached = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondReached = CompletableDeferred<Unit>()
        val releaseSecond = CompletableDeferred<Unit>()
        var secondFinished = false

        queue.submit {
            firstReached.complete(Unit)
            releaseFirst.await()
        }

        val waiter = launch { assertTrue(queue.awaitDrained(timeoutMs = 30_000L)) }
        firstReached.await()

        // The push case this models: handling one update produces another one
        // after the wait has already begun. The wait must extend to cover it,
        // not end with the first piece of work it happened to see.
        queue.submit {
            secondReached.complete(Unit)
            releaseSecond.await()
            secondFinished = true
        }
        releaseFirst.complete(Unit)
        secondReached.await()

        assertFalse(
            "The drain must still be waiting while later work is unfinished",
            waiter.isCompleted
        )
        releaseSecond.complete(Unit)
        waiter.join()

        assertTrue("The drain must not return before later work finished", secondFinished)
    }

    @Test
    fun a_failing_submission_neither_escapes_nor_strands_the_wait() = runTest {
        val failures = mutableListOf<Throwable>()
        val queue = NotificationWorkQueue(backgroundScope, onError = { failures += it })
        var laterWorkRan = false

        queue.submit { throw IllegalStateException("rendering failed") }
        queue.submit { laterWorkRan = true }

        assertTrue(queue.awaitDrained(timeoutMs = 1_000L))
        assertEquals(1, failures.size)
        assertEquals("rendering failed", failures.single().message)
        assertTrue("One failure must not stop the notifications behind it", laterWorkRan)
    }

    @Test
    fun draining_reports_a_timeout_rather_than_claiming_completion() = runTest {
        val queue = NotificationWorkQueue(backgroundScope)
        val neverCompletes = CompletableDeferred<Unit>()
        queue.submit { neverCompletes.await() }

        assertFalse(queue.awaitDrained(timeoutMs = 5_000L))

        neverCompletes.complete(Unit)
    }

    @Test
    fun cancelling_running_work_releases_the_wait_instead_of_holding_it() = runTest {
        val queue = NotificationWorkQueue(backgroundScope)
        val started = CompletableDeferred<Unit>()
        val job = queue.submit {
            started.complete(Unit)
            CompletableDeferred<Unit>().await()
        }
        started.await()

        job.cancel()

        assertTrue(queue.awaitDrained(timeoutMs = 1_000L))
        assertTrue(queue.isIdle())
    }

    @Test
    fun depth_reflects_queued_and_running_work() = runTest {
        val queue = NotificationWorkQueue(backgroundScope)
        val release = CompletableDeferred<Unit>()
        val running = CompletableDeferred<Unit>()

        queue.submit {
            running.complete(Unit)
            release.await()
        }
        queue.submit { }
        running.await()

        assertEquals(2, queue.depth())

        release.complete(Unit)
        assertTrue(queue.awaitDrained(timeoutMs = 1_000L))
        assertEquals(0, queue.depth())
    }

    @Test
    fun a_queue_with_nothing_in_it_drains_immediately() = runTest(StandardTestDispatcher()) {
        val queue = NotificationWorkQueue(backgroundScope)
        assertTrue(queue.isIdle())
        assertTrue(queue.awaitDrained(timeoutMs = 1_000L))
    }
}
