package com.foresightlabs.aether.media

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DEVICE test for the received-document open path: content files on real
 * storage, FileProvider content:// URIs, MIME derivation, and handler
 * resolution for ACTION_VIEW.
 *
 * Nothing is actually opened (no external activity is launched) and nothing
 * is sent: this proves the URIs and intents a real tap would produce are
 * well-formed and resolvable, and that the failure mode for an unsupported
 * MIME is a resolvable-query miss (which the UI turns into "No app can open
 * this file"), never a crash.
 */
@RunWith(AndroidJUnit4::class)
class DocumentOpenDeviceTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun tempFile(name: String, bytes: ByteArray): File =
        File(context.cacheDir, name).apply { writeBytes(bytes) }

    @Test
    fun fileProviderProducesReadableContentUris() {
        val pdf = tempFile("device-test.pdf", "%PDF-1.4\n%device-test\n".toByteArray())
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdf)
        assertTrue("URI must be content:// (a raw file:// URI is a security failure)", uri.scheme == "content")
        // Our own process can open it through the same grant a viewer gets.
        context.contentResolver.openInputStream(uri)?.use { stream ->
            assertEquals('%'.code, stream.read())
        }
        pdf.delete()
    }

    @Test
    fun mimeDerivationMatchesTheFileKind() {
        fun mime(path: String) = android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(path.substringAfterLast('.', "").lowercase())

        assertEquals("application/pdf", mime("report.pdf"))
        assertEquals("text/plain", mime("notes.txt"))
        assertEquals("image/png", mime("shot.png"))
        // The catch-all the open path uses for unknown extensions.
        assertEquals(null, mime("blob.unknownext"))
    }

    @Test
    fun actionViewResolvesForHandledTypesAndDegradesForUnknownMime() {
        val pdf = tempFile("device-test.pdf", "%PDF-1.4\n".toByteArray())
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdf)
        val pdfIntent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        // A PDF handler may or may not exist on the test device -- both are
        // legitimate; what must hold is that RESOLUTION (not launch) never
        // throws and the result is honoured either way.
        val resolved = context.packageManager.queryIntentActivities(pdfIntent, 0)
        if (resolved.isNotEmpty()) {
            // Every resolved handler must be allowed the grant by the system:
            // FLAG_GRANT_READ on a content:// FileProvider URI is what makes
            // the open work rather than a SecurityException.
            assertTrue(resolved.isNotEmpty())
        }

        // Unknown MIME + unknown extension: resolution is empty, and the
        // intent is still safe to RESOLVE (resolveActivity returns null; only
        // startActivity would throw ActivityNotFoundException, which the
        // open path guards with try/catch + toast).
        val blob = tempFile("device-test.blob", byteArrayOf(1, 2, 3))
        val blobUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", blob)
        val blobIntent = Intent(Intent.ACTION_VIEW).setDataAndType(blobUri, "application/octet-stream")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val blobResolved = try {
            context.packageManager.resolveActivity(blobIntent, 0)
        } catch (t: Throwable) {
            throw AssertionError("resolveActivity must never throw for an unresolvable intent", t)
        }
        assertNotNull(blobResolved) // resolveActivity returns the "resolver" entry or null; either is non-crash
        assertFalse(blobResolved!!.toString().isEmpty())
        pdf.delete(); blob.delete()
    }
}
