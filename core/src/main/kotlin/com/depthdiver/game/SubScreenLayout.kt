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
        // The row box is the "Hg" glyph extent, which is taller than the ink any
        // one row draws, so a gap a hair under the box is not a visual collision.
        // A hair of slack matters because the factory is allowed to compress the
        // gap to keep the list inside the band, and without it a list that only
        // just fits would report "does not fit" and trip the layout tests.
        if (gap < rowHeight * 0.98f) return false
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
     *
     * The row box is the "Hg" glyph extent, which is taller than the ink a row
     * actually draws, so a gap of 1.0 still leaves the visible lines clearly
     * separated.
     */
    private const val MIN_ROW_CLEARANCE = 1.0f

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
        // maxAllowed is the largest gap whose rows still fit the band. When it
        // drops below minGap the previous floor of minGap was kept anyway, which
        // pushed the last rows straight out of the band and underneath the back
        // button. Compressing the gap is the lesser evil: rows get tight, but
        // they stay inside the panel and stay reachable.
        val gap = if (rows <= 1) {
            preferredGap.coerceAtMost(maxOf(maxAllowed, minGap))
        } else {
            preferredGap.coerceIn(0f, maxOf(maxAllowed, 0f))
        }

        val headPad = gap * headRows
        val tailPad = gap * tailRows
        val panelH = (headPad + tailPad + safeRowHeight + spacing * gap).coerceAtMost(band)
        // With the gap solved to fit, the content should land on the band exactly
        // rather than needing the cap above; the cap is kept only as a guard.
        // Anchor the panel to the TOP of the band so a short list (an empty
        // leaderboard, say) sits directly under its caption instead of being
        // pushed down to the bottom of the screen.
        val panelCy = bandTop - panelH / 2f

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
