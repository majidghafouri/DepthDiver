package com.depthdiver.game

import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

internal data class FairnessPlacement(
    val centerXMeters: Float,
    val safeClearanceMeters: Float,
)

internal data class FairnessEelSpawn(
    val dir: Int,
    val startXMeters: Float,
    val baseYMeters: Float,
    val speedMetersPerSecond: Float,
    val reactionSeconds: Float,
)

internal class ProceduralFairness(
    private val worldWidthMeters: Float = WORLD_WIDTH_METERS,
    private val playerRadiusMeters: Float = PLAYER_RADIUS_METERS,
) {
    private data class RecentHazard(val centerXMeters: Float, val halfWidthMeters: Float)

    private var random = Random(0L)
    private val recentHazards = mutableListOf<RecentHazard>()
    private var lastPickupCenter = Float.NaN
    private var lastOxygenTankDepth = -Float.MAX_VALUE
    private var lastEelBaseY = Float.NaN

    init {
        require(worldWidthMeters.isFinite() && worldWidthMeters > 0f)
        require(playerRadiusMeters.isFinite() && playerRadiusMeters > 0f)
    }

    fun reset(seed: Long) {
        random = Random(seed)
        recentHazards.clear()
        lastPickupCenter = Float.NaN
        lastOxygenTankDepth = -Float.MAX_VALUE
        lastEelBaseY = Float.NaN
    }

    fun unit(): Float = random.nextFloat()

    fun range(minimum: Float, maximum: Float): Float {
        if (!minimum.isFinite() || !maximum.isFinite() || maximum <= minimum) return minimum
        return minimum + random.nextFloat() * (maximum - minimum)
    }

    fun hazardCenter(playerXMeters: Float, widthMeters: Float, depthMeters: Float): FairnessPlacement {
        val halfWidth = (widthMeters / 2f).coerceAtLeast(0f)
        val lower = halfWidth.coerceAtMost(worldWidthMeters / 2f)
        val upper = worldWidthMeters - halfWidth
        if (upper <= lower) return FairnessPlacement(worldWidthMeters / 2f, 0f)

        val clearance = safeClearance(depthMeters)
        val safeFromPlayer = playerRadiusMeters + clearance + halfWidth
        val candidate = findCandidate(lower, upper) { value ->
            abs(value - playerXMeters) >= safeFromPlayer && isSeparatedFromRecent(value, halfWidth)
        } ?: fallbackCandidate(lower, upper, playerXMeters, safeFromPlayer, halfWidth)
        recordHazard(candidate, halfWidth)
        return FairnessPlacement(candidate, clearance)
    }

    fun pickupCenter(playerXMeters: Float, widthMeters: Float, depthMeters: Float): Float {
        val halfWidth = (widthMeters / 2f).coerceAtLeast(0f)
        val lower = halfWidth.coerceAtMost(worldWidthMeters / 2f)
        val upper = worldWidthMeters - halfWidth
        if (upper <= lower) return worldWidthMeters / 2f

        val maxDistance = min(14f, worldWidthMeters / 2f)
        val candidate = findCandidate(lower, upper) { value ->
            abs(value - playerXMeters) <= maxDistance &&
                isSeparatedFromRecent(value, halfWidth) &&
                (lastPickupCenter.isNaN() || abs(value - lastPickupCenter) >= 2f)
        } ?: playerXMeters.coerceIn(lower, upper)
        lastPickupCenter = candidate
        return candidate
    }

    fun eelSpawn(
        playerXMeters: Float,
        playerYMeters: Float,
        widthMeters: Float,
        topYMeters: Float,
        bandOffsetMinimum: Float,
        bandOffsetMaximum: Float,
        minBandGapMeters: Float,
        minPlayerGapMeters: Float,
        minReactionSeconds: Float,
        baseSpeedMetersPerSecond: Float,
        depthMeters: Float,
    ): FairnessEelSpawn {
        val halfWidth = (widthMeters / 2f).coerceAtLeast(0f)
        val leftEntry = -halfWidth + 0.25f
        val rightEntry = worldWidthMeters - 0.25f + halfWidth
        val leftTravel = (playerXMeters - leftEntry).coerceAtLeast(0f)
        val rightTravel = (rightEntry - playerXMeters).coerceAtLeast(0f)
        val dir = when {
            leftTravel > rightTravel -> 1
            rightTravel > leftTravel -> -1
            else -> if (unit() < 0.5f) 1 else -1
        }
        val startX = if (dir == 1) leftEntry else rightEntry
        val travel = if (dir == 1) leftTravel else rightTravel
        val depthFactor = (depthMeters / 400f).coerceIn(0f, 1f)
        val depthScaledSpeed = baseSpeedMetersPerSecond / (1f + depthFactor * 0.25f)
        val reactionCap = if (minReactionSeconds > 0f) travel / minReactionSeconds else Float.MAX_VALUE
        val speed = min(depthScaledSpeed, reactionCap).coerceAtLeast(0.5f)
        val band = eelBand(topYMeters, bandOffsetMinimum, bandOffsetMaximum, minBandGapMeters, minPlayerGapMeters, playerYMeters)
        lastEelBaseY = band
        return FairnessEelSpawn(
            dir = dir,
            startXMeters = startX,
            baseYMeters = band,
            speedMetersPerSecond = speed,
            reactionSeconds = if (speed > 0f) travel / speed else 0f,
        )
    }

    fun recordHazard(centerXMeters: Float, widthMeters: Float) {
        recentHazards += RecentHazard(centerXMeters, (widthMeters / 2f).coerceAtLeast(0f))
        while (recentHazards.size > 4) recentHazards.removeAt(0)
    }

    fun shouldForceOxygenTank(oxygenFraction: Float, depthMeters: Float): Boolean {
        if (!oxygenFraction.isFinite() || !depthMeters.isFinite()) return false
        return oxygenFraction <= 0.4f && depthMeters - lastOxygenTankDepth >= 12f
    }

    fun noteOxygenTank(depthMeters: Float) {
        if (depthMeters.isFinite()) lastOxygenTankDepth = depthMeters
    }

    fun hazardInterval(baseMinimum: Float, baseMaximum: Float, depthMeters: Float, difficultyMultiplier: Float): Float {
        val depthFactor = (depthMeters / WORLD_WIDTH_METERS).coerceAtLeast(0f)
        val ramp = 1f + depthFactor * 0.6f
        return range(baseMinimum, baseMaximum) * difficultyMultiplier.coerceAtLeast(0f) / ramp
    }

    fun pickupInterval(baseMinimum: Float, baseMaximum: Float, difficultyMultiplier: Float): Float =
        range(baseMinimum, baseMaximum) * difficultyMultiplier.coerceAtLeast(0f)

    private fun eelBand(
        topYMeters: Float,
        bandOffsetMinimum: Float,
        bandOffsetMaximum: Float,
        minBandGapMeters: Float,
        minPlayerGapMeters: Float,
        playerYMeters: Float,
    ): Float {
        val lower = topYMeters + minOf(bandOffsetMinimum, bandOffsetMaximum)
        val upper = topYMeters + maxOf(bandOffsetMinimum, bandOffsetMaximum)
        if (upper <= lower) return lower
        val candidate = findCandidate(lower, upper) { value ->
            val playerClear = abs(value - playerYMeters) >= minPlayerGapMeters
            val bandClear = lastEelBaseY.isNaN() || abs(value - lastEelBaseY) >= minBandGapMeters
            playerClear && bandClear
        } ?: fallbackBand(lower, upper, minPlayerGapMeters, minBandGapMeters, playerYMeters)
        return candidate
    }

    private fun fallbackBand(
        lower: Float,
        upper: Float,
        minPlayerGapMeters: Float,
        minBandGapMeters: Float,
        playerYMeters: Float,
    ): Float {
        val options = floatArrayOf(lower, upper, (lower + upper) / 2f)
        return options.maxBy { value ->
            val playerSlack = abs(value - playerYMeters) - minPlayerGapMeters
            val bandSlack = if (lastEelBaseY.isNaN()) Float.MAX_VALUE else abs(value - lastEelBaseY) - minBandGapMeters
            min(playerSlack, bandSlack)
        }
    }

    private fun safeClearance(depthMeters: Float): Float =
        1.35f + (depthMeters.coerceAtLeast(0f) / 400f).coerceAtMost(0.75f)

    private fun isSeparatedFromRecent(centerXMeters: Float, halfWidthMeters: Float): Boolean =
        recentHazards.all { recent ->
            abs(centerXMeters - recent.centerXMeters) >= recent.halfWidthMeters + halfWidthMeters + 1f
        }

    private fun findCandidate(lower: Float, upper: Float, predicate: (Float) -> Boolean): Float? {
        repeat(32) {
            val candidate = range(lower, upper)
            if (predicate(candidate)) return candidate
        }
        return null
    }

    private fun fallbackCandidate(
        lower: Float,
        upper: Float,
        playerXMeters: Float,
        safeFromPlayer: Float,
        halfWidth: Float,
    ): Float {
        val options = floatArrayOf(lower, upper, (lower + upper) / 2f)
        return options.maxBy { value ->
            val playerDistance = abs(value - playerXMeters)
            val recentDistance = recentHazards.minOfOrNull { recent ->
                abs(value - recent.centerXMeters) - recent.halfWidthMeters - halfWidth
            } ?: Float.MAX_VALUE
            min(playerDistance - safeFromPlayer, recentDistance - 1f)
        }
    }
}
