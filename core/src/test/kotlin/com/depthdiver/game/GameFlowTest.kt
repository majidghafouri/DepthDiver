package com.depthdiver.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GameFlowTest {

    private val legal: Map<GameState, Map<GameAction, GameState>> = mapOf(
        GameState.MAIN_MENU to mapOf(
            GameAction.StartRun to GameState.PLAYING,
            GameAction.OpenProfile to GameState.PROFILE,
            GameAction.OpenLeaderboard to GameState.LEADERBOARD,
            GameAction.OpenShop to GameState.SHOP,
            GameAction.OpenSettings to GameState.SETTINGS,
        ),
        GameState.PROFILE to mapOf(
            GameAction.OpenAchievements to GameState.ACHIEVEMENTS,
            GameAction.MainMenu to GameState.MAIN_MENU,
        ),
        GameState.LEADERBOARD to mapOf(GameAction.MainMenu to GameState.MAIN_MENU),
        GameState.SHOP to mapOf(GameAction.MainMenu to GameState.MAIN_MENU),
        GameState.ACHIEVEMENTS to mapOf(GameAction.MainMenu to GameState.MAIN_MENU),
        GameState.SETTINGS to mapOf(GameAction.MainMenu to GameState.MAIN_MENU),
        GameState.PLAYING to mapOf(
            GameAction.Pause to GameState.PAUSED,
            GameAction.Restart to GameState.PLAYING,
            GameAction.MainMenu to GameState.MAIN_MENU,
            GameAction.EndRun to GameState.GAME_OVER,
        ),
        GameState.PAUSED to mapOf(
            GameAction.Resume to GameState.PLAYING,
            GameAction.Restart to GameState.PLAYING,
            GameAction.MainMenu to GameState.MAIN_MENU,
        ),
        GameState.GAME_OVER to mapOf(
            GameAction.Restart to GameState.PLAYING,
            GameAction.MainMenu to GameState.MAIN_MENU,
        ),
    )

    @Test
    fun theFlowStartsOnTheMainMenu() {
        val flow = GameFlow()
        assertEquals(GameState.MAIN_MENU, flow.state)
        assertTrue(flow.isMenuVisible)
        assertFalse(flow.inWorld)
    }

    @Test
    fun everyDeclaredTransitionIsLegal() {
        for ((state, expected) in legal) {
            for ((action, next) in expected) {
                val flow = GameFlow(state)
                val label = "$state + $action"
                assertEquals(next, flow.nextState(action), label)
                assertTrue(flow.accepts(action), label)
                assertEquals(next, flow.reduce(action).state, label)
            }
        }
    }

    @Test
    fun everyOtherTransitionIsRejectedWithoutMutation() {
        for (state in GameState.values()) {
            val flow = GameFlow(state)
            for (action in GameAction.ALL) {
                if (legal.getValue(state)[action] != null) continue
                val label = "$state + $action"
                assertEquals(null, flow.nextState(action), label)
                assertFalse(flow.accepts(action), label)
                assertSame(flow, flow.reduce(action), label)
                assertEquals(state, flow.state, label)
            }
        }
    }

    @Test
    fun aFullRunWalksTheWholeLifecycle() {
        var flow = GameFlow().reduce(GameAction.StartRun)
        assertEquals(GameState.PLAYING, flow.state)

        flow = flow.reduce(GameAction.Pause)
        assertEquals(GameState.PAUSED, flow.state)

        flow = flow.reduce(GameAction.Resume)
        assertEquals(GameState.PLAYING, flow.state)

        flow = flow.reduce(GameAction.EndRun)
        assertEquals(GameState.GAME_OVER, flow.state)

        flow = flow.reduce(GameAction.Restart)
        assertEquals(GameState.PLAYING, flow.state)

        flow = flow.reduce(GameAction.EndRun).reduce(GameAction.MainMenu)
        assertEquals(GameState.MAIN_MENU, flow.state)
        assertTrue(flow.isMenuVisible)
    }

    @Test
    fun aPausedRunCanRestartStraightBackIntoPlay() {
        val restarted = GameFlow(GameState.PLAYING)
            .reduce(GameAction.Pause)
            .reduce(GameAction.Restart)
        assertEquals(GameState.PLAYING, restarted.state)
        assertTrue(restarted.inWorld)
    }

    @Test
    fun aRejectedActionNeverMutatesTheFlowItCameFrom() {
        val playing = GameFlow(GameState.PLAYING)

        assertSame(playing, playing.reduce(GameAction.StartRun))
        assertEquals(GameState.PLAYING, playing.state)

        val paused = playing.reduce(GameAction.Pause)
        assertNotSame(playing, paused)
        assertEquals(GameState.PAUSED, paused.state)
        assertEquals(GameState.PLAYING, playing.state)
    }

    @Test
    fun achievementsOnlyOpenFromTheProfile() {
        assertEquals(
            GameState.ACHIEVEMENTS,
            GameFlow(GameState.PROFILE).reduce(GameAction.OpenAchievements).state,
        )
        assertSame(GameState.MAIN_MENU, GameFlow().reduce(GameAction.OpenAchievements).state)
        for (state in listOf(
            GameState.LEADERBOARD,
            GameState.SHOP,
            GameState.ACHIEVEMENTS,
            GameState.PLAYING,
            GameState.PAUSED,
            GameState.GAME_OVER,
        )) {
            assertFalse(GameFlow(state).accepts(GameAction.OpenAchievements), state.name)
        }
    }

    @SafeVarargs
    private fun onlyTheThreeRunStatesDrawTheWorld() {
        for (state in GameState.values()) {
            assertEquals(state in WORLD_STATES, GameFlow(state).inWorld, state.name)
        }
    }

    private companion object {
        val WORLD_STATES = setOf(GameState.PLAYING, GameState.PAUSED, GameState.GAME_OVER)
    }
}
