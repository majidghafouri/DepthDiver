package com.depthdiver.game

enum class Biome(
    val minDepth: Float,
    val nameKey: String,
    val topRed: Float,
    val topGreen: Float,
    val topBlue: Float,
    val bottomRed: Float,
    val bottomGreen: Float,
    val bottomBlue: Float,
    val hazardMix: Map<HazardKind, Float>,
) {
    SUNLIT_SHALLOWS(
        minDepth = 0f,
        nameKey = "zoneSunlit",
        topRed = 0.05f, topGreen = 0.30f, topBlue = 0.46f,
        bottomRed = 0.02f, bottomGreen = 0.13f, bottomBlue = 0.28f,
        hazardMix = mapOf(
            HazardKind.ROCK to 0.45f,
            HazardKind.MINE to 0.35f,
            HazardKind.JELLYFISH to 0.20f,
        ),
    ),
    TURQUOISE_REEF(
        minDepth = 60f,
        nameKey = "zoneReef",
        topRed = 0.02f, topGreen = 0.22f, topBlue = 0.40f,
        bottomRed = 0.008f, bottomGreen = 0.09f, bottomBlue = 0.20f,
        hazardMix = mapOf(
            HazardKind.ROCK to 0.30f,
            HazardKind.MINE to 0.25f,
            HazardKind.JELLYFISH to 0.25f,
            HazardKind.ANGLER to 0.20f,
        ),
    ),
    MIDNIGHT_ZONE(
        minDepth = 140f,
        nameKey = "zoneMidnight",
        topRed = 0.008f, topGreen = 0.12f, topBlue = 0.19f,
        bottomRed = 0.003f, bottomGreen = 0.04f, bottomBlue = 0.085f,
        hazardMix = mapOf(
            HazardKind.ROCK to 0.20f,
            HazardKind.MINE to 0.20f,
            HazardKind.JELLYFISH to 0.20f,
            HazardKind.EEL to 0.20f,
            HazardKind.ANGLER to 0.20f,
        ),
    ),
    ABYSS(
        minDepth = 260f,
        nameKey = "zoneAbyss",
        topRed = 0.005f, topGreen = 0.055f, topBlue = 0.085f,
        bottomRed = 0.002f, bottomGreen = 0.012f, bottomBlue = 0.03f,
        hazardMix = mapOf(
            HazardKind.ROCK to 0.15f,
            HazardKind.MINE to 0.20f,
            HazardKind.JELLYFISH to 0.15f,
            HazardKind.EEL to 0.20f,
            HazardKind.ANGLER to 0.15f,
            HazardKind.VORTEX to 0.15f,
        ),
    ),
    HADAL_TRENCH(
        minDepth = 420f,
        nameKey = "zoneHadal",
        topRed = 0.004f, topGreen = 0.03f, topBlue = 0.05f,
        bottomRed = 0.001f, bottomGreen = 0.006f, bottomBlue = 0.016f,
        hazardMix = mapOf(
            HazardKind.ROCK to 0.10f,
            HazardKind.MINE to 0.15f,
            HazardKind.JELLYFISH to 0.10f,
            HazardKind.EEL to 0.25f,
            HazardKind.ANGLER to 0.20f,
            HazardKind.VORTEX to 0.20f,
        ),
    );

    fun blendTo(next: Biome, t: Float): WaterColor {
        val k = t.coerceIn(0f, 1f)
        return WaterColor(
            topRed = lerp(topRed, next.topRed, k),
            topGreen = lerp(topGreen, next.topGreen, k),
            topBlue = lerp(topBlue, next.topBlue, k),
            bottomRed = lerp(bottomRed, next.bottomRed, k),
            bottomGreen = lerp(bottomGreen, next.bottomGreen, k),
            bottomBlue = lerp(bottomBlue, next.bottomBlue, k),
        )
    }

    fun color(): WaterColor = WaterColor(topRed, topGreen, topBlue, bottomRed, bottomGreen, bottomBlue)

    companion object {
        fun forDepth(depth: Float): Biome {
            val safeDepth = if (depth.isFinite()) depth else 0f
            var found = entries.first()
            for (biome in entries) {
                if (safeDepth >= biome.minDepth) found = biome else return found
            }
            return found
        }

        fun nextOf(biome: Biome): Biome {
            val index = entries.indexOf(biome)
            return entries.getOrElse(index + 1) { biome }
        }

        /** 0 at the start of a biome, 1 once the next biome is reached. */
        fun progressWithin(depth: Float, biome: Biome): Float {
            val next = nextOf(biome)
            if (next === biome) return 0f
            val span = (next.minDepth - biome.minDepth).coerceAtLeast(1f)
            return ((depth - biome.minDepth) / span).coerceIn(0f, 1f)
        }

        fun roll(mix: Map<HazardKind, Float>, roll: Float): HazardKind {
            val total = mix.values.sum()
            if (total <= 0f) return HazardKind.ROCK
            var cursor = roll.coerceIn(0f, 0.999999f) * total
            for ((kind, weight) in mix) {
                cursor -= weight
                if (cursor < 0f) return kind
            }
            return mix.keys.lastOrNull() ?: HazardKind.ROCK
        }
    }
}

enum class HazardKind { ROCK, MINE, JELLYFISH, EEL, ANGLER, VORTEX, SHARK }

data class WaterColor(
    val topRed: Float,
    val topGreen: Float,
    val topBlue: Float,
    val bottomRed: Float,
    val bottomGreen: Float,
    val bottomBlue: Float,
)

private fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t
