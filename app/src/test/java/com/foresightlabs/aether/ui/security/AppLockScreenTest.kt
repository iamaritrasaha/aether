package com.foresightlabs.aether.ui.security

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.domain.security.AppLockUiState
import com.foresightlabs.aether.domain.security.LockReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class AppLockScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun setLockScreenContent() {
        composeRule.setContent {
            AppLockScreen(
                state = AppLockUiState.Locked(LockReason.FRESH_PROCESS),
                pinLength = 6,
                pinEntered = 0,
                biometricAvailable = false,
                onDigit = {}, onDelete = {}, onBiometricTap = {}
            )
        }
    }

    @Test
    fun pinDotsSemanticsNeverExposeTheActualDigits() {
        composeRule.setContent {
            AppLockScreen(
                state = AppLockUiState.Locked(LockReason.FRESH_PROCESS),
                pinLength = 6,
                pinEntered = 3,
                biometricAvailable = false,
                onDigit = {}, onDelete = {}, onBiometricTap = {}
            )
        }
        // Only the progress count is legitimate a11y info -- the exact match
        // here is itself the proof no digit value snuck into the description.
        composeRule.onNodeWithTag("app_lock_pin_dots").assertContentDescriptionEquals("3 of 6 digits entered")
    }

    @Test
    fun keypadDigitKeysHaveAccessibilitySafeTouchTargets() {
        composeRule.setContent {
            AetherNumericKeypad(onDigit = {}, onDelete = {})
        }
        // 48dp is Android's own minimum touch target -- not an arbitrary
        // choice. The visible circle *is* the touch target here (no separate
        // minimumInteractiveComponentSize padding), so this doubles as proof
        // the visual size itself never shrinks below what's tappable.
        composeRule.onNodeWithTag("app_lock_key_5")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun tappingDigitKeyInvokesCallbackWithThatDigit() {
        var pressed: Char? = null
        composeRule.setContent {
            AetherNumericKeypad(onDigit = { pressed = it }, onDelete = {})
        }
        composeRule.onNodeWithTag("app_lock_key_7").performClick()
        assertEquals('7', pressed)
    }

    @Test
    fun deleteKeyInvokesOnDeleteAndRespectsEnabledState() {
        var deletes = 0
        composeRule.setContent {
            AetherNumericKeypad(onDigit = {}, onDelete = { deletes++ }, deleteEnabled = true)
        }
        composeRule.onNodeWithTag("app_lock_key_delete").performClick()
        assertEquals(1, deletes)
    }

    @Test
    fun disabledDeleteKeyDoesNotInvokeCallback() {
        var deletes = 0
        composeRule.setContent {
            AetherNumericKeypad(onDigit = {}, onDelete = { deletes++ }, deleteEnabled = false)
        }
        composeRule.onNodeWithTag("app_lock_key_delete").performClick()
        assertEquals(0, deletes)
    }

    @Test
    fun biometricButtonHiddenWhenUnavailable() {
        composeRule.setContent {
            AppLockScreen(
                state = AppLockUiState.Locked(LockReason.FRESH_PROCESS),
                pinLength = 6,
                pinEntered = 0,
                biometricAvailable = false,
                onDigit = {}, onDelete = {}, onBiometricTap = {}
            )
        }
        composeRule.onNodeWithTag("app_lock_biometric_button").assertDoesNotExist()
    }

    @Test
    fun biometricButtonShownWhenAvailable() {
        composeRule.setContent {
            AppLockScreen(
                state = AppLockUiState.Locked(LockReason.FRESH_PROCESS),
                pinLength = 6,
                pinEntered = 0,
                biometricAvailable = true,
                onDigit = {}, onDelete = {}, onBiometricTap = {}
            )
        }
        composeRule.onNodeWithTag("app_lock_biometric_button").assertExists()
    }

    @Test
    fun temporarilyBlockedShowsRetryCountdownNotAlarmingLanguage() {
        composeRule.setContent {
            AppLockScreen(
                state = AppLockUiState.TemporarilyBlocked(retryAtElapsedRealtimeMillis = 0L),
                pinLength = 6,
                pinEntered = 0,
                biometricAvailable = false,
                secondsRemaining = 20L,
                onDigit = {}, onDelete = {}, onBiometricTap = {}
            )
        }
        composeRule.onNodeWithTag("app_lock_status_line").assertTextEquals("Try again in 20s")
    }

    @Test
    fun keyDiameterConstantsMatchTheApprovedVisualScale() {
        // A direct lock on the visual target from the regression pass: 68dp
        // is the default starting size, and nothing -- not a screen, not a
        // future tweak -- should push the widest legitimate variant past
        // 72dp. This is deliberately a plain constant check, no composition
        // needed, so it fails immediately and obviously if either drifts.
        assertEquals(68.dp, AetherKeypadDefaultKeyDiameter)
        assertTrue(
            "roomy key diameter was $AetherKeypadRoomyKeyDiameter, expected <= 72dp",
            AetherKeypadRoomyKeyDiameter <= 72.dp
        )
    }

    @Test
    fun eachVisibleDigitKeyDiameterNeverExceeds72Dp() {
        composeRule.setContent {
            AetherNumericKeypad(onDigit = {}, onDelete = {}, keyDiameter = AetherKeypadRoomyKeyDiameter)
        }
        val bounds = composeRule.onNodeWithTag("app_lock_key_5").getUnclippedBoundsInRoot()
        val width = bounds.right - bounds.left
        val height = bounds.bottom - bounds.top
        assertTrue("key width was $width, expected <= 72dp", width <= 72.dp)
        assertTrue("key height was $height, expected <= 72dp", height <= 72.dp)
    }

    @Test
    fun horizontalGapBetweenAdjacentKeysMatchesTheSpecGap() {
        composeRule.setContent {
            AetherNumericKeypad(onDigit = {}, onDelete = {})
        }
        val key1 = composeRule.onNodeWithTag("app_lock_key_1").getUnclippedBoundsInRoot()
        val key2 = composeRule.onNodeWithTag("app_lock_key_2").getUnclippedBoundsInRoot()
        val gap = key2.left - key1.right
        assertTrue(
            "horizontal gap was $gap, expected within 2dp of $AetherKeypadHorizontalGap",
            gap in (AetherKeypadHorizontalGap - 2.dp)..(AetherKeypadHorizontalGap + 2.dp)
        )
    }

    @Test
    fun verticalGapBetweenRowsMatchesTheSpecGap() {
        composeRule.setContent {
            AetherNumericKeypad(onDigit = {}, onDelete = {})
        }
        val row1Key = composeRule.onNodeWithTag("app_lock_key_2").getUnclippedBoundsInRoot()
        val row2Key = composeRule.onNodeWithTag("app_lock_key_5").getUnclippedBoundsInRoot()
        val gap = row2Key.top - row1Key.bottom
        assertTrue(
            "vertical gap was $gap, expected within 2dp of $AetherKeypadVerticalGap",
            gap in (AetherKeypadVerticalGap - 2.dp)..(AetherKeypadVerticalGap + 2.dp)
        )
    }

    @Test
    fun backspaceSitsOnTheSameGridColumnAsTheTopRightDigit() {
        composeRule.setContent {
            AetherNumericKeypad(onDigit = {}, onDelete = {})
        }
        // Backspace must fall on the phone-dialer grid under '3'/'6'/'9', not
        // at an offset derived independently of the digit columns.
        val topRightDigit = composeRule.onNodeWithTag("app_lock_key_3").getUnclippedBoundsInRoot()
        val backspace = composeRule.onNodeWithTag("app_lock_key_delete").getUnclippedBoundsInRoot()
        val digitCenterX = (topRightDigit.left + topRightDigit.right) / 2
        val backspaceCenterX = (backspace.left + backspace.right) / 2
        val drift = if (digitCenterX > backspaceCenterX) digitCenterX - backspaceCenterX else backspaceCenterX - digitCenterX
        assertTrue("backspace column drifted by $drift from the digit column", drift <= 1.dp)
    }

    @Test
    fun keypadStaysBoundedOnATabletWideContainer() {
        composeRule.setContent {
            Box(modifier = Modifier.width(1200.dp)) {
                AetherNumericKeypad(onDigit = {}, onDelete = {})
            }
        }
        // AetherNumericKeypad no longer reads screen/container width itself
        // (see AppLockScreen/AppLockSetupScreen for where keyDiameter is
        // actually derived) -- this just confirms the component's own fixed
        // sizing can never stretch a keypad rendered with the default
        // diameter, no matter how oversized a host it's dropped into. 260dp
        // is the widest the keypad ever gets at the roomy 72dp key size; the
        // default-diameter keypad exercised here (248dp) clears it with
        // margin to spare, far tighter than the old 340dp ceiling that let
        // the reported oversized-keypad regression through unnoticed.
        val bounds = composeRule.onNodeWithTag("app_lock_keypad").getUnclippedBoundsInRoot()
        val measuredWidth: Dp = bounds.right - bounds.left
        assertTrue("keypad width was $measuredWidth, expected <= 260dp", measuredWidth <= 260.dp)
    }

    @Test
    @Config(qualifiers = "w393dp-h851dp-xhdpi")
    fun narrowWindowUsesTheDefaultKeyDiameterNotTheRoomyOne() {
        // A `Modifier.width()` wrapper can't exceed the real window -- it
        // only clamps down to it -- so testing an actual window size means
        // configuring the emulated display itself, the same technique
        // ConversationCompositionTest/HomeCompositionTest already use.
        setLockScreenContent()
        val bounds = composeRule.onNodeWithTag("app_lock_key_5").getUnclippedBoundsInRoot()
        val width = bounds.right - bounds.left
        assertTrue(
            "key width was $width on a 393dp display, expected the 68dp default",
            width in (AetherKeypadDefaultKeyDiameter - 1.dp)..(AetherKeypadDefaultKeyDiameter + 1.dp)
        )
    }

    @Test
    @Config(qualifiers = "w840dp-h1280dp-xhdpi")
    fun roomyWideWindowCapsAtTheRoomyKeyDiameterInsteadOfStretching() {
        setLockScreenContent()
        val bounds = composeRule.onNodeWithTag("app_lock_key_5").getUnclippedBoundsInRoot()
        val width = bounds.right - bounds.left
        // The point of the fix: a genuinely roomy display (well past the
        // 720dp threshold) steps up to the small, capped 72dp variant -- it
        // must not balloon to fill anything close to the 840dp display.
        assertTrue(
            "key width was $width on an 840dp display, expected the 72dp roomy cap",
            width in (AetherKeypadRoomyKeyDiameter - 1.dp)..(AetherKeypadRoomyKeyDiameter + 1.dp)
        )
        val keypadBounds = composeRule.onNodeWithTag("app_lock_keypad").getUnclippedBoundsInRoot()
        val keypadWidth = keypadBounds.right - keypadBounds.left
        assertTrue("keypad width was $keypadWidth on an 840dp display, expected <= 260dp", keypadWidth <= 260.dp)
    }

    @Test
    @Config(qualifiers = "w320dp-h568dp-xhdpi")
    fun narrowRealisticPhoneWindowDoesNotClipAnyKey() {
        // 320dp is a legitimately small phone width (not a degenerate test
        // size) -- after the screen's 24dp horizontal padding on each side,
        // 272dp of content width comfortably fits the 248dp default keypad.
        setLockScreenContent()
        val digits = listOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
        digits.forEach { digit ->
            composeRule.onNodeWithTag("app_lock_key_$digit").assertExists()
        }
        composeRule.onNodeWithTag("app_lock_key_delete").assertExists()
        val keypadBounds = composeRule.onNodeWithTag("app_lock_keypad").getUnclippedBoundsInRoot()
        val keypadWidth = keypadBounds.right - keypadBounds.left
        assertTrue(
            "keypad width was $keypadWidth, expected to fit inside a 320dp display's content area",
            keypadWidth <= 320.dp - 48.dp
        )
    }
}
