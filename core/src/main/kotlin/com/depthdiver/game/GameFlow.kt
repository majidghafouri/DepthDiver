package com.depthdiver.game

internal enum class GameState {
    MAIN_MENU,
    PROFILE,
    LEADERBOARD,
    SHOP,
    ACHIEVEMENTS,
    PLAYING,
    PAUSED,
    GAME_OVER,
}

internal sealed interface GameAction {
    data object StartRun : GameAction
    data object OpenProfile : GameAction
    data object OpenLeaderboard : GameAction
    data object OpenShop : GameAction
    data object OpenAchievements : GameAction
    data object MainMenu : GameAction
    data object Pause : GameAction
    data object Resume : GameAction
    data object Restart : GameAction
    data object EndRun : GameAction

    companion object {
        val ALL: List<GameAction> = listOf(
            StartRun,
            OpenProfile,
            OpenLeaderboard,
            OpenShop,
            OpenAchievements,
            MainMenu,
            Pause,
            Resume,
            Restart,
            EndRun,
        )
    }
}

internal data class GameFlow(val state: GameState = GameState.MAIN_MENU) {

    val isMenuVisible: Boolean get() = state == GameState.MAIN_MENU

    val inWorld: Boolean
        get() = state == GameState.PLAYING || state == GameState.PAUSED || state == GameState.GAME_OVER

    fun nextState(action: GameAction): GameState? = when (state) {
        GameState.MAIN_MENU -> when (action) {
            GameAction.StartRun -> GameState.PLAYING
            GameAction.OpenProfile -> GameState.PROFILE
            GameAction.OpenLeaderboard -> GameState.LEADERBOARD
            GameAction.OpenShop -> GameState.SHOP
            else -> null
        }
        GameState.PROFILE -> when (action) {
            GameAction.OpenAchievements -> GameState.ACHIEVEMENTS
            GameAction.MainMenu -> GameState.MAIN_MENU
            else -> null
        }
        GameState.LEADERBOARD -> if (action == GameAction.MainMenu) GameState.MAIN_MENU else null
        GameState.SHOP -> if (action == GameAction.MainMenu) GameState.MAIN_MENU else null
        GameState.ACHIEVEMENTS -> if (action == GameAction.MainMenu) GameState.MAIN_MENU else null
        GameState.PLAYING -> when (action) {
            GameAction.Pause -> GameState.PAUSED
            GameAction.Restart -> GameState.PLAYING
            GameAction.MainMenu -> GameState.MAIN_MENU
            GameAction.EndRun -> GameState.GAME_OVER
            else -> null
        }
        GameState.PAUSED -> when (action) {
            GameAction.Resume -> GameState.PLAYING
            GameAction.Restart -> GameState.PLAYING
            GameAction.MainMenu -> GameState.MAIN_MENU
            else -> null
        }
        GameState.GAME_OVER -> when (action) {
            GameAction.Restart -> GameState.PLAYING
            GameAction.MainMenu -> GameState.MAIN_MENU
            else -> null
        }
    }

    fun accepts(action: GameAction): Boolean = nextState(action) != null

    fun reduce(action: GameAction): GameFlow {
        val next = nextState(action) ?: return this
        return GameFlow(next)
    }
}
