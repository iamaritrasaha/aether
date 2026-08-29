package com.foresightlabs.aether.data.telegram
import com.foresightlabs.aether.data.telegram.TelegramMappers
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reactions.
 *
 * Aether previously only ever *wrote* reactions: it called `addMessageReaction` and
 * never read `interactionInfo` back, so a reaction it sent was invisible until the
 * conversation was reloaded, and reactions from anyone else never appeared at all.
 */
class ReactionMappingTest {

    private fun info(vararg reactions: TdApi.MessageReaction) =
        TdApi.MessageInteractionInfo(
            0,
            0,
            null,
            TdApi.MessageReactions(arrayOf(*reactions), false, emptyArray(), false)
        )

    private fun emoji(value: String, count: Int, chosen: Boolean = false) =
        TdApi.MessageReaction(TdApi.ReactionTypeEmoji(value), count, chosen, null, emptyArray())

    @Test
    fun reactionCountsComeFromTelegram() {
        val mapped = TelegramMappers.mapReactions(info(emoji("🔥", 4), emoji("👍", 2)))
        assertEquals(2, mapped.size)
        assertEquals("🔥", mapped[0].emoji)
        assertEquals(4, mapped[0].count)
        assertEquals(2, mapped[1].count)
    }

    @Test
    fun theAccountsOwnReactionIsMarkedFromTheServersFlag() {
        val mapped = TelegramMappers.mapReactions(
            info(emoji("🔥", 4, chosen = true), emoji("👍", 2))
        )
        assertTrue(mapped[0].userReacted)
        assertFalse(mapped[1].userReacted)
    }

    @Test
    fun aMessageWithNoReactionsMapsToNone() {
        assertTrue(TelegramMappers.mapReactions(null).isEmpty())
        assertTrue(TelegramMappers.mapReactions(info()).isEmpty())
        assertTrue(
            TelegramMappers.mapReactions(
                TdApi.MessageInteractionInfo(0, 0, null, null)
            ).isEmpty()
        )
    }

    @Test
    fun aCustomEmojiReactionKeepsItsCountEvenThoughItsGlyphCannotBeDrawn() {
        val custom = TdApi.MessageReaction(
            TdApi.ReactionTypeCustomEmoji(9001L),
            7,
            false,
            null,
            emptyArray()
        )
        val mapped = TelegramMappers.mapReactions(info(custom))
        assertEquals(1, mapped.size)
        assertEquals("Its count is real and belongs in the total", 7, mapped.single().count)
        assertEquals("", mapped.single().emoji)
    }

    @Test
    fun reactionOrderFromTheServerIsPreserved() {
        val mapped = TelegramMappers.mapReactions(
            info(emoji("a", 1), emoji("b", 5), emoji("c", 3))
        )
        assertEquals(listOf("a", "b", "c"), mapped.map { it.emoji })
    }
}
