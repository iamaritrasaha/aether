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
     * Odd dimensions round the chroma planes up. Getting this wrong is exactly
     * how a converter reads one byte past the end of a real frame.
     */
    @Test
    fun oddDimensionsComputeChromaPlanesThatActuallyFit() {
        for (width in listOf(1, 3, 17, 65)) {
            for (height in listOf(1, 3, 17, 65)) {
                val required = I420Converter.requiredSize(width, height).toInt()
                val data = ByteArray(required)
                assertNotNull("$width x $height should convert", I420Converter.convert(data, width, height))
                assertNull(
                    "$width x $height must reject one byte less",
                    I420Converter.convert(ByteArray(required - 1), width, height)
                )
            }
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
}
