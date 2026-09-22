package com.depthdiver

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.Preferences
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle
import com.depthdiver.entity.Hazard
import com.depthdiver.entity.Pickup
import kotlin.math.max

object Strings {
    private val EN = mapOf(
        "depth" to "DEPTH",
        "score" to "SCORE",
        "best" to "BEST",
        "oxygen" to "OXYGEN",
        "pause" to "PAUSE",
        "resume" to "RESUME",
        "restart" to "RESTART",
        "muteOn" to "MUTE ON",
        "muteOff" to "MUTE OFF",
        "paused" to "PAUSED",
        "gameOver" to "GAME OVER",
        "pressR" to "press R to restart"
    )
    private val locale = EN

    fun t(key: String): String = locale[key] ?: key
}

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    var maxLife: Float,
    var color: Color,
    var size: Float
)

class DepthDiverGame : ApplicationAdapter() {

    private lateinit var batch: SpriteBatch
    private lateinit var camera: OrthographicCamera
    private lateinit var font: BitmapFont
    private lateinit var playerTex: Texture
    private lateinit var rockTex: Texture
    private lateinit var mineTex: Texture
    private lateinit var jellyfishTex: Texture
    private lateinit var pearlTex: Texture
    private lateinit var oxyTex: Texture
    private lateinit var uiPixel: Texture

    private val hazards = mutableListOf<Hazard>()
    private val pickups = mutableListOf<Pickup>()
    private var state: GameState = GameState.PLAYING

    private val audio = AudioManager()

    private lateinit var prefs: Preferences
    private var bestDepth = 0f
    private var bestScore = 0

    private var playerX = 0f
    private var playerY = 0f
    private var depth = 0f
    private var score = 0
    private var oxygen = 1f
    private var elapsed = 0f
    private var hazardTimer = 1f
    private var pickupTimer = 2f
    private var combo = 1
    private var comboTimer = 0f

    private var shakeTimer = 0f
    private var shakeIntensity = 0f

    private val particles = mutableListOf<Particle>()

    private val playerSpeed = 320f
    private val playerRadius = 18f
    private val pixelsPerMeter = 20f
    private var worldWidth = 800f
    private var worldHeight = 600f

