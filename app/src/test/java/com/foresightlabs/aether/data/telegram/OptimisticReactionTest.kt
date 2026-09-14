package com.foresightlabs.aether.data.telegram

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.Reaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [TelegramClient.applyOptimisticReaction] and [TelegramClient.revertReaction] --
 * the bubble must update the instant a reaction is tapped, in both directions
 * (add and remove), and be restorable to exactly what it showed before if the
 * server round-trip that follows turns out to fail. See [TelegramClient.addReaction]
 * and `ConversationViewModel.addReaction`, which pair these with the real
 * `TdApi.AddMessageReaction`/`RemoveMessageReaction` call.
 */
@RunWith(AndroidJUnit4::class)
class OptimisticReactionTest {

    private lateinit var client: TelegramClient

    @Before
    fun setUp() {
        client = TelegramClient(ApplicationProvider.getApplicationContext())
    }

    private fun message(id: Long, reactions: List<Reaction> = emptyList()) = Message(
        id = id.toString(),
        chatId = "1",
        senderId = "1",
        senderName = "Test",
        text = "msg-$id",
        timestamp = "",
        isOutgoing = true,
        reactions = reactions
    )

    @Test
    fun addingANewEmojiAppearsImmediatelyAsTheAccountsOwnReaction() {
        client.upsertConversation(1L, listOf(message(1)), prepend = false)

        client.applyOptimisticReaction(1L, 1L, "🔥", adding = true)

        val reactions = client.messagesFlow(1L).value.single().reactions
        assertEquals(listOf(Reaction("🔥", 1, userReacted = true)), reactions)
    }

    @Test
    fun addingToAnEmojiOthersAlreadyGaveIncrementsRatherThanDuplicating() {
        client.upsertConversation(1L, listOf(message(1, listOf(Reaction("🔥", 2)))), prepend = false)

        client.applyOptimisticReaction(1L, 1L, "🔥", adding = true)

        val reactions = client.messagesFlow(1L).value.single().reactions
        assertEquals(1, reactions.size)
        assertEquals(3, reactions.single().count)
        assertTrue(reactions.single().userReacted)
    }

    @Test
    fun removingTheAccountsOnlyReactionDropsItFromTheList() {
        client.upsertConversation(1L, listOf(message(1, listOf(Reaction("🔥", 1, userReacted = true)))), prepend = false)

        client.applyOptimisticReaction(1L, 1L, "🔥", adding = false)

        assertTrue(client.messagesFlow(1L).value.single().reactions.isEmpty())
    }

    @Test
    fun removingWhenOthersStillHaveItDecrementsRatherThanClearing() {
        client.upsertConversation(1L, listOf(message(1, listOf(Reaction("🔥", 3, userReacted = true)))), prepend = false)

        client.applyOptimisticReaction(1L, 1L, "🔥", adding = false)

        val reaction = client.messagesFlow(1L).value.single().reactions.single()
        assertEquals(2, reaction.count)
        assertTrue("The optimistic remove must clear the account's own flag", !reaction.userReacted)
    }

    @Test
    fun revertRestoresExactlyWhatWasThereBeforeTheOptimisticChange() {
        val original = listOf(Reaction("🔥", 2), Reaction("👍", 1, userReacted = true))
        client.upsertConversation(1L, listOf(message(1, original)), prepend = false)

        val previous = client.applyOptimisticReaction(1L, 1L, "🔥", adding = true)
        assertEquals(original, previous)
        assertEquals(3, client.messagesFlow(1L).value.single().reactions.first { it.emoji == "🔥" }.count)

        client.revertReaction(1L, 1L, previous!!)

        assertEquals(original, client.messagesFlow(1L).value.single().reactions)
    }

    @Test
    fun switchingReactionsOptimisticallyClearsPreviousUserReaction() {
        val initial = listOf(Reaction("👍", 1, userReacted = true), Reaction("🔥", 2, userReacted = false))
        client.upsertConversation(1L, listOf(message(1, initial)), prepend = false)

        val previous = client.applyOptimisticReaction(1L, 1L, "❤️", adding = true)

        assertEquals(initial, previous)
        val reactions = client.messagesFlow(1L).value.single().reactions
        assertTrue("Previous single user reaction should be removed", reactions.none { it.emoji == "👍" })
        val fire = reactions.first { it.emoji == "🔥" }
        assertEquals(2, fire.count)
        assertTrue(!fire.userReacted)
        val heart = reactions.first { it.emoji == "❤️" }
        assertEquals(1, heart.count)
        assertTrue(heart.userReacted)
    }

    @Test
    fun applyingToAMessageThatIsNotLoadedReturnsNullAndTouchesNothing() {
        client.upsertConversation(1L, listOf(message(1)), prepend = false)

        val previous = client.applyOptimisticReaction(1L, 999L, "🔥", adding = true)

        assertNull(previous)
        assertTrue(client.messagesFlow(1L).value.single().reactions.isEmpty())
    }
}
