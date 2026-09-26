package com.depthdiver.simulation

import kotlin.math.sqrt

class PerformanceMonitor {

    data class FrameStats(
        val fps: Float,
        val avgFrameTimeMs: Float,
        val minFrameTimeMs: Float,
        val maxFrameTimeMs: Float,
        val frameTimeVariance: Float,
    )

    private val windowSamples = ArrayDeque<Long>(256)
    private var lastFrameTime = System.nanoTime()
    private var lastReportTime = System.currentTimeMillis()
    private var framesThisWindow = 0
    private var enabled = true
    private var latest: FrameStats = FrameStats(0f, 0f, 0f, 0f, 0f)

    fun startFrame() {
        lastFrameTime = System.nanoTime()
    }

    fun endFrame(): FrameStats? {
        val frameTimeNs = System.nanoTime() - lastFrameTime
        if (frameTimeNs in 0..MAX_FRAME_TIME_NS) {
            windowSamples.addLast(frameTimeNs)
            if (windowSamples.size > MAX_WINDOW_SAMPLES) windowSamples.removeFirst()
        }
        framesThisWindow++

        val nowMs = System.currentTimeMillis()
        val elapsedMs = nowMs - lastReportTime
        if (elapsedMs < REPORT_INTERVAL_MS || framesThisWindow == 0) return null

        latest = summarize(framesThisWindow, elapsedMs)
        framesThisWindow = 0
        lastReportTime = nowMs
        windowSamples.clear()
        return latest
    }

    fun latest(): FrameStats = latest

    fun reset() {
        windowSamples.clear()
        framesThisWindow = 0
        lastFrameTime = System.nanoTime()
        lastReportTime = System.currentTimeMillis()
        latest = FrameStats(0f, 0f, 0f, 0f, 0f)
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) reset()
    }

    fun isEnabled(): Boolean = enabled

    private fun summarize(frames: Int, elapsedMs: Long): FrameStats {
        if (windowSamples.isEmpty()) {
            return FrameStats(0f, 0f, 0f, 0f, 0f)
        }
        var sum = 0L
        var minNs = Long.MAX_VALUE
        var maxNs = 0L
        for (sample in windowSamples) {
            sum += sample
            if (sample < minNs) minNs = sample
            if (sample > maxNs) maxNs = sample
        }
        val count = windowSamples.size
        val avgNs = (sum.toDouble() / count).toFloat()
        var varianceSum = 0.0
        for (sample in windowSamples) {
            val diff = sample - avgNs
            varianceSum += diff * diff
        }
        return FrameStats(
            fps = frames * 1000f / elapsedMs.coerceAtLeast(1),
            avgFrameTimeMs = avgNs / 1_000_000f,
            minFrameTimeMs = minNs / 1_000_000f,
            maxFrameTimeMs = maxNs / 1_000_000f,
            frameTimeVariance = (sqrt(varianceSum / count) / 1_000_000.0).toFloat(),
        )
    }

    private companion object {
        const val REPORT_INTERVAL_MS = 1000L
        const val MAX_WINDOW_SAMPLES = 512
        const val MAX_FRAME_TIME_NS = 2_000_000_000L
    }
}
