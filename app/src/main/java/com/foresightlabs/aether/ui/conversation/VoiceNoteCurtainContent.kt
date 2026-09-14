package com.foresightlabs.aether.ui.conversation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foresightlabs.aether.data.media.VoiceWaveform
import com.foresightlabs.aether.domain.messages.ConversationMotion
import com.foresightlabs.aether.ui.design.AetherAccent
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import com.foresightlabs.aether.ui.theme.aetherDuration
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** What the voice-note UI asks for. The screen turns these into [VoiceNoteEvent]s and playback calls. */
sealed interface VoiceNoteAction {
    data object Press : VoiceNoteAction
    data class Drag(val towardCancelDp: Float, val upDp: Float) : VoiceNoteAction
    data object Release : VoiceNoteAction
    /** The system took the gesture away (not a lift): treated as an interruption, never as "send". */
    data object GestureCancelled : VoiceNoteAction
    data object StartHandsFree : VoiceNoteAction
    data object Lock : VoiceNoteAction
    data object Cancel : VoiceNoteAction
    data object TogglePause : VoiceNoteAction
    /** Stop recording and listen back. */
    data object Review : VoiceNoteAction
    data object Send : VoiceNoteAction
    data object Discard : VoiceNoteAction
    data object Keep : VoiceNoteAction
    data object TogglePlay : VoiceNoteAction
    /** Seek within the selected (trimmed) stretch, 0..1. */
    data class Seek(val fraction: Float) : VoiceNoteAction
    data class Trim(val startMs: Long, val endMs: Long) : VoiceNoteAction
}

/** The key the review player runs under; anything starting with this is review playback. */
const val VoiceReviewPlaybackPrefix = "voice-review:"

fun voiceReviewPlaybackKey(review: VoiceNoteState.Review): String =
    "$VoiceReviewPlaybackPrefix${review.take.path}:${review.trimStartMs}-${review.trimEndMs}"

/** The composer placeholder line for each notice -- short, because it sits where "Your Message…" does. */
fun voiceNoticeText(notice: VoiceNotice): String = when (notice) {
    VoiceNotice.HOLD_TO_RECORD -> "Hold to record, release to send"
    VoiceNotice.TOO_SHORT -> "Too short to send"
    VoiceNotice.MIC_UNAVAILABLE -> "Microphone unavailable"
    VoiceNotice.MIC_READY -> "Microphone ready — hold to record"
    VoiceNotice.MIC_DENIED -> "Microphone access is off"
    VoiceNotice.SEND_FAILED -> "Voice message not sent"
}

/** Subtle, and only at moments that change something: never per movement. */
fun voiceHapticConstant(kind: VoiceHaptic): Int = when (kind) {
    VoiceHaptic.START, VoiceHaptic.LOCK -> android.view.HapticFeedbackConstants.VIRTUAL_KEY
    VoiceHaptic.THRESHOLD -> android.view.HapticFeedbackConstants.CLOCK_TICK
    VoiceHaptic.TOGGLE -> android.view.HapticFeedbackConstants.KEYBOARD_TAP
    VoiceHaptic.SEND -> if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        android.view.HapticFeedbackConstants.CONFIRM
    } else {
        android.view.HapticFeedbackConstants.VIRTUAL_KEY
    }
}

fun formatVoiceDuration(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L).toInt()
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

// The two reds the conversation already speaks: a recording dot, and the
// destructive action colour DeleteConfirmCurtainContent uses.
private val RecordingRed = Color(0xFFFF453A)
private val DestructiveRed = Color(0xFFEF4444)

/** How far the rail's content follows the finger toward cancel -- resistance, not free dragging. */
private const val RAIL_RESISTANCE = 0.32f

/** Raw mic levels sit low for speech; a square-root curve makes quiet speech readable without inventing peaks. */
private fun displayLevel(raw: Float): Float = sqrt(raw.coerceIn(0f, 1f))

