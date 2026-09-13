package com.foresightlabs.aether.calls.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A video frame arrives from a native thread with a declared geometry and a raw
 * buffer, and the pixel format is an assumption rather than a documented
 * contract (see [I420Converter]). So every frame is treated as untrusted: no
 * input shape may read past the buffer it was given, and a frame that does not
 * match its own geometry is dropped rather than partially rendered.
 */
class CallFrameSafetyTest {

    @Test
    fun aWellFormedFrameIsAccepted() {
        val width = 16
        val height = 16
        val data = ByteArray(I420Converter.requiredSize(width, height).toInt())
        assertTrue(I420Converter.isWellFormed(data.size, width, height))
        assertNotNull(I420Converter.convert(data, width, height))
    }

    @Test
    fun aTruncatedBufferIsRejectedRatherThanReadPast() {
        val width = 64
        val height = 64
        val full = I420Converter.requiredSize(width, height).toInt()
        for (shortfall in listOf(1, 16, full / 2, full - 1)) {
            val data = ByteArray(full - shortfall)
            assertFalse(I420Converter.isWellFormed(data.size, width, height))
            assertNull("a short buffer must never convert", I420Converter.convert(data, width, height))
        }
    }

    @Test
    fun nonPositiveOrAbsurdGeometryIsRejected() {
        val data = ByteArray(4096)
        assertNull(I420Converter.convert(data, 0, 16))
        assertNull(I420Converter.convert(data, 16, 0))
        assertNull(I420Converter.convert(data, -8, 16))
        assertNull(I420Converter.convert(data, 16, -8))
        assertNull(I420Converter.convert(data, I420Converter.MAX_DIMENSION + 1, 16))
        assertEquals(-1L, I420Converter.requiredSize(0, 0))
    }

    /**
     * Chroma geometry must match the pinned native producer exactly
     * (`ntgcalls/src/media/video_receiver.cpp`: uv plane size = floor(w*h/4),
     * chroma stride = w/2, chroma rows = h/2). Getting this wrong either
     * reads past the buffer or rejects real native frames.
     */
    @Test
    fun chromaGeometryMatchesNativeProducerForEveryParity() {
        for (width in 2..37) {
            for (height in 2..37) {
                val native = width * height + 2 * ((width * height) / 4)
                assertEquals(
                    "requiredSize($width,$height) must equal native total_size",
                    native.toLong(),
                    I420Converter.requiredSize(width, height)
                )
                val required = native
                val data = ByteArray(required)
                // Every accepted size converts without reading past the end
                // (this exercises the floor-truncation padding paths).
                assertNotNull("$width x $height should convert", I420Converter.convert(data, width, height))
                assertNull(
                    "$width x $height must reject one byte less",
                    I420Converter.convert(ByteArray(required - 1), width, height)
                )
            }
        }
    }

    @Test
    fun degenerateSubTwoPixelGeometryIsRejected() {
        // Native cannot meaningfully scale chroma below 2x2, and a 1-wide
        // chroma plane cannot be indexed safely: reject rather than guess.
        for ((w, h) in listOf(1 to 1, 1 to 64, 64 to 1)) {
            assertEquals(-1L, I420Converter.requiredSize(w, h))
            assertNull(I420Converter.convert(ByteArray(64), w, h))
        }
    }

    /**
     * A 720p ARGB frame is 3.7MB and one arrives every ~33ms, so conversion is
     * bounded by subsampling rather than allocating whatever arrives.
     */
    @Test
    fun largeFramesAreSubsampledToABoundedSize() {
        val width = 1280
        val height = 720
        val data = ByteArray(I420Converter.requiredSize(width, height).toInt())
        val image = I420Converter.convert(data, width, height, maxDimension = 640)

        assertNotNull(image)
        assertTrue("longest edge was ${image!!.width}", maxOf(image.width, image.height) <= 640)
        assertEquals(image.width * image.height, image.pixels.size)
    }

    @Test
    fun smallFramesAreNotUpscaledOrResampled() {
        val width = 320
        val height = 240
        val data = ByteArray(I420Converter.requiredSize(width, height).toInt())
        val image = I420Converter.convert(data, width, height, maxDimension = 640)

        assertEquals(1, I420Converter.sampleStep(width, height, 640))
        assertEquals(width, image?.width)
        assertEquals(height, image?.height)
    }

    @Test
    fun everyProducedImageHasExactlyOnePixelPerDeclaredCell() {
        for ((w, h) in listOf(2 to 2, 33 to 17, 640 to 480, 1920 to 1080)) {
            val data = ByteArray(I420Converter.requiredSize(w, h).toInt())
            val image = I420Converter.convert(data, w, h) ?: error("$w x $h should convert")
            assertEquals(image.width * image.height, image.pixels.size)
        }
    }

    @Test
    fun anOversizedBufferIsAcceptedWithoutOverreading() {
        // A buffer longer than the declared geometry is legal (producers may
        // pad); conversion must consume only the required prefix.
        val w = 34
        val h = 18
        val data = syntheticFrame(w, h) + ByteArray(4096) { 0x55 }
        val image = I420Converter.convert(data, w, h)!!
        assertEquals(w, image.width)
        assertEquals(h, image.height)
    }

    @Test
    fun aSyntheticGradientConvertsToExpectedPixels() {
        // Deterministic content check, not just geometry: mid-grey Y with
        // neutral chroma must land on neutral mid-grey RGB under BT.601.
        val w = 8
        val h = 8
        val data = ByteArray(w * h * 3 / 2)
        java.util.Arrays.fill(data, 0, w * h, 128.toByte())          // Y mid
        java.util.Arrays.fill(data, w * h, data.size, 128.toByte())  // U=V=128 -> neutral
        val image = I420Converter.convert(data, w, h)!!
        // (128-16)*298 >> 8 + 128 = 135 coerced; chroma terms vanish at u=v=128.
        val px = image.pixels[0]
        val r = (px shr 16) and 0xFF
        val g = (px shr 8) and 0xFF
        val b = px and 0xFF
        assertEquals(r, g)
        assertEquals(g, b)
        assertTrue("neutral grey must be plausible: $r", r in 100..160)
    }

    @Test
    fun conversionCostOfARealisticFrameIsMeasuredAndBounded() {
        // 640x480 is the conversion ceiling for a remote frame
        // (maxDimension=640). Sixty frames ~ two seconds of 30fps video.
        val w = 640
        val h = 480
        val data = syntheticFrame(w, h)
        val start = System.nanoTime()
        val runs = 60
        repeat(runs) { I420Converter.convert(data, w, h) }
        val perFrameMillis = (System.nanoTime() - start) / 1_000_000.0 / runs
        println("I420->ARGB 640x480 average conversion: %.2f ms/frame".format(perFrameMillis))
        // Generous: the assertion catches catastrophic regressions (orders of
        // magnitude), not real-time suitability. Real-time suitability comes
        // from the engine's newest-frame-only, throttled callback path.
        assertTrue(
            "conversion regressed catastrophically: %.2f ms/frame".format(perFrameMillis),
            perFrameMillis < 100.0
        )
    }

    /** A deterministic, well-formed I420 buffer for [w] x [h]: greyscale ramp. */
    private fun syntheticFrame(w: Int, h: Int): ByteArray {
        val data = ByteArray(I420Converter.requiredSize(w, h).toInt())
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                data[y * w + x] = ((x + y) * 7 % 256).toByte()
                x++
            }
            y++
        }
        java.util.Arrays.fill(data, w * h, data.size, 128.toByte())
        return data
    }
}
