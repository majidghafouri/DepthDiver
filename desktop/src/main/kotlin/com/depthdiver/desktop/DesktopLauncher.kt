package com.depthdiver.desktop

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.depthdiver.DepthDiverGame

fun main() {
    StartupHelper.startNewJvmIfRequired(args = arrayOf(), mainClass = "com.depthdiver.desktop.DesktopLauncherKt")
    if (StartupHelper.hasRestarted()) return

    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("DepthDiver")
        setWindowedMode(800, 600)
        useVsync(true)
        setForegroundFPS(60)
    }
    Lwjgl3Application(DepthDiverGame(), config)
}