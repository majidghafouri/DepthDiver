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
import kotlin.math.min

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
        "pressR" to "press R to restart",
        "menuTitle" to "DEPTH DIVER",
        "menuSubtitle" to "plunge into the abyss",
        "play" to "PLAY",
        "profile" to "PROFILE",
        "leaderboard" to "LEADERBOARD",
        "shop" to "SHOP",
        "back" to "BACK",
        "menu" to "MENU",
        "quit" to "QUIT",
        "pearls" to "PEARLS",
        "pearlsEarned" to "PEARLS EARNED",
        "dives" to "DIVES",
        "newRecord" to "NEW RECORD!",
        "top5" to "ENTERED TOP 5!",
        "rank" to "RANK",
        "noRuns" to "NO RUNS YET",
        "level" to "LVL",
        "buy" to "BUY",
        "max" to "MAX"
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
    private lateinit var titleFont: BitmapFont
    private lateinit var playerTex: Texture
    private lateinit var rockTex: Texture
    private lateinit var mineTex: Texture
    private lateinit var jellyfishTex: Texture
    private lateinit var pearlTex: Texture
    private lateinit var oxyTex: Texture
    private lateinit var uiPixel: Texture

    private val hazards = mutableListOf<Hazard>()
    private val pickups = mutableListOf<Pickup>()
    private var state: GameState = GameState.MAIN_MENU
    private var hudPauseCx = 0f
    private var hudPauseCy = 0f
    private var hudPauseW = 0f
    private var hudPauseH = 0f

    private val audio = AudioManager()

    private lateinit var prefs: Preferences
    private var bestDepth = 0f
    private var bestScore = 0
    private var leaderboardMade = false
    private var startBestScore = 0

    private var upgradeOxygenLevel = 0
    private var upgradeSpeedLevel = 0
    private var upgradeComboLevel = 0
    private var upgradeShieldLevel = 0
    private var upgradePearlValueLevel = 0

    private var playerX = 0f
    private var playerY = 0f
    private var depth = 0f
    private var score = 0
    private var oxygen = 1f
    private var maxOxygen = 1f
    private var elapsed = 0f
    private var hazardTimer = 1f
    private var pickupTimer = 2f
    private var combo = 1
    private var comboTimer = 0f
    private var shieldActive = false
    private var shieldCooldown = 0f

    private var shakeTimer = 0f
    private var shakeIntensity = 0f

    private val particles = mutableListOf<Particle>()

    private var playerSpeed = 320f
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
        titleFont = generator.generateFont(
            FreeTypeFontGenerator.FreeTypeFontParameter().apply {
                size = (worldHeight / 9f).toInt().coerceIn(36, 84)
                color = Color(0.35f, 0.85f, 1f, 1f)
                borderWidth = 2f
                borderColor = Color(0.02f, 0.2f, 0.4f, 1f)
                borderStraight = true
                shadowOffsetY = 4
                shadowColor = Color(0f, 0f, 0f, 0.6f)
            }
        )
        generator.dispose()
        prefs = Gdx.app.getPreferences("depthdiver")
        bestDepth = prefs.getFloat("bestDepth", 0f)
        bestScore = prefs.getInteger("bestScore", 0)
        refreshUpgradeLevels()
        applyUpgrades()
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
        state = GameState.MAIN_MENU
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
        titleFont.dispose()
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

        if (shieldCooldown > 0f) {
            shieldCooldown -= delta
            if (shieldCooldown <= 0f) {
                shieldCooldown = 0f
                shieldActive = false
            }
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
                if (upgradeShieldLevel > 0 && !shieldActive && shieldCooldown <= 0f) {
                    shieldActive = true
                    shieldCooldown = 10f - upgradeShieldLevel * 1.5f
                    audio.playOxygen()
                    triggerShake(0.15f, 8f)
                    Gdx.input.vibrate(60)
                    spawnParticles(playerX, playerY, Color.MAGENTA, 15)
                } else {
                    triggerShake(0.3f, 12f)
                    Gdx.input.vibrate(100)
                    spawnParticles(playerX, playerY, Color.RED, 12)
                    endGame()
                }
            }
        }
        for (pickup in pickups) {
            if (!pickup.collected && playerRect().overlaps(pickup.rect)) {
                pickup.collected = true
                when (pickup) {
                    is Pickup.OxygenTank -> {
                        oxygen = (oxygen + 0.4f).coerceAtMost(maxOxygen)
                        audio.playOxygen()
                        triggerShake(0.15f, 6f)
                        Gdx.input.vibrate(40)
                        spawnParticles(pickup.rect.x + pickup.rect.width / 2f, pickup.rect.y + pickup.rect.height / 2f, Color.CYAN, 8)
                    }
                    is Pickup.Pearl -> {
                        combo += 1
                        comboTimer = 5f + upgradeComboLevel * 2f
                        val pearlValue = (5 * (1 + upgradePearlValueLevel * 0.5)).toInt()
                        score += pearlValue * combo
                        Profile.addPearls(pearlValue)
                        Profile.addLifetimePearls(pearlValue)
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
        val escJust = Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isKeyJustPressed(Input.Keys.BACK)
        val pJust = Gdx.input.isKeyJustPressed(Input.Keys.P)
        val mJust = Gdx.input.isKeyJustPressed(Input.Keys.M)

        when (state) {
            GameState.MAIN_MENU -> {
                if (escJust || pJust) {
                    Gdx.app.exit()
                    return
                }
                if (mJust) {
                    audio.toggleMute()
                    audio.playClick()
                    return
                }
                if (Gdx.input.justTouched()) {
                    handleMenuTouch(touchX(), touchY())
                }
            }

            GameState.PROFILE, GameState.LEADERBOARD, GameState.SHOP -> {
                if (escJust || pJust) {
                    goToMenu()
                    return
                }
                if (Gdx.input.justTouched()) {
                    if (state == GameState.SHOP) {
                        handleShopTouch(touchX(), touchY())
                    } else {
                        handleSubScreenTouch(touchX(), touchY())
                    }
                }
            }

            GameState.PLAYING -> {
                if (escJust || pJust) {
                    state = GameState.PAUSED
                    return
                }
                if (mJust) {
                    audio.toggleMute()
                    return
                }
                if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
                    reset()
                    return
                }
                if (Gdx.input.justTouched() && Widgets.contains(touchX(), touchY(), hudPauseCx, hudPauseCy, hudPauseW, hudPauseH)) {
                    state = GameState.PAUSED
                    return
                }
            }

            GameState.PAUSED -> {
                if (escJust || pJust) {
                    state = GameState.PLAYING
                    return
                }
                if (mJust) {
                    audio.toggleMute()
                    return
                }
                if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
                    reset()
                    return
                }
                if (Gdx.input.justTouched()) {
                    handlePauseTouch(touchX(), touchY())
                }
                return
            }

            GameState.GAME_OVER -> {
                if (escJust || pJust) {
                    goToMenu()
                    return
                }
                if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
                    reset()
                    return
                }
                if (Gdx.input.justTouched()) {
                    val tx = touchX()
                    val ty = touchY()
                    val pillY = worldHeight / 2f - 118f
                    val menu = Strings.t("menu")
                    if (Widgets.contains(tx, ty, worldWidth / 2f + 95f, pillY, Widgets.pillW(font, menu), Widgets.pillH(font, menu))) {
                        goToMenu()
                    } else {
                        reset()
                    }
                    return
                }
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

    private fun touchX(): Float = Gdx.input.x.toFloat()

    private fun touchY(): Float = worldHeight - Gdx.input.y.toFloat()

    private fun menuLabels(): List<String> = listOf(
        Strings.t("play"),
        Strings.t("profile"),
        Strings.t("leaderboard"),
        Strings.t("shop"),
        if (audio.muted) Strings.t("muteOn") else Strings.t("muteOff"),
        Strings.t("quit")
    )

    private fun handleMenuTouch(tx: Float, ty: Float) {
        val labels = menuLabels()
        for (i in labels.indices) {
            val (cx, cy) = Widgets.stack(worldWidth, worldHeight, i, labels.size)
            if (Widgets.contains(tx, ty, cx, cy, Widgets.pillW(font, labels[i]), Widgets.pillH(font, labels[i]))) {
                engage(i)
                return
            }
        }
    }

    private fun engage(index: Int) {
        audio.playClick()
        when (index) {
            0 -> reset()
            1 -> state = GameState.PROFILE
            2 -> state = GameState.LEADERBOARD
            3 -> state = GameState.SHOP
            4 -> audio.toggleMute()
            5 -> Gdx.app.exit()
        }
    }

    private fun handleSubScreenTouch(tx: Float, ty: Float) {
        val pill = backPill()
        if (Widgets.contains(tx, ty, pill.cx, pill.cy, pill.w, pill.h)) {
            goToMenu()
        }
    }

    private fun backPill(): ShopRect =
        ShopRect(worldWidth / 2f, worldHeight * 0.08f, Widgets.pillW(font, Strings.t("back")), Widgets.pillH(font, Strings.t("back")))

    private fun shopBuyLabel(u: Profile.Upgrade): String {
        val cost = Profile.upgradeCost(u)
        return if (cost == null) Strings.t("max") else "${Strings.t("buy")} $cost"
    }

    private class ShopRect(val cx: Float, val cy: Float, val w: Float, val h: Float)

    private fun shopPanel(): ShopRect {
        val panelW = min(worldWidth * 0.82f, worldHeight * 1.35f).coerceAtMost(560f)
        val panelH = worldHeight * 0.58f
        return ShopRect(worldWidth / 2f, worldHeight / 2f, panelW, panelH)
    }

    private fun shopRowCy(index: Int, panel: ShopRect): Float {
        val lineGap = min(52f, panel.h / (Profile.Upgrade.values().size + 1))
        return panel.cy + panel.h / 2f - 50f - lineGap * index
    }

    private fun shopBuyPill(u: Profile.Upgrade, panel: ShopRect, index: Int): ShopRect {
        val label = shopBuyLabel(u)
        return ShopRect(
            panel.cx + panel.w / 2f - 82f,
            shopRowCy(index, panel),
            Widgets.pillW(font, label),
            Widgets.pillH(font, label)
        )
    }

    private fun handleShopTouch(tx: Float, ty: Float) {
        val pill = backPill()
        if (Widgets.contains(tx, ty, pill.cx, pill.cy, pill.w, pill.h)) {
            goToMenu()
            return
        }
        val panel = shopPanel()
        Profile.Upgrade.values().forEachIndexed { i, u ->
            if (Profile.isMaxed(u)) return@forEachIndexed
            val pill = shopBuyPill(u, panel, i)
            if (Widgets.contains(tx, ty, pill.cx, pill.cy, pill.w, pill.h)) {
                buyUpgrade(u)
                return
            }
        }
    }

    private fun buyUpgrade(u: Profile.Upgrade) {
        val cost = Profile.upgradeCost(u) ?: return
        if (Profile.pearls() < cost) return
        Profile.spendPearls(cost)
        Profile.setLevel(u, Profile.level(u) + 1)
        refreshUpgradeLevels()
        applyUpgrades()
        audio.playClick()
    }

    private fun refreshUpgradeLevels() {
        upgradeOxygenLevel = Profile.level(Profile.Upgrade.Oxygen)
        upgradeSpeedLevel = Profile.level(Profile.Upgrade.Speed)
        upgradeComboLevel = Profile.level(Profile.Upgrade.Combo)
        upgradeShieldLevel = Profile.level(Profile.Upgrade.Shield)
        upgradePearlValueLevel = Profile.level(Profile.Upgrade.PearlValue)
        applyUpgrades()
    }

    private fun goToMenu() {
        prefs.putBoolean("runSaved", false)
        prefs.flush()
        state = GameState.MAIN_MENU
    }

    private fun handlePauseTouch(tx: Float, ty: Float) {
        val labels = listOf(Strings.t("resume")) + listOf(Strings.t("menu"))
        val centerX = worldWidth / 2f
        val centerY = worldHeight / 2f
        val rowOffset = 44f
        if (Widgets.contains(tx, ty, centerX, centerY + rowOffset, Widgets.pillW(font, labels[0]), Widgets.pillH(font, labels[0]))) {
            state = GameState.PLAYING
            audio.playClick()
            return
        }
        if (Widgets.contains(tx, ty, centerX - 90f, centerY - rowOffset, Widgets.pillW(font, Strings.t("restart")), Widgets.pillH(font, Strings.t("restart")))) {
            reset()
            audio.playClick()
            return
        }
        if (Widgets.contains(tx, ty, centerX + 90f, centerY - rowOffset, Widgets.pillW(font, if (audio.muted) Strings.t("muteOn") else Strings.t("muteOff")), Widgets.pillH(font, if (audio.muted) Strings.t("muteOn") else Strings.t("muteOff")))) {
            audio.toggleMute()
            audio.playClick()
            return
        }
        if (Widgets.contains(tx, ty, centerX, centerY - 3f * rowOffset, Widgets.pillW(font, labels[1]), Widgets.pillH(font, labels[1]))) {
            goToMenu()
            audio.playClick()
        }
    }

    private fun draw() {
        Gdx.gl.glClearColor(0.02f, 0.12f, 0.25f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        val inGame = state == GameState.PLAYING || state == GameState.PAUSED || state == GameState.GAME_OVER

        batch.projectionMatrix = camera.combined
        batch.begin()

        if (inGame) {
            drawWorld()
            drawHud()
        } else {
            when (state) {
                GameState.MAIN_MENU -> drawMainMenu()
                GameState.PROFILE -> drawProfileScreen()
                GameState.LEADERBOARD -> drawLeaderboardScreen()
                GameState.SHOP -> drawShopScreen()
                GameState.PLAYING, GameState.PAUSED, GameState.GAME_OVER -> {}
            }
        }
        batch.end()
    }

    private fun drawWorld() {
        val originalCamX = camera.position.x
        val originalCamY = camera.position.y
        if (shakeTimer > 0f) {
            val progress = 1f - shakeTimer / 0.3f
            val currentIntensity = shakeIntensity * (1f - progress * 0.7f)
            camera.position.x += MathUtils.random(-currentIntensity, currentIntensity)
            camera.position.y += MathUtils.random(-currentIntensity, currentIntensity)
            camera.update()
            batch.projectionMatrix = camera.combined
        }

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
    }

    private fun drawMainMenu() {
        Widgets.text(batch, titleFont, Strings.t("menuTitle"), worldWidth / 2f, worldHeight * 0.86f)
        if (worldHeight >= 520f) {
            font.color = Color.CYAN
            Widgets.text(batch, font, Strings.t("menuSubtitle"), worldWidth / 2f, worldHeight * 0.74f)
        }
        font.color = Color.WHITE
        val labels = menuLabels()
        for (i in labels.indices) {
            val (cx, cy) = Widgets.stack(worldWidth, worldHeight, i, labels.size)
            Widgets.pill(batch, font, uiPixel, cx, cy, labels[i])
        }
        font.color = Color.CYAN
        Widgets.text(batch, font, "P/Esc ${Strings.t("pause").lowercase()} · M ${Strings.t("muteOff").lowercase()}", worldWidth / 2f, worldHeight * 0.05f)
        font.color = Color.WHITE
    }

    private fun drawSubScreenHeader(title: String) {
        Widgets.text(batch, titleFont, title, worldWidth / 2f, worldHeight * 0.84f)
        val pill = backPill()
        Widgets.pill(batch, font, uiPixel, pill.cx, pill.cy, Strings.t("back"))
    }

    private fun drawProfileScreen() {
        drawSubScreenHeader(Strings.t("profile"))
        val panelCx = worldWidth / 2f
        val panelCy = worldHeight / 2f
        val panelW = min(worldWidth * 0.8f, worldHeight * 1.2f).coerceAtMost(520f)
        val panelH = worldHeight * 0.58f
        Widgets.panel(batch, uiPixel, panelCx, panelCy, panelW, panelH)

        val stats = listOf(
            "${Strings.t("best")} ${Strings.t("depth")}" to "${bestDepth.toInt()} m",
            "${Strings.t("best")} ${Strings.t("score")}" to "$bestScore",
            Strings.t("pearls") to "${Profile.pearls()}",
            Strings.t("pearlsEarned") to "${Profile.lifetimePearls()}",
            Strings.t("dives") to "${Profile.dives()}"
        )
        val labelX = panelCx - panelW / 2f + 34f
        val valueX = panelCx + panelW / 2f - 34f
        val lineGap = min(46f, panelH / (stats.size + 1))
        stats.forEachIndexed { i, (label, value) ->
            val cy = panelCy + panelH / 2f - lineGap * (i + 1)
            font.color = Color.WHITE
            Widgets.textLeft(batch, font, label, labelX, cy)
            font.color = Color.GOLD
            Widgets.textRight(batch, font, value, valueX, cy)
        }
        font.color = Color.WHITE
    }

    private fun drawLeaderboardScreen() {
        drawSubScreenHeader(Strings.t("leaderboard"))
        val panelCx = worldWidth / 2f
        val panelCy = worldHeight / 2f
        val panelW = min(worldWidth * 0.8f, worldHeight * 1.2f).coerceAtMost(520f)
        val panelH = worldHeight * 0.56f
        Widgets.panel(batch, uiPixel, panelCx, panelCy, panelW, panelH)

        val entries = Leaderboard.top()

        font.color = Color.CYAN
        Widgets.textLeft(batch, font, Strings.t("rank"), panelCx - panelW / 2f + 40f, panelCy + panelH / 2f - 26f)
        val scoreX = panelCx + panelW / 4f
        val depthX = panelCx + panelW / 2f - 40f
        Widgets.textRight(batch, font, Strings.t("score"), scoreX, panelCy + panelH / 2f - 26f)
        Widgets.textRight(batch, font, Strings.t("depth"), depthX, panelCy + panelH / 2f - 26f)

        if (entries.isEmpty()) {
            font.color = Color.WHITE
            Widgets.text(batch, font, Strings.t("noRuns"), panelCx, panelCy - 10f)
        } else {
            val lineGap = min(38f, panelH / (entries.size + 1))
            entries.forEachIndexed { i, e ->
                val cy = panelCy + panelH / 2f - 56f - lineGap * i
                font.color = Color.WHITE
                Widgets.textLeft(batch, font, "${i + 1}.", panelCx - panelW / 2f + 40f, cy)
                Widgets.textRight(batch, font, "${e.score}", scoreX, cy)
                Widgets.textRight(batch, font, "${e.depth.toInt()} m", depthX, cy)
            }
        }
        font.color = Color.WHITE
    }

    private fun drawShopScreen() {
        drawSubScreenHeader(Strings.t("shop"))
        val panel = shopPanel()
        Widgets.panel(batch, uiPixel, panel.cx, panel.cy, panel.w, panel.h)

        font.color = Color.GOLD
        Widgets.textRight(batch, font, "${Strings.t("pearls")}: ${Profile.pearls()}", panel.cx + panel.w / 2f - 30f, panel.cy + panel.h / 2f - 12f)

        Profile.Upgrade.values().forEachIndexed { i, u ->
            val cy = shopRowCy(i, panel)
            val lvl = Profile.level(u)
            val pill = shopBuyPill(u, panel, i)
            val label = shopBuyLabel(u)
            val affordable = !Profile.isMaxed(u) && Profile.pearls() >= (Profile.upgradeCost(u) ?: 0)

            font.color = Color.WHITE
            Widgets.textLeft(batch, font, u.label, panel.cx - panel.w / 2f + 30f, cy)
            font.color = Color.CYAN
            Widgets.textRight(batch, font, "${Strings.t("level")} $lvl/${Profile.MAX_LEVEL}", pill.cx - pill.w / 2f - 14f, cy)
            Widgets.pill(batch, font, uiPixel, pill.cx, pill.cy, label, enabled = affordable)
        }
        font.color = Color.WHITE
    }

    private fun drawHud() {
        val glyphLayout = GlyphLayout()
        font.color = Color.WHITE

        glyphLayout.setText(font, "Hg")
        val lineHeight = glyphLayout.height
        val padding = 6f
        val lineSpacing = 8f
        val colGap = 22f

        var y = worldHeight - lineHeight - padding

        val depthStr = "${Strings.t("depth")}: ${depth.toInt()} m"
        glyphLayout.setText(font, depthStr)
        val depthW = glyphLayout.width
        font.draw(batch, glyphLayout, 10f, y)

        val scoreStr = "${Strings.t("score")}: $score"
        glyphLayout.setText(font, scoreStr)
        val scoreX = 10f + depthW + colGap
        font.draw(batch, glyphLayout, scoreX, y)

        val bestStr = "${Strings.t("best")}: ${bestDepth.toInt()} m / $bestScore"
        glyphLayout.setText(font, bestStr)
        val bestW = glyphLayout.width
        val pausePillRight = worldWidth - 12f
        val pausePillW = 64f
        var bestX = worldWidth - bestW - 12f
        val bestLimit = pausePillRight - pausePillW - 16f
        if (bestX > bestLimit - bestW) bestX = bestLimit - bestW
        if (bestX < scoreX + 16f) bestX = scoreX + 16f
        glyphLayout.setText(font, bestStr)
        font.draw(batch, glyphLayout, bestX, y)

        y -= lineHeight + lineSpacing
        val oxygenStr = "${Strings.t("oxygen")}: ${(oxygen * 100).toInt()}%"
        glyphLayout.setText(font, oxygenStr)
        font.draw(batch, glyphLayout, 10f, y)

        var pauseW = pausePillW
        var pauseH = 30f
        if (state == GameState.PLAYING) {
            val (w, h) = Widgets.pill(batch, font, uiPixel, bestX + bestW / 2f, y - lineHeight / 2f, Strings.t("pause"))
            pauseW = w
            pauseH = h
        }
        hudPauseCx = bestX + bestW / 2f
        hudPauseCy = y - lineHeight / 2f
        hudPauseW = pauseW
        hudPauseH = pauseH

        if (state == GameState.PAUSED) {
            drawPauseOverlay(glyphLayout, lineHeight)
        }
        if (state == GameState.GAME_OVER) {
            val centerX = worldWidth / 2f
            val centerY = worldHeight / 2f

            font.color = Color.RED
            val gameOverStr = "${Strings.t("gameOver")} - ${Strings.t("pressR")}"
            glyphLayout.setText(font, gameOverStr)
            font.draw(batch, glyphLayout, centerX - glyphLayout.width / 2f, centerY + glyphLayout.height / 2f + 16f)

            font.color = Color.GOLD
            val bestStr = "${Strings.t("best")}: ${bestDepth.toInt()} m   ${Strings.t("score")}: $bestScore"
            glyphLayout.setText(font, bestStr)
            font.draw(batch, glyphLayout, centerX - glyphLayout.width / 2f, centerY - glyphLayout.height / 2f - 16f)

            if (leaderboardMade) {
                font.color = Color.GOLD
                glyphLayout.setText(font, Strings.t("top5"))
                font.draw(batch, glyphLayout, centerX - glyphLayout.width / 2f, centerY - glyphLayout.height / 2f - 16f - lineHeight * 1.6f)
            }
            if (score > startBestScore) {
                font.color = Color.GOLD
                glyphLayout.setText(font, Strings.t("newRecord"))
                font.draw(batch, glyphLayout, centerX - glyphLayout.width / 2f, centerY + glyphLayout.height / 2f + 16f + lineHeight * 1.6f)
            }
            font.color = Color.WHITE

            val pillY = centerY - 118f
            Widgets.pill(batch, font, uiPixel, centerX - 95f, pillY, Strings.t("restart"))
            Widgets.pill(batch, font, uiPixel, centerX + 95f, pillY, Strings.t("menu"))
        }
    }

    private fun drawPauseOverlay(glyphLayout: GlyphLayout, lineHeight: Float) {
        val centerX = worldWidth / 2f
        val centerY = worldHeight / 2f
        val pillGap = 18f
        val rowOffset = 44f

        Widgets.panel(batch, uiPixel, centerX, centerY, worldWidth * 0.72f, rowOffset * 3.2f)

        font.color = Color.CYAN
        val pausedStr = Strings.t("paused")
        glyphLayout.setText(font, pausedStr)
        font.draw(batch, glyphLayout, centerX - glyphLayout.width / 2f, centerY + rowOffset * 2.2f + lineHeight)

        font.color = Color.WHITE
        Widgets.pill(batch, font, uiPixel, centerX, centerY + rowOffset, Strings.t("resume"))

        Widgets.pill(batch, font, uiPixel, centerX - 90f, centerY - rowOffset, Strings.t("restart"))
        Widgets.pill(batch, font, uiPixel, centerX + 90f, centerY - rowOffset, if (audio.muted) Strings.t("muteOn") else Strings.t("muteOff"))
        Widgets.pill(batch, font, uiPixel, centerX, centerY - 3f * rowOffset, Strings.t("menu"))

        font.color = Color.CYAN
        val helpStr = "P/Esc ${Strings.t("resume").lowercase()}    R ${Strings.t("restart").lowercase()}    M ${if (audio.muted) Strings.t("muteOn").lowercase() else Strings.t("muteOff").lowercase()}"
        glyphLayout.setText(font, helpStr)
        font.draw(batch, glyphLayout, centerX - glyphLayout.width / 2f, centerY - 3f * rowOffset - 30f)
        font.color = Color.WHITE
    }

    private fun playerRect() =
        Rectangle(playerX - playerRadius, playerY - playerRadius, playerRadius * 2f, playerRadius * 2f)

    private fun endGame() {
        if (state == GameState.PLAYING) {
            state = GameState.GAME_OVER
            leaderboardMade = Leaderboard.qualifies(score, depth)
            Leaderboard.submit(score, depth)
            Profile.recordDive()
            audio.playCrash()
        }
    }

    private fun applyUpgrades() {
        maxOxygen = 1f + upgradeOxygenLevel * 0.15f
        oxygen = maxOxygen
        playerSpeed = 320f * (1f + upgradeSpeedLevel * 0.08f)
    }

    private fun reset() {
        playerX = worldWidth / 2f
        playerY = worldHeight * 0.25f
        depth = 0f
        score = 0
        startBestScore = bestScore
        leaderboardMade = false
        applyUpgrades()
        elapsed = 0f
        hazardTimer = 1f
        pickupTimer = 2f
        combo = 1
        comboTimer = 0f
        shieldActive = false
        shieldCooldown = 0f
        hazards.clear()
        pickups.clear()
        particles.clear()
        state = GameState.PLAYING
    }

    private enum class GameState { MAIN_MENU, PLAYING, PAUSED, GAME_OVER, PROFILE, LEADERBOARD, SHOP }
}