    override fun create() {
        batch = SpriteBatch()
        camera = OrthographicCamera()
        resize(Gdx.graphics.width, Gdx.graphics.height)
        val generator = FreeTypeFontGenerator(Gdx.files.internal("fonts/OpenSans-Regular.ttf"))
        val parameter = FreeTypeFontGenerator.FreeTypeFontParameter().apply {
            size = (worldHeight / 30f).toInt().coerceIn(16, 48)
            color = Color.WHITE
            borderWidth = 1f
            borderColor = Color.BLACK
            borderStraight = true
        }
        font = generator.generateFont(parameter)
        generator.dispose()
        prefs = Gdx.app.getPreferences("depthdiver")
        bestDepth = prefs.getFloat("bestDepth", 0f)
        bestScore = prefs.getInteger("bestScore", 0)
        audio.init()
        restoreInterruptedRun()

        val playerPix = Pixmap(64, 64, Pixmap.Format.RGBA8888)
        playerPix.setColor(0.2f, 0.75f, 1f, 1f)
        playerPix.fillCircle(32, 32, 26)
        playerPix.setColor(0.1f, 0.45f, 0.7f, 1f)
        playerPix.fillCircle(40, 40, 10)
        playerTex = Texture(playerPix)
        playerPix.dispose()

        val rockPix = Pixmap(64, 64, Pixmap.Format.RGBA8888)
        rockPix.setColor(0.45f, 0.4f, 0.35f, 1f)
        rockPix.fillCircle(32, 32, 28)
        rockPix.setColor(0.3f, 0.27f, 0.24f, 1f)
        rockPix.fillCircle(22, 38, 14)
        rockTex = Texture(rockPix)
        rockPix.dispose()

        val minePix = Pixmap(48, 48, Pixmap.Format.RGBA8888)
        minePix.setColor(0.15f, 0.15f, 0.22f, 1f)
        minePix.fillCircle(24, 24, 15)
        minePix.setColor(0.1f, 0.1f, 0.16f, 1f)
        minePix.drawLine(24, 38, 24, 46)
        minePix.drawLine(24, 10, 24, 2)
        minePix.drawLine(38, 24, 46, 24)
        minePix.drawLine(10, 24, 2, 24)
        minePix.drawLine(34, 34, 41, 41)
        minePix.drawLine(14, 14, 7, 7)
        minePix.drawLine(34, 14, 41, 7)
        minePix.drawLine(14, 34, 7, 41)
        minePix.setColor(0.9f, 0.2f, 0.15f, 1f)
        minePix.fillCircle(24, 24, 6)
        mineTex = Texture(minePix)
        minePix.dispose()

        val jellyPix = Pixmap(48, 48, Pixmap.Format.RGBA8888)
        jellyPix.setColor(0.7f, 0.9f, 1f, 0.85f)
        jellyPix.fillCircle(24, 40, 17)
        jellyPix.setColor(0.6f, 0.8f, 1f, 0.8f)
        jellyPix.drawLine(18, 28, 15, 8)
        jellyPix.drawLine(24, 26, 24, 6)
        jellyPix.drawLine(30, 28, 33, 8)
        jellyfishTex = Texture(jellyPix)
        jellyPix.dispose()

        val pearlPix = Pixmap(24, 24, Pixmap.Format.RGBA8888)
        pearlPix.setColor(0.95f, 0.9f, 0.82f, 1f)
        pearlPix.fillCircle(12, 12, 9)
        pearlPix.setColor(1f, 1f, 1f, 0.8f)
        pearlPix.fillCircle(9, 15, 3)
        pearlTex = Texture(pearlPix)
        pearlPix.dispose()

        val oxyPix = Pixmap(32, 32, Pixmap.Format.RGBA8888)
        oxyPix.setColor(0.3f, 0.3f, 0.3f, 1f)
        oxyPix.fillRectangle(12, 24, 8, 4)
        oxyPix.setColor(0.1f, 0.75f, 0.2f, 1f)
        oxyPix.fillRectangle(10, 21, 12, 4)
        oxyPix.setColor(0.9f, 0.9f, 0.88f, 1f)
        oxyPix.fillRectangle(8, 4, 16, 18)
        oxyTex = Texture(oxyPix)
        oxyPix.dispose()

        val uiPix = Pixmap(1, 1, Pixmap.Format.RGBA8888)
        uiPix.setColor(Color.WHITE)
        uiPix.fill()
        uiPixel = Texture(uiPix)
        uiPix.dispose()

        val sharkPix = Pixmap(96, 32, Pixmap.Format.RGBA8888)
        sharkPix.setColor(0.55f, 0.62f, 0.72f, 1f)
        sharkPix.fillTriangle(8, 16, 88, 16, 52, 30)
        sharkPix.setColor(0.85f, 0.9f, 0.95f, 1f)
        sharkPix.fillTriangle(58, 17, 84, 17, 62, 26)
        sharkPix.setColor(0.15f, 0.18f, 0.22f, 1f)
        sharkPix.fillCircle(22, 120, 3)

        reset()
    }

    override fun resize(width: Int, height: Int) {
        worldWidth = width.toFloat()
        worldHeight = height.toFloat()
        camera.setToOrtho(false, worldWidth, worldHeight)
    }

    override fun render() {
        handleInput()
        update(Gdx.graphics.deltaTime)
        draw()
    }

    override fun pause() {
        if (state == GameState.PLAYING) {
            prefs.putFloat("runDepth", depth)
            prefs.putFloat("runScore", score.toFloat())
            prefs.putFloat("runOxygen", oxygen)
            prefs.putFloat("runElapsed", elapsed)
            prefs.putBoolean("runSaved", true)
            prefs.flush()
        }
    }

    override fun resume() {
        restoreInterruptedRun()
    }

