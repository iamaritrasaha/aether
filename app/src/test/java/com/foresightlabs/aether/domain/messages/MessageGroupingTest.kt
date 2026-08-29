package com.foresightlabs.aether.domain.messages
import com.foresightlabs.aether.domain.messages.ConversationEntry
import com.foresightlabs.aether.domain.messages.MessageGrouping
import com.foresightlabs.aether.domain.model.MediaItem
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.MessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Album grouping.
 *
 * Telegram sends grouped media as several messages meant to be read as one. Drawing
 * each as its own full-width bubble misrepresents what was sent and repeats the
 * caption once per member.
 */
class MessageGroupingTest {

    private fun message(
        id: String,
        albumId: Long = 0L,
        text: String = "",
        type: MessageType = MessageType.IMAGE
    ) = Message(
        id = id,
        chatId = "100",
        senderId = "1",
        senderName = "Sam",
        text = text,
        timestamp = "12:00",
        isOutgoing = false,
        type = type,
        mediaAlbumId = albumId,
        mediaItems = listOf(MediaItem(id = id, url = "/data/$id.jpg"))
    )

    @Test
    fun standaloneMessagesEachBecomeTheirOwnRow() {
        val entries = MessageGrouping.group(listOf(message("1"), message("2")))
        assertEquals(2, entries.size)
        assertTrue(entries.all { it is ConversationEntry.Single })
    }

    @Test
    fun messagesSharingAnAlbumIdBecomeOneRow() {
        val entries = MessageGrouping.group(
            listOf(message("1", albumId = 77L), message("2", albumId = 77L), message("3", albumId = 77L))
        )
        assertEquals(1, entries.size)
        val album = entries.single() as ConversationEntry.Album
        assertEquals(3, album.messages.size)
        assertEquals(77L, album.albumId)
    }

    @Test
    fun twoAlbumsStayApart() {
        val entries = MessageGrouping.group(
            listOf(
                message("1", albumId = 1L), message("2", albumId = 1L),
                message("3", albumId = 2L), message("4", albumId = 2L)
            )
        )
        assertEquals(2, entries.size)
        assertTrue(entries.all { it is ConversationEntry.Album })
    }

    @Test
    fun onlyAdjacentMembersAreGrouped() {
        // A non-adjacent repeat of the same id must not pull two clusters together.
        val entries = MessageGrouping.group(
            listOf(
                message("1", albumId = 9L), message("2", albumId = 9L),
                message("3"),
                message("4", albumId = 9L), message("5", albumId = 9L)
            )
        )
        assertEquals(3, entries.size)
        assertTrue(entries[0] is ConversationEntry.Album)
        assertTrue(entries[1] is ConversationEntry.Single)
        assertTrue(entries[2] is ConversationEntry.Album)
    }

    @Test
    fun aLoneAlbumMemberIsASingleMessageNotAnAlbumOfOne() {
        // What a partly-deleted album leaves behind.
        val entries = MessageGrouping.group(listOf(message("1", albumId = 5L)))
        assertTrue(entries.single() is ConversationEntry.Single)
    }

    @Test
    fun theCaptionIsTakenOnceFromTheMemberThatCarriesIt() {
        val album = MessageGrouping.group(
            listOf(
                message("1", albumId = 3L, text = "Trip photos"),
                message("2", albumId = 3L),
                message("3", albumId = 3L)
            )
        ).single() as ConversationEntry.Album

        assertEquals("Trip photos", album.caption)
    }

    @Test
    fun anAlbumWithNoCaptionReportsNone() {
        val album = MessageGrouping.group(
            listOf(message("1", albumId = 3L), message("2", albumId = 3L))
        ).single() as ConversationEntry.Album
        assertEquals("", album.caption)
    }

    @Test
    fun theAnchorIsTheOldestMemberSoActionsTargetARealMessage() {
        val album = MessageGrouping.group(
            listOf(message("10", albumId = 4L), message("11", albumId = 4L))
        ).single() as ConversationEntry.Album
        assertEquals("10", album.anchor.id)
    }

    @Test
    fun everyEntryKeyIsStableAndDistinct() {
        val entries = MessageGrouping.group(
            listOf(message("1"), message("2", albumId = 8L), message("3", albumId = 8L))
        )
        val keys = entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertEquals(MessageGrouping.group(
            listOf(message("1"), message("2", albumId = 8L), message("3", albumId = 8L))
        ).map { it.key }, keys)
    }

    @Test
    fun anEmptyConversationGroupsToNothing() {
        assertTrue(MessageGrouping.group(emptyList()).isEmpty())
    }
}
