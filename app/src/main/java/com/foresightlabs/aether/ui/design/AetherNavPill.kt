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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.foresightlabs.aether.ui.theme.LocalAtmosphere
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
    val Height: Dp = 52.dp
    val OuterHorizontalPadding: Dp = 40.dp
    val IconSize: Dp = 20.dp
    val DestinationSlotSize: Dp = 44.dp
    val SelectionLensSize: Dp = 40.dp
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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                horizontal = AetherNavPillDefaults.OuterHorizontalPadding,
                vertical = AetherEmber.Spacing.Space12
            )
    ) {
        AetherFrostedGlass(
            frostState = frostState,
            modifier = Modifier
                .fillMaxWidth()
                .height(AetherNavPillDefaults.Height),
            shape = AetherEmber.Shapes.Pill,
            elevation = 12.dp,
            emphasis = 0.12f
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AetherNavPillDefaults.Height)
                    .padding(horizontal = AetherEmber.Spacing.Space8, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    AetherNavSlot(
                        item = item,
                        selected = item.key == selectedKey,
                        frostState = frostState,
                        modifier = Modifier.weight(1f)
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
    frostState: AetherFrostState?,
    modifier: Modifier = Modifier
) {
    val atmosphere = LocalAtmosphere.current
    val colors = LocalAetherColors.current
    val reducedMotion = LocalReducedMotion.current
    val duration = aetherDuration(AetherMotion.ControlMillis)
    val spec = tween<Color>(duration, easing = AetherMotion.ControlEasing)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && !reducedMotion) 0.97f else 1f,
        animationSpec = if (reducedMotion) snap() else AetherMotion.Stiff,
        label = "nav_press_scale"
    )
    val lensEmphasis by animateFloatAsState(
        targetValue = when {
            selected && pressed -> 0.52f
            selected -> 0.34f
            else -> 0f
        },
        animationSpec = if (reducedMotion) snap() else AetherMotion.Stiff,
        label = "nav_lens_emphasis"
    )
    val iconTint by animateColorAsState(
        targetValue = when {
            selected && colors.isDark -> Color.White
            selected -> colors.textPrimary
            pressed -> atmosphere.accent
            else -> colors.textSecondary
        },
        animationSpec = spec,
        label = "nav_tint"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(AetherEmber.Shapes.Pill)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = item.onClick
            )
            .semantics {
                contentDescription = item.contentDescription
                role = Role.Tab
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
                AetherFrostedGlass(
                    frostState = frostState,
                    modifier = Modifier
                        .size(AetherNavPillDefaults.SelectionLensSize)
                        .testTag("nav_lens_${item.key}"),
                    shape = AetherEmber.Shapes.Pill,
                    elevation = 0.dp,
                    emphasis = lensEmphasis
                ) {}
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
