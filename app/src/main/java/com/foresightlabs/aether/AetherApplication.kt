package com.foresightlabs.aether

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.foresightlabs.aether.data.contacts.DefaultContactsRepository
import com.foresightlabs.aether.data.permissions.PermissionCoordinator
import com.foresightlabs.aether.data.telegram.TelegramClient
import com.foresightlabs.aether.domain.contacts.ContactsRepository

import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import com.foresightlabs.aether.data.calls.DefaultCallsRepository
import com.foresightlabs.aether.domain.calls.CallsRepository

class AetherApplication : Application(), ImageLoaderFactory {
    lateinit var telegram: TelegramClient
        private set

    lateinit var contactsRepository: ContactsRepository
        private set

    lateinit var permissionCoordinator: PermissionCoordinator
        private set

    lateinit var callsRepository: CallsRepository
        private set

    lateinit var liveLocationCoordinator: com.foresightlabs.aether.data.location.LiveLocationCoordinator
        private set

    private val applicationScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }

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
        liveLocationCoordinator = com.foresightlabs.aether.data.location.LiveLocationCoordinator(
            context = this,
            locationProvider = com.foresightlabs.aether.data.location.SystemLocationProvider(this),
            gateway = com.foresightlabs.aether.data.location.TelegramLiveLocationGateway(telegram),
            scope = applicationScope
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
