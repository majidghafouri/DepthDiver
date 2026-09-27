package com.depthdiver.game

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guards the sub-screen row layout across the device sizes the game has to
 * survive.
 *
 * These screens spaced rows with fixed pixel constants (46f, 50f, 38f) that
 * predated the scaled font, so on a tall modern phone the text was taller than
 * the gap and every list overlapped. The main menu was unaffected because it
 * measured the rendered row height; this asserts the panels do the same.
 */
class SubScreenLayoutTest {

    private data class Device(val name: String, val w: Float, val h: Float)

    private val devices = listOf(
        Device("small phone", 480f, 800f),
        Device("reference phone", 720f, 1280f),
        Device("tall 18:9 phone", 1080f, 2340f),
        Device("tall 20:9 phone", 1080f, 2400f),
        Device("user's phone 1440x3200", 1440f, 3200f),
        Device("tablet portrait", 1536f, 2048f),
        Device("tablet landscape", 2048f, 1536f),
        Device("foldable open", 1812f, 2176f),
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

    private fun pillHeight(d: Device): Float = glyphHeight(d) + 16f * scale(d)

    /** Vertical band left free for the panel, between the title and the back pill. */
    private fun band(d: Device): Pair<Float, Float> {
        val titleSize = (d.h / 9f * scale(d)).toInt().coerceIn(36, 120).toFloat()
        val titleBottom = d.h * 0.84f - titleSize * 1.15f / 2f - 12f * scale(d)
        val backTop = d.h * 0.08f + pillHeight(d) / 2f + 12f * scale(d)
        return titleBottom to backTop
    }

    private fun layoutFor(d: Device, rows: Int, rowHeight: Float): SubScreenLayout {
        val (bandTop, bandBottom) = band(d)
        return SubScreenLayoutFactory.create(
            screenW = d.w,
            screenH = d.h,
            bandTop = bandTop,
            bandBottom = bandBottom,
            rowHeight = rowHeight,
            rows = rows,
            scale = scale(d),
            padX = 18f * scale(d),
        )
    }

    /**
     * One entry per real sub-screen: how many rows it stacks and whether those
     * rows contain a pill (shop and settings) or are plain text.
     */
    private data class Screen(val name: String, val rows: Int, val pillRows: Boolean)

    private val screens = listOf(
        Screen("profile stats", 6, false),
        Screen("achievements", 9, false),
        Screen("leaderboard", 5, false),
        Screen("shop", 5, true),
        Screen("settings", 6, true),
    )

    @Test
    fun everyScreenFitsOnEveryDevice() {
        for (d in devices) {
            for (screen in screens) {
                val rowHeight = if (screen.pillRows) pillHeight(d) else glyphHeight(d)
                val l = layoutFor(d, screen.rows, rowHeight)
                assertTrue(
                    l.rowsFit(screen.rows),
                    "${d.name} / ${screen.name}: rows do not fit " +
                        "(gap=${l.gap} rowHeight=${l.rowHeight} panelH=${l.panelH} band=${band(d)})",
                )
            }
        }
    }

    @Test
    fun rowsNeverOverlapEvenWhenTheContentCannotFit() {
        // A screen can be handed more rows than it has room for. Overlap is never
        // acceptable, so the gap must hold at its minimum spacing even then.
        for (d in devices) {
            for (rows in listOf(12, 20, 40)) {
                val l = layoutFor(d, rows, pillHeight(d))
                assertTrue(
                    l.gap >= l.rowHeight,
                    "${d.name} rows=$rows: gap ${l.gap} is below row height ${l.rowHeight}",
                )
            }
        }
    }

    @Test
    fun consecutiveRowsNeverTouch() {
        for (d in devices) {
            val l = layoutFor(d, 9, glyphHeight(d))
            for (i in 0 until 8) {
                val upper = l.rowY(i) - l.rowHeight / 2f
                val lower = l.rowY(i + 1) + l.rowHeight / 2f
                assertTrue(
                    upper >= lower,
                    "${d.name}: row $i overlaps row ${i + 1} (upper=$upper lower=$lower)",
                )
            }
        }
    }

    @Test
    fun panelStaysBetweenTheTitleAndTheBackButton() {
        for (d in devices) {
            val (bandTop, bandBottom) = band(d)
            val l = layoutFor(d, 9, glyphHeight(d))
            assertTrue(
                l.panelTop <= bandTop + 0.5f,
                "${d.name}: panel top ${l.panelTop} runs into the title (band top $bandTop)",
            )
            assertTrue(
                l.panelBottom >= bandBottom - 0.5f,
                "${d.name}: panel bottom ${l.panelBottom} runs into the back pill (band bottom $bandBottom)",
            )
        }
    }

    @Test
    fun panelStaysOnScreen() {
        for (d in devices) {
            val l = layoutFor(d, 9, glyphHeight(d))
            assertTrue(l.panelW <= d.w, "${d.name}: panel wider than screen")
            assertTrue(l.panelH <= d.h, "${d.name}: panel taller than screen")
            assertTrue(l.panelCx - l.panelW / 2f >= -0.5f, "${d.name}: panel left of screen")
            assertTrue(l.panelCx + l.panelW / 2f <= d.w + 0.5f, "${d.name}: panel right of screen")
            assertTrue(l.panelBottom >= -0.5f, "${d.name}: panel below screen")
            assertTrue(l.panelTop <= d.h + 0.5f, "${d.name}: panel above screen")
        }
    }

    @Test
    fun panelUsesTheAvailableWidthOnAHighResolutionPhone() {
        // The bug this replaces: an absolute 700px cap left the panel at 48% of a
        // 1440px screen while the text inside it was 64px.
        val d = devices.first { it.name == "user's phone 1440x3200" }
        val l = layoutFor(d, 6, pillHeight(d))
        assertTrue(
            l.panelW >= d.w * 0.85f,
            "panel ${l.panelW} should use most of a ${d.w}px screen, got ${l.panelW / d.w * 100}%",
        )
    }

    @Test
    fun shortListsDoNotSpreadOutAbsurdly() {
        val d = devices.first { it.name == "tall 20:9 phone" }
        val l = layoutFor(d, 2, pillHeight(d))
        assertTrue(
            l.gap <= l.rowHeight * 2.5f,
            "two rows should stay near each other, gap=${l.gap} rowHeight=${l.rowHeight}",
        )
    }

    @Test
    fun degenerateScreensDoNotProduceNonsense() {
        val l = SubScreenLayoutFactory.create(
            screenW = 0f, screenH = 0f, bandTop = 0f, bandBottom = 0f,
            rowHeight = 0f, rows = 0, scale = 1f, padX = 0f,
        )
        assertTrue(l.panelW > 0f, "width must stay positive")
        assertTrue(l.panelH > 0f, "height must stay positive")
        assertTrue(l.gap > 0f, "gap must stay positive")
        assertTrue(l.rowHeight > 0f, "row height must stay positive")
    }
}
