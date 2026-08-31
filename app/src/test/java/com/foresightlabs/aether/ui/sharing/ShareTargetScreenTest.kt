package com.foresightlabs.aether.ui.sharing

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ChatType
import com.foresightlabs.aether.domain.model.User
import com.foresightlabs.aether.domain.sharing.SharedAttachment
import com.foresightlabs.aether.domain.sharing.SharedAttachmentKind
import com.foresightlabs.aether.domain.sharing.SharedContent
import com.foresightlabs.aether.ui.conversation.AetherCurtain
import com.foresightlabs.aether.ui.theme.AetherTheme
import com.foresightlabs.aether.ui.theme.AppThemeState
import com.foresightlabs.aether.ui.theme.AtmosphereMode
import com.foresightlabs.aether.ui.theme.LocalAppThemeState
import com.foresightlabs.aether.ui.theme.TimeAtmospherePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Recipient selection for an incoming share.
 *
 * The architectural claims, not the pixels: one Curtain and it is the bottom
 * surface, the recipient list is inside it, what is being shared is stated, and
 * nothing is sent from here -- choosing someone reports the choice and no more.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi", application = Application::class)
class ShareTargetScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theRecipientListLivesInTheOneCurtain() {
        showShare(SharedContent.Text("https://example.com/article"))

        composeRule.onAllNodesWithTag(AetherCurtain.TestTag).assertCountEquals(1)
        composeRule.onNodeWithTag(ShareTargetTags.Search).assertIsDisplayed()
        composeRule.onNodeWithTag(ShareTargetTags.Submit).assertIsDisplayed()

        val curtain = boundsOf(AetherCurtain.TestTag)
        val list = boundsOf(ShareTargetTags.target("1"))
        assertTrue(
            "Recipients must be content of the Curtain, not a surface of their own",
            list.top >= curtain.top - 1f && list.bottom <= curtain.bottom + 1f
        )
        val rootHeight = composeRule.onRoot().fetchSemanticsNode().size.height.toFloat()
        assertTrue(
            "The Curtain must stay anchored to the bottom edge",
            curtain.bottom >= rootHeight - 1f
        )
    }

    @Test
    fun whatIsBeingSharedIsStatedAboveTheCurtain() {
        showShare(SharedContent.Text("https://example.com/article"))

        composeRule.onNodeWithTag(ShareTargetTags.Preview).assertIsDisplayed()
        val preview = boundsOf(ShareTargetTags.Preview)
        val curtain = boundsOf(AetherCurtain.TestTag)
        assertTrue(
            "The share preview belongs above the Curtain, not inside a second panel",
            preview.bottom <= curtain.top + 1f
        )
    }

    @Test
    fun choosingARecipientReportsTheChoiceAndSendsNothing() {
        var chosen: Chat? = null
        showShare(SharedContent.Text("hello"), onChoose = { chosen = it })

        // Nothing is chosen yet, so the confirmation cannot commit anything.
        composeRule.onNodeWithTag(ShareTargetTags.Submit).performClick()
        composeRule.waitForIdle()
        assertNull("Nothing may be addressed before a recipient is picked", chosen)

        composeRule.onNodeWithTag(ShareTargetTags.target("1")).performClick()
        composeRule.onNodeWithTag(ShareTargetTags.Submit).performClick()
        composeRule.waitForIdle()

        assertEquals("1", chosen?.id)
    }

    @Test
    fun aMultipleShareIsDescribedAsWhatItIs() {
        showShare(
            SharedContent.Attachments(
                items = listOf(
                    SharedAttachment("content://media/1", SharedAttachmentKind.IMAGE, "image/jpeg"),
                    SharedAttachment("content://media/2", SharedAttachmentKind.IMAGE, "image/jpeg")
                ),
                caption = "from the trip"
            )
        )

        composeRule.onNodeWithTag(ShareTargetTags.Preview).assertIsDisplayed()
        composeRule.onAllNodesWithTag(AetherCurtain.TestTag).assertCountEquals(1)
    }

    @Test
    fun dismissingIsAvailableWithoutChoosingAnyone() {
        var dismissed = false
        showShare(SharedContent.Text("hello"), onDismiss = { dismissed = true })

        composeRule.onNodeWithContentDescriptionSafely("Cancel sharing").performClick()
        composeRule.waitForIdle()

        assertTrue(dismissed)
    }

    // --- helpers ------------------------------------------------------------

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onNodeWithContentDescriptionSafely(
        description: String
    ) = onNode(
        androidx.compose.ui.test.hasContentDescription(description),
        useUnmergedTree = true
    )

    private fun boundsOf(tag: String): Rect =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun showShare(
        content: SharedContent,
        onChoose: (Chat) -> Unit = {},
        onDismiss: () -> Unit = {}
    ) {
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
                    Box(modifier = Modifier.fillMaxSize()) {
                        ShareTargetScreen(
                            content = content,
                            targets = targets,
                            onDismiss = onDismiss,
                            onChooseRecipient = onChoose
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private val targets = listOf(
        chat("1", "Ada"),
        chat("2", "Grace")
    )

    private fun chat(id: String, name: String) = Chat(
        id = id,
        title = name,
        type = ChatType.DIRECT,
        lastMessageText = "",
        lastMessageTime = "",
        avatarInitials = name.take(1),
        avatarGradient = listOf(Color(0xFF4F46E5), Color(0xFF7C3AED)),
        directUser = User(
            id = id,
            name = name,
            username = name.lowercase(),
            avatarInitials = name.take(1),
            avatarGradient = listOf(Color(0xFF4F46E5), Color(0xFF7C3AED)),
            phone = "+1 555 010$id"
        ),
        blockableUserId = id.toLong()
    )
}
