package com.foresightlabs.aether.data.telegram

import android.annotation.SuppressLint
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.EOFException
import java.io.IOException
import java.io.RandomAccessFile
import kotlinx.coroutines.runBlocking

/**
 * Media3 source addressed by TDLib file id, never by a message's local path.
 *
 * This first version intentionally downloads voice/audio in full. A later
 * range-aware version can change read policy without changing media identity.
 */
@SuppressLint("UnsafeOptInUsageError")
class TelegramDataSource(private val files: TelegramFileManager) : BaseDataSource(false) {
    private var uri: Uri? = null
    private var fileId: Int = 0
    private var reader: RandomAccessFile? = null
    private var remaining: Long = C.LENGTH_UNSET.toLong()

    override fun open(dataSpec: DataSpec): Long {
        val requestedId = fileId(dataSpec.uri)
        transferInitializing(dataSpec)
        val initialFile = files.file(requestedId)
        val initialPath = initialFile?.local?.path
        log("MEDIA3_FILE_OPEN fileId=$requestedId initialCompleted=${initialFile?.local?.isDownloadingCompleted == true}")
        val completed = try {
            runBlocking { files.awaitDownloaded(requestedId) }
        } catch (error: Exception) {
            throw IOException("Unable to open Telegram file $requestedId", error)
        }

        // Resolve the manager again after the wait. The path embedded in the
        // message (or returned by the initial GetFile) is intentionally unused.
        val latest = files.get(requestedId) as? TelegramFileManager.State.Downloaded
            ?: completed
        log(
            "MEDIA3_FILE_READY fileId=$requestedId finalCompleted=${latest.file.local.isDownloadingCompleted} " +
                "pathChanged=${initialPath != null && initialPath != latest.path}"
        )
        val opened = RandomAccessFile(latest.path, "r")
        if (dataSpec.position > opened.length()) {
            opened.close()
            throw EOFException("Position ${dataSpec.position} exceeds Telegram file length")
        }
        opened.seek(dataSpec.position)
        uri = dataSpec.uri
        fileId = requestedId
        reader = opened
        val available = opened.length() - dataSpec.position
        remaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) available else minOf(available, dataSpec.length)
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val source = reader ?: throw IOException("Telegram data source is not open")
        val count = source.read(buffer, offset, minOf(length.toLong(), remaining).toInt())
        if (count < 0) return C.RESULT_END_OF_INPUT
        remaining -= count
        bytesTransferred(count)
        return count
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        val wasOpen = reader != null
        reader?.close()
        reader = null
        uri = null
        fileId = 0
        remaining = C.LENGTH_UNSET.toLong()
        if (wasOpen) transferEnded()
    }

    class Factory(private val files: TelegramFileManager) : DataSource.Factory {
        override fun createDataSource(): DataSource = TelegramDataSource(files)
    }

    companion object {
        private const val SCHEME = "telegram"
        private const val AUTHORITY = "file"

        fun uri(fileId: Int): Uri = Uri.Builder()
            .scheme(SCHEME)
            .authority(AUTHORITY)
            .appendPath(fileId.toString())
            .build()

        fun fileId(uri: Uri): Int {
            if (uri.scheme != SCHEME || uri.authority != AUTHORITY) {
                throw IOException("Unsupported Telegram media URI")
            }
            return uri.lastPathSegment?.toIntOrNull()?.takeIf { it != 0 }
                ?: throw IOException("Missing Telegram file id")
        }

        private fun log(message: String) {
            if (com.foresightlabs.aether.BuildConfig.DEBUG) android.util.Log.d("AetherTd", message)
        }
    }
}
