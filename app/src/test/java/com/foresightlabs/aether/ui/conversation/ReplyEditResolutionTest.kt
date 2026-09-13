package com.foresightlabs.aether.ui.conversation

import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.MessageStatus
import com.foresightlabs.aether.domain.model.MessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reply/edit recreation matrix. After Activity recreation the saved
 * target ids are re-resolved exactly; each row pins what must happen for a
 * target that is present, out-of-window, deleted, or transiently
 * unresolvable -- and that a deleted target never leaves a lingering id
 * whose send would silently go out without its reply.
 */
class ReplyEditResolutionTest {

    private fun message(id: String) = Message(
        id = id, chatId = "100", senderId = "200", senderName = "Sender $id",
        text = "m$id", timestamp = "10:00", isOutgoing = true,
        status = MessageStatus.SENT, type = MessageType.TEXT
    )

    private val initial = ReplyEditState(
        outOfWindow = emptyMap(),
        replyId = "42",
        editId = "7"
    )

    @Test
    fun targetInsideTheMaterializedWindowIsUntouched() {
        val state = resolveReplyEditTarget(
            initial, id = "42",
            materializedIds = setOf("42", "7", "9"),
            outcome = ReplyEditTargetOutcome.Unavailable
        )
        assertEquals("42", state.replyId)
        // Window presence wins even if TDLib resolution would fail.
        assertTrue("42" !in state.outOfWindow)
    }

    @Test
    fun targetOutsideTheWindowIsResolvedAndRemembered() {
        val state = resolveReplyEditTarget(
            initial, id = "42",
            materializedIds = setOf("7", "9"),
            outcome = ReplyEditTargetOutcome.Resolved(message("42"))
        )
        assertEquals(message("42"), state.outOfWindow["42"])
        assertEquals("42", state.replyId)
    }

    @Test
    fun deletedEditTargetClearsTheEditOperation() {
        val state = resolveReplyEditTarget(
            initial, id = "7",
            materializedIds = setOf("42"),
            outcome = ReplyEditTargetOutcome.Missing
        )
        assertNull("a deleted edit target must clear the edit", state.editId)
        assertEquals("the unrelated reply survives", "42", state.replyId)
        assertTrue("7" !in state.outOfWindow)
    }

    @Test
    fun deletedReplyTargetClearsTheReplyOperation() {
        val state = resolveReplyEditTarget(
            initial, id = "42",
            materializedIds = setOf("7"),
            outcome = ReplyEditTargetOutcome.Missing
        )
        assertNull(state.replyId)
        assertEquals("the unrelated edit survives", "7", state.editId)
    }

    @Test
    fun transientFailureKeepsTheTargetForRetry() {
        val state = resolveReplyEditTarget(
            initial, id = "42",
            materializedIds = emptySet(),
            outcome = ReplyEditTargetOutcome.Unavailable
        )
        assertEquals("network failure must not silently drop a reply", "42", state.replyId)
        assertEquals("7", state.editId)
        assertTrue(state.outOfWindow.isEmpty())
    }

    @Test
    fun resolutionIsIdScopedAndNeverTargetsAnotherMessage() {
        // Resolving one id must not disturb the other operation's id.
        val afterReply = resolveReplyEditTarget(
            initial, id = "42",
            materializedIds = emptySet(),
            outcome = ReplyEditTargetOutcome.Resolved(message("42"))
        )
        assertEquals("7", afterReply.editId)
        val afterEdit = resolveReplyEditTarget(
            afterReply, id = "7",
            materializedIds = emptySet(),
            outcome = ReplyEditTargetOutcome.Missing
        )
        assertNull(afterEdit.editId)
        assertEquals("42", afterEdit.replyId)
        assertEquals(message("42"), afterEdit.outOfWindow["42"])
    }

    @Test
    fun repeatedResolutionOfTheSameMessageAfterCompletingAnEditWorks() {
        // Edit completes -> id cleared -> user edits the SAME message again:
        // the id re-enters and re-resolves (no stale "already known" state).
        var state = initial.copy(editId = null)
        state = resolveReplyEditTarget(state, "7", setOf("9"), ReplyEditTargetOutcome.Resolved(message("7")))
        assertEquals(message("7"), state.outOfWindow["7"])
        state = state.copy(editId = "7")
        // Still resolvable from the out-of-window cache on a later run.
        val cached = resolveReplyEditTarget(state, "7", setOf("9"), ReplyEditTargetOutcome.Unavailable)
        assertEquals(message("7"), cached.outOfWindow["7"])
        assertEquals("7", cached.editId)
    }
}
