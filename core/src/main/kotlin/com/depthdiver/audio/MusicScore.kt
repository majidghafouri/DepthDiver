package com.depthdiver.audio

import kotlin.math.min

/**
 * The music, written out as notes instead of as a drone.
 *
 * The old bed held a 55Hz root and four voices for six seconds per chord, with
 * a second 110Hz drone underneath it for depth. Nothing in it moved, and the
 * low sustained tones were the complaint: it was not relaxing, it was oppressive.
 *
 * What is here instead is a plain I-vi-IV-V loop in seventh chords, played as
 * short arpeggiated piano notes with a melody on top. Every note rings out and
 * is gone well before the next one starts, so the texture is light rather than
 * thick, and the lowest note sits at C3 rather than A1 -- there is still a root
 * for the ear to hold on to, just not one that muddies a phone speaker.
 *
 * Nothing here reaches for tension, and the second layer stays bright as it
 * opens with depth, so descending reads as wider rather than darker.
 */
internal object MusicScore {

    const val LOOP_SECONDS = 24f
    const val CHORD_SECONDS = 6f

    /** Cmaj7 - Am7 - Fmaj7 - G. Warm, resolves home, no chord wants resolving away from. */
    private val CHORDS = arrayOf(
        intArrayOf(60, 64, 67, 71),
        intArrayOf(57, 60, 64, 67),
        intArrayOf(53, 57, 60, 64),
        intArrayOf(55, 59, 62, 67),
    )

    /** Up the chord and back down: 0,1,2,3,2,1. */
    private val ARP_PATTERN = intArrayOf(0, 1, 2, 3, 2, 1)

    private const val ARP_STEP = 0.86f
    private const val ARP_START = 0.34f
    private const val ARP_DURATION = 1.55f

    /** One sustained melody note per chord, so the loop has something to remember. */
    private val MELODY = arrayOf(
        intArrayOf(79, 76, 72), // over Cmaj7: G5 E5 C5
        intArrayOf(76, 72, 69), // over Am7:  E5 C5 A4
        intArrayOf(72, 69, 65), // over Fmaj7: C5 A4 F4
        intArrayOf(74, 71, 67), // over G:    D5 B4 G4
    )

    private const val MELODY_AT = 1.9f
    private const val MELODY_DURATION = 3.1f

    /** Constant layer: the arpeggio, the melody and a soft root under each chord. */
    fun bed(t: Float): Float {
        if (t < 0f || t >= LOOP_SECONDS) return 0f
        val index = chordIndexAt(t)
        val local = t - index * CHORD_SECONDS
        val chord = CHORDS[index]
        var wave = 0f

        // Root, an octave below the chord, to give the harmony a floor.
        wave += note(local, 0f, chord[0] - 12, ROOT_DURATION, 0.30f)

        for ((i, step) in ARP_PATTERN.withIndex()) {
            wave += note(local, ARP_START + step * ARP_STEP, chord[step], ARP_DURATION, 0.42f)
        }

        for ((i, melody) in MELODY[index].withIndex()) {
            wave += note(local, MELODY_AT + i * 0.72f, melody, MELODY_DURATION, 0.30f)
        }
        // Seven partials across a chord will happily sum past full scale, and a
        // loop that clips is far more tiresome than one that is quiet.
        return wave * BED_GAIN * loopWindow(t)
    }

    /**
     * Depth layer: a bright open voicing that fades in as the run goes deeper.
     *
     * This used to be a 110Hz drone. It is now a high sustained fifth and a
     * scatter of high bells, so the descent adds sparkle instead of pressure.
     */
    fun deep(t: Float): Float {
        if (t < 0f || t >= LOOP_SECONDS) return 0f
        val tide = 0.78f + 0.22f * sine(t, 0.037f)

        var wave = 0f
        for ((i, midi) in DEEP_CHORD.withIndex()) {
            val swell = linearFade(
                t,
                LOOP_SECONDS,
                LOOP_SECONDS * 0.3f,
                LOOP_SECONDS * 0.3f,
            )
            wave += sine(t, midiToHz(midi)) * (0.17f / (1f + i * 0.5f)) * swell
        }

        // Sparse bells on the upper octave, offset so they do not land with the
        // arpeggio and turn into a rhythm.
        for (i in 0 until BELL_COUNT) {
            val at = i * (LOOP_SECONDS / BELL_COUNT) + 2.3f + (i % 2) * 0.4f
            val age = t - at
            if (age in 0f..BELL_DECAY) {
                wave += note(age, 0f, BELL_MIDI[i % BELL_MIDI.size], BELL_DECAY, 0.16f)
            }
        }
        return wave * DEEP_GAIN * tide * loopWindow(t)
    }

    private fun chordIndexAt(t: Float): Int = ((t / CHORD_SECONDS).toInt()) % CHORDS.size

    /** One piano note struck at [at] seconds into the sample being rendered. */
    private fun note(t: Float, at: Float, midi: Int, duration: Float, gain: Float): Float {
        val age = t - at
        return if (age < 0f) 0f else piano(age, midi, duration, gain)
    }

    /**
     * Trims the very start and end of the loop.
     *
     * The last note rings across the loop point, so without this there is a step
     * in the waveform and an audible click every 24 seconds. 20ms and 60ms are
     * short enough not to be heard as a fade.
     */
    private fun loopWindow(t: Float): Float = min(
        (t / FADE_IN).coerceIn(0f, 1f),
        ((LOOP_SECONDS - t) / FADE_OUT).coerceIn(0f, 1f),
    )

    private const val ROOT_DURATION = 2.6f
    private const val BED_GAIN = 0.52f
    private const val DEEP_GAIN = 0.55f
    private const val FADE_IN = 0.02f
    private const val FADE_OUT = 0.06f

    private val DEEP_CHORD = intArrayOf(72, 76, 79, 84)
    private const val BELL_COUNT = 6
    private const val BELL_DECAY = 3.4f
    private val BELL_MIDI = intArrayOf(88, 84, 91, 86, 88, 93)
}
