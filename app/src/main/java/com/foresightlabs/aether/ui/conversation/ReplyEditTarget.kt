package com.foresightlabs.aether.ui.conversation

import com.foresightlabs.aether.domain.model.Message

/**
 * The outcome of trying to re-resolve one composer reply/edit target exactly.
 *
 * [Missing] and [Unavailable] are deliberately distinct: a deleted message
 * clears the operation (nothing else can be done), while a transient failure
 * (network) keeps it, because silently dropping a reply the user believes is
 * attached is worse than a slightly deferred chip.
 */
sealed interface ReplyEditTargetOutcome {
    data class Resolved(val message: Message) : ReplyEditTargetOutcome
    data object Missing : ReplyEditTargetOutcome
    data object Unavailable : ReplyEditTargetOutcome
}

/** The composer's reply/edit target state, as the resolution step sees it. */
data class ReplyEditState(
    val outOfWindow: Map<String, Message>,
    val replyId: String?,
    val editId: String?
)

/**
 * One step of exact reply/edit target re-resolution, pure so every case in
 * the recreation matrix is unit-testable:
 *
 * - target in the materialized window: nothing to do;
 * - target resolved out-of-window: remembered, so the chip/reseed works;
 * - target gone (deleted): the operation is cleared rather than left
 *   pointing at nothing or silently targeting another message;
 * - transient failure: kept for a later retry.
 */
fun resolveReplyEditTarget(
    state: ReplyEditState,
    id: String,
    materializedIds: Set<String>,
    outcome: ReplyEditTargetOutcome
): ReplyEditState = when {
    id in materializedIds -> state
    outcome is ReplyEditTargetOutcome.Resolved -> state.copy(
        outOfWindow = state.outOfWindow + (id to outcome.message)
    )
    outcome is ReplyEditTargetOutcome.Missing -> state.copy(
        outOfWindow = state.outOfWindow - id,
        replyId = state.replyId?.takeIf { it != id },
        editId = state.editId?.takeIf { it != id }
    )
    else -> state
}
