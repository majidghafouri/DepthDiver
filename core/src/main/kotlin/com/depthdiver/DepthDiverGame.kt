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
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle
import com.depthdiver.audio.intensityFor
import com.depthdiver.common.Particle
import com.depthdiver.common.Particle.ParticleType
import com.depthdiver.common.Strings
import com.depthdiver.entity.Hazard
import com.depthdiver.entity.Pickup
import kotlin.ranges.ClosedFloatingPointRange
import com.depthdiver.game.BackAction
import com.depthdiver.game.Biome
import com.depthdiver.game.HazardKind
import com.depthdiver.game.WaterColor
import com.depthdiver.game.VORTEX_PULL
import com.depthdiver.game.clampToWorld
import com.depthdiver.game.homingStep
import com.depthdiver.game.vortexPush
import com.depthdiver.simulation.PerformanceMonitor
import com.depthdiver.simulation.FrameTimeOverlay
import com.depthdiver.game.DifficultyCurve
import com.depthdiver.game.FixedStepClock
import com.depthdiver.game.GameAction
import com.depthdiver.game.GameFlow
import com.depthdiver.game.GameState
import com.depthdiver.game.INITIAL_PLAYER_X_METERS
import com.depthdiver.game.INITIAL_PLAYER_Y_METERS
import com.depthdiver.game.MoveDirection
import com.depthdiver.game.PLAYER_RADIUS_METERS
import com.depthdiver.game.ProceduralFairness
import com.depthdiver.game.WORLD_WIDTH_METERS
import com.depthdiver.game.WorldViewSpec
import com.depthdiver.game.backActionFor
import com.depthdiver.run.BonusCategory
import com.depthdiver.run.RunLedger
import com.depthdiver.run.RunSettlement
import com.depthdiver.run.RunTerminalReason
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

internal const val MUSIC_DEPTH_SCALE = 220f

internal const val BOSS_WARNING_STEP = 0.85f

internal const val MAX_CURRENT_PUSH = 14f

internal const val BIOME_BANNER_FADE = 0.6f

internal fun bossCountdownStep(warningRemaining: Float): Int =
    (3 - (warningRemaining / BOSS_WARNING_STEP).toInt()).coerceIn(1, 3)

internal fun safeFrameDelta(delta: Float): Float = when {
    !delta.isFinite() || delta <= 0f -> 0f
    else -> min(delta, MAX_FRAME_DELTA)
}

