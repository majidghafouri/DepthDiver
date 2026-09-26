package com.depthdiver.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiScaleTest {

    private fun assertClose(expected: Float, actual: Float) =
        assertTrue("expected $expected but was $actual", kotlin.math.abs(expected - actual) < 1e-3f)

    @Test
    fun referencePhoneLayoutIsUnscaled() {
        val scale = UiScale.forScreen(1280f, 720f)
        assertClose(1f, scale.factor)
    }

    @Test
    fun smallPhonesNeverShrinkBelowOne() {
        val scale = UiScale.forScreen(960f, 540f)
        assertEquals(1f, scale.factor, 1e-4f)
    }

    @Test
    fun tabletsScaleUp() {
        // 10" tablet in landscape: ~1920x1200
        val scale = UiScale.forScreen(1920f, 1200f)
        assertTrue("tablet should scale up", scale.factor > 1.4f)
        assertTrue(scale.factor <= UiScale.MAX_FACTOR)
    }

    @Test
    fun scalingIsCappedSoUiDoesNotBecomeClumsy() {
        val huge = UiScale.forScreen(3840f, 2400f)
        assertEquals(UiScale.MAX_FACTOR, huge.factor, 1e-4f)
    }

    @Test
    fun scaleUsesTheShorterEdgeSoOrientationDoesNotMatter() {
        val landscape = UiScale.forScreen(1920f, 1200f)
        val portrait = UiScale.forScreen(1200f, 1920f)
        assertEquals(landscape.factor, portrait.factor, 1e-4f)
    }

    @Test
    fun touchFloorIsNeverBelowTheAccessibilityMinimum() {
        val tiny = UiScale.forScreen(640f, 360f)
        assertTrue(tiny.minTouchPx >= UiScale.MIN_TOUCH_PX)
        val big = UiScale.forScreen(2560f, 1600f)
        assertTrue(big.minTouchPx >= UiScale.MIN_TOUCH_PX)
    }

    @Test
    fun touchFloorGrowsWithTheUi() {
        val phone = UiScale.forScreen(1280f, 720f)
        val tablet = UiScale.forScreen(1920f, 1200f)
        assertTrue(tablet.minTouchPx > phone.minTouchPx)
    }

    @Test
    fun degenerateSizesFallBackToUnscaled() {
        for ((w, h) in listOf(0f to 0f, -10f to 100f, 100f to -10f)) {
            val scale = UiScale.forScreen(w, h)
            assertEquals(1f, scale.factor, 1e-4f)
        }
    }

    @Test
    fun nonFiniteSizesFallBackToUnscaled() {
        for ((w, h) in listOf(Float.NaN to 720f, 1280f to Float.NaN, Float.POSITIVE_INFINITY to 1f)) {
            val scale = UiScale.forScreen(w, h)
            assertEquals(1f, scale.factor, 1e-4f)
        }
    }

    @Test
    fun pxScalesLinearConstants() {
        val scale = UiScale(2f, 96f)
        assertClose(200f, scale.px(100f))
        assertClose(140f, scale.gap(70f))
    }

    @Test
    fun touchEnforcesTheFloorButNotForLargerTargets() {
        val scale = UiScale(1f, 48f)
        assertClose(48f, scale.touch(10f))
        assertClose(60f, scale.touch(60f))
    }

    @Test
    fun gapHasNoMinimumSoLayoutCanStayCompact() {
        val scale = UiScale(1f, 48f)
        assertClose(8f, scale.gap(8f))
    }
}
