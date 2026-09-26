package com.depthdiver.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BiomeTest {

    @Test
    fun shallowWaterIsTheFirstBiome() {
        assertEquals(Biome.SUNLIT_SHALLOWS, Biome.forDepth(0f))
        assertEquals(Biome.SUNLIT_SHALLOWS, Biome.forDepth(-50f))
    }

    @Test
    fun depthBandsMapToExpectedBiomes() {
        assertEquals(Biome.SUNLIT_SHALLOWS, Biome.forDepth(59.9f))
        assertEquals(Biome.TURQUOISE_REEF, Biome.forDepth(60f))
        assertEquals(Biome.TURQUOISE_REEF, Biome.forDepth(139f))
        assertEquals(Biome.MIDNIGHT_ZONE, Biome.forDepth(140f))
        assertEquals(Biome.ABYSS, Biome.forDepth(260f))
        assertEquals(Biome.HADAL_TRENCH, Biome.forDepth(420f))
        assertEquals(Biome.HADAL_TRENCH, Biome.forDepth(5000f))
    }

    @Test
    fun nonFiniteDepthFallsBackToTheStart() {
        assertEquals(Biome.SUNLIT_SHALLOWS, Biome.forDepth(Float.NaN))
        assertEquals(Biome.SUNLIT_SHALLOWS, Biome.forDepth(Float.POSITIVE_INFINITY))
    }

    @Test
    fun biomeDepthsAreStrictlyIncreasing() {
        val depths = Biome.entries.map { it.minDepth }
        assertEquals(depths.sorted(), depths)
        assertEquals(depths.distinct().size, depths.size)
    }

    @Test
    fun nextOfWalksForwardAndStopsAtTheLastBiome() {
        assertEquals(Biome.TURQUOISE_REEF, Biome.nextOf(Biome.SUNLIT_SHALLOWS))
        assertEquals(Biome.MIDNIGHT_ZONE, Biome.nextOf(Biome.TURQUOISE_REEF))
        assertSame(Biome.HADAL_TRENCH, Biome.nextOf(Biome.HADAL_TRENCH))
    }

    @Test
    fun progressWithinBiomeRunsFromZeroToOne() {
        assertEquals(0f, Biome.progressWithin(60f, Biome.TURQUOISE_REEF), 1e-4f)
        assertEquals(0.5f, Biome.progressWithin(100f, Biome.TURQUOISE_REEF), 1e-3f)
        assertEquals(1f, Biome.progressWithin(140f, Biome.TURQUOISE_REEF), 1e-4f)
    }

    @Test
    fun lastBiomeHasNoProgressSpan() {
        assertEquals(0f, Biome.progressWithin(500f, Biome.HADAL_TRENCH), 1e-4f)
    }

    @Test
    fun blendingInterpolatesBetweenNeighbouringColors() {
        val start = Biome.SUNLIT_SHALLOWS.color()
        val end = Biome.TURQUOISE_REEF.color()
        val midway = Biome.SUNLIT_SHALLOWS.blendTo(Biome.TURQUOISE_REEF, 0.5f)
        assertEquals((start.topGreen + end.topGreen) / 2f, midway.topGreen, 1e-4f)
        assertTrue(midway.topGreen < start.topGreen)
    }

    @Test
    fun blendingClampsOutOfRangeFactors() {
        val start = Biome.SUNLIT_SHALLOWS.color()
        val end = Biome.TURQUOISE_REEF.color()
        assertEquals(start.topGreen, Biome.SUNLIT_SHALLOWS.blendTo(Biome.TURQUOISE_REEF, -1f).topGreen, 1e-4f)
        assertEquals(end.topGreen, Biome.SUNLIT_SHALLOWS.blendTo(Biome.TURQUOISE_REEF, 5f).topGreen, 1e-4f)
    }

    @Test
    fun deeperBiomesAreDarker() {
        val surface = Biome.SUNLIT_SHALLOWS.color()
        val hadal = Biome.HADAL_TRENCH.color()
        assertTrue(hadal.topGreen < surface.topGreen)
        assertTrue(hadal.bottomBlue < surface.bottomBlue)
    }

    @Test
    fun everyBiomeHasAHazardMixAndANameKey() {
        for (biome in Biome.entries) {
            assertTrue(biome.nameKey.isNotBlank())
            assertTrue("mix for ${biome.name}", biome.hazardMix.isNotEmpty())
            assertNotEquals(0f, biome.hazardMix.values.sum())
        }
    }

    @Test
    fun anglerAndVortexOnlyAppearInDeeperWater() {
        assertTrue(Biome.SUNLIT_SHALLOWS.hazardMix.keys.none { it == HazardKind.ANGLER })
        assertTrue(Biome.SUNLIT_SHALLOWS.hazardMix.keys.none { it == HazardKind.VORTEX })
        assertTrue(Biome.TURQUOISE_REEF.hazardMix.keys.contains(HazardKind.ANGLER))
        assertTrue(Biome.ABYSS.hazardMix.keys.contains(HazardKind.VORTEX))
    }

    @Test
    fun rollRespectsWeights() {
        val mix = mapOf(
            HazardKind.ROCK to 0.5f,
            HazardKind.MINE to 0.3f,
            HazardKind.ANGLER to 0.2f,
        )
        assertEquals(HazardKind.ROCK, Biome.roll(mix, 0.1f))
        assertEquals(HazardKind.MINE, Biome.roll(mix, 0.6f))
        assertEquals(HazardKind.ANGLER, Biome.roll(mix, 0.95f))
    }

    @Test
    fun rollStaysSafeForDegenerateInput() {
        assertEquals(HazardKind.ROCK, Biome.roll(emptyMap(), 0.5f))
        assertEquals(HazardKind.ROCK, Biome.roll(mapOf(HazardKind.ROCK to 0f), 0.5f))
        assertTrue(Biome.roll(mapOf(HazardKind.MINE to 1f), -3f) == HazardKind.MINE)
    }

    @Test
    fun eachBiomeRollAlwaysReturnsAKindFromItsMix() {
        for (biome in Biome.entries) {
            repeat(50) { step ->
                val roll = step / 50f
                assertTrue(biome.hazardMix.containsKey(Biome.roll(biome.hazardMix, roll)))
            }
        }
    }
}
