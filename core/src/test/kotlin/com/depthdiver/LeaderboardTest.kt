package com.depthdiver

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LeaderboardTest {

    @BeforeTest
    fun setUp() {
        Leaderboard.prefsOverride = TestPreferences("depthdiver-leaderboard")
    }

    @AfterTest
    fun tearDown() {
        Leaderboard.prefsOverride = null
    }

    @Test
    fun emptyBoardQualifiesPositiveScoresOnly() {
        assertTrue(Leaderboard.qualifies(1, 1f))
        assertFalse(Leaderboard.qualifies(0, 1f))
        assertFalse(Leaderboard.qualifies(-5, 1f))
    }

    @Test
    fun submitKeepsTopFiveSortedAndDropsTheRest() {
        Leaderboard.submit(10, 5f)
        Leaderboard.submit(99, 30f)
        Leaderboard.submit(50, 20f)
        Leaderboard.submit(33, 15f)
        Leaderboard.submit(77, 25f)
        Leaderboard.submit(1, 2f)

        val top = Leaderboard.top()
        assertEquals(5, top.size)
        assertEquals(listOf(99, 77, 50, 33, 10), top.map { it.score })
        assertFalse(Leaderboard.qualifies(5, 3f))
    }

    @Test
    fun equalScorePrefersGreaterDepth() {
        Leaderboard.submit(100, 10f)
        Leaderboard.submit(100, 20f)
        assertEquals(20f, Leaderboard.top().first().depth, 0.001f)
    }

    @Test
    fun qualifyingEntryShiftsLeaderboard() {
        Leaderboard.submit(30, 5f)
        Leaderboard.submit(50, 8f)
        assertTrue(Leaderboard.qualifies(40, 7f))
        Leaderboard.submit(40, 7f)
        assertEquals(listOf(50, 40, 30), Leaderboard.top().map { it.score })
    }
}