package com.foresightlabs.aether.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.foresightlabs.aether.domain.model.ChatType
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import com.foresightlabs.aether.ui.theme.OnlineGreen

@Composable
fun AetherAvatar(
    initials: String,
    gradient: List<Color>,
    size: Dp = 48.dp,
    isOnline: Boolean = false,
    chatType: ChatType = ChatType.DIRECT,
    photoPath: String? = null,
    showGlowingRim: Boolean = false,
    modifier: Modifier = Modifier
) {
    val outerRimPadding = if (showGlowingRim) 2.5.dp else 0.dp
    val innerSize = if (showGlowingRim) size - 5.dp else size

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Optional subtle glowing warm rim
        if (showGlowingRim) {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .border(
                        width = 1.5.dp,
                        brush = Brush.sweepGradient(
                            listOf(
                                Color(0xFFFF9A4A),
                                Color(0xFFFF7038),
                                Color(0xFFF04425),
                                Color(0xFFFF9A4A)
                            )
                        ),
                        shape = CircleShape
                    )
            )
        }

        val brush = if (gradient.size >= 2) {
            Brush.linearGradient(gradient)
        } else {
            Brush.linearGradient(
                listOf(
                    AetherEmber.Colors.BrightOrange,
                    AetherEmber.Colors.Vermilion
                )
            )
        }

        Box(
            modifier = Modifier
                .size(innerSize)
                .clip(CircleShape)
                .background(brush),
            contentAlignment = Alignment.Center
        ) {
            when (chatType) {
                ChatType.SAVED_MESSAGES -> {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = "Saved Messages",
                        tint = Color.White,
                        modifier = Modifier.size(innerSize * 0.5f)
                    )
                }
                ChatType.CHANNEL -> {
                    if (initials.isNotEmpty()) {
                        Text(
                            text = initials,
                            color = Color.White,
                            fontFamily = ManropeFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = (innerSize.value * 0.36f).sp,
                            letterSpacing = 0.5.sp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Campaign,
                            contentDescription = "Channel",
                            tint = Color.White,
                            modifier = Modifier.size(innerSize * 0.5f)
                        )
                    }
                }
                ChatType.GROUP -> {
                    if (initials.isNotEmpty()) {
                        Text(
                            text = initials,
                            color = Color.White,
                            fontFamily = ManropeFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = (innerSize.value * 0.36f).sp,
                            letterSpacing = 0.5.sp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = "Group",
                            tint = Color.White,
                            modifier = Modifier.size(innerSize * 0.5f)
                        )
                    }
                }
                else -> {
                    Text(
                        text = initials,
                        color = Color.White,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = (innerSize.value * 0.38f).sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            if (!photoPath.isNullOrBlank() && chatType != ChatType.SAVED_MESSAGES) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(photoPath)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(innerSize)
                        .clip(CircleShape)
                )
            }
        }

        if (isOnline) {
            val indicatorSize = (size * 0.26f).coerceAtLeast(10.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 1.dp, y = 1.dp)
                    .size(indicatorSize)
                    .clip(CircleShape)
                    .background(OnlineGreen)
                    .border(2.dp, AetherEmber.Colors.Background, CircleShape)
            )
        }
    }
}
