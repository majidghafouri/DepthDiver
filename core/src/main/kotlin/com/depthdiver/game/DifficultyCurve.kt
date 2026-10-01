package com.depthdiver.game

import com.depthdiver.content.Tuning
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

data class IntervalRange(val minimum: Float, val maximum: Float)

object DifficultyCurve {

    /**
     * The tuning the curve runs on.
     *
     * Set once at boot from the content file, falling back to [Tuning.DEFAULT].
     * Left mutable rather than threaded through every call because these are
     * read in the frame loop and the alternative is a parameter on five methods
     * that a content file then has to reach into.
     */
    @Volatile
    var tuning: Tuning = Tuning.DEFAULT

    /** Point the curve at loaded content. */
    fun use(t: Tuning) {
        tuning = t
    }

    fun scrollSpeed(
        baseMetersPerSecond: Float,
        rampMetersPerSecond: Float,
        depthMeters: Float,
        playerSpeedMetersPerSecond: Float,
    ): Float {
        val base = max(baseMetersPerSecond, 0f)
        val depth = if (depthMeters.isFinite()) depthMeters.coerceAtLeast(0f) else 0f
        val legacy = base + max(rampMetersPerSecond, 0f) * (depth / WORLD_WIDTH_METERS)
        val ceiling = max(playerSpeedMetersPerSecond, 0f) * tuning.scrollCeilingRatio
        return min(legacy, max(ceiling, base))
    }

    fun hazardIntervalRange(
        baseMinimum: Float,
        baseMaximum: Float,
        depthMeters: Float,
        difficultyMultiplier: Float,
    ): IntervalRange {
        val multiplier = max(difficultyMultiplier, 0f)
        val low = max(baseMinimum, 0f) * multiplier
        val high = max(baseMaximum, low) * multiplier
        val ramp = 1f + tuning.hazardRampGain * saturation(depthMeters, tuning.hazardSaturationMeters)
        val floored = min(tuning.hazardIntervalFloor, high / ramp)
        return IntervalRange(max(low / ramp, floored), max(high / ramp, floored))
    }

    fun oxygenDrain(baseDrainPerSecond: Float, depthMeters: Float): Float {
        val base = max(baseDrainPerSecond, 0f)
        return base * (1f + tuning.oxygenDrainGain * saturation(depthMeters, tuning.oxygenSaturationMeters))
    }

    fun saturation(depthMeters: Float, scaleMeters: Float): Float {
        if (!depthMeters.isFinite() || depthMeters <= 0f) return 0f
        if (!scaleMeters.isFinite() || scaleMeters <= 0f) return 1f
        return (1f - exp(-depthMeters / scaleMeters)).coerceIn(0f, 1f)
    }
}