package com.foresightlabs.aether.ui.calls

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foresightlabs.aether.domain.calls.CallBackend
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import kotlinx.coroutines.launch

/**
 * The backend chooser, shown when BOTH calling systems can reach the
 * recipient. Deliberately small: a bottom sheet with two rows, no
 * engineering explanation.
 *
 *     Call <name>
 *     Aether Call        Secure Aether-to-Aether call · Recommended
 *     Telegram Call (Beta)   Uses Telegram calling
 *
 * If the recipient is not an Aether-calling user the sheet never appears
 * (the caller goes straight to Telegram), so "Recommended" here is always
 * honest: both options genuinely exist.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CallChooserSheet(
    calleeName: String,
    isVideo: Boolean,
    onChoose: (CallBackend) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAetherColors.current
    val sheetState = rememberModalBottomSheetState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Rows hide the sheet FIRST and only then report the choice: a shown
    // ModalBottomSheet that merely leaves composition (without hide()) keeps
    // its invisible window mounted and silently eats every touch underneath
    // -- the call screen's End button went dead under it during a physical run.
    val choose: (CallBackend) -> Unit = { backend ->
        scope.launch { sheetState.hide() }.invokeOnCompletion { onChoose(backend) }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceElevated,
        modifier = modifier.testTag("call_chooser")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = if (isVideo) "Video call $calleeName" else "Call $calleeName",
                fontFamily = ManropeFontFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(14.dp))

            ChooserRow(
                title = CallBackend.AETHER.label,
                subtitle = "Secure Aether-to-Aether call · Recommended",
                tag = "chooser_aether",
                onClick = { choose(CallBackend.AETHER) }
            )
            Spacer(modifier = Modifier.height(6.dp))
            ChooserRow(
                title = CallBackend.TELEGRAM_BETA.label,
                subtitle = "Uses Telegram calling",
                tag = "chooser_telegram",
                onClick = { choose(CallBackend.TELEGRAM_BETA) }
            )
            // The sheet already applies the navigation-bar inset; this keeps
            // the last row off the very edge on gesture-nav devices.
            Spacer(modifier = Modifier.height(22.dp))
        }
    }
}

@Composable
private fun ChooserRow(
    title: String,
    subtitle: String,
    tag: String,
    onClick: () -> Unit
) {
    val colors = LocalAetherColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Call,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier
                .padding(end = 12.dp)
                .width(18.dp)
                .height(18.dp)
        )
        Column {
            Text(
                text = title,
                fontFamily = ManropeFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            Text(
                text = subtitle,
                fontFamily = ManropeFontFamily,
                fontSize = 11.5.sp,
                color = colors.textSecondary
            )
        }
    }
}
