package com.foresightlabs.aether

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.domain.model.ConnectionStatus
import com.foresightlabs.aether.screenshot.HomeFixtures
import com.foresightlabs.aether.ui.screens.HomeScreen
import com.foresightlabs.aether.ui.theme.AetherTheme
import com.foresightlabs.aether.ui.theme.AppThemeState
import com.foresightlabs.aether.ui.theme.AtmosphereMode
import com.foresightlabs.aether.ui.theme.LocalAppThemeState
import com.foresightlabs.aether.ui.theme.TimeAtmospherePalette
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The status mote replaces the earlier, oversized connection orb — it must
 * keep a tiny visible glyph while exposing a real accessible touch target,
 * sit quietly between Search and Settings, and keep semantics tied to the
 * real [ConnectionStatus] driving it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi", application = Application::class)
class HomeStatusMoteTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val connection = mutableStateOf(ConnectionStatus.READY)

    private fun setUpContent() {
        val theme = AppThemeState().apply {
            atmosphereMode = AtmosphereMode.MANUAL
            manualAtmosphere = TimeAtmospherePalette.DAY
        }
        composeRule.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalAppThemeState provides theme
            ) {
                AetherTheme(themeState = theme) {
                    HomeScreen(
                        chats = HomeFixtures.populated,
                        currentUser = HomeFixtures.me,
                        connection = connection.value,
                        isLoading = false,
                        onChatClick = {},
                        onNavigateToCalls = {},
                        onNavigateToSettings = {},
                        onNewMessageClick = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun theMoteIsTinyAndSitsBetweenSearchAndSettings() {
        setUpContent()
        val search = boundsOf("home_top_search")
        val mote = boundsOf("home_status_mote")
        val visibleMote = boundsOf("connection_mote_visual")
        val settings = boundsOf("home_settings_button")

        assertTrue(
            "The mote has to sit to the right of Search, in the open middle " +
                "space, not overlap or precede it",
            mote.left >= search.right - 1f
        )
        assertTrue(
            "The mote has to sit to the left of Settings, not overlap or " +
                "follow it",
            mote.right <= settings.left + 1f
        )
        val density = composeRule.density
        val minTouchPx = with(density) { Dp(48f).toPx() }
        assertTrue(
            "The mote hit area measured ${mote.width}x${mote.height}px — " +
                "it must remain accessible",
            mote.width >= minTouchPx && mote.height >= minTouchPx
        )
        val maxVisiblePx = with(density) { Dp(20f).toPx() }
        assertTrue(
            "The visible mote measured ${visibleMote.width}px wide — it must " +
                "remain a tiny ambient point",
            visibleMote.width <= maxVisiblePx
        )
    }

    @Test
    fun connectedStateExposesTruthfulSemantics() {
        connection.value = ConnectionStatus.READY
        setUpContent()
        assertMoteDescribed("Aether connected")
    }

    @Test
    fun syncingStateExposesTruthfulSemantics() {
        connection.value = ConnectionStatus.UPDATING
        setUpContent()
        assertMoteDescribed("Synchronizing messages")
    }

    @Test
    fun connectingStateExposesTruthfulSemantics() {
        connection.value = ConnectionStatus.CONNECTING
        setUpContent()
        assertMoteDescribed("Connecting to Telegram")
    }

    @Test
    fun offlineStateExposesTruthfulSemanticsForWaitingOnNetwork() {
        connection.value = ConnectionStatus.WAITING_FOR_NETWORK
        setUpContent()
        assertMoteDescribed("Offline")
    }

    @Test
    fun unresolvedConnectionReadsAsStartingRatherThanOffline() {
        connection.value = ConnectionStatus.UNKNOWN
        setUpContent()
        assertMoteDescribed("Starting connection")
    }

    @Test
    fun tappingConnectedMoteRevealsItsStatusWithoutChangingHomeControls() {
        connection.value = ConnectionStatus.READY
        setUpContent()

        composeRule.onAllNodesWithTag("connection_mote_surface", useUnmergedTree = true)
            .assertCountEquals(0)

        composeRule.onNodeWithTag("home_status_mote", useUnmergedTree = true)
            .performClick()

        composeRule.onNodeWithTag("connection_mote_surface", useUnmergedTree = true)
            .assertExists()
        composeRule.onNodeWithText("Connected", useUnmergedTree = true).assertExists()
        boundsOf("home_top_search")
        boundsOf("home_settings_button")
    }

    // --- helpers ---------------------------------------------------------

    private fun assertMoteDescribed(expected: String) {
        composeRule.onNodeWithTag("home_status_mote", useUnmergedTree = true)
            .assert(
                SemanticsMatcher("has content description \"$expected\"") { node ->
                    node.config.getOrNull(SemanticsProperties.ContentDescription)
                        ?.contains(expected) == true
                }
            )
    }

    private fun boundsOf(tag: String): Rect =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
}
