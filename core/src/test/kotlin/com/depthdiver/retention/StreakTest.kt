package com.depthdiver.retention

import com.depthdiver.run.RunTerminalReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Streaks.
 *
 * The interesting part of a streak is not counting up, it is everything that can
 * go wrong around the counting, so that is what most of these are about: a day
 * counted twice, a clock that jumps, a reward paid twice, and -- the one that
 * decides whether this is a good mechanic -- anything the player loses.
 */
class StreakTest {

    private val done = RunTerminalReason.COMPLETED

    /** Play [days] consecutive days of completed runs, stopping at each return. */
    private fun playFrom(state: StreakState, days: List<Int>): StreakState {
        var s = state
        for (day in days) s = Streak.apply(s, day, Streak.update(s, day, done))
        return s
    }

    @Test
    fun theFirstDiveStartsAStreak() {
        val update = Streak.update(StreakState.NONE, 100, done)
        val advanced = assertIs<StreakUpdate.Advanced>(update)
        assertEquals(1, advanced.streak)
        assertTrue(advanced.isNewBest)
        assertNull(advanced.milestone, "day one is not a milestone")
    }

    @Test
    fun consecutiveDaysAddUp() {
        val state = playFrom(StreakState.NONE, listOf(100, 101, 102, 103))
        assertEquals(4, state.current)
        assertEquals(4, state.best)
        assertEquals(103, state.lastDay)
    }

    @Test
    fun severalRunsInOneDayCountOnce() {
        // This is the whole reason the update is idempotent: the settlement path
        // can be replayed, and a streak that counted the same day twice would be
        // worth farming by finishing two runs before midnight.
        var state = playFrom(StreakState.NONE, listOf(100))
        repeat(5) {
            val update = Streak.update(state, 100, done)
            assertIs<StreakUpdate.Unchanged>(update)
            state = Streak.apply(state, 100, update)
        }
        assertEquals(1, state.current)
    }

    @Test
    fun aMissedDayBreaksTheStreak() {
        val before = playFrom(StreakState.NONE, listOf(100, 101, 102, 103))
        val update = Streak.update(before, 110, done)
        val reset = assertIs<StreakUpdate.Reset>(update)
        assertEquals(4, reset.from)
        assertEquals(1, reset.streak)
    }

    @Test
    fun aMissedDayTakesNothingAway() {
        // The design decision this whole feature rests on. A streak that
        // confiscates pearls already earned teaches the player the game holds
        // something from them, and they stop playing.
        val before = playFrom(StreakState.NONE, listOf(100, 101, 102))
        assertEquals(3, before.current)
        val after = Streak.apply(before, 110, Streak.update(before, 110, done))
        assertEquals(1, after.current, "the count restarts")
        assertEquals(3, after.best, "but the best ever is kept")
    }

    @Test
    fun theBestStreakSurvivesRebuildingFromNothing() {
        var state = playFrom(StreakState.NONE, listOf(100, 101, 102, 103, 104))
        assertEquals(5, state.best)
        state = Streak.apply(state, 200, Streak.update(state, 200, done))
        state = playFrom(state, listOf(201, 202))
        assertEquals(3, state.current, "200, 201 and 202 are three days")
        assertEquals(5, state.best)
    }

    @Test
    fun aClockThatWentBackwardsDoesNotResetAnything() {
        // A time zone change or a corrected device date must not be able to cost
        // a player a streak. The game should not be able to do that to them.
        val before = playFrom(StreakState.NONE, listOf(1000, 1001, 1002))
        val update = Streak.update(before, 900, done)
        assertIs<StreakUpdate.Unchanged>(update)
        assertEquals(3, Streak.apply(before, 900, update).current)
    }

    @Test
    fun aSameDayRunAtTheSameDayIsStillJustOneDay() {
        val before = playFrom(StreakState.NONE, listOf(500))
        assertIs<StreakUpdate.Unchanged>(Streak.update(before, 500, done))
    }

