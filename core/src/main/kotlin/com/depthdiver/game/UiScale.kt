package com.depthdiver.game

/**
 * Tablet / large-screen layout scale.
 *
 * The game was authored against a 16:9 phone at roughly 1280x720. A 10" tablet
 * in landscape is ~2x that in each axis, so pixel-fixed UI (button padding,
 * row gaps, font size) ends up comically small and hard to tap even though the
 * proportional parts of the layout look fine.
 *
 * [factor] is a single multiplier to apply to pixel-sized UI constants, and
 * [minTouchPx] is a floor for interactive hit areas. Both are derived from the
 * *shorter* screen edge so that scaling is consistent regardless of whether the
 * device is a wide tablet or a tall one.
 */
data class UiScale(
    val factor: Float,
    val minTouchPx: Float,
) {
    /** Scales a pixel constant. */
    fun px(value: Float): Float = value * factor

    /** Scales [value] but never returns less than [minTouchPx]. */
    fun touch(value: Float): Float = maxOf(px(value), minTouchPx)

    /** Scales a gap/margin; no minimum is enforced. */
    fun gap(value: Float): Float = px(value)

    companion object {
        /** Shortest edge of the reference 16:9 phone layout the constants assume. */
        const val REFERENCE_SHORT_EDGE = 720f

        /** Beyond this the UI starts to look clumsy, so growth is capped. */
        const val MAX_FACTOR = 1.9f

        /** Android's accessibility guidance is ~48dp; 48px is a sane floor here. */
        const val MIN_TOUCH_PX = 48f

        /**
         * @param screenWidth  viewport width in pixels
         * @param screenHeight viewport height in pixels
         */
        fun forScreen(screenWidth: Float, screenHeight: Float): UiScale {
            if (!screenWidth.isFinite() || !screenHeight.isFinite()) {
                return UiScale(1f, MIN_TOUCH_PX)
            }
            if (screenWidth <= 0f || screenHeight <= 0f) {
                return UiScale(1f, MIN_TOUCH_PX)
            }
            val shortEdge = minOf(screenWidth, screenHeight)
            val raw = shortEdge / REFERENCE_SHORT_EDGE
            val factor = raw.coerceIn(1f, MAX_FACTOR)
            // Touch targets grow with the UI, but never fall below the floor.
            val minTouch = (MIN_TOUCH_PX * factor).coerceAtLeast(MIN_TOUCH_PX)
            return UiScale(factor, minTouch)
        }
    }
}
