package com.foresightlabs.aether.ui.security

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foresightlabs.aether.ui.theme.ManropeFontFamily

// Fixed key sizes, not a `weight(1f)` share of a `fillMaxWidth()` row -- a
// weighted key grows with whatever width its parent hands it, which is how
// this keypad ended up reading as a full-width phone dialer instead of a
// compact, premium control. Sizing the keys themselves is what actually
// bounds the keypad; a `widthIn(max = ...)` on the container alone still let
// three `weight(1f)` keys stretch to fill nearly all of it.
//
// [DefaultKeyDiameter]/[RoomyKeyDiameter]/[RoomyWidthThreshold] are exposed so
// a caller can derive its own keyDiameter (see AppLockScreen/AppLockSetupScreen,
// which measure the screen's actual available window via BoxWithConstraints)
// without redefining these constants. This keypad itself only ever renders
// the diameter it's handed -- it does not read screen or display width, since
// that reflects the physical display rather than the window this composable
// actually has to fit in (multi-window, split-screen, a resized desktop
// window). Deriving from the real constraint belongs at the screen boundary,
// not baked into every key.
val AetherKeypadDefaultKeyDiameter = 68.dp
val AetherKeypadRoomyKeyDiameter = 72.dp
val AetherKeypadRoomyWidthThreshold = 720.dp
val AetherKeypadHorizontalGap = 22.dp
val AetherKeypadVerticalGap = 18.dp

/**
 * A custom numeric keypad for the App Lock screen, rather than the system
 * IME -- a stable layout, no keyboard covering the lock UI, and no
 * suggestion/history behaviour on digits that are never real text input.
 *
 * Each digit key clears its own semantics down to a plain "digit N" role: the
 * PIN itself is never readable from the tree, only which key was pressed --
 * see [com.foresightlabs.aether.domain.security.AppLockUiState] callers, which
 * never expose the accumulated PIN in semantics either.
 */
@Composable
fun AetherNumericKeypad(
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = Color.White,
    deleteEnabled: Boolean = true,
    keyDiameter: Dp = AetherKeypadDefaultKeyDiameter
) {
    val haptic = LocalHapticFeedback.current
    val rows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9')
    )
    Column(
        modifier = modifier.testTag("app_lock_keypad"),
        verticalArrangement = Arrangement.spacedBy(AetherKeypadVerticalGap),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(AetherKeypadHorizontalGap)) {
                row.forEach { digit ->
                    DigitKey(
                        digit = digit,
                        diameter = keyDiameter,
                        textColor = textColor,
                        modifier = Modifier.testTag("app_lock_key_$digit"),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDigit(digit)
                        }
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AetherKeypadHorizontalGap)) {
            // Empty cell on the same grid as the digit rows above -- keeps 0
            // and backspace aligned under the middle and right columns rather
            // than at an arbitrary offset.
            Box(modifier = Modifier.size(keyDiameter))
            DigitKey(
                digit = '0',
                diameter = keyDiameter,
                textColor = textColor,
                modifier = Modifier.testTag("app_lock_key_0"),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDigit('0')
                }
            )
            Box(
                modifier = Modifier
                    .size(keyDiameter)
                    .clip(CircleShape)
                    .clickable(
                        enabled = deleteEnabled,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDelete()
                        }
                    )
                    .testTag("app_lock_key_delete")
                    .clearAndSetSemantics { contentDescription = "Delete" },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = null,
                    tint = if (deleteEnabled) textColor else textColor.copy(alpha = 0.3f)
                )
            }
        }
    }
}

@Composable
private fun DigitKey(
    digit: Char,
    diameter: Dp,
    textColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(diameter)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .clearAndSetSemantics { contentDescription = "Digit $digit" },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit.toString(),
            fontFamily = ManropeFontFamily,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}
