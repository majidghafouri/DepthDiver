package com.depthdiver

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences
import com.depthdiver.run.RunId

data class ProfileRunEffectResult(
    val applied: Boolean,
    val newlyApplied: Boolean,
    val dailyAwarded: Boolean,
    val disposition: String,
    val alreadyApplied: Boolean = !newlyApplied,
)

typealias RunProfileEffectResult = ProfileRunEffectResult

/**
 * Player profile & meta-progression, stored in the shared `depthdiver` prefs.
 * Single source of truth for spendable pearls, lifetime stats, dives and upgrades.
 */
object Profile {

    /**
     * Android [AndroidPreferences] buffers writes in a per-instance [Preferences.Editor];
     * `flush()` is a no-op when the editor is empty (i.e. a fresh wrapper). Every mutator
     * must therefore go through ONE retained instance, otherwise put/flush land on separate
     * wrappers and the write is silently dropped. [prefsOverride] lets unit tests inject an
     * isolated in-memory [Preferences].
     */
    private var retained: Preferences? = null

    internal var prefsOverride: Preferences? = null

    private fun prefs(): Preferences =
        prefsOverride ?: (retained ?: Gdx.app.getPreferences("depthdiver").also { retained = it })

    internal fun preferences(): Preferences = prefs()

    internal fun batch(update: (Preferences) -> Unit) {
        val p = prefs()
        update(p)
        p.flush()
    }

    const val MAX_LEVEL = 5

    enum class Upgrade(val key: String, val baseCost: Int, val label: String) {
        Oxygen("upgradeOxygen", 30, "OXYGEN"),
        Speed("upgradeSpeed", 25, "SPEED"),
        Combo("upgradeCombo", 20, "COMBO"),
        Shield("upgradeShield", 40, "SHIELD"),
        PearlValue("upgradePearlValue", 15, "PEARL VALUE")
    }

    // ---------- wallet / lifetime stats ----------

    fun pearls(): Int = prefs().getInteger("totalPearls", 0)

    fun addPearls(n: Int) {
        val p = prefs()
        p.putInteger("totalPearls", pearls() + n)
        p.flush()
    }

    fun spendPearls(n: Int) = addPearls(-n)

    fun lifetimePearls(): Int = prefs().getInteger("lifetimePearls", 0)

    fun addLifetimePearls(n: Int) {
        val p = prefs()
        p.putInteger("lifetimePearls", lifetimePearls() + n)
        p.flush()
    }

    fun grantPearls(amount: Int) {
        require(amount >= 0) { "amount must be non-negative" }
        batch { p ->
            p.putInteger("totalPearls", p.getInteger("totalPearls", 0) + amount)
            p.putInteger("lifetimePearls", p.getInteger("lifetimePearls", 0) + amount)
        }
    }

    fun dives(): Int = prefs().getInteger("dives", 0)

    fun recordDive() {
        val p = prefs()
        p.putInteger("dives", dives() + 1)
        p.flush()
    }

    fun bestDepth(): Float = prefs().getFloat("bestDepth", 0f)

    fun bestScore(): Int = prefs().getInteger("bestScore", 0)

    /** Best single-run pearl haul — the target for daily pearl challenges. */
    fun bestRunPearls(): Int = prefs().getInteger("bestRunPearls", 0)

    /** Record a run's collected pearls, keeping the best on record. */
    fun noteRun(pearls: Int) {
        if (pearls > bestRunPearls()) {
            val p = prefs()
            p.putInteger("bestRunPearls", pearls)
            p.flush()
        }
    }

    fun applyCompletedRun(
        runId: String,
        depth: Float,
        score: Int,
        preDailyRunPearls: Int,
        settlementDay: Int,
        dailyEligible: Boolean,
    ): ProfileRunEffectResult = applyCompletedRunEffect(
        runId = runId,
        depth = depth,
        score = score,
        preDailyRunPearls = preDailyRunPearls,
        settlementDay = settlementDay,
        dailyEligible = dailyEligible,
    )

