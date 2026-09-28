package com.depthdiver.analytics

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences
import kotlin.math.roundToInt

/**
 * Turns the aggregate into something readable, and persists the raw counts.
 *
 * The report is plain text on purpose. A chart on a phone is a chart you cannot
 * read in a bug report, and the person who needs this is whoever is deciding what
 * to build next, usually not while holding the game.
 */
object AnalyticsReport {

    private const val KEY_RUNS = "analytics.runs"
    private const val KEY_OXYGEN = "analytics.deathsOxygen"
    private const val KEY_HAZARD = "analytics.deathsHazard"
    private const val KEY_DONE = "analytics.completions"
    private const val KEY_DEPTH_SUM = "analytics.depthSum"
    private const val KEY_DEPTH_MAX = "analytics.depthMax"
    private const val KEY_SCORE_MAX = "analytics.scoreMax"
    private const val KEY_SECONDS_SUM = "analytics.secondsSum"
    private const val KEY_MUTATIONS = "analytics.mutations"

    private var prefs: Preferences? = null
    private var retained: Preferences? = null

    private fun prefs(): Preferences =
        prefs ?: retained ?: Gdx.app.getPreferences("depthdiver-analytics").also { retained = it }

    /** Point the analytics at a different store, for tests. */
    fun useOverride(override: Preferences?) {
        prefs = override
    }

    fun reset() {
        val p = prefs()
        p.clear()
        p.flush()
    }

    fun record(sample: RunSample) {
        val p = prefs()
        p.putInteger(KEY_RUNS, p.getInteger(KEY_RUNS, 0) + 1)
        p.putInteger(
            KEY_OXYGEN,
            p.getInteger(KEY_OXYGEN, 0) + if (sample.outcome == RunOutcome.OXYGEN) 1 else 0,
        )
        p.putInteger(
            KEY_HAZARD,
            p.getInteger(KEY_HAZARD, 0) + if (sample.outcome == RunOutcome.HAZARD) 1 else 0,
        )
        p.putInteger(
            KEY_DONE,
            p.getInteger(KEY_DONE, 0) + if (sample.outcome == RunOutcome.COMPLETED) 1 else 0,
        )
        p.putFloat(KEY_DEPTH_SUM, p.getFloat(KEY_DEPTH_SUM, 0f) + sample.depthMeters)
        p.putFloat(KEY_SECONDS_SUM, p.getFloat(KEY_SECONDS_SUM, 0f) + sample.seconds)
        p.putFloat(KEY_DEPTH_MAX, maxOf(p.getFloat(KEY_DEPTH_MAX, 0f), sample.depthMeters))
        p.putInteger(KEY_SCORE_MAX, maxOf(p.getInteger(KEY_SCORE_MAX, 0), sample.score))
        // Running counts, not a log: a mutation is one key and one integer, so
        // this cannot grow without bound however many runs are played.
        val all = p.getString(KEY_MUTATIONS, "")
            .split(',')
            .mapNotNull { it.split(':').takeIf { parts -> parts.size == 2 } }
            .associate { (id, n) -> id to (n.toIntOrNull() ?: 0) }
            .toMutableMap()
        sample.mutations.forEach { all[it] = (all[it] ?: 0) + 1 }
        p.putString(KEY_MUTATIONS, all.entries.joinToString(",") { "${it.key}:${it.value}" })
        p.flush()
    }

    fun runs(): Int = prefs().getInteger(KEY_RUNS, 0)

    fun bestDepth(): Float = prefs().getFloat(KEY_DEPTH_MAX, 0f)

    fun bestScore(): Int = prefs().getInteger(KEY_SCORE_MAX, 0)

    fun mutationCounts(): Map<String, Int> {
        val raw = prefs().getString(KEY_MUTATIONS, "")
        if (raw.isBlank()) return emptyMap()
        return raw.split(',')
            .mapNotNull { entry ->
                val parts = entry.split(':')
                if (parts.size != 2) null else parts[0] to (parts[1].toIntOrNull() ?: 0)
            }
            .toMap()
    }

    /**
     * The summary. Built from running totals rather than stored samples, so it
     * is exact for the counts and an average rather than a median for the
     * depths -- an honest trade for never keeping a history.
     */
    fun summary(): RunAnalyticsSummary {
        val n = runs()
        if (n == 0) return RunAnalytics.EMPTY
        val avgDepth = prefs().getFloat(KEY_DEPTH_SUM, 0f) / n
        return RunAnalyticsSummary(
            runs = n,
            deathsByOxygen = prefs().getInteger(KEY_OXYGEN, 0),
            deathsByHazard = prefs().getInteger(KEY_HAZARD, 0),
            completions = prefs().getInteger(KEY_DONE, 0),
            medianDepth = avgDepth,
            medianSeconds = prefs().getFloat(KEY_SECONDS_SUM, 0f) / n,
            bestDepth = bestDepth(),
            bestScore = bestScore(),
            // Running totals cannot produce an exact funnel: "how many runs
            // passed 100m" is not recoverable from a sum and a count. Rather
            // than invent a number, the persisted report leaves it out; an exact
            // funnel comes from the in-memory RunAnalytics while runs are watched.
            reached = emptyMap(),
        )
    }

    /** Plain text, for a bug report or a commit message. */
    fun render(): String {
        val n = runs()
        if (n == 0) return "No runs recorded yet."
        val p = prefs()
        val avgDepth = p.getFloat(KEY_DEPTH_SUM, 0f) / n
        val avgSeconds = p.getFloat(KEY_SECONDS_SUM, 0f) / n
        return buildString {
            appendLine("DepthDiver run report")
            appendLine("  runs            $n")
            appendLine("  average depth   ${avgDepth.roundToInt()} m")
            appendLine("  average length  ${avgSeconds.roundToInt()} s")
            appendLine("  best depth      ${bestDepth().roundToInt()} m")
            appendLine("  best score      ${bestScore()}")
            appendLine("  ended: oxygen   ${p.getInteger(KEY_OXYGEN, 0)}")
            appendLine("  ended: hazard   ${p.getInteger(KEY_HAZARD, 0)}")
            appendLine("  completed       ${p.getInteger(KEY_DONE, 0)}")
            val mutations = mutationCounts()
            if (mutations.isNotEmpty()) {
                appendLine("  mutations taken:")
                mutations.entries.sortedByDescending { it.value }.forEach {
                    appendLine("    ${it.key.padEnd(12)} ${it.value}")
                }
            }
        }
    }
}
