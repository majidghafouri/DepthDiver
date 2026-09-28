package com.depthdiver.mutation

/**
 * The mutation half of a run.
 *
 * Separate from the game on purpose: the deck, the effects and the offer rules
 * are pure and can be tested without a render loop, a GL context or a device.
 * The game owns *when* a pick happens and *what the effects touch*; this owns
 * what the run has picked up and what that adds up to.
 */
class MutationDeck(
    private val pool: List<Mutation> = MutationPool.ALL,
    private val random: java.util.Random = java.util.Random(),
    private val offerCount: Int = 3,
) {

    private val taken = mutableListOf<Mutation>()
    private var pending: List<Mutation> = emptyList()

    val active: List<Mutation> get() = taken
    val activeIds: Set<String> get() = taken.mapTo(mutableSetOf()) { it.id }

    /** Everything the run has picked up, combined. */
    val effects: MutationEffects
        get() = taken.fold(MutationEffects.NONE) { acc, m -> acc + m.effects }

    /** The three on offer, drawn the first time this is asked. */
    fun offers(): List<Mutation> {
        if (pending.isEmpty()) {
            pending = draw(pool, offerCount, activeIds, random)
        }
        return pending
    }

    /** Take the offer at [index]. Returns null if the index is not on offer. */
    fun take(index: Int): Mutation? {
        val offer = offers()
        if (index !in offer.indices) return null
        val chosen = offer[index]
        taken += chosen
        pending = emptyList()
        return chosen
    }

    /** Take by id, for a friend sending a specific build. */
    fun takeId(id: String): Mutation? {
        val m = pool.firstOrNull { it.id == id } ?: return null
        if (taken.any { it.id == id }) return null
        taken += m
        pending = emptyList()
        return m
    }

    fun has(id: String): Boolean = taken.any { it.id == id }

    fun clear() {
        taken.clear()
        pending = emptyList()
    }

    private fun draw(
        source: List<Mutation>,
        count: Int,
        owned: Set<String>,
        random: java.util.Random,
    ): List<Mutation> {
        val chosen = mutableListOf<Mutation>()
        val used = owned.toMutableSet()
        repeat(count) {
            val candidates = source.filter { m ->
                m.id !in used && chosen.none { other ->
                    m.id in other.conflictsWith || other.id in m.conflictsWith
                } && used.none { takenId ->
                    // A mutation that conflicts with something already held is
                    // not offered again.
                    m.id in (source.firstOrNull { it.id == takenId }?.conflictsWith ?: emptySet())
                }
            }
            if (candidates.isEmpty()) return@repeat
            val picked = weighted(candidates, random)
            chosen += picked
            used += picked.id
        }
        return chosen
    }

    private fun weighted(candidates: List<Mutation>, random: java.util.Random): Mutation {
        var roll = random.nextInt(candidates.sumOf { it.rarity.weight }.coerceAtLeast(1))
        for (m in candidates) {
            roll -= m.rarity.weight
            if (roll < 0) return m
        }
        return candidates.last()
    }
}
