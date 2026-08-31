package com.foresightlabs.aether.data.telegram

import com.foresightlabs.aether.domain.text.LinkPreviewIntent
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * The TDLib side of Composer link previews.
 *
 * Both translations are asserted against the pinned `TdApi` types themselves:
 * what `linkPreview` means for the Composer, and what the Composer's intent
 * becomes on the way back out through the ordinary `inputMessageText` send path.
 */
@RunWith(AndroidJUnit4::class)
class LinkPreviewSupportTest {

    @Test
    fun telegramsPreviewBecomesWhatTheComposerShows() {
        val card = LinkPreviewSupport.cardOf(preview())

        assertNotNull(card)
        assertEquals("https://example.com/article", card!!.url)
        assertEquals("example.com", card.displayUrl)
        assertEquals("Example", card.siteName)
        assertEquals("An article", card.title)
        assertEquals("What it is about", card.description)
    }

    @Test
    fun aPreviewWithNoMediaHasNoThumbnail() {
        val card = LinkPreviewSupport.cardOf(preview(type = TdApi.LinkPreviewTypeArticle(null)))

        assertNotNull(card)
        assertTrue("A preview without media must not claim a thumbnail", !card!!.hasThumbnail)
        assertEquals(0, LinkPreviewSupport.thumbnailFileId(preview(type = TdApi.LinkPreviewTypeArticle(null))))
    }

    @Test
    fun theSmallestPhotoVariantIsUsedAsTheThumbnail() {
        val photo = TdApi.Photo(
            false,
            TdApi.Minithumbnail(20, 20, byteArrayOf(1, 2, 3)),
            arrayOf(
                photoSize("y", fileId = 90, width = 1280, height = 720),
                photoSize("s", fileId = 42, width = 90, height = 51)
            )
        )
        val preview = preview(type = TdApi.LinkPreviewTypeArticle(photo))

        assertEquals(42, LinkPreviewSupport.thumbnailFileId(preview))

        val card = LinkPreviewSupport.cardOf(preview) { file -> "/cache/${file?.id}.jpg" }
        assertEquals("/cache/42.jpg", card?.thumbnailPath)
        assertNotNull("Telegram's embedded thumbnail carries the card", card?.thumbnailBase64)
        assertTrue(card!!.hasThumbnail)
    }

    @Test
    fun videoAndPlayerPreviewsUseTheirOwnCover() {
        val cover = TdApi.Photo(false, null, arrayOf(photoSize("s", fileId = 7, width = 90, height = 51)))
        val video = preview(
            type = TdApi.LinkPreviewTypeVideo(TdApi.Video(), cover, 0)
        )
        assertEquals(7, LinkPreviewSupport.thumbnailFileId(video))

        val player = preview(
            type = TdApi.LinkPreviewTypeEmbeddedVideoPlayer("https://player.example.com", null, cover, 0, 320, 180)
        )
        assertEquals(7, LinkPreviewSupport.thumbnailFileId(player))
    }

    @Test
    fun nothingFromTelegramMeansNoCard() {
        assertNull(LinkPreviewSupport.cardOf(null))
        // A preview with no URL at all is not something the Composer can show.
        assertNull(LinkPreviewSupport.cardOf(preview(url = "", displayUrl = "")))
    }

    // -----------------------------------------------------------------------
    // The send path
    // -----------------------------------------------------------------------

    @Test
    fun theDefaultIntentSendsExactlyWhatItAlwaysDid() {
        assertNull(LinkPreviewSupport.optionsFor(LinkPreviewIntent.Default))
    }

    @Test
    fun aDismissedPreviewSendsWithPreviewsDisabled() {
        val options = LinkPreviewSupport.optionsFor(LinkPreviewIntent.Disabled)

        assertNotNull(options)
        assertTrue(options!!.isDisabled)
        assertEquals("", options.url)
    }

    @Test
    fun theShownPreviewIsNamedExplicitlyOnSend() {
        val options = LinkPreviewSupport.optionsFor(LinkPreviewIntent.Show("https://example.com/article"))

        assertNotNull(options)
        assertTrue(!options!!.isDisabled)
        assertEquals("https://example.com/article", options.url)
        assertTrue(!options.forceSmallMedia && !options.forceLargeMedia && !options.showAboveText)
    }

    @Test
    fun theOptionsRideTheOrdinaryTextMessageContent() {
        // The send path is unchanged apart from this field: one inputMessageText,
        // through the one sendMessage every outgoing message already uses.
        val options = LinkPreviewSupport.optionsFor(LinkPreviewIntent.Show("https://example.com"))
        val content = TdApi.InputMessageText(
            TdApi.FormattedText("read https://example.com", emptyArray()),
            options,
            true
        )

        assertEquals(TdApi.InputMessageText.CONSTRUCTOR, content.getConstructor())
        assertEquals("https://example.com", content.linkPreviewOptions?.url)
        assertTrue(content.clearDraft)
    }

    private fun preview(
        url: String = "https://example.com/article",
        displayUrl: String = "example.com",
        type: TdApi.LinkPreviewType = TdApi.LinkPreviewTypeArticle(null)
    ) = TdApi.LinkPreview(
        url,
        displayUrl,
        "Example",
        "An article",
        TdApi.FormattedText("What it is about", emptyArray()),
        "",
        type,
        false,
        false,
        false,
        false,
        false,
        0
    )

    private fun photoSize(type: String, fileId: Int, width: Int, height: Int) = TdApi.PhotoSize(
        type,
        TdApi.File(fileId, 0, 0, TdApi.LocalFile(), TdApi.RemoteFile()),
        width,
        height,
        intArrayOf()
    )
}
