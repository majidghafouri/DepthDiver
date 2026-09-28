package com.depthdiver.analytics

import com.depthdiver.common.Strings

/**
 * What a player is told in the first few seconds of their first dives.
 *
 * The first run decides whether there is a second one, and the opening was
 * "descend, avoid, collect" with no guidance and nothing guaranteed. So:
 *
 *  - the first pickup of a run is always a pearl, not a coin flip, which puts a
 *    win roughly two seconds in;
 *  - the first depth milestone comes at 15m rather than 50m, so the first
 *    reward is inside the depth most players actually reach;
 *  - a first mutation pick is offered at 15m, before the run is likely to end,
 *    so the feature that makes runs differ is seen on dive one;
 *  - two lines of guidance, for the first couple of dives.
 *
 * Every one of these is a constant rather than a behaviour, so the numbers can
 * be argued about without reading the spawn code.
 */
object Opening {

    /** The first depth reward, in metres. Was 50. */
    const val FIRST_MILESTONE_METERS = 15f

    /** Milestones after the first, in metres. */
    const val MILESTONE_STEP_METERS = 50f

    /** The first mutation pick. Was 120m, past where a first run tends to end. */
    const val FIRST_PICK_METERS = 15f

    /** Later picks. */
    const val PICK_STEP_METERS = 90f

    /** How long each hint stays up, in seconds. */
    const val HINT_SECONDS = 4.5f

    /**
     * How many dives get the hints.
     *
     * The hints show while `dives <= HINT_RUNS`. `dives` counts *finished*
     * dives, so this is the first two dives in practice: during the very first
     * one it is 0, and during the second it is 1. A player who has dived exactly
     * once has barely learned the controls, and showing the hint once and never
     * again means the player who most needed it is the one who stops seeing it.
     */
    const val HINT_RUNS = 1

    /**
     * Whether this is the player's very first dive.
     *
     * Read from the dive count rather than tracked separately, so a player who
     * wipes their progress gets the introduction again -- which is correct for
     * someone who has forgotten everything.
     */
    fun isFirstDive(dives: Int): Boolean = dives <= 0

    /**
     * The two lines, or nothing once the player has settled in.
     *
     * Short on purpose. A wall of text over the opening of a game about a quiet
     * ocean is the wrong first impression, and anything long enough to need
     * skipping is long enough that nobody reads it.
     */
    fun hintsFor(dives: Int): List<String> =
        if (dives <= HINT_RUNS) listOf(Strings.t("hintSteer"), Strings.t("hintPearls")) else emptyList()

    /** 1 at the start of a hint's life, 0 once it has faded. */
    fun hintAlpha(secondsRemaining: Float): Float =
        if (secondsRemaining <= 0f) 0f else (secondsRemaining / HINT_SECONDS).coerceIn(0f, 1f)
}
