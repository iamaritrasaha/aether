package com.foresightlabs.aether.ui.home
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ConnectionStatus
import com.foresightlabs.aether.screenshot.HomeFixtures
import com.foresightlabs.aether.ui.home.HomeScreen
import com.foresightlabs.aether.ui.theme.AetherTheme
import com.foresightlabs.aether.ui.theme.AppThemeState
import com.foresightlabs.aether.ui.theme.AtmosphereMode
import com.foresightlabs.aether.ui.theme.LocalAppThemeState
import com.foresightlabs.aether.ui.theme.TimeAtmospherePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Home is one static composition: an atmosphere region above a content surface.
 *
 * These tests hold the two properties that composition has to keep. First, the
 * proportion: the atmosphere must stay a genuine hero — large enough to breathe,
 * never so large that the conversations are pushed off the screen — at every phone
 * width Aether ships to. Second, the hit invariant carried over from the sheet era
 * and stated without reference to it: **a tap may only ever activate something the
 * user can actually see at that coordinate.**
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi", application = Application::class)
class HomeCompositionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val openedChats = mutableListOf<Chat>()
    private var newMessageOpened = 0
    private var settingsOpened = 0

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

    // --- the composition -----------------------------------------------------

    @Test
    fun theAtmosphereIsAGenuineHeroAndTheConversationsStillGetTheirRoom() {
        val root = composeRule.onRoot().fetchSemanticsNode().size.height.toFloat()
        val fraction = boundsOf("home_hero").bottom / root

        assertTrue(
            "The hero collapsed to ${"%.0f".format(fraction * 100)}% of the screen; " +
                "Home's upper region is meant to be a large calm atmosphere",
            fraction >= 0.34f
        )
        assertTrue(
            "The hero took ${"%.0f".format(fraction * 100)}% of the screen, leaving " +
                "too little of it for the conversations",
            fraction <= 0.50f
        )
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun theCompositionHoldsOnA360dpDisplay() = assertHomeFitsItsWidth()

    @Test
    @Config(qualifiers = "w393dp-h851dp-xhdpi")
    fun theCompositionHoldsOnA393dpDisplay() = assertHomeFitsItsWidth()

    @Test
    @Config(qualifiers = "w412dp-h892dp-xhdpi")
    fun theCompositionHoldsOnA412dpDisplay() = assertHomeFitsItsWidth()

    /**
     * The proportion survives, and nothing in the hero or the list runs off the
     * side of the display.
     */
    private fun assertHomeFitsItsWidth() {
        val root = composeRule.onRoot().fetchSemanticsNode().size
        val fraction = boundsOf("home_hero").bottom / root.height.toFloat()
        assertTrue(
            "Hero fraction ${"%.2f".format(fraction)} is outside the intended range",
            fraction in 0.30f..0.52f
        )
        listOf("home_greeting", "aether_daily", "home_top_search", "home_settings_button", "active_now_strip")
            .mapNotNull { boundsOfOrNull(it) }
            .forEach { bounds ->
                assertTrue(
                    "Hero content ran past the right edge at ${root.width}px wide",
                    bounds.right <= root.width.toFloat() + 1f
                )
                assertTrue("Hero content ran past the left edge", bounds.left >= -1f)
            }
        chatRowBoundsById().values.forEach { row ->
            assertTrue(
                "A conversation row ran past the right edge at ${root.width}px wide",
                row.right <= root.width.toFloat() + 1f
            )
        }
    }

    @Test
    fun theGreetingIsTheDominantElementOfTheHero() {
        val greeting = boundsOf("home_greeting")
        val search = boundsOf("home_top_search")

        assertTrue(
            "The greeting must sit below the quiet search control, not compete with it",
            greeting.top >= search.bottom
        )
        assertTrue(
            "The greeting is meant to be the largest thing on the hero; it measured " +
                "${greeting.height}px against a ${search.height}px search control",
            greeting.height > search.height
        )
    }

    /**
     * The layering the screen is built on: the conversations are the rear layer
     * and already fill the window, and the hero is a panel lying on top of them.
     * Not a dark sheet laid onto the atmosphere — the other way round.
     */
    @Test
    fun theConversationsAreTheRearLayerAndTheHeroSitsInFrontOfThem() {
        val root = composeRule.onRoot().fetchSemanticsNode().size
        val rear = boundsOf("conversations_surface")
        val hero = boundsOf("home_hero")

        assertTrue(
            "The conversations must start at the top of the window and run behind " +
                "the hero, not begin below it as a sheet",
            rear.top <= hero.top + 1f
        )
        assertTrue(
            "The rear layer left a ${root.height - rear.bottom}px gap under it",
            rear.bottom >= root.height.toFloat() - 1f
        )
        assertTrue(
            "The hero has to be the shorter of the two — it is the panel, the " +
                "conversations are the screen behind it",
            hero.height < rear.height
        )
    }

    /**
     * The conversations run underneath the hero, so the first one has to come out
     * from under its lower edge with room to breathe rather than being pressed
     * against the curve.
     */
    @Test
    fun theFirstConversationClearsTheHerosLowerEdge() {
        val hero = boundsOf("home_hero")
        val firstRow = chatRowBoundsById().values.minByOrNull { it.top }
        assertNotNull("Fixtures must produce conversations to guard", firstRow)
        val gap = firstRow!!.top - hero.bottom
        assertTrue(
            "The first conversation sits ${gap}px from the hero's edge; it should " +
                "clear the curve, not touch it",
            gap > 0f
        )
    }

    // --- the hit invariant ---------------------------------------------------

    /**
     * Sweeps a column of points down the whole screen and requires that whatever
     * opened was the control genuinely occupying that point. Stated without naming
     * any particular layer, so a future layer that fades or slides is covered too.
     */
    @Test
    fun onlyTheControlActuallyOccupyingATappedPointCanActivate() {
        val rootHeight = composeRule.onRoot().fetchSemanticsNode().size.height.toFloat()
        val x = composeRule.onRoot().fetchSemanticsNode().size.width * 0.35f

        var y = rootHeight * 0.06f
        while (y < rootHeight * 0.94f) {
            val point = Offset(x, y)
            // Adjacent rows tile to sub-pixel precision, so a point landing exactly
            // on a seam legitimately belongs to either neighbour. Every control
            // whose bounds reach the point is therefore an acceptable answer — a
            // ghost hit is off by a whole region, never by half a pixel.
            val candidates = chatOpeningTargets()
                .filter { it.second.inflate(1.5f).contains(point) }
                .map { it.first }
            openedChats.clear()
            tapRoot(point)
            val actual = openedChats.singleOrNull()?.id
            if (actual != null) {
                assertTrue(
                    "Tapping $point opened chat $actual, but the controls occupying " +
                        "that point were ${candidates.ifEmpty { listOf("none") }}",
                    actual in candidates
                )
            }
            y += rootHeight * 0.045f
        }
    }

    @Test
    fun aVisibleChatRowOpensItsOwnChat() {
        val target = chatRowBoundsById().entries.first()
        openedChats.clear()
        tapRoot(target.value.center)
        assertEquals(target.key, openedChats.singleOrNull()?.id)
    }

    @Test
    fun aPresenceAvatarOpensThatPersonsConversation() {
        val avatar = firstPresenceAvatar()
        assertNotNull("Fixtures must produce an Active Now strip to guard", avatar)
        openedChats.clear()
        tapRoot(avatar!!.second.center)
        assertEquals(avatar.first, openedChats.singleOrNull()?.id)
    }

    @Test
    fun startingAConversationIsTheFirstThingInTheStrip() {
        tapRoot(boundsOf("active_now_new").center)
        assertEquals(1, newMessageOpened)
    }

    @Test
    fun tappingSettingsOpensSettings() {
        tapRoot(boundsOf("home_settings_button").center)
        assertEquals(1, settingsOpened)
    }

    // --- helpers -------------------------------------------------------------

    private fun tapRoot(point: Offset) {
        composeRule.onRoot().performTouchInput { click(point) }
        composeRule.waitForIdle()
    }

    private fun boundsOf(tag: String): Rect =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun chatRowBoundsById(): Map<String, Rect> = chats.filter { it.isPersonalChat }.mapNotNull { chat ->
        runCatching {
            composeRule.onNodeWithTag("chat_row_${chat.id}", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
        }.getOrNull()?.let { chat.id to it }
    }.toMap()

    /**
     * Every control currently on screen that opens a chat, by the chat it opens.
     *
     * Home has two of them: the conversation rows on the content surface, and the
     * Active Now avatars in the hero. Both are queried against the *merged*
     * semantics tree — the tree accessibility services read — so a control that has
     * been made inert correctly drops out of this map.
     */
    private fun chatOpeningTargets(): List<Pair<String, Rect>> =
        // A chat can be reachable twice at once — as a row and as a presence
        // avatar — so these are kept as separate targets rather than collapsed
        // into a map keyed by chat.
        chatRowBoundsById().toList() + presenceAvatarBoundsById()

    private fun presenceAvatarBoundsById(): List<Pair<String, Rect>> = chats.mapNotNull { chat ->
        boundsOfOrNull("active_now_person_${chat.id}")?.let { chat.id to it }
    }

    private fun firstPresenceAvatar(): Pair<String, Rect>? =
        presenceAvatarBoundsById().firstOrNull()

    private fun boundsOfOrNull(tag: String): Rect? = runCatching {
        composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
    }.getOrNull()
}
