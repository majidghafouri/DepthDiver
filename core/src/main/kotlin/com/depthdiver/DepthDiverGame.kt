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
import com.badlogic.gdx.graphics.glutils.FrameBuffer
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
        "max" to "MAX",
        "claim" to "CLAIM",
        "difficulty" to "DIFF",
        "easy" to "EASY",
        "normal" to "NORMAL",
        "hard" to "HARD",
        "achievements" to "ACHIEVEMENTS",
        "open" to "OPEN",
        "locked" to "LOCKED",
        "allDone" to "ALL ACHIEVEMENTS UNLOCKED",
        "bossCleared" to "LEVIATHAN CLEARED",
        "quickBuy" to "QUICK BUY",
        "reachedDepth" to "REACHED DEPTH",
        "pearlsGained" to "PEARLS GAINED",
        "milestone" to "MILESTONE",
        "leviathan" to "LEVIATHAN AHEAD!",
        "combo" to "COMBO",
        "dailyBonus" to "DAILY FIRST-DIVE BONUS",
        "dailyClaimed" to "DAILY BONUS: CLAIMED TODAY",
        "dailyReady" to "DAILY BONUS: +25 READY",
        "chReach" to "CHALLENGE: REACH",
        "chCollect" to "CHALLENGE: COLLECT",
        "chScore" to "CHALLENGE: SCORE",
        "chDone" to "CHALLENGE: COMPLETE TODAY",
        "zoneSunlit" to "SUNLIT COAST",
        "zoneReef" to "TURQUOISE REEF",
        "zoneMidnight" to "MIDNIGHT ZONE",
        "zoneAbyss" to "ABYSS"
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

internal const val MAX_FRAME_DELTA = 0.05f

internal fun safeFrameDelta(delta: Float): Float = when {
    !delta.isFinite() || delta <= 0f -> 0f
    else -> min(delta, MAX_FRAME_DELTA)
}

internal enum class ShieldCollisionResult {
    ACTIVATED,
    BLOCKED,
    FATAL,
}

internal fun resolveShieldCollision(shieldLevel: Int, shieldActive: Boolean, shieldCooldown: Float): ShieldCollisionResult = when {
    shieldActive -> ShieldCollisionResult.BLOCKED
    shieldLevel > 0 && shieldCooldown <= 0f -> ShieldCollisionResult.ACTIVATED
    else -> ShieldCollisionResult.FATAL
}

internal fun shieldDuration(shieldLevel: Int): Float = (10f - shieldLevel * 1.5f).coerceAtLeast(0f)

internal data class TouchTarget(
    val cx: Float,
    val cy: Float,
    val w: Float,
    val h: Float,
) {
    fun contains(tx: Float, ty: Float): Boolean =
        tx >= cx - w / 2f && tx <= cx + w / 2f && ty >= cy - h / 2f && ty <= cy + h / 2f
}

internal enum class GameOverAction {
    RESTART,
    MENU,
    NONE,
}

internal fun gameOverActionAt(tx: Float, ty: Float, restart: TouchTarget, menu: TouchTarget): GameOverAction = when {
    restart.contains(tx, ty) -> GameOverAction.RESTART
    menu.contains(tx, ty) -> GameOverAction.MENU
    else -> GameOverAction.NONE
}

class DepthDiverGame : ApplicationAdapter() {

    private lateinit var batch: SpriteBatch
    private lateinit var camera: OrthographicCamera
    private lateinit var font: BitmapFont
    private lateinit var titleFont: BitmapFont
    private lateinit var playerTex: Texture
    private lateinit var rockTex: Texture
    private lateinit var mineTex: Texture
    private lateinit var jellyfishTex: Texture
    private lateinit var sharkTex: Texture
    private lateinit var eelTex: Texture
    private lateinit var fishTex: Texture
    private lateinit var pearlTex: Texture
    private lateinit var oxyTex: Texture
    private lateinit var uiPixel: Texture

    private val hazards = mutableListOf<Hazard>()
    private val pickups = mutableListOf<Pickup>()
    private var state: GameState = GameState.MAIN_MENU
    private var frameDelta = 0f
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
    private var runPearls = 0

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
    private var maxComboWindow = 5f
    private var shieldActive = false
    private var shieldCooldown = 0f

    private var shakeTimer = 0f
    private var shakeIntensity = 0f

    private val particles = mutableListOf<Particle>()

    private var menuTime = 0f
    private var achievementToast: String? = null
    private var achievementToastTimer = 0f
    private var nextMilestone = 50f
    private var bossWarning = 0f
    private var lowOxyTick = 0f

    private var playerSpeed = 320f
    private val playerRadius = 18f
    private val pixelsPerMeter = 20f
    private var worldWidth = 800f
    private var worldHeight = 600f
    private var menuFbo: FrameBuffer? = null

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
        if (prefs.getBoolean("runSaved", false)) {
            prefs.remove("runSaved")
            prefs.flush()
        }
        refreshUpgradeLevels()
        applyUpgrades()
        audio.init()

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
        sharkPix.fillCircle(52, 16, 14)
        sharkPix.fillTriangle(38, 16, 18, 6, 18, 26)
        sharkPix.fillTriangle(66, 16, 46, 4, 46, 28)
        sharkPix.setColor(0.85f, 0.9f, 0.95f, 1f)
        sharkPix.fillCircle(56, 21, 7)
        sharkPix.setColor(0.55f, 0.62f, 0.72f, 1f)
        sharkPix.fillTriangle(46, 8, 56, 2, 60, 10)
        sharkPix.setColor(0.1f, 0.1f, 0.14f, 1f)
        sharkPix.fillCircle(62, 12, 2)
        sharkTex = Texture(sharkPix)
        sharkPix.dispose()

        val eelPix = Pixmap(96, 32, Pixmap.Format.RGBA8888)
        eelPix.setColor(0.16f, 0.5f, 0.34f, 1f)
        eelPix.fillCircle(30, 16, 10)
        eelPix.fillCircle(48, 16, 9)
        eelPix.fillCircle(66, 16, 8)
        eelPix.fillCircle(82, 16, 7)
        eelPix.fillTriangle(24, 16, 10, 7, 10, 25)
        eelPix.setColor(0.35f, 0.75f, 0.5f, 1f)
        eelPix.fillRectangle(22, 21, 52, 4)
        eelPix.setColor(0.95f, 0.9f, 0.3f, 1f)
        eelPix.fillCircle(87, 13, 2)
        eelPix.setColor(0.06f, 0.2f, 0.15f, 1f)
        eelPix.fillCircle(87, 9, 2)
        eelTex = Texture(eelPix)
        eelPix.dispose()

        val fishPix = Pixmap(28, 14, Pixmap.Format.RGBA8888)
        fishPix.setColor(0.45f, 0.75f, 0.95f, 1f)
        fishPix.fillCircle(12, 7, 5)
        fishPix.setColor(0.65f, 0.85f, 1f, 1f)
        fishPix.fillCircle(16, 8, 3)
        fishPix.setColor(0.45f, 0.75f, 0.95f, 1f)
        fishPix.fillTriangle(9, 7, 2, 3, 2, 11)
        fishPix.setColor(0.1f, 0.15f, 0.25f, 1f)
        fishPix.fillCircle(21, 8, 1)
        fishTex = Texture(fishPix)
        fishPix.dispose()

