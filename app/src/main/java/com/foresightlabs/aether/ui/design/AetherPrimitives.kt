package com.foresightlabs.aether.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.AetherMotion
import com.foresightlabs.aether.ui.theme.AetherType
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.LocalAtmosphere
import com.foresightlabs.aether.ui.theme.LocalReducedMotion
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import com.foresightlabs.aether.ui.theme.SpaceGroteskFontFamily
import com.foresightlabs.aether.ui.theme.aetherDuration

/**
 * Canonical Aether primitives.
 *
 * Screens compose these; they do not re-derive surfaces, radii, spacing or accents
 * locally. If a screen needs a new visual pattern, the pattern belongs here first.
 */

/** Minimum practical touch target. Icon-only controls must not go below this. */
val AetherMinTouchTarget: Dp = 44.dp

/**
 * Aether has no visually attached app bars. Primary navigation headers are floating
 * foreground surfaces suspended over the living interface.
 */
object AetherFloatingHeaderDefaults {
    val HorizontalMargin = AetherEmber.Spacing.Space16
    val TopGap = AetherEmber.Spacing.Space8
    val ExpandedHeight = 64.dp
    val CompactHeight = 56.dp
    val ContentGap = AetherEmber.Spacing.Space8
    val Shape = AetherEmber.Shapes.L
}

/** Top inset for Layer 2 content so its initial state is never hidden by Layer 3. */
@Composable
fun aetherFloatingHeaderContentTopPadding(
    headerHeight: Dp = AetherFloatingHeaderDefaults.ExpandedHeight,
    extraGap: Dp = AetherFloatingHeaderDefaults.ContentGap
): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
    AetherFloatingHeaderDefaults.TopGap + headerHeight + extraGap

/** Shared restrained reaction for lazy content; never hides the navigation surface. */
@Composable
fun rememberAetherFloatingHeaderScrollFraction(listState: LazyListState): Float {
    val fraction by remember(listState) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / 64f).coerceIn(0f, 1f)
        }
    }
    return fraction
}

/** Elevation levels in the Aether near-black family (Layer 3). */
enum class AetherElevation { Base, Surface, Elevated, High }

@Composable
private fun elevationColor(level: AetherElevation): Color {
    val colors = LocalAetherColors.current
    return when (level) {
        AetherElevation.Base -> colors.background
        AetherElevation.Surface -> colors.surface
        AetherElevation.Elevated -> colors.surfaceElevated
        AetherElevation.High -> colors.surfaceHighlight
    }
}

/**
 * An opaque near-black foreground surface. The default container for anything the
 * user reads or operates.
 */
@Composable
fun AetherSurface(
    modifier: Modifier = Modifier,
    elevation: AetherElevation = AetherElevation.Surface,
    shape: Shape = AetherEmber.Shapes.L,
    bordered: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = LocalAetherColors.current
    Box(
        modifier = modifier
            .clip(shape)
            .background(elevationColor(elevation))
            .then(if (bordered) Modifier.border(1.dp, colors.borderSubtle, shape) else Modifier),
        content = content
    )
}

/**
 * Translucent glass. A functional material for surfaces that float above the
 * atmosphere — docks, overlays, sheets, floating controls.
 *
 * Deliberately does not blur: per-row runtime blur is the single most expensive
 * thing this UI could do, and glass belongs on a handful of floating elements
 * rather than behind every list item.
 */
@Composable
fun AetherGlass(
    modifier: Modifier = Modifier,
    shape: Shape = AetherEmber.Shapes.L,
    tint: Color? = null,
    borderColor: Color? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = LocalAetherColors.current
    val resolvedTint = tint ?: colors.surfaceGlass
    val resolvedBorder = borderColor ?: colors.border
    Box(
        modifier = modifier
            .clip(shape)
            .background(resolvedTint)
            .border(1.dp, resolvedBorder, shape),
        content = content
    )
}

