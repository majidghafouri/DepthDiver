package com.depthdiver.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards the settings sliders and the difficulty caption.
 *
 * The settings screen used to render a decorative "MUTED" pill that could not be
 * touched, and the main menu drew "DIFF" with a fixed +18px offset, which put
 * the caption inside the difficulty segment once the font scaled up. These
 * assertions pin the behaviour that replaced both.
 */
class MenuMetricsTest {

    private data class Device(val name: String, val w: Float, val h: Float)

    private val devices = listOf(
        Device("small phone", 480f, 800f),
        Device("reference phone", 720f, 1280f),
        Device("tall 20:9 phone", 1080f, 2400f),
        Device("user's phone 3200x1440", 3200f, 1440f),
        Device("tablet portrait", 1536f, 2048f),
        Device("tablet landscape", 2048f, 1536f),
        Device("landscape phone", 2400f, 1080f),
    )

    /** Mirrors how DepthDiverGame generates the body font. */
    private fun fontSize(d: Device): Float {
        val factor = minOf(maxOf(minOf(d.w, d.h) / 720f, 1f), UiScale.MAX_FACTOR)
        return (d.h / 30f * factor).toInt().coerceIn(16, 64).toFloat()
    }

    private fun scale(d: Device): Float =
        minOf(maxOf(minOf(d.w, d.h) / 720f, 1f), UiScale.MAX_FACTOR)

    private fun glyphHeight(d: Device): Float = fontSize(d) * 1.15f

    private fun gap(d: Device, base: Float): Float = base * scale(d)

    // ---- volume sliders -------------------------------------------------

    @Test
    fun tappingTheSliderEndsSetsThatExactLevel() {
        val cx = 1000f
        val w = 400f
        assertEquals(0f, MenuMetrics.sliderFraction(cx - w / 2f, cx, w), 0.0001f)
        assertEquals(0.5f, MenuMetrics.sliderFraction(cx, cx, w), 0.0001f)
        assertEquals(1f, MenuMetrics.sliderFraction(cx + w / 2f, cx, w), 0.0001f)
        assertEquals(0.25f, MenuMetrics.sliderFraction(cx - w / 4f, cx, w), 0.0001f)
    }

    @Test
    fun sliderPositionIsClampedToTheTrack() {
        val cx = 500f
        val w = 200f
        // A finger that slides off either end must not produce a negative or
        // >100% volume.
        assertEquals(0f, MenuMetrics.sliderFraction(-900f, cx, w), 0.0001f)
        assertEquals(1f, MenuMetrics.sliderFraction(900f, cx, w), 0.0001f)
    }

    @Test
    fun aZeroWidthTrackCannotDivideByZero() {
        assertEquals(0f, MenuMetrics.sliderFraction(120f, 100f, 0f), 0.0001f)
        assertEquals(0f, MenuMetrics.sliderFraction(120f, 100f, -5f), 0.0001f)
    }

    @Test
    fun everyFractionRoundTripsBackToATouchPosition() {
        // Dragging relies on this mapping being continuous and monotonic.
        val cx = 640f
        val w = 460f
        var previous = -1f
        for (i in 0..100) {
            val f = i / 100f
            val x = cx - w / 2f + w * f
            assertEquals(f, MenuMetrics.sliderFraction(x, cx, w), 0.0001f)
            assertTrue(MenuMetrics.sliderFraction(x, cx, w) > previous)
            previous = MenuMetrics.sliderFraction(x, cx, w)
        }
    }

    @Test
    fun aSliderCanReachFullAndZeroVolume() {
        // Mute is now "volume 0" rather than a separate boolean, so both ends of
        // the track have to be reachable exactly.
        val cx = 300f
        val w = 300f
        assertEquals(1f, MenuMetrics.sliderFraction(cx + w / 2f, cx, w), 0.0001f)
        assertEquals(0f, MenuMetrics.sliderFraction(cx - w / 2f, cx, w), 0.0001f)
    }

    // ---- difficulty caption --------------------------------------------

    @Test
    fun difficultyCaptionClearsTheSegmentOnEveryDevice() {
        for (d in devices) {
            val rowH = glyphHeight(d) + 16f * scale(d)
            val segCy = d.h * 0.10f
            val captionH = glyphHeight(d)
            val cy = MenuMetrics.captionAboveSegment(segCy, rowH, captionH, gap(d, 6f))
            assertTrue(
                MenuMetrics.captionClearsSegment(cy, captionH, segCy, rowH),
                "DIFF caption overlaps the segment on ${d.name}",
            )
        }
    }

