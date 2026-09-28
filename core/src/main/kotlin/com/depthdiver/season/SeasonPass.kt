package com.depthdiver.season

/**
 * Season pass: a track of tiers the player climbs by diving, with a free track
 * and a paid track alongside it.
 *
 * XP comes from finished runs, not from spending money and not from watching
 * ads. That is the part that matters: the pass is a second reason to play, and
 * if the paid tier were also the fast tier then buying it would be paying to
 * keep up rather than paying to get something extra.
 */
object SeasonPass {

    /** Tiers on the track. */
    const val LAST_TIER = 30

    /** XP for reaching [tier], growing so the back half takes longer. */
    fun xpForTier(tier: Int): Int {
        val t = tier.coerceIn(1, LAST_TIER)
        return 200 + (t - 1) * (t - 1) * 6
    }

    /** The highest tier a player with [xp] has reached. 0 means no progress. */
    fun tierForXp(xp: Int): Int {
        var tier = 0
        while (tier < LAST_TIER && xp >= xpForTier(tier + 1)) tier++
        return tier
    }

    /** XP still owed before the next tier, or 0 at the end of the track. */
    fun xpToNextTier(xp: Int): Int {
        val tier = tierForXp(xp)
        if (tier >= LAST_TIER) return 0
        return xpForTier(tier + 1) - xp
    }

    /**
     * XP earned by a finished run.
     *
     * Scales with depth and score so a long dive is worth more, with a floor so
     * an instantly-drowned run is not worthless. Capped so a single very good
     * run cannot finish the whole season.
     */
    fun xpForRun(depthMeters: Float, score: Int): Int {
        val fromDepth = (depthMeters.coerceAtLeast(0f) * 4f).toInt()
        val fromScore = score / 10
        return (fromDepth + fromScore).coerceIn(20, 400)
    }

    /**
     * What a tier gives on each track.
     *
     * A data class per tier would be a lot of numbers to keep straight; this
     * derives both tracks from the tier so they can never disagree about what
     * "tier 7" means.
     */
    fun freeReward(tier: Int): Reward = when (tier) {
        1 -> Reward.Pearls(20)
        2 -> Reward.Pearls(40)
        else -> Reward.Pearls(20 + (tier % 5) * 10)
    }

    /** The premium track costs real money, so it is a flat currency, not pearls. */
    fun premiumReward(tier: Int): Reward = when (tier) {
        1 -> Reward.Cosmetic("diver_coral")
        8 -> Reward.Cosmetic("diver_kelp")
        15 -> Reward.Cosmetic("diver_frost")
        22 -> Reward.Cosmetic("diver_ember")
        else -> Reward.Pearls(60)
    }

    /** Whether [premium] is actually unlocked, which the game only ever decides
     *  after a real purchase has been reported as granted. */
    fun premiumUnlocked(premium: Boolean, store: Boolean): Boolean = premium && store
}

sealed interface Reward {
    data class Pearls(val amount: Int) : Reward
    data class Cosmetic(val id: String) : Reward
}

/** One season's progress, persisted. */
data class SeasonProgress(
    val season: Int,
    val xp: Int,
    val premium: Boolean,
    /** Tiers whose reward has been taken, so a claim cannot be repeated. */
    val claimed: Set<Int> = emptySet(),
) {
    val tier: Int get() = SeasonPass.tierForXp(xp)
    val xpToNext: Int get() = SeasonPass.xpToNextTier(xp)

    fun canClaim(t: Int, premiumTrack: Boolean): Boolean =
        t in 1..tier && t !in claimed && (!premiumTrack || premium)

    /** Marks [t] taken. Returns false if it was not claimable. */
    fun claim(t: Int, premiumTrack: Boolean): SeasonProgress? =
        if (canClaim(t, premiumTrack)) copy(claimed = claimed + t) else null

    fun addXp(amount: Int): SeasonProgress =
        if (amount <= 0) this else copy(xp = xp + amount)
}
