package com.foresightlabs.aether.ui.conversation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.ui.theme.AetherTheme
import com.foresightlabs.aether.ui.theme.AppThemeState
import com.foresightlabs.aether.ui.theme.AtmosphereMode
import com.foresightlabs.aether.ui.theme.LocalAppThemeState
import com.foresightlabs.aether.ui.theme.TimeAtmospherePalette
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The bookmark glyph on a message bubble: it must be driven by real, observable
 * bookmark state (never a value captured once and left stale), so it appears the
 * instant a message is bookmarked and disappears the instant it isn't -- surviving
 * recomposition the same way [com.foresightlabs.aether.ui.conversation.ConversationViewModel.bookmarkedMessageIds]
 * (a live `StateFlow`) does in the real screen.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi", application = Application::class)
class MessageBubbleBookmarkTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun message(id: String) = Message(
        id = id,
        chatId = "1",
        senderId = "1",
        senderName = "Test",
        text = "Hello",
        timestamp = "10:41 AM",
        isOutgoing = false
    )

    private fun host(content: @Composable () -> Unit) {
        val appTheme = AppThemeState().apply {
            atmosphereMode = AtmosphereMode.MANUAL
            manualAtmosphere = TimeAtmospherePalette.DAY
        }
        composeRule.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalAppThemeState provides appTheme
            ) {
                AetherTheme(themeState = appTheme) { content() }
            }
        }
    }

    @Test
    fun noGlyphWhenTheMessageIsNotBookmarked() {
        host {
            val context = LocalContext.current
            val playback = remember { AudioPlaybackController(context) }
            MessageBubble(
                message = message("1"),
                onSwipeToReply = {},
                onLongPress = {},
                onMediaClick = {},
                audioPlayback = playback,
                onReactionClick = { _, _ -> },
                isBookmarked = false
            )
        }
        composeRule.onNodeWithContentDescription("Bookmarked").assertDoesNotExist()
    }

    @Test
    fun glyphAppearsWhenTheMessageIsBookmarked() {
        host {
            val context = LocalContext.current
            val playback = remember { AudioPlaybackController(context) }
            MessageBubble(
                message = message("2"),
                onSwipeToReply = {},
                onLongPress = {},
                onMediaClick = {},
                audioPlayback = playback,
                onReactionClick = { _, _ -> },
                isBookmarked = true
            )
        }
        composeRule.onNodeWithContentDescription("Bookmarked").assertExists()
    }

    /**
     * Both directions in one recomposition -- guards against a value that was
     * only ever read once (e.g. a `remember` with no key) and so never reflects
     * a later toggle without the whole bubble being torn down and rebuilt.
     */
    @Test
    fun glyphTracksBookmarkStateAcrossRecomposition() {
        var bookmarked by mutableStateOf(false)
        host {
            val context = LocalContext.current
            val playback = remember { AudioPlaybackController(context) }
            MessageBubble(
                message = message("3"),
                onSwipeToReply = {},
                onLongPress = {},
                onMediaClick = {},
                audioPlayback = playback,
                onReactionClick = { _, _ -> },
                isBookmarked = bookmarked
            )
        }
        composeRule.onNodeWithContentDescription("Bookmarked").assertDoesNotExist()

        bookmarked = true
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Bookmarked").assertExists()

        bookmarked = false
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Bookmarked").assertDoesNotExist()
    }
}
