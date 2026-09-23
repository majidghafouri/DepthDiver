package com.depthdiver

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences

/**
 * Player profile & meta-progression, stored in the shared `depthdiver` prefs.
 * Single source of truth for spendable pearls, lifetime stats, dives and upgrades.
 */
object Profile {

    private val prefs: Preferences by lazy { Gdx.app.getPreferences("depthdiver") }

    const val MAX_LEVEL = 5

    enum class Upgrade(val key: String, val baseCost: Int, val label: String) {
        Oxygen("upgradeOxygen", 30, "OXYGEN"),
        Speed("upgradeSpeed", 25, "SPEED"),
        Combo("upgradeCombo", 20, "COMBO"),
        Shield("upgradeShield", 40, "SHIELD"),
        PearlValue("upgradePearlValue", 15, "PEARL VALUE")
    }

    // ---------- wallet / lifetime stats ----------

    fun pearls(): Int = prefs.getInteger("totalPearls", 0)

    fun addPearls(n: Int) {
        prefs.putInteger("totalPearls", pearls() + n)
        prefs.flush()
    }

    fun spendPearls(n: Int) = addPearls(-n)

    fun lifetimePearls(): Int = prefs.getInteger("lifetimePearls", 0)

    fun addLifetimePearls(n: Int) {
        prefs.putInteger("lifetimePearls", lifetimePearls() + n)
        prefs.flush()
    }

    fun dives(): Int = prefs.getInteger("dives", 0)

    fun recordDive() {
        prefs.putInteger("dives", dives() + 1)
        prefs.flush()
    }

    fun bestDepth(): Float = prefs.getFloat("bestDepth", 0f)

    fun bestScore(): Int = prefs.getInteger("bestScore", 0)

    // ---------- settings ----------

    /** 0 = EASY, 1 = NORMAL, 2 = HARD. */
    fun difficulty(): Int = prefs.getInteger("difficulty", 1)

    fun setDifficulty(index: Int) {
        prefs.putInteger("difficulty", index)
        prefs.flush()
    }

    // ---------- upgrades ----------

    fun level(u: Upgrade): Int = prefs.getInteger(u.key, 0)

    fun setLevel(u: Upgrade, value: Int) {
        prefs.putInteger(u.key, value)
        prefs.flush()
    }

    fun isMaxed(u: Upgrade): Boolean = level(u) >= MAX_LEVEL

    /** Cost to buy the next level of [u]; null when already maxed. */
    fun upgradeCost(u: Upgrade): Int? {
        if (isMaxed(u)) return null
        return u.baseCost * (level(u) + 1)
    }
}