package com.depthdiver

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guards the Android-preferences "retained instance" fix: `put`/`flush` must
 * operate on ONE [Preferences] wrapper. [FlakyPreferences] reproduces libGDX's
 * per-wrapper pending editor, under which the old computed-getter code (a fresh
 * wrapper per access) silently lost every write.
 */
class RetentionTest {

    @Test
    fun profileMutatorsPersistEvenWithFlakyPreferences() {
        val prefs = FlakyPreferences()
        Profile.prefsOverride = prefs
        try {
            Profile.setDifficulty(2)
            assertEquals(2, prefs.getInteger("difficulty", -1))

            Profile.addPearls(5)
            Profile.addPearls(7)
            Profile.addLifetimePearls(5)
            Profile.addLifetimePearls(7)
            assertEquals(12, prefs.getInteger("totalPearls", -1))
            assertEquals(12, prefs.getInteger("lifetimePearls", -1))

            Profile.recordDive()
            assertEquals(1, prefs.getInteger("dives", -1))

            Profile.claimDaily(42)
            assertEquals(42, prefs.getInteger("dailyDay", -1))

            Profile.setLevel(Profile.Upgrade.Oxygen, 2)
            assertEquals(2, prefs.getInteger(Profile.Upgrade.Oxygen.key, -1))
        } finally {
            Profile.prefsOverride = null
        }
    }

    @Test
    fun challengeClaimPersistsEvenWithFlakyPreferences() {
        val prefs = FlakyPreferences()
        Challenge.prefsOverride = prefs
        try {
            val active = Challenge.activeFor(424242)
            Challenge.claim(active)
            assertEquals(true, prefs.getBoolean("challenge.${active.day}", false))
        } finally {
            Challenge.prefsOverride = null
        }
    }
}