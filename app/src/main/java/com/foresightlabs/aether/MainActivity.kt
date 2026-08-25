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
    setContent {
      val themeState = remember { AppThemeState() }
      CompositionLocalProvider(LocalAppThemeState provides themeState) {
        AetherTheme(themeState = themeState) {
          AetherApp()
        }
      }
    }
  }
}

