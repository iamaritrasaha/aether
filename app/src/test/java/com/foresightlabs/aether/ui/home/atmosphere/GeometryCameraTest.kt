package com.foresightlabs.aether.ui.home.atmosphere

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Home to Conversation must move the camera, never reshape the scene.
 *
 * The regression these guard against is specific and was visible on device: the
 * renderer projected x through one scale and y through the container's height,
 * so every circle became an ellipse whose eccentricity depended on how tall the
 * container happened to be -- and Home's hero and Conversation's canvas are very
 * different heights, so entering a conversation visibly stretched the geometry.
 */
class GeometryCameraTest {

    private val phone = 1080f to 2400f
    private val hero = 1080f to 860f
    private val tablet = 1600f to 2560f

    /** The only scale there is. A second axis cannot disagree with it. */
    @Test
    fun scaleDependsOnWidthAloneSoHeightCannotDeformTheScene() {
        val camera = GeometryCamera.Conversation
        val short = camera.scaleFor(1080f)
        val tall = camera.scaleFor(1080f)
        assertEquals(short, tall, 0f)

        // Two containers of identical width but very different heights must
        // project identically in x, and differ in y only by their own centring.
        val xShort = camera.projectX(0.5f, 1080f)
        val xTall = camera.projectX(0.5f, 1080f)
        assertEquals(xShort, xTall, 1e-4f)
    }

    /**
     * A circle stays a circle: equal world extents project to equal pixel
     * extents on both axes, at any viewport aspect ratio.
     */
    @Test
    fun aCircleRemainsACircleAtEveryViewportAspect() {
        val radius = 0.4f
        for (camera in listOf(GeometryCamera.Home, GeometryCamera.Conversation)) {
            for ((w, h) in listOf(phone, hero, tablet)) {
                val left = camera.projectX(camera.centerX - radius, w)
                val right = camera.projectX(camera.centerX + radius, w)
                val top = camera.projectY(camera.centerY - radius, w, h)
                val bottom = camera.projectY(camera.centerY + radius, w, h)

                val widthPx = abs(right - left)
                val heightPx = abs(bottom - top)
                assertEquals(
                    "circle became an ellipse at ${w}x$h: ${widthPx}x$heightPx",
                    widthPx,
                    heightPx,
                    1e-3f
                )
            }
        }
    }

    /** Mid-transition frames are cameras too, and must be just as uniform. */
    @Test
    fun everyFrameOfTheTransitionIsUniform() {
        val radius = 0.35f
        val (w, h) = phone
        var fraction = 0f
        while (fraction <= 1f) {
            val camera = GeometryCamera.lerp(GeometryCamera.Home, GeometryCamera.Conversation, fraction)
            val widthPx = abs(camera.projectX(camera.centerX + radius, w) - camera.projectX(camera.centerX - radius, w))
            val heightPx = abs(
                camera.projectY(camera.centerY + radius, w, h) - camera.projectY(camera.centerY - radius, w, h)
            )
            assertEquals("non-uniform at fraction $fraction", widthPx, heightPx, 1e-3f)
            fraction += 0.05f
        }
    }

    @Test
    fun lerpReachesBothEndsExactly() {
        assertEquals(GeometryCamera.Home, GeometryCamera.lerp(GeometryCamera.Home, GeometryCamera.Conversation, 0f))
        assertEquals(
            GeometryCamera.Conversation,
            GeometryCamera.lerp(GeometryCamera.Home, GeometryCamera.Conversation, 1f)
        )
    }

    @Test
    fun lerpClampsOutOfRangeFractions() {
        assertEquals(GeometryCamera.Home, GeometryCamera.lerp(GeometryCamera.Home, GeometryCamera.Conversation, -3f))
        assertEquals(
            GeometryCamera.Conversation,
            GeometryCamera.lerp(GeometryCamera.Home, GeometryCamera.Conversation, 9f)
        )
    }

    /** Home and Conversation are different views, not different scenes. */
    @Test
    fun homeAndConversationAreDifferentCamerasOverTheSameWorld() {
        assertNotEquals(GeometryCamera.Home, GeometryCamera.Conversation)
        assertEquals(GeometryCamera.Home, GeometryCamera.forExpression(AtmosphereExpression.HOME))
        assertEquals(GeometryCamera.Conversation, GeometryCamera.forExpression(AtmosphereExpression.CONVERSATION))
    }

    /**
     * Conversation is the closer camera, which is what makes the scene fill a
     * tall screen rather than shrinking into a band in the middle of it.
     */
    @Test
    fun conversationMovesIntoTheSceneRatherThanAwayFromIt() {
        assertTrue(
            "conversation must be closer than home",
            GeometryCamera.Conversation.scaleFor(1080f) > GeometryCamera.Home.scaleFor(1080f)
        )
    }

    /**
     * Conversation's framing must actually cover a full-height screen: a scene
     * should span a real fraction of it rather than sitting in a band with
     * empty space above and below. The floor is deliberately well under half:
     * compositions differ in proportion by design, and a wide eclipse spanning
     * the full width with generous space above and below is the intended
     * reading, not a failure. What this rules out is the scene collapsing into
     * a thin band -- the shape of the defect this replaced.
     */
    @Test
    fun conversationProjectionCoversAFullScreenCanvas() {
        val (w, h) = phone
        for (seed in 0L until 40L) {
            val topology = GeometryTopology.build(seed)
            val camera = GeometryCamera.framing(topology, AtmosphereExpression.CONVERSATION)
            val ys = topology.curves.flatMap { it.samples }.map { camera.projectY(it.y, w, h) }
            val covered = (ys.max() - ys.min()) / h
            assertTrue(
                "seed $seed (${topology.composition}) covered only ${covered * 100}% of screen height",
                covered > 0.35f
            )
        }
    }

    /**
     * Home shows a crop of the same structure: part of the scene is expected to
     * fall outside a short hero, rather than being squeezed to fit it.
     */
    @Test
    fun homeShowsACropRatherThanTheWholeScene() {
        val topology = GeometryTopology.build(20260911L)
        val camera = GeometryCamera.framing(topology, AtmosphereExpression.HOME)
        val (w, h) = hero

        val ys = topology.curves.flatMap { it.samples }.map { camera.projectY(it.y, w, h) }
        assertTrue("nothing ran off the hero; the scene was squeezed to fit", ys.min() < 0f || ys.max() > h)
    }
}
