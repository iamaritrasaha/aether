package com.foresightlabs.aether.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaReferenceIndexTest {
    @Test
    fun fileUpdateOnlyReturnsReferencingMessages() {
        val index = MediaReferenceIndex()
        val affected = MessageMediaReference(1L, 10L)
        val unrelated = MessageMediaReference(2L, 20L)
        index.replace(affected, setOf(7))
        index.replace(unrelated, setOf(8))

        assertEquals(setOf(affected), index.referencesFor(7))
        assertTrue(index.referencesFor(7).none { it.chatId == unrelated.chatId })
    }

    @Test
    fun replacingMessageReferencesRemovesStaleFileLinks() {
        val index = MediaReferenceIndex()
        val reference = MessageMediaReference(1L, 10L)
        index.replace(reference, setOf(7, 8))
        index.replace(reference, setOf(8))

        assertTrue(index.referencesFor(7).isEmpty())
        assertEquals(setOf(reference), index.referencesFor(8))
    }
}
