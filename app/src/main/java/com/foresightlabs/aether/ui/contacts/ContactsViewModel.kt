package com.foresightlabs.aether.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.foresightlabs.aether.domain.contacts.ContactsRepository
import com.foresightlabs.aether.domain.contacts.DiscoveredContact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ContactsViewModel(
    private val contactsRepository: ContactsRepository
) : ViewModel() {

    private val _contacts = MutableStateFlow<List<DiscoveredContact>>(emptyList())
    val contacts: StateFlow<List<DiscoveredContact>> = _contacts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hasDeviceContactsLoaded = MutableStateFlow(false)
    val hasDeviceContactsLoaded: StateFlow<Boolean> = _hasDeviceContactsLoaded.asStateFlow()

    init {
        loadTelegramContacts()
    }

    fun loadTelegramContacts() {
        viewModelScope.launch {
            _isLoading.value = true
            val tgUsers = contactsRepository.getTelegramContacts()
            _contacts.value = tgUsers.map { user ->
                DiscoveredContact(
                    name = user.name,
                    phone = user.phone,
                    isTelegramUser = true,
                    telegramUser = user
                )
            }.sortedBy { it.name.lowercase() }
            _isLoading.value = false
        }
    }

    fun onUserApprovedDeviceSync() {
        viewModelScope.launch {
            _isLoading.value = true
            val deviceContacts = contactsRepository.readDeviceContacts()
            if (deviceContacts.isNotEmpty()) {
                val synced = contactsRepository.syncDeviceContactsWithTelegram(deviceContacts)
                _contacts.value = synced
            } else {
                val tgUsers = contactsRepository.getTelegramContacts()
                _contacts.value = tgUsers.map { user ->
                    DiscoveredContact(
                        name = user.name,
                        phone = user.phone,
                        isTelegramUser = true,
                        telegramUser = user
                    )
                }.sortedBy { it.name.lowercase() }
            }
            _hasDeviceContactsLoaded.value = true
            _isLoading.value = false
        }
    }

    class Factory(
        private val contactsRepository: ContactsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ContactsViewModel::class.java)) {
                return ContactsViewModel(contactsRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
