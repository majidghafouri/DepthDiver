package com.depthdiver.hud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HudStateTest {

    private fun state(
        oxygen: Float = 1f,
        maxOxygen: Float = 1f,
        isPlaying: Boolean = true,
        combo: Int = 1,
        comboTimer: Float = 0f,
        maxComboWindow: Float = 5f,
    ) = HudState(
        screenWidth = 1280f,
        screenHeight = 720f,
        lineHeight = 20f,
        depth = 120f,
        score = 900,
        bestDepth = 300f,
        bestScore = 4200,
        oxygen = oxygen,
        maxOxygen = maxOxygen,
        combo = combo,
        comboTimer = comboTimer,
        maxComboWindow = maxComboWindow,
        elapsed = 0f,
        isPlaying = isPlaying,
    )

    @Test
    fun oxygenRatioClampsToUnitRange() {
        assertEquals(0.5f, state(oxygen = 0.5f, maxOxygen = 1f).oxygenRatio, 1e-4f)
        assertEquals(1f, state(oxygen = 5f, maxOxygen = 1f).oxygenRatio, 1e-4f)
        assertEquals(0f, state(oxygen = -1f, maxOxygen = 1f).oxygenRatio, 1e-4f)
    }

    @Test
    fun zeroMaxOxygenDoesNotDivideByZero() {
        assertEquals(1f, state(maxOxygen = 0f).oxygenRatio, 1e-4f)
    }

    @Test
    fun lowOxygenOnlyTriggersWhilePlaying() {
        assertTrue(state(oxygen = 0.2f).isLowOxygen)
        assertFalse(state(oxygen = 0.2f, isPlaying = false).isLowOxygen)
    }

    @Test
    fun lowOxygenTriggersAtTheQuarterThreshold() {
        assertFalse(state(oxygen = 0.3f).isLowOxygen)
        assertTrue(state(oxygen = 0.25f).isLowOxygen)
    }

    @Test
    fun dangerRisesAsAirRunsOut() {
        assertEquals(0f, state(oxygen = 0.25f).lowOxygenDanger, 1e-4f)
        assertEquals(0.5f, state(oxygen = 0.125f).lowOxygenDanger, 1e-3f)
        assertEquals(1f, state(oxygen = 0f).lowOxygenDanger, 1e-4f)
    }

    @Test
    fun dangerIsZeroWhenThereIsNoOxygenBudget() {
        assertEquals(0f, state(maxOxygen = 0f).lowOxygenDanger, 1e-4f)
    }

    @Test
    fun comboOnlyShowsAboveOneWhilePlaying() {
        assertFalse(state(combo = 1).showCombo)
        assertTrue(state(combo = 3).showCombo)
        assertFalse(state(combo = 3, isPlaying = false).showCombo)
    }

    @Test
    fun comboFractionClamps() {
        assertEquals(0.5f, state(combo = 2, comboTimer = 2.5f, maxComboWindow = 5f).comboFraction, 1e-4f)
        assertEquals(1f, state(combo = 2, comboTimer = 99f).comboFraction, 1e-4f)
        assertEquals(0f, state(combo = 2, maxComboWindow = 0f).comboFraction, 1e-4f)
    }
}

class HudLayoutRulesTest {

    private val state = HudState(
        screenWidth = 1280f,
        screenHeight = 720f,
        lineHeight = 20f,
        depth = 100f,
        score = 500,
        bestDepth = 200f,
        bestScore = 900,
        oxygen = 1f,
        maxOxygen = 1f,
        combo = 1,
        comboTimer = 0f,
        maxComboWindow = 5f,
        elapsed = 0f,
        isPlaying = true,
    )

    @Test
    fun rowsStepDownByLineHeightPlusSpacing() {
        val top = HudLayoutRules.rowBaseline(720f, 20f, row = 0)
        val second = HudLayoutRules.rowBaseline(720f, 20f, row = 1)
        assertEquals(720f - 20f - 6f, top, 1e-3f)
        assertEquals(top - 20f - 8f, second, 1e-3f)
    }

    @Test
    fun pauseSitsOnTheThirdRow() {
        val expected = HudLayoutRules.rowBaseline(720f, 20f, row = 2)
        val layout = HudLayoutRules.pauseHitbox(state, pillW = 100f, pillH = 40f)
        assertEquals(expected, layout.pauseCy, 1e-3f)
    }

