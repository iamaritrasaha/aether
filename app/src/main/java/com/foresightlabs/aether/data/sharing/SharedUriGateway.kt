package com.foresightlabs.aether.data.sharing

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.foresightlabs.aether.domain.sharing.SharedAttachment
import com.foresightlabs.aether.domain.sharing.SharedAttachmentKind
import java.io.File
import java.io.FileOutputStream

/**
 * One shared attachment, once its bytes are actually in Aether's hands.
 *
 * [path] is a real file in the cache directory, which is what the Telegram send
 * path needs; the `content://` URI it came from is not a filesystem path and was
 * never treated as one.
 */
data class MaterializedAttachment(
    val path: String,
    val kind: SharedAttachmentKind,
    val name: String? = null
)

/**
 * Everything Aether does with a `content://` URI another application shared.
 *
 * The rules a share imposes, in one place:
 *  - the URI is opened through the content resolver, never resolved to a path;
 *  - the grant is temporary and belongs to the receiving Intent, so the bytes are
 *    copied while it is still valid rather than held as a URI to open later;
 *  - a URI that cannot be read is reported as such, so the caller can say so in
 *    Aether's own words instead of failing silently or crashing.
 *
 * This is the same copy-through-the-resolver approach the gallery and document
 * pickers already use, consolidated so both paths have one implementation.
 */
class SharedUriGateway(private val context: Context) {

    private val resolver: ContentResolver get() = context.contentResolver

    /** The resolver's own MIME type for [uri], which outranks any declared one. */
    fun mimeType(uri: Uri): String? = runCatching { resolver.getType(uri) }.getOrNull()

    /** The human name of [uri], when the provider publishes one. */
    fun displayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
        }
    }.getOrNull()

    /**
     * Keeps read access alive past this Intent, where the provider allows it.
     *
     * Most share sources grant read access for the life of the receiving Intent
     * only, and asking for more throws. That is not an error worth surfacing --
     * the bytes are copied while the temporary grant holds either way -- so a
     * refusal is absorbed here.
     */
    fun retainAccess(uri: Uri) {
        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Copies [attachment] into the cache, or returns null if it cannot be read.
     *
     * Null is the honest answer for a revoked grant, a provider that has gone
     * away, or a URI that never pointed at anything -- all of which a share can
     * legitimately contain. The caller reports it; nothing here throws.
     */
    fun materialize(attachment: SharedAttachment): MaterializedAttachment? {
        val uri = runCatching { attachment.uri.toUri() }.getOrNull() ?: return null
        val name = attachment.name ?: displayName(uri)
        val mimeType = attachment.mimeType ?: mimeType(uri)
        return runCatching {
            val target = File.createTempFile(prefixFor(attachment.kind), suffixFor(name, mimeType), context.cacheDir)
            val copied = resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
            if (copied == null || target.length() == 0L) {
                target.delete()
                null
            } else {
                MaterializedAttachment(target.absolutePath, attachment.kind, name)
            }
        }.getOrNull()
    }

    private fun prefixFor(kind: SharedAttachmentKind): String = when (kind) {
        SharedAttachmentKind.IMAGE -> "shared_photo_"
        SharedAttachmentKind.VIDEO -> "shared_video_"
        SharedAttachmentKind.FILE -> "shared_file_"
    }

    /**
     * A suffix Telegram and the local media stack can recognise: the shared name's
     * own extension where there is one, the MIME subtype otherwise.
     */
    private fun suffixFor(name: String?, mimeType: String?): String {
        val fromName = name?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() && it.length <= 5 }
        val fromMime = mimeType?.substringAfterLast('/', "")?.takeIf { it.isNotBlank() && it.all { c -> c.isLetterOrDigit() } }
        return ".${fromName ?: fromMime ?: "tmp"}"
    }
}
