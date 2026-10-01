package com.depthdiver.tools

import com.depthdiver.content.BiomeDefinition
import com.depthdiver.content.ContentDefinition
import com.depthdiver.content.ContentLoader
import com.depthdiver.content.ContentSource
import com.depthdiver.content.Tuning
import kotlin.math.round

/**
 * Writes content back out in one canonical shape.
 *
 * A tool that emits a different file every time you run it turns a content file
 * into a diff nobody can read, and a `git diff` showing forty reindented lines
 * hides the one line you actually changed. So output is generated from the
 * parsed model rather than edited in place: same input, same bytes.
 *
 * Two-space indent, one hazard per line, weights as given. What the tool writes
 * and what ships are therefore byte-comparable, which is what lets a test assert
 * they have not drifted.
 */
object ContentWriter {

    fun toJson(content: ContentDefinition): String {
        val out = StringBuilder()
        out.line("{")
        out.line("""  "version": 1,""")
        tuning(out, content.tuning)
        out.line("""  "biomes": [""")
        content.biomes.forEachIndexed { index, biome ->
            biome(out, biome, last = index == content.biomes.size - 1)
        }
        out.line("  ]")
        out.line("}")
        return out.toString()
    }

    /** The tuning keys, in a fixed order, so diffs line up. */
    private val TUNING_KEYS = listOf(
        "worldWidthMeters", "hazardIntervalBaseMin", "hazardIntervalBaseMax",
        "hazardRampGain", "hazardSaturationMeters", "hazardIntervalFloor",
        "scrollCeilingRatio", "oxygenDrainGain", "oxygenSaturationMeters",
        "oxygenTankMaxFraction", "oxygenTankMinGapMeters", "pickupMinOffsetMeters",
    )

    private fun tuning(out: StringBuilder, t: Tuning) {
        out.line("""  "tuning": {""")
        for (key in TUNING_KEYS) {
            val value = when (key) {
                "worldWidthMeters" -> t.worldWidthMeters
                "hazardIntervalBaseMin" -> t.hazardIntervalBaseMin
                "hazardIntervalBaseMax" -> t.hazardIntervalBaseMax
                "hazardRampGain" -> t.hazardRampGain
                "hazardSaturationMeters" -> t.hazardSaturationMeters
                "hazardIntervalFloor" -> t.hazardIntervalFloor
                "scrollCeilingRatio" -> t.scrollCeilingRatio
                "oxygenDrainGain" -> t.oxygenDrainGain
                "oxygenSaturationMeters" -> t.oxygenSaturationMeters
                "oxygenTankMaxFraction" -> t.oxygenTankMaxFraction
                "oxygenTankMinGapMeters" -> t.oxygenTankMinGapMeters
                else -> t.pickupMinOffsetMeters
            }
            val comma = if (key == TUNING_KEYS.last()) "" else ","
            out.line("""    "$key": ${number(value)}$comma""")
        }
        out.line("  },")
    }

    private fun biome(out: StringBuilder, biome: BiomeDefinition, last: Boolean) {
        out.line("    {")
        out.line("""      "id": "${biome.id}",""")
        out.line("""      "minDepth": ${number(biome.minDepth)},""")
        val weights = biome.hazardWeights.entries.sortedBy { it.key }
        if (weights.isEmpty()) {
            out.line("""      "hazardWeights": {}""")
        } else {
            out.line("""      "hazardWeights": {""")
            weights.forEachIndexed { i, entry ->
                val comma = if (i == weights.size - 1) "" else ","
                out.line("""        "${entry.key}": ${number(entry.value)}$comma""")
            }
            out.line("      }")
        }
        out.line(if (last) "    }" else "    },")
    }

    /**
     * Numbers as plain decimals, never in exponent form.
     *
     * `1.4E-4` is valid JSON and unreadable in a balance file.
     */
    private fun number(value: Float): String {
        // Float.toString spells 0.45f as 0.44999998807907104, which is correct and
        // unreadable. Rounding to a few decimals is safe here because every one of
        // these numbers is a weight or a rate, and a weight that differs in the
        // ninth decimal is not a weight anyone tuned.
        val v = round(value.toDouble() * 1000.0) / 1000.0
        if (v == v.toLong().toDouble()) return "${v.toLong()}.0"
        var s = v.toString()
        if (s.contains('E') || s.contains('e')) {
            s = String.format("%.6f", v).trimEnd('0').trimEnd('.')
        }
        return s
    }

    private fun StringBuilder.line(text: String) {
        append(text)
        append('\n')
    }
}

/** The content a brand-new file starts from: the built-in copy. */
fun builtInContent(): ContentDefinition = ContentLoader.parse(ContentSource.BUILT_IN_JSON)
