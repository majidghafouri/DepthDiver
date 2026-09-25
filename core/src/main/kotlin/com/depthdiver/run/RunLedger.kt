package com.depthdiver.run

import kotlin.math.max

 data class RunId(val value: Long) : Comparable<RunId> {
    init {
        require(value >= 0L) { "run id must not be negative" }
    }

    constructor(value: String) : this(value.toLongOrNull() ?: throw IllegalArgumentException("invalid run id"))

    val id: Long get() = value
    val text: String get() = value.toString()

    override fun compareTo(other: RunId): Int = value.compareTo(other.value)

    companion object {
        fun of(value: Long): RunId = RunId(value)
        fun parse(value: String): RunId = RunId(value)
    }
}

enum class RunDisposition {
    ACTIVE,
    COMPLETED,
    ABANDONED;

    companion object {
        val RUNNING: RunDisposition = ACTIVE
        val TERMINAL: RunDisposition = COMPLETED
        val ABORTED: RunDisposition = ABANDONED
    }
}

enum class RunTerminalReason {
    OXYGEN,
    HAZARD,
    BOSS,
    ABANDONED,
    COMPLETED,
    MANUAL,
    UNKNOWN;

    companion object {
        val OXYGEN_DEPLETED: RunTerminalReason = OXYGEN
        val HAZARD_DEATH: RunTerminalReason = HAZARD
        val BOSS_CLEARED: RunTerminalReason = BOSS
        val PLAYER_ABORT: RunTerminalReason = MANUAL
        val TERMINATED: RunTerminalReason = COMPLETED
    }
}

typealias TerminalReason = RunTerminalReason
typealias RunReason = RunTerminalReason

enum class BonusCategory {
    MILESTONE,
    BOSS,
    CHALLENGE,
    DAILY,
    OTHER,
}

typealias PearlBonusCategory = BonusCategory

 data class BonusAward(
    val key: String,
    val category: BonusCategory,
    val pearls: Int,
) {
    init {
        require(key.isNotBlank()) { "bonus key must not be blank" }
        require(pearls >= 0) { "bonus pearls must not be negative" }
    }
}

data class RunProgress(
    val runId: RunId,
    val depth: Float,
    val score: Int,
    val collectedPearls: Int,
    val milestonePearls: Int,
    val bossPearls: Int,
    val challengePearls: Int,
    val otherPearls: Int,
    val dailyPearls: Int,
    val preDailyRunPearls: Int,
    val displayedPearls: Int,
    val walletPearls: Int,
    val challengeQualified: Boolean,
    val settlementDay: Int,
    val dailyEligible: Boolean,
    val terminalReason: RunTerminalReason?,
    val disposition: RunDisposition,
) {
    val id: RunId get() = runId
    val day: Int get() = settlementDay
    val dailyQualified: Boolean get() = dailyEligible
    val runPearls: Int get() = displayedPearls
    val totalRunPearls: Int get() = displayedPearls
    val pearls: Int get() = displayedPearls
    val isTerminal: Boolean get() = disposition != RunDisposition.ACTIVE
}

 data class RunResult(
    val runId: RunId,
    val disposition: RunDisposition,
    val reason: RunTerminalReason,
    val settlementDay: Int,
    val dailyEligible: Boolean,
    val dailyAwarded: Boolean,
    val dailyPearls: Int,
    val collectedPearls: Int,
    val milestonePearls: Int,
    val bossPearls: Int,
    val challengePearls: Int,
    val otherPearls: Int,
    val preDailyRunPearls: Int,
    val displayedPearls: Int,
    val walletPearls: Int,
    val score: Int,
    val depth: Float,
    val challengeQualified: Boolean,
    val bonusAwards: List<BonusAward> = emptyList(),
    val leaderboardEntered: Boolean? = null,
    val leaderboardRank: Int? = null,
) {
    val id: RunId get() = runId
    val terminalReason: RunTerminalReason get() = reason
    val day: Int get() = settlementDay
    val dailyQualified: Boolean get() = dailyEligible
    val runPearls: Int get() = displayedPearls
    val totalRunPearls: Int get() = displayedPearls
    val pearls: Int get() = displayedPearls
    val isAbandoned: Boolean get() = disposition == RunDisposition.ABANDONED
    val enteredLeaderboard: Boolean? get() = leaderboardEntered
    val rank: Int? get() = leaderboardRank
}

 data class RunLedgerSnapshot(
    val runId: RunId,
    val settlementDay: Int,
    val dailyEligible: Boolean,
    val depth: Float,
    val score: Int,
    val collectedPearls: Int,
    val milestonePearls: Int,
    val bossPearls: Int,
    val challengePearls: Int,
    val otherPearls: Int,
    val dailyPearls: Int,
    val walletPearls: Int,
    val challengeQualified: Boolean,
    val bonusAwards: List<BonusAward>,
    val terminalReason: RunTerminalReason?,
    val disposition: RunDisposition,
    val result: RunResult?,
)

