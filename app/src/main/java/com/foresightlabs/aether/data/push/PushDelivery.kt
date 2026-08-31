package com.foresightlabs.aether.data.push

import kotlinx.coroutines.withTimeoutOrNull

/**
 * The order and the lifetime rules of one background push, expressed without
 * TDLib so both are testable rather than only reviewable.
 *
 * The sequence a push must follow, and why each step is where it is:
 *
 * 1. **Routing.** GetPushReceiverId is pure computation over the payload and
 *    needs no running client, so it happens first: a push meant for a
 *    registration this session does not hold is dropped before anything is
 *    started for it.
 * 2. **Initialization before processing.** TDLib will not accept
 *    ProcessPushNotification before its parameters are set. In a process the
 *    push itself started, that has only just been kicked off, so the payload
 *    must wait for it -- with a bound, because waiting forever inside a push
 *    callback is its own failure.
 * 3. **Processing**, whose result decides the ending:
 *    - **Ok** -- TDLib's contract is that every update this push caused has
 *      already been emitted. Emitted is not the same as rendered: the updates
 *      are handled on their own coroutines, and if this returned now, the FCM
 *      callback would return and the OS could freeze the process with the
 *      posting still in flight. So this waits for that work to drain.
 *    - **Needs a live fetch** (TDLib's error 406) -- the push alone did not
 *      carry enough, and only a real connection resolves it. That is
 *      genuinely slow work that can outlast the callback, so it is handed to
 *      the bounded worker instead of being waited on here.
 *    - **Anything else** -- a parse or decryption failure. Nothing left to try.
 *
 * The whole sequence runs under [budgetMs]. A push callback holds a wakelock
 * and has a limited execution allowance; a step that never answers must end
 * the delivery rather than hold the callback open until the system takes the
 * process away anyway.
 */
class PushDelivery(
    private val resolveReceiverId: (String) -> Long,
    private val registeredReceiverId: () -> Long?,
    private val awaitTdlibReady: suspend () -> Unit,
    private val processPush: suspend (String) -> ProcessResult,
    private val awaitNotificationWork: suspend (Long) -> Boolean,
    private val handOffToLiveFetch: () -> Unit,
    private val log: (String) -> Unit = {}
) {

    /** What TDLib made of the payload, in the only three shapes that change what happens next. */
    sealed interface ProcessResult {
        /** Processed; every update it caused has been emitted. */
        object Ok : ProcessResult

        /** TDLib error 406: a live connection is required to find out what changed. */
        object NeedsLiveFetch : ProcessResult

        /** Any other failure. [code] is TDLib's error code, or 0 when unknown. */
        data class Failed(val code: Int) : ProcessResult
    }

    enum class Outcome {
        /** Processed and the resulting notification work finished. */
        PROCESSED,

        /** Processed, but the notification work had not drained within its bound. */
        PROCESSED_WORK_UNFINISHED,

        /** Error 406: handed to the bounded fetch worker. */
        HANDED_OFF,

        /** The push was registered by someone else; nothing was started for it. */
        FOREIGN_RECEIVER,

        /** TDLib refused the payload. */
        FAILED,

        /** The delivery budget ran out before the sequence finished. */
        TIMED_OUT
    }

    suspend fun deliver(
        payload: String,
        budgetMs: Long = DEFAULT_BUDGET_MS,
        notificationWorkTimeoutMs: Long = DEFAULT_NOTIFICATION_WORK_TIMEOUT_MS
    ): Outcome {
        val receiverId = resolveReceiverId(payload)
        val ourId = registeredReceiverId()
        // 0 means no id could be extracted: there is nowhere else for the push
        // to go, so it is processed rather than dropped.
        if (receiverId != 0L && ourId != null && receiverId != ourId) {
            log("PUSH_RECEIVER_MISMATCH")
            return Outcome.FOREIGN_RECEIVER
        }

        val outcome = withTimeoutOrNull(budgetMs) {
            awaitTdlibReady()
            log("PROCESS_PUSH_STARTED")
            when (val result = processPush(payload)) {
                is ProcessResult.Ok -> {
                    log("PUSH_PROCESS_OK")
                    val drained = awaitNotificationWork(notificationWorkTimeoutMs)
                    if (drained) {
                        Outcome.PROCESSED
                    } else {
                        log("PUSH_NOTIFICATION_WORK_UNFINISHED")
                        Outcome.PROCESSED_WORK_UNFINISHED
                    }
                }
                is ProcessResult.NeedsLiveFetch -> {
                    log("PUSH_PROCESS_406")
                    handOffToLiveFetch()
                    Outcome.HANDED_OFF
                }
                is ProcessResult.Failed -> {
                    log("PUSH_PROCESS_ERROR code=${result.code}")
                    Outcome.FAILED
                }
            }
        }
        if (outcome == null) log("PUSH_DELIVERY_BUDGET_EXHAUSTED")
        return outcome ?: Outcome.TIMED_OUT
    }

    companion object {
        /**
         * The whole delivery, end to end. Comfortably inside the execution
         * allowance a high-priority FCM callback gets, and short enough that a
         * step which never answers cannot hold the callback (and its wakelock)
         * open indefinitely.
         */
        const val DEFAULT_BUDGET_MS = 9_000L

        /**
         * How long the drain of notification rendering may take within that
         * budget. Smaller than the budget so a slow render still leaves the
         * delivery able to finish and log its own outcome.
         */
        const val DEFAULT_NOTIFICATION_WORK_TIMEOUT_MS = 5_000L
    }
}
