package com.foresightlabs.aether.ui.security

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.foresightlabs.aether.ui.theme.AetherAuthLavender
import com.foresightlabs.aether.ui.theme.AetherAuthMist
import com.foresightlabs.aether.ui.theme.AetherAuthMoon
import com.foresightlabs.aether.ui.theme.DarkBackground
import com.foresightlabs.aether.ui.theme.DarkSurface
import com.foresightlabs.aether.ui.theme.ManropeFontFamily

private const val MIN_PIN_LENGTH = 4
private const val MAX_PIN_LENGTH = 8

private enum class SetupStage { CREATE, CONFIRM }

/**
 * Create passcode -> confirm passcode -> App Lock enabled. Never enables the
 * lock unless the confirmation matches the original entry exactly.
 */
@Composable
fun AppLockSetupScreen(
    onCancel: () -> Unit,
    onCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: AppLockSetupViewModel = viewModel()
    var stage by remember { mutableStateOf(SetupStage.CREATE) }
    var createdPin by remember { mutableStateOf("") }
    var buffer by remember { mutableStateOf("") }
    var mismatch by remember { mutableStateOf(false) }

    fun reset() {
        stage = SetupStage.CREATE
        createdPin = ""
        buffer = ""
        mismatch = false
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DarkSurface, DarkBackground)))
            .testTag("app_lock_setup_screen"),
        contentAlignment = Alignment.Center
    ) {
        // Measured against this screen's own available window, not the
        // physical display -- a split-screen or resized window should not
        // read as "roomy" just because the device itself is a tablet.
        val keyDiameter = if (maxWidth >= AetherKeypadRoomyWidthThreshold) {
            AetherKeypadRoomyKeyDiameter
        } else {
            AetherKeypadDefaultKeyDiameter
        }
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp)) {
            IconButton(onClick = onCancel, modifier = Modifier.testTag("app_lock_setup_back")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedContent(targetState = stage, label = "setup_stage") { current ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (current == SetupStage.CREATE) "Create a passcode" else "Confirm your passcode",
                            fontFamily = ManropeFontFamily,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = when {
                                mismatch -> "Passcodes didn't match -- try again"
                                current == SetupStage.CREATE -> "Choose 4 to 8 digits"
                                else -> "Enter it once more to confirm"
                            },
                            fontFamily = ManropeFontFamily,
                            fontSize = 13.5.sp,
                            color = if (mismatch) Color(0xFFEF9A9A) else AetherAuthMist,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                val dotCount = if (stage == SetupStage.CREATE) maxOf(buffer.length, MIN_PIN_LENGTH) else createdPin.length
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .testTag("app_lock_setup_pin_dots")
                        .clearAndSetSemantics { contentDescription = "${buffer.length} digits entered" }
                ) {
                    repeat(if (stage == SetupStage.CREATE) MAX_PIN_LENGTH else createdPin.length) { index ->
                        val isFilled = index < buffer.length
                        val isPastMin = stage == SetupStage.CREATE && index >= MIN_PIN_LENGTH
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isFilled -> AetherAuthMoon
                                        isPastMin -> Color.White.copy(alpha = 0.08f)
                                        else -> Color.White.copy(alpha = 0.16f)
                                    }
                                )
                        )
                    }
                }
            }

            // Less flexible space than the spacer above -- a compact keypad
            // no longer needs a bottom-heavy push to avoid crowding the dots,
            // and less of it here reads as centered rather than pinned low.
            Spacer(modifier = Modifier.weight(0.6f))

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                AetherNumericKeypad(
                    onDigit = { digit ->
                        mismatch = false
                        val maxLen = if (stage == SetupStage.CREATE) MAX_PIN_LENGTH else createdPin.length
                        if (buffer.length >= maxLen) return@AetherNumericKeypad
                        buffer += digit
                        if (stage == SetupStage.CONFIRM && buffer.length == createdPin.length) {
                            if (buffer == createdPin) {
                                viewModel.enable(createdPin.toCharArray(), onCompleted)
                            } else {
                                mismatch = true
                                buffer = ""
                            }
                        }
                    },
                    onDelete = { if (buffer.isNotEmpty()) buffer = buffer.dropLast(1) },
                    keyDiameter = keyDiameter
                )

                Spacer(modifier = Modifier.height(20.dp))

                if (stage == SetupStage.CREATE) {
                    val canContinue = buffer.length in MIN_PIN_LENGTH..MAX_PIN_LENGTH
                    TextButton(
                        enabled = canContinue,
                        onClick = {
                            createdPin = buffer
                            buffer = ""
                            stage = SetupStage.CONFIRM
                        },
                        modifier = Modifier.testTag("app_lock_setup_continue")
                    ) {
                        Text(
                            "Continue",
                            fontFamily = ManropeFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = if (canContinue) AetherAuthMoon else AetherAuthMist.copy(alpha = 0.4f)
                        )
                    }
                } else {
                    TextButton(onClick = { reset() }, modifier = Modifier.testTag("app_lock_setup_start_over")) {
                        Text("Start over", fontFamily = ManropeFontFamily, color = AetherAuthMist)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
