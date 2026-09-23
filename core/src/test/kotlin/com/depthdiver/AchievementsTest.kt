package com.depthdiver

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AchievementsTest {

    @BeforeTest
    fun setUp() {
        Profile.prefsOverride = TestPreferences("depthdiver")
        Achievements.prefsOverride = TestPreferences("depthdiver-achievements")
    }

    @AfterTest
    fun tearDown() {
        Profile.prefsOverride = null
        Achievements.prefsOverride = null
    }

    @Test
    fun depthAchievementLocksOnceBestDepthPasses() {
        assertNull(Achievements.checkAndEarn())
        assertEquals(0, Achievements.count())

        Profile.prefsOverride?.putFloat("bestDepth", 60f)
        assertEquals("REACHED 50 M", Achievements.checkAndEarn())
        assertNull(Achievements.checkAndEarn())
        assertEquals(1, Achievements.count())
    }

    @Test
    fun pearlAchievementsGateOnLifetimePearlsOnly() {
        Profile.prefsOverride?.putInteger("lifetimePearls", 150)
        assertEquals("EARNED 100 PEARLS", Achievements.checkAndEarn())
        assertNull(Achievements.checkAndEarn())
        assertEquals(1, Achievements.count())
    }

    @Test
    fun multipleAchievementsEarnInOrderEachCall() {
        Profile.prefsOverride?.putFloat("bestDepth", 250f)
        Profile.prefsOverride?.putInteger("lifetimePearls", 600)
        Profile.prefsOverride?.putInteger("dives", 30)
        assertEquals("REACHED 50 M", Achievements.checkAndEarn())
        assertEquals("REACHED 100 M", Achievements.checkAndEarn())
        assertEquals("REACHED 200 M", Achievements.checkAndEarn())
        assertEquals("EARNED 100 PEARLS", Achievements.checkAndEarn())
        assertEquals("EARNED 500 PEARLS", Achievements.checkAndEarn())
        assertEquals("COMPLETED 5 DIVES", Achievements.checkAndEarn())
        assertEquals("COMPLETED 20 DIVES", Achievements.checkAndEarn())
        assertEquals(7, Achievements.count())
        assertNull(Achievements.checkAndEarn())
    }
}