package com.foresightlabs.aether.ui.home.atmosphere

/**
 * A view onto the one geometry world.
 *
 * Geometry is authored once, in a normalised world where `x` and `y` both run
 * roughly `-1..1` and a circle is a circle. Home and Conversation are not two
 * geometries and not two sizes of the same geometry -- they are two *cameras*
 * looking at it. Entering a conversation moves the camera; it never reshapes
 * the scene.
 *
 * This type exists because the previous renderer projected `x` through one
 * scale and `y` through another (`x * sceneScale` against `y * height`), so
 * every circle became an ellipse whose eccentricity depended on the height of
 * whatever container it happened to be drawn in -- which is why Home to
 * Conversation visibly stretched the geometry. A camera has exactly one
 * [scaleFor]: there is no second axis to disagree with.
 *
 * @param centerX world x held at the centre of the viewport.
 * @param centerY world y held at the centre of the viewport.
 * @param halfExtent half the world width mapped across the viewport's width.
 *   Smaller means closer in.
 */
data class GeometryCamera(
    val centerX: Float,
    val centerY: Float,
    val halfExtent: Float
) {
    init {
        require(halfExtent > 0f) { "halfExtent must be positive" }
    }

    /**
     * World units to pixels: one number, used for both axes.
     *
     * Deliberately derived from width alone. The viewport's height then decides
     * only *how much* of the world is visible vertically -- a short hero shows a
     * band of the scene, a full-screen conversation shows far more of the same
     * scene at the same size. That is the entire Home-to-Conversation effect,
     * and it cannot deform anything because height never reaches the scale.
     */
    fun scaleFor(viewportWidth: Float): Float = viewportWidth / (2f * halfExtent)

    /** Screen x for a world x, given the viewport. */
    fun projectX(worldX: Float, viewportWidth: Float): Float =
        viewportWidth / 2f + (worldX - centerX) * scaleFor(viewportWidth)

    /** Screen y for a world y, given the viewport. */
    fun projectY(worldY: Float, viewportWidth: Float, viewportHeight: Float): Float =
        viewportHeight / 2f + (worldY - centerY) * scaleFor(viewportWidth)

    companion object {
        /**
         * Home: a concentrated crop, offset up and left, seen through a short
         * hero band. Most of the scene's width is in frame while its height runs
         * off the top and bottom edges -- a window onto something bigger, which
         * is the intended reading rather than a whole diagram squeezed into a
         * band.
         */
        val Home: GeometryCamera = GeometryCamera(centerX = -0.10f, centerY = -0.16f, halfExtent = 1.05f)

        /**
         * Conversation: the same scene, moved into. The camera is *closer*
         * (smaller extent, so everything is physically larger on screen) and
         * re-centred, and because the conversation canvas is tall the structure
         * now spans the whole screen instead of a band. Pulling back instead
         * would have shrunk the scene into the middle of a tall canvas and left
         * empty space above and below it.
         */
        val Conversation: GeometryCamera = GeometryCamera(centerX = 0.0f, centerY = 0.0f, halfExtent = 0.78f)

        fun forExpression(expression: AtmosphereExpression): GeometryCamera = when (expression) {
            AtmosphereExpression.HOME -> Home
            AtmosphereExpression.CONVERSATION -> Conversation
        }

        /**
         * The framing for [expression], centred on where the scene actually is.
         *
         * Compositions are deliberately asymmetric -- arcs stop short, bodies sit
         * off to one side -- so a camera fixed at the world origin leaves one
         * seed beautifully placed and the next one hugging an edge. Framing on
         * the drawn bounds instead makes every seed sit deliberately, while
         * [Home] and [Conversation] keep their meaning as *offsets and distance*
         * relative to the scene rather than absolute coordinates.
         */
        fun framing(topology: GeometryTopology, expression: AtmosphereExpression): GeometryCamera {
            val preset = forExpression(expression)
            val points = topology.curves.flatMap { it.samples }
            if (points.isEmpty()) return preset

            var minX = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            for (point in points) {
                if (point.x < minX) minX = point.x
                if (point.x > maxX) maxX = point.x
                if (point.y < minY) minY = point.y
                if (point.y > maxY) maxY = point.y
            }

            return preset.copy(
                centerX = (minX + maxX) / 2f + preset.centerX,
                centerY = (minY + maxY) / 2f + preset.centerY
            )
        }

        /**
         * Interpolates between two cameras. Every field moves together and the
         * scale stays a single value throughout, so no intermediate frame of a
         * transition can be non-uniform.
         */
        fun lerp(start: GeometryCamera, end: GeometryCamera, fraction: Float): GeometryCamera {
            val t = fraction.coerceIn(0f, 1f)
            return GeometryCamera(
                centerX = start.centerX + (end.centerX - start.centerX) * t,
                centerY = start.centerY + (end.centerY - start.centerY) * t,
                halfExtent = start.halfExtent + (end.halfExtent - start.halfExtent) * t
            )
        }
    }
}
