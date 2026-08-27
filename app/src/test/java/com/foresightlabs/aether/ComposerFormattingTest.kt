package com.foresightlabs.aether

import com.foresightlabs.aether.data.telegram.TelegramMappers
import com.foresightlabs.aether.domain.text.AetherEntity
import com.foresightlabs.aether.domain.text.AetherText
import com.foresightlabs.aether.domain.text.ComposerFormatting
import com.foresightlabs.aether.domain.text.ComposerStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Composer formatting.
 *
 * Everything here is in UTF-16 code units, matching TDLib. Converting to code points
 * or characters shifts every span in any message containing an emoji, which is the
 * most common way formatted text arrives corrupted.
 */
class ComposerFormattingTest {

    private fun bold(offset: Int, length: Int) = AetherEntity.Bold(offset, length)

    // --- toggling ------------------------------------------------------------

    @Test
    fun applyingAStyleToASelectionCreatesOneSpanOverIt() {
        val result = ComposerFormatting.toggle(emptyList(), ComposerStyle.BOLD, 2, 6)
        assertEquals(listOf(bold(2, 4)), result)
    }

    @Test
    fun togglingTwiceReturnsToWhereItStarted() {
        val once = ComposerFormatting.toggle(emptyList(), ComposerStyle.BOLD, 0, 5)
        val twice = ComposerFormatting.toggle(once, ComposerStyle.BOLD, 0, 5)
        assertTrue("A second toggle must undo the first: $twice", twice.isEmpty())
    }

    @Test
    fun aPartiallyStyledSelectionIsCompletedRatherThanCleared() {
        // "select a sentence where half is already bold, hit bold" must bold it all.
        val existing = listOf(bold(0, 3))
        val result = ComposerFormatting.toggle(existing, ComposerStyle.BOLD, 0, 10)
        assertEquals(listOf(bold(0, 10)), result)
    }

    @Test
    fun removingFromTheMiddleOfASpanKeepsBothEnds() {
        val existing = listOf(bold(0, 10))
        val result = ComposerFormatting.toggle(existing, ComposerStyle.BOLD, 4, 6)
        assertEquals(listOf(bold(0, 4), bold(6, 4)), result)
    }

    @Test
    fun adjacentRunsOfTheSameStyleMergeRatherThanAccumulate() {
        val existing = listOf(bold(0, 4))
        val result = ComposerFormatting.toggle(existing, ComposerStyle.BOLD, 4, 8)
        assertEquals(listOf(bold(0, 8)), result)
    }

    @Test
    fun differentStylesCoexistOverTheSameRange() {
        var entities = ComposerFormatting.toggle(emptyList(), ComposerStyle.BOLD, 0, 5)
        entities = ComposerFormatting.toggle(entities, ComposerStyle.ITALIC, 0, 5)
        assertEquals(2, entities.size)
        assertTrue(entities.any { it is AetherEntity.Bold })
        assertTrue(entities.any { it is AetherEntity.Italic })
    }

    @Test
    fun anEmptySelectionChangesNothing() {
        assertEquals(emptyList<AetherEntity>(), ComposerFormatting.toggle(emptyList(), ComposerStyle.BOLD, 3, 3))
    }

    @Test
    fun activeStylesReportOnlyWhatCoversTheWholeSelection() {
        val entities = listOf(bold(0, 3), AetherEntity.Italic(0, 10))
        val active = ComposerFormatting.activeStyles(entities, 0, 10)
        assertFalse(ComposerStyle.BOLD in active)
        assertTrue(ComposerStyle.ITALIC in active)
    }

    // --- re-anchoring across edits -------------------------------------------

    @Test
    fun insertingBeforeASpanShiftsIt() {
        val result = ComposerFormatting.reanchor(listOf(bold(5, 4)), changeStart = 0, removed = 0, inserted = 3)
        assertEquals(listOf(bold(8, 4)), result)
    }

