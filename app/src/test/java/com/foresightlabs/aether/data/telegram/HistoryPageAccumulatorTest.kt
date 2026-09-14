package com.foresightlabs.aether.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryPageAccumulatorTest {

    @Test
    fun initialPageUsesStableIdsAndKeepsTelegramOrderForLaterReversal() {
        val accumulator = HistoryPageAccumulator<Long> { it }

        val result = accumulator.merge(listOf(9L, 8L, 7L), requestBoundary = 0L)

        assertEquals(3, result.newUniqueCount)
        assertEquals(7L, result.oldestId)
        assertEquals(listOf(9L, 8L, 7L), accumulator.values)
    }

    @Test
    fun olderPageDropsBoundaryOverlapAndOnlyAddsOlderMessages() {
        val accumulator = HistoryPageAccumulator<Long> { it }
        accumulator.merge(listOf(9L, 8L, 7L), requestBoundary = 0L)

        val result = accumulator.merge(listOf(7L, 6L, 5L), requestBoundary = 7L)

        assertEquals(2, result.newUniqueCount)
        assertEquals(5L, result.oldestId)
        assertEquals(listOf(9L, 8L, 7L, 6L, 5L), accumulator.values)
    }

    @Test
    fun duplicatePageMakesNoProgressAndEmptyFinalPageIsAlsoNoProgress() {
        val accumulator = HistoryPageAccumulator<Long> { it }
        accumulator.merge(listOf(9L, 8L), requestBoundary = 0L)

        val duplicate = accumulator.merge(listOf(8L, 9L), requestBoundary = 8L)
        val empty = accumulator.merge(emptyList(), requestBoundary = 8L)

        assertEquals(0, duplicate.newUniqueCount)
        assertEquals(0L, duplicate.oldestId)
        assertEquals(0, empty.newUniqueCount)
        assertEquals(0L, empty.oldestId)
        assertTrue(accumulator.values == listOf(9L, 8L))
    }
}