/**
 * The composer row while the mic is held -- the recorder takes the text
 * field's slot, so the row neither grows nor gains a card:
 *
 * ```
 * •  0:08   ▂▅▇▄▃▆▅▂▃   ‹ Slide to cancel
 * ```
 *
 * Sliding toward cancel drags the waveform and the hint along at a fraction of
 * the finger's travel and fades the waveform; past the threshold the hint says
 * what letting go will do. TalkBack gets Cancel and Lock as actions.
 */
@Composable
internal fun VoiceRecordingRail(
    voice: VoiceNoteUiState,
    onAction: (VoiceNoteAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAetherColors.current
    val holding = voice.state as? VoiceNoteState.Holding
    val cancelFraction = holding?.cancelFraction ?: 0f
    val armed = holding?.cancelArmed == true
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 1f else -1f
    val shiftPx = with(LocalDensity.current) { ((holding?.cancelDragDp ?: 0f) * RAIL_RESISTANCE).dp.toPx() } * direction
    val hintTint by animateColorAsState(
        targetValue = if (armed) DestructiveRed else colors.textSecondary,
        animationSpec = tween(aetherDuration(ConversationMotion.FAST_MS)),
        label = "voice_rail_cancel_tint"
    )
    val currentOnAction by rememberUpdatedState(onAction)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = ComposerRowHeight)
            .testTag("voice_recording_rail")
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("Cancel recording") { currentOnAction(VoiceNoteAction.Cancel); true },
                    CustomAccessibilityAction("Lock recording") { currentOnAction(VoiceNoteAction.Lock); true }
                )
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(RecordingRed)
        )
        Spacer(modifier = Modifier.width(8.dp))
        VoiceTimer(voice = voice, fontSizeSp = 14f, color = colors.textPrimary)
        Spacer(modifier = Modifier.width(12.dp))
        LiveLevelsStrip(
            levels = voice.levels,
            color = colors.accent,
            modifier = Modifier
                .weight(1f)
                .height(22.dp)
                .graphicsLayer {
                    translationX = shiftPx
                    alpha = 1f - 0.75f * cancelFraction
                }
        )
        Spacer(modifier = Modifier.width(10.dp))
        Row(
            modifier = Modifier.graphicsLayer { translationX = shiftPx },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = null,
                tint = hintTint,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = if (armed) "Release to cancel" else "Slide to cancel",
                fontFamily = ManropeFontFamily,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = hintTint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("voice_cancel_hint")
            )
        }
    }
}

/**
 * The lock affordance, in the emoji button's place while the mic is held: a
 * lock under a small chevron, rising and warming toward the accent as the
 * finger slides up. Decorative -- the rail carries TalkBack's Lock action.
 */
