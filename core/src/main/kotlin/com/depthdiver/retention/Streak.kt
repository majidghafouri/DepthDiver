package com.depthdiver.retention

import com.depthdiver.run.RunTerminalReason

/**
 * A consecutive-days diving streak.
 *
 * Streak systems are usually written to be a little bit cruel: miss a day and
 * you lose the reward you already earned, so the mechanic teaches the player
 * that the game is holding something from them. This one does not do that. A
 * broken streak resets the count and nothing else -- pearls already granted stay
 * granted, and the best streak is kept forever, so the number a player reached
 * is never retroactively erased by a week of not playing.
 *
 * The pull has to come from somewhere, so it comes from the milestones: a
 * streak pays out at 3, 7, 14, 30 and 100 days, and each pays **once, ever**.
 * That is enforced by paying only on a new personal best, which is also why
 * rebuilding a broken streak does not farm the early milestones again.
 */
data class StreakState(
    /** Consecutive days, counting today if a qualifying run has happened. */
    val current: Int = 0,
    /** Longest run of consecutive days ever achieved. Never decreases. */
    val best: Int = 0,
    /** Day number of the last counted dive; 0 means never. */
    val lastDay: Int = 0,
) {
    val hasDived: Boolean get() = lastDay > 0

    companion object {
        val NONE = StreakState()
    }
}

/** What recording a run did to the streak. */
sealed interface StreakUpdate {
    /** Nothing moved: a second run the same day, an abandoned run, or a clock that went backwards. */
    data object Unchanged : StreakUpdate

    /**
     * The streak grew.
     *
     * [reward] is zero unless this run crossed a milestone for the first time
     * ever, and [milestone] is the day count that paid.
     */
    data class Advanced(
        val streak: Int,
        val milestone: Int?,
        val reward: Int,
        val isNewBest: Boolean,
    ) : StreakUpdate

    /**
     * The streak broke and restarted at one.
     *
     * [from] is what it was before. Nothing is taken away: only the count moves.
     */
    data class Reset(val from: Int, val streak: Int = 1) : StreakUpdate
}

object Streak {

    /** Day counts that pay out, and what they pay. */
    val MILESTONES: Map<Int, Int> = linkedMapOf(
        3 to 25,
        7 to 60,
        14 to 150,
        30 to 400,
        100 to 1500,
    )

    /**
     * Record a run against the streak.
     *
     * [today] is a day number, not a date, so it is only ever compared to
     * another day number.
     */
    fun update(state: StreakState, today: Int, reason: RunTerminalReason): StreakUpdate {
        // Only completed runs count. Crediting a dive that was abandoned the
        // moment it started would pay for showing up, which is not the habit
        // the streak is trying to build.
        if (!countsTowardStreak(reason)) return StreakUpdate.Unchanged

        // The clock went backwards -- a time zone change, or someone fixed the
        // device date. Resetting here would let a player lose a streak by
        // flying somewhere, which is not something the game should be able to do.
        if (state.lastDay > today) return StreakUpdate.Unchanged

        // Already counted today. Several runs in one day is one day.
        if (state.lastDay == today) return StreakUpdate.Unchanged

        if (state.hasDived && today != state.lastDay + 1) {
            // A whole day, or more, went by unrecorded.
            return StreakUpdate.Reset(from = state.current)
        }

        val next = state.current + 1
        val newBest = next > state.best
        // A milestone pays only when it is a new personal best, so it can be
        // earned exactly once however often the streak is rebuilt.
        val milestone = MILESTONES[next]?.takeIf { newBest }
        return StreakUpdate.Advanced(
            streak = next,
            milestone = if (milestone != null) next else null,
            reward = milestone ?: 0,
            isNewBest = newBest,
        )
    }

    /**
     * The state to persist after [update].
     *
     * Kept next to [update] so the two cannot disagree about what a result means.
     */
    fun apply(state: StreakState, today: Int, update: StreakUpdate): StreakState = when (update) {
        StreakUpdate.Unchanged -> state
        is StreakUpdate.Reset -> StreakState(
            current = update.streak,
            best = maxOf(state.best, update.streak),
            lastDay = today,
        )

        is StreakUpdate.Advanced -> StreakState(
            current = update.streak,
            best = maxOf(state.best, update.streak),
            lastDay = today,
        )
    }

    /** A completed run counts. Quitting, and a stale run recovered at startup, do not. */
    fun countsTowardStreak(reason: RunTerminalReason): Boolean = when (reason) {
        RunTerminalReason.COMPLETED,
        RunTerminalReason.OXYGEN,
        RunTerminalReason.HAZARD,
        RunTerminalReason.BOSS,
        -> true

        RunTerminalReason.ABANDONED,
        RunTerminalReason.MANUAL,
        RunTerminalReason.UNKNOWN,
        -> false
    }

    /**
     * The next milestone above [streak], or null if they are all behind them.
     *
     * For the profile's "3 days to 7" line, which is the part that makes the
     * next dive feel like it is going somewhere.
     */
    fun nextMilestone(streak: Int): Int? = MILESTONES.keys.firstOrNull { it > streak }
}
