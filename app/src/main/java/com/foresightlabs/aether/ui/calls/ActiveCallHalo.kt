package com.foresightlabs.aether.ui.calls

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.foresightlabs.aether.ui.theme.LocalAetherColors

/**
 * The active-call perimeter: a thin gradient highlight travelling continuously
 * around the outer edge of a control that owns the active call.
 *
 * Aether-specific treatment, not a spinner: the ring is hairline-thin, mostly
 * quiet, and carries ONE strong head with a faint trailing tail drifting
 * around the perimeter at walking pace -- energy without urgency. Colours come
 * from the app's own accent, so the treatment follows the theme (dark/light)
 * instead of importing an external brand.
 *
 * GPU-friendly by construction: one remembered [Brush.sweepGradient], a
 * canvas rotation, and zero per-frame allocations; no bitmaps, no coroutines.
 *
 * With reduced motion (animator duration scale 0) the animation is replaced
 * by a tasteful static accent ring -- the state stays legible, nothing moves.
 */
fun Modifier.activeCallPerimeter(active: Boolean): Modifier = composed {
    if (!active) return@composed this

    val colors = LocalAetherColors.current
    val reducedMotion = com.foresightlabs.aether.ui.conversation.rememberIsReducedMotion()

    if (reducedMotion) {
        return@composed drawBehind {
            val stroke = 2.dp.toPx()
            drawCircle(
                color = colors.accent.copy(alpha = 0.85f),
                radius = size.minDimension / 2f - stroke / 2f,
                center = Offset(size.width / 2f, size.height / 2f),
                style = Stroke(width = stroke)
            )
        }
    }

    val transition = rememberInfiniteTransition(label = "active_call_perimeter")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3400, easing = LinearEasing)
        ),
        label = "active_call_perimeter_sweep"
    )
    // Remembered once per palette: the rotation provides the travel, so the
    // brush itself is never rebuilt per frame.
    val brush = remember(colors) {
        Brush.sweepGradient(
            0.00f to colors.accent.copy(alpha = 0.0f),
            0.10f to colors.accent.copy(alpha = 0.95f),
            0.16f to colors.accent.copy(alpha = 0.0f),
            0.55f to Color.Transparent,
            0.68f to colors.accent.copy(alpha = 0.30f),
            0.76f to colors.accent.copy(alpha = 0.0f),
            1.00f to Color.Transparent
        )
    }

    drawBehind {
        val stroke = 2.dp.toPx()
        val radius = size.minDimension / 2f - stroke / 2f
        rotate(degrees = sweep) {
            drawCircle(
                brush = brush,
                radius = radius,
                center = Offset(size.width / 2f, size.height / 2f),
                style = Stroke(width = stroke)
            )
        }
    }
}
