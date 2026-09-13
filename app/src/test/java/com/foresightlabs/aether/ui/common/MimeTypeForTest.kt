package com.foresightlabs.aether.ui.common

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The saved file's MediaStore/DocumentsUI MIME entry must match its real
 * bytes: a hardcoded image/jpeg mislabeled every PNG/WebP photo, which then
 * mis-handles them in other apps. DocumentsUI uses the suggested name +
 * MIME pair when creating the file, so both must be consistent.
 */
@RunWith(RobolectricTestRunner::class)
class MimeTypeForTest {

    @Test
    fun realExtensionsMapToTheirRealMimes() {
        assertEquals("image/png", mimeTypeFor("photo.png", isVideo = false))
        assertEquals("image/webp", mimeTypeFor("photo.webp", isVideo = false))
        assertEquals("image/jpeg", mimeTypeFor("photo.jpg", isVideo = false))
        assertEquals("video/mp4", mimeTypeFor("clip.mp4", isVideo = true))
        assertEquals("application/pdf", mimeTypeFor("report.pdf", isVideo = false))
        assertEquals("audio/ogg", mimeTypeFor("voice.ogg", isVideo = false))
    }

    @Test
    fun unknownExtensionsFallBackToTheBroadViewerType() {
        assertEquals("video/mp4", mimeTypeFor("clip.unknownext", isVideo = true))
        assertEquals("image/jpeg", mimeTypeFor("photo.unknownext", isVideo = false))
    }

    @Test
    fun extensionCaseIsIgnoredAndMissingExtensionFallsBack() {
        assertEquals("image/png", mimeTypeFor("photo.PNG", isVideo = false))
        assertEquals("image/jpeg", mimeTypeFor("noextension", isVideo = false))
    }
}
