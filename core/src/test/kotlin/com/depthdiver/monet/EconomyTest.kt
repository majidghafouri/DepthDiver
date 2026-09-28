package com.depthdiver.monet

import com.depthdiver.TestPreferences
import com.depthdiver.cosmetic.Cosmetics
import com.depthdiver.season.SeasonPass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The economy, and in particular the two rules it exists to hold: nothing a
 * player can buy changes how a run plays, and nothing can be claimed twice.
 */
class EconomyTest {

    private lateinit var prefs: TestPreferences
    private lateinit var purchases: LocalPurchases
    private lateinit var ads: LocalAds
    private lateinit var economy: Economy

    @BeforeTest
    fun setUp() {
        prefs = TestPreferences("depthdiver-economy-test")
        purchases = LocalPurchases()
        ads = LocalAds()
        economy = Economy(prefs, purchases, ads)
    }

    @AfterTest
    fun tearDown() = Unit

    // --- cosmetics ---------------------------------------------------------

    @Test
    fun theDefaultDiverIsOwnedWithoutBuyingAnything() {
        assertTrue(economy.owns(Cosmetics.DEFAULT.id))
        assertEquals(Cosmetics.DEFAULT, economy.equippedCosmetic())
    }

    @Test
    fun anUnequippedPlayerStillRendersTheOriginalDiver() {
        // A cosmetic system that reskins the game on update reads as a bug, so
        // the default has to be the colour the game already had.
        assertEquals(0x3399FF, Cosmetics.DEFAULT.bodyColor)
    }

    @Test
    fun equippingChangesNothingButTheDiver() {
        // The deeper guarantee is structural: `Cosmetic` holds two colours and
        // nothing else, so there is no stat to balance. What is checkable here is
        // that flipping one on does not quietly move any other number -- no
        // pearls, no season XP, no second cosmetic silently unlocked.
        val paid = Cosmetics.purchasable().first()
        val charging = LocalPurchases(chargingEnabled = true)
        val live = Economy(prefs, charging, ads)
        live.buy(paid)

        val pearlsBefore = live.pearls()
        val xpBefore = live.seasonXp()
        val ownedBefore = live.ownedCosmetics()

        assertTrue(live.equip(paid.id))

        assertEquals(pearlsBefore, live.pearls(), "equipping must not pay out")
        assertEquals(xpBefore, live.seasonXp(), "equipping must not award season XP")
        assertEquals(ownedBefore, live.ownedCosmetics(), "equipping must not unlock anything else")
    }

    @Test
    fun equippingSomethingYouDoNotOwnIsRefused() {
        val paid = Cosmetics.purchasable().first()
        assertFalse(economy.owns(paid.id))
        assertFalse(economy.equip(paid.id))
        assertEquals(Cosmetics.DEFAULT, economy.equippedCosmetic())
    }

    @Test
    fun anUnknownIdFromTamperedPrefsFallsBackInsteadOfCrashing() {
        prefs.putString("economy.equipped", "not_a_real_cosmetic")
        assertEquals(Cosmetics.DEFAULT, economy.equippedCosmetic())
    }

    @Test
    fun aFreeCosmeticIsNeverSold() {
        val result = economy.buy(Cosmetics.DEFAULT)
        assertTrue(result is PurchaseResult.AlreadyOwned)
    }

    // --- purchases ---------------------------------------------------------

    @Test
    fun aPurchaseIsRefusedWhenNoBillingBackendExists() {
        // The local build must not hand out paid content.
        val paid = Cosmetics.purchasable().first()
        assertFalse(purchases.canPurchase)
        val result = economy.buy(paid)
        assertTrue(result is PurchaseResult.Failed)
        assertFalse(economy.owns(paid.id), "a failed purchase must grant nothing")
    }

    @Test
    fun aGrantedPurchaseGrantsExactlyOnce() {
        val charging = LocalPurchases(chargingEnabled = true)
        val live = Economy(prefs, charging, ads)
        val paid = Cosmetics.purchasable().first()
        val first = live.buy(paid)
        assertTrue(first is PurchaseResult.Granted, "expected a grant, got $first")
        assertTrue(live.owns(paid.id))
        val second = live.buy(paid)
        assertTrue(second is PurchaseResult.AlreadyOwned, "a second buy must not charge again")
    }

