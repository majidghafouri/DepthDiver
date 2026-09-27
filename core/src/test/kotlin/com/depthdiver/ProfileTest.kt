package com.depthdiver

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileTest {

    @BeforeTest
    fun setUp() {
        Profile.prefsOverride = TestPreferences("depthdiver")
    }

    @AfterTest
    fun tearDown() {
        Profile.prefsOverride = null
    }

    @Test
    fun upgradeCostEscalatesUntilMaxed() {
        Profile.setLevel(Profile.Upgrade.Oxygen, 0)
        assertEquals(30, Profile.upgradeCost(Profile.Upgrade.Oxygen))
        Profile.setLevel(Profile.Upgrade.Oxygen, 1)
        assertEquals(60, Profile.upgradeCost(Profile.Upgrade.Oxygen))
        Profile.setLevel(Profile.Upgrade.Oxygen, Profile.MAX_LEVEL)
        assertNull(Profile.upgradeCost(Profile.Upgrade.Oxygen))
        assertTrue(Profile.isMaxed(Profile.Upgrade.Oxygen))
    }

    @Test
    fun speedUpgradeBaseCostIsLevelScaled() {
        Profile.setLevel(Profile.Upgrade.Speed, 2)
        assertEquals(75, Profile.upgradeCost(Profile.Upgrade.Speed))
    }

    @Test
    fun walletAddAndSpend() {
        assertEquals(0, Profile.pearls())
        Profile.addPearls(25)
        Profile.spendPearls(10)
        assertEquals(15, Profile.pearls())
    }

    @Test
    fun lifetimePearlsAreNeverDeductedBySpends() {
        Profile.addPearls(5)
        Profile.addLifetimePearls(5)
        Profile.spendPearls(3)
        assertEquals(2, Profile.pearls())
        assertEquals(5, Profile.lifetimePearls())
    }

    @Test
    fun dailyBonusTracksTheClaimedDay() {
        val today = Profile.dailyDay()
        assertEquals(today, Profile.dailyDay())
        Profile.claimDaily(today)
        assertEquals(today, Profile.claimedDailyDay())
        Profile.claimDaily(today - 1)
        assertEquals(today - 1, Profile.claimedDailyDay())
    }

    @Test
    fun grantPearlsUpdatesWalletAndLifetimeTogether() {
        Profile.grantPearls(7)
        assertEquals(7, Profile.pearls())
        assertEquals(7, Profile.lifetimePearls())
    }

    @Test
    fun completedRunEffectsAreAppliedOnlyOnce() {
        Profile.applyCompletedRun("run-1", 42f, 120, 19, 100, true)
        Profile.applyCompletedRun("run-1", 999f, 999, 999, 100, true)

        assertEquals(1, Profile.dives())
        assertEquals(42f, Profile.bestDepth())
        assertEquals(120, Profile.bestScore())
        assertEquals(19, Profile.bestRunPearls())
        assertEquals(100, Profile.claimedDailyDay())
        assertEquals(25, Profile.pearls())
        assertEquals(25, Profile.lifetimePearls())
        assertTrue(Profile.completedRunApplied("run-1"))
    }

    @Test
    fun abandonedRunOnlyMovesHighWaterMarks() {
        Profile.applyAbandonedRun("run-2", 33f, 44, 12)
        Profile.applyAbandonedRun("run-2", 1f, 2, 1)

        assertEquals(33f, Profile.bestDepth())
        assertEquals(44, Profile.bestScore())
        assertEquals(12, Profile.bestRunPearls())
        assertEquals(0, Profile.dives())
        assertEquals(0, Profile.claimedDailyDay())
        assertEquals(0, Profile.pearls())
        assertEquals(0, Profile.lifetimePearls())
        assertTrue(Profile.abandonedRunApplied("run-2"))
        assertFalse(Profile.completedRunApplied("run-2"))
    }

    @Test
    fun musicAndSfxMuteIndependently() {
        assertFalse(Profile.musicMuted())
        assertFalse(Profile.sfxMuted())

        Profile.setMusicMuted(true)
        assertTrue(!Profile.sfxMuted(), "music mute must not silence effects")
        assertFalse(Profile.allAudioMuted(), "a silent music channel is not a fully muted app")

        Profile.setSfxMuted(true)
        assertTrue(Profile.allAudioMuted())

        Profile.setMusicMuted(false)
        assertFalse(Profile.allAudioMuted())
    }

    @Test
    fun muteStateIsStoredRatherThanCachedOnTheObject() {
        val store = TestPreferences("depthdiver")
        Profile.prefsOverride = store
        Profile.setMusicMuted(true)

        // A different backing store must not see the flag, which proves the
        // value was written through instead of held in a field on Profile.
        Profile.prefsOverride = TestPreferences("other")
        assertFalse(Profile.musicMuted())

        // Reopening the original store is what a relaunch does: music is still
        // off, effects were never touched.
        Profile.prefsOverride = store
        assertTrue(Profile.musicMuted())
        assertFalse(Profile.sfxMuted())
    }
}
