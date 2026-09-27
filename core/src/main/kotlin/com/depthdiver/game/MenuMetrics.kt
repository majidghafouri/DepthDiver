package com.depthdiver.game

/**
 * Geometry helpers for the main-menu and settings affordances.
 *
 * These used to be inlined as raw pixel offsets, which is how the "DIFF"
 * caption ended up drawn inside the difficulty segment once the font scaled
 * up on a 1440x3200 phone. Keeping the arithmetic here makes the clearance
 * checkable.
 */
object MenuMetrics {

    /**
     * Vertical centre for a caption of height [captionH] that belongs *above*
     * a segment of height [segmentH] centred on [segmentCy].
     *
     * Screen y grows upward, so "above" means a larger centre than the segment
     * plus half of both boxes and the gap. Getting this sign wrong parks the
     * caption underneath the segment, against the bottom edge of the screen.
     */
    fun captionAboveSegment(
        segmentCy: Float,
        segmentH: Float,
        captionH: Float,
        gap: Float,
    ): Float = segmentCy + segmentH / 2f + captionH / 2f + gap

    /**
     * True when a caption centred on [captionCy] with height [captionH] clears
     * the segment entirely, i.e. there is no overlap.
     */
    fun captionClearsSegment(
        captionCy: Float,
        captionH: Float,
        segmentCy: Float,
        segmentH: Float,
    ): Boolean = captionCy - captionH / 2f >= segmentCy + segmentH / 2f

    /**
     * Vertical centre for a caption of height [captionH] drawn *under* a
     * segment of height [segmentH] centred on [segmentCy].
     */
    fun captionBelowSegment(
        segmentCy: Float,
        segmentH: Float,
        captionH: Float,
        gap: Float,
    ): Float = segmentCy - segmentH / 2f - captionH / 2f - gap

    /**
     * Fraction along a slider track of width [trackW] centred on [trackCx] that
     * a touch at [tx] selects, clamped to 0..1.
     *
     * This is what makes a volume control behave like a real slider: tapping
     * anywhere on the track jumps to that level rather than stepping a fixed
     * amount, and dragging keeps following the finger.
     */
    fun sliderFraction(tx: Float, trackCx: Float, trackW: Float): Float =
        if (trackW <= 0f) 0f else ((tx - (trackCx - trackW / 2f)) / trackW).coerceIn(0f, 1f)

    /** True when two rectangles centred on ([ax],[ay]) and ([bx],[by]) overlap. */
    fun overlaps(
        ax: Float, ay: Float, aw: Float, ah: Float,
        bx: Float, by: Float, bw: Float, bh: Float,
    ): Boolean = kotlin.math.abs(ax - bx) < (aw + bw) / 2f && kotlin.math.abs(ay - by) < (ah + bh) / 2f
}
