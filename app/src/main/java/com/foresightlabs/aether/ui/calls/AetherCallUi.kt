package com.foresightlabs.aether.ui.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foresightlabs.aether.domain.calls.AudioRoute
import com.foresightlabs.aether.domain.calls.CallPresentationState
import com.foresightlabs.aether.domain.model.ActiveCall
import com.foresightlabs.aether.ui.design.AetherAvatar
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import com.foresightlabs.aether.ui.theme.SpaceGroteskFontFamily

/**
 * The one call visual language.
 *
 * Every active-call surface -- the full-screen scene, the Conversation Curtain,
 * the minimised bar -- draws its identity block, its status line and its
 * controls from here. There is deliberately no second set: a call that looked
 * like one thing in Conversation and another thing full-screen was the reason
 * the calling UI read as unfinished.
 *
 * Restraint is the rule: graphite surfaces, one lavender accent inherited from
 * the atmosphere, hairline borders, and no saturated fills except the single
 * destructive control.
 */
object AetherCallUi {
    /** Hairline border used on every control, so nothing floats unanchored. */
    val ControlBorder = Color(0x1FFFFFFF)

    /** Resting control fill: graphite glass, never a coloured circle. */
    val ControlFill = Color(0x14FFFFFF)

    /** Engaged control fill -- muted, still not a brand colour. */
    val ControlFillActive = Color(0xF2ECEAF2)

    /** The single destructive emphasis in the whole call UI. */
    val EndFill = Color(0xFF8E3B36)

    val ControlSize = 58.dp
    val EndControlSize = 64.dp
}

/**
 * The status line for a call. Never claims more than the call's real state:
 * a duration appears only once the media transport itself reported connected
 * (see [com.foresightlabs.aether.domain.calls.CallStatePresenter]).
 */
fun callStatusText(state: CallPresentationState, durationSec: Int, isVideo: Boolean): String = when (state) {
    CallPresentationState.IDLE -> ""
    CallPresentationState.OUTGOING_REQUEST -> "Calling"
    CallPresentationState.RINGING -> if (isVideo) "Incoming video call" else "Incoming call"
    CallPresentationState.CONNECTING -> "Connecting"
    CallPresentationState.ACTIVE -> formatCallClock(durationSec)
    CallPresentationState.RECONNECTING -> "Reconnecting"
    CallPresentationState.ENDED -> "Call ended"
    CallPresentationState.FAILED -> "Call failed"
}

internal fun formatCallClock(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val minutes = safe / 60
    val seconds = safe % 60
    return "%d:%02d".format(minutes, seconds)
}

/**
 * Avatar, name and status. The identity block of every call surface.
 */
@Composable
fun CallIdentity(
    call: ActiveCall,
    state: CallPresentationState,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val colors = LocalAetherColors.current
    Column(
        modifier = modifier.testTag("call_identity"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AetherAvatar(
            initials = call.user?.avatarInitials ?: "?",
            gradient = call.user?.avatarGradient ?: listOf(Color(0xFF6F6A8A), Color(0xFF3A3750)),
            size = if (compact) 72.dp else 104.dp,
            photoPath = call.user?.photoPath,
            showGlowingRim = state == CallPresentationState.ACTIVE
        )

        Spacer(modifier = Modifier.height(if (compact) 16.dp else 26.dp))

        Text(
            text = call.user?.name ?: "Telegram contact",
            fontFamily = SpaceGroteskFontFamily,
            fontSize = if (compact) 20.sp else 27.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("call_contact_name")
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = callStatusText(state, call.durationSec, call.isVideo),
            fontFamily = ManropeFontFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            // Tracking, not colour, carries the state: a status line that turned
            // green would be the loudest thing on a deliberately quiet screen.
            letterSpacing = 0.9.sp,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("call_state_text")
        )
    }
}

/** One circular control. The only shape in the call UI's control model. */
@Composable
fun CallControl(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    engaged: Boolean = false,
    destructive: Boolean = false
) {
    val colors = LocalAetherColors.current
    val diameter = if (destructive) AetherCallUi.EndControlSize else AetherCallUi.ControlSize
    val fill = when {
        destructive -> AetherCallUi.EndFill
        engaged -> AetherCallUi.ControlFillActive
        else -> AetherCallUi.ControlFill
    }
    val tint = when {
        destructive -> Color.White
        engaged -> Color(0xFF141319)
        else -> colors.textPrimary
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(diameter)
                .clip(CircleShape)
                .background(fill)
                .border(0.5.dp, AetherCallUi.ControlBorder, CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = diameter / 2),
                    onClick = onClick
                )
                .semantics { contentDescription = label }
                .testTag(testTag),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(23.dp)
            )
        }
        Spacer(modifier = Modifier.height(9.dp))
        Text(
            text = label,
            fontFamily = ManropeFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textSecondary
        )
    }
}

/**
 * The control model for an active call, shared by every surface that offers
 * controls. Camera controls appear only for a call that actually carries video.
 */
@Composable
fun CallControlRow(
    isMuted: Boolean,
    audioRoute: AudioRoute,
    isVideoCall: Boolean,
    isCameraEnabled: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier,
    showControls: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .testTag("call_control_row"),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Top
    ) {
        if (showControls) {
            CallControl(
                icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                label = if (isMuted) "Unmute" else "Mute",
                onClick = onToggleMute,
                engaged = isMuted,
                testTag = "call_mute_button"
            )
            CallControl(
                icon = if (audioRoute == AudioRoute.SPEAKER) {
                    Icons.AutoMirrored.Filled.VolumeUp
                } else {
                    Icons.Default.Hearing
                },
                label = if (audioRoute == AudioRoute.SPEAKER) "Speaker" else "Earpiece",
                onClick = onToggleSpeaker,
                engaged = audioRoute == AudioRoute.SPEAKER,
                testTag = "call_speaker_button"
            )
            if (isVideoCall) {
                CallControl(
                    icon = if (isCameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                    label = "Camera",
                    onClick = onToggleCamera,
                    engaged = isCameraEnabled,
                    testTag = "call_camera_button"
                )
                if (isCameraEnabled) {
                    CallControl(
                        icon = Icons.Default.Cameraswitch,
                        label = "Flip",
                        onClick = onSwitchCamera,
                        testTag = "call_switch_camera_button"
                    )
                }
            }
        }
        // End is always offered, even when nothing else is: a stuck or failed
        // call must always be dismissible.
        CallControl(
            icon = Icons.Default.CallEnd,
            label = "End",
            onClick = onEndCall,
            destructive = true,
            testTag = "call_end_button"
        )
    }
}

/**
 * Answer / decline for an incoming call. Deliberately only two controls, in the
 * same control model as everything else.
 */
@Composable
fun IncomingCallControls(
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp)
            .testTag("incoming_call_controls"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        CallControl(
            icon = Icons.Default.CallEnd,
            label = "Decline",
            onClick = onDecline,
            destructive = true,
            testTag = "decline_call_button"
        )
        CallControl(
            icon = Icons.Default.Call,
            label = "Answer",
            onClick = onAnswer,
            engaged = true,
            testTag = "accept_call_button"
        )
    }
}
