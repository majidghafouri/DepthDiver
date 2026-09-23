package com.depthdiver

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences

/**
 * Daily challenge: a rotating, day-indexed objective (reach a depth / collect
 * pearls / hit a score) with a fixed pearl reward. The objective for a given
 * UTC day is deterministic — same target for every player that day.
 */
object Challenge {

    private val prefs: Preferences
        get() = prefsOverride ?: (retained ?: Gdx.app.getPreferences("depthdiver-challenge").also { retained = it })

    /** One retained instance — see [Profile] note; a fresh wrapper per access would
     *  put() into one editor and flush() another (a silent no-op). */
    private var retained: Preferences? = null

    /** Test seam: lets unit tests inject an isolated in-memory [Preferences]. */
    internal var prefsOverride: Preferences? = null

    const val REWARD = 40

    enum class Kind { Depth, Pearls, Score }

    class Active(val kind: Kind, val target: Int, val day: Int) {
        fun met(depth: Float, runPearls: Int, score: Int): Boolean = when (kind) {
            Kind.Depth -> depth >= target
            Kind.Pearls -> runPearls >= target
            Kind.Score -> score >= target
        }

        /** Readable objective using lifetime-best stats for the profile screen. */
        fun summary(bestDepth: Float, lifetimePearls: Int, bestScore: Int): String = when (kind) {
            Kind.Depth -> "${Strings.t("chReach")} $target m (${bestDepth.toInt()} / $target)"
            Kind.Pearls -> "${Strings.t("chCollect")} $target (${lifetimePearls} / $target)"
            Kind.Score -> "${Strings.t("chScore")} $target (${bestScore} / $target)"
        }
    }

    /** Deterministic per-day objective derived from the UTC day index. */
    fun activeFor(day: Int): Active = when (day % 3) {
        0 -> Active(Kind.Depth, 60 + (day % 4) * 15, day)
        1 -> Active(Kind.Pearls, 15 + (day % 4) * 5, day)
        else -> Active(Kind.Score, 300 + (day % 3) * 200, day)
    }

    fun claimedFor(active: Active): Boolean = prefs.getBoolean("challenge.${active.day}", false)

    fun claim(active: Active) {
        val p = prefs
        p.putBoolean("challenge.${active.day}", true)
        p.flush()
    }
}