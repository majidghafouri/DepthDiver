package com.depthdiver.game

import kotlin.math.max
import kotlin.math.min

/**
 * How far a line box's centre sits above its baseline, as a fraction of the line
 * box height. Matches how FreeType lays out OpenSans.
 */
private const val BASELINE_DROP = 0.3f

/**
 * Row layout for the sub-screens (profile, achievements, leaderboard, shop and
 * settings).
 *
 * These screens used to space their rows with fixed pixel constants -- 46f,
 * 50f, 38f, 60f -- written when the body font was around 15px tall. The font is
 * generated from the screen height and the UI scale, so on a 1440x3200 phone it
 * renders 64px and every row ended up closer to the next one than it was tall:
 * the lists overlapped. The main menu was the only screen that measured the
 * rendered row height, which is why it was the only one that read correctly.
 *
 * This measures instead of assuming. The gap is derived from the real row height
 * and the panel is sized from its content, so rows cannot collide regardless of
 * the aspect ratio.
 */
data class SubScreenLayout(
    val panelCx: Float,
    val panelCy: Float,
    val panelW: Float,
    val panelH: Float,
    val firstRowY: Float,
    val gap: Float,
    val rowHeight: Float,
    val padX: Float,
) {
    /** Vertical centre of row [i], counting down from the first. */
    fun rowY(i: Int): Float = firstRowY - gap * i

    /**
     * Baseline to hand to `BitmapFont.draw` for row [i].
     *
     * `Widgets.textLeft`/`textRight` take a baseline while `Widgets.text` takes a
     * centre, which is exactly the kind of mismatch that makes rows drift into
     * each other. Rows are positioned by centre here and converted once.
     */
    fun rowBaseline(i: Int): Float = rowY(i) + rowHeight * BASELINE_DROP

    fun labelX(): Float = panelCx - panelW / 2f + padX

    fun valueX(): Float = panelCx + panelW / 2f - padX

    val panelTop: Float get() = panelCy + panelH / 2f

    val panelBottom: Float get() = panelCy - panelH / 2f

    /**
     * True when [count] rows fit inside the panel with no overlap. Used by tests
     * across a matrix of screen sizes, and by the drawing code to fall back to a
     * tighter spacing if a device ever turns out to be cramped.
     */
    fun rowsFit(count: Int): Boolean {
        if (count <= 0) return true
        if (gap < rowHeight - 0.5f) return false
        if (firstRowY + rowHeight / 2f > panelTop + 0.5f) return false
        val lastY = rowY(count - 1)
        return lastY - rowHeight / 2f >= panelBottom - 0.5f
    }
}

object SubScreenLayoutFactory {

    /** Panels never span more than this share of the screen width. */
    private const val MAX_PANEL_FRACTION = 0.9f

    /**
     * Rows are never allowed to sit closer than this multiple of their own
     * height, so a cramped screen degrades to "tight" rather than "overlapping".
     */
    private const val MIN_ROW_CLEARANCE = 1.06f

    /** Comfortable spacing, matching what the main menu already used. */
    private const val PREFERRED_GAP = 72f

    private const val ROW_PADDING = 10f

    /** Breathing room above the first row and below the last, in gaps. */
    private const val PANEL_PAD_ROWS = 0.35f

    /**
     * Panel width for a sub-screen.
     *
     * This used to be capped at an absolute 700px, which on a 1440px-wide phone
     * left the panel at 48% of the width while the text inside it was 64px, so
     * labels ran into their right-aligned values. The cap now scales with the UI.
     */
    fun panelWidth(screenW: Float, scale: Float): Float =
        min(screenW * MAX_PANEL_FRACTION, 700f * scale)
            .coerceAtMost(screenW - 8f)
            .coerceAtLeast(1f)

    /**
     * Builds a panel that fits [rows] rows of [rowHeight] between [bandTop] and
     * [bandBottom], leaving a little breathing room above the first row and below
     * the last for banners, headers and buttons.
     *
     * [headRows] and [tailRows] are expressed in gaps rather than pixels so the
     * padding follows the gap that actually gets used, instead of being computed
     * from the preferred gap and then wasting the difference.
     */
    fun create(
        screenW: Float,
        screenH: Float,
        bandTop: Float,
        bandBottom: Float,
        rowHeight: Float,
        rows: Int,
        scale: Float,
        padX: Float,
        headRows: Float = PANEL_PAD_ROWS,
        tailRows: Float = PANEL_PAD_ROWS,
    ): SubScreenLayout {
        val safeRowHeight = rowHeight.coerceAtLeast(1f)
        val band = (bandTop - bandBottom).coerceAtLeast(safeRowHeight)
        val spacing = (rows - 1).coerceAtLeast(0)

        val maxGap = safeRowHeight * 2.4f
        val preferredGap = max(
            safeRowHeight + ROW_PADDING * scale,
            PREFERRED_GAP * scale,
        ).coerceAtMost(maxGap)
        val minGap = safeRowHeight * MIN_ROW_CLEARANCE

        // Solve for the largest gap that still fits, then never go below the
        // spacing at which two rows would touch.
        val gapDivisions = spacing + headRows + tailRows
        val maxAllowed = if (gapDivisions > 0f) {
            (band - safeRowHeight) / gapDivisions
        } else {
            preferredGap
        }
        val gap = if (rows <= 1) {
            preferredGap.coerceAtMost(maxOf(maxAllowed, minGap))
        } else {
            preferredGap.coerceIn(minGap, maxOf(minGap, maxAllowed))
        }

        val headPad = gap * headRows
        val tailPad = gap * tailRows
        val panelH = (headPad + tailPad + safeRowHeight + spacing * gap).coerceAtMost(band)
        val panelCy = bandBottom + panelH / 2f

        return SubScreenLayout(
            panelCx = screenW / 2f,
            panelCy = panelCy,
            panelW = panelWidth(screenW, scale),
            panelH = panelH,
            firstRowY = panelCy + panelH / 2f - headPad - safeRowHeight / 2f,
            gap = gap,
            rowHeight = safeRowHeight,
            padX = padX,
        )
    }
}