    @Test
    fun insertingAfterASpanLeavesItAlone() {
        val result = ComposerFormatting.reanchor(listOf(bold(0, 4)), changeStart = 10, removed = 0, inserted = 3)
        assertEquals(listOf(bold(0, 4)), result)
    }

    @Test
    fun deletingBeforeASpanPullsItBack() {
        val result = ComposerFormatting.reanchor(listOf(bold(8, 4)), changeStart = 2, removed = 3, inserted = 0)
        assertEquals(listOf(bold(5, 4)), result)
    }

    @Test
    fun deletingAcrossASpanShrinksIt() {
        val result = ComposerFormatting.reanchor(listOf(bold(4, 6)), changeStart = 6, removed = 2, inserted = 0)
        assertEquals(listOf(bold(4, 4)), result)
    }

    @Test
    fun deletingASpanEntirelyRemovesIt() {
        val result = ComposerFormatting.reanchor(listOf(bold(4, 4)), changeStart = 3, removed = 8, inserted = 0)
        assertTrue("A span whose text is gone must not survive: $result", result.isEmpty())
    }

    // --- UTF-16 correctness ---------------------------------------------------

    @Test
    fun offsetsStayInUtf16CodeUnitsAcrossASurrogatePair() {
        // "👋" is two UTF-16 code units; "bold" therefore begins at index 3.
        val text = "👋 bold text"
        val entities = ComposerFormatting.toggle(emptyList(), ComposerStyle.BOLD, 3, 7)
        val span = entities.single()
        assertEquals("bold", text.substring(span.offset, span.end))
    }

    @Test
    fun aSpanCoveringAnEmojiKeepsBothOfItsCodeUnits() {
        val text = "hi 👋 there"
        // Cover "👋" — indices 3..5 in UTF-16.
        val span = ComposerFormatting.toggle(emptyList(), ComposerStyle.SPOILER, 3, 5).single()
        assertEquals(2, span.length)
        assertEquals("👋", text.substring(span.offset, span.end))
    }

    @Test
    fun aCombinedEmojiSequenceIsMeasuredInCodeUnitsNotGlyphs() {
        // Family emoji: several code points joined by zero-width joiners.
        val family = "👨‍👩‍👦"
        val text = "$family done"
        val span = ComposerFormatting.toggle(emptyList(), ComposerStyle.BOLD, 0, family.length).single()
        assertEquals(family.length, span.length)
        assertEquals(family, text.substring(span.offset, span.end))
    }

    @Test
    fun emojiInsertedBeforeFormattedTextShiftsTheSpanByTwoNotOne() {
        // The single most common corruption: treating an emoji as one unit.
        val result = ComposerFormatting.reanchor(listOf(bold(0, 4)), changeStart = 0, removed = 0, inserted = 2)
        assertEquals(listOf(bold(2, 4)), result)
    }

    // --- sanitising before send ----------------------------------------------

    @Test
    fun spansAreTrimmedToTheTextBeingSent() {
        val result = ComposerFormatting.sanitise(listOf(bold(2, 20)), textLength = 6)
        assertEquals(listOf(bold(2, 4)), result)
    }

    @Test
    fun aSpanEntirelyPastTheEndIsDropped() {
        assertTrue(ComposerFormatting.sanitise(listOf(bold(10, 4)), textLength = 5).isEmpty())
    }

    @Test
    fun sanitisedSpansSurviveConversionIntoTdlibEntities() {
        val entities = ComposerFormatting.sanitise(
            listOf(bold(0, 4), AetherEntity.Spoiler(5, 3)),
            textLength = 8
        )
        val td = TelegramMappers.toTdEntities(AetherText("hello ok", entities))
        assertEquals(2, td.size)
        val round = TelegramMappers.mapFormattedText(
            org.drinkless.tdlib.TdApi.FormattedText("hello ok", td)
        )
        assertEquals(entities, round.entities)
    }
}
