package com.depthdiver

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences

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

    fun dives(): Int = prefs().getInteger("dives", 0)

    fun recordDive() {
        val p = prefs()
        p.putInteger("dives", dives() + 1)
        p.flush()
    }

    fun bestDepth(): Float = prefs().getFloat("bestDepth", 0f)

    fun bestScore(): Int = prefs().getInteger("bestScore", 0)

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
}