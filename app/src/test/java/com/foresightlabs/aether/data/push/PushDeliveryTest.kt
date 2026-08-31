package com.foresightlabs.aether.data.push

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

/**
 * What one background push must do, in order, and how long it may stay alive
 * doing it.
 *
 * These exercise the real [NotificationWorkQueue] behind [PushDelivery], so
 * "the callback does not return before the notification is posted" is verified
 * as behaviour rather than asserted about the source.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PushDeliveryTest {

    private val steps = Collections.synchronizedList(mutableListOf<String>())

    private fun delivery(
        queue: NotificationWorkQueue,
        receiverId: Long = 0L,
        ourReceiverId: Long? = null,
        tdlibReady: suspend () -> Unit = { steps += "tdlib-ready" },
        process: suspend (String) -> PushDelivery.ProcessResult = {
            steps += "process"
            PushDelivery.ProcessResult.Ok
        },
        onHandOff: () -> Unit = { steps += "hand-off" }
    ) = PushDelivery(
        resolveReceiverId = { receiverId },
        registeredReceiverId = { ourReceiverId },
        awaitTdlibReady = tdlibReady,
        processPush = process,
        awaitNotificationWork = { timeoutMs -> queue.awaitDrained(timeoutMs) },
        handOffToLiveFetch = onHandOff
    )

    @Test
    fun tdlib_is_initialised_before_the_payload_is_handed_to_it() = runTest {
        val queue = NotificationWorkQueue(backgroundScope)
        val initialised = CompletableDeferred<Unit>()
        val delivery = delivery(
            queue = queue,
            tdlibReady = {
                yield()
                steps += "tdlib-ready"
                initialised.complete(Unit)
            },
            process = {
                assertTrue(
                    "ProcessPushNotification must not be sent before TDLib is initialised",
                    initialised.isCompleted
                )
                steps += "process"
                PushDelivery.ProcessResult.Ok
            }
        )

        assertEquals(PushDelivery.Outcome.PROCESSED, delivery.deliver("{}"))
        assertEquals(listOf("tdlib-ready", "process"), steps.toList())
    }

    @Test
    fun a_notification_emitted_during_processing_is_posted_before_the_callback_returns() = runTest {
        val queue = NotificationWorkQueue(backgroundScope)
        var posted = false
        val delivery = delivery(
            queue = queue,
            process = {
                // TDLib's contract: the updates a push causes are emitted
                // before it answers Ok.
                queue.submit {
                    yield()
                    posted = true
                }
                PushDelivery.ProcessResult.Ok
            }
        )

        val outcome = delivery.deliver("{}")

        assertEquals(PushDelivery.Outcome.PROCESSED, outcome)
        assertTrue("The push must not finish with the notification still pending", posted)
    }

    @Test
    fun a_notification_emitted_after_processing_returns_is_still_waited_for() = runTest {
        val queue = NotificationWorkQueue(backgroundScope)
        val processed = CompletableDeferred<Unit>()
        var posted = false
        val delivery = delivery(
            queue = queue,
            process = {
                // Submitted before Ok, but its work only completes later --
                // and a further update arrives once that one is running.
                queue.submit {
                    processed.await()
                    queue.submit { posted = true }
                }
                PushDelivery.ProcessResult.Ok
            }
        )

        val deliveryJob = launch { assertEquals(PushDelivery.Outcome.PROCESSED, delivery.deliver("{}")) }
        yield()
        assertFalse("The delivery must still be waiting", deliveryJob.isCompleted)

        processed.complete(Unit)
        deliveryJob.join()

        assertTrue(posted)
    }

    @Test
    fun several_notification_updates_from_one_push_all_finish_first() = runTest {
        val queue = NotificationWorkQueue(backgroundScope)
        val postedGroups = Collections.synchronizedList(mutableListOf<Int>())
        val delivery = delivery(
            queue = queue,
            process = {
                repeat(3) { group ->
                    queue.submit {
                        yield()
                        postedGroups += group
                    }
                }
                PushDelivery.ProcessResult.Ok
            }
        )

        assertEquals(PushDelivery.Outcome.PROCESSED, delivery.deliver("{}"))
        assertEquals(listOf(0, 1, 2), postedGroups.toList())
    }

    @Test
    fun a_push_that_produces_no_notification_finishes_without_waiting_for_one() = runTest {
        val queue = NotificationWorkQueue(backgroundScope)
        val delivery = delivery(queue)

        // A push TDLib processes silently (an edit, a read receipt) is a
        // successful delivery, not a timeout.
        assertEquals(PushDelivery.Outcome.PROCESSED, delivery.deliver("{}"))
        assertEquals(listOf("tdlib-ready", "process"), steps.toList())
    }

    @Test
    fun notification_rendering_that_fails_does_not_fail_the_delivery_or_hang_it() = runTest {
        val failures = mutableListOf<Throwable>()
        val queue = NotificationWorkQueue(backgroundScope, onError = { failures += it })
        val delivery = delivery(
            queue = queue,
            process = {
                queue.submit { throw IllegalStateException("notify() blew up") }
                PushDelivery.ProcessResult.Ok
            }
        )

        assertEquals(PushDelivery.Outcome.PROCESSED, delivery.deliver("{}"))
        assertEquals(1, failures.size)
    }

    @Test
    fun rendering_that_never_finishes_is_reported_rather_than_held_open() = runTest {
        val queue = NotificationWorkQueue(backgroundScope)
        val delivery = delivery(
            queue = queue,
            process = {
                queue.submit { awaitCancellation() }
                PushDelivery.ProcessResult.Ok
            }
        )

        val outcome = delivery.deliver(
            payload = "{}",
            budgetMs = PushDelivery.DEFAULT_BUDGET_MS,
            notificationWorkTimeoutMs = 5_000L
        )

        assertEquals(PushDelivery.Outcome.PROCESSED_WORK_UNFINISHED, outcome)
    }

    @Test
    fun error_406_hands_off_to_the_bounded_fetch_instead_of_waiting() = runTest {
        val queue = NotificationWorkQueue(backgroundScope)
        var handedOff = false
        val delivery = delivery(
            queue = queue,
            process = { PushDelivery.ProcessResult.NeedsLiveFetch },
            onHandOff = { handedOff = true }
        )

        assertEquals(PushDelivery.Outcome.HANDED_OFF, delivery.deliver("{}"))
        assertTrue(handedOff)
    }

    @Test
    fun any_other_processing_error_ends_the_delivery_without_a_hand_off() = runTest {
        val queue = NotificationWorkQueue(backgroundScope)
        var handedOff = false
        val delivery = delivery(
            queue = queue,
            process = { PushDelivery.ProcessResult.Failed(400) },
            onHandOff = { handedOff = true }
        )

        assertEquals(PushDelivery.Outcome.FAILED, delivery.deliver("{}"))
        assertFalse("Only error 406 gets the live-fetch continuation", handedOff)
    }

    @Test
    fun a_push_registered_by_someone_else_starts_nothing() = runTest {
        val queue = NotificationWorkQueue(backgroundScope)
        val delivery = delivery(
            queue = queue,
            receiverId = 111L,
            ourReceiverId = 222L
        )

        assertEquals(PushDelivery.Outcome.FOREIGN_RECEIVER, delivery.deliver("{}"))
        assertTrue("Nothing may be started for a foreign push", steps.isEmpty())
    }

    @Test
    fun a_push_with_no_extractable_receiver_id_is_processed_anyway() = runTest {
        val queue = NotificationWorkQueue(backgroundScope)
        val delivery = delivery(queue = queue, receiverId = 0L, ourReceiverId = 222L)

        assertEquals(PushDelivery.Outcome.PROCESSED, delivery.deliver("{}"))
    }

    @Test
    fun the_callback_cannot_be_held_open_indefinitely_by_a_stuck_step() = runTest {
        val queue = NotificationWorkQueue(backgroundScope)
        val delivery = delivery(
            queue = queue,
            tdlibReady = { awaitCancellation() }
        )

        assertEquals(PushDelivery.Outcome.TIMED_OUT, delivery.deliver("{}", budgetMs = 9_000L))
    }

    @Test
    fun cancelling_the_callback_stops_the_delivery() = runTest {
        val queue = NotificationWorkQueue(backgroundScope)
        val started = CompletableDeferred<Unit>()
        val delivery = delivery(
            queue = queue,
            tdlibReady = {
                started.complete(Unit)
                awaitCancellation()
            }
        )

        val job = launch { delivery.deliver("{}") }
        started.await()
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
    }
}