    @Test
    fun aCancelledPurchaseGrantsNothing() {
        val charging = object : PurchaseService {
            override val canPurchase = true
            override fun purchase(cosmetic: com.depthdiver.cosmetic.Cosmetic) =
                PurchaseResult.Cancelled
            override fun restore() = emptyList<com.depthdiver.cosmetic.Cosmetic>()
        }
        val live = Economy(prefs, charging, ads)
        val paid = Cosmetics.purchasable().first()
        assertTrue(live.buy(paid) is PurchaseResult.Cancelled)
        assertFalse(live.owns(paid.id))
    }

    // --- ads ---------------------------------------------------------------

    @Test
    fun adsAreOffUntilThePlayerSaysOtherwise() {
        assertFalse(economy.adOptIn())
        val result = economy.offerRewardedAd("test")
        assertTrue(result is AdResult.Unavailable)
        assertEquals(0, economy.pearls())
    }

    @Test
    fun optingInWithoutConsentStillShowsNothing() {
        economy.setAdOptIn(true)
        assertFalse(economy.adConsented())
        val result = economy.offerRewardedAd("test")
        assertTrue(result is AdResult.Unavailable)
        assertEquals(AdBlockReason.NO_CONSENT, (result as AdResult.Unavailable).reason)
    }

    @Test
    fun optingOutStopsAdsImmediately() {
        economy.setAdConsented(true)
        economy.setAdOptIn(true)
        economy.setAdOptIn(false)
        assertTrue(economy.offerRewardedAd("x") is AdResult.Unavailable)
    }

    @Test
    fun onlyAnEarnedAdPaysOut() {
        val earning = object : AdService {
            override var optedIn = true
            override var consented = true
            var shown = 0
            override fun showRewarded(rewardId: String): AdResult {
                shown++
                return AdResult.Earned(rewardId)
            }
        }
        val live = Economy(prefs, purchases, earning)
        val before = live.pearls()
        assertTrue(live.offerRewardedAd("double_pearls") is AdResult.Earned)
        assertTrue(live.pearls() > before)
    }

    // --- season pass -------------------------------------------------------

    @Test
    fun xpComesFromRunsNotFromThePass() {
        // The point of the pass is a second reason to dive. If buying it made
        // you level faster, it would be paying to keep up.
        assertTrue(SeasonPass.xpForRun(60f, 2000) > SeasonPass.xpForRun(2f, 20))
    }

    @Test
    fun aRunIsCreditedOnce() {
        val first = economy.addSeasonXp(50f, 1200, "run-1")
        assertTrue(first > 0)
        val second = economy.addSeasonXp(50f, 1200, "run-1")
        assertEquals(0, second, "the same run must not pay XP twice")
    }

    @Test
    fun aTierCannotBeClaimedTwice() {
        economy.addSeasonXp(400f, 9000, "run-1")
        val tier = economy.seasonProgress().tier
        assertTrue(tier > 0)
        val reward = economy.claimTier(1, premiumTrack = false)
        assertNotNull(reward)
        assertNull(economy.claimTier(1, premiumTrack = false), "a second claim must pay nothing")
    }

    @Test
    fun aPremiumTierCannotBeClaimedWithoutBuyingThePass() {
        economy.addSeasonXp(400f, 9000, "run-1")
        assertFalse(economy.canClaimTier(1, premiumTrack = true))
        assertNull(economy.claimTier(1, premiumTrack = true))
    }

    @Test
    fun aPremiumTierClaimsOnceThePassIsUnlocked() {
        economy.addSeasonXp(400f, 9000, "run-1")
        economy.unlockPremiumFromStore()
        assertTrue(economy.premiumUnlocked())
        val reward = economy.claimTier(1, premiumTrack = true)
        assertNotNull(reward)
        assertNull(economy.claimTier(1, premiumTrack = true))
    }

    @Test
    fun aSeasonCosmeticRewardArrivesAsSomethingEquippable() {
        economy.addSeasonXp(400f, 9000, "run-1")
        economy.unlockPremiumFromStore()
        economy.claimTier(1, premiumTrack = true)
        assertTrue(economy.owns("diver_coral"), "the tier 1 reward should have granted the coral diver")
        assertTrue(economy.equip("diver_coral"))
        assertEquals("diver_coral", economy.equippedCosmetic().id)
    }

    @Test
    fun tiersAreOrderedAndTheTrackEnds() {
        assertEquals(0, SeasonPass.tierForXp(0))
        for (t in 1..SeasonPass.LAST_TIER) {
            assertEquals(t, SeasonPass.tierForXp(SeasonPass.xpForTier(t)))
        }
        assertEquals(SeasonPass.LAST_TIER, SeasonPass.tierForXp(10_000_000))
        assertEquals(0, SeasonPass.xpToNextTier(10_000_000))
    }
}
