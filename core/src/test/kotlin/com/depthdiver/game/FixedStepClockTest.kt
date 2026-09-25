package com.depthdiver.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FixedStepClockTest {

    @Test
    fun invalidDeltasAreIgnoredEntirely() {
        val clock = FixedStepClock()
        val invalid = listOf(-1f, -0.0001f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)

        for (raw in invalid) {
            assertEquals(0, clock.advance(raw) { }, "delta $raw")
            assertEquals(0f, clock.backlogSeconds, "delta $raw")
            assertEquals(0f, clock.droppedSeconds, "delta $raw")
            assertEquals(0, clock.pendingSteps, "delta $raw")
            assertEquals(0f, clock.alpha, "delta $raw")
            assertEquals(0f, clock.frameDeltaSeconds(raw), "delta $raw")
        }
    }

    @Test
    fun everyWholeFixedStepRunsTheCallback() {
        val clock = FixedStepClock(fixedStepSeconds = 0.25f, maxFrameSeconds = 1f, maxStepsPerFrame = 8)
        val steps = mutableListOf<Float>()

        assertEquals(2, clock.advance(0.5f) { steps += it })
        assertEquals(listOf(0.25f, 0.25f), steps)
        assertEquals(0f, clock.backlogSeconds)

        assertEquals(3, clock.advance(0.75f) { steps += it })
        assertEquals(5, steps.size)
        assertEquals(0f, clock.backlogSeconds)
        assertEquals(0f, clock.droppedSeconds)
    }

    @Test
    fun partialTimeCarriesOverUntilItCompletesAStep() {
        val clock = FixedStepClock(fixedStepSeconds = 0.25f, maxFrameSeconds = 1f, maxStepsPerFrame = 8)
        val steps = mutableListOf<Float>()

        assertEquals(0, clock.advance(0.125f) { steps += it })
        assertEquals(0.125f, clock.backlogSeconds)
        assertEquals(0, clock.pendingSteps)
        assertEquals(0.5f, clock.alpha, 1e-4f)

        assertEquals(1, clock.advance(0.125f) { steps += it })
        assertEquals(listOf(0.25f), steps)
        assertEquals(0f, clock.backlogSeconds)
        assertEquals(0f, clock.alpha)
    }

    @Test
    fun aStalledFrameOnlyCreditsTheFrameBudget() {
        val clock = FixedStepClock(fixedStepSeconds = 0.1f, maxFrameSeconds = 0.25f, maxStepsPerFrame = 15)
        var steps = 0

        assertEquals(0.25f, clock.frameDeltaSeconds(10f))
        assertEquals(0.01f, clock.frameDeltaSeconds(0.01f))

        assertEquals(2, clock.advance(10f) { steps++ })
        assertEquals(2, steps)
        assertEquals(0.05f, clock.backlogSeconds, 1e-4f)
        assertEquals(0f, clock.droppedSeconds)
    }

    @Test
    fun catchUpWorkIsCappedAndTheExcessBacklogIsDropped() {
        val clock = FixedStepClock(fixedStepSeconds = 0.1f, maxFrameSeconds = 1f, maxStepsPerFrame = 3)
        var steps = 0

        assertEquals(3, clock.advance(0.9f) { steps++ })
        assertEquals(3, steps)
        assertEquals(0f, clock.backlogSeconds)
        assertEquals(0.6f, clock.droppedSeconds, 1e-4f)

        assertEquals(0, clock.advance(0.05f) { steps++ })
        assertEquals(3, steps)
        assertEquals(0f, clock.droppedSeconds)

        assertEquals(1, clock.advance(0.1f) { steps++ })
        assertEquals(4, steps)
    }

    @Test
    fun resetDropsPendingPartialTime() {
        val clock = FixedStepClock(fixedStepSeconds = 0.25f, maxFrameSeconds = 1f, maxStepsPerFrame = 8)
        var steps = 0

        assertEquals(0, clock.advance(0.125f) { steps++ })
        assertEquals(0.125f, clock.backlogSeconds)

        clock.reset()
        assertEquals(0f, clock.backlogSeconds)
        assertEquals(0f, clock.droppedSeconds)
        assertEquals(0f, clock.alpha)

        assertEquals(0, clock.advance(0.125f) { steps++ })
        assertEquals(1, clock.advance(0.125f) { steps++ })
        assertEquals(1, steps)
        assertEquals(0f, clock.backlogSeconds)
    }

    @Test
    fun theDefaultClockRunsOneStepPerSixtiethOfASecond() {
        val clock = FixedStepClock()
        assertEquals(1f / 60f, clock.fixedStepSeconds)
        assertEquals(0.25f, clock.maxFrameSeconds)
        assertEquals(15, clock.maxStepsPerFrame)

        var steps = 0
        repeat(60) { assertEquals(1, clock.advance(1f / 60f) { steps++ }) }
        assertEquals(60, steps)
        assertEquals(0f, clock.backlogSeconds)
        assertEquals(0f, clock.alpha)
    }

    @Test
    fun theDefaultClockNeverQueuesUnboundedCatchUp() {
        val clock = FixedStepClock()
        var steps = 0

        val ran = clock.advance(30f) { steps++ }
        assertEquals(ran, steps)
        assertTrue(ran <= clock.maxStepsPerFrame, "ran $ran steps")
        assertTrue(clock.backlogSeconds < clock.fixedStepSeconds, "backlog ${clock.backlogSeconds}")
        assertTrue(clock.droppedSeconds.isFinite())
    }

    @Test
    fun aNonsensicalConfigurationIsRejected() {
        assertFailsWith<IllegalArgumentException> { FixedStepClock(fixedStepSeconds = 0f) }
        assertFailsWith<IllegalArgumentException> { FixedStepClock(fixedStepSeconds = -0.1f) }
        assertFailsWith<IllegalArgumentException> { FixedStepClock(fixedStepSeconds = Float.NaN) }
        assertFailsWith<IllegalArgumentException> { FixedStepClock(maxFrameSeconds = 0f) }
        assertFailsWith<IllegalArgumentException> { FixedStepClock(maxFrameSeconds = Float.POSITIVE_INFINITY) }
        assertFailsWith<IllegalArgumentException> { FixedStepClock(maxStepsPerFrame = 0) }
    }
}
