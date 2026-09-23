package com.depthdiver

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
}