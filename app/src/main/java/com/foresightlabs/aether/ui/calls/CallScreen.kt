package com.foresightlabs.aether.ui.calls

import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.foresightlabs.aether.calls.media.CallDiagnostics
import com.foresightlabs.aether.calls.media.CallStage
import com.foresightlabs.aether.calls.media.DecodedVideoFrame
import com.foresightlabs.aether.domain.calls.CallPermissions
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
    onSwitchCamera: () -> Unit = {},
    mediaHealthProvider: () -> com.foresightlabs.aether.calls.media.CallMediaHealth? = { null }
) {
    if (activeCall == null) return

    // Back does NOT end the call: the canonical call state lives outside this
    // composable. Back only leaves the surface -- the call keeps running in
    // the background (foreground-service notification persists) and the
    // owning conversation's animated call icon is the way back in. There is
    // deliberately no floating call bar anywhere else in the app.
    androidx.activity.compose.BackHandler(enabled = true) { onMinimize() }

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

    // Turning the camera ON mid-call requires CAMERA, asked at the tap (never
    // earlier, never for a voice call -- the control row only offers the
    // camera on a video call). Turning it OFF needs no permission, so an off
    // toggle always passes straight through. A denial leaves the camera off
    // without faking any call failure.
    val context = LocalContext.current
    val requestCameraThenToggle = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onToggleCamera()
    }
    val cameraToggleWithPermission = {
        val cameraGranted = ContextCompat.checkSelfPermission(context, CallPermissions.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (cameraGranted || activeCall.cameraIntentOn) {
            onToggleCamera()
        } else {
            requestCameraThenToggle.launch(CallPermissions.CAMERA)
        }
    }

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

        // DEBUG-build call inspector: media-pipeline evidence (counters, flags,
        // facing, route) in one place, to make the later physical validation a
        // read-off exercise. Metadata only -- no payload, addresses or keys --
        // and never compiled into release UI.
        if (com.foresightlabs.aether.BuildConfig.DEBUG) {
            CallDebugInspector(
                activeCall = activeCall,
                healthProvider = mediaHealthProvider,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 96.dp)
            )
        }

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
                // Front-camera preview is mirrored (what users expect from a
                // mirror); the transmitted frame itself is untouched.
                if (activeCall.isVideo && isCameraEnabled && localVideoFrame != null) {
                    DecodedVideoFrameImage(
                        frame = localVideoFrame,
                        contentScale = ContentScale.Crop,
                        mirror = activeCall.isFrontCamera,
                        modifier = Modifier
                            .width(84.dp)
                            .height(112.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(0.5.dp, AetherCallUi.ControlBorder, RoundedCornerShape(14.dp))
                            .testTag("local_video_preview")
                    )
                } else if (activeCall.isVideo && isCameraEnabled && presentation == CallPresentationState.ACTIVE) {
                    // Camera intended but no frame yet (starting, or native
                    // capture still negotiating): a quiet placeholder, never a
                    // frozen black panel.
                    Box(
                        modifier = Modifier
                            .width(84.dp)
                            .height(112.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(AetherCallUi.ControlFill)
                            .border(0.5.dp, AetherCallUi.ControlBorder, RoundedCornerShape(14.dp))
                            .testTag("local_video_placeholder"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Camera starting",
                            tint = colors.textTertiary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
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
                // The route shown is ActiveCall's synced ACTUAL route (Bluetooth
                // and wired devices included), not the last speaker toggle.
                CallControlRow(
                    isMuted = activeCall.isMuted,
                    audioRoute = activeCall.audioRoute,
                    isVideoCall = activeCall.isVideo,
                    isCameraEnabled = isCameraEnabled,
                    onToggleMute = onToggleMute,
                    onToggleSpeaker = onToggleSpeaker,
                    onToggleCamera = cameraToggleWithPermission,
                    onSwitchCamera = onSwitchCamera,
                    onEndCall = { onDiscardCall(activeCall.callId) }
                )
            }

            Spacer(modifier = Modifier.height(44.dp))
        }
    }
}

/**
 * DEBUG-BUILD-ONLY call inspector: polls the media engine's evidence snapshot
 * ([CallMediaHealth]) plus the call's own state once a second and renders it
 * as a compact translucent panel. Its entire audience is the physical
 * validation session -- every line answers a question the diagnostics log
 * would otherwise have to be mined for. Exposes metadata/state only: no
 * signalling bytes, no addresses, no keys, no frame data.
 */
@Composable
private fun CallDebugInspector(
    activeCall: ActiveCall,
    healthProvider: () -> com.foresightlabs.aether.calls.media.CallMediaHealth?,
    modifier: Modifier = Modifier
) {
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(activeCall.callId) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            tick++
        }
    }
    val health = remember(tick, activeCall.callId) { healthProvider() }
    val lastFailure = remember(tick) { com.foresightlabs.aether.calls.media.CallDiagnostics.lastFailure }
    val colors = LocalAetherColors.current

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xB3141419))
            .border(0.5.dp, AetherCallUi.ControlBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("call_debug_inspector")
    ) {
        InspectorLine("sig", activeCall.state.name)
        InspectorLine("media", activeCall.mediaState.name)
        InspectorLine("gen", health?.generation?.toString() ?: "-")
        InspectorLine(
            "conn",
            activeCall.connectedAtMs?.let { "CONNECTED ${rel(it)}" } ?: "not yet"
        )
        InspectorLine("cap", "${health?.captureSeconds ?: 0}s @ ${health?.lastCaptureActivityMs?.let { rel(it) } ?: "never"}")
        InspectorLine("pbk", "${health?.playbackSeconds ?: 0}s @ ${health?.lastPlaybackActivityMs?.let { rel(it) } ?: "never"}")
        InspectorLine("src", "mic=${health?.remoteMicActive == true} vid=${health?.remoteVideoSourcePresent == true}")
        InspectorLine("flags", "mute=${health?.muted} vPause=${health?.videoPaused} vStop=${health?.videoStopped}")
        InspectorLine("cam", "intent=${activeCall.cameraIntentOn} front=${health?.localCameraIsFront ?: activeCall.isFrontCamera}")
        InspectorLine("route", activeCall.audioRoute.name)
        lastFailure?.let {
            InspectorLine("err", "gen=${it.generation} ${it.stage.substringAfterLast('.')} ${it.type.substringAfterLast('.')}")
        }
        Text(
            text = "DEBUG INSPECTOR",
            fontFamily = ManropeFontFamily,
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textTertiary,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

private fun rel(epochMs: Long): String = "${((System.currentTimeMillis() - epochMs) / 1000).coerceAtLeast(0)}s ago"

@Composable
private fun InspectorLine(label: String, value: String) {
    val colors = LocalAetherColors.current
    Text(
        text = "$label: $value",
        fontFamily = ManropeFontFamily,
        fontSize = 10.sp,
        fontWeight = FontWeight.Normal,
        color = colors.textSecondary
    )
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
 *
 * A quarter-turned frame ([DecodedVideoFrame.rotationDegrees] 90/270) is drawn
 * scaled to fit: rotating a box by 90° swaps its on-screen extents, so without
 * the fit-scale the rotated bitmap's corners would fall outside its bounds.
 *
 * [mirror] flips the image horizontally -- used for the front-camera local
 * preview, where people expect to see themselves as in a mirror. It is purely
 * a display transform; the transmitted frame is never touched.
 */
@Composable
internal fun DecodedVideoFrameImage(
    frame: DecodedVideoFrame,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
    mirror: Boolean = false
) {
    // The ARGB->Bitmap copy is real work (a 640x480 frame moves ~1.2MB), so it
    // runs off the UI thread. Keying the producer on [frame] means a newer
    // frame cancels the in-flight conversion of an older one -- the newest
    // frame always wins, and while a conversion runs the previously produced
    // bitmap keeps rendering (no flicker, no unbounded queue).
    val imageBitmap by produceState<ImageBitmap?>(initialValue = null, frame) {
        value = withContext(Dispatchers.Default) {
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
        }
    }
    val bitmap: ImageBitmap = imageBitmap ?: return

    var boxSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val quarterTurn = frame.rotationDegrees == 90 || frame.rotationDegrees == 270
    val fitScale = if (quarterTurn && boxSize.width > 0 && boxSize.height > 0) {
        val long = maxOf(boxSize.width, boxSize.height).toFloat()
        val short = minOf(boxSize.width, boxSize.height).toFloat()
        if (long > 0f) short / long else 1f
    } else {
        1f
    }

    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier
            .onSizeChanged { boxSize = androidx.compose.ui.unit.IntSize(it.width, it.height) }
            .graphicsLayer {
                rotationZ = frame.rotationDegrees.toFloat()
                scaleX = if (mirror) -fitScale else fitScale
                scaleY = fitScale
            }
    )
}
