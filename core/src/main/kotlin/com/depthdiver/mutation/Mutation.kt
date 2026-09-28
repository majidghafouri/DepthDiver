package com.depthdiver.mutation

import com.depthdiver.entity.Hazard

/**
 * The multiplicative knobs a mutation is allowed to turn.
 *
 * Kept as one value rather than a bag of fields on the game, because that makes
 * the important property checkable: mutations can only ever scale these numbers,
 * so none of them can introduce a new mechanic that has not been played through
 * and balanced. [combine] is the only way they combine, and it is pure
 * multiplication, so ten mild mutations compound rather than override.
 */
data class MutationEffects(
    val oxygenDrain: Float = 1f,
    val scrollSpeed: Float = 1f,
    val playerSpeed: Float = 1f,
    val hazardInterval: Float = 1f,
    val pickupInterval: Float = 1f,
    val pearlValue: Float = 1f,
    val scoreGain: Float = 1f,
    val currentPush: Float = 1f,
    /** Hazards that deal no contact damage. */
    val harmless: Set<HazardFamily> = emptySet(),
    /** Extra air at the start of a run, in the same units as max oxygen. */
    val startingOxygen: Float = 0f,
) {
    operator fun plus(other: MutationEffects): MutationEffects = MutationEffects(
        oxygenDrain = oxygenDrain * other.oxygenDrain,
        scrollSpeed = scrollSpeed * other.scrollSpeed,
        playerSpeed = playerSpeed * other.playerSpeed,
        hazardInterval = hazardInterval * other.hazardInterval,
        pickupInterval = pickupInterval * other.pickupInterval,
        pearlValue = pearlValue * other.pearlValue,
        scoreGain = scoreGain * other.scoreGain,
        currentPush = currentPush * other.currentPush,
        harmless = harmless + other.harmless,
        startingOxygen = startingOxygen + other.startingOxygen,
    )

    /** Neutral effects, so callers never need a null. */
    companion object {
        val NONE = MutationEffects()
    }
}

/** Hazard families a mutation can spare the player from. */
enum class HazardFamily {
    JELLYFISH,
    MINE,
    ANGLER,
    VORTEX,
    ROCK,
    EEL,
    SHARK,
}

enum class MutationRarity(val weight: Int) {
    /** Shows up often. Small, safe trade-offs. */
    COMMON(60),

    /** Needs the right run to want. */
    RARE(25),

    /** Run-defining, and deliberately awkward to stack. */
    EPIC(15),
}

data class Mutation(
    val id: String,
    val nameKey: String,
    val descriptionKey: String,
    val rarity: MutationRarity,
    val effects: MutationEffects,
    /** Mutations that cannot appear together with this one. */
    val conflictsWith: Set<String> = emptySet(),
)

/**
 * The catalog.
 *
 * Every entry is a trade: something given up for something gained. A mutation
 * that is purely a gain does not make a decision, it just makes the run easier,
 * and the reason the upgrade shop is dull is that it is a wall of those.
 */
object MutationPool {

