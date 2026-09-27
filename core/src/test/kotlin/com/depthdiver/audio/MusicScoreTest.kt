package com.depthdiver.audio

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The music used to be a 55Hz root held under four sine voices for six seconds
 * a chord, with a 110Hz drone underneath it for the depth layer. Nothing moved
 * and the low sustained tones were the complaint. These tests pin down the two
 * properties that fix that, so the loop cannot quietly drift back into a drone.
 */
class MusicScoreTest {

    private val rate = SYNTH_SAMPLE_RATE
    private val count = (rate * MusicScore.LOOP_SECONDS).toInt()

    private fun render(sample: (Float) -> Float): FloatArray =
        FloatArray(count) { i -> sample(i / rate.toFloat()) }

    private fun rms(values: FloatArray): Float {
        var sum = 0.0
        for (v in values) sum += (v * v).toDouble()
        return sqrt(sum / values.size).toFloat()
    }

    private fun peak(values: FloatArray): Float {
        var m = 0f
        for (v in values) m = maxOf(m, abs(v))
        return m
    }

    /**
     * Amplitude of a single frequency, by correlating against a complex
     * exponential. This is the measurement that matters: the old bed carried a
     * 55Hz root and the old depth layer a 110Hz drone, and those two sustained
     * tones were the complaint. Counting note attacks cannot see them and a
     * one-pole low pass measures its own settling instead of the content.
     */
    private fun toneAmplitude(values: FloatArray, freq: Float): Float {
        val n = values.size
        var re = 0.0
        var im = 0.0
        for (i in 0 until n) {
            val phase = -2.0 * Math.PI * freq * i / rate
            re += values[i] * kotlin.math.cos(phase)
            im += values[i] * kotlin.math.sin(phase)
        }
        return (2.0 * kotlin.math.hypot(re, im) / n).toFloat()
    }

    @Test
    fun theBedIsAudibleAndNeverClips() {
        val bed = render(MusicScore::bed)
        assertTrue("bed is silent", rms(bed) > 0.02f)
        // Full scale is 1.0. The old bed peaked at 1.167 and was audibly
        // distorting for the whole loop, so this also has to leave headroom.
        assertTrue("bed peaks at ${peak(bed)}, so it is clipping", peak(bed) < 0.95f)
    }

    @Test
    fun theBedCarriesNoSustainedLowRoot() {
        val bed = render(MusicScore::bed)
        val level = rms(bed)
        // The old bed sat on 55Hz at 0.26 gain for the whole loop. It is gone
        // now, so anything audible down there means a drone crept back in.
        for (freq in floatArrayOf(55f, 58.27f, 73.42f)) {
            val share = toneAmplitude(bed, freq) / level
            assertTrue(
                "${freq}Hz is ${(share * 100).toInt()}% of the bed, so the drone is back",
                share < 0.05f,
            )
        }
    }

    @Test
    fun theDepthLayerIsBrightRatherThanALowDrone() {
        val deep = render(MusicScore::deep)
        val level = rms(deep)
        // The old depth layer was 110Hz held under the whole run.
        val share = toneAmplitude(deep, 110f) / level
        assertTrue(
            "the depth layer still has ${(share * 100).toInt()}% of its energy at 110Hz",
            share < 0.1f,
        )
    }

    @Test
    fun theBedIsPlayedAsNotesRatherThanHeldAsAChord() {
        // A sustained pad has a couple of swells across the loop. Struck notes
        // give one per attack: this bed is 6 arpeggio notes, 3 melody notes and
        // a root in each of four chords. The old bed scored 2 here.
        val bed = render(MusicScore::bed)
        val win = (rate * 0.04f).toInt()
        val env = FloatArray(bed.size / win) { b ->
            rms(bed.copyOfRange(b * win, (b + 1) * win))
        }
        val span = 8
        var attacks = 0
        for (i in span until env.size - span) {
            val window = env.copyOfRange(i - span, i + span + 1)
            if (env[i] != window.max()) continue
            val before = env.copyOfRange(maxOf(0, i - span * 3), i).average()
            val after = env.copyOfRange(i + 1, minOf(env.size, i + span * 3)).average()
            if (env[i] > 1.35f * maxOf(before, after)) attacks++
        }
        assertTrue("only $attacks note attacks in 24s, that is a pad not a melody", attacks >= 10)
    }

    @Test
    fun theLoopDoesNotClickWhenItRepeats() {
        for ((name, sample) in listOf("bed" to MusicScore::bed, "deep" to MusicScore::deep)) {
            val values = render(sample)
            val first = abs(values[0])
            val last = abs(values[values.size - 1])
            assertTrue("$name starts at $first, should start from silence", first < 0.02f)
            assertTrue("$name ends at $last, so it clicks every 24 seconds", last < 0.02f)
            // The step across the seam is what the ear hears as a click.
            val seam = abs(values[0] - values[values.size - 1])
            assertTrue("$name has a $seam step at the loop point", seam < 0.02f)
        }
    }

    @Test
    fun theBedStaysWithinTheLoop() {
        assertTrue(MusicScore.bed(-0.01f) == 0f)
        assertTrue(MusicScore.bed(MusicScore.LOOP_SECONDS) == 0f)
        assertTrue(MusicScore.deep(-0.01f) == 0f)
        assertTrue(MusicScore.deep(MusicScore.LOOP_SECONDS) == 0f)
    }

    @Test
    fun pitchIsTuned() {
        assertTrue(abs(midiToHz(69) - 440f) < 0.01f)
        assertTrue(abs(midiToHz(60) - 261.626f) < 0.01f)
        assertTrue(midiToHz(72) > midiToHz(60))
    }

    @Test
    fun aStruckNoteRingsAndThenFades() {
        val duration = 1.5f
        // A raw waveform crosses zero constantly, so a single sample says
        // nothing about loudness. A short window is what the ear hears.
        fun levelAt(t: Float): Float {
            val window = FloatArray((rate * 0.02f).toInt())
            for (i in window.indices) window[i] = piano(t + i / rate.toFloat(), 60, duration)
            return rms(window)
        }
        val struck = levelAt(0.02f)
        val middle = levelAt(duration * 0.4f)
        val tail = levelAt(duration * 1.4f)
        assertTrue("a struck note should sound at once, got $struck", struck > 0.05f)
        assertTrue("should still be sounding mid-note, got $middle", middle > 0.02f)
        assertTrue("should have faded by 1.4x its duration, got $tail", tail < middle * 0.25f)
    }
}
