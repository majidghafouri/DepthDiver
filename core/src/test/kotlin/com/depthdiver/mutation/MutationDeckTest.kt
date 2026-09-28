package com.depthdiver.mutation

import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Mutations, and the two properties that make them a decision rather than a
 * discount: every one is a trade, and they compound rather than cancel.
 */
class MutationDeckTest {

    private fun deck(random: Long = 1L) = MutationDeck(random = Random(random))

    @Test
    fun aFreshDeckHasNothing() {
        val d = deck()
        assertTrue(d.active.isEmpty())
        assertEquals(MutationEffects.NONE, d.effects)
    }

    @Test
    fun threeOffersArePresented() {
        val offers = deck().offers()
        assertEquals(3, offers.size)
    }

    @Test
    fun offersAreDistinct() {
        val offers = deck().offers()
        assertEquals(offers.size, offers.map { it.id }.toSet().size)
    }

    @Test
    fun offersAreStableUntilOneIsTaken() {
        // The screen is read before it is answered, so re-reading it must not
        // reroll what the player is looking at.
        val d = deck()
        assertEquals(d.offers().map { it.id }, d.offers().map { it.id })
    }

    @Test
    fun takingAnOfferAppliesIt() {
        val d = deck()
        val taken = d.take(0)
        assertNotNull(taken)
        assertEquals(taken.id, d.active.single().id)
        assertTrue(d.has(taken.id))
    }

    @Test
    fun takingClearsTheOfferAndRerollsNextTime() {
        val d = deck()
        val first = d.take(0)!!
        val second = d.offers()
        assertFalse(second.any { it.id == first.id }, "a taken mutation must not be offered again")
    }

    @Test
    fun takingOutOfRangeIsRefused() {
        assertNull(deck().take(7))
    }

    @Test
    fun aConflictingMutationIsNotOfferedAfterItsPartner() {
        val d = deck()
        val first = d.take(0)!!
        for (offer in d.offers()) {
            val blocked = first.conflictsWith
            if (blocked.isNotEmpty()) {
                assertFalse(
                    offer.id in blocked,
                    "offered ${offer.id} which conflicts with ${first.id}",
                )
            }
        }
    }

    @Test
    fun effectsCompoundAcrossTakes() {
        val d = deck()
        val first = d.take(0)!!
        val after1 = d.effects
        val second = d.take(0)!!
        val after2 = d.effects
        // Multiplication, so a second pick never undoes the first.
        assertTrue(after2.oxygenDrain >= after1.oxygenDrain)
        assertTrue(after2.oxygenDrain > 0f)
        assertEquals(
            after1.oxygenDrain * second.effects.oxygenDrain,
            after2.oxygenDrain,
            1e-6f,
        )
        assertTrue(d.active.size == 2, "both should be held")
        assertNotNull(first)
    }

    @Test
    fun everyMutationIsATradeAndNotAPureDiscount() {
        // A mutation that only gives is not a choice. Each must raise at least
        // one number and worsen at least one other.
        MutationPool.ALL.forEach { m ->
            val e = m.effects
            val given = e.pearlValue > 1f || e.scoreGain > 1f || e.pickupInterval < 1f ||
                e.oxygenDrain < 1f || e.hazardInterval > 1f || e.startingOxygen > 0f ||
                e.harmless.isNotEmpty()
            val cost = e.pearlValue < 1f || e.scoreGain < 1f || e.oxygenDrain > 1f ||
                e.hazardInterval < 1f || e.playerSpeed < 1f || e.currentPush > 1f ||
                e.scrollSpeed > 1f || e.pickupInterval > 1f
            assertTrue(given, "${m.id} gives nothing")
            assertTrue(cost, "${m.id} costs nothing")
        }
    }

    @Test
    fun noMutationChangesScoreWithoutAlsoMakingTheRunHarder() {
        // The score multiplier is the easiest thing to abuse: a score boost with
        // no downside is just a bigger number.
        MutationPool.ALL.filter { it.effects.scoreGain > 1f }.forEach { m ->
            val e = m.effects
            val harder = e.oxygenDrain > 1f || e.hazardInterval < 1f ||
                e.playerSpeed < 1f || e.pickupInterval > 1f
            assertTrue(harder, "${m.id} multiplies score with no downside")
        }
    }

    @Test
    fun theHarshestMultiplierIsStillBounded() {
        // Stacking is allowed, but a run of them must not produce a number the
        // rest of the game cannot render or reason about.
        var effects = MutationEffects.NONE
        repeat(8) { effects += MutationPool.ALL.first().effects }
        assertTrue(effects.oxygenDrain.isFinite(), "oxygen multiplier ran away")
        assertTrue(effects.oxygenDrain < 100f, "oxygenDrain ${effects.oxygenDrain} is absurd")
        assertTrue(effects.scrollSpeed.isFinite())
        assertTrue(effects.pearlValue.isFinite())
    }

    @Test
    fun clearResetsTheRun() {
        val d = deck()
        d.take(0)
        d.clear()
        assertTrue(d.active.isEmpty())
        assertEquals(MutationEffects.NONE, d.effects)
    }

    @Test
    fun everyMutationHasAnIdAndLooksUpable() {
        MutationPool.ALL.forEach { m ->
            assertNotNull(MutationPool.byId(m.id), "${m.id} is not in the catalog index")
        }
        assertNull(MutationPool.byId("nope"))
    }

    @Test
    fun theCatalogIsBigEnoughToFeelVaried() {
        // With 3 offered from a pool, too few mutations means players see the
        // same three every run.
        assertTrue(MutationPool.ALL.size >= 10, "only ${MutationPool.ALL.size} mutations")
    }

    @Test
    fun commonMutationsShowUpMoreOftenThanEpicOnes() {
        // A rarity that does not change the odds is just a label.
        val seen = mutableMapOf<MutationRarity, Int>()
        repeat(3000) {
            val offered = deck(it.toLong()).offers()
            offered.forEach { m -> seen[m.rarity] = (seen[m.rarity] ?: 0) + 1 }
        }
        val common = seen[MutationRarity.COMMON] ?: 0
        val epic = seen[MutationRarity.EPIC] ?: 0
        assertTrue(common > epic, "COMMON ($common) should outnumber EPIC ($epic)")
    }
}
