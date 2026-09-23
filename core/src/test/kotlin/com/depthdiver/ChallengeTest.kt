package com.depthdiver

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ChallengeTest {

    @BeforeTest
    fun setUp() {
        Profile.prefsOverride = TestPreferences("depthdiver")
        Challenge.prefsOverride = TestPreferences("depthdiver-challenge")
    }

    @AfterTest
    fun tearDown() {
        Challenge.prefsOverride = null
    }

    @Test
    fun objectiveIsDeterministicPerDay() {
        val dayA = 1_000_000
        val dayB = 999_999
        assertEquals(Challenge.activeFor(dayA).kind, Challenge.activeFor(dayA).kind)
        assertEquals(Challenge.activeFor(dayA).target, Challenge.activeFor(dayA).target)
        assertNotEquals(Challenge.activeFor(dayA).target, Challenge.activeFor(dayB).target)
    }

    @Test
    fun depthChallengeMetAgainstDepth() {
        val active = Challenge.activeFor(100000)
        if (active.kind == Challenge.Kind.Depth) {
            assertFalse(active.met(active.target - 1f, 0, 0))
            assertTrue(active.met(active.target.toFloat(), 0, 0))
        }
    }

    @Test
    fun pearlsChallengeMetAgainstRunPearls() {
        val day = (0..999_999).first { Challenge.activeFor(it).kind == Challenge.Kind.Pearls }
        val active = Challenge.activeFor(day)
        assertFalse(active.met(999f, active.target - 1, 0))
        assertTrue(active.met(999f, active.target, 0))
    }

    @Test
    fun claimIsPersistentAndIdempotent() {
        val active = Challenge.activeFor(1_000_001)
        assertFalse(Challenge.claimedFor(active))
        Challenge.claim(active)
        assertTrue(Challenge.claimedFor(active))
    }

    @Test
    fun rewardIsFixed() {
        assertEquals(40, Challenge.REWARD)
    }
}