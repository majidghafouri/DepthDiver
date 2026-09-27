package com.depthdiver.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow

/** Equal-tempered pitch. 60 is middle C, 69 is A4 at 440Hz. */
internal fun midiToHz(midi: Int): Float =
    (440.0 * 2.0.pow((midi - 69) / 12.0)).toFloat()

/**
 * A struck-string tone: the sound of the music bed and the melody.
 *
 * The bed used to be built from bare sines held for six seconds at a time, which
 * is a pad, and a pad with a 55Hz root underneath it is a drone. Sustained low
 * tones are the part of a soundtrack people tire of fastest, so the bed is now
 * struck notes that ring and decay instead.
 *
 * Two details do most of the work in making these read as a piano:
 *
 *  - **Inharmonicity.** Real strings are stiff, so their upper partials are
 *    stretched sharp rather than sitting on exact multiples. Partial `n` is
 *    detuned by `1 + stretch * (n - 1)^2`, which is the standard approximation.
 *  - **Partial decay.** The fundamental rings longest and the upper partials
 *    die away first, so a note starts bright and settles into a warm tone as it
 *    fades. Without this the tone stays glassy and synthetic.
 */
internal fun piano(
    t: Float,
    midi: Int,
    duration: Float,
    gain: Float = 1f,
    brightness: Float = 0.44f,
): Float {
    if (t < 0f || duration <= 0f) return 0f
    val age = t
    if (age > duration * 1.8f) return 0f
    val freq = midiToHz(midi)
    if (freq <= 0f) return 0f

    val env = attackDecay(age, duration, PIANO_ATTACK, PIANO_DECAY)
    if (env <= 1e-4f) return 0f

    var wave = 0f
    var amp = 1f
    for (n in 1..PIANO_PARTIALS) {
        // Stiffer strings detune more, so the stretch grows with pitch.
        val stretch = 1f + PIANO_STRETCH * (n - 1) * (n - 1) * (1f + freq / 880f)
        val partial = freq * n * stretch
        // Leave a little room under Nyquist rather than aliasing the top partial.
        if (partial > SYNTH_SAMPLE_RATE * 0.45f) break
        val partialEnv = if (n == 1) {
            1f
        } else {
            exp((-PIANO_PARTIAL_DECAY * (n - 1) * age / duration).toDouble()).toFloat()
        }
        wave += sine(age, partial) * amp * partialEnv
        amp *= brightness
    }
    return wave * env * gain
}

private const val PIANO_PARTIALS = 5
private const val PIANO_STRETCH = 0.0006f
private const val PIANO_PARTIAL_DECAY = 2.4f
private const val PIANO_ATTACK = 0.004f
private const val PIANO_DECAY = 2.2f
