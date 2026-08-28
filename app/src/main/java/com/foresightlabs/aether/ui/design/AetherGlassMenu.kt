package com.foresightlabs.aether.ui.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foresightlabs.aether.ui.theme.AetherMotion
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.LocalReducedMotion
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import com.foresightlabs.aether.ui.theme.aetherDuration

/**
 * Shared Design Tokens for Aether Transient Glass Surfaces.
 *
 * All transient surfaces (overflow popups, context menus, action sheets, and selection capsules)
 * derive their geometry and spacing from these canonical values.
 */
object AetherGlassMenuDefaults {
    val PopupRadius = 22.dp
    val SheetRadius = 28.dp
    val ItemMinHeight = 48.dp
    val IconSize = 22.dp
    val ItemSpacing = 14.dp
    val PopupPadding = PaddingValues(vertical = 8.dp)
    val PopupMaxWidth = 280.dp
    val PopupEdgeMargin = 16.dp
    val PopupTopMargin = 8.dp
    val DestructiveRed = Color(0xFFEF4444)
}

/**
 * Canonical transient glass container.
 *
 * Inherits visual characteristics from [AetherFrostedGlass] — real background frost,
 * specular highlight, and restrained depth shadow.
 */
@Composable
fun AetherGlassMenuSurface(
    modifier: Modifier = Modifier,
    frostState: AetherFrostState? = null,
    shape: Shape = RoundedCornerShape(AetherGlassMenuDefaults.PopupRadius),
    elevation: Dp = 8.dp,
    emphasis: Float = 0.2f,
    content: @Composable ColumnScope.() -> Unit
) {
    AetherGlass(
        frostState = frostState,
        modifier = modifier,
        shape = shape,
        elevation = elevation,
        emphasis = emphasis
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp),
            content = content
        )
    }
}

/**
 * Standardized row item for Aether glass menus.
 *
 * Maintains a >= 48dp touch target, restrained press luminosity, and clear destructive semantics.
 */
@Composable
fun AetherGlassMenuItem(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isDestructive: Boolean = false,
    tint: Color? = null,
    enabled: Boolean = true,
    testTag: String = "",
    trailingContent: (@Composable () -> Unit)? = null
) {
    val colors = LocalAetherColors.current
    val reducedMotion = LocalReducedMotion.current
    val resolvedTint = when {
        !enabled -> colors.textMuted
        isDestructive -> AetherGlassMenuDefaults.DestructiveRed
        tint != null -> tint
        else -> colors.textPrimary
    }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && enabled && !reducedMotion) 0.985f else 1f,
        animationSpec = if (reducedMotion) snap() else AetherMotion.Stiff,
        label = "glass_menu_press_scale"
    )
    val pressedBackground by animateColorAsState(
        targetValue = if (pressed && enabled) {
            Color.White.copy(alpha = 0.08f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(aetherDuration(AetherMotion.MicroMillis)),
        label = "glass_menu_press_tint"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier)
            .defaultMinSize(minHeight = AetherGlassMenuDefaults.ItemMinHeight)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(12.dp))
            .background(pressedBackground)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AetherGlassMenuDefaults.ItemSpacing)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = resolvedTint,
                modifier = Modifier.size(AetherGlassMenuDefaults.IconSize)
            )
        }

        Text(
            text = title,
            fontFamily = ManropeFontFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = resolvedTint,
            modifier = Modifier.weight(1f)
        )

        trailingContent?.invoke()
    }
}

/** Subtle divider for sectioning glass menu actions. */
@Composable
fun AetherGlassMenuDivider(
    modifier: Modifier = Modifier
) {
    val colors = LocalAetherColors.current
    HorizontalDivider(
        color = colors.divider,
        thickness = 0.5.dp,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

/**
 * Anchored/Top-Right Glass Popup Container.
 *
 * Appears close to the triggering control (e.g. top-right floating header action)
 * with a soft scale + fade entrance, dark atmospheric scrim, and click-outside dismissal.
 */
@Composable
fun AetherGlassPopup(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    frostState: AetherFrostState? = null,
    shape: Shape = RoundedCornerShape(AetherGlassMenuDefaults.PopupRadius),
    alignment: Alignment = Alignment.TopEnd,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!isVisible) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
            .testTag("glass_popup_scrim")
    ) {
        AnimatedVisibility(
            visible = isVisible,
            modifier = Modifier
                .align(alignment)
                .statusBarsPadding()
                .padding(
                    top = AetherFloatingHeaderDefaults.ExpandedHeight + AetherGlassMenuDefaults.PopupTopMargin,
                    end = AetherGlassMenuDefaults.PopupEdgeMargin,
                    start = AetherGlassMenuDefaults.PopupEdgeMargin
                ),
            enter = scaleIn(
                initialScale = 0.95f,
                transformOrigin = TransformOrigin(0.9f, 0f),
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
            ) + fadeIn(animationSpec = tween(150)),
            exit = scaleOut(targetScale = 0.96f) + fadeOut(animationSpec = tween(120))
        ) {
            AetherGlassMenuSurface(
                frostState = frostState,
                shape = shape,
                elevation = 10.dp,
                emphasis = 0.25f,
                modifier = modifier
                    .widthIn(min = 220.dp, max = AetherGlassMenuDefaults.PopupMaxWidth)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* retain clicks inside popup */ }
            ) {
                content()
            }
        }
    }
}
