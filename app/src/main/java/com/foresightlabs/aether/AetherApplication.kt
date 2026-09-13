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
import com.foresightlabs.aether.data.network.AetherConnectivityObserver
import com.foresightlabs.aether.data.security.AppLockCoordinator
import com.foresightlabs.aether.data.security.AppLockRepository
import com.foresightlabs.aether.domain.calls.CallsRepository
import kotlinx.coroutines.launch

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

    val connectivityObserver: AetherConnectivityObserver by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AetherConnectivityObserver.getInstance(this, telegram)
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

    /**
     * The Aether (LiveKit) call backend. Publishes into [callHub]; a call
     * from here carries backend=AETHER and is the primary, stable path.
     */
    val aetherCallsRepository: com.foresightlabs.aether.data.calls.aether.AetherCallsRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        com.foresightlabs.aether.data.calls.aether.AetherCallsRepository(
            context = this,
            hub = callHub,
            serviceClient = com.foresightlabs.aether.data.calls.aether.AetherCallServiceClient(
                com.foresightlabs.aether.BuildConfig.AETHER_CALL_SERVICE_URL
            ),
            scope = applicationScope,
            selfIdentity = {
                com.foresightlabs.aether.data.calls.aether.AetherInstallIdentity.toCallingIdentity(
                    this,
                    displayName = "Aether " + com.foresightlabs.aether.data.calls.aether.AetherInstallIdentity.aetherId(this).takeLast(4),
                    telegramUserId = telegram.getMyUserId().takeIf { it > 0 }
                )
            }
        )
    }

    /**
     * The one canonical active call across BOTH call backends
     * ([com.foresightlabs.aether.domain.calls.CallBackend]): a LiveKit call
     * and a Telegram call must never coexist -- they would fight over the
     * microphone, camera and audio focus. App surfaces observe this, never a
     * backend's flow directly.
     */
    val callHub: com.foresightlabs.aether.domain.calls.CallHub by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        com.foresightlabs.aether.domain.calls.CallHub(
            telegramState = (callsRepository as DefaultCallsRepository).activeCallState,
            scope = applicationScope
        ).also { hub ->
            hub.registerResumeHandler(
                com.foresightlabs.aether.domain.calls.CallBackend.TELEGRAM_BETA
            ) { callsRepository.setMinimized(false) }
            hub.registerMinimizeHandler(
                com.foresightlabs.aether.domain.calls.CallBackend.TELEGRAM_BETA
            ) { callsRepository.setMinimized(true) }
        }
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

    val appLockRepository: AppLockRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AppLockRepository.getInstance(this)
    }

    /**
     * Created (not just lazily read) in [onCreate] so [AppLockCoordinator.attachTo]
     * registers before any Activity can start -- a lock-enabled process must
     * never have a window in front of the user before this is watching it.
     */
    lateinit var appLockCoordinator: AppLockCoordinator
        private set

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
        appLockCoordinator = AppLockCoordinator(appLockRepository, applicationScope)
        appLockCoordinator.attachTo(this)
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

        // Cross-backend preemption: when the OTHER backend's newer call takes
        // the single call slot, the displaced backend tears its own call down.
        // Each side only ever reacts to its own name here -- backend isolation.
        // Touching callHub builds the whole call stack (repository, media
        // engine, notifier), so a build with calling held never touches it.
        if (AetherFeatureFlags.CALLS_ENABLED) applicationScope.launch {
            callHub.preemptedBackend.collect { preempted ->
                if (preempted == com.foresightlabs.aether.domain.calls.CallBackend.TELEGRAM_BETA) {
                    val repo = callsRepository
                    repo.activeCallState.value?.callId?.let { callId ->
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                            repo.discardCall(callId)
                        }
                    }
                }
            }
        }
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
    @Suppress("DEPRECATION")
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
            if (AetherFeatureFlags.CALLS_ENABLED) {
                notificationManager.createNotificationChannel(callsChannel)
            } else {
                // A build without calling shows no "Calls" category in system
                // settings -- including one an earlier install left behind.
                notificationManager.deleteNotificationChannel(CHANNEL_CALLS)
            }
        }
    }

    companion object {
        const val CHANNEL_MESSAGES = "aether_messages"
        const val CHANNEL_CALLS = "aether_calls"
    }
}
