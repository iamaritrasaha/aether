package com.foresightlabs.aether.screenshot

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ChatType
import com.foresightlabs.aether.domain.model.MediaItem
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.MessageStatus
import com.foresightlabs.aether.domain.model.MessageType
import com.foresightlabs.aether.domain.model.Presence
import com.foresightlabs.aether.domain.model.Reaction
import com.foresightlabs.aether.domain.model.User
import com.foresightlabs.aether.ui.screens.ConversationScreen
import com.foresightlabs.aether.ui.theme.AetherTheme
import com.foresightlabs.aether.ui.theme.AppThemeState
import com.foresightlabs.aether.ui.theme.AppThemeMode
import com.foresightlabs.aether.ui.theme.AtmosphereMode
import com.foresightlabs.aether.ui.theme.LocalAppThemeState
import com.foresightlabs.aether.ui.theme.TimeAtmospherePalette
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi", application = Application::class)
class ConversationScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private data class Scenario(
        val name: String,
        val chat: Chat,
        val messages: List<Message>,
        val theme: AppThemeState
    )

    private val scenario = mutableStateOf<Scenario?>(null)
    private var contentInstalled = false

    private val outputDir = File("build/reports/aether-screenshots").apply { mkdirs() }

    private fun themeState(palette: TimeAtmospherePalette) = AppThemeState().apply {
        atmosphereMode = AtmosphereMode.MANUAL
        manualAtmosphere = palette
    }

    private fun capture(
        name: String,
        chat: Chat,
        messages: List<Message>,
        state: AppThemeState = themeState(TimeAtmospherePalette.DAY)
    ) {
        val next = Scenario(name, chat, messages, state)
        if (!contentInstalled) {
            contentInstalled = true
            composeRule.setContent {
                scenario.value?.let { active ->
                    key(active.name) { Render(active) }
                }
            }
        }
        composeRule.runOnUiThread { scenario.value = next }
        composeRule.waitForIdle()
        writePng(name)
    }

    @Composable
    private fun Render(active: Scenario) {
        CompositionLocalProvider(
            LocalInspectionMode provides true,
            LocalAppThemeState provides active.theme
        ) {
            AetherTheme(themeState = active.theme) {
                ConversationScreen(
                    chat = active.chat,
                    messages = active.messages,
                    canSend = true,
                    onBack = {},
                    onNavigateToProfile = {},
                    onSendMessage = { _, _ -> },
                    onComposerChanged = {},
                    onLoadOlder = {},
                    onDeleteMessage = {},
                    onRetryMessage = {},
                    onVisibleMessages = {}
                )
            }
        }
    }

    private fun writePng(name: String) {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        val view = composeRule.activity.window.decorView
        require(view.width > 0 && view.height > 0) {
            "decor view was not laid out (${view.width}x${view.height})"
        }
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val file = File(outputDir, "$name.png")
        file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        bitmap.recycle()
        assertTrue("no pixels written for $name", file.length() > 0)
    }

    @Test
    fun conversationRichMessagingPopulated() {
        val directUser = User(
            id = "103",
            name = "Ishani Roy",
            username = "ishani",
            avatarInitials = "IR",
            avatarGradient = listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)),
            phone = "+1 555 0103",
            presence = Presence.ONLINE
        )

        val chat = Chat(
            id = "103",
            title = "Ishani Roy",
            type = ChatType.DIRECT,
            lastMessageText = "Let's review the new studio proofs together.",
            lastMessageTime = "10:42 AM",
            avatarInitials = "IR",
            avatarGradient = listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)),
            directUser = directUser,
            hasUnseenPulse = true
        )

        val replyTarget = Message(
            id = "1",
            chatId = "103",
            senderId = "103",
            senderName = "Ishani Roy",
            text = "Are we still doing the gallery review at 3pm?",
            timestamp = "10:30 AM",
            isOutgoing = false
        )

        val messages = listOf(
            replyTarget,
            Message(
                id = "2",
                chatId = "103",
                senderId = "me",
                senderName = "You",
                text = "Yes, absolutely! Bringing the latest prototypes.",
                timestamp = "10:32 AM",
                isOutgoing = true,
                status = MessageStatus.READ,
                replyToMessage = replyTarget,
                reactions = listOf(Reaction("🔥", 1, true))
            ),
            Message(
                id = "3",
                chatId = "103",
                senderId = "103",
                senderName = "Ishani Roy",
                text = "Here's the current workspace setup ✨",
                timestamp = "10:35 AM",
                isOutgoing = false,
                type = MessageType.IMAGE,
                mediaItems = listOf(
                    MediaItem(
                        id = "m1",
                        url = "https://images.unsplash.com/photo-1513519245088-0e12902e5a38",
                        caption = "Ceramics workbench"
                    )
                ),
                reactions = listOf(Reaction("❤️", 2, false), Reaction("👏", 1, true)),
                isPinned = true
            ),
            Message(
                id = "4",
                chatId = "103",
                senderId = "me",
                senderName = "You",
                text = "Looks incredible. The lighting is perfect!",
                timestamp = "10:38 AM",
                isOutgoing = true,
                status = MessageStatus.READ
            ),
            Message(
                id = "5",
                chatId = "103",
                senderId = "103",
                senderName = "Ishani Roy",
                text = "Quick audio note on the glaze technique",
                timestamp = "10:40 AM",
                isOutgoing = false,
                type = MessageType.VOICE,
                voiceDurationSec = 34,
                voiceWaveform = listOf(0.2f, 0.5f, 0.8f, 0.4f, 0.9f, 0.7f, 0.3f, 0.6f, 0.5f, 0.2f)
            )
        )

        capture(
            name = "conversation-rich-messaging",
            chat = chat,
            messages = messages,
            state = themeState(TimeAtmospherePalette.DAY).apply { themeMode = AppThemeMode.DARK }
        )
        capture(
            name = "conversation-frosted-header",
            chat = chat,
            messages = messages,
            state = themeState(TimeAtmospherePalette.DAY).apply { themeMode = AppThemeMode.DARK }
        )
        capture(
            name = "conversation-dark",
            chat = chat,
            messages = messages,
            state = themeState(TimeAtmospherePalette.NIGHT).apply { themeMode = AppThemeMode.DARK }
        )
        capture(
            name = "conversation-header-liquid-glass",
            chat = chat,
            messages = messages,
            state = themeState(TimeAtmospherePalette.NIGHT).apply { themeMode = AppThemeMode.DARK }
        )
        capture(
            name = "conversation-light",
            chat = chat,
            messages = messages,
            state = themeState(TimeAtmospherePalette.DAY).apply { themeMode = AppThemeMode.LIGHT }
        )
        capture(
            name = "conversation-long-name",
            chat = chat.copy(
                title = "Professor Ishani Roy Venkataraghavan",
                directUser = directUser.copy(name = "Professor Ishani Roy Venkataraghavan")
            ),
            messages = messages,
            state = themeState(TimeAtmospherePalette.GOLDEN_HOUR).apply { themeMode = AppThemeMode.DARK }
        )
        capture(
            name = "conversation-font-scale-150",
            chat = chat,
            messages = messages,
            state = themeState(TimeAtmospherePalette.DAY).apply {
                themeMode = AppThemeMode.LIGHT
                fontScale = 1.5f
            }
        )
    }
}
