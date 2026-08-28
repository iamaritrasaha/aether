package com.foresightlabs.aether.ui.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How densely the presence strip is drawn.
 *
 * Both densities use canonical Aether spacing and radii; the compact variant simply
 * chooses smaller steps from the same 4dp grid.
 */
enum class PresenceDensity {
    COMFORTABLE,
    COMPACT
}

/** Geometry tokens for the presence strip at each density. */
object PresenceStripTokens {

    fun avatarSize(density: PresenceDensity): Dp = when (density) {
        PresenceDensity.COMFORTABLE -> 60.dp
        PresenceDensity.COMPACT -> 52.dp
    }

    fun itemSpacing(density: PresenceDensity): Dp = when (density) {
        PresenceDensity.COMFORTABLE -> 16.dp
        PresenceDensity.COMPACT -> 12.dp
    }

    fun labelWidth(density: PresenceDensity): Dp = when (density) {
        PresenceDensity.COMFORTABLE -> 64.dp
        PresenceDensity.COMPACT -> 56.dp
    }

    /**
     * Vertical space the whole strip needs — section label, gap, avatar, name and
     * bottom breathing room before the conversation sheet.
     * Used to decide whether the strip fits before it is composed, so the decision
     * cannot feed back into its own measurement.
     */
    fun verticalAllowance(density: PresenceDensity): Dp = when (density) {
        PresenceDensity.COMFORTABLE -> 152.dp
        PresenceDensity.COMPACT -> 132.dp
    }
}

/**
 * The resolved Home layout budget for one frame.
 *
 * @param presence the density to draw the strip at, or null when it genuinely
 * cannot be shown.
 * @param minViewportPx the conversation viewport the sheet anchors must preserve.
 */
@Immutable
data class HomeFit(
    val presence: PresenceDensity?,
    val minViewportPx: Float
)

object HomeLayout {

    /** Preferred conversation viewport: roughly three conversation rows. */
    val PreferredChatViewport: Dp = 232.dp

    /**
     * Absolute floor for the conversation viewport, roughly two rows. Real presence
     * data is worth trading a row for; it is not worth trading the list away.
     */
    val MinimumChatViewport: Dp = 156.dp

    /**
     * Chooses the densest presence presentation that still leaves a usable
     * conversation viewport.
     *
     * Order of preference:
     *  1. comfortable strip, preferred viewport
     *  2. compact strip, preferred viewport
     *  3. compact strip, trading list rows down to [MinimumChatViewport] — a narrow
     *     display is not a reason to drop truthful presence data
     *  4. no strip, only when even the floor cannot be met
     *
     * Horizontal crowding is handled by the strip itself, which scrolls, so this is
     * purely a vertical budget.
     */
    fun resolve(
        containerHeightPx: Float,
        topInsetPx: Float,
        heroCoreHeightPx: Float,
        hasPresence: Boolean,
        comfortableAllowancePx: Float,
        compactAllowancePx: Float,
        preferredViewportPx: Float,
        floorViewportPx: Float
    ): HomeFit {
        if (containerHeightPx <= 0f || heroCoreHeightPx <= 0f || !hasPresence) {
            return HomeFit(presence = null, minViewportPx = preferredViewportPx)
        }

        fun remainingWith(allowancePx: Float): Float =
            containerHeightPx - (topInsetPx + heroCoreHeightPx + allowancePx)

        if (remainingWith(comfortableAllowancePx) >= preferredViewportPx) {
            return HomeFit(PresenceDensity.COMFORTABLE, preferredViewportPx)
        }
        if (remainingWith(compactAllowancePx) >= preferredViewportPx) {
            return HomeFit(PresenceDensity.COMPACT, preferredViewportPx)
        }
        if (remainingWith(compactAllowancePx) >= floorViewportPx) {
            return HomeFit(PresenceDensity.COMPACT, floorViewportPx)
        }
        return HomeFit(presence = null, minViewportPx = preferredViewportPx)
    }
}
