package com.foresightlabs.aether.ui.conversation
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.foresightlabs.aether.data.media.TgsDecompressor
import com.foresightlabs.aether.domain.emoji.EmojiCategory
import com.foresightlabs.aether.domain.emoji.EmojiData
import com.foresightlabs.aether.domain.model.AnimationItem
import com.foresightlabs.aether.domain.model.StickerItem
import com.foresightlabs.aether.domain.model.StickerSetInfo
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import com.foresightlabs.aether.ui.theme.SpaceGroteskFontFamily
import java.io.File

enum class PickerTab {
    EMOJI,
    STICKERS,
    GIFS
}

/**
 * Unified content panel for the expanded composer dock, housing Emoji, Sticker,
 * and GIF pickers in one living continuous surface.
 */
@Composable
fun EmojiStickerGifPanel(
    activeTab: PickerTab,
    onTabChange: (PickerTab) -> Unit,
    onInsertEmoji: (String) -> Unit,
    installedStickerSets: List<StickerSetInfo>,
    recentStickers: List<StickerItem>,
    favoriteStickers: List<StickerItem>,
    onLoadStickerSetDetails: (Long, (StickerSetInfo) -> Unit) -> Unit,
    onSendSticker: (fileId: Int, emoji: String) -> Unit,
    savedAnimations: List<AnimationItem>,
    onSendAnimation: (fileId: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAetherColors.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(252.dp)
            .testTag("emoji_sticker_gif_panel")
    ) {
        // --- Restrained Top Mode Switcher (🙂 Emoji | 🏷️ Stickers | GIF GIFs) ---
        PickerModeSwitcher(
            activeTab = activeTab,
            onTabChange = onTabChange,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        // --- Active Content Pane ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            AnimatedContent(
                targetState = activeTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(140))
                },
                label = "picker_tab_content"
            ) { tab ->
                when (tab) {
                    PickerTab.EMOJI -> EmojiPickerContent(
                        onInsertEmoji = onInsertEmoji
                    )
                    PickerTab.STICKERS -> StickerPickerContent(
                        installedSets = installedStickerSets,
                        recentStickers = recentStickers,
                        favoriteStickers = favoriteStickers,
                        onLoadSetDetails = onLoadStickerSetDetails,
                        onSendSticker = onSendSticker
                    )
                    PickerTab.GIFS -> GifPickerContent(
                        savedAnimations = savedAnimations,
                        onSendAnimation = onSendAnimation
                    )
                }
            }
        }
    }
}

/**
 * Quiet, restrained mode switcher for the expanded dock.
 */
@Composable
private fun PickerModeSwitcher(
    activeTab: PickerTab,
    onTabChange: (PickerTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val containerBg = Color(0x14FFFFFF)

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(containerBg)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModeTabButton(
            label = "Emoji",
            glyph = "🙂",
            isSelected = activeTab == PickerTab.EMOJI,
            onClick = { onTabChange(PickerTab.EMOJI) },
            testTag = "picker_tab_emoji"
        )
        ModeTabButton(
            label = "Stickers",
            glyph = "🎨",
            isSelected = activeTab == PickerTab.STICKERS,
            onClick = { onTabChange(PickerTab.STICKERS) },
            testTag = "picker_tab_stickers"
        )
        ModeTabButton(
            label = "GIFs",
            glyph = "GIF",
            isSelected = activeTab == PickerTab.GIFS,
            onClick = { onTabChange(PickerTab.GIFS) },
            testTag = "picker_tab_gifs"
        )
    }
}

@Composable
private fun ModeTabButton(
    label: String,
    glyph: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val selectedBg = Color(0x28FFFFFF)
    val textTint = if (isSelected) Color(0xFFF2F2F5) else Color(0x88FFFFFF)

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (isSelected) selectedBg else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, radius = 20.dp),
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .testTag(testTag)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = glyph,
            fontFamily = SpaceGroteskFontFamily,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = textTint
        )
        Text(
            text = label,
            fontFamily = ManropeFontFamily,
            fontSize = 11.5.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = textTint
        )
    }
}

/**
 * Categorized Emoji Grid with minimal category selector rail.
 */
