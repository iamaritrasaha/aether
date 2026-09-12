package com.foresightlabs.aether.ui.calls

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foresightlabs.aether.calls.media.CallDiagnostics
import com.foresightlabs.aether.calls.media.CallStage
import com.foresightlabs.aether.calls.media.DecodedVideoFrame
import com.foresightlabs.aether.domain.calls.AudioRoute
import com.foresightlabs.aether.domain.calls.CallPresentationState
import com.foresightlabs.aether.domain.calls.CallStatePresenter
import com.foresightlabs.aether.domain.model.ActiveCall
import com.foresightlabs.aether.domain.model.CallStateEnum
import com.foresightlabs.aether.ui.home.atmosphere.AetherTimeAtmosphere
import com.foresightlabs.aether.ui.home.atmosphere.AtmosphereExpression
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.ManropeFontFamily

/**
 * The one full-screen call surface: outgoing, incoming, voice and video.
 *
 * It is a scene, not a dashboard -- the Aether atmosphere and its mathematical
 * geometry continue behind the call, identity sits high with the screen's
 * quietest region beneath it, and controls sit in a single row at the bottom.
 * Every control and every label comes from [AetherCallUi], the same ones the
 * Conversation Curtain uses, so the two surfaces are one system rather than two
 * designs of the same feature.
 */
@Composable
fun AetherCallScreen(
    activeCall: ActiveCall?,
    onAcceptCall: (Int) -> Unit,
    onDiscardCall: (Int) -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onMinimize: () -> Unit,
    modifier: Modifier = Modifier,
    remoteVideoFrame: DecodedVideoFrame? = null,
    localVideoFrame: DecodedVideoFrame? = null,
    isCameraEnabled: Boolean = false,
    onToggleCamera: () -> Unit = {},
    onSwitchCamera: () -> Unit = {}
) {
    if (activeCall == null) return

    val colors = LocalAetherColors.current
    // Keyed explicitly by exactly the fields present() reads -- never by the
    // whole activeCall instance, which also changes every second from the
    // duration ticker and would otherwise force presentation to look stale
    // (or force a needless recompute) on every tick that isn't its business.
    val presentation = remember(activeCall.state, activeCall.mediaState, activeCall.isOutgoing) {
        CallStatePresenter.present(activeCall.state, activeCall.mediaState, activeCall.isOutgoing, activeCall.mediaEverConnected)
    }
    val isIncomingPending = !activeCall.isOutgoing && activeCall.state == CallStateEnum.PENDING
    // Video is only ever the dominant surface once a real decoded frame exists;
    // before that the call keeps the Aether scene rather than turning black.
    val showsRemoteVideo = activeCall.isVideo &&
        presentation == CallPresentationState.ACTIVE &&
        remoteVideoFrame != null

    // A call is never accepted before the OS grants what it needs -- the same
    // rule that applies to placing one. See CallPermissionGate.
    val acceptWithPermission = rememberCallStarter { onAcceptCall(activeCall.callId) }

    // Closes the observable connect sequence: the last stage is only reached
    // once the UI itself is showing a connected call.
    LaunchedEffect(presentation, activeCall.callId) {
        if (presentation == CallPresentationState.ACTIVE) {
            CallDiagnostics.stage(
                activeCall.callId.toLong(),
                CallStage.UI_ACTIVE,
                "video=${activeCall.isVideo} remote_frame=${remoteVideoFrame != null}"
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("aether_call_screen")
    ) {
        // The call happens inside Aether's environment, not on a separate
        // screen pasted over it.
        AetherTimeAtmosphere(
            modifier = Modifier.fillMaxSize(),
            expression = AtmosphereExpression.CONVERSATION,
            enableAmbientMotion = true
        )

        if (showsRemoteVideo) {
            DecodedVideoFrameImage(
                frame = remoteVideoFrame,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("remote_video_surface")
            )
        }

        // A single restrained scrim: enough to seat the typography, never a
        // heavy panel over the video.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = if (showsRemoteVideo) {
                            listOf(Color(0x66000000), Color(0x1A000000), Color(0x99000000))
                        } else {
                            listOf(Color(0xCC0B0B0F), Color(0x990B0B0F), Color(0xE60B0B0F))
                        }
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AetherCallUi.ControlFill)
                        .border(0.5.dp, AetherCallUi.ControlBorder, CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false, radius = 20.dp),
                            onClick = onMinimize
                        )
                        .testTag("minimize_call_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Minimise call",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // A video call whose local preview is running shows it here,
                // small and anchored -- never a floating panel over the scene.
                if (activeCall.isVideo && isCameraEnabled && localVideoFrame != null) {
                    DecodedVideoFrameImage(
                        frame = localVideoFrame,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(84.dp)
                            .height(112.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(0.5.dp, AetherCallUi.ControlBorder, RoundedCornerShape(14.dp))
                            .testTag("local_video_preview")
                    )
                }
            }

            Spacer(modifier = Modifier.height(if (showsRemoteVideo) 8.dp else 40.dp))

            // Identity stays high; the centre of the screen is deliberately
            // left to the atmosphere.
            CallIdentity(
                call = activeCall,
                state = presentation,
                compact = showsRemoteVideo,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )

            if (activeCall.state == CallStateEnum.ERROR && activeCall.errorMessage != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = activeCall.errorMessage,
                    fontFamily = ManropeFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = colors.textTertiary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp)
                        .testTag("call_error_text")
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (isIncomingPending) {
                IncomingCallControls(
                    onAnswer = { acceptWithPermission(activeCall.isVideo) },
                    onDecline = { onDiscardCall(activeCall.callId) }
                )
            } else {
                CallControlRow(
                    isMuted = activeCall.isMuted,
                    audioRoute = if (activeCall.isSpeakerOn) AudioRoute.SPEAKER else AudioRoute.EARPIECE,
                    isVideoCall = activeCall.isVideo,
                    isCameraEnabled = isCameraEnabled,
                    onToggleMute = onToggleMute,
                    onToggleSpeaker = onToggleSpeaker,
                    onToggleCamera = onToggleCamera,
                    onSwitchCamera = onSwitchCamera,
                    onEndCall = { onDiscardCall(activeCall.callId) }
                )
            }

            Spacer(modifier = Modifier.height(44.dp))
        }
    }
}