class RunLedger(
    val runId: RunId,
    settlementDay: Int,
    dailyEligible: Boolean = true,
    initialWalletPearls: Int = 0,
    initialDepth: Float = 0f,
    initialScore: Int = 0,
) {
    private var settlementDayValue = settlementDay
    private var dailyEligibleValue = dailyEligible
    private var collectedPearlsValue = 0
    private var milestonePearlsValue = 0
    private var bossPearlsValue = 0
    private var challengePearlsValue = 0
    private var otherPearlsValue = 0
    private var dailyPearlsValue = 0
    private var walletPearlsValue = nonNegative(initialWalletPearls)
    private var scoreValue = nonNegative(initialScore)
    private var depthValue = if (initialDepth.isFinite()) max(0f, initialDepth) else 0f
    private var challengeQualifiedValue = false
    private val awardedKeys = LinkedHashSet<String>()
    private val awardValues = ArrayList<BonusAward>()
    private var terminalReasonValue: RunTerminalReason? = null
    private var dispositionValue = RunDisposition.ACTIVE
    private var resultValue: RunResult? = null

    init {
        require(settlementDay >= 0) { "settlement day must not be negative" }
    }

    internal constructor(snapshot: RunLedgerSnapshot) : this(
        runId = snapshot.runId,
        settlementDay = snapshot.settlementDay,
        dailyEligible = snapshot.dailyEligible,
        initialWalletPearls = snapshot.walletPearls,
        initialDepth = snapshot.depth,
        initialScore = snapshot.score,
    ) {
        validateSnapshot(snapshot)
        collectedPearlsValue = snapshot.collectedPearls
        milestonePearlsValue = snapshot.milestonePearls
        bossPearlsValue = snapshot.bossPearls
        challengePearlsValue = snapshot.challengePearls
        otherPearlsValue = snapshot.otherPearls
        dailyPearlsValue = snapshot.dailyPearls
        walletPearlsValue = snapshot.walletPearls
        challengeQualifiedValue = snapshot.challengeQualified
        awardValues.addAll(snapshot.bonusAwards)
        snapshot.bonusAwards.forEach { awardedKeys.add(it.key) }
        terminalReasonValue = snapshot.terminalReason
        dispositionValue = snapshot.disposition
        resultValue = snapshot.result
    }

    val id: RunId get() = runId
    val settlementDay: Int get() = settlementDayValue
    val dailyEligible: Boolean get() = dailyEligibleValue
    val day: Int get() = settlementDayValue
    val dailyQualified: Boolean get() = dailyEligibleValue
    val collectedPearls: Int get() = collectedPearlsValue
    val milestonePearls: Int get() = milestonePearlsValue
    val bossPearls: Int get() = bossPearlsValue
    val challengePearls: Int get() = challengePearlsValue
    val otherPearls: Int get() = otherPearlsValue
    val dailyPearls: Int get() = dailyPearlsValue
    val preDailyRunPearls: Int
        get() = collectedPearlsValue + milestonePearlsValue + bossPearlsValue + challengePearlsValue + otherPearlsValue
    val displayedPearls: Int get() = preDailyRunPearls + dailyPearlsValue
    val runPearls: Int get() = displayedPearls
    val totalRunPearls: Int get() = displayedPearls
    val pearls: Int get() = displayedPearls
    val walletPearls: Int get() = walletPearlsValue
    val score: Int get() = scoreValue
    val depth: Float get() = depthValue
    val challengeQualified: Boolean get() = challengeQualifiedValue
    val terminalReason: RunTerminalReason? get() = terminalReasonValue
    val disposition: RunDisposition get() = dispositionValue
    val result: RunResult? get() = resultValue
    val isActive: Boolean get() = dispositionValue == RunDisposition.ACTIVE
    val isSettled: Boolean get() = resultValue != null
    val isTerminal: Boolean get() = dispositionValue != RunDisposition.ACTIVE
    val bonusAwards: List<BonusAward> get() = awardValues.toList()

    fun prepareSettlement(day: Int, eligible: Boolean): RunLedger {
        requireActive()
        require(day >= 0) { "settlement day must not be negative" }
        settlementDayValue = day
        dailyEligibleValue = eligible
        return this
    }

    fun syncWalletPearls(value: Int): RunLedger {
        requireActive()
        walletPearlsValue = nonNegative(value)
        return this
    }

    val progress: RunProgress
        get() = RunProgress(
            runId = runId,
            depth = depthValue,
            score = scoreValue,
            collectedPearls = collectedPearlsValue,
            milestonePearls = milestonePearlsValue,
            bossPearls = bossPearlsValue,
            challengePearls = challengePearlsValue,
            otherPearls = otherPearlsValue,
            dailyPearls = dailyPearlsValue,
            preDailyRunPearls = preDailyRunPearls,
            displayedPearls = displayedPearls,
            walletPearls = walletPearlsValue,
            challengeQualified = challengeQualifiedValue,
            settlementDay = settlementDay,
            dailyEligible = dailyEligible,
            terminalReason = terminalReasonValue,
            disposition = dispositionValue,
        )

    fun collectPearl(pearlValue: Int = 1, scoreValue: Int = pearlValue): Int {
        requireActive()
        require(pearlValue >= 0) { "pearl value must not be negative" }
        require(scoreValue >= 0) { "score value must not be negative" }
        collectedPearlsValue += pearlValue
        val scoreDelta = scoreValue
        this.scoreValue += scoreDelta
        walletPearlsValue += pearlValue
        return scoreDelta
    }

    fun collectPearls(amount: Int = 1, scorePerPearl: Int = amount): Int =
        collectPearl(amount, scorePerPearl)

    fun addPearl(pearlValue: Int = 1, scoreValue: Int = pearlValue): Int =
        collectPearl(pearlValue, scoreValue)

    fun awardBonus(
        key: String,
        pearls: Int,
        category: BonusCategory = inferBonusCategory(key),
    ): Int {
        requireActive()
        require(key.isNotBlank()) { "bonus key must not be blank" }
        require(pearls >= 0) { "bonus pearls must not be negative" }
        if (!awardedKeys.add(key)) return 0
        awardValues.add(BonusAward(key, category, pearls))
        when (category) {
            BonusCategory.MILESTONE -> milestonePearlsValue += pearls
            BonusCategory.BOSS -> bossPearlsValue += pearls
            BonusCategory.CHALLENGE -> {
                challengePearlsValue += pearls
                challengeQualifiedValue = true
            }
            BonusCategory.DAILY -> dailyPearlsValue += pearls
            BonusCategory.OTHER -> otherPearlsValue += pearls
        }
        walletPearlsValue += pearls
        return pearls
    }

    fun awardBonus(key: String, pearls: Int, category: String): Int =
        awardBonus(key, pearls, parseBonusCategory(category))

    fun awardMilestoneBonus(depth: Float, pearls: Int = 10, key: String = "milestone:$depth"): Int =
        awardBonus(key, pearls, BonusCategory.MILESTONE)

    fun awardMilestonePearls(depth: Float, pearls: Int = 10, key: String = "milestone:$depth"): Int =
        awardMilestoneBonus(depth, pearls, key)

    fun awardMilestonePearls(depth: Int, pearls: Int = 10, key: String = "milestone:$depth"): Int =
        awardMilestoneBonus(depth.toFloat(), pearls, key)

    fun awardBossPearls(pearls: Int = 50, key: String = "boss"): Int =
        awardBonus(key, pearls, BonusCategory.BOSS)

    fun awardChallengePearls(pearls: Int = ChallengeReward, key: String = "challenge"): Int =
        awardBonus(key, pearls, BonusCategory.CHALLENGE)

    fun markChallengeQualified(value: Boolean = true): Boolean {
        requireActive()
        if (!value || challengeQualifiedValue) return false
        challengeQualifiedValue = true
        return true
    }

    fun setChallengeQualified(value: Boolean): Boolean {
        requireActive()
        if (value == challengeQualifiedValue) return false
        challengeQualifiedValue = value
        return true
    }

    fun checkpoint(depth: Float, score: Int): RunProgress {
        requireActive()
        require(score >= 0) { "score must not be negative" }
        if (depth.isFinite()) depthValue = max(depthValue, max(0f, depth))
        scoreValue = max(scoreValue, score)
        return progress
    }

    fun recordProgress(depth: Float, score: Int): RunProgress = checkpoint(depth, score)

    fun settle(
        reason: RunTerminalReason = RunTerminalReason.COMPLETED,
        disposition: RunDisposition = RunDisposition.COMPLETED,
        dailyBonus: Int = if (disposition == RunDisposition.COMPLETED && dailyEligible) DailyReward else 0,
    ): RunResult {
        resultValue?.let { return it }
        require(disposition != RunDisposition.ACTIVE) { "a terminal disposition is required" }
        require(dailyBonus >= 0) { "daily bonus must not be negative" }
        val effectiveDisposition = if (
            reason == RunTerminalReason.ABANDONED || reason == RunTerminalReason.MANUAL
        ) {
            RunDisposition.ABANDONED
        } else {
            disposition
        }
        if (effectiveDisposition == RunDisposition.COMPLETED && dailyEligible && dailyBonus > 0) {
            awardBonus("daily:$settlementDay", dailyBonus, BonusCategory.DAILY)
        }
        terminalReasonValue = reason
        dispositionValue = effectiveDisposition
        resultValue = makeResult()
        return resultValue!!
    }

    fun abandon(reason: RunTerminalReason = RunTerminalReason.ABANDONED): RunResult =
        settle(reason, RunDisposition.ABANDONED, 0)

    fun snapshot(): RunLedgerSnapshot = RunLedgerSnapshot(
        runId = runId,
        settlementDay = settlementDay,
        dailyEligible = dailyEligible,
        depth = depthValue,
        score = scoreValue,
        collectedPearls = collectedPearlsValue,
        milestonePearls = milestonePearlsValue,
        bossPearls = bossPearlsValue,
        challengePearls = challengePearlsValue,
        otherPearls = otherPearlsValue,
        dailyPearls = dailyPearlsValue,
        walletPearls = walletPearlsValue,
        challengeQualified = challengeQualifiedValue,
        bonusAwards = bonusAwards,
        terminalReason = terminalReasonValue,
        disposition = dispositionValue,
        result = resultValue,
    )

    private fun makeResult(): RunResult = RunResult(
        runId = runId,
        disposition = dispositionValue,
        reason = terminalReasonValue ?: RunTerminalReason.UNKNOWN,
        settlementDay = settlementDay,
        dailyEligible = dailyEligible,
        dailyAwarded = dailyPearlsValue > 0,
        dailyPearls = dailyPearlsValue,
        collectedPearls = collectedPearlsValue,
        milestonePearls = milestonePearlsValue,
        bossPearls = bossPearlsValue,
        challengePearls = challengePearlsValue,
        otherPearls = otherPearlsValue,
        preDailyRunPearls = preDailyRunPearls,
        displayedPearls = displayedPearls,
        walletPearls = walletPearlsValue,
        score = scoreValue,
        depth = depthValue,
        challengeQualified = challengeQualifiedValue,
        bonusAwards = bonusAwards,
    )

    private fun requireActive() {
        require(isActive) { "run is already terminal" }
    }

    companion object {
        const val DailyReward = 25
        const val ChallengeReward = 40
    }
}

