package com.depthdiver

import com.depthdiver.game.ProceduralFairness
import com.depthdiver.run.RunId

/**
 * Shareable run seed system.
 * 
 * A "run seed" encodes the ProceduralFairness seed + optional difficulty
 * so players can share identical runs with friends.
 * 
 * Format: "DD-<seed>-<difficulty>" where difficulty is 0=EASY, 1=NORMAL, 2=HARD
 * Example: "DD-123456789-1"
 */
object RunSeed {

    private const val PREFIX = "DD-"
    private const val SEPARATOR = "-"

    /**
     * Encode a raw seed and difficulty into a shareable string.
     */
    fun encode(seed: Long, difficulty: Int): String {
        return "$PREFIX${seed.toString(36).uppercase()}$SEPARATOR$difficulty"
    }

    /**
     * Decode a shareable string back into (seed, difficulty).
     * Returns null if the format is invalid.
     */
    fun decode(code: String): Pair<Long, Int>? {
        val trimmed = code.trim().uppercase()
        if (!trimmed.startsWith(PREFIX)) return null
        val parts = trimmed.substring(PREFIX.length).split(SEPARATOR)
        if (parts.size != 2) return null
        val seedStr = parts[0]
        val diffStr = parts[1]
        return try {
            val seed = seedStr.toLong(36)
            val diff = diffStr.toIntOrNull() ?: return null
            if (diff !in 0..2) return null
            seed to diff
        } catch (e: NumberFormatException) {
            null
        }
    }

    /**
     * Validate a code without decoding (for UI validation).
     */
    fun isValid(code: String): Boolean = decode(code) != null

    /**
     * Apply a decoded run seed, but only if it encodes the expected difficulty.
     *
     * [fairness] is left untouched when the code is malformed or carries a
     * different difficulty, so a rejected code cannot leave the generator
     * half-re-seeded.
     */
    fun apply(fairness: ProceduralFairness, difficulty: Int, code: String): Boolean {
        val (seed, diff) = decode(code) ?: return false
        if (diff != difficulty) return false
        fairness.reset(seed)
        return true
    }
}

/**
 * RunSeedProvider captures the seed at run creation and provides shareable codes.
 * This should be instantiated at the start of each run.
 */
class RunSeedProvider {

    private var currentSeed: Long = 0
    private var currentDifficulty = 1 // NORMAL

    fun onRunStart(fairness: ProceduralFairness, difficulty: Int): String =
        onRunStartWithSeed(fairness, generateSeed(), difficulty)

    /**
     * Start a run from an explicit seed rather than a fresh one.
     *
     * This is the path a shared/friend code has to take: [onRunStart] always
     * mints a new seed, so routing a shared code through it would overwrite
     * the seed and the two players would get different worlds.
     */
    fun onRunStartWithSeed(fairness: ProceduralFairness, seed: Long, difficulty: Int): String {
        fairness.reset(seed)
        currentSeed = seed
        currentDifficulty = difficulty
        return RunSeed.encode(seed, difficulty)
    }

    fun getCurrentCode(): String = RunSeed.encode(currentSeed, currentDifficulty)

    fun getCurrentSeed(): Long = currentSeed

    fun getCurrentDifficulty(): Int = currentDifficulty

    private fun generateSeed(): Long =
        System.currentTimeMillis() xor (Thread.currentThread().id shl 32)
}