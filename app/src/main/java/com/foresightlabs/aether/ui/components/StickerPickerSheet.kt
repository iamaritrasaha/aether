package com.foresightlabs.aether.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.foresightlabs.aether.data.media.TgsDecompressor
import com.foresightlabs.aether.domain.model.StickerItem
import com.foresightlabs.aether.domain.model.StickerSetInfo
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import com.foresightlabs.aether.ui.theme.SpaceGroteskFontFamily
import java.io.File

private enum class StickerTab {
    RECENT,
    FAVORITES,
    PACK
}

@Composable
fun StickerPickerSheet(
    isVisible: Boolean,
    installedSets: List<StickerSetInfo>,
    recentStickers: List<StickerItem>,
    favoriteStickers: List<StickerItem>,
    onLoadSetDetails: (Long, (StickerSetInfo) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onSendSticker: (fileId: Int, emoji: String) -> Unit
) {
    val colors = LocalAetherColors.current
    var currentTab by remember { mutableStateOf(StickerTab.RECENT) }
    var selectedSetId by remember { mutableStateOf<Long?>(null) }
    var currentSetDetails by remember { mutableStateOf<StickerSetInfo?>(null) }
    var isLoadingSet by remember { mutableStateOf(false) }

    LaunchedEffect(selectedSetId) {
        val setId = selectedSetId
        if (setId != null && setId != 0L) {
            isLoadingSet = true
            onLoadSetDetails(setId) { details ->
                currentSetDetails = details
                isLoadingSet = false
            }
        }
    }

    if (isVisible) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .clip(AetherEmber.Shapes.RisingSheet)
                        .background(colors.surface)
                        .border(1.dp, colors.border, AetherEmber.Shapes.RisingSheet)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* absorb taps inside sheet */ }
                        .padding(top = 16.dp)
                        .navigationBarsPadding()
                        .testTag("sticker_picker_sheet")
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Stickers",
                                fontFamily = SpaceGroteskFontFamily,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Category Rail
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            item {
                                TabPill(
                                    label = "Recent",
                                    icon = Icons.Default.History,
                                    isSelected = currentTab == StickerTab.RECENT,
                                    onClick = { currentTab = StickerTab.RECENT }
                                )
                            }
                            item {
                                TabPill(
                                    label = "Favorites",
                                    icon = Icons.Default.Favorite,
                                    isSelected = currentTab == StickerTab.FAVORITES,
                                    onClick = { currentTab = StickerTab.FAVORITES }
                                )
                            }
                            items(installedSets) { set ->
                                val isSelected = currentTab == StickerTab.PACK && selectedSetId == set.id
                                TabPill(
                                    label = set.title,
                                    isSelected = isSelected,
                                    onClick = {
                                        currentTab = StickerTab.PACK
                                        selectedSetId = set.id
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Content Grid
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            val stickersToShow = when (currentTab) {
                                StickerTab.RECENT -> recentStickers
                                StickerTab.FAVORITES -> favoriteStickers
                                StickerTab.PACK -> currentSetDetails?.stickers.orEmpty()
                            }

                            if (isLoadingSet && currentTab == StickerTab.PACK) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = colors.accent,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            } else if (stickersToShow.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = when (currentTab) {
                                            StickerTab.RECENT -> "No recent stickers"
                                            StickerTab.FAVORITES -> "No favorite stickers"
                                            StickerTab.PACK -> "No stickers in this pack"
                                        },
                                        fontFamily = ManropeFontFamily,
                                        fontSize = 14.sp,
                                        color = colors.textSecondary
                                    )
                                }
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = 68.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(stickersToShow, key = { it.fileId }) { sticker ->
                                        StickerPickerItem(
                                            sticker = sticker,
                                            onClick = {
                                                onSendSticker(sticker.fileId, sticker.emoji)
                                                onDismiss()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalAetherColors.current
    Row(
        modifier = Modifier
            .clip(AetherEmber.Shapes.Pill)
            .background(
                if (isSelected) colors.accent.copy(alpha = 0.32f) else colors.surfaceElevated
            )
            .border(
                1.dp,
                if (isSelected) colors.accent else colors.border,
                AetherEmber.Shapes.Pill
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) colors.textPrimary else colors.textSecondary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = label,
            fontFamily = ManropeFontFamily,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) colors.textPrimary else colors.textSecondary
        )
    }
}

@Composable
private fun StickerPickerItem(
    sticker: StickerItem,
    onClick: () -> Unit
) {
    val colors = LocalAetherColors.current
    val path = sticker.localPath

    Box(
        modifier = Modifier
            .size(68.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (!path.isNullOrBlank()) {
            if (sticker.isAnimated || path.endsWith(".tgs", ignoreCase = true)) {
                val file = remember(path) { File(path) }
                val lottieJson = remember(path) { TgsDecompressor.decompressFile(file) }
                if (lottieJson != null) {
                    val composition by rememberLottieComposition(LottieCompositionSpec.JsonString(lottieJson))
                    LottieAnimation(
                        composition = composition,
                        iterations = LottieConstants.IterateForever,
                        modifier = Modifier.size(60.dp)
                    )
                    return@Box
                }
            }

            if (sticker.isVideo || path.endsWith(".webm", ignoreCase = true)) {
                LoopingVideoSticker(
                    filePath = path,
                    modifier = Modifier.size(60.dp),
                    contentScale = ContentScale.Fit
                )
                return@Box
            }

            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(path)
                    .crossfade(true)
                    .build(),
                contentDescription = sticker.emoji.ifBlank { "Sticker" },
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = sticker.emoji.ifBlank { "🎨" }, fontSize = 28.sp)
                    }
                }
            )
        } else {
            Text(
                text = sticker.emoji.ifBlank { "🎨" },
                fontSize = 32.sp
            )
        }
    }
}