private fun nonNegative(value: Int): Int = if (value < 0) 0 else value

private fun inferBonusCategory(key: String): BonusCategory {
    val normalized = key.lowercase()
    return when {
        "challenge" in normalized -> BonusCategory.CHALLENGE
        "boss" in normalized || "leviathan" in normalized -> BonusCategory.BOSS
        "milestone" in normalized -> BonusCategory.MILESTONE
        else -> BonusCategory.OTHER
    }
}

private fun parseBonusCategory(value: String): BonusCategory = try {
    BonusCategory.valueOf(value)
} catch (_: IllegalArgumentException) {
    when (value.lowercase()) {
        "challenge" -> BonusCategory.CHALLENGE
        "boss", "leviathan" -> BonusCategory.BOSS
        "milestone" -> BonusCategory.MILESTONE
        "daily" -> BonusCategory.DAILY
        else -> throw IllegalArgumentException("invalid bonus category")
    }
}

internal fun validateSnapshot(snapshot: RunLedgerSnapshot) {
    require(snapshot.settlementDay >= 0) { "invalid settlement day" }
    require(snapshot.depth.isFinite() && snapshot.depth >= 0f) { "invalid depth" }
    require(snapshot.score >= 0) { "invalid score" }
    require(snapshot.collectedPearls >= 0) { "invalid collected pearls" }
    require(snapshot.milestonePearls >= 0) { "invalid milestone pearls" }
    require(snapshot.bossPearls >= 0) { "invalid boss pearls" }
    require(snapshot.challengePearls >= 0) { "invalid challenge pearls" }
    require(snapshot.otherPearls >= 0) { "invalid other pearls" }
    require(snapshot.dailyPearls >= 0) { "invalid daily pearls" }
    require(snapshot.walletPearls >= 0) { "invalid wallet pearls" }
    val keys = HashSet<String>()
    for (award in snapshot.bonusAwards) {
        require(award.key.isNotBlank() && keys.add(award.key)) { "invalid bonus keys" }
    }
    val sums = HashMap<BonusCategory, Int>()
    for (award in snapshot.bonusAwards) sums[award.category] = (sums[award.category] ?: 0) + award.pearls
    require((sums[BonusCategory.MILESTONE] ?: 0) == snapshot.milestonePearls) { "invalid milestone total" }
    require((sums[BonusCategory.BOSS] ?: 0) == snapshot.bossPearls) { "invalid boss total" }
    require((sums[BonusCategory.CHALLENGE] ?: 0) == snapshot.challengePearls) { "invalid challenge total" }
    require((sums[BonusCategory.DAILY] ?: 0) == snapshot.dailyPearls) { "invalid daily total" }
    require((sums[BonusCategory.OTHER] ?: 0) == snapshot.otherPearls) { "invalid other total" }
    require(snapshot.challengeQualified || snapshot.challengePearls == 0) { "invalid challenge qualification" }
    if (snapshot.disposition == RunDisposition.ACTIVE) {
        require(snapshot.terminalReason == null && snapshot.result == null) { "active snapshot is terminal" }
    } else {
        require(snapshot.terminalReason != null && snapshot.result != null) { "terminal snapshot is incomplete" }
    }
    snapshot.result?.let { validateResult(it, snapshot) }
}

