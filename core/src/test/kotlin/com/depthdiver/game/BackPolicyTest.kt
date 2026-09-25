package com.depthdiver.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackPolicyTest {

    @Test
    fun backPausesResumesAndReturnsToMenu() {
        assertEquals(BackAction.PAUSE, backActionFor(GameState.PLAYING))
        assertEquals(BackAction.RESUME, backActionFor(GameState.PAUSED))
        assertEquals(BackAction.MAIN_MENU, backActionFor(GameState.GAME_OVER))
    }

    @Test
    fun backExitsOnlyFromTheMainMenu() {
        assertEquals(BackAction.EXIT, backActionFor(GameState.MAIN_MENU))
        for (state in listOf(GameState.PROFILE, GameState.LEADERBOARD, GameState.SHOP, GameState.ACHIEVEMENTS)) {
            assertEquals(BackAction.MAIN_MENU, backActionFor(state), "$state must not exit the app")
        }
    }

    @Test
    fun everyBackActionMapsToATransitionTheFlowAccepts() {
        for (state in GameState.values()) {
            val action = backActionTarget(state) ?: continue
            val flow = GameFlow(state)
            assertTrue(flow.accepts(action), "back from $state wants unsupported $action")
            assertEquals(expectedTarget(state), flow.reduce(action).state)
        }
    }

    @Test
    fun backFromTheMainMenuHasNoTransition() {
        assertNull(backActionTarget(GameState.MAIN_MENU))
        assertEquals(GameState.MAIN_MENU, GameFlow(GameState.MAIN_MENU).state)
    }

    @Test
    fun backNeverLandsOnAnUnreachableState() {
        for (state in GameState.values()) {
            val action = backActionTarget(state) ?: continue
            val next = GameFlow(state).reduce(action).state
            assertTrue(next != state || action == GameAction.Restart, "back from $state is a no-op loop")
        }
    }

    private fun expectedTarget(state: GameState): GameState = when (state) {
        GameState.PLAYING -> GameState.PAUSED
        GameState.PAUSED -> GameState.PLAYING
        else -> GameState.MAIN_MENU
    }
}
