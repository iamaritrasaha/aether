package com.foresightlabs.aether

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.foresightlabs.aether.data.contacts.DefaultContactsRepository
import com.foresightlabs.aether.data.permissions.PermissionCoordinator
import com.foresightlabs.aether.data.telegram.TelegramClient
import com.foresightlabs.aether.domain.contacts.ContactsRepository

import com.foresightlabs.aether.data.calls.DefaultCallsRepository
import com.foresightlabs.aether.domain.calls.CallsRepository

class AetherApplication : Application() {
    lateinit var telegram: TelegramClient
        private set

    lateinit var contactsRepository: ContactsRepository
        private set

    lateinit var permissionCoordinator: PermissionCoordinator
        private set

    lateinit var callsRepository: CallsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        permissionCoordinator = PermissionCoordinator(this)
        createNotificationChannels()
        telegram = TelegramClient(this)
        telegram.start()
        contactsRepository = DefaultContactsRepository(
            context = applicationContext,
            telegram = telegram,
        )
        callsRepository = DefaultCallsRepository(
            telegram = telegram,
            application = this,
            permissionCoordinator = permissionCoordinator
        )
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            val messagesChannel = NotificationChannel(
                CHANNEL_MESSAGES,
                "Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for new Telegram messages"
                enableVibration(true)
            }

            val callsChannel = NotificationChannel(
                CHANNEL_CALLS,
                "Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for incoming Telegram calls"
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(messagesChannel)
            notificationManager.createNotificationChannel(callsChannel)
        }
    }

    companion object {
        const val CHANNEL_MESSAGES = "aether_messages"
        const val CHANNEL_CALLS = "aether_calls"
    }
}
