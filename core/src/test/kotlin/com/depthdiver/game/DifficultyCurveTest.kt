package com.depthdiver.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DifficultyCurveTest {

    private val depths = listOf(0f, 25f, 50f, 100f, 150f, 200f, 300f, 400f, 600f, 1000f, 5000f)

    @Test
    fun scrollSpeedIsMonotonicAndNeverReachesPlayerSpeed() {
        for ((base, ramp) in listOf(3.9f to 1.6f, 4.5f to 2f, 5.5f to 2.5f)) {
            for (playerSpeed in listOf(16f, 22.4f)) {
                var previous = -1f
                for (depth in depths) {
                    val speed = DifficultyCurve.scrollSpeed(base, ramp, depth, playerSpeed)
                    assertTrue(speed >= previous, "scroll must not decrease at ${depth}m")
                    assertTrue(speed < playerSpeed, "scroll $speed must stay under player speed $playerSpeed")
                    assertTrue(speed >= base, "scroll must never drop below base $base")
                    previous = speed
                }
            }
        }
    }

    @Test
    fun scrollSpeedIsUnchangedAtTheSurface() {
        for ((base, ramp) in listOf(3.9f to 1.6f, 4.5f to 2f, 5.5f to 2.5f)) {
            assertEquals(base, DifficultyCurve.scrollSpeed(base, ramp, 0f, 16f), 0.0001f)
        }
    }

    @Test
    fun scrollSpeedKeepsTheOriginalFeelBelowTheCeiling() {
        for ((base, ramp) in listOf(3.9f to 1.6f, 4.5f to 2f, 5.5f to 2.5f)) {
            for (depth in listOf(25f, 50f, 100f)) {
                val legacy = base + ramp * (depth / 40f)
                assertEquals(legacy, DifficultyCurve.scrollSpeed(base, ramp, depth, 16f), 0.0001f)
            }
        }
    }

    @Test
    fun scrollSpeedCapsOnceTheDiverWouldOtherwiseBeOverrun() {
        val ceiling = 16f * DifficultyCurve.SCROLL_CEILING_RATIO
        assertEquals(ceiling, DifficultyCurve.scrollSpeed(5.5f, 2.5f, 150f, 16f), 0.0001f)
        assertEquals(ceiling, DifficultyCurve.scrollSpeed(5.5f, 2.5f, 4000f, 16f), 0.0001f)
        assertEquals(ceiling, DifficultyCurve.scrollSpeed(3.9f, 1.6f, 900f, 16f), 0.0001f)
    }

    @Test
    fun hazardIntervalShrinksWithDepthButStaysAboveTheFloor() {
        var previousMaximum = Float.MAX_VALUE
        for (depth in depths) {
            val range = DifficultyCurve.hazardIntervalRange(1.4f, 2.6f, depth, 1f)
            assertTrue(range.minimum > 0f, "interval must stay positive at ${depth}m")
            assertTrue(range.maximum <= previousMaximum + 0.0001f, "frequency must not ease off at ${depth}m")
            assertTrue(
                range.minimum >= minOf(DifficultyCurve.HAZARD_INTERVAL_FLOOR, range.maximum) - 0.0001f,
                "interval must respect the ${DifficultyCurve.HAZARD_INTERVAL_FLOOR}s floor at ${depth}m",
            )
            previousMaximum = range.maximum
        }
    }

    @Test
    fun hazardIntervalRateStaysPlayableInDeepWater() {
        val deep = DifficultyCurve.hazardIntervalRange(1.4f, 2.6f, 600f, 1f)
        val spawnsPerSecond = 2f / ((deep.minimum + deep.maximum) / 2f)
        assertTrue(spawnsPerSecond < 2.5f, "deep water spawned $spawnsPerSecond hazards/s")
    }

    @Test
    fun harderDifficultiesSpawnHazardsMoreOften() {
        val easy = DifficultyCurve.hazardIntervalRange(1.4f, 2.6f, 200f, 1.3f)
        val normal = DifficultyCurve.hazardIntervalRange(1.4f, 2.6f, 200f, 1f)
        val hard = DifficultyCurve.hazardIntervalRange(1.4f, 2.6f, 200f, 0.75f)
        assertTrue(easy.minimum >= normal.minimum)
        assertTrue(normal.minimum > hard.minimum)
    }

    @Test
    fun oxygenDrainGrowsWithDepthAndIsCapped() {
        assertEquals(0.02f, DifficultyCurve.oxygenDrain(0.02f, 0f), 0.0001f)
        val shallow = DifficultyCurve.oxygenDrain(0.02f, 100f)
        val deep = DifficultyCurve.oxygenDrain(0.02f, 400f)
        val deepest = DifficultyCurve.oxygenDrain(0.02f, 5000f)
        assertTrue(shallow > 0.02f)
        assertTrue(deep > shallow)
        assertTrue(deepest <= 0.02f * (1f + DifficultyCurve.OXYGEN_DRAIN_GAIN) + 0.0001f)
    }

    @Test
    fun nonFiniteAndNegativeInputsStaySafe() {
        assertEquals(4.5f, DifficultyCurve.scrollSpeed(4.5f, 2f, Float.NaN, 16f), 0.0001f)
        assertEquals(4.5f, DifficultyCurve.scrollSpeed(4.5f, 2f, -50f, 16f), 0.0001f)
        assertTrue(DifficultyCurve.scrollSpeed(4.5f, 2f, 200f, 0f) >= 0f)
        assertEquals(0.02f, DifficultyCurve.oxygenDrain(0.02f, Float.NaN), 0.0001f)
        assertTrue(DifficultyCurve.hazardIntervalRange(1.4f, 2.6f, Float.NaN, 1f).minimum > 0f)
        assertEquals(0f, DifficultyCurve.hazardIntervalRange(1.4f, 2.6f, 200f, 0f).minimum, 0.0001f)
    }

    @Test
    fun saturationStaysNormalized() {
        assertEquals(0f, DifficultyCurve.saturation(-10f, 100f), 0.0001f)
        assertEquals(0f, DifficultyCurve.saturation(0f, 100f), 0.0001f)
        assertTrue(DifficultyCurve.saturation(100f, 100f) in 0.6f..0.7f)
        assertEquals(1f, DifficultyCurve.saturation(100000f, 100f), 0.0001f)
        assertEquals(1f, DifficultyCurve.saturation(100f, 0f), 0.0001f)
    }
}
