package com.depthdiver.run

import com.depthdiver.FlakyPreferences
import com.depthdiver.Leaderboard
import com.depthdiver.Profile
import com.depthdiver.TestPreferences
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The transitions a run can make, and what each one is allowed to do.
 *
 * These rules used to be implied by the order of statements inside
 * DepthDiverGame: which call had to come before which, and which of them had to
 * release the ledger. Asserting them here means a run cannot be settled without
 * being checkpointed first, cannot be awarded a bonus it has already had, and
 * cannot be lost to a write that fails.
 */
class RunLifecycleTest {

    private val granted = mutableListOf<Int>()

    @BeforeTest
    fun setUp() {
        Profile.prefsOverride = TestPreferences("depthdiver")
        Leaderboard.prefsOverride = TestPreferences("depthdiver-leaderboard")
        granted.clear()
    }

    @AfterTest
    fun tearDown() {
        Profile.prefsOverride = null
        Leaderboard.prefsOverride = null
    }

    private fun lifecycle(day: Int = 100): RunLifecycle =
        RunLifecycle(RunSettlement(currentDay = { day })) { granted += it }

    private fun RunLifecycle.openRun(wallet: Int = 0): RunLedger =
        begin(wallet)?.also { hold(it) } ?: error("begin failed")

    @Test
    fun beginDoesNotHoldTheLedgerUntilAsked() {
        val lc = lifecycle()
        val ledger = lc.begin(0)
        assertNotNull(ledger)
        // The world reset detaches whatever is held, so begin and hold are
        // separate on purpose.
        assertNull(lc.ledger)
        lc.hold(ledger!!)
        assertEquals(ledger, lc.ledger)
        assertTrue(lc.hasActiveRun)
    }

    @Test
    fun endingARunCheckpointsFirstSoTheResultIsWhereThePlayerFinished() {
        val lc = lifecycle()
        lc.openRun().collectPearl(7, 25)
        // The ledger is left at the run's opening depth; only a checkpoint moves
        // it. Settling without one would report depth 0 for a 40 m dive.
        val outcome = lc.end(RunTerminalReason.OXYGEN, depth = 40f, score = 900)!!
        assertTrue(abs(40f - outcome.depth) < 1e-4f, "reported depth ${outcome.depth}, wanted 40")
        assertEquals(900, outcome.score)
        assertNull(lc.ledger, "ending a run must release it")
    }

    @Test
    fun endingWithNoRunDoesNothing() {
        assertNull(lifecycle().end(RunTerminalReason.OXYGEN, 10f, 100))
    }

    @Test
    fun abandoningKeepsWhatTheRunEarnedAndReleasesTheLedger() {
        val lc = lifecycle()
        lc.openRun().collectPearl(9, 30)
        val outcome = lc.abandon(depth = 12f, score = 250)!!
        assertTrue(
            outcome.displayedPearls >= 9,
            "an abandoned run should still report the pearls it banked, got ${outcome.displayedPearls}",
        )
        assertTrue(abs(12f - outcome.depth) < 1e-4f, "reported depth ${outcome.depth}, wanted 12")
        assertNull(lc.ledger)
    }

    @Test
    fun abandoningWithNoRunDoesNothing() {
        assertNull(lifecycle().abandon(5f, 50))
    }

    @Test
    fun checkpointingWithNoRunIsNotAnError() {
        // The simulation ticks every frame whether or not a ledger is open.
        assertNull(lifecycle().checkpoint(5f, 50))
    }

    @Test
    fun aBonusIsCreditedOnceAndOnlyOnce() {
        val lc = lifecycle()
        lc.openRun()
        val first = lc.awardBonus("boss:r1", 200, BonusCategory.BOSS)
        assertTrue(first > 0, "first award should pay out, got $first")
        assertEquals(1, granted.size)
        val second = lc.awardBonus("boss:r1", 200, BonusCategory.BOSS)
        assertEquals(0, second, "the same bonus must not pay twice")
        assertEquals(1, granted.size)
    }

    @Test
    fun aBonusWithNoRunPaysNothing() {
        assertEquals(0, lifecycle().awardBonus("boss:r1", 200, BonusCategory.BOSS))
        assertTrue(granted.isEmpty())
    }

    @Test
    fun differentBonusesInOneRunBothPay() {
        val lc = lifecycle()
        lc.openRun()
        assertTrue(lc.awardBonus("boss:r1", 200, BonusCategory.BOSS) > 0)
        assertTrue(lc.awardBonus("challenge:100", 40, BonusCategory.CHALLENGE) > 0)
        assertEquals(2, granted.size)
    }

    @Test
    fun detachingDropsTheRunWithoutSettlingIt() {
        val lc = lifecycle()
        lc.openRun()
        lc.detach()
        assertNull(lc.ledger)
        assertFalse(lc.hasActiveRun)
    }

    @Test
    fun aCleanStartupReportsNothingToRecover() {
        val lc = lifecycle()
        assertEquals(StartupRecovery.Clean, lc.recoverOnStartup())
        assertNull(lc.ledger)
    }

    @Test
    fun anUnfinishedRunFromLastSessionIsAbandonedNotResumed() {
        val settlement = RunSettlement(currentDay = { 100 })
        val stale = settlement.begin(day = 100, dailyEligible = true)
        stale.collectPearl(9, 40)
        settlement.checkpoint(stale, 18f, 400)

        val lc = RunLifecycle(settlement) { }
        val recovery = lc.recoverOnStartup()
        assertTrue(
            recovery is StartupRecovery.AbandonedStale,
            "a run left active must be abandoned, got $recovery",
        )
        assertNull(lc.ledger, "nothing may be held after recovery")
    }

    @Test
    fun mirrorReportsTheCheckpointsScoreAndPearls() {
        val lc = lifecycle()
        lc.openRun()
        val progress = lc.checkpoint(20f, 640)!!
        val (mirroredScore, mirroredPearls) = lc.mirror(progress)
        assertEquals(640, mirroredScore)
        assertEquals(progress.displayedPearls, mirroredPearls)
    }

    @Test
    fun aRunThatCannotBeWrittenStaysHeldSoItIsNotLost() {
        // A settlement that cannot be persisted must not look like a completed
        // run: the game pauses on a null outcome, and if the ledger were released
        // the player's dive would vanish with no way back.
        val flaky = FlakyPreferences()
        val settlement = RunSettlement(
            currentDay = { 100 },
            persistence = RunPersistence(flaky),
        )
        val lc = RunLifecycle(settlement) { }
        val ledger = lc.openRun()
        flaky.failOnFlush = true
        assertNull(lc.end(RunTerminalReason.OXYGEN, 30f, 500))
        assertEquals(ledger, lc.ledger, "the run must still be held")
    }
}