    fun applyCompletedRun(
        runId: RunId,
        depth: Float,
        score: Int,
        preDailyRunPearls: Int,
        settlementDay: Int,
        dailyEligible: Boolean,
    ): ProfileRunEffectResult = applyCompletedRun(
        runId.value.toString(),
        depth,
        score,
        preDailyRunPearls,
        settlementDay,
        dailyEligible,
    )

    fun applyAbandonedRun(
        runId: String,
        depth: Float,
        score: Int,
        preDailyRunPearls: Int,
    ): ProfileRunEffectResult = applyAbandonedRunEffect(
        runId = runId,
        depth = depth,
        score = score,
        preDailyRunPearls = preDailyRunPearls,
    )

    fun applyAbandonedRun(
        runId: RunId,
        depth: Float,
        score: Int,
        preDailyRunPearls: Int,
    ): ProfileRunEffectResult = applyAbandonedRun(
        runId.value.toString(),
        depth,
        score,
        preDailyRunPearls,
    )

    fun completedRunApplied(runId: String): Boolean = runEffectStatus(runId) == EFFECT_COMPLETED

    fun completedRunApplied(runId: RunId): Boolean = completedRunApplied(runId.value.toString())

    fun abandonedRunApplied(runId: String): Boolean = runEffectStatus(runId) == EFFECT_ABANDONED

    fun abandonedRunApplied(runId: RunId): Boolean = abandonedRunApplied(runId.value.toString())

    private fun applyCompletedRunEffect(
        runId: String,
        depth: Float,
        score: Int,
        preDailyRunPearls: Int,
        settlementDay: Int,
        dailyEligible: Boolean,
    ): ProfileRunEffectResult {
        val id = requireRunId(runId)
        val p = prefs()
        val status = runEffectStatus(id, p)
        if (status.isNotEmpty()) return existingRunEffect(status, id, p)
        val safeDepth = if (depth.isFinite()) maxOf(0f, depth) else 0f
        val safeScore = maxOf(0, score)
        val safePearls = maxOf(0, preDailyRunPearls)
        val dailyAwarded = dailyEligible && p.getInteger("dailyDay", 0) != settlementDay
        val dailyAmount = if (dailyAwarded) DAILY_BONUS else 0
        p.putInteger("dives", p.getInteger("dives", 0) + 1)
        p.putFloat("bestDepth", maxOf(p.getFloat("bestDepth", 0f), safeDepth))
        p.putInteger("bestScore", maxOf(p.getInteger("bestScore", 0), safeScore))
        p.putInteger("bestRunPearls", maxOf(p.getInteger("bestRunPearls", 0), safePearls))
        if (dailyAwarded) {
            p.putInteger("totalPearls", p.getInteger("totalPearls", 0) + dailyAmount)
            p.putInteger("lifetimePearls", p.getInteger("lifetimePearls", 0) + dailyAmount)
            p.putInteger("dailyDay", settlementDay)
        }
        writeRunEffect(id, EFFECT_COMPLETED, dailyAwarded, p)
        p.flush()
        return ProfileRunEffectResult(
            applied = true,
            newlyApplied = true,
            dailyAwarded = dailyAwarded,
            disposition = EFFECT_COMPLETED,
        )
    }

    private fun applyAbandonedRunEffect(
        runId: String,
        depth: Float,
        score: Int,
        preDailyRunPearls: Int,
    ): ProfileRunEffectResult {
        val id = requireRunId(runId)
        val p = prefs()
        val status = runEffectStatus(id, p)
        if (status.isNotEmpty()) return existingRunEffect(status, id, p)
        val safeDepth = if (depth.isFinite()) maxOf(0f, depth) else 0f
        val safeScore = maxOf(0, score)
        val safePearls = maxOf(0, preDailyRunPearls)
        p.putFloat("bestDepth", maxOf(p.getFloat("bestDepth", 0f), safeDepth))
        p.putInteger("bestScore", maxOf(p.getInteger("bestScore", 0), safeScore))
        p.putInteger("bestRunPearls", maxOf(p.getInteger("bestRunPearls", 0), safePearls))
        writeRunEffect(id, EFFECT_ABANDONED, false, p)
        p.flush()
        return ProfileRunEffectResult(
            applied = true,
            newlyApplied = true,
            dailyAwarded = false,
            disposition = EFFECT_ABANDONED,
        )
    }

