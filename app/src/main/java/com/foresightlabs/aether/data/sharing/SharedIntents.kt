package com.foresightlabs.aether.data.sharing

import android.content.Intent
import android.net.Uri
import android.os.Build
import com.foresightlabs.aether.domain.sharing.SharedAttachment
import com.foresightlabs.aether.domain.sharing.SharedAttachmentKind
import com.foresightlabs.aether.domain.sharing.SharedContent

/**
 * Turning an Android share into something Aether can carry.
 *
 * The one place `ACTION_SEND` and `ACTION_SEND_MULTIPLE` are read. Everything
 * that arrives is treated as untrusted shape rather than as a promise: a share
 * can name a MIME type it does not deliver, claim a stream it did not attach, or
 * carry a URI this process cannot open. Every one of those normalizes to null
 * or to fewer items, never to a crash.
 */
object SharedIntents {

    /** True for the actions Aether is registered to receive. */
    fun isShare(intent: Intent?): Boolean =
        intent?.action == Intent.ACTION_SEND || intent?.action == Intent.ACTION_SEND_MULTIPLE

    /**
     * The share in [intent], or null when there is nothing deliverable in it.
     *
     * @param mimeTypeOf the content resolver's answer for a URI, which outranks
     *   the intent's declared type -- a sender may declare a wildcard type and
     *   hand over a JPEG.
     * @param displayNameOf the resolver's name for a URI, used for file labels.
     */
    fun normalize(
        intent: Intent?,
        mimeTypeOf: (Uri) -> String? = { null },
        displayNameOf: (Uri) -> String? = { null }
    ): SharedContent? {
        if (!isShare(intent) || intent == null) return null
        val declaredType = intent.type
        val text = sharedText(intent)

        val uris = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(singleStream(intent))
            else -> multipleStreams(intent)
        }.ifEmpty { clipUris(intent) }

        if (uris.isEmpty()) {
            // A share with neither a stream nor text is malformed as far as
            // Aether is concerned; so is one whose text is only whitespace.
            return text?.takeIf { it.isNotBlank() }?.let { SharedContent.Text(it) }
        }

        val attachments = uris.map { uri ->
            val mimeType = mimeTypeOf(uri) ?: declaredType
            SharedAttachment(
                uri = uri.toString(),
                kind = kindOf(mimeType),
                mimeType = mimeType,
                name = displayNameOf(uri)
            )
        }
        return SharedContent.Attachments(attachments, caption = text.orEmpty().trim())
    }

    /**
     * A stable identity for one share.
     *
     * The same Intent is redelivered when the Activity is recreated, so the share
     * it describes must be recognised as one already accepted rather than
     * processed again. Built from what the share actually is, not from object
     * identity, which does not survive recreation.
     */
    fun identityOf(intent: Intent?): String? {
        if (!isShare(intent) || intent == null) return null
        val streams = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(singleStream(intent))
            else -> multipleStreams(intent)
        }.ifEmpty { clipUris(intent) }
        return buildString {
            append(intent.action)
            append('|')
            append(intent.type.orEmpty())
            append('|')
            append(sharedText(intent).orEmpty())
            append('|')
            streams.joinTo(this, ",") { it.toString() }
        }
    }

    /** Which send path an item belongs in, from the most trustworthy MIME type. */
    fun kindOf(mimeType: String?): SharedAttachmentKind = when {
        mimeType == null -> SharedAttachmentKind.FILE
        mimeType.startsWith("image/", ignoreCase = true) -> SharedAttachmentKind.IMAGE
        mimeType.startsWith("video/", ignoreCase = true) -> SharedAttachmentKind.VIDEO
        else -> SharedAttachmentKind.FILE
    }

    private fun sharedText(intent: Intent): String? {
        val body = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        if (!body.isNullOrBlank()) return body
        // Some senders put the whole message in the subject (mail-style shares).
        return intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString()
    }

    @Suppress("DEPRECATION")
    private fun singleStream(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }

    @Suppress("DEPRECATION")
    private fun multipleStreams(intent: Intent): List<Uri> {
        val extras = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
        }
        return extras?.filterNotNull().orEmpty()
    }

    /**
     * The URIs a sender attached through `ClipData` instead of `EXTRA_STREAM`.
     *
     * Both are legitimate; a share that carries only clip data is a share, and
     * reading it here is what keeps such senders from looking like empty shares.
     */
    private fun clipUris(intent: Intent): List<Uri> {
        val clip = intent.clipData ?: return emptyList()
        return (0 until clip.itemCount).mapNotNull { index ->
            runCatching { clip.getItemAt(index)?.uri }.getOrNull()
        }
    }
}