@Composable
internal fun VoiceLockHint(voice: VoiceNoteUiState, modifier: Modifier = Modifier) {
    val colors = LocalAetherColors.current
    val progress = (voice.state as? VoiceNoteState.Holding)?.lockFraction ?: 0f
    Column(
        modifier = modifier
            .size(48.dp)
            .graphicsLayer { translationY = -6.dp.toPx() * progress }
            .semantics { invisibleToUser() }
            .testTag("voice_lock_hint"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.KeyboardArrowUp,
            contentDescription = null,
            tint = colors.textTertiary.copy(alpha = 0.45f + 0.55f * progress),
            modifier = Modifier.size(14.dp)
        )
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            tint = lerp(colors.textSecondary, colors.accent, progress),
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * The voice note as Curtain content -- [CurtainState.VOICE_RECORDING] and
 * [CurtainState.VOICE_REVIEW]. Plain layout on the one Curtain: no title bar,
 * no close icon, no surface of its own. The Curtain's top seam is the frame;
 * delete, and Back, are the ways out.
 */
@Composable
fun VoiceNoteCurtainContent(
    voice: VoiceNoteUiState,
    reviewPlayback: AudioPlaybackController.Playback?,
    onAction: (VoiceNoteAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val state = voice.state
    if (state !is VoiceNoteState.Locked && state !is VoiceNoteState.Review) return
    val motion = aetherDuration(ConversationMotion.STANDARD_MS)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 10.dp)
            .testTag("curtain_voice_content")
    ) {
        AnimatedContent(
            targetState = state,
            contentKey = { it::class },
            transitionSpec = {
                (fadeIn(tween(motion)) togetherWith fadeOut(tween(motion)))
                    .using(SizeTransform(clip = false))
            },
            label = "voice_curtain_phase"
        ) { phase ->
            when (phase) {
                is VoiceNoteState.Locked -> LockedVoiceContent(voice, phase, onAction)
                is VoiceNoteState.Review -> ReviewVoiceContent(phase, reviewPlayback, onAction)
                else -> Unit
            }
        }
    }
}

@Composable
private fun LockedVoiceContent(
    voice: VoiceNoteUiState,
    state: VoiceNoteState.Locked,
    onAction: (VoiceNoteAction) -> Unit
) {
    val colors = LocalAetherColors.current
    val fast = aetherDuration(ConversationMotion.FAST_MS)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("voice_locked_content"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .animateContentSize(tween(fast))
                .semantics(mergeDescendants = true) {
                    stateDescription = if (state.paused) "Recording paused" else "Recording"
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.paused) {
                Text(
                    text = "Paused",
                    fontFamily = ManropeFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary,
                    modifier = Modifier.testTag("voice_paused_label")
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(RecordingRed)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            VoiceTimer(
                voice = voice,
                fontSizeSp = 22f,
                color = if (state.paused) colors.textSecondary else colors.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(10.dp))
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = "Locked",
                tint = colors.textTertiary,
                modifier = Modifier.size(13.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Paused audio can be listened to -- which finishes the recording
            // (the file has to be closed to be played), so it is offered only
            // once the person has stopped talking.
            AnimatedVisibility(
                visible = state.paused && !state.confirmingDiscard,
                enter = fadeIn(tween(fast)) + expandHorizontally(tween(fast)),
                exit = fadeOut(tween(fast)) + shrinkHorizontally(tween(fast))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VoiceRoundButton(
                        icon = Icons.Filled.PlayArrow,
                        label = "Review recording",
                        tint = colors.textPrimary,
                        size = 40.dp,
                        testTag = "voice_review_button",
                        onClick = { onAction(VoiceNoteAction.Review) }
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
            }
            LiveLevelsStrip(
                levels = voice.levels,
                color = if (state.paused) colors.textSecondary else colors.accent,
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        AnimatedContent(
            targetState = state.confirmingDiscard,
            transitionSpec = { fadeIn(tween(fast)) togetherWith fadeOut(tween(fast)) },
            label = "voice_locked_controls"
        ) { confirming ->
            if (confirming) {
                VoiceDiscardQuestion(onAction)
            } else {
                VoiceControlsRow(
                    center = {
                        VoiceRoundButton(
                            icon = if (state.paused) Icons.Filled.Mic else Icons.Filled.Pause,
                            label = if (state.paused) "Resume recording" else "Pause recording",
                            tint = colors.textPrimary,
                            testTag = "voice_pause_button",
                            onClick = { onAction(VoiceNoteAction.TogglePause) }
                        )
                    },
                    onAction = onAction
                )
            }
        }
    }
}

@Composable
private fun ReviewVoiceContent(
    state: VoiceNoteState.Review,
    playback: AudioPlaybackController.Playback?,
    onAction: (VoiceNoteAction) -> Unit
) {
    val colors = LocalAetherColors.current
    val fast = aetherDuration(ConversationMotion.FAST_MS)
    // Exactly what will be sent: the take's levels packed into Telegram's
    // waveform and read back.
    val samples = remember(state.take.levels) { VoiceWaveform.decode(VoiceWaveform.encode(state.take.levels)) }
    val selectedMs = state.selectedDurationMs
    val positionMs = playback?.positionMs?.coerceIn(0L, selectedMs)
    val playing = playback?.isPlaying == true

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("voice_review_content"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (positionMs != null) {
                "${formatVoiceDuration(positionMs)} / ${formatVoiceDuration(selectedMs)}"
            } else {
                formatVoiceDuration(selectedMs)
            },
            fontFamily = ManropeFontFamily,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
            modifier = Modifier.testTag("voice_review_duration")
        )

        Spacer(modifier = Modifier.height(14.dp))

        ReviewWaveform(
            samples = samples,
            durationMs = state.take.durationMs,
            trimStartMs = state.trimStartMs,
            trimEndMs = state.trimEndMs,
            progress = positionMs?.let { if (selectedMs > 0L) it.toFloat() / selectedMs else 0f },
            canTrim = state.canTrim,
            onTrim = { start, end -> onAction(VoiceNoteAction.Trim(start, end)) },
            onSeek = { onAction(VoiceNoteAction.Seek(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        )

        if (state.canTrim) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TrimTrackInset)
                    .semantics { invisibleToUser() },
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatVoiceDuration(state.trimStartMs), fontFamily = ManropeFontFamily, fontSize = 11.sp, color = colors.textTertiary)
                Text(formatVoiceDuration(state.trimEndMs), fontFamily = ManropeFontFamily, fontSize = 11.sp, color = colors.textTertiary)
            }
        }

        if (state.sendFailed) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Not sent. Try again.",
                fontFamily = ManropeFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = DestructiveRed,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .testTag("voice_send_failed")
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        AnimatedContent(
            targetState = state.confirmingDiscard,
            transitionSpec = { fadeIn(tween(fast)) togetherWith fadeOut(tween(fast)) },
            label = "voice_review_controls"
        ) { confirming ->
            if (confirming) {
                VoiceDiscardQuestion(onAction)
            } else {
                VoiceControlsRow(
                    center = {
                        VoiceRoundButton(
                            icon = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            label = if (playing) "Pause playback" else "Play recording",
                            tint = colors.textPrimary,
                            testTag = "voice_review_play_button",
                            onClick = { onAction(VoiceNoteAction.TogglePlay) }
                        )
                    },
                    onAction = onAction
                )
            }
        }
    }
}

/** Delete at the start, [center] in the middle, Send -- the one accented action -- at the end. */
@Composable
private fun VoiceControlsRow(
    center: @Composable () -> Unit,
    onAction: (VoiceNoteAction) -> Unit
) {
    val colors = LocalAetherColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            VoiceRoundButton(
                icon = Icons.Outlined.Delete,
                label = "Discard voice message",
                tint = colors.textSecondary,
                testTag = "voice_discard_button",
                onClick = { onAction(VoiceNoteAction.Discard) }
            )
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            center()
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            VoiceSendButton(onClick = { onAction(VoiceNoteAction.Send) })
        }
    }
}

/** The Curtain's own confirmation idiom (see DeleteConfirmCurtainContent), in place of the controls. */
@Composable
private fun VoiceDiscardQuestion(onAction: (VoiceNoteAction) -> Unit) {
    val colors = LocalAetherColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("voice_discard_question"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Discard this voice message?",
            fontFamily = ManropeFontFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.textPrimary.copy(alpha = 0.08f))
                    .clickable { onAction(VoiceNoteAction.Keep) }
                    .testTag("voice_discard_keep"),
                contentAlignment = Alignment.Center
            ) {
                Text("Keep", fontFamily = ManropeFontFamily, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(DestructiveRed.copy(alpha = 0.15f))
                    .clickable { onAction(VoiceNoteAction.Discard) }
                    .testTag("voice_discard_confirm"),
                contentAlignment = Alignment.Center
            ) {
                Text("Discard", fontFamily = ManropeFontFamily, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DestructiveRed)
            }
        }
    }
}

