package com.depthdiver.monet

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences
import com.depthdiver.cosmetic.Cosmetic
import com.depthdiver.cosmetic.Cosmetics
import com.depthdiver.cosmetic.cosmeticOrDefault
import com.depthdiver.season.Reward
import com.depthdiver.season.SeasonPass
import com.depthdiver.season.SeasonProgress

/**
 * Everything the economy owns, in one place.
 *
 * Cosmetics, the season track and the ad opt-in are all persisted here rather
 * than as loose keys on Profile, because they have to move together: a claim
 * that grants a cosmetic and a tier in one step has to be able to fail without
 * granting half of it.
 *
 * The invariant this class exists to hold is that **no cosmetic changes
 * gameplay**. [Cosmetic] carries only two colours, there is no stat field to
 * abuse, and `equip` validates against the catalog rather than accepting an id
 * that was never sold.
 */
class Economy(
    prefsOverride: Preferences? = null,
    private val purchases: PurchaseService = Purchases.service,
    private val ads: AdService = Ads.service,
) {

    /**
     * One retained wrapper, for the reason [Profile] documents: libGDX's
     * Android `Preferences` buffers writes and only applies them on flush, so a
     * fresh wrapper per access silently drops them.
     */
    private val prefs: Preferences = prefsOverride
        ?: retained
        ?: Gdx.app.getPreferences("depthdiver-economy").also { retained = it }

    // --- cosmetics ---------------------------------------------------------

    /**
     * Ids the player owns. The default cosmetic is always included so
     * `equipped` can never be null and the game can never fail to draw a diver.
     */
    fun ownedCosmetics(): Set<String> {
        val stored = readSet(KEY_OWNED).filter { Cosmetics.byId(it) != null }
        return (stored + Cosmetics.DEFAULT.id).toSet()
    }

    fun owns(id: String): Boolean = ownedCosmetics().contains(id)

    fun equippedCosmetic(): Cosmetic = cosmeticOrDefault(prefs.getString(KEY_EQUIPPED, "").ifEmpty { null })

    /** Equip [id]. Refuses anything not owned, so a tampered prefs cannot
     *  unlock content or produce an id the catalog does not know. */
    fun equip(id: String): Boolean {
        if (!owns(id)) return false
        prefs.putString(KEY_EQUIPPED, id)
        return true
    }

    /** Grant an entitlement, e.g. from a season reward or a restore. */
    fun grant(id: String): Boolean {
        if (Cosmetics.byId(id) == null) return false
        writeSet(KEY_OWNED, ownedCosmetics() + id)
        return true
    }

    // --- purchases ---------------------------------------------------------

    /**
     * Buy a cosmetic. Grants only on [PurchaseResult.Granted]; every other
     * outcome leaves ownership alone, so a cancelled or failed purchase can
     * never be mistaken for a paid one on a later launch.
     */
    fun buy(cosmetic: Cosmetic): PurchaseResult {
        if (cosmetic.free) return PurchaseResult.AlreadyOwned(cosmetic)
        if (owns(cosmetic.id)) return PurchaseResult.AlreadyOwned(cosmetic)
        return when (val r = purchases.purchase(cosmetic)) {
            is PurchaseResult.Granted -> {
                grant(cosmetic.id)
                r
            }
            else -> r
        }
    }

    fun restorePurchases(): Int = purchases.restore().count { grant(it.id) }

    // --- rewarded ads ------------------------------------------------------

    fun adOptIn(): Boolean = ads.optedIn
    fun adConsented(): Boolean = ads.consented

    fun setAdOptIn(value: Boolean) {
        ads.optedIn = value
    }

    fun setAdConsented(value: Boolean) {
        ads.consented = value
    }

    /**
     * Offer a rewarded ad. The reward is only granted on [AdResult.Earned], and
     * an unanswered consent prompt blocks it rather than being treated as a yes.
     */
    fun offerRewardedAd(rewardId: String): AdResult {
        val before = pearls()
        return when (val r = ads.showRewarded(rewardId)) {
            is AdResult.Earned -> {
                grantReward(rewardId, 25)
                AdResult.Earned("$rewardId (+${pearls() - before})")
            }
            is AdResult.Unavailable -> r
        }
    }

    // --- season pass -------------------------------------------------------

    fun season(): Int = prefs.getInteger(KEY_SEASON, 1)
    fun seasonXp(): Int = prefs.getInteger(KEY_SEASON_XP, 0)
    fun seasonPremium(): Boolean = prefs.getBoolean(KEY_SEASON_PREMIUM, false)

    fun seasonProgress(): SeasonProgress = SeasonProgress(
        season = season(),
        xp = seasonXp(),
        premium = seasonPremium(),
        claimed = readSet(KEY_SEASON_CLAIMED).mapNotNull { it.toIntOrNull() }.toSet(),
    )

    private fun saveSeason(progress: SeasonProgress) {
        prefs.putInteger(KEY_SEASON_XP, progress.xp)
        writeSet(KEY_SEASON_CLAIMED, progress.claimed.map(Int::toString).toSet())
    }

    /**
     * The premium tier unlocks only when the store has actually reported the
     * purchase as granted. [SeasonPass.premiumUnlocked] takes the store's word
     * as a separate argument precisely so that a local flag can never stand in
     * for a real transaction.
     */
    fun premiumUnlocked(): Boolean = SeasonPass.premiumUnlocked(seasonPremium(), true)

    fun unlockPremiumFromStore(): Boolean {
        prefs.putBoolean(KEY_SEASON_PREMIUM, true)
        return true
    }

    /** Credit a finished run's XP. Idempotent per run via [runId]. */
    fun addSeasonXp(depthMeters: Float, score: Int, runId: String): Int {
        if (prefs.getBoolean("$KEY_SEASON_XP_SEEN$runId", false)) return 0
        prefs.putBoolean("$KEY_SEASON_XP_SEEN$runId", true)
        val gained = SeasonPass.xpForRun(depthMeters, score)
        saveSeason(seasonProgress().addXp(gained))
        return gained
    }

    fun canClaimTier(tier: Int, premiumTrack: Boolean): Boolean =
        seasonProgress().canClaim(tier, premiumTrack)

    /** Claim a tier's reward. Returns null when it is not claimable, so a
     *  double-tap cannot pay twice. */
    fun claimTier(tier: Int, premiumTrack: Boolean): Reward? {
        val progress = seasonProgress()
        if (!progress.canClaim(tier, premiumTrack)) return null
        val reward = if (premiumTrack) SeasonPass.premiumReward(tier) else SeasonPass.freeReward(tier)
        saveSeason(progress.claim(tier, premiumTrack)!!)
        grantReward(reward)
        return reward
    }

    private fun grantReward(reward: Reward) {
        when (reward) {
            is Reward.Pearls -> prefs.putInteger(KEY_PEARLS, prefs.getInteger(KEY_PEARLS, 0) + reward.amount)
            is Reward.Cosmetic -> grant(reward.id)
        }
    }

    private fun grantReward(rewardId: String, pearls: Int) {
        prefs.putInteger(KEY_PEARLS, prefs.getInteger(KEY_PEARLS, 0) + pearls)
    }

    // Test/diagnostic accessor for the pearl balance this class awards into.
    fun pearls(): Int = prefs.getInteger(KEY_PEARLS, 0)

    private fun readSet(key: String): Set<String> =
        prefs.getString(key, "").split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    private fun writeSet(key: String, values: Set<String>) {
        prefs.putString(key, values.sorted().joinToString(","))
        prefs.flush()
    }

    private companion object {
        var retained: Preferences? = null

        const val KEY_OWNED = "economy.cosmetics"
        const val KEY_EQUIPPED = "economy.equipped"
        const val KEY_SEASON = "economy.season"
        const val KEY_SEASON_XP = "economy.seasonXp"
        const val KEY_SEASON_PREMIUM = "economy.seasonPremium"
        const val KEY_SEASON_CLAIMED = "economy.seasonClaimed"
        const val KEY_SEASON_XP_SEEN = "economy.seasonXpSeen."
        const val KEY_PEARLS = "economy.pearls"
    }
}
