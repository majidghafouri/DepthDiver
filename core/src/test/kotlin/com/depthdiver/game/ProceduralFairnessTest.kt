package com.depthdiver.game

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProceduralFairnessTest {

    @Test
    fun sameSeedProducesTheSameSequence() {
        val first = ProceduralFairness()
        val second = ProceduralFairness()
        first.reset(41L)
        second.reset(41L)

        repeat(20) {
            assertEquals(first.unit(), second.unit())
            assertEquals(first.range(3f, 9f), second.range(3f, 9f))
        }
    }

    @Test
    fun differentSeedsProduceDifferentSequences() {
        val first = ProceduralFairness()
        val second = ProceduralFairness()
        first.reset(1L)
        second.reset(2L)

        val values = (0 until 20).map { first.unit() }
        val otherValues = (0 until 20).map { second.unit() }

        assertTrue(values != otherValues)
    }

    @Test
    fun hazardPlacementStaysInBoundsAndLeavesPlayerClearance() {
        val fairness = ProceduralFairness()
        fairness.reset(7L)

        repeat(100) { index ->
            val width = 2.4f + index % 3
            val placement = fairness.hazardCenter(playerXMeters = 20f, widthMeters = width, depthMeters = index * 4f)
            val halfWidth = width / 2f
            assertTrue(placement.centerXMeters - halfWidth >= -0.0001f)
            assertTrue(placement.centerXMeters + halfWidth <= 40f + 0.0001f)
            assertTrue(abs(placement.centerXMeters - 20f) >= 0.9f + placement.safeClearanceMeters + halfWidth - 0.0001f)
        }
    }

    @Test
    fun recentHazardsAreSeparated() {
        val fairness = ProceduralFairness()
        fairness.reset(19L)

        val first = fairness.hazardCenter(20f, 4f, 0f)
        val second = fairness.hazardCenter(20f, 4f, 0f)

        assertTrue(abs(first.centerXMeters - second.centerXMeters) >= 5f)
    }

    @Test
    fun pickupsStayReachableAndInBounds() {
        val fairness = ProceduralFairness()
        fairness.reset(23L)

        repeat(50) {
            val center = fairness.pickupCenter(playerXMeters = 20f, widthMeters = 1.6f, depthMeters = 100f)
            assertTrue(center - 0.8f >= -0.0001f)
            assertTrue(center + 0.8f <= 40f + 0.0001f)
            assertTrue(abs(center - 20f) <= 14f + 0.0001f)
        }
    }

    @Test
    fun lowOxygenForcesTankAfterCooldown() {
        val fairness = ProceduralFairness()
        fairness.reset(3L)

        assertFalse(fairness.shouldForceOxygenTank(0.5f, 20f))
        assertTrue(fairness.shouldForceOxygenTank(0.3f, 20f))
        fairness.noteOxygenTank(20f)
        assertFalse(fairness.shouldForceOxygenTank(0.3f, 25f))
        assertTrue(fairness.shouldForceOxygenTank(0.3f, 32f))
    }

    @Test
    fun eelSpawnsGuaranteeReactionTimeAndSeparateBands() {
        val fairness = ProceduralFairness()
        fairness.reset(97L)

        val first = fairness.eelSpawn(
            playerXMeters = 20f,
            playerYMeters = -50f,
            widthMeters = 7.5f,
            topYMeters = -40f,
            bandOffsetMinimum = 0.5f,
            bandOffsetMaximum = 6.5f,
            minBandGapMeters = 3f,
            minPlayerGapMeters = 2.5f,
            minReactionSeconds = 1.6f,
            baseSpeedMetersPerSecond = 7.5f,
            depthMeters = 60f,
        )
        val second = fairness.eelSpawn(
            playerXMeters = 20f,
            playerYMeters = -50f,
            widthMeters = 7.5f,
            topYMeters = -40f,
            bandOffsetMinimum = 0.5f,
            bandOffsetMaximum = 6.5f,
            minBandGapMeters = 3f,
            minPlayerGapMeters = 2.5f,
            minReactionSeconds = 1.6f,
            baseSpeedMetersPerSecond = 7.5f,
            depthMeters = 60f,
        )

        assertTrue(first.reactionSeconds >= 1.6f)
        assertTrue(second.reactionSeconds >= 1.6f)
        assertTrue(first.speedMetersPerSecond > 0f)
        assertTrue(second.speedMetersPerSecond > 0f)
        assertTrue(first.baseYMeters in -39.5f..-33.5f)
        assertTrue(second.baseYMeters in -39.5f..-33.5f)
        assertTrue(abs(first.baseYMeters - second.baseYMeters) >= 3f)
        assertTrue(first.startXMeters <= 0f || first.startXMeters >= 40f)
        assertTrue(second.startXMeters <= 0f || second.startXMeters >= 40f)
    }

    @Test
    fun eelEntersFromTheSideWithMoreTravelDistance() {
        val fairness = ProceduralFairness()
        fairness.reset(5L)

        val rightSidePlayer = fairness.eelSpawn(
            playerXMeters = 34f,
            playerYMeters = 0f,
            widthMeters = 7.5f,
            topYMeters = 0f,
            bandOffsetMinimum = 0.5f,
            bandOffsetMaximum = 6.5f,
            minBandGapMeters = 3f,
            minPlayerGapMeters = 2.5f,
            minReactionSeconds = 1.6f,
            baseSpeedMetersPerSecond = 7.5f,
            depthMeters = 60f,
        )
        val leftSidePlayer = fairness.eelSpawn(
            playerXMeters = 6f,
            playerYMeters = 0f,
            widthMeters = 7.5f,
            topYMeters = 0f,
            bandOffsetMinimum = 0.5f,
            bandOffsetMaximum = 6.5f,
            minBandGapMeters = 3f,
            minPlayerGapMeters = 2.5f,
            minReactionSeconds = 1.6f,
            baseSpeedMetersPerSecond = 7.5f,
            depthMeters = 60f,
        )

        assertEquals(1, rightSidePlayer.dir)
        assertEquals(-1, leftSidePlayer.dir)
    }

    @Test
    fun eelSpeedSlowsWithDepth() {
        val fairness = ProceduralFairness()
        fairness.reset(13L)

        val shallow = fairness.eelSpawn(
            playerXMeters = 0.5f,
            playerYMeters = 0f,
            widthMeters = 7.5f,
            topYMeters = 0f,
            bandOffsetMinimum = 0.5f,
            bandOffsetMaximum = 6.5f,
            minBandGapMeters = 3f,
            minPlayerGapMeters = 2.5f,
            minReactionSeconds = 0f,
            baseSpeedMetersPerSecond = 7.5f,
            depthMeters = 50f,
        )
        val deep = fairness.eelSpawn(
            playerXMeters = 0.5f,
            playerYMeters = 0f,
            widthMeters = 7.5f,
            topYMeters = 0f,
            bandOffsetMinimum = 0.5f,
            bandOffsetMaximum = 6.5f,
            minBandGapMeters = 3f,
            minPlayerGapMeters = 2.5f,
            minReactionSeconds = 0f,
            baseSpeedMetersPerSecond = 7.5f,
            depthMeters = 400f,
        )

        assertTrue(deep.speedMetersPerSecond < shallow.speedMetersPerSecond)
    }

    @Test
    fun resetClearsEelBandHistory() {
        val fairness = ProceduralFairness()
        fairness.reset(29L)

        val first = fairness.eelSpawn(
            playerXMeters = 20f,
            playerYMeters = 0f,
            widthMeters = 7.5f,
            topYMeters = 0f,
            bandOffsetMinimum = 0.5f,
            bandOffsetMaximum = 6.5f,
            minBandGapMeters = 3f,
            minPlayerGapMeters = 0f,
            minReactionSeconds = 1.6f,
            baseSpeedMetersPerSecond = 7.5f,
            depthMeters = 60f,
        )
        fairness.reset(29L)
        val afterReset = fairness.eelSpawn(
            playerXMeters = 20f,
            playerYMeters = 0f,
            widthMeters = 7.5f,
            topYMeters = 0f,
            bandOffsetMinimum = 0.5f,
            bandOffsetMaximum = 6.5f,
            minBandGapMeters = 3f,
            minPlayerGapMeters = 0f,
            minReactionSeconds = 1.6f,
            baseSpeedMetersPerSecond = 7.5f,
            depthMeters = 60f,
        )

        assertEquals(first.baseYMeters, afterReset.baseYMeters)
    }

    @Test
    fun intervalsRemainPositiveAndDepthReducesHazardFrequency() {
        val fairness = ProceduralFairness()
        fairness.reset(11L)

        val shallow = fairness.hazardInterval(1.4f, 2.6f, 0f, 1f)
        val deep = fairness.hazardInterval(1.4f, 2.6f, 160f, 1f)

        assertTrue(shallow > 0f)
        assertTrue(deep > 0f)
        assertTrue(deep < shallow)
        assertTrue(fairness.pickupInterval(3f, 5.5f, 1f) > 0f)
    }

    @Test
    fun simulatedRunNeverBreaksFairnessInvariants() {
        val fairness = ProceduralFairness()
        fairness.reset(2024L)

        var playerX = 20f
        var playerY = 0f
        var eelCount = 0
        var previousEelBand = Float.NaN

        for (step in 0 until 400) {
            val depth = depthFor(step)
            val top = playerY + 12f
            val roll = fairness.unit()

            when {
                roll < 0.3f -> {
                    fairness.recordHazard(1.2f, 4.8f)
                    fairness.recordHazard(38.8f, 4.8f)
                }
                depth > 45f && roll < 0.45f -> {
                    val eel = fairness.eelSpawn(
                        playerXMeters = playerX,
                        playerYMeters = playerY,
                        widthMeters = 7.5f,
                        topYMeters = top,
                        bandOffsetMinimum = 0.5f,
                        bandOffsetMaximum = 6.5f,
                        minBandGapMeters = 3f,
                        minPlayerGapMeters = 2.5f,
                        minReactionSeconds = 1.6f,
                        baseSpeedMetersPerSecond = 7.5f,
                        depthMeters = depth,
                    )
                    assertTrue(eel.reactionSeconds >= 1.6f)
                    assertTrue(abs(eel.baseYMeters - playerY) >= 2.5f - 0.0001f)
                    if (!previousEelBand.isNaN()) {
                        assertTrue(abs(eel.baseYMeters - previousEelBand) >= 3f - 0.0001f)
                    }
                    previousEelBand = eel.baseYMeters
                    eelCount++
                }
                depth > 35f && roll < 0.65f -> {
                    assertHazardFair(fairness, playerX, depth, 3f)
                }
                depth > 18f && roll < 0.85f -> {
                    assertHazardFair(fairness, playerX, depth, 2.4f)
                }
                depth > 80f && roll < 0.97f -> {
                    assertHazardFair(fairness, playerX, depth, 4.2f)
                }
                else -> {
                    assertHazardFair(fairness, playerX, depth, fairness.range(2.25f, 4.25f))
                }
            }

            val pickupWidth = if (fairness.unit() >= 0.65f) 1.6f else 1.2f
            val pickup = fairness.pickupCenter(playerX, pickupWidth, depth)
            assertTrue(pickup - pickupWidth / 2f >= -0.0001f)
            assertTrue(pickup + pickupWidth / 2f <= 40f + 0.0001f)
            assertTrue(abs(pickup - playerX) <= 14f + 0.0001f)
            assertTrue(fairness.hazardInterval(1.4f, 2.6f, depth, 1f) > 0f)

            playerX = fairness.range(1f, 39f)
        }

        assertTrue(eelCount > 0)
    }

    @Test
    fun simulatedRunReplaysIdenticallyForTheSameSeed() {
        assertEquals(simulateSpawnLog(7L), simulateSpawnLog(7L))
        assertTrue(simulateSpawnLog(7L) != simulateSpawnLog(8L))
    }

    private fun assertHazardFair(fairness: ProceduralFairness, playerX: Float, depth: Float, width: Float) {
        val placement = fairness.hazardCenter(playerX, width, depth)
        val halfWidth = width / 2f
        assertTrue(placement.centerXMeters - halfWidth >= -0.0001f)
        assertTrue(placement.centerXMeters + halfWidth <= 40f + 0.0001f)
        assertTrue(abs(placement.centerXMeters - playerX) >= 0.9f + placement.safeClearanceMeters + halfWidth - 0.0001f)
    }

    private fun simulateSpawnLog(seed: Long): List<String> {
        val fairness = ProceduralFairness()
        fairness.reset(seed)
        val log = mutableListOf<String>()
        var playerX = 20f
        repeat(200) { step ->
            val depth = step * 3f
            val roll = fairness.unit()
            log += "h:$roll"
            if (depth > 45f && roll < 0.45f) {
                val eel = fairness.eelSpawn(
                    playerXMeters = playerX,
                    playerYMeters = -depth,
                    widthMeters = 7.5f,
                    topYMeters = -depth + 12f,
                    bandOffsetMinimum = 0.5f,
                    bandOffsetMaximum = 6.5f,
                    minBandGapMeters = 3f,
                    minPlayerGapMeters = 2.5f,
                    minReactionSeconds = 1.6f,
                    baseSpeedMetersPerSecond = 7.5f,
                    depthMeters = depth,
                )
                log += "eel:${eel.dir}:${eel.baseYMeters}:${eel.speedMetersPerSecond}"
            } else {
                log += "place:${fairness.hazardCenter(playerX, 3f, depth).centerXMeters}"
            }
            playerX = fairness.range(1f, 39f)
        }
        return log
    }

    private fun depthFor(step: Int): Float = step * 1.5f
}
