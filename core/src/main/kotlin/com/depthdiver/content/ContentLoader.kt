package com.depthdiver.content

import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue
import kotlin.math.abs

/**
 * Reads content JSON, or falls back to the built-in copy.
 *
 * The rule is that a bad content file must never cost anyone a playable build.
 * So every problem is collected rather than thrown, and if anything at all is
 * wrong the game uses [ContentSource.BUILT_IN_JSON] and reports why. The
 * alternative -- trusting the asset -- means a missing comma in a JSON file is a
 * crash on a device, which is a bad trade for the convenience of tuning in a
 * text editor.
 */
object ContentLoader {

    /** The game falls back rather than half-loading when it hits this many. */
    private const val MAX_PROBLEMS = 20

    /**
     * Load for the game: never fails, falls back and explains.
     *
     * A bad content file must not cost anyone a playable build, so this returns
     * something runnable no matter what it is handed.
     */
    fun load(json: String?): ContentDefinition {
        val parsed = parse(json)
        if (!parsed.hasProblems) return parsed
        val builtIn = parse(ContentSource.BUILT_IN_JSON)
        return ContentDefinition(
            biomes = builtIn.biomes,
            tuning = builtIn.tuning,
            problems = parsed.problems,
            isFallback = true,
        )
    }

    /**
     * Parse for a tool, without the fallback.
     *
     * The important difference from [load]: this does not paper over a broken
     * file. An editor that quietly replaced someone's content with built-in
     * values the moment they typo'd a key would destroy their work, so the
     * authoring path gets the truth and the game gets the safety net.
     */
    fun parse(json: String?): ContentDefinition {
        if (json.isNullOrBlank()) {
            return broken("content file was missing or empty")
        }
        return try {
            parseRoot(JsonReader().parse(json))
        } catch (e: Exception) {
            broken("content file is not valid JSON: ${e.message}")
        }
    }

    /** Parse, collecting problems rather than acting on them. */
    private fun parseRoot(root: JsonValue): ContentDefinition {
        val problems = mutableListOf<String>()

        if (!root.isObject()) {
            return broken("content file's root is not an object")
        }
        if (root.getInt("version", 0) != 1) {
            problems += "unsupported content version ${root.getInt("version", 0)}, expected 1"
        }

        val tuning = readTuning(root.get("tuning"), problems)
        val biomes = readBiomes(root.get("biomes"), problems)

        if (problems.isEmpty()) {
            return ContentDefinition(biomes = biomes, tuning = tuning, problems = emptyList())
        }
        return broken(problems)
    }

    /** A definition carrying problems, with no usable content of its own. */
    private fun broken(reason: String): ContentDefinition = broken(listOf(reason))

    private fun broken(reasons: List<String>): ContentDefinition = ContentDefinition(
        biomes = emptyList(),
        tuning = Tuning.DEFAULT,
        problems = reasons.take(MAX_PROBLEMS),
        isFallback = false,
    )

    private fun readTuning(node: JsonValue?, problems: MutableList<String>): Tuning {
        if (node == null || !node.isObject()) {
            problems += "missing \"tuning\" object"
            return Tuning.DEFAULT
        }
        val d = Tuning.DEFAULT
        return Tuning(
            worldWidthMeters = positive(node, "worldWidthMeters", d.worldWidthMeters, problems),
            hazardIntervalBaseMin = positive(node, "hazardIntervalBaseMin", d.hazardIntervalBaseMin, problems),
            hazardIntervalBaseMax = positive(node, "hazardIntervalBaseMax", d.hazardIntervalBaseMax, problems),
            hazardRampGain = positive(node, "hazardRampGain", d.hazardRampGain, problems),
            hazardSaturationMeters = positive(node, "hazardSaturationMeters", d.hazardSaturationMeters, problems),
            hazardIntervalFloor = positive(node, "hazardIntervalFloor", d.hazardIntervalFloor, problems),
            scrollCeilingRatio = ratio(node, "scrollCeilingRatio", d.scrollCeilingRatio, problems),
            oxygenDrainGain = positive(node, "oxygenDrainGain", d.oxygenDrainGain, problems),
            oxygenSaturationMeters = positive(node, "oxygenSaturationMeters", d.oxygenSaturationMeters, problems),
            oxygenTankMaxFraction = ratio(node, "oxygenTankMaxFraction", d.oxygenTankMaxFraction, problems),
            oxygenTankMinGapMeters = positive(node, "oxygenTankMinGapMeters", d.oxygenTankMinGapMeters, problems),
            pickupMinOffsetMeters = positive(node, "pickupMinOffsetMeters", d.pickupMinOffsetMeters, problems),
        )
    }

