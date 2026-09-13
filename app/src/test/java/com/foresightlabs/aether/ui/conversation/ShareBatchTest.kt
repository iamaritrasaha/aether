package com.foresightlabs.aether.ui.conversation

import com.foresightlabs.aether.domain.sharing.SharedAttachmentKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The mixed-share batch semantics: deterministic order, caption on the first
 * item only, every item attempted exactly once even when an earlier one
 * fails (continue-on-failure is the existing send UX -- a failed album
 * member never cancels its siblings), and no silent drops.
 */
class ShareBatchTest {

    private class RecordingSend {
        val attempts = mutableListOf<Triple<String, String, Long?>>()
        val failures = mutableMapOf<String, String>()

        suspend fun send(item: ShareBatchItem, caption: String, reply: Long?): Result<Unit> {
            attempts += Triple(item.path, caption, reply)
            failures[item.path]?.let { return Result.failure(IllegalStateException(it)) }
            return Result.success(Unit)
        }
    }

    @Test
    fun itemsAreAttemptedOnceInOrderWithCaptionOnTheFirstOnly() = runTest {
        val recorder = RecordingSend()
        val items = listOf(
            ShareBatchItem("/p.jpg", SharedAttachmentKind.IMAGE),
            ShareBatchItem("/v.mp4", SharedAttachmentKind.VIDEO),
            ShareBatchItem("/d.pdf", SharedAttachmentKind.FILE)
        )
        sendShareBatch(items, caption = "notes", replyToMessageId = 77L, send = recorder::send, onError = {})

        assertEquals(
            listOf("/p.jpg", "/v.mp4", "/d.pdf"),
            recorder.attempts.map { it.first }
        )
        assertEquals("notes", recorder.attempts[0].second)
        assertEquals(77L, recorder.attempts[0].third)
        assertEquals("", recorder.attempts[1].second)
        assertEquals("", recorder.attempts[2].second)
        assertEquals(null, recorder.attempts[1].third)
    }

    @Test
    fun aFailedItemDoesNotStopOrDropTheRest() = runTest {
        val recorder = RecordingSend().apply {
            failures["/v.mp4"] = "disk full"
        }
        val errors = mutableListOf<String>()
        val items = listOf(
            ShareBatchItem("/p.jpg", SharedAttachmentKind.IMAGE),
            ShareBatchItem("/v.mp4", SharedAttachmentKind.VIDEO),
            ShareBatchItem("/d.pdf", SharedAttachmentKind.FILE)
        )
        sendShareBatch(items, caption = "", replyToMessageId = null, send = recorder::send, onError = { errors += it })

        assertEquals("all three were attempted", 3, recorder.attempts.size)
        assertEquals(listOf("disk full"), errors)
    }

    @Test
    fun everyFailureIsReportedNotOnlyTheFirst() = runTest {
        val recorder = RecordingSend().apply {
            failures["/p.jpg"] = "one"
            failures["/v.mp4"] = "two"
        }
        val errors = mutableListOf<String>()
        val items = listOf(
            ShareBatchItem("/p.jpg", SharedAttachmentKind.IMAGE),
            ShareBatchItem("/v.mp4", SharedAttachmentKind.VIDEO),
            ShareBatchItem("/d.pdf", SharedAttachmentKind.FILE)
        )
        sendShareBatch(items, caption = "", replyToMessageId = null, send = recorder::send, onError = { errors += it })

        assertEquals(listOf("one", "two"), errors)
        assertEquals("the healthy last item still went out", "/d.pdf", recorder.attempts[2].first)
    }

    @Test
    fun anEmptyBatchSendsNothing() = runTest {
        val recorder = RecordingSend()
        sendShareBatch(emptyList(), caption = "x", replyToMessageId = null, send = recorder::send, onError = {})
        assertEquals(0, recorder.attempts.size)
    }
}