    @Test
    fun theOldFixedOffsetWouldHaveOverlapped() {
        // Guards the regression itself: the previous +18f offset is inside the
        // segment on the user's 1440x3200 phone, which is what was reported.
        val d = devices.first { it.name.contains("1440") }
        val rowH = glyphHeight(d) + 16f * scale(d)
        val segCy = d.h * 0.10f
        val captionH = glyphHeight(d)
        val oldCy = segCy + rowH / 2f + 18f
        assertFalse(
            MenuMetrics.captionClearsSegment(oldCy, captionH, segCy, rowH),
            "the old +18 offset is expected to overlap, otherwise this test proves nothing",
        )
    }

    @Test
    fun theDifficultyCaptionSitsAboveTheSegmentNotBelow() {
        // Screen y grows upward. An earlier attempt subtracted the offsets and
        // parked the caption under the segment, hard against the bottom edge of
        // a landscape phone. This pins the caption to the upper side and keeps it
        // clear of the screen edge.
        for (d in devices) {
            val rowH = glyphHeight(d) + 16f * scale(d)
            val segCy = d.h * 0.10f
            val captionH = glyphHeight(d)
            val cy = MenuMetrics.captionAboveSegment(segCy, rowH, captionH, gap(d, 6f))
            assertTrue(cy > segCy, "caption should be above the segment on ${d.name}")
            assertTrue(MenuMetrics.captionClearsSegment(cy, captionH, segCy, rowH))
            // The whole caption has to stay on screen with some room to spare.
            assertTrue(
                cy + captionH / 2f < d.h,
                "caption runs off the top of ${d.name}",
            )
        }
    }

    @Test
    fun captionAboveAndBelowSegmentAreSymmetric() {
        val above = MenuMetrics.captionAboveSegment(500f, 100f, 40f, 10f)
        val below = MenuMetrics.captionBelowSegment(500f, 100f, 40f, 10f)
        assertEquals(500f - (above - 500f), below, 0.0001f)
    }

    // ---- panel anchoring ------------------------------------------------

    @Test
    fun aShortPanelSitsAtTheTopOfTheBandUnderTheCaption() {
        // An empty leaderboard has only two rows, so its panel is much shorter
        // than the band. It must hug the caption instead of falling to the
        // bottom of the screen.
        val d = devices.first { it.name.contains("1440") }
        val bandTop = d.h * 0.90f
        val bandBottom = d.h * 0.14f
        val layout = SubScreenLayoutFactory.create(
            screenW = d.w, screenH = d.h, bandTop = bandTop, bandBottom = bandBottom,
            rowHeight = glyphHeight(d), rows = 2, scale = scale(d), padX = 26f * scale(d),
        )
        assertEquals(bandTop, layout.panelTop, 0.5f)
        // And it should be plainly in the upper half of the screen.
        assertTrue(
            layout.panelCy > d.h * 0.55f,
            "leaderboard panel is still low on screen: cy=${layout.panelCy} of ${d.h}",
        )
    }

    @Test
    fun theFirstRowStaysInsideTheTopAlignedPanel() {
        for (d in devices) {
            for (rows in listOf(1, 2, 3, 6, 9, 12)) {
                val bandTop = d.h * 0.90f
                val bandBottom = d.h * 0.14f
                val layout = SubScreenLayoutFactory.create(
                    screenW = d.w, screenH = d.h, bandTop = bandTop, bandBottom = bandBottom,
                    rowHeight = glyphHeight(d), rows = rows, scale = scale(d), padX = 26f * scale(d),
                )
                assertTrue(
                    layout.firstRowY + layout.rowHeight / 2f <= layout.panelTop + 0.5f,
                    "row 0 escapes the panel on ${d.name} with $rows rows",
                )
                val lastY = layout.rowY(rows - 1)
                assertTrue(
                    lastY - layout.rowHeight / 2f >= layout.panelBottom - 0.5f,
                    "last row escapes the panel on ${d.name} with $rows rows",
                )
            }
        }
    }

    @Test
    fun overlapHelperDetectsBothAxes() {
        assertTrue(MenuMetrics.overlaps(0f, 0f, 10f, 10f, 5f, 0f, 10f, 10f))
        assertTrue(MenuMetrics.overlaps(0f, 0f, 10f, 10f, 0f, 5f, 10f, 10f))
        assertFalse(MenuMetrics.overlaps(0f, 0f, 10f, 10f, 20f, 0f, 10f, 10f))
        assertFalse(MenuMetrics.overlaps(0f, 0f, 10f, 10f, 0f, 20f, 10f, 10f))
    }
}