    private fun readBiomes(node: JsonValue?, problems: MutableList<String>): List<BiomeDefinition> {
        if (node == null || !node.isArray()) {
            problems += "missing \"biomes\" array"
            return emptyList()
        }
        val out = mutableListOf<BiomeDefinition>()
        val seen = mutableSetOf<String>()

        for (index in 0 until node.size()) {
            val biome = node.get(index)
            if (!biome.isObject()) {
                problems += "biome $index is not an object"
                continue
            }
            val id = biome.getString("id", null)
            if (id.isNullOrBlank()) {
                problems += "biome $index has no \"id\""
                continue
            }
            if (!seen.add(id)) {
                // Two zones at one depth makes the biome lookup ambiguous, and the
                // fix is obvious enough to be worth saying out loud.
                problems += "biome \"$id\" is defined more than once"
                continue
            }

            val weightsNode = biome.get("hazardWeights")
            if (weightsNode == null || !weightsNode.isObject) {
                problems += "biome \"$id\" has no \"hazardWeights\" object"
                continue
            }
            val weights = mutableMapOf<String, Float>()
            for (entry in weightsNode) {
                val name = entry.name
                val kind = name.uppercase()
                if (kind !in KNOWN_HAZARDS) {
                    problems += "biome \"$id\" lists unknown hazard \"$name\""
                    continue
                }
                val w = weightsNode.getFloat(name, -1f)
                if (w < 0f) {
                    problems += "biome \"$id\" gives \"$name\" a negative weight"
                    continue
                }
                weights[kind] = w
            }
            if (weights.isEmpty()) {
                problems += "biome \"$id\" has no usable hazard weights"
                continue
            }
            val minDepth = biome.getFloat("minDepth", -1f)
            if (minDepth < 0f) {
                problems += "biome \"$id\" has a negative minDepth"
                continue
            }
            out += BiomeDefinition(id = id, minDepth = minDepth, hazardWeights = weights)
        }

        if (out.isEmpty()) {
            problems += "no usable biomes"
            return emptyList()
        }

        // The loader wants depth order and a single shallowest zone, because the
        // game's own lookup assumes both and would otherwise pick one silently.
        out.sortBy { it.minDepth }
        if (out.first().minDepth > 0f) {
            problems += "the shallowest biome starts at ${out.first().minDepth}m, so the surface has no biome"
        }
        for (i in 0 until out.size - 1) {
            if (abs(out[i].minDepth - out[i + 1].minDepth) < 0.001f) {
                problems += "biomes \"${out[i].id}\" and \"${out[i + 1].id}\" start at the same depth"
            }
        }
        return out
    }

    /** A required positive number; anything else is a problem and uses the default. */
    private fun positive(
        node: JsonValue,
        key: String,
        default: Float,
        problems: MutableList<String>,
    ): Float {
        if (!node.has(key)) {
            problems += "tuning is missing \"$key\""
            return default
        }
        val v = node.getFloat(key, default)
        if (!v.isFinite() || v <= 0f) {
            problems += "tuning value \"$key\" must be positive, was $v"
            return default
        }
        return v
    }

    /** A number that has to be a fraction, used for two different ratios. */
    private fun ratio(
        node: JsonValue,
        key: String,
        default: Float,
        problems: MutableList<String>,
    ): Float {
        if (!node.has(key)) {
            problems += "tuning is missing \"$key\""
            return default
        }
        val v = node.getFloat(key, default)
        if (!v.isFinite() || v <= 0f || v > 1f) {
            problems += "tuning value \"$key\" must be between 0 and 1, was $v"
            return default
        }
        return v
    }

    /** Mirrors `HazardKind` so this file does not have to import game code. */
    private val KNOWN_HAZARDS = setOf(
        "ROCK", "MINE", "JELLYFISH", "EEL", "ANGLER", "VORTEX", "SHARK",
    )
}
