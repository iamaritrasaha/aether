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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.domain.contacts.DiscoveredContact
import com.foresightlabs.aether.domain.model.Presence
import com.foresightlabs.aether.domain.model.User
import com.foresightlabs.aether.ui.screens.ContactsScreen
import com.foresightlabs.aether.ui.theme.AetherTheme
import com.foresightlabs.aether.ui.theme.AppThemeState
import com.foresightlabs.aether.ui.theme.AtmosphereMode
import com.foresightlabs.aether.ui.theme.LocalAppThemeState
import com.foresightlabs.aether.ui.theme.TimeAtmospherePalette
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi", application = Application::class)
class ContactsScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private data class Scenario(
        val name: String,
        val contacts: List<DiscoveredContact>,
        val isLoading: Boolean,
        val hasDeviceContactsLoaded: Boolean,
        val theme: AppThemeState
    )

    private val scenario = mutableStateOf<Scenario?>(null)
    private var contentInstalled = false

    private val outputDir = File("build/reports/aether-screenshots").apply { mkdirs() }

    private fun themeState(palette: TimeAtmospherePalette) = AppThemeState().apply {
        atmosphereMode = AtmosphereMode.MANUAL
        manualAtmosphere = palette
    }

    private fun capture(
        name: String,
        contacts: List<DiscoveredContact>,
        isLoading: Boolean = false,
        hasDeviceContactsLoaded: Boolean = false,
        state: AppThemeState = themeState(TimeAtmospherePalette.DAY)
    ) {
        val next = Scenario(name, contacts, isLoading, hasDeviceContactsLoaded, state)
        if (!contentInstalled) {
            contentInstalled = true
            composeRule.setContent {
                scenario.value?.let { active ->
                    key(active.name) { Render(active) }
                }
            }
        }
        composeRule.runOnUiThread { scenario.value = next }
        composeRule.waitForIdle()
        writePng(name)
    }

    @Composable
    private fun Render(active: Scenario) {
        CompositionLocalProvider(
            LocalInspectionMode provides true,
            LocalAppThemeState provides active.theme
        ) {
            AetherTheme(themeState = active.theme) {
                ContactsScreen(
                    contacts = active.contacts,
                    isLoading = active.isLoading,
                    hasDeviceContactsLoaded = active.hasDeviceContactsLoaded,
                    onContactClick = {},
                    onBack = {},
                    onRequestDeviceSync = {}
                )
            }
        }
    }

    private fun writePng(name: String) {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        val view = composeRule.activity.window.decorView
        require(view.width > 0 && view.height > 0) {
            "decor view was not laid out (${view.width}x${view.height})"
        }
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val file = File(outputDir, "$name.png")
        file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        bitmap.recycle()
        assertTrue("no pixels written for $name", file.length() > 0)
    }

    @Test
    fun contactsPopulated() {
        val list = listOf(
            DiscoveredContact(
                name = "Aarav Sharma",
                phone = "+1 555 0101",
                isTelegramUser = true,
                telegramUser = User(
                    id = "101",
                    name = "Aarav Sharma",
                    username = "aarav",
                    avatarInitials = "AS",
                    avatarGradient = listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8)),
                    phone = "+1 555 0101",
                    presence = Presence.ONLINE
                )
            ),
            DiscoveredContact(
                name = "Dev Malhotra",
                phone = "+1 555 0102",
                isTelegramUser = true,
                telegramUser = User(
                    id = "102",
                    name = "Dev Malhotra",
                    username = "devm",
                    avatarInitials = "DM",
                    avatarGradient = listOf(Color(0xFF10B981), Color(0xFF047857)),
                    phone = "+1 555 0102",
                    presence = Presence.OFFLINE,
                    lastSeenText = "last seen 2h ago"
                )
            ),
            DiscoveredContact(
                name = "Ishani Roy",
                phone = "+1 555 0103",
                isTelegramUser = true,
                telegramUser = User(
                    id = "103",
                    name = "Ishani Roy",
                    username = "ishani",
                    avatarInitials = "IR",
                    avatarGradient = listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)),
                    phone = "+1 555 0103",
                    presence = Presence.ONLINE
                )
            ),
            DiscoveredContact(
                name = "Marcus Vance",
                phone = "+1 555 0104",
                isTelegramUser = false
            ),
            DiscoveredContact(
                name = "Meera Nair",
                phone = "+1 555 0105",
                isTelegramUser = true,
                telegramUser = User(
                    id = "105",
                    name = "Meera Nair",
                    username = "meera",
                    avatarInitials = "MN",
                    avatarGradient = listOf(Color(0xFFEC4899), Color(0xFFBE185D)),
                    phone = "+1 555 0105",
                    presence = Presence.OFFLINE,
                    lastSeenText = "last seen yesterday"
                )
            ),
            DiscoveredContact(
                name = "Zoya Akhtar",
                phone = "+1 555 0106",
                isTelegramUser = false
            )
        )
        capture(
            name = "contacts-dark",
            contacts = list,
            hasDeviceContactsLoaded = true,
            state = themeState(TimeAtmospherePalette.DAY)
        )
    }

    @Test
    fun contactsTelegramOnlyWithDiscoveryCard() {
        val list = listOf(
            DiscoveredContact(
                name = "Aarav Sharma",
                phone = "+1 555 0101",
                isTelegramUser = true,
                telegramUser = User(
                    id = "101",
                    name = "Aarav Sharma",
                    username = "aarav",
                    avatarInitials = "AS",
                    avatarGradient = listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8)),
                    phone = "+1 555 0101",
                    presence = Presence.ONLINE
                )
            ),
            DiscoveredContact(
                name = "Ishani Roy",
                phone = "+1 555 0103",
                isTelegramUser = true,
                telegramUser = User(
                    id = "103",
                    name = "Ishani Roy",
                    username = "ishani",
                    avatarInitials = "IR",
                    avatarGradient = listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)),
                    phone = "+1 555 0103",
                    presence = Presence.ONLINE
                )
            )
        )
        capture(
            name = "contacts-cloud-only",
            contacts = list,
            hasDeviceContactsLoaded = false,
            state = themeState(TimeAtmospherePalette.DAY)
        )
    }

    @Test
    fun contactsEmpty() {
        capture(
            name = "contacts-empty",
            contacts = emptyList(),
            hasDeviceContactsLoaded = false,
            state = themeState(TimeAtmospherePalette.EVENING)
        )
    }
}
