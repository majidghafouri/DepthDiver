package com.depthdiver.simulation

import com.badlogic.gdx.Gdx

class PerformanceMonitor {
    private var frameCount = 0
    private var frameTimeSum = 0L
    private var frameTimeMin = Long.MAX_VALUE
    private var frameTimeMax = 0L
    private var lastFrameTime = System.nanoTime()
    private var frameCountSinceLastReport = 0
    private var lastReportTime = System.currentTimeMillis()
    private var enabled = true
    
    data class FrameStats(
        val fps: Float,
        val avgFrameTimeMs: Float,
        val minFrameTimeMs: Float,
        val maxFrameTimeMs: Float,
        val frameTimeVariance: Float,
    )
    
    fun startFrame() {
        lastFrameTime = System.nanoTime()
    }
    
    fun endFrame(): FrameStats? {
        val now = System.nanoTime()
        val frameTimeNs = now - lastFrameTime
        frameTimeSum += frameTimeNs
        frameTimeMin = minOf(frameTimeMin, frameTimeNs)
        frameTimeMax = maxOf(frameTimeMax, frameTimeNs)
        frameCount++
        frameCountSinceLastReport++
        
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastReportTime >= 1000 && frameCountSinceLastReport > 0) {
            val fps = frameCountSinceLastReport * 1000f / (nowMs - lastReportTime)
            val avgMs = frameTimeSum / frameCountSinceLastReport / 1_000_000f
            val minMs = frameTimeMin / 1_000_000f
            val maxMs = frameTimeMax / 1_000_000f
            val variance = calculateVariance()
            
            frameCountSinceLastReport = 0
            lastReportTime = nowMs
            frameTimeSum = 0
            frameTimeMin = Long.MAX_VALUE
            frameTimeMax = 0
            
            return FrameStats(fps, avgMs, minMs, maxMs, variance)
        }
        return null
    }
    
    private fun calculateVariance(): Float {
        // Simplified variance calculation
        if (frameCountSinceLastReport <= 1) return 0f
        val avg = frameTimeSum.toFloat() / frameCountSinceLastReport
        var sumSqDiff = 0f
        // Simplified: we don't store individual frame times, so we approximate
        return 0f
    }
    
    fun getCurrentFPS(): Float {
        val now = System.currentTimeMillis()
        val elapsedSec = (now - lastReportTime) / 1000f
        if (elapsedSec > 0) {
            return frameCountSinceLastReport / elapsedSec
        }
        return 0f
    }
    
    fun getAverageFrameTimeMs(): Float {
        if (frameCountSinceLastReport > 0) {
            return frameTimeSum.toFloat() / frameCountSinceLastReport / 1_000_000f
        }
        return 0f
    }
    
    fun getMinFrameTimeMs(): Float = frameTimeMin / 1_000_000f
    fun getMaxFrameTimeMs(): Float = frameTimeMax / 1_000_000f
    
    fun reset() {
        frameCount = 0
        frameTimeSum = 0
        frameTimeMin = Long.MAX_VALUE
        frameTimeMax = 0
        frameCountSinceLastReport = 0
        lastReportTime = System.currentTimeMillis()
    }
    
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }
    
    fun isEnabled(): Boolean = enabled
}