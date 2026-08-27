package com.foresightlabs.aether.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/** Shared capture state for one atmospheric scene and its small floating frost surfaces. */
@Stable
class AetherFrostState internal constructor(internal val hazeState: HazeState)

@Composable
fun rememberAetherFrostState(): AetherFrostState = remember { AetherFrostState(HazeState()) }

/** Marks only content behind a header/dock as the backdrop capture source. */
fun Modifier.aetherFrostSource(state: AetherFrostState): Modifier = hazeSource(state.hazeState)

/**
 * Canonical real frosted material for Aether's top header and bottom dock.
 *
 * Haze captures a sibling backdrop and uses Android RenderEffect on supported hardware. On an
 * unsupported API/runtime it draws [HazeStyle.fallbackTint], so navigation remains readable
 * without applying Modifier.blur() to the controls themselves.
 */
@Composable
fun AetherFrostedGlass(
    frostState: AetherFrostState?,
    modifier: Modifier = Modifier,
    shape: Shape = AetherEmber.Shapes.L,
    elevation: Dp = 6.dp,
    emphasis: Float = 0f,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = LocalAetherColors.current
    val resolvedEmphasis = emphasis.coerceIn(0f, 1f)

    val neutralVeil = when {
        !colors.isDark -> Color(0xFFFBFAF8)
        else -> Color(0xFF111116)
    }
    val tintAlpha = when {
        !colors.isDark -> 0.08f + resolvedEmphasis * 0.04f
        else -> 0.12f + resolvedEmphasis * 0.05f
    }
    val fallbackAlpha = when {
        !colors.isDark -> 0.94f
        else -> 0.82f
    }
    val style = remember(colors.isDark, resolvedEmphasis) {
        HazeStyle(
            backgroundColor = neutralVeil,
            tints = listOf(
                HazeTint(neutralVeil.copy(alpha = tintAlpha))
            ),
            blurRadius = 24.dp,
            noiseFactor = 0.06f,
            fallbackTint = HazeTint(neutralVeil.copy(alpha = fallbackAlpha))
        )
    }
    val edgeBrush = remember(colors.isDark, resolvedEmphasis) {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = if (colors.isDark) 0.18f else 0.45f),
                Color.White.copy(alpha = if (colors.isDark) 0.08f else 0.15f),
                if (colors.isDark) Color.White.copy(alpha = 0.04f)
                else Color.Black.copy(alpha = 0.12f)
            )
        )
    }
    val frostModifier = if (frostState != null) {
        Modifier.hazeEffect(state = frostState.hazeState, style = style)
    } else {
        Modifier.drawWithCache {
            onDrawBehind { drawRect(style.fallbackTint.color) }
        }
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = if (colors.isDark) 0.20f else 0.14f),
                spotColor = Color.Black.copy(alpha = if (colors.isDark) 0.25f else 0.18f)
            )
            .clip(shape)
            .then(frostModifier)
            .drawWithCache {
                val specular = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.White.copy(alpha = if (colors.isDark) 0.08f else 0.15f),
                        0.24f to Color.Transparent,
                        0.76f to Color.Transparent,
                        1f to Color.Black.copy(alpha = if (colors.isDark) 0.06f else 0.04f)
                    )
                )
                onDrawWithContent {
                    drawRect(specular)
                    drawContent()
                }
            }
            .border(BorderStroke(1.dp, edgeBrush), shape),
        content = content
    )
}