@Composable
private fun EmojiPickerContent(
    onInsertEmoji: (String) -> Unit
) {
    val colors = LocalAetherColors.current
    val categories = EmojiData.categories
    var selectedCategoryId by remember { mutableStateOf("recents") }

    val recentList = EmojiData.recentEmojis

    Column(modifier = Modifier.fillMaxSize()) {
        // Category Selector Rail
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                CategoryGlyphButton(
                    glyph = "🕒",
                    label = "Recent",
                    isSelected = selectedCategoryId == "recents",
                    onClick = { selectedCategoryId = "recents" },
                    testTag = "emoji_cat_recents"
                )
            }
            items(categories, key = { it.id }) { cat ->
                CategoryGlyphButton(
                    glyph = cat.iconEmoji,
                    label = cat.name,
                    isSelected = selectedCategoryId == cat.id,
                    onClick = { selectedCategoryId = cat.id },
                    testTag = "emoji_cat_${cat.id}"
                )
            }
        }

        // Emoji Grid
        val emojisToShow = remember(selectedCategoryId, recentList) {
            if (selectedCategoryId == "recents") {
                if (recentList.isNotEmpty()) recentList else categories.first().emojis
            } else {
                categories.find { it.id == selectedCategoryId }?.emojis.orEmpty()
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 40.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .fillMaxSize()
                .testTag("emoji_grid")
        ) {
            items(emojisToShow, key = { it }) { emoji ->
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true, radius = 21.dp),
                            onClick = {
                                EmojiData.recordRecentEmoji(emoji)
                                onInsertEmoji(emoji)
                            }
                        )
                        .testTag("emoji_item_$emoji"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = emoji,
                        fontSize = 24.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryGlyphButton(
    glyph: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val activeBg = Color(0x22FFFFFF)

    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(if (isSelected) activeBg else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, radius = 16.dp),
                onClick = onClick
            )
            .testTag(testTag)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = glyph,
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Sticker Picker Content powered by real Telegram stickers.
 */
private enum class StickerTabType {
    RECENT,
    FAVORITES,
    PACK
}

@Composable
private fun StickerPickerContent(
    installedSets: List<StickerSetInfo>,
    recentStickers: List<StickerItem>,
    favoriteStickers: List<StickerItem>,
    onLoadSetDetails: (Long, (StickerSetInfo) -> Unit) -> Unit,
    onSendSticker: (fileId: Int, emoji: String) -> Unit
) {
    val colors = LocalAetherColors.current
    var currentTab by remember { mutableStateOf(StickerTabType.RECENT) }
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

    Column(modifier = Modifier.fillMaxSize()) {
        // Category Rail
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                StickerTabPill(
                    label = "Recent",
                    icon = Icons.Default.History,
                    isSelected = currentTab == StickerTabType.RECENT,
                    onClick = { currentTab = StickerTabType.RECENT }
                )
            }
            item {
                StickerTabPill(
                    label = "Favorites",
                    icon = Icons.Default.Favorite,
                    isSelected = currentTab == StickerTabType.FAVORITES,
                    onClick = { currentTab = StickerTabType.FAVORITES }
                )
            }
            items(installedSets) { set ->
                val isSelected = currentTab == StickerTabType.PACK && selectedSetId == set.id
                StickerTabPill(
                    label = set.title,
                    isSelected = isSelected,
                    onClick = {
                        currentTab = StickerTabType.PACK
                        selectedSetId = set.id
                    }
                )
            }
        }

        // Stickers Grid
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val stickersToShow = when (currentTab) {
                StickerTabType.RECENT -> recentStickers
                StickerTabType.FAVORITES -> favoriteStickers
                StickerTabType.PACK -> currentSetDetails?.stickers.orEmpty()
            }

            if (isLoadingSet && currentTab == StickerTabType.PACK) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = colors.accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp)
                    )
                }
            } else if (stickersToShow.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (currentTab) {
                            StickerTabType.RECENT -> "No recent stickers"
                            StickerTabType.FAVORITES -> "No favorite stickers"
                            StickerTabType.PACK -> "No stickers in this pack"
                        },
                        fontFamily = ManropeFontFamily,
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 64.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("sticker_grid")
                ) {
                    items(stickersToShow, key = { it.fileId }) { sticker ->
                        StickerCell(
                            sticker = sticker,
                            onClick = { onSendSticker(sticker.fileId, sticker.emoji) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StickerTabPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalAetherColors.current
    val pillBg = if (isSelected) Color(0x28FFFFFF) else Color(0x10FFFFFF)

    Row(
        modifier = Modifier
            .clip(AetherEmber.Shapes.Pill)
            .background(pillBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, radius = 18.dp),
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) colors.textPrimary else colors.textSecondary,
                modifier = Modifier.size(13.dp)
            )
        }
        Text(
            text = label,
            fontFamily = ManropeFontFamily,
            fontSize = 11.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) colors.textPrimary else colors.textSecondary
        )
    }
}

