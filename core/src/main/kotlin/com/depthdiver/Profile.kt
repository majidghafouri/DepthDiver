package com.depthdiver

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences
import com.depthdiver.retention.Streak
import com.depthdiver.retention.StreakState
import com.depthdiver.retention.StreakUpdate
import com.depthdiver.run.RunId
import com.depthdiver.run.RunTerminalReason

data class ProfileRunEffectResult(
    val applied: Boolean,
    val newlyApplied: Boolean,
    val dailyAwarded: Boolean,
    val disposition: String,
    val alreadyApplied: Boolean = !newlyApplied,
    /** Day count after this run, or 0 when the streak did not move. */
    val streakDays: Int = 0,
    /** Pearls this run paid for crossing a streak milestone. 0 when none. */
    val streakReward: Int = 0,
    /** Milestone day count that paid, or null. */
    val streakMilestone: Int? = null,
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

    // ---------- landmarks ----------

    /**
     * Landmarks found, as ids.
     *
     * Kept as a comma-joined string rather than a set because libGDX's Android
     * preferences have no string-set type and one blob is a single flush instead
     * of one per id.
     */
    fun foundLandmarks(): Set<String> =
        prefs().getString("landmarks.found", "")
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    fun hasFoundLandmark(id: String): Boolean = foundLandmarks().contains(id)

    /**
     * Record a landmark as found. Returns false if it was already recorded, so
     * a reward cannot be claimed twice by passing the same landmark twice.
     */
    fun markLandmarkFound(id: String): Boolean {
        val found = foundLandmarks().toMutableSet()
        if (!found.add(id)) return false
        batch { p -> p.putString("landmarks.found", found.sorted().joinToString(",")) }
        return true
    }

    fun landmarksFoundCount(): Int = foundLandmarks().size

    // ---------- streak ----------

    fun streakState(): StreakState = StreakState(
        current = prefs().getInteger("streak.current", 0),
        best = prefs().getInteger("streak.best", 0),
        lastDay = prefs().getInteger("streak.lastDay", 0),
    )

    fun streak(): Int = streakState().current

    fun bestStreak(): Int = streakState().best

    /**
     * What the most recent streak record did, so the game can announce it.
     *
     * A field rather than part of the settlement result because the settlement
     * result is returned inside the run machinery and the announcement is a
     * presentation concern that belongs here.
     */
    fun lastStreakUpdate(): StreakUpdate = lastStreakUpdate ?: StreakUpdate.Unchanged

    private var lastStreakUpdate: StreakUpdate? = null

    /**
     * Record a run against the streak and return what happened.
     *
     * Idempotent per day, because the settlement path can be replayed and a
     * streak that counted the same day twice would be worth farming.
     */
    fun recordStreakRun(day: Int, reason: RunTerminalReason): StreakUpdate {
        val before = streakState()
        val update = Streak.update(before, day, reason)
        lastStreakUpdate = update
        if (update is StreakUpdate.Unchanged) return update
        val after = Streak.apply(before, day, update)
        val p = prefs()
        p.putInteger("streak.current", after.current)
        p.putInteger("streak.best", after.best)
        p.putInteger("streak.lastDay", after.lastDay)
        p.flush()
        return update
    }

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
        // This is the completed-run path, so the run counts by construction --
        // abandoned runs settle elsewhere and never reach here. Passed as
        // COMPLETED rather than threading a reason that is always the same.
        val streakResult = recordStreakRun(settlementDay, RunTerminalReason.COMPLETED)
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
        // Milestone pearls go on the same write, so a crash between the streak
        // moving and the reward landing cannot leave one without the other.
        val advanced = streakResult as? StreakUpdate.Advanced
        if (advanced != null && advanced.reward > 0) {
            p.putInteger("totalPearls", p.getInteger("totalPearls", 0) + advanced.reward)
            p.putInteger("lifetimePearls", p.getInteger("lifetimePearls", 0) + advanced.reward)
        }
        return ProfileRunEffectResult(
            applied = true,
            newlyApplied = true,
            dailyAwarded = dailyAwarded,
            disposition = EFFECT_COMPLETED,
            streakDays = when (streakResult) {
                is StreakUpdate.Advanced -> streakResult.streak
                is StreakUpdate.Reset -> streakResult.streak
                StreakUpdate.Unchanged -> 0
            },
            streakReward = advanced?.reward ?: 0,
            streakMilestone = advanced?.milestone,
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
    
    /** Master volume [0..1]. */
    fun masterVolume(): Float = prefs().getFloat("masterVolume", 1f)
    
    fun setMasterVolume(v: Float) {
        val p = prefs()
        p.putFloat("masterVolume", v.coerceIn(0f, 1f))
        p.flush()
    }
    
    /** SFX volume [0..1]. */
    fun sfxVolume(): Float = prefs().getFloat("sfxVolume", 1f)
    
    fun setSfxVolume(v: Float) {
        val p = prefs()
        p.putFloat("sfxVolume", v.coerceIn(0f, 1f))
        p.flush()
    }
    
    /** Music/ambience volume [0..1]. */
    fun musicVolume(): Float = prefs().getFloat("musicVolume", 1f)
    
    fun setMusicVolume(v: Float) {
        val p = prefs()
        p.putFloat("musicVolume", v.coerceIn(0f, 1f))
        p.flush()
    }

    /**
     * Music and sound effects are muted independently: someone may want the
     * calm bed playing with the effects silenced, or the reverse.
     */
    fun musicMuted(): Boolean = prefs().getBoolean("musicMuted", false)

    fun setMusicMuted(muted: Boolean) {
        val p = prefs()
        p.putBoolean("musicMuted", muted)
        p.flush()
    }

    fun sfxMuted(): Boolean = prefs().getBoolean("sfxMuted", false)

    fun setSfxMuted(muted: Boolean) {
        val p = prefs()
        p.putBoolean("sfxMuted", muted)
        p.flush()
    }

    /** True only when both channels are off, for the quick master toggle. */
    fun allAudioMuted(): Boolean = musicMuted() && sfxMuted()
    
    /** Reduce motion: disable screen shake, particles, vignette pulse. */
    fun reduceMotion(): Boolean = prefs().getBoolean("reduceMotion", false)
    
    fun setReduceMotion(enabled: Boolean) {
        val p = prefs()
        p.putBoolean("reduceMotion", enabled)
        p.flush()
    }
    
    /** High contrast mode for accessibility. */
    fun highContrast(): Boolean = prefs().getBoolean("highContrast", false)
    
    fun setHighContrast(enabled: Boolean) {
        val p = prefs()
        p.putBoolean("highContrast", enabled)
        p.flush()
    }
    
/** Screen shake toggle (independent of reduceMotion for fine-grained control). */
    fun screenShakeEnabled(): Boolean = prefs().getBoolean("screenShake", true)
    
    fun setScreenShakeEnabled(enabled: Boolean) {
        val p = prefs()
        p.putBoolean("screenShake", enabled)
        p.flush()
    }
    
    /** Current locale code (e.g., "en", "es"). */
    fun locale(): String = prefs().getString("locale", "en")
    
    fun setLocale(localeCode: String) {
        val p = prefs()
        p.putString("locale", localeCode.lowercase())
        p.flush()
        com.depthdiver.common.Strings.setLocale(localeCode)
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