private fun validateResult(result: RunResult, snapshot: RunLedgerSnapshot) {
    require(result.runId == snapshot.runId) { "result run id mismatch" }
    require(result.disposition == snapshot.disposition) { "result disposition mismatch" }
    require(result.reason == snapshot.terminalReason) { "result reason mismatch" }
    require(result.settlementDay == snapshot.settlementDay) { "result day mismatch" }
    require(result.dailyEligible == snapshot.dailyEligible) { "result eligibility mismatch" }
    require(result.dailyPearls == snapshot.dailyPearls) { "result daily total mismatch" }
    require(result.collectedPearls == snapshot.collectedPearls) { "result collected total mismatch" }
    require(result.milestonePearls == snapshot.milestonePearls) { "result milestone total mismatch" }
    require(result.bossPearls == snapshot.bossPearls) { "result boss total mismatch" }
    require(result.challengePearls == snapshot.challengePearls) { "result challenge total mismatch" }
    require(result.otherPearls == snapshot.otherPearls) { "result other total mismatch" }
    require(result.preDailyRunPearls == snapshot.collectedPearls + snapshot.milestonePearls + snapshot.bossPearls + snapshot.challengePearls + snapshot.otherPearls) { "result pre-daily total mismatch" }
    require(result.displayedPearls == result.preDailyRunPearls + result.dailyPearls) { "result displayed total mismatch" }
    require(result.walletPearls == snapshot.walletPearls) { "result wallet mismatch" }
    require(result.score == snapshot.score && result.depth == snapshot.depth) { "result progress mismatch" }
    require(result.challengeQualified == snapshot.challengeQualified) { "result challenge state mismatch" }
    require(result.bonusAwards == snapshot.bonusAwards) { "result bonus state mismatch" }
}
