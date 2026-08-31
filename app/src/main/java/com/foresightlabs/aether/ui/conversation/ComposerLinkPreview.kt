package com.foresightlabs.aether.ui.conversation

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.foresightlabs.aether.domain.messages.ConversationMotion
import com.foresightlabs.aether.domain.text.ComposerLinkPreviewState
import com.foresightlabs.aether.domain.text.LinkPreviewCard
import com.foresightlabs.aether.ui.design.AetherAccent
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.ManropeFontFamily

/** Test handles for the preview strip, kept in one place. */
object ComposerLinkPreviewTags {
    const val Strip = "composer_link_preview"
    const val Loading = "composer_link_preview_loading"
    const val Dismiss = "composer_link_preview_dismiss"
    const val Title = "composer_link_preview_title"
    const val Thumbnail = "composer_link_preview_thumbnail"
}

/**
 * Telegram's preview for the draft's link, as Composer content.
 *
 * Deliberately not a card: no surface of its own, no border, no shadow, no
 * elevation. It sits directly on the Curtain the composer already lives on,
 * marked by the same accent bar the reply and edit strips use, so it reads as
 * part of what is being written rather than as something layered above it.
 */
@Composable
fun ComposerLinkPreviewStrip(
    state: ComposerLinkPreviewState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val card = state.card
    if (!state.isVisible) return
    val colors = LocalAetherColors.current
    val accent = AetherAccent.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .testTag(ComposerLinkPreviewTags.Strip),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The same 3dp accent mark as the reply and edit strips: this belongs to
        // the message being written, and says so the same way they do.
        val barAlpha = if (card == null) loadingAlpha() else 1f
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(if (card?.description?.isNotBlank() == true) 38.dp else 30.dp)
                .clip(CircleShape)
                .graphicsLayer { alpha = barAlpha }
                .background(accent)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            if (card == null) {
                // The whole loading state: the link, dimmed, under a breathing
                // accent mark. No spinner, no skeleton, no reserved empty frame
                // -- if Telegram has nothing, nothing was promised.
                Text(
                    text = state.url.orEmpty(),
                    fontFamily = ManropeFontFamily,
                    fontSize = 11.5.sp,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .graphicsLayer { alpha = barAlpha }
                        .testTag(ComposerLinkPreviewTags.Loading)
                )
            } else {
                Text(
                    text = card.heading,
                    fontFamily = ManropeFontFamily,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(ComposerLinkPreviewTags.Title)
                )
                Text(
                    text = card.displayUrl,
                    fontFamily = ManropeFontFamily,
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (card.description.isNotBlank()) {
                    Text(
                        text = card.description,
                        fontFamily = ManropeFontFamily,
                        fontSize = 11.sp,
                        color = colors.textSecondary.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (card != null && card.hasThumbnail) {
            Spacer(modifier = Modifier.width(8.dp))
            LinkPreviewThumbnail(card)
        }

        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .clickable(onClick = onDismiss)
                .testTag(ComposerLinkPreviewTags.Dismiss),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                // Closing the preview leaves the link exactly where it was typed.
                contentDescription = "Remove link preview",
                tint = colors.textSecondary,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

/**
 * A small square of the page's own image, when Telegram sent one.
 *
 * Restrained on purpose: thumbnail-sized, softly rounded, and never large
 * enough to turn the composer into a media surface. Telegram's embedded
 * thumbnail stands in until the downloaded bytes exist.
 */
@Composable
private fun LinkPreviewThumbnail(card: LinkPreviewCard) {
    val embedded = remember(card.thumbnailBase64) {
        card.thumbnailBase64?.let { encoded ->
            runCatching {
                val bytes = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
        }
    }
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .testTag(ComposerLinkPreviewTags.Thumbnail)
    ) {
        if (embedded != null) {
            Image(
                bitmap = embedded.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(34.dp)
            )
        }
        card.thumbnailPath?.let { path ->
            AsyncImage(
                model = path,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(34.dp)
            )
        }
    }
}

/** A slow breath rather than a spinner: present, but never demanding. */
@Composable
private fun loadingAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "link_preview_loading")
    val alpha by transition.animateFloat(
        initialValue = 0.34f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(
            animation = tween(ConversationMotion.STANDARD_MS * 3),
            repeatMode = RepeatMode.Reverse
        ),
        label = "link_preview_loading_alpha"
    )
    return alpha
}
