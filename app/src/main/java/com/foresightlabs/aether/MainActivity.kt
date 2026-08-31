package com.foresightlabs.aether

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.foresightlabs.aether.data.notifications.ActiveConversationTracker
import com.foresightlabs.aether.data.sharing.SharedContentInbox
import com.foresightlabs.aether.data.sharing.SharedIntents
import com.foresightlabs.aether.data.sharing.SharedUriGateway
import com.foresightlabs.aether.data.notifications.AetherNotificationManager
import com.foresightlabs.aether.navigation.AetherApp
import com.foresightlabs.aether.ui.theme.AetherTheme
import com.foresightlabs.aether.ui.theme.AppThemeState
import com.foresightlabs.aether.ui.theme.LocalAppThemeState

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    handleNotificationIntent(intent)
    handleShareIntent(intent)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
    )
    val appearanceRepository = com.foresightlabs.aether.data.preferences.AppearanceRepository.getInstance(applicationContext)
    setContent {
      val scope = androidx.compose.runtime.rememberCoroutineScope()
      val themeState = remember { AppThemeState(appearanceRepository, scope) }
      CompositionLocalProvider(
        LocalAppThemeState provides themeState,
        com.foresightlabs.aether.ui.theme.LocalAppearanceRepository provides appearanceRepository,
      ) {
        AetherTheme(themeState = themeState) {
          AetherApp()
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleNotificationIntent(intent)
    handleShareIntent(intent)
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
  private fun handleShareIntent(intent: Intent?) {
    if (!SharedIntents.isShare(intent)) return
    val gateway = SharedUriGateway(applicationContext)
    val content = SharedIntents.normalize(
      intent = intent,
      mimeTypeOf = gateway::mimeType,
      displayNameOf = gateway::displayName
    )
    SharedContentInbox.offer(content, SharedIntents.identityOf(intent))
  }

  private fun handleNotificationIntent(intent: Intent?) {
    if (intent == null) return
    val chatId = intent.getLongExtra(AetherNotificationManager.EXTRA_CHAT_ID, 0L)
    if (chatId != 0L) {
      ActiveConversationTracker.setPendingNavigationChatId(chatId)
    }
  }
}

