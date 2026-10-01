package com.depthdiver.content

/**
 * Where content comes from, and the built-in copy of it.
 *
 * The built-in JSON is a string constant rather than a Kotlin data structure on
 * purpose. If the fallback were a hand-written object and the shipped asset were
 * a separate file, the two would drift the first time someone tuned one of them
 * -- and a silent divergence here means a balance change nobody made. Holding
 * the fallback as the same text the asset is generated from means there is one
 * answer, and a test can check the asset still matches it.
 */
object ContentSource {

    const val ASSET_PATH = "content/content.json"

    /**
     * The canonical content, as JSON.
     *
     * These are the numbers the game shipped with. Changing them here is a real
     * balance change and should go through a deliberate edit, not a convenience.
     */
    val BUILT_IN_JSON: String = """
        {
          "version": 1,
          "tuning": {
            "worldWidthMeters": 40.0,
            "hazardIntervalBaseMin": 1.4,
            "hazardIntervalBaseMax": 2.6,
            "hazardRampGain": 0.9,
            "hazardSaturationMeters": 600.0,
            "hazardIntervalFloor": 0.45,
            "scrollCeilingRatio": 0.88,
            "oxygenDrainGain": 0.55,
            "oxygenSaturationMeters": 500.0,
            "oxygenTankMaxFraction": 0.4,
            "oxygenTankMinGapMeters": 12.0,
            "pickupMinOffsetMeters": 2.0
          },
          "biomes": [
            {
              "id": "SUNLIT_SHALLOWS",
              "minDepth": 0.0,
              "hazardWeights": { "ROCK": 0.45, "MINE": 0.35, "JELLYFISH": 0.20 }
            },
            {
              "id": "TURQUOISE_REEF",
              "minDepth": 60.0,
              "hazardWeights": { "ROCK": 0.30, "MINE": 0.25, "JELLYFISH": 0.25, "ANGLER": 0.20 }
            },
            {
              "id": "MIDNIGHT_ZONE",
              "minDepth": 140.0,
              "hazardWeights": { "ROCK": 0.20, "MINE": 0.20, "JELLYFISH": 0.20, "EEL": 0.20, "ANGLER": 0.20 }
            },
            {
              "id": "ABYSS",
              "minDepth": 260.0,
              "hazardWeights": { "ROCK": 0.15, "MINE": 0.20, "JELLYFISH": 0.15, "EEL": 0.20, "ANGLER": 0.15, "VORTEX": 0.15 }
            },
            {
              "id": "HADAL_TRENCH",
              "minDepth": 420.0,
              "hazardWeights": { "ROCK": 0.10, "MINE": 0.15, "JELLYFISH": 0.10, "EEL": 0.25, "ANGLER": 0.20, "VORTEX": 0.20 }
            }
          ]
        }
    """.trimIndent() + "\n"
}