    @Test
    fun playingPauseUsesTheMeasuredPill() {
        val layout = HudLayoutRules.pauseHitbox(state, pillW = 100f, pillH = 40f)
        assertEquals(100f, layout.pauseW, 1e-3f)
        assertEquals(40f, layout.pauseH, 1e-3f)
        assertEquals(1280f - 12f - 50f, layout.pauseCx, 1e-3f)
    }

    @Test
    fun pausedPlaceholderIsInertButStillValid() {
        val paused = state.copy(isPlaying = false)
        val layout = HudLayoutRules.pauseHitbox(paused, pillW = 100f, pillH = 40f)
        assertEquals(1280f * 0.1f, layout.pauseW, 1e-3f)
        assertEquals(720f * 0.05f, layout.pauseH, 1e-3f)
        assertTrue(layout.pauseCx > 0f)
    }

    @Test
    fun pauseStaysOnScreenForWideViewports() {
        val wide = state.copy(screenWidth = 3840f)
        val layout = HudLayoutRules.pauseHitbox(wide, pillW = 300f, pillH = 90f)
        assertTrue("centre should be inside the viewport", layout.pauseCx in 0f..3840f)
        assertTrue("pill should not overflow right", layout.pauseCx + layout.pauseW / 2f <= 3840f)
    }

    @Test
    fun scoreSitsToTheRightOfDepth() {
        val x = HudLayoutRules.scoreLeft(state, depthTextWidth = 120f)
        assertEquals(10f + 120f + 22f, x, 1e-3f)
        assertTrue(x > 10f)
    }

    @Test
    fun hudGapsScaleWithTheFontsTheySeparate() {
        // Fonts grow with the viewport, so a fixed gap makes the depth and
        // score readouts collide on tablets. The gap must scale with them.
        val scaled = state.copy(scale = 2f)
        val unscaledGap = HudLayoutRules.scoreLeft(state, 120f) - (10f + 120f)
        val scaledGap = HudLayoutRules.scoreLeft(scaled, 120f) - (10f * 2f + 120f)
        assertEquals(unscaledGap * 2f, scaledGap, 1e-3f)
        assertTrue("gap must grow on large screens", scaledGap > unscaledGap)
    }

    @Test
    fun rowSpacingScalesSoRowsDoNotCollide() {
        // Line height grows with the font, so a fixed line spacing would let
        // consecutive HUD rows touch on large screens.
        val gapAt = { scale: Float ->
            val top = HudLayoutRules.rowBaseline(720f, 20f, row = 0, scale)
            val second = HudLayoutRules.rowBaseline(720f, 20f, row = 1, scale)
            top - second
        }
        assertEquals(20f + 8f, gapAt(1f), 1e-3f)
        assertEquals(20f + 16f, gapAt(2f), 1e-3f)
        assertTrue("spacing must grow with the UI", gapAt(2f) > gapAt(1f))
    }

    @Test
    fun comboBarScalesToo() {
        assertEquals(90f, HudLayoutRules.comboBarWidth(state), 1e-3f)
        assertEquals(180f, HudLayoutRules.comboBarWidth(state.copy(scale = 2f)), 1e-3f)
    }

    @Test
    fun difficultyLabelIsRightAlignedWithMargin() {
        val right = HudLayoutRules.difficultyRight(state, labelWidth = 90f)
        assertEquals(1280f - 10f - 90f, right, 1e-3f)
    }

    @Test
    fun bossWarningSitsBelowTheTopRow() {
        val top = HudLayoutRules.rowBaseline(720f, 20f, row = 0)
        val boss = HudLayoutRules.bossWarningBaseline(state)
        assertTrue(boss < top)
    }

    @Test
    fun comboBarSitsBelowTheComboText() {
        val comboY = HudLayoutRules.comboBaseline(state)
        val barBottom = HudLayoutRules.comboBarBottom(state)
        assertTrue(barBottom < comboY)
        assertEquals(comboY - 20f * 0.6f - 5f, barBottom, 1e-3f)
    }

    @Test
    fun comboBarUsesTheDocumentedSize() {
        assertEquals(90f, HudLayoutRules.COMBO_BAR_WIDTH, 1e-3f)
        assertEquals(5f, HudLayoutRules.COMBO_BAR_HEIGHT, 1e-3f)
    }
}
