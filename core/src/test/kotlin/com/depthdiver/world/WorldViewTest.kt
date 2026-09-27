package com.depthdiver.world

import com.depthdiver.game.Biome
import com.depthdiver.game.WORLD_WIDTH_METERS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The world renderer is snapshot-driven, so the parts of it that are pure
 * arithmetic can be pinned without a run in progress.
 */
class WorldViewTest {

    @Test
    fun lightRaysFadeOutWithDepth() {
        // Rays only make sense near the surface and have to stop entirely before
        // the water is uniformly dark, or the top of the screen keeps a glow no
        // matter how deep the player is.
        assertTrue("rays should show at the surface", raysVisible(0f))
        assertTrue("rays should still show just below the surface", raysVisible(1f))
        assertTrue("rays should be gone at the world width", !raysVisible(WORLD_WIDTH_METERS))
        assertTrue("rays should stay gone far deeper", !raysVisible(WORLD_WIDTH_METERS * 4f))
    }

    @Test
    fun surfaceFactorIsClamped() {
        assertEquals(1f, surfaceFactor(0f), 1e-5f)
        // Negative depth is above the surface, which clamps to 1, not 0.
        assertEquals(1f, surfaceFactor(-50f), 1e-5f)
        assertTrue(surfaceFactor(WORLD_WIDTH_METERS) >= 0f)
        assertTrue(surfaceFactor(WORLD_WIDTH_METERS * 10f) >= 0f)
    }

    @Test
    fun raysStopExactlyWhereTheSurfaceFactorDropsBelowTheCutoff() {
        // The drawing code tests `surface > 0.05f`; this pins the two to the same
        // threshold so they cannot drift into a state where rays are computed and
        // then thrown away.
        val cutoffDepth = WORLD_WIDTH_METERS * 0.95f
        assertTrue(
            "raysVisible disagrees with the 0.05 cutoff the renderer uses",
            raysVisible(cutoffDepth) == (surfaceFactor(cutoffDepth) > 0.05f),
        )
    }

    @Test
    fun waterColourFollowsTheBiomeBlend() {
        // The water colour is a function of depth alone, so it must equal the
        // biome blend the simulation would produce for the same depth.
        for (depth in listOf(0f, 12.5f, 25f, 38f, 120f, 900f)) {
            val current = Biome.forDepth(depth)
            val expected = current.blendTo(Biome.nextOf(current), Biome.progressWithin(depth, current))
            val actual = waterColorAt(depth)
            assertEquals("top red at $depth m", expected.topRed, actual.topRed, 1e-5f)
            assertEquals("top green at $depth m", expected.topGreen, actual.topGreen, 1e-5f)
            assertEquals("top blue at $depth m", expected.topBlue, actual.topBlue, 1e-5f)
            assertEquals("bottom red at $depth m", expected.bottomRed, actual.bottomRed, 1e-5f)
            assertEquals("bottom green at $depth m", expected.bottomGreen, actual.bottomGreen, 1e-5f)
            assertEquals("bottom blue at $depth m", expected.bottomBlue, actual.bottomBlue, 1e-5f)
        }
    }

    @Test
    fun waterColourIsDeterministic() {
        // The renderer is handed a snapshot; if the colour were computed from
        // anything that moved, the same depth would not give the same water.
        val a = waterColorAt(37.5f)
        val b = waterColorAt(37.5f)
        assertEquals(a, b)
    }
}
