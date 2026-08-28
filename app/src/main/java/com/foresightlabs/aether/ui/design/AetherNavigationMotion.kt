package com.foresightlabs.aether.ui.design

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.unit.IntOffset
import com.foresightlabs.aether.ui.theme.AetherMotion
import kotlin.math.roundToInt

/**
 * Aether's one route-transition language, so no destination invents its own.
 *
 * Aether lives on a vertical axis. There are exactly two motion families:
 *
 * - **Home ↔ Conversation** is not here at all — it is the connected
 *   foreground-panel expansion/retraction driven by `LocalSceneTransitionProgress`
 *   in `AppNavigation.kt`, and stays that way. It is a single continuous scene,
 *   not two pages trading places.
 * - Every other destination (Settings, Profile, Shared Media, chat detail
 *   pages, search, calls…) is a **secondary page**, and uses [secondaryForwardEnter]
 *   / [secondaryForwardExit] going in and [secondaryBackExit] / [secondaryBackEnter]
 *   coming back — a page rising into place over what was there, and settling
 *   back down when dismissed. Navigation Compose 2.8+ drives these same specs
 *   from predictive-back gesture progress automatically; nothing here needs to
 *   read the gesture itself.
 */
object AetherNavigationMotion {

    /** How far a secondary page travels, as a fraction of its own height. */
    private const val ForwardTravelFraction = 0.12f
    private const val ReducedTravelFraction = 0.035f

    /** How much the page underneath recedes — never blurred, never darkened. */
    private const val RecedeScale = 0.98f
    private const val RecedeAlpha = 0.94f

    private val Spec = tween<Float>(AetherMotion.SurfaceMillis, easing = FastOutSlowInEasing)
    private val OffsetSpec = tween<IntOffset>(AetherMotion.SurfaceMillis, easing = FastOutSlowInEasing)

    /**
     * VERTICAL_RISE (forward, incoming page): rises from moderately below into
     * place while fading in. Never a full-height sheet throw — a fraction of
     * the page's own measured height, not the whole screen.
     */
    fun secondaryForwardEnter(reducedMotion: Boolean): EnterTransition {
        val fraction = if (reducedMotion) ReducedTravelFraction else ForwardTravelFraction
        return slideInVertically(animationSpec = OffsetSpec) { full -> (full * fraction).roundToInt() } +
            fadeIn(animationSpec = Spec)
    }

    /**
     * VERTICAL_RISE (forward, outgoing/underneath page): stays visible and
     * settles only very slightly — the continuity of "this page is still
     * there, just underneath" is the entire point.
     */
    fun secondaryForwardExit(reducedMotion: Boolean): ExitTransition {
        return if (reducedMotion) {
            fadeOut(animationSpec = Spec, targetAlpha = RecedeAlpha)
        } else {
            scaleOut(animationSpec = Spec, targetScale = RecedeScale) +
                fadeOut(animationSpec = Spec, targetAlpha = RecedeAlpha)
        }
    }

    /**
     * VERTICAL_RETREAT (back, outgoing secondary page): retreats downward and
     * fades — the spatial reverse of [secondaryForwardEnter].
     */
    fun secondaryBackExit(reducedMotion: Boolean): ExitTransition {
        val fraction = if (reducedMotion) ReducedTravelFraction else ForwardTravelFraction
        return slideOutVertically(animationSpec = OffsetSpec) { full -> (full * fraction).roundToInt() } +
            fadeOut(animationSpec = Spec)
    }

    /**
     * VERTICAL_RETREAT (back, revealed page underneath): settles back to full
     * scale and presence — the spatial reverse of [secondaryForwardExit].
     */
    fun secondaryBackEnter(reducedMotion: Boolean): EnterTransition {
        return if (reducedMotion) {
            fadeIn(animationSpec = Spec, initialAlpha = RecedeAlpha)
        } else {
            scaleIn(animationSpec = Spec, initialScale = RecedeScale) +
                fadeIn(animationSpec = Spec, initialAlpha = RecedeAlpha)
        }
    }
}