/**
 * The minimised call, shown while the user is elsewhere in the app. Same
 * language, same state source -- only smaller.
 */
@Composable
fun OngoingCallBar(
    activeCall: ActiveCall,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAetherColors.current
    val presentation = remember(activeCall.state, activeCall.mediaState, activeCall.isOutgoing) {
        CallStatePresenter.present(activeCall.state, activeCall.mediaState, activeCall.isOutgoing, activeCall.mediaEverConnected)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceElevated)
            .border(0.5.dp, AetherCallUi.ControlBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onExpand)
            .padding(horizontal = 16.dp, vertical = 11.dp)
            .testTag("ongoing_call_bar")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(colors.accent)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = activeCall.user?.name ?: "Call",
                fontFamily = ManropeFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = callStatusText(presentation, activeCall.durationSec, activeCall.isVideo),
                fontFamily = ManropeFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary
            )
        }
    }
}

/**
 * Renders one decoded call video frame.
 *
 * The backing [Bitmap] is rebuilt only when the frame instance changes -- there
 * is no continuous video *stream* object to hand a player, only a sequence of
 * discrete validated images (see
 * docs/architecture/calling-native-stack.md). Frames arrive already validated
 * and bounded by the media engine; this still refuses to build a bitmap from a
 * buffer whose length disagrees with its dimensions rather than letting the
 * platform throw.
 */
@Composable
internal fun DecodedVideoFrameImage(
    frame: DecodedVideoFrame,
    contentScale: ContentScale,
    modifier: Modifier = Modifier
) {
    val imageBitmap = remember(frame) {
        if (frame.width <= 0 || frame.height <= 0 ||
            frame.pixels.size < frame.width * frame.height
        ) {
            null
        } else {
            runCatching {
                Bitmap.createBitmap(frame.pixels, frame.width, frame.height, Bitmap.Config.ARGB_8888)
                    .asImageBitmap()
            }.getOrNull()
        }
    } ?: return

    Image(
        bitmap = imageBitmap,
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier.graphicsLayer { rotationZ = frame.rotationDegrees.toFloat() }
    )
}
