package com.depthdiver.monet

import com.depthdiver.cosmetic.Cosmetic
import com.depthdiver.cosmetic.Cosmetics

/**
 * How a purchase ended.
 *
 * Billing is not a yes/no: a player can cancel the sheet, be short of funds, hit
 * an error, or already own the thing. Collapsing those into one boolean is how
 * an app ends up charging someone for something it then fails to grant.
 */
sealed interface PurchaseResult {
    /** The entitlement was granted. */
    data class Granted(val cosmetic: Cosmetic) : PurchaseResult

    /** The player closed the sheet. Not an error and not worth a dialog. */
    data object Cancelled : PurchaseResult

    /** Nothing was charged and nothing was granted. */
    data class Failed(val reason: String) : PurchaseResult

    /** Already owned. Google will not re-charge, so this is a no-op not a bug. */
    data class AlreadyOwned(val cosmetic: Cosmetic) : PurchaseResult
}

/**
 * The one thing the game ever asks about buying.
 *
 * Mirrors [com.depthdiver.LeaderboardService] and [com.depthdiver.CloudSaveService]:
 * the game talks to this interface, never to a store SDK, so swapping in
 * [PlayBillingPurchases] when the Play dependency is available is a one-line
 * change and touches no gameplay code.
 *
 * The local implementation grants without charging. That is deliberate for a
 * build with no store dependency wired up, and it is also a trap for a shipping
 * build: [LocalPurchases.chargingEnabled] exists so a release build can be
 * switched to refuse rather than to hand out cosmetics for nothing.
 */
interface PurchaseService {
    /** True when a real purchase could actually charge the player. */
    val canPurchase: Boolean

    /** Buy [cosmetic]. */
    fun purchase(cosmetic: Cosmetic): PurchaseResult

    /** Ask the store to re-deliver anything already bought, e.g. after reinstall. */
    fun restore(): List<Cosmetic>
}

/**
 * Grants cosmetics for free.
 *
 * This is what runs in a build with no billing dependency, and what the tests
 * run against. [chargingEnabled] is the safety interlock: with it on, every
 * purchase is refused, so a shipping build wired up by mistake cannot give away
 * paid content.
 */
class LocalPurchases(
    private val chargingEnabled: Boolean = false,
    private val owned: MutableSet<String> = mutableSetOf(),
) : PurchaseService {

    override val canPurchase: Boolean get() = chargingEnabled

    override fun purchase(cosmetic: Cosmetic): PurchaseResult {
        if (!chargingEnabled) {
            return PurchaseResult.Failed("no billing backend is wired up")
        }
        if (!owned.add(cosmetic.id)) return PurchaseResult.AlreadyOwned(cosmetic)
        return PurchaseResult.Granted(cosmetic)
    }

    override fun restore(): List<Cosmetic> =
        owned.mapNotNull { Cosmetics.byId(it) }
}

/**
 * What a build should use right now.
 *
 * Swap the instance for [PlayBillingPurchases] once the Play Billing artifact
 * resolves; nothing else has to change.
 */
object Purchases {
    @Volatile
    private var impl: PurchaseService = LocalPurchases()

    val service: PurchaseService get() = impl

    fun install(service: PurchaseService) {
        impl = service
    }
}
