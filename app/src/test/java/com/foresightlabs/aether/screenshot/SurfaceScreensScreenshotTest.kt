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
import com.foresightlabs.aether.ui.auth.AuthScreen
import com.foresightlabs.aether.ui.appearance.ChatAppearanceScreen
import com.foresightlabs.aether.ui.profile.ProfileScreen
import com.foresightlabs.aether.ui.search.SearchScreen
import com.foresightlabs.aether.ui.settings.SettingsScreen
import com.foresightlabs.aether.ui.theme.AetherTheme
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

    private fun theme() = AppThemeState().apply {
        atmosphereMode = AtmosphereMode.MANUAL
        manualAtmosphere = TimeAtmospherePalette.DAY
    }

    private fun capture(name: String, screen: Screen) {
        if (!installed) {
            installed = true
            composeRule.setContent { scenario.value?.let { key(it.name) { Render(it) } } }
        }
        composeRule.runOnUiThread { scenario.value = Scenario(name, screen, theme()) }
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
                    Screen.SETTINGS -> SettingsScreen(
                        currentUser = HomeFixtures.me,
                        confirmLogout = false,
                        onBack = {},
                        onNavigateToAppearance = {},
                        onNavigateToAbout = {},
                        onRequestLogout = {},
                        onConfirmLogout = {},
                        onDismissLogout = {}
                    )
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

    @Test fun profile() {
        capture("profile", Screen.PROFILE)
        capture("profile-header-liquid-glass", Screen.PROFILE)
    }

    @Test fun settings() {
        capture("settings", Screen.SETTINGS)
        capture("settings-frosted-header", Screen.SETTINGS)
    }

    @Test fun chatAppearanceFloatingHeader() {
        capture("chat-appearance", Screen.CHAT_APPEARANCE)
    }

    @Test fun searchFloatingHeader() {
        capture("search", Screen.SEARCH)
    }

    @Test fun authStateMatrix() {
        capture("auth-phone", Screen.AUTH_PHONE)
        capture("auth-verification", Screen.AUTH_CODE)
        capture("auth-registration", Screen.AUTH_REGISTRATION)
    }
}