@Composable
private fun StickerCell(
    sticker: StickerItem,
    onClick: () -> Unit
) {
    val path = sticker.localPath

    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, radius = 32.dp),
                onClick = onClick
            )
            .padding(2.dp)
            .testTag("sticker_item_${sticker.fileId}"),
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
                        modifier = Modifier.size(56.dp)
                    )
                    return@Box
                }
            }

            if (sticker.isVideo || path.endsWith(".webm", ignoreCase = true)) {
                LoopingVideoSticker(
                    filePath = path,
                    modifier = Modifier.size(56.dp),
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
                        Text(text = sticker.emoji.ifBlank { "🎨" }, fontSize = 24.sp)
                    }
                }
            )
        } else {
            Text(
                text = sticker.emoji.ifBlank { "🎨" },
                fontSize = 28.sp
            )
        }
    }
}

/**
 * GIF Picker Content powered by real Telegram animations.
 */
@Composable
private fun GifPickerContent(
    savedAnimations: List<AnimationItem>,
    onSendAnimation: (fileId: Int) -> Unit
) {
    val colors = LocalAetherColors.current
    var query by remember { mutableStateOf("") }

    val filteredAnimations = remember(savedAnimations, query) {
        if (query.isBlank()) {
            savedAnimations
        } else {
            savedAnimations.filter {
                it.fileName.contains(query, ignoreCase = true) || query in "gif"
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Small GIF Search input inside the dock
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val searchBg = Color(0x18FFFFFF)
            val searchBorder = Color(0x14FFFFFF)

            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(CircleShape)
                    .background(searchBg)
                    .border(0.5.dp, searchBorder, CircleShape)
                .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(15.dp)
                )
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    textStyle = TextStyle(
                        fontFamily = ManropeFontFamily,
                        fontSize = 12.sp,
                        color = colors.textPrimary
                    ),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        if (query.isEmpty()) {
                            Text(
                                text = "Search GIFs",
                                fontFamily = ManropeFontFamily,
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                        innerTextField()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("gif_search_input")
                )
                if (query.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .clickable { query = "" },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // GIFs Grid
        if (filteredAnimations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (query.isBlank()) "No saved GIFs" else "No GIFs matching “$query”",
                    fontFamily = ManropeFontFamily,
                    fontSize = 13.sp,
                    color = colors.textSecondary
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 100.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .testTag("gif_grid")
            ) {
                items(filteredAnimations, key = { it.fileId }) { anim ->
                    GifCell(
                        animation = anim,
                        onClick = { onSendAnimation(anim.fileId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GifCell(
    animation: AnimationItem,
    onClick: () -> Unit
) {
    val colors = LocalAetherColors.current
    val path = animation.localPath ?: animation.thumbnailPath

    Box(
        modifier = Modifier
            .height(78.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x14FFFFFF))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick
            )
            .testTag("gif_item_${animation.fileId}"),
        contentAlignment = Alignment.Center
    ) {
        if (!path.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(path)
                    .crossfade(true)
                    .build(),
                contentDescription = "GIF",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = colors.accent,
                            strokeWidth = 1.5.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )
        } else {
            Text(
                text = "GIF",
                fontFamily = SpaceGroteskFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textSecondary
            )
        }
    }
}
