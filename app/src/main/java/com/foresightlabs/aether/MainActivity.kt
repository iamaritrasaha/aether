package com.foresightlabs.aether

import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foresightlabs.aether.data.network.AetherConnectivityState
import com.foresightlabs.aether.data.notifications.ActiveConversationTracker
import com.foresightlabs.aether.data.sharing.SharedContentInbox
import com.foresightlabs.aether.data.sharing.SharedIntents
import com.foresightlabs.aether.data.sharing.SharedUriGateway
import com.foresightlabs.aether.data.notifications.AetherNotificationManager
import com.foresightlabs.aether.navigation.AetherApp
import com.foresightlabs.aether.ui.home.atmosphere.LocalHeroSeed
import com.foresightlabs.aether.ui.security.AppLockGate
import com.foresightlabs.aether.ui.theme.AetherTheme
import com.foresightlabs.aether.ui.theme.AppThemeState
import com.foresightlabs.aether.ui.theme.LocalAppThemeState
import com.foresightlabs.aether.ui.theme.LocalAppearanceRepository
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * FragmentActivity, not the plainer ComponentActivity most single-Activity
 * Compose apps use -- AndroidX BiometricPrompt requires one to host the
 * system biometric prompt. FragmentActivity is itself a ComponentActivity,
 * so Compose's setContent and everything else below is unaffected.
 */
class MainActivity : androidx.fragment.app.FragmentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    handleNotificationIntent(intent)
    handleShareIntent(intent, isNewShare = false)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
    )
    val appearanceRepository = com.foresightlabs.aether.data.preferences.AppearanceRepository.getInstance(applicationContext)
    val aetherApp = applicationContext as? AetherApplication
    val connectivityObserver = aetherApp?.connectivityObserver

    setContent {
      val scope = rememberCoroutineScope()
      val themeState = remember { AppThemeState(appearanceRepository, scope) }
      val sessionSeed = remember { System.currentTimeMillis() }
      val connectivityState by (connectivityObserver?.state
          ?: MutableStateFlow(AetherConnectivityState.ONLINE))
          .collectAsStateWithLifecycle(initialValue = AetherConnectivityState.ONLINE)

      CompositionLocalProvider(
        LocalAppThemeState provides themeState,
        LocalAppearanceRepository provides appearanceRepository,
        LocalHeroSeed provides sessionSeed,
        com.foresightlabs.aether.ui.home.atmosphere.LocalAetherConnectivityState provides connectivityState
      ) {
        AetherTheme(themeState = themeState) {
          // Every entry point -- launcher, notification tap, share target,
          // process recreation -- reaches AetherApp() through this same call,
          // so there is exactly one place the lock gate can be bypassed from:
          // nowhere. See AppLockGate for why locked content is never composed.
          //
          // App Lock itself is held for this milestone (AetherFeatureFlags.
          // APP_LOCK_ENABLED) -- see that flag's doc for why nothing here
          // deletes the gate or its stored data, just stops composing it.
          if (AetherFeatureFlags.APP_LOCK_ENABLED) {
            AppLockGate {
              AetherApp()
            }
          } else {
            AetherApp()
          }
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleNotificationIntent(intent)
    handleShareIntent(intent, isNewShare = true)
  }

  /**
   * Takes in what another application shared.
   *
   * Called for all three ways a share can arrive -- cold start, a backgrounded
   * process being brought forward, and Aether already on screen -- because both
   * onCreate and onNewIntent lead here. The share is identified by what it is,
   * so the same Intent redelivered after the Activity is recreated is recognised
   * rather than presented a second time.
   *
   * The URIs are read through the content resolver while the sender's temporary
   * grant still holds; nothing here assumes a filesystem path, and nothing is
   * sent -- the share waits for a recipient and an explicit send.
   */
  private fun handleShareIntent(intent: Intent?, isNewShare: Boolean) {
    if (!SharedIntents.isShare(intent)) return
    val gateway = SharedUriGateway(applicationContext)
    val content = SharedIntents.normalize(
      intent = intent,
      mimeTypeOf = gateway::mimeType,
      displayNameOf = gateway::displayName
    )
    // onNewIntent is always a fresh share from the sending app (never a
    // recreation replay), so the identity dedupe is bypassed -- otherwise a
    // second share of identical content was silently dropped. onCreate
    // redelivers the stored launch intent on every recreation, where the
    // dedupe is exactly what prevents the picker reopening.
    if (isNewShare) {
      SharedContentInbox.offerNewShare(content, SharedIntents.identityOf(intent))
    } else {
      SharedContentInbox.offer(content, SharedIntents.identityOf(intent))
    }
  }

  private fun handleNotificationIntent(intent: Intent?) {
    if (intent == null) return
    val chatId = intent.getLongExtra(AetherNotificationManager.EXTRA_CHAT_ID, 0L)
    if (chatId != 0L) {
      ActiveConversationTracker.setPendingNavigationChatId(chatId)
      // Consumed: the same launch Intent is redelivered on every
      // configuration-change recreation (onCreate), and without this the app
      // force-jumped back into the notified chat on each rotation. Removing
      // the extra only affects this in-process copy; a fresh tap always
      // delivers a fresh PendingIntent.
      intent.removeExtra(AetherNotificationManager.EXTRA_CHAT_ID)
    }
  }
}

