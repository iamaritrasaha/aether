package com.foresightlabs.aether.ui.design
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.ui.conversation.AttachmentSheet
import com.foresightlabs.aether.ui.conversation.MessageComposer
import com.foresightlabs.aether.ui.design.AetherGlassTokens
import com.foresightlabs.aether.ui.design.AetherNavItem
import com.foresightlabs.aether.ui.design.AetherNavPill
import com.foresightlabs.aether.ui.theme.AetherTheme
import com.foresightlabs.aether.ui.theme.AppThemeState
import com.foresightlabs.aether.ui.theme.LocalAppThemeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi", application = Application::class)
class AetherGlassSystemTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val navItems = listOf(
        AetherNavItem("chats", Icons.Default.ChatBubble, "Chats") {},
        AetherNavItem("pulse", Icons.Default.AutoAwesome, "Pulse") {},
        AetherNavItem("calls", Icons.Default.Call, "Calls") {},
        AetherNavItem("settings", Icons.Default.Settings, "Settings") {}
    )

    @Test
    fun glassTokens_enforceSingleMaterialInvariants() {
        // Blur radius must be canonical 16dp (low-blur translucency)
        assertEquals(16.dp, AetherGlassTokens.BlurRadius)
        // Noise factor must be 0.02f
        assertEquals(0.02f, AetherGlassTokens.NoiseFactor, 0.001f)
        // Base glass color must be strictly transparent (no white/black/color overlay)
        assertEquals(Color.Transparent, AetherGlassTokens.BaseGlassColor)
        // Specular & Border brushes must be non-null and defined
        assertNotNull(AetherGlassTokens.SpecularBrush)
        assertNotNull(AetherGlassTokens.BorderBrush)
        // Canonical radii
        assertEquals(24.dp, AetherGlassTokens.BarRadius)
        assertEquals(28.dp, AetherGlassTokens.DockRadius)
        assertEquals(18.dp, AetherGlassTokens.PopupRadius)
        assertEquals(28.dp, AetherGlassTokens.SheetRadius)
        assertEquals(14.dp, AetherGlassTokens.ControlRadius)
    }

    @Test
    fun navPill_rendersNeutralGlassDockAndLens() {
        val themeState = AppThemeState()
        composeRule.setContent {
            CompositionLocalProvider(LocalAppThemeState provides themeState) {
                AetherTheme(themeState = themeState) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AetherNavPill(
                            items = navItems,
                            selectedKey = "chats",
                            modifier = Modifier
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("nav_slot_chats", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("nav_lens_chats", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("nav_icon_chats", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun messageComposer_rendersCanonicalGlassDock() {
        val themeState = AppThemeState()
        composeRule.setContent {
            CompositionLocalProvider(LocalAppThemeState provides themeState) {
                AetherTheme(themeState = themeState) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        MessageComposer(
                            onSendMessage = { _, _ -> },
                            onOpenAttachmentSheet = {},
                            onVoiceNoteRecorded = {},
                            replyingTo = null,
                            onDismissReply = {}
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("message_input_field").assertIsDisplayed()
        composeRule.onNodeWithTag("attachment_button").assertIsDisplayed()
        composeRule.onNodeWithTag("sticker_button").assertIsDisplayed()
        composeRule.onNodeWithTag("voice_record_button").assertIsDisplayed()
    }

    @Test
    fun attachmentSheet_rendersUnifiedNeutralGlassSheetAndCircles() {
        val themeState = AppThemeState()
        composeRule.setContent {
            CompositionLocalProvider(LocalAppThemeState provides themeState) {
                AetherTheme(themeState = themeState) {
                    AttachmentSheet(
                        isVisible = true,
                        onDismiss = {},
                        onOptionSelected = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("attachment_sheet_content").assertIsDisplayed()
        composeRule.onNodeWithTag("attachment_gallery").assertIsDisplayed()
        composeRule.onNodeWithTag("attachment_file").assertIsDisplayed()
        composeRule.onNodeWithTag("attachment_location").assertIsDisplayed()
    }
}
