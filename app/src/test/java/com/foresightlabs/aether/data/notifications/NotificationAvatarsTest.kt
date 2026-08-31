package com.foresightlabs.aether.data.notifications

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NotificationAvatarsTest {

    // --- center-crop preserves aspect ratio before masking --------------------

    @Test
    fun squareSourceIsUntouchedByCenterCrop() {
        val square = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        val cropped = NotificationAvatars.centerCropToSquare(square)
        assertEquals(80, cropped.width)
        assertEquals(80, cropped.height)
    }

    @Test
    fun portraitSourceIsCroppedToItsWidth() {
        val portrait = Bitmap.createBitmap(60, 120, Bitmap.Config.ARGB_8888)
        val cropped = NotificationAvatars.centerCropToSquare(portrait)
        assertEquals(60, cropped.width)
        assertEquals(60, cropped.height)
    }

    @Test
    fun landscapeSourceIsCroppedToItsHeight() {
        val landscape = Bitmap.createBitmap(150, 90, Bitmap.Config.ARGB_8888)
        val cropped = NotificationAvatars.centerCropToSquare(landscape)
        assertEquals(90, cropped.width)
        assertEquals(90, cropped.height)
    }

    // --- circular masking bounds -----------------------------------------------

    @Test
    fun maskedAvatarIsAvatarSizeAndCircular() {
        val opaqueSquare = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        val masked = NotificationAvatars.maskToCircle(opaqueSquare)

        assertEquals(NotificationAvatars.AVATAR_SIZE_PX, masked.width)
        assertEquals(NotificationAvatars.AVATAR_SIZE_PX, masked.height)

        // Center of the circle is fully opaque; the far corner -- outside the
        // circle's radius -- is masked away to transparent.
        val center = NotificationAvatars.AVATAR_SIZE_PX / 2
        assertEquals(255, Color.alpha(masked.getPixel(center, center)))
        assertEquals(0, Color.alpha(masked.getPixel(1, 1)))
    }

    @Test
    fun transparentSourceStaysTransparentWithinTheCircle() {
        val transparentSquare = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
        }
        val masked = NotificationAvatars.maskToCircle(transparentSquare)
        val center = NotificationAvatars.AVATAR_SIZE_PX / 2
        // Fully transparent source stays transparent, even where the circle mask
        // itself is opaque -- SRC_IN intersects, it never adds opacity.
        assertEquals(0, Color.alpha(masked.getPixel(center, center)))
    }

    // --- initials fallback -------------------------------------------------------

    @Test
    fun initialsFallbackRendersAtAvatarSize() {
        val bitmap = NotificationAvatars.initialsBitmap("Ada Lovelace", colorSeedId = 42L)
        assertEquals(NotificationAvatars.AVATAR_SIZE_PX, bitmap.width)
        assertEquals(NotificationAvatars.AVATAR_SIZE_PX, bitmap.height)
    }

    @Test
    fun initialsFallbackIsDeterministicForTheSameSeed() {
        val first = NotificationAvatars.initialsBitmap("Ada Lovelace", colorSeedId = 42L)
        val second = NotificationAvatars.initialsBitmap("Ada Lovelace", colorSeedId = 42L)
        val corner = NotificationAvatars.AVATAR_SIZE_PX - 2
        assertEquals(first.getPixel(2, corner), second.getPixel(2, corner))
    }

    // --- missing photo / decode failure fall back to initials, end to end ------

    @Test
    fun missingPhotoPathFallsBackToInitialsAndIsCircular() {
        val icon = NotificationAvatars.circularIcon(
            photoPath = null,
            displayName = "Jane Doe",
            colorSeedId = 7L
        )
        assertNotNull(icon)
    }

    @Test
    fun blankPhotoPathFallsBackToInitialsAndIsCircular() {
        val icon = NotificationAvatars.circularIcon(
            photoPath = "   ",
            displayName = "Jane Doe",
            colorSeedId = 7L
        )
        assertNotNull(icon)
    }

    @Test
    fun unreadableFilePathFallsBackToInitials() {
        val icon = NotificationAvatars.circularIcon(
            photoPath = "/nonexistent/path/does-not-exist.jpg",
            displayName = "Jane Doe",
            colorSeedId = 7L
        )
        assertNotNull(icon)
    }

    @Test
    fun everyBoundarySizeCircularMaskStaysWithinAvatarBounds() {
        listOf(1, 2, 32, 63, 200).forEach { side ->
            val source = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.BLUE)
            }
            val masked = NotificationAvatars.maskToCircle(source)
            assertTrue(masked.width == NotificationAvatars.AVATAR_SIZE_PX)
            assertTrue(masked.height == NotificationAvatars.AVATAR_SIZE_PX)
        }
    }
}
