package com.foresightlabs.aether

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.foresightlabs.aether.data.contacts.DefaultContactsRepository
import com.foresightlabs.aether.data.permissions.PermissionCoordinator
import com.foresightlabs.aether.data.telegram.TelegramClient
import com.foresightlabs.aether.data.preferences.OnboardingRepository
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

    lateinit var onboardingRepository: OnboardingRepository
        private set

    val contactsRepository: ContactsRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        DefaultContactsRepository(
            context = applicationContext,
            telegram = telegram,
        )
    }

    lateinit var permissionCoordinator: PermissionCoordinator
        private set

    val callsRepository: CallsRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        DefaultCallsRepository(
            telegram = telegram,
            application = this,
            permissionCoordinator = permissionCoordinator
        )
    }

    val liveLocationCoordinator: com.foresightlabs.aether.data.location.LiveLocationCoordinator by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        com.foresightlabs.aether.data.location.LiveLocationCoordinator(
            context = this,
            locationProvider = com.foresightlabs.aether.data.location.SystemLocationProvider(this),
            gateway = com.foresightlabs.aether.data.location.TelegramLiveLocationGateway(telegram),
            scope = applicationScope
        )
    }

    lateinit var notificationManager: com.foresightlabs.aether.data.notifications.AetherNotificationManager
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
        val legacyInstallation = filesDir.resolve("tdlib").exists() ||
            filesDir.resolve("tdlib-files").exists()
        onboardingRepository = OnboardingRepository(this, legacyInstallation)
        telegram = TelegramClient(this)
        notificationManager = com.foresightlabs.aether.data.notifications.AetherNotificationManager(
            context = this,
            getChat = { telegram.getRawChat(it) },
            getUser = { telegram.getRawUser(it) },
            getMyUserId = { telegram.getMyUserId() }
        )
        telegram.notificationManager = notificationManager
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var resumedActivities = 0

            override fun onActivityResumed(activity: android.app.Activity) {
                resumedActivities++
                com.foresightlabs.aether.data.notifications.ActiveConversationTracker.setAppForeground(true)
                telegram.setOnline(true)
            }

            override fun onActivityPaused(activity: android.app.Activity) {
                resumedActivities--
                if (resumedActivities <= 0) {
                    resumedActivities = 0
                    com.foresightlabs.aether.data.notifications.ActiveConversationTracker.setAppForeground(false)
                    telegram.setOnline(false)
                }
            }

            override fun onActivityStarted(activity: android.app.Activity) {}
            override fun onActivityStopped(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })
        telegram.start()
        telegram.setOnline(false)
        registerExistingFcmToken()
    }

    /**
     * Fetches whatever FCM token already exists for this install (a fresh
     * token generation, or one issued on a previous run) so a device that
     * never receives an onNewToken call during this process's lifetime still
     * gets registered with Telegram. [TelegramClient.registerFcmToken] is
     * idempotent, so this racing with a live onNewToken callback is harmless.
     */
    private fun registerExistingFcmToken() {
        if (!BuildConfig.HAS_FCM_CONFIG) {
            if (BuildConfig.DEBUG) {
                android.util.Log.d("AetherTd", "FCM_DISABLED_MISSING_CONFIG")
            }
            return
        }
        try {
            if (com.google.firebase.FirebaseApp.getApps(this).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(this)
            }
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token -> telegram.registerFcmToken(token) }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                android.util.Log.w("AetherTd", "FCM_INIT_FAILED", e)
            }
        }
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
                setShowBadge(true)
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
