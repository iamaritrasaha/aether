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
import com.foresightlabs.aether.data.notifications.AetherNotificationManager
import com.foresightlabs.aether.navigation.AetherApp
import com.foresightlabs.aether.ui.theme.AetherTheme
import com.foresightlabs.aether.ui.theme.AppThemeState
import com.foresightlabs.aether.ui.theme.LocalAppThemeState

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    handleNotificationIntent(intent)
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
  }

  private fun handleNotificationIntent(intent: Intent?) {
    if (intent == null) return
    val chatId = intent.getLongExtra(AetherNotificationManager.EXTRA_CHAT_ID, 0L)
    if (chatId != 0L) {
      ActiveConversationTracker.setPendingNavigationChatId(chatId)
    }
  }
}

