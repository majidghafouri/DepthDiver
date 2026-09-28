package com.depthdiver.monet

/** Why an ad cannot be shown right now. */
enum class AdBlockReason {
    /** The player has not opted in. This is the default and the answer for
     *  most players, forever, if they want it that way. */
    NOT_OPTED_IN,

    /** No consent has been recorded. Treated as "not opted in" so an
     *  un-answered prompt can never become an accidental yes. */
    NO_CONSENT,

    /** An ad is already on screen. */
    BUSY,

    /** The SDK has nothing to serve. */
    NO_FILL,

    /** There is nothing worth advertising to this player right now. */
    NOTHING_TO_EARN,
}

sealed interface AdResult {
    data class Earned(val reward: String) : AdResult
    data class Unavailable(val reason: AdBlockReason) : AdResult
}

/**
 * Opt-in rewarded video.
 *
 * Rewarded ads are opt-in by construction here: nothing is requested unless the
 * player turned them on, and the switch starts off. There is no interstitial
 * and no banner in this seam, deliberately -- those are the formats that take
 * play away from someone rather than trading a choice for a reward.
 */
interface AdService {
    /** True when the player has said yes to rewarded ads. Settable by the UI. */
    var optedIn: Boolean

    /** Has the player answered the consent prompt? An unanswered prompt is a no. */
    var consented: Boolean

    /** Show a rewarded ad for [rewardId]. */
    fun showRewarded(rewardId: String): AdResult

    /** True if this ad is even worth offering. */
    fun isAvailable(rewardId: String): Boolean = optedIn && consented
}

/**
 * Records intent, shows nothing.
 *
 * The real implementation plays the video through the ad SDK and reports the
 * outcome; until that SDK is a dependency, this refuses every request with the
 * reason, which is exactly what the UI needs to explain why nothing happened.
 */
class LocalAds : AdService {
    override var optedIn: Boolean = false
    override var consented: Boolean = false

    override fun showRewarded(rewardId: String): AdResult {
        if (!consented) return AdResult.Unavailable(AdBlockReason.NO_CONSENT)
        if (!optedIn) return AdResult.Unavailable(AdBlockReason.NOT_OPTED_IN)
        return AdResult.Unavailable(AdBlockReason.NO_FILL)
    }
}

object Ads {
    @Volatile
    private var impl: AdService = LocalAds()

    val service: AdService get() = impl

    fun install(service: AdService) {
        impl = service
    }
}
