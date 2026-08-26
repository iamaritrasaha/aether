package com.foresightlabs.aether

import com.foresightlabs.aether.ui.design.HomeLayout
import com.foresightlabs.aether.ui.design.PresenceDensity
import com.foresightlabs.aether.ui.design.PresenceStripTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutTest {

    // xhdpi: 2 px per dp, matching the rendered screenshot configurations.
    private val scale = 2f
    private fun dp(value: Float) = value * scale

    private val comfortable = dp(120f)
    private val compact = dp(100f)
    private val preferred = dp(232f + 52f)
    private val floor = dp(156f + 52f)

    private fun resolve(
        containerDp: Float,
        heroCoreDp: Float,
        topInsetDp: Float = 0f,
        hasPresence: Boolean = true
    ) = HomeLayout.resolve(
        containerHeightPx = dp(containerDp),
        topInsetPx = dp(topInsetDp),
        heroCoreHeightPx = dp(heroCoreDp),
        hasPresence = hasPresence,
        comfortableAllowancePx = comfortable,
        compactAllowancePx = compact,
        preferredViewportPx = preferred,
        floorViewportPx = floor
    )

    @Test
    fun aTallScreenGetsTheComfortableStrip() {
        val fit = resolve(containerDp = 891f, heroCoreDp = 230f)
        assertEquals(PresenceDensity.COMFORTABLE, fit.presence)
        assertEquals(preferred, fit.minViewportPx, 0.01f)
    }

    @Test
    fun aSmallScreenKeepsPresenceByCompactingRatherThanHiding() {
        // The 320x568dp case that previously clipped the strip in half.
        val fit = resolve(containerDp = 568f, heroCoreDp = 205f)
        assertNotNull("presence must not vanish on a narrow display", fit.presence)
        assertEquals(PresenceDensity.COMPACT, fit.presence)
    }

    @Test
    fun theStripSurvivesAtEveryRealisticSmallScreenHeroSize() {
        // Whatever the greeting and daily line wrap to, presence stays visible.
        for (heroCore in 180..260 step 5) {
            val fit = resolve(containerDp = 568f, heroCoreDp = heroCore.toFloat())
            assertNotNull("no presence at heroCore=$heroCore", fit.presence)
        }
    }

    @Test
    fun listRowsAreTradedOnlyDownToTheFloor() {
        val fit = resolve(containerDp = 568f, heroCoreDp = 240f)
        assertNotNull(fit.presence)
        assertTrue(
            "viewport dropped below the floor",
            fit.minViewportPx >= floor - 0.01f
        )
        assertTrue(
            "viewport should never exceed the preferred budget",
            fit.minViewportPx <= preferred + 0.01f
        )
    }

    @Test
    fun theResolvedBudgetAlwaysLeavesRoomForTheChosenStrip() {
        for (container in 480..900 step 10) {
            for (heroCore in 160..280 step 20) {
                val fit = resolve(container.toFloat(), heroCore.toFloat())
                val density = fit.presence ?: continue
                val allowance = when (density) {
                    PresenceDensity.COMFORTABLE -> comfortable
                    PresenceDensity.COMPACT -> compact
                }
                val remaining = dp(container.toFloat()) - (dp(heroCore.toFloat()) + allowance)
                assertTrue(
                    "strip would be clipped at container=$container hero=$heroCore",
                    remaining >= fit.minViewportPx - 0.01f
                )
            }
        }
    }

    @Test
    fun theStripHidesOnlyWhenThereIsGenuinelyNoRoom() {
        // A hero taller than the whole usable area leaves nothing to trade.
        val fit = resolve(containerDp = 400f, heroCoreDp = 300f)
        assertNull(fit.presence)
    }

    @Test
    fun noPresenceDataMeansNoStripAndTheFullViewport() {
        val fit = resolve(containerDp = 891f, heroCoreDp = 230f, hasPresence = false)
        assertNull(fit.presence)
        assertEquals(preferred, fit.minViewportPx, 0.01f)
    }

    @Test
    fun anUnmeasuredHeroDoesNotCommitToADensity() {
        val fit = HomeLayout.resolve(
            containerHeightPx = dp(891f),
            topInsetPx = 0f,
            heroCoreHeightPx = 0f,
            hasPresence = true,
            comfortableAllowancePx = comfortable,
            compactAllowancePx = compact,
            preferredViewportPx = preferred,
            floorViewportPx = floor
        )
        assertNull(fit.presence)
    }

    @Test
    fun topInsetIsCountedAgainstTheBudget() {
        val withoutInset = resolve(containerDp = 700f, heroCoreDp = 230f, topInsetDp = 0f)
        val withInset = resolve(containerDp = 700f, heroCoreDp = 230f, topInsetDp = 120f)
        assertTrue(
            "a larger inset must not yield a roomier result",
            withInset.minViewportPx <= withoutInset.minViewportPx
        )
    }

    @Test
    fun compactTokensAreSmallerButStayOnTheFourDpGrid() {
        PresenceDensity.entries.forEach { density ->
            listOf(
                PresenceStripTokens.avatarSize(density),
                PresenceStripTokens.itemSpacing(density),
                PresenceStripTokens.labelWidth(density),
                PresenceStripTokens.verticalAllowance(density)
            ).forEach { token ->
                assertEquals(
                    "$density token ${token.value} is off the 4dp grid",
                    0f,
                    token.value % 4f,
                    0.01f
                )
            }
        }
        assertTrue(
            PresenceStripTokens.avatarSize(PresenceDensity.COMPACT) <
                PresenceStripTokens.avatarSize(PresenceDensity.COMFORTABLE)
        )
        assertTrue(
            PresenceStripTokens.verticalAllowance(PresenceDensity.COMPACT) <
                PresenceStripTokens.verticalAllowance(PresenceDensity.COMFORTABLE)
        )
    }
}
