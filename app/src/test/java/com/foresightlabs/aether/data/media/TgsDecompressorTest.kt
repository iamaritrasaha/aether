package com.foresightlabs.aether.data.media

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * TGS = gzip-compressed Lottie. The viewer and bubble both render through
 * [TgsDecompressor]; malformed content must return null (the caller shows a
 * failed state), never throw, and real content must round-trip.
 */
class TgsDecompressorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun gzipOf(content: ByteArray): ByteArray {
        val bos = java.io.ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(bos).use { it.write(content) }
        return bos.toByteArray()
    }

    @Test
    fun validGzipLottieRoundTrips() {
        val json = """{"v":"5.7.4","fr":60}"""
        val file = tmp.newFile("sticker.tgs").apply { writeBytes(gzipOf(json.toByteArray())) }
        assertEquals(json, TgsDecompressor.decompressFile(file))
    }

    @Test
    fun nonGzipBytesReturnNullRatherThanThrowing() {
        val file = tmp.newFile("broken.tgs").apply { writeBytes("plain text, not gzip".toByteArray()) }
        assertNull(TgsDecompressor.decompressFile(file))
    }

    @Test
    fun truncatedGzipReturnsNullRatherThanThrowing() {
        val full = gzipOf("""{"v":"5"}""".toByteArray())
        val file = tmp.newFile("truncated.tgs").apply { writeBytes(full.copyOfRange(0, full.size / 2)) }
        assertNull(TgsDecompressor.decompressFile(file))
    }

    @Test
    fun missingOrEmptyFileReturnsNull() {
        val missing = File(tmp.root, "nope.tgs")
        assertNull(TgsDecompressor.decompressFile(missing))
        val empty = tmp.newFile("empty.tgs")
        assertNull(TgsDecompressor.decompressFile(empty))
    }

    @Test
    fun repeatedReadsUseTheCacheAndStayConsistent() {
        val json = """{"cache":true}"""
        val file = tmp.newFile("cached.tgs").apply { writeBytes(gzipOf(json.toByteArray())) }
        assertNotNull(TgsDecompressor.decompressFile(file))
        // Even if the file vanished after the first read, the cached decode
        // is what the renderer sees (no partial re-reads mid-scroll).
        file.delete()
        assertEquals(json, TgsDecompressor.decompressFile(file))
    }
}
