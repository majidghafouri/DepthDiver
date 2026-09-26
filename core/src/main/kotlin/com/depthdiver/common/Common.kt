package com.depthdiver.common

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle
import com.depthdiver.game.MoveDirection
import com.depthdiver.game.WorldViewSpec
import kotlin.math.max
import kotlin.math.min

object Strings {
    private val EN = mapOf(
        "depth" to "DEPTH", "score" to "SCORE", "best" to "BEST", "oxygen" to "OXYGEN",
        "pause" to "PAUSE", "resume" to "RESUME", "restart" to "RESTART",
        "muteOn" to "MUTE ON", "muteOff" to "MUTE OFF", "paused" to "PAUSED",
        "gameOver" to "GAME OVER", "pressR" to "press R to restart",
        "menuTitle" to "DEPTH DIVER", "menuSubtitle" to "plunge into the abyss",
        "play" to "PLAY", "profile" to "PROFILE", "leaderboard" to "LEADERBOARD",
        "shop" to "SHOP", "back" to "BACK", "menu" to "MENU", "quit" to "QUIT",
        "pearls" to "PEARLS", "pearlsEarned" to "PEARLS EARNED", "dives" to "DIVES",
        "newRecord" to "NEW RECORD!", "top5" to "ENTERED TOP 5!", "rank" to "RANK",
        "noRuns" to "NO RUNS YET", "level" to "LVL", "buy" to "BUY", "max" to "MAX",
        "claim" to "CLAIM", "difficulty" to "DIFF", "easy" to "EASY", "normal" to "NORMAL",
        "hard" to "HARD", "achievements" to "ACHIEVEMENTS", "open" to "OPEN",
        "locked" to "LOCKED", "allDone" to "ALL ACHIEVEMENTS UNLOCKED",
        "bossCleared" to "LEVIATHAN CLEARED", "quickBuy" to "QUICK BUY",
        "reachedDepth" to "REACHED DEPTH", "pearlsGained" to "PEARLS GAINED",
        "milestone" to "MILESTONE", "leviathan" to "LEVIATHAN AHEAD!",
        "combo" to "COMBO", "dailyBonus" to "DAILY FIRST-DIVE BONUS",
        "dailyClaimed" to "DAILY BONUS: CLAIMED TODAY", "dailyReady" to "DAILY BONUS: +25 READY",
        "chReach" to "CHALLENGE: REACH", "chCollect" to "CHALLENGE: COLLECT",
        "chScore" to "CHALLENGE: SCORE", "chDone" to "CHALLENGE: COMPLETE TODAY",
        "zoneSunlit" to "SUNLIT COAST", "zoneReef" to "TURQUOISE REEF",
        "zoneMidnight" to "MIDNIGHT ZONE", "zoneAbyss" to "ABYSS",
        "zoneHadal" to "HADAL TRENCH",
        // Settings
        "settings" to "SETTINGS", "masterVolume" to "MASTER VOLUME", "sfxVolume" to "SFX VOLUME",
        "musicVolume" to "MUSIC VOLUME", "reduceMotion" to "REDUCE MOTION",
        "highContrast" to "HIGH CONTRAST", "screenShake" to "SCREEN SHAKE",
        "on" to "ON", "off" to "OFF",
    )
    
    private val ES = mapOf(
        "depth" to "PROFUNDIDAD", "score" to "PUNTUACIÓN", "best" to "MEJOR", "oxygen" to "OXÍGENO",
        "pause" to "PAUSA", "resume" to "REANUDAR", "restart" to "REINICIAR",
        "muteOn" to "SILENCIO ON", "muteOff" to "SILENCIO OFF", "paused" to "PAUSADO",
        "gameOver" to "GAME OVER", "pressR" to "pulsa R para reiniciar",
        "menuTitle" to "DEPTH DIVER", "menuSubtitle" to "sumérgete en el abismo",
        "play" to "JUGAR", "profile" to "PERFIL", "leaderboard" to "CLASIFICACIÓN",
        "shop" to "TIENDA", "back" to "VOLVER", "menu" to "MENÚ", "quit" to "SALIR",
        "pearls" to "PERLAS", "pearlsEarned" to "PERLAS GANADAS", "dives" to "INMERSIONES",
        "newRecord" to "¡NUEVO RÉCORD!", "top5" to "¡TOP 5!", "rank" to "RANGO",
        "noRuns" to "SIN PARTIDAS", "level" to "NIVEL", "buy" to "COMPRAR", "max" to "MÁX",
        "claim" to "RECLAMAR", "difficulty" to "DIFICULTAD", "easy" to "FÁCIL", "normal" to "NORMAL",
        "hard" to "DIFÍCIL", "achievements" to "LOGROS", "open" to "ABRIR",
        "locked" to "BLOQUEADO", "allDone" to "¡TODOS LOS LOGROS!",
        "bossCleared" to "¡LEVIATÁN DERROTADO!", "quickBuy" to "COMPRA RÁPIDA",
        "reachedDepth" to "PROFUNDIDAD ALCANZADA", "pearlsGained" to "PERLAS GANADAS",
        "milestone" to "HITO", "leviathan" to "¡LEVIATÁN ADELANTE!",
        "combo" to "COMBO", "dailyBonus" to "BONO DIARIO PRIMERA INMERSIÓN",
        "dailyClaimed" to "BONO DIARIO: RECLAMADO HOY", "dailyReady" to "BONO DIARIO: +25 LISTO",
        "chReach" to "RETO: ALCANZAR", "chCollect" to "RETO: RECOGER",
        "chScore" to "RETO: PUNTUACIÓN", "chDone" to "RETO: COMPLETADO HOY",
        "zoneSunlit" to "COSTA SOLEADA", "zoneReef" to "ARRECIFE TURQUESA",
        "zoneMidnight" to "ZONA MEDIANOCHE", "zoneAbyss" to "ABISMO",
        "zoneHadal" to "FOSA HADAL",
        // Settings
        "settings" to "AJUSTES", "masterVolume" to "VOLUMEN MAESTRO", "sfxVolume" to "VOLUMEN EFECTOS",
        "musicVolume" to "VOLUMEN MÚSICA", "reduceMotion" to "REDUCIR MOVIMIENTO",
        "highContrast" to "ALTO CONTRASTE", "screenShake" to "VIBRACIÓN PANTALLA",
        "on" to "ACTIVADO", "off" to "DESACTIVADO",
    )
    
