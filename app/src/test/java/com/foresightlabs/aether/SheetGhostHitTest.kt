package com.foresightlabs.aether

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ConnectionStatus
import com.foresightlabs.aether.screenshot.HomeFixtures
import com.foresightlabs.aether.ui.design.SheetAnchor
import com.foresightlabs.aether.ui.screens.HomeScreen
import com.foresightlabs.aether.ui.theme.AetherTheme
import com.foresightlabs.aether.ui.theme.AppThemeState
import com.foresightlabs.aether.ui.theme.AtmosphereMode
import com.foresightlabs.aether.ui.theme.LocalAppThemeState
import com.foresightlabs.aether.ui.theme.TimeAtmospherePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Regression cover for the release-blocking ghost-hit defect on Home.
 *
 * The defect: the personal hero — which carries the Active Now avatars, and those
 * avatars open chats — was faded out with a draw-time `graphicsLayer` as the sheet
 * was pulled down. Its pixels disappeared; its touch targets did not. Tapping the
 * empty atmosphere where an avatar used to be opened that person's chat.
 *
 * The invariant these tests hold is narrow and absolute: **a tap may only ever
 * activate something the user can actually see at that coordinate.**
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi", application = Application::class)
class SheetGhostHitTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val openedChats = mutableListOf<Chat>()
    private var settingsOpened = 0
    private var newMessageOpened = 0

    private val chats = HomeFixtures.populated

    @Before
    fun setUp() {
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
                        chats = chats,
                        currentUser = HomeFixtures.me,
                        connection = ConnectionStatus.READY,
                        isLoading = false,
                        onChatClick = { openedChats += it },
                        onNavigateToCalls = {},
                        onNavigateToSettings = { settingsOpened++ },
                        onNewMessageClick = { newMessageOpened++ }
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    // --- the reported defect -------------------------------------------------

    @Test
    fun tappingWhereAPresenceAvatarUsedToBeOpensNothing() {
        val avatar = firstPresenceAvatar()
        assertNotNull("Fixtures must produce an Active Now strip to guard", avatar)
        val staleSpot = avatar!!.second.center

        moveSheetTo(SheetAnchor.PEEK)

        tapRoot(staleSpot)
        assertTrue(
            "A faded-out presence avatar opened a chat at its old coordinates: $openedChats",
            openedChats.isEmpty()
        )
    }

    @Test
    fun tappingWhereTheTopBarUsedToBeOpensNothingOnceTheHeroHasReceded() {
        val settings = boundsOf("settings_button")
        val newMessage = boundsOf("new_conversation_button")

        moveSheetTo(SheetAnchor.EXPANDED)

        tapRoot(settings.center)
        tapRoot(newMessage.center)

        assertEquals("Receded settings button still accepted a tap", 0, settingsOpened)
        assertEquals("Receded compose button still accepted a tap", 0, newMessageOpened)
    }

    // --- the same invariant, stated generally --------------------------------

    /**
     * Sweeps a column of points down the whole screen at each anchor and requires
     * that whatever opened was the row genuinely occupying that point. This is the
     * invariant the defect broke, expressed without naming any particular layer, so
     * a future layer that fades or slides is covered too.
     */
    @Test
    fun everyAnchorOnlyActivatesTheRowActuallyOccupyingTheTappedPoint() {
        val rootHeight = composeRule.onRoot().fetchSemanticsNode().size.height.toFloat()
        val x = composeRule.onRoot().fetchSemanticsNode().size.width * 0.35f

        for (anchor in listOf(SheetAnchor.RESTING, SheetAnchor.EXPANDED, SheetAnchor.PEEK)) {
            moveSheetTo(anchor)
            var y = rootHeight * 0.06f
            while (y < rootHeight * 0.94f) {
                val point = Offset(x, y)
                val expected = chatOpeningTargets().entries
                    .firstOrNull { it.value.contains(point) }?.key
                openedChats.clear()
                tapRoot(point)
                val actual = openedChats.singleOrNull()?.id
                if (actual != null) {
                    assertEquals(
                        "At $anchor, tapping $point opened chat $actual, but the " +
                            "control occupying that point was ${expected ?: "none"}",
                        expected,
                        actual
                    )
                }
                y += rootHeight * 0.045f
            }
        }
    }

    @Test
    fun aVisibleChatRowStillOpensItsOwnChat() {
        moveSheetTo(SheetAnchor.EXPANDED)
        val target = chatRowBoundsById().entries.first()
        openedChats.clear()
        tapRoot(target.value.center)
        assertEquals(target.key, openedChats.singleOrNull()?.id)
    }

    // --- accessibility bounds move with the pixels ---------------------------

    @Test
    fun aFadedHeroExposesNoAccessibilityTarget() {
        assertNotNull(
            "The Active Now strip must be reachable while it is visible",
            firstPresenceAvatar()
        )
        moveSheetTo(SheetAnchor.PEEK)
        assertNull(
            "A faded-out presence avatar must not remain an accessibility target",
            firstPresenceAvatar()
        )
    }

    // --- settling and rapid gestures -----------------------------------------

    @Test
    fun anImmediateTapAfterMovingTheSheetDoesNotUseStaleCoordinates() {
        val avatar = firstPresenceAvatar()!!
        val staleSpot = avatar.second.center

        // No waitForIdle: tap while the settle animation is still running.
        invokeSheetAction("Collapse conversations")
        tapRoot(staleSpot)
        composeRule.waitForIdle()
        tapRoot(staleSpot)

        assertTrue(
            "A tap during or right after settling opened a chat from stale bounds: $openedChats",
            openedChats.isEmpty()
        )
    }

    // --- helpers -------------------------------------------------------------

    private fun tapRoot(point: Offset) {
        composeRule.onRoot().performTouchInput { click(point) }
        composeRule.waitForIdle()
    }

    private fun boundsOf(tag: String): Rect =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun chatRowBoundsById(): Map<String, Rect> = chats.mapNotNull { chat ->
        runCatching {
            composeRule.onNodeWithTag("chat_row_${chat.id}", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
        }.getOrNull()?.let { chat.id to it }
    }.toMap()

    /**
     * Every control currently on screen that opens a chat, by the chat it opens.
     *
     * Home has two of them: the conversation rows in the sheet, and the Active Now
     * avatars in the hero. Both are queried against the *merged* semantics tree —
     * the tree accessibility services read — so a control that has been made inert
     * correctly drops out of this map.
     */
    private fun chatOpeningTargets(): Map<String, Rect> =
        chatRowBoundsById() + presenceAvatarBoundsById()

    private fun presenceAvatarBoundsById(): Map<String, Rect> = chats.mapNotNull { chat ->
        boundsOfOrNull("active_now_person_${chat.id}")?.let { chat.id to it }
    }.toMap()

    /** The first Active Now avatar that is currently an addressable target, if any. */
    private fun firstPresenceAvatar(): Pair<String, Rect>? =
        presenceAvatarBoundsById().entries.firstOrNull()?.toPair()

    private fun boundsOfOrNull(tag: String): Rect? = runCatching {
        composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
    }.getOrNull()

    private fun moveSheetTo(anchor: SheetAnchor) {
        invokeSheetAction(
            when (anchor) {
                SheetAnchor.EXPANDED -> "Expand conversations"
                SheetAnchor.RESTING -> "Balance conversations"
                SheetAnchor.PEEK -> "Collapse conversations"
            }
        )
        composeRule.waitForIdle()
    }

    private fun invokeSheetAction(label: String) {
        // The action for the position the sheet already occupies is absent by design.
        findCustomAction(composeRule.onRoot().fetchSemanticsNode(), label)?.invoke()
    }

    private fun findCustomAction(node: SemanticsNode, label: String): (() -> Unit)? {
        node.config.getOrNull(SemanticsActions.CustomActions)
            ?.firstOrNull { it.label == label }
            ?.let { action -> return { composeRule.runOnUiThread { action.action() } } }
        node.children.forEach { child ->
            findCustomAction(child, label)?.let { return it }
        }
        return null
    }
}
