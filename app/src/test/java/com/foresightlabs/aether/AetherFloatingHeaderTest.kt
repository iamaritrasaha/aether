package com.foresightlabs.aether

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.ui.components.AetherAtmosphericBackground
import com.foresightlabs.aether.ui.design.AetherFloatingHeader
import com.foresightlabs.aether.ui.design.AetherFloatingHeaderDefaults
import com.foresightlabs.aether.ui.design.AetherIconButton
import com.foresightlabs.aether.ui.design.rememberAetherFrostState
import com.foresightlabs.aether.ui.theme.AetherTheme
import com.foresightlabs.aether.ui.theme.AppThemeMode
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
class AetherFloatingHeaderTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val outputDir = File("build/reports/aether-screenshots").apply { mkdirs() }

    @Test
    fun headerGeometryMatchesExpandedAndCompactTokens() {
        val scrollFraction = mutableFloatStateOf(0f)

        composeRule.setContent {
            val themeState = AppThemeState().apply {
                themeMode = AppThemeMode.DARK
                atmosphereMode = AtmosphereMode.MANUAL
                manualAtmosphere = TimeAtmospherePalette.DAY
            }
            CompositionLocalProvider(LocalAppThemeState provides themeState) {
                AetherTheme(themeState = themeState) {
                    val frostState = rememberAetherFrostState()
                    Box(modifier = Modifier.fillMaxSize()) {
                        AetherAtmosphericBackground(
                            modifier = Modifier.fillMaxSize(),
                            frostState = frostState
                        ) {
                            Box(modifier = Modifier.fillMaxSize())
                        }

                        AetherFloatingHeader(
                            title = "Settings",
                            subtitle = "General preferences",
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .testTag("header_host"),
                            surfaceModifier = Modifier.testTag("header_surface"),
                            scrollFraction = scrollFraction.floatValue,
                            frostState = frostState,
                            navigation = {
                                AetherIconButton(
                                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    onClick = {},
                                    modifier = Modifier.testTag("header_back_btn")
                                )
                            },
                            actions = {
                                AetherIconButton(
                                    icon = Icons.Default.MoreVert,
                                    contentDescription = "More",
                                    onClick = {},
                                    modifier = Modifier.testTag("header_more_btn")
                                )
                            }
                        )
                    }
                }
            }
        }

        // Expanded state (scrollFraction = 0f)
        scrollFraction.floatValue = 0f
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("header_host").assertIsDisplayed()
        composeRule.onNodeWithTag("header_surface").assertIsDisplayed()
            .assertHeightIsEqualTo(AetherFloatingHeaderDefaults.ExpandedHeight) // 64dp
        composeRule.onNodeWithTag("header_back_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("header_more_btn").assertIsDisplayed()

        // Compacted state (scrollFraction = 1f)
        scrollFraction.floatValue = 1f
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("header_surface").assertIsDisplayed()
            .assertHeightIsEqualTo(AetherFloatingHeaderDefaults.CompactHeight) // 56dp
        composeRule.onNodeWithTag("header_back_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("header_more_btn").assertIsDisplayed()

        // Midway state (scrollFraction = 0.5f) -> 60dp
        scrollFraction.floatValue = 0.5f
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("header_surface").assertIsDisplayed()
            .assertHeightIsEqualTo(60.dp)
    }

    @Test
    fun headerRendersCorrectlyAcrossAllScreenshotStates() {
        val themeState = mutableStateOf(AppThemeState().apply {
            themeMode = AppThemeMode.DARK
            atmosphereMode = AtmosphereMode.MANUAL
            manualAtmosphere = TimeAtmospherePalette.DAY
        })
        val scrollFraction = mutableFloatStateOf(0f)

        composeRule.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalAppThemeState provides themeState.value
            ) {
                AetherTheme(themeState = themeState.value) {
                    val frostState = rememberAetherFrostState()
                    Box(modifier = Modifier.fillMaxSize()) {
                        AetherAtmosphericBackground(
                            modifier = Modifier.fillMaxSize(),
                            frostState = frostState
                        ) {
                            Box(modifier = Modifier.fillMaxSize())
                        }

                        AetherFloatingHeader(
                            title = "Conversation Info",
                            subtitle = "Online now",
                            modifier = Modifier.align(Alignment.TopCenter),
                            surfaceModifier = Modifier.testTag("screenshot_header_surface"),
                            scrollFraction = scrollFraction.floatValue,
                            frostState = frostState,
                            navigation = {
                                AetherIconButton(
                                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    onClick = {}
                                )
                            }
                        )
                    }
                }
            }
        }

        val scenarios = listOf(
            Triple("header_expanded_day", AppThemeMode.DARK, TimeAtmospherePalette.DAY to 0f),
            Triple("header_compacted_day", AppThemeMode.DARK, TimeAtmospherePalette.DAY to 1f),
            Triple("header_golden_hour", AppThemeMode.DARK, TimeAtmospherePalette.GOLDEN_HOUR to 0f),
            Triple("header_night", AppThemeMode.DARK, TimeAtmospherePalette.NIGHT to 0f),
            Triple("header_light", AppThemeMode.LIGHT, TimeAtmospherePalette.DAY to 0f),
            Triple("header_oled", AppThemeMode.OLED, TimeAtmospherePalette.NIGHT to 0f)
        )

        for ((name, mode, atmosphereAndFraction) in scenarios) {
            val (palette, fraction) = atmosphereAndFraction
            themeState.value = AppThemeState().apply {
                themeMode = mode
                atmosphereMode = AtmosphereMode.MANUAL
                manualAtmosphere = palette
            }
            scrollFraction.floatValue = fraction
            composeRule.waitForIdle()
            Shadows.shadowOf(Looper.getMainLooper()).idle()

            val view = composeRule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File(outputDir, "$name.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            assertTrue("Screenshot $name must be saved", file.length() > 0)
        }
    }
}
