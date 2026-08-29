package com.foresightlabs.aether.ui.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foresightlabs.aether.BuildConfig
import com.foresightlabs.aether.domain.model.ConnectionStatus
import com.foresightlabs.aether.ui.theme.LocalReducedMotion
import kotlinx.coroutines.delay

enum class AetherConnectionMoteState(val label: String, val accessibilityLabel: String) {
    STARTING("Starting…", "Starting connection"),
    CONNECTED("Connected", "Aether connected"),
    CONNECTING("Connecting…", "Connecting to Telegram"),
    SYNCING("Syncing…", "Synchronizing messages"),
    OFFLINE("Offline", "Offline"),
    ERROR("Connection error", "Connection error")
}

enum class ConnectionMoteMotion {
    STATIC,
    CONNECTING,
    SYNCING
}

data class ConnectionMotePresentation(
    val raw: ConnectionStatus,
    val state: AetherConnectionMoteState,
    val expanded: Boolean,
    val label: String,
    val accessibilityLabel: String,
    val motion: ConnectionMoteMotion
)

/** Pure presentation policy for the Connection Mote. */
class ConnectionMotePresenter(
    private val connectedAcknowledgementMs: Long = 1_200L,
    private val connectingExpansionMs: Long = 500L,
    private val syncingExpansionMs: Long = 700L,
    private val offlineExpansionMs: Long = 350L,
    private val manualExpansionMs: Long = 2_000L
) {
    private var currentRaw = ConnectionStatus.UNKNOWN
    private var rawSince = 0L
    private var hasInput = false
    private var acknowledgementUntil = 0L
    private var manualExpansionUntil = 0L

    fun present(raw: ConnectionStatus, now: Long): ConnectionMotePresentation {
        if (!hasInput) {
            hasInput = true
            currentRaw = raw
            rawSince = now
        } else if (raw != currentRaw) {
            val previousState = currentRaw.toAetherConnectionMoteState()
            val previousDuration = now - rawSince
            currentRaw = raw
            rawSince = now
            if (raw == ConnectionStatus.READY && shouldAcknowledge(previousState, previousDuration)) {
                acknowledgementUntil = now + connectedAcknowledgementMs
            } else if (raw != ConnectionStatus.READY) {
                acknowledgementUntil = 0L
            }
        }

        val state = raw.toAetherConnectionMoteState()
        val duration = (now - rawSince).coerceAtLeast(0L)
        val expanded = when (state) {
            AetherConnectionMoteState.STARTING -> false
            AetherConnectionMoteState.CONNECTED ->
                now < acknowledgementUntil || now < manualExpansionUntil
            AetherConnectionMoteState.CONNECTING -> duration >= connectingExpansionMs
            AetherConnectionMoteState.SYNCING -> duration >= syncingExpansionMs
            AetherConnectionMoteState.OFFLINE -> duration >= offlineExpansionMs
            AetherConnectionMoteState.ERROR -> true
        }
        return ConnectionMotePresentation(
            raw = raw,
            state = state,
            expanded = expanded,
            label = state.label,
            accessibilityLabel = state.accessibilityLabel,
            motion = when (state) {
                AetherConnectionMoteState.CONNECTING -> ConnectionMoteMotion.CONNECTING
                AetherConnectionMoteState.SYNCING -> ConnectionMoteMotion.SYNCING
                else -> ConnectionMoteMotion.STATIC
            }
        )
    }

    fun tap(now: Long): ConnectionMotePresentation {
        val current = present(currentRaw, now)
        if (current.state == AetherConnectionMoteState.CONNECTED) {
            manualExpansionUntil = now + manualExpansionMs
        }
        return present(currentRaw, now)
    }

    fun nextRefreshDelayMs(now: Long): Long? {
        val current = present(currentRaw, now)
        val elapsed = (now - rawSince).coerceAtLeast(0L)
        return when {
            current.state == AetherConnectionMoteState.CONNECTING && !current.expanded ->
                (connectingExpansionMs - elapsed).coerceAtLeast(1L)
            current.state == AetherConnectionMoteState.SYNCING && !current.expanded ->
                (syncingExpansionMs - elapsed).coerceAtLeast(1L)
            current.state == AetherConnectionMoteState.OFFLINE && !current.expanded ->
                (offlineExpansionMs - elapsed).coerceAtLeast(1L)
            current.state == AetherConnectionMoteState.CONNECTED && current.expanded ->
                listOfNotNull(
                    acknowledgementUntil.takeIf { it > now },
                    manualExpansionUntil.takeIf { it > now }
                ).minOrNull()?.minus(now)?.coerceAtLeast(1L)
            else -> null
        }
    }

    private fun shouldAcknowledge(previous: AetherConnectionMoteState, duration: Long): Boolean {
        return when (previous) {
            AetherConnectionMoteState.OFFLINE -> duration >= offlineExpansionMs
            AetherConnectionMoteState.CONNECTING -> duration >= connectingExpansionMs
            AetherConnectionMoteState.SYNCING -> duration >= syncingExpansionMs
            else -> false
        }
    }
}

fun ConnectionStatus.toAetherConnectionMoteState(): AetherConnectionMoteState = when (this) {
    ConnectionStatus.UNKNOWN -> AetherConnectionMoteState.STARTING
    ConnectionStatus.WAITING_FOR_NETWORK -> AetherConnectionMoteState.OFFLINE
    ConnectionStatus.CONNECTING, ConnectionStatus.CONNECTING_PROXY -> AetherConnectionMoteState.CONNECTING
    ConnectionStatus.UPDATING -> AetherConnectionMoteState.SYNCING
    ConnectionStatus.READY -> AetherConnectionMoteState.CONNECTED
}

