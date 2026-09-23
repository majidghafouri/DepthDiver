package com.depthdiver.desktop

import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * LWJGL3 (GLFW) requires its window/GL context to be created on thread 0 on macOS.
 * When the JVM wasn't started with -XstartOnFirstThread, relaunch ourselves on
 * the main thread. Mirrors gdx-liftoff's official StartupHelper.
 */
object StartupHelper {
    private const val JVM_RESTARTED_ARG = "-DjvmIsRestarted"
    private const val JVM_RESTARTED_KEY = "jvmIsRestarted"
    private val restarted = AtomicBoolean(false)

    fun startNewJvmIfRequired(args: Array<String>, mainClass: String) {
        val os = System.getProperty("os.name", "").lowercase(Locale.ROOT)
        if (!os.contains("mac")) return
        if (System.getProperty(JVM_RESTARTED_KEY, "false") == "true") return

        val javaHome = System.getProperty("java.home")
        val separator = File.separator
        val vmPath = javaHome + separator + "bin" + separator + "java"

        val command = arrayListOf(vmPath, JVM_RESTARTED_ARG, "-XstartOnFirstThread", "-cp", System.getProperty("java.class.path"))
        command.add(mainClass)
        command.addAll(args)

        ProcessBuilder(command)
            .directory(File(System.getProperty("user.dir", ".")))
            .redirectErrorStream(true)
            .start()
        restarted.set(true)
    }

    fun hasRestarted(): Boolean = restarted.get()
}
