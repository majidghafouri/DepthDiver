package com.depthdiver.landmark

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture

/**
 * The landmark art, drawn the same way as every other prop in the game: a
 * `Pixmap` built at boot out of circles, rectangles and lines.
 *
 * Each one is a silhouette rather than a picture, because that is what the rest
 * of the game is and because a wreck you cannot quite make out at depth is
 * closer to the actual experience than a wreck you can.
 */
object LandmarkArt {

    /** Texture for [kind], and the world size it was drawn at, in metres. */
    fun textureFor(kind: Landmark.Kind): Texture = Texture(pixmapFor(kind))

    private fun pixmapFor(kind: Landmark.Kind): Pixmap = when (kind) {
        Landmark.Kind.WRECK -> wreck()
        Landmark.Kind.WHALE_FALL -> whaleFall()
        Landmark.Kind.ARCH -> arch()
        Landmark.Kind.VENT -> vent()
        Landmark.Kind.KELP -> kelp()
    }

    /** A hull on its side: a long box with a broken-off bow. */
    private fun wreck(): Pixmap {
        val p = Pixmap(128, 64, Pixmap.Format.RGBA8888)
        p.setColor(0.28f, 0.24f, 0.2f, 1f)
        p.fillRectangle(18, 16, 88, 30)
        p.setColor(0.20f, 0.17f, 0.14f, 1f)
        p.fillRectangle(18, 16, 88, 6)
        // a snapped bow, so it reads as broken rather than as a crate
        p.setColor(0.28f, 0.24f, 0.2f, 1f)
        p.fillTriangle(0, 16, 0, 46, 18, 40)
        // a mast stub
        p.setColor(0.22f, 0.19f, 0.15f, 1f)
        p.fillRectangle(56, 46, 5, 16)
        return p
    }

    /** Ribs curving out of the silt, with a skull at one end. */
    private fun whaleFall(): Pixmap {
        val p = Pixmap(128, 64, Pixmap.Format.RGBA8888)
        p.setColor(0.86f, 0.86f, 0.82f, 0.92f)
        for (i in 0 until 7) {
            val x = 30 + i * 11
            p.drawLine(x, 10, x + 4, 40)
        }
        p.setColor(0.9f, 0.9f, 0.85f, 0.95f)
        p.fillCircle(20, 14, 12)
        p.fillRectangle(28, 12, 84, 5)
        // tail flukes
        p.setColor(0.86f, 0.86f, 0.82f, 0.9f)
        p.fillTriangle(112, 14, 126, 26, 112, 18)
        p.fillTriangle(112, 14, 126, 6, 112, 10)
        return p
    }

    /** Two stags meeting overhead, so the player passes through it. */
    private fun arch(): Pixmap {
        val p = Pixmap(128, 96, Pixmap.Format.RGBA8888)
        p.setColor(0.34f, 0.27f, 0.2f, 1f)
        p.fillRectangle(6, 0, 14, 74)
        p.fillRectangle(108, 0, 14, 74)
        // a lintel, slightly cracked in the middle
        p.setColor(0.31f, 0.25f, 0.18f, 1f)
        p.fillRectangle(6, 70, 116, 12)
        p.setColor(0.26f, 0.21f, 0.15f, 1f)
        p.fillRectangle(56, 78, 10, 14)
        // growth along the top
        p.setColor(0.35f, 0.62f, 0.42f, 0.8f)
        p.fillCircle(24, 82, 7)
        p.fillCircle(102, 84, 6)
        return p
    }

    /** A chimney with something pale and blind living on it. */
    private fun vent(): Pixmap {
        val p = Pixmap(96, 128, Pixmap.Format.RGBA8888)
        p.setColor(0.22f, 0.18f, 0.17f, 1f)
        p.fillTriangle(30, 0, 66, 0, 58, 96)
        p.fillTriangle(38, 96, 58, 96, 48, 118)
        // the plume, which is the only warm thing down here
        p.setColor(0.55f, 0.3f, 0.22f, 0.5f)
        p.fillCircle(48, 30, 20)
        p.setColor(0.7f, 0.4f, 0.28f, 0.32f)
        p.fillCircle(52, 48, 13)
        // tube worms
        p.setColor(0.85f, 0.8f, 0.75f, 0.9f)
        for (i in 0 until 5) {
            val x = 34 + i * 7
            p.drawLine(x, 96, x, 108)
        }
        return p
    }

    /** A screen of strands, drawn as vertical lines of varying height. */
    private fun kelp(): Pixmap {
        val p = Pixmap(128, 128, Pixmap.Format.RGBA8888)
        p.setColor(0.16f, 0.45f, 0.28f, 0.95f)
        for (i in 0 until 11) {
            val x = 8 + i * 11
            val top = 14 + (i * 37 % 40)
            p.drawLine(x, 0, x + ((i % 3) - 1) * 5, top)
        }
        p.setColor(0.22f, 0.55f, 0.34f, 0.9f)
        for (i in 0 until 7) {
            val x = 14 + i * 17
            val top = 26 + (i * 29 % 34)
            p.drawLine(x, 0, x - 4, top)
        }
        return p
    }
}
