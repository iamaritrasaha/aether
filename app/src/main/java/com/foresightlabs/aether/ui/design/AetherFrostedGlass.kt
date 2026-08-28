package com.foresightlabs.aether.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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

/**
 * Shared capture state for one atmospheric scene and its floating glass surfaces.
 */
@Stable
class AetherFrostState internal constructor(internal val hazeState: HazeState)

@Composable
fun rememberAetherFrostState(): AetherFrostState = remember { AetherFrostState(HazeState()) }

/** Marks only content behind a header/dock as the backdrop capture source. */
fun Modifier.aetherFrostSource(state: AetherFrostState): Modifier = hazeSource(state.hazeState)

/**
 * Canonical Design Tokens for Aether Glass Material.
 *
 * "The glass has no color. The environment behind the glass gives it color."
 *
 * Optical and physical characteristics:
 * - Transparent material base (zero flat white/black/color tint overlay)
 * - Specular top catch light & bottom ambient bounce
 * - Fine refraction-edge border brush
 * - Soft depth ambient and spot shadows
 */
@Immutable
object AetherGlassTokens {
    val BlurRadius: Dp = 16.dp
    val NoiseFactor: Float = 0.02f

    // Base glass color is strictly transparent — appearance comes from the living backdrop
    val BaseGlassColor: Color = Color.Transparent

    /** Neutral low-fidelity fallback tint used ONLY when hardware backdrop blur is unavailable */
    val FallbackTint: Color = Color(0xD0141416)

    val BorderWidth: Dp = 0.75.dp
    val BorderBrush: Brush = Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.16f),
        0.5f to Color.White.copy(alpha = 0.04f),
        1f to Color.White.copy(alpha = 0.01f)
    )

    val SpecularBrush: Brush = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color.White.copy(alpha = 0.06f),
            0.20f to Color.Transparent,
            0.85f to Color.Transparent,
            1f to Color.White.copy(alpha = 0.01f)
        )
    )

    val AmbientShadowColor: Color = Color.Black.copy(alpha = 0.12f)
    val SpotShadowColor: Color = Color.Black.copy(alpha = 0.16f)

    // Canonical Structural Radii (12-14dp small controls, 18-22dp content/popups, 26-30dp surfaces)
    val BarRadius: Dp = 24.dp
    val DockRadius: Dp = 28.dp
    val PopupRadius: Dp = 18.dp
    val SheetRadius: Dp = 28.dp
    val ControlRadius: Dp = 14.dp
}

/**
 * Canonical Aether Glass Material Primitive.
 *
 * Haze captures the living backdrop and applies hardware RenderEffect blur on supported devices.
 * On unsupported API/runtime it renders [AetherGlassTokens.FallbackTint] for guaranteed legibility.
 *
 * All floating chrome (top bar, dock, composer, menus, sheets, selection toolbar, controls)
 * derives its physical material from this single source of truth.
 */
@Composable
fun AetherGlass(
    frostState: AetherFrostState?,
    modifier: Modifier = Modifier,
    shape: Shape = AetherEmber.Shapes.L,
    elevation: Dp = 6.dp,
    emphasis: Float = 0f,
    content: @Composable BoxScope.() -> Unit
) {
    // Without hardware backdrop blur the material has to fall back to a flat tone.
    val style = remember {
        HazeStyle(
            backgroundColor = Color.Transparent,
            tints = emptyList(), // Zero color/white veil overlay!
            blurRadius = AetherGlassTokens.BlurRadius,
            noiseFactor = AetherGlassTokens.NoiseFactor,
            fallbackTint = HazeTint(AetherGlassTokens.FallbackTint)
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
                ambientColor = AetherGlassTokens.AmbientShadowColor,
                spotColor = AetherGlassTokens.SpotShadowColor
            )
            .clip(shape)
            .then(frostModifier)
            .drawWithCache {
                onDrawWithContent {
                    drawRect(AetherGlassTokens.SpecularBrush)
                    drawContent()
                }
            }
            .border(BorderStroke(AetherGlassTokens.BorderWidth, AetherGlassTokens.BorderBrush), shape),
        content = content
    )
}

/**
 * Convenience structural variants for AetherGlass.
 */
object AetherGlassVariants {
    @Composable
    fun Bar(
        frostState: AetherFrostState?,
        modifier: Modifier = Modifier,
        shape: Shape = RoundedCornerShape(AetherGlassTokens.BarRadius),
        elevation: Dp = 6.dp,
        emphasis: Float = 0f,
        content: @Composable BoxScope.() -> Unit
    ) = AetherGlass(
        frostState = frostState,
        modifier = modifier,
        shape = shape,
        elevation = elevation,
        emphasis = emphasis,
        content = content
    )

    @Composable
    fun Popup(
        frostState: AetherFrostState? = null,
        modifier: Modifier = Modifier,
        shape: Shape = RoundedCornerShape(AetherGlassTokens.PopupRadius),
        elevation: Dp = 8.dp,
        emphasis: Float = 0.2f,
        content: @Composable BoxScope.() -> Unit
    ) = AetherGlass(
        frostState = frostState,
        modifier = modifier,
        shape = shape,
        elevation = elevation,
        emphasis = emphasis,
        content = content
    )

    @Composable
    fun Sheet(
        frostState: AetherFrostState? = null,
        modifier: Modifier = Modifier,
        shape: Shape = RoundedCornerShape(
            topStart = AetherGlassTokens.SheetRadius,
            topEnd = AetherGlassTokens.SheetRadius
        ),
        elevation: Dp = 12.dp,
        emphasis: Float = 0.25f,
        content: @Composable BoxScope.() -> Unit
    ) = AetherGlass(
        frostState = frostState,
        modifier = modifier,
        shape = shape,
        elevation = elevation,
        emphasis = emphasis,
        content = content
    )

    @Composable
    fun Control(
        frostState: AetherFrostState? = null,
        modifier: Modifier = Modifier,
        shape: Shape = CircleShape,
        elevation: Dp = 4.dp,
        emphasis: Float = 0.1f,
        content: @Composable BoxScope.() -> Unit
    ) = AetherGlass(
        frostState = frostState,
        modifier = modifier,
        shape = shape,
        elevation = elevation,
        emphasis = emphasis,
        content = content
    )
}

/**
 * Direct alias to [AetherGlass] for seamless migration across existing call-sites.
 */
@Composable
fun AetherFrostedGlass(
    frostState: AetherFrostState?,
    modifier: Modifier = Modifier,
    shape: Shape = AetherEmber.Shapes.L,
    elevation: Dp = 6.dp,
    emphasis: Float = 0f,
    content: @Composable BoxScope.() -> Unit
) = AetherGlass(
    frostState = frostState,
    modifier = modifier,
    shape = shape,
    elevation = elevation,
    emphasis = emphasis,
    content = content
)
