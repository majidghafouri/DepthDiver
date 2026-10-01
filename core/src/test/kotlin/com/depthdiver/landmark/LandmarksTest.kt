package com.depthdiver.landmark

import com.depthdiver.Profile
import com.depthdiver.TestPreferences
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Landmarks.
 *
 * The point of them is that a player has something to *reach*, so these tests
 * care about placement and discovery rather than about drawing: a landmark that
 * sits past where runs end, or pays out twice, or cannot be found, fails the
 * thing landmarks are for.
 */
class LandmarksTest {

    @BeforeTest
    fun setUp() {
        Profile.prefsOverride = TestPreferences("depthdiver-landmark-test")
    }

    @AfterTest
    fun tearDown() {
        Profile.prefsOverride = null
    }

    @Test
    fun everyLandmarkIsInsideTheChannel() {
        // A landmark off the side of a 40m-wide world is one that may or may not
        // appear, depending on where the camera happens to be.
        Landmarks.ALL.forEach {
            val left = it.centerXMeters - it.widthMeters / 2f
            val right = it.centerXMeters + it.widthMeters / 2f
            assertTrue(left >= 0f, "${it.id} starts at $left")
            assertTrue(right <= 40f, "${it.id} ends at $right")
        }
    }

    @Test
    fun theFirstLandmarkIsInsideTheDepthPeopleActuallyReach() {
        // The shallow one matters most: a first dive often ends before the 50m
        // milestone, and a landmark past that is decoration.
        assertTrue(Landmarks.ALL.minOf { it.depthMeters } <= 30f, "first landmark at ${Landmarks.ALL.minOf { it.depthMeters }}m is too deep to be seen")
    }

    @Test
    fun landmarksAreOrderedAndDoNotStack() {
        val sorted = Landmarks.ALL.sortedBy { it.depthMeters }
        assertEquals(
            sorted.map { it.id },
            Landmarks.ALL.map { it.id },
            "the catalog should already be in depth order",
        )
        for (i in 0 until Landmarks.ALL.size - 1) {
            val gap = Landmarks.ALL[i + 1].depthMeters - Landmarks.ALL[i].depthMeters
            assertTrue(gap >= 20f, "${Landmarks.ALL[i].id} and ${Landmarks.ALL[i + 1].id} are only ${gap}m apart")
        }
    }

    @Test
    fun everyLandmarkIsFindableAtItsOwnDepth() {
        for (lm in Landmarks.ALL) {
            assertNotNull(Landmarks.byId(lm.id), "${lm.id} is missing from the index")
            assertTrue(Landmarks.reached(lm.depthMeters).contains(lm), "${lm.id} is not reported as reached at its own depth")
        }
    }

    @Test
    fun aFastDescentDoesNotSkipALandmark() {
        // Counted against depth reached rather than checked per frame, so a dive
        // that drops past two in one frame still finds both.
        val found = Landmarks.reached(200f)
        assertEquals(3, found.size)
        assertTrue(found.any { it.id == "old_wreck" })
        assertTrue(found.any { it.id == "reef_arch" })
    }

    @Test
    fun theNextLandmarkIsTheOneBelowYou() {
        assertEquals("reef_arch", Landmarks.nextBelow(25f)?.id)
        assertEquals("old_wreck", Landmarks.nextBelow(100f)?.id)
        assertNull(Landmarks.nextBelow(9999f), "nothing is below the last one")
    }

    @Test
    fun theDistanceToTheNextOneIsMeasured() {
        assertEquals(46f, Landmarks.depthToNext(22f)!!, 0.01f)
        assertNull(Landmarks.depthToNext(9999f))
    }

    @Test
    fun aLandmarkIsFoundOnceAndOnlyOnce() {
        // The reward is the reason to look, so paying twice for one wreck is the
        // sort of thing nobody notices until the numbers are wrong.
        assertTrue(Profile.markLandmarkFound("old_wreck"))
        assertTrue(Profile.hasFoundLandmark("old_wreck"))
        assertTrue(!Profile.markLandmarkFound("old_wreck"), "a second pass must not re-award")
        assertEquals(1, Profile.landmarksFoundCount())
    }

    @Test
    fun findingIsPersistedIndependently() {
        Profile.markLandmarkFound("kelp_shallows")
        Profile.markLandmarkFound("reef_arch")
        assertEquals(setOf("kelp_shallows", "reef_arch"), Profile.foundLandmarks())
        assertEquals(2, Profile.landmarksFoundCount())
    }

    @Test
    fun rewardsGrowWithDepth() {
        // Deeper should be worth more, or there is no reason to keep going.
        val rewards = Landmarks.ALL.map { it.reward }
        for (i in 0 until rewards.size - 1) {
            assertTrue(rewards[i + 1] >= rewards[i], "${Landmarks.ALL[i + 1].id} pays no more than ${Landmarks.ALL[i].id}")
        }
    }

    @Test
    fun noLandmarkCostsAPearl() {
        // A landmark that takes something is a toll, and this is not a toll game.
        Landmarks.ALL.forEach { assertTrue(it.reward >= 0, "${it.id} has a negative reward") }
    }
}
