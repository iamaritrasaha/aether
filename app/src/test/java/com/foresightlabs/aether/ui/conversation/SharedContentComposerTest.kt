package com.foresightlabs.aether.ui.conversation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.domain.sharing.SharedAttachmentKind
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

/**
 * A share once it reaches the conversation it was addressed to.
 *
 * Two things matter here. The review surface is the Curtain Aether already has,
 * in one more of its states -- not a dialog, a sheet, or a second surface. And
 * pressing send routes into the media paths that already exist, including the
 * album path for several photos, rather than into anything share-specific.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi", application = Application::class)
class SharedContentComposerTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // --- the review surface -------------------------------------------------

    @Test
    fun aSharedFileIsReviewedInsideTheOneCurtain() {
        showComposer(
            curtainState = CurtainState.SHARE_PREVIEW,
            pendingShare = PendingShare(
                listOf(SharedAttachmentFile("/tmp/report.pdf", SharedAttachmentKind.FILE, "report.pdf"))
            )
        )

        composeRule.onAllNodesWithTag(AetherCurtain.TestTag).assertCountEquals(1)
        composeRule.onNodeWithTag("curtain_share_preview_content").assertIsDisplayed()

        val curtain = boundsOf(AetherCurtain.TestTag)
        val preview = boundsOf("curtain_share_preview_content")
        assertTrue(
            "The review must be content of the Curtain, not a surface over it",
            preview.top >= curtain.top - 1f && preview.bottom <= curtain.bottom + 1f
        )
        val rootHeight = composeRule.onRoot().fetchSemanticsNode().size.height.toFloat()
        assertTrue("The Curtain must stay on the bottom edge", curtain.bottom >= rootHeight - 1f)
    }

    @Test
    fun aSharedFileIsNeverOfferedAsViewOnce() {
        showComposer(
            curtainState = CurtainState.SHARE_PREVIEW,
            pendingShare = PendingShare(
                listOf(SharedAttachmentFile("/tmp/photo.jpg", SharedAttachmentKind.IMAGE))
            )
        )

        // View once is a decision about a photo taken or picked here, and there
        // is no field on a shared item that could carry it.
        composeRule.onAllNodesWithTag("media_preview_view_once_toggle").assertCountEquals(0)
    }

    @Test
    fun theShareIsSentOnlyWhenTheSendControlIsPressed() {
        var sent = 0
        var cancelled = 0
        showComposer(
            curtainState = CurtainState.SHARE_PREVIEW,
            pendingShare = PendingShare(
                listOf(SharedAttachmentFile("/tmp/photo.jpg", SharedAttachmentKind.IMAGE))
            ),
            onSendShare = { sent++ },
            onCancelShare = { cancelled++ }
        )

        assertEquals("Presenting a share must not send it", 0, sent)
        composeRule.onNodeWithTag("share_preview_send").performClick()
        composeRule.waitForIdle()
        assertEquals(1, sent)
        assertEquals(0, cancelled)
    }

    @Test
    fun sharedTextArrivesInTheComposerAsADraft() {
        var reported: String? = null
        showComposer(prefillText = "Read this: https://example.com", onTextChanged = { reported = it })

        assertEquals("Read this: https://example.com", typedText())
        // Reported like typing, so the link preview asks Telegram about it.
        assertEquals("Read this: https://example.com", reported)
        composeRule.onNodeWithTag("send_message_button").assertIsDisplayed()
    }

    // --- the send routing ---------------------------------------------------

    @Test
    fun severalSharedPhotosGoThroughTheAlbumPath() {
        val recorder = Recorder()
        recorder.send(
            PendingShare(
                listOf(
                    SharedAttachmentFile("/tmp/a.jpg", SharedAttachmentKind.IMAGE),
                    SharedAttachmentFile("/tmp/b.jpg", SharedAttachmentKind.IMAGE)
                ),
                caption = "the trip"
            )
        )

        assertEquals(listOf(listOf("/tmp/a.jpg", "/tmp/b.jpg")), recorder.albums.map { it.first })
        assertEquals("the trip", recorder.albums.single().second)
        assertTrue("The album path replaces individual sends", recorder.photos.isEmpty())
    }

    @Test
    fun oneSharedPhotoGoesThroughTheOrdinaryPhotoSend() {
        val recorder = Recorder()
        recorder.send(
            PendingShare(listOf(SharedAttachmentFile("/tmp/a.jpg", SharedAttachmentKind.IMAGE)), caption = "hi")
        )

        assertEquals(1, recorder.photos.size)
        assertEquals("/tmp/a.jpg", recorder.photos.single().first)
        assertEquals("hi", recorder.photos.single().second)
        assertFalse("A share is never sent as view once", recorder.photos.single().third)
        assertTrue(recorder.albums.isEmpty())
    }

    @Test
    fun sharedVideosAndFilesUseTheirOwnExistingSends() {
        val recorder = Recorder()
        recorder.send(
            PendingShare(
                listOf(
                    SharedAttachmentFile("/tmp/clip.mp4", SharedAttachmentKind.VIDEO),
                    SharedAttachmentFile("/tmp/report.pdf", SharedAttachmentKind.FILE)
                ),
                caption = "notes"
            )
        )

        assertEquals(listOf("/tmp/clip.mp4"), recorder.videos.map { it.first })
        assertEquals(listOf("/tmp/report.pdf"), recorder.documents.map { it.first })
        // Telegram captions a group from its first member.
        assertEquals("notes", recorder.videos.single().second)
        assertEquals("", recorder.documents.single().second)
        assertTrue(recorder.albums.isEmpty())
    }

    @Test
    fun anEmptyShareSendsNothing() {
        val recorder = Recorder()
        recorder.send(PendingShare(emptyList()))

        assertTrue(recorder.photos.isEmpty() && recorder.videos.isEmpty())
        assertTrue(recorder.documents.isEmpty() && recorder.albums.isEmpty())
    }

    // --- helpers ------------------------------------------------------------

    private class Recorder {
        val photos = mutableListOf<Triple<String, String, Boolean>>()
        val videos = mutableListOf<Pair<String, String>>()
        val documents = mutableListOf<Pair<String, String>>()
        val albums = mutableListOf<Pair<List<String>, String>>()

        fun send(share: PendingShare) = sendSharedAttachments(
            share = share,
            replyingTo = null,
            onSendPhoto = { path, caption, _, viewOnce -> photos += Triple(path, caption, viewOnce) },
            onSendVideo = { path, caption, _, _, _ -> videos += path to caption },
            onSendDocument = { path, caption, _ -> documents += path to caption },
            onSendPhotoAlbum = { paths, caption, _ -> albums += paths to caption }
        )
    }

    private fun boundsOf(tag: String): Rect =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun typedText(): String =
        composeRule.onNodeWithTag("message_input_field", useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsProperties.EditableText]
            .text

    private fun showComposer(
        curtainState: CurtainState = CurtainState.COMPOSER,
        pendingShare: PendingShare? = null,
        prefillText: String? = null,
        onSendShare: () -> Unit = {},
        onCancelShare: () -> Unit = {},
        onTextChanged: (String) -> Unit = {}
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
                        MessageComposer(
                            replyingTo = null,
                            onDismissReply = {},
                            onSendMessage = { _, _ -> },
                            curtainState = curtainState,
                            pendingShare = pendingShare,
                            prefillText = prefillText,
                            onSendPendingShare = onSendShare,
                            onCancelPendingShare = onCancelShare,
                            onTextChanged = onTextChanged,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }
}
