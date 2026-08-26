package com.foresightlabs.aether.ui.design

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.foresightlabs.aether.ui.theme.AetherMotion
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.LocalReducedMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The three meaningful resting positions of the Aether foreground sheet.
 *
 * These are positions of a physical object, not screen fractions.
 */
enum class SheetAnchor {
    /** Relaxed. More atmosphere and hero visible. */
    PEEK,

    /** Balanced default composition. */
    RESTING,

    /** Conversations take almost the full usable height. */
    EXPANDED
}

/**
 * Resolved anchor offsets, measured in pixels from the top of the sheet's container
 * to the sheet's top edge. Smaller offset means more expanded.
 */
@Immutable
data class SheetAnchors(
    val expanded: Float,
    val resting: Float,
    val peek: Float
) {
    val isResolved: Boolean get() = peek > expanded

    fun offsetFor(anchor: SheetAnchor): Float = when (anchor) {
        SheetAnchor.PEEK -> peek
        SheetAnchor.RESTING -> resting
        SheetAnchor.EXPANDED -> expanded
    }

    companion object {
        val Unresolved = SheetAnchors(0f, 0f, 0f)

        /**
         * Derives anchors adaptively from what was actually measured, never from a
         * fixed screen split.
         *
         * @param containerHeightPx usable height the sheet lives in
         * @param heroBottomPx measured bottom edge of the hero content
         * @param topInsetPx safe top inset
         * @param minChatViewportPx smallest conversation viewport still worth showing
         * @param minAtmosphereRevealPx atmosphere that stays visible when expanded
         * @param relaxedExtraPx extra atmosphere revealed in the relaxed position
         */
        fun derive(
            containerHeightPx: Float,
            heroBottomPx: Float,
            topInsetPx: Float,
            minChatViewportPx: Float,
            minAtmosphereRevealPx: Float,
            relaxedExtraPx: Float
        ): SheetAnchors {
            if (containerHeightPx <= 0f) return Unresolved
            // The sheet may never collapse past a usable conversation viewport.
            val lowestOffset = (containerHeightPx - minChatViewportPx).coerceAtLeast(0f)
            val expanded = (topInsetPx + minAtmosphereRevealPx).coerceIn(0f, lowestOffset)
            val resting = heroBottomPx.coerceIn(expanded, lowestOffset)
            val peek = (heroBottomPx + relaxedExtraPx).coerceIn(resting, lowestOffset)
            return SheetAnchors(expanded = expanded, resting = resting, peek = peek)
        }
    }
}

/**
 * State of a physical foreground sheet: it follows the finger, settles onto anchors
 * with mass, and hands scrolling over to its inner list once fully expanded.
 */
@Stable
class AetherSheetState internal constructor(
    private val scope: CoroutineScope,
    initialAnchor: SheetAnchor,
    private val reducedMotion: Boolean
) {
    var anchors: SheetAnchors by mutableStateOf(SheetAnchors.Unresolved)
        private set

    /** Pixels from the top of the container to the sheet's top edge. */
    var offset: Float by mutableFloatStateOf(Float.NaN)
        private set

    var currentAnchor: SheetAnchor by mutableStateOf(initialAnchor)
        private set

    private var settleJob: Job? = null

    val isResolved: Boolean get() = anchors.isResolved && !offset.isNaN()

    /** 0 at the resting position or lower, 1 when fully expanded. */
    val expandProgress: Float
        get() {
            val a = anchors
            if (!isResolved) return 0f
            val span = a.resting - a.expanded
            if (span <= 0f) return if (offset <= a.expanded) 1f else 0f
            return ((a.resting - offset) / span).coerceIn(0f, 1f)
        }

    /** 0 at the resting position or higher, 1 at the fully relaxed position. */
    val relaxProgress: Float
        get() {
            val a = anchors
            if (!isResolved) return 0f
            val span = a.peek - a.resting
            if (span <= 0f) return 0f
            return ((offset - a.resting) / span).coerceIn(0f, 1f)
        }

    /** How far the sheet's bottom currently extends past the container. */
    val bottomOverflow: Float
        get() = if (isResolved) (offset - anchors.expanded).coerceAtLeast(0f) else 0f

    internal fun updateAnchors(next: SheetAnchors) {
        if (!next.isResolved || next == anchors) return
        val hadAnchors = anchors.isResolved
        anchors = next
        if (offset.isNaN() || !hadAnchors) {
            offset = next.offsetFor(currentAnchor)
        } else if (settleJob?.isActive != true) {
            // Layout changed while at rest: stay on the same anchor, not the same pixel.
            offset = next.offsetFor(currentAnchor)
        } else {
            offset = offset.coerceIn(next.expanded, next.peek)
        }
    }

    /** Moves the sheet by [delta] px, returning how much was actually consumed. */
    fun drag(delta: Float): Float {
        if (!isResolved || delta == 0f) return 0f
        settleJob?.cancel()
        val next = (offset + delta).coerceIn(anchors.expanded, anchors.peek)
        val consumed = next - offset
        offset = next
        return consumed
    }

    /** Settles onto the anchor implied by the current position and [velocity]. */
    fun settle(velocity: Float) {
        if (!isResolved) return
        animateTo(nearestAnchor(velocity), velocity)
    }

    fun animateTo(anchor: SheetAnchor, initialVelocity: Float = 0f) {
        currentAnchor = anchor
        if (!isResolved) return
        settleJob?.cancel()
        val target = anchors.offsetFor(anchor)
        if (reducedMotion) {
            offset = target
            return
        }
        settleJob = scope.launch {
            animate(
                initialValue = offset,
                targetValue = target,
                initialVelocity = initialVelocity,
                animationSpec = AetherMotion.SheetSettle
            ) { value, _ -> offset = value }
        }
    }

    fun snapTo(anchor: SheetAnchor) {
        settleJob?.cancel()
        currentAnchor = anchor
        if (anchors.isResolved) offset = anchors.offsetFor(anchor)
    }

    private fun nearestAnchor(velocity: Float): SheetAnchor {
        val candidates = listOf(
            SheetAnchor.EXPANDED to anchors.expanded,
            SheetAnchor.RESTING to anchors.resting,
            SheetAnchor.PEEK to anchors.peek
        )
        return when {
            // Moving up: settle on the next anchor above the current position.
            velocity < -FLING_VELOCITY_PX_PER_SEC ->
                candidates.filter { it.second < offset - 1f }.maxByOrNull { it.second }?.first
                    ?: SheetAnchor.EXPANDED
            // Moving down: settle on the next anchor below.
            velocity > FLING_VELOCITY_PX_PER_SEC ->
                candidates.filter { it.second > offset + 1f }.minByOrNull { it.second }?.first
                    ?: SheetAnchor.PEEK
            else -> candidates.minByOrNull { abs(it.second - offset) }?.first ?: SheetAnchor.RESTING
        }
    }

    /**
     * Couples the sheet to its inner scrollable.
     *
     * Upward: the sheet follows the finger to the expanded anchor, and only then does
     * the inner list consume scrolling. Downward: the list returns to its top first,
     * and continued downward gesture pulls the sheet back down.
     *
     * Only vertical deltas are touched, so horizontal chip scrolling is unaffected.
     */
    fun nestedScrollConnection(): NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput) return Offset.Zero
            if (available.y >= 0f) return Offset.Zero
            return Offset(0f, drag(available.y))
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            if (source != NestedScrollSource.UserInput) return Offset.Zero
            if (available.y <= 0f) return Offset.Zero
            return Offset(0f, drag(available.y))
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (available.y < 0f && isResolved && offset > anchors.expanded) {
                settle(available.y)
                return Velocity(0f, available.y)
            }
            return Velocity.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (available.y != 0f) {
                settle(available.y)
                return available
            }
            settle(0f)
            return Velocity.Zero
        }
    }

    private companion object {
        const val FLING_VELOCITY_PX_PER_SEC = 220f
    }
}

