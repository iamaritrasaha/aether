package com.foresightlabs.aether.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The bookmark store's on-disk format and edit rules: stable identities only.
 * Whatever the format does, it must never grow message content -- the bookmark
 * resolves everything else on demand, on the device, when the list is opened --
 * and one account's bookmarks must never touch another's.
 *
 * Robolectric because the format is org.json, which is an Android framework
 * class (a throwing stub on the plain JVM).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookmarkStoreTest {

    private val me = 111L
    private val other = 222L

    @Test
    fun encodeDecodeRoundTrips() {
        val bookmarks = listOf(
            BookmarkStore.Bookmark(accountId = me, chatId = -1001234L, messageId = 42L, savedAtSec = 1_757_822_400),
            BookmarkStore.Bookmark(accountId = other, chatId = 987L, messageId = 7L, savedAtSec = 1_700_000_000)
        )
        val decoded = BookmarkStore.decode(BookmarkStore.encode(bookmarks))
        assertEquals(bookmarks, decoded)
    }

    @Test
    fun theEncodingHoldsIdentitiesAndNothingElse() {
        val encoded = BookmarkStore.encode(
            listOf(BookmarkStore.Bookmark(accountId = me, chatId = 5L, messageId = 6L, savedAtSec = 7))
        )
        assertEquals("""[{"a":111,"c":5,"m":6,"t":7}]""", encoded)
    }

    @Test
    fun decodingGarbageYieldsNothing() {
        assertTrue(BookmarkStore.decode(null).isEmpty())
        assertTrue(BookmarkStore.decode("").isEmpty())
        assertTrue(BookmarkStore.decode("not json at all").isEmpty())
        assertTrue(BookmarkStore.decode("[1,2,3]").isEmpty())
        // Entries without a full identity are unusable and dropped.
        assertTrue(BookmarkStore.decode("""[{"c":5,"m":6,"t":7}]""").isEmpty())
    }

    @Test
    fun anEmptyListEncodesToAnEmptyArray() {
        assertTrue(BookmarkStore.decode(BookmarkStore.encode(emptyList())).isEmpty())
    }

    @Test
    fun toggleAddsThenRemoves() {
        val added = BookmarkStore.toggled(emptyList(), me, 5L, 6L, nowSec = 100)
        assertEquals(listOf(BookmarkStore.Bookmark(me, 5L, 6L, 100)), added)
        assertTrue(BookmarkStore.toggled(added, me, 5L, 6L, nowSec = 200).isEmpty())
    }

    @Test
    fun theSameMessageBookmarkedByTwoAccountsStaysTwoBookmarks() {
        val mine = BookmarkStore.toggled(emptyList(), me, 5L, 6L, nowSec = 100)
        val both = BookmarkStore.toggled(mine, other, 5L, 6L, nowSec = 101)
        assertEquals(2, both.size)
        // Removing mine leaves theirs untouched.
        val afterRemove = BookmarkStore.removed(both, me, 5L, 6L)
        assertEquals(listOf(BookmarkStore.Bookmark(other, 5L, 6L, 101)), afterRemove)
    }
}
