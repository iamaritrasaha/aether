package com.foresightlabs.aether.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import com.foresightlabs.aether.ui.theme.SpaceGroteskFontFamily

/**
 * Composes a contact card to send.
 *
 * Entry is manual by design. Aether does not read the device address book to fill
 * this in, and does not upload it — the only contact details that leave the device
 * are the ones typed here, and the sheet says so before the send button is reachable.
 */
@Composable
fun ContactShareSheet(
    onDismiss: () -> Unit,
    onSend: (phone: String, firstName: String, lastName: String) -> Unit
) {
    val colors = LocalAetherColors.current
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    val canSend = phone.isNotBlank() && firstName.isNotBlank()

    ShareSheetScaffold(onDismiss = onDismiss, title = "Send a contact", testTag = "contact_share_sheet") {
        Text(
            text = "These details will be sent to this chat as a Telegram contact card. " +
                "Aether does not read or upload your address book.",
            fontFamily = ManropeFontFamily,
            fontSize = 12.5.sp,
            color = colors.textSecondary
        )
        Spacer(modifier = Modifier.height(12.dp))

        SheetField(
            value = firstName,
            onValueChange = { firstName = it },
            label = "First name",
            testTag = "contact_first_name"
        )
        Spacer(modifier = Modifier.height(8.dp))
        SheetField(
            value = lastName,
            onValueChange = { lastName = it },
            label = "Last name (optional)",
            testTag = "contact_last_name"
        )
        Spacer(modifier = Modifier.height(8.dp))
        SheetField(
            value = phone,
            onValueChange = { phone = it },
            label = "Phone number",
            keyboardType = KeyboardType.Phone,
            testTag = "contact_phone"
        )
        Spacer(modifier = Modifier.height(14.dp))

        SheetPrimaryAction(
            label = "Send contact",
            enabled = canSend,
            testTag = "contact_send",
            onClick = { onSend(phone.trim(), firstName.trim(), lastName.trim()) }
        )
    }
}

/**
 * Confirms sending a static location.
 *
 * The coordinates are shown before anything is sent, because a location is not
 * something that should leave the device on a single unlabelled tap.
 */
@Composable
fun LocationShareSheet(
    latitude: Double?,
    longitude: Double?,
    isResolving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSend: (Double, Double) -> Unit
) {
    val colors = LocalAetherColors.current

    ShareSheetScaffold(onDismiss = onDismiss, title = "Send your location", testTag = "location_share_sheet") {
        Text(
            text = when {
                error != null -> error
                isResolving -> "Finding your location…"
                latitude != null && longitude != null ->
                    "This point will be sent to the chat as a static location. " +
                        "It is not a live share and will not keep updating."
                else -> "No location is available yet."
            },
            fontFamily = ManropeFontFamily,
            fontSize = 12.5.sp,
            color = if (error != null) Color(0xFFEF4444) else colors.textSecondary,
            modifier = Modifier.testTag("location_share_status")
        )

        if (latitude != null && longitude != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = String.format(java.util.Locale.US, "%.5f, %.5f", latitude, longitude),
                fontFamily = SpaceGroteskFontFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                modifier = Modifier.testTag("location_share_coordinates")
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        SheetPrimaryAction(
            label = "Send location",
            enabled = latitude != null && longitude != null && !isResolving,
            testTag = "location_send",
            onClick = { onSend(latitude ?: return@SheetPrimaryAction, longitude ?: return@SheetPrimaryAction) }
        )
    }
}

@Composable
private fun ShareSheetScaffold(
    onDismiss: () -> Unit,
    title: String,
    testTag: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val colors = LocalAetherColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AetherEmber.Shapes.L)
                .background(colors.surface)
                .border(1.dp, colors.border, AetherEmber.Shapes.L)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* keep taps inside the sheet */ }
                .padding(20.dp)
                .testTag(testTag)
        ) {
            Text(
                text = title,
                fontFamily = SpaceGroteskFontFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SheetField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    testTag: String,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    val colors = LocalAetherColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(label, fontFamily = ManropeFontFamily, fontSize = 13.sp)
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = AetherEmber.Shapes.M,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.accent,
            unfocusedBorderColor = colors.border,
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            focusedLabelColor = colors.accent,
            unfocusedLabelColor = colors.textTertiary,
            cursorColor = colors.accent
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    )
}

@Composable
private fun SheetPrimaryAction(
    label: String,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val colors = LocalAetherColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AetherEmber.Shapes.Pill)
            .background(
                if (enabled) colors.accent.copy(alpha = 0.32f) else colors.surfaceHighlight
            )
            .clickable(enabled = enabled) { onClick() }
            // Keeps the target comfortably past the 44dp minimum.
            .padding(vertical = 14.dp)
            .testTag(testTag),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontFamily = ManropeFontFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) colors.textPrimary else colors.textTertiary
        )
    }
}