internal fun inputDirection(
    left: Boolean,
    right: Boolean,
    up: Boolean,
    down: Boolean,
): MoveDirection {
    val x = when {
        left && right -> 0f
        left -> -1f
        right -> 1f
        else -> 0f
    }
    val y = when {
        up && down -> 0f
        up -> 1f
        down -> -1f
        else -> 0f
    }
    return MoveDirection(x, y).normalized()
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
    private val screenCamera = OrthographicCamera()
    private val worldCamera = OrthographicCamera()
    private lateinit var font: BitmapFont
    private lateinit var titleFont: BitmapFont
    private lateinit var playerTex: Texture
    private lateinit var rockTex: Texture
    private lateinit var mineTex: Texture
    private lateinit var jellyfishTex: Texture
    private lateinit var sharkTex: Texture
    private lateinit var eelTex: Texture
    private lateinit var anglerTex: Texture
    private lateinit var vortexTex: Texture
    private lateinit var fishTex: Texture
    private lateinit var pearlTex: Texture
    private lateinit var oxyTex: Texture
    private lateinit var uiPixel: Texture

    private val hazards = mutableListOf<Hazard>()
    private val pickups = mutableListOf<Pickup>()
    private var flow = GameFlow()
    private val state: GameState get() = flow.state
    private val gameplayClock = FixedStepClock()
    private val fairness = ProceduralFairness()
    private val runSettlement = RunSettlement()
    private var activeRun: RunLedger? = null
    private var frameDelta = 0f
    private var hudPauseCx = 0f
    private var hudPauseCy = 0f
    private var hudPauseW = 0f
    private var hudPauseH = 0f

    private val audio = AudioManager()
    private val performanceMonitor = PerformanceMonitor()
    private val frameTimeOverlay = FrameTimeOverlay()

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

    private var playerX = INITIAL_PLAYER_X_METERS
    private var playerY = INITIAL_PLAYER_Y_METERS
    private var depth = -INITIAL_PLAYER_Y_METERS
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
    private var shakeDuration = 0f

    private val particles = mutableListOf<Particle>()

    private var menuTime = 0f
    private var achievementToast: String? = null
    private var achievementToastTimer = 0f
    private var nextMilestone = 50f
    private var bossWarning = 0f
    private var countdownStep = 0
    private var biome: Biome = Biome.SUNLIT_SHALLOWS
    private var biomeToastTimer = 0f
    private var lowOxyTick = 0f

    private var playerSpeed = 16f
    private val playerRadius = PLAYER_RADIUS_METERS
    private var screenWidth = 800f
    private var screenHeight = 600f
    private var worldViewSpec = WorldViewSpec(screenWidth, screenHeight)
    private var worldCameraTarget = worldViewSpec.cameraFor(INITIAL_PLAYER_X_METERS, INITIAL_PLAYER_Y_METERS)
    private var menuFbo: FrameBuffer? = null

    override fun create() {
        batch = SpriteBatch()
        resize(Gdx.graphics.width, Gdx.graphics.height)
        val generator = FreeTypeFontGenerator(Gdx.files.internal("fonts/OpenSans-Regular.ttf"))
        val parameter = FreeTypeFontGenerator.FreeTypeFontParameter().apply {
            size = (screenHeight / 30f).toInt().coerceIn(16, 48)
            color = Color.WHITE
            borderWidth = 1f
            borderColor = Color.BLACK
            borderStraight = true
        }
        font = generator.generateFont(parameter)
        titleFont = generator.generateFont(
            FreeTypeFontGenerator.FreeTypeFontParameter().apply {
                size = (screenHeight / 9f).toInt().coerceIn(36, 84)
                color = Color(0.35f, 0.85f, 1f, 1f)
                borderWidth = 2f
                borderColor = Color(0.02f, 0.2f, 0.4f, 1f)
                borderStraight = true
                shadowOffsetY = 4
                shadowColor = Color(0f, 0f, 0f, 0.6f)
            }
        )
        generator.dispose()
        refreshBests()
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

        val anglerPix = Pixmap(48, 40, Pixmap.Format.RGBA8888)
        anglerPix.setColor(0.18f, 0.16f, 0.28f, 1f)
        anglerPix.fillCircle(20, 20, 15)
        anglerPix.fillTriangle(6, 20, 20, 30, 20, 10)
        anglerPix.setColor(0.32f, 0.28f, 0.45f, 1f)
        anglerPix.fillCircle(20, 20, 9)
        anglerPix.setColor(0.95f, 0.85f, 0.35f, 1f)
        anglerPix.fillCircle(41, 32, 4)
        anglerPix.setColor(0.75f, 0.95f, 1f, 1f)
        anglerPix.fillCircle(41, 32, 2)
        anglerPix.setColor(0.9f, 0.9f, 0.95f, 1f)
        anglerPix.fillTriangle(18, 26, 26, 18, 26, 30)
        anglerPix.fillTriangle(18, 14, 26, 8, 26, 20)
        anglerPix.setColor(1f, 0.95f, 0.5f, 1f)
        anglerPix.fillCircle(25, 20, 2)
        anglerTex = Texture(anglerPix)
        anglerPix.dispose()

        val vortexPix = Pixmap(64, 64, Pixmap.Format.RGBA8888)
        for (i in 0 until 3) {
            val radius = 30 - i * 9
            vortexPix.setColor(0.45f, 0.8f, 0.95f, 0.55f - i * 0.15f)
            for (step in 0 until 40) {
                val angle = step / 40f * MathUtils.PI2
                val x = 32f + kotlin.math.cos(angle) * radius
                val y = 32f + kotlin.math.sin(angle) * radius
                vortexPix.fillCircle(x.toInt(), y.toInt(), 2)
            }
        }
        vortexPix.setColor(0.75f, 0.95f, 1f, 0.8f)
        vortexPix.fillCircle(32, 32, 4)
        vortexTex = Texture(vortexPix)
        vortexPix.dispose()

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

        resetWorld()
        recoverStartupRuns()
    }

    override fun resize(width: Int, height: Int) {
        screenWidth = width.toFloat().coerceAtLeast(1f)
        screenHeight = height.toFloat().coerceAtLeast(1f)
        screenCamera.setToOrtho(false, screenWidth, screenHeight)
        screenCamera.update()
        worldViewSpec = WorldViewSpec(screenWidth, screenHeight)
        worldCamera.setToOrtho(false, worldViewSpec.viewWidthMeters, worldViewSpec.viewHeightMeters)
        updateWorldCamera()
    }

    override fun render() {
        frameDelta = gameplayClock.frameDeltaSeconds(Gdx.graphics.deltaTime)
        performanceMonitor.startFrame()
        menuTime += frameDelta
        if (achievementToastTimer > 0f) {
            achievementToastTimer -= frameDelta
            if (achievementToastTimer <= 0f) achievementToast = null
        }
        if (biomeToastTimer > 0f) biomeToastTimer -= frameDelta
        if (bossWarning > 0f) {
            bossWarning -= frameDelta
            updateBossCountdown()
        }
        handleInput()
        if (state == GameState.PLAYING) {
            gameplayClock.advance(frameDelta) { step ->
                if (state == GameState.PLAYING) fixedUpdate(step)
            }
        }
        if (state != GameState.PLAYING) gameplayClock.reset()
        updateAudioMix(frameDelta)
        draw()
        performanceMonitor.endFrame()
    }

    private fun updateBossCountdown() {
        val step = bossCountdownStep(bossWarning)
        if (step == countdownStep) return
        countdownStep = step
        audio.playCountdown(step)
    }

    private fun updateAudioMix(dt: Float) {
        if (dt <= 0f) return
        if (state == GameState.PLAYING) {
            val oxygenRatio = if (maxOxygen > 0f) oxygen / maxOxygen else 1f
            audio.updateMusic(intensityFor(depth, MUSIC_DEPTH_SCALE, oxygenRatio, bossWarning > 0f), dt)
        } else {
            audio.stopMusic()
        }
    }

    override fun pause() {
        pauseGame()
    }

    override fun resume() {
        frameDelta = 0f
        gameplayClock.reset()
    }

    fun handleSystemBack(): Boolean = when (backActionFor(state)) {
        BackAction.EXIT -> false
        BackAction.PAUSE -> {
            pauseGame()
            true
        }
        BackAction.RESUME -> {
            resumeGame()
            true
        }
        BackAction.MAIN_MENU -> {
            goToMenu()
            true
        }
    }

    private fun pauseGame() {
        if (state != GameState.PLAYING) return
        try {
            checkpointActiveRun()
        } catch (_: Exception) {
            return
        }
        if (!dispatch(GameAction.Pause)) return
        gameplayClock.reset()
        frameDelta = 0f
        stopShake()
    }

    private fun resumeGame() {
        if (state != GameState.PAUSED) return
        if (!dispatch(GameAction.Resume)) return
        gameplayClock.reset()
        frameDelta = 0f
    }

    private fun stopShake() {
        shakeTimer = 0f
        shakeIntensity = 0f
        shakeDuration = 0f
    }

    private var shakeDirection: Float = 0f

    private fun triggerShake(duration: Float, intensity: Float, direction: Float = -1f) {
        shakeDuration = duration.coerceAtLeast(0f)
        shakeTimer = shakeDuration
        shakeIntensity = intensity.coerceAtLeast(0f)
        shakeDirection = direction.coerceIn(-MathUtils.PI, MathUtils.PI)
    }

    private fun spawnParticles(
        x: Float, y: Float, color: Color, count: Int,
        type: Particle.ParticleType = Particle.ParticleType.NORMAL,
        speedMin: Float = 3f, speedMax: Float = 9f,
        lifeMin: Float = 0.3f, lifeMax: Float = 0.8f,
        sizeMin: Float = 0.15f, sizeMax: Float = 0.35f,
        fadeRate: Float = 1f,
        gravity: Float = 10f
    ) {
        repeat(count) {
            val angle = MathUtils.random(MathUtils.PI2)
            val speed = MathUtils.random(speedMin, speedMax)
            val life = MathUtils.random(lifeMin, lifeMax)
            particles.add(Particle(
                x = x,
                y = y,
                vx = MathUtils.cos(angle) * speed,
                vy = MathUtils.sin(angle) * speed,
                life = life,
                maxLife = life,
                color = Color(color),
                size = MathUtils.random(sizeMin, sizeMax),
                trailLength = if (type == Particle.ParticleType.TRAIL) MathUtils.random(0.5f, 1.5f) else 0f,
                fadeRate = fadeRate,
                particleType = type,
                gravity = gravity
            ))
        }
    }

    private fun spawnTrailParticles() {
        if (state == GameState.PLAYING && MathUtils.randomBoolean(0.3f)) {
            val trailColor = Color(0.3f, 0.7f, 1f, 0.6f)
            spawnParticles(
                x = playerX,
                y = playerY - playerRadius,
                color = trailColor,
                count = 2,
                type = Particle.ParticleType.TRAIL,
                speedMin = 0.5f, speedMax = 2f,
                lifeMin = 0.1f, lifeMax = 0.3f,
                sizeMin = 0.08f, sizeMax = 0.15f,
                fadeRate = 2f,
                gravity = 0f
            )
        }
    }

    private fun spawnBubbleParticles(x: Float, y: Float) {
        if (MathUtils.randomBoolean(0.15f)) {
            val bubbleColor = Color(0.4f, 0.8f, 1f, 0.4f)
            spawnParticles(
                x = x + MathUtils.random(-0.5f, 0.5f),
                y = y + MathUtils.random(-0.5f, 0.5f),
                color = bubbleColor,
                count = 1,
                type = Particle.ParticleType.BUBBLE,
                speedMin = 0.2f, speedMax = 0.8f,
                lifeMin = 1f, lifeMax = 3f,
                sizeMin = 0.1f, sizeMax = 0.25f,
                fadeRate = 0.5f,
                gravity = -2f
            )
        }
    }

    private fun spawnExplosionParticles(x: Float, y: Float, color: Color) {
        spawnParticles(
            x = x, y = y, color = color, count = 20,
            type = Particle.ParticleType.EXPLOSION,
            speedMin = 5f, speedMax = 20f,
            lifeMin = 0.4f, lifeMax = 1f,
            sizeMin = 0.2f, sizeMax = 0.5f,
            fadeRate = 1.5f,
            gravity = 15f
        )
    }

    private fun spawnSplashParticles(x: Float, y: Float) {
        spawnParticles(
            x = x, y = y, color = Color(0.4f, 0.8f, 1f, 0.8f), count = 12,
            type = Particle.ParticleType.SPLASH,
            speedMin = 3f, speedMax = 12f,
            lifeMin = 0.2f, lifeMax = 0.6f,
            sizeMin = 0.1f, sizeMax = 0.3f,
            fadeRate = 2f,
            gravity = 8f
        )
    }

    private fun spawnSparkParticles(x: Float, y: Float, color: Color, count: Int) {
        spawnParticles(
            x = x, y = y, color = color, count = count,
            type = Particle.ParticleType.SPARK,
            speedMin = 8f, speedMax = 25f,
            lifeMin = 0.1f, lifeMax = 0.4f,
            sizeMin = 0.05f, sizeMax = 0.15f,
            fadeRate = 3f,
            gravity = 0f
        )
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
        anglerTex.dispose()
        vortexTex.dispose()
        fishTex.dispose()
        pearlTex.dispose()
        oxyTex.dispose()
        menuFbo?.dispose()
        menuFbo = null
        audio.dispose()
    }

    /** One low-res render target used to fake a soft blur behind menu content. */
    private fun ensureMenuFbo() {
        val wantW = (screenWidth / 4).toInt().coerceAtLeast(1)
        val wantH = (screenHeight / 4).toInt().coerceAtLeast(1)
        val cur = menuFbo
        if (cur != null && cur.width == wantW && cur.height == wantH) return
        cur?.dispose()
        menuFbo = FrameBuffer(Pixmap.Format.RGBA8888, wantW, wantH, false).apply {
            colorBufferTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        }
    }

    private fun fixedUpdate(delta: Float) {
        if (state != GameState.PLAYING) return
        val ledger = activeRun ?: return
        val keyboardDirection = inputDirection(
            left = Gdx.input.isKeyPressed(Input.Keys.LEFT) || Gdx.input.isKeyPressed(Input.Keys.A),
            right = Gdx.input.isKeyPressed(Input.Keys.RIGHT) || Gdx.input.isKeyPressed(Input.Keys.D),
            up = Gdx.input.isKeyPressed(Input.Keys.UP) || Gdx.input.isKeyPressed(Input.Keys.W),
            down = Gdx.input.isKeyPressed(Input.Keys.DOWN) || Gdx.input.isKeyPressed(Input.Keys.S),
        )
        val direction = if (!keyboardDirection.isZero) {
            keyboardDirection
        } else if (Gdx.input.isTouched()) {
            worldViewSpec.touchDirection(
                playerXMeters = playerX,
                playerYMeters = playerY,
                screenX = Gdx.input.x.toFloat(),
                screenYFromTop = Gdx.input.y.toFloat(),
                camera = worldCameraTarget,
            )
        } else {
            MoveDirection.ZERO
        }
        playerX = (playerX + direction.x * playerSpeed * delta).coerceIn(playerRadius, WORLD_WIDTH_METERS - playerRadius)
        playerX = (playerX + currentPush() * delta).coerceIn(playerRadius, WORLD_WIDTH_METERS - playerRadius)
        playerY = (playerY + direction.y * playerSpeed * delta).coerceAtMost(-playerRadius)
        depth = max(0f, -playerY)
        updateWorldCamera()

        spawnTrailParticles()
        spawnBubbleParticles(playerX, playerY - playerRadius * 2)

        elapsed += delta
        val diff = currentDifficulty()
        oxygen -= delta * DifficultyCurve.oxygenDrain(diff.drain, depth)
        if (oxygen <= 0f) {
            oxygen = 0f
            endGame(RunTerminalReason.OXYGEN)
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
                audio.playShieldBreak()
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
                p.vy -= 10f * delta
            }
        }

        val scrollSpeed = DifficultyCurve.scrollSpeed(
            baseMetersPerSecond = diff.baseScrollMeters,
            rampMetersPerSecond = diff.rampMetersPerSecond,
            depthMeters = depth,
            playerSpeedMetersPerSecond = playerSpeed,
        )

        hazardTimer -= delta
        if (hazardTimer <= 0) {
            spawnHazard()
            val interval = DifficultyCurve.hazardIntervalRange(1.4f, 2.6f, depth, diff.spawnMul)
            hazardTimer = fairness.range(interval.minimum, interval.maximum)
        }
        pickupTimer -= delta
        if (pickupTimer <= 0) {
            spawnPickup()
            pickupTimer = fairness.pickupInterval(3f, 5.5f, diff.pickupMul)
        }

        updateEntities(delta, scrollSpeed)

        val hazardIterator = hazards.iterator()
        while (hazardIterator.hasNext()) {
            val hazard = hazardIterator.next()
            if (hazard is Hazard.Vortex) continue
            if (!playerRect().overlaps(hazard.rect)) continue
            when (resolveShieldCollision(upgradeShieldLevel, shieldActive, shieldCooldown)) {
                ShieldCollisionResult.ACTIVATED -> {
                    shieldActive = true
                    shieldCooldown = shieldDuration(upgradeShieldLevel)
                    audio.playShield()
                    triggerShake(0.15f, 0.4f, MathUtils.PI) // Shield activation shakes backward
                    Gdx.input.vibrate(60)
                    spawnExplosionParticles(playerX, playerY, Color.MAGENTA) // Shield activation explosion
                    spawnSparkParticles(playerX, playerY, Color.WHITE, 12) // Shield sparks
                    hazardIterator.remove()
                }
                ShieldCollisionResult.BLOCKED -> {
                    if (hazard is Hazard.Shark && hazard.isBoss) {
                        audio.playBossHit()
                        triggerShake(0.2f, 0.5f, MathUtils.PI)
                    } else {
                        audio.playShieldBreak()
                    }
                    spawnSparkParticles(playerX, playerY, Color.MAGENTA, 8) // Block sparks
                    hazardIterator.remove()
                }
                ShieldCollisionResult.FATAL -> {
                    audio.playCrash()
                    triggerShake(0.3f, 0.6f, MathUtils.PI) // Fatal collision shakes backward
                    Gdx.input.vibrate(100)
                    spawnExplosionParticles(playerX, playerY, Color.RED) // Death explosion
                    endGame(RunTerminalReason.HAZARD)
                    return
                }
            }
        }
        for (pickup in pickups) {
            if (!pickup.collected && playerRect().overlaps(pickup.rect)) {
                pickup.collected = true
                when (pickup) {
                    is Pickup.OxygenTank -> {
                        fairness.noteOxygenTank(depth)
                        oxygen = (oxygen + 0.4f).coerceAtMost(maxOxygen)
                        audio.playOxygen()
                        triggerShake(0.15f, 0.3f, MathUtils.PI / 2) // Upward shake for oxygen
                        Gdx.input.vibrate(40)
                        spawnSplashParticles(pickup.rect.x + pickup.rect.width / 2f, pickup.rect.y + pickup.rect.height / 2f) // Water splash
                        spawnParticles(pickup.rect.x + pickup.rect.width / 2f, pickup.rect.y + pickup.rect.height / 2f, Color.CYAN, 12, type = Particle.ParticleType.BUBBLE) // Rising bubbles
                    }
                    is Pickup.Pearl -> {
                        combo += 1
                        val window = 5f + upgradeComboLevel * 2f
                        comboTimer = window
                        maxComboWindow = window
                        val pearlValue = (5 * (1 + upgradePearlValueLevel * 0.5)).toInt()
                        ledger.collectPearl(pearlValue, pearlValue * combo)
                        Profile.grantPearls(pearlValue)
                        checkpointActiveRun()
                        audio.playPickup()
                        if (combo > 2 && combo % 3 == 0) audio.playCombo(combo)
                        triggerShake(0.1f, 0.2f, -MathUtils.PI / 2) // Forward shake for pearl
                        Gdx.input.vibrate(30)
                        spawnParticles(pickup.rect.x + pickup.rect.width / 2f, pickup.rect.y + pickup.rect.height / 2f, Color.GOLD, 15, type = Particle.ParticleType.SPARK) // Gold sparks
                        spawnParticles(pickup.rect.x + pickup.rect.width / 2f, pickup.rect.y + pickup.rect.height / 2f, Color(1f, 1f, 0.8f, 1f), 8, type = Particle.ParticleType.BUBBLE) // Golden bubbles
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

        val earned = Achievements.checkAndEarn()
        if (earned != null) {
            achievementToast = earned
            achievementToastTimer = 3f
            audio.playAchieve()
        }

        while (depth >= nextMilestone) {
            val m = nextMilestone
            nextMilestone += 50f
            if (awardRunBonus("milestone:$m", 10, BonusCategory.MILESTONE)) {
                achievementToast = "${Strings.t("milestone")} ${m.toInt()} M +10"
                achievementToastTimer = 3f
                audio.playAchieve()
            }
        }

        updateBiome()

        val activeCh = Challenge.activeFor(Profile.dailyDay())
        if (!Challenge.claimedFor(activeCh) && activeCh.met(depth, runPearls, score)) {
            Challenge.claim(activeCh)
            if (awardRunBonus("challenge:${activeCh.day}", Challenge.REWARD, BonusCategory.CHALLENGE)) {
                achievementToast = "${Strings.t("challengeDone")} +${Challenge.REWARD}"
                achievementToastTimer = 3f
                audio.playAchieve()
            }
        }
    }

    private fun updateBiome() {
        val current = Biome.forDepth(depth)
        if (current == biome) return
        biome = current
        biomeToastTimer = 3f
        audio.playAlert()
    }

    private fun currentPush(): Float {
        var push = 0f
        for (hazard in hazards) {
            if (hazard !is Hazard.Vortex) continue
            val centerX = hazard.rect.x + hazard.rect.width / 2f
            val centerY = hazard.rect.y + hazard.rect.height / 2f
            push += vortexPush(playerX - centerX, playerY - centerY, hazard.radius, hazard.strength)
        }
        return push.coerceIn(-MAX_CURRENT_PUSH, MAX_CURRENT_PUSH)
    }

    private fun updateEntities(delta: Float, scrollSpeed: Float) {
        val visibleBottom = worldViewSpec.worldBottom(worldCameraTarget)
        val itr = hazards.iterator()
        while (itr.hasNext()) {
            val hazard = itr.next()
            when (hazard) {
                is Hazard.Rock -> {
                    hazard.rect.y -= scrollSpeed * delta
                    hazard.rect.x += MathUtils.sin(elapsed * 2f + hazard.phase) * 0.5f * delta
                }
                is Hazard.Mine -> {
                    hazard.rect.y -= scrollSpeed * 0.6f * delta
                    hazard.rect.x += MathUtils.sin(elapsed * 1.2f + hazard.phase) * 1.2f * delta
                }
                is Hazard.Jellyfish -> {
                    hazard.rect.y -= scrollSpeed * 0.45f * delta
                    hazard.rect.x = hazard.baseX + MathUtils.sin(elapsed * 1.5f + hazard.phase) * hazard.sway
                }
                is Hazard.Shark -> {
                    hazard.rect.y -= scrollSpeed * (if (hazard.isBoss) 0.15f else 0.5f) * delta
                    hazard.rect.x += MathUtils.sin(elapsed * 0.8f + hazard.phase) * 0.7f * delta
                }
                is Hazard.Eel -> {
                    hazard.rect.x += hazard.speed * hazard.dir * delta
                    hazard.rect.y = hazard.baseY - scrollSpeed * (elapsed - hazard.spawn) * 0.35f + MathUtils.sin(elapsed * 2f + hazard.phase) * 0.4f
                }
                is Hazard.Angler -> {
                    hazard.rect.y -= scrollSpeed * 0.4f * delta
                    val dx = playerX - (hazard.rect.x + hazard.rect.width / 2f)
                    val drift = MathUtils.sin(elapsed * 0.9f + hazard.phase) * 0.4f * delta
                    hazard.rect.x = clampToWorld(
                        hazard.rect.x + homingStep(dx, hazard.homingSpeed, delta) + drift,
                        hazard.rect.width / 2f,
                        WORLD_WIDTH_METERS,
                    )
                }
                is Hazard.Vortex -> {
                    hazard.rect.y -= scrollSpeed * 0.3f * delta
                    hazard.rect.x += MathUtils.sin(elapsed * 0.7f + hazard.phase) * 1.6f * delta
                }
            }
            if (hazard.rect.y + hazard.rect.height < visibleBottom ||
                hazard.rect.x + hazard.rect.width < 0f ||
                hazard.rect.x > WORLD_WIDTH_METERS
            ) {
                if (hazard is Hazard.Shark && hazard.isBoss && hazard.rect.y + hazard.rect.height < visibleBottom) {
                    onBossEscaped()
                }
                itr.remove()
            }
        }
        val itrP = pickups.iterator()
        while (itrP.hasNext()) {
            val pickup = itrP.next()
            pickup.rect.y -= scrollSpeed * 0.55f * delta
            pickup.rect.x += MathUtils.sin(elapsed * 1.1f + pickup.phase) * 0.4f * delta
            if (pickup.rect.y + pickup.rect.height < visibleBottom ||
                pickup.rect.x + pickup.rect.width < 0f ||
                pickup.rect.x > WORLD_WIDTH_METERS ||
                pickup.collected
            ) {
                itrP.remove()
            }
        }
    }

    private fun spawnHazard() {
        val top = worldViewSpec.worldTop(worldCameraTarget)
        if (spawnRockGates()) return
        if (spawnEel(top)) return
        if (spawnBossShark(top)) return

        when (Biome.roll(biome.hazardMix, fairness.unit())) {
            HazardKind.JELLYFISH -> spawnJellyfish(top)
            HazardKind.MINE -> spawnMine(top)
            HazardKind.ANGLER -> spawnAngler(top)
            HazardKind.VORTEX -> spawnVortex(top)
            else -> spawnRock(top)
        }
    }

    private fun spawnRockGates(): Boolean {
        if (fairness.unit() >= 0.3f) return false
        hazards.add(Hazard.Rock(Rectangle(-1.2f, worldViewSpec.worldTop(worldCameraTarget) + 2f, 4.8f, 6f), 0f))
        hazards.add(Hazard.Rock(Rectangle(WORLD_WIDTH_METERS - 3.6f, worldViewSpec.worldTop(worldCameraTarget) + 2f, 4.8f, 6f), 0f))
        fairness.recordHazard(1.2f, 4.8f)
        fairness.recordHazard(WORLD_WIDTH_METERS - 1.2f, 4.8f)
        return true
    }

    private fun spawnEel(top: Float): Boolean {
        if (depth <= 45f || fairness.unit() >= 0.45f) return false
        val width = 7.5f
        val eel = fairness.eelSpawn(
            playerXMeters = playerX,
            playerYMeters = playerY,
            widthMeters = width,
            topYMeters = top,
            bandOffsetMinimum = 0.5f,
            bandOffsetMaximum = 6.5f,
            minBandGapMeters = 3f,
            minPlayerGapMeters = 2.5f,
            minReactionSeconds = 1.6f,
            baseSpeedMetersPerSecond = 7.5f,
            depthMeters = depth,
        )
        hazards.add(
            Hazard.Eel(
                Rectangle(eel.startXMeters, eel.baseYMeters, width, 1.7f),
                eel.dir,
                fairness.range(0f, MathUtils.PI2),
                eel.baseYMeters,
                elapsed,
                eel.speedMetersPerSecond
            )
        )
        return true
    }

    private fun spawnBossShark(top: Float): Boolean {
        if (depth <= 80f || fairness.unit() >= 0.97f) return false
        val boss = depth > 120f && fairness.unit() < 0.06f
        if (boss) {
            bossWarning = 2.5f
            countdownStep = 0
            audio.playBossRoar()
            audio.playAlarm()
        }
        val w = if (boss) 6.5f else 4.2f
        val h = if (boss) 2.3f else 1.5f
        val center = fairness.hazardCenter(playerX, w, depth).centerXMeters
        hazards.add(Hazard.Shark(Rectangle(center - w / 2f, top + 3f, w, h), 0f, boss))
        return true
    }

    private fun spawnJellyfish(top: Float) {
        if (depth < 35f) {
            spawnRock(top)
            return
        }
        val width = 3f
        val center = fairness.hazardCenter(playerX, width, depth).centerXMeters
        hazards.add(
            Hazard.Jellyfish(
                Rectangle(center - width / 2f, top + 3f, width, width),
                fairness.range(0f, MathUtils.PI2),
                fairness.range(1.25f, 2.25f),
                center
            )
        )
    }

    private fun spawnMine(top: Float) {
        if (depth < 18f) {
            spawnRock(top)
            return
        }
        val width = 2.4f
        val center = fairness.hazardCenter(playerX, width, depth).centerXMeters
        hazards.add(Hazard.Mine(Rectangle(center - width / 2f, top + 2.4f, width, width), fairness.range(0f, MathUtils.PI2)))
    }

    private fun spawnAngler(top: Float) {
        if (depth < 55f) {
            spawnJellyfish(top)
            return
        }
        val width = 3.4f
        val center = fairness.hazardCenter(playerX, width, depth).centerXMeters
        hazards.add(
            Hazard.Angler(
                Rectangle(center - width / 2f, top + 3f, width, width * 0.75f),
                fairness.range(0f, MathUtils.PI2),
                homingSpeed = fairness.range(1.1f, 2.2f)
            )
        )
    }

    private fun spawnVortex(top: Float) {
        if (depth < 120f) {
            spawnAngler(top)
            return
        }
        val radius = fairness.range(4.5f, 7f)
        val center = clampToWorld(fairness.range(2f, WORLD_WIDTH_METERS - 2f), radius, WORLD_WIDTH_METERS)
        hazards.add(
            Hazard.Vortex(
                Rectangle(center - 1.5f, top + 4f, 3f, 3f),
                fairness.range(0f, MathUtils.PI2),
                radius,
                strength = VORTEX_PULL * fairness.range(0.7f, 1.1f)
            )
        )
    }

    private fun spawnRock(top: Float) {
        val size = fairness.range(2.25f, 4.25f)
        val center = fairness.hazardCenter(playerX, size, depth).centerXMeters
        hazards.add(Hazard.Rock(Rectangle(center - size / 2f, top + 4f, size, size), fairness.range(0f, MathUtils.PI2)))
    }

    private fun onBossEscaped() {
        val ledger = activeRun ?: return
        val bonus = 50
        if (awardRunBonus("boss:${ledger.runId}", bonus, BonusCategory.BOSS)) {
            achievementToast = "${Strings.t("bossCleared")} +$bonus"
            achievementToastTimer = 3f
            audio.playLevelUp()
            audio.playAchieve()
        }
    }

    private fun spawnPickup() {
        val top = worldViewSpec.worldTop(worldCameraTarget)
        val oxygenFraction = if (maxOxygen > 0f) oxygen / maxOxygen else 0f
        val forceTank = fairness.shouldForceOxygenTank(oxygenFraction, depth)
        val tank = forceTank || fairness.unit() >= 0.65f
        val width = if (tank) 1.6f else 1.2f
        val center = fairness.pickupCenter(playerX, width, depth)
        val phase = fairness.range(0f, MathUtils.PI2)
        if (tank) {
            pickups.add(Pickup.OxygenTank(Rectangle(center - width / 2f, top + 2.4f, width, width), phase))
        } else {
            pickups.add(Pickup.Pearl(Rectangle(center - width / 2f, top + 2f, width, width), phase))
        }
    }

    private fun handleInput() {
        val escJust = Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)
        val pJust = Gdx.input.isKeyJustPressed(Input.Keys.P)
        val mJust = Gdx.input.isKeyJustPressed(Input.Keys.M)

        if (escJust || pJust) {
            if (!handleSystemBack()) Gdx.app.exit()
            return
        }

        when (state) {
            GameState.MAIN_MENU -> {
                if (mJust) {
                    audio.toggleMute()
                    audio.playClick()
                    return
                }
                if (Gdx.input.justTouched()) {
                    handleMenuTouch(touchX(), touchY())
                }
            }

            GameState.PROFILE, GameState.LEADERBOARD, GameState.SHOP, GameState.ACHIEVEMENTS, GameState.SETTINGS -> {
                if (Gdx.input.justTouched()) {
                    when (state) {
                        GameState.SHOP -> handleShopTouch(touchX(), touchY())
                        GameState.PROFILE -> handleProfileTouch(touchX(), touchY())
                        else -> handleSubScreenTouch(touchX(), touchY())
                    }
                }
            }

            GameState.PLAYING -> {
                if (mJust) {
                    audio.toggleMute()
                    return
                }
                if (Gdx.input.isKeyJustPressed(Input.Keys.F)) {
                    frameTimeOverlay.toggle()
                    return
                }
                if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
                    restartRun()
                    return
                }
                if (Gdx.input.justTouched() && Widgets.contains(touchX(), touchY(), hudPauseCx, hudPauseCy, hudPauseW, hudPauseH)) {
                    pauseGame()
                    return
                }
            }

            GameState.PAUSED -> {
                if (mJust) {
                    audio.toggleMute()
                    return
                }
                if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
                    restartRun()
                    return
                }
                if (Gdx.input.justTouched()) {
                    handlePauseTouch(touchX(), touchY())
                }
                return
            }

            GameState.GAME_OVER -> {
                if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
                    restartRun()
                    return
                }
                if (Gdx.input.justTouched()) {
                    val targets = gameOverTargets()
                    when (gameOverActionAt(touchX(), touchY(), targets.restart, targets.menu)) {
                        GameOverAction.RESTART -> restartRun()
                        GameOverAction.MENU -> goToMenu()
                        GameOverAction.NONE -> Unit
                    }
                    return
                }
            }
        }
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
        val gap = min(72f, screenHeight * 0.115f)
        val startY = screenHeight * 0.64f
        val row = i / 2
        val col = i % 2
        val cx = screenWidth * (if (col == 0) 0.335f else 0.665f)
        return cx to (startY - row * gap)
    }

    /** EASY / NORMAL / HARD segmented controls, well clear of gesture bars. */
    private fun difficultySegs(): Array<FloatArray> {
        val w = max(196f, Widgets.pillW(font, Strings.t("normal")) + 10f)
        val h = Widgets.pillH(font, Strings.t("normal"))
        val cy = screenHeight * 0.10f
        val gap = w + 18f
        return arrayOf(
            floatArrayOf(screenWidth / 2f - gap, cy, w, h, 0f),
            floatArrayOf(screenWidth / 2f, cy, w, h, 1f),
            floatArrayOf(screenWidth / 2f + gap, cy, w, h, 2f)
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
            0 -> startRun()
            1 -> dispatch(GameAction.OpenProfile)
            2 -> dispatch(GameAction.OpenLeaderboard)
            3 -> dispatch(GameAction.OpenShop)
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
        val backPill = arrayOf(screenWidth * 0.2f, screenHeight * 0.08f)
        if (Widgets.contains(tx, ty, backPill[0], backPill[1], Widgets.pillW(font, Strings.t("back")), Widgets.pillH(font, Strings.t("back")))) {
            goToMenu()
            return
        }
        val achLabel = "${Strings.t("achievements")} ${Achievements.count()}/${Achievements.ALL.size}"
        if (Widgets.contains(tx, ty, screenWidth * 0.8f, screenHeight * 0.08f, Widgets.pillW(font, achLabel), Widgets.pillH(font, achLabel))) {
            dispatch(GameAction.OpenAchievements)
            audio.playClick()
            return
        }
        val day = Profile.dailyDay()
        val active = Challenge.activeFor(day)
        if (!Challenge.claimedFor(active) && active.met(bestDepth, Profile.bestRunPearls(), bestScore)) {
            val label = "${Strings.t("claim")} +${Challenge.REWARD}"
            if (Widgets.contains(tx, ty, screenWidth / 2f, profileClaimCy(), Widgets.pillW(font, label), Widgets.pillH(font, label))) {
                Challenge.claim(active)
                Profile.grantPearls(Challenge.REWARD)
                achievementToast = "${Strings.t("claim")} +${Challenge.REWARD}"
                achievementToastTimer = 2.5f
                audio.playClick()
            }
        }
    }

    private fun backPill(): ShopRect =
        ShopRect(screenWidth / 2f, screenHeight * 0.08f, Widgets.pillW(font, Strings.t("back")), Widgets.pillH(font, Strings.t("back")))

    private fun shopBuyLabel(u: Profile.Upgrade): String {
        val cost = Profile.upgradeCost(u)
        return if (cost == null) Strings.t("max") else "${Strings.t("buy")} $cost"
    }

    private class ShopRect(val cx: Float, val cy: Float, val w: Float, val h: Float)

    /** Shared panel width for the sub-screens (profile/achievements/leaderboard/shop):
     *  wide enough that long stat/achievement names never collide with their values. */
    private fun subPanelW(): Float = min(screenWidth * 0.86f, screenHeight * 1.6f).coerceAtMost(700f)

    private fun shopPanel(): ShopRect {
        val panelW = subPanelW()
        val panelH = screenHeight * 0.6f
        return ShopRect(screenWidth / 2f, screenHeight / 2f, panelW, panelH)
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
        if (state == GameState.PLAYING || state == GameState.PAUSED) {
            try {
                abandonActiveRun()
            } catch (_: Exception) {
                return
            }
        }
        dispatch(GameAction.MainMenu)
        gameplayClock.reset()
        frameDelta = 0f
        menuTime = 0f
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
        val centerX = screenWidth / 2f
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
        val centerY = screenHeight / 2f
        val tall = screenHeight >= 560f
        if (!tall) {
            return GameOverBox(centerY + 96f, Float.NaN, Float.NaN, 0f, 0f, 0f, centerY - 118f)
        }
        val titleY = centerY + min(290f, screenHeight * 0.26f)
        val showD = score > startBestScore
        val showT = leaderboardMade
        val recordY = if (showD) titleY - 50f else Float.NaN
        val top5Y = if (showT) titleY - (if (showD) 96f else 50f) else Float.NaN
        val rowGap = min(44f, screenHeight / 22f)
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
        val centerX = screenWidth / 2f
        val centerY = screenHeight / 2f
        val tall = screenHeight >= 540f
        val lineHeight = GlyphLayout(font, "Hg").height
        val r = pauseRows(tall, lineHeight)
        if (Widgets.contains(tx, ty, centerX, centerY + r.resume, Widgets.pillW(font, labels[0]), Widgets.pillH(font, labels[0]))) {
            resumeGame()
            audio.playClick()
            return
        }
        if (Widgets.contains(tx, ty, centerX - 90f, centerY + r.side, Widgets.pillW(font, Strings.t("restart")), Widgets.pillH(font, Strings.t("restart")))) {
            restartRun()
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
        if (inGame) {
            updateWorldCamera()
            batch.projectionMatrix = worldCamera.combined
            batch.begin()
            if (state == GameState.PAUSED) drawWorldBlurred() else drawWorld()
            screenCamera.update()
            batch.projectionMatrix = screenCamera.combined
            drawHud()
            if (frameTimeOverlay.isVisible()) {
                frameTimeOverlay.render(batch, font, uiPixel, performanceMonitor, state)
            }
        } else {
            screenCamera.update()
            batch.projectionMatrix = screenCamera.combined
            batch.begin()
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
                GameState.SETTINGS -> {
                    drawMenuBackgroundBlur()
                    drawSettingsScreen()
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
        batch.projectionMatrix = worldCamera.combined
        batch.begin()
        drawWorld()
        batch.end()
        fbo.end()
        screenCamera.update()
        batch.projectionMatrix = screenCamera.combined
        batch.begin()
        batch.setColor(1f, 1f, 1f, 0.9f)
        batch.draw(fbo.colorBufferTexture, 0f, 0f, screenWidth, screenHeight)
        batch.setColor(0f, 0.03f, 0.10f, 0.28f)
        batch.draw(uiPixel, 0f, 0f, screenWidth, screenHeight)
        batch.setColor(Color.WHITE)
    }

    private fun drawWorld() {
        drawWorldBackground()
        if (state == GameState.PLAYING && shakeTimer > 0f) {
            val duration = shakeDuration.coerceAtLeast(0.0001f)
            val progress = (1f - shakeTimer / duration).coerceIn(0f, 1f)
            val currentIntensity = shakeIntensity * (1f - progress * 0.7f)
            worldCamera.position.x += MathUtils.random(-currentIntensity, currentIntensity)
            worldCamera.position.y += MathUtils.random(-currentIntensity, currentIntensity)
            worldCamera.update()
            batch.projectionMatrix = worldCamera.combined
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
                is Hazard.Angler -> {
                    batch.draw(anglerTex, hazard.rect.x, hazard.rect.y, hazard.rect.width, hazard.rect.height)
                    val pulse = 0.35f + 0.25f * MathUtils.sin(elapsed * 3f + hazard.phase)
                    batch.setColor(1f, 0.9f, 0.5f, pulse)
                    val lureX = hazard.rect.x + hazard.rect.width * 0.85f
                    val lureY = hazard.rect.y + hazard.rect.height * 0.8f
                    batch.draw(uiPixel, lureX - 0.3f, lureY - 0.3f, 0.6f, 0.6f)
                }
                is Hazard.Vortex -> {
                    val centerX = hazard.rect.x + hazard.rect.width / 2f
                    val centerY = hazard.rect.y + hazard.rect.height / 2f
                    val spin = 1f + 0.08f * MathUtils.sin(elapsed * 2.5f + hazard.phase)
                    batch.setColor(1f, 1f, 1f, 0.55f)
                    batch.draw(
                        vortexTex,
                        centerX - hazard.radius * spin,
                        centerY - hazard.radius * spin,
                        hazard.radius * 2f * spin,
                        hazard.radius * 2f * spin
                    )
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
            worldCamera.position.set(worldCameraTarget.xMeters, worldCameraTarget.yMeters, 0f)
            worldCamera.update()
            batch.projectionMatrix = worldCamera.combined
        }
    }

    private fun waterColor(): WaterColor {
        val current = Biome.forDepth(depth)
        val progress = Biome.progressWithin(depth, current)
        return current.blendTo(Biome.nextOf(current), progress)
    }

    private fun drawWorldBackground() {
        val water = waterColor()
        val topR = water.topRed
        val topG = water.topGreen
        val topB = water.topBlue
        val botR = water.bottomRed
        val botG = water.bottomGreen
        val botB = water.bottomBlue

        val visibleCamera = worldCameraTarget
        val backgroundMargin = 1f
        val left = worldViewSpec.worldLeft(visibleCamera) - backgroundMargin
        val top = worldViewSpec.worldTop(visibleCamera) + backgroundMargin
        val visibleWidth = worldViewSpec.viewWidthMeters + backgroundMargin * 2f
        val visibleHeight = worldViewSpec.viewHeightMeters + backgroundMargin * 2f

        val bands = 16
        val bandH = visibleHeight / bands
        for (i in 0 until bands) {
            val t = (i + 1f) / bands
            batch.setColor(
                topR + (botR - topR) * t,
                topG + (botG - topG) * t,
                topB + (botB - topB) * t,
                1f
            )
            batch.draw(uiPixel, left, top - (i + 1) * bandH - 0.1f, visibleWidth, bandH + 0.2f)
        }

        val streakCount = 5
        for (i in 0 until streakCount) {
            val x = left + ((i * 31) % 100) / 100f * visibleWidth
            val speed = 1.3f + (i % 3) * 0.7f
            val span = visibleHeight + 5f
            val start = ((i * 47) % 100) / 100f * span
            val y = top - (start + elapsed * speed) % span - 2.5f
            batch.setColor(1f, 1f, 1f, 0.045f)
            batch.draw(uiPixel, x - 3.5f, y - 0.05f, 7f, 0.1f)
        }

        drawAmbientFish()

        val surface = (1f - (depth / WORLD_WIDTH_METERS)).coerceIn(0f, 1f)
        if (surface > 0.05f) {
            val rayCount = 4
            for (i in 0 until rayCount) {
                val sway = MathUtils.sin(elapsed * 0.35f + i * 1.3f) * 0.7f
                val baseX = left + visibleWidth * (0.16f + i * 0.24f) + sway
                val rayH = visibleHeight * (0.16f + (i % 2) * 0.06f)
                val segments = 6
                for (s in 0 until segments) {
                    val startT = s / segments.toFloat()
                    val endT = (s + 1) / segments.toFloat()
                    val alpha = 0.05f * surface * (1f - endT * 0.85f)
                    val w = 1.3f - endT * 0.6f
                    val segmentH = rayH * (endT - startT) + 0.05f
                    batch.setColor(0.75f, 0.95f, 1f, alpha)
                    batch.draw(uiPixel, baseX - w / 2f, top - rayH * endT, w, segmentH)
                }
            }
            batch.setColor(1f, 1f, 1f, 0.07f * surface)
            batch.draw(uiPixel, left, top - 2f, visibleWidth, 2f)
        }
        batch.setColor(1f, 1f, 1f, 1f)
    }

    private fun drawAmbientFish() {
        val visibleCamera = worldCameraTarget
        val left = worldViewSpec.worldLeft(visibleCamera)
        val right = worldViewSpec.worldRight(visibleCamera)
        val top = worldViewSpec.worldTop(visibleCamera)
        val bottom = worldViewSpec.worldBottom(visibleCamera)
        val visibleWidth = right - left
        val visibleHeight = top - bottom
        val fishCount = 9
        val scale = if (visibleWidth >= 60f) 1.15f else 0.9f
        for (i in 0 until fishCount) {
            val laneFrac = ((i * 29) % 100) / 100f
            val baseY = bottom + visibleHeight * (0.08f + laneFrac * 0.78f)
            val speed = 1f + (i % 4) * 0.45f
            val span = visibleWidth + 9f
            val dir = if ((i % 2) == 0) 1 else -1
            val cx = if (dir == 1) {
                left + (elapsed * speed % span) - 4.5f
            } else {
                left + span - (elapsed * speed % span) - 4.5f
            }
            val cy = baseY + MathUtils.sin(elapsed * 1.1f + i * 2.1f) * 0.35f
            val size = (1.1f + (i % 3) * 0.35f) * scale
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
        batch.projectionMatrix = screenCamera.combined
        batch.begin()
        drawMenuBackground()
        drawMenuVignette()
        batch.end()
        fbo.end()
        batch.projectionMatrix = screenCamera.combined
        batch.begin()
        batch.setColor(1f, 1f, 1f, 0.92f)
        batch.draw(fbo.colorBufferTexture, 0f, 0f, screenWidth, screenHeight)
        batch.setColor(0f, 0.03f, 0.10f, 0.22f)
        batch.draw(uiPixel, 0f, 0f, screenWidth, screenHeight)
        batch.setColor(Color.WHITE)
    }

    private fun drawMenuContent() {
        val pulse = 0.5f + 0.5f * MathUtils.sin(menuTime * 1.8f)
        val titleY = screenHeight * 0.86f
        titleFont.color = Color(0.12f, 0.5f, 0.95f, 0.22f + 0.15f * pulse)
        for (off in floatArrayOf(-3f, 3f)) {
            Widgets.text(batch, titleFont, Strings.t("menuTitle"), screenWidth / 2f + off, titleY)
        }
        titleFont.color = Color(0.4f + 0.5f * pulse, 0.87f, 1f, 1f)
        Widgets.text(batch, titleFont, Strings.t("menuTitle"), screenWidth / 2f, titleY)
        titleFont.color = Color(0.35f, 0.85f, 1f, 1f)
        batch.setColor(0.2f, 0.78f, 1f, 0.55f)
        batch.draw(uiPixel, screenWidth / 2f - 170f, titleY - 26f, 340f, 4f)
        batch.draw(uiPixel, screenWidth / 2f - 110f, titleY - 35f, 220f, 3f)
        batch.setColor(Color.WHITE)

        if (screenHeight >= 520f) {
            font.color = Color.CYAN
            Widgets.text(batch, font, Strings.t("menuSubtitle"), screenWidth / 2f, screenHeight * 0.74f)
        }
        font.color = Color.WHITE
        val labels = menuLabels()
        for (i in labels.indices) {
            val (cx, cy) = menuGridPos(i)
            Widgets.pill(batch, font, uiPixel, cx, cy, labels[i])
        }

        val segs = difficultySegs()
        font.color = Color(0.5f, 0.8f, 1f, 0.85f)
        Widgets.text(batch, font, Strings.t("difficulty"), screenWidth / 2f, segs[0][1] + segs[0][3] / 2f + 18f)
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
        val edge = screenHeight * 0.06f
        batch.setColor(0f, 0.03f, 0.09f, 0.40f)
        batch.draw(uiPixel, 0f, screenHeight - edge, screenWidth, edge)
        batch.draw(uiPixel, 0f, 0f, screenWidth, edge)
        batch.draw(uiPixel, 0f, edge, edge, screenHeight - 2f * edge)
        batch.draw(uiPixel, screenWidth - edge, edge, edge, screenHeight - 2f * edge)
        batch.setColor(Color.WHITE)
    }

    private fun drawMenuBackground() {
        val bubbleCount = 26
        for (i in 0 until bubbleCount) {
            val xFrac = (i * 37 % 100) / 100f
            val x = xFrac * screenWidth
            val speed = 20f + (i % 5) * 9f
            val size = 4f + (i % 4) * 3f
            val start = (i * 53 % 100) / 100f * (screenHeight + 80f)
            val y = (start + menuTime * speed) % (screenHeight + 80f) - 40f
            val alpha = 0.10f + (i % 3) * 0.05f
            batch.setColor(0.55f, 0.85f, 1f, alpha)
            batch.draw(uiPixel, x - size / 2f, y - size / 2f, size, size)
        }
        val dSize = min(screenWidth, screenHeight) * 0.108f
        val dx = screenWidth * 0.5f + MathUtils.sin(menuTime * 0.5f) * screenWidth * 0.16f
        val dy = screenHeight * 0.585f + MathUtils.sin(menuTime * 1.1f) * 12f
        batch.draw(playerTex, dx - dSize / 2f, dy - dSize / 2f, dSize, dSize)
        batch.setColor(1f, 1f, 1f, 1f)
    }

    private fun drawSubScreenHeader(title: String) {
        Widgets.text(batch, titleFont, title, screenWidth / 2f, screenHeight * 0.84f)
        val pill = backPill()
        Widgets.pill(batch, font, uiPixel, pill.cx, pill.cy, Strings.t("back"))
    }

    private fun drawProfileScreen() {
        Widgets.text(batch, titleFont, Strings.t("profile"), screenWidth / 2f, screenHeight * 0.84f)
        Widgets.pill(batch, font, uiPixel, screenWidth * 0.2f, screenHeight * 0.08f, Strings.t("back"))
        val achLabel = "${Strings.t("achievements")} ${Achievements.count()}/${Achievements.ALL.size}"
        Widgets.pill(batch, font, uiPixel, screenWidth * 0.8f, screenHeight * 0.08f, achLabel)
        val panelCx = screenWidth / 2f
        val panelCy = screenHeight / 2f
        val panelW = subPanelW()
        val panelH = screenHeight * 0.58f
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
    private fun profileClaimCy(): Float = screenHeight / 2f - 168f

    private fun drawAchievementsScreen() {
        drawSubScreenHeader(Strings.t("achievements"))
        val panelCx = screenWidth / 2f
        val panelCy = screenHeight / 2f
        val panelW = subPanelW()
        val panelH = screenHeight * 0.52f
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
        val panelCx = screenWidth / 2f
        val panelCy = screenHeight / 2f
        val panelW = subPanelW()
        val panelH = screenHeight * 0.56f
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

    private fun drawSettingsScreen() {
        drawSubScreenHeader(Strings.t("settings"))
        val panel = settingsPanel()
        Widgets.panel(batch, uiPixel, panel.cx, panel.cy, panel.w, panel.h)

        // Volume controls
        val lineGap = min(50f, panel.h / 8f)
        val startY = panel.cy + panel.h / 2f - 30f

        // Master volume
        font.color = Color.WHITE
        Widgets.textLeft(batch, font, Strings.t("masterVolume"), panel.cx - panel.w / 2f + 30f, startY)
        val masterVol = Profile.masterVolume()
        Widgets.pill(batch, font, uiPixel, panel.cx + panel.w / 2f - 60f, startY, 
            "${(Profile.masterVolume() * 100).toInt()}%", enabled = true)

        // SFX volume
        val sfxY = startY - lineGap
        font.color = Color.WHITE
        Widgets.textLeft(batch, font, Strings.t("sfxVolume"), panel.cx - panel.w / 2f + 30f, sfxY)
        Widgets.pill(batch, font, uiPixel, panel.cx + panel.w / 2f - 60f, sfxY,
            "${(Profile.sfxVolume() * 100).toInt()}%", enabled = true)

        // Music volume
        val musicY = sfxY - lineGap
        font.color = Color.WHITE
        Widgets.textLeft(batch, font, Strings.t("musicVolume"), panel.cx - panel.w / 2f + 30f, musicY)
        Widgets.pill(batch, font, uiPixel, panel.cx + panel.w / 2f - 60f, musicY,
            "${(Profile.musicVolume() * 100).toInt()}%", enabled = true)

        // Reduce motion
        val reduceMotionY = musicY - lineGap
        font.color = Color.WHITE
        Widgets.textLeft(batch, font, Strings.t("reduceMotion"), panel.cx - panel.w / 2f + 30f, reduceMotionY)
        Widgets.pill(batch, font, uiPixel, panel.cx + panel.w / 2f - 60f, reduceMotionY,
            if (Profile.reduceMotion()) Strings.t("on") else Strings.t("off"), 
            enabled = true)

        // High contrast
        val contrastY = reduceMotionY - lineGap
        font.color = Color.WHITE
        Widgets.textLeft(batch, font, Strings.t("highContrast"), panel.cx - panel.w / 2f + 30f, contrastY)
        Widgets.pill(batch, font, uiPixel, panel.cx + panel.w / 2f - 60f, contrastY,
            if (Profile.highContrast()) Strings.t("on") else Strings.t("off"),
            enabled = true)

        // Screen shake
        val shakeY = contrastY - lineGap
        font.color = Color.WHITE
        Widgets.textLeft(batch, font, Strings.t("screenShake"), panel.cx - panel.w / 2f + 30f, shakeY)
        Widgets.pill(batch, font, uiPixel, panel.cx + panel.w / 2f - 60f, shakeY,
            if (Profile.screenShakeEnabled()) Strings.t("on") else Strings.t("off"),
            enabled = true)

        font.color = Color.WHITE
    }

    private fun settingsPanel(): ShopRect {
        val panelW = subPanelW()
        val panelH = screenHeight * 0.7f
        return ShopRect(screenWidth / 2f, screenHeight / 2f, panelW, panelH)
    }

    private fun drawHud() {
        val glyphLayout = GlyphLayout()
        font.color = Color.WHITE

        glyphLayout.setText(font, "Hg")
        val lineHeight = glyphLayout.height
        val padding = 6f
        val lineSpacing = 8f
        val colGap = 22f

        var y = screenHeight - lineHeight - padding

        font.color = Color(0.6f, 0.85f, 1f, 0.9f)
        glyphLayout.setText(font, Strings.t(biome.nameKey))
        font.draw(batch, glyphLayout, screenWidth / 2f - glyphLayout.width / 2f, y)
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
        font.draw(batch, glyphLayout, screenWidth - 10f - glyphLayout.width, y)
        font.color = Color.WHITE

        if (bossWarning > 0f) {
            font.color = Color(1f, 0.35f, 0.3f, 1f)
            glyphLayout.setText(font, Strings.t("leviathan"))
            font.draw(batch, glyphLayout, screenWidth / 2f - glyphLayout.width / 2f, y - lineHeight * 1.4f)
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
            val plCX = screenWidth - 12f - plW / 2f
            val plCY = y
            val (w, h) = Widgets.pill(batch, font, uiPixel, plCX, plCY, pl)
            hudPauseW = w
            hudPauseH = h
            hudPauseCx = plCX
            hudPauseCy = plCY
        } else {
            hudPauseW = screenWidth * 0.1f
            hudPauseH = screenHeight * 0.05f
            hudPauseCx = screenWidth - 12f - hudPauseW / 2f
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
            batch.draw(uiPixel, 0f, screenHeight - edge, screenWidth, edge)
            batch.draw(uiPixel, 0f, 0f, screenWidth, edge)
            batch.draw(uiPixel, 0f, edge, edge, screenHeight - 2f * edge)
            batch.draw(uiPixel, screenWidth - edge, edge, edge, screenHeight - 2f * edge)
            batch.setColor(1f, 1f, 1f, 1f)
        }
        if (state == GameState.GAME_OVER) {
            val centerX = screenWidth / 2f
            val box = gameOverBox()
            val tall = screenHeight >= 560f

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
                val panelW = min(screenWidth * 0.75f, 460f)
                Widgets.panel(batch, uiPixel, panelCx, panelCy, panelW, box.panelH)

                val rowGap = min(44f, screenHeight / 22f)
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
                val centerY = screenHeight / 2f
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
        if (biomeToastTimer > 0f) drawBiomeBanner()
        if (achievementToastTimer > 0f) drawAchievementToast()
    }

    private fun drawBiomeBanner() {
        val alpha = (biomeToastTimer / BIOME_BANNER_FADE).coerceIn(0f, 1f)
        val label = Strings.t(biome.nameKey)
        val layout = GlyphLayout(font, label)
        val centerY = screenHeight * 0.32f
        val w = layout.width + 56f
        val h = layout.height + 22f
        batch.setColor(0f, 0f, 0f, 0.45f * alpha)
        batch.draw(uiPixel, screenWidth / 2f - w / 2f, centerY - h / 2f, w, h)
        batch.setColor(1f, 1f, 1f, 1f)
        font.color = Color(0.6f, 0.9f, 1f, alpha)
        font.draw(batch, label, screenWidth / 2f - layout.width / 2f, centerY - (layout.height - font.capHeight) / 2f)
        font.color = Color.WHITE
    }

    private fun drawAchievementToast() {
        val s = achievementToast ?: return
        val layout = GlyphLayout(font, s)
        val w = layout.width + 48f
        val h = layout.height + 26f
        Widgets.panel(batch, uiPixel, screenWidth / 2f, screenHeight - 56f, w, h)
        font.color = Color.GOLD
        Widgets.text(batch, font, s, screenWidth / 2f, screenHeight - 56f)
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
        val centerX = screenWidth / 2f
        val centerY = screenHeight / 2f
        val tall = screenHeight >= 540f
        val r = pauseRows(tall, lineHeight)
        val s = pauseSpacing(lineHeight)
        val panelH = if (tall) 8.8f * s else 6.1f * s

        Widgets.panel(batch, uiPixel, centerX, centerY, screenWidth * 0.76f, panelH)

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

    private fun updateWorldCamera() {
        worldCameraTarget = worldViewSpec.cameraFor(playerX, playerY)
        worldCamera.position.set(worldCameraTarget.xMeters, worldCameraTarget.yMeters, 0f)
        worldCamera.update()
    }

    private fun dispatch(action: GameAction): Boolean {
        if (!flow.accepts(action)) return false
        flow = flow.reduce(action)
        return true
    }

    private fun refreshBests() {
        bestDepth = Profile.bestDepth()
        bestScore = Profile.bestScore()
    }

    private fun recoverStartupRuns() {
        try {
            val recovered = runSettlement.recoverPending()
            if (recovered != null) refreshBests()
            val active = runSettlement.active()
            if (active != null) {
                runSettlement.abandon(active)
                activeRun = null
                refreshBests()
            }
        } catch (_: Exception) {
            activeRun = null
        }
        refreshBests()
    }

    private fun startRun() {
        if (!flow.accepts(GameAction.StartRun)) return
        val ledger = try {
            runSettlement.begin(initialWalletPearls = Profile.pearls())
        } catch (_: Exception) {
            return
        }
        resetWorld()
        startBestScore = Profile.bestScore()
        activeRun = ledger
        dispatch(GameAction.StartRun)
    }

    private fun restartRun() {
        if (!flow.accepts(GameAction.Restart)) return
        if (state == GameState.PLAYING || state == GameState.PAUSED) {
            try {
                abandonActiveRun()
            } catch (_: Exception) {
                return
            }
        }
        val ledger = try {
            runSettlement.begin(initialWalletPearls = Profile.pearls())
        } catch (_: Exception) {
            return
        }
        resetWorld()
        startBestScore = Profile.bestScore()
        activeRun = ledger
        dispatch(GameAction.Restart)
    }

    private fun abandonActiveRun() {
        val ledger = activeRun ?: return
        checkpointActiveRun()
        val result = runSettlement.abandon(ledger)
        activeRun = null
        depth = result.depth
        score = result.score
        runPearls = result.displayedPearls
        leaderboardMade = false
        refreshBests()
    }

    private fun checkpointActiveRun() {
        val ledger = activeRun ?: return
        runSettlement.checkpoint(ledger, depth, score)
        syncRunMirror()
    }

    private fun syncRunMirror() {
        val ledger = activeRun ?: return
        score = ledger.score
        runPearls = ledger.displayedPearls
    }

    private fun awardRunBonus(key: String, pearls: Int, category: BonusCategory): Boolean {
        val ledger = activeRun ?: return false
        val awarded = ledger.awardBonus(key, pearls, category)
        if (awarded <= 0) return false
        Profile.grantPearls(awarded)
        checkpointActiveRun()
        return true
    }

    private fun endGame(reason: RunTerminalReason) {
        if (state != GameState.PLAYING) return
        val ledger = activeRun ?: return
        val result = try {
            checkpointActiveRun()
            runSettlement.settle(ledger, reason)
        } catch (_: Exception) {
            dispatch(GameAction.Pause)
            return
        }
        activeRun = null
        depth = result.depth
        score = result.score
        runPearls = result.displayedPearls
        leaderboardMade = result.leaderboardEntered == true
        refreshBests()
        check(dispatch(GameAction.EndRun))
        audio.playCrash()
    }

    private fun applyUpgrades() {
        maxOxygen = 1f + upgradeOxygenLevel * 0.15f
        playerSpeed = 16f * (1f + upgradeSpeedLevel * 0.08f)
    }

    private fun resetWorld() {
        activeRun = null
        fairness.reset(System.nanoTime())
        gameplayClock.reset()
        frameDelta = 0f
        playerX = INITIAL_PLAYER_X_METERS
        playerY = INITIAL_PLAYER_Y_METERS
        depth = max(0f, -playerY)
        updateWorldCamera()
        score = 0
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
        countdownStep = 0
        biome = Biome.forDepth(0f)
        biomeToastTimer = 0f
        hazards.clear()
        pickups.clear()
        particles.clear()
        stopShake()
    }

private enum class Difficulty(
    val drain: Float,
    val baseScrollMeters: Float,
    val rampMetersPerSecond: Float,
    val spawnMul: Float,
    val pickupMul: Float,
) {
    EASY(0.014f, 3.9f, 1.6f, 1.3f, 1.2f),
    NORMAL(0.02f, 4.5f, 2f, 1f, 1f),
    HARD(0.028f, 5.5f, 2.5f, 0.75f, 0.85f)
}
}