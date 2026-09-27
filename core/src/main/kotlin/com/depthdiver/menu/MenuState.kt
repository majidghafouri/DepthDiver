package com.depthdiver.menu

import com.depthdiver.game.UiScale

/**
 * Everything the menu renderer needs that is not already in [MenuGeometry].
 *
 * The persisted stores (Profile, Achievements, Challenge, Leaderboard) are read
 * directly by the renderer: they are already singletons behind a prefs seam, and
 * threading a snapshot of every achievement through here would be a lot of
 * copying for no isolation, since the renderer draws the screen and nothing
 * else. What does get passed in is the state the *game* owns, because that is
 * genuinely per-run and has no store behind it.
 */
data class MenuState(
    val scale: UiScale,
    val menuTime: Float,
    val muted: Boolean,
    val difficulty: Int,
    // Run-owned stats. The profile screen shows these rather than the persisted
    // copies, so a value earned this run shows up immediately.
    val bestDepth: Float,
    val bestScore: Int,
    val bestRunPearls: Int,
)
