package com.depthdiver.simulation

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.depthdiver.game.GameState

class FrameTimeOverlay {

    private var visible = false

    fun toggle() {
        visible = !visible
    }

    fun setVisible(visible: Boolean) {
        this.visible = visible
    }

    fun isVisible(): Boolean = visible

    fun render(
        batch: SpriteBatch,
        font: BitmapFont,
        pixel: Texture,
        monitor: PerformanceMonitor,
        state: GameState,
    ) {
        if (!visible || state == GameState.MAIN_MENU) return

        val stats = monitor.latest()
        val padding = 6f
        val lineHeight = 18f
        val boxWidth = 148f
        val boxHeight = lineHeight * 2f + padding * 2f + 14f
        val x = 10f
        val top = 110f

        batch.setColor(0f, 0f, 0f, 0.35f)
        batch.draw(pixel, x - padding, top - lineHeight * 2f - 14f - padding, boxWidth, boxHeight)
        batch.setColor(1f, 1f, 1f, 1f)

        font.color = when {
            stats.fps < 30f -> Color.RED
            stats.fps < 55f -> Color.YELLOW
            else -> Color.GREEN
        }
        font.draw(batch, "FPS ${stats.fps.toInt()}", x, top)
        font.color = Color.WHITE
        font.draw(batch, "avg ${stats.avgFrameTimeMs.toInt()}ms", x, top - lineHeight)
        font.draw(
            batch,
            "min/max ${stats.minFrameTimeMs.toInt()}/${stats.maxFrameTimeMs.toInt()}ms",
            x,
            top - lineHeight * 2f
        )
    }
}
