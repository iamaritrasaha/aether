package com.foresightlabs.aether.domain.model

import androidx.compose.runtime.Immutable

/**
 * A Telegram chat folder, as the server defines it.
 *
 * Folders are account state that syncs across every Telegram client. Aether reads
 * them; it never invents a second, local folder model, because a locally-invented
 * folder would disagree with every other client the account is signed into.
 *
 * This is distinct from Aether's own People / Groups / Channels / Unread chips,
 * which are a presentation filter over whatever list is showing and are deliberately
 * kept separate.
 */
@Immutable
data class ChatFolder(
    val id: Int,
    val title: String,
    /** Where the main list sits relative to the folders, per Telegram. */
    val isMainList: Boolean = false
) {
    companion object {
        /** The account's main chat list, which is not a folder but sits among them. */
        val Main = ChatFolder(id = 0, title = "All chats", isMainList = true)
    }
}