    private fun restoreInterruptedRun() {
        if (!prefs.getBoolean("runSaved", false)) return
        depth = prefs.getFloat("runDepth", 0f)
        score = prefs.getFloat("runScore", 0f).toInt()
        oxygen = prefs.getFloat("runOxygen", 1f)
        elapsed = prefs.getFloat("runElapsed", 0f)
        prefs.putBoolean("runSaved", false)
        prefs.flush()
        if (state == GameState.GAME_OVER) {
            endGame()
        }
    }

    private fun triggerShake(duration: Float, intensity: Float) {
        shakeTimer = duration
        shakeIntensity = intensity
    }

    private fun spawnParticles(x: Float, y: Float, color: Color, count: Int) {
        repeat(count) {
            val angle = MathUtils.random(MathUtils.PI2)
            val speed = MathUtils.random(60f, 180f)
            val life = MathUtils.random(0.3f, 0.8f)
            particles.add(Particle(
                x = x,
                y = y,
                vx = MathUtils.cos(angle) * speed,
                vy = MathUtils.sin(angle) * speed,
                life = life,
                maxLife = life,
                color = Color(color),
                size = MathUtils.random(3f, 7f)
            ))
        }
    }

    override fun dispose() {
        batch.dispose()
        font.dispose()
        playerTex.dispose()
        rockTex.dispose()
        mineTex.dispose()
        jellyfishTex.dispose()
        pearlTex.dispose()
        oxyTex.dispose()
        audio.dispose()
    }

    private fun update(delta: Float) {
        if (state != GameState.PLAYING) return
        elapsed += delta
        oxygen -= delta * 0.02f
        depth = max(depth, (worldHeight - max(playerY, playerRadius)) / pixelsPerMeter)

        if (shakeTimer > 0f) {
            shakeTimer -= delta
            if (shakeTimer < 0f) shakeTimer = 0f
        }

        val itrP = particles.iterator()
        while (itrP.hasNext()) {
            val p = itrP.next()
            p.life -= delta
            if (p.life <= 0f) {
                itrP.remove()
            } else {
                p.x += p.vx * delta
                p.y += p.vy * delta
                p.vy -= 200f * delta
            }
        }

        val difficulty = (depth / 40f).coerceAtLeast(0f)
        val scrollSpeed = 90f + difficulty * 40f

        hazardTimer -= delta
        if (hazardTimer <= 0) {
            spawnHazard()
            hazardTimer = MathUtils.random(1.4f, 2.6f) / (1f + difficulty * 0.6f)
        }
        pickupTimer -= delta
        if (pickupTimer <= 0) {
            spawnPickup()
            pickupTimer = MathUtils.random(3f, 5.5f)
        }

        updateEntities(delta, scrollSpeed)

        for (hazard in hazards) {
            if (playerRect().overlaps(hazard.rect)) {
                triggerShake(0.3f, 12f)
                Gdx.input.vibrate(100)
                spawnParticles(playerX, playerY, Color.RED, 12)
                endGame()
            }
        }
        for (pickup in pickups) {
            if (!pickup.collected && playerRect().overlaps(pickup.rect)) {
                pickup.collected = true
                when (pickup) {
                    is Pickup.OxygenTank -> {
                        oxygen = (oxygen + 0.4f).coerceAtMost(1f)
                        audio.playOxygen()
                        triggerShake(0.15f, 6f)
                        Gdx.input.vibrate(40)
                        spawnParticles(pickup.rect.x + pickup.rect.width / 2f, pickup.rect.y + pickup.rect.height / 2f, Color.CYAN, 8)
                    }
                    is Pickup.Pearl -> {
                        combo += 1
                        comboTimer = 5f
                        score += 5 * combo
                        audio.playPickup()
                        triggerShake(0.1f, 4f)
                        Gdx.input.vibrate(30)
                        spawnParticles(pickup.rect.x + pickup.rect.width / 2f, pickup.rect.y + pickup.rect.height / 2f, Color.GOLD, 10)
                    }
                }
            }
        }
        if (oxygen <= 0f) {
            endGame()
        }

        if (depth > bestDepth) {
            bestDepth = depth
            prefs.putFloat("bestDepth", bestDepth)
        }
        if (score > bestScore) {
            bestScore = score
            prefs.putInteger("bestScore", bestScore)
        }
        if (state == GameState.GAME_OVER) {
            prefs.flush()
        }
    }

