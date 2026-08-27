package com.foresightlabs.aether.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.foresightlabs.aether.domain.calls.MediaConnectionState
import com.foresightlabs.aether.domain.model.ActiveCall
import com.foresightlabs.aether.domain.model.CallStateEnum
import com.foresightlabs.aether.ui.components.AetherAtmosphericBackground
import com.foresightlabs.aether.ui.components.AetherAvatar
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import com.foresightlabs.aether.ui.theme.OnlineGreen
import com.foresightlabs.aether.ui.theme.SpaceGroteskFontFamily

@Composable
fun FullCallScreen(
    activeCall: ActiveCall?,
    onAcceptCall: (Int) -> Unit,
    onDiscardCall: (Int) -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onMinimize: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (activeCall == null) return

    val context = LocalContext.current
    var showPermissionRationale by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onAcceptCall(activeCall.callId)
        }
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        AetherAtmosphericBackground(
            modifier = modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .statusBarsPadding()
                    .padding(24.dp)
            ) {
                // Top Bar - Minimize
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0x30000000))
                        .border(1.dp, Color(0x20FFFFFF), CircleShape)
                        .clickable { onMinimize() }
                        .testTag("minimize_call_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Minimize Call",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Main Call Info
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 40.dp)
                ) {
                    Spacer(modifier = Modifier.weight(1f))

                    // Avatar
                    AetherAvatar(
                        initials = activeCall.user?.avatarInitials ?: "?",
                        gradient = activeCall.user?.avatarGradient ?: listOf(Color(0xFF4DA3FF), Color(0xFF1D4ED8)),
                        size = 112.dp,
                        photoPath = activeCall.user?.photoPath,
                        showGlowingRim = true
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // User Name
                    Text(
                        text = activeCall.user?.name ?: "Telegram Contact",
                        fontFamily = SpaceGroteskFontFamily,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Call State Label / Monotonic Timer
                    val stateText = when (activeCall.state) {
                        CallStateEnum.PENDING -> if (activeCall.isOutgoing) "Calling…" else "Incoming Voice Call"
                        CallStateEnum.EXCHANGING_KEYS -> "Exchanging keys…"
                        CallStateEnum.READY -> when (activeCall.mediaState) {
                            MediaConnectionState.CONNECTED -> formatCallDuration(activeCall.durationSec)
                            MediaConnectionState.RECONNECTING -> "Reconnecting…"
                            MediaConnectionState.FAILED -> "Couldn't connect"
                            else -> "Connecting…"
                        }
                        CallStateEnum.HANGING_UP -> "Hanging up…"
                        CallStateEnum.DISCARDED -> "Call ended"
                        CallStateEnum.ERROR -> activeCall.errorMessage ?: "Couldn't connect"
                    }

                    Text(
                        text = stateText,
                        fontFamily = ManropeFontFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (activeCall.state == CallStateEnum.READY && activeCall.mediaState == MediaConnectionState.CONNECTED) OnlineGreen else Color(0xDDFFFFFF),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("call_state_text")
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // Call Action Controls
                    val isIncomingPending = !activeCall.isOutgoing && activeCall.state == CallStateEnum.PENDING

                    if (isIncomingPending) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(48.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Decline Button
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFEF4444))
                                        .clickable { onDiscardCall(activeCall.callId) }
                                        .testTag("decline_call_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CallEnd,
                                        contentDescription = "Decline Call",
                                        tint = Color.White,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Decline",
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 13.5.sp,
                                    color = Color.White
                                )
                            }

                            // Accept Button
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(OnlineGreen)
                                        .clickable {
                                            val hasPermission = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.RECORD_AUDIO
                                            ) == PackageManager.PERMISSION_GRANTED

                                            if (hasPermission) {
                                                onAcceptCall(activeCall.callId)
                                            } else {
                                                showPermissionRationale = true
                                            }
                                        }
                                        .testTag("accept_call_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = "Accept Call",
                                        tint = Color.White,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Accept",
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 13.5.sp,
                                    color = Color.White
                                )
                            }
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(32.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Mute
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(if (activeCall.isMuted) Color.White else Color(0x30FFFFFF))
                                        .border(1.dp, Color(0x40FFFFFF), CircleShape)
                                        .clickable { onToggleMute() }
                                        .testTag("mute_call_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (activeCall.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                        contentDescription = "Mute",
                                        tint = if (activeCall.isMuted) Color.Black else Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (activeCall.isMuted) "Muted" else "Mute",
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 12.5.sp,
                                    color = Color(0xEEFFFFFF)
                                )
                            }

                            // Speaker
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(if (activeCall.isSpeakerOn) Color.White else Color(0x30FFFFFF))
                                        .border(1.dp, Color(0x40FFFFFF), CircleShape)
                                        .clickable { onToggleSpeaker() }
                                        .testTag("speaker_call_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (activeCall.isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                        contentDescription = "Speaker",
                                        tint = if (activeCall.isSpeakerOn) Color.Black else Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (activeCall.isSpeakerOn) "Speaker On" else "Speaker",
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 12.5.sp,
                                    color = Color(0xEEFFFFFF)
                                )
                            }

                            // End Call
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFEF4444))
                                        .clickable { onDiscardCall(activeCall.callId) }
                                        .testTag("end_call_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CallEnd,
                                        contentDescription = "End Call",
                                        tint = Color.White,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "End",
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 12.5.sp,
                                    color = Color(0xEEFFFFFF)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(40.dp))
                }

                // Permission Rationale Modal
                if (showPermissionRationale) {
                    AlertDialog(
                        onDismissRequest = { showPermissionRationale = false },
                        title = {
                            Text(
                                text = "Allow microphone access",
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        },
                        text = {
                            Text(
                                text = "Aether needs microphone access for voice calls.",
                                fontFamily = ManropeFontFamily,
                                color = Color(0xDDFFFFFF)
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showPermissionRationale = false
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            ) {
                                Text("Continue", color = AetherEmber.Colors.Accent, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showPermissionRationale = false }) {
                                Text("Cancel", color = Color(0xAAFFFFFF))
                            }
                        },
                        containerColor = AetherEmber.Colors.SurfaceElevated,
                        shape = AetherEmber.Shapes.L
                    )
                }
            }
        }
    }
}

@Composable
fun OngoingCallBar(
    activeCall: ActiveCall,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xDC101828))
            .border(1.dp, OnlineGreen.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
            .clickable { onExpand() }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(OnlineGreen)
                )

                val callDurationText = if (activeCall.state == CallStateEnum.READY && activeCall.mediaState == MediaConnectionState.CONNECTED) {
                    formatCallDuration(activeCall.durationSec)
                } else if (activeCall.state == CallStateEnum.READY && activeCall.mediaState == MediaConnectionState.RECONNECTING) {
                    "Reconnecting…"
                } else {
                    "Connecting…"
                }

                Text(
                    text = "${activeCall.user?.name ?: "Call"} • $callDurationText",
                    fontFamily = ManropeFontFamily,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }

            Text(
                text = "Tap to return",
                fontFamily = ManropeFontFamily,
                fontSize = 12.sp,
                color = OnlineGreen,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun formatCallDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", mins, secs)
}
