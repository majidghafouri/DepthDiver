package com.depthdiver.run

import com.depthdiver.Leaderboard
import com.depthdiver.LeaderboardService
import com.depthdiver.Profile
import com.depthdiver.ProfileRunEffectResult

interface RunProfileEffects {
    fun applyCompletedRun(
        runId: RunId,
        depth: Float,
        score: Int,
        preDailyRunPearls: Int,
        settlementDay: Int,
        dailyEligible: Boolean,
    ): ProfileRunEffectResult

    fun applyAbandonedRun(
        runId: RunId,
        depth: Float,
        score: Int,
        preDailyRunPearls: Int,
    ): ProfileRunEffectResult
}

object ProfileRunEffects : RunProfileEffects {
    override fun applyCompletedRun(
        runId: RunId,
        depth: Float,
        score: Int,
        preDailyRunPearls: Int,
        settlementDay: Int,
        dailyEligible: Boolean,
    ): ProfileRunEffectResult = Profile.applyCompletedRun(
        runId = runId,
        depth = depth,
        score = score,
        preDailyRunPearls = preDailyRunPearls,
        settlementDay = settlementDay,
        dailyEligible = dailyEligible,
    )

    override fun applyAbandonedRun(
        runId: RunId,
        depth: Float,
        score: Int,
        preDailyRunPearls: Int,
    ): ProfileRunEffectResult = Profile.applyAbandonedRun(
        runId = runId,
        depth = depth,
        score = score,
        preDailyRunPearls = preDailyRunPearls,
    )
}