        reset()
        state = GameState.MAIN_MENU
    }

    override fun resize(width: Int, height: Int) {
        worldWidth = width.toFloat()
        worldHeight = height.toFloat()
        camera.setToOrtho(false, worldWidth, worldHeight)
    }

    override fun render() {
        frameDelta = safeFrameDelta(Gdx.graphics.deltaTime)
        menuTime += frameDelta
        if (achievementToastTimer > 0f) {
            achievementToastTimer -= frameDelta
            if (achievementToastTimer <= 0f) achievementToast = null
        }
        if (bossWarning > 0f) bossWarning -= frameDelta
        handleInput()
        update(frameDelta)
        draw()
    }

    override fun pause() {
        pauseGame()
    }

    override fun resume() {
        frameDelta = 0f
    }

    private fun pauseGame() {
        if (state != GameState.PLAYING) return
        state = GameState.PAUSED
        stopShake()
    }

    private fun resumeGame() {
        if (state != GameState.PAUSED) return
        state = GameState.PLAYING
        frameDelta = 0f
    }

    private fun stopShake() {
        shakeTimer = 0f
        shakeIntensity = 0f
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
        sharkTex.dispose()
        eelTex.dispose()
        fishTex.dispose()
        pearlTex.dispose()
        oxyTex.dispose()
        menuFbo?.dispose()
        menuFbo = null
        audio.dispose()
    }

    /** One low-res render target used to fake a soft blur behind menu content. */
    private fun ensureMenuFbo() {
        val wantW = (worldWidth / 4).toInt().coerceAtLeast(1)
        val wantH = (worldHeight / 4).toInt().coerceAtLeast(1)
        val cur = menuFbo
        if (cur != null && cur.width == wantW && cur.height == wantH) return
        cur?.dispose()
        menuFbo = FrameBuffer(Pixmap.Format.RGBA8888, wantW, wantH, false).apply {
            colorBufferTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        }
    }

    private fun update(delta: Float) {
        if (state != GameState.PLAYING) return
        elapsed += delta
        val diff = currentDifficulty()
        oxygen -= delta * diff.drain
        depth = max(depth, (worldHeight - max(playerY, playerRadius)) / pixelsPerMeter)
        if (oxygen <= 0f) {
            oxygen = 0f
            settleRunRecords()
            endGame()
            return
        }

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

        val depthFactor = (depth / 40f).coerceAtLeast(0f)
        val scrollSpeed = diff.baseScroll + depthFactor * (40f * diff.ramp)

        hazardTimer -= delta
        if (hazardTimer <= 0) {
            spawnHazard()
            hazardTimer = MathUtils.random(1.4f, 2.6f) * diff.spawnMul / (1f + depthFactor * 0.6f)
        }
        pickupTimer -= delta
        if (pickupTimer <= 0) {
            spawnPickup()
            pickupTimer = MathUtils.random(3f, 5.5f) * diff.pickupMul
        }

        updateEntities(delta, scrollSpeed)

        val hazardIterator = hazards.iterator()
        while (hazardIterator.hasNext()) {
            val hazard = hazardIterator.next()
            if (!playerRect().overlaps(hazard.rect)) continue
            when (resolveShieldCollision(upgradeShieldLevel, shieldActive, shieldCooldown)) {
                ShieldCollisionResult.ACTIVATED -> {
                    shieldActive = true
                    shieldCooldown = shieldDuration(upgradeShieldLevel)
                    audio.playClick()
                    triggerShake(0.15f, 8f)
                    Gdx.input.vibrate(60)
                    spawnParticles(playerX, playerY, Color.MAGENTA, 15)
                    hazardIterator.remove()
                }
                ShieldCollisionResult.BLOCKED -> {
                    spawnParticles(playerX, playerY, Color.MAGENTA, 6)
                    hazardIterator.remove()
                }
                ShieldCollisionResult.FATAL -> {
                    triggerShake(0.3f, 12f)
                    Gdx.input.vibrate(100)
                    spawnParticles(playerX, playerY, Color.RED, 12)
                    settleRunRecords()
                    endGame()
                    return
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
                        val window = 5f + upgradeComboLevel * 2f
                        comboTimer = window
                        maxComboWindow = window
                        val pearlValue = (5 * (1 + upgradePearlValueLevel * 0.5)).toInt()
                        score += pearlValue * combo
                        runPearls += pearlValue
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

        if (oxygen <= maxOxygen * 0.25f) {
            lowOxyTick -= delta
            if (lowOxyTick <= 0f) {
                audio.playAlert()
                lowOxyTick = 0.85f
            }
        } else {
            lowOxyTick = 0f
        }

        if (comboTimer > 0f) {
            comboTimer -= delta
            if (comboTimer <= 0f) combo = 1
        }

        settleRunRecords()

        val earned = Achievements.checkAndEarn()
        if (earned != null) {
            achievementToast = earned
            achievementToastTimer = 3f
            audio.playAchieve()
        }

        while (depth >= nextMilestone) {
            val m = nextMilestone
            nextMilestone += 50f
            runPearls += 10
            Profile.addPearls(10)
            Profile.addLifetimePearls(10)
            achievementToast = "${Strings.t("milestone")} ${m.toInt()} M +10"
            achievementToastTimer = 3f
            audio.playAchieve()
        }

        val activeCh = Challenge.activeFor(Profile.dailyDay())
        if (!Challenge.claimedFor(activeCh) && activeCh.met(depth, runPearls, score)) {
            Challenge.claim(activeCh)
            val bonus = Challenge.REWARD
            runPearls += bonus
            Profile.addPearls(bonus)
            Profile.addLifetimePearls(bonus)
            achievementToast = "${Strings.t("challengeDone")} +$bonus"
            achievementToastTimer = 3f
            audio.playAchieve()
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
                is Hazard.Eel -> {
                    hazard.rect.x += 150f * hazard.dir * delta
                    hazard.rect.y = hazard.baseY - scrollSpeed * (elapsed - hazard.spawn) * 0.35f + MathUtils.sin(elapsed * 2f + hazard.phase) * 8f
                }
            }
            if (hazard.rect.y + hazard.rect.height < 0f ||
                hazard.rect.x + hazard.rect.width < 0f ||
                hazard.rect.x > worldWidth
            ) {
                if (hazard is Hazard.Shark && hazard.isBoss && hazard.rect.y + hazard.rect.height < 0f) {
                    onBossEscaped()
                }
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
            depth > 45f && roll < 0.45f -> {
                val dir = if (MathUtils.random() < 0.5f) 1 else -1
                val baseY = MathUtils.random(0.35f, 0.7f) * worldHeight
                val startX = if (dir == 1) -170f else worldWidth + 10f
                hazards.add(
                    Hazard.Eel(
                        Rectangle(startX, baseY, 150f, 34f),
                        dir,
                        MathUtils.random(0f, MathUtils.PI2),
                        baseY,
                        elapsed
                    )
                )
            }
            depth > 35f && roll < 0.65f -> {
                hazards.add(
                    Hazard.Jellyfish(
                        Rectangle(x, worldHeight + 60f, 60f, 60f),
                        MathUtils.random(0f, MathUtils.PI2),
                        MathUtils.random(25f, 45f),
                        x
                    )
                )
            }
            depth > 18f && roll < 0.85f -> {
                hazards.add(Hazard.Mine(Rectangle(x, worldHeight + 48f, 48f, 48f), MathUtils.random(0f, MathUtils.PI2)))
            }
            depth > 80f && roll < 0.97f -> {
                val boss = depth > 120f && MathUtils.random() < 0.06f
                if (boss) {
                    bossWarning = 2.5f
                    audio.playAlarm()
                }
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

    private fun onBossEscaped() {
        val bonus = 50
        runPearls += bonus
        Profile.addPearls(bonus)
        Profile.addLifetimePearls(bonus)
        achievementToast = "${Strings.t("bossCleared")} +$bonus"
        achievementToastTimer = 3f
        audio.playAchieve()
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

            GameState.PROFILE, GameState.LEADERBOARD, GameState.SHOP, GameState.ACHIEVEMENTS -> {
                if (escJust || pJust) {
                    goToMenu()
                    return
                }
                if (Gdx.input.justTouched()) {
                    when (state) {
                        GameState.SHOP -> handleShopTouch(touchX(), touchY())
                        GameState.PROFILE -> handleProfileTouch(touchX(), touchY())
                        else -> handleSubScreenTouch(touchX(), touchY())
                    }
                }
            }

            GameState.PLAYING -> {
                if (escJust || pJust) {
                    pauseGame()
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
                    pauseGame()
                    return
                }
            }

            GameState.PAUSED -> {
                if (escJust || pJust) {
                    resumeGame()
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
                    val targets = gameOverTargets()
                    when (gameOverActionAt(touchX(), touchY(), targets.restart, targets.menu)) {
                        GameOverAction.RESTART -> reset()
                        GameOverAction.MENU -> goToMenu()
                        GameOverAction.NONE -> Unit
                    }
                    return
                }
            }
        }

        if (state != GameState.PLAYING) return

        val delta = frameDelta
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

    private fun touchY(): Float = Gdx.graphics.height.toFloat() - Gdx.input.y.toFloat()

    private fun menuLabels(): List<String> = listOf(
        Strings.t("play"),
        Strings.t("profile"),
        Strings.t("leaderboard"),
        Strings.t("shop"),
        if (audio.muted) Strings.t("muteOn") else Strings.t("muteOff"),
        Strings.t("quit")
    )

    private fun currentDifficulty(): Difficulty =
        Difficulty.values()[Profile.difficulty().coerceIn(0, Difficulty.values().size - 1)]

    private fun difficultyLabel(): String =
        "${Strings.t("difficulty")}: ${Strings.t(currentDifficulty().name.lowercase())}"

    /** 2-column button grid used by the main menu. */
    private fun menuGridPos(i: Int): Pair<Float, Float> {
        val rows = (menuLabels().size + 1) / 2
        val gap = min(72f, worldHeight * 0.115f)
        val startY = worldHeight * 0.64f
        val row = i / 2
        val col = i % 2
        val cx = worldWidth * (if (col == 0) 0.335f else 0.665f)
        return cx to (startY - row * gap)
    }

    /** EASY / NORMAL / HARD segmented controls, well clear of gesture bars. */
    private fun difficultySegs(): Array<FloatArray> {
        val w = max(196f, Widgets.pillW(font, Strings.t("normal")) + 10f)
        val h = Widgets.pillH(font, Strings.t("normal"))
        val cy = worldHeight * 0.10f
        val gap = w + 18f
        return arrayOf(
            floatArrayOf(worldWidth / 2f - gap, cy, w, h, 0f),
            floatArrayOf(worldWidth / 2f, cy, w, h, 1f),
            floatArrayOf(worldWidth / 2f + gap, cy, w, h, 2f)
        )
    }

    private fun handleMenuTouch(tx: Float, ty: Float) {
        for (s in difficultySegs()) {
            if (Widgets.contains(tx, ty, s[0], s[1], s[2], s[3])) {
                val index = s[4].toInt()
                if (index != Profile.difficulty()) {
                    Profile.setDifficulty(index)
                    audio.playClick()
                }
                return
            }
        }
        val labels = menuLabels()
        for (i in labels.indices) {
            val (cx, cy) = menuGridPos(i)
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

    private fun handleProfileTouch(tx: Float, ty: Float) {
        val backPill = arrayOf(worldWidth * 0.2f, worldHeight * 0.08f)
        if (Widgets.contains(tx, ty, backPill[0], backPill[1], Widgets.pillW(font, Strings.t("back")), Widgets.pillH(font, Strings.t("back")))) {
            goToMenu()
            return
        }
        val achLabel = "${Strings.t("achievements")} ${Achievements.count()}/${Achievements.ALL.size}"
        if (Widgets.contains(tx, ty, worldWidth * 0.8f, worldHeight * 0.08f, Widgets.pillW(font, achLabel), Widgets.pillH(font, achLabel))) {
            state = GameState.ACHIEVEMENTS
            audio.playClick()
            return
        }
        val day = Profile.dailyDay()
        val active = Challenge.activeFor(day)
        if (!Challenge.claimedFor(active) && active.met(bestDepth, Profile.bestRunPearls(), bestScore)) {
            val label = "${Strings.t("claim")} +${Challenge.REWARD}"
            if (Widgets.contains(tx, ty, worldWidth / 2f, profileClaimCy(), Widgets.pillW(font, label), Widgets.pillH(font, label))) {
                Challenge.claim(active)
                Profile.addPearls(Challenge.REWARD)
                Profile.addLifetimePearls(Challenge.REWARD)
                achievementToast = "${Strings.t("claim")} +${Challenge.REWARD}"
                achievementToastTimer = 2.5f
                audio.playClick()
            }
        }
    }

    private fun backPill(): ShopRect =
        ShopRect(worldWidth / 2f, worldHeight * 0.08f, Widgets.pillW(font, Strings.t("back")), Widgets.pillH(font, Strings.t("back")))

    private fun shopBuyLabel(u: Profile.Upgrade): String {
        val cost = Profile.upgradeCost(u)
        return if (cost == null) Strings.t("max") else "${Strings.t("buy")} $cost"
    }

    private class ShopRect(val cx: Float, val cy: Float, val w: Float, val h: Float)

    /** Shared panel width for the sub-screens (profile/achievements/leaderboard/shop):
     *  wide enough that long stat/achievement names never collide with their values. */
    private fun subPanelW(): Float = min(worldWidth * 0.86f, worldHeight * 1.6f).coerceAtMost(700f)

    private fun shopPanel(): ShopRect {
        val panelW = subPanelW()
        val panelH = worldHeight * 0.6f
        return ShopRect(worldWidth / 2f, worldHeight / 2f, panelW, panelH)
    }

    private fun shopHeaderCy(panel: ShopRect): Float =
        panel.cy + panel.h / 2f - 34f

    private fun shopRowCy(index: Int, panel: ShopRect): Float {
        val lineGap = min(60f, (panel.h - 84f) / Profile.Upgrade.values().size)
        return panel.cy + panel.h / 2f - 84f - lineGap * index
    }

    /** Fixed left edge for every BUY pill so the buttons form one clean column. */
    private fun shopPillLeft(panel: ShopRect): Float = panel.cx + panel.w / 2f - 150f

    private fun shopBuyPill(u: Profile.Upgrade, panel: ShopRect, index: Int): ShopRect {
        val label = shopBuyLabel(u)
        val leftX = shopPillLeft(panel)
        return ShopRect(
            leftX + Widgets.pillW(font, label) / 2f,
            shopRowCy(index, panel) - 13f,
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
        menuTime = 0f
        state = GameState.MAIN_MENU
    }

    private data class GameOverBox(
        val titleY: Float,
        val recordY: Float,
        val top5Y: Float,
        val panelTop: Float,
        val panelBottom: Float,
        val panelH: Float,
        val pillY: Float,
    )

    private data class GameOverTargets(
        val restart: TouchTarget,
        val menu: TouchTarget,
    )

    private fun gameOverTargets(): GameOverTargets {
        val centerX = worldWidth / 2f
        val pillY = gameOverBox().pillY
        val restartLabel = Strings.t("restart")
        val menuLabel = Strings.t("menu")
        return GameOverTargets(
            restart = TouchTarget(centerX - 95f, pillY, Widgets.pillW(font, restartLabel), Widgets.pillH(font, restartLabel)),
            menu = TouchTarget(centerX + 95f, pillY, Widgets.pillW(font, menuLabel), Widgets.pillH(font, menuLabel)),
        )
    }

    /** One shared source for the game-over screen layout so drawing and hit-tests agree
     *  and text/buttons always clear each other. Badge/panel rows are stacked from the
     *  title downward with spacing that scales with the screen, never fixed offsets. */
    private fun gameOverBox(): GameOverBox {
        val centerY = worldHeight / 2f
        val tall = worldHeight >= 560f
        if (!tall) {
            return GameOverBox(centerY + 96f, Float.NaN, Float.NaN, 0f, 0f, 0f, centerY - 118f)
        }
        val titleY = centerY + min(290f, worldHeight * 0.26f)
        val showD = score > startBestScore
        val showT = leaderboardMade
        val recordY = if (showD) titleY - 50f else Float.NaN
        val top5Y = if (showT) titleY - (if (showD) 96f else 50f) else Float.NaN
        val rowGap = min(44f, worldHeight / 22f)
        val contentH = rowGap * 4f + 58f
        var panelTop = titleY - 30f
        if (showD) panelTop -= 46f
        if (showT) panelTop -= 46f
        val panelBottom = panelTop - contentH
        val pillY = panelBottom - 85f
        return GameOverBox(titleY, recordY, top5Y, panelTop, panelBottom, contentH, pillY)
    }

    private fun handlePauseTouch(tx: Float, ty: Float) {
        val labels = listOf(Strings.t("resume")) + listOf(Strings.t("menu"))
        val centerX = worldWidth / 2f
        val centerY = worldHeight / 2f
        val tall = worldHeight >= 540f
        val lineHeight = GlyphLayout(font, "Hg").height
        val r = pauseRows(tall, lineHeight)
        if (Widgets.contains(tx, ty, centerX, centerY + r.resume, Widgets.pillW(font, labels[0]), Widgets.pillH(font, labels[0]))) {
            resumeGame()
            audio.playClick()
            return
        }
        if (Widgets.contains(tx, ty, centerX - 90f, centerY + r.side, Widgets.pillW(font, Strings.t("restart")), Widgets.pillH(font, Strings.t("restart")))) {
            reset()
            audio.playClick()
            return
        }
        if (Widgets.contains(tx, ty, centerX + 90f, centerY + r.side, Widgets.pillW(font, if (audio.muted) Strings.t("muteOn") else Strings.t("muteOff")), Widgets.pillH(font, if (audio.muted) Strings.t("muteOn") else Strings.t("muteOff")))) {
            audio.toggleMute()
            audio.playClick()
            return
        }
        if (Widgets.contains(tx, ty, centerX, centerY + r.menu, Widgets.pillW(font, labels[1]), Widgets.pillH(font, labels[1]))) {
            goToMenu()
            audio.playClick()
            return
        }
        if (tall) {
            val oxyLabel = shopBuyLabel(Profile.Upgrade.Oxygen)
            val spdLabel = shopBuyLabel(Profile.Upgrade.Speed)
            if (Widgets.contains(tx, ty, centerX - 90f, centerY + r.buy, Widgets.pillW(font, oxyLabel), Widgets.pillH(font, oxyLabel))) {
                buyUpgrade(Profile.Upgrade.Oxygen)
                return
            }
            if (Widgets.contains(tx, ty, centerX + 90f, centerY + r.buy, Widgets.pillW(font, spdLabel), Widgets.pillH(font, spdLabel))) {
                buyUpgrade(Profile.Upgrade.Speed)
                return
            }
        }
    }

    private fun draw() {
        Gdx.gl.glClearColor(0.02f, 0.12f, 0.25f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        val inGame = state == GameState.PLAYING || state == GameState.PAUSED || state == GameState.GAME_OVER

        batch.projectionMatrix = camera.combined
        batch.begin()

        if (inGame) {
            if (state == GameState.PAUSED) drawWorldBlurred() else drawWorld()
            drawHud()
        } else {
            when (state) {
                GameState.MAIN_MENU -> drawMainMenu()
                GameState.PROFILE -> {
                    drawMenuBackgroundBlur()
                    drawProfileScreen()
                }
                GameState.LEADERBOARD -> {
                    drawMenuBackgroundBlur()
                    drawLeaderboardScreen()
                }
                GameState.SHOP -> {
                    drawMenuBackgroundBlur()
                    drawShopScreen()
                }
                GameState.ACHIEVEMENTS -> {
                    drawMenuBackgroundBlur()
                    drawAchievementsScreen()
                }
                GameState.PLAYING, GameState.PAUSED, GameState.GAME_OVER -> {}
            }
        }
        batch.end()
    }

    /** Renders the frozen game world into the same low-res FBO used by the menu and
     *  composites it back upscaled (bilinear + dim tint) so the pause overlay pops. */
    private fun drawWorldBlurred() {
        ensureMenuFbo()
        val fbo = menuFbo ?: return
        batch.end()
        fbo.begin()
        Gdx.gl.glClearColor(0.02f, 0.12f, 0.25f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        batch.projectionMatrix = camera.combined
        batch.begin()
        drawWorld()
        batch.end()
        fbo.end()
        batch.projectionMatrix = camera.combined
        batch.begin()
        batch.setColor(1f, 1f, 1f, 0.9f)
        batch.draw(fbo.colorBufferTexture, 0f, 0f, worldWidth, worldHeight)
        batch.setColor(0f, 0.03f, 0.10f, 0.28f)
        batch.draw(uiPixel, 0f, 0f, worldWidth, worldHeight)
        batch.setColor(Color.WHITE)
    }

    private fun drawWorld() {
        drawWorldBackground()
        val originalCamX = camera.position.x
        val originalCamY = camera.position.y
        if (state == GameState.PLAYING && shakeTimer > 0f) {
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
            val isBoss = hazard is Hazard.Shark && hazard.isBoss
            if (isBoss) batch.setColor(1f, 0.45f, 0.4f, 1f)
            when (hazard) {
                is Hazard.Rock -> batch.draw(rockTex, hazard.rect.x, hazard.rect.y, hazard.rect.width, hazard.rect.height)
                is Hazard.Mine -> batch.draw(mineTex, hazard.rect.x, hazard.rect.y, hazard.rect.width, hazard.rect.height)
                is Hazard.Jellyfish -> batch.draw(jellyfishTex, hazard.rect.x, hazard.rect.y, hazard.rect.width, hazard.rect.height)
                is Hazard.Shark -> batch.draw(sharkTex, hazard.rect.x, hazard.rect.y, hazard.rect.width, hazard.rect.height)
                is Hazard.Eel ->
                    if (hazard.dir > 0) {
                        batch.draw(eelTex, hazard.rect.x, hazard.rect.y, hazard.rect.width, hazard.rect.height)
                    } else {
                        batch.draw(eelTex, hazard.rect.x + hazard.rect.width, hazard.rect.y, -hazard.rect.width, hazard.rect.height)
                    }
            }
            batch.setColor(1f, 1f, 1f, 1f)
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

        if (state == GameState.PLAYING && shakeTimer > 0f) {
            camera.position.x = originalCamX
            camera.position.y = originalCamY
            camera.update()
            batch.projectionMatrix = camera.combined
        }
    }

    private fun zoneAt(depth: Float): Int = when {
        depth < 40f -> 0
        depth < 120f -> 1
        depth < 300f -> 2
        else -> 3
    }

    private fun zoneKey(zone: Int): String = when (zone) {
        0 -> "zoneSunlit"
        1 -> "zoneReef"
        2 -> "zoneMidnight"
        else -> "zoneAbyss"
    }

    private fun drawWorldBackground() {
        val palettes = arrayOf(
            floatArrayOf(0.05f, 0.30f, 0.46f, 0.02f, 0.13f, 0.28f),
            floatArrayOf(0.02f, 0.22f, 0.40f, 0.008f, 0.09f, 0.20f),
            floatArrayOf(0.008f, 0.12f, 0.19f, 0.003f, 0.04f, 0.085f),
            floatArrayOf(0.005f, 0.055f, 0.085f, 0.002f, 0.012f, 0.03f)
        )
        val z = zoneAt(depth)
        val p = when (z) {
            0 -> (depth / 40f).coerceIn(0f, 1f)
            1 -> ((depth - 40f) / 80f).coerceIn(0f, 1f)
            2 -> ((depth - 120f) / 180f).coerceIn(0f, 1f)
            else -> 0f
        }
        val a = palettes[z]
        val b = palettes[if (z >= 3) z else z + 1]
        fun lerp(ai: Int, bi: Int) = a[ai] + (b[bi] - a[ai]) * p
        val topR = lerp(0, 0)
        val topG = lerp(1, 1)
        val topB = lerp(2, 2)
        val botR = lerp(3, 3)
        val botG = lerp(4, 4)
        val botB = lerp(5, 5)

        val bands = 16
        val bandH = worldHeight / bands
        for (i in 0 until bands) {
            val t = (i + 1f) / bands
            batch.setColor(
                topR + (botR - topR) * t,
                topG + (botG - topG) * t,
                topB + (botB - topB) * t,
                1f
            )
            batch.draw(uiPixel, 0f, i * bandH - 1f, worldWidth, bandH + 2f)
        }

        val streakCount = 5
        for (i in 0 until streakCount) {
            val x = ((i * 31) % 100) / 100f * worldWidth
            val speed = 26f + (i % 3) * 14f
            val start = ((i * 47) % 100) / 100f * (worldHeight + 100f)
            val y = (start + elapsed * speed) % (worldHeight + 100f) - 50f
            batch.setColor(1f, 1f, 1f, 0.045f)
            batch.draw(uiPixel, x - 70f, y - 1f, 140f, 2f)
        }

        drawAmbientFish()

        val surface = (1f - (depth / 40f)).coerceIn(0f, 1f)
        if (surface > 0.05f) {
            val rayCount = 4
            for (i in 0 until rayCount) {
                val sway = MathUtils.sin(elapsed * 0.35f + i * 1.3f) * 14f
                val baseX = worldWidth * (0.16f + i * 0.24f) + sway
                val rayH = worldHeight * (0.16f + (i % 2) * 0.06f)
                val segments = 6
                for (s in 0 until segments) {
                    val t = (s + 1) / segments.toFloat()
                    val alpha = 0.05f * surface * (1f - t * 0.85f)
                    val w = 26f - t * 12f
                    batch.setColor(0.75f, 0.95f, 1f, alpha)
                    batch.draw(uiPixel, baseX - w / 2f, worldHeight - rayH * t, w, rayH * (t - (s) / segments.toFloat()) + 1f)
                }
            }
            batch.setColor(1f, 1f, 1f, 0.07f * surface)
            batch.draw(uiPixel, 0f, worldHeight - 40f, worldWidth, 40f)
        }
        batch.setColor(1f, 1f, 1f, 1f)
    }

    private fun drawAmbientFish() {
        val fishCount = 9
        val scale = if (worldWidth >= 1200f) 1.15f else 0.9f
        for (i in 0 until fishCount) {
            val laneFrac = ((i * 29) % 100) / 100f
            val baseY = worldHeight * (0.08f + laneFrac * 0.78f)
            val speed = 20f + (i % 4) * 9f
            val span = worldWidth + 180f
            val dir = if ((i % 2) == 0) 1 else -1
            val cx = if (dir == 1) {
                (elapsed * speed % span) - 90f
            } else {
                span - (elapsed * speed % span) - 90f
            }
            val cy = baseY + MathUtils.sin(elapsed * 1.1f + i * 2.1f) * 7f
            val size = (22f + (i % 3) * 7f) * scale
            val alpha = 0.10f + (i % 3) * 0.05f
            batch.setColor(0.7f, 0.9f, 1f, alpha)
            if (dir == 1) {
                batch.draw(fishTex, cx, cy, size, size * 0.5f)
            } else {
                batch.draw(fishTex, cx + size, cy, -size, size * 0.5f)
            }
        }
    }

    private fun drawMainMenu() {
        drawMenuBackgroundBlur()
        drawMenuContent()
    }

    /** Renders the animated menu background into a 1/4-res FBO, then composites it
     *  back upscaled with bilinear filtering (with a dim tint) to fake a soft blur
     *  behind the crisp menu content. */
    private fun drawMenuBackgroundBlur() {
        ensureMenuFbo()
        val fbo = menuFbo ?: return
        batch.end()
        fbo.begin()
        Gdx.gl.glClearColor(0.02f, 0.12f, 0.25f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        batch.projectionMatrix = camera.combined
        batch.begin()
        drawMenuBackground()
        drawMenuVignette()
        batch.end()
        fbo.end()
        batch.projectionMatrix = camera.combined
        batch.begin()
        batch.setColor(1f, 1f, 1f, 0.92f)
        batch.draw(fbo.colorBufferTexture, 0f, 0f, worldWidth, worldHeight)
        batch.setColor(0f, 0.03f, 0.10f, 0.22f)
        batch.draw(uiPixel, 0f, 0f, worldWidth, worldHeight)
        batch.setColor(Color.WHITE)
    }

    private fun drawMenuContent() {
        val pulse = 0.5f + 0.5f * MathUtils.sin(menuTime * 1.8f)
        val titleY = worldHeight * 0.86f
        titleFont.color = Color(0.12f, 0.5f, 0.95f, 0.22f + 0.15f * pulse)
        for (off in floatArrayOf(-3f, 3f)) {
            Widgets.text(batch, titleFont, Strings.t("menuTitle"), worldWidth / 2f + off, titleY)
        }
        titleFont.color = Color(0.4f + 0.5f * pulse, 0.87f, 1f, 1f)
        Widgets.text(batch, titleFont, Strings.t("menuTitle"), worldWidth / 2f, titleY)
        titleFont.color = Color(0.35f, 0.85f, 1f, 1f)
        batch.setColor(0.2f, 0.78f, 1f, 0.55f)
        batch.draw(uiPixel, worldWidth / 2f - 170f, titleY - 26f, 340f, 4f)
        batch.draw(uiPixel, worldWidth / 2f - 110f, titleY - 35f, 220f, 3f)
        batch.setColor(Color.WHITE)

        if (worldHeight >= 520f) {
            font.color = Color.CYAN
            Widgets.text(batch, font, Strings.t("menuSubtitle"), worldWidth / 2f, worldHeight * 0.74f)
        }
        font.color = Color.WHITE
        val labels = menuLabels()
        for (i in labels.indices) {
            val (cx, cy) = menuGridPos(i)
            Widgets.pill(batch, font, uiPixel, cx, cy, labels[i])
        }

        val segs = difficultySegs()
        font.color = Color(0.5f, 0.8f, 1f, 0.85f)
        Widgets.text(batch, font, Strings.t("difficulty"), worldWidth / 2f, segs[0][1] + segs[0][3] / 2f + 18f)
        font.color = Color.WHITE
        val current = Profile.difficulty()
        val names = listOf("easy", "normal", "hard")
        for (s in segs) {
            Widgets.segment(
                batch,
                font,
                uiPixel,
                s[0],
                s[1],
                s[2],
                s[3],
                Strings.t(names[s[4].toInt()]),
                selected = s[4].toInt() == current
            )
        }
    }

    private fun drawMenuVignette() {
        val edge = worldHeight * 0.06f
        batch.setColor(0f, 0.03f, 0.09f, 0.40f)
        batch.draw(uiPixel, 0f, worldHeight - edge, worldWidth, edge)
        batch.draw(uiPixel, 0f, 0f, worldWidth, edge)
        batch.draw(uiPixel, 0f, edge, edge, worldHeight - 2f * edge)
        batch.draw(uiPixel, worldWidth - edge, edge, edge, worldHeight - 2f * edge)
        batch.setColor(Color.WHITE)
    }

    private fun drawMenuBackground() {
        val bubbleCount = 26
        for (i in 0 until bubbleCount) {
            val xFrac = (i * 37 % 100) / 100f
            val x = xFrac * worldWidth
            val speed = 20f + (i % 5) * 9f
            val size = 4f + (i % 4) * 3f
            val start = (i * 53 % 100) / 100f * (worldHeight + 80f)
            val y = (start + menuTime * speed) % (worldHeight + 80f) - 40f
            val alpha = 0.10f + (i % 3) * 0.05f
            batch.setColor(0.55f, 0.85f, 1f, alpha)
            batch.draw(uiPixel, x - size / 2f, y - size / 2f, size, size)
        }
        val dSize = playerRadius * 2f * 1.8f
        val dx = worldWidth * 0.5f + MathUtils.sin(menuTime * 0.5f) * worldWidth * 0.16f
        val dy = worldHeight * 0.585f + MathUtils.sin(menuTime * 1.1f) * 12f
        batch.draw(playerTex, dx - dSize / 2f, dy - dSize / 2f, dSize, dSize)
        batch.setColor(1f, 1f, 1f, 1f)
    }

    private fun drawSubScreenHeader(title: String) {
        Widgets.text(batch, titleFont, title, worldWidth / 2f, worldHeight * 0.84f)
        val pill = backPill()
        Widgets.pill(batch, font, uiPixel, pill.cx, pill.cy, Strings.t("back"))
    }

    private fun drawProfileScreen() {
        Widgets.text(batch, titleFont, Strings.t("profile"), worldWidth / 2f, worldHeight * 0.84f)
        Widgets.pill(batch, font, uiPixel, worldWidth * 0.2f, worldHeight * 0.08f, Strings.t("back"))
        val achLabel = "${Strings.t("achievements")} ${Achievements.count()}/${Achievements.ALL.size}"
        Widgets.pill(batch, font, uiPixel, worldWidth * 0.8f, worldHeight * 0.08f, achLabel)
        val panelCx = worldWidth / 2f
        val panelCy = worldHeight / 2f
        val panelW = subPanelW()
        val panelH = worldHeight * 0.58f
        Widgets.panel(batch, uiPixel, panelCx, panelCy, panelW, panelH)

        val stats = listOf(
            "${Strings.t("best")} ${Strings.t("depth")}" to "${bestDepth.toInt()} m",
            "${Strings.t("best")} ${Strings.t("score")}" to "$bestScore",
            Strings.t("pearls") to "${Profile.pearls()}",
            Strings.t("pearlsEarned") to "${Profile.lifetimePearls()}",
            Strings.t("dives") to "${Profile.dives()}",
            Strings.t("achievements") to "${Achievements.count()}/${Achievements.ALL.size}"
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
        val day = Profile.dailyDay()
        val activeCh = Challenge.activeFor(day)
        val chClaimed = Challenge.claimedFor(activeCh)
        val chMet = activeCh.met(bestDepth, Profile.bestRunPearls(), bestScore)

        font.color = Color.GOLD
        Widgets.text(
            batch,
            font,
            if (Profile.claimedDailyDay() == day) Strings.t("dailyClaimed") else Strings.t("dailyReady"),
            panelCx,
            panelCy - 70f
        )
        val chText = if (chClaimed) {
            Strings.t("chDone")
        } else {
            activeCh.summary(bestDepth, Profile.bestRunPearls(), bestScore)
        }
        font.color = Color.CYAN
        Widgets.text(batch, font, chText, panelCx, panelCy - 116f)

        if (!chClaimed) {
            val claimLabel = "${Strings.t("claim")} +${Challenge.REWARD}"
            Widgets.pill(batch, font, uiPixel, panelCx, profileClaimCy(), claimLabel, enabled = chMet)
        }
        font.color = Color.WHITE
    }

    /** Claim button for the daily challenge on the profile screen. */
    private fun profileClaimCy(): Float = worldHeight / 2f - 168f

    private fun drawAchievementsScreen() {
        drawSubScreenHeader(Strings.t("achievements"))
        val panelCx = worldWidth / 2f
        val panelCy = worldHeight / 2f
        val panelW = subPanelW()
        val panelH = worldHeight * 0.52f
        Widgets.panel(batch, uiPixel, panelCx, panelCy, panelW, panelH)

        if (Achievements.count() == Achievements.ALL.size) {
            font.color = Color.GOLD
            Widgets.text(batch, font, Strings.t("allDone"), panelCx, panelCy + panelH / 2f - 22f)
        }

        val labelX = panelCx - panelW / 2f + 34f
        val statusX = panelCx + panelW / 2f - 34f
        val lineGap = min(46f, panelH / (Achievements.ALL.size + 1))
        Achievements.ALL.forEachIndexed { i, def ->
            val cy = panelCy + panelH / 2f - lineGap * (i + 1)
            if (Achievements.isUnlocked(def)) {
                font.color = Color.GOLD
                Widgets.textLeft(batch, font, def.name, labelX, cy)
                font.color = Color.WHITE
                Widgets.textRight(batch, font, Strings.t("open"), statusX, cy)
            } else {
                font.color = Color(0.45f, 0.55f, 0.65f, 1f)
                Widgets.textLeft(batch, font, def.name, labelX, cy)
                Widgets.textRight(batch, font, Strings.t("locked"), statusX, cy)
            }
        }
        font.color = Color.WHITE
    }

    private fun drawLeaderboardScreen() {
        drawSubScreenHeader(Strings.t("leaderboard"))
        val panelCx = worldWidth / 2f
        val panelCy = worldHeight / 2f
        val panelW = subPanelW()
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
        Widgets.text(batch, font, "${Strings.t("pearls")}: ${Profile.pearls()}", panel.cx, shopHeaderCy(panel))

        Profile.Upgrade.values().forEachIndexed { i, u ->
            val cy = shopRowCy(i, panel)
            val pill = shopBuyPill(u, panel, i)
            val lvl = Profile.level(u)
            val label = shopBuyLabel(u)
            val affordable = !Profile.isMaxed(u) && Profile.pearls() >= (Profile.upgradeCost(u) ?: 0)

            font.color = Color.WHITE
            Widgets.textLeft(batch, font, u.label, panel.cx - panel.w / 2f + 34f, cy)
            font.color = Color.CYAN
            Widgets.textRight(batch, font, "${Strings.t("level")} $lvl/${Profile.MAX_LEVEL}", shopPillLeft(panel) - 24f, cy)
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

        font.color = Color(0.6f, 0.85f, 1f, 0.9f)
        glyphLayout.setText(font, Strings.t(zoneKey(zoneAt(depth))))
        font.draw(batch, glyphLayout, worldWidth / 2f - glyphLayout.width / 2f, y)
        font.color = Color.WHITE

        val depthStr = "${Strings.t("depth")}: ${depth.toInt()} m"
        glyphLayout.setText(font, depthStr)
        val depthW = glyphLayout.width
        font.draw(batch, glyphLayout, 10f, y)

        val scoreStr = "${Strings.t("score")}: $score"
        glyphLayout.setText(font, scoreStr)
        val scoreX = 10f + depthW + colGap
        font.draw(batch, glyphLayout, scoreX, y)

        font.color = Color.CYAN
        glyphLayout.setText(font, difficultyLabel())
        font.draw(batch, glyphLayout, worldWidth - 10f - glyphLayout.width, y)
        font.color = Color.WHITE

        if (bossWarning > 0f) {
            font.color = Color(1f, 0.35f, 0.3f, 1f)
            glyphLayout.setText(font, Strings.t("leviathan"))
            font.draw(batch, glyphLayout, worldWidth / 2f - glyphLayout.width / 2f, y - lineHeight * 1.4f)
            font.color = Color.WHITE
        }

        y -= lineHeight + lineSpacing
        val oxygenStr = "${Strings.t("oxygen")}: ${(oxygen * 100).toInt()}%"
        glyphLayout.setText(font, oxygenStr)
        font.draw(batch, glyphLayout, 10f, y)

        y -= lineHeight + lineSpacing
        if (state == GameState.PLAYING) {
            val pl = Strings.t("pause")
            val plW = Widgets.pillW(font, pl)
            val plH = Widgets.pillH(font, pl)
            val plCX = worldWidth - 12f - plW / 2f
            val plCY = y
            val (w, h) = Widgets.pill(batch, font, uiPixel, plCX, plCY, pl)
            hudPauseW = w
            hudPauseH = h
            hudPauseCx = plCX
            hudPauseCy = plCY
        } else {
            hudPauseW = worldWidth * 0.1f
            hudPauseH = worldHeight * 0.05f
            hudPauseCx = worldWidth - 12f - hudPauseW / 2f
            hudPauseCy = y
        }

        y -= lineHeight + lineSpacing
        val bestStr = "${Strings.t("best")}: ${bestDepth.toInt()} m / $bestScore"
        glyphLayout.setText(font, bestStr)
        font.draw(batch, glyphLayout, 10f, y)

        if (state == GameState.PLAYING && combo > 1) {
            font.color = Color.GOLD
            glyphLayout.setText(font, "${Strings.t("combo")} x$combo")
            val comboY = y - lineHeight * 1.5f
            font.draw(batch, glyphLayout, 10f, comboY)
            val barW = 90f
            val barH = 5f
            val frac = (comboTimer / maxComboWindow).coerceIn(0f, 1f)
            batch.setColor(0f, 0f, 0f, 0.6f)
            batch.draw(uiPixel, 10f, comboY - lineHeight * 0.6f - barH, barW, barH)
            batch.setColor(1f, 0.85f, 0.2f, 1f)
            batch.draw(uiPixel, 10f, comboY - lineHeight * 0.6f - barH, barW * frac, barH)
            batch.setColor(1f, 1f, 1f, 1f)
            font.color = Color.WHITE
        }

        if (state == GameState.PAUSED) {
            drawPauseOverlay(glyphLayout, lineHeight)
        }
        if (state == GameState.PLAYING && oxygen <= maxOxygen * 0.25f) {
            val danger = ((maxOxygen * 0.25f - oxygen) / (maxOxygen * 0.25f)).coerceIn(0f, 1f)
            val alpha = 0.15f * danger * (0.65f + 0.35f * ((MathUtils.sin(elapsed * 5f) + 1f) / 2f))
            val edge = 26f
            batch.setColor(1f, 0.1f, 0.08f, alpha)
            batch.draw(uiPixel, 0f, worldHeight - edge, worldWidth, edge)
            batch.draw(uiPixel, 0f, 0f, worldWidth, edge)
            batch.draw(uiPixel, 0f, edge, edge, worldHeight - 2f * edge)
            batch.draw(uiPixel, worldWidth - edge, edge, edge, worldHeight - 2f * edge)
            batch.setColor(1f, 1f, 1f, 1f)
        }
        if (state == GameState.GAME_OVER) {
            val centerX = worldWidth / 2f
            val box = gameOverBox()
            val tall = worldHeight >= 560f

            font.color = Color.RED
            val gameOverStr = "${Strings.t("gameOver")} - ${Strings.t("pressR")}"
            glyphLayout.setText(font, gameOverStr)
            font.draw(batch, glyphLayout, centerX - glyphLayout.width / 2f, box.titleY)

            if (tall) {
                if (!box.recordY.isNaN()) {
                    font.color = Color.GOLD
                    glyphLayout.setText(font, Strings.t("newRecord"))
                    font.draw(batch, glyphLayout, centerX - glyphLayout.width / 2f, box.recordY)
                }
                if (!box.top5Y.isNaN()) {
                    font.color = Color.GOLD
                    glyphLayout.setText(font, Strings.t("top5"))
                    font.draw(batch, glyphLayout, centerX - glyphLayout.width / 2f, box.top5Y)
                }

                val panelCx = centerX
                val panelCy = (box.panelTop + box.panelBottom) / 2f
                val panelW = min(worldWidth * 0.75f, 460f)
                Widgets.panel(batch, uiPixel, panelCx, panelCy, panelW, box.panelH)

                val rowGap = min(44f, worldHeight / 22f)
                val rows = listOf(
                    Pair(Strings.t("reachedDepth"), "${depth.toInt()} m"),
                    Pair(Strings.t("score"), "$score"),
                    Pair(Strings.t("pearlsGained"), "+$runPearls"),
                    Pair("${Strings.t("best")} ${Strings.t("depth").lowercase()}", "${bestDepth.toInt()} m"),
                    Pair("${Strings.t("best")} ${Strings.t("score").lowercase()}", "$bestScore")
                )
                val labelX = panelCx - panelW / 2f + 30f
                val valueX = panelCx + panelW / 2f - 30f
                rows.forEachIndexed { i, (label, value) ->
                    val cy = box.panelTop - 28f - rowGap * i
                    font.color = Color.WHITE
                    Widgets.textLeft(batch, font, label, labelX, cy)
                    font.color = Color.GOLD
                    Widgets.textRight(batch, font, value, valueX, cy)
                }
                font.color = Color.WHITE
            } else {
                val centerY = worldHeight / 2f
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
            }
            font.color = Color.WHITE

            val targets = gameOverTargets()
            Widgets.pill(batch, font, uiPixel, targets.restart.cx, targets.restart.cy, Strings.t("restart"))
            Widgets.pill(batch, font, uiPixel, targets.menu.cx, targets.menu.cy, Strings.t("menu"))
        }
        if (achievementToastTimer > 0f) drawAchievementToast()
    }

    private fun drawAchievementToast() {
        val s = achievementToast ?: return
        val layout = GlyphLayout(font, s)
        val w = layout.width + 48f
        val h = layout.height + 26f
        Widgets.panel(batch, uiPixel, worldWidth / 2f, worldHeight - 56f, w, h)
        font.color = Color.GOLD
        Widgets.text(batch, font, s, worldWidth / 2f, worldHeight - 56f)
        font.color = Color.WHITE
    }

    private data class PauseRows(
        val title: Float,
        val pearls: Float,
        val resume: Float,
        val side: Float,
        val menu: Float,
        val help: Float,
        val buyLabel: Float,
        val buy: Float,
    )

    private fun pauseSpacing(lineHeight: Float): Float = lineHeight + 28f

    private fun pauseRows(tall: Boolean, lineHeight: Float): PauseRows {
        val s = pauseSpacing(lineHeight)
        return if (tall) {
            PauseRows(3.0f * s, 2.0f * s, 1.0f * s, 0.0f * s, -1.0f * s, -2.0f * s, -3.0f * s, -4.0f * s)
        } else {
            PauseRows(2.5f * s, Float.NaN, 1.0f * s, 0.0f * s, -1.0f * s, -2.0f * s, Float.NaN, Float.NaN)
        }
    }

    private fun drawPauseOverlay(glyphLayout: GlyphLayout, lineHeight: Float) {
        val centerX = worldWidth / 2f
        val centerY = worldHeight / 2f
        val tall = worldHeight >= 540f
        val r = pauseRows(tall, lineHeight)
        val s = pauseSpacing(lineHeight)
        val panelH = if (tall) 8.8f * s else 6.1f * s

        Widgets.panel(batch, uiPixel, centerX, centerY, worldWidth * 0.76f, panelH)

        font.color = Color.CYAN
        val pausedStr = Strings.t("paused")
        glyphLayout.setText(font, pausedStr)
        font.draw(batch, glyphLayout, centerX - glyphLayout.width / 2f, centerY + r.title)

        if (tall) {
            font.color = Color.GOLD
            glyphLayout.setText(font, "${Strings.t("pearls")}: ${Profile.pearls()}")
            font.draw(batch, glyphLayout, centerX - glyphLayout.width / 2f, centerY + r.pearls)
        }

        font.color = Color.WHITE
        Widgets.pill(batch, font, uiPixel, centerX, centerY + r.resume, Strings.t("resume"))
        Widgets.pill(batch, font, uiPixel, centerX - 90f, centerY + r.side, Strings.t("restart"))
        Widgets.pill(batch, font, uiPixel, centerX + 90f, centerY + r.side, if (audio.muted) Strings.t("muteOn") else Strings.t("muteOff"))
        Widgets.pill(batch, font, uiPixel, centerX, centerY + r.menu, Strings.t("menu"))

        font.color = Color.CYAN
        val helpStr = "P/Esc ${Strings.t("resume").lowercase()}    R ${Strings.t("restart").lowercase()}    M ${if (audio.muted) Strings.t("muteOn").lowercase() else Strings.t("muteOff").lowercase()}"
        glyphLayout.setText(font, helpStr)
        font.draw(batch, glyphLayout, centerX - glyphLayout.width / 2f, centerY + r.help)
        font.color = Color.WHITE

        if (tall) {
            font.color = Color.CYAN
            glyphLayout.setText(font, Strings.t("quickBuy"))
            font.draw(batch, glyphLayout, centerX - glyphLayout.width / 2f, centerY + r.buyLabel)

            val oxyLabel = shopBuyLabel(Profile.Upgrade.Oxygen)
            val spdLabel = shopBuyLabel(Profile.Upgrade.Speed)
            val oxyAffordable = !Profile.isMaxed(Profile.Upgrade.Oxygen) && Profile.pearls() >= (Profile.upgradeCost(Profile.Upgrade.Oxygen) ?: 0)
            val spdAffordable = !Profile.isMaxed(Profile.Upgrade.Speed) && Profile.pearls() >= (Profile.upgradeCost(Profile.Upgrade.Speed) ?: 0)
            Widgets.pill(batch, font, uiPixel, centerX - 90f, centerY + r.buy, oxyLabel, enabled = oxyAffordable)
            Widgets.pill(batch, font, uiPixel, centerX + 90f, centerY + r.buy, spdLabel, enabled = spdAffordable)
        }
    }

    private fun playerRect() =
        Rectangle(playerX - playerRadius, playerY - playerRadius, playerRadius * 2f, playerRadius * 2f)

    private fun settleRunRecords() {
        if (depth > bestDepth) {
            bestDepth = depth
            prefs.putFloat("bestDepth", bestDepth)
        }
        if (score > bestScore) {
            bestScore = score
            prefs.putInteger("bestScore", bestScore)
        }
    }

    private fun endGame() {
        if (state == GameState.PLAYING) {
            state = GameState.GAME_OVER
            leaderboardMade = Leaderboard.qualifies(score, depth)
            Leaderboard.submit(score, depth)
            Profile.recordDive()
            Profile.noteRun(runPearls)
            val day = Profile.dailyDay()
            if (Profile.claimedDailyDay() != day) {
                Profile.claimDaily(day)
                val bonus = 25
                runPearls += bonus
                Profile.addPearls(bonus)
                Profile.addLifetimePearls(bonus)
                achievementToast = "${Strings.t("dailyBonus")} +$bonus"
                achievementToastTimer = 3f
            }
            audio.playCrash()
            prefs.flush()
        }
    }

    private fun applyUpgrades() {
        maxOxygen = 1f + upgradeOxygenLevel * 0.15f
        playerSpeed = 320f * (1f + upgradeSpeedLevel * 0.08f)
    }

    private fun reset() {
        frameDelta = 0f
        playerX = worldWidth / 2f
        playerY = worldHeight * 0.25f
        depth = 0f
        score = 0
        startBestScore = bestScore
        leaderboardMade = false
        applyUpgrades()
        oxygen = maxOxygen
        elapsed = 0f
        hazardTimer = 1f
        pickupTimer = 2f
        combo = 1
        comboTimer = 0f
        shieldActive = false
        shieldCooldown = 0f
        runPearls = 0
        nextMilestone = 50f
        bossWarning = 0f
        hazards.clear()
        pickups.clear()
        particles.clear()
        stopShake()
        state = GameState.PLAYING
    }

    private enum class GameState { MAIN_MENU, PLAYING, PAUSED, GAME_OVER, PROFILE, LEADERBOARD, SHOP, ACHIEVEMENTS }

private enum class Difficulty(val drain: Float, val baseScroll: Float, val ramp: Float, val spawnMul: Float, val pickupMul: Float) {
    EASY(0.014f, 78f, 0.8f, 1.3f, 1.2f),
    NORMAL(0.02f, 90f, 1f, 1f, 1f),
    HARD(0.028f, 110f, 1.25f, 0.75f, 0.85f)
}
}