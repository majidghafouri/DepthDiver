package com.depthdiver.hud

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.MathUtils
import com.depthdiver.Widgets
import com.depthdiver.common.Strings

/**
 * Draws the in-run HUD readouts.
 *
 * Owns no game state: everything comes in through [HudState], and the pause
 * button box is *returned* rather than written back into the caller, so the
 * renderer and the hit-tester always agree.
 */
class HudRenderer(
    private val batch: SpriteBatch,
    private val font: BitmapFont,
    private val pixel: Texture,
) {

    /**
     * @param difficultyLabel already localized
     * @param pausePillSize measured size of the pause pill
     * @return the pause button box for this frame
     */
    fun render(
        state: HudState,
        difficultyLabel: String,
        pausePillSize: Pair<Float, Float>,
    ): HudLayout {
        val layout = GlyphLayout()
        font.color = Color.WHITE

        val topY = HudLayoutRules.rowBaseline(state.screenHeight, state.lineHeight, row = 0, state.scale)
        var y = topY

        drawCentered(layout, Strings.t(state.biomeNameKey), state.screenWidth / 2f, y, Color(0.6f, 0.85f, 1f, 0.9f))

        layout.setText(font, "${Strings.t("depth")}: ${state.depth.toInt()} m")
        val depthWidth = layout.width
        font.color = Color.WHITE
        font.draw(batch, layout, HUD_LEFT_MARGIN * state.scale, y)

        layout.setText(font, "${Strings.t("score")}: ${state.score}")
        font.color = Color.WHITE
        font.draw(batch, layout, HudLayoutRules.scoreLeft(state, depthWidth), y)

        drawRight(
            layout,
            difficultyLabel,
            HudLayoutRules.difficultyRight(state, measure(difficultyLabel)),
            y,
            Color.CYAN,
        )

        if (state.bossWarningRemaining > 0f) {
            drawCentered(
                layout,
                Strings.t("leviathan"),
                state.screenWidth / 2f,
                HudLayoutRules.bossWarningBaseline(state),
                Color(1f, 0.35f, 0.3f, 1f),
            )
        }

        y = HudLayoutRules.rowBaseline(state.screenHeight, state.lineHeight, row = 1, state.scale)
        layout.setText(font, "${Strings.t("oxygen")}: ${(state.oxygenRatio * 100).toInt()}%")
        font.color = Color.WHITE
        font.draw(batch, layout, HUD_LEFT_MARGIN * state.scale, y)

        y = HudLayoutRules.pauseRowBaseline(state)
        val pillLayout = HudLayoutRules.pauseHitbox(state, pausePillSize.first, pausePillSize.second)
        if (state.isPlaying) {
            Widgets.pill(batch, font, pixel, pillLayout.pauseCx, pillLayout.pauseCy, Strings.t("pause"))
        }

        val bestRow = HudLayoutRules.rowBaseline(state.screenHeight, state.lineHeight, row = 3, state.scale)
        layout.setText(font, "${Strings.t("best")}: ${state.bestDepth.toInt()} m / ${state.bestScore}")
        font.color = Color.WHITE
        font.draw(batch, layout, HUD_LEFT_MARGIN, bestRow)

        if (state.showCombo) {
            drawCombo(state, layout)
        }

        if (state.isLowOxygen) {
            drawLowOxygenVignette(state)
        }

        return pillLayout
    }

    private fun drawCombo(state: HudState, layout: GlyphLayout) {
        layout.setText(font, "${Strings.t("combo")} x${state.combo}")
        val comboY = HudLayoutRules.comboBaseline(state)
        font.color = Color.GOLD
        font.draw(batch, layout, HUD_LEFT_MARGIN * state.scale, comboY)

        val barBottom = HudLayoutRules.comboBarBottom(state)
        val width = HudLayoutRules.comboBarWidth(state)
        val height = HudLayoutRules.COMBO_BAR_HEIGHT
        val fraction = state.comboFraction
        batch.setColor(0f, 0f, 0f, 0.6f)
        batch.draw(pixel, HUD_LEFT_MARGIN, barBottom, width, height)
        batch.setColor(1f, 0.85f, 0.2f, 1f)
        batch.draw(pixel, HUD_LEFT_MARGIN, barBottom, width * fraction, height)
        batch.setColor(1f, 1f, 1f, 1f)
        font.color = Color.WHITE
    }

    private fun drawLowOxygenVignette(state: HudState) {
        val danger = state.lowOxygenDanger
        val alpha = 0.15f * danger * (0.65f + 0.35f * ((MathUtils.sin(state.elapsed * 5f) + 1f) / 2f))
        val edge = 26f
        batch.setColor(1f, 0.1f, 0.08f, alpha)
        batch.draw(pixel, 0f, state.screenHeight - edge, state.screenWidth, edge)
        batch.draw(pixel, 0f, 0f, state.screenWidth, edge)
        batch.draw(pixel, 0f, edge, edge, state.screenHeight - 2f * edge)
        batch.draw(pixel, state.screenWidth - edge, edge, edge, state.screenHeight - 2f * edge)
        batch.setColor(1f, 1f, 1f, 1f)
    }

    private fun measure(text: String): Float {
        val scratch = GlyphLayout(font, text)
        return scratch.width
    }

    private fun drawCentered(layout: GlyphLayout, text: String, cx: Float, y: Float, color: Color) {
        layout.setText(font, text)
        font.color = color
        font.draw(batch, layout, cx - layout.width / 2f, y)
        font.color = Color.WHITE
    }

    private fun drawRight(layout: GlyphLayout, text: String, right: Float, y: Float, color: Color) {
        layout.setText(font, text)
        font.color = color
        font.draw(batch, layout, right, y)
        font.color = Color.WHITE
    }
}
