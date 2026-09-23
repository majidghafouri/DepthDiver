package com.depthdiver

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences

/** Milestone achievements checked against the [Profile] lifetime stats, persisted locally. */
object Achievements {

    data class Def(val id: String, val name: String, val check: () -> Boolean)

    private val prefs: Preferences
        get() = prefsOverride ?: Gdx.app.getPreferences("depthdiver-achievements")

    /** Test seam: lets unit tests inject an isolated in-memory [Preferences]. */
    internal var prefsOverride: Preferences? = null

    val ALL: List<Def> = listOf(
        Def("depth50", "REACHED 50 M", { Profile.bestDepth() >= 50f }),
        Def("depth100", "REACHED 100 M", { Profile.bestDepth() >= 100f }),
        Def("depth200", "REACHED 200 M", { Profile.bestDepth() >= 200f }),
        Def("pearl100", "EARNED 100 PEARLS", { Profile.lifetimePearls() >= 100 }),
        Def("pearl500", "EARNED 500 PEARLS", { Profile.lifetimePearls() >= 500 }),
        Def("pearl1000", "EARNED 1000 PEARLS", { Profile.lifetimePearls() >= 1000 }),
        Def("dive5", "COMPLETED 5 DIVES", { Profile.dives() >= 5 }),
        Def("dive20", "COMPLETED 20 DIVES", { Profile.dives() >= 20 }),
        Def("dive50", "COMPLETED 50 DIVES", { Profile.dives() >= 50 })
    )

    fun isUnlocked(def: Def): Boolean = prefs.getBoolean(def.id, false)

    fun count(): Int = ALL.count { isUnlocked(it) }

    /** Earn any achievements whose conditions now hold; returns the name of the first one newly earned, if any. */
    fun checkAndEarn(): String? {
        for (def in ALL) {
            if (!isUnlocked(def) && def.check()) {
                prefs.putBoolean(def.id, true)
                prefs.flush()
                return def.name
            }
        }
        return null
    }
}