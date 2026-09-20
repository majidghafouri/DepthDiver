package com.depthdiver.entity

import com.badlogic.gdx.math.Rectangle

sealed class Pickup {
    abstract val rect: Rectangle
    abstract val phase: Float
    var collected: Boolean = false

    class Pearl(override val rect: Rectangle, override val phase: Float) : Pickup()
    class OxygenTank(override val rect: Rectangle, override val phase: Float) : Pickup()
}