    private fun existingRunEffect(
        status: String,
        runId: String,
        p: Preferences,
    ): ProfileRunEffectResult {
        require(status == EFFECT_COMPLETED || status == EFFECT_ABANDONED) { "invalid profile run effect marker" }
        val daily = p.getBoolean(runEffectDailyKey(runId), false)
        return ProfileRunEffectResult(
            applied = true,
            newlyApplied = false,
            dailyAwarded = status == EFFECT_COMPLETED && daily,
            disposition = status,
        )
    }

    private fun writeRunEffect(
        runId: String,
        status: String,
        dailyAwarded: Boolean,
        p: Preferences,
    ) {
        p.putString(RUN_EFFECT_PREFIX + runId, status)
        p.putBoolean(runEffectDailyKey(runId), dailyAwarded)
        if (status == EFFECT_COMPLETED) {
            p.putBoolean(COMPLETED_EFFECT_PREFIX + runId, true)
        } else {
            p.putBoolean(ABANDONED_EFFECT_PREFIX + runId, true)
        }
    }

    private fun runEffectStatus(runId: String, p: Preferences = prefs()): String {
        val id = requireRunId(runId)
        val status = p.getString(RUN_EFFECT_PREFIX + id, "")
        if (status.isNotEmpty() && status != EFFECT_COMPLETED && status != EFFECT_ABANDONED) {
            throw IllegalStateException("invalid profile run effect marker")
        }
        return status
    }

    private fun requireRunId(runId: String): String {
        require(runId.isNotBlank()) { "runId must not be blank" }
        require(runId.none { it.isISOControl() }) { "runId must not contain control characters" }
        return runId
    }

    private fun runEffectDailyKey(runId: String): String = "$RUN_EFFECT_PREFIX$runId.daily"

    // ---------- daily bonus ----------

    /** Day index in UTC (24 h buckets) — avoids needing java.time on older Android. */
    fun dailyDay(): Int = (System.currentTimeMillis() / 86_400_000L).toInt()

    fun claimedDailyDay(): Int = prefs().getInteger("dailyDay", 0)

    fun claimDaily(day: Int) {
        val p = prefs()
        p.putInteger("dailyDay", day)
        p.flush()
    }

    // ---------- settings ----------

    /** 0 = EASY, 1 = NORMAL, 2 = HARD. */
    fun difficulty(): Int = prefs().getInteger("difficulty", 1)

    fun setDifficulty(index: Int) {
        val p = prefs()
        p.putInteger("difficulty", index)
        p.flush()
    }

    // ---------- upgrades ----------

    fun level(u: Upgrade): Int = prefs().getInteger(u.key, 0)

    fun setLevel(u: Upgrade, value: Int) {
        val p = prefs()
        p.putInteger(u.key, value)
        p.flush()
    }

    fun isMaxed(u: Upgrade): Boolean = level(u) >= MAX_LEVEL

    /** Cost to buy the next level of [u]; null when already maxed. */
    fun upgradeCost(u: Upgrade): Int? {
        if (isMaxed(u)) return null
        return u.baseCost * (level(u) + 1)
    }

    const val DAILY_BONUS = 25
    const val RUN_EFFECT_PREFIX = "profile.runEffect."
    const val COMPLETED_EFFECT_PREFIX = "profile.completedRun."
    const val ABANDONED_EFFECT_PREFIX = "profile.abandonedRun."
    const val EFFECT_COMPLETED = "completed"
    const val EFFECT_ABANDONED = "abandoned"
}
