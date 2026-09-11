package com.foresightlabs.aether.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import com.foresightlabs.aether.domain.calls.AudioRoute
import com.foresightlabs.aether.ui.calls.CallControlRow
import com.foresightlabs.aether.ui.calls.callStatusText
import com.foresightlabs.aether.domain.calls.CallPresentationState
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.ManropeFontFamily

/**
 * The Conversation header's call action. Rendered only when the caller has
 * already decided the conversation is eligible ([com.foresightlabs.aether.domain.calls.CallEligibility])
 * and the feature is enabled -- there is no disabled/greyed-out state, because a
 * call action visible for an ineligible conversation is exactly what
 * [com.foresightlabs.aether.domain.calls.CallEligibility] exists to prevent.
 */
@Composable
fun ConversationCallButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 24.dp),
                onClick = onClick
            )
            .semantics { this.contentDescription = "Call" }
            .testTag("conversation_call_button"),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0x22FFFFFF))
                .border(width = 0.5.dp, color = Color(0x18FFFFFF), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Call,
                contentDescription = null,
                tint = LocalAetherColors.current.textPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** The Conversation header's video call action -- the same affordance as [ConversationCallButton], distinguished by icon so voice and video are never conflated. */
@Composable
fun ConversationVideoCallButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 24.dp),
                onClick = onClick
            )
            .semantics { this.contentDescription = "Video call" }
            .testTag("conversation_video_call_button"),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0x22FFFFFF))
                .border(width = 0.5.dp, color = Color(0x18FFFFFF), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Videocam,
                contentDescription = null,
                tint = LocalAetherColors.current.textPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * The truthful, presentation-only label for a call state, delegating to the one
 * definition in [callStatusText] so Conversation and the full-screen call
 * surface can never word the same state differently.
 */
fun callStatusLabel(state: CallPresentationState, durationSec: Int): String =
    callStatusText(state, durationSec, isVideo = false)

/**
 * The upper Conversation foreground's call identity, replacing the ordinary
 * message content while [state] is anything other than idle -- the same scene,
 * adapted, never a second screen. Duration text is only ever built from
 * [durationSec], which the caller must not advance before the real media
 * transport reports CONNECTED (see [CallPresentationState.ACTIVE] and
 * [com.foresightlabs.aether.domain.calls.CallStatePresenter.durationShouldRun]) --
 * this composable has no way to fake one on its own.
 */
@Composable
fun ConversationCallIdentityBanner(
    name: String,
    state: CallPresentationState,
    durationSec: Int,
    modifier: Modifier = Modifier
) {
    val colors = LocalAetherColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
            .testTag("conversation_call_banner"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = name,
            fontFamily = ManropeFontFamily,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = callStatusLabel(state, durationSec),
            fontFamily = ManropeFontFamily,
            fontSize = 14.sp,
            color = colors.textSecondary
        )
    }
}

/**
 * The Curtain's call state -- direct content on the one persistent surface,
 * never a second sheet. Controls only appear when [isMediaTransportAvailable];
 * end is always available so a stuck or failed call can still be dismissed.
 */
@Composable
fun ConversationCallCurtainContent(
    isMuted: Boolean,
    audioRoute: AudioRoute,
    isMediaTransportAvailable: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier,
    isVideoCall: Boolean = false,
    isCameraEnabled: Boolean = false,
    onToggleCamera: () -> Unit = {},
    onSwitchCamera: () -> Unit = {}
) {
    // The same control model the full-screen call surface uses. Conversation is
    // not a second design of the calling feature; it is the same one, in the
    // Curtain.
    CallControlRow(
        isMuted = isMuted,
        audioRoute = audioRoute,
        isVideoCall = isVideoCall,
        isCameraEnabled = isCameraEnabled,
        onToggleMute = onToggleMute,
        onToggleSpeaker = onToggleSpeaker,
        onToggleCamera = onToggleCamera,
        onSwitchCamera = onSwitchCamera,
        onEndCall = onEndCall,
        showControls = isMediaTransportAvailable,
        modifier = modifier
            .padding(vertical = 14.dp)
            .testTag("conversation_call_curtain")
    )
}
