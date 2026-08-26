package com.foresightlabs.aether.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * Canonical Aether motion tokens.
 *
 * Motion communicates physical state, never decoration:
 * - the atmosphere is a slow environmental shift
 * - surfaces have mass and settle with a spring
 * - controls answer immediately
 */
object AetherMotion {

    /** Full atmospheric palette transition. Deliberately slow and ambient. */
    const val AtmosphereMillis: Int = 2500

    /** A surface changing size, position or shape without being dragged. */
    const val SurfaceMillis: Int = 320

    /** Direct control feedback: chips, toggles, icon buttons. */
    const val ControlMillis: Int = 180

    /** Micro feedback: tint, ripple-adjacent colour shifts. */
    const val MicroMillis: Int = 120

    val AtmosphereEasing: Easing = FastOutSlowInEasing
    val ControlEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /**
     * A dragged surface settling onto an anchor. Slightly under-damped so the sheet
     * reads as a physical object with mass, without visible bounce.
     */
    val SheetSettle: SpringSpec<Float> = spring(
        dampingRatio = 0.9f,
        stiffness = 420f
    )

    val Physical: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )

    val Stiff: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh
    )
}

/**
 * True when the user has asked the system to reduce or remove animation.
 * Consumers must still reach the same end state, only instantly.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
fun rememberSystemReducedMotion(): Boolean {
    if (LocalInspectionMode.current) return false
    val context = LocalContext.current
    return remember(context) { systemReducedMotion(context) }
}

fun systemReducedMotion(context: Context): Boolean {
    return try {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        scale == 0f
    } catch (_: Exception) {
        false
    }
}

/** Collapses a duration to zero when the user has reduced motion enabled. */
@Composable
fun aetherDuration(millis: Int): Int = if (LocalReducedMotion.current) 0 else millis