    private val locales = mapOf("en" to EN, "es" to ES)
    private var currentLocale = EN
    
    fun setLocale(localeCode: String) {
        currentLocale = locales[localeCode.lowercase()] ?: EN
    }
    
    fun getCurrentLocale(): String = locales.entries.first { it.value === currentLocale }.key
    
    fun t(key: String): String = currentLocale[key] ?: key
}

data class Particle(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    var life: Float, var maxLife: Float, var color: Color, var size: Float,
    var trailLength: Float = 0f,
    var fadeRate: Float = 1f,
    var particleType: ParticleType = ParticleType.NORMAL,
    var gravity: Float = 10f
) {
    var alpha: Float = 1f
        get() = (life / maxLife) * fadeRate

    enum class ParticleType {
        NORMAL,      // Standard particle
        TRAIL,       // Player movement trail
        BUBBLE,      // Rising bubble
        SPARK,       // Sharp spark
        EXPLOSION,   // Explosion debris
        SPLASH       // Water splash
    }
}

const val MAX_FRAME_DELTA = 0.05f
fun safeFrameDelta(delta: Float): Float = when {
    !delta.isFinite() || delta <= 0f -> 0f
    else -> min(delta, MAX_FRAME_DELTA)
}

fun inputDirection(left: Boolean, right: Boolean, up: Boolean, down: Boolean): MoveDirection {
    val x = when { left && right -> 0f; left -> -1f; right -> 1f; else -> 0f }
    val y = when { up && down -> 0f; up -> 1f; down -> -1f; else -> 0f }
    return MoveDirection(x, y).normalized()
}

enum class ShieldCollisionResult { ACTIVATED, BLOCKED, FATAL }
fun resolveShieldCollision(shieldLevel: Int, shieldActive: Boolean, shieldCooldown: Float): ShieldCollisionResult = when {
    shieldActive -> ShieldCollisionResult.BLOCKED
    shieldLevel > 0 && shieldCooldown <= 0f -> ShieldCollisionResult.ACTIVATED
    else -> ShieldCollisionResult.FATAL
}

data class TouchTarget(val cx: Float, val cy: Float, val w: Float, val h: Float) {
    fun contains(tx: Float, ty: Float): Boolean = tx >= cx - w/2f && tx <= cx + w/2f && ty >= cy - h/2f && ty <= cy + h/2f
}

enum class GameOverAction { RESTART, MENU, NONE }
fun gameOverActionAt(tx: Float, ty: Float, restart: TouchTarget, menu: TouchTarget): GameOverAction = when {
    restart.contains(tx, ty) -> GameOverAction.RESTART
    menu.contains(tx, ty) -> GameOverAction.MENU
    else -> GameOverAction.NONE
}

enum class BackAction { EXIT, PAUSE, RESUME, MAIN_MENU }
enum class GameState { MAIN_MENU, PROFILE, LEADERBOARD, SHOP, ACHIEVEMENTS, SETTINGS, PLAYING, PAUSED, GAME_OVER }
sealed interface GameAction {
    data object StartRun : GameAction
    data object OpenProfile : GameAction
    data object OpenLeaderboard : GameAction
    data object OpenShop : GameAction
    data object OpenAchievements : GameAction
    data object OpenSettings : GameAction
    data object MainMenu : GameAction
    data object Pause : GameAction
    data object Resume : GameAction
    data object Restart : GameAction
    data object EndRun : GameAction
}

data class GameOverBox(val titleY: Float, val recordY: Float, val top5Y: Float, val panelTop: Float, val panelBottom: Float, val panelH: Float, val pillY: Float)
data class GameOverTargets(val restart: TouchTarget, val menu: TouchTarget)
data class PauseRows(val title: Float, val pearls: Float, val resume: Float, val side: Float, val menu: Float, val help: Float, val buyLabel: Float, val buy: Float)
fun pauseSpacing(lineHeight: Float) = lineHeight + 28f
fun pauseRows(tall: Boolean, lineHeight: Float): PauseRows {
    val s = lineHeight + 28f
    return if (tall) PauseRows(3f*s, 2f*s, 1f*s, 0f, -1f*s, -2f*s, -3f*s, -4f*s) else PauseRows(2.5f*s, Float.NaN, 1f*s, 0f, -1f*s, -2f*s, Float.NaN, Float.NaN)
}