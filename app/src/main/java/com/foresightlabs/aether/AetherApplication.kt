package com.foresightlabs.aether

import android.app.Application
import com.foresightlabs.aether.data.contacts.DefaultContactsRepository
import com.foresightlabs.aether.data.telegram.TelegramClient
import com.foresightlabs.aether.domain.contacts.ContactsRepository

class AetherApplication : Application() {
    lateinit var telegram: TelegramClient
        private set

    lateinit var contactsRepository: ContactsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        telegram = TelegramClient(this)
        telegram.start()
        contactsRepository = DefaultContactsRepository(
            context = applicationContext,
            telegram = telegram
        )
    }
}