class RunSettlement(
    private val profile: RunProfileEffects = ProfileRunEffects,
    private val leaderboard: LeaderboardService = Leaderboard,
    val persistence: RunPersistence = RunPersistence(),
    private val currentDay: () -> Int = { Profile.dailyDay() },
) {
    constructor(leaderboardService: LeaderboardService) : this(ProfileRunEffects, leaderboardService)

    constructor(profileEffects: RunProfileEffects, leaderboardService: LeaderboardService) :
        this(profileEffects, leaderboardService, RunPersistence())

    val store: RunPersistence get() = persistence

    @JvmOverloads
    fun begin(
        day: Int = Profile.dailyDay(),
        dailyEligible: Boolean = Profile.claimedDailyDay() != day,
        initialWalletPearls: Int = 0,
    ): RunLedger = persistence.begin(day, dailyEligible, initialWalletPearls)

    fun beginRun(
        day: Int = Profile.dailyDay(),
        dailyEligible: Boolean = Profile.claimedDailyDay() != day,
        initialWalletPearls: Int = 0,
    ): RunLedger = begin(day, dailyEligible, initialWalletPearls)

    fun active(): RunLedger? = persistence.loadActive()

    fun activeLedger(): RunLedger? = active()

    fun checkpoint(depth: Float, score: Int): RunProgress {
        val ledger = active() ?: throw RunPersistenceException("no active run")
        return checkpoint(ledger, depth, score)
    }

    fun checkpoint(ledger: RunLedger, depth: Float, score: Int): RunProgress {
        val progress = ledger.checkpoint(depth, score)
        persistence.saveActive(ledger)
        return progress
    }

    @JvmOverloads
    fun settle(reason: RunTerminalReason = RunTerminalReason.COMPLETED): RunResult {
        val pending = persistence.loadPending()
        if (pending != null) return processPending(pending)
        val ledger = active() ?: throw RunPersistenceException("no active run")
        return settle(ledger, reason, RunDisposition.COMPLETED)
    }

    fun settle(
        ledger: RunLedger,
        reason: RunTerminalReason = RunTerminalReason.COMPLETED,
        disposition: RunDisposition = RunDisposition.COMPLETED,
    ): RunResult {
        if (ledger.result != null) {
            persistence.loadResult(ledger.runId)?.let { return it }
        }
        prepareLedgerForSettlement(ledger, reason, disposition)
        val pending = persistence.persistTerminal(ledger, reason, disposition)
        return processPending(pending)
    }

    fun complete(
        ledger: RunLedger,
        reason: RunTerminalReason = RunTerminalReason.COMPLETED,
    ): RunResult = settle(ledger, reason, RunDisposition.COMPLETED)

    fun endRun(
        ledger: RunLedger,
        reason: RunTerminalReason = RunTerminalReason.COMPLETED,
    ): RunResult = complete(ledger, reason)

    fun settle(ledger: RunLedger, reason: String): RunResult =
        settle(ledger, parseReason(reason), RunDisposition.COMPLETED)

    fun settle(reason: String): RunResult = settle(parseReason(reason))

    @JvmOverloads
    fun abandon(
        ledger: RunLedger,
        reason: RunTerminalReason = RunTerminalReason.ABANDONED,
    ): RunResult {
        if (ledger.result != null) {
            persistence.loadResult(ledger.runId)?.let { return it }
        }
        prepareLedgerForSettlement(ledger, reason, RunDisposition.ABANDONED)
        val pending = persistence.persistTerminal(ledger, reason, RunDisposition.ABANDONED)
        return processPending(pending)
    }

    fun abandon(reason: RunTerminalReason = RunTerminalReason.ABANDONED): RunResult {
        val pending = persistence.loadPending()
        if (pending != null) return processPending(pending)
        val ledger = active() ?: throw RunPersistenceException("no active run")
        return abandon(ledger, reason)
    }

    fun recoverPending(): RunResult? {
        persistence.initialize()
        val pending = persistence.loadPending() ?: return null
        return processPending(pending)
    }

    fun recover(): RunResult? = recoverPending()

    fun recoverOnStartup(): RunResult? = recoverPending()

    fun start(): RunResult? = recoverPending()

    fun pending(): PendingRun? = persistence.loadPending()

    fun hasPending(): Boolean = persistence.hasPending()

    private fun prepareLedgerForSettlement(
        ledger: RunLedger,
        reason: RunTerminalReason,
        disposition: RunDisposition,
    ) {
        val day = currentDay()
        val eligible = ledger.dailyEligible &&
            disposition == RunDisposition.COMPLETED &&
            reason != RunTerminalReason.ABANDONED &&
            reason != RunTerminalReason.MANUAL &&
            Profile.claimedDailyDay() != day
        ledger.prepareSettlement(day, eligible)
        ledger.syncWalletPearls(Profile.pearls())
    }

    private fun processPending(pending: PendingRun): RunResult {
        if (pending.result.disposition == RunDisposition.ABANDONED) {
            if (!pending.profileApplied) {
                profile.applyAbandonedRun(
                    runId = pending.runId,
                    depth = pending.result.depth,
                    score = pending.result.score,
                    preDailyRunPearls = pending.result.preDailyRunPearls,
                )
                persistence.markProfileApplied(pending.runId)
            }
            persistence.storeResult(pending.result)
            persistence.clearPending(pending.runId)
            return pending.result
        }
        if (!pending.profileApplied) {
            profile.applyCompletedRun(
                runId = pending.runId,
                depth = pending.result.depth,
                score = pending.result.score,
                preDailyRunPearls = pending.result.preDailyRunPearls,
                settlementDay = pending.result.settlementDay,
                dailyEligible = pending.result.dailyEligible,
            )
            persistence.markProfileApplied(pending.runId)
        }
        val afterProfile = persistence.loadPending() ?: pending
        val leaderboardPending = if (afterProfile.leaderboardApplied) {
            afterProfile
        } else {
            val submission = leaderboard.submitOnce(
                runId = pending.runId.value.toString(),
                score = pending.result.score,
                depth = pending.result.depth,
            )
            persistence.markLeaderboardApplied(
                runId = pending.runId,
                entered = submission.entered,
                rank = submission.rank,
            )
        }
        val result = leaderboardPending.result
        persistence.storeResult(result)
        persistence.clearPending(pending.runId)
        return result
    }

    private fun parseReason(reason: String): RunTerminalReason = try {
        RunTerminalReason.valueOf(reason.uppercase())
    } catch (_: IllegalArgumentException) {
        when (reason.lowercase()) {
            "oxygen_depleted", "oxygen" -> RunTerminalReason.OXYGEN
            "hazard", "death" -> RunTerminalReason.HAZARD
            "boss", "boss_cleared" -> RunTerminalReason.BOSS
            "abandon", "abandoned", "manual" -> RunTerminalReason.ABANDONED
            else -> throw IllegalArgumentException("invalid terminal reason")
        }
    }
}

typealias RunSettlementCoordinator = RunSettlement
