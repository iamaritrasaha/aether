package com.foresightlabs.aether

import com.foresightlabs.aether.data.telegram.TelegramMappers
import com.foresightlabs.aether.domain.model.MessageType
import com.foresightlabs.aether.domain.model.PollKind
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Polls.
 *
 * Every number a poll shows belongs to Telegram. The percentages in particular are
 * the server's own rounded values rather than a local division, because Telegram
 * rounds them so they sum correctly — computing them here would disagree with every
 * other client on the same poll.
 */
class PollPresentationTest {

    private fun option(
        text: String,
        voters: Int,
        percentage: Int,
        chosen: Boolean = false,
        beingChosen: Boolean = false
    ) = TdApi.PollOption(
        text,
        TdApi.FormattedText(text, emptyArray()),
        null,
        voters,
        percentage,
        emptyArray(),
        chosen,
        beingChosen,
        null,
        0
    )

    private fun poll(
        options: List<TdApi.PollOption>,
        total: Int = options.sumOf { it.voterCount },
        type: TdApi.PollType = TdApi.PollTypeRegular(),
        isClosed: Boolean = false,
        isAnonymous: Boolean = true,
        multiple: Boolean = false,
        revoting: Boolean = false
    ) = TdApi.Poll(
        7L,
        TdApi.FormattedText("Lunch?", emptyArray()),
        options.toTypedArray(),
        total,
        emptyArray(),
        false,
        isAnonymous,
        multiple,
        revoting,
        false,
        emptyArray(),
        intArrayOf(),
        type,
        0,
        0,
        isClosed,
        null
    )

    @Test
    fun aPollMessageIsPresentedAsAPollNotAsText() {
        val content = TdApi.MessagePoll().apply {
            this.poll = poll(listOf(option("Pizza", 3, 60), option("Sushi", 2, 40)))
        }
        val presentation = TelegramMappers.mapPresentation(content, 1L, resolvePath = { null })
        assertEquals(MessageType.POLL, presentation.type)
        assertEquals("Lunch?", presentation.poll?.question)
    }

    @Test
    fun percentagesComeFromTheServerNotFromLocalArithmetic() {
        // Three-way split: Telegram sends 33/33/34, which does not equal any naive
        // local rounding of 1/3.
        val mapped = TelegramMappers.mapPoll(
            poll(listOf(option("A", 1, 33), option("B", 1, 33), option("C", 1, 34)))
        )!!
        assertEquals(listOf(33, 33, 34), mapped.choices.map { it.votePercentage })
        assertEquals(100, mapped.choices.sumOf { it.votePercentage })
    }

    @Test
    fun optionIndicesAreThePositionsSetPollAnswerExpects() {
        val mapped = TelegramMappers.mapPoll(
            poll(listOf(option("A", 0, 0), option("B", 0, 0), option("C", 0, 0)))
        )!!
        assertEquals(listOf(0, 1, 2), mapped.choices.map { it.index })
    }

    // --- results visibility --------------------------------------------------

    @Test
    fun resultsStayHiddenUntilTheAccountHasVoted() {
        val unvoted = TelegramMappers.mapPoll(poll(listOf(option("A", 5, 100))))!!
        assertFalse(unvoted.hasVoted)
        assertFalse(unvoted.showResults)

        val voted = TelegramMappers.mapPoll(poll(listOf(option("A", 5, 100, chosen = true))))!!
        assertTrue(voted.showResults)
    }

    @Test
    fun aClosedPollShowsItsResultsWhetherOrNotTheAccountVoted() {
        val closed = TelegramMappers.mapPoll(
            poll(listOf(option("A", 5, 100)), isClosed = true)
        )!!
        assertTrue(closed.showResults)
        assertFalse("A closed poll accepts no votes", closed.canVote)
    }

    // --- voting rules --------------------------------------------------------

    @Test
    fun aSingleAnswerPollCannotBeVotedTwiceUnlessRevotingIsAllowed() {
        val voted = TelegramMappers.mapPoll(poll(listOf(option("A", 1, 100, chosen = true))))!!
        assertFalse(voted.canVote)

        val revotable = TelegramMappers.mapPoll(
            poll(listOf(option("A", 1, 100, chosen = true)), revoting = true)
        )!!
        assertTrue(revotable.canVote)
    }

    @Test
    fun anUnvotedOpenPollAcceptsAVote() {
        assertTrue(TelegramMappers.mapPoll(poll(listOf(option("A", 0, 0))))!!.canVote)
    }

    @Test
    fun aVoteInFlightIsMarkedAsInFlight() {
        val mapped = TelegramMappers.mapPoll(
            poll(listOf(option("A", 0, 0, beingChosen = true)))
        )!!
        assertTrue(mapped.choices.single().isBeingChosen)
        assertFalse("An unconfirmed vote is not a vote", mapped.choices.single().isChosen)
    }

    // --- quizzes -------------------------------------------------------------

    @Test
    fun aQuizIsIdentifiedAsAQuizAndCarriesItsCorrectAnswer() {
        val mapped = TelegramMappers.mapPoll(
            poll(
                listOf(option("Right", 1, 100, chosen = true), option("Wrong", 0, 0)),
                type = TdApi.PollTypeQuiz(
                    intArrayOf(0),
                    TdApi.FormattedText("Because.", emptyArray()),
                    null
                )
            )
        )!!
        assertEquals(PollKind.QUIZ, mapped.kind)
        assertTrue(mapped.choices[0].isCorrect)
        assertFalse(mapped.choices[1].isCorrect)
        assertEquals("Because.", mapped.explanation)
    }

    @Test
    fun aQuizWithNoExplanationCarriesNone() {
        val mapped = TelegramMappers.mapPoll(
            poll(
                listOf(option("A", 0, 0)),
                type = TdApi.PollTypeQuiz(intArrayOf(0), TdApi.FormattedText("", emptyArray()), null)
            )
        )!!
        assertNull(mapped.explanation)
    }

    // --- subtitle wording ----------------------------------------------------

    @Test
    fun theSubtitleStatesTheRealVoteCountAndAnonymity() {
        assertTrue(
            TelegramMappers.mapPoll(poll(listOf(option("A", 0, 0)), total = 0))!!
                .subtitle.contains("No votes yet")
        )
        assertTrue(
            TelegramMappers.mapPoll(poll(listOf(option("A", 1, 100)), total = 1))!!
                .subtitle.contains("1 vote")
        )
        assertTrue(
            TelegramMappers.mapPoll(poll(listOf(option("A", 9, 100)), total = 9))!!
                .subtitle.contains("9 votes")
        )
        assertTrue(
            TelegramMappers.mapPoll(poll(listOf(option("A", 1, 100)), isAnonymous = false))!!
                .subtitle.contains("Public poll")
        )
    }

    @Test
    fun aNullPollMapsToNothingRatherThanAnEmptyPoll() {
        assertNull(TelegramMappers.mapPoll(null))
    }
}
