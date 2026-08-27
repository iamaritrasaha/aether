package com.foresightlabs.aether.ui.design

import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

/**
 * Reveal geometry for Aether's layered surfaces.
 *
 * Aether composes by moving whole layers past one another: the hero recedes as the
 * conversation sheet rises, the weather hero takes the upper stage as the sheet
 * relaxes. Every one of those layers carries real controls.
 *
 * The rule this file exists to enforce is that a layer's *visible* state and its
 * *interactive* state are the same state. A layer that has faded out cannot keep
 * answering taps at the coordinates it used to occupy, and a layer that has moved
 * must take its touch target and its accessibility node along with its pixels.
 */
object AetherReveal {

    /**
     * Below this opacity a layer no longer reads as present to the user, so it must
     * stop behaving as present: no taps, no accessibility focus.
     *
     * Deliberately well above zero. A control at 12% opacity over a busy atmospheric
     * backdrop is not something a person can aim at, and a tap there is far more
     * likely to be aimed at whatever is behind it.
     */
    const val InteractiveThreshold: Float = 0.25f
}

/**
 * Fades and shifts a layer while keeping its touch and accessibility bounds honest.
 *
 * The vertical shift is applied as a *layout* offset rather than a draw-time
 * translation, so hit testing and semantics follow the pixels. Once [alpha] falls
 * below [interactiveThreshold] the subtree is made inert, because a transform alone
 * would otherwise leave a fully transparent control sitting in front of the content
 * the user can actually see.
 *
 * @param alpha opacity to render the layer at
 * @param verticalShiftPx layout displacement, positive downwards
 * @param interactiveThreshold opacity below which the layer stops accepting input
 */
fun Modifier.aetherReveal(
    alpha: Float,
    verticalShiftPx: Float = 0f,
    interactiveThreshold: Float = AetherReveal.InteractiveThreshold
): Modifier {
    val resolved = alpha.coerceIn(0f, 1f)
    val shift = if (verticalShiftPx == 0f) {
        this
    } else {
        this.offset { IntOffset(0, verticalShiftPx.roundToInt()) }
    }
    val drawn = if (resolved >= 1f) shift else shift.graphicsLayer { this.alpha = resolved }
    return if (resolved < interactiveThreshold) drawn.aetherInert() else drawn
}

/**
 * Makes a subtree unable to receive pointer input or accessibility focus.
 *
 * Pointer events are consumed on the [PointerEventPass.Initial] pass, which runs
 * outermost-first, so descendant `clickable`/`pointerInput` nodes never see an
 * unconsumed change and cannot fire. Semantics are cleared so TalkBack cannot land
 * on a control the user cannot see either.
 */
fun Modifier.aetherInert(): Modifier = this
    .clearAndSetSemantics { }
    .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }
    }
