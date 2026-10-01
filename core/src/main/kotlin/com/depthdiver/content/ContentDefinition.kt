package com.depthdiver.content

/**
 * The content the game generates from, as data.
 *
 * Only generation lives here. Biome *colours* stay in code, because they are
 * presentation and pulling them through the loader would mean a malformed asset
 * could change how deep the sea looks rather than how it plays.
 */
data class ContentDefinition(
    val biomes: List<BiomeDefinition>,
    val tuning: Tuning,
    /** Problems found while loading. Empty when the asset parsed cleanly. */
    val problems: List<String> = emptyList(),
    /** True when the built-in values are in use because the asset was unusable. */
    val isFallback: Boolean = false,
) {
    /**
     * How the game is told about a bad content file.
     *
     * Falling back silently would mean a typo in an asset looks like a balance
     * change nobody made, so the caller is expected to log these.
     */
    val hasProblems: Boolean get() = problems.isNotEmpty()

    companion object {
        val BUILT_IN: ContentDefinition by lazy { ContentLoader.load(ContentSource.BUILT_IN_JSON) }
    }
}

data class BiomeDefinition(
    val id: String,
    val minDepth: Float,
    /**
     * Relative spawn weights. Not required to sum to 1: they are normalised on
     * use, so an author can rebalance by eye without doing arithmetic, and a
     * weight of 0 is a legitimate way to remove a hazard from one zone.
     */
    val hazardWeights: Map<String, Float>,
)

/**
 * The numbers that decide what the game feels like.
 *
 * These were constants scattered across three files, which is a reasonable way
 * to arrive at a tuning and a bad way to keep one. Moving them here is what
 * makes "make it easier" an edit rather than an archaeology.
 */
data class Tuning(
    val worldWidthMeters: Float,
    val hazardIntervalBaseMin: Float,
    val hazardIntervalBaseMax: Float,
    val hazardRampGain: Float,
    val hazardSaturationMeters: Float,
    /** A floor under the spawn interval, so depth can never make it impossible. */
    val hazardIntervalFloor: Float,
    val scrollCeilingRatio: Float,
    val oxygenDrainGain: Float,
    val oxygenSaturationMeters: Float,
    /** Force an oxygen tank below this fraction, if one is allowed. */
    val oxygenTankMaxFraction: Float,
    /** And no closer than this to the last one. */
    val oxygenTankMinGapMeters: Float,
    /** A pickup no closer than this to the previous one, in metres. */
    val pickupMinOffsetMeters: Float,
) {
    companion object {
        /**
         * The values the game ran before any of this was data.
         *
         * [ContentSource.BUILT_IN_JSON] holds these same numbers, and a test
         * asserts the two agree, so there is one answer rather than two that
         * drift.
         */
        val DEFAULT = Tuning(
            worldWidthMeters = 40f,
            hazardIntervalBaseMin = 1.4f,
            hazardIntervalBaseMax = 2.6f,
            hazardRampGain = 0.9f,
            hazardSaturationMeters = 600f,
            hazardIntervalFloor = 0.45f,
            scrollCeilingRatio = 0.88f,
            oxygenDrainGain = 0.55f,
            oxygenSaturationMeters = 500f,
            oxygenTankMaxFraction = 0.4f,
            oxygenTankMinGapMeters = 12f,
            pickupMinOffsetMeters = 2f,
        )
    }
}
