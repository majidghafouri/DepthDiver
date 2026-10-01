package com.depthdiver.tools

import com.depthdiver.content.BiomeDefinition
import com.depthdiver.content.ContentDefinition
import com.depthdiver.content.ContentLoader
import java.io.File

/**
 * The content authoring tool.
 *
 * External on purpose. An in-game editor would put editing code, editing UI and
 * an input model into a game that has none of those, and would ship all of it to
 * players. This is a plain JVM program that reads and writes the same JSON the
 * game loads, so the only thing it shares with the game is the file format and
 * the validator.
 *
 * Every command that writes goes through [ContentLoader.parse] first, which does
 * not fall back. An editor that replaced a broken file with built-in values would
 * destroy the author's work at exactly the moment they were trying to fix it.
 */
fun main(args: Array<String>) {
    if (args.isEmpty() || args[0] == "-h" || args[0] == "--help" || args[0] == "help") {
        printUsage()
        return
    }
    val exit = try {
        when (args[0]) {
            "validate" -> validate(args.drop(1))
            "show" -> show(args.drop(1))
            "init" -> init(args.drop(1))
            "set" -> set(args.drop(1))
            "add-hazard" -> addHazard(args.drop(1))
            "remove-hazard" -> removeHazard(args.drop(1))
            "rename-biome" -> renameBiome(args.drop(1))
            "normalise" -> normalise(args.drop(1))
            else -> {
                System.err.println("unknown command '${args[0]}'")
                printUsage()
                2
            }
        }
    } catch (e: ContentToolError) {
        System.err.println("error: ${e.message}")
        1
    }
    if (exit != 0) System.exit(exit)
}

private fun printUsage() {
    println(
        """
        depthdiver content tool

        validate   <file>
            Parse a content file and report every problem. Exit 1 if any.

        show       <file>
            Print a summary of biomes, hazard weights and tuning.

        init       <file>
            Write a new content file from the built-in values.

        set        <file> <tuning-key> <number>
            Change one tuning value. e.g. set content.json hazardRampGain 0.7

        add-hazard    <file> <biome-id> <HAZARD> [weight]
            Add a hazard to a biome. Default weight 0.1.

        remove-hazard <file> <biome-id> <HAZARD>
            Remove a hazard from a biome.

        rename-biome  <file> <old-id> <new-id>
            Rename a biome, keeping its weights.

        normalise  <file>
            Rewrite a file in the canonical form, so diffs stay readable.

        Exit codes: 0 fine, 1 invalid or refused, 2 bad usage.
        """.trimIndent(),
    )
}

private class ContentToolError(message: String) : Exception(message)

/** Read and validate, refusing to go further if the file does not make sense. */
private fun read(path: String): Pair<File, ContentDefinition> {
    val file = File(path)
    if (!file.isFile) throw ContentToolError("$path does not exist")
    val content = ContentLoader.parse(file.readText())
    if (content.hasProblems) {
        System.err.println("$path has ${content.problems.size} problem(s):")
        content.problems.forEach { System.err.println("  - $it") }
        throw ContentToolError("refusing to edit a file that does not parse; fix the problems above")
    }
    return file to content
}

/**
 * Write, having first checked the file is still one the game will accept.
 *
 * The message names the change rather than blaming the writer, because the
 * overwhelmingly likely cause is a typo in a command, not a bug in here.
 */
private fun writeChecked(file: File, content: ContentDefinition) {
    val problems = ContentLoader.parse(ContentWriter.toJson(content)).problems
    if (problems.isNotEmpty()) {
        throw ContentToolError("that change would produce a file the game rejects: ${problems.joinToString("; ")}")
    }
    write(file, content)
}

/**
 * The known hazard names, checked before a write rather than after.
 *
 * Without this the author finds out about a typo from a message about the
 * writer, which reads like the tool being broken.
 */
