package com.depthdiver.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GamepadTest {

    private fun approx(expected: Float, actual: Float) =
        assertTrue("expected $expected but was $actual", abs(expected - actual) < 1e-4f)

    @Test
    fun deadZoneZeroesSmallInputs() {
        approx(0f, applyDeadZone(0f))
        approx(0f, applyDeadZone(0.1f))
        approx(0f, applyDeadZone(-0.15f))
    }

    @Test
    fun deadZonePreservesSignAndFullScale() {
        approx(1f, applyDeadZone(1f))
        approx(-1f, applyDeadZone(-1f))
        assertTrue(applyDeadZone(0.6f) > 0f)
        assertTrue(applyDeadZone(-0.6f) < 0f)
    }

    @Test
    fun deadZoneIsContinuousAtTheBoundary() {
        val justUnder = applyDeadZone(PAD_DEAD_ZONE - 0.001f)
        val justOver = applyDeadZone(PAD_DEAD_ZONE + 0.001f)
        assertEquals(0f, justUnder, 1e-4f)
        assertTrue("no jump across the dead zone", justOver < 0.01f)
    }

    @Test
    fun deadZoneRejectsNonFiniteInput() {
        approx(0f, applyDeadZone(Float.NaN))
        approx(0f, applyDeadZone(Float.POSITIVE_INFINITY))
    }

    @Test
    fun deadZoneCanBeConfigured() {
        approx(0f, applyDeadZone(0.3f, deadZone = 0.5f))
        assertTrue(applyDeadZone(0.8f, deadZone = 0.5f) > 0f)
    }

    @Test
    fun directionIsZeroWhenTheStickIsCentered() {
        assertTrue(gamepadDirection(GamepadState()).isZero)
    }

    @Test
    fun directionFollowsTheLeftStick() {
        val right = gamepadDirection(GamepadState(leftX = 1f))
        approx(1f, right.x)
        approx(0f, right.y)

        val down = gamepadDirection(GamepadState(leftY = -1f))
        approx(-1f, down.y)
    }

    @Test
    fun directionIsNormalisedForDiagonals() {
        val diagonal = gamepadDirection(GamepadState(leftX = 1f, leftY = 1f))
        approx(1f, diagonal.length)
        assertTrue(diagonal.x > 0f && diagonal.y > 0f)
    }

    @Test
    fun directionIgnoresStickDriftInsideDeadZone() {
        assertTrue(gamepadDirection(GamepadState(leftX = 0.05f, leftY = -0.04f)).isZero)
    }

    @Test
    fun directionRejectsGarbageAxes() {
        assertTrue(gamepadDirection(GamepadState(leftX = Float.NaN)).isZero)
    }

    @Test
    fun idleStateIsDetected() {
        assertTrue(GamepadState().isIdle)
        assertFalse(GamepadState(leftX = 0.9f).isIdle)
        assertFalse(GamepadState(buttons = setOf(PadButton.A)).isIdle)
    }

    @Test
    fun actionsFireOnceOnPress() {
        val idle = GamepadState()
        val pressed = GamepadState(buttons = setOf(PadButton.A))
        assertTrue(PadAction.CONFIRM in gamepadActions(pressed, idle))
        assertTrue(gamepadActions(pressed, pressed).isEmpty())
    }

    @Test
    fun startAndBackBothPause() {
        val idle = GamepadState()
        for (button in listOf(PadButton.START, PadButton.BACK)) {
            val actions = gamepadActions(GamepadState(buttons = setOf(button)), idle)
            assertTrue("$button should pause", PadAction.PAUSE in actions)
        }
    }

    @Test
    fun buttonMapsToIntendedAction() {
        val idle = GamepadState()
        val mapping = mapOf(
            PadButton.B to PadAction.CANCEL,
            PadButton.X to PadAction.RESTART,
            PadButton.Y to PadAction.FRAME_STATS,
            PadButton.LEFT_STICK to PadAction.MUTE,
        )
        for ((button, expected) in mapping) {
            val actions = gamepadActions(GamepadState(buttons = setOf(button)), idle)
            assertTrue("$button should map to $expected", expected in actions)
        }
    }

    @Test
    fun releasingAButtonDoesNotFireAnAction() {
        val pressed = GamepadState(buttons = setOf(PadButton.A))
        val released = GamepadState()
        assertTrue(gamepadActions(released, pressed).isEmpty())
    }

    @Test
    fun simultaneousButtonsAllFire() {
        val actions = gamepadActions(
            GamepadState(buttons = setOf(PadButton.A, PadButton.X)),
            GamepadState(),
        )
        assertTrue(PadAction.CONFIRM in actions)
        assertTrue(PadAction.RESTART in actions)
    }

    @Test
    fun triggersCountAsButtonPresses() {
        assertFalse(GamepadState().triggerPressed())
        assertTrue(GamepadState(rightTrigger = 1f).triggerPressed())
        assertTrue(GamepadState(leftTrigger = 1f).triggerPressed())
        assertFalse(GamepadState(leftTrigger = 0.2f).triggerPressed())
    }

    @Test
    fun bridgeIsOptionalAndNeverThrows() {
        // The gdx-controllers artifact is not on every classpath; polling must
        // degrade to null instead of crashing the game.
        val state = GamepadBridge.poll()
        if (!GamepadBridge.available) {
            assertEquals(null, state)
        }
    }
}
