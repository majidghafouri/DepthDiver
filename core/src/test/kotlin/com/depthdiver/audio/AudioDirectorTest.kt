package com.depthdiver.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioDirectorTest {

    @Test
    fun layerGainIsZeroBelowThresholdAndOneAbove() {
        assertEquals(0f, layerGain(0f, threshold = 0.35f), 1e-4f)
        assertEquals(1f, layerGain(1f, threshold = 0.35f), 1e-4f)
    }

    @Test
    fun layerGainRampsSmoothlyAcrossThreshold() {
        val atThreshold = layerGain(0.35f, threshold = 0.35f)
        val below = layerGain(0.3f, threshold = 0.35f)
        val above = layerGain(0.4f, threshold = 0.35f)
        assertTrue(below < atThreshold)
        assertTrue(atThreshold < above)
        assertTrue(above - atThreshold < 0.3f)
    }

    @Test
    fun layerGainClampsOutOfRangeInput() {
        assertEquals(0f, layerGain(-5f, threshold = 0.5f), 1e-4f)
        assertEquals(1f, layerGain(9f, threshold = 0.5f), 1e-4f)
    }

    @Test
    fun smoothTowardMovesTowardTargetWithoutOvershoot() {
        val once = smoothToward(0f, 1f, rate = 2f, dt = 0.5f)
        assertTrue(once in 0f..1f)
        assertTrue(once > 0f)
    }

    @Test
    fun smoothTowardIsStableForLargeDt() {
        val result = smoothToward(0f, 1f, rate = 10f, dt = 4f)
        assertTrue(result <= 1f)
        assertTrue(result > 0.9f)
    }

    @Test
    fun smoothTowardIgnoresNonPositiveRateOrDt() {
        assertEquals(0.25f, smoothToward(0.25f, 1f, rate = 0f, dt = 1f), 1e-4f)
        assertEquals(0.25f, smoothToward(0.25f, 1f, rate = 2f, dt = 0f), 1e-4f)
    }

    @Test
    fun intensityGrowsWithDepthAndOxygenStrain() {
        val shallowFullAir = intensityFor(0f, 200f, 1f, bossActive = false)
        val deepFullAir = intensityFor(200f, 200f, 1f, bossActive = false)
        val shallowLowAir = intensityFor(0f, 200f, 0.2f, bossActive = false)
        assertEquals(0f, shallowFullAir, 1e-4f)
        assertTrue(deepFullAir > shallowFullAir)
        assertTrue(shallowLowAir > shallowFullAir)
    }

    @Test
    fun bossForcesHighIntensity() {
        val calm = intensityFor(0f, 200f, 1f, bossActive = false)
        val boss = intensityFor(0f, 200f, 1f, bossActive = true)
        assertTrue(boss >= 0.85f)
        assertTrue(boss > calm)
    }

    @Test
    fun intensityStaysInUnitRange() {
        assertEquals(1f, intensityFor(1000f, 200f, 0f, bossActive = true), 1e-4f)
        assertEquals(0f, intensityFor(-50f, 0f, 2f, bossActive = false), 1e-4f)
    }

    @Test
    fun heartbeatAcceleratesWithDanger() {
        val calm = heartbeatPeriod(0f)
        val critical = heartbeatPeriod(1f)
        assertTrue(critical < calm)
        assertEquals(calm, heartbeatPeriod(-1f), 1e-4f)
        assertEquals(critical, heartbeatPeriod(2f), 1e-4f)
    }

    @Test
    fun mixLayersAreOrderedAndMutuallyReasonable() {
        val calmMix = LayerMixer.mixFor(0f)
        val dangerMix = LayerMixer.mixFor(1f)
        assertEquals(1f, calmMix.calm, 1e-4f)
        assertEquals(0f, calmMix.tension, 1e-4f)
        assertEquals(0f, calmMix.danger, 1e-4f)
        assertTrue(dangerMix.tension > calmMix.tension)
        assertTrue(dangerMix.danger > calmMix.danger)
        assertTrue(dangerMix.calm < calmMix.calm)
    }

    @Test
    fun layerDirectorRisesQuicklyAndFallsSlowly() {
        val director = LayerDirector(riseRate = 4f, fallRate = 0.5f)
        director.update(1f, 0.25f)
        val risen = director.intensity
        assertTrue(risen > 0.5f)
        val calmDown = LayerDirector(riseRate = 4f, fallRate = 0.5f)
        calmDown.update(1f, 0.25f)
        calmDown.update(0f, 0.25f)
        assertTrue(calmDown.intensity < risen)
    }

    @Test
    fun layerDirectorResetsToSilence() {
        val director = LayerDirector()
        director.update(1f, 1f)
        director.reset()
        assertEquals(0f, director.intensity, 1e-4f)
        assertEquals(0f, director.mix.tension, 1e-4f)
    }

    @Test
    fun rateLimiterBlocksRapidRepeats() {
        val limiter = RateLimiter(0.3f)
        assertTrue(limiter.allow(0f))
        assertFalse(limiter.allow(0.1f))
        assertFalse(limiter.allow(0.29f))
        assertTrue(limiter.allow(0.31f))
        limiter.reset()
        assertTrue(limiter.allow(0f))
    }

    @Test
    fun synthProducesRiffWavHeaderAndMatchingLength() {
        val bytes = synth(0.05f) { t, _ -> sine(t, 440f) }
        assertEquals(44 + (SYNTH_SAMPLE_RATE * 0.05f).toInt() * 2, bytes.size)
        assertEquals('R', bytes[0].toInt().toChar())
        assertEquals('I', bytes[1].toInt().toChar())
        assertEquals('F', bytes[2].toInt().toChar())
        assertEquals('F', bytes[3].toInt().toChar())
    }

    @Test
    fun attackDecayStartsQuietAndDecays() {
        val start = attackDecay(0f, duration = 1f, attack = 0.1f)
        val peak = attackDecay(0.1f, duration = 1f, attack = 0.1f)
        val tail = attackDecay(0.9f, duration = 1f, attack = 0.1f)
        assertEquals(0f, start, 1e-4f)
        assertTrue(peak > tail)
        assertTrue(tail >= 0f)
    }

    @Test
    fun linearFadeFadesInAndOut() {
        assertEquals(0f, linearFade(0f, 1f, 0.2f, 0.2f), 1e-4f)
        assertEquals(0f, linearFade(1f, 1f, 0.2f, 0.2f), 1e-4f)
        assertEquals(1f, linearFade(0.5f, 1f, 0.2f, 0.2f), 1e-4f)
    }

    @Test
    fun onePoleSmoothsInput() {
        val filter = OnePole(0.5f)
        val first = filter.next(1f)
        val second = filter.next(1f)
        assertTrue(first > 0f)
        assertTrue(second > first)
    }
}
