package com.depthdiver.cosmetic

/**
 * A cosmetic the player can own.
 *
 * Cosmetics are appearance only. None of them touch oxygen, speed, scoring or
 * pearl value, so buying one cannot make a run easier -- which is both the rule
 * the app stores enforce and the thing that keeps this out of trouble with the
 * platforms that police it. A cosmetic that improved a run would be a gameplay
 * item wearing a hat.
 */
data class Cosmetic(
    val id: String,
    val name: String,
    val bodyColor: Int,
    val accentColor: Int,
    /** Free cosmetics are unlocked from the first run; the rest are bought. */
    val free: Boolean = false,
    /** Store product id, or null for a free one. */
    val productId: String? = null,
)

/**
 * The catalog.
 *
 * The first entry is deliberately the colour the diver already had, so an
 * install that has never bought anything looks exactly like it did before this
 * existed. A cosmetic system that silently reskins the game on update reads as a
 * bug.
 */
object Cosmetics {

    val DEFAULT: Cosmetic = Cosmetic(
        id = "diver_default",
        name = "Diver",
        bodyColor = 0x3399FF,
        accentColor = 0x1A73B3,
        free = true,
    )

    val CATALOG: List<Cosmetic> = listOf(
        DEFAULT,
        Cosmetic("diver_coral", "Coral Diver", 0xFF8A70, 0xC4553A),
        Cosmetic("diver_kelp", "Kelp Diver", 0x8FD694, 0x3F8F55),
        Cosmetic("diver_ember", "Ember Diver", 0xFFC46B, 0xB87333),
        Cosmetic("diver_frost", "Frost Diver", 0xBFE9FF, 0x5C9DBF),
        Cosmetic("diver_moth", "Moth Diver", 0xE3C7F0, 0x8A6BA8),
    )

    private val byId = CATALOG.associateBy { it.id }

    fun byId(id: String): Cosmetic? = byId[id]

    fun purchasable(): List<Cosmetic> = CATALOG.filter { !it.free }

    fun count(): Int = CATALOG.size

    /** Catalog position, used to keep a next/previous arrow in the shop. */
    fun indexOf(id: String): Int = CATALOG.indexOfFirst { it.id == id }.coerceAtLeast(0)

    fun next(id: String): Cosmetic = CATALOG[(indexOf(id) + 1) % CATALOG.size]

    fun previous(id: String): Cosmetic = CATALOG[(indexOf(id) - 1 + CATALOG.size) % CATALOG.size]

    /**
     * Every id that is legitimately obtainable for free, derived from the
     * catalog rather than hand-listed, so a free cosmetic cannot be added
     * without this knowing about it.
     */
    fun freeIds(): Set<String> = CATALOG.filter { it.free }.map { it.id }.toSet()
}

/** Cosmetic ids come from a catalog that cannot change between releases, so an
 *  unknown id in prefs has to resolve to something drawable rather than crash. */
internal fun cosmeticOrDefault(id: String?): Cosmetic =
    (id?.let { Cosmetics.byId(it) }) ?: Cosmetics.DEFAULT
