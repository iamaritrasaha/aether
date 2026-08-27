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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ConnectionStatus
import com.foresightlabs.aether.ui.design.SheetAnchor
import com.foresightlabs.aether.ui.screens.HomeScreen
import com.foresightlabs.aether.ui.theme.AetherTheme
import com.foresightlabs.aether.ui.theme.AppThemeState
import com.foresightlabs.aether.ui.theme.AppThemeMode
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
        val withCurrentUser: Boolean,
        val selectedDockKey: String
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
        anchor: SheetAnchor = SheetAnchor.RESTING,
        isLoading: Boolean = false,
        withCurrentUser: Boolean = true,
        selectedDockKey: String = "chats"
    ) {
        val next = Scenario(name, chats, state, isLoading, withCurrentUser, selectedDockKey)
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

        moveSheetTo(anchor)
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
                    onNewMessageClick = {},
                    dockSelectedKey = active.selectedDockKey
                )
            }
        }
    }

    private fun assertDockGeometry(selectedKey: String): Pair<Float, Float> {
        val keys = listOf("chats", "pulse", "calls", "settings")
        val slots = keys.map {
            composeRule.onNodeWithTag("nav_slot_$it", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
        }
        slots.drop(1).forEach { assertEquals(slots.first().width, it.width, 1f) }
        val lens = composeRule.onNodeWithTag("nav_lens_$selectedKey", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val icon = composeRule.onNodeWithTag("nav_icon_$selectedKey", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertEquals(lens.center.x, icon.center.x, 1f)
        assertEquals(lens.center.y, icon.center.y, 1f)
        assertTrue(lens.left <= icon.left && lens.top <= icon.top)
        assertTrue(lens.right >= icon.right && lens.bottom >= icon.bottom)
        val dock = composeRule.onNodeWithTag("home_dock").fetchSemanticsNode().boundsInRoot
        return dock.width to dock.height
    }

    /** Drives the sheet through its own accessibility action, not a hand-set offset. */
    private fun moveSheetTo(anchor: SheetAnchor) {
        val label = when (anchor) {
            SheetAnchor.EXPANDED -> "Expand conversations"
            SheetAnchor.RESTING -> "Balance conversations"
            SheetAnchor.PEEK -> "Collapse conversations"
        }
        val action = findCustomAction(composeRule.onRoot().fetchSemanticsNode(), label)
        // The action for the position the sheet already occupies is absent by design.
        action?.invoke()
    }

    private fun findCustomAction(node: SemanticsNode, label: String): (() -> Boolean)? {
        node.config.getOrNull(SemanticsActions.CustomActions)
            ?.firstOrNull { it.label == label }
            ?.let { return it.action }
        node.children.forEach { child ->
            findCustomAction(child, label)?.let { return it }
        }
        return null
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
    fun homeLightAcrossDayAndNight() {
        listOf(TimeAtmospherePalette.DAY, TimeAtmospherePalette.NIGHT).forEach { palette ->
            capture(
                name = "home-light-${palette.name.lowercase()}",
                chats = HomeFixtures.populated,
                state = themeState(palette).apply { themeMode = AppThemeMode.LIGHT }
            )
        }
    }

    @Test
    fun homeAcrossMaterialModes() {
        capture(
            name = "home-dark-day",
            chats = HomeFixtures.populated,
            state = themeState(TimeAtmospherePalette.DAY).apply { themeMode = AppThemeMode.DARK }
        )
        capture(
            name = "home-dark-night",
            chats = HomeFixtures.populated,
            state = themeState(TimeAtmospherePalette.NIGHT).apply { themeMode = AppThemeMode.DARK }
        )
        capture(
            name = "home-oled-night",
            chats = HomeFixtures.populated,
            state = themeState(TimeAtmospherePalette.NIGHT).apply { themeMode = AppThemeMode.OLED }
        )
        capture(
            name = "dark-frosted-bars",
            chats = HomeFixtures.populated,
            state = themeState(TimeAtmospherePalette.DAY).apply { themeMode = AppThemeMode.DARK }
        )
        capture(
            name = "light-frosted-bars",
            chats = HomeFixtures.populated,
            state = themeState(TimeAtmospherePalette.DAY).apply { themeMode = AppThemeMode.LIGHT }
        )
        capture(
            name = "oled-frosted-bars",
            chats = HomeFixtures.populated,
            state = themeState(TimeAtmospherePalette.NIGHT).apply { themeMode = AppThemeMode.OLED }
        )
        capture(
            name = "night-frosted-bars",
            chats = HomeFixtures.populated,
            state = themeState(TimeAtmospherePalette.NIGHT).apply { themeMode = AppThemeMode.DARK }
        )
    }

    @Test
    fun frostedDockSelectionGeometryAndRenders() {
        val scenarios = listOf(
            "chats" to "home-frosted-dock-chats",
            "pulse" to "home-frosted-dock-pulse-selected",
            "calls" to "home-frosted-dock-calls-selected",
            "settings" to "home-frosted-dock-settings-selected"
        )
        var dockSize: Pair<Float, Float>? = null
        scenarios.forEach { (key, name) ->
            capture(
                name = name,
                chats = HomeFixtures.populated,
                state = themeState(TimeAtmospherePalette.DAY),
                selectedDockKey = key
            )
            val measured = assertDockGeometry(key)
            dockSize?.let {
                assertEquals(it.first, measured.first, 1f)
                assertEquals(it.second, measured.second, 1f)
            }
            dockSize = measured
        }
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
    fun homeWeatherHeroAcrossConditionsAtPeek() {
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
                name = "home-weather-hero-${weather.name.lowercase().replace('_', '-')}",
                chats = HomeFixtures.populated,
                state = themeState(TimeAtmospherePalette.DAY, weather = weather),
                anchor = SheetAnchor.PEEK
            )
        }
    }

    @Test
    fun homeWeatherHeroNightAndGoldenHour() {
        capture(
            name = "home-weather-hero-clear-night",
            chats = HomeFixtures.populated,
            state = themeState(TimeAtmospherePalette.NIGHT, weather = WeatherCondition.CLEAR),
            anchor = SheetAnchor.PEEK
        )
        capture(
            name = "home-weather-hero-rain-night",
            chats = HomeFixtures.populated,
            state = themeState(TimeAtmospherePalette.NIGHT, weather = WeatherCondition.RAIN),
            anchor = SheetAnchor.PEEK
        )
        capture(
            name = "home-weather-hero-golden-hour",
            chats = HomeFixtures.populated,
            state = themeState(TimeAtmospherePalette.GOLDEN_HOUR, weather = WeatherCondition.CLEAR),
            anchor = SheetAnchor.PEEK
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
            name = "home-weather-hero-unavailable",
            chats = HomeFixtures.populated,
            state = themeState(TimeAtmospherePalette.GOLDEN_HOUR, weather = null),
            anchor = SheetAnchor.PEEK
        )
    }

    // --- sheet positions -----------------------------------------------------

    @Test
    fun homeAtEverySheetPosition() {
        SheetAnchor.entries.forEach { anchor ->
            capture(
                name = "home-sheet-${anchor.name.lowercase()}",
                chats = HomeFixtures.populated,
                state = themeState(TimeAtmospherePalette.EVENING),
                anchor = anchor
            )
        }
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
