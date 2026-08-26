package com.foresightlabs.aether.data.contacts

import android.content.Context
import android.provider.ContactsContract
import com.foresightlabs.aether.data.telegram.TelegramClient
import com.foresightlabs.aether.domain.contacts.ContactsRepository
import com.foresightlabs.aether.domain.contacts.DiscoveredContact
import com.foresightlabs.aether.domain.model.User
import org.drinkless.tdlib.TdApi

class DefaultContactsRepository(
    private val context: Context,
    private val telegram: TelegramClient
) : ContactsRepository {

    override suspend fun getTelegramContacts(): List<User> {
        return telegram.getContacts()
    }

    override suspend fun searchTelegramContacts(query: String, limit: Int): List<User> {
        return telegram.searchContacts(query, limit)
    }

    override suspend fun readDeviceContacts(): List<DiscoveredContact> {
        val list = mutableListOf<DiscoveredContact>()
        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )
            cursor?.use {
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val name = if (nameIndex >= 0) it.getString(nameIndex).orEmpty().trim() else ""
                    val number = if (numberIndex >= 0) it.getString(numberIndex).orEmpty().trim() else ""
                    if (name.isNotBlank() && number.isNotBlank()) {
                        list.add(DiscoveredContact(name = name, phone = number))
                    }
                }
            }
        } catch (_: SecurityException) {
            // Permission denied or revoked
        }
        return list.distinctBy { cleanPhone(it.phone).ifBlank { it.name } }
    }

    override suspend fun syncDeviceContactsWithTelegram(deviceContacts: List<DiscoveredContact>): List<DiscoveredContact> {
        val tdContacts = deviceContacts.map { c ->
            val parts = c.name.split(" ", limit = 2)
            TdApi.ImportedContact(
                c.phone,
                parts.getOrElse(0) { "" },
                parts.getOrElse(1) { "" },
                null
            )
        }
        if (tdContacts.isNotEmpty()) {
            telegram.importContacts(tdContacts)
        }

        val tgUsers = telegram.getContacts()
        val byPhone = tgUsers.associateBy { cleanPhone(it.phone) }

        val combined = deviceContacts.map { dev ->
            val matched = byPhone[cleanPhone(dev.phone)]
            dev.copy(
                isTelegramUser = matched != null,
                telegramUser = matched
            )
        } + tgUsers.filter { user -> deviceContacts.none { cleanPhone(it.phone) == cleanPhone(user.phone) } }
            .map { user ->
                DiscoveredContact(
                    name = user.name,
                    phone = user.phone,
                    isTelegramUser = true,
                    telegramUser = user
                )
            }

        return combined.distinctBy { cleanPhone(it.phone).ifBlank { it.name } }
            .sortedBy { it.name.lowercase() }
    }

    private fun cleanPhone(phone: String): String = phone.replace(Regex("[^0-9+]"), "")
}
