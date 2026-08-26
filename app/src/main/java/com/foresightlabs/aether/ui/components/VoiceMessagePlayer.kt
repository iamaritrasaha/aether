package com.foresightlabs.aether.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import kotlinx.coroutines.delay

@Composable
fun VoiceMessagePlayer(
    durationSec: Int,
    waveform: List<Float>,
    isOutgoing: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LocalAetherColors.current
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var playbackSpeed by remember { mutableStateOf("1.0x") }

    val safeDuration = durationSec.coerceAtLeast(1)

    LaunchedEffect(isPlaying, progress) {
        if (isPlaying) {
            val stepTimeMs = 100L
            val stepProgress = (stepTimeMs / (safeDuration * 1000f))
            while (isPlaying && progress < 1.0f) {
                delay(stepTimeMs)
                progress = (progress + stepProgress).coerceAtMost(1.0f)
                if (progress >= 1.0f) {
                    isPlaying = false
                    progress = 0f
                    break
                }
            }
        }
    }

    val contentColor = if (isOutgoing) colors.bubbleOutgoingText else colors.bubbleIncomingText
    val playButtonBg = if (isOutgoing) contentColor.copy(alpha = 0.25f) else colors.accent
    val playButtonIconTint = if (isOutgoing) contentColor else colors.surface

    val activeWaveformColor = if (isOutgoing) contentColor else colors.accent
    val inactiveWaveformColor = contentColor.copy(alpha = 0.35f)

    val currentSeconds = (progress * safeDuration).toInt()
    val timeLabel = String.format("%d:%02d / %d:%02d", currentSeconds / 60, currentSeconds % 60, safeDuration / 60, safeDuration % 60)

    val playScale by animateFloatAsState(
        targetValue = if (isPlaying) 0.95f else 1.0f,
        animationSpec = spring(),
        label = "play_scale"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Play / Pause Button
        Box(
            modifier = Modifier
                .size(40.dp)
                .scale(playScale)
                .clip(CircleShape)
                .background(playButtonBg)
                .clickable {
                    isPlaying = !isPlaying
                }
                .testTag("voice_play_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause Voice Note" else "Play Voice Note",
                tint = playButtonIconTint,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            // Waveform canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .pointerInput(waveform) {
                        detectTapGestures { offset ->
                            val clickedProgress = (offset.x / size.width).coerceIn(0f, 1f)
                            progress = clickedProgress
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxWidth().height(28.dp)) {
                    val count = if (waveform.isNotEmpty()) waveform.size else 30
                    val totalWidth = size.width
                    val barWidth = 3.dp.toPx()
                    val space = ((totalWidth - (count * barWidth)) / (count - 1)).coerceAtLeast(1.5.dp.toPx())
                    val maxHeight = size.height

                    for (i in 0 until count) {
                        val amplitude = waveform.getOrElse(i) { 0.3f }.coerceIn(0.15f, 1.0f)
                        val barHeight = (amplitude * maxHeight).coerceAtLeast(3.dp.toPx())
                        val x = i * (barWidth + space)
                        val y = (maxHeight - barHeight) / 2f

                        val barProgress = x / totalWidth
                        val barColor = if (barProgress <= progress) activeWaveformColor else inactiveWaveformColor

                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(x, y),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Time & Speed toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = timeLabel,
                    fontFamily = ManropeFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = contentColor.copy(alpha = 0.84f)
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(contentColor.copy(alpha = 0.14f))
                        .clickable {
                            playbackSpeed = when (playbackSpeed) {
                                "1.0x" -> "1.5x"
                                "1.5x" -> "2.0x"
                                else -> "1.0x"
                            }
                        }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = playbackSpeed,
                        fontFamily = ManropeFontFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                }
            }
        }
    }
}
