package com.depthdiver.audio

import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

internal const val SYNTH_SAMPLE_RATE = 22050

private val TAU = (2.0 * PI).toFloat()

internal fun sine(t: Float, freq: Float): Float = sin(TAU * freq * t)

internal fun saw(t: Float, freq: Float): Float {
    val phase = (t * freq) % 1f
    return 2f * phase - 1f
}

internal fun noise(random: Random): Float = random.nextFloat() * 2f - 1f

internal class OnePole(alpha: Float) {
    private val a = alpha.coerceIn(0f, 1f)
    private var state = 0f

    fun next(input: Float): Float {
        state += (input - state) * a
        return state
    }
}

internal fun attackDecay(t: Float, duration: Float, attack: Float, curve: Float = 1.6f): Float {
    if (t < 0f) return 0f
    val a = attack.coerceAtLeast(1e-4f)
    val d = duration.coerceAtLeast(1e-4f)
    val env = if (t < a) t / a else exp((-curve * (t - a) / d).toDouble()).toFloat()
    return env.coerceIn(0f, 1f)
}

internal fun linearFade(t: Float, duration: Float, fadeIn: Float, fadeOut: Float): Float {
    if (t < 0f || t > duration) return 0f
    val inGain = if (fadeIn <= 0f) 1f else (t / fadeIn).coerceIn(0f, 1f)
    val outGain = if (fadeOut <= 0f) 1f else ((duration - t) / fadeOut).coerceIn(0f, 1f)
    return minOf(inGain, outGain).coerceIn(0f, 1f)
}

internal fun synth(durationSec: Float, sampleRate: Int = SYNTH_SAMPLE_RATE, block: (t: Float, i: Int) -> Float): ByteArray {
    val count = (sampleRate * durationSec).toInt().coerceAtLeast(1)
    val samples = FloatArray(count)
    for (i in 0 until count) {
        samples[i] = block(i / sampleRate.toFloat(), i).coerceIn(-1f, 1f)
    }
    return wavFrom(samples, sampleRate)
}

internal fun wavFrom(samples: FloatArray, sampleRate: Int = SYNTH_SAMPLE_RATE): ByteArray {
    val data = ByteArray(44 + samples.size * 2)
    writeAscii(data, 0, "RIFF")
    writeIntLe(data, 4, 36 + samples.size * 2)
    writeAscii(data, 8, "WAVE")
    writeAscii(data, 12, "fmt ")
    writeIntLe(data, 16, 16)
    writeShortLe(data, 20, 1)
    writeShortLe(data, 22, 1)
    writeIntLe(data, 24, sampleRate)
    writeIntLe(data, 28, sampleRate * 2)
    writeShortLe(data, 32, 2)
    writeShortLe(data, 34, 16)
    writeAscii(data, 36, "data")
    writeIntLe(data, 40, samples.size * 2)
    for (i in samples.indices) {
        val value = (samples[i] * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        data[44 + i * 2] = (value and 0xFF).toByte()
        data[44 + i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
    }
    return data
}

private fun writeAscii(array: ByteArray, offset: Int, text: String) {
    for (i in text.indices) {
        array[offset + i] = text[i].code.toByte()
    }
}

private fun writeShortLe(array: ByteArray, offset: Int, value: Int) {
    array[offset] = (value and 0xFF).toByte()
    array[offset + 1] = ((value shr 8) and 0xFF).toByte()
}

private fun writeIntLe(array: ByteArray, offset: Int, value: Int) {
    array[offset] = (value and 0xFF).toByte()
    array[offset + 1] = ((value shr 8) and 0xFF).toByte()
    array[offset + 2] = ((value shr 16) and 0xFF).toByte()
    array[offset + 3] = ((value shr 24) and 0xFF).toByte()
}
