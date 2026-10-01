package com.depthdiver

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.InputAdapter
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
import com.depthdiver.audio.musicDepthFactor
import com.depthdiver.common.Particle
import com.depthdiver.common.Particle.ParticleType
import com.depthdiver.common.Strings
import com.depthdiver.entity.Hazard
import com.depthdiver.entity.Pickup
import kotlin.ranges.ClosedFloatingPointRange
import com.depthdiver.game.BackAction
import com.depthdiver.game.Biome
import com.depthdiver.game.GamepadBridge
import com.depthdiver.game.MenuMetrics
import com.depthdiver.cosmetic.Cosmetic
import com.depthdiver.cosmetic.Cosmetics
import com.depthdiver.menu.CosmeticBrowserView
import com.depthdiver.menu.LandmarkProgress
import com.depthdiver.monet.PurchaseResult
import com.depthdiver.analytics.AnalyticsReport
import com.depthdiver.analytics.AnalyticsSettings
import com.depthdiver.analytics.Opening
import com.depthdiver.analytics.RunSample
import com.depthdiver.landmark.Landmark
import com.depthdiver.landmark.LandmarkArt
import com.depthdiver.landmark.Landmarks
import com.depthdiver.mutation.HazardFamily
import com.depthdiver.mutation.MutationDeck
import com.depthdiver.mutation.MutationEffects
import com.depthdiver.mutation.MutationOption
import com.depthdiver.monet.Purchases
import com.depthdiver.menu.FontMenuText
import com.depthdiver.monet.Economy
import com.depthdiver.world.ParticleView
import com.depthdiver.world.waterColorAt
import com.depthdiver.world.WorldRenderer
import com.depthdiver.world.WorldShake
import com.depthdiver.world.WorldTextures
import com.depthdiver.world.WorldViewState
import com.depthdiver.menu.MenuGeometry
import com.depthdiver.menu.MenuRenderer
import com.depthdiver.menu.MenuState
import com.depthdiver.menu.Setting
import com.depthdiver.menu.SettingFlags
import com.depthdiver.menu.SettingVolumes
import com.depthdiver.game.UiScale
import com.depthdiver.hud.HudRenderer
import com.depthdiver.hud.HudState
import com.depthdiver.game.GamepadState
import com.depthdiver.game.PadAction
import com.depthdiver.game.gamepadActions
import com.depthdiver.game.gamepadDirection
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
import com.depthdiver.game.SubScreenLayoutFactory
import com.depthdiver.game.INITIAL_PLAYER_X_METERS
import com.depthdiver.game.INITIAL_PLAYER_Y_METERS
import com.depthdiver.game.MoveDirection
import com.depthdiver.game.PLAYER_RADIUS_METERS
import com.depthdiver.game.ProceduralFairness
import com.depthdiver.game.WORLD_WIDTH_METERS
import com.depthdiver.game.WorldViewSpec
import com.depthdiver.game.backActionFor
import com.depthdiver.run.BonusCategory
import com.depthdiver.run.RunLifecycle
import com.depthdiver.run.RunLedger
import com.depthdiver.run.RunOutcome
import com.depthdiver.run.StartupRecovery
import com.depthdiver.run.RunSettlement
import com.depthdiver.run.DeathLesson
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

    /**
     * Repaints the diver in the equipped cosmetic.
     *
     * The sprite is two circles, so a cosmetic is two colours and nothing else
     * -- there is deliberately no stat attached to any of them.
     */
    private fun applyCosmetic(cosmetic: Cosmetic) {
        val pix = Pixmap(64, 64, Pixmap.Format.RGBA8888)
        pix.setColor(redOf(cosmetic.bodyColor), greenOf(cosmetic.bodyColor), blueOf(cosmetic.bodyColor), 1f)
        pix.fillCircle(32, 32, 26)
        pix.setColor(redOf(cosmetic.accentColor), greenOf(cosmetic.accentColor), blueOf(cosmetic.accentColor), 1f)
        pix.fillCircle(40, 40, 10)
        if (this::playerTex.isInitialized) playerTex.dispose()
        playerTex = Texture(pix)
        pix.dispose()
    }
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
    private val lifecycle = RunLifecycle(
        runSettlement,
        grantPearls = { pearls -> Profile.grantPearls(pearls) },
    )
    private var frameDelta = 0f
    private var hudPauseCx = 0f
    private var hudPauseCy = 0f
    private var hudPauseW = 0f
    private var hudPauseH = 0f

    private val audio = AudioManager()

    /**
     * Built in [create], not as a field.
     *
     * `Economy` reaches for preferences on construction, and a field
     * initializer runs before libGDX has an `Application`, so building it here
     * crashed the game on launch. Anything that touches Gdx has to wait for
     * create() even if it does not look like it does.
     */
    private lateinit var economy: Economy

    private fun redOf(rgb: Int) = (rgb shr 16 and 0xFF) / 255f
    private fun greenOf(rgb: Int) = (rgb shr 8 and 0xFF) / 255f
    private fun blueOf(rgb: Int) = (rgb and 0xFF) / 255f

    /** Equip a cosmetic and repaint the diver, if the player owns it. */
    private fun equipCosmetic(id: String): Boolean {
        if (!economy.equip(id)) return false
        applyCosmetic(economy.equippedCosmetic())
        return true
    }
    private var padState = GamepadState()
    private var padPrevious = GamepadState()
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
    private var pickupsInRun = 0
    private var hintTimer = 0f
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
    private var deathLesson: DeathLesson? = null
    private var achievementToast: String? = null
    private var achievementToastTimer = 0f
    private var nextMilestone = 50f
    private var bossWarning = 0f
    private var countdownStep = 0
    private var biome: Biome = Biome.SUNLIT_SHALLOWS
    private var biomeToastTimer = 0f
    private val runSeedProvider = RunSeedProvider()
    private var currentRunCode: String? = null
    private var lowOxyTick = 0f

    private var playerSpeed = 16f
    private val playerRadius = PLAYER_RADIUS_METERS
    private var screenWidth = 800f
    private var screenHeight = 600f
    private var uiScale = UiScale(1f, UiScale.MIN_TOUCH_PX)

    /** Shares the game's batch/font/pixel, so it must be built in create(). */
    private lateinit var hudRenderer: HudRenderer
    private var worldViewSpec = WorldViewSpec(screenWidth, screenHeight)
    private var worldCameraTarget = worldViewSpec.cameraFor(INITIAL_PLAYER_X_METERS, INITIAL_PLAYER_Y_METERS)
    private var menuFbo: FrameBuffer? = null
    private var menuGeometry: MenuGeometry? = null
    private var menuRenderer: MenuRenderer? = null
    private var worldRenderer: WorldRenderer? = null
    private var worldTextures: WorldTextures? = null

    /**
     * Menu geometry, rebuilt whenever the viewport changes. One instance is the
     * single source of truth for every menu box, so what a control is drawn into
     * and what a tap is tested against cannot drift apart.
     */
    private fun menu(): MenuGeometry {
        menuGeometry?.let {
            if (it.screenWidth == screenWidth && it.screenHeight == screenHeight && it.scale == uiScale) return it
        }
        val built = MenuGeometry(FontMenuText(font, titleFont), screenWidth, screenHeight, uiScale)
        menuGeometry = built
        return built
    }

    private fun menuState() = MenuState(
        scale = uiScale,
        menuTime = menuTime,
        muted = audio.muted,
        difficulty = Profile.difficulty(),
        bestDepth = bestDepth,
        bestScore = bestScore,
        bestRunPearls = runPearls,
    )

    /**
     * Landmarks inside the visible band, so the renderer is not handed the whole
     * catalogue every frame.
     */
    private fun visibleLandmarks(): List<WorldViewState.VisibleLandmark> {
        val spec = worldViewSpec
        val top = spec.worldTop(worldCameraTarget)
        val bottom = spec.worldBottom(worldCameraTarget)
        return Landmarks.ALL
            .filter { val y = -it.depthMeters; y in bottom..top }
            .map {
                WorldViewState.VisibleLandmark(
                    id = it.id,
                    kind = it.kind,
                    centerXMeters = it.centerXMeters,
                    depthMeters = it.depthMeters,
                    widthMeters = it.widthMeters,
                    heightMeters = it.heightMeters,
                    alreadyFound = Profile.hasFoundLandmark(it.id),
                )
            }
    }

    private fun world(): WorldViewState = WorldViewState(
        spec = worldViewSpec,
        camera = worldCameraTarget,
        water = waterColorAt(depth),
        depth = depth,
        elapsed = elapsed,
        // Shake only applies during a live run, so the state carries it only there.
        shake = if (state == GameState.PLAYING && shakeTimer > 0f) {
            WorldShake(shakeTimer, shakeDuration, shakeIntensity)
        } else {
            null
        },
        particles = particles.map {
            ParticleView(it.x, it.y, it.life, it.maxLife, it.color, it.size)
        },
        hazards = hazards,
        pickups = pickups,
        landmarks = visibleLandmarks(),
        playerX = playerX,
        playerY = playerY,
        playerRadius = playerRadius,
    )

    private fun worlds() = worldRenderer!!

    private fun menus() = menuRenderer ?: MenuRenderer(
        batch, font, titleFont, uiPixel, playerTex, screenCamera,
    ).also { menuRenderer = it }

    override fun create() {
        batch = SpriteBatch()
        resize(Gdx.graphics.width, Gdx.graphics.height)
        val generator = FreeTypeFontGenerator(Gdx.files.internal("fonts/OpenSans-Regular.ttf"))
        val parameter = FreeTypeFontGenerator.FreeTypeFontParameter().apply {
            size = (screenHeight / 30f * uiScale.factor).toInt().coerceIn(16, 64)
            color = Color.WHITE
            borderWidth = 1f
            borderColor = Color.BLACK
            borderStraight = true
        }
        font = generator.generateFont(parameter)
        titleFont = generator.generateFont(
            FreeTypeFontGenerator.FreeTypeFontParameter().apply {
                size = (screenHeight / 9f * uiScale.factor).toInt().coerceIn(36, 120)
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

        economy = Economy(Profile.preferences())
        applyCosmetic(economy.equippedCosmetic())

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
        hudRenderer = HudRenderer(batch, font, uiPixel)
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

        // playerTex only exists once the pixel art is uploaded.
        menuRenderer = MenuRenderer(batch, font, titleFont, uiPixel, playerTex, screenCamera)
        // Grouped so the world renderer takes one dependency, and built only once
        // every texture above has been uploaded.
        worldTextures = WorldTextures(
            pixel = uiPixel, player = playerTex, rock = rockTex, mine = mineTex,
            jellyfish = jellyfishTex, shark = sharkTex, eel = eelTex,
            angler = anglerTex, vortex = vortexTex, oxygen = oxyTex,
            pearl = pearlTex, fish = fishTex,
        )
        // One texture per landmark kind, so the five authored places are drawn
        // from the same procedural art style as every other prop.
        val landmarkTextures = Landmark.Kind.values().associateWith { LandmarkArt.textureFor(it) }
        worldRenderer = WorldRenderer(batch, worldCamera, font, worldTextures!!, landmarkTextures)
        resetWorld()
        recoverStartupRuns()
        Gdx.input.inputProcessor = object : InputAdapter() {
            override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
                onTouchDragged(touchX())
                return false
            }

            override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                onTouchUp()
                return false
            }
        }
    }

    override fun resize(width: Int, height: Int) {
        screenWidth = width.coerceAtLeast(1).toFloat()
        screenHeight = height.coerceAtLeast(1).toFloat()
        uiScale = UiScale.forScreen(screenWidth, screenHeight)
        screenCamera.setToOrtho(false, screenWidth, screenHeight)
        screenCamera.update()
        worldViewSpec = WorldViewSpec(screenWidth, screenHeight)
        worldCamera.setToOrtho(false, worldViewSpec.viewWidthMeters, worldViewSpec.viewHeightMeters)
        updateWorldCamera()
    }

    /** Slider currently held by the finger, or null when no drag is active. */
    private var draggingSetting: Setting? = null

    /**
     * Drag the volume slider. [ApplicationAdapter] is not an [InputProcessor],
     * so the game registers a tiny adapter to receive these callbacks.
     */
    private fun onTouchDragged(screenX: Float) {
        val setting = draggingSetting ?: return
        if (state != GameState.SETTINGS) return
        val layout = menu().settingsLayout()
        setSettingVolume(setting, MenuMetrics.sliderFraction(screenX, menu().sliderCx(layout), menu().sliderW(layout)))
    }

    private fun onTouchUp() {
        draggingSetting = null
    }

    override fun render() {
        frameDelta = gameplayClock.frameDeltaSeconds(Gdx.graphics.deltaTime)
        performanceMonitor.startFrame()
        // High Contrast is read once per frame so every panel and pill in the
        // frame agrees on the styling.
        Widgets.highContrast = Profile.highContrast()
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
        pollGamepad()
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

    private fun pollGamepad() {
        padPrevious = padState
        padState = GamepadBridge.poll() ?: GamepadState()
    }

    /** @return true when the action was consumed and input handling should stop. */
    private fun applyGamepadAction(action: PadAction): Boolean {
        when (action) {
            PadAction.PAUSE, PadAction.CANCEL -> {
                if (!handleSystemBack()) Gdx.app.exit()
                return true
            }
            PadAction.RESTART -> {
                restartRun()
                return true
            }
            PadAction.FRAME_STATS -> {
                frameTimeOverlay.toggle()
                return true
            }
            PadAction.MUTE -> {
                audio.toggleMute()
                audio.playClick()
                return true
            }
            PadAction.RESUME -> {
                if (state == GameState.PAUSED) {
                    resumeGame()
                    return true
                }
            }
            PadAction.CONFIRM -> {
                when (state) {
                    GameState.GAME_OVER, GameState.PAUSED -> {
                        restartRun()
                        return true
                    }
                    else -> Unit
                }
            }
        }
        return false
    }

    private fun updateBossCountdown() {
        val step = bossCountdownStep(bossWarning)
        if (step == countdownStep) return
        countdownStep = step
        audio.playCountdown(step)
    }

    private fun updateBoss(boss: Hazard.Shark, delta: Float, scrollSpeed: Float) {
        boss.attackTimer += delta
        boss.attackCooldown -= delta

        when (boss.attackPattern) {
            Hazard.BossPattern.IDLE -> {
                boss.rect.y -= scrollSpeed * 0.15f * delta
                boss.rect.x += MathUtils.sin(elapsed * 0.8f + boss.phase) * 0.7f * delta
                if (boss.attackCooldown <= 0f) {
                    chooseNextAttack(boss)
                }
            }
            Hazard.BossPattern.CHARGE -> executeCharge(boss, delta, scrollSpeed)
            Hazard.BossPattern.SWEEP -> executeSweep(boss, delta, scrollSpeed)
            Hazard.BossPattern.DIVE -> executeDive(boss, delta, scrollSpeed)
            Hazard.BossPattern.PROJECTILE -> executeProjectile(boss, delta, scrollSpeed)
        }
    }

    private fun chooseNextAttack(boss: Hazard.Shark) {
        val roll = fairness.unit()
        boss.attackPattern = when {
            roll < 0.35f -> Hazard.BossPattern.CHARGE
            roll < 0.65f -> Hazard.BossPattern.SWEEP
            roll < 0.85f -> Hazard.BossPattern.DIVE
            else -> Hazard.BossPattern.PROJECTILE
        }
        boss.attackTimer = 0f
        boss.attackCooldown = 0f
    }

    private fun executeCharge(boss: Hazard.Shark, delta: Float, scrollSpeed: Float) {
        val windup = 1.2f
        val chargeSpeed = 18f
        if (boss.attackTimer < windup) {
            boss.rect.y -= scrollSpeed * 0.1f * delta
            val pulse = 0.5f + 0.5f * MathUtils.sin(elapsed * 15f)
            boss.rect.x += MathUtils.sin(elapsed * 2f + boss.phase) * pulse * delta
        } else if (boss.attackTimer < windup + 1.5f) {
            val dir = if (playerX > boss.rect.x + boss.rect.width / 2f) 1f else -1f
            boss.rect.x += dir * chargeSpeed * delta
            boss.rect.y -= scrollSpeed * 0.05f * delta
            if (boss.rect.x < boss.rect.width / 2f || boss.rect.x > WORLD_WIDTH_METERS - boss.rect.width / 2f) {
                endAttack(boss)
            }
        } else {
            endAttack(boss)
        }
    }

    private fun executeSweep(boss: Hazard.Shark, delta: Float, scrollSpeed: Float) {
        val windup = 1.0f
        val sweepSpeed = 12f
        if (boss.attackTimer < windup) {
            boss.rect.y -= scrollSpeed * 0.1f * delta
            val pulse = 0.5f + 0.5f * MathUtils.sin(elapsed * 10f)
            boss.rect.x += MathUtils.sin(elapsed * 1.5f + boss.phase) * pulse * delta
        } else if (boss.attackTimer < windup + 2f) {
            val targetX = playerX.coerceIn(boss.rect.width / 2f, WORLD_WIDTH_METERS - boss.rect.width / 2f)
            val dx = targetX - (boss.rect.x + boss.rect.width / 2f)
            val step = Math.signum(dx) * minOf(sweepSpeed * delta, kotlin.math.abs(dx))
            boss.rect.x += step
            boss.rect.y -= scrollSpeed * 0.05f * delta
        } else {
            endAttack(boss)
        }
    }

    private fun executeDive(boss: Hazard.Shark, delta: Float, scrollSpeed: Float) {
        val windup = 1.5f
        val diveSpeed = 22f
        if (boss.attackTimer < windup) {
            boss.rect.y -= scrollSpeed * 0.05f * delta
            val pulse = 0.5f + 0.5f * MathUtils.sin(elapsed * 8f)
            boss.rect.x += MathUtils.sin(elapsed * 2f + boss.phase) * pulse * delta
        } else if (boss.attackTimer < windup + 2f) {
            boss.rect.y -= diveSpeed * delta
            boss.rect.x += MathUtils.sin(elapsed * 3f + boss.phase) * 1.5f * delta
        } else {
            endAttack(boss)
        }
    }

    private fun executeProjectile(boss: Hazard.Shark, delta: Float, scrollSpeed: Float) {
        val windup = 1.0f
        val fireInterval = 0.4f
        if (boss.attackTimer < windup) {
            boss.rect.y -= scrollSpeed * 0.1f * delta
            val pulse = 0.5f + 0.5f * MathUtils.sin(elapsed * 12f)
            boss.rect.x += MathUtils.sin(elapsed * 1.5f + boss.phase) * pulse * delta
        } else if (boss.attackTimer < windup + 3f) {
            boss.rect.y -= scrollSpeed * 0.1f * delta
            boss.rect.x += MathUtils.sin(elapsed * 1.2f + boss.phase) * 0.8f * delta
            if (boss.attackTimer % fireInterval < delta) {
                spawnBossProjectile(boss)
            }
        } else {
            endAttack(boss)
        }
    }

    private fun endAttack(boss: Hazard.Shark) {
        boss.attackPattern = Hazard.BossPattern.IDLE
        boss.attackTimer = 0f
        boss.attackCooldown = 3f + fairness.range(0f, 2f)
    }

    private fun damageBoss(boss: Hazard.Shark) {
        boss.health -= 1f
        if (boss.health <= 0f) {
            onBossDefeated(boss)
        } else {
            audio.playBossHit()
            triggerShake(0.15f, 0.4f, MathUtils.PI)
            spawnSparkParticles(playerX, playerY, Color.MAGENTA, 10)
        }
    }

    private fun onBossDefeated(boss: Hazard.Shark) {
        boss.attackPattern = Hazard.BossPattern.IDLE
        audio.playLevelUp()
        audio.playAchieve()
        triggerShake(0.5f, 0.8f, MathUtils.PI)
        spawnExplosionParticles(playerX, playerY, Color(1f, 0.8f, 0.2f, 1f))
        spawnSparkParticles(playerX, playerY, Color.GOLD, 20)
        Gdx.input.vibrate(200)
        val bonus = 200
        val ledger = lifecycle.ledger ?: return
        if (awardRunBonus("boss:${ledger.runId}", bonus, BonusCategory.BOSS)) {
            achievementToast = "${Strings.t("bossCleared")} +$bonus"
            achievementToastTimer = 4f
        }
        val itr = hazards.iterator()
        while (itr.hasNext()) {
            val h = itr.next()
            if (h is Hazard.Shark && h.isBoss && h === boss) {
                itr.remove()
                break
            }
        }
    }

    private fun spawnBossProjectile(boss: Hazard.Shark) {
        val centerX = boss.rect.x + boss.rect.width / 2f
        val centerY = boss.rect.y
        val projWidth = 1.2f
        val projHeight = 2.5f
        val proj = Hazard.Shark(
            rect = Rectangle(centerX - projWidth / 2f, centerY - projHeight, projWidth, projHeight),
            phase = 0f,
            isBoss = false,
            health = 1f,
            maxHealth = 1f,
            attackPattern = Hazard.BossPattern.IDLE,
        )
        hazards.add(proj)
        audio.playAlert()
    }

    private fun updateAudioMix(dt: Float) {
        if (dt <= 0f) return
        if (state == GameState.PLAYING) {
            val oxygenRatio = if (maxOxygen > 0f) oxygen / maxOxygen else 1f
            audio.updateMusic(
                musicDepthFactor(depth, MUSIC_DEPTH_SCALE),
                oxygenRatio,
                dt,
            )
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

    private fun shareRunCode() {
        val code = shareCurrentRunCode() ?: return
        Gdx.app.getClipboard().setContents(code)
        achievementToast = "${Strings.t("copied")} ${Strings.t("shareRun")}: $code"
        achievementToastTimer = 3f
        audio.playClick()
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

    fun shareCurrentRunCode(): String? = currentRunCode

    fun setRunCodeFromFriend(code: String): Boolean {
        val (seed, diff) = RunSeed.decode(code) ?: return false
        if (diff != Profile.difficulty()) {
            Profile.setDifficulty(diff)
        }
        // The seed has to be threaded into the world reset, otherwise the
        // provider mints a fresh seed and the shared world is never built.
        resetWorld(seed)
        return true
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
        if (!Profile.screenShakeEnabled()) {
            stopShake()
            return
        }
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
        if (Profile.reduceMotion()) return
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
        val ledger = lifecycle.ledger ?: return

        val gamepadDir = gamepadDirection(padState)

        val keyboardDirection = inputDirection(
            left = Gdx.input.isKeyPressed(Input.Keys.LEFT) || Gdx.input.isKeyPressed(Input.Keys.A),
            right = Gdx.input.isKeyPressed(Input.Keys.RIGHT) || Gdx.input.isKeyPressed(Input.Keys.D),
            up = Gdx.input.isKeyPressed(Input.Keys.UP) || Gdx.input.isKeyPressed(Input.Keys.W),
            down = Gdx.input.isKeyPressed(Input.Keys.DOWN) || Gdx.input.isKeyPressed(Input.Keys.S),
        )

        val direction = when {
            !gamepadDir.isZero -> gamepadDir
            !keyboardDirection.isZero -> keyboardDirection
            Gdx.input.isTouched() -> worldViewSpec.touchDirection(
                playerXMeters = playerX,
                playerYMeters = playerY,
                screenX = Gdx.input.x.toFloat(),
                screenYFromTop = Gdx.input.y.toFloat(),
                camera = worldCameraTarget,
            )
            else -> MoveDirection.ZERO
        }
        playerX = (playerX + direction.x * playerSpeed * mutationEffects().playerSpeed * delta)
            .coerceIn(playerRadius, WORLD_WIDTH_METERS - playerRadius)
        playerX = (playerX + currentPush() * delta).coerceIn(playerRadius, WORLD_WIDTH_METERS - playerRadius)
        playerY = (playerY + direction.y * playerSpeed * delta).coerceAtMost(-playerRadius)
        depth = max(0f, -playerY)
        updateWorldCamera()

        spawnTrailParticles()
        spawnBubbleParticles(playerX, playerY - playerRadius * 2)

        elapsed += delta
        if (hintTimer > 0f) hintTimer -= delta
        val diff = currentDifficulty()
        val fx = mutationEffects()
        oxygen -= delta * DifficultyCurve.oxygenDrain(diff.drain, depth) * fx.oxygenDrain
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
            playerSpeedMetersPerSecond = playerSpeed * fx.playerSpeed,
        ) * fx.scrollSpeed

        hazardTimer -= delta
        if (hazardTimer <= 0) {
            spawnHazard()
            val interval = DifficultyCurve.hazardIntervalRange(1.4f, 2.6f, depth, diff.spawnMul)
            hazardTimer = fairness.range(interval.minimum, interval.maximum) * fx.hazardInterval
        }
        pickupTimer -= delta
        if (pickupTimer <= 0) {
            spawnPickup()
            pickupTimer = fairness.pickupInterval(3f, 5.5f, diff.pickupMul) * fx.pickupInterval
        }

        checkLandmarkDiscovery()

        // A milestone is the moment a pick feels earned rather than interrupted.
        if (depth >= nextMilestone) {
            nextMilestone += Opening.MILESTONE_STEP_METERS
            offerMutationPick()
        }

        updateEntities(delta, scrollSpeed)

        val hazardIterator = hazards.iterator()
        while (hazardIterator.hasNext()) {
            val hazard = hazardIterator.next()
            if (hazard is Hazard.Vortex) continue
            if (!playerRect().overlaps(hazard.rect)) continue

            val isBoss = hazard is Hazard.Shark && hazard.isBoss
            val isBossProjectile = hazard is Hazard.Shark && !hazard.isBoss && hazard.maxHealth == 1f && hazard.health == 1f && hazard.attackPattern == Hazard.BossPattern.IDLE

            when (resolveShieldCollision(upgradeShieldLevel, shieldActive, shieldCooldown)) {
                ShieldCollisionResult.ACTIVATED -> {
                    shieldActive = true
                    shieldCooldown = shieldDuration(upgradeShieldLevel)
                    audio.playShield()
                    triggerShake(0.15f, 0.4f, MathUtils.PI)
                    Gdx.input.vibrate(60)
                    spawnExplosionParticles(playerX, playerY, Color.MAGENTA)
                    spawnSparkParticles(playerX, playerY, Color.WHITE, 12)
                    if (isBoss) {
                        damageBoss(hazard)
                    } else {
                        hazardIterator.remove()
                    }
                }
                ShieldCollisionResult.BLOCKED -> {
                    if (isBoss) {
                        audio.playBossHit()
                        triggerShake(0.2f, 0.5f, MathUtils.PI)
                        damageBoss(hazard)
                    } else if (isBossProjectile) {
                        audio.playBossHit()
                        triggerShake(0.15f, 0.3f, MathUtils.PI)
                        hazardIterator.remove()
                    } else {
                        audio.playShieldBreak()
                        spawnSparkParticles(playerX, playerY, Color.MAGENTA, 8)
                        hazardIterator.remove()
                    }
                }
                ShieldCollisionResult.FATAL -> {
                    if (mutationEffects().harmless.contains(familyOf(hazard))) {
                        // Spared by a mutation: it still shoves the player around,
                        // it just does not end the run.
                        audio.playAlert()
                        continue
                    }
                    if (isBoss) {
                        audio.playCrash()
                        triggerShake(0.3f, 0.6f, MathUtils.PI)
                        Gdx.input.vibrate(100)
                        damageBoss(hazard)
                    } else if (isBossProjectile) {
                        audio.playCrash()
                        triggerShake(0.3f, 0.5f, MathUtils.PI)
                        Gdx.input.vibrate(80)
                        hazardIterator.remove()
                    } else {
                        audio.playCrash()
                        triggerShake(0.3f, 0.6f, MathUtils.PI)
                        Gdx.input.vibrate(100)
                        spawnExplosionParticles(playerX, playerY, Color.RED)
                        endGame(RunTerminalReason.HAZARD)
                        return
                    }
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
                        val pearlValue = (5 * (1 + upgradePearlValueLevel * 0.5) * fx.pearlValue).toInt()
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

    /** Which mutation-protected family a hazard belongs to. */
    private fun familyOf(hazard: Hazard): HazardFamily? = when (hazard) {
        is Hazard.Rock -> HazardFamily.ROCK
        is Hazard.Mine -> HazardFamily.MINE
        is Hazard.Jellyfish -> HazardFamily.JELLYFISH
        is Hazard.Angler -> HazardFamily.ANGLER
        is Hazard.Vortex -> HazardFamily.VORTEX
        is Hazard.Eel -> HazardFamily.EEL
        is Hazard.Shark -> HazardFamily.SHARK
    }

    private fun currentPush(): Float {
        var push = 0f
        for (hazard in hazards) {
            if (hazard !is Hazard.Vortex) continue
            val centerX = hazard.rect.x + hazard.rect.width / 2f
            val centerY = hazard.rect.y + hazard.rect.height / 2f
            push += vortexPush(playerX - centerX, playerY - centerY, hazard.radius, hazard.strength)
        }
        return (push * mutationEffects().currentPush).coerceIn(-MAX_CURRENT_PUSH, MAX_CURRENT_PUSH)
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
                    if (hazard.isBoss) {
                        updateBoss(hazard, delta, scrollSpeed)
                    } else {
                        hazard.rect.y -= scrollSpeed * 0.5f * delta
                        hazard.rect.x += MathUtils.sin(elapsed * 0.8f + hazard.phase) * 0.7f * delta
                    }
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
        val hp = if (boss) 20f + (depth / 50f).coerceAtMost(10f) else 1f
        hazards.add(
            Hazard.Shark(
                rect = Rectangle(center - w / 2f, top + 3f, w, h),
                phase = 0f,
                isBoss = boss,
                health = hp,
                maxHealth = hp,
                attackPattern = Hazard.BossPattern.IDLE,
                attackTimer = 0f,
                attackCooldown = 3f,
            )
        )
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
        lifecycle.ledger ?: return
        achievementToast = Strings.t("bossEscaped")
        achievementToastTimer = 3f
        audio.playAlert()
    }

    private fun spawnPickup() {
        val top = worldViewSpec.worldTop(worldCameraTarget)
        val oxygenFraction = if (maxOxygen > 0f) oxygen / maxOxygen else 0f
        val forceTank = fairness.shouldForceOxygenTank(oxygenFraction, depth)
        // The opening of a run should not be a coin flip. A player who has never
        // collected anything gets a pearl first, so the first win lands about
        // two seconds in rather than whenever the dice please.
        val firstOfRun = pickupsInRun == 0
        val tank = !firstOfRun && (forceTank || fairness.unit() >= 0.65f)
        val width = if (tank) 1.6f else 1.2f
        val center = fairness.pickupCenter(playerX, width, depth)
        val phase = fairness.range(0f, MathUtils.PI2)
        pickupsInRun++
        if (tank) {
            pickups.add(Pickup.OxygenTank(Rectangle(center - width / 2f, top + 2.4f, width, width), phase))
        } else {
            pickups.add(Pickup.Pearl(Rectangle(center - width / 2f, top + 2f, width, width), phase))
        }
    }

    private fun handleInput() {
        for (action in gamepadActions(padState, padPrevious)) {
            if (!applyGamepadAction(action)) return
        }

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

            GameState.SETTINGS -> {
                if (Gdx.input.justTouched()) {
                    handleSettingsTouch(touchX(), touchY())
                }
            }

            GameState.PROFILE, GameState.LEADERBOARD, GameState.SHOP,
            GameState.COSMETICS, GameState.ACHIEVEMENTS, GameState.REPORT -> {
                if (Gdx.input.justTouched()) {
                    when (state) {
                        GameState.SHOP -> handleShopTouch(touchX(), touchY())
                        GameState.COSMETICS -> handleCosmeticsTouch(touchX(), touchY())
                        GameState.REPORT -> handleReportTouch(touchX(), touchY())
                        GameState.PROFILE -> handleProfileTouch(touchX(), touchY())
                        else -> handleSubScreenTouch(touchX(), touchY())
                    }
                }
            }

            GameState.MUTATION_SELECT -> {
                if (Gdx.input.justTouched()) {
                    handleMutationTouch(touchX(), touchY())
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

    private fun currentDifficulty(): Difficulty =
        Difficulty.values()[Profile.difficulty().coerceIn(0, Difficulty.values().size - 1)]

    private fun difficultyLabel(): String =
        "${Strings.t("difficulty")}: ${Strings.t(currentDifficulty().name.lowercase())}"

    private fun handleMenuTouch(tx: Float, ty: Float) {
        for (s in menu().difficultySegs()) {
            if (Widgets.contains(tx, ty, s[0], s[1], s[2], s[3])) {
                val index = s[4].toInt()
                if (index != Profile.difficulty()) {
                    Profile.setDifficulty(index)
                    audio.playClick()
                }
                return
            }
        }
        val labels = menus().menuLabels(menuState())
        for (i in labels.indices) {
            val (cx, cy) = menu().menuGridPos(i)
            if (Widgets.containsTouch(
                    tx, ty, cx, cy,
                    Widgets.pillW(font, labels[i], uiScale.factor),
                    Widgets.pillH(font, labels[i], uiScale.factor),
                    uiScale.minTouchPx,
                )
            ) {
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
            4 -> dispatch(GameAction.OpenCosmetics)
            5 -> dispatch(GameAction.OpenSettings)
            6 -> audio.toggleMute()
            7 -> Gdx.app.exit()
        }
    }

    private fun handleSettingsTouch(tx: Float, ty: Float) {
        val back = menu().backPill()
        if (Widgets.contains(tx, ty, back.cx, back.cy, back.w, back.h)) {
            goToMenu()
            return
        }
        val layout = menu().settingsLayout()
        for (setting in Setting.values()) {
            val r = menu().settingControlRect(setting, layout, settingEnabled(setting))
            val hit = Widgets.containsTouch(tx, ty, r.cx, r.cy, r.w, r.h, uiScale.minTouchPx)
            if (!hit) continue
            if (setting.isSlider) {
                // Grab the track, then keep following the finger via touchDragged.
                draggingSetting = setting
                setSettingVolume(setting, MenuMetrics.sliderFraction(tx, menu().sliderCx(layout), menu().sliderW(layout)))
                return
            }
            toggleSetting(setting)
            return
        }
    }

    private fun handleSubScreenTouch(tx: Float, ty: Float) {
        val pill = menu().backPill()
        if (Widgets.contains(tx, ty, pill.cx, pill.cy, pill.w, pill.h)) {
            goToMenu()
        }
    }

    private fun handleProfileTouch(tx: Float, ty: Float) {
        val back = menu().backPill()
        if (Widgets.containsTouch(tx, ty, back.cx, back.cy, back.w, back.h, uiScale.minTouchPx)) {
            goToMenu()
            return
        }
        if (AnalyticsSettings.isVisible() || AnalyticsReport.runs() > 0) {
            val profileLayout = menu().profileLayout()
            val lineY = profileLayout.rowBaseline(6) + profileLayout.rowHeight * 0.9f
            if (Widgets.contains(tx, ty, menu().labelXFor(profileLayout), lineY, profileLayout.panelW * 0.7f, profileLayout.rowHeight)) {
                AnalyticsSettings.setVisible(true)
                dispatch(GameAction.OpenReport)
                audio.playClick()
                return
            }
        }
        val ach = menu().achievementsPill(Achievements.count(), Achievements.ALL.size)
        if (Widgets.containsTouch(tx, ty, ach.cx, ach.cy, ach.w, ach.h, uiScale.minTouchPx)) {
            dispatch(GameAction.OpenAchievements)
            audio.playClick()
            return
        }
        val day = Profile.dailyDay()
        val active = Challenge.activeFor(day)
        if (!Challenge.claimedFor(active) && active.met(bestDepth, Profile.bestRunPearls(), bestScore)) {
            val claim = menu().claimPill()
            if (Widgets.containsTouch(tx, ty, claim.cx, claim.cy, claim.w, claim.h, uiScale.minTouchPx)) {
                Challenge.claim(active)
                Profile.grantPearls(Challenge.REWARD)
                achievementToast = "${Strings.t("claim")} +${Challenge.REWARD}"
                achievementToastTimer = 2.5f
                audio.playClick()
            }
        }
    }

    // --- mutations ---------------------------------------------------------

    private val mutations = MutationDeck()

    /** Every mutation a run has picked up, combined. */
    private fun mutationEffects(): MutationEffects = mutations.effects

    /** Depth at which the next pick is offered, and the depth of the last one. */
    private var nextMutationDepth = Opening.FIRST_PICK_METERS
    private var lastMutationDepth = 0f

    /**
     * Open the pick screen, unless the run is not in a state to.
     *
     * Called from the simulation rather than on a timer, so a dive that ends
     * early never opens a screen nobody chose to answer.
     */
    private fun offerMutationPick() {
        if (state != GameState.PLAYING) return
        if (depth < nextMutationDepth) return
        nextMutationDepth += Opening.PICK_STEP_METERS
        lastMutationDepth = depth
        dispatch(GameAction.OpenMutationSelect)
    }

    private fun mutationOptions(): List<MutationOption> = mutations.offers().map { m ->
        MutationOption(
            id = m.id,
            name = Strings.t(m.nameKey),
            description = Strings.t(m.descriptionKey),
            rarity = m.rarity,
        )
    }

    private fun drawMutationScreen(geometry: MenuGeometry, menuState: MenuState) {
        menus().drawMutationSelect(
            menuState,
            geometry,
            Strings.t(if (lastMutationDepth <= 0f) "mutationChooseFirst" else "mutationChooseMore"),
            mutationOptions(),
        )
    }

    private fun handleMutationTouch(tx: Float, ty: Float) {
        val layout = menu().mutationSelectLayout(mutations.offers().size)
        val options = mutations.offers()
        for (i in options.indices) {
            val box = menu().mutationOptionBox(layout, i)
            if (!Widgets.contains(tx, ty, box.cx, box.cy, box.w, box.h)) continue
            val taken = mutations.take(i) ?: return
            audio.playClick()
            achievementToast = "${Strings.t("mutation")}: ${Strings.t(taken.nameKey)}"
            achievementToastTimer = 2.5f
            // Air granted by a mutation applies the moment it is taken, not at
            // the next reset, or picking "Second Wind" mid-run would do nothing.
            oxygen = (oxygen + taken.effects.startingOxygen).coerceAtMost(maxOxygen)
            dispatch(GameAction.TakeMutation)
            return
        }
    }

    // --- cosmetics ---------------------------------------------------------

    private var cosmeticIndex: Int = 0

    private fun selectedCosmetic(): Cosmetic = Cosmetics.CATALOG[cosmeticIndex % Cosmetics.CATALOG.size]

    private fun landmarkProgress(): LandmarkProgress {
        val next = Landmarks.nextBelow(bestDepth)
        return LandmarkProgress(
            found = Profile.landmarksFoundCount(),
            total = Landmarks.ALL.size,
            nextName = next?.let { Strings.t(it.nameKey) },
        )
    }

    private fun cosmeticsView(): CosmeticBrowserView {
        val c = selectedCosmetic()
        val owned = economy.owns(c.id)
        val equipped = economy.equippedCosmetic().id == c.id
        return CosmeticBrowserView(
            selected = c,
            owned = owned,
            equipped = equipped,
            canAct = !equipped && (owned || Purchases.service.canPurchase),
        )
    }

    private fun handleCosmeticsTouch(tx: Float, ty: Float) {
        val layout = menu().cosmeticsLayout()
        val back = menu().backPill()
        if (Widgets.contains(tx, ty, back.cx, back.cy, back.w, back.h)) {
            goToMenu()
            return
        }
        val (left, right) = menu().cosmeticsArrows(layout)
        if (Widgets.containsTouch(tx, ty, left.cx, left.cy, left.w, left.h, uiScale.minTouchPx)) {
            cosmeticIndex = (cosmeticIndex - 1 + Cosmetics.CATALOG.size) % Cosmetics.CATALOG.size
            audio.playClick()
            return
        }
        if (Widgets.containsTouch(tx, ty, right.cx, right.cy, right.w, right.h, uiScale.minTouchPx)) {
            cosmeticIndex = (cosmeticIndex + 1) % Cosmetics.CATALOG.size
            audio.playClick()
            return
        }
        val action = menu().cosmeticsActionPill(layout)
        if (!Widgets.containsTouch(tx, ty, action.cx, action.cy, action.w, action.h, uiScale.minTouchPx)) {
            return
        }
        val c = selectedCosmetic()
        when {
            // Already worn: a tap must never start a purchase for what is on.
            economy.equippedCosmetic().id == c.id -> audio.playClick()
            economy.owns(c.id) -> {
                if (equipCosmetic(c.id)) {
                    audio.playClick()
                    achievementToast = "${Strings.t("equipped")}: ${c.name}"
                    achievementToastTimer = 2f
                }
            }
            else -> when (val result = economy.buy(c)) {
                is PurchaseResult.Granted -> {
                    equipCosmetic(c.id)
                    audio.playClick()
                }
                is PurchaseResult.AlreadyOwned -> equipCosmetic(c.id)
                // A closed sheet gets no dialog; a failure says why.
                is PurchaseResult.Cancelled -> Unit
                is PurchaseResult.Failed -> {
                    achievementToast = result.reason
                    achievementToastTimer = 2.5f
                }
            }
        }
    }

    // --- run report --------------------------------------------------------

    /** The figures shown on the report screen, already localized. */
    private fun reportLines(): List<Pair<String, String>> {
        val n = AnalyticsReport.runs()
        if (n == 0) {
            return listOf(Strings.t("reportNoRuns") to "")
        }
        val p = AnalyticsReport.prefsForDisplay()
        val avgDepth = p.first / n
        val avgSeconds = p.second / n
        return listOf(
            Strings.t("reportRuns") to "$n",
            Strings.t("reportTypicalStop") to "${(avgDepth / 10f).toInt() * 10} m",
            Strings.t("reportAvgDepth") to "${avgDepth.toInt()} m",
            Strings.t("reportAvgLength") to "${avgSeconds.toInt()} s",
            Strings.t("reportBestDepth") to "${AnalyticsReport.bestDepth().toInt()} m",
            Strings.t("reportBestScore") to "${AnalyticsReport.bestScore()}",
            Strings.t("reportOxygen") to "${AnalyticsReport.oxygenDeaths()}",
            Strings.t("reportHazard") to "${AnalyticsReport.hazardDeaths()}",
        )
    }

    private fun handleReportTouch(tx: Float, ty: Float) {
        val back = menu().backPill()
        if (Widgets.contains(tx, ty, back.cx, back.cy, back.w, back.h)) goToMenu()
    }

    private fun handleShopTouch(tx: Float, ty: Float) {
        val pill = menu().backPill()
        if (Widgets.contains(tx, ty, pill.cx, pill.cy, pill.w, pill.h)) {
            goToMenu()
            return
        }
        val layout = menu().shopLayout()
        Profile.Upgrade.values().forEachIndexed { i, u ->
            if (Profile.isMaxed(u)) return@forEachIndexed
            val pill = menu().shopBuyPill(u, layout, i + 1)
            if (Widgets.containsTouch(tx, ty, pill.cx, pill.cy, pill.w, pill.h, uiScale.minTouchPx)) {
                buyUpgrade(u)
                audio.playClick()
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

    // --- Settings values -----------------------------------------------------
    // Reading and writing settings stays in the game: the renderer is handed
    // numbers, but Profile and the mixer are game-owned state.

    private fun settingVolume(setting: Setting): Float = when (setting) {
        Setting.MUSIC -> Profile.musicVolume()
        Setting.SFX -> Profile.sfxVolume()
        Setting.MASTER -> Profile.masterVolume()
        else -> 0f
    }

    private fun settingEnabled(setting: Setting): Boolean = when (setting) {
        Setting.REDUCE_MOTION -> Profile.reduceMotion()
        Setting.HIGH_CONTRAST -> Profile.highContrast()
        Setting.SCREEN_SHAKE -> Profile.screenShakeEnabled()
        else -> true
    }

    private fun setSettingVolume(setting: Setting, value: Float) {
        when (setting) {
            Setting.MUSIC -> {
                Profile.setMusicVolume(value)
                // The mute pills are gone, so a leftover flag from an older build
                // would keep the channel silent behind a slider that reads 70%.
                Profile.setMusicMuted(value <= 0f)
            }
            Setting.SFX -> {
                Profile.setSfxVolume(value)
                Profile.setSfxMuted(value <= 0f)
            }
            Setting.MASTER -> Profile.setMasterVolume(value)
            else -> return
        }
        // Push the new levels into the mixer so the change is audible at once.
        audio.updateVolumes()
        // Volume 0 is how a channel gets muted now that there is no mute pill.
        audio.updateMuted()
    }

    private fun toggleSetting(setting: Setting) {
        when (setting) {
            Setting.REDUCE_MOTION -> Profile.setReduceMotion(!Profile.reduceMotion())
            Setting.HIGH_CONTRAST -> Profile.setHighContrast(!Profile.highContrast())
            Setting.SCREEN_SHAKE -> Profile.setScreenShakeEnabled(!Profile.screenShakeEnabled())
            else -> return
        }
        audio.playClick()
    }

    private fun goToMenu() {
        if (state == GameState.PLAYING || state == GameState.PAUSED) {
            if (lifecycle.ledger != null && lifecycle.abandon(depth, score) == null) return
            applyOutcome(RunOutcome(depth, score, runPearls, leaderboardMade))
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
        val sideOffset = sideButtonOffset()
        if (Widgets.containsTouch(tx, ty, centerX - sideOffset, centerY + r.side, Widgets.pillW(font, Strings.t("restart"), uiScale.factor), Widgets.pillH(font, Strings.t("restart"), uiScale.factor), uiScale.minTouchPx)) {
            restartRun()
            audio.playClick()
            return
        }
        if (Widgets.containsTouch(tx, ty, centerX + sideOffset, centerY + r.side, Widgets.pillW(font, if (audio.muted) Strings.t("muteOn") else Strings.t("muteOff"), uiScale.factor), Widgets.pillH(font, if (audio.muted) Strings.t("muteOn") else Strings.t("muteOff"), uiScale.factor), uiScale.minTouchPx)) {
            audio.toggleMute()
            audio.playClick()
            return
        }
        if (Widgets.contains(tx, ty, centerX, centerY + r.menu, Widgets.pillW(font, labels[1]), Widgets.pillH(font, labels[1]))) {
            goToMenu()
            audio.playClick()
            return
        }
        if (Widgets.contains(tx, ty, centerX, centerY + r.share, Widgets.pillW(font, Strings.t("shareRun")), Widgets.pillH(font, Strings.t("shareRun")))) {
            shareRunCode()
            audio.playClick()
            return
        }
        if (tall) {
            val oxyLabel = menu().shopBuyLabel(Profile.Upgrade.Oxygen)
            val spdLabel = menu().shopBuyLabel(Profile.Upgrade.Speed)
            if (Widgets.containsTouch(tx, ty, centerX - sideOffset, centerY + r.buy, Widgets.pillW(font, oxyLabel, uiScale.factor), Widgets.pillH(font, oxyLabel, uiScale.factor), uiScale.minTouchPx)) {
                buyUpgrade(Profile.Upgrade.Oxygen)
                return
            }
            if (Widgets.containsTouch(tx, ty, centerX + sideOffset, centerY + r.buy, Widgets.pillW(font, spdLabel, uiScale.factor), Widgets.pillH(font, spdLabel, uiScale.factor), uiScale.minTouchPx)) {
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
            if (state == GameState.PAUSED) drawWorldBlurred() else worlds().render(world())
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
            val geometry = menu()
            val menuState = menuState()
            val renderer = menus()
            when (state) {
                GameState.MAIN_MENU -> renderer.drawMainMenu(menuState, geometry, menuFbo)
                GameState.PROFILE -> {
                    renderer.drawBackground(menuState, geometry, menuFbo)
                    renderer.drawProfileScreen(
                        menuState, geometry,
                        showReportLine = AnalyticsSettings.isVisible(),
                        landmarks = landmarkProgress(),
                    )
                }
                GameState.LEADERBOARD -> {
                    renderer.drawBackground(menuState, geometry, menuFbo)
                    renderer.drawLeaderboardScreen(menuState, geometry)
                }
                GameState.SHOP -> {
                    renderer.drawBackground(menuState, geometry, menuFbo)
                    renderer.drawShopScreen(menuState, geometry)
                }
                GameState.ACHIEVEMENTS -> {
                    renderer.drawBackground(menuState, geometry, menuFbo)
                    renderer.drawAchievementsScreen(menuState, geometry)
                }
                GameState.SETTINGS -> {
                    renderer.drawBackground(menuState, geometry, menuFbo)
                    renderer.drawSettingsScreen(
                        menuState,
                        geometry,
                        SettingVolumes(
                            settingVolume(Setting.MUSIC),
                            settingVolume(Setting.SFX),
                            settingVolume(Setting.MASTER),
                        ),
                        SettingFlags(
                            settingEnabled(Setting.REDUCE_MOTION),
                            settingEnabled(Setting.HIGH_CONTRAST),
                            settingEnabled(Setting.SCREEN_SHAKE),
                        ),
                    )
                }
                GameState.MUTATION_SELECT -> {
                    // Deliberately the world underneath, dimmed: the player should
                    // still see the dive they are choosing a mutation for.
                    renderer.drawBackground(menuState, geometry, menuFbo)
                    drawMutationScreen(geometry, menuState)
                }
                GameState.COSMETICS -> {
                    renderer.drawBackground(menuState, geometry, menuFbo)
                    renderer.drawCosmeticsScreen(menuState, geometry, cosmeticsView())
                }
                GameState.REPORT -> {
                    renderer.drawBackground(menuState, geometry, menuFbo)
                    renderer.drawReportScreen(
                        menuState, geometry, reportLines(), Strings.t("yourDivesPrivacy"),
                    )
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
        worlds().render(world())
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

    private fun drawHud() {
        val glyphLayout = GlyphLayout()
        font.color = Color.WHITE

        val hudState = HudState(
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            lineHeight = glyphLayout.setText(font, "Hg").let { glyphLayout.height },
            depth = depth,
            score = score,
            bestDepth = bestDepth,
            bestScore = bestScore,
            oxygen = oxygen,
            maxOxygen = maxOxygen,
            combo = combo,
            comboTimer = comboTimer,
            maxComboWindow = maxComboWindow,
            elapsed = elapsed,
            isPlaying = state == GameState.PLAYING,
            biomeNameKey = biome.nameKey,
            bossWarningRemaining = bossWarning,
            scale = uiScale.factor,
        )
        val pauseLabel = Strings.t("pause")
        val layout = hudRenderer.render(
            state = hudState,
            difficultyLabel = difficultyLabel(),
            pausePillSize = Widgets.pillW(font, pauseLabel, uiScale.factor) to
                Widgets.pillH(font, pauseLabel, uiScale.factor),
        )
        hudPauseCx = layout.pauseCx
        hudPauseCy = layout.pauseCy
        hudPauseW = layout.pauseW
        hudPauseH = layout.pauseH

        if (state == GameState.PAUSED) {
            drawPauseOverlay(glyphLayout, hudState.lineHeight)
        }
        if (state == GameState.GAME_OVER) {
            drawGameOverOverlay(glyphLayout, hudState.lineHeight)
        }
        menus().drawOpeningHints(
            menuState(), menu(),
            Opening.hintsFor(Profile.dives()),
            Opening.hintAlpha(hintTimer),
        )
        if (biomeToastTimer > 0f) drawBiomeBanner()
        if (achievementToastTimer > 0f) drawAchievementToast()
    }

    private fun drawGameOverOverlay(glyphLayout: GlyphLayout, lineHeight: Float) {
        val centerX = screenWidth / 2f
        val box = gameOverBox()
        val tall = screenHeight >= 560f

        // Name the cause rather than just announcing GAME OVER. A death you
        // cannot name is a death you cannot learn from, and this is the only
        // moment the player is actually thinking about why.
        val lesson = deathLesson
        font.color = Color.RED
        val gameOverStr = if (lesson != null && lesson.isDeath) {
            Strings.t(lesson.causeKey)
        } else {
            Strings.t("gameOver")
        }
        glyphLayout.setText(font, gameOverStr)
        font.draw(batch, glyphLayout, centerX - glyphLayout.width / 2f, box.titleY)

        // The counter-play, and how close the run came. The hint is the whole
        // point; the near miss is what makes the next dive feel worth starting.
        if (lesson != null && lesson.isDeath) {
            var lessonY = box.titleY - 30f
            lesson.nearMissMeters?.let { gap ->
                font.color = Color.GOLD
                val text = "${gap} m ${Strings.t("nearMiss")} ${bestDepth.toInt()} m"
                glyphLayout.setText(font, text)
                font.draw(batch, glyphLayout, centerX - glyphLayout.width / 2f, lessonY)
                lessonY -= 26f
            }
            lesson.hintKey?.let { hintKey ->
                font.color = Color(0.7f, 0.8f, 0.9f, 1f)
                glyphLayout.setText(font, Strings.t(hintKey))
                font.draw(batch, glyphLayout, centerX - glyphLayout.width / 2f, lessonY)
            }
        }

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
        Widgets.pill(batch, font, uiPixel, targets.restart.cx, targets.restart.cy, Strings.t("restart"), scale = uiScale.factor)
        Widgets.pill(batch, font, uiPixel, targets.menu.cx, targets.menu.cy, Strings.t("menu"), scale = uiScale.factor)
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
        val share: Float,
    )

    private fun pauseSpacing(lineHeight: Float): Float = lineHeight + uiScale.gap(28f)

    /**
     * Horizontal offset of the pause menu's paired side buttons.
     *
     * Draw and hit-test must agree on this, so both read it from here rather
     * than repeating a pixel constant.
     */
    private fun sideButtonOffset(): Float = uiScale.px(90f)

    private fun pauseRows(tall: Boolean, lineHeight: Float): PauseRows {
        val s = pauseSpacing(lineHeight)
        return if (tall) {
            PauseRows(3.0f * s, 2.0f * s, 1.0f * s, 0.0f * s, -1.0f * s, -2.0f * s, -3.0f * s, -4.0f * s, -5.0f * s)
        } else {
            PauseRows(2.5f * s, Float.NaN, 1.0f * s, 0.0f * s, -1.0f * s, -2.0f * s, Float.NaN, Float.NaN, -3.5f * s)
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
        val sideOffset = sideButtonOffset()
        Widgets.pill(batch, font, uiPixel, centerX - sideOffset, centerY + r.side, Strings.t("restart"), scale = uiScale.factor)
        Widgets.pill(batch, font, uiPixel, centerX + sideOffset, centerY + r.side, if (audio.muted) Strings.t("muteOn") else Strings.t("muteOff"), scale = uiScale.factor)
        Widgets.pill(batch, font, uiPixel, centerX, centerY + r.menu, Strings.t("menu"))
        Widgets.pill(batch, font, uiPixel, centerX, centerY + r.share, Strings.t("shareRun"))

        font.color = Color.CYAN
        val helpStr = "P/Esc ${Strings.t("resume").lowercase()}    R ${Strings.t("restart").lowercase()}    M ${if (audio.muted) Strings.t("muteOn").lowercase() else Strings.t("muteOff").lowercase()}"
        glyphLayout.setText(font, helpStr)
        font.draw(batch, glyphLayout, centerX - glyphLayout.width / 2f, centerY + r.help)
        font.color = Color.WHITE

        if (tall) {
            font.color = Color.CYAN
            glyphLayout.setText(font, Strings.t("quickBuy"))
            font.draw(batch, glyphLayout, centerX - glyphLayout.width / 2f, centerY + r.buyLabel)

            val oxyLabel = menu().shopBuyLabel(Profile.Upgrade.Oxygen)
            val spdLabel = menu().shopBuyLabel(Profile.Upgrade.Speed)
            val oxyAffordable = !Profile.isMaxed(Profile.Upgrade.Oxygen) && Profile.pearls() >= (Profile.upgradeCost(Profile.Upgrade.Oxygen) ?: 0)
            val spdAffordable = !Profile.isMaxed(Profile.Upgrade.Speed) && Profile.pearls() >= (Profile.upgradeCost(Profile.Upgrade.Speed) ?: 0)
            Widgets.pill(batch, font, uiPixel, centerX - sideOffset, centerY + r.buy, oxyLabel, enabled = oxyAffordable, scale = uiScale.factor)
            Widgets.pill(batch, font, uiPixel, centerX + sideOffset, centerY + r.buy, spdLabel, enabled = spdAffordable, scale = uiScale.factor)
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

    /** Copies a terminal result into the live fields the rest of the game reads. */
    private fun applyOutcome(outcome: RunOutcome?) {
        if (outcome == null) return
        depth = outcome.depth
        score = outcome.score
        runPearls = outcome.displayedPearls
        leaderboardMade = outcome.leaderboardEntered
        refreshBests()
    }

    private fun recoverStartupRuns() {
        when (lifecycle.recoverOnStartup()) {
            is StartupRecovery.Settled -> refreshBests()
            is StartupRecovery.AbandonedStale -> refreshBests()
            // A clean start and a failed read both leave the game with no run;
            // only the abandoned case needed the bests re-read.
            StartupRecovery.Clean, StartupRecovery.Failed -> Unit
        }
    }

    private fun startRun() {
        if (!flow.accepts(GameAction.StartRun)) return
        // Begin before the world reset: if persistence is unhappy the run must
        // not open at all, and the world must be left exactly as it was.
        val ledger = lifecycle.begin(Profile.pearls()) ?: return
        resetWorld()
        startBestScore = Profile.bestScore()
        lifecycle.hold(ledger)
        dispatch(GameAction.StartRun)
    }

    private fun restartRun() {
        if (!flow.accepts(GameAction.Restart)) return
        deathLesson = null
        if (state == GameState.PLAYING || state == GameState.PAUSED) {
            if (lifecycle.ledger != null && lifecycle.abandon(depth, score) == null) return
        }
        val ledger = lifecycle.begin(Profile.pearls()) ?: return
        resetWorld()
        startBestScore = Profile.bestScore()
        lifecycle.hold(ledger)
        dispatch(GameAction.Restart)
    }

    /**
     * Ends the run in progress and moves to the game-over state.
     *
     * A failure to settle leaves the run held and pauses instead, so the player
     * is not dropped into a game-over screen for a dive whose results were never
     * written.
     */
    private fun endGame(reason: RunTerminalReason) {
        if (state != GameState.PLAYING) return
        if (lifecycle.ledger == null) return
        val outcome = lifecycle.end(reason, depth, score)
        if (outcome == null) {
            dispatch(GameAction.Pause)
            return
        }
        recordRunSample(reason, outcome.score)
        applyOutcome(outcome)
        check(dispatch(GameAction.EndRun))
        audio.playCrash()
    }

    /**
     * Log how the run went, so the next design decision is made on where players
     * actually stop rather than on a guess. Local only; see `AnalyticsReport`.
     */
    /**
     * Announce a landmark the first time the dive reaches it.
     *
     * Counted against the deepest depth reached rather than checked per frame,
     * because a fast descent can pass two between frames and a player should not
     * lose one to a dropped update. The reward is only granted the first time,
     * so a repeat visit is a landmark and not a payout.
     */
    private fun checkLandmarkDiscovery() {
        for (lm in Landmarks.reached(depth)) {
            if (!Profile.markLandmarkFound(lm.id)) continue
            if (lm.reward > 0) Profile.grantPearls(lm.reward)
            achievementToast = "${Strings.t("found")} ${Strings.t(lm.nameKey)}" +
                if (lm.reward > 0) "  +${lm.reward}" else ""
            achievementToastTimer = 3f
            audio.playAchieve()
        }
    }

    private fun recordRunSample(reason: RunTerminalReason, finalScore: Int) {
        AnalyticsReport.record(
            RunSample(
                depthMeters = depth,
                score = finalScore,
                seconds = elapsed,
                outcome = com.depthdiver.analytics.RunOutcome.of(reason),
                difficulty = Profile.difficulty(),
                mutations = mutations.active.map { it.id },
            ),
        )
    }

    private fun checkpointActiveRun() {
        val progress = lifecycle.checkpoint(depth, score) ?: return
        val (mirroredScore, mirroredPearls) = lifecycle.mirror(progress)
        score = mirroredScore
        runPearls = mirroredPearls
    }

    private fun awardRunBonus(key: String, pearls: Int, category: BonusCategory): Boolean =
        lifecycle.awardBonus(key, pearls, category) > 0

    private fun applyUpgrades() {
        maxOxygen = 1f + upgradeOxygenLevel * 0.15f
        playerSpeed = 16f * (1f + upgradeSpeedLevel * 0.08f)
    }

    private fun resetWorld(seed: Long? = null) {
        // The original nulled the active run here, and the callers that open a
        // fresh one hold their ledger again straight afterwards.
        lifecycle.detach()
        val difficulty = Profile.difficulty()
        val code = if (seed == null) {
            runSeedProvider.onRunStart(fairness, difficulty)
        } else {
            runSeedProvider.onRunStartWithSeed(fairness, seed, difficulty)
        }
        currentRunCode = code
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
        // A run's mutations belong to that run.
        mutations.clear()
        nextMutationDepth = Opening.FIRST_PICK_METERS
        lastMutationDepth = 0f
        pickupsInRun = 0
        hintTimer = if (Opening.isFirstDive(Profile.dives())) Opening.HINT_SECONDS else 0f
        combo = 1
        comboTimer = 0f
        shieldActive = false
        shieldCooldown = 0f
        runPearls = 0
        nextMilestone = Opening.FIRST_MILESTONE_METERS
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