/**
 * Selectable pill. Selected state is tinted by the current atmosphere, never by a
 * fixed brand orange.
 */
@Composable
fun AetherChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null
) {
    val atmosphere = LocalAtmosphere.current
    val colors = LocalAetherColors.current
    val duration = aetherDuration(AetherMotion.ControlMillis)
    val spec = tween<Color>(duration, easing = AetherMotion.ControlEasing)

    val background by animateColorAsState(
        if (selected) atmosphere.accent.copy(alpha = if (colors.isDark) 0.22f else 0.16f) else colors.surfaceElevated,
        spec,
        label = "chip_background"
    )
    val borderColor by animateColorAsState(
        if (selected) atmosphere.accent.copy(alpha = 0.85f) else colors.border,
        spec,
        label = "chip_border"
    )
    val labelColor by animateColorAsState(
        if (selected && colors.isDark) Color.White else colors.textPrimary,
        spec,
        label = "chip_label"
    )

    Row(
        modifier = modifier
            .clip(AetherEmber.Shapes.Pill)
            .background(background)
            .border(if (selected) 1.dp else 0.75.dp, borderColor, AetherEmber.Shapes.Pill)
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 34.dp)
            .padding(horizontal = AetherEmber.Spacing.Space12, vertical = AetherEmber.Spacing.Space8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AetherEmber.Spacing.Space4)
    ) {
        Text(
            text = label,
            style = AetherType.Label,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = labelColor
        )
        if (!badge.isNullOrBlank()) {
            Text(
                text = badge,
                style = AetherType.Metadata,
                fontWeight = FontWeight.Bold,
                color = if (selected && colors.isDark) Color.White else colors.textTertiary
            )
        }
    }
}

/**
 * Circular icon-only control. [contentDescription] is required — icon-only affordances
 * must stay understandable to a screen reader.
 */
@Composable
fun AetherIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = AetherMinTouchTarget,
    iconSize: Dp = 19.dp,
    tint: Color? = null,
    background: Color? = null,
    borderColor: Color? = null,
    enabled: Boolean = true
) {
    val atmosphere = LocalAtmosphere.current
    val colors = LocalAetherColors.current
    val reducedMotion = LocalReducedMotion.current
    val resolvedTint = tint ?: colors.textPrimary
    val resolvedBackground = background ?: colors.surfaceGlass
    val resolvedBorder = borderColor ?: colors.border
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && enabled && !reducedMotion) 0.97f else 1f,
        animationSpec = if (reducedMotion) snap() else AetherMotion.Stiff,
        label = "aether_icon_press_scale"
    )
    val pressedBackground by animateColorAsState(
        targetValue = if (pressed && enabled) {
            lerp(
                resolvedBackground,
                atmosphere.accent.copy(alpha = resolvedBackground.alpha),
                0.16f
            )
        } else {
            resolvedBackground
        },
        animationSpec = tween(aetherDuration(AetherMotion.MicroMillis)),
        label = "aether_icon_press_tint"
    )
    Box(
        modifier = modifier
            .size(size.coerceAtLeast(AetherMinTouchTarget))
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(AetherEmber.Shapes.Pill)
            .background(pressedBackground)
            .border(1.dp, resolvedBorder, AetherEmber.Shapes.Pill)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) resolvedTint else resolvedTint.copy(alpha = 0.4f),
            modifier = Modifier.size(iconSize)
        )
    }
}

/** Canonical back affordance for normal Aether sub-screens. */
@Composable
fun AetherBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "Back"
) = AetherIconButton(
    icon = Icons.AutoMirrored.Filled.ArrowBack,
    contentDescription = contentDescription,
    onClick = onClick,
    modifier = modifier
)

/**
 * Layer-3 floating navigation surface. Callers align this at TopCenter; this primitive
 * owns status/cutout clearance, canonical outer margins, material, geometry and shadow.
 * [scrollFraction] is intentionally restrained: 0 is spacious, 1 is only 8dp shorter.
 */
