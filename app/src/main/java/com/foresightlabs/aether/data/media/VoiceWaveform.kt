package com.foresightlabs.aether.data.media

/**
 * Telegram's voice-note waveform: 5-bit samples (0..31) packed end to end,
 * least significant bit first, little-endian across byte boundaries -- the
 * layout Telegram's own clients write (and read) in `voiceNote.waveform`.
 *
 * Both directions live here so what Aether sends is exactly what Aether (and
 * every other Telegram client) draws back.
 */
object VoiceWaveform {

    /** What Telegram clients send: 100 samples, 63 bytes. */
    const val SAMPLE_COUNT = 100

    private const val MAX_VALUE = 31

    /**
     * Unpacks a waveform into 0..1 amplitudes. An absent waveform yields an
     * empty list rather than invented amplitudes -- anything else drawn in a
     * voice bubble would be decoration, not audio.
     */
    fun decode(packed: ByteArray?): List<Float> {
        if (packed == null || packed.isEmpty()) return emptyList()
        val sampleCount = packed.size * 8 / 5
        val samples = ArrayList<Float>(sampleCount)
        for (index in 0 until sampleCount) {
            val bitOffset = index * 5
            val byteIndex = bitOffset / 8
            val shift = bitOffset % 8
            val low = packed[byteIndex].toInt() and 0xFF
            val high = if (byteIndex + 1 < packed.size) packed[byteIndex + 1].toInt() and 0xFF else 0
            val value = ((low or (high shl 8)) shr shift) and MAX_VALUE
            samples += value / MAX_VALUE.toFloat()
        }
        return samples
    }

    /**
     * Packs recorded input levels (0..1, one per poll) into [sampleCount]
     * samples. Each output sample is the loudest level in its slice of the
     * recording, normalized to the recording's own peak so a quiet voice still
     * draws a readable shape. No levels -> no waveform (never a fake one).
     */
    fun encode(levels: List<Float>, sampleCount: Int = SAMPLE_COUNT): ByteArray {
        if (levels.isEmpty() || sampleCount <= 0) return ByteArray(0)
        val resampled = FloatArray(sampleCount) { index ->
            val from = index * levels.size / sampleCount
            val until = maxOf(from + 1, (index + 1) * levels.size / sampleCount)
            var peak = 0f
            for (i in from until until.coerceAtMost(levels.size)) {
                peak = maxOf(peak, levels[i].coerceIn(0f, 1f))
            }
            peak
        }
        val peak = resampled.maxOrNull() ?: 0f
        val packed = ByteArray((sampleCount * 5 + 7) / 8)
        if (peak <= 0f) return packed
        resampled.forEachIndexed { index, level ->
            val value = Math.round(level / peak * MAX_VALUE).coerceIn(0, MAX_VALUE)
            val bitOffset = index * 5
            val byteIndex = bitOffset / 8
            val shift = bitOffset % 8
            val bits = value shl shift
            packed[byteIndex] = (packed[byteIndex].toInt() or (bits and 0xFF)).toByte()
            if (bits > 0xFF && byteIndex + 1 < packed.size) {
                packed[byteIndex + 1] = (packed[byteIndex + 1].toInt() or (bits shr 8)).toByte()
            }
        }
        return packed
    }
}