private fun requireKnownHazard(hazard: String) {
    val known = setOf("ROCK", "MINE", "JELLYFISH", "EEL", "ANGLER", "VORTEX", "SHARK")
    if (hazard !in known) {
        throw ContentToolError("unknown hazard '$hazard'. Known hazards: ${known.joinToString(", ")}")
    }
}

/** Write the file, re-validating so a tool bug cannot leave an unloadable result. */
private fun write(file: File, content: ContentDefinition) {
    val text = ContentWriter.toJson(content)
    val check = ContentLoader.parse(text)
    if (check.hasProblems) {
        throw ContentToolError("internal error: the file this tool would write does not parse: ${check.problems}")
    }
    file.parentFile?.mkdirs()
    file.writeText(text)
    println("wrote ${file.path} (${text.length} bytes)")
}

private fun validate(rest: List<String>): Int {
    if (rest.size != 1) throw ContentToolError("usage: validate <file>")
    val file = File(rest[0])
    if (!file.isFile) throw ContentToolError("${rest[0]} does not exist")
    val content = ContentLoader.parse(file.readText())
    if (content.hasProblems) {
        println("${file.path}: ${content.problems.size} problem(s)")
        content.problems.forEach { println("  - $it") }
        return 1
    }
    println("${file.path}: ok, ${content.biomes.size} biomes")
    return 0
}

private fun show(rest: List<String>): Int {
    if (rest.size != 1) throw ContentToolError("usage: show <file>")
    val (_, content) = read(rest[0])
    println("biomes (${content.biomes.size}):")
    for (biome in content.biomes) {
        val weights = biome.hazardWeights.entries.sortedBy { it.key }
            .joinToString("  ") { entry -> "${entry.key} ${format(entry.value)}" }
        println("  ${biome.id.padEnd(18)} from ${format(biome.minDepth)}m")
        println("      $weights")
    }
    println("tuning:")
    for (line in ContentWriter.toJson(content).lines()) {
        val t = line.trim()
        if (t.startsWith('"') && t.contains(':') && !t.contains('{')) println("  $t")
    }
    return 0
}

private fun init(rest: List<String>): Int {
    if (rest.size != 1) throw ContentToolError("usage: init <file>")
    val file = File(rest[0])
    if (file.exists()) {
        throw ContentToolError("${rest[0]} already exists; delete it first if you mean to start over")
    }
    write(file, builtInContent())
    return 0
}

private fun set(rest: List<String>): Int {
    if (rest.size != 3) throw ContentToolError("usage: set <file> <tuning-key> <number>")
    val (file, content) = read(rest[0])
    val key = rest[1]
    val value = rest[2].toFloatOrNull()
        ?: throw ContentToolError("'" + rest[2] + "' is not a number")

    val updated = content.copy(tuning = setTuning(content.tuning, key, value))
    val check = ContentLoader.parse(ContentWriter.toJson(updated))
    if (check.hasProblems) {
        // Rejected by the same validator the game uses, so the tool cannot write
        // a file the game would refuse.
        throw ContentToolError("the game would not accept that: ${check.problems.joinToString("; ")}")
    }
    writeChecked(file, updated)
    return 0
}

private fun setTuning(t: com.depthdiver.content.Tuning, key: String, v: Float) = when (key) {
    "worldWidthMeters" -> t.copy(worldWidthMeters = v)
    "hazardIntervalBaseMin" -> t.copy(hazardIntervalBaseMin = v)
    "hazardIntervalBaseMax" -> t.copy(hazardIntervalBaseMax = v)
    "hazardRampGain" -> t.copy(hazardRampGain = v)
    "hazardSaturationMeters" -> t.copy(hazardSaturationMeters = v)
    "hazardIntervalFloor" -> t.copy(hazardIntervalFloor = v)
    "scrollCeilingRatio" -> t.copy(scrollCeilingRatio = v)
    "oxygenDrainGain" -> t.copy(oxygenDrainGain = v)
    "oxygenSaturationMeters" -> t.copy(oxygenSaturationMeters = v)
    "oxygenTankMaxFraction" -> t.copy(oxygenTankMaxFraction = v)
    "oxygenTankMinGapMeters" -> t.copy(oxygenTankMinGapMeters = v)
    "pickupMinOffsetMeters" -> t.copy(pickupMinOffsetMeters = v)
    else -> throw ContentToolError(
        "unknown tuning key '$key'. Known keys: ${TUNING_KEYS.joinToString(", ")}",
    )
}

