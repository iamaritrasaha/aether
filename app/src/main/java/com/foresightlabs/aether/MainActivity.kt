package com.foresightlabs.aether

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import com.foresightlabs.aether.navigation.AetherApp
import com.foresightlabs.aether.ui.theme.AetherTheme
import com.foresightlabs.aether.ui.theme.AppThemeState
import com.foresightlabs.aether.ui.theme.LocalAppThemeState
import androidx.compose.runtime.CompositionLocalProvider

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val appearanceRepository = com.foresightlabs.aether.data.preferences.AppearanceRepository.getInstance(applicationContext)
    setContent {
      val scope = androidx.compose.runtime.rememberCoroutineScope()
      val themeState = remember { AppThemeState(appearanceRepository, scope) }
      CompositionLocalProvider(
        LocalAppThemeState provides themeState,
        com.foresightlabs.aether.ui.theme.LocalAppearanceRepository provides appearanceRepository
      ) {
        AetherTheme(themeState = themeState) {
          AetherApp()
        }
      }
    }
  }
}