    private fun updateEntities(delta: Float, scrollSpeed: Float) {
        val itr = hazards.iterator()
        while (itr.hasNext()) {
            val hazard = itr.next()
            when (hazard) {
                is Hazard.Rock -> {
                    hazard.rect.y -= scrollSpeed * delta
                    hazard.rect.x += MathUtils.sin(elapsed * 2f + hazard.phase) * 10f * delta
                }
                is Hazard.Mine -> {
                    hazard.rect.y -= scrollSpeed * 0.6f * delta
                    hazard.rect.x += MathUtils.sin(elapsed * 1.2f + hazard.phase) * 24f * delta
                }
                is Hazard.Jellyfish -> {
                    hazard.rect.y -= scrollSpeed * 0.45f * delta
                    hazard.rect.x = hazard.baseX + MathUtils.sin(elapsed * 1.5f + hazard.phase) * hazard.sway
                }
                is Hazard.Shark -> {
                    hazard.rect.y -= scrollSpeed * (if (hazard.isBoss) 0.15f else 0.5f) * delta
                    hazard.rect.x += MathUtils.sin(elapsed * 0.8f + hazard.phase) * 14f * delta
                }
            }
            if (hazard.rect.y + hazard.rect.height < 0f ||
                hazard.rect.x + hazard.rect.width < 0f ||
                hazard.rect.x > worldWidth
            ) {
                itr.remove()
            }
        }
        val itrP = pickups.iterator()
        while (itrP.hasNext()) {
            val pickup = itrP.next()
            pickup.rect.y -= scrollSpeed * 0.55f * delta
            pickup.rect.x += MathUtils.sin(elapsed * 1.1f + pickup.phase) * 8f * delta
            if (pickup.rect.y + pickup.rect.height < 0f || pickup.collected) {
                itrP.remove()
            }
        }
    }

    private fun spawnHazard() {
        val roll = MathUtils.random()
        val x = MathUtils.random(0f, (worldWidth - 80f).coerceAtLeast(0f))
        when {
            roll < 0.3f -> {
                hazards.add(Hazard.Rock(Rectangle(-24f, worldHeight + 40f, 96f, 120f), 0f))
                hazards.add(Hazard.Rock(Rectangle(worldWidth - 72f, worldHeight + 40f, 96f, 120f), 0f))
            }
            depth > 35f && roll < 0.55f -> {
                hazards.add(
                    Hazard.Jellyfish(
                        Rectangle(x, worldHeight + 60f, 60f, 60f),
                        MathUtils.random(0f, MathUtils.PI2),
                        MathUtils.random(25f, 45f),
                        x
                    )
                )
            }
            depth > 18f && roll < 0.8f -> {
                hazards.add(Hazard.Mine(Rectangle(x, worldHeight + 48f, 48f, 48f), MathUtils.random(0f, MathUtils.PI2)))
            }
            depth > 80f && roll < 0.95f -> {
                val boss = depth > 120f && MathUtils.random() < 0.04f
                val w = if (boss) 130f else 84f
                val h = if (boss) 46f else 30f
                hazards.add(Hazard.Shark(Rectangle(x, worldHeight + 60f, w, h), 0f, boss))
            }
            else -> {
                val size = MathUtils.random(45f, 85f)
                hazards.add(Hazard.Rock(Rectangle(x, worldHeight + 80f, size, size), MathUtils.random(0f, MathUtils.PI2)))
            }
        }
    }

    private fun spawnPickup() {
        val x = MathUtils.random(0f, (worldWidth - 40f).coerceAtLeast(0f))
        val phase = MathUtils.random(0f, MathUtils.PI2)
        if (MathUtils.random() < 0.65f) {
            pickups.add(Pickup.Pearl(Rectangle(x, worldHeight + 40f, 24f, 24f), phase))
        } else {
            pickups.add(Pickup.OxygenTank(Rectangle(x, worldHeight + 48f, 32f, 32f), phase))
        }
    }

