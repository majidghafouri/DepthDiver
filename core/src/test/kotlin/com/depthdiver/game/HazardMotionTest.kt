package com.depthdiver.game

import com.depthdiver.MAX_CURRENT_PUSH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HazardMotionTest {

    @Test
    fun vortexPushIsZeroOutsideItsRadius() {
        assertEquals(0f, vortexPush(12f, 0f, radius = 6f), 1e-4f)
        assertEquals(0f, vortexPush(-12f, 0f, radius = 6f), 1e-4f)
    }

    @Test
    fun vortexPushPullsThePlayerTowardTheCentre() {
        val fromRight = vortexPush(dx = 4f, dy = 0f, radius = 8f)
        val fromLeft = vortexPush(dx = -4f, dy = 0f, radius = 8f)
        assertTrue(fromRight < 0f)
        assertTrue(fromLeft > 0f)
        assertEquals(-fromRight, fromLeft, 1e-4f)
    }

    @Test
    fun vortexPushFallsOffWithDistance() {
        val near = kotlin.math.abs(vortexPush(dx = 1f, dy = 0f, radius = 8f))
        val far = kotlin.math.abs(vortexPush(dx = 6f, dy = 0f, radius = 8f))
        assertTrue(near > far)
    }

    @Test
    fun vortexPushIsBoundedByItsStrength() {
        val strength = 9f
        val push = vortexPush(dx = 0.5f, dy = 0f, radius = 8f, strength = strength)
        assertTrue(kotlin.math.abs(push) <= strength)
    }

    @Test
    fun vortexPushRejectsDegenerateInput() {
        assertEquals(0f, vortexPush(2f, 0f, radius = 0f), 1e-4f)
        assertEquals(0f, vortexPush(2f, 0f, radius = 8f, strength = 0f), 1e-4f)
        assertEquals(0f, vortexPush(Float.NaN, 0f, radius = 8f), 1e-4f)
        assertEquals(0f, vortexPush(0f, 0f, radius = 8f), 1e-4f)
    }

    @Test
    fun vortexPullNeverExceedsTheGameplayCap() {
        val single = vortexPush(0.5f, 0f, radius = 8f, strength = VORTEX_PULL)
        assertTrue(kotlin.math.abs(single) <= MAX_CURRENT_PUSH)
    }

    @Test
    fun homingStepClosesTheGapWithoutOvershooting() {
        assertEquals(1.5f, homingStep(dx = 10f, speed = 3f, delta = 0.5f), 1e-4f)
        assertEquals(0.5f, homingStep(dx = 0.5f, speed = 3f, delta = 0.5f), 1e-4f)
    }

    @Test
    fun homingStepMovesInTheDirectionOfTheTarget() {
        assertTrue(homingStep(dx = 5f, speed = 2f, delta = 0.1f) > 0f)
        assertTrue(homingStep(dx = -5f, speed = 2f, delta = 0.1f) < 0f)
    }

    @Test
    fun homingStepIsSafeForDegenerateInput() {
        assertEquals(0f, homingStep(dx = 5f, speed = 0f, delta = 0.1f), 1e-4f)
        assertEquals(0f, homingStep(dx = 5f, speed = 2f, delta = 0f), 1e-4f)
        assertEquals(0f, homingStep(dx = Float.NaN, speed = 2f, delta = 0.1f), 1e-4f)
    }

    @Test
    fun aHomingHazardCanAlwaysBeOutrun() {
        val slowestPlayerSpeed = 16f
        val fastestHoming = 2.2f
        assertTrue(fastestHoming < slowestPlayerSpeed)
    }

    @Test
    fun clampToWorldKeepsHazardsOnScreen() {
        assertEquals(3f, clampToWorld(x = -50f, halfWidth = 3f, worldWidth = 30f), 1e-4f)
        assertEquals(27f, clampToWorld(x = 900f, halfWidth = 3f, worldWidth = 30f), 1e-4f)
        assertEquals(15f, clampToWorld(x = 15f, halfWidth = 3f, worldWidth = 30f), 1e-4f)
    }

    @Test
    fun clampToWorldCopesWithOversizedHazards() {
        assertEquals(15f, clampToWorld(x = 15f, halfWidth = 40f, worldWidth = 30f), 1e-4f)
    }
}
