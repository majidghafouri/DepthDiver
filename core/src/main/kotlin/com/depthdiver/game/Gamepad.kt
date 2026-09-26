package com.depthdiver.game

import kotlin.math.abs

/**
 * Pure gamepad input model. Deliberately free of libGDX so it can be unit
 * tested and reused by any backend (LWJGL3, Android, HTML5).
 *
 * A backend fills [GamepadState] from whatever controller API it has, then
 * [GamepadMapper] turns that into game intent.
 */
data class GamepadState(
    val leftX: Float = 0f,
    val leftY: Float = 0f,
    val rightX: Float = 0f,
    val rightY: Float = 0f,
    val leftTrigger: Float = 0f,
    val rightTrigger: Float = 0f,
    val buttons: Set<PadButton> = emptySet(),
) {
    fun held(button: PadButton): Boolean = button in buttons

    val isIdle: Boolean
        get() = leftX == 0f && leftY == 0f && rightX == 0f && rightY == 0f &&
            leftTrigger == 0f && rightTrigger == 0f && buttons.isEmpty()
}

enum class PadButton {
    A, B, X, Y,
    DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT,
    LEFT_BUMPER, RIGHT_BUMPER,
    LEFT_STICK, RIGHT_STICK,
    START, BACK,
}

/** A named action a gamepad can trigger. */
enum class PadAction { PAUSE, RESUME, CONFIRM, CANCEL, RESTART, FRAME_STATS, MUTE }

/** Default analog dead zone, matching the usual console gamepad feel. */
const val PAD_DEAD_ZONE = 0.22f

/** Analog magnitude below which input is treated as zero. */
fun applyDeadZone(value: Float, deadZone: Float = PAD_DEAD_ZONE): Float {
    if (!value.isFinite()) return 0f
    val limit = deadZone.coerceIn(0f, 0.95f)
    if (abs(value) <= limit) return 0f
    // Re-map the surviving range back onto the full 0..1 so there is no jump
    // in speed at the dead zone boundary.
    val sign = if (value < 0f) -1f else 1f
    val magnitude = (abs(value) - limit) / (1f - limit)
    return sign * magnitude.coerceIn(0f, 1f)
}

/**
 * Maps a [GamepadState] to the same [MoveDirection] the keyboard produces, so
 * the rest of the game keeps a single movement path.
 */
fun gamepadDirection(
    state: GamepadState,
    deadZone: Float = PAD_DEAD_ZONE,
): MoveDirection {
    if (!state.leftX.isFinite() || !state.leftY.isFinite()) return MoveDirection.ZERO
    val x = applyDeadZone(state.leftX, deadZone)
    val y = applyDeadZone(state.leftY, deadZone)
    if (x == 0f && y == 0f) return MoveDirection.ZERO
    return MoveDirection(x, y).normalized()
}

/**
 * Edge-triggered action resolution.
 *
 * [previous] is the state seen last frame, so a button fires once on press
 * instead of every frame it is held.
 */
fun gamepadActions(
    state: GamepadState,
    previous: GamepadState,
): Set<PadAction> {
    val actions = linkedSetOf<PadAction>()

    fun justPressed(button: PadButton): Boolean = state.held(button) && !previous.held(button)

    if (justPressed(PadButton.START)) actions += PadAction.PAUSE
    if (justPressed(PadButton.BACK)) actions += PadAction.PAUSE
    if (justPressed(PadButton.A)) actions += PadAction.CONFIRM
    if (justPressed(PadButton.B)) actions += PadAction.CANCEL
    if (justPressed(PadButton.X)) actions += PadAction.RESTART
    if (justPressed(PadButton.Y)) actions += PadAction.FRAME_STATS
    if (justPressed(PadButton.LEFT_STICK)) actions += PadAction.MUTE
    if (justPressed(PadButton.RIGHT_STICK)) actions += PadAction.FRAME_STATS

    return actions
}

/** Trigger threshold treated as a button press. */
const val PAD_TRIGGER_THRESHOLD = 0.6f

/** True when either trigger is pulled far enough to count as a press. */
fun GamepadState.triggerPressed(threshold: Float = PAD_TRIGGER_THRESHOLD): Boolean =
    applyDeadZone(leftTrigger) >= threshold || applyDeadZone(rightTrigger) >= threshold
