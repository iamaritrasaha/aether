package com.foresightlabs.aether.ui.conversation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.MessageStatus
import com.foresightlabs.aether.domain.model.MessageType
import com.foresightlabs.aether.domain.model.ReplyPreview
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

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi", application = Application::class)
class MessageBubbleMeasurementTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun renderBubbleInWidth(widthDp: Dp, message: Message) {
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
                    Box(modifier = Modifier.requiredWidth(widthDp)) {
                        MessageBubble(
                            message = message,
                            onSwipeToReply = {},
                            onLongPress = {},
                            onMediaClick = {},
                            onReactionClick = { _, _ -> },
                            audioPlayback = com.foresightlabs.aether.ui.conversation.AudioPlaybackController(
                                androidx.test.core.app.ApplicationProvider.getApplicationContext()
                            )
                        )
                    }
                }
            }
        }
    }

    @Test
    fun shortOutgoingTextBubbleRemainsCompact() {
        val shortMessage = Message(
            id = "short_out",
            chatId = "100",
            senderId = "200",
            senderName = "Me",
            text = "Okay",
            timestamp = "12:34 PM",
            isOutgoing = true,
            status = MessageStatus.READ,
            type = MessageType.TEXT
        )

        renderBubbleInWidth(393.dp, shortMessage)

        val bounds = composeRule.onNodeWithTag("message_bubble_short_out").getUnclippedBoundsInRoot()
        val width = bounds.right - bounds.left
        assertTrue("Short text bubble width ($width) must remain compact (< 150dp)", width < 150.dp)
    }

    @Test
    fun longOutgoingTextBubbleIsWiderThanLegacyCap() {
        val longText = "This is a longer outgoing message designed to verify that the message bubble uses the available conversation width efficiently on a 393dp screen rather than wrapping at the legacy 258dp limit."
        val longMessage = Message(
            id = "long_out",
            chatId = "100",
            senderId = "200",
            senderName = "Me",
            text = longText,
            timestamp = "12:35 PM",
            isOutgoing = true,
            status = MessageStatus.READ,
            type = MessageType.TEXT
        )

        renderBubbleInWidth(393.dp, longMessage)

        val bounds = composeRule.onNodeWithTag("message_bubble_long_out").getUnclippedBoundsInRoot()
        val width = bounds.right - bounds.left
        assertTrue("Long text bubble width ($width) must exceed legacy 258dp cap", width > 275.dp)
        assertTrue("Long text bubble width ($width) must stay below total screen width (393dp)", width < 393.dp)
    }

    @Test
    fun longIncomingTextBubbleIsWiderThanLegacyCap() {
        val longText = "This is a longer incoming message designed to verify that incoming messages also benefit from the wider responsive layout instead of being trapped in a narrow bubble."
        val longMessage = Message(
            id = "long_in",
            chatId = "100",
            senderId = "101",
            senderName = "Alice",
            text = longText,
            timestamp = "12:36 PM",
            isOutgoing = false,
            status = MessageStatus.READ,
            type = MessageType.TEXT
        )

        renderBubbleInWidth(393.dp, longMessage)

        val bounds = composeRule.onNodeWithTag("message_bubble_long_in").getUnclippedBoundsInRoot()
        val width = bounds.right - bounds.left
        assertTrue("Long incoming text bubble width ($width) must exceed legacy 258dp cap", width > 275.dp)
        assertTrue("Long incoming text bubble width ($width) must stay below total screen width (393dp)", width < 393.dp)
    }

    @Test
    fun replyPreviewSupportsWiderBubbleWithoutFullWidthExpansion() {
        val replyMessage = Message(
            id = "reply_msg",
            chatId = "100",
            senderId = "200",
            senderName = "Me",
            text = "Detailed response to a long question.",
            timestamp = "12:37 PM",
            isOutgoing = true,
            status = MessageStatus.READ,
            type = MessageType.TEXT,
            replyPreview = ReplyPreview(
                messageId = 10L,
                chatId = 100L,
                senderName = "Alice",
                text = "This is the original question snippet that used to be constrained to 240dp max width.",
                isAvailable = true,
                isNavigable = true
            )
        )

        renderBubbleInWidth(393.dp, replyMessage)

        val bounds = composeRule.onNodeWithTag("message_bubble_reply_msg").getUnclippedBoundsInRoot()
        val width = bounds.right - bounds.left
        assertTrue("Reply bubble width ($width) must exceed legacy 240dp restriction", width > 250.dp)
        assertTrue("Reply bubble width ($width) must remain below 393dp screen width", width < 393.dp)
    }
}
