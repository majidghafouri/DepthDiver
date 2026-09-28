package com.depthdiver.analytics

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences

/**
 * Whether the player has asked to see their own run numbers.
 *
 * Off by default, and it stays off until it is turned on. The report is a list
 * of how the player's dives went, which is nobody's business but theirs, so it
 * is not something the game volunteers.
 */
object AnalyticsSettings {

    private const val KEY_VISIBLE = "analytics.showReport"
    private const val KEY_ACKED = "analytics.privacyAcked"

    private var override: Preferences? = null
    private var retained: Preferences? = null

    private fun prefs(): Preferences =
        override ?: retained ?: Gdx.app.getPreferences("depthdiver-analytics").also { retained = it }

    fun useOverride(value: Preferences?) {
        override = value
    }

    fun isVisible(): Boolean = prefs().getBoolean(KEY_VISIBLE, false)

    fun setVisible(value: Boolean) {
        prefs().putBoolean(KEY_VISIBLE, value).putBoolean(KEY_ACKED, true).flush()
    }

    /** True once the player has actually been shown the toggle, which is when
     *  the "this stays on the device" line can honestly be attached to it. */
    fun hasAcknowledged(): Boolean = prefs().getBoolean(KEY_ACKED, false)
}