/** A quiet round control: the composer mic's own fill, no border, no glow. */
@Composable
private fun VoiceRoundButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    testTag: String,
    onClick: () -> Unit,
    size: Dp = 48.dp
) {
    val colors = LocalAetherColors.current
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(colors.textPrimary.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

/** The composer's Send, as a pill with its word: the only accented control on the Curtain. */
@Composable
private fun VoiceSendButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(48.dp)
            .clip(AetherEmber.Shapes.Pill)
            .background(AetherAccent.actionBrush)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "Send voice message" }
            .padding(horizontal = 18.dp)
            .testTag("voice_send_button"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Send",
            fontFamily = ManropeFontFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(17.dp)
        )
    }
}

/**
 * The elapsed time, isolated so only this text recomposes as it ticks. Not a
 * live region: TalkBack reads it when asked, instead of every second.
 */
@Composable
private fun VoiceTimer(
    voice: VoiceNoteUiState,
    fontSizeSp: Float,
    color: Color,
    fontWeight: FontWeight = FontWeight.SemiBold
) {
    Text(
        text = formatVoiceDuration(voice.elapsedMs),
        fontFamily = ManropeFontFamily,
        fontSize = fontSizeSp.sp,
        fontWeight = fontWeight,
        color = color,
        maxLines = 1,
        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
        modifier = Modifier.testTag("voice_timer")
    )
}

