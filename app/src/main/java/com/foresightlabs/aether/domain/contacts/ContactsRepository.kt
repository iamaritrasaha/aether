package com.foresightlabs.aether.domain.contacts

import com.foresightlabs.aether.domain.model.User

data class DiscoveredContact(
    val name: String,
    val phone: String,
    val isTelegramUser: Boolean = false,
    val telegramUser: User? = null
)

/**
 * Clean abstraction for device contact discovery and Telegram cloud contact matching.
 * This separates UI from direct system ContactsContract or TDLib calls, ensuring
 * future minimum-scope contact providers (like photo/contact pickers) can be introduced cleanly.
 */
interface ContactsRepository {
    suspend fun getTelegramContacts(): List<User>
    suspend fun searchTelegramContacts(query: String, limit: Int = 50): List<User>
    suspend fun readDeviceContacts(): List<DiscoveredContact>
    suspend fun syncDeviceContactsWithTelegram(deviceContacts: List<DiscoveredContact>): List<DiscoveredContact>
}
