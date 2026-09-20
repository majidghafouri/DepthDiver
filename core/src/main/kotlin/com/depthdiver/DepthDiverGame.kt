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
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle
import com.depthdiver.entity.Hazard
import com.depthdiver.entity.Pickup
import kotlin.math.max

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

    private val hazards = mutableListOf<Hazard>()
    private val pickups = mutableListOf<Pickup>()
    private var state: GameState = GameState.PLAYING

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

    private val playerSpeed = 320f
    private val playerRadius = 18f
    private val pixelsPerMeter = 20f
    private var worldWidth = 800f
    private var worldHeight = 600f

    override fun create() {
        batch = SpriteBatch()
        camera = OrthographicCamera()
        resize(Gdx.graphics.width, Gdx.graphics.height)
        font = BitmapFont()
        prefs = Gdx.app.getPreferences("depthdiver")
        bestDepth = prefs.getFloat("bestDepth", 0f)
        bestScore = prefs.getInteger("bestScore", 0)

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

    override fun dispose() {
        batch.dispose()
        font.dispose()
        playerTex.dispose()
        rockTex.dispose()
        mineTex.dispose()
        jellyfishTex.dispose()
        pearlTex.dispose()
        oxyTex.dispose()
    }

    private fun update(delta: Float) {
        if (state != GameState.PLAYING) return
        elapsed += delta
        oxygen -= delta * 0.02f
        depth = max(depth, (worldHeight - max(playerY, playerRadius)) / pixelsPerMeter)

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
                state = GameState.GAME_OVER
            }
        }
        for (pickup in pickups) {
            if (!pickup.collected && playerRect().overlaps(pickup.rect)) {
                pickup.collected = true
                when (pickup) {
                    is Pickup.OxygenTank -> oxygen = (oxygen + 0.4f).coerceAtMost(1f)
                    is Pickup.Pearl -> score += 5
                }
            }
        }
        if (oxygen <= 0f) {
            state = GameState.GAME_OVER
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
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            reset()
            return
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
        batch.projectionMatrix = camera.combined
        batch.begin()

        batch.setColor(1f, 1f, 1f, 1f)
        for (hazard in hazards) {
            val tex = when (hazard) {
                is Hazard.Rock -> rockTex
                is Hazard.Mine -> mineTex
                is Hazard.Jellyfish -> jellyfishTex
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

        font.color = Color.WHITE
        font.draw(batch, "DEPTH: ${depth.toInt()} m", 10f, worldHeight - 14f)
        font.draw(batch, "SCORE: $score", 150f, worldHeight - 14f)
        font.draw(batch, "BEST: ${bestDepth.toInt()} m / $bestScore", 260f, worldHeight - 14f)
        font.draw(batch, "OXYGEN: ${(oxygen * 100).toInt()}%", 10f, worldHeight - 34f)
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

    private enum class GameState { PLAYING, GAME_OVER }
}