package com.foresightlabs.aether.ui.contacts
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.foresightlabs.aether.domain.contacts.ContactsRepository
import com.foresightlabs.aether.domain.contacts.DiscoveredContact
import com.foresightlabs.aether.domain.model.Presence
import com.foresightlabs.aether.domain.model.User
import com.foresightlabs.aether.ui.contacts.ContactsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContactsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeContactsRepository : ContactsRepository {
        var telegramContacts = listOf(
            User(
                id = "1",
                name = "Alice Smith",
                phone = "+1234567890",
                username = "@alice",
                avatarInitials = "AS",
                avatarGradient = listOf(Color(0xFF4DA3FF), Color(0xFF1D4ED8)),
                presence = Presence.OFFLINE
            )
        )
        var deviceContacts = listOf(
            DiscoveredContact(name = "Bob Jones", phone = "+1987654321")
        )
        var getTelegramContactsCallCount = 0
        var readDeviceContactsCallCount = 0
        var syncDeviceContactsCallCount = 0

        override suspend fun getTelegramContacts(): List<User> {
            getTelegramContactsCallCount++
            return telegramContacts
        }

        override suspend fun searchTelegramContacts(query: String, limit: Int): List<User> {
            return telegramContacts.filter { it.name.contains(query, ignoreCase = true) }
        }

        override suspend fun readDeviceContacts(): List<DiscoveredContact> {
            readDeviceContactsCallCount++
            return deviceContacts
        }

        override suspend fun syncDeviceContactsWithTelegram(deviceContacts: List<DiscoveredContact>): List<DiscoveredContact> {
            syncDeviceContactsCallCount++
            return deviceContacts.map { it.copy(isTelegramUser = true) } + telegramContacts.map {
                DiscoveredContact(name = it.name, phone = it.phone, isTelegramUser = true, telegramUser = it)
            }
        }
    }

    @Test
    fun factoryCreatesContactsViewModelWithoutApplicationConstructor() = runTest(testDispatcher) {
        val fakeRepo = FakeContactsRepository()
        val factory = ContactsViewModel.Factory(fakeRepo)
        val viewModelStore = ViewModelStore()
        val provider = ViewModelProvider(viewModelStore, factory)

        // Verifies ViewModelProvider creates instance via Factory without reflection NoSuchMethodException
        val viewModel = provider[ContactsViewModel::class.java]
        assertNotNull("ContactsViewModel should be instantiated via Factory", viewModel)

        advanceUntilIdle()

        assertEquals("Should have called getTelegramContacts on init", 1, fakeRepo.getTelegramContactsCallCount)
        assertEquals(1, viewModel.contacts.value.size)
        assertEquals("Alice Smith", viewModel.contacts.value.first().name)
        assertFalse(viewModel.isLoading.value)

        viewModelStore.clear()
    }

    @Test
    fun onUserApprovedDeviceSyncDelegatesToRepositoryAndUpdatesState() = runTest(testDispatcher) {
        val fakeRepo = FakeContactsRepository()
        val factory = ContactsViewModel.Factory(fakeRepo)
        val viewModel = factory.create(ContactsViewModel::class.java)

        advanceUntilIdle()
        assertEquals(1, viewModel.contacts.value.size)

        viewModel.onUserApprovedDeviceSync()
        advanceUntilIdle()

        assertEquals(1, fakeRepo.readDeviceContactsCallCount)
        assertEquals(1, fakeRepo.syncDeviceContactsCallCount)
        assertTrue("Device contacts should be marked loaded", viewModel.hasDeviceContactsLoaded.value)
        assertEquals(2, viewModel.contacts.value.size)
        assertFalse(viewModel.isLoading.value)
    }
}
