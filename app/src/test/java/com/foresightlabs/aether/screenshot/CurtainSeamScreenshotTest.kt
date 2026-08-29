package com.foresightlabs.aether.screenshot

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ChatType
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.MessageStatus
import com.foresightlabs.aether.domain.model.Presence
import com.foresightlabs.aether.domain.model.User
import com.foresightlabs.aether.ui.conversation.ConversationScreen
import com.foresightlabs.aether.ui.conversation.CurtainState
import com.foresightlabs.aether.ui.conversation.MessageComposer
import com.foresightlabs.aether.ui.theme.AetherTheme
import com.foresightlabs.aether.ui.theme.AppThemeState
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
import kotlin.math.abs

/**
 * The seam and surface-count checks, run against actual pixels.
 *
 * The composition tests hold the layout tree; these hold what the tree renders,
 * because the defects this architecture keeps re-acquiring are visual: a second
 * dark rectangle inside the bottom region, and a rounded top edge that makes the
 * Curtain read as a sheet over the conversation. Both are legible in the pixels
 * of two scanlines, so they are asserted there and the frames are written out for
 * a human to look at as well.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi", application = Application::class)
class CurtainSeamScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val outputDir = File("build/reports/aether-screenshots").apply { mkdirs() }

    @Test
    fun curtainComposer() {
        show()
        assertCurtainIsOneFlatSurface("curtain-composer")
    }

    @Test
    fun curtainAttachments() {
        show()
        composeRule.onNodeWithTag("attachment_button").performClick()
        composeRule.waitForIdle()
        assertCurtainIsOneFlatSurface("curtain-attachments")
    }

    /**
     * Forwarding is the state that most often came back as a bottom sheet, so its
     * top edge is worth a frame of its own. Driven through the Curtain directly:
     * the defect being guarded against is a rounded sheet corner drawn by the
     * forwarding state, which needs no conversation behind it to be visible.
     */
    @Test
    fun curtainForwarding() {
        showCurtainForwarding()
        assertCurtainIsOneFlatSurface("curtain-forwarding", withForeground = false)
    }

    /**
     * Two things are read off the frame.
     *
     * Across the Curtain's own top scanline, the colour has to be uniform edge to
     * edge. A rounded top edge leaves the outer pixels showing whatever is behind
     * it while the middle shows the surface, and an inset feature card leaves a
     * margin of one colour around a block of another — so both defects break
     * uniformity on exactly that line.
     *
     * Down the Curtain's vertical centre, no second large block of a different
     * tone may appear: that is the "panel inside a panel" defect, counted rather
     * than eyeballed.
     */
    private fun assertCurtainIsOneFlatSurface(name: String, withForeground: Boolean = true) {
        val frame = capture(name)
        val curtain = composeRule.onNodeWithTag("conversation_curtain", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot

        val top = curtain.top.toInt().coerceIn(0, frame.height - 1)
        val bottom = (curtain.bottom.toInt() - 1).coerceIn(0, frame.height - 1)
        val left = curtain.left.toInt().coerceIn(0, frame.width - 1)
        val right = (curtain.right.toInt() - 1).coerceIn(0, frame.width - 1)

        // 1. The Curtain begins where the conversation ends.
        if (withForeground) {
            val foreground = composeRule
                .onNodeWithTag("conversation_foreground", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            assertTrue(
                "$name: the Curtain starts ${foreground.bottom - curtain.top}px inside " +
                    "the conversation, so it is drawn over it",
                curtain.top >= foreground.bottom - 1f
            )
        }

        // 2. Its first scanline is one colour: no sheet corners, no inset card.
        val reference = frame.getPixel((left + right) / 2, top + 1)
        val cornerSamples = listOf(left + 1, left + 6, right - 6, right - 1)
        cornerSamples.forEach { x ->
            val pixel = frame.getPixel(x, top + 1)
            assertTrue(
                "$name: the Curtain's top edge is not flat — x=$x reads " +
                    "${hex(pixel)} where the middle reads ${hex(reference)}. That is " +
                    "either a rounded sheet corner or an inset feature card.",
                near(pixel, reference)
            )
        }

        // 3. Its lower edge reaches the bottom of the frame.
        assertTrue(
            "$name: the Curtain does not reach the bottom edge",
            near(frame.getPixel((left + right) / 2, frame.height - 2), reference)
        )

        // 4. Down both outer margins, one unbroken surface. Controls — icon
        // circles, the input, a search field — are inset from the edge, so only a
        // second feature-sized background reaches these columns, whether it runs
        // full width or leaves a margin of its own around itself.
        listOf(left + 2, right - 2).forEach { probeX ->
            val foreign = (top + 1..bottom).count {
                !near(frame.getPixel(probeX, it), reference, tolerance = 16)
            }
            assertTrue(
                "$name: $foreign of ${bottom - top}px down the Curtain's edge at " +
                    "x=$probeX are a different tone. A second feature-sized surface " +
                    "inside the Curtain is exactly what that looks like.",
                foreign == 0
            )
        }
    }

    private fun near(a: Int, b: Int, tolerance: Int = 10): Boolean {
        fun channel(v: Int, shift: Int) = (v shr shift) and 0xFF
        return abs(channel(a, 16) - channel(b, 16)) <= tolerance &&
            abs(channel(a, 8) - channel(b, 8)) <= tolerance &&
            abs(channel(a, 0) - channel(b, 0)) <= tolerance
    }

    private fun hex(pixel: Int) = "#%08X".format(pixel)

    private fun capture(name: String): Bitmap {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        val view = composeRule.activity.window.decorView
        require(view.width > 0 && view.height > 0) {
            "decor view was not laid out (${view.width}x${view.height})"
        }
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File(outputDir, "$name.png").outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return bitmap
    }

    private fun showCurtainForwarding() {
        val theme = theme()
        composeRule.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalAppThemeState provides theme
            ) {
                AetherTheme(themeState = theme) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF2A1F3D))
                    ) {
                        MessageComposer(
                            replyingTo = null,
                            onDismissReply = {},
                            onSendMessage = { _, _ -> },
                            curtainState = CurtainState.FORWARDING,
                            forwardMessages = messages.take(1),
                            forwardTargets = listOf(chat),
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun theme() = AppThemeState().apply {
        atmosphereMode = AtmosphereMode.MANUAL
        manualAtmosphere = TimeAtmospherePalette.DAY
    }

    private fun show() {
        val theme = theme()
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
                        onVisibleMessages = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private val user = User(
        id = "103",
        name = "Ishani Roy",
        username = "ishani",
        avatarInitials = "IR",
        avatarGradient = listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)),
        phone = "+1 555 0103",
        presence = Presence.ONLINE
    )

    private val chat = Chat(
        id = "103",
        title = "Ishani Roy",
        type = ChatType.DIRECT,
        lastMessageText = "See you at eight",
        lastMessageTime = "10:42 AM",
        avatarInitials = "IR",
        avatarGradient = listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)),
        directUser = user
    )

    private val messages = listOf(
        Message(
            id = "1",
            chatId = "103",
            senderId = "103",
            senderName = "Ishani Roy",
            text = "See you at eight",
            timestamp = "10:41 AM",
            isOutgoing = false,
            status = MessageStatus.SENT
        ),
        Message(
            id = "2",
            chatId = "103",
            senderId = "me",
            senderName = "You",
            text = "Perfect",
            timestamp = "10:42 AM",
            isOutgoing = true,
            status = MessageStatus.READ
        )
    )
}
