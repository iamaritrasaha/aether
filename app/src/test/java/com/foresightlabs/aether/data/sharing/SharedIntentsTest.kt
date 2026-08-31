package com.foresightlabs.aether.data.sharing

import android.content.ClipData
import android.content.ClipDescription
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.domain.sharing.SharedAttachmentKind
import com.foresightlabs.aether.domain.sharing.SharedContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Normalizing what the Android Sharesheet hands over.
 *
 * A share is another application's data, so these cover the shapes Aether has no
 * control over as well as the well-formed ones: a declared type that disagrees
 * with the actual file, a stream that was never attached, an action that is not a
 * share at all, and the same Intent arriving twice because the Activity was
 * recreated.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SharedIntentsTest {

    // --- ACTION_SEND --------------------------------------------------------

    @Test
    fun plainTextBecomesSharedText() {
        val content = SharedIntents.normalize(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "something worth saying")
            }
        )

        assertEquals(SharedContent.Text("something worth saying"), content)
        assertNull((content as SharedContent.Text).link)
    }

    @Test
    fun aSharedUrlIsTextThatCarriesALink() {
        val content = SharedIntents.normalize(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Example page")
                putExtra(Intent.EXTRA_TEXT, "Read this: https://example.com/article")
            }
        ) as SharedContent.Text

        // The URL is preserved exactly as shared -- Telegram previews it later.
        assertEquals("Read this: https://example.com/article", content.text)
        assertEquals("https://example.com/article", content.link)
    }

    @Test
    fun aSubjectOnlyShareStillCarriesItsText() {
        val content = SharedIntents.normalize(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Just the subject")
            }
        )

        assertEquals(SharedContent.Text("Just the subject"), content)
    }

    @Test
    fun anImageStreamBecomesAnImageAttachment() {
        val uri = Uri.parse("content://media/external/images/media/42")
        val content = SharedIntents.normalize(
            Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
            }
        ) as SharedContent.Attachments

        val item = content.items.single()
        assertEquals(uri.toString(), item.uri)
        assertEquals(SharedAttachmentKind.IMAGE, item.kind)
        assertFalse(content.isMultiple)
    }

    @Test
    fun aVideoStreamBecomesAVideoAttachment() {
        val content = SharedIntents.normalize(
            Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, Uri.parse("content://media/external/video/media/7"))
            }
        ) as SharedContent.Attachments

        assertEquals(SharedAttachmentKind.VIDEO, content.items.single().kind)
    }

    @Test
    fun anyOtherMimeTypeBecomesAFile() {
        val content = SharedIntents.normalize(
            Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, Uri.parse("content://docs/1"))
            }
        ) as SharedContent.Attachments

        assertEquals(SharedAttachmentKind.FILE, content.items.single().kind)
    }

    @Test
    fun theResolversMimeTypeOutranksTheDeclaredOne() {
        val uri = Uri.parse("content://provider/opaque")
        val content = SharedIntents.normalize(
            intent = Intent(Intent.ACTION_SEND).apply {
                // A sender that declares a wildcard but hands over a photo.
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
            },
            mimeTypeOf = { "image/png" },
            displayNameOf = { "sunset.png" }
        ) as SharedContent.Attachments

        val item = content.items.single()
        assertEquals(SharedAttachmentKind.IMAGE, item.kind)
        assertEquals("image/png", item.mimeType)
        assertEquals("sunset.png", item.name)
    }

    @Test
    fun textSharedAlongsideAStreamBecomesTheCaption() {
        val content = SharedIntents.normalize(
            Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, Uri.parse("content://media/1"))
                putExtra(Intent.EXTRA_TEXT, "  from the roof  ")
            }
        ) as SharedContent.Attachments

        assertEquals("from the roof", content.caption)
    }

    // --- ACTION_SEND_MULTIPLE ----------------------------------------------

    @Test
    fun severalImagesBecomeSeveralAttachments() {
        val uris = arrayListOf(
            Uri.parse("content://media/external/images/media/1"),
            Uri.parse("content://media/external/images/media/2"),
            Uri.parse("content://media/external/images/media/3")
        )
        val content = SharedIntents.normalize(
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        ) as SharedContent.Attachments

        assertEquals(3, content.items.size)
        assertTrue(content.isMultiple)
        assertTrue(content.items.all { it.kind == SharedAttachmentKind.IMAGE })
    }

    @Test
    fun severalFilesOfMixedKindsKeepTheirOwnKinds() {
        val content = SharedIntents.normalize(
            intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(
                    Intent.EXTRA_STREAM,
                    arrayListOf(Uri.parse("content://a/video"), Uri.parse("content://a/doc"))
                )
            },
            mimeTypeOf = { uri -> if (uri.toString().endsWith("video")) "video/mp4" else "application/zip" }
        ) as SharedContent.Attachments

        assertEquals(
            listOf(SharedAttachmentKind.VIDEO, SharedAttachmentKind.FILE),
            content.items.map { it.kind }
        )
    }

    // --- shapes Aether does not control ------------------------------------

    @Test
    fun aNonShareIntentIsNotAShare() {
        assertNull(SharedIntents.normalize(Intent(Intent.ACTION_VIEW)))
        assertNull(SharedIntents.normalize(Intent(Intent.ACTION_MAIN)))
        assertNull(SharedIntents.normalize(null))
        assertFalse(SharedIntents.isShare(Intent(Intent.ACTION_VIEW)))
    }

    @Test
    fun aShareWithNothingInItIsNotDeliverable() {
        assertNull(SharedIntents.normalize(Intent(Intent.ACTION_SEND).apply { type = "image/jpeg" }))
        assertNull(
            SharedIntents.normalize(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "   ")
                }
            )
        )
        assertNull(SharedIntents.normalize(Intent(Intent.ACTION_SEND_MULTIPLE).apply { type = "image/*" }))
    }

    @Test
    fun aStreamlessShareThatStillHasTextFallsBackToTheText() {
        // EXTRA_STREAM missing while the type claims media: the text is what
        // actually arrived, so that is what Aether carries.
        val content = SharedIntents.normalize(
            Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_TEXT, "the photo did not come through")
            }
        )

        assertTrue(content is SharedContent.Text)
    }

    @Test
    fun aClipDataOnlyShareIsStillAShare() {
        val uri = Uri.parse("content://provider/clip-only")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            clipData = ClipData(
                ClipDescription("shared", arrayOf("image/png")),
                ClipData.Item(uri)
            )
        }

        val content = SharedIntents.normalize(intent) as SharedContent.Attachments
        assertEquals(uri.toString(), content.items.single().uri)
    }

    @Test
    fun anUnreadableUriIsStillNormalizedAndFailsLaterInstead() {
        // Nothing here opens the URI, so a stream this process cannot read
        // normalizes cleanly; the failure surfaces when its bytes are needed.
        val content = SharedIntents.normalize(
            intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, Uri.parse("content://revoked/999"))
            },
            mimeTypeOf = { null }
        )

        assertNotNull(content)
        assertEquals(SharedAttachmentKind.IMAGE, content!!.attachments.single().kind)
    }

    // --- identity -----------------------------------------------------------

    @Test
    fun theSameShareHasTheSameIdentityAcrossRecreation() {
        fun share() = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, Uri.parse("content://media/9"))
            putExtra(Intent.EXTRA_TEXT, "look")
        }

        assertEquals(SharedIntents.identityOf(share()), SharedIntents.identityOf(share()))
    }

    @Test
    fun differentSharesHaveDifferentIdentities() {
        val first = SharedIntents.identityOf(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "one")
            }
        )
        val second = SharedIntents.identityOf(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "two")
            }
        )

        assertTrue(first != second)
        assertNull(SharedIntents.identityOf(Intent(Intent.ACTION_VIEW)))
    }
}
