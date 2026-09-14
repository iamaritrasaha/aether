package com.foresightlabs.aether.ui.bookmarks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.foresightlabs.aether.ui.design.AetherAtmosphericBackground
import com.foresightlabs.aether.ui.design.AetherBackButton
import com.foresightlabs.aether.ui.design.AetherFloatingHeader
import com.foresightlabs.aether.ui.design.AetherIconButton
import com.foresightlabs.aether.ui.design.aetherFloatingHeaderContentTopPadding
import com.foresightlabs.aether.ui.design.rememberAetherFrostState
import com.foresightlabs.aether.ui.design.rememberAetherFloatingHeaderScrollFraction
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Aether-local bookmarks: every message this account saved on THIS device.
 *
 * The local-only nature is stated once, here, in the header's subtitle --
 * never as per-message chrome -- and everything listed was resolved on
 * demand: what is on disk is only a stable identity.
 */
@Composable
fun BookmarksScreen(
    onBack: () -> Unit,
    /** Opens the bookmark's conversation and jumps to the message. */
    onOpenBookmark: (chatId: Long, messageId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: BookmarksViewModel = viewModel()
    // Null until the store answers: show nothing rather than a false empty state.
    val rows = viewModel.rows.collectAsStateWithLifecycle().value
    val colors = LocalAetherColors.current
    val listState = rememberLazyListState()
    val headerScrollFraction = rememberAetherFloatingHeaderScrollFraction(listState)
    val frostState = rememberAetherFrostState()
    val zone = remember { TimeZone.getDefault() }

    Box(modifier = modifier.fillMaxSize()) {
        AetherAtmosphericBackground(modifier = Modifier.fillMaxSize(), frostState = frostState) {
            if (rows == null) {
                Box(modifier = Modifier.fillMaxSize())
            } else if (rows.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = aetherFloatingHeaderContentTopPadding())
                        .padding(horizontal = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        tint = colors.textSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "No bookmarks yet",
                        fontFamily = ManropeFontFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Long-press a message, tap More, then Bookmark. Bookmarks stay on this device only.",
                        fontFamily = ManropeFontFamily,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = colors.textSecondary,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("bookmarks_list"),
                    contentPadding = PaddingValues(top = aetherFloatingHeaderContentTopPadding(), bottom = 40.dp)
                ) {
                    item {
                        Text(
                            text = "Saved only in Aether — never synced to Telegram or any other device.",
                            fontFamily = ManropeFontFamily,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 14.dp)
                        )
                    }
                    items(rows, key = { "${it.chatId}:${it.messageId}" }) { row ->
                        BookmarkRowView(
                            row = row,
                            zone = zone,
                            onOpen = { onOpenBookmark(row.chatId, row.messageId) },
                            onRemove = { viewModel.remove(row) }
                        )
                        HorizontalDivider(
                            color = colors.divider,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(start = 68.dp)
                        )
                    }
                }
            }
        }

        AetherFloatingHeader(
            title = "Bookmarks",
            modifier = Modifier.align(Alignment.TopCenter),
            scrollFraction = headerScrollFraction,
            frostState = frostState,
            navigation = { AetherBackButton(onClick = onBack) }
        )
    }
}

@Composable
private fun BookmarkRowView(
    row: BookmarkRow,
    zone: TimeZone,
    onOpen: () -> Unit,
    onRemove: () -> Unit
) {
    val colors = LocalAetherColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !row.isMissing && !row.chatIsMissing, onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.surfaceElevated)
                .border(1.dp, colors.border, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = (row.chatTitle ?: "?").take(1).uppercase(),
                fontFamily = ManropeFontFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = colors.accent
            )
        }
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.chatTitle ?: if (row.chatIsMissing) "Unavailable conversation" else "Loading…",
                    fontFamily = ManropeFontFamily,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = savedDateFormat.get().apply { timeZone = zone }
                        .format(Date(row.savedAtSec * 1000L)),
                    fontFamily = ManropeFontFamily,
                    fontSize = 11.sp,
                    color = colors.textSecondary
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = when {
                    row.isMissing -> "Original message no longer available"
                    row.preview != null -> row.preview
                    row.previewUnavailable -> "Preview unavailable right now"
                    else -> "Resolving…"
                },
                fontFamily = ManropeFontFamily,
                fontSize = 12.5.sp,
                lineHeight = 16.sp,
                color = if (row.isMissing) colors.textSecondary.copy(alpha = 0.7f) else colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        AetherIconButton(
            icon = Icons.Default.Close,
            contentDescription = "Remove bookmark",
            onClick = onRemove,
            size = 34.dp,
            iconSize = 16.dp,
            tint = colors.textSecondary
        )
    }
}

// Per-thread formatter, lazily initialized where it is used.
private val savedDateFormat: ThreadLocal<SimpleDateFormat> = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue() = SimpleDateFormat("MMM d", Locale.getDefault())
}