private val TUNING_KEYS = listOf(
    "worldWidthMeters", "hazardIntervalBaseMin", "hazardIntervalBaseMax",
    "hazardRampGain", "hazardSaturationMeters", "hazardIntervalFloor",
    "scrollCeilingRatio", "oxygenDrainGain", "oxygenSaturationMeters",
    "oxygenTankMaxFraction", "oxygenTankMinGapMeters", "pickupMinOffsetMeters",
)

private fun addHazard(rest: List<String>): Int {
    if (rest.size !in 3..4) throw ContentToolError("usage: add-hazard <file> <biome-id> <HAZARD> [weight]")
    val (file, content) = read(rest[0])
    val biomeId = rest[1]
    val hazard = rest[2].uppercase()
    val weight = if (rest.size == 4) {
        rest[3].toFloatOrNull() ?: throw ContentToolError("'${rest[3]}' is not a number")
    } else {
        0.1f
    }
    if (weight < 0f) throw ContentToolError("a weight cannot be negative, got $weight")
    requireKnownHazard(hazard)
    val biomes = content.biomes.map { biome ->
        if (biome.id != biomeId) return@map biome
        if (biome.hazardWeights.containsKey(hazard)) {
            throw ContentToolError("$biomeId already has $hazard at ${biome.hazardWeights[hazard]}")
        }
        biome.copy(hazardWeights = biome.hazardWeights + (hazard to weight))
    }
    if (biomes.none { it.id == biomeId }) throw ContentToolError("no biome called '$biomeId'")
    writeChecked(file, content.copy(biomes = biomes))
    return 0
}

private fun removeHazard(rest: List<String>): Int {
    if (rest.size != 3) throw ContentToolError("usage: remove-hazard <file> <biome-id> <HAZARD>")
    val (file, content) = read(rest[0])
    val biomeId = rest[1]
    val hazard = rest[2].uppercase()
    val biomes = content.biomes.map { biome ->
        if (biome.id != biomeId) return@map biome
        if (!biome.hazardWeights.containsKey(hazard)) {
            throw ContentToolError("$biomeId does not have $hazard")
        }
        biome.copy(hazardWeights = biome.hazardWeights - hazard)
    }
    if (biomes.none { it.id == biomeId }) throw ContentToolError("no biome called '$biomeId'")
    writeChecked(file, content.copy(biomes = biomes))
    return 0
}

private fun renameBiome(rest: List<String>): Int {
    if (rest.size != 3) throw ContentToolError("usage: rename-biome <file> <old-id> <new-id>")
    val (file, content) = read(rest[0])
    val (old, new) = rest[1] to rest[2]
    if (content.biomes.none { it.id == old }) throw ContentToolError("no biome called '$old'")
    if (content.biomes.any { it.id == new }) throw ContentToolError("'$new' is already a biome")
    val biomes: List<BiomeDefinition> = content.biomes.map {
        if (it.id == old) it.copy(id = new) else it
    }
    write(file, content.copy(biomes = biomes))
    return 0
}

private fun normalise(rest: List<String>): Int {
    if (rest.size != 1) throw ContentToolError("usage: normalise <file>")
    val (file, content) = read(rest[0])
    write(file, content)
    return 0
}

private fun format(v: Float): String =
    if (v == v.toLong().toFloat()) "${v.toLong()}" else v.toString()
