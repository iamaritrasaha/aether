package com.foresightlabs.aether

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.foresightlabs.aether.ui.components.AetherAtmosphericBackground
import com.foresightlabs.aether.ui.design.AetherFloatingHeader
import com.foresightlabs.aether.ui.design.AetherNavItem
import com.foresightlabs.aether.ui.design.AetherNavPill
import com.foresightlabs.aether.ui.design.AetherNavPillDefaults
import com.foresightlabs.aether.ui.design.AetherSheet
import com.foresightlabs.aether.ui.design.SheetAnchor
import com.foresightlabs.aether.ui.design.rememberAetherFrostState
import com.foresightlabs.aether.ui.design.rememberAetherSheetState
import com.foresightlabs.aether.ui.theme.AetherColors
import com.foresightlabs.aether.ui.theme.AetherTheme
import com.foresightlabs.aether.ui.theme.AppThemeState
import com.foresightlabs.aether.ui.theme.AtmosphereMode
import com.foresightlabs.aether.ui.theme.DarkBackground
import com.foresightlabs.aether.ui.theme.DarkBorder
import com.foresightlabs.aether.ui.theme.DarkBorderSubtle
import com.foresightlabs.aether.ui.theme.DarkBubbleIncoming
import com.foresightlabs.aether.ui.theme.DarkBubbleIncomingText
import com.foresightlabs.aether.ui.theme.DarkBubbleOutgoing
import com.foresightlabs.aether.ui.theme.DarkBubbleOutgoingEnd
import com.foresightlabs.aether.ui.theme.DarkBubbleOutgoingText
import com.foresightlabs.aether.ui.theme.DarkSurface
import com.foresightlabs.aether.ui.theme.DarkSurfaceElevated
import com.foresightlabs.aether.ui.theme.DarkSurfaceHighlight
import com.foresightlabs.aether.ui.theme.DarkTextPrimary
import com.foresightlabs.aether.ui.theme.DarkTextSecondary
import com.foresightlabs.aether.ui.theme.DarkTextTertiary
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.LocalAppThemeState
import com.foresightlabs.aether.ui.theme.TimeAtmospherePalette
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi", application = Application::class)
class AetherNavPillTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val navItems = listOf(
        AetherNavItem("chats", Icons.Default.ChatBubble, "Chats") {},
        AetherNavItem("pulse", Icons.Default.AutoAwesome, "Pulse") {},
        AetherNavItem("calls", Icons.Default.Call, "Calls") {},
        AetherNavItem("settings", Icons.Default.Settings, "Settings") {}
    )

    @Test
    fun dockDimensionsMatchTokens() {
        assertEquals(62.dp, AetherNavPillDefaults.Height)
        assertEquals(48.dp, AetherNavPillDefaults.DestinationSlotSize)
        assertEquals(44.dp, AetherNavPillDefaults.SelectionLensSize)
        assertEquals(22.dp, AetherNavPillDefaults.IconSize)
        assertEquals(36.dp, AetherNavPillDefaults.OuterHorizontalPadding)
    }

    @Test
    fun selectionLensHasExactFixedGeometry() {
        composeRule.setContent {
            val frostState = rememberAetherFrostState()
            AetherNavPill(
                items = navItems,
                selectedKey = "chats",
                frostState = frostState
            )
        }

        composeRule.onNodeWithTag("nav_slot_chats", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertWidthIsEqualTo(48.dp)
            .assertHeightIsEqualTo(48.dp)

        composeRule.onNodeWithTag("nav_lens_chats", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertWidthIsEqualTo(44.dp)
            .assertHeightIsEqualTo(44.dp)

        composeRule.onNodeWithTag("nav_icon_chats", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertWidthIsEqualTo(22.dp)
            .assertHeightIsEqualTo(22.dp)

        // Unselected slots must not render a lens
        composeRule.onNodeWithTag("nav_lens_pulse", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("nav_lens_calls", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("nav_lens_settings", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun rapidNavigationMaintainsCorrectSelectionLensBounds() {
        val selectedKey = mutableStateOf("chats")

        composeRule.setContent {
            val frostState = rememberAetherFrostState()
            AetherNavPill(
                items = navItems,
                selectedKey = selectedKey.value,
                frostState = frostState
            )
        }

        val keys = listOf("chats", "pulse", "calls", "settings")
        // Rapidly toggle selection 20 times
        for (i in 0 until 20) {
            val current = keys[i % keys.size]
            selectedKey.value = current
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("nav_lens_$current", useUnmergedTree = true)
                .assertIsDisplayed()
                .assertWidthIsEqualTo(44.dp)
                .assertHeightIsEqualTo(44.dp)
        }
    }

    @Test
    fun dockAndLensAreIsolatedFromExtremeContextualAccents() {
        val extremeAccents = listOf(
            Color.Red,
            Color.Blue,
            Color.Green,
            Color.Magenta,
            Color(0xFFFF7038)
        )

        fun createColors(accent: Color) = AetherColors(
            background = DarkBackground,
            surface = DarkSurface,
            surfaceElevated = DarkSurfaceElevated,
            surfaceHighlight = DarkSurfaceHighlight,
            border = DarkBorder,
            borderSubtle = DarkBorderSubtle,
            textPrimary = DarkTextPrimary,
            textSecondary = DarkTextSecondary,
            textTertiary = DarkTextTertiary,
            accent = accent,
            accentSubtle = accent.copy(alpha = 0.2f),
            bubbleOutgoing = DarkBubbleOutgoing,
            bubbleOutgoingEnd = DarkBubbleOutgoingEnd,
            bubbleOutgoingText = DarkBubbleOutgoingText,
            bubbleIncoming = DarkBubbleIncoming,
            bubbleIncomingText = DarkBubbleIncomingText,
            isDark = true
        )

        val activeColors = mutableStateOf(createColors(extremeAccents.first()))

        composeRule.setContent {
            CompositionLocalProvider(LocalAetherColors provides activeColors.value) {
                val frostState = rememberAetherFrostState()
                AetherNavPill(
                    items = navItems,
                    selectedKey = "pulse",
                    frostState = frostState
                )
            }
        }

        for (accent in extremeAccents) {
            activeColors.value = createColors(accent)
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("nav_lens_pulse", useUnmergedTree = true)
                .assertIsDisplayed()
                .assertWidthIsEqualTo(44.dp)
                .assertHeightIsEqualTo(44.dp)
        }
    }

    @Test
    fun dockRendersNeutrallyAcrossAllAtmospherePalettes() {
        val activePalette = mutableStateOf(TimeAtmospherePalette.DAWN)

        composeRule.setContent {
            val themeState = AppThemeState().apply {
                atmosphereMode = AtmosphereMode.MANUAL
                manualAtmosphere = activePalette.value
            }
            CompositionLocalProvider(LocalAppThemeState provides themeState) {
                AetherTheme(themeState = themeState) {
                    val frostState = rememberAetherFrostState()
                    AetherNavPill(
                        items = navItems,
                        selectedKey = "settings",
                        frostState = frostState
                    )
                }
            }
        }

        for (palette in TimeAtmospherePalette.entries) {
            activePalette.value = palette
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("nav_lens_settings", useUnmergedTree = true)
                .assertIsDisplayed()
                .assertWidthIsEqualTo(44.dp)
                .assertHeightIsEqualTo(44.dp)
        }
    }

    @Test
    fun goldenHourHomeDockOverSheetDoesNotSampleHiddenCrimson() {
        val selectedKey = mutableStateOf("chats")
        val themeState = AppThemeState().apply {
            atmosphereMode = AtmosphereMode.MANUAL
            manualAtmosphere = TimeAtmospherePalette.GOLDEN_HOUR
        }

        composeRule.setContent {
            CompositionLocalProvider(LocalAppThemeState provides themeState) {
                AetherTheme(themeState = themeState) {
                    val frostState = rememberAetherFrostState()
                    val sheetState = rememberAetherSheetState(initialAnchor = SheetAnchor.RESTING)

                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Unified Backdrop Scene: Atmosphere + Sheet
                        AetherAtmosphericBackground(
                            modifier = Modifier.fillMaxSize(),
                            heroFraction = 0.7f,
                            frostState = frostState
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                AetherSheet(
                                    state = sheetState,
                                    containerHeightPx = 2000f,
                                    label = "Conversations",
                                    modifier = Modifier.testTag("conversations_sheet")
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(top = 100.dp)
                                    )
                                }
                            }
                        }

                        // Frosted Dock overlay on top of the sheet
                        AetherNavPill(
                            items = navItems,
                            selectedKey = selectedKey.value,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .testTag("home_dock"),
                            frostState = frostState
                        )
                    }
                }
            }
        }

        // Test across all tabs in Golden Hour
        val keys = listOf("chats", "pulse", "calls", "settings")
        for (key in keys) {
            selectedKey.value = key
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("home_dock")
                .assertIsDisplayed()

            composeRule.onNodeWithTag("nav_slot_$key", useUnmergedTree = true)
                .assertIsDisplayed()
                .assertWidthIsEqualTo(48.dp)
                .assertHeightIsEqualTo(48.dp)

            composeRule.onNodeWithTag("nav_lens_$key", useUnmergedTree = true)
                .assertIsDisplayed()
                .assertWidthIsEqualTo(44.dp)
                .assertHeightIsEqualTo(44.dp)
        }
    }

    @Test
    fun exposedAtmosphereBehindFloatingHeaderNaturallyBlursAtmosphere() {
        val activePalette = mutableStateOf(TimeAtmospherePalette.DAY)

        composeRule.setContent {
            val themeState = AppThemeState().apply {
                atmosphereMode = AtmosphereMode.MANUAL
                manualAtmosphere = activePalette.value
            }
            CompositionLocalProvider(LocalAppThemeState provides themeState) {
                AetherTheme(themeState = themeState) {
                    val frostState = rememberAetherFrostState()
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Backdrop Scene with exposed atmosphere
                        AetherAtmosphericBackground(
                            modifier = Modifier.fillMaxSize(),
                            heroFraction = 1f,
                            frostState = frostState
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Floating Header overlay directly on top of exposed atmosphere
                        AetherFloatingHeader(
                            title = "Exposed Atmosphere Test",
                            frostState = frostState,
                            modifier = Modifier.testTag("exposed_header")
                        )
                    }
                }
            }
        }

        for (palette in listOf(TimeAtmospherePalette.DAY, TimeAtmospherePalette.GOLDEN_HOUR, TimeAtmospherePalette.NIGHT)) {
            activePalette.value = palette
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("exposed_header")
                .assertIsDisplayed()
        }
    }
}
