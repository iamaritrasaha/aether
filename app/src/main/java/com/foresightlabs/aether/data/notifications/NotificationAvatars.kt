package com.foresightlabs.aether.data.notifications

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.IconCompat
import com.foresightlabs.aether.data.telegram.TelegramMappers

/**
 * Prepares a circularly-masked avatar for Android's standard notification APIs
 * (a [Person] icon inside a `MessagingStyle` notification).
 *
 * Android does not crop `Person`/notification icons to a circle itself, so a
 * square or non-square source photo renders as a square on many launchers and
 * shades. This builds the circular bitmap Aether hands to the platform instead
 * of relying on the platform to do it -- the source Telegram profile photo file
 * on disk is never touched.
 */
object NotificationAvatars {

    /** Matches the ~40dp circular avatar Android renders inside the expanded shade. */
    internal const val AVATAR_SIZE_PX = 128

    /**
     * Builds a circular [IconCompat] from a profile photo at [photoPath], falling
     * back to a solid-color initials avatar (matching the in-app avatar palette)
     * when the path is missing or the file cannot be decoded.
     */
    fun circularIcon(photoPath: String?, displayName: String, colorSeedId: Long): IconCompat {
        val source = photoPath?.takeIf { it.isNotBlank() }?.let { decodeBitmap(it) }
        val square = source?.let { centerCropToSquare(it) }
            ?: initialsBitmap(displayName, colorSeedId)
        return IconCompat.createWithBitmap(maskToCircle(square))
    }

    private fun decodeBitmap(path: String): Bitmap? = runCatching {
        BitmapFactory.decodeFile(path)
    }.getOrNull()

    /**
     * Crops to a centered square first so portrait and landscape sources are
     * masked from their visual center rather than being squashed into a circle.
     */
    internal fun centerCropToSquare(bitmap: Bitmap): Bitmap {
        val side = minOf(bitmap.width, bitmap.height)
        if (side <= 0) return bitmap
        val left = (bitmap.width - side) / 2
        val top = (bitmap.height - side) / 2
        return if (left == 0 && top == 0 && bitmap.width == side && bitmap.height == side) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, left, top, side, side)
        }
    }

    internal fun maskToCircle(square: Bitmap): Bitmap {
        val size = AVATAR_SIZE_PX
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val radius = size / 2f
        canvas.drawCircle(radius, radius, radius, paint)

        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val src = Rect(0, 0, square.width, square.height)
        val dst = RectF(0f, 0f, size.toFloat(), size.toFloat())
        canvas.drawBitmap(square, src, dst, paint)

        return output
    }

    internal fun initialsBitmap(displayName: String, colorSeedId: Long): Bitmap {
        val size = AVATAR_SIZE_PX
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val backgroundColor = TelegramMappers.gradientFor(colorSeedId).first().toArgb()
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = backgroundColor }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), fillPaint)

        val initials = TelegramMappers.initials(displayName)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = size * 0.4f
            textAlign = Paint.Align.CENTER
        }
        val textY = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(initials, size / 2f, textY, textPaint)

        return bitmap
    }
}
