package com.foresightlabs.aether.data.media

import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream

/**
 * Decompresses Telegram animated stickers (.tgs) into standard Lottie JSON.
 *
 * Telegram animated stickers are GZIP-compressed Lottie JSON files. This utility
 * safely inflates them and maintains an in-memory cache to prevent re-decompressing
 * the same sticker on repeated compose passes or scrolling.
 */
object TgsDecompressor {

    private val jsonCache = ConcurrentHashMap<String, String>()

    /**
     * Decompresses a .tgs file on disk into a Lottie JSON string.
     * Returns null if the file does not exist, cannot be read, or is not valid GZIP data.
     */
    fun decompressFile(file: File): String? {
        val path = file.absolutePath
        jsonCache[path]?.let { return it }

        if (!file.exists() || !file.canRead() || file.length() == 0L) {
            return null
        }

        return try {
            val json = FileInputStream(file).use { fis ->
                GZIPInputStream(fis).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
            if (json.isNotBlank()) {
                jsonCache[path] = json
            }
            json
        } catch (e: Exception) {
            // Fallback: If it's already uncompressed plain JSON
            try {
                val plain = file.readText(Charsets.UTF_8)
                if (plain.trimStart().startsWith("{")) {
                    jsonCache[path] = plain
                    return plain
                }
            } catch (_: Exception) {}
            null
        }
    }

    /**
     * Decompresses raw .tgs bytes into a Lottie JSON string.
     */
    fun decompressBytes(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        return try {
            GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader(Charsets.UTF_8).use {
                it.readText()
            }
        } catch (e: Exception) {
            try {
                val plain = String(bytes, Charsets.UTF_8)
                if (plain.trimStart().startsWith("{")) plain else null
            } catch (_: Exception) {
                null
            }
        }
    }

    /** Clears the in-memory cache when memory is low. */
    fun clearCache() {
        jsonCache.clear()
    }
}
