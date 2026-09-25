package com.depthdiver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameplaySafetyTest {

    @Test
    fun frameDeltaIsBoundedAndInvalidValuesAreIgnored() {
        assertEquals(0.02f, safeFrameDelta(0.02f))
        assertEquals(MAX_FRAME_DELTA, safeFrameDelta(10f))
        assertEquals(0f, safeFrameDelta(-1f))
        assertEquals(0f, safeFrameDelta(Float.NaN))
        assertEquals(0f, safeFrameDelta(Float.POSITIVE_INFINITY))
    }

    @Test
    fun availableShieldActivatesOnceThenBlocks() {
        assertEquals(
            ShieldCollisionResult.ACTIVATED,
            resolveShieldCollision(shieldLevel = 1, shieldActive = false, shieldCooldown = 0f),
        )
        assertEquals(
            ShieldCollisionResult.BLOCKED,
            resolveShieldCollision(shieldLevel = 1, shieldActive = true, shieldCooldown = 4f),
        )
    }

    @Test
    fun collisionIsFatalWithoutAnAvailableShield() {
        assertEquals(
            ShieldCollisionResult.FATAL,
            resolveShieldCollision(shieldLevel = 0, shieldActive = false, shieldCooldown = 0f),
        )
        assertEquals(
            ShieldCollisionResult.FATAL,
            resolveShieldCollision(shieldLevel = 1, shieldActive = false, shieldCooldown = 2f),
        )
    }

    @Test
    fun shieldDurationScalesWithUpgradeLevel() {
        assertEquals(8.5f, shieldDuration(1))
        assertEquals(2.5f, shieldDuration(5))
    }

    @Test
    fun ambienceCannotStartBeforeItsSoundExists() {
        assertFalse(canStartAmbience(muted = false, playing = false, soundAvailable = false))
        assertTrue(canStartAmbience(muted = false, playing = false, soundAvailable = true))
        assertFalse(canStartAmbience(muted = true, playing = false, soundAvailable = true))
        assertFalse(canStartAmbience(muted = false, playing = true, soundAvailable = true))
    }

    @Test
    fun gameOverOnlyActsInsideVisibleButtons() {
        val restart = TouchTarget(cx = 100f, cy = 50f, w = 80f, h = 40f)
        val menu = TouchTarget(cx = 200f, cy = 50f, w = 80f, h = 40f)

        assertEquals(GameOverAction.RESTART, gameOverActionAt(100f, 50f, restart, menu))
        assertEquals(GameOverAction.MENU, gameOverActionAt(200f, 50f, restart, menu))
        assertEquals(GameOverAction.NONE, gameOverActionAt(150f, 50f, restart, menu))
        assertEquals(GameOverAction.NONE, gameOverActionAt(100f, 100f, restart, menu))
    }
}
