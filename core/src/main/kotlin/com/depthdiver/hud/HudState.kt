package com.depthdiver.hud

/**
 * Immutable snapshot of everything the in-run HUD needs to draw itself.
 *
 * Extracting this is what lets the HUD be rendered without reaching back into
 * the game object, so the two can evolve (and be tested) independently.
 */
data class HudState(
    val screenWidth: Float,
    val screenHeight: Float,
    val lineHeight: Float,
    val depth: Float,
    val score: Int,
    val bestDepth: Float,
    val bestScore: Int,
    val oxygen: Float,
    val maxOxygen: Float,
    val combo: Int,
    val comboTimer: Float,
    val maxComboWindow: Float,
    val elapsed: Float,
    val isPlaying: Boolean,
    val biomeNameKey: String = "zoneSunlit",
    val bossWarningRemaining: Float = 0f,
    /** UI scale for this viewport, so gaps grow with the fonts they separate. */
    val scale: Float = 1f,
) {
    val oxygenRatio: Float
        get() = if (maxOxygen > 0f) (oxygen / maxOxygen).coerceIn(0f, 1f) else 1f

    val isLowOxygen: Boolean
        get() = isPlaying && maxOxygen > 0f && oxygen <= maxOxygen * LOW_OXYGEN_FRACTION

    /** 0 at the threshold, 1 when completely out of air. */
    val lowOxygenDanger: Float
        get() = if (maxOxygen <= 0f) 0f
        else ((maxOxygen * LOW_OXYGEN_FRACTION - oxygen) / (maxOxygen * LOW_OXYGEN_FRACTION))
            .coerceIn(0f, 1f)

    val showCombo: Boolean
        get() = isPlaying && combo > 1

    val comboFraction: Float
        get() = if (maxComboWindow <= 0f) 0f else (comboTimer / maxComboWindow).coerceIn(0f, 1f)

    companion object {
        const val LOW_OXYGEN_FRACTION = 0.25f
    }
}

/** Where the HUD's pause button sits, so hit-testing need not re-derive it. */
data class HudLayout(
    val pauseCx: Float,
    val pauseCy: Float,
    val pauseW: Float,
    val pauseH: Float,
)

internal const val HUD_PADDING = 6f
internal const val HUD_LINE_SPACING = 8f
internal const val HUD_COLUMN_GAP = 22f
internal const val HUD_LEFT_MARGIN = 10f
internal const val HUD_RIGHT_MARGIN = 10f
internal const val HUD_PAUSE_MARGIN = 12f
internal const val HUD_BOSS_LINE_FACTOR = 1.4f

/**
 * Pure HUD geometry.
 *
 * These used to be inline in the renderer with the results stashed into game
 * fields as a side effect; making them pure means the touch handler and the
 * renderer cannot drift apart, and the layout is unit-testable.
 */
object HudLayoutRules {

    /**
     * Baseline of HUD row [row], counting down from the top of the screen.
     *
     * Line spacing scales with the row height: fonts grow on large screens, so
     * a fixed gap would let consecutive rows collide.
     */
    fun rowBaseline(screenHeight: Float, lineHeight: Float, row: Int, scale: Float = 1f): Float =
        screenHeight - lineHeight - HUD_PADDING * scale -
            (lineHeight + HUD_LINE_SPACING * scale) * row

    /** Y of the row that holds the pause button. */
    fun pauseRowBaseline(state: HudState): Float =
        rowBaseline(state.screenHeight, state.lineHeight, row = 2, state.scale)

    /**
     * Pause button box. While playing this is the real pill; otherwise a small
     * inert placeholder is reported so callers always get a valid rectangle.
     */
    fun pauseHitbox(state: HudState, pillW: Float, pillH: Float): HudLayout {
        val cy = pauseRowBaseline(state)
        return if (state.isPlaying) {
            HudLayout(
                pauseCx = state.screenWidth - HUD_PAUSE_MARGIN * state.scale - pillW / 2f,
                pauseCy = cy,
                pauseW = pillW,
                pauseH = pillH,
            )
        } else {
            val w = state.screenWidth * 0.1f
            val h = state.screenHeight * 0.05f
            HudLayout(
                pauseCx = state.screenWidth - HUD_PAUSE_MARGIN * state.scale - w / 2f,
                pauseCy = cy,
                pauseW = w,
                pauseH = h,
            )
        }
    }

    /** X of the score readout, to the right of the depth readout. */
    fun scoreLeft(state: HudState, depthTextWidth: Float): Float =
        HUD_LEFT_MARGIN * state.scale + depthTextWidth + HUD_COLUMN_GAP * state.scale

    /** X of the right-aligned difficulty label. */
    fun difficultyRight(state: HudState, labelWidth: Float): Float =
        state.screenWidth - HUD_RIGHT_MARGIN * state.scale - labelWidth

    /** Y of the boss warning strip, tucked under the top row. */
    fun bossWarningBaseline(state: HudState): Float =
        rowBaseline(state.screenHeight, state.lineHeight, 0, state.scale) -
            state.lineHeight * HUD_BOSS_LINE_FACTOR

    /** Y of the combo readout, tucked under the best-depth row. */
    fun comboBaseline(state: HudState): Float {
        val bestRow = rowBaseline(state.screenHeight, state.lineHeight, row = 3, state.scale)
        return bestRow - state.lineHeight * 1.5f
    }

    /** Y of the combo timer bar. */
    fun comboBarBottom(state: HudState): Float =
        comboBaseline(state) - state.lineHeight * 0.6f - COMBO_BAR_HEIGHT * state.scale

    const val COMBO_BAR_WIDTH = 90f
    const val COMBO_BAR_HEIGHT = 5f

    /** Width of the combo timer bar for this viewport. */
    fun comboBarWidth(state: HudState): Float = COMBO_BAR_WIDTH * state.scale
}
