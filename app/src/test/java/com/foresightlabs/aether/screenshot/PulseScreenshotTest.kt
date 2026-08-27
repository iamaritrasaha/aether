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
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.domain.model.StoryItem
import com.foresightlabs.aether.domain.model.User
import com.foresightlabs.aether.domain.model.UserPulse
import com.foresightlabs.aether.ui.pulse.PulseViewerState
import com.foresightlabs.aether.ui.screens.PulseScreen
import com.foresightlabs.aether.ui.theme.AetherTheme
import com.foresightlabs.aether.ui.theme.AppThemeState
import com.foresightlabs.aether.ui.theme.AppThemeMode
import com.foresightlabs.aether.ui.theme.AtmosphereMode
import com.foresightlabs.aether.ui.theme.LocalAppThemeState
import com.foresightlabs.aether.ui.theme.TimeAtmospherePalette
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
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
class PulseScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private data class Scenario(
        val name: String,
        val myPulse: UserPulse?,
        val pulses: List<UserPulse>,
        val theme: AppThemeState,
        val viewerState: PulseViewerState? = null
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
        myPulse: UserPulse?,
        pulses: List<UserPulse>,
        state: AppThemeState,
        viewerState: PulseViewerState? = null
    ) {
        val next = Scenario(name, myPulse, pulses, state, viewerState)
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
                PulseScreen(
                    myPulse = active.myPulse,
                    pulses = active.pulses,
                    canPostPulse = true,
                    currentUser = HomeFixtures.me,
                    viewerState = active.viewerState,
                    isPosting = false,
                    postError = null,
                    onOpenViewer = { _, _ -> },
                    onCloseViewer = {},
                    onStoryChanged = { _, _ -> },
                    onSendReaction = { _, _, _ -> },
                    onSendReply = { _, _ -> },
                    onPostPulse = { _, _, _, _ -> },
                    onDeletePulse = {},
                    onNavigateToChats = {},
                    onNavigateToCalls = {},
                    onNavigateToSettings = {}
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


    private val sampleStories = listOf(
        StoryItem(
            id = 101,
            senderChatId = 1L,
            senderName = "Ishani Roy",
            dateSeconds = 1724659200,
            caption = "Morning light at the studio ✨",
            isForCloseFriends = false
        ),
        StoryItem(
            id = 102,
            senderChatId = 1L,
            senderName = "Ishani Roy",
            dateSeconds = 1724662800,
            caption = "New ceramics in progress",
            isForCloseFriends = true
        )
    )

    private val samplePulses = listOf(
        UserPulse(
            chatId = 1L,
            name = "Ishani Roy",
            avatarInitials = "IR",
            avatarGradient = listOf(Color(0xFF4DA3FF), Color(0xFF1D4ED8)),
            isOnline = true,
            stories = sampleStories,
            maxReadStoryId = 0
        ),
        UserPulse(
            chatId = 2L,
            name = "Dev Malhotra",
            avatarInitials = "DM",
            avatarGradient = listOf(Color(0xFF10B981), Color(0xFF047857)),
            isOnline = true,
            stories = listOf(
                StoryItem(
                    id = 201,
                    senderChatId = 2L,
                    senderName = "Dev Malhotra",
                    dateSeconds = 1724650000,
                    caption = "On the train to Berlin 🚆"
                )
            ),
            maxReadStoryId = 0
        ),
        UserPulse(
            chatId = 3L,
            name = "Meera Nair",
            avatarInitials = "MN",
            avatarGradient = listOf(Color(0xFFF43F5E), Color(0xFFBE123C)),
            isOnline = false,
            stories = listOf(
                StoryItem(
                    id = 301,
                    senderChatId = 3L,
                    senderName = "Meera Nair",
                    dateSeconds = 1724640000,
                    caption = "Book release tomorrow!"
                )
            ),
            maxReadStoryId = 301
        )
    )

    private val myPulseFixture = UserPulse(
        chatId = 999L,
        name = "Aritra",
        avatarInitials = "AS",
        avatarGradient = listOf(Color(0xFF4DA3FF), Color(0xFF1D4ED8)),
        isOnline = true,
        stories = listOf(
            StoryItem(
                id = 991,
                senderChatId = 999L,
                senderName = "Aritra",
                dateSeconds = 1724659200,
                caption = "Coffee and contemplation"
            )
        ),
        isMine = true
    )

    @Test
    fun pulsePopulatedScreen() {
        capture(
            name = "pulse-populated",
            myPulse = myPulseFixture,
            pulses = samplePulses,
            state = themeState(TimeAtmospherePalette.DAY).apply { themeMode = AppThemeMode.DARK }
        )
        capture(
            name = "pulse-frosted-header",
            myPulse = myPulseFixture,
            pulses = samplePulses,
            state = themeState(TimeAtmospherePalette.DAY).apply { themeMode = AppThemeMode.DARK }
        )
    }

    @Test
    @Config(qualifiers = "w320dp-h568dp-xhdpi")
    fun pulseFrostedHeaderOnNarrowPhone() {
        capture(
            name = "pulse-frosted-header-narrow",
            myPulse = myPulseFixture,
            pulses = samplePulses,
            state = themeState(TimeAtmospherePalette.DAY).apply { themeMode = AppThemeMode.DARK }
        )
    }

    @Test
    fun pulseEmptyScreen() {
        capture(
            name = "pulse-empty",
            myPulse = null,
            pulses = emptyList(),
            state = themeState(TimeAtmospherePalette.EVENING)
        )
    }

    @Test
    fun pulseViewerScreen() {
        val activePulse = samplePulses.first()
        capture(
            name = "pulse-viewer",
            myPulse = null,
            pulses = samplePulses,
            state = themeState(TimeAtmospherePalette.NIGHT),
            viewerState = PulseViewerState(activePulse, initialStoryIndex = 0)
        )
    }
}
