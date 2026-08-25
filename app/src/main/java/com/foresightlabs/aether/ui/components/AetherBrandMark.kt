package com.foresightlabs.aether.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foresightlabs.aether.ui.theme.AetherEmber

/**
 * Original Aether Geometric Brand Mark.
 * Renders the minimalist stylized "A" with orbital atmospheric curvature
 * and glowing negative space.
 */
@Composable
fun AetherBrandMark(
    size: Dp = 64.dp,
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(
        Color(0xFFFFA665),
        Color(0xFFFF753A),
        Color(0xFFF04425),
        Color(0xFFC90B27)
    )
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val scaleX = w / 108f
            val scaleY = h / 108f

            // Outer Atmospheric Orbit
            val orbitPath = Path().apply {
                moveTo(31f * scaleX, 64f * scaleY)
                cubicTo(
                    29.5f * scaleX, 58f * scaleY,
                    31f * scaleX, 50f * scaleY,
                    35f * scaleX, 43f * scaleY
                )
                cubicTo(
                    40f * scaleX, 34f * scaleY,
                    50f * scaleX, 28f * scaleY,
                    61f * scaleX, 28f * scaleY
                )
                cubicTo(
                    73f * scaleX, 28f * scaleY,
                    82f * scaleX, 36f * scaleY,
                    84f * scaleX, 47f * scaleY
                )
                cubicTo(
                    85.5f * scaleX, 55f * scaleY,
                    83f * scaleX, 62f * scaleY,
                    78f * scaleX, 67f * scaleY
                )
                cubicTo(
                    74f * scaleX, 71f * scaleY,
                    67f * scaleX, 74f * scaleY,
                    59f * scaleX, 74f * scaleY
                )
                cubicTo(
                    50f * scaleX, 74f * scaleY,
                    43f * scaleX, 71f * scaleY,
                    39f * scaleX, 67f * scaleY
                )
            }
            drawPath(
                path = orbitPath,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0x66FF9A4A),
                        Color(0x99FF7038),
                        Color(0x33F04425)
                    ),
                    start = Offset(30f * scaleX, 28f * scaleY),
                    end = Offset(84f * scaleX, 74f * scaleY)
                ),
                style = Stroke(width = 3.2f * scaleX, cap = StrokeCap.Round)
            )

            // Main "A" Emblem
            val aPath = Path().apply {
                fillType = PathFillType.EvenOdd
                // Outer perimeter
                moveTo(54f * scaleX, 25.5f * scaleY)
                cubicTo(57.5f * scaleX, 25.5f * scaleY, 60.5f * scaleX, 27.5f * scaleY, 62.2f * scaleX, 30.8f * scaleY)
                lineTo(78.8f * scaleX, 63.2f * scaleY)
                cubicTo(80.8f * scaleX, 67.2f * scaleY, 77.8f * scaleX, 72f * scaleY, 73.2f * scaleX, 72f * scaleY)
                cubicTo(70.8f * scaleX, 72f * scaleY, 68.6f * scaleX, 70.6f * scaleY, 67.5f * scaleX, 68.4f * scaleY)
                lineTo(62.8f * scaleX, 59f * scaleY)
                lineTo(45.2f * scaleX, 59f * scaleY)
                lineTo(40.5f * scaleX, 68.4f * scaleY)
                cubicTo(39.4f * scaleX, 70.6f * scaleY, 37.2f * scaleX, 72f * scaleY, 34.8f * scaleX, 72f * scaleY)
                cubicTo(30.2f * scaleX, 72f * scaleY, 27.2f * scaleX, 67.2f * scaleY, 29.2f * scaleX, 63.2f * scaleY)
                lineTo(45.8f * scaleX, 30.8f * scaleY)
                cubicTo(47.5f * scaleX, 27.5f * scaleY, 50.5f * scaleX, 25.5f * scaleY, 54f * scaleX, 25.5f * scaleY)
                close()

                // Inner aperture
                moveTo(54f * scaleX, 37.2f * scaleY)
                lineTo(48.8f * scaleX, 49.5f * scaleY)
                lineTo(59.2f * scaleX, 49.5f * scaleY)
                close()
            }
            drawPath(
                path = aPath,
                brush = Brush.linearGradient(
                    colors = colors,
                    start = Offset(32f * scaleX, 26f * scaleY),
                    end = Offset(76f * scaleX, 72f * scaleY)
                )
            )

            // Wave Cross-Bridge
            val wavePath = Path().apply {
                moveTo(42f * scaleX, 54f * scaleY)
                cubicTo(
                    46f * scaleX, 51.5f * scaleY,
                    51f * scaleX, 50f * scaleY,
                    56f * scaleX, 50f * scaleY
                )
                cubicTo(
                    62f * scaleX, 50f * scaleY,
                    67f * scaleX, 52f * scaleY,
                    71f * scaleX, 55f * scaleY
                )
            }
            drawPath(
                path = wavePath,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFFFFFF),
                        Color(0xE6FFD5BA),
                        Color(0x80FFA07A)
                    ),
                    start = Offset(42f * scaleX, 50f * scaleY),
                    end = Offset(71f * scaleX, 55f * scaleY)
                ),
                style = Stroke(width = 2.5f * scaleX, cap = StrokeCap.Round)
            )

            // Luminous Core Dot
            drawCircle(
                color = Color.White,
                radius = 2.2f * scaleX,
                center = Offset(54f * scaleX, 41.5f * scaleY)
            )
        }
    }
}
