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

/**
 * How far the run has progressed, used only to open up the gentle upper pad.
 *
 * Deliberately independent of oxygen: running out of air used to drive a
 * swelling noise bed, which is exactly the escalating din this replaced. Air
 * trouble is now carried by the heartbeat and breath SFX instead.
 */
internal fun musicDepthFactor(depth: Float, depthScale: Float): Float {
    val scale = depthScale.coerceAtLeast(1f)
    return (depth / scale).coerceIn(0f, 1f)
}

/** 0 = comfortable, 1 = out of air. Drives heartbeat rate and volume. */
internal fun strainFor(oxygenRatio: Float): Float =
    (1f - oxygenRatio.coerceIn(0f, 1f)).coerceIn(0f, 1f)

internal fun heartbeatPeriod(danger: Float, slow: Float = 1.15f, fast: Float = 0.42f): Float {
    val d = danger.coerceIn(0f, 1f)
    return slow + (fast - slow) * d
}

/** Air has to be this low before the heartbeat surfaces at all. */
internal const val HEARTBEAT_STRAIN_THRESHOLD = 0.55f

internal data class MusicMix(val bed: Float, val deep: Float)

internal object MusicMixer {

    /** The bed is the constant floor of the soundtrack and never drops away. */
    const val BED_FLOOR = 0.82f

    /**
     * The bed holds roughly constant so the music stays calm; the upper pad
     * opens slowly with depth. Neither layer is ever driven by oxygen strain,
     * so the soundtrack cannot build into noise as the run gets harder.
     */
    fun mixFor(depthFactor: Float): MusicMix {
        val d = depthFactor.coerceIn(0f, 1f)
        return MusicMix(
            bed = BED_FLOOR + (1f - BED_FLOOR) * (1f - d),
            // Softness is kept at 2x the threshold so the pad is genuinely
            // silent near the surface instead of leaking a little from the
            // smoothstep's foot, and still creeps in over the whole descent.
            deep = layerGain(d, threshold = 0.3f, softness = 0.6f),
        )
    }
}

/**
 * Smooths the music's response to depth. Both directions are slow on purpose:
 * a fast rise made the pad swell every time the player dropped quickly, which
 * read as the music reacting rather than sitting calmly underneath.
 */
internal class MusicDirector(
    private val riseRate: Float = 0.22f,
    private val fallRate: Float = 0.12f
) {
    var depthFactor: Float = 0f
        private set

    var mix: MusicMix = MusicMixer.mixFor(0f)
        private set

    fun update(target: Float, dt: Float): MusicMix {
        val t = target.coerceIn(0f, 1f)
        val rate = if (t > depthFactor) riseRate else fallRate
        depthFactor = smoothToward(depthFactor, t, rate, dt)
        mix = MusicMixer.mixFor(depthFactor)
        return mix
    }

    fun reset() {
        depthFactor = 0f
        mix = MusicMixer.mixFor(0f)
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
