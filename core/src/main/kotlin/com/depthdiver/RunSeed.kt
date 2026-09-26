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
     * Create a shareable run seed from a ProceduralFairness instance and difficulty.
     */
    fun create(fairness: ProceduralFairness, difficulty: Int): String {
        // The ProceduralFairness doesn't expose its seed directly, so we need
        // to track it at run creation time. This is a simplified version.
        val seed = System.currentTimeMillis() xor (Thread.currentThread().id shl 32)
        fairness.reset(seed) // This re-seeds; in practice capture at run start
        return encode(seed, difficulty)
    }

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
     * Apply a decoded run seed to a ProceduralFairness and Profile difficulty.
     */
    fun apply(fairness: ProceduralFairness, difficulty: Int, code: String): Boolean {
        return decode(code)?.let { (seed, diff) ->
            fairness.reset(seed)
            diff == difficulty // caller can decide to override or enforce
        } ?: false
    }
}

/**
 * RunSeedProvider captures the seed at run creation and provides shareable codes.
 * This should be instantiated at the start of each run.
 */
class RunSeedProvider {

    private var currentSeed: Long = 0
    private var currentDifficulty = 1 // NORMAL

    fun onRunStart(fairness: ProceduralFairness, difficulty: Int): String {
        currentSeed = System.currentTimeMillis() xor (Thread.currentThread().id shl 32)
        fairness.reset(currentSeed)
        currentDifficulty = difficulty
        return RunSeed.encode(currentSeed, currentDifficulty)
    }

    fun getCurrentCode(): String = RunSeed.encode(currentSeed, currentDifficulty)

    fun getCurrentSeed(): Long = currentSeed

    fun getCurrentDifficulty(): Int = currentDifficulty
}