package com.depthdiver.run

import com.depthdiver.game.HazardKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The death loop.
 *
 * A run that ends should say what ended it and what to do about it. These tests
 * are about the sentences being right and specific, because the failure mode
 * here is not a crash -- it is a vague line that teaches a player nothing while
 * every test still passes.
 */
class DeathLessonTest {

    @Test
    fun runningOutOfAirIsNamedAsRunningOutOfAir() {
        val lesson = DeathLesson.forRun(RunTerminalReason.OXYGEN, 120f, 0f)
        assertEquals("causeAir", lesson.causeKey)
        assertEquals("hintAir", lesson.hintKey)
        assertTrue(lesson.isDeath)
    }

    @Test
    fun everyLethalHazardIsNamedSpecifically() {
        // "HIT SOMETHING" is a fallback for when the kind is somehow unknown. The
        // whole value of this feature is that it usually is not needed.
        val causes = HazardKind.values().map { kind ->
            DeathLesson.forRun(RunTerminalReason.HAZARD, 100f, 0f, kind).causeKey
        }
        assertEquals(HazardKind.values().size, causes.toSet().size, "two hazards share a cause: $causes")
        assertFalse(causes.contains("causeHazard"), "a known hazard fell back to the generic cause")
    }

    @Test
    fun anUnknownHazardFallsBackWithoutFailing() {
        // Reachable if a hazard is added to the enum and not to the lesson.
        val lesson = DeathLesson.forRun(RunTerminalReason.HAZARD, 100f, 0f, null)
        assertEquals("causeHazard", lesson.causeKey)
        assertTrue(lesson.isDeath)
    }

    @Test
    fun everyHintSaysSomethingDifferent() {
        val hints = HazardKind.values().mapNotNull { kind ->
            DeathLesson.forRun(RunTerminalReason.HAZARD, 100f, 0f, kind).hintKey
        }
        assertEquals(hints.toSet().size, hints.size, "two hazards share a hint: $hints")
    }

    @Test
    fun quittingIsNotADeath() {
        // Dressing a voluntary exit up as a death, with a lesson attached, is
        // the version of this feature that would be genuinely annoying.
        for (reason in listOf(
            RunTerminalReason.ABANDONED,
            RunTerminalReason.COMPLETED,
            RunTerminalReason.MANUAL,
            RunTerminalReason.UNKNOWN,
        )) {
            val lesson = DeathLesson.forRun(reason, 100f, 0f)
            assertFalse(lesson.isDeath, "$reason should not count as a death")
            assertEquals("", lesson.causeKey)
            assertNull(lesson.hintKey)
        }
    }

    @Test
    fun aCloseRunIsCalledClose() {
        val lesson = DeathLesson.forRun(RunTerminalReason.OXYGEN, 118f, 120f)
        assertEquals(2, lesson.nearMissMeters)
    }

    @Test
    fun aRunThatBeatTheBestIsNotANearMiss() {
        // Getting further than ever is not "so close", it is a new record, and
        // the record banner already says so.
        val lesson = DeathLesson.forRun(RunTerminalReason.OXYGEN, 140f, 120f)
        assertNull(lesson.nearMissMeters)
    }

    @Test
    fun aLongWayShortIsNotCalledClose() {
        val lesson = DeathLesson.forRun(RunTerminalReason.OXYGEN, 40f, 400f)
        assertNull(lesson.nearMissMeters)
    }

    @Test
    fun aFirstEverDiveHasNothingToFallShortOf() {
        val lesson = DeathLesson.forRun(RunTerminalReason.OXYGEN, 30f, 0f)
        assertNull(lesson.nearMissMeters)
    }

    @Test
    fun theNearMissThresholdIsInclusiveAtTheBoundary() {
        val atLimit = DeathLesson.forRun(RunTerminalReason.OXYGEN, 100f, 125f)
        assertEquals(25, atLimit.nearMissMeters)
        val justOver = DeathLesson.forRun(RunTerminalReason.OXYGEN, 100f, 126f)
        assertNull(justOver.nearMissMeters)
    }

    @Test
    fun aNearMissNeverRoundsDownToNothing() {
        // "0 m from your best" reads as a bug, so a sub-metre gap claims one.
        val lesson = DeathLesson.forRun(RunTerminalReason.OXYGEN, 119.6f, 120f)
        assertEquals(1, lesson.nearMissMeters)
    }

    @Test
    fun theBossDeathIsNamedAndHasACounterPlay() {
        val lesson = DeathLesson.forRun(RunTerminalReason.BOSS, 300f, 0f)
        assertEquals("causeBoss", lesson.causeKey)
        assertEquals("hintBoss", lesson.hintKey)
    }

    @Test
    fun aVortexDeathHasNoCounterPlayAndThatIsFine() {
        // Nothing useful to say about a vortex, so it says nothing rather than
        // inventing advice.
        val lesson = DeathLesson.forRun(RunTerminalReason.HAZARD, 100f, 0f, HazardKind.VORTEX)
        assertEquals("causeVortex", lesson.causeKey)
        assertNull(lesson.hintKey)
    }

    @Test
    fun theTwoDeathReasonsWithNoLessonAreNotGivenOne() {
        // Every lethal reason needs a cause. A death with no name is the bug.
        val lethal = listOf(RunTerminalReason.OXYGEN, RunTerminalReason.HAZARD, RunTerminalReason.BOSS)
        for (reason in lethal) {
            assertNotEquals(
                "",
                DeathLesson.forRun(reason, 100f, 0f).causeKey,
                "$reason has no cause",
            )
        }
    }
}
