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
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ConnectionStatus
import com.foresightlabs.aether.ui.home.HomeScreen
import com.foresightlabs.aether.ui.theme.AetherTheme
import com.foresightlabs.aether.ui.theme.AppThemeState
import com.foresightlabs.aether.ui.theme.AtmosphereMode
import com.foresightlabs.aether.ui.theme.LocalAppThemeState
import com.foresightlabs.aether.ui.theme.TimeAtmospherePalette
import com.foresightlabs.aether.ui.theme.WeatherCondition
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the real Home composable on the JVM and writes PNGs for visual inspection.
 *
 * This is not a golden-image comparison — it is a way to actually look at every
 * atmosphere, sheet position, font scale and empty state. It exists because this
 * machine's only emulator image is x86_64 while the TDLib native library Aether
 * ships is arm64-only, so Home cannot be exercised on a running emulator here.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A plain Application: these renders must not boot the Telegram client.
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi", application = Application::class)
class HomeScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private data class Scenario(
        val name: String,
        val chats: List<Chat>,
        val theme: AppThemeState,
        val isLoading: Boolean,
        val withCurrentUser: Boolean
    )

    private val scenario = mutableStateOf<Scenario?>(null)
    private var contentInstalled = false

    private val outputDir = File("build/reports/aether-screenshots").apply { mkdirs() }

    private fun themeState(
        palette: TimeAtmospherePalette,
        weather: WeatherCondition? = null,
        fontScale: Float = 1f
    ) = AppThemeState().apply {
        atmosphereMode = AtmosphereMode.MANUAL
        manualAtmosphere = palette
        weatherOverride = weather
        this.fontScale = fontScale
    }

    private fun capture(
        name: String,
        chats: List<Chat>,
        state: AppThemeState,
        isLoading: Boolean = false,
        withCurrentUser: Boolean = true
    ) {
        val next = Scenario(name, chats, state, isLoading, withCurrentUser)
        if (!contentInstalled) {
            contentInstalled = true
            composeRule.setContent {
                scenario.value?.let { active ->
                    // Keyed so each scenario starts from a clean sheet position.
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
        // Inspection mode disables the wall-clock ticker and the weather fetch so
        // renders are deterministic and never wait on real time.
        CompositionLocalProvider(
            LocalInspectionMode provides true,
            LocalAppThemeState provides active.theme
        ) {
            AetherTheme(themeState = active.theme) {
                HomeScreen(
                    chats = active.chats,
                    currentUser = if (active.withCurrentUser) HomeFixtures.me else null,
                    connection = ConnectionStatus.READY,
                    isLoading = active.isLoading,
                    onChatClick = {},
                    onNavigateToCalls = {},
                    onNavigateToSettings = {},
                    onNewMessageClick = {}
                )
            }
        }
    }

    /**
     * Draws the decor view into a software bitmap. Robolectric cannot drive the
     * PixelCopy path captureToImage() uses, but it renders Compose correctly through
     * an ordinary Canvas in NATIVE graphics mode.
     */
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

    // --- atmospheres ---------------------------------------------------------

    @Test
    fun homeAcrossEveryAtmosphere() {
        TimeAtmospherePalette.entries.forEach { palette ->
            capture(
                name = "home-atmosphere-${palette.name.lowercase()}",
                chats = HomeFixtures.populated,
                state = themeState(palette)
            )
        }
    }

    @Test
    fun homeAcrossMaterialModes() {
        capture(
            name = "home-dark-day",
            chats = HomeFixtures.populated,
            state = themeState(TimeAtmospherePalette.DAY)
        )
        capture(
            name = "home-dark-night",
            chats = HomeFixtures.populated,
            state = themeState(TimeAtmospherePalette.NIGHT)
        )
        capture(
            name = "dark-frosted-bars",
            chats = HomeFixtures.populated,
            state = themeState(TimeAtmospherePalette.DAY)
        )
        capture(
            name = "night-frosted-bars",
            chats = HomeFixtures.populated,
            state = themeState(TimeAtmospherePalette.NIGHT)
        )
    }

    @Test
    fun homeWithWeatherModulation() {
        WeatherCondition.entries.filter { it != WeatherCondition.UNKNOWN }.forEach { weather ->
            capture(
                name = "home-weather-${weather.name.lowercase().replace('_', '-')}",
                chats = HomeFixtures.populated,
                state = themeState(TimeAtmospherePalette.DAY, weather = weather)
            )
        }
    }

    @Test
    fun homeWeatherAcrossEveryCondition() {
        val conditions = listOf(
            WeatherCondition.CLEAR,
            WeatherCondition.PARTLY_CLOUDY,
            WeatherCondition.CLOUDY,
            WeatherCondition.RAIN,
            WeatherCondition.HEAVY_RAIN,
            WeatherCondition.STORM,
            WeatherCondition.FOG,
            WeatherCondition.SNOW
        )
        conditions.forEach { weather ->
            capture(
                name = "home-weather-scene-${weather.name.lowercase().replace('_', '-')}",
                chats = HomeFixtures.populated,
                state = themeState(TimeAtmospherePalette.DAY, weather = weather)
            )
        }
    }

    @Test
    fun homeWeatherAtNightAndGoldenHour() {
        capture(
            name = "home-weather-scene-clear-night",
            chats = HomeFixtures.populated,
            state = themeState(TimeAtmospherePalette.NIGHT, weather = WeatherCondition.CLEAR)
        )
        capture(
            name = "home-weather-scene-rain-night",
            chats = HomeFixtures.populated,
            state = themeState(TimeAtmospherePalette.NIGHT, weather = WeatherCondition.RAIN)
        )
        capture(
            name = "home-weather-scene-golden-hour",
            chats = HomeFixtures.populated,
            state = themeState(TimeAtmospherePalette.GOLDEN_HOUR, weather = WeatherCondition.CLEAR)
        )
    }

    @Test
    fun homeWithNoWeatherFallsBackToTimeOnly() {
        capture(
            name = "home-weather-unavailable",
            chats = HomeFixtures.populated,
            state = themeState(TimeAtmospherePalette.GOLDEN_HOUR, weather = null)
        )
        capture(
            name = "home-weather-scene-unavailable",
            chats = HomeFixtures.populated,
            state = themeState(TimeAtmospherePalette.GOLDEN_HOUR, weather = null)
        )
    }

    // --- presence states -----------------------------------------------------

    @Test
    fun homeWithNoOnlineContacts() {
        capture(
            name = "home-presence-none",
            chats = HomeFixtures.noOnlinePeople,
            state = themeState(TimeAtmospherePalette.DAY)
        )
    }

    @Test
    fun homeWithOnlyApproximateActivity() {
        capture(
            name = "home-presence-recently-active",
            chats = HomeFixtures.onlyRecentlyActive,
            state = themeState(TimeAtmospherePalette.DAY)
        )
    }

    // --- empty and loading ---------------------------------------------------

    @Test
    fun homeWithNoConversations() {
        capture(
            name = "home-empty",
            chats = HomeFixtures.empty,
            state = themeState(TimeAtmospherePalette.NIGHT),
            withCurrentUser = false
        )
    }

    @Test
    fun homeWhileLoading() {
        capture(
            name = "home-loading",
            chats = HomeFixtures.empty,
            state = themeState(TimeAtmospherePalette.DAWN),
            isLoading = true
        )
    }

    // --- accessibility and small screens -------------------------------------

    @Test
    fun homeAtLargeFontScale() {
        capture(
            name = "home-font-scale-large",
            chats = HomeFixtures.populated,
            state = themeState(TimeAtmospherePalette.GOLDEN_HOUR, fontScale = 1.25f)
        )
    }

    @Test
    fun homeAtSmallFontScale() {
        capture(
            name = "home-font-scale-small",
            chats = HomeFixtures.populated,
            state = themeState(TimeAtmospherePalette.GOLDEN_HOUR, fontScale = 0.85f)
        )
    }

    @Test
    @Config(qualifiers = "w320dp-h568dp-xhdpi")
    fun homeOnASmallPhone() {
        capture(
            name = "home-small-phone",
            chats = HomeFixtures.populated,
            state = themeState(TimeAtmospherePalette.DAY)
        )
    }

    @Test
    fun homeWithSystemFontScaleTurnedUp() {
        RuntimeEnvironment.setFontScale(1.5f)
        try {
            capture(
                name = "home-system-font-scale-150",
                chats = HomeFixtures.populated,
                state = themeState(TimeAtmospherePalette.EVENING)
            )
        } finally {
            RuntimeEnvironment.setFontScale(1f)
        }
    }
}
