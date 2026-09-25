package com.depthdiver.game

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

data class IntervalRange(val minimum: Float, val maximum: Float)

object DifficultyCurve {

    const val SCROLL_CEILING_RATIO = 0.88f
    const val HAZARD_RAMP_GAIN = 0.9f
    const val HAZARD_SATURATION_METERS = 600f
    const val HAZARD_INTERVAL_FLOOR = 0.45f
    const val OXYGEN_DRAIN_GAIN = 0.55f
    const val OXYGEN_SATURATION_METERS = 500f

    fun scrollSpeed(
        baseMetersPerSecond: Float,
        rampMetersPerSecond: Float,
        depthMeters: Float,
        playerSpeedMetersPerSecond: Float,
    ): Float {
        val base = max(baseMetersPerSecond, 0f)
        val depth = if (depthMeters.isFinite()) depthMeters.coerceAtLeast(0f) else 0f
        val legacy = base + max(rampMetersPerSecond, 0f) * (depth / WORLD_WIDTH_METERS)
        val ceiling = max(playerSpeedMetersPerSecond, 0f) * SCROLL_CEILING_RATIO
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
        val ramp = 1f + HAZARD_RAMP_GAIN * saturation(depthMeters, HAZARD_SATURATION_METERS)
        val floored = min(HAZARD_INTERVAL_FLOOR, high / ramp)
        return IntervalRange(max(low / ramp, floored), max(high / ramp, floored))
    }

    fun oxygenDrain(baseDrainPerSecond: Float, depthMeters: Float): Float {
        val base = max(baseDrainPerSecond, 0f)
        return base * (1f + OXYGEN_DRAIN_GAIN * saturation(depthMeters, OXYGEN_SATURATION_METERS))
    }

    fun saturation(depthMeters: Float, scaleMeters: Float): Float {
        if (!depthMeters.isFinite() || depthMeters <= 0f) return 0f
        if (!scaleMeters.isFinite() || scaleMeters <= 0f) return 1f
        return (1f - exp(-depthMeters / scaleMeters)).coerceIn(0f, 1f)
    }
}