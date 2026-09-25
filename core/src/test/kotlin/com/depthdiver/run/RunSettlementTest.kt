package com.depthdiver.run

import com.depthdiver.Leaderboard
import com.depthdiver.LeaderboardEntry
import com.depthdiver.LeaderboardService
import com.depthdiver.LeaderboardSubmission
import com.depthdiver.Profile
import com.depthdiver.TestPreferences
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RunSettlementTest {

    private lateinit var profilePreferences: TestPreferences

    @BeforeTest
    fun setUp() {
        profilePreferences = TestPreferences("depthdiver")
        Profile.prefsOverride = profilePreferences
        Leaderboard.prefsOverride = TestPreferences("depthdiver-leaderboard")
    }

    @AfterTest
    fun tearDown() {
        Profile.prefsOverride = null
        Leaderboard.prefsOverride = null
    }

    @Test
    fun completedSettlementIsDurableAndIdempotent() {
        val settlement = RunSettlement(currentDay = { 100 })
        val ledger = settlement.begin(day = 100, dailyEligible = true)
        ledger.collectPearl(5, 25)
        ledger.awardMilestonePearls(50f, 10, "milestone:50")
        settlement.checkpoint(ledger, 24f, 300)

        val result = settlement.settle(ledger, RunTerminalReason.OXYGEN)
        assertEquals(ledger.runId, result.runId)
        assertEquals(15, result.preDailyRunPearls)
        assertEquals(40, result.displayedPearls)
        assertEquals(1, Profile.dives())
        assertEquals(24f, Profile.bestDepth())
        assertEquals(300, Profile.bestScore())
        assertEquals(15, Profile.bestRunPearls())
        assertEquals(100, Profile.claimedDailyDay())
        assertEquals(25, Profile.pearls())
        assertEquals(25, Profile.lifetimePearls())
        assertEquals(1, Leaderboard.top().size)

        assertEquals(result, settlement.settle(ledger, RunTerminalReason.HAZARD))
        assertEquals(1, Profile.dives())
        assertEquals(25, Profile.pearls())
        assertEquals(1, Leaderboard.top().size)
        assertTrue(Profile.completedRunApplied(ledger.runId.value.toString()))
    }

    @Test
    fun idsIncreaseAcrossSettlementAndPendingBlocksSecondBegin() {
        val settlement = RunSettlement(currentDay = { 101 })
        val first = settlement.begin(day = 101, dailyEligible = true)
        assertFailsWith<RunPersistenceException> { settlement.begin(day = 101, dailyEligible = false) }
        settlement.settle(first, RunTerminalReason.OXYGEN)

        val second = settlement.begin(day = 101, dailyEligible = false)
        assertEquals(first.runId.value + 1L, second.runId.value)
        settlement.abandon(second)
        val third = settlement.begin(day = 102, dailyEligible = true)
        assertEquals(second.runId.value + 1L, third.runId.value)
    }

    @Test
    fun recoveryAfterProfilePhaseCompletesLeaderboardOnce() {
        val ledger = RunSettlement(currentDay = { 102 }).begin(day = 102, dailyEligible = true)
        ledger.checkpoint(20f, 100)
        val failing = RunSettlement(leaderboard = FailingBeforeSubmit(), currentDay = { 102 })
        assertFailsWith<IllegalStateException> {
            failing.settle(ledger, RunTerminalReason.OXYGEN)
        }
        assertEquals(1, Profile.dives())
        assertTrue(Leaderboard.top().isEmpty())
        assertTrue(RunSettlement().hasPending())

        val result = RunSettlement().recoverPending()
        assertEquals(ledger.runId, result?.runId)
        assertEquals(1, Profile.dives())
        assertEquals(1, Leaderboard.top().size)
        assertFalse(RunSettlement().hasPending())
    }

    @Test
    fun recoveryAfterLeaderboardPhaseDoesNotAddASecondEntry() {
        val ledger = RunSettlement(currentDay = { 103 }).begin(day = 103, dailyEligible = true)
        ledger.checkpoint(20f, 100)
        val failing = RunSettlement(leaderboard = FailingAfterSubmit(), currentDay = { 103 })
        assertFailsWith<IllegalStateException> {
            failing.settle(ledger, RunTerminalReason.OXYGEN)
        }
        assertEquals(1, Leaderboard.top().size)
        assertTrue(RunSettlement().hasPending())

        val result = RunSettlement().recoverPending()
        assertEquals(ledger.runId, result?.runId)
        assertEquals(1, Leaderboard.top().size)
        assertEquals(1, Profile.dives())
        assertEquals(25, Profile.pearls())
    }

    @Test
    fun settlementUsesTheCurrentDayForTheDailyAward() {
        val settlement = RunSettlement(currentDay = { 200 })
        val ledger = settlement.begin(day = 199, dailyEligible = true)

        val result = settlement.settle(ledger, RunTerminalReason.OXYGEN)

        assertEquals(200, result.settlementDay)
        assertEquals(200, Profile.claimedDailyDay())
        assertEquals(25, result.dailyPearls)
    }

    @Test
    fun settlementSynchronizesThePersistedWalletBeforeTerminalResult() {
        val settlement = RunSettlement(currentDay = { 201 })
        Profile.grantPearls(100)
        val ledger = settlement.begin(day = 201, dailyEligible = false, initialWalletPearls = 100)
        ledger.collectPearl(5, 5)
        Profile.grantPearls(5)

        val result = settlement.settle(ledger, RunTerminalReason.HAZARD)

        assertEquals(105, result.walletPearls)
    }

    @Test
    fun bonusKeysRoundTripThroughTheRunCodec() {
        val settlement = RunSettlement(currentDay = { 202 })
        val ledger = settlement.begin(day = 202, dailyEligible = false)
        val key = "odd|key;\\line"
        ledger.awardBonus(key, 7, BonusCategory.OTHER)
        settlement.checkpoint(ledger, 12f, 12)

        val result = settlement.settle(ledger, RunTerminalReason.HAZARD)

        assertEquals(key, result.bonusAwards.single().key)
    }

    @Test
    fun abandonmentOnlyMovesHighWaterMarks() {
        val settlement = RunSettlement(currentDay = { 104 })
        val ledger = settlement.begin(day = 104, dailyEligible = true)
        ledger.collectPearl(8, 8)
        settlement.checkpoint(ledger, 31f, 90)
        val result = settlement.abandon(ledger)

        assertEquals(RunDisposition.ABANDONED, result.disposition)
        assertEquals(31f, Profile.bestDepth())
        assertEquals(90, Profile.bestScore())
        assertEquals(8, Profile.bestRunPearls())
        assertEquals(0, Profile.dives())
        assertEquals(0, Profile.claimedDailyDay())
        assertEquals(0, Profile.pearls())
        assertEquals(0, Profile.lifetimePearls())
        assertTrue(Leaderboard.top().isEmpty())
    }

    @Test
    fun corruptPendingStateIsQuarantinedBeforeStarting() {
        profilePreferences.putString(RunPersistenceKeys.PENDING, "corrupt-pending")
        val ledger = RunSettlement(currentDay = { 105 }).begin(day = 105, dailyEligible = true)

        assertEquals(RunId(1), ledger.runId)
        assertFalse(profilePreferences.contains(RunPersistenceKeys.PENDING))
        assertTrue(profilePreferences.get().keys.any { it.startsWith("run.corrupt.") })
    }

    private class FailingBeforeSubmit : LeaderboardService {
        override fun top(): List<LeaderboardEntry> = Leaderboard.top()
        override fun submit(score: Int, depth: Float): List<LeaderboardEntry> = Leaderboard.submit(score, depth)
        override fun qualifies(score: Int, depth: Float): Boolean = Leaderboard.qualifies(score, depth)
        override fun submitOnce(runId: String, score: Int, depth: Float): LeaderboardSubmission {
            throw IllegalStateException("leaderboard unavailable")
        }
    }

    private class FailingAfterSubmit : LeaderboardService {
        override fun top(): List<LeaderboardEntry> = Leaderboard.top()
        override fun submit(score: Int, depth: Float): List<LeaderboardEntry> = Leaderboard.submit(score, depth)
        override fun qualifies(score: Int, depth: Float): Boolean = Leaderboard.qualifies(score, depth)
        override fun submitOnce(runId: String, score: Int, depth: Float): LeaderboardSubmission {
            Leaderboard.submitOnce(runId, score, depth)
            throw IllegalStateException("post-submit failure")
        }
    }
}
