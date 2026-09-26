package com.depthdiver.game

import java.lang.reflect.Method

/**
 * Optional bridge to libGDX's controller support.
 *
 * libGDX 1.13+ ships gamepad support in the separate `gdx-controllers`
 * artifact, which not every backend (and not every build) has on the
 * classpath. This bridge talks to it reflectively so the game compiles and
 * runs with or without it: when the classes are missing, [poll] simply
 * returns null and the game falls back to keyboard/touch.
 *
 * Nothing here is required for correctness — [GamepadState] and
 * [gamepadDirection] do the real work.
 */
object GamepadBridge {

    private const val CONTROLLERS_CLASS = "com.badlogic.gdx.controllers.Controllers"
    private const val CONTROLLER_CLASS = "com.badlogic.gdx.controllers.Controller"
    private const val LISTENER_CLASS = "com.badlogic.gdx.controllers.ControllerListener"

    private val axisEnum: Class<*>? = runCatching {
        Class.forName("$CONTROLLER_CLASS\$Axis")
    }.getOrNull()
    private val buttonEnum: Class<*>? = runCatching {
        Class.forName("$CONTROLLER_CLASS\$Button")
    }.getOrNull()

    private val buttonNames = mapOf(
        PadButton.A to "A",
        PadButton.B to "B",
        PadButton.X to "X",
        PadButton.Y to "Y",
        PadButton.DPAD_UP to "DPAD_UP",
        PadButton.DPAD_DOWN to "DPAD_DOWN",
        PadButton.DPAD_LEFT to "DPAD_LEFT",
        PadButton.DPAD_RIGHT to "DPAD_RIGHT",
        PadButton.LEFT_BUMPER to "LEFT_BUMPER",
        PadButton.RIGHT_BUMPER to "RIGHT_BUMPER",
        PadButton.LEFT_STICK to "LEFT_STICK",
        PadButton.RIGHT_STICK to "RIGHT_STICK",
        PadButton.START to "START",
        PadButton.BACK to "BACK",
    )

    private val getControllers: Method? = runCatching {
        Class.forName(CONTROLLERS_CLASS).getMethod("getControllers")
    }.getOrNull()

    private val getAxis: Method? = runCatching {
        Class.forName(CONTROLLER_CLASS).getMethod("getAxis", axisEnum!!)
    }.getOrNull()

    private val getButton: Method? = runCatching {
        Class.forName(CONTROLLER_CLASS).getMethod("getButton", buttonEnum!!)
    }.getOrNull()

    /** True when libGDX controller support is usable in this build. */
    val available: Boolean =
        getControllers != null && getAxis != null && getButton != null &&
            axisEnum != null && buttonEnum != null

    private val axisConstants: Map<String, Any> by lazy {
        axisEnum?.enumConstants.orEmpty().mapNotNull { c ->
            (c as? Enum<*>)?.let { it.name to c }
        }.toMap()
    }

    private val buttonConstants: Map<String, Any> by lazy {
        buttonEnum?.enumConstants.orEmpty().mapNotNull { c ->
            (c as? Enum<*>)?.let { it.name to c }
        }.toMap()
    }

    private var lastConnectedId: String? = null

    /**
     * Reads the first connected controller.
     *
     * @return a fresh [GamepadState], or null when no controller is available.
     */
    fun poll(): GamepadState? {
        if (!available) return null
        return try {
            @Suppress("UNCHECKED_CAST")
            val controllers = getControllers!!.invoke(null) as? List<Any> ?: return null
            val controller = controllers.firstOrNull() ?: return null
            val id = controller.toString()
            if (id != lastConnectedId) lastConnectedId = id

            var leftX = 0f
            var leftY = 0f
            var rightX = 0f
            var rightY = 0f
            var leftTrigger = 0f
            var rightTrigger = 0f

            fun axis(name: String): Float {
                val constant = axisConstants[name] ?: return 0f
                val value = getAxis!!.invoke(controller, constant) as? Float ?: return 0f
                return if (value.isFinite()) value else 0f
            }

            leftX = axis("LEFT_X")
            leftY = axis("LEFT_Y")
            rightX = axis("RIGHT_X")
            rightY = axis("RIGHT_Y")
            leftTrigger = axis("LEFT_TRIGGER")
            rightTrigger = axis("RIGHT_TRIGGER")

            val held = mutableSetOf<PadButton>()
            for ((button, enumName) in buttonNames) {
                val constant = buttonConstants[enumName] ?: continue
                val pressed = getButton!!.invoke(controller, constant) as? Boolean ?: false
                if (pressed) held += button
            }

            GamepadState(
                leftX = leftX,
                leftY = leftY,
                rightX = rightX,
                rightY = rightY,
                leftTrigger = leftTrigger,
                rightTrigger = rightTrigger,
                buttons = held,
            )
        } catch (_: Exception) {
            null
        } catch (_: LinkageError) {
            null
        }
    }
}
