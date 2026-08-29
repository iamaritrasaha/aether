package com.foresightlabs.aether.ui.design
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.ui.design.AetherReveal
import com.foresightlabs.aether.ui.design.aetherReveal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The ghost-hit invariant, held at the modifier rather than at one screen.
 *
 * `SheetGhostHitTest` guards Home specifically. This guards the mechanism, so any
 * layer that adopts [aetherReveal] — including ones added after this was written —
 * inherits the same guarantee: a control the user cannot see cannot be tapped, and
 * a control that has moved takes its touch target with it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi", application = Application::class)
class AetherRevealTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val alpha = mutableFloatStateOf(1f)
    private val shift = mutableFloatStateOf(0f)
    private var clicks = 0

    @Before
    fun setUp() {
        composeRule.setContent {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .aetherReveal(alpha = alpha.floatValue, verticalShiftPx = shift.floatValue)
                    .testTag("revealed")
                    .clickable { clicks++ }
            )
        }
        composeRule.waitForIdle()
    }

    private fun setReveal(newAlpha: Float, newShift: Float = 0f) {
        composeRule.runOnUiThread {
            alpha.floatValue = newAlpha
            shift.floatValue = newShift
        }
        composeRule.waitForIdle()
    }

    private fun bounds() =
        composeRule.onNodeWithTag("revealed").fetchSemanticsNode().boundsInRoot

    private fun tap(point: Offset) {
        composeRule.onRoot().performTouchInput { click(point) }
        composeRule.waitForIdle()
    }

    @Test
    fun aFullyVisibleLayerIsTappable() {
        tap(bounds().center)
        assertEquals(1, clicks)
    }

    @Test
    fun aFadedOutLayerStopsAcceptingTapsAtItsOwnCoordinates() {
        val spot = bounds().center
        setReveal(0f)
        tap(spot)
        assertEquals("A layer at zero opacity accepted a tap", 0, clicks)
    }

    @Test
    fun aLayerBelowTheInteractiveThresholdIsInertEvenThoughItIsStillDrawn() {
        val spot = bounds().center
        setReveal(AetherReveal.InteractiveThreshold - 0.01f)
        tap(spot)
        assertEquals(
            "A control too faint to aim at must not accept the tap aimed past it",
            0,
            clicks
        )
    }

    @Test
    fun aLayerAtTheThresholdIsStillInteractive() {
        val spot = bounds().center
        setReveal(AetherReveal.InteractiveThreshold)
        tap(spot)
        assertEquals(1, clicks)
    }

    @Test
    fun anInertLayerLeavesNoAccessibilityTarget() {
        setReveal(0f)
        val found = runCatching {
            composeRule.onNodeWithTag("revealed").fetchSemanticsNode()
        }.isSuccess
        assertTrue("A faded-out layer stayed in the merged semantics tree", !found)
    }

    // --- displacement moves the touch target, not just the pixels ------------

    @Test
    fun shiftingALayerMovesItsTouchTargetWithIt() {
        val original = bounds().center
        setReveal(1f, newShift = 300f)

        tap(original)
        assertEquals("The old coordinates stayed live after the layer moved", 0, clicks)

        tap(bounds().center)
        assertEquals("The layer did not accept a tap at its new position", 1, clicks)
    }

    @Test
    fun shiftingALayerMovesItsLayoutBoundsNotOnlyItsDrawing() {
        val before = bounds()
        setReveal(1f, newShift = 120f)
        val after = bounds()
        assertEquals(
            "Layout bounds did not follow the displacement",
            120f,
            after.top - before.top,
            1.5f
        )
    }
}
