package com.foresightlabs.aether.ui.about

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.BuildConfig
import com.foresightlabs.aether.navigation.Destinations
import com.foresightlabs.aether.ui.settings.SettingsScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AboutScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun aboutScreenDisplaysVersionBuildAndKeySections() {
        composeRule.setContent {
            AboutScreen(onBack = {})
        }

        // Verify Hero & Version info
        composeRule.onNodeWithText("Aether").assertIsDisplayed()
        composeRule.onNodeWithText("“A quieter way to Telegram.”").assertIsDisplayed()
        composeRule.onNodeWithTag("about_version_info").assertIsDisplayed()
        composeRule.onNodeWithText("Current version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})").assertIsDisplayed()

        // Verify key section titles via performScrollToNode
        val listNode = composeRule.onNodeWithTag("about_list")
        listNode.performScrollToNode(hasText("ABOUT AETHER"))
        composeRule.onNodeWithText("ABOUT AETHER").assertIsDisplayed()

        listNode.performScrollToNode(hasText("THE AMBITION"))
        composeRule.onNodeWithText("THE AMBITION").assertIsDisplayed()

        listNode.performScrollToNode(hasText("WHAT MAKES AETHER DIFFERENT"))
        composeRule.onNodeWithText("WHAT MAKES AETHER DIFFERENT").assertIsDisplayed()

        listNode.performScrollToNode(hasText("TECHNOLOGY"))
        composeRule.onNodeWithText("TECHNOLOGY").assertIsDisplayed()

        listNode.performScrollToNode(hasText("PROJECT"))
        composeRule.onNodeWithText("PROJECT").assertIsDisplayed()

        listNode.performScrollToNode(hasText("OPEN SOURCE"))
        composeRule.onNodeWithText("OPEN SOURCE").assertIsDisplayed()

        listNode.performScrollToNode(hasText("LEGAL"))
        composeRule.onNodeWithText("LEGAL").assertIsDisplayed()

        // Verify Open Source attributions
        listNode.performScrollToNode(hasTestTag("about_open_source_text"))
        composeRule.onNodeWithTag("about_open_source_text").assertIsDisplayed()
        composeRule.onNodeWithText("Boost Software License 1.0").assertExists()
        composeRule.onNodeWithText("Apache License 2.0").assertExists()

        // Verify Legal & Copyright
        listNode.performScrollToNode(hasTestTag("about_legal_text"))
        composeRule.onNodeWithTag("about_legal_text").assertIsDisplayed()
    }

    @Test
    fun tappingGitHubRowTriggersUriHandler() {
        var openedUri: String? = null
        val testUriHandler = object : UriHandler {
            override fun openUri(uri: String) {
                openedUri = uri
            }
        }

        composeRule.setContent {
            CompositionLocalProvider(LocalUriHandler provides testUriHandler) {
                AboutScreen(onBack = {})
            }
        }

        composeRule.onNodeWithTag("about_list").performScrollToNode(hasTestTag("about_github_row"))
        composeRule.onNodeWithTag("about_github_row").performClick()
        assertEquals(AETHER_GITHUB_URL, openedUri)
    }

    @Test
    fun settingsAboutRowNavigatesToAboutScreen() {
        lateinit var navController: NavHostController

        composeRule.setContent {
            navController = rememberNavController()
            NavHost(navController = navController, startDestination = Destinations.SETTINGS) {
                composable(Destinations.SETTINGS) {
                    SettingsScreen(
                        currentUser = null,
                        confirmLogout = false,
                        onBack = {},
                        onNavigateToAppearance = {},
                        onNavigateToAbout = { navController.navigate(Destinations.ABOUT) },
                        onRequestLogout = {},
                        onConfirmLogout = {},
                        onDismissLogout = {}
                    )
                }
                composable(Destinations.ABOUT) {
                    AboutScreen(onBack = { navController.popBackStack() })
                }
            }
        }

        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_about_item"))
        composeRule.onNodeWithTag("settings_about_item").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("about_screen").assertIsDisplayed()
        assertEquals(Destinations.ABOUT, navController.currentDestination?.route)
    }

    @Test
    fun aboutNavigationWorksOnTabletLayoutUsingSingleNavController() {
        lateinit var navController: NavHostController

        composeRule.setContent {
            Box(modifier = Modifier.requiredSize(900.dp, 900.dp)) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val isTablet = this.maxWidth >= 720.dp
                    assertTrue(isTablet)

                    navController = rememberNavController()

                    Row(modifier = Modifier.fillMaxSize()) {
                        NavHost(navController = navController, startDestination = Destinations.SETTINGS) {
                            composable(Destinations.SETTINGS) {
                                SettingsScreen(
                                    currentUser = null,
                                    confirmLogout = false,
                                    onBack = {},
                                    onNavigateToAppearance = {},
                                    onNavigateToAbout = { navController.navigate(Destinations.ABOUT) },
                                    onRequestLogout = {},
                                    onConfirmLogout = {},
                                    onDismissLogout = {}
                                )
                            }
                            composable(Destinations.ABOUT) {
                                AboutScreen(onBack = { navController.popBackStack() })
                            }
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_about_item"))
        composeRule.onNodeWithTag("settings_about_item").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("about_screen").assertIsDisplayed()
        assertEquals(Destinations.ABOUT, navController.currentDestination?.route)
    }

    @Test
    fun settingsFooterRetainsCopyrightAndLegalDisclaimer() {
        composeRule.setContent {
            SettingsScreen(
                currentUser = null,
                confirmLogout = false,
                onBack = {},
                onNavigateToAppearance = {},
                onNavigateToAbout = {},
                onRequestLogout = {},
                onConfirmLogout = {},
                onDismissLogout = {}
            )
        }

        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_legal_footer"))
        composeRule.onNodeWithTag("settings_legal_footer").assertIsDisplayed()
        composeRule.onNodeWithText("© 2026 Aritra Saha / Foresight Labs. All rights reserved.", substring = true).assertExists()
        composeRule.onNodeWithText("Aether is an independent third-party client that uses the Telegram API.", substring = true).assertExists()
    }
}
