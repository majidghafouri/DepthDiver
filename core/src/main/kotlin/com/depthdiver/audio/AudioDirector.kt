package com.depthdiver.audio

import kotlin.math.exp

internal fun layerGain(intensity: Float, threshold: Float, softness: Float = 0.3f): Float {
    val s = softness.coerceAtLeast(1e-4f)
    val x = ((intensity - threshold) / s + 0.5f).coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

internal fun smoothToward(current: Float, target: Float, rate: Float, dt: Float): Float {
    if (rate <= 0f || dt <= 0f) return current
    val k = 1f - exp((-rate * dt).toDouble()).toFloat()
    return current + (target - current) * k
}

internal fun intensityFor(depth: Float, depthScale: Float, oxygenRatio: Float, bossActive: Boolean): Float {
    val scale = depthScale.coerceAtLeast(1f)
    val depthPart = (depth / scale).coerceIn(0f, 1f)
    val strain = (1f - oxygenRatio.coerceIn(0f, 1f)).coerceIn(0f, 1f)
    val base = (depthPart * 0.45f + strain * 0.55f).coerceIn(0f, 1f)
    return if (bossActive) maxOf(base, 0.85f) else base
}

internal fun heartbeatPeriod(danger: Float, slow: Float = 1.15f, fast: Float = 0.42f): Float {
    val d = danger.coerceIn(0f, 1f)
    return slow + (fast - slow) * d
}

internal data class LayerMix(val calm: Float, val tension: Float, val danger: Float)

internal object LayerMixer {

    fun mixFor(intensity: Float): LayerMix {
        val i = intensity.coerceIn(0f, 1f)
        return LayerMix(
            calm = (1f - 0.5f * layerGain(i, 0.25f)).coerceIn(0f, 1f),
            tension = layerGain(i, 0.35f),
            danger = layerGain(i, 0.7f)
        )
    }
}

internal class LayerDirector(
    private val riseRate: Float = 1.8f,
    private val fallRate: Float = 0.7f
) {
    var intensity: Float = 0f
        private set

    var mix: LayerMix = LayerMixer.mixFor(0f)
        private set

    fun update(target: Float, dt: Float): LayerMix {
        val t = target.coerceIn(0f, 1f)
        val rate = if (t > intensity) riseRate else fallRate
        intensity = smoothToward(intensity, t, rate, dt)
        mix = LayerMixer.mixFor(intensity)
        return mix
    }

    fun reset() {
        intensity = 0f
        mix = LayerMixer.mixFor(0f)
    }
}

internal class RateLimiter(private val minGap: Float) {
    private var lastAt = -Float.MAX_VALUE

    fun allow(now: Float): Boolean {
        if (now - lastAt < minGap) return false
        lastAt = now
        return true
    }

    fun reset() {
        lastAt = -Float.MAX_VALUE
    }
}