private val ConnectedCore = Color(0xFF9ABBB0)
private val SyncingCore = Color(0xFF9D98BA)
private val ConnectingCore = Color(0xFFA4A2B5)
private val OfflineCore = Color(0xFF8B8583)
private val ErrorCore = Color(0xFFC0837E)
private val MoteFrost = Color(0x16FFFFFF)
private val MoteBorder = Color(0x2AFFFFFF)

@Composable
fun AetherConnectionMote(
    rawStatus: ConnectionStatus,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val reducedMotion = LocalReducedMotion.current
    val presenter = remember { ConnectionMotePresenter() }
    val inspection = LocalInspectionMode.current
    val interactionSource = remember { MutableInteractionSource() }
    var refreshTick by remember { mutableIntStateOf(0) }
    val now = android.os.SystemClock.elapsedRealtime()
    val presentation = presenter.present(rawStatus, now)

    LaunchedEffect(rawStatus, presentation.expanded, refreshTick) {
        presenter.nextRefreshDelayMs(android.os.SystemClock.elapsedRealtime())?.let { delayMs ->
            delay(delayMs)
            refreshTick++
        }
    }

    LaunchedEffect(rawStatus, presentation.state, presentation.expanded, presentation.label) {
        if (BuildConfig.DEBUG && !inspection) {
            android.util.Log.d(
                "AetherConnectionMote",
                "CONNECTION_DISPLAY raw=${rawStatus.name} display=${presentation.state.name} " +
                    "expanded=${presentation.expanded} elapsedRealtime=${android.os.SystemClock.elapsedRealtime()}"
            )
        }
    }

    val coreColor by animateColorAsState(
        targetValue = when (presentation.state) {
            AetherConnectionMoteState.CONNECTED -> ConnectedCore
            AetherConnectionMoteState.SYNCING -> SyncingCore
            AetherConnectionMoteState.CONNECTING -> ConnectingCore
            AetherConnectionMoteState.OFFLINE -> OfflineCore
            AetherConnectionMoteState.ERROR -> ErrorCore
            AetherConnectionMoteState.STARTING -> ConnectingCore.copy(alpha = 0.68f)
        },
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "connection_mote_core_color"
    )
    val animatedPhase = if (!reducedMotion && presentation.motion != ConnectionMoteMotion.STATIC) {
        val transition = rememberInfiniteTransition(label = "connection_mote_motion")
        val duration = if (presentation.motion == ConnectionMoteMotion.CONNECTING) 1_400 else 1_900
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = duration, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "connection_mote_phase"
        ).value
    } else 0.5f
    val motionAlpha = if (presentation.motion == ConnectionMoteMotion.CONNECTING) {
        0.82f + 0.18f * animatedPhase
    } else if (presentation.motion == ConnectionMoteMotion.SYNCING) {
        0.86f + 0.14f * animatedPhase
    } else 1f
    val semanticModifier = Modifier.semantics {
        contentDescription = presentation.accessibilityLabel
        onClick(label = if (presentation.state == AetherConnectionMoteState.CONNECTED) "Show connection status" else "Keep connection status visible") {
            presenter.tap(android.os.SystemClock.elapsedRealtime())
            refreshTick++
            true
        }
    }

    Box(
        modifier = modifier
            .widthIn(min = 48.dp)
            .height(48.dp)
            .then(semanticModifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    presenter.tap(android.os.SystemClock.elapsedRealtime())
                    refreshTick++
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        val visualModifier = Modifier
            .animateContentSize(animationSpec = tween(220, easing = FastOutSlowInEasing))
            .then(
                if (presentation.expanded) {
                    Modifier
                        .height(30.dp)
                        .clip(CircleShape)
                        .background(MoteFrost)
                        .border(0.5.dp, MoteBorder, CircleShape)
                        .padding(horizontal = 9.dp)
                } else {
                    // The touch target above is intentionally invisible. In the
                    // calm collapsed state, the visual surface is only the mote.
                    Modifier.size(16.dp)
                }
            )
        Row(
            modifier = visualModifier.then(
                if (presentation.expanded) Modifier.testTag("connection_mote_surface") else Modifier
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            MoteGlyph(
                state = presentation.state,
                core = coreColor,
                alpha = motionAlpha,
                modifier = Modifier.size(16.dp).testTag("connection_mote_visual")
            )
            AnimatedVisibility(
                visible = presentation.expanded,
                enter = fadeIn(animationSpec = tween(180)) ,
                exit = fadeOut(animationSpec = tween(140))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.size(5.dp))
                    Text(
                        text = if (compact) presentation.label.removeSuffix("…") else presentation.label,
                        color = Color(0xFFD8D6E1),
                        fontSize = if (compact) 10.sp else 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun MoteGlyph(
    state: AetherConnectionMoteState,
    core: Color,
    alpha: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        if (state == AetherConnectionMoteState.OFFLINE) {
            drawCircle(
                color = core.copy(alpha = core.alpha * alpha),
                radius = 3.5.dp.toPx(),
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )
        } else {
            drawCircle(
                color = core.copy(alpha = core.alpha * 0.16f * alpha),
                radius = 7.dp.toPx()
            )
            drawCircle(
                color = core.copy(alpha = core.alpha * alpha),
                radius = 3.dp.toPx()
            )
        }
        if (state == AetherConnectionMoteState.SYNCING && alpha > 0.9f) {
            drawLine(
                color = Color(0xFFE2DFEF).copy(alpha = 0.25f),
                start = Offset(5.dp.toPx(), size.height - 3.dp.toPx()),
                end = Offset(size.width - 5.dp.toPx(), size.height - 3.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}