/**
 * The recording's real input levels, newest at the trailing edge, flowing
 * toward the start as more arrive. Where there is no audio yet the bars sit at
 * a faint baseline -- never invented peaks. The levels are read in the draw
 * phase, so a new level redraws this strip without recomposing anything.
 */
@Composable
internal fun LiveLevelsStrip(
    levels: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    barWidth: Dp = 2.5.dp,
    gap: Dp = 2.dp
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier = modifier.semantics { invisibleToUser() }) {
        val barPx = barWidth.toPx()
        val step = barPx + gap.toPx()
        val count = (size.width / step).toInt().coerceAtLeast(1)
        val available = levels.size
        for (i in 0 until count) {
            val levelIndex = available - count + i
            val hasLevel = levelIndex >= 0
            val level = if (hasLevel) displayLevel(levels[levelIndex]) else 0f
            val height = (level * size.height).coerceAtLeast(barPx)
            val slot = if (rtl) count - 1 - i else i
            val x = size.width - (count - slot) * step + gap.toPx()
            drawRoundRect(
                color = if (hasLevel) color else color.copy(alpha = 0.22f),
                topLeft = Offset(x, (size.height - height) / 2f),
                size = Size(barPx, height),
                cornerRadius = CornerRadius(barPx / 2f, barPx / 2f)
            )
        }
    }
}

/** Room at each end of the review track so a handle at 0:00 or at the end still has its full touch target. */
private val TrimTrackInset = 16.dp
private const val TRIM_STEP_MS = 500L

/**
 * The review waveform: the note's real waveform, playback progress over the
 * selected stretch, and -- when the note is long enough to trim -- a handle at
 * each end of the selection. Audio outside the selection is dimmed; that is
 * what will be cut. Tapping inside the selection seeks.
 */
