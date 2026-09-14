package com.foresightlabs.aether.data.media

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * Cuts a stretch out of a recorded voice note without re-encoding it.
 *
 * Aether records AAC in an MPEG-4 container ([AudioRecorderManager]). Every AAC
 * frame is independently decodable -- each is a sync sample -- so copying the
 * frames whose timestamps fall inside the range into a fresh container, rebased
 * to start at zero, yields exactly that stretch of audio: frame-accurate (about
 * 23 ms at 44.1 kHz), lossless, and with no codec to configure.
 */
object VoiceNoteTrimmer {

    /**
     * Writes `[startMs, endMs)` of [source] to [output]. True only when at least
     * one frame was written and the container was finalized. On any failure
     * [output] is deleted; [source] is never touched either way.
     */
    fun trim(source: File, output: File, startMs: Long, endMs: Long): Boolean {
        if (endMs <= startMs) return false
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var ok = false
        try {
            extractor.setDataSource(source.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return false
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)

            val out = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = out
            val outTrack = out.addTrack(format)
            out.start()
            muxerStarted = true

            val startUs = startMs * 1_000L
            val endUs = endMs * 1_000L
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val declared = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } else {
                0
            }
            val buffer = ByteBuffer.allocate(maxOf(declared, DEFAULT_BUFFER_BYTES))
            val info = MediaCodec.BufferInfo()
            var firstUs = -1L
            var written = 0
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val timeUs = extractor.sampleTime
                if (timeUs >= endUs) break
                if (timeUs >= startUs) {
                    if (firstUs < 0) firstUs = timeUs
                    val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    } else {
                        0
                    }
                    info.set(0, size, timeUs - firstUs, flags)
                    out.writeSampleData(outTrack, buffer, info)
                    written++
                }
                if (!extractor.advance()) break
            }
            muxerStarted = false
            out.stop()
            ok = written > 0
            return ok
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            extractor.release()
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            if (!ok) output.delete()
        }
    }

    private const val DEFAULT_BUFFER_BYTES = 256 * 1024
}