@Composable
fun AetherFloatingHeader(
    modifier: Modifier = Modifier,
    scrollFraction: Float = 0f,
    surfaceModifier: Modifier = Modifier,
    frostState: AetherFrostState? = null,
    horizontalMargin: Dp = AetherFloatingHeaderDefaults.HorizontalMargin,
    expandedHeight: Dp = AetherFloatingHeaderDefaults.ExpandedHeight,
    compactHeight: Dp = AetherFloatingHeaderDefaults.CompactHeight,
    content: @Composable RowScope.() -> Unit
) {
    val fraction = scrollFraction.coerceIn(0f, 1f)
    val height = expandedHeight - (expandedHeight - compactHeight) * fraction
    val shadow = 2.dp + 4.dp * fraction

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
            .padding(
                start = horizontalMargin,
                top = AetherFloatingHeaderDefaults.TopGap,
                end = horizontalMargin
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        AetherFrostedGlass(
            frostState = frostState,
            modifier = surfaceModifier
                .fillMaxWidth()
                .heightIn(min = height),
            shape = AetherFloatingHeaderDefaults.Shape,
            elevation = shadow,
            emphasis = fraction * 0.35f
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = height)
                    .padding(horizontal = AetherEmber.Spacing.Space12),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AetherEmber.Spacing.Space8),
                content = content
            )
        }
    }
}

/** Standard title/subtitle content model for [AetherFloatingHeader]. */
@Composable
fun AetherFloatingHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    scrollFraction: Float = 0f,
    surfaceModifier: Modifier = Modifier,
    frostState: AetherFrostState? = null,
    horizontalMargin: Dp = AetherFloatingHeaderDefaults.HorizontalMargin,
    expandedHeight: Dp = AetherFloatingHeaderDefaults.ExpandedHeight,
    compactHeight: Dp = AetherFloatingHeaderDefaults.CompactHeight,
    navigation: (@Composable RowScope.() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    val colors = LocalAetherColors.current
    AetherFloatingHeader(
        modifier = modifier,
        scrollFraction = scrollFraction,
        surfaceModifier = surfaceModifier,
        frostState = frostState,
        horizontalMargin = horizontalMargin,
        expandedHeight = expandedHeight,
        compactHeight = compactHeight
    ) {
        navigation?.invoke(this)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = AetherType.ScreenTitle,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = AetherType.Caption,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        actions?.invoke(this)
    }
}

/** Context selector for typography placed directly on atmosphere versus a material. */
enum class AetherHeaderTone { Surface, Atmosphere }

/** Small uppercase section label used to structure long screens. */
@Composable
fun AetherSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    tone: AetherHeaderTone = AetherHeaderTone.Surface
) {
    val colors = LocalAetherColors.current
    Text(
        text = text.uppercase(),
        style = AetherType.SectionTitle,
        color = if (tone == AetherHeaderTone.Atmosphere) colors.atmosphereTextSecondary else colors.textTertiary,
        modifier = modifier.padding(
            start = AetherEmber.Spacing.Space24,
            bottom = AetherEmber.Spacing.Space8
        )
    )
}

/**
 * The designed truthful state for "there is genuinely nothing here".
 * Empty areas are never filled with invented content.
 */
@Composable
fun AetherEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    icon: ImageVector? = null,
    action: (@Composable ColumnScope.() -> Unit)? = null
) {
    val colors = LocalAetherColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = AetherEmber.Spacing.Space32,
                vertical = AetherEmber.Spacing.Space32
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space12))
        }
        Text(
            text = title,
            style = AetherType.Body,
            fontWeight = FontWeight.SemiBold,
            color = colors.textSecondary,
            textAlign = TextAlign.Center
        )
        if (!detail.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space4))
            Text(
                text = detail,
                style = AetherType.Caption,
                color = colors.textTertiary,
                textAlign = TextAlign.Center
            )
        }
        if (action != null) {
            Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space16))
            action()
        }
    }
}
