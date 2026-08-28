package com.foresightlabs.aether.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.AetherMotion
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.LocalReducedMotion
import com.foresightlabs.aether.ui.theme.aetherDuration

@Immutable
data class AetherNavItem(
    val key: String,
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit
)

object AetherNavPillDefaults {
    val Height: Dp = 62.dp
    val OuterHorizontalPadding: Dp = 36.dp
    val IconSize: Dp = 22.dp
    val DestinationSlotSize: Dp = 48.dp
    val SelectionLensSize: Dp = 44.dp
}

/**
 * The compact Aether dock.
 *
 * Fixed near the safe bottom and independent of the conversation list — it is not
 * part of any scrolling surface. Icon-only, so every slot carries a
 * [AetherNavItem.contentDescription] and a selected state for screen readers.
 */
@Composable
fun AetherNavPill(
    items: List<AetherNavItem>,
    selectedKey: String,
    modifier: Modifier = Modifier,
    frostState: AetherFrostState? = null
) {
    val pillShape = RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                horizontal = AetherNavPillDefaults.OuterHorizontalPadding,
                vertical = AetherEmber.Spacing.Space12
            ),
        contentAlignment = Alignment.Center
    ) {
        AetherGlass(
            frostState = frostState,
            modifier = Modifier
                .wrapContentWidth()
                .height(AetherNavPillDefaults.Height),
            shape = pillShape,
            elevation = 8.dp,
            emphasis = 0.08f
        ) {
            Row(
                modifier = Modifier
                    .wrapContentWidth()
                    .height(AetherNavPillDefaults.Height)
                    .padding(horizontal = AetherEmber.Spacing.Space12),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    AetherNavSlot(
                        item = item,
                        selected = item.key == selectedKey
                    )
                }
            }
        }
    }
}

@Composable
private fun AetherNavSlot(
    item: AetherNavItem,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LocalAetherColors.current
    val reducedMotion = LocalReducedMotion.current
    val duration = aetherDuration(AetherMotion.ControlMillis)
    val spec = tween<Color>(duration, easing = AetherMotion.ControlEasing)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && !reducedMotion) 0.94f else 1f,
        animationSpec = if (reducedMotion) snap() else AetherMotion.Stiff,
        label = "nav_press_scale"
    )

    val iconTint by animateColorAsState(
        targetValue = when {
            selected -> Color.White
            else -> colors.textTertiary
        },
        animationSpec = spec,
        label = "nav_icon_tint"
    )

    val lensEmphasis by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else tween(duration, easing = AetherMotion.ControlEasing),
        label = "nav_lens_emphasis"
    )

    Box(
        modifier = modifier
            .size(AetherNavPillDefaults.DestinationSlotSize)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = item.onClick
            )
            .semantics(mergeDescendants = true) {
                this.contentDescription = item.contentDescription
                this.selected = selected
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(AetherNavPillDefaults.DestinationSlotSize)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .testTag("nav_slot_${item.key}"),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = selected,
                enter = if (reducedMotion) fadeIn(snap()) else fadeIn(tween(duration)) + scaleIn(
                    initialScale = 0.72f,
                    animationSpec = AetherMotion.Stiff
                ),
                exit = if (reducedMotion) fadeOut(snap()) else fadeOut(tween(duration)) + scaleOut(
                    targetScale = 0.72f,
                    animationSpec = AetherMotion.Stiff
                )
            ) {
                Box(
                    modifier = Modifier
                        .size(AetherNavPillDefaults.SelectionLensSize)
                        .shadow(
                            elevation = 2.dp,
                            shape = CircleShape,
                            ambientColor = Color.Black.copy(alpha = 0.14f),
                            spotColor = Color.Black.copy(alpha = 0.18f)
                        )
                        .clip(CircleShape)
                        .drawWithCache {
                            val specular = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0f to Color.White.copy(alpha = 0.08f),
                                    0.45f to Color.Transparent,
                                    1f to Color.White.copy(alpha = 0.01f)
                                )
                            )
                            onDrawWithContent {
                                drawRect(specular)
                                drawContent()
                            }
                        }
                        .border(
                            width = 0.75.dp,
                            brush = Brush.verticalGradient(
                                0f to Color.White.copy(alpha = 0.22f),
                                0.5f to Color.White.copy(alpha = 0.08f),
                                1f to Color.White.copy(alpha = 0.02f)
                            ),
                            shape = CircleShape
                        )
                        .testTag("nav_lens_${item.key}")
                )
            }
            AetherNavIcon(item = item, tint = iconTint)
        }
    }
}

@Composable
private fun BoxScope.AetherNavIcon(item: AetherNavItem, tint: Color) {
    Icon(
        imageVector = item.icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .size(AetherNavPillDefaults.IconSize)
            .testTag("nav_icon_${item.key}")
            .align(Alignment.Center)
    )
}
