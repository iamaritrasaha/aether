package com.foresightlabs.aether.ui.calls

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/**
 * The backend chooser, shown when BOTH calling systems can reach the
 * recipient. Deliberately small: two rows, no engineering explanation.
 *
 *     Call <name>
 *     Aether Call        Secure Aether-to-Aether call · Recommended
 *     Telegram Call (Beta)   Uses Telegram calling
 *
 * If the recipient is not an Aether-calling user the dialog never appears
 * (the caller goes straight to Telegram), so "Recommended" here is always
 * honest: both options genuinely exist.
 */
@Composable
fun CallChooserDialog(
    calleeName: String,
    isVideo: Boolean,
    onChoose: (CallBackend) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAetherColors.current
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surfaceElevated)
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .testTag("call_chooser")
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
                onClick = { onChoose(CallBackend.AETHER) }
            )
            Spacer(modifier = Modifier.height(6.dp))
            ChooserRow(
                title = CallBackend.TELEGRAM_BETA.label,
                subtitle = "Uses Telegram calling",
                tag = "chooser_telegram",
                onClick = { onChoose(CallBackend.TELEGRAM_BETA) }
            )
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
