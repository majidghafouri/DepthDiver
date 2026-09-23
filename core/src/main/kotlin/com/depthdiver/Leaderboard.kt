package com.depthdiver

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences

/**
 * Local-first leaderboard (roadmap item: "High-score leaderboard").
 *
 * Storage is Preferences-backed and 100% offline — the top 5 runs are kept
 * in `depthdiver-leaderboard` prefs under "score.0".."score.4" (and matching
 * "depth.0".."depth.4"), newest-wins at equal score, sorted descending.
 *
 * CLOUD SEAM: to ship an online leaderboard later, introduce a class
 * implementing [LeaderboardService] (e.g. `CloudLeaderboard` backed by your
 * chosen backend) and swap the singleton in [Leaderboard.instance]. The
 * interface below is the only thing the game ever talks to, so no gameplay
 * code changes are required for the migration — see README roadmap for the
 * documented seam.
 */
interface LeaderboardService {
    /** @return top entries, best first. */
    fun top(): List<LeaderboardEntry>
    /** Record a finished run; returns the updated top list. */
    fun submit(score: Int, depth: Float): List<LeaderboardEntry>
    /** True if [score]/[depth] would make the local top 5. */
    fun qualifies(score: Int, depth: Float): Boolean
}

data class LeaderboardEntry(val score: Int, val depth: Float, val muted: Boolean = false)

object Leaderboard : LeaderboardService {

    private const val MAX_ENTRIES = 5

    fun instance(): LeaderboardService = this

    /** Test seam: lets unit tests inject an isolated in-memory [Preferences]. */
    internal var prefsOverride: Preferences? = null

    private val prefs: Preferences
        get() = prefsOverride ?: Gdx.app.getPreferences("depthdiver-leaderboard")

    override fun top(): List<LeaderboardEntry> {
        val entries = ArrayList<LeaderboardEntry>(MAX_ENTRIES)
        for (i in 0 until MAX_ENTRIES) {
            val score = prefs.getInteger("score.$i", -1)
            if (score < 0) break
            entries.add(LeaderboardEntry(score, prefs.getFloat("depth.$i", 0f)))
        }
        return entries
    }

    override fun qualifies(score: Int, depth: Float): Boolean {
        if (score <= 0) return false
        val current = top()
        if (current.size < MAX_ENTRIES) return true
        return score > current.last().score
    }

    override fun submit(score: Int, depth: Float): List<LeaderboardEntry> {
        if (!qualifies(score, depth)) return top()
        val merged = (top() + LeaderboardEntry(score, depth))
            .sortedWith(compareByDescending<LeaderboardEntry> { it.score }
                .thenByDescending { it.depth })
            .take(MAX_ENTRIES)
        for (i in merged.indices) {
            prefs.putInteger("score.$i", merged[i].score)
            prefs.putFloat("depth.$i", merged[i].depth)
        }
        prefs.flush()
        return merged
    }
}
