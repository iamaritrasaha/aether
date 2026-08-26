package com.foresightlabs.aether.screenshot

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.domain.model.AuthUiState
import com.foresightlabs.aether.data.preferences.AppearanceRepository
import com.foresightlabs.aether.ui.screens.AuthScreen
import com.foresightlabs.aether.ui.screens.ChatAppearanceScreen
import com.foresightlabs.aether.ui.screens.ProfileScreen
import com.foresightlabs.aether.ui.screens.SearchScreen
import com.foresightlabs.aether.ui.screens.SettingsScreen
import com.foresightlabs.aether.ui.theme.AetherTheme
import com.foresightlabs.aether.ui.theme.AppThemeMode
import com.foresightlabs.aether.ui.theme.AppThemeState
import com.foresightlabs.aether.ui.theme.AtmosphereMode
import com.foresightlabs.aether.ui.theme.LocalAppThemeState
import com.foresightlabs.aether.ui.theme.LocalAppearanceRepository
import com.foresightlabs.aether.ui.theme.TimeAtmospherePalette
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi", application = Application::class)
class SurfaceScreensScreenshotTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private enum class Screen { PROFILE, SETTINGS, SEARCH, CHAT_APPEARANCE, AUTH_PHONE, AUTH_CODE, AUTH_REGISTRATION }
    private data class Scenario(val name: String, val screen: Screen, val theme: AppThemeState)
    private val scenario = mutableStateOf<Scenario?>(null)
    private var installed = false
    private val outputDir = File("build/reports/aether-screenshots").apply { mkdirs() }
    private val appearanceRepository = AppearanceRepository(
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        ) { Files.createTempDirectory("aether-header-render").resolve("prefs.preferences_pb").toFile() }
    )

    private fun theme(mode: AppThemeMode) = AppThemeState().apply {
        themeMode = mode
        atmosphereMode = AtmosphereMode.MANUAL
        manualAtmosphere = TimeAtmospherePalette.DAY
    }

    private fun capture(name: String, screen: Screen, mode: AppThemeMode) {
        if (!installed) {
            installed = true
            composeRule.setContent { scenario.value?.let { key(it.name) { Render(it) } } }
        }
        composeRule.runOnUiThread { scenario.value = Scenario(name, screen, theme(mode)) }
        composeRule.waitForIdle()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        val view = composeRule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val file = File(outputDir, "$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        assertTrue(file.length() > 0)
    }

    @Composable private fun Render(active: Scenario) {
        CompositionLocalProvider(
            LocalInspectionMode provides true,
            LocalAppThemeState provides active.theme,
            LocalAppearanceRepository provides appearanceRepository
        ) {
            AetherTheme(active.theme) {
                when (active.screen) {
                    Screen.PROFILE -> ProfileScreen(HomeFixtures.populated.first(), {}, {})
                    Screen.SETTINGS -> SettingsScreen(HomeFixtures.me, false, {}, {}, {}, {}, {})
                    Screen.SEARCH -> SearchScreen(HomeFixtures.populated, {}, {}, {})
                    Screen.CHAT_APPEARANCE -> ChatAppearanceScreen(chatId = 103L, onBack = {})
                    Screen.AUTH_PHONE -> Auth(AuthUiState.Phone())
                    Screen.AUTH_CODE -> Auth(AuthUiState.Code("+91 ••••••1234", 5, ""))
                    Screen.AUTH_REGISTRATION -> Auth(AuthUiState.Registration())
                }
            }
        }
    }

    @Composable private fun Auth(state: AuthUiState) = AuthScreen(
        state = state, busy = false, error = null,
        onSubmitPhone = {}, onSubmitCode = {}, onSubmitPassword = {},
        onRegister = { _, _ -> }, onResendCode = {}
    )

    @Test fun profileDarkAndLight() {
        capture("profile-dark", Screen.PROFILE, AppThemeMode.DARK)
        capture("profile-header-liquid-glass", Screen.PROFILE, AppThemeMode.DARK)
        capture("profile-light", Screen.PROFILE, AppThemeMode.LIGHT)
    }

    @Test fun settingsDarkAndLight() {
        capture("settings-dark", Screen.SETTINGS, AppThemeMode.DARK)
        capture("settings-frosted-header", Screen.SETTINGS, AppThemeMode.DARK)
        capture("settings-light", Screen.SETTINGS, AppThemeMode.LIGHT)
        capture("settings-oled", Screen.SETTINGS, AppThemeMode.OLED)
    }

    @Test fun chatAppearanceFloatingHeader() {
        capture("chat-appearance", Screen.CHAT_APPEARANCE, AppThemeMode.DARK)
        capture("chat-appearance-light", Screen.CHAT_APPEARANCE, AppThemeMode.LIGHT)
    }

    @Test fun searchFloatingHeaderDarkAndLight() {
        capture("search-dark", Screen.SEARCH, AppThemeMode.DARK)
        capture("search-light", Screen.SEARCH, AppThemeMode.LIGHT)
    }

    @Test fun authStateMatrix() {
        capture("auth-phone-dark", Screen.AUTH_PHONE, AppThemeMode.DARK)
        capture("auth-phone-light", Screen.AUTH_PHONE, AppThemeMode.LIGHT)
        capture("auth-verification", Screen.AUTH_CODE, AppThemeMode.DARK)
        capture("auth-registration", Screen.AUTH_REGISTRATION, AppThemeMode.DARK)
    }
}
