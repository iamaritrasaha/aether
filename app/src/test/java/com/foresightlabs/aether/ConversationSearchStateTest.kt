package com.foresightlabs.aether

import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.search.ConversationSearchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * In-conversation search navigation.
 *
 * The subtle requirements are that "no results" is only ever claimed after a search
 * has genuinely completed, and that the position label reports the server's total
 * rather than the number paged in so far — otherwise the total appears to grow as
 * the user steps through it.
 */
class ConversationSearchStateTest {

    private fun message(id: String) = Message(
        id = id,
        chatId = "100",
        senderId = "1",
        senderName = "Sam",
        text = "match $id",
        timestamp = "12:00",
        isOutgoing = false
    )

    private fun state(
        resultCount: Int,
        selectedIndex: Int = 0,
        totalCount: Int = resultCount,
        hasMore: Boolean = false,
        isLoading: Boolean = false,
        error: String? = null,
        query: String = "match"
    ) = ConversationSearchState(
        query = query,
        isActive = true,
        isLoading = isLoading,
        results = (1..resultCount).map { message("m$it") },
        selectedIndex = if (resultCount == 0) -1 else selectedIndex,
        totalCount = totalCount,
        hasMore = hasMore,
        error = error
    )

    @Test
    fun theSelectedResultIsTheOneAtTheSelectedIndex() {
        assertEquals("m3", state(5, selectedIndex = 2).currentResult?.id)
    }

    @Test
    fun anOutOfRangeIndexYieldsNoResultRatherThanCrashing() {
        assertNull(state(2, selectedIndex = 9).currentResult)
    }

    @Test
    fun thePositionLabelReportsTheServerTotalNotThePagedInCount() {
        // 20 paged in, 128 exist.
        assertEquals("1 of 128", state(20, selectedIndex = 0, totalCount = 128).positionLabel)
    }

    @Test
    fun thePositionLabelFallsBackToTheLoadedCountWhenNoTotalIsReported() {
        assertEquals("2 of 5", state(5, selectedIndex = 1, totalCount = 0).positionLabel)
    }

    @Test
    fun thereIsNoPositionLabelBeforeAnythingIsSelected() {
        assertNull(state(0).positionLabel)
        assertNull(ConversationSearchState.Idle.positionLabel)
    }

    // --- navigation limits ---------------------------------------------------

    @Test
    fun steppingNewerStopsAtTheNewestResult() {
        assertFalse(state(5, selectedIndex = 0).canGoNewer)
        assertTrue(state(5, selectedIndex = 1).canGoNewer)
    }

    @Test
    fun steppingOlderStopsAtTheLastResultWhenNoMorePagesExist() {
        assertFalse(state(5, selectedIndex = 4, hasMore = false).canGoOlder)
    }

    @Test
    fun steppingOlderRemainsPossibleAtThePageEdgeWhenMoreResultsExistOnTheServer() {
        assertTrue(state(5, selectedIndex = 4, hasMore = true).canGoOlder)
    }

    @Test
    fun neitherDirectionIsAvailableWithNoResults() {
        val empty = state(0)
        assertFalse(empty.canGoOlder)
        assertFalse(empty.canGoNewer)
    }

    // --- truthful empty and error states -------------------------------------

    @Test
    fun noResultsIsClaimedOnlyAfterTheSearchHasActuallyCompleted() {
        assertFalse("in flight", state(0, isLoading = true).isEmptyResult)
        assertTrue("completed with nothing", state(0, isLoading = false).isEmptyResult)
    }

    @Test
    fun aFailedSearchIsNotReportedAsAnEmptyResult() {
        assertFalse(state(0, error = "Network unavailable").isEmptyResult)
    }

    @Test
    fun anUntypedQueryIsNotReportedAsAnEmptyResult() {
        assertFalse(state(0, query = "   ").isEmptyResult)
        assertFalse(ConversationSearchState(isActive = true).isEmptyResult)
    }

    @Test
    fun anInactiveSearchReportsNothing() {
        assertFalse(ConversationSearchState.Idle.isEmptyResult)
        assertFalse(ConversationSearchState.Idle.hasResults)
    }
}
