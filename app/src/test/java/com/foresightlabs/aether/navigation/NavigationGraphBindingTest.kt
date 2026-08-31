package com.foresightlabs.aether.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression coverage for the crash fixed in [com.foresightlabs.aether.navigation.AppNavigation]:
 * "Cannot navigate to settings. Navigation graph has not been set for NavController."
 *
 * Root cause: `AetherApp`'s single `NavHost` -- the only thing that ever attaches
 * a graph to the shared `navController` -- used to be composed only inside the
 * phone-width (`else`) branch of `if (isTablet) { ... } else { ... NavHost(...) }`.
 * At a tablet-width window, the `NavHost` was never composed at all, so the same
 * `navController` the tablet layout's `onNavigateToSettings` called `.navigate()`
 * on had no graph, and Navigation Compose throws exactly the reported
 * `IllegalArgumentException` the instant `navigate()` runs.
 *
 * These tests reproduce that exact shape -- a shared `NavHostController`, a
 * width-gated layout, and a `navigate()` call from within it -- to lock in the
 * fix (`NavHost` mounted unconditionally, with the wide layout as its sibling)
 * and to document the failure mode it replaces.
 */
@RunWith(AndroidJUnit4::class)
class NavigationGraphBindingTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * The fixed shape: the NavHost is mounted unconditionally, so the shared
     * navController always has a graph by the time a tablet-width layout's
     * Settings action can call navigate() on it -- mirrors AppNavigation.kt's
     * `Box(...) { ... if (isTablet) { Row(...) { ...onNavigateToSettings... } };
     * NavHost(...) { ... } }`.
     */
    @Composable
    private fun FixedTabletWidthHarness(navController: NavHostController) {
        Box(modifier = Modifier.requiredSize(900.dp, 900.dp)) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isTablet = maxWidth >= 720.dp

                if (isTablet) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Button(
                            onClick = { navController.navigate("settings") },
                            modifier = Modifier.testTag("open_settings")
                        ) {
                            Text("Settings")
                        }
                    }
                }

                NavHost(navController = navController, startDestination = "chats") {
                    composable("chats") {
                        // Tablet already shows its own content above; the phone
                        // layout would render Home here. Empty either way for
                        // this harness -- only the graph binding is under test.
                    }
                    composable("settings") {
                        Text("Settings screen", modifier = Modifier.testTag("settings_screen"))
                    }
                }
            }
        }
    }

    /** The exact bug shape: NavHost gated behind the same width check as the tablet layout. */
    @Composable
    private fun BrokenTabletWidthHarness(navController: NavHostController) {
        Box(modifier = Modifier.requiredSize(900.dp, 900.dp)) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isTablet = maxWidth >= 720.dp

                if (isTablet) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Button(
                            onClick = { navController.navigate("settings") },
                            modifier = Modifier.testTag("open_settings")
                        ) {
                            Text("Settings")
                        }
                    }
                } else {
                    NavHost(navController = navController, startDestination = "chats") {
                        composable("chats") {}
                        composable("settings") {
                            Text("Settings screen", modifier = Modifier.testTag("settings_screen"))
                        }
                    }
                }
            }
        }
    }

    @Test
    fun tappingSettingsAtTabletWidthNavigatesWithoutThrowing() {
        lateinit var navController: NavHostController
        composeRule.setContent {
            navController = rememberNavController()
            FixedTabletWidthHarness(navController)
        }

        composeRule.onNodeWithTag("open_settings").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings_screen").assertExists()
    }

    @Test(expected = IllegalArgumentException::class)
    fun theOldBrokenShapeThrowsExactlyTheReportedCrash() {
        lateinit var navController: NavHostController
        composeRule.setContent {
            navController = rememberNavController()
            BrokenTabletWidthHarness(navController)
        }

        // At tablet width the broken harness never composes a NavHost, so this
        // is the exact "Navigation graph has not been set for NavController."
        // IllegalArgumentException the bug report described.
        composeRule.onNodeWithTag("open_settings").performClick()
        composeRule.waitForIdle()
    }
}