    private fun handleInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isKeyJustPressed(Input.Keys.P)) {
            state = if (state == GameState.PLAYING) GameState.PAUSED else GameState.PLAYING
            return
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.M)) {
            audio.toggleMute()
            return
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            reset()
            return
        }

        if (Gdx.input.justTouched()) {
            val tx = Gdx.input.x.toFloat()
            val ty = worldHeight - Gdx.input.y.toFloat()
            if (state == GameState.PLAYING) {
                if (pillAt(tx, ty, worldWidth - 44f, worldHeight - 30f, 56f, 30f) || state == GameState.PLAYING && false) {
                    state = GameState.PAUSED
                    return
                }
            }
            if (state == GameState.PAUSED) {
                if (pillAt(tx, ty, worldWidth / 2f, worldHeight / 2f + 48f, 140f, 34f)) {
                    state = GameState.PLAYING
                    return
                }
                if (pillAt(tx, ty, worldWidth / 2f - 80f, worldHeight / 2f - 46f, 140f, 34f)) {
                    reset()
                    return
                }
                if (pillAt(tx, ty, worldWidth / 2f + 80f, worldHeight / 2f - 46f, 140f, 34f)) {
                    audio.toggleMute()
                    return
                }
            }
            if (state == GameState.GAME_OVER) {
                reset()
                return
            }
        }

        if (state != GameState.PLAYING) return

        val delta = Gdx.graphics.deltaTime
        var dx = 0f
        var dy = 0f
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT) || Gdx.input.isKeyPressed(Input.Keys.A)) dx -= 1f
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT) || Gdx.input.isKeyPressed(Input.Keys.D)) dx += 1f
        if (Gdx.input.isKeyPressed(Input.Keys.UP) || Gdx.input.isKeyPressed(Input.Keys.W)) dy += 1f
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN) || Gdx.input.isKeyPressed(Input.Keys.S)) dy -= 1f

        if (dx != 0f || dy != 0f) {
            val len = kotlin.math.sqrt(dx * dx + dy * dy)
            dx /= len
            dy /= len
        } else if (Gdx.input.isTouched()) {
            val touchX = Gdx.input.x.toFloat()
            val touchY = Gdx.graphics.height.toFloat() - Gdx.input.y.toFloat()
            val toX = touchX - playerX
            val toY = touchY - playerY
            val dist = kotlin.math.sqrt(toX * toX + toY * toY)
            if (dist > playerRadius + 8f) {
                dx = toX / dist
                dy = toY / dist
            }
        }
        playerX = (playerX + dx * playerSpeed * delta).coerceIn(playerRadius, worldWidth - playerRadius)
        playerY = (playerY + dy * playerSpeed * delta).coerceIn(playerRadius, worldHeight - playerRadius)
    }

    private fun draw() {
        Gdx.gl.glClearColor(0.02f, 0.12f, 0.25f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        val originalCamX = camera.position.x
        val originalCamY = camera.position.y
        if (shakeTimer > 0f) {
            val progress = 1f - shakeTimer / 0.3f
            val currentIntensity = shakeIntensity * (1f - progress * 0.7f)
            camera.position.x += MathUtils.random(-currentIntensity, currentIntensity)
            camera.position.y += MathUtils.random(-currentIntensity, currentIntensity)
            camera.update()
        }
        batch.projectionMatrix = camera.combined
        batch.begin()

        batch.setColor(1f, 1f, 1f, 1f)
        for (particle in particles) {
            val alpha = particle.life / particle.maxLife
            batch.setColor(particle.color.r, particle.color.g, particle.color.b, alpha)
            batch.draw(uiPixel, particle.x - particle.size / 2f, particle.y - particle.size / 2f, particle.size, particle.size)
        }
        batch.setColor(1f, 1f, 1f, 1f)
        for (hazard in hazards) {
            val tex = when (hazard) {
                is Hazard.Rock -> rockTex
                is Hazard.Mine -> mineTex
                is Hazard.Jellyfish -> jellyfishTex
                is Hazard.Shark -> rockTex
            }
            batch.draw(tex, hazard.rect.x, hazard.rect.y, hazard.rect.width, hazard.rect.height)
        }
        for (pickup in pickups) {
            if (pickup.collected) continue
            val tex = if (pickup is Pickup.OxygenTank) oxyTex else pearlTex
            batch.draw(tex, pickup.rect.x, pickup.rect.y, pickup.rect.width, pickup.rect.height)
        }

        batch.draw(
            playerTex,
            playerX - playerRadius,
            playerY - playerRadius,
            playerRadius * 2f,
            playerRadius * 2f
        )

        if (shakeTimer > 0f) {
            camera.position.x = originalCamX
            camera.position.y = originalCamY
            camera.update()
            batch.projectionMatrix = camera.combined
        }

        font.color = Color.WHITE
        font.draw(batch, "${Strings.t("depth")}: ${depth.toInt()} m", 10f, worldHeight - 14f)
        font.draw(batch, "${Strings.t("score")}: $score", 150f, worldHeight - 14f)
        font.draw(batch, "${Strings.t("best")}: ${bestDepth.toInt()} m / $bestScore", 260f, worldHeight - 14f)
        font.draw(batch, "${Strings.t("oxygen")}: ${(oxygen * 100).toInt()}%", 10f, worldHeight - 34f)
        if (state == GameState.PLAYING) {
            drawPill(batch, font, worldWidth - 44f, worldHeight - 30f, 56f, 30f, "PAUSE")
        }
        if (state == GameState.PAUSED) {
            drawPill(batch, font, worldWidth / 2f, worldHeight / 2f + 48f, 140f, 34f, "RESUME")
            drawPill(batch, font, worldWidth / 2f - 80f, worldHeight / 2f - 46f, 140f, 34f, "RESTART")
            drawPill(batch, font, worldWidth / 2f + 80f, worldHeight / 2f - 46f, 140f, 34f, "MUTE")
            font.color = Color.CYAN
            font.draw(batch, "PAUSED -- P/Esc resume  R restart  M mute", worldWidth / 2f - 180f, worldHeight / 2f)
            font.color = Color.WHITE
        }
        if (state == GameState.GAME_OVER) {
            font.color = Color.RED
            font.draw(
                batch,
                "GAME OVER - press R to restart",
                worldWidth / 2f - 140f,
                worldHeight / 2f
            )
            font.color = Color.GOLD
            font.draw(
                batch,
                "BEST DEPTH: ${bestDepth.toInt()} m   HIGH SCORE: $bestScore",
                worldWidth / 2f - 180f,
                worldHeight / 2f - 22f
            )
        }
        batch.end()
    }

    private fun playerRect() =
        Rectangle(playerX - playerRadius, playerY - playerRadius, playerRadius * 2f, playerRadius * 2f)

    private fun pillAt(tx: Float, ty: Float, cx: Float, cy: Float, w: Float, h: Float): Boolean =
        tx >= cx - w / 2f && tx <= cx + w / 2f && ty >= cy - h / 2f && ty <= cy + h / 2f

    private fun drawPill(
        batch: SpriteBatch,
        font: BitmapFont,
        cx: Float,
        cy: Float,
        w: Float,
        h: Float,
        label: String
    ) {
        batch.setColor(0f, 0f, 0f, 0.55f)
        batch.draw(uiPixel, cx - w / 2f, cy - h / 2f, w, h)
        batch.setColor(Color.WHITE)
        font.color = Color.WHITE
        val layout = GlyphLayout(font, label)
        font.draw(batch, layout, cx - layout.width / 2f, cy + layout.height / 2f)
    }


    private fun endGame() {
        if (state == GameState.PLAYING) {
            state = GameState.GAME_OVER
            audio.playCrash()
        }
    }

    private fun reset() {
        playerX = worldWidth / 2f
        playerY = worldHeight * 0.25f
        depth = 0f
        score = 0
        oxygen = 1f
        elapsed = 0f
        hazardTimer = 1f
        pickupTimer = 2f
        hazards.clear()
        pickups.clear()
        state = GameState.PLAYING
    }

    private enum class GameState { PLAYING, PAUSED, GAME_OVER }
}