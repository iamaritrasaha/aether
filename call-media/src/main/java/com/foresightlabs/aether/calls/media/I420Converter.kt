package com.foresightlabs.aether.calls.media

/**
 * Converts a planar I420 (YUV 4:2:0) buffer to packed ARGB_8888 pixels.
 *
 * The native call engine hands video frames back as raw byte buffers with no
 * explicit pixel-format field. The I420 layout is now source-proven against
 * the pinned engine revision (not merely assumed): the remote-video path
 * (`ntgcalls/src/media/video_receiver.cpp`) converts every incoming frame
 * through `webrtc::I420Buffer::ToI420()` + `I420Scale` with luminance stride
 * == width and chroma stride == width/2, producing a tightly packed
 * `w*h*3/2`-byte buffer; `wrtc/src/models/i420_image_data.cpp` additionally
 * enforces the exact plane sizes. Because device behaviour can still surprise
 * (and the cost of a bad read is high), every entry point below still
 * validates before it reads: a buffer whose length does not match the frame's
 * declared geometry is rejected outright rather than read past, and callers
 * are expected to drop the frame. No input shape may produce an out-of-bounds
 * read.
 */
object I420Converter {

    /**
     * Largest frame geometry accepted. A 4K frame would be a 33MB ARGB buffer;
     * a negative or absurd width/height is a corrupt frame header. Both are
     * refused rather than allocated.
     */
    const val MAX_DIMENSION: Int = 4096

    /**
     * Bytes an I420 buffer must contain for [width] x [height], assuming tightly
     * packed planes (stride == plane width), which is what the engine's own
     * conversion produces.
     */
    fun requiredSize(width: Int, height: Int): Long {
        if (width <= 0 || height <= 0) return -1L
        val y = width.toLong() * height.toLong()
        val chroma = ((width + 1) / 2).toLong() * ((height + 1) / 2).toLong()
        return y + 2L * chroma
    }

    /** Whether a frame's declared geometry and buffer length are self-consistent. */
    fun isWellFormed(dataSize: Int, width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0) return false
        if (width > MAX_DIMENSION || height > MAX_DIMENSION) return false
        val required = requiredSize(width, height)
        return required > 0L && dataSize.toLong() >= required
    }

    /**
     * The subsample step that keeps a converted frame within [maxDimension] on
     * its longest side. 1 means full resolution.
     *
     * Subsampling exists to bound memory: a 720p ARGB frame is 3.7MB, and one
     * arrives every ~33ms. Nearest-neighbour is sufficient for a phone-sized
     * call surface and keeps the conversion allocation-light.
     */
    fun sampleStep(width: Int, height: Int, maxDimension: Int): Int {
        if (maxDimension <= 0) return 1
        val longest = maxOf(width, height)
        if (longest <= maxDimension) return 1
        return ((longest + maxDimension - 1) / maxDimension).coerceAtLeast(1)
    }

    /**
     * @param data Planar I420: a full-resolution Y plane, followed by
     *   quarter-resolution U and V planes, with no padding between rows.
     * @param maxDimension longest edge of the produced image; larger frames are
     *   nearest-neighbour subsampled to fit.
     * @return the converted frame, or null when [data] is not a well-formed I420
     *   buffer for [width] x [height] -- callers must drop such a frame.
     */
    fun convert(data: ByteArray, width: Int, height: Int, maxDimension: Int = 640): ArgbImage? {
        if (!isWellFormed(data.size, width, height)) return null

        val step = sampleStep(width, height, maxDimension)
        val outWidth = (width + step - 1) / step
        val outHeight = (height + step - 1) / step
        if (outWidth <= 0 || outHeight <= 0) return null

        val chromaWidth = (width + 1) / 2
        val chromaHeight = (height + 1) / 2
        val ySize = width * height
        val uOffset = ySize
        val vOffset = ySize + chromaWidth * chromaHeight

        val pixels = IntArray(outWidth * outHeight)
        var out = 0
        var row = 0
        while (row < height) {
            val yRowStart = row * width
            val chromaRowStart = (row / 2).coerceAtMost(chromaHeight - 1) * chromaWidth
            var col = 0
            while (col < width) {
                val y = data[yRowStart + col].toInt() and 0xFF
                val chromaCol = (col / 2).coerceAtMost(chromaWidth - 1)
                val u = (data[uOffset + chromaRowStart + chromaCol].toInt() and 0xFF) - 128
                val v = (data[vOffset + chromaRowStart + chromaCol].toInt() and 0xFF) - 128

                // BT.601 limited-range YUV -> RGB.
                val c = y - 16
                val r = ((298 * c + 409 * v + 128) shr 8).coerceIn(0, 255)
                val g = ((298 * c - 100 * u - 208 * v + 128) shr 8).coerceIn(0, 255)
                val b = ((298 * c + 516 * u + 128) shr 8).coerceIn(0, 255)

                pixels[out++] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                col += step
            }
            row += step
        }

        return ArgbImage(width = outWidth, height = outHeight, pixels = pixels)
    }
}

/** A converted, renderer-ready ARGB_8888 image. [pixels] is exactly width*height long. */
data class ArgbImage(
    val width: Int,
    val height: Int,
    val pixels: IntArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ArgbImage) return false
        return width == other.width && height == other.height && pixels.contentEquals(other.pixels)
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + pixels.contentHashCode()
        return result
    }
}
