package com.foresightlabs.aether.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.LocalAtmosphere
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.ManropeFontFamily

/**
 * The canonical Aether search field.
 *
 * Focus indication is drawn from the current atmosphere rather than a fixed accent.
 * There is deliberately no voice-search affordance: Aether does not yet implement
 * voice input, and an inert microphone would be a fabricated capability.
 */
@Composable
fun AetherSearchPill(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "Search",
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    requestFocus: Boolean = false,
    onClick: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
    onClearClick: (() -> Unit)? = null,
    /**
     * Placed directly on the Living Atmosphere rather than on a foreground surface.
     * The field then reads as a quiet translucent lens instead of an opaque slab.
     */
    onAtmosphere: Boolean = false,
    height: Dp = 46.dp
) {
    val shape = RoundedCornerShape(if (onAtmosphere) 999.dp else 16.dp)
    val atmosphere = LocalAtmosphere.current
    val colors = LocalAetherColors.current
    val focusRequester = remember { FocusRequester() }

    val fieldBackground = if (onAtmosphere) Color(0x1C000000) else colors.input
    val fieldBorder = if (onAtmosphere) Color(0x24FFFFFF) else colors.borderSubtle
    val leadingTint = if (onAtmosphere) colors.atmosphereTextTertiary else colors.textSecondary
    val placeholderTint = if (onAtmosphere) colors.atmosphereTextMuted else colors.textTertiary
    val valueTint = if (onAtmosphere) colors.atmosphereTextPrimary else colors.textPrimary

    LaunchedEffect(requestFocus) {
        if (requestFocus && !readOnly) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(fieldBackground)
            .border(0.75.dp, fieldBorder, shape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = leadingTint,
                modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.width(9.dp))

            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = placeholderTint,
                        fontFamily = ManropeFontFamily,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (!readOnly) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        textStyle = TextStyle(
                            color = valueTint,
                            fontFamily = ManropeFontFamily,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(atmosphere.accent),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { if (it.isFocused) onFocused?.invoke() }
                            .testTag("search_pill_input")
                    )
                }
            }

            if (value.isNotEmpty()) {
                IconButton(
                    onClick = {
                        onValueChange("")
                        onClearClick?.invoke()
                    },
                    modifier = Modifier
                        .defaultMinSize(minWidth = 32.dp, minHeight = 32.dp)
                        .size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search",
                        tint = leadingTint,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
