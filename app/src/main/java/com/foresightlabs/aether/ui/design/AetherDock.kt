package com.foresightlabs.aether.ui.design

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.util.lerp
import androidx.compose.ui.Modifier
import com.foresightlabs.aether.ui.theme.aetherDuration

/**
 * The Aether dock: one dark surface rising from the bottom of the window, which
 * exists in two states.
 *
 * On Home it is expanded and holds the conversations. Opening one collapses it
 * down into the shallow region that carries the composer, and the conversation
 * canvas takes the space it releases. Back expands it again. These are not two
 * dark surfaces on two screens — they are the same surface at two heights, and
 * the transition between them is a bounds morph rather than a page swap.
 *
 * The surface itself is not handed between routes — it is painted once by the
 * scene above the navigation graph and simply uncovered to different heights, so
 * there is no frame in which it does not exist. See [LocalSceneOwnsDock].
 */
object AetherDockDefaults {
    /** Long enough to read as one object moving, short enough to stay responsive. */
    const val MorphMillis: Int = 380
}

/**
 * True when a persistent scene above the navigation graph is already painting the
 * dock, so the screen inside a route must not paint its own.
 *
 * Two routes each painting the same black surface is what made it dip during a
 * cross-fade: one fading out while the other faded in leaves both partly
 * transparent in the middle. With the scene owning it, the surface is literally
 * the same pixels the whole way through and cannot flash. A screen rendered
 * outside the app — a preview, a screenshot, a test — still paints its own, so it
 * looks the same standing alone.
 */
val LocalSceneOwnsDock = compositionLocalOf { false }

/**
 * How far into the Home → Conversation morph the scene currently is: 0 at rest
 * on Home, 1 at rest in a conversation, moving continuously between while the
 * transition runs — including live, frame-by-frame, under a finger during the
 * system back gesture, since it is read directly from the route transition
 * Navigation Compose already drives.
 *
 * Null outside the scene (a screen composed standalone — a preview, a
 * screenshot, a test), where each screen sizes itself exactly as it always did.
 */
val LocalSceneTransitionProgress = compositionLocalOf<Float?> { null }

/**
 * The one pair of numbers the whole morph is built from: how tall the Home hero
 * is at rest, and how tall the conversation canvas is at rest. Every intermediate
 * frame of the transition is a straight interpolation between these two — the
 * same two numbers on both sides of the morph — which is what keeps Home's
 * shrinking panel and Conversation's growing panel exactly the same rectangle
 * throughout rather than two independently-sized shapes that merely resemble
 * each other.
 *
 * Lives in the persistent scene so it survives Home and Conversation being
 * unmounted and remounted by the navigation graph; each screen refreshes its own
 * half of the pair whenever it is genuinely at rest (not mid-transition) and
 * reads both halves whenever it is not.
 */
@Stable
class SceneHeightCache {
    var homeRestPx by mutableFloatStateOf(0f)
    var conversationRestPx by mutableFloatStateOf(0f)
}

val LocalSceneHeightCache = compositionLocalOf<SceneHeightCache?> { null }

/**
 * The shared rectangle height for one frame of the morph.
 *
 * Before either side has ever measured itself the cache reads 0 for that side;
 * [fallbackPx] (the container height scaled by a plausible fraction) stands in
 * so a height is never zero or negative while the real measurement is still on
 * its way — which would otherwise crash the layout it is applied to.
 */
fun SceneHeightCache.edgePx(progress: Float, fallbackPx: Float): Float {
    val home = homeRestPx.takeIf { it > 0f } ?: (fallbackPx * 0.4f)
    val conversation = conversationRestPx.takeIf { it > 0f } ?: (fallbackPx * 0.88f)
    return lerp(home, conversation, progress.coerceIn(0f, 1f))
}

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalDockAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Carries one conversation's avatar across the transition, so the face you tapped
 * on Home is the face that arrives in the conversation header rather than one
 * disappearing while another fades in somewhere else.
 *
 * Keyed by chat, so only the conversation actually being opened matches; every
 * other row on Home simply has no counterpart and animates nothing. Opt-in, so
 * the chat row reused in the forward-target picker never claims the same key.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.aetherChatAvatar(chatId: String?): Modifier {
    if (chatId.isNullOrBlank()) return this
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val animatedScope = LocalDockAnimatedScope.current ?: return this
    val duration = aetherDuration(AetherDockDefaults.MorphMillis)
    return with(sharedScope) {
        this@aetherChatAvatar.sharedElement(
            state = rememberSharedContentState(key = "aether_chat_avatar_$chatId"),
            animatedVisibilityScope = animatedScope,
            boundsTransform = { _, _ -> tween(duration, easing = FastOutSlowInEasing) }
        )
    }
}
