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
        val isBoss: Boolean = false
    ) : Hazard()
}