@Composable
private fun ReviewWaveform(
    samples: List<Float>,
    durationMs: Long,
    trimStartMs: Long,
    trimEndMs: Long,
    progress: Float?,
    canTrim: Boolean,
    onTrim: (startMs: Long, endMs: Long) -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAetherColors.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val safeDuration = durationMs.coerceAtLeast(1L)
    val startFraction = trimStartMs.toFloat() / safeDuration
    val endFraction = trimEndMs.toFloat() / safeDuration
    val currentStart by rememberUpdatedState(trimStartMs)
    val currentEnd by rememberUpdatedState(trimEndMs)
    val currentOnSeek by rememberUpdatedState(onSeek)

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val insetPx = with(density) { TrimTrackInset.toPx() }
        val trackWidthPx = (constraints.maxWidth - 2 * insetPx).coerceAtLeast(1f)
        fun xOf(fraction: Float): Float = insetPx + (if (rtl) 1f - fraction else fraction) * trackWidthPx

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .semantics { invisibleToUser() }
                .pointerInput(safeDuration, rtl) {
                    detectTapGestures { offset ->
                        val raw = ((offset.x - insetPx) / trackWidthPx).coerceIn(0f, 1f)
                        val ms = (if (rtl) 1f - raw else raw) * safeDuration
                        val start = currentStart
                        val end = currentEnd
                        if (ms >= start && ms <= end && end > start) {
                            currentOnSeek((ms - start) / (end - start))
                        }
                    }
                }
        ) {
            val barPx = 3.dp.toPx()
            val step = barPx + 2.dp.toPx()
            val count = (trackWidthPx / step).toInt().coerceAtLeast(1)
            val selectedSpan = (endFraction - startFraction).coerceAtLeast(0.0001f)
            for (i in 0 until count) {
                val fraction = (i + 0.5f) / count
                val sample = if (samples.isEmpty()) {
                    0f
                } else {
                    val from = i * samples.size / count
                    val until = maxOf(from + 1, (i + 1) * samples.size / count).coerceAtMost(samples.size)
                    var peak = 0f
                    for (s in from until until) peak = maxOf(peak, samples[s])
                    peak
                }
                val inSelection = fraction in startFraction..endFraction
                val played = progress != null && inSelection && (fraction - startFraction) / selectedSpan <= progress
                val barColor = when {
                    !inSelection -> colors.textTertiary.copy(alpha = 0.3f)
                    played -> colors.accent
                    else -> colors.accent.copy(alpha = 0.42f)
                }
                val height = (sample * size.height * 0.86f).coerceAtLeast(barPx)
                val x = xOf(fraction) - barPx / 2f
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, (size.height - height) / 2f),
                    size = Size(barPx, height),
                    cornerRadius = CornerRadius(barPx / 2f, barPx / 2f)
                )
            }
        }

        if (canTrim) {
            TrimHandle(
                xPx = xOf(startFraction),
                label = "Trim start",
                valueMs = trimStartMs,
                durationMs = safeDuration,
                trackWidthPx = trackWidthPx,
                rtl = rtl,
                onMoveTo = { onTrim(it, currentEnd) },
                testTag = "voice_trim_start"
            )
            TrimHandle(
                xPx = xOf(endFraction),
                label = "Trim end",
                valueMs = trimEndMs,
                durationMs = safeDuration,
                trackWidthPx = trackWidthPx,
                rtl = rtl,
                onMoveTo = { onTrim(currentStart, it) },
                testTag = "voice_trim_end"
            )
        }
    }
}

/**
 * One end of the trim selection: a thin accent rule you can take hold of by a
 * full 48dp target. The drag is measured from where it began, so the clamp at
 * the other handle never makes the handle jump when the finger comes back.
 * TalkBack sees an adjustable value with earlier/later steps.
 */
@Composable
private fun TrimHandle(
    xPx: Float,
    label: String,
    valueMs: Long,
    durationMs: Long,
    trackWidthPx: Float,
    rtl: Boolean,
    onMoveTo: (Long) -> Unit,
    testTag: String
) {
    val colors = LocalAetherColors.current
    val touch = 48.dp
    val touchPx = with(LocalDensity.current) { touch.toPx() }
    val currentValue by rememberUpdatedState(valueMs)
    val currentMove by rememberUpdatedState(onMoveTo)
    var dragBaseMs by remember { mutableLongStateOf(0L) }
    var dragPx by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .offset { IntOffset((xPx - touchPx / 2f).roundToInt(), 0) }
            .width(touch)
            .fillMaxHeight()
            .pointerInput(durationMs, trackWidthPx, rtl) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragBaseMs = currentValue
                        dragPx = 0f
                    }
                ) { change, amount ->
                    change.consume()
                    dragPx += if (rtl) -amount else amount
                    currentMove(dragBaseMs + (dragPx / trackWidthPx * durationMs).toLong())
                }
            }
            .semantics {
                contentDescription = label
                stateDescription = formatVoiceDuration(valueMs)
                progressBarRangeInfo = ProgressBarRangeInfo(valueMs.toFloat(), 0f..durationMs.toFloat())
                setProgress { target -> currentMove(target.toLong()); true }
                customActions = listOf(
                    CustomAccessibilityAction("Move earlier") { currentMove(currentValue - TRIM_STEP_MS); true },
                    CustomAccessibilityAction("Move later") { currentMove(currentValue + TRIM_STEP_MS); true }
                )
            }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(colors.accent)
        )
    }
}
