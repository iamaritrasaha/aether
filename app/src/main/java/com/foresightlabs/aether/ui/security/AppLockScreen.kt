package com.foresightlabs.aether.ui.security

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.foresightlabs.aether.domain.security.AppLockUiState
import com.foresightlabs.aether.domain.security.LockReason
import com.foresightlabs.aether.ui.design.AetherBrandMark
import com.foresightlabs.aether.ui.theme.AetherAuthLavender
import com.foresightlabs.aether.ui.theme.AetherAuthMist
import com.foresightlabs.aether.ui.theme.AetherAuthMoon
import com.foresightlabs.aether.ui.theme.DarkBackground
import com.foresightlabs.aether.ui.theme.DarkSurface
import com.foresightlabs.aether.ui.theme.LocalReducedMotion
import com.foresightlabs.aether.ui.theme.ManropeFontFamily

/**
 * "A dark room under quiet lavender moonlight" -- the opaque surface shown
 * whenever [state] is not [AppLockUiState.Unlocked]. Fully opaque by design:
 * nothing behind it (chat names, message text, avatars) may be visible or
 * recoverable, so this never blurs live content the way the rest of Aether's
 * glass surfaces do -- see [com.foresightlabs.aether.ui.design.AetherFrostedGlass]
 * for that pattern elsewhere, deliberately not used here.
 */
@Composable
fun AppLockScreen(
    state: AppLockUiState,
    pinLength: Int,
    pinEntered: Int,
    biometricAvailable: Boolean,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onBiometricTap: () -> Unit,
    secondsRemaining: Long = 0L,
    title: String = "Aether is locked",
    modifier: Modifier = Modifier
) {
    val calm = LocalReducedMotion.current
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DarkSurface, DarkBackground)
                )
            )
            .testTag("app_lock_screen"),
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
        LockGeometry(calm = calm)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            AetherBrandMark(
                size = 56.dp,
                colors = listOf(AetherAuthMoon, AetherAuthMist, AetherAuthLavender, AetherAuthLavender)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = title,
                fontFamily = ManropeFontFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = statusLine(state, secondsRemaining),
                fontFamily = ManropeFontFamily,
                fontSize = 13.5.sp,
                color = AetherAuthMist,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("app_lock_status_line")
            )

            Spacer(modifier = Modifier.height(28.dp))

            PinDots(
                total = pinLength,
                filled = pinEntered,
                isVerifying = state is AppLockUiState.Verifying
            )

            Spacer(modifier = Modifier.weight(0.6f))

            val blocked = state is AppLockUiState.TemporarilyBlocked
            AetherNumericKeypad(
                onDigit = onDigit,
                onDelete = onDelete,
                deleteEnabled = !blocked && pinEntered > 0,
                keyDiameter = keyDiameter
            )

            Spacer(modifier = Modifier.height(20.dp))

            Box(modifier = Modifier.height(56.dp), contentAlignment = Alignment.Center) {
                if (biometricAvailable && !blocked) {
                    IconButton(
                        onClick = onBiometricTap,
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("app_lock_biometric_button")
                            .clearAndSetSemantics { contentDescription = "Unlock with biometrics" }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Fingerprint,
                            contentDescription = null,
                            tint = AetherAuthMoon,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun statusLine(state: AppLockUiState, secondsRemaining: Long): String = when (state) {
    is AppLockUiState.TemporarilyBlocked -> "Try again in ${secondsRemaining}s"
    is AppLockUiState.Locked -> when (state.reason) {
        LockReason.FRESH_PROCESS -> "Enter your passcode to continue"
        LockReason.AUTO_LOCK_TIMEOUT -> "Enter your passcode to continue"
        LockReason.MANUAL_LOCK -> "Enter your passcode to continue"
    }
    AppLockUiState.Verifying -> "Checking..."
    else -> ""
}

@Composable
private fun PinDots(total: Int, filled: Int, isVerifying: Boolean) {
    if (isVerifying) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp).testTag("app_lock_verifying_spinner"),
            color = AetherAuthMoon,
            strokeWidth = 2.dp
        )
        return
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .testTag("app_lock_pin_dots")
            // The count is meaningful for a11y (progress toward the full
            // passcode); the digits themselves are never in the tree.
            .clearAndSetSemantics { contentDescription = "$filled of $total digits entered" }
    ) {
        repeat(total) { index ->
            val isFilled = index < filled
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (isFilled) AetherAuthMoon else Color.White.copy(alpha = 0.16f))
            )
        }
    }
}

/**
 * A single restrained, mostly-static geometric focal point. With reduced
 * motion it never animates at all; otherwise a slow breathing glow only --
 * no per-frame path work, no continuous rebuild, so an idle lock screen
 * settles rather than burning GPU.
 */
@Composable
private fun LockGeometry(calm: Boolean) {
    val transition = rememberInfiniteTransition(label = "lock_breath")
    val alpha by if (calm) {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0.10f) }
    } else {
        transition.animateFloat(
            initialValue = 0.06f,
            targetValue = 0.16f,
            animationSpec = infiniteRepeatable(
                animation = tween(3600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "lock_breath_alpha"
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .background(
                Brush.radialGradient(
                    colors = listOf(AetherAuthLavender.copy(alpha = alpha), Color.Transparent)
                )
            )
    )
}
