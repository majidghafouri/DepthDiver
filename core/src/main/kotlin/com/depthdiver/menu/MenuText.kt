package com.depthdiver.menu

import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout

/**
 * The text measurements the menu geometry depends on.
 *
 * Geometry only ever needs three numbers out of a font, and a `BitmapFont` is a
 * GL-backed object that cannot be built in a plain JVM test. Depending on it
 * directly made the extracted geometry untestable without a device, which is the
 * opposite of what the extraction was for. [FontMenuText] is the real
 * implementation; tests supply their own.
 */
interface MenuText {
    /** Rendered width of [text] in pixels. */
    fun width(text: String): Float

    /** Line box height of the body font. Rows are spaced by this. */
    fun bodyLineHeight(): Float

    /** Line box height of the title font, which the sub-screen band reserves for. */
    fun titleLineHeight(): Float
}

/** [MenuText] backed by the game's actual fonts. */
class FontMenuText(body: BitmapFont, title: BitmapFont) : MenuText {

    private val bodyLayout = GlyphLayout(body, "Hg")
    private val titleLayout = GlyphLayout(title, "Hg")
    private val body = body

    override fun width(text: String): Float {
        bodyLayout.setText(body, text)
        return bodyLayout.width
    }

    override fun bodyLineHeight(): Float = bodyLayout.height

    override fun titleLineHeight(): Float = titleLayout.height
}
