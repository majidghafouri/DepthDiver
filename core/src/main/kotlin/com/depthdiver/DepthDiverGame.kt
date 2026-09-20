package com.depthdiver

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle
import kotlin.math.max

class DepthDiverGame : ApplicationAdapter() {

    private lateinit var batch: SpriteBatch
    private lateinit var camera: OrthographicCamera
    private lateinit var font: BitmapFont
    private lateinit var playerTex: Texture
    private lateinit var rockTex: Texture
    private lateinit var treasureTex: Texture

    private val rocks = mutableListOf<Rock>()
    private val treasures = mutableListOf<Treasure>()
    private var state: GameState = GameState.PLAYING

    private var playerX = 0f
    private var playerY = 0f
    private var depth = 0f
    private var score = 0
    private var oxygen = 1f
    private var elapsed = 0f
    private var rockTimer = 0f
    private var treasureTimer = 0f

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

        val treasurePix = Pixmap(32, 32, Pixmap.Format.RGBA8888)
        treasurePix.setColor(1f, 0.85f, 0.2f, 1f)
        treasurePix.fillCircle(16, 16, 12)
        treasurePix.setColor(1f, 0.95f, 0.5f, 1f)
        treasurePix.fillCircle(16, 16, 5)
        treasureTex = Texture(treasurePix)
        treasurePix.dispose()

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

    private fun update(delta: Float) {
        if (state != GameState.PLAYING) return
        elapsed += delta
        oxygen -= delta * 0.02f
        depth = max(depth, (worldHeight - max(playerY, playerRadius)) / pixelsPerMeter)

        rockTimer -= delta
        if (rockTimer <= 0) {
            spawnRock()
            rockTimer = MathUtils.random(1.2f, 2.4f)
        }
        treasureTimer -= delta
        if (treasureTimer <= 0) {
            spawnTreasure()
            treasureTimer = MathUtils.random(3f, 6f)
        }

        val speed = 140f + elapsed * 4f
        val itrR = rocks.iterator()
        while (itrR.hasNext()) {
            val rock = itrR.next()
            rock.rect.y -= speed * delta
            rock.rect.x += MathUtils.sin(elapsed * 2f + rock.phase) * 10f * delta
            if (rock.rect.y + rock.rect.height < 0f) itrR.remove()
        }
        val itrT = treasures.iterator()
        while (itrT.hasNext()) {
            val treasure = itrT.next()
            treasure.rect.y -= speed * 0.6f * delta
            if (treasure.rect.y + treasure.rect.height < 0f || treasure.collected) {
                itrT.remove()
            }
        }

        for (rock in rocks) {
            if (playerRect().overlaps(rock.rect)) {
                state = GameState.GAME_OVER
            }
        }
        for (treasure in treasures) {
            if (!treasure.collected && playerRect().overlaps(treasure.rect)) {
                treasure.collected = true
                score += 10
                oxygen = (oxygen + 0.25f).coerceAtMost(1f)
            }
        }
        if (oxygen <= 0f) {
            state = GameState.GAME_OVER
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

        for (rock in rocks) {
            batch.setColor(1f, 1f, 1f, 1f)
            batch.draw(rockTex, rock.rect.x, rock.rect.y, rock.rect.width, rock.rect.height)
        }
        for (treasure in treasures) {
            batch.setColor(1f, 1f, 1f, 1f)
            batch.draw(treasureTex, treasure.rect.x, treasure.rect.y)
        }

        batch.setColor(1f, 1f, 1f, 1f)
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
        font.draw(batch, "OXYGEN: ${(oxygen * 100).toInt()}%", 10f, worldHeight - 34f)
        if (state == GameState.GAME_OVER) {
            font.color = Color.RED
            font.draw(
                batch,
                "GAME OVER - press R to restart",
                worldWidth / 2f - 140f,
                worldHeight / 2f
            )
        }
        batch.end()
    }

    private fun spawnRock() {
        val size = MathUtils.random(45f, 85f)
        val x = MathUtils.random(0f, worldWidth - size)
        rocks.add(Rock(Rectangle(x, worldHeight + size, size, size), MathUtils.random(0f, MathUtils.PI2)))
    }

    private fun spawnTreasure() {
        val x = MathUtils.random(0f, worldWidth - 32f)
        treasures.add(Treasure(Rectangle(x, worldHeight + 32f, 32f, 32f)))
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
        rockTimer = 1f
        treasureTimer = 2f
        rocks.clear()
        treasures.clear()
        state = GameState.PLAYING
    }

    override fun dispose() {
        batch.dispose()
        font.dispose()
        playerTex.dispose()
        rockTex.dispose()
        treasureTex.dispose()
    }

    private data class Rock(val rect: Rectangle, val phase: Float)
    private data class Treasure(val rect: Rectangle, var collected: Boolean = false)
    private enum class GameState { PLAYING, GAME_OVER }
}