package com.depthdiver.entity

import com.badlogic.gdx.math.Rectangle

sealed class Hazard {
    abstract val rect: Rectangle

    class Rock(override val rect: Rectangle, val phase: Float) : Hazard()
    class Mine(override val rect: Rectangle, val phase: Float) : Hazard()
    class Jellyfish(
        override val rect: Rectangle,
        val phase: Float,
        val sway: Float,
        val baseX: Float
    ) : Hazard()
class Shark(
        override val rect: Rectangle,
        val phase: Float,
        val isBoss: Boolean = false,
        var health: Float = 1f,
        var maxHealth: Float = 1f,
        var attackPattern: BossPattern = BossPattern.IDLE,
        var attackTimer: Float = 0f,
        var attackCooldown: Float = 0f,
    ) : Hazard()

    enum class BossPattern { IDLE, CHARGE, SWEEP, DIVE, PROJECTILE }

    /** Sweeps horizontally across the screen at a fixed depth band. */
    class Eel(
        override val rect: Rectangle,
        val dir: Int,
        val phase: Float,
        val baseY: Float,
        val spawn: Float,
        val speed: Float = 7.5f
    ) : Hazard()

    /** Slow, telegraphed hunter that closes in horizontally but never faster than [homingSpeed]. */
    class Angler(
        override val rect: Rectangle,
        val phase: Float,
        val homingSpeed: Float
    ) : Hazard()

    /** Non-lethal current that shoves the player sideways; the swirl itself is safe to cross. */
    class Vortex(
        override val rect: Rectangle,
        val phase: Float,
        val radius: Float,
        val strength: Float = 9f
    ) : Hazard()
}