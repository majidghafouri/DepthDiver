package com.depthdiver

import com.depthdiver.game.ProceduralFairness
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RunSeedTest {

    private val hardSeed = 0x5EED_1234L

    @Test
    fun encodeDecodeRoundTrips() {
        val code = RunSeed.encode(hardSeed, 2)
        assertEquals(hardSeed to 2, RunSeed.decode(code))
    }

    @Test
    fun codeIsCaseInsensitive() {
        val code = RunSeed.encode(hardSeed, 1)
        assertEquals(RunSeed.decode(code), RunSeed.decode(code.lowercase()))
    }

    @Test
    fun decodeRejectsMalformedCodes() {
        assertEquals(null, RunSeed.decode(""))
        assertEquals(null, RunSeed.decode("nope"))
        assertEquals(null, RunSeed.decode("DD-"))
        assertEquals(null, RunSeed.decode("DD-AB!C-1"))
        assertEquals(null, RunSeed.decode("DD-ABC"))
        assertEquals(null, RunSeed.decode("DD-ABC-1-2"))
        assertEquals(null, RunSeed.decode("DD-ABC-x"))
    }

    @Test
    fun decodeRejectsOutOfRangeDifficulty() {
        assertEquals(null, RunSeed.decode("DD-ABC-3"))
        assertEquals(null, RunSeed.decode("DD-ABC--1"))
    }

    @Test
    fun isValidMatchesDecode() {
        assertTrue(RunSeed.isValid(RunSeed.encode(hardSeed, 0)))
        assertFalse(RunSeed.isValid("DD-ABC-9"))
    }

    @Test
    fun applyRejectsOtherDifficultyWithoutTouchingGenerator() {
        val fairness = ProceduralFairness()
        fairness.reset(999L)
        val expected = fairness.unit()

        fairness.reset(999L)
        val applied = RunSeed.apply(fairness, difficulty = 1, code = RunSeed.encode(hardSeed, 2))

        assertFalse(applied)
        // A rejected code must not leave the generator re-seeded.
        assertEquals(expected, fairness.unit())
    }

    @Test
    fun applySeedsGeneratorOnMatchingDifficulty() {
        val fairness = ProceduralFairness()
        assertTrue(RunSeed.apply(fairness, difficulty = 2, code = RunSeed.encode(hardSeed, 2)))

        val reference = ProceduralFairness()
        reference.reset(hardSeed)
        assertEquals(reference.unit(), fairness.unit())
    }

    @Test
    fun explicitSeedRunActuallyUsesThatSeed() {
        val started = ProceduralFairness()
        val provider = RunSeedProvider()
        provider.onRunStartWithSeed(started, hardSeed, 2)

        // A generator reset independently with the same seed must agree,
        // which is only true if onRunStartWithSeed really seeded `started`.
        val reference = ProceduralFairness()
        reference.reset(hardSeed)
        assertEquals(reference.unit(), started.unit())
    }

    @Test
    fun sameSeedProducesIdenticalGeneration() {
        val first = ProceduralFairness()
        val second = ProceduralFairness()
        RunSeedProvider().onRunStartWithSeed(first, hardSeed, 2)
        RunSeedProvider().onRunStartWithSeed(second, hardSeed, 2)

        val rollsA = List(200) { first.unit() }
        val rollsB = List(200) { second.unit() }
        assertEquals(rollsA, rollsB)

        val rangesA = List(50) { first.range(0f, 1000f) }
        val rangesB = List(50) { second.range(0f, 1000f) }
        assertEquals(rangesA, rangesB)
    }

    @Test
    fun sameSeedSharesTheSameCode() {
        val first = ProceduralFairness()
        val second = ProceduralFairness()
        val firstCode = RunSeedProvider().onRunStartWithSeed(first, hardSeed, 1)
        val secondCode = RunSeedProvider().onRunStartWithSeed(second, hardSeed, 1)
        assertEquals(firstCode, secondCode)
    }

    @Test
    fun differentSeedsProduceDifferentGeneration() {
        val first = ProceduralFairness()
        val second = ProceduralFairness()
        RunSeedProvider().onRunStartWithSeed(first, hardSeed, 1)
        RunSeedProvider().onRunStartWithSeed(second, hardSeed + 1, 1)

        val rollsA = List(20) { first.unit() }
        val rollsB = List(20) { second.unit() }
        assertNotEquals(rollsA, rollsB)
    }

    @Test
    fun freshRunDoesNotReuseASharedSeed() {
        val shared = ProceduralFairness()
        val provider = RunSeedProvider()
        provider.onRunStartWithSeed(shared, hardSeed, 1)

        val fresh = ProceduralFairness()
        val provider2 = RunSeedProvider()
        provider2.onRunStart(fresh, 1)

        // A brand new run must not inherit the shared seed, otherwise every
        // run after opening a friend code would replay the same world.
        assertNotEquals(hardSeed, provider2.getCurrentSeed())
    }

    @Test
    fun freshRunReportsItsOwnCode() {
        val fairness = ProceduralFairness()
        val provider = RunSeedProvider()
        val code = provider.onRunStart(fairness, 0)

        assertEquals(code, provider.getCurrentCode())
        assertEquals(0, provider.getCurrentDifficulty())
        assertNotEquals(0L, provider.getCurrentSeed())
        assertEquals(provider.getCurrentSeed() to 0, RunSeed.decode(code))
    }
}
