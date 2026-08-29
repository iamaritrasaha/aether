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
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.ui.appearance.AppearanceScreen
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
class AppearanceScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private data class Scenario(
        val name: String,
        val theme: AppThemeState
    )

    private val scenario = mutableStateOf<Scenario?>(null)
    private var contentInstalled = false

    private val outputDir = File("build/reports/aether-screenshots").apply { mkdirs() }

    private fun themeState(palette: TimeAtmospherePalette) = AppThemeState().apply {
        atmosphereMode = AtmosphereMode.MANUAL
        manualAtmosphere = palette
    }

    private fun capture(name: String, state: AppThemeState) {
        val next = Scenario(name, state)
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
                AppearanceScreen(onBack = {})
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
    fun appearanceScreenInDayAtmosphere() {
        capture("appearance-day", themeState(TimeAtmospherePalette.DAY))
    }

    @Test
    fun appearanceScreenInGoldenHourAtmosphere() {
        capture("appearance-golden-hour", themeState(TimeAtmospherePalette.GOLDEN_HOUR))
    }

    @Test
    fun appearanceScreenInNightAtmosphere() {
        capture("appearance-night", themeState(TimeAtmospherePalette.NIGHT))
    }
}
