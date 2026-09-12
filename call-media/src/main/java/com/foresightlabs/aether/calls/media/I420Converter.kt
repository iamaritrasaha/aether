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
     * Bytes an I420 buffer must contain for [width] x [height].
     *
     * Mirrors the pinned native producer exactly
     * (`ntgcalls/src/media/video_receiver.cpp`): `total_size = w*h +
     * 2*((w*h)/4)` -- chroma plane size is the FLOOR of w*h/4 (integer
     * division of the product), with chroma stride `w/2` and chroma rows
     * `h/2`, both floored. The old Kotlin-side `(w+1)/2` rounding disagreed
     * with native for odd dimensions and would have rejected real frames.
     * Sub-2-pixel geometry is refused outright: native cannot scale chroma
     * for it meaningfully and a 1-wide chroma plane cannot be indexed safely.
     */
    fun requiredSize(width: Int, height: Int): Long {
        if (width < 2 || height < 2) return -1L
        if (width > MAX_DIMENSION || height > MAX_DIMENSION) return -1L
        val y = width.toLong() * height.toLong()
        val chroma = y / 4
        return y + 2L * chroma
    }

    /**
     * Chroma plane geometry as produced natively: stride = width/2 (floored),
     * rows = height/2 (floored), plane slot size = floor(w*h/4) -- which can
     * exceed stride*rows for odd dimensions (padding exists before the V
     * plane), so [convert] must use these exact offsets, never stride*rows.
     */
    private fun chromaStride(width: Int): Int = width / 2

    private fun chromaRows(height: Int): Int = height / 2

    private fun chromaSlotBytes(width: Int, height: Int): Long {
        val y = width.toLong() * height.toLong()
        return y / 4
    }

    /** Whether a frame's declared geometry and buffer length are self-consistent. */
    fun isWellFormed(dataSize: Int, width: Int, height: Int): Boolean {
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

        // Native plane layout (video_receiver.cpp): Y at 0, U at w*h, V at
        // w*h + floor(w*h/4); chroma stride w/2, chroma rows h/2. Clamping
        // keeps the last row/column of an odd-sized frame inside the chroma
        // plane instead of reading into padding or the next plane.
        val chromaStride = chromaStride(width)
        val chromaRows = chromaRows(height)
        val chromaSlot = chromaSlotBytes(width, height).toInt()
        val ySize = width * height
        val uOffset = ySize
        val vOffset = ySize + chromaSlot

        val pixels = IntArray(outWidth * outHeight)
        var out = 0
        var row = 0
        while (row < height) {
            val yRowStart = row * width
            val chromaRowStart = (row / 2).coerceAtMost(chromaRows - 1) * chromaStride
            var col = 0
            while (col < width) {
                val y = data[yRowStart + col].toInt() and 0xFF
                val chromaCol = (col / 2).coerceAtMost(chromaStride - 1)
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
