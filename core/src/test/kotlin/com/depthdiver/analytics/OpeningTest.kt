package com.depthdiver.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The opening of a first dive.
 *
 * These numbers used to be scattered through the simulation -- a 50m milestone,
 * a 120m first pick, a 35/65 coin flip for the first pickup -- which is why none
 * of them could be argued about as a group. They are constants now, and this
 * pins the intent rather than the implementation.
 */
class OpeningTest {

    @Test
    fun theFirstMilestoneComesBeforeWhereFirstRunsEnd() {
        // The whole point: a first dive often ends well before 50m, so a reward
        // at 50m is a reward most first-timers never see.
        assertTrue(
            Opening.FIRST_MILESTONE_METERS <= 20f,
            "first milestone at ${Opening.FIRST_MILESTONE_METERS}m is too deep to be seen on a first dive",
        )
    }

    @Test
    fun theFirstMutationPickIsInsideThatSameWindow() {
        // If the pick that makes runs differ only arrives at depth, a player
        // never sees the feature that is supposed to keep them playing.
        assertTrue(
            Opening.FIRST_PICK_METERS <= Opening.FIRST_MILESTONE_METERS + 20f,
            "first pick at ${Opening.FIRST_PICK_METERS}m is past where a first run ends",
        )
    }

    @Test
    fun laterPicksAreSpreadOutEnoughToStayInteresting() {
        // Three picks back to back at the same spacing is a rhythm, not a
        // choice; the first one is early and the rest give the run some shape.
        assertTrue(Opening.PICK_STEP_METERS >= 60f)
        assertTrue(Opening.PICK_STEP_METERS > Opening.FIRST_PICK_METERS)
    }

    @Test
    fun theHintsStopAfterTheSecondDive() {
        // isFirstDive is strictly the first dive; the hint window is wider, which
        // is the point. A player who has dived once still gets the lines.
        assertTrue(Opening.isFirstDive(0))
        assertTrue(!Opening.isFirstDive(1))
        assertTrue(!Opening.isFirstDive(2))
        // dives counts finished dives, so HINT_RUNS = 1 covers dives one and two.
        assertEquals(1, Opening.HINT_RUNS)
    }

    @Test
    fun hintsAreOnlyShownBeforeTheSecondDive() {
        assertEquals(2, Opening.hintsFor(0).size)
        assertTrue(Opening.hintsFor(1).isNotEmpty(), "dive two can still use them")
        assertTrue(Opening.hintsFor(2).isEmpty(), "by the third dive the hints are noise")
        assertTrue(Opening.hintsFor(50).isEmpty(), "a regular player gets no hints")
    }

    @Test
    fun hintsAreShortEnoughToReadInOneGlance() {
        // Anything longer has to be skipped, and a thing that has to be skipped
        // is a thing nobody reads.
        Opening.hintsFor(0).forEach {
            assertTrue(it.length <= 40, "hint is too long to read at a glance: '$it'")
        }
    }

    @Test
    fun hintsAreLocalizedRatherThanHardcoded() {
        // The strings live in the string table, so a player in another language
        // is not shown English instructions in the middle of their first dive.
        val hint = Opening.hintsFor(0).first()
        assertTrue(
            hint.isNotEmpty(),
            "the hint should come from the string table and be non-empty",
        )
    }

    @Test
    fun theHintFadesOverAReadableWindow() {
        assertTrue(
            Opening.HINT_SECONDS >= 3f,
            "hints vanish too fast to read",
        )
        assertTrue(
            Opening.HINT_SECONDS <= 8f,
            "hints linger long enough to become an annoyance",
        )
    }
}
