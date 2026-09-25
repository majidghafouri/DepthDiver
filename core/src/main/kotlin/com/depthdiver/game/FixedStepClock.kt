package com.depthdiver.game

import kotlin.math.min

internal class FixedStepClock(
    val fixedStepSeconds: Float = DEFAULT_FIXED_STEP_SECONDS,
    val maxFrameSeconds: Float = DEFAULT_MAX_FRAME_SECONDS,
    val maxStepsPerFrame: Int = DEFAULT_MAX_STEPS_PER_FRAME,
) {
    private var accumulatedSeconds = 0f
    private var lastDroppedSeconds = 0f

    val backlogSeconds: Float get() = accumulatedSeconds

    val droppedSeconds: Float get() = lastDroppedSeconds

    val pendingSteps: Int get() = (accumulatedSeconds / fixedStepSeconds).toInt().coerceAtLeast(0)

    val alpha: Float get() = (accumulatedSeconds / fixedStepSeconds).coerceIn(0f, 1f)

    init {
        require(fixedStepSeconds.isFinite() && fixedStepSeconds > 0f) { "fixedStepSeconds must be positive and finite" }
        require(maxFrameSeconds.isFinite() && maxFrameSeconds > 0f) { "maxFrameSeconds must be positive and finite" }
        require(maxStepsPerFrame >= 1) { "maxStepsPerFrame must be at least 1" }
    }

    fun frameDeltaSeconds(rawDeltaSeconds: Float): Float =
        if (!rawDeltaSeconds.isFinite() || rawDeltaSeconds <= 0f) 0f else min(rawDeltaSeconds, maxFrameSeconds)

    fun advance(rawDeltaSeconds: Float, onStep: (Float) -> Unit): Int {
        accumulatedSeconds += frameDeltaSeconds(rawDeltaSeconds)
        lastDroppedSeconds = 0f
        var steps = 0
        while (steps < maxStepsPerFrame && accumulatedSeconds >= fixedStepSeconds) {
            accumulatedSeconds -= fixedStepSeconds
            steps++
            onStep(fixedStepSeconds)
        }
        if (steps == maxStepsPerFrame && accumulatedSeconds > 0f) {
            lastDroppedSeconds = accumulatedSeconds
            accumulatedSeconds = 0f
        }
        return steps
    }

    fun reset() {
        accumulatedSeconds = 0f
        lastDroppedSeconds = 0f
    }

    companion object {
        const val DEFAULT_FIXED_STEP_SECONDS = 1f / 60f
        const val DEFAULT_MAX_FRAME_SECONDS = 0.25f
        const val DEFAULT_MAX_STEPS_PER_FRAME = 15
    }
}
