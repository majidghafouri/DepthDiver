package com.depthdiver.simulation

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.depthdiver.common.Strings
import com.depthdiver.game.GameState

class FrameTimeOverlay(
    private val font: BitmapFont,
    private val glyphLayout: GlyphLayout,
) {
    
    private var visible = false
    private var showDetailed = false
    
    fun toggle() {
        visible = !visible
    }
    
    fun toggleDetailed() {
        showDetailed = !showDetailed
    }
    
    fun setVisible(visible: Boolean) {
        this.visible = visible
    }
    
    fun isVisible(): Boolean = visible
    
    fun render(
        batch: com.badlogic.gdx.graphics.g2d.SpriteBatch,
        font: com.badlogic.gdx.graphics.g2d.BitmapFont,
        monitor: PerformanceMonitor,
        screenWidth: Float,
        screenHeight: Float,
        state: com.depthdiver.game.GameState,
    ) {
        if (!visible || state == com.depthdiver.game.GameState.MAIN_MENU) return
        
        val glyphLayout = GlyphLayout()
        val fps = monitor.getCurrentFPS()
        val avgMs = monitor.getAverageFrameTimeMs()
        val minMs = monitor.getMinFrameTimeMs()
        val maxMs = monitor.getMaxFrameTimeMs()
        
        val fpsStr = "FPS: ${fps.toInt()}"
        val avgStr = "Avg: ${avgMs.toInt()}ms"
        val minStr = "Min: ${minMs.toInt()}ms"
        val maxStr = "Max: ${maxMs.toInt()}ms"
        
        val padding = 8f
        val lineHeight = 20f
        var y = 100f
        val x = 10f
        
        // Draw background
        // batch.setColor(0f, 0f, 0f, 0.7f)
        // batch.draw(uiPixel, 5f, y - 5f, 200f, 100f)
        // batch.setColor(Color.WHITE)
        
        // Draw FPS
        font.color = if (monitor.getCurrentFPS() < 30f) Color.RED else if (monitor.getCurrentFPS() < 55f) Color.YELLOW else Color.GREEN
        font.draw(batch, fpsStr, x, y)
        y += lineHeight + 4f
        
        // Draw average frame time
        font.color = Color.WHITE
        font.draw(batch, avgStr, x, y)
        y += lineHeight + 4f
        
        // Draw min/max
        font.draw(batch, minStr, x, y)
        y += lineHeight + 4f
        font.draw(batch, maxStr, x, y)
    }
    
}