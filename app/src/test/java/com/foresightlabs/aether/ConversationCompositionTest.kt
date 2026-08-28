package com.foresightlabs.aether

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ChatType
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.MessageStatus
import com.foresightlabs.aether.domain.model.Presence
import com.foresightlabs.aether.domain.model.User
import com.foresightlabs.aether.domain.search.ConversationSearchState
import com.foresightlabs.aether.ui.screens.ConversationScreen
import com.foresightlabs.aether.ui.theme.AetherTheme
import com.foresightlabs.aether.ui.theme.AppThemeState
import com.foresightlabs.aether.ui.theme.AtmosphereMode
import com.foresightlabs.aether.ui.theme.LocalAppThemeState
import com.foresightlabs.aether.ui.theme.TimeAtmospherePalette
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The conversation screen is two regions with a real depth relationship: a pale
 * canvas in front, and a deep footer behind it that runs to the physical bottom
 * of the window.
 *
 * These tests hold that relationship structurally rather than by eye — the canvas
 * has to stop short of the bottom, the input has to be anchored to the footer's
 * lower edge with no gap left under it, and no message may end up behind either.
 * They run at every phone width Aether ships to, because the proportion is what
 * carries the composition.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi", application = Application::class)
class ConversationCompositionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val searchState = mutableStateOf(ConversationSearchState.Idle)

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
                    ConversationScreen(
                        chat = chat,
                        messages = messages,
                        canSend = true,
                        onBack = {},
                        onNavigateToProfile = {},
                        onSendMessage = { _, _, _, _ -> },
                        onComposerChanged = {},
                        onLoadOlder = {},
                        onDeleteMessage = { _, _ -> },
                        onRetryMessage = {},
                        onVisibleMessages = {},
                        searchState = searchState.value
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    // --- gesture-first navigation -------------------------------------------

    /**
     * Aether leaves a conversation through the system's own back gesture, so no
     * state of this screen may offer a back arrow of its own. Cancel controls in
     * transient modes are a different thing and stay.
     */
    @Test
    fun theConversationOffersNoBackControlOfItsOwn() = assertNoBackControl()

    @Test
    fun searchModeAlsoOffersNoBackControl() {
        composeRule.runOnUiThread {
            searchState.value = ConversationSearchState(query = "gallery", isActive = true)
        }
        composeRule.waitForIdle()
        assertNoBackControl()
    }

    private fun assertNoBackControl() {
        composeRule.onAllNodesWithTag("conversation_back_button", useUnmergedTree = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Back", useUnmergedTree = true)
            .assertCountEquals(0)
    }

    /**
     * With the arrow gone the identity should take the space, not leave a hole
     * where a button used to be.
     */
    @Test
    fun theIdentityStartsAtTheScreenInsetRatherThanBehindAGap() {
        val root = composeRule.onRoot().fetchSemanticsNode().size
        val identity = boundsOf("conversation_header_profile")
        assertTrue(
            "The header identity starts ${identity.left}px in; with no back arrow " +
                "it should sit at the screen inset",
            identity.left <= root.width * 0.08f
        )
    }

    /**
     * The frosted header only reads the living atmosphere behind it when it lives
     * inside the same "conversation_canvas" Box that registers that atmosphere as
     * Haze's backdrop source. A header rendered as a cousin instead of a child —
     * outside that Box rather than inside it — once shipped with the header as a
     * flat, opaque panel instead of glass. This holds the relationship
     * structurally so that regression can't silently return.
     */
    @Test
    fun theHeaderIsAChildOfTheCanvasItFrosts() {
        var node = composeRule.onNodeWithTag("conversation_header_profile", useUnmergedTree = true)
            .fetchSemanticsNode()
            .parent
        while (node != null) {
            if (node.config.getOrNull(SemanticsProperties.TestTag) == "conversation_canvas") {
                return
            }
            node = node.parent
        }
        assertTrue("The header is no longer a descendant of the atmospheric canvas it frosts", false)
    }

    // --- the depth relationship ---------------------------------------------

    @Test
    fun theCanvasStopsShortOfTheBottomSoTheFooterShowsBehindIt() = assertComposition()

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun theCompositionHoldsOnA360dpDisplay() = assertComposition()

    @Test
    @Config(qualifiers = "w393dp-h851dp-xhdpi")
    fun theCompositionHoldsOnA393dpDisplay() = assertComposition()

    @Test
    @Config(qualifiers = "w412dp-h892dp-xhdpi")
    fun theCompositionHoldsOnA412dpDisplay() = assertComposition()

    private fun assertComposition() {
        val root = composeRule.onRoot().fetchSemanticsNode().size
        val canvas = boundsOf("conversation_canvas")
        val composer = boundsOf("message_composer")

        assertTrue(
            "The canvas ran to the bottom of the window; the footer has to show " +
                "behind its lower edge",
            canvas.bottom < root.height.toFloat() - 1f
        )
        assertTrue(
            "The composer must sit below the canvas, inside the footer",
            composer.top >= canvas.bottom - 1f
        )
        assertTrue(
            "The conversation must own most of the height; the canvas took only " +
                "${"%.0f".format(canvas.bottom / root.height * 100)}%",
            canvas.bottom / root.height >= 0.80f
        )
        assertTrue(
            "The canvas is meant to end above the footer, not swallow it",
            canvas.bottom / root.height <= 0.94f
        )
        assertTrue(
            "The composer ran off the side at ${root.width}px wide",
            composer.left >= -1f && composer.right <= root.width.toFloat() + 1f
        )
    }

    @Test
    fun theInputIsAnchoredToTheBottomWithNoDeadSpaceUnderIt() {
        val root = composeRule.onRoot().fetchSemanticsNode().size
        val composer = boundsOf("message_composer")
        val gap = root.height.toFloat() - composer.bottom

        assertTrue(
            "There are ${gap}px of empty footer under the input; it should be " +
                "anchored to the bottom, clearing only the gesture inset",
            gap <= 48f
        )
    }

    /**
     * The counterpart of Home's expanded dock: the same surface, collapsed. It
     * still reaches the bottom edge, but now it is shallow enough that the
     * conversation owns the screen.
     */
    @Test
    fun theDockIsCollapsedAndStillAttachedToTheBottomEdge() {
        val root = composeRule.onRoot().fetchSemanticsNode().size
        val dock = boundsOf("conversation_dock")

        assertTrue(
            "The dock left a ${root.height - dock.bottom}px gap under it",
            dock.bottom >= root.height.toFloat() - 1f
        )
        assertTrue(
            "Collapsed, the dock should be shallow; it took " +
                "${"%.0f".format(dock.height / root.height * 100)}% of the window",
            dock.height / root.height <= 0.20f
        )
        assertTrue(
            "The composer has to live inside the dock",
            boundsOf("message_composer").top >= dock.top - 1f
        )
    }

    @Test
    fun noMessageEndsUpBehindTheFooter() {
        val canvas = boundsOf("conversation_canvas")
        messages.forEach { message ->
            val bubble = boundsOfOrNull("message_bubble_${message.id}") ?: return@forEach
            assertTrue(
                "Message ${message.id} is cut off by the footer edge",
                bubble.bottom <= canvas.bottom + 1f
            )
        }
    }

    // --- helpers -------------------------------------------------------------

    private fun boundsOf(tag: String): Rect =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun boundsOfOrNull(tag: String): Rect? = runCatching {
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    }.getOrNull()

    private val chat = Chat(
        id = "103",
        title = "Ishani Roy",
        type = ChatType.DIRECT,
        lastMessageText = "Let's review the new studio proofs together.",
        lastMessageTime = "10:42 AM",
        avatarInitials = "IR",
        avatarGradient = listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)),
        directUser = User(
            id = "103",
            name = "Ishani Roy",
            username = "ishani",
            avatarInitials = "IR",
            avatarGradient = listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)),
            phone = "+1 555 0103",
            presence = Presence.ONLINE
        )
    )

    private val messages = listOf(
        Message(
            id = "1",
            chatId = "103",
            senderId = "103",
            senderName = "Ishani Roy",
            text = "Are we still doing the gallery review at 3pm?",
            timestamp = "10:30 AM",
            isOutgoing = false
        ),
        Message(
            id = "2",
            chatId = "103",
            senderId = "me",
            senderName = "You",
            text = "Hello",
            timestamp = "10:32 AM",
            isOutgoing = true,
            status = MessageStatus.READ
        )
    )
}
