package com.foresightlabs.aether.ui.conversation

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import com.foresightlabs.aether.domain.messages.ConversationMotion
import com.foresightlabs.aether.domain.sharing.SharedAttachmentKind
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.aetherDuration

/**
 * What the Curtain is currently showing.
 *
 * Every one of these is a *state of one surface*, not a screen, panel or sheet.
 * Adding a bottom interaction to Conversation means adding an entry here and a
 * content composable for it — never a new bottom surface. See
 * [AetherConversationCurtain] for the invariants that keeps.
 */
enum class CurtainState {
    COMPOSER,
    ATTACHMENTS,
    EMOJI,
    STICKERS,
    GIFS,
    FORWARDING,
    /** Reviewing one picked photo/video -- caption-free preview plus the View once toggle -- before it sends. */
    MEDIA_PREVIEW,
    /** Reviewing what another application shared into this conversation, before it sends. */
    SHARE_PREVIEW;

    /** True for every state that exposes more Curtain than the resting composer. */
    val isExpanded: Boolean get() = this != COMPOSER

    val isPicker: Boolean get() = this == EMOJI || this == STICKERS || this == GIFS
}

/**
 * One photo or video picked from the gallery or camera, held while the user
 * reviews it in [CurtainState.MEDIA_PREVIEW] and decides whether to send it
 * as View once.
 */
/**
 * What another application shared, once its bytes are in Aether's hands and it
 * is waiting to be reviewed in [CurtainState.SHARE_PREVIEW].
 *
 * Separate from [PendingMedia] on purpose. View once is a decision people make
 * about a photo they picked themselves, in private chats, on a single item; a
 * share carries no such intent and may carry several files at once, so it is
 * never given that flag by default -- there is no field here to set.
 */
@Immutable
data class PendingShare(
    val attachments: List<SharedAttachmentFile>,
    val caption: String = ""
) {
    val isEmpty: Boolean get() = attachments.isEmpty()
}

/** One shared file on disk, ready for the ordinary Telegram send path. */
@Immutable
data class SharedAttachmentFile(
    val path: String,
    val kind: SharedAttachmentKind,
    val name: String? = null
)

@Immutable
data class PendingMedia(
    val path: String,
    val isVideo: Boolean,
    val viewOnce: Boolean = false
)

/**
 * How much Curtain is showing.
 *
 * [livePx] is this frame's exposed height, including any transient expansion, and
 * is what the Conversation foreground must lay out against right now. [restingPx]
 * is the compact composer relationship, and is the only height allowed to become
 * scene geometry — feeding a transient expansion into the scene is what used to
 * retract the foreground into a small card stacked above a second screen.
 */
@Immutable
data class CurtainHeights(val livePx: Int, val restingPx: Int)

object AetherCurtain {
    /** The one stable handle tests and tooling use to find the rear surface. */
    const val TestTag: String = "conversation_curtain"

    /** How much of the window Forwarding exposes: enough for people, no more. */
    const val ForwardingExposure: Float = 0.58f
}

/**
 * The Conversation bottom region is one persistent rear Curtain.
 *
 * Expanded features provide content and state only; they must not create
 * independent feature-sized bottom surfaces. The Conversation foreground owns the
 * rounded seam above it.
 *
 * Concretely, this root — and nothing inside it — owns:
 *  1. the one background, painted edge to edge with square top corners, because a
 *     rounded top edge here would read as a sheet covering the conversation;
 *  2. the bottom (navigation) inset, applied exactly once, inside the background
 *     so the surface still reaches the physical bottom edge;
 *  3. the exposed height and the animation between states;
 *  4. the split between the resting height and a transient expanded one.
 *
 * Content composables passed here are plain layout — rows, columns, lists, text,
 * and small controls with their own shapes. A feature-sized second background
 * inside this slot is the defect this root exists to prevent.
 */
@Composable
fun AetherConversationCurtain(
    state: CurtainState,
    modifier: Modifier = Modifier,
    onHeightChanged: (CurtainHeights) -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val fill: Color = LocalAetherColors.current.background
    val motion = aetherDuration(ConversationMotion.STANDARD_MS)

    var restingPx by remember { mutableIntStateOf(0) }
    // An expanded state collapsing back to the composer passes through heights
    // that are still taller than rest. Those frames are not the resting
    // relationship and must not be recorded as it.
    var settling by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (state != CurtainState.COMPOSER) settling = true
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        val exposed = if (state == CurtainState.FORWARDING) {
            maxHeight * AetherCurtain.ForwardingExposure
        } else {
            null
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                // Outside animateContentSize, so this reports the height actually
                // exposed this frame rather than the settled target.
                .onSizeChanged { size ->
                    if (state == CurtainState.COMPOSER && !settling) {
                        restingPx = size.height
                    } else if (state == CurtainState.COMPOSER && settling &&
                        (restingPx == 0 || size.height <= restingPx)
                    ) {
                        restingPx = size.height
                        settling = false
                    }
                    onHeightChanged(CurtainHeights(livePx = size.height, restingPx = restingPx))
                }
                // Bottom-aligned, so only the upper edge travels: the Curtain is
                // revealed and withdrawn, never slid in as an arriving panel.
                .animateContentSize(
                    animationSpec = tween(motion, easing = FastOutSlowInEasing),
                    alignment = Alignment.BottomCenter
                )
                .then(if (exposed != null) Modifier.height(exposed) else Modifier)
                // The one feature-sized background in the Conversation bottom
                // region. No clip: the seam's rounding belongs to the foreground.
                .background(fill)
                .testTag(AetherCurtain.TestTag)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // The single bottom inset owner. Inside the background, so the
                    // surface still runs to the physical bottom edge.
                    .navigationBarsPadding(),
                content = content
            )
        }
    }
}
