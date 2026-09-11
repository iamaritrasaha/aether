package com.foresightlabs.aether.ui.design

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.min

/** How far the seam sags at the peak of the Home <-> Conversation morph. */
val AetherCurtainSeamBow = 14.dp

/**
 * The bottom edge shared by Home's hero and Conversation's foreground panel.
 *
 * At rest ([bowFraction] == 0, i.e. fully on Home or fully in a conversation)
 * this is exactly a rounded rectangle -- the same shape
 * [androidx.compose.foundation.shape.RoundedCornerShape] already drew here.
 * While the Home <-> Conversation scene is mid-transition, the straight run
 * between the two bottom corners bows outward like a curtain being drawn,
 * then flattens back to that plain rounded rectangle as either side comes to
 * rest. The effect exists only during the morph, never as a resting style
 * change to the everyday Home or Conversation screen.
 */
class AetherCurtainSeamShape(
    private val cornerRadius: Dp,
    private val bowFraction: Float,
    private val maxBow: Dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val radius = with(density) { cornerRadius.toPx() }.coerceAtMost(min(size.width, size.height) / 2f)
        val bow = with(density) { maxBow.toPx() } * bowFraction.coerceIn(0f, 1f)

        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height - radius)
            // Bottom-right corner.
            quadraticTo(size.width, size.height, size.width - radius, size.height)
            // The seam itself: a flat line when bow is 0, a single smooth sag
            // through the middle when it isn't. One quadratic control point
            // placed at twice the target bow puts the curve's actual midpoint
            // exactly at (midX, height + bow).
            if (bow > 0f) {
                val midX = size.width / 2f
                quadraticTo(midX, size.height + bow * 2f, radius, size.height)
            } else {
                lineTo(radius, size.height)
            }
            // Bottom-left corner.
            quadraticTo(0f, size.height, 0f, size.height - radius)
            close()
        }
        return Outline.Generic(path)
    }
}
