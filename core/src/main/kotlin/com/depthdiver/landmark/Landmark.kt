package com.depthdiver.landmark

/**
 * An authored thing at a fixed depth.
 *
 * The world is procedural, which is why it is infinite and also why it is
 * forgettable: every run is the same arrangement logic, so nobody has anything
 * to say about their hundredth dive beyond the number. Landmarks are the
 * opposite on purpose -- a handful of fixed, hand-placed places, so that a
 * player has something to *reach* rather than only a score to beat, and so that
 * "I finally got to the wreck" is a sentence anyone would say out loud.
 *
 * They are set dressing, not obstacles. Nothing about a landmark makes a run
 * harder, because the game this is makes no sense otherwise.
 */
data class Landmark(
    val id: String,
    val nameKey: String,
    val depthMeters: Float,
    /** World x of the centre, in metres, in the 0..40m channel. */
    val centerXMeters: Float,
    val widthMeters: Float,
    val heightMeters: Float,
    val kind: Kind,
    /** Pearls for finding it the first time. Zero is allowed. */
    val reward: Int = 0,
) {
    enum class Kind {
        /** A hull lying on its side. */
        WRECK,

        /** A skeleton, ribs curving out of the silt. */
        WHALE_FALL,

        /** Two stags meeting overhead. */
        ARCH,

        /** A chimney with something living on it. */
        VENT,

        /** A stand of kelp, drawn as a screen of strands. */
        KELP,
    }
}

object Landmarks {

    /**
     * Spread through the biomes, one per zone plus one deep.
     *
     * Depths are placed just *inside* each biome boundary rather than on it, so
     * arriving at a landmark and the zone banner happen as one moment instead of
     * two that fight each other.
     */
    val ALL: List<Landmark> = listOf(
        Landmark(
            id = "kelp_shallows", nameKey = "lmKelp", depthMeters = 22f,
            centerXMeters = 9f, widthMeters = 9f, heightMeters = 7f,
            kind = Landmark.Kind.KELP, reward = 10,
        ),
        Landmark(
            id = "reef_arch", nameKey = "lmArch", depthMeters = 68f,
            centerXMeters = 30f, widthMeters = 10f, heightMeters = 6f,
            kind = Landmark.Kind.ARCH, reward = 15,
        ),
        Landmark(
            id = "old_wreck", nameKey = "lmWreck", depthMeters = 146f,
            centerXMeters = 14f, widthMeters = 12f, heightMeters = 5f,
            kind = Landmark.Kind.WRECK, reward = 30,
        ),
        Landmark(
            id = "whale_fall", nameKey = "lmWhale", depthMeters = 268f,
            centerXMeters = 25f, widthMeters = 14f, heightMeters = 5f,
            kind = Landmark.Kind.WHALE_FALL, reward = 40,
        ),
        Landmark(
            id = "deep_vent", nameKey = "lmVent", depthMeters = 430f,
            centerXMeters = 19f, widthMeters = 7f, heightMeters = 8f,
            kind = Landmark.Kind.VENT, reward = 60,
        ),
    )

    fun byId(id: String): Landmark? = ALL.firstOrNull { it.id == id }

    /**
     * The landmarks whose depth has been reached or passed.
     *
     * Counted rather than "the one nearest", because a fast descent can cross
     * several between frames and a player should not lose one to a dropped
     * update.
     */
    fun reached(depthMeters: Float): List<Landmark> = ALL.filter { depthMeters >= it.depthMeters }

    /** The next one below [depthMeters], for the profile's "next landmark" line. */
    fun nextBelow(depthMeters: Float): Landmark? = ALL.firstOrNull { it.depthMeters > depthMeters }

    /**
     * How far the player is from the next one, in metres, or null at the bottom.
     *
     * Null rather than zero when there is nothing left, so the profile can say
     * "every landmark found" instead of claiming there is one at 0m.
     */
    fun depthToNext(depthMeters: Float): Float? =
        nextBelow(depthMeters)?.let { it.depthMeters - depthMeters }
}
