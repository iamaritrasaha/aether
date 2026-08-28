package com.foresightlabs.aether.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.foresightlabs.aether.ui.theme.LocalReducedMotion

/**
 * Aether's own reading of Telegram/TDLib connection & sync state — never
 * network speed, signal strength, or SMS state.
 */
enum class AetherStatusMoteState(val semanticLabel: String) {
    CONNECTED("Aether connected"),
    SYNCING("Synchronizing messages"),
    CONNECTING("Connecting to Telegram"),
    OFFLINE("Offline"),

    /**
     * Reserved for a meaningful, persistent failure — not a momentary network
     * drop. TDLib's `ConnectionStatus` has no such signal today, so nothing
     * currently maps to this; it exists so a real error source can be wired
     * in later without inventing a fake one now.
     */
    ERROR("Connection error")
}

private val ConnectedCore = Color(0xFF7DBFA3)
private val SyncingCore = Color(0xFF8B84AC)
private val ConnectingCore = Color(0xFFC99A5B)
private val OfflineCore = Color(0xFF565A66)
private val ErrorCore = Color(0xFFBD6E64)

private fun coreColorFor(state: AetherStatusMoteState): Color = when (state) {
    AetherStatusMoteState.CONNECTED -> ConnectedCore
    AetherStatusMoteState.SYNCING -> SyncingCore
    AetherStatusMoteState.CONNECTING -> ConnectingCore
    AetherStatusMoteState.OFFLINE -> OfflineCore
    AetherStatusMoteState.ERROR -> ErrorCore
}

private val MoteTouchArea = 16.dp
private val MoteHaloDiameter = 12.dp
private val MoteCoreRadius = 3.dp

/**
 * A tiny ambient point of light reading Aether's real Telegram connection
 * state — not a control. It sits directly on the atmosphere with no glass,
 * no lens, and no touch target: a ~6dp core inside an optional, extremely
 * faint ~12dp halo, small enough that it should only be noticed on a second
 * look, well after Search, Settings and the greeting.
 *
 * Non-interactive by design: nothing a tap could add beyond what's already
 * exposed through [Modifier.semantics], and no `liveRegion` is set, so
 * TalkBack only reads it when a user explicitly navigates onto it rather
 * than announcing every state change as it happens.
 */
@Composable
fun AetherStatusMote(
    state: AetherStatusMoteState,
    modifier: Modifier = Modifier
) {
    val reducedMotion = LocalReducedMotion.current
    val coreColor by animateColorAsState(
        targetValue = coreColorFor(state),
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "status_mote_core_color"
    )

    // Connected: luminosity breathes, nothing scales or moves.
    val breathing = rememberInfiniteTransition(label = "status_mote_breathing")
    val breathAlpha by breathing.animateFloat(
        initialValue = 1f,
        targetValue = if (!reducedMotion && state == AetherStatusMoteState.CONNECTED) 0.82f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "status_mote_breath_alpha"
    )

    // Connecting: a faster, gentle pulse — trying, not alarmed.
    val pulsing = rememberInfiniteTransition(label = "status_mote_pulsing")
    val pulseAlpha by pulsing.animateFloat(
        initialValue = 1f,
        targetValue = if (!reducedMotion && state == AetherStatusMoteState.CONNECTING) 0.65f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "status_mote_pulse_alpha"
    )

    // Syncing: a brief shimmer that recurs, not a continuous animation and
    // never a spinner or orbit — quiet, occasional activity.
    val shimmering = rememberInfiniteTransition(label = "status_mote_shimmer")
    val shimmerAlpha by shimmering.animateFloat(
        initialValue = 0.68f,
        targetValue = 0.68f,
        animationSpec = infiniteRepeatable(
            animation = if (!reducedMotion && state == AetherStatusMoteState.SYNCING) {
                keyframes {
                    durationMillis = 2800
                    0.68f at 0
                    1f at 500 using FastOutSlowInEasing
                    0.68f at 1100 using FastOutSlowInEasing
                    0.68f at 2800
                }
            } else {
                tween(2800)
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "status_mote_shimmer_alpha"
    )

    val alpha = when (state) {
        AetherStatusMoteState.CONNECTED -> breathAlpha
        AetherStatusMoteState.CONNECTING -> pulseAlpha
        AetherStatusMoteState.SYNCING -> shimmerAlpha
        else -> 1f
    }

    Box(
        modifier = modifier
            .size(MoteTouchArea)
            .semantics { contentDescription = state.semanticLabel }
    ) {
        Canvas(modifier = Modifier.size(MoteTouchArea)) {
            // Extremely faint halo — a hint of glow, never an opaque disc.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        coreColor.copy(alpha = 0.16f * alpha),
                        Color.Transparent
                    ),
                    radius = MoteHaloDiameter.toPx() / 2f
                ),
                radius = MoteHaloDiameter.toPx() / 2f
            )
            drawCircle(
                color = coreColor.copy(alpha = alpha),
                radius = MoteCoreRadius.toPx()
            )
        }
    }
}
