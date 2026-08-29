package com.foresightlabs.aether.ui.conversation
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foresightlabs.aether.ui.design.AetherAccent
import com.foresightlabs.aether.ui.design.AetherGlass
import com.foresightlabs.aether.ui.design.AetherGlassTokens
import com.foresightlabs.aether.ui.design.AetherIconButton
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import com.foresightlabs.aether.ui.theme.SpaceGroteskFontFamily


data class AttachmentOption(
    val title: String,
    val icon: ImageVector,
    val isPrimaryAccent: Boolean = false
)

@Composable
fun AttachmentSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onOptionSelected: (String) -> Unit
) {
    if (!isVisible) return

    val colors = LocalAetherColors.current

    val options = remember {
        buildList {
            add(AttachmentOption("Gallery", Icons.Default.PhotoLibrary, isPrimaryAccent = true))
            add(AttachmentOption("Camera", Icons.Default.CameraAlt))
            add(AttachmentOption("Video Message", Icons.Default.Videocam))
            add(AttachmentOption("File", Icons.Default.Description))
            add(AttachmentOption("Audio", Icons.Default.Headphones))
            add(AttachmentOption("Location", Icons.Default.LocationOn))
            if (com.foresightlabs.aether.AetherFeatureFlags.LIVE_LOCATION_ENABLED) {
                add(AttachmentOption("Live Location", Icons.Default.NearMe))
            }
            add(AttachmentOption("Venue", Icons.Default.Place))
            add(AttachmentOption("Contact", Icons.Default.Person))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
            .testTag("attachment_sheet_scrim"),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            val sheetShape = RoundedCornerShape(topStart = AetherGlassTokens.SheetRadius, topEnd = AetherGlassTokens.SheetRadius)
            AetherGlass(
                frostState = null,
                shape = sheetShape,
                elevation = 12.dp,
                emphasis = 0.25f,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* consume taps */ }
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
                    .testTag("attachment_sheet_content")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Grabber handle
                    Box(
                        modifier = Modifier
                            .size(width = 36.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(Color(0x35FFFFFF))
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Title & close affordance
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Share Content",
                            fontFamily = SpaceGroteskFontFamily,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )

                        AetherIconButton(
                            icon = Icons.Default.Close,
                            contentDescription = "Close",
                            onClick = onDismiss,
                            modifier = Modifier.testTag("attachment_sheet_close")
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 4-Column Adaptive Grid Layout
                    val chunks = options.chunked(4)
                    chunks.forEachIndexed { rowIndex, rowOptions ->
                        if (rowIndex > 0) {
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowOptions.forEach { opt ->
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    AttachmentItemButton(
                                        option = opt,
                                        onOptionSelected = onOptionSelected,
                                        onDismiss = onDismiss
                                    )
                                }
                            }
                            // Fill trailing empty slots in last row to maintain column alignment
                            if (rowOptions.size < 4) {
                                repeat(4 - rowOptions.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun AttachmentItemButton(
    option: AttachmentOption,
    onOptionSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalAetherColors.current
    val normalizedTag = option.title.lowercase().replace(" ", "_")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(AetherEmber.Shapes.M)
            .clickable {
                onOptionSelected(option.title)
                onDismiss()
            }
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .testTag("attachment_$normalizedTag")
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(0x22FFFFFF))
                .border(BorderStroke(0.5.dp, colors.borderSubtle), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = option.icon,
                contentDescription = option.title,
                tint = if (option.isPrimaryAccent) AetherAccent.current else colors.textPrimary,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = option.title,
            fontFamily = ManropeFontFamily,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