@Composable
fun rememberAetherSheetState(
    initialAnchor: SheetAnchor = SheetAnchor.RESTING
): AetherSheetState {
    val scope = rememberCoroutineScope()
    val reducedMotion = LocalReducedMotion.current
    return remember(scope, reducedMotion) {
        AetherSheetState(scope, initialAnchor, reducedMotion)
    }
}

/** Default sheet geometry, expressed as tokens rather than magic numbers per screen. */
object AetherSheetDefaults {
    val RelaxedCornerRadius: Dp = 34.dp
    val ExpandedCornerRadius: Dp = 22.dp
    val MinChatViewport: Dp = 232.dp
    val MinAtmosphereReveal: Dp = 76.dp
    val RelaxedExtraAtmosphere: Dp = 84.dp
    val HandleWidth: Dp = 36.dp
    val HandleHeight: Dp = 4.dp
}

/**
 * Layer 3 foreground sheet. Near-black, physically draggable, shape-interpolating.
 *
 * The sheet is measured at its tallest and repositioned by placement offset, so
 * dragging does not force a re-measure of the conversation list every frame.
 */
@Composable
fun AetherSheet(
    state: AetherSheetState,
    containerHeightPx: Float,
    modifier: Modifier = Modifier,
    label: String = "Conversations",
    showHandle: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val density = LocalDensity.current
    val colors = LocalAetherColors.current
    val anchors = state.anchors

    val sheetHeightPx = (containerHeightPx - anchors.expanded).coerceAtLeast(0f)
    val sheetHeight = with(density) { sheetHeightPx.toDp() }

    val cornerRadius = lerp(
        AetherSheetDefaults.RelaxedCornerRadius,
        AetherSheetDefaults.ExpandedCornerRadius,
        state.expandProgress
    )
    val shape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius)

    val dragState = rememberDraggableState { delta -> state.drag(delta) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(sheetHeight)
            .offset { IntOffset(0, if (state.isResolved) state.offset.roundToInt() else 0) }
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.border, shape)
            .semantics {
                contentDescription = label
                customActions = buildList {
                    if (state.currentAnchor != SheetAnchor.EXPANDED) {
                        add(
                            CustomAccessibilityAction("Expand conversations") {
                                state.animateTo(SheetAnchor.EXPANDED); true
                            }
                        )
                    }
                    if (state.currentAnchor != SheetAnchor.RESTING) {
                        add(
                            CustomAccessibilityAction("Balance conversations") {
                                state.animateTo(SheetAnchor.RESTING); true
                            }
                        )
                    }
                    if (state.currentAnchor != SheetAnchor.PEEK) {
                        add(
                            CustomAccessibilityAction("Collapse conversations") {
                                state.animateTo(SheetAnchor.PEEK); true
                            }
                        )
                    }
                }
            }
    ) {
        if (showHandle) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity -> state.settle(velocity) }
                    )
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(
                            width = AetherSheetDefaults.HandleWidth,
                            height = AetherSheetDefaults.HandleHeight
                        )
                        .clip(RoundedCornerShape(percent = 50))
                        .background(colors.surfaceHighlight)
                )
            }
        }
        content()
    }
}
