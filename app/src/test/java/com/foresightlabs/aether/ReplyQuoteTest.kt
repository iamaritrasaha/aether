package com.foresightlabs.aether

import com.foresightlabs.aether.domain.text.AetherEntity
import com.foresightlabs.aether.domain.text.AetherText
import com.foresightlabs.aether.domain.text.ReplyQuote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reply quotes.
 *
 * A quote carries its **position in the original**, not just its text. Telegram uses
 * the position to keep the quote attached when the original is edited, and without it
 * a quote of words that appear twice would silently match the wrong occurrence.
 */
class ReplyQuoteTest {

    private val source = AetherText(
        "the first part and the second part",
        listOf(AetherEntity.Bold(4, 5))
    )

    @Test
    fun aQuoteCarriesItsPositionInTheOriginal() {
        val quote = ReplyQuote.from(source, 19, 34)!!
        assertEquals("the second part", quote.text)
        assertEquals(19, quote.position)
    }

    @Test
    fun quotingRepeatedWordsKeepsTheOccurrenceThatWasSelected() {
        val text = AetherText("part one, part two")
        val first = ReplyQuote.from(text, 0, 4)!!
        val second = ReplyQuote.from(text, 10, 14)!!
        assertEquals(first.text, second.text)
        assertEquals(
            "Two identical quotes must be distinguished by position",
            0 to 10,
            first.position to second.position
        )
    }

    @Test
    fun formattingInsideTheQuotedSpanIsRebasedOntoTheExcerpt() {
        // "first" is bold at 4..9 in the original; quoting from 4 puts it at 0.
        val quote = ReplyQuote.from(source, 4, 14)!!
        val bold = quote.formatted.entities.filterIsInstance<AetherEntity.Bold>().single()
        assertEquals(0, bold.offset)
        assertEquals(5, bold.length)
        assertEquals("first", quote.text.substring(bold.offset, bold.end))
    }

    @Test
    fun formattingOverlappingOnlyPartlyIsClippedToTheQuote() {
        // Quote starts mid-bold: only the overlapping half survives.
        val quote = ReplyQuote.from(source, 6, 14)!!
        val bold = quote.formatted.entities.filterIsInstance<AetherEntity.Bold>().single()
        assertEquals(0, bold.offset)
        assertEquals(3, bold.length)
        assertEquals("rst", quote.text.substring(bold.offset, bold.end))
    }

    @Test
    fun formattingEntirelyOutsideTheQuoteIsDropped() {
        val quote = ReplyQuote.from(source, 19, 34)!!
        assertTrue(quote.formatted.entities.isEmpty())
    }

    @Test
    fun anEmptyOrWhitespaceSelectionProducesNoQuote() {
        assertNull(ReplyQuote.from(source, 5, 5))
        assertNull(ReplyQuote.from(AetherText("   "), 0, 3))
    }

    @Test
    fun aSelectionRunningPastTheEndIsClippedRatherThanCrashing() {
        val quote = ReplyQuote.from(source, 19, 9_000)!!
        assertEquals("the second part", quote.text)
    }

    @Test
    fun anInvertedRangeProducesNoQuote() {
        assertNull(ReplyQuote.from(source, 20, 4))
    }

    @Test
    fun quotingTheWholeMessageIsStillAQuoteAtPositionZero() {
        val quote = ReplyQuote.from(source, 0, source.text.length)!!
        assertEquals(source.text, quote.text)
        assertEquals(0, quote.position)
    }

    @Test
    fun offsetsStayInUtf16CodeUnitsAcrossEmoji() {
        val text = AetherText("👋 hello there")
        // "hello" begins at UTF-16 index 3.
        val quote = ReplyQuote.from(text, 3, 8)!!
        assertEquals("hello", quote.text)
        assertEquals(3, quote.position)
    }
}