    @Test
    fun quittingDoesNotCount() {
        // Crediting a dive abandoned the moment it started would pay for showing
        // up, which is not the habit the streak is meant to build.
        for (reason in listOf(
            RunTerminalReason.ABANDONED,
            RunTerminalReason.MANUAL,
            RunTerminalReason.UNKNOWN,
        )) {
            assertIs<StreakUpdate.Unchanged>(Streak.update(StreakState.NONE, 100, reason))
            assertTrue(!Streak.countsTowardStreak(reason), "$reason should not count")
        }
    }

    @Test
    fun dyingStillCountsBecauseThePlayerStillDived() {
        // A death is a completed attempt. Rewarding survival instead would mean
        // the streak punished the people having a hard time, which is backwards.
        for (reason in listOf(
            RunTerminalReason.OXYGEN,
            RunTerminalReason.HAZARD,
            RunTerminalReason.BOSS,
        )) {
            assertTrue(Streak.countsTowardStreak(reason), "$reason should count")
        }
    }

    @Test
    fun aMilestonePaysOnceAndOnlyOnce() {
        // Paid on a new personal best, so rebuilding a broken streak cannot farm
        // the early milestones over and over.
        var state = playFrom(StreakState.NONE, listOf(300, 301))
        val third = Streak.update(state, 302, done)
        val paid = assertIs<StreakUpdate.Advanced>(third)
        assertEquals(3, paid.milestone)
        assertEquals(Streak.MILESTONES[3], paid.reward)
        assertTrue(paid.reward > 0)

        // break it, then rebuild past three again
        state = Streak.apply(state, 302, third)
        state = Streak.apply(state, 400, Streak.update(state, 400, done))
        state = playFrom(state, listOf(401))
        val again = Streak.update(state, 402, done)
        val secondTime = assertIs<StreakUpdate.Advanced>(again)
        assertNull(secondTime.milestone, "the 3-day milestone must not pay twice")
        assertEquals(0, secondTime.reward)
    }

    @Test
    fun noMilestonePaysBeforeItsDay() {
        var state = StreakState.NONE
        for (day in 300..301) {
            val update = Streak.update(state, day, done)
            assertEquals(0, assertIs<StreakUpdate.Advanced>(update).reward, "paid early on day ${day - 299}")
            state = Streak.apply(state, day, update)
        }
        // and day three, the first milestone, does pay
        val third = Streak.update(state, 302, done)
        assertTrue(assertIs<StreakUpdate.Advanced>(third).reward > 0, "day 3 should pay")
    }

    @Test
    fun everyMilestonePaysSomethingWorthHaving() {
        // A milestone nobody would notice is not a milestone.
        Streak.MILESTONES.forEach { (day, reward) ->
            assertTrue(reward >= 25, "the $day-day milestone pays only $reward")
        }
        // And they should escalate, or the big ones are not worth reaching for.
        val rewards = Streak.MILESTONES.values.toList()
        rewards.forEachIndexed { i, reward ->
            if (i == 0) return@forEachIndexed
            assertTrue(reward > rewards[i - 1], "milestone ${i + 1} pays no more than the one before")
        }
    }

    @Test
    fun theNextMilestoneIsKnownSoTheProfileCanNameIt() {
        assertEquals(3, Streak.nextMilestone(0))
        assertEquals(3, Streak.nextMilestone(1))
        assertEquals(7, Streak.nextMilestone(3))
        assertEquals(7, Streak.nextMilestone(6))
        assertEquals(14, Streak.nextMilestone(7))
        assertNull(Streak.nextMilestone(100), "there is nothing after 100")
    }

    @Test
    fun aLongAbsenceRestartsAtOneNotZero() {
        // Restarting at zero would need a second dive tomorrow before the counter
        // moves at all, which feels broken rather than motivating.
        val before = playFrom(StreakState.NONE, listOf(100, 101))
        val after = Streak.apply(before, 5000, Streak.update(before, 5000, done))
        assertEquals(1, after.current)
    }

    @Test
    fun aFreshProfileHasNoStreak() {
        val state = StreakState.NONE
        assertEquals(0, state.current)
        assertEquals(0, state.best)
        assertTrue(!state.hasDived)
    }

    @Test
    fun milestoneDaysAreDistinctAndAscending() {
        val days = Streak.MILESTONES.keys.toList()
        assertEquals(days.sorted(), days, "milestone days should ascend")
        assertEquals(days.size, days.toSet().size, "a milestone day is listed twice")
    }
}
