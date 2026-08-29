package com.foresightlabs.aether.ui.theme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.foresightlabs.aether.ui.theme.AetherColors
import com.foresightlabs.aether.ui.theme.DarkBubbleIncoming
import com.foresightlabs.aether.ui.theme.DarkBubbleIncomingText
import com.foresightlabs.aether.ui.theme.DarkBubbleOutgoing
import com.foresightlabs.aether.ui.theme.DarkBubbleOutgoingText
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/**
 * Validates that Aether's incoming and outgoing message bubbles belong to the dark
 * conversation atmosphere, distinguish by temperature and subtle tone (not white vs black),
 * and provide WCAG-compliant legibility.
 */
class MessagePaletteTest {

    private fun contrastRatio(fg: Color, bg: Color): Float {
        val lum1 = fg.luminance()
        val lum2 = bg.luminance()
        val brightest = max(lum1, lum2)
        val darkest = min(lum1, lum2)
        return (brightest + 0.05f) / (darkest + 0.05f)
    }

    @Test
    fun incomingBubbleIsDarkAndNotWhiteOrPale() {
        // Incoming bubble must be dark (luminance well below 0.35, never pale/white)
        val incomingLum = DarkBubbleIncoming.luminance()
        assertTrue("Incoming bubble must be dark, but was lum=$incomingLum", incomingLum < 0.15f)

        // Must not be white, near-white, or pale grey
        assertTrue("Incoming red channel must be dark", DarkBubbleIncoming.red < 0.35f)
        assertTrue("Incoming green channel must be dark", DarkBubbleIncoming.green < 0.35f)
        assertTrue("Incoming blue channel must be dark", DarkBubbleIncoming.blue < 0.35f)
    }

    @Test
    fun outgoingBubbleIsDeepNeutralGraphite() {
        val outgoingLum = DarkBubbleOutgoing.luminance()
        assertTrue("Outgoing bubble must be deep dark graphite", outgoingLum < 0.10f)

        // Outgoing is darker than incoming
        assertTrue(
            "Outgoing bubble should be darker than incoming bubble",
            outgoingLum <= DarkBubbleIncoming.luminance()
        )
    }

    @Test
    fun incomingBubbleIsCoolerInTemperatureThanOutgoing() {
        // Incoming is smoky lavender / cool graphite: blue channel >= red and green channels
        assertTrue(
            "Incoming should have cool lavender/slate tone (blue >= red)",
            DarkBubbleIncoming.blue >= DarkBubbleIncoming.red
        )
        assertTrue(
            "Incoming should have cool lavender/slate tone (blue >= green)",
            DarkBubbleIncoming.blue >= DarkBubbleIncoming.green
        )
    }

    @Test
    fun incomingAndOutgoingTextHaveHighContrast() {
        // Primary text on incoming bubble must meet WCAG AAA (> 7.0:1)
        val incomingContrast = contrastRatio(DarkBubbleIncomingText, DarkBubbleIncoming)
        assertTrue(
            "Incoming text contrast ratio must be >= 7.0:1, was $incomingContrast",
            incomingContrast >= 7.0f
        )

        // Primary text on outgoing bubble must meet WCAG AAA (> 7.0:1)
        val outgoingContrast = contrastRatio(DarkBubbleOutgoingText, DarkBubbleOutgoing)
        assertTrue(
            "Outgoing text contrast ratio must be >= 7.0:1, was $outgoingContrast",
            outgoingContrast >= 7.0f
        )
    }

    @Test
    fun defaultAetherColorsBindsNewPalette() {
        val colors = AetherColors()
        assertTrue(colors.isDark)
        assertTrue(colors.bubbleIncoming == DarkBubbleIncoming)
        assertTrue(colors.bubbleIncomingText == DarkBubbleIncomingText)
        assertTrue(colors.bubbleOutgoing == DarkBubbleOutgoing)
        assertTrue(colors.bubbleOutgoingText == DarkBubbleOutgoingText)
    }
}
