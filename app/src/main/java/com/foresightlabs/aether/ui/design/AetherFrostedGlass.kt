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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.LocalAtmosphere
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
    val atmosphere = LocalAtmosphere.current
    val resolvedEmphasis = emphasis.coerceIn(0f, 1f)

    val neutralVeil = when {
        !colors.isDark -> Color(0xFFFBFAF8)
        colors.isOled -> Color(0xFF050507)
        else -> Color(0xFF111116)
    }
    val tintAlpha = when {
        !colors.isDark -> 0.28f + resolvedEmphasis * 0.06f
        colors.isOled -> 0.36f + resolvedEmphasis * 0.07f
        else -> 0.32f + resolvedEmphasis * 0.07f
    }
    val fallbackAlpha = when {
        !colors.isDark -> 0.88f
        colors.isOled -> 0.84f
        else -> 0.80f
    }
    val style = remember(colors.isDark, colors.isOled, atmosphere.accent, resolvedEmphasis) {
        HazeStyle(
            backgroundColor = neutralVeil,
            tints = listOf(
                HazeTint(neutralVeil.copy(alpha = tintAlpha)),
                HazeTint(atmosphere.accent.copy(alpha = 0.055f + resolvedEmphasis * 0.025f))
            ),
            blurRadius = 22.dp,
            noiseFactor = 0.08f,
            fallbackTint = HazeTint(neutralVeil.copy(alpha = fallbackAlpha))
        )
    }
    val edgeBrush = remember(colors.isDark, atmosphere.accent, resolvedEmphasis) {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = if (colors.isDark) 0.22f else 0.58f),
                atmosphere.accent.copy(alpha = 0.15f + resolvedEmphasis * 0.05f),
                if (colors.isDark) Color.White.copy(alpha = 0.07f)
                else Color.Black.copy(alpha = 0.10f)
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
                ambientColor = Color.Black.copy(alpha = if (colors.isDark) 0.24f else 0.12f),
                spotColor = atmosphere.shadow.copy(alpha = if (colors.isDark) 0.30f else 0.16f)
            )
            .clip(shape)
            .then(frostModifier)
            .drawWithCache {
                val specular = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.White.copy(alpha = if (colors.isDark) 0.10f else 0.22f),
                        0.24f to Color.Transparent,
                        0.76f to Color.Transparent,
                        1f to Color.Black.copy(alpha = if (colors.isDark) 0.08f else 0.025f)
                    )
                )
                val refraction = Brush.linearGradient(
                    colors = listOf(
                        atmosphere.accent.copy(alpha = 0.045f),
                        Color.Transparent,
                        atmosphere.glow.copy(alpha = 0.025f)
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )
                onDrawWithContent {
                    drawContent()
                    drawRect(refraction)
                    drawRect(specular)
                }
            }
            .border(BorderStroke(1.dp, edgeBrush), shape),
        content = content
    )
}
