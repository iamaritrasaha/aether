package com.foresightlabs.aether.ui.conversation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.ui.theme.AetherTheme
import com.foresightlabs.aether.ui.theme.AppThemeState
import com.foresightlabs.aether.ui.theme.AtmosphereMode
import com.foresightlabs.aether.ui.theme.LocalAppThemeState
import com.foresightlabs.aether.ui.theme.TimeAtmospherePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Verification test for minimal Aether jump-to-latest control:
 *
 * 1. Accessible hit box: >= 48dp width and height.
 * 2. Unread count badge: cleanly attached inline when unreadCount > 0, absent when 0.
 * 3. Accessibility semantics: proper contentDescription with unread count.
 * 4. Tap handling: executes click callback.
 * 5. Visibility: hides cleanly when visible is false.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi", application = Application::class)
class JumpToLatestTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val theme = AppThemeState().apply {
        atmosphereMode = AtmosphereMode.MANUAL
        manualAtmosphere = TimeAtmospherePalette.DAY
    }

    @Test
    fun hitBoxIsAtLeast48dpAndTappable() {
        var clicked = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppThemeState provides theme) {
                AetherTheme(themeState = theme) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                        AetherJumpToLatestControl(
                            visible = true,
                            unreadCount = 0,
                            reducedMotion = true,
                            onClick = { clicked = true }
                        )
                    }
                }
            }
        }

        val node = composeRule.onNodeWithTag("jump_to_latest")
        node.assertIsDisplayed()
        node.assertWidthIsAtLeast(48.dp)
        node.assertHeightIsAtLeast(48.dp)
        node.performClick()
        assertTrue("Click callback must be invoked on tap", clicked)
    }

    @Test
    fun unreadCountBadgeAttachesWhenPositive() {
        val countState = mutableIntStateOf(0)

        composeRule.setContent {
            CompositionLocalProvider(LocalAppThemeState provides theme) {
                AetherTheme(themeState = theme) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                        AetherJumpToLatestControl(
                            visible = true,
                            unreadCount = countState.intValue,
                            reducedMotion = true,
                            onClick = {}
                        )
                    }
                }
            }
        }

        // Initially 0: badge does not exist, semantics is plain
        assertEquals(0, composeRule.onAllNodesWithTag("jump_to_latest_unread_badge").fetchSemanticsNodes().size)
        composeRule.onNode(hasContentDescription("Jump to latest message")).assertExists()

        // Increment to 3: small badge attaches
        countState.intValue = 3
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("jump_to_latest_unread_badge", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("jump_to_latest_unread_badge", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNode(hasText("3")).assertIsDisplayed()
        composeRule.onNode(hasContentDescription("Jump to latest message, 3 unread")).assertExists()

        // Hit box remains >= 48dp
        val node = composeRule.onNodeWithTag("jump_to_latest")
        node.assertWidthIsAtLeast(48.dp)
        node.assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun hiddenWhenVisibleIsFalse() {
        val visibleState = mutableStateOf(false)

        composeRule.setContent {
            CompositionLocalProvider(LocalAppThemeState provides theme) {
                AetherTheme(themeState = theme) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                        AetherJumpToLatestControl(
                            visible = visibleState.value,
                            unreadCount = 2,
                            reducedMotion = true,
                            onClick = {}
                        )
                    }
                }
            }
        }

        assertEquals(0, composeRule.onAllNodesWithTag("jump_to_latest").fetchSemanticsNodes().size)

        visibleState.value = true
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("jump_to_latest").assertIsDisplayed()
        composeRule.onNodeWithTag("jump_to_latest_unread_badge", useUnmergedTree = true).assertIsDisplayed()
    }
}
