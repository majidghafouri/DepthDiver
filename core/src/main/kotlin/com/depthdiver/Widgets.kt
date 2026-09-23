package com.depthdiver

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import kotlin.math.min

/** Lightweight HUD/menu widget helpers used by every screen. */
object Widgets {

    fun pillW(font: BitmapFont, label: String): Float = GlyphLayout(font, label).width + 26f

    fun pillH(font: BitmapFont, label: String): Float = GlyphLayout(font, label).height + 16f

    fun contains(tx: Float, ty: Float, cx: Float, cy: Float, w: Float, h: Float): Boolean =
        tx >= cx - w / 2f && tx <= cx + w / 2f && ty >= cy - h / 2f && ty <= cy + h / 2f

    /** Draw the styled pill button centered on (cx, cy); returns its actual [w, h] so hitboxes match.
     *  When [enabled] is false the pill is rendered dimmed. */
    fun pill(batch: SpriteBatch, font: BitmapFont, pixel: Texture, cx: Float, cy: Float, label: String, enabled: Boolean = true): Pair<Float, Float> {
        val layout = GlyphLayout(font, label)
        val w = layout.width + 26f
        val h = layout.height + 16f
        batch.setColor(0f, 0f, 0f, 0.30f)
        batch.draw(pixel, cx - w / 2f + 4f, cy - h / 2f - 4f, w, h)
        batch.setColor(
            if (enabled) 0.14f else 0.22f,
            if (enabled) 0.24f else 0.26f,
            if (enabled) 0.42f else 0.36f,
            if (enabled) 0.90f else 0.60f
        )
        batch.draw(pixel, cx - w / 2f, cy - h / 2f, w, h)
        batch.setColor(0.34f, 0.52f, 0.78f, if (enabled) 0.55f else 0.25f)
        batch.draw(pixel, cx - w / 2f, cy, w, h / 2f)
        val b = 2f
        batch.setColor(if (enabled) 0.95f else 0.45f, if (enabled) 0.97f else 0.48f, if (enabled) 1f else 0.5f, 0.95f)
        batch.draw(pixel, cx - w / 2f, cy - h / 2f, w, b)
        batch.draw(pixel, cx - w / 2f, cy + h / 2f - b, w, b)
        batch.draw(pixel, cx - w / 2f, cy - h / 2f, b, h)
        batch.draw(pixel, cx + w / 2f - b, cy - h / 2f, b, h)
        batch.setColor(Color.WHITE)
        font.color = if (enabled) Color.WHITE else Color(0.55f, 0.58f, 0.65f, 1f)
        font.draw(batch, layout, cx - layout.width / 2f, cy + layout.height / 2f)
        font.color = Color.WHITE
        return w to h
    }

    /** Draw centered text (baseline-anchored at cy + height/2). */
    fun text(batch: SpriteBatch, font: BitmapFont, s: String, cx: Float, cy: Float) {
        val layout = GlyphLayout(font, s)
        font.draw(batch, layout, cx - layout.width / 2f, cy + layout.height / 2f)
    }

    /** Left-aligned text; anchor is the baseline at (x, y). */
    fun textLeft(batch: SpriteBatch, font: BitmapFont, s: String, x: Float, y: Float) {
        font.draw(batch, s, x, y)
    }

    /** Right-aligned text; anchor is the baseline at (x, y). */
    fun textRight(batch: SpriteBatch, font: BitmapFont, s: String, x: Float, y: Float) {
        val layout = GlyphLayout(font, s)
        font.draw(batch, layout, x - layout.width, y)
    }

    /** Vertical stack position (cx, cy) for the [index]-th of [count] menu buttons.
     *  Tall screens use a single centered column; short (landscape) screens switch to a 2-column grid. */
    fun stack(worldW: Float, worldH: Float, index: Int, count: Int): Pair<Float, Float> {
        if (worldH >= 520f) {
            val gap = min(58f, worldH * 0.9f / (count + 1))
            val startY = worldH * 0.80f - gap * (count - 1) / 2f
            return worldW / 2f to startY - index * gap
        }
        val rows = (count + 1) / 2
        val gap = min(54f, worldH / (rows + 2))
        val startY = worldH * 0.80f - gap * (rows - 1) / 2f
        val col = index % 2
        val row = index / 2
        val cx = worldW * if (col == 0) 0.35f else 0.65f
        return cx to startY - row * gap
    }

    /** Dark translucent panel behind menu content to make labels pop. */
    fun panel(batch: SpriteBatch, pixel: Texture, cx: Float, cy: Float, w: Float, h: Float) {
        batch.setColor(0f, 0.02f, 0.08f, 0.55f)
        batch.draw(pixel, cx - w / 2f, cy - h / 2f, w, h)
        batch.setColor(0.25f, 0.45f, 0.72f, 0.25f)
        batch.draw(pixel, cx - w / 2f, cy + h / 2f - 3f, w, 3f)
        batch.setColor(Color.WHITE)
    }
}