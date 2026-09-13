package com.foresightlabs.aether.data.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The voice-note waveform codec. What Aether sends must be exactly what every
 * Telegram client draws back, so the layout is pinned byte-for-byte, not just
 * by round trip.
 */
class VoiceWaveformTest {

    @Test
    fun theLayoutIsLeastSignificantBitFirst() {
        // Telegram clients write sample i at bit offset i*5, low bits first.
        // A lone full-scale first sample occupies the low five bits of byte 0.
        val packed = VoiceWaveform.encode(listOf(1f, 0f, 0f), sampleCount = 3)
        assertArrayEquals(byteArrayOf(0x1F, 0x00), packed)
    }

    @Test
    fun aSampleStraddlingABytePairsItsBitsAcrossBothBytes() {
        // Sample 1 sits at bits 5..9: three bits in byte 0, two in byte 1.
        val packed = VoiceWaveform.encode(listOf(0f, 1f, 0f), sampleCount = 3)
        assertArrayEquals(byteArrayOf(0xE0.toByte(), 0x03), packed)
    }

    @Test
    fun aHundredSamplesPackIntoSixtyThreeBytesAndDecodeBackToAHundred() {
        val levels = List(237) { index -> (index % 17) / 16f }
        val packed = VoiceWaveform.encode(levels)
        assertEquals(63, packed.size)
        assertEquals(VoiceWaveform.SAMPLE_COUNT, VoiceWaveform.decode(packed).size)
    }

    @Test
    fun encodeThenDecodeKeepsTheShape() {
        val levels = listOf(0.1f, 0.5f, 1f, 0.25f, 0f)
        val decoded = VoiceWaveform.decode(VoiceWaveform.encode(levels, sampleCount = 5))
        // Normalized to the recording's own peak and quantized to 31 steps.
        val expected = levels.map { Math.round(it * 31) / 31f }
        expected.forEachIndexed { index, value -> assertEquals(value, decoded[index], 0.0001f) }
    }

    @Test
    fun aQuietRecordingIsNormalizedToItsOwnPeak() {
        val decoded = VoiceWaveform.decode(VoiceWaveform.encode(listOf(0.02f, 0.04f), sampleCount = 2))
        assertEquals(1f, decoded[1], 0.0001f)
        assertEquals(0.5f, decoded[0], 0.05f)
    }

    @Test
    fun fewerLevelsThanSamplesStretchRatherThanPadWithSilence() {
        val decoded = VoiceWaveform.decode(VoiceWaveform.encode(listOf(1f, 1f), sampleCount = 10))
        assertTrue(decoded.take(10).all { it == 1f })
    }

    @Test
    fun noLevelsMeansNoWaveformAndSilenceMeansAFlatOne() {
        assertEquals(0, VoiceWaveform.encode(emptyList()).size)
        val silent = VoiceWaveform.decode(VoiceWaveform.encode(listOf(0f, 0f, 0f)))
        assertTrue(silent.all { it == 0f })
    }

    @Test
    fun decodingNothingYieldsNothing() {
        assertTrue(VoiceWaveform.decode(null).isEmpty())
        assertTrue(VoiceWaveform.decode(ByteArray(0)).isEmpty())
    }
}
