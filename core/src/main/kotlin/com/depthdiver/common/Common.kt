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
        "bossEscaped" to "BOSS ESCAPED",
        "shareRun" to "SHARE RUN",
        "copied" to "COPIED",
        "dailyChallenge" to "Daily Challenge",
        "weeklyChallenge" to "Weekly Challenge",
        "weeklyDesc" to "Complete 3 daily challenges this week",
        "reward" to "Reward",
        // Settings
        "settings" to "SETTINGS", "muted" to "MUTED", "masterVolume" to "MASTER VOLUME", "sfxVolume" to "SFX VOLUME",
        "musicVolume" to "MUSIC VOLUME", "reduceMotion" to "REDUCE MOTION",
        "highContrast" to "HIGH CONTRAST", "screenShake" to "SCREEN SHAKE",
        "on" to "ON", "off" to "OFF",
        // Cosmetics
        "cosmetics" to "COSMETICS", "equip" to "EQUIP", "equipped" to "EQUIPPED",
        "buy" to "BUY", "locked" to "LOCKED",
        "cosmeticsHint" to "Appearance only - never affects a run",
        // Run report
        "yourDives" to "YOUR DIVES", "yourDivesPrivacy" to "These numbers never leave your device",
        "reportRuns" to "Dives", "reportTypicalStop" to "Typical stop", "reportAvgDepth" to "Average depth",
        "reportAvgLength" to "Average length", "reportBestDepth" to "Best depth", "reportBestScore" to "Best score",
        "reportOxygen" to "Ended: out of air", "reportHazard" to "Ended: hazard",
        "reportNoRuns" to "No dives yet - go and leave one", "view" to "VIEW",
        // Mutations
        "mutation" to "MUTATION",
        "mutationChooseFirst" to "CHOOSE A MUTATION",
        "mutationChooseMore" to "CHOOSE ANOTHER",
        "mutationGreedy" to "GREEDY", "mutationGreedyDesc" to "Pearls x2, oxygen drains 35% faster",
        "mutationCautious" to "CAUTIOUS", "mutationCautiousDesc" to "Pearls x0.7, oxygen drains 30% slower",
        "mutationBargain" to "BARGAIN", "mutationBargainDesc" to "Pearls x1.5, currents 25% stronger",
        "mutationIronLungs" to "IRON LUNGS", "mutationIronLungsDesc" to "Oxygen drains 25% slower, you move 10% slower",
        "mutationSecondWind" to "SECOND WIND", "mutationSecondWindDesc" to "Start with extra air, score x0.8",
        "mutationDeepLungs" to "DEEP LUNGS", "mutationDeepLungsDesc" to "Oxygen drains 45% slower, currents 30% stronger",
        "mutationClouds" to "CLOUDS", "mutationCloudsDesc" to "Pickups 2x more often, oxygen drains 20% faster",
        "mutationOpenWater" to "OPEN WATER", "mutationOpenWaterDesc" to "Hazards 70% less often, score x0.75",
        "mutationThickWater" to "THICK WATER", "mutationThickWaterDesc" to "Hazards closer, currents 50% stronger, score x1.4",
        "mutationGhostly" to "GHOSTLY", "mutationGhostlyDesc" to "Mines and rocks cannot end the run, you move 12% slower",
        "mutationJellyproof" to "JELLYPROOF", "mutationJellyproofDesc" to "Jellyfish cannot end the run, you move 8% slower",
        "mutationGilded" to "GILDED", "mutationGildedDesc" to "Pickups 2.5x more often, currents 45% stronger",
        "mutationNomad" to "NOMAD", "mutationNomadDesc" to "Score x1.35, oxygen drains 15% faster",
        "mutationDrift" to "DRIFT", "mutationDriftDesc" to "Currents 2.2x stronger, oxygen drains 20% slower",
        // Opening hints. These were referenced by Opening.kt but never defined,
        // so the first-run tips were showing their own key names.
        "hintSteer" to "Touch the water to swim that way.",
        "hintPearls" to "Pearls are yours. Collect them.",
        // Landmarks.
        "found" to "FOUND",
        "lmKelp" to "Sunlit Kelp",
        "lmArch" to "The Reef Arch",
        "lmWreck" to "The Old Wreck",
        "lmWhale" to "A Whale Falls",
        "lmVent" to "The Black Smoker",
        "landmarksFound" to "Landmarks",
        "landmarksNext" to "Next",
        "landmarksAll" to "all found",
        // Death causes, and what to do about each one. A hint that only restates
        // the cause teaches nothing, so each says what to actually do.
        "causeAir" to "OUT OF AIR",
        "causeHazard" to "HIT SOMETHING",
        "causeRock" to "HIT A ROCK",
        "causeMine" to "MINE",
        "causeJelly" to "JELLYFISH STING",
        "causeEel" to "EEL",
        "causeAngler" to "ANGLER",
        "causeVortex" to "VORTEX",
        "causeShark" to "SHARK",
        "causeBoss" to "THE DEEP KEEPER",
        "hintAir" to "Turn back before the gauge empties. Oxygen upgrades last longer.",
        "hintRock" to "Rocks are the scenery. Stay in open water.",
        "hintMine" to "Mines are the only hazard worth the detour. Swear around them.",
        "hintJelly" to "Jellies drift with the current and sting on contact.",
        "hintEel" to "Eels are fast. Do not let one follow you.",
        "hintAngler" to "The light is bait. The lure is the hook.",
        "hintShark" to "Sharks hunt in open water. Stay low.",
        "hintBoss" to "The Deep Keeper telegraphs before it strikes. Watch the tell, not the boss.",
        "nearMiss" to "only",
        "streakDays" to "STREAK",
        "streakLost" to "Streak ended at",
        "streakLabel" to "Day streak",
        "friendRun" to "FRIEND RUN",
        "friendRunEmpty" to "Copy a run code from a friend first",
        "friendRunBad" to "No run code in your clipboard",
        "friendRunPlaying" to "Playing",
        "challengeDone" to "CHALLENGE COMPLETE",
    )
    
    private val ES = mapOf(
        "depth" to "PROFUNDIDAD", "score" to "PUNTUACIÓN", "best" to "MEJOR", "oxygen" to "OXÍGENO",
        "pause" to "PAUSA", "resume" to "REANUDAR", "restart" to "REINICIAR",
        "muteOn" to "SILENCIO ON", "muteOff" to "SILENCIO OFF", "paused" to "PAUSADO",
        "gameOver" to "GAME OVER", "pressR" to "pulsa R para reiniciar",
        "hintSteer" to "Toca el agua para nadar hacia ahi.",
        "hintPearls" to "Las perlas son tuyas. Recógelas.",
        "found" to "HALLAZGO",
        "lmKelp" to "Alga al Sol", "lmArch" to "El Arco del Arrecife",
        "lmWreck" to "El Pecio Viejo", "lmWhale" to "Cae una Ballena",
        "lmVent" to "El Fumarol Negro",
        "landmarksFound" to "Hitos", "landmarksNext" to "Siguiente",
        "landmarksAll" to "todos encontrados",
        "causeAir" to "SIN AIRE", "causeHazard" to "GOLPEADO",
        "causeRock" to "CONTRA UNA ROCA", "causeMine" to "MINA",
        "causeJelly" to "PICADURA DE MEDUSA", "causeEel" to "ANGUIA",
        "causeAngler" to "RAPE", "causeVortex" to "VÓRTICE", "causeShark" to "TIBURÓN",
        "causeBoss" to "EL GUARDIÁN",
        "hintAir" to "Vuelve antes de que se vacíe la aguja. Las mejoras de oxígeno duran más.",
        "hintRock" to "Las rocas son el decorado. Quédate en aguas abiertas.",
        "hintMine" to "Las minas son el único peligro que compensa. Rodea las que veas.",
        "hintJelly" to "Las medusas van con la corriente y pican al contacto.",
        "hintEel" to "Las anguilas son rápidas. No dejes que te siga una.",
        "hintAngler" to "La luz es el cebo. El anzuelo es el gancho.",
        "hintShark" to "Cazan en aguas abiertas. Quédate abajo.",
        "hintBoss" to "El Guardián avisa antes de golpear. Mira la señal, no al jefe.",
        // Mutation names and descriptions. The mutation screen is entirely
        // untranslated otherwise, so a Spanish player saw ten English cards.
        "mutation" to "MUTACIÓN",
        "mutationChooseFirst" to "Elige tu primera mutación",
        "mutationChooseMore" to "Elige otra mutación",
        "mutationBargain" to "BARATO",
        "mutationBargainDesc" to "Ganas menos puntos, pero los obstáculos hacen menos daño",
        "mutationCautious" to "CAUTELOSO",
        "mutationCautiousDesc" to "Los Supercuentos valen menos y hay menos obstáculos",
        "mutationIronLungs" to "PULMONES DE HIERRO",
        "mutationIronLungsDesc" to "El oxígeno se agota un 30% más despacio",
        "mutationSecondWind" to "SEGUNDO AIRE",
        "mutationSecondWindDesc" to "Empiezas con aire extra, los puntos x0.8",
        "mutationDeepLungs" to "PULMONES PROFUNDOS",
        "mutationDeepLungsDesc" to "El oxígeno se agota un 45% más despacio, las corrientes un 30% más fuertes",
        "mutationClouds" to "NUBES",
        "mutationCloudsDesc" to "El doble de objetos, pero el oxígeno se agota un 20% más rápido",
        "mutationOpenWater" to "AGUA ABIERTA",
        "mutationOpenWaterDesc" to "Un 70% menos de obstáculos, los puntos x0.75",
        "mutationThickWater" to "AGUA Densa",
        "mutationThickWaterDesc" to "Obstáculos más cerca, corrientes un 50% más fuertes, puntos x1.4",
        "mutationGhostly" to "FANTASMAL",
        "mutationGhostlyDesc" to "Minas y rocas no terminan la partida, te mueves un 12% más despacio",
        "mutationJellyproof" to "INMUNE A MEDUSAS",
        "mutationJellyproofDesc" to "Las medusas no terminan la partida, te mueves un 8% más despacio",
        "mutationGilded" to "DORADO",
        "mutationGildedDesc" to "El doble y medio de objetos, corrientes un 45% más fuertes",
        "mutationNomad" to "NÓMADA",
        "mutationNomadDesc" to "Puntos x1.35, el oxígeno se agota un 15% más rápido",
        "mutationDrift" to "DERIVA",
        "mutationDriftDesc" to "Corrientes 2.2x más fuertes, el oxígeno se agota un 20% más despacio",
        "mutationGreedy" to "AVARICIOSO",
        "mutationGreedyDesc" to "Los Supercuentos valen un 60% más, puntos x0.85",
        // The run report, which was English-only in full.
        "reportRuns" to "Inmersiones", "reportNoRuns" to "Aún no hay inmersiones",
        "reportTypicalStop" to "Sueles parar en",
        "reportBestDepth" to "Mejor profundidad",
        "reportBestScore" to "Mejor puntuación",
        "reportAvgDepth" to "Profundidad media",
        "reportAvgLength" to "Duración media",
        "reportHazard" to "Lo que suele matar",
        "reportOxygen" to "sin aire",
        // The run-history line on the profile.
        "yourDives" to "TUS INMERSIONES",
        "yourDivesPrivacy" to "Estos números nunca salen de tu dispositivo",
        "view" to "VER",
        "nearMiss" to "solo a",
        "streakDays" to "RACHA",
        "streakLost" to "La racha terminó en",
        "streakLabel" to "Días de racha",
        "friendRun" to "PARTE DE UN AMIGO",
        "friendRunEmpty" to "Copia primero el código de un amigo",
        "friendRunBad" to "No hay ningún código en el portapapeles",
        "friendRunPlaying" to "Jugando", "challengeDone" to "DESAFÍO COMPLETO",
        "menuTitle" to "DEPTH DIVER", "menuSubtitle" to "sumérgete en el abismo",
        "play" to "JUGAR", "profile" to "PERFIL", "leaderboard" to "CLASIFICACIÓN",
        "shop" to "TIENDA", "back" to "VOLVER", "menu" to "MENÚ", "quit" to "SALIR",
        "cosmetics" to "ASPECTO", "equip" to "EQUIPAR", "equipped" to "PUESTO",
        "buy" to "COMPRAR", "locked" to "BLOQUEADO",
        "cosmeticsHint" to "Solo apariencia - nunca afecta a la partida",
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
        "bossEscaped" to "JEFE ESCAPÓ",
        "shareRun" to "COMPARTIR PARTIDA",
        "copied" to "COPIADO",
        "dailyChallenge" to "Desafío Diario",
        "weeklyChallenge" to "Desafío Semanal",
        "weeklyDesc" to "Completa 3 desafíos diarios esta semana",
        "reward" to "Recompensa",
        // Settings
        "settings" to "AJUSTES", "muted" to "SILENCIADO", "masterVolume" to "VOLUMEN MAESTRO", "sfxVolume" to "VOLUMEN EFECTOS",
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

    /**
     * Each locale's key set, paired with English.
     *
     * Exposed so `StringsLocaleTest` can check coverage without hard-coding key
     * names -- a list in the test would go stale and then quietly stop catching
     * anything, which is worse than having no test at all.
     */
    fun localeKeySets(): Pair<Set<String>, List<Pair<String, Set<String>>>> {
        val others = locales.filter { it.key != "en" }.map { (name, table) -> name to table.keys }
        return EN.keys to others
    }

    /**
     * Non-English values identical to the English one.
     *
     * A translation pasted in as English reads as done in the table and as
     * English on screen. Returns `key -> "ES"` so a report names both.
     */
    fun untranslatedValues(): List<String> = locales.filter { it.key != "en" }.flatMap { (name, table) ->
        table.filter { (key, value) -> EN[key] == value && isProse(value) }
            .map { (key, value) -> "$name:$key=\"$value\"" }
    }

    /**
     * Whether a value looks like prose worth translating.
     *
     * Deliberately narrow: proper nouns, version numbers and single words are
     * legitimately identical across locales, and flagging them would make this
     * test noise nobody reads.
     */
    private fun isProse(value: String): Boolean =
        value.length >= 12 && value.contains(' ') && value.none { it.isDigit() }

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