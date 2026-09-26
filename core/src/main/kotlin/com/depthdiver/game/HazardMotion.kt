package com.depthdiver.game

import kotlin.math.abs

internal const val VORTEX_PULL = 9f

/** Signed sideways acceleration applied to the player by a current, 0 outside its radius. */
internal fun vortexPush(dx: Float, dy: Float, radius: Float, strength: Float = VORTEX_PULL): Float {
    if (!dx.isFinite() || !dy.isFinite() || !radius.isFinite() || !strength.isFinite()) return 0f
    if (radius <= 0f || strength <= 0f) return 0f
    val distance = kotlin.math.sqrt(dx * dx + dy * dy)
    if (distance >= radius || distance <= 1e-4f) return 0f
    val falloff = 1f - distance / radius
    val side = if (dx >= 0f) -1f else 1f
    return side * strength * falloff * falloff
}

/** Bounded approach speed so a homing hazard can be outrun. */
internal fun homingStep(dx: Float, speed: Float, delta: Float): Float {
    if (!dx.isFinite() || !speed.isFinite() || !delta.isFinite()) return 0f
    if (speed <= 0f || delta <= 0f) return 0f
    val maxStep = speed * delta
    return when {
        abs(dx) <= maxStep -> dx
        else -> if (dx >= 0f) maxStep else -maxStep
    }
}

/** Clamps a position to the playfield with a small margin. */
internal fun clampToWorld(x: Float, halfWidth: Float, worldWidth: Float): Float {
    val lower = halfWidth.coerceAtMost(worldWidth / 2f)
    val upper = worldWidth - lower
    if (upper <= lower) return worldWidth / 2f
    return x.coerceIn(lower, upper)
}
