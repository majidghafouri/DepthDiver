package com.depthdiver.analytics

import com.depthdiver.run.RunTerminalReason
import kotlin.math.roundToInt

/** How a run ended, as a shape the funnel can be computed over. */
enum class RunOutcome {
    OXYGEN,
    HAZARD,
    COMPLETED,
    ABANDONED,
    ;

    companion object {
        fun of(reason: RunTerminalReason?): RunOutcome = when (reason) {
            RunTerminalReason.OXYGEN -> OXYGEN
            RunTerminalReason.HAZARD -> HAZARD
            RunTerminalReason.COMPLETED -> COMPLETED
            RunTerminalReason.ABANDONED, null -> ABANDONED
            // A boss kill, a manual quit and an unreadable reason are all "the
            // player stopped", which is the only thing the funnel needs to know.
            else -> ABANDONED
        }
    }
}

data class RunSample(
    val depthMeters: Float,
    val score: Int,
    val seconds: Float,
    val outcome: RunOutcome,
    val difficulty: Int,
    val mutations: List<String> = emptyList(),
)

/**
 * What every run looks like, folded into counts a person can act on.
 *
 * Deliberately local and deliberately aggregate. This is not telemetry: nothing
 * leaves the device, there is no identifier, and there is no per-run history
 * beyond the counts below. That is the only version of this that is worth
 * building for a game about a quiet ocean, and it is enough for the question it
 * exists to answer -- *where do players actually stop?*
 */
data class RunAnalyticsSummary(
    val runs: Int,
    val deathsByOxygen: Int,
    val deathsByHazard: Int,
    val completions: Int,
    val medianDepth: Float,
    val medianSeconds: Float,
    val bestDepth: Float,
    val bestScore: Int,
    /** Share of runs reaching each depth, as a fraction 0..1. */
    val reached: Map<Int, Double>,
) {
    val medianRunSeconds: Float get() = medianSeconds

    /** Where the typical run stops, to the nearest 10m. The single most useful
     *  number here: if it is 40m, the first forty seconds is the problem. */
    val typicalStopMeters: Int get() = (medianDepth / 10f).roundToInt() * 10

    fun reachedFraction(atMeters: Int): Double = reached[atMeters] ?: 0.0
}

/** Depth markers the funnel reports on, chosen to bracket the biomes. */
val DEPTH_MARKS = listOf(25, 50, 100, 150, 200, 300, 500)

class RunAnalytics(
    /** Kept as a capped ring of recent samples: enough for a median, bounded in size. */
    private val capacity: Int = 500,
    initial: List<RunSample> = emptyList(),
) {
    private val samples = ArrayDeque<RunSample>()

    init {
        initial.takeLast(capacity).forEach { samples.addLast(it) }
    }

    fun record(sample: RunSample) {
        if (samples.size >= capacity) samples.removeFirst()
        samples.addLast(sample)
    }

    fun size(): Int = samples.size

    fun all(): List<RunSample> = samples.toList()

    fun summary(): RunAnalyticsSummary {
        val all = samples.toList()
        if (all.isEmpty()) return EMPTY
        return RunAnalyticsSummary(
            runs = all.size,
            deathsByOxygen = all.count { it.outcome == RunOutcome.OXYGEN },
            deathsByHazard = all.count { it.outcome == RunOutcome.HAZARD },
            completions = all.count { it.outcome == RunOutcome.COMPLETED },
            medianDepth = median(all.map { it.depthMeters }),
            medianSeconds = median(all.map { it.seconds }),
            bestDepth = all.maxOf { it.depthMeters },
            bestScore = all.maxOf { it.score },
            reached = DEPTH_MARKS.associateWith { mark ->
                if (all.isEmpty()) 0.0 else all.count { it.depthMeters >= mark }.toDouble() / all.size
            },
        )
    }

    /**
     * How often each mutation was taken, against how often it was offered.
     *
     * A mutation that is always picked was a good offer; one that is never picked
     * is either badly balanced or badly described, and neither is visible from
     * the counters alone.
     */
    fun mutationPopularity(taken: Map<String, Int>, offered: Map<String, Int>): Map<String, Double> =
        taken.mapValues { (id, count) ->
            val seen = offered[id] ?: 0
            if (seen == 0) 0.0 else count.toDouble() / seen
        }

    /** Depth reached, bucketed, so a histogram of the funnel can be printed. */
    fun depthHistogram(bucketMeters: Int = 25): Map<Int, Int> {
        val out = sortedMapOf<Int, Int>()
        for (s in samples) {
            val bucket = ((s.depthMeters / bucketMeters).toInt() * bucketMeters).coerceAtLeast(0)
            out[bucket] = (out[bucket] ?: 0) + 1
        }
        return out
    }

    private fun median(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2f
    }

    companion object {
        val EMPTY = RunAnalyticsSummary(0, 0, 0, 0, 0f, 0f, 0f, 0, emptyMap())
    }
}
