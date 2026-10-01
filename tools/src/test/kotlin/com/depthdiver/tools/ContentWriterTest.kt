package com.depthdiver.tools

import com.depthdiver.content.ContentLoader
import com.depthdiver.content.ContentSource
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The authoring tool.
 *
 * The property that matters most is idempotence. A tool that rewrites a file
 * differently every time is worse than no tool, because every balance change
 * arrives wrapped in forty lines of reformatting and the actual edit stops being
 * reviewable. So most of these are about the output being stable.
 */
class ContentWriterTest {

    @Test
    fun theShippedFileIsAlreadyCanonical() {
        // The strongest statement available: running the tool over the shipped
        // file changes nothing at all.
        val shipped = readShipped()
        val content = ContentLoader.parse(shipped)
        assertTrue(!content.hasProblems, "the shipped file does not parse: ${content.problems}")
        assertEquals(shipped, ContentWriter.toJson(content), "the shipped file is not in canonical form")
    }

    @Test
    fun writingIsStableAcrossRuns() {
        val once = ContentWriter.toJson(builtInContent())
        val twice = ContentWriter.toJson(ContentLoader.parse(once))
        assertEquals(once, twice, "a second pass changed the file")
    }

    @Test
    fun whatTheToolWritesMatchesWhatTheGameExpects() {
        // Same validator, so a file the tool produces is a file the game reads.
        val written = ContentWriter.toJson(builtInContent())
        val reloaded = ContentLoader.parse(written)
        assertTrue(!reloaded.hasProblems, "the tool wrote a file the loader rejects: ${reloaded.problems}")
        assertEquals(5, reloaded.biomes.size)
    }

    @Test
    fun theBuiltInFallbackIsAlsoCanonical() {
        // Both the constant and what the tool writes come from the same model, so
        // a tuned file and a generated one are byte-comparable.
        assertEquals(ContentSource.BUILT_IN_JSON, ContentWriter.toJson(builtInContent()))
    }

    @Test
    fun numbersAreNeverWrittenInExponentForm() {
        // Valid JSON, unreadable in a balance file.
        val tiny = builtInContent().copy(
            tuning = builtInContent().tuning.copy(hazardRampGain = 0.00001f),
        )
        val json = ContentWriter.toJson(tiny)
        assertTrue(!json.contains("E-"), "exponent notation leaked into the file:\n$json")
    }

    @Test
    fun wholeNumbersKeepADecimalPoint() {
        val rounded = builtInContent().copy(
            tuning = builtInContent().tuning.copy(hazardIntervalBaseMin = 2f),
        )
        assertTrue(
            ContentWriter.toJson(rounded).contains("\"hazardIntervalBaseMin\": 2.0"),
            "a whole number lost its .0 and now looks like an int",
        )
    }

    @Test
    fun hazardWeightsComeOutSorted() {
        // Alphabetical so a rebalanced file has a readable diff.
        val json = ContentWriter.toJson(builtInContent())
        val firstBiome = json.substringAfter("\"hazardWeights\": {").substringBefore("}")
        val order = Regex("\"([A-Z]+)\":").findAll(firstBiome).map { it.groupValues[1] }.toList()
        assertEquals(order.sorted(), order, "hazard weights are not in order: $order")
    }

    @Test
    fun everyTuningKeySurvivesARoundTrip() {
        val original = builtInContent().tuning
        val reloaded = ContentLoader.parse(ContentWriter.toJson(builtInContent())).tuning
        assertEquals(original, reloaded)
    }

    @Test
    fun anEditedValueIsTheOnlyThingThatChanges() {
        // One tuning key edited, one line different.
        val before = ContentWriter.toJson(builtInContent())
        val edited = ContentWriter.toJson(
            builtInContent().copy(
                tuning = builtInContent().tuning.copy(hazardRampGain = 0.7f),
            ),
        )
        val differing = before.lines().zip(edited.lines()).filter { (a, b) -> a != b }
        assertEquals(1, differing.size, "editing one value changed ${differing.size} lines:\n$differing")
    }

    private fun readShipped(): String {
        val suffix = listOf("core", "assets", ContentSource.ASSET_PATH)
        var dir: File? = File(".").absoluteFile
        repeat(6) {
            dir?.let {
                val candidate = File(it, suffix.joinToString("/"))
                if (candidate.isFile) return candidate.readText()
            }
            dir = dir?.parentFile
        }
        throw AssertionError("could not find the shipped content file")
    }
}