    val ALL: List<Mutation> = listOf(
        // --- trade speed for reward -----------------------------------------
        Mutation(
            "greedy", "mutationGreedy", "mutationGreedyDesc", MutationRarity.RARE,
            MutationEffects(pearlValue = 2f, oxygenDrain = 1.35f),
            conflictsWith = setOf("cautious"),
        ),
        Mutation(
            "cautious", "mutationCautious", "mutationCautiousDesc", MutationRarity.RARE,
            MutationEffects(pearlValue = 0.7f, oxygenDrain = 0.7f),
            conflictsWith = setOf("greedy"),
        ),
        Mutation(
            "bargain", "mutationBargain", "mutationBargainDesc", MutationRarity.COMMON,
            MutationEffects(pearlValue = 1.5f, scrollSpeed = 1.25f),
        ),

        // --- trade survivability for air ------------------------------------
        Mutation(
            "ironLungs", "mutationIronLungs", "mutationIronLungsDesc", MutationRarity.COMMON,
            MutationEffects(oxygenDrain = 0.75f, playerSpeed = 0.9f),
        ),
        Mutation(
            "secondWind", "mutationSecondWind", "mutationSecondWindDesc", MutationRarity.RARE,
            MutationEffects(startingOxygen = 0.35f, scoreGain = 0.8f),
        ),
        Mutation(
            "deepLungs", "mutationDeepLungs", "mutationDeepLungsDesc", MutationRarity.EPIC,
            MutationEffects(oxygenDrain = 0.55f, scrollSpeed = 1.3f),
        ),

        // --- trade space for space ------------------------------------------
        Mutation(
            "clouds", "mutationClouds", "mutationCloudsDesc", MutationRarity.RARE,
            MutationEffects(pickupInterval = 0.45f, oxygenDrain = 1.2f),
        ),
        Mutation(
            "openWater", "mutationOpenWater", "mutationOpenWaterDesc", MutationRarity.RARE,
            MutationEffects(hazardInterval = 1.7f, scoreGain = 0.75f),
        ),
        Mutation(
            // Was hazards closer and currents stronger, which the trade test
            // correctly rejected: both halves are worse, so taking it was not a
            // choice. The score is what is being bought with the extra difficulty.
            "thickWater", "mutationThickWater", "mutationThickWaterDesc", MutationRarity.COMMON,
            MutationEffects(hazardInterval = 0.7f, currentPush = 1.5f, scoreGain = 1.4f),
        ),

        // --- survivability trades -------------------------------------------
        Mutation(
            // The trade test caught this one too: immunity with no cost is just
            // a discount. Phasing through rock is slow, and that is the price.
            "ghostly", "mutationGhostly", "mutationGhostlyDesc", MutationRarity.RARE,
            MutationEffects(
                harmless = setOf(HazardFamily.MINE, HazardFamily.ROCK),
                playerSpeed = 0.88f,
            ),
            conflictsWith = setOf("jellyproof"),
        ),
        Mutation(
            "jellyproof", "mutationJellyproof", "mutationJellyproofDesc", MutationRarity.COMMON,
            MutationEffects(harmless = setOf(HazardFamily.JELLYFISH), playerSpeed = 0.92f),
        ),
        Mutation(
            "gilded", "mutationGilded", "mutationGildedDesc", MutationRarity.EPIC,
            MutationEffects(pickupInterval = 0.4f, scrollSpeed = 1.45f),
        ),
        Mutation(
            "nomad", "mutationNomad", "mutationNomadDesc", MutationRarity.COMMON,
            MutationEffects(scoreGain = 1.35f, oxygenDrain = 1.15f),
        ),
        Mutation(
            "drift", "mutationDrift", "mutationDriftDesc", MutationRarity.RARE,
            MutationEffects(currentPush = 2.2f, oxygenDrain = 0.8f),
        ),
    )

    fun byId(id: String): Mutation? = ALL.firstOrNull { it.id == id }

    /**
     * Draw [count] distinct offers, never one already taken and never a
     * conflict of one already taken.
     *
     * Weighted by rarity, and the offered set is checked for internal conflicts
     * too -- offering a mutually exclusive pair as two of the three choices would
     * be a decision that has no right answer.
     */
    fun offers(
        count: Int,
        owned: Set<String> = emptySet(),
        random: java.util.Random = java.util.Random(),
    ): List<Mutation> {
        val chosen = mutableListOf<Mutation>()
        val takenIds = owned.toMutableSet()
        repeat(count) {
            val candidates = ALL.filter { m ->
                m.id !in takenIds &&
                    takenIds.none { taken -> m.id in (byId(taken)?.conflictsWith ?: emptySet()) } &&
                    chosen.none { m.id in it.conflictsWith || it.id in m.conflictsWith }
            }
            if (candidates.isEmpty()) return@repeat
            chosen += weightedPick(candidates, random)
            takenIds += chosen.last().id
        }
        return chosen
    }

    private fun weightedPick(candidates: List<Mutation>, random: java.util.Random): Mutation {
        val total = candidates.sumOf { it.rarity.weight }
        var roll = random.nextInt(total.coerceAtLeast(1))
        for (m in candidates) {
            roll -= m.rarity.weight
            if (roll < 0) return m
        }
        return candidates.last()
    }
}

/** One row on the pick-one-of-three screen: already localized, never re-read. */
data class MutationOption(
    val id: String,
    val name: String,
    val description: String,
    val rarity: MutationRarity,
)
