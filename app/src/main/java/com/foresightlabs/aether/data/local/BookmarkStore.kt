package com.foresightlabs.aether.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.aetherBookmarkStore by preferencesDataStore(name = "aether_bookmarks")

/**
 * Aether-local message bookmarks.
 *
 * This is deliberately NOT Telegram's Saved Messages and never talks to the
 * network: it stores stable identities only -- the account that saved it, chat
 * id, message id, and when it was saved. No message text, no media, nothing
 * else a bookmark list can live without, because everything else can be
 * re-resolved on demand from TDLib when the list is opened. The data lives in
 * this device's private storage and follows no account anywhere.
 *
 * Every read and write is scoped to one Telegram account: message and chat ids
 * are only meaningful inside the account that saw them, so another account
 * signed in on this device never sees -- or resolves -- someone else's list.
 */
class BookmarkStore(private val context: Context) {

    data class Bookmark(
        /** Telegram user id of the account that saved it. */
        val accountId: Long,
        val chatId: Long,
        val messageId: Long,
        val savedAtSec: Int
    )

    private val all: Flow<List<Bookmark>> = context.aetherBookmarkStore.data.map { prefs ->
        decode(prefs[KEY_JSON])
    }

    /** The bookmarks [accountId] saved on this device, in save order. */
    fun bookmarksFor(accountId: Long): Flow<List<Bookmark>> =
        all.map { list -> list.filter { it.accountId == accountId } }.distinctUntilChanged()

    /** Adds the bookmark, or removes it when one for this message already exists. */
    suspend fun toggle(accountId: Long, chatId: Long, messageId: Long) {
        if (accountId == 0L || chatId == 0L || messageId == 0L) return
        val nowSec = (System.currentTimeMillis() / 1000).toInt()
        context.aetherBookmarkStore.edit { prefs ->
            prefs[KEY_JSON] = encode(toggled(decode(prefs[KEY_JSON]), accountId, chatId, messageId, nowSec))
        }
    }

    suspend fun remove(accountId: Long, chatId: Long, messageId: Long) {
        context.aetherBookmarkStore.edit { prefs ->
            prefs[KEY_JSON] = encode(removed(decode(prefs[KEY_JSON]), accountId, chatId, messageId))
        }
    }

    companion object {
        private val KEY_JSON = stringPreferencesKey("bookmarks_json")

        /** [list] with the bookmark for this message flipped: removed if present, appended if not. */
        fun toggled(
            list: List<Bookmark>,
            accountId: Long,
            chatId: Long,
            messageId: Long,
            nowSec: Int
        ): List<Bookmark> {
            val exists = list.any { it.matches(accountId, chatId, messageId) }
            return if (exists) {
                removed(list, accountId, chatId, messageId)
            } else {
                list + Bookmark(accountId, chatId, messageId, nowSec)
            }
        }

        fun removed(list: List<Bookmark>, accountId: Long, chatId: Long, messageId: Long): List<Bookmark> =
            list.filterNot { it.matches(accountId, chatId, messageId) }

        private fun Bookmark.matches(accountId: Long, chatId: Long, messageId: Long) =
            this.accountId == accountId && this.chatId == chatId && this.messageId == messageId

        fun decode(raw: String?): List<Bookmark> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val array = JSONArray(raw)
                (0 until array.length()).mapNotNull { index ->
                    val entry = array.optJSONObject(index) ?: return@mapNotNull null
                    Bookmark(
                        accountId = entry.optLong("a"),
                        chatId = entry.optLong("c"),
                        messageId = entry.optLong("m"),
                        savedAtSec = entry.optInt("t")
                    ).takeIf { it.accountId != 0L && it.chatId != 0L && it.messageId != 0L }
                }
            }.getOrDefault(emptyList())
        }

        fun encode(list: List<Bookmark>): String {
            val array = JSONArray()
            list.forEach { bookmark ->
                array.put(
                    JSONObject()
                        .put("a", bookmark.accountId)
                        .put("c", bookmark.chatId)
                        .put("m", bookmark.messageId)
                        .put("t", bookmark.savedAtSec)
                )
            }
            return array.toString()
        }
    }
}
