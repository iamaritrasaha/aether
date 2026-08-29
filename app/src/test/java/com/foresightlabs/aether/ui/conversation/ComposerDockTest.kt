package com.foresightlabs.aether.ui.conversation
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.domain.emoji.EmojiData
import com.foresightlabs.aether.domain.model.AnimationItem
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ChatType
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.MessageStatus
import com.foresightlabs.aether.domain.model.Presence
import com.foresightlabs.aether.domain.model.StickerItem
import com.foresightlabs.aether.domain.model.StickerSetInfo
import com.foresightlabs.aether.domain.model.User
import com.foresightlabs.aether.domain.search.ConversationSearchState
import com.foresightlabs.aether.ui.conversation.CurtainState
import com.foresightlabs.aether.ui.conversation.MessageComposer
import com.foresightlabs.aether.ui.conversation.PickerTab
import com.foresightlabs.aether.ui.conversation.ConversationScreen
import com.foresightlabs.aether.ui.theme.AetherTheme
import com.foresightlabs.aether.ui.theme.AppThemeState
import com.foresightlabs.aether.ui.theme.AtmosphereMode
import com.foresightlabs.aether.ui.theme.LocalAppThemeState
import com.foresightlabs.aether.ui.theme.TimeAtmospherePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi", application = Application::class)
class ComposerDockTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // --- State Model Tests ---

    @Test
    fun curtainStatePropertiesAreCorrect() {
        assertFalse(CurtainState.COMPOSER.isExpanded)
        assertFalse(CurtainState.COMPOSER.isPicker)

        assertTrue(CurtainState.ATTACHMENTS.isExpanded)
        assertFalse(CurtainState.ATTACHMENTS.isPicker)

        assertTrue(CurtainState.EMOJI.isExpanded)
        assertTrue(CurtainState.EMOJI.isPicker)

        assertTrue(CurtainState.STICKERS.isExpanded)
        assertTrue(CurtainState.STICKERS.isPicker)

        assertTrue(CurtainState.GIFS.isExpanded)
        assertTrue(CurtainState.GIFS.isPicker)
    }

    // --- Emoji Insertion & Cursor Preservation ---

    @Test
    fun insertingEmojiAtCursorAppendsCorrectly() {
        val initial = TextFieldValue("Hello ", selection = TextRange(6))
        val emoji = "❤️"
        val start = initial.selection.min.coerceIn(0, initial.text.length)
        val end = initial.selection.max.coerceIn(0, initial.text.length)
        val newText = initial.text.replaceRange(start, end, emoji)
        val newCursor = start + emoji.length
        val result = initial.copy(text = newText, selection = TextRange(newCursor))

        assertEquals("Hello ❤️", result.text)
        assertEquals(TextRange(6 + emoji.length), result.selection)
    }

    @Test
    fun insertingEmojiInMiddleOfTextPreservesSurroundingText() {
        val initial = TextFieldValue("Hello world", selection = TextRange(5))
        val emoji = "✨"
        val start = initial.selection.min.coerceIn(0, initial.text.length)
        val end = initial.selection.max.coerceIn(0, initial.text.length)
        val newText = initial.text.replaceRange(start, end, emoji)
        val newCursor = start + emoji.length
        val result = initial.copy(text = newText, selection = TextRange(newCursor))

        assertEquals("Hello✨ world", result.text)
        assertEquals(TextRange(5 + emoji.length), result.selection)
    }

    @Test
    fun insertingEmojiOverSelectionReplacesSelectedRange() {
        val initial = TextFieldValue("Hello beautiful world", selection = TextRange(6, 15))
        val emoji = "🌸"
        val start = initial.selection.min.coerceIn(0, initial.text.length)
        val end = initial.selection.max.coerceIn(0, initial.text.length)
        val newText = initial.text.replaceRange(start, end, emoji)
        val newCursor = start + emoji.length
        val result = initial.copy(text = newText, selection = TextRange(newCursor))

        assertEquals("Hello 🌸 world", result.text)
        assertEquals(TextRange(6 + emoji.length), result.selection)
    }

    // --- Emoji Data & Recents Integrity ---

    @Test
    fun emojiCategoriesArePopulated() {
        val categories = EmojiData.categories
        assertEquals(9, categories.size)
        assertTrue(categories.all { it.emojis.isNotEmpty() })
        assertTrue(categories.any { it.id == "smileys" })
        assertTrue(categories.any { it.id == "symbols" })
        assertTrue(categories.any { it.id == "flags" })
    }

    @Test
    fun recordingRecentEmojiMovesItToTop() {
        EmojiData.recordRecentEmoji("🔥")
        assertEquals("🔥", EmojiData.recentEmojis.first())

        EmojiData.recordRecentEmoji("🚀")
        assertEquals("🚀", EmojiData.recentEmojis.first())
        assertEquals("🔥", EmojiData.recentEmojis[1])
    }

    // --- Composer UI Composition & Mode Switching ---

    @Test
    fun tappingPlusButtonTogglesAttachmentGrid() {
        var currentDockMode by mutableStateOf(CurtainState.COMPOSER)
        composeRule.setContent {
            val theme = AppThemeState()
            AetherTheme(themeState = theme) {
                MessageComposer(
                    replyingTo = null,
                    onDismissReply = {},
                    onSendMessage = { _, _ -> },
                    curtainState = currentDockMode,
                    onCurtainStateChange = { currentDockMode = it }
                )
            }
        }
        composeRule.waitForIdle()

        // Initially collapsed
        assertEquals(CurtainState.COMPOSER, currentDockMode)

        // Tap Plus
        composeRule.onNodeWithTag("attachment_button").performClick()
        composeRule.waitForIdle()
        assertEquals(CurtainState.ATTACHMENTS, currentDockMode)
        composeRule.onNodeWithTag("attachment_option_gallery").assertIsDisplayed()

        // Tap Plus again to collapse
        composeRule.onNodeWithTag("attachment_button").performClick()
        composeRule.waitForIdle()
        assertEquals(CurtainState.COMPOSER, currentDockMode)
    }

    @Test
    fun tappingEmojiButtonTogglesEmojiPicker() {
        var currentDockMode by mutableStateOf(CurtainState.COMPOSER)
        composeRule.setContent {
            val theme = AppThemeState()
            AetherTheme(themeState = theme) {
                MessageComposer(
                    replyingTo = null,
                    onDismissReply = {},
                    onSendMessage = { _, _ -> },
                    curtainState = currentDockMode,
                    onCurtainStateChange = { currentDockMode = it }
                )
            }
        }
        composeRule.waitForIdle()

        // Tap Emoji button
        composeRule.onNodeWithTag("sticker_button").performClick()
        composeRule.waitForIdle()
        assertEquals(CurtainState.EMOJI, currentDockMode)
        composeRule.onNodeWithTag("emoji_sticker_gif_panel").assertIsDisplayed()

        // Tap again (now showing keyboard icon) to collapse
        composeRule.onNodeWithTag("sticker_button").performClick()
        composeRule.waitForIdle()
        assertEquals(CurtainState.COMPOSER, currentDockMode)
    }

    @Test
    fun switchingBetweenAttachmentsAndEmojiMaintainsSingleDock() {
        var currentDockMode by mutableStateOf(CurtainState.COMPOSER)
        composeRule.setContent {
            val theme = AppThemeState()
            AetherTheme(themeState = theme) {
                MessageComposer(
                    replyingTo = null,
                    onDismissReply = {},
                    onSendMessage = { _, _ -> },
                    curtainState = currentDockMode,
                    onCurtainStateChange = { currentDockMode = it }
                )
            }
        }
        composeRule.waitForIdle()

        // Open Attachments
        composeRule.onNodeWithTag("attachment_button").performClick()
        composeRule.waitForIdle()
        assertEquals(CurtainState.ATTACHMENTS, currentDockMode)

        // Switch to Emoji directly
        composeRule.onNodeWithTag("sticker_button").performClick()
        composeRule.waitForIdle()
        assertEquals(CurtainState.EMOJI, currentDockMode)
        composeRule.onNodeWithTag("emoji_sticker_gif_panel").assertIsDisplayed()

        // Switch back to Attachments directly
        composeRule.onNodeWithTag("attachment_button").performClick()
        composeRule.waitForIdle()
        assertEquals(CurtainState.ATTACHMENTS, currentDockMode)
        composeRule.onNodeWithTag("attachment_option_gallery").assertIsDisplayed()
    }

    @Test
    fun stickerAndGifActionsRouteCorrectly() {
        var sentStickerFileId = 0
        var sentAnimationFileId = 0
        var currentDockMode by mutableStateOf(CurtainState.STICKERS)

        val testSticker = StickerItem(fileId = 12345, emoji = "😀")
        val testAnim = AnimationItem(fileId = 67890, fileName = "funny.gif")

        composeRule.setContent {
            val theme = AppThemeState()
            AetherTheme(themeState = theme) {
                MessageComposer(
                    replyingTo = null,
                    onDismissReply = {},
                    onSendMessage = { _, _ -> },
                    curtainState = currentDockMode,
                    onCurtainStateChange = { currentDockMode = it },
                    recentStickers = listOf(testSticker),
                    savedAnimations = listOf(testAnim),
                    onSendSticker = { fileId, _ -> sentStickerFileId = fileId },
                    onSendAnimation = { fileId -> sentAnimationFileId = fileId }
                )
            }
        }
        composeRule.waitForIdle()

        // Tap sticker item
        composeRule.onNodeWithTag("sticker_item_12345").performClick()
        assertEquals(12345, sentStickerFileId)

        // Switch to GIFs tab inside panel
        composeRule.onNodeWithTag("picker_tab_gifs").performClick()
        composeRule.waitForIdle()
        assertEquals(CurtainState.GIFS, currentDockMode)

        // Tap GIF item
        composeRule.onNodeWithTag("gif_item_67890").performClick()
        assertEquals(67890, sentAnimationFileId)
    }
}
