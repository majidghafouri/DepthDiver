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
    fun musicDepthFactorGrowsWithDepthAndClamps() {
        assertEquals(0f, musicDepthFactor(0f, 200f), 1e-4f)
        assertEquals(0.5f, musicDepthFactor(100f, 200f), 1e-4f)
        assertEquals(1f, musicDepthFactor(9999f, 200f), 1e-4f)
        assertEquals(0f, musicDepthFactor(-50f, 200f), 1e-4f)
    }

    @Test
    fun strainRisesAsOxygenFalls() {
        assertEquals(0f, strainFor(1f), 1e-4f)
        assertEquals(1f, strainFor(0f), 1e-4f)
        assertTrue(strainFor(0.2f) > strainFor(0.8f))
        assertEquals(0f, strainFor(1.5f), 1e-4f)
        assertEquals(1f, strainFor(-1f), 1e-4f)
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
    fun bedStaysCalmAcrossTheWholeDepthRange() {
        // The regression this replaces: the bed used to thin out as the danger
        // layer came up. It must never drop below the floor.
        val shallow = MusicMixer.mixFor(0f)
        val middle = MusicMixer.mixFor(0.5f)
        val deepest = MusicMixer.mixFor(1f)
        assertTrue(shallow.bed >= MusicMixer.BED_FLOOR - 1e-4f)
        assertTrue(middle.bed >= MusicMixer.BED_FLOOR - 1e-4f)
        assertTrue(deepest.bed >= MusicMixer.BED_FLOOR - 1e-4f)
    }

    @Test
    fun deepPadOpensUpAsDepthIncreases() {
        val shallow = MusicMixer.mixFor(0f)
        val deep = MusicMixer.mixFor(1f)
        assertEquals(0f, shallow.deep, 1e-4f)
        assertTrue(deep.deep > shallow.deep)
    }

    @Test
    fun mixIsMonotonicInDepth() {
        var previous = MusicMixer.mixFor(0f)
        var step = 0.05f
        while (step <= 1.0001f) {
            val current = MusicMixer.mixFor(step)
            assertTrue("deep dipped at $step", current.deep >= previous.deep - 1e-4f)
            previous = current
            step += 0.05f
        }
    }

    @Test
    fun musicRespondsSlowlyInBothDirections() {
        val rise = MusicDirector()
        rise.update(1f, 0.25f)
        assertTrue(
            "pad opened ${rise.depthFactor} in 0.25s; it should creep, not swell",
            rise.depthFactor < 0.2f
        )
        val fall = MusicDirector()
        fall.update(1f, 30f)
        val opened = fall.depthFactor
        fall.update(0f, 0.25f)
        assertTrue("pad should ease back down slowly", fall.depthFactor < opened)
    }

    @Test
    fun musicDirectorResetsToShallowMix() {
        val director = MusicDirector()
        director.update(1f, 10f)
        director.reset()
        assertEquals(0f, director.depthFactor, 1e-4f)
        assertEquals(0f, director.mix.deep, 1e-4f)
        assertEquals(MusicMixer.mixFor(0f).bed, director.mix.bed, 1e-4f)
    }

    @Test
    fun heartbeatOnlySurfacesWhenAirIsActuallyLow() {
        // Guards the direction: a double strainFor() inversion used to make the
        // heartbeat play at full oxygen and stay silent when air ran out.
        fun urgent(oxygenRatio: Float) = strainFor(oxygenRatio) >= HEARTBEAT_STRAIN_THRESHOLD
        assertFalse("heartbeat must stay silent at full oxygen", urgent(1f))
        assertFalse(urgent(0.8f))
        assertTrue("heartbeat must surface when air is low", urgent(0.2f))
        assertTrue(urgent(0f))
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
