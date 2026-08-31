package com.foresightlabs.aether.data.push

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs the work TDLib's notification updates imply, one at a time and in
 * arrival order, and lets a caller wait until that work is actually finished.
 *
 * Two separate guarantees, both of which the background push path depends on:
 *
 * 1. **Serialization.** TDLib emits its notification updates
 *    (UpdateActiveNotifications on start-up, then UpdateNotificationGroup /
 *    UpdateNotification per change) back to back, and a push-woken process
 *    receives several of them within milliseconds of each other. Handling them
 *    on a multi-threaded dispatcher lets two coroutines touch the same
 *    notification group's item map at once -- one iterating it to build a
 *    MessagingStyle while another inserts into it. That is a
 *    ConcurrentModificationException in a coroutine whose failure nothing
 *    catches, i.e. a lost notification at best. Each submission here joins the
 *    previous one before running, so group state is only ever touched by one
 *    coroutine, and updates are applied in the order TDLib produced them
 *    (which is also the order their content is meant to be shown in).
 *
 * 2. **A wait that means something.** [awaitDrained] returns only once every
 *    submission accepted so far has finished -- including any submitted while
 *    the wait was already in progress, which is exactly the case where a push
 *    produces one update whose handling produces another. Without that, a push
 *    callback could return -- and the process be frozen -- with the posting
 *    still queued behind it.
 *
 * A failing submission completes the queue entry rather than propagating:
 * rendering one notification badly must not take down the process (in a
 * push-woken process there is no Activity and nothing else to notice), and it
 * must not strand a caller waiting on the drain. Failures are reported to
 * [onError] instead.
 */
class NotificationWorkQueue(
    private val scope: CoroutineScope,
    private val onError: (Throwable) -> Unit = {}
) {
    private val lock = Any()

    /** The most recently submitted job; the chain each new submission joins onto. */
    private var tail: Job? = null

    /**
     * How many submissions are queued or running.
     *
     * The queue is deliberately not capped. Its input is TDLib's own
     * notification updates, so a cap could only be enforced by dropping one --
     * silently losing a notification, which is the failure this whole path
     * exists to prevent. What is bounded instead is the *waiting*: no caller
     * ever blocks on it without a timeout (see [awaitDrained]), so a queue that
     * is not draining delays nothing beyond that bound.
     */
    private var depth = 0

    /** Submits notification work. Returns the job so a caller can join a single item. */
    fun submit(work: suspend () -> Unit): Job {
        synchronized(lock) {
            val previous = tail
            val job = scope.launch {
                // Ordering: this cannot start before everything submitted
                // earlier has finished.
                previous?.join()
                try {
                    work()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Throwable) {
                    onError(error)
                }
            }
            tail = job
            depth++
            job.invokeOnCompletion {
                synchronized(lock) {
                    depth--
                    if (tail === job) tail = null
                }
            }
            return job
        }
    }

    /** True when nothing is queued or running. */
    fun isIdle(): Boolean = synchronized(lock) { tail?.isCompleted ?: true }

    /** Queued plus running submissions, for diagnostics. */
    fun depth(): Int = synchronized(lock) { depth }

    /**
     * Suspends until the queue is empty, or [timeoutMs] elapses.
     *
     * Re-checks after each join, so work submitted while waiting is waited for
     * too. Returns true only if the queue actually drained -- a timeout is
     * reported as false and never mistaken for completion.
     */
    suspend fun awaitDrained(timeoutMs: Long): Boolean {
        val drained = withTimeoutOrNull(timeoutMs) {
            while (true) {
                val current = synchronized(lock) { tail } ?: break
                current.join()
            }
            true
        }
        return drained == true
    }
}
