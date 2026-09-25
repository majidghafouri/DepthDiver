package com.depthdiver.run

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RunLedgerTest {

    @Test
    fun duplicateBonusesAndCollectionAreCountedOncePerEvent() {
        val ledger = RunLedger(RunId(1), settlementDay = 10, dailyEligible = true)

        ledger.collectPearl(pearlValue = 5, scoreValue = 15)
        ledger.collectPearl(pearlValue = 5, scoreValue = 15)
        ledger.awardMilestonePearls(50f, 10, "milestone:50")
        ledger.awardMilestonePearls(50f, 10, "milestone:50")
        ledger.awardBossPearls(50, "boss")
        ledger.awardBossPearls(50, "boss")
        ledger.awardChallengePearls(40, "challenge")

        assertEquals(10, ledger.collectedPearls)
        assertEquals(10, ledger.milestonePearls)
        assertEquals(50, ledger.bossPearls)
        assertEquals(40, ledger.challengePearls)
        assertEquals(30, ledger.score)
        assertEquals(110, ledger.preDailyRunPearls)
        assertTrue(ledger.challengeQualified)
    }

    @Test
    fun checkpointsAreMonotonicAndSettlementIsOneShot() {
        val ledger = RunLedger(RunId(2), settlementDay = 11, dailyEligible = true)
        ledger.checkpoint(20f, 200)
        ledger.checkpoint(10f, 100)
        assertEquals(20f, ledger.depth)
        assertEquals(200, ledger.score)

        val first = ledger.settle(RunTerminalReason.OXYGEN)
        val second = ledger.settle(RunTerminalReason.HAZARD)
        assertTrue(first === second)
        assertEquals(25, first.dailyPearls)
        assertEquals(25, first.displayedPearls)
        assertEquals(0, first.preDailyRunPearls)
        assertEquals(RunDisposition.COMPLETED, first.disposition)
        assertFailsWith<IllegalArgumentException> { ledger.collectPearl() }
    }

    @Test
    fun manualTerminationIsAbandoned() {
        val ledger = RunLedger(RunId(4), settlementDay = 13, dailyEligible = true)

        val result = ledger.settle(RunTerminalReason.MANUAL)

        assertEquals(RunDisposition.ABANDONED, result.disposition)
        assertEquals(RunTerminalReason.MANUAL, result.reason)
        assertEquals(0, result.dailyPearls)
    }

    @Test
    fun abandonedRunHasNoDailyBonusAndHasAnImmutableResult() {
        val ledger = RunLedger(RunId(3), settlementDay = 12, dailyEligible = true)
        ledger.collectPearl(7)
        val result = ledger.abandon()
        val snapshot = result

        assertEquals(RunDisposition.ABANDONED, result.disposition)
        assertEquals(RunTerminalReason.ABANDONED, result.terminalReason)
        assertEquals(0, result.dailyPearls)
        assertEquals(7, result.preDailyRunPearls)
        assertEquals(result, snapshot)
        assertNotNull(ledger.result)
        assertFalse(ledger.isActive)
    }
}
