package com.depthdiver.run

import com.depthdiver.game.HazardKind

/**
 * Why a dive ended, in words, and what to do about it.
 *
 * The death screen used to say "GAME OVER" and nothing else, which is the least
 * motivating sentence a game can show someone who just spent four minutes
 * failing. A death you cannot name is a death you cannot learn from, so this
 * turns the terminal reason into a specific cause and a specific counter-play.
 *
 * Pure on purpose: this is the part worth testing, and it is also the part most
 * likely to be wrong in a way that only shows up in front of a player.
 */
data class DeathLesson(
    /** Localization key for what killed you. Empty when the run was not a death. */
    val causeKey: String,
    /** Localization key for the counter-play, or null when there is nothing useful. */
    val hintKey: String?,
    /**
     * How far short of their best this run fell, when that is close enough to be
     * motivating. Null when there was no earlier best, or the gap is too wide for
     * "so close" to be true.
     */
    val nearMissMeters: Int?,
) {
    /** True for a run that actually ended badly, as opposed to a voluntary exit. */
    val isDeath: Boolean get() = causeKey.isNotEmpty()

    companion object {
        /**
         * A gap worth calling a near miss.
         *
         * Twenty-five metres is roughly one biome step: close enough that the next
         * dive is obviously going to happen, far enough that the claim is honest.
         */
        const val NEAR_MISS_METERS = 25f

        fun forRun(
            reason: RunTerminalReason,
            depthMeters: Float,
            bestDepthMeters: Float,
            /** Which hazard ended it, when the reason is [RunTerminalReason.HAZARD]. */
            hazard: HazardKind? = null,
        ): DeathLesson {
            if (!isDeathReason(reason)) {
                return DeathLesson(causeKey = "", hintKey = null, nearMissMeters = null)
            }
            return DeathLesson(
                causeKey = causeKeyFor(reason, hazard),
                hintKey = hintKeyFor(reason, hazard),
                nearMissMeters = nearMiss(depthMeters, bestDepthMeters),
            )
        }

        /**
         * Only the reasons where the player lost something. Quitting is not a
         * death and must not be dressed up as one with a lesson attached.
         */
        fun isDeathReason(reason: RunTerminalReason): Boolean = when (reason) {
            RunTerminalReason.OXYGEN,
            RunTerminalReason.HAZARD,
            RunTerminalReason.BOSS,
            -> true

            RunTerminalReason.ABANDONED,
            RunTerminalReason.COMPLETED,
            RunTerminalReason.MANUAL,
            RunTerminalReason.UNKNOWN,
            -> false
        }

        private fun causeKeyFor(reason: RunTerminalReason, hazard: HazardKind?): String = when (reason) {
            RunTerminalReason.OXYGEN -> "causeAir"
            RunTerminalReason.BOSS -> "causeBoss"
            RunTerminalReason.HAZARD -> when (hazard) {
                HazardKind.ROCK -> "causeRock"
                HazardKind.MINE -> "causeMine"
                HazardKind.JELLYFISH -> "causeJelly"
                HazardKind.EEL -> "causeEel"
                HazardKind.ANGLER -> "causeAngler"
                HazardKind.VORTEX -> "causeVortex"
                HazardKind.SHARK -> "causeShark"
                null -> "causeHazard"
            }

            else -> ""
        }

        /**
         * The counter-play, per hazard where there is a real one to give.
         *
         * A hint that restates the cause ("mines kill you") teaches nothing, so
         * each of these says what to actually do. Where there is genuinely
         * nothing to do but dive better, the hint is null rather than filler.
         */
        private fun hintKeyFor(reason: RunTerminalReason, hazard: HazardKind?): String? = when (reason) {
            RunTerminalReason.OXYGEN -> "hintAir"
            RunTerminalReason.BOSS -> "hintBoss"
            RunTerminalReason.HAZARD -> when (hazard) {
                HazardKind.ROCK -> "hintRock"
                HazardKind.MINE -> "hintMine"
                HazardKind.JELLYFISH -> "hintJelly"
                HazardKind.EEL -> "hintEel"
                HazardKind.ANGLER -> "hintAngler"
                HazardKind.SHARK -> "hintShark"
                HazardKind.VORTEX, null -> null
            }

            else -> null
        }

        /**
         * Depth short of the player's best, or null when the claim would be silly.
         *
         * No earlier best means nothing to be short of, and a run that *beat* the
         * best is not a near miss.
         */
        private fun nearMiss(depthMeters: Float, bestDepthMeters: Float): Int? {
            if (bestDepthMeters <= 0f) return null
            val gap = bestDepthMeters - depthMeters
            if (gap <= 0f || gap > NEAR_MISS_METERS) return null
            return gap.toInt().coerceAtLeast(1)
        }
    }
}
