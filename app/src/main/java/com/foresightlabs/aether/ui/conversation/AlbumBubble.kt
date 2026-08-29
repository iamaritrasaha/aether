package com.foresightlabs.aether.ui.conversation
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.foresightlabs.aether.domain.messages.ConversationEntry
import com.foresightlabs.aether.domain.model.MediaItem
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.ManropeFontFamily

/**
 * A grouped media album, drawn as one cluster.
 *
 * The layout follows the count, the way Telegram's own does: two side by side, three
 * as one large and two stacked, four as a square, and more as a grid with the
 * remainder counted on the last tile. Anything else — a column of full-width bubbles
 * — misrepresents a group that was sent as a single thing.
 *
 * A tile whose file has not downloaded yet draws its placeholder rather than a
 * stand-in image, so nothing claims to be content that has not arrived.
 */
@Composable
fun AlbumBubble(
    album: ConversationEntry.Album,
    contentColor: Color,
    onMediaClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val tiles = album.messages.flatMap { it.mediaItems }
    val pending = album.messages.count { it.mediaItems.isEmpty() }

    Column(modifier = modifier.testTag("album_${album.albumId}")) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AetherEmber.Shapes.M)
        ) {
            when {
                tiles.isEmpty() -> AlbumPlaceholder(
                    count = album.messages.size,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.4f)
                )
                tiles.size == 1 -> AlbumTile(
                    item = tiles.first(),
                    onClick = onMediaClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.4f)
                )
                tiles.size == 2 -> Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tiles.forEach { item ->
                        AlbumTile(
                            item = item,
                            onClick = onMediaClick,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(0.85f)
                        )
                    }
                }
                tiles.size == 3 -> Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AlbumTile(
                        item = tiles[0],
                        onClick = onMediaClick,
                        modifier = Modifier
                            .weight(2f)
                            .aspectRatio(0.9f)
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        AlbumTile(
                            item = tiles[1],
                            onClick = onMediaClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                        AlbumTile(
                            item = tiles[2],
                            onClick = onMediaClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                }
                else -> AlbumGrid(tiles = tiles, onMediaClick = onMediaClick)
            }
        }

        if (pending > 0 && tiles.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$pending more loading…",
                fontFamily = ManropeFontFamily,
                fontSize = 11.sp,
                color = contentColor.copy(alpha = 0.7f),
                modifier = Modifier.testTag("album_pending")
            )
        }

        if (album.caption.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = album.caption,
                fontFamily = ManropeFontFamily,
                fontSize = 15.sp,
                lineHeight = 20.5.sp,
                fontWeight = FontWeight.Medium,
                color = contentColor,
                modifier = Modifier.testTag("album_caption")
            )
        }
    }
}

@Composable
private fun AlbumGrid(tiles: List<MediaItem>, onMediaClick: (MediaItem) -> Unit) {
    // At most four tiles are drawn; the rest are counted on the last one, which is
    // how a nine-photo album stays a readable cluster.
    val visible = tiles.take(4)
    val overflow = tiles.size - visible.size

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        visible.chunked(2).forEachIndexed { rowIndex, row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEachIndexed { columnIndex, item ->
                    val isLastVisible = rowIndex == 1 && columnIndex == row.lastIndex
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                    ) {
                        AlbumTile(
                            item = item,
                            onClick = onMediaClick,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (isLastVisible && overflow > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.52f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+$overflow",
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.testTag("album_overflow")
                                )
                            }
                        }
                    }
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AlbumTile(
    item: MediaItem,
    onClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    AsyncImage(
        model = item.url,
        contentDescription = item.caption.ifBlank { "Photo" },
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(AetherEmber.Shapes.S)
            .clickable { onClick(item) }
    )
}

@Composable
private fun AlbumPlaceholder(count: Int, modifier: Modifier = Modifier) {
    val colors = LocalAetherColors.current
    Box(
        modifier = modifier
            .clip(AetherEmber.Shapes.M)
            .background(colors.surfaceHighlight),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Loading $count photos…",
            fontFamily = ManropeFontFamily,
            fontSize = 12.sp,
            color = colors.textTertiary,
            modifier = Modifier.testTag("album_placeholder")
        )
    }
}
