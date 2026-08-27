package com.foresightlabs.aether.domain.model

import androidx.compose.runtime.Immutable

/** One option in a Telegram poll. */
@Immutable
data class PollChoice(
    /**
     * Index into the poll's option array.
     *
     * This — not the option's string id — is what TDLib's `setPollAnswer` takes.
     */
    val index: Int,
    val text: String,
    val voterCount: Int,
    val votePercentage: Int,
    /** Whether this account chose this option. */
    val isChosen: Boolean,
    /** Whether this account's vote for it is still in flight. */
    val isBeingChosen: Boolean,
    /** Set only for a quiz whose answer has been revealed. */
    val isCorrect: Boolean = false
)

/** Whether a poll is an ordinary poll or a quiz. */
enum class PollKind { REGULAR, QUIZ }

/**
 * A Telegram poll as Aether presents it.
 *
 * Every count here is Telegram's. Percentages are the server's own
 * `votePercentage` rather than a local division, because Telegram rounds them so
 * they sum correctly and a locally computed set will disagree with every other
 * client.
 */
@Immutable
data class PollPresentation(
    val id: Long,
    val question: String,
    val choices: List<PollChoice>,
    val totalVoterCount: Int,
    val kind: PollKind,
    val isAnonymous: Boolean,
    val allowsMultipleAnswers: Boolean,
    val allowsRevoting: Boolean,
    val isClosed: Boolean,
    /** Explanation shown after a quiz is answered, when the author set one. */
    val explanation: String? = null
) {
    /** Whether this account has voted. */
    val hasVoted: Boolean get() = choices.any { it.isChosen }

    /** Whether results should be shown rather than hidden behind a vote. */
    val showResults: Boolean get() = hasVoted || isClosed

    /** Whether tapping an option would do anything right now. */
    val canVote: Boolean
        get() = !isClosed && (!hasVoted || allowsRevoting || allowsMultipleAnswers)

    /**
     * The line under the question describing the poll's own rules.
     *
     * Anonymity is stated because it changes what voting means, and it is Telegram's
     * flag rather than an assumption.
     */
    val subtitle: String
        get() {
            val kindLabel = when {
                kind == PollKind.QUIZ -> "Quiz"
                isAnonymous -> "Anonymous poll"
                else -> "Public poll"
            }
            return when {
                isClosed -> "$kindLabel · Final results"
                totalVoterCount == 0 -> "$kindLabel · No votes yet"
                totalVoterCount == 1 -> "$kindLabel · 1 vote"
                else -> "$kindLabel · $totalVoterCount votes"
            }
        }
}
