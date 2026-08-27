package com.foresightlabs.aether

import com.foresightlabs.aether.data.telegram.ChatOrdering
import com.foresightlabs.aether.domain.model.ChatFolder
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Telegram chat folders.
 *
 * Folder membership is an *additional* chat position, not a replacement for the main
 * one — which is why a chat in a folder was still reachable before folders were read,
 * and why folder order must come from the folder's own position rather than from
 * re-sorting the main list.
 */
class ChatFolderTest {

    private fun position(list: TdApi.ChatList, order: Long, pinned: Boolean = false) =
        TdApi.ChatPosition(list, order, pinned, null)

    @Test
    fun aChatInAFolderKeepsItsMainListPositionToo() {
        val positions = arrayOf(
            position(TdApi.ChatListMain(), 900L),
            position(TdApi.ChatListFolder(7), 500L)
        )
        assertEquals(900L, ChatOrdering.mainPosition(positions)?.order)
        assertEquals(500L, ChatOrdering.folderPosition(positions, 7)?.order)
    }

    @Test
    fun aChatOutsideAFolderHasNoPositionInIt() {
        val positions = arrayOf(position(TdApi.ChatListMain(), 900L))
        assertNull(ChatOrdering.folderPosition(positions, 7))
    }

    @Test
    fun aZeroOrderInAFolderMeansTheChatIsNotInIt() {
        // TDLib reports removal as order 0 rather than by dropping the position.
        val positions = arrayOf(position(TdApi.ChatListFolder(7), 0L))
        assertNull(ChatOrdering.folderPosition(positions, 7))
    }

    @Test
    fun folderPinningIsSeparateFromMainListPinning() {
        val positions = arrayOf(
            position(TdApi.ChatListMain(), 900L, pinned = false),
            position(TdApi.ChatListFolder(3), 500L, pinned = true)
        )
        assertFalse(ChatOrdering.mainPosition(positions)!!.isPinned)
        assertTrue(ChatOrdering.folderPosition(positions, 3)!!.isPinned)
    }

    @Test
    fun oneFoldersPositionIsNotReadAsAnothers() {
        val positions = arrayOf(position(TdApi.ChatListFolder(3), 500L))
        assertNull(ChatOrdering.folderPosition(positions, 4))
    }

    @Test
    fun theArchiveIsNotAFolder() {
        val positions = arrayOf(position(TdApi.ChatListArchive(), 400L))
        assertTrue(ChatOrdering.isArchived(positions))
        assertNull(ChatOrdering.folderPosition(positions, 0))
    }

    @Test
    fun theMainListIsMarkedAsSuchSoItIsNeverQueriedAsAFolder() {
        assertTrue(ChatFolder.Main.isMainList)
        assertFalse(ChatFolder(id = 7, title = "Work").isMainList)
    }
}
