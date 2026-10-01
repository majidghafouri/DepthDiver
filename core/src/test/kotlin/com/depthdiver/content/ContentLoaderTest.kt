package com.depthdiver.content

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The content pipeline.
 *
 * Two things matter here and they pull against each other. The file should be
 * the source of truth, so tuning it is an edit and not an archaeology. And a
 * broken file must never be able to cost anyone a playable build, so everything
 * falls back rather than throwing. Most of these tests are about the second,
 * because the first is easy and the second is what actually bites.
 */
class ContentLoaderTest {

    // ---------- the happy path ----------

    @Test
    fun theBuiltInContentLoadsCleanly() {
        val content = ContentLoader.load(ContentSource.BUILT_IN_JSON)
        assertFalse(content.isFallback, "the built-in content did not load: ${content.problems}")
        assertFalse(content.hasProblems)
        assertEquals(5, content.biomes.size)
    }

    @Test
    fun theShippedAssetMatchesTheBuiltInConstant() {
        // The point of holding the fallback as text rather than as a Kotlin
        // object. If these can drift, a balance change lives in one place and
        // ships from the other, and nobody notices until it is live.
        val asset = locateAsset()
        assertEquals(
            ContentSource.BUILT_IN_JSON,
            asset.readText(),
            "core/assets/${ContentSource.ASSET_PATH} has drifted from ContentSource.BUILT_IN_JSON",
        )
    }

    @Test
    fun theAndroidAppActuallyPackagesIt() {
        // The failure this catches is invisible: the file exists, every test
        // passes, and the game quietly uses built-in values on the device while
        // a tuning edit appears to do nothing. Worth a build-file assertion.
        val appBuild = locateRepoRoot().resolve("app/build.gradle.kts")
        assertTrue(appBuild.isFile, "could not find ${appBuild.path}")
        val text = appBuild.readText()
        assertTrue(
            text.contains("core/assets"),
            "app/build.gradle.kts does not package core/assets, so the content file never ships",
        )
    }

    @Test
    fun bothPlatformsShipTheSameAsset() {
        // Located by walking up from the core asset, because this test runs with
        // the module directory as its working directory, not the repo root.
        val core = locateAsset().readText()
        val desktop = locateAsset().parentFile.parentFile.parentFile.parentFile
            .resolve("desktop/assets/${ContentSource.ASSET_PATH}")
        assertTrue(desktop.isFile, "desktop is missing ${desktop.path}")
        assertEquals(core, desktop.readText(), "the desktop and core content files have diverged")
    }

    @Test
    fun theAssetIsTheThingThatActuallyShips() {
        // A file that exists in the repo but is not in an assets dir is not in
        // the build, and this is the mistake that is invisible until runtime.
        val path = locateAsset().path
        assertTrue(
            path.contains("${File.separator}assets${File.separator}"),
            "the content file is not under an assets directory: $path",
        )
    }

    // ---------- never crash on bad input ----------

    @Test
    fun nothingAtAllFallsBack() {
        val content = ContentLoader.load(null)
        assertTrue(content.isFallback)
        assertEquals(5, content.biomes.size, "a fallback still has to have biomes")
    }

    @Test
    fun emptyAndBlankFallBack() {
        assertTrue(ContentLoader.load("").isFallback)
        assertTrue(ContentLoader.load("   \n ").isFallback)
    }

    @Test
    fun malformedJsonFallsBack() {
        for (bad in listOf("{", "not json", "{\"a\":}", "[1,2,3]", "null")) {
            val content = ContentLoader.load(bad)
            assertTrue(content.isFallback, "\"$bad\" was accepted")
            assertTrue(content.hasProblems, "\"$bad\" was accepted silently")
        }
    }

    @Test
    fun everyFallbackStillHasUsableContent() {
        // Falling back to nothing would be worse than not falling back at all.
        for (bad in listOf<String?>(null, "", "{", "[]", "{\"version\":2}")) {
            val content = ContentLoader.load(bad)
            assertEquals(5, content.biomes.size, "\"$bad\" left no biomes")
            assertEquals(Tuning.DEFAULT, content.tuning, "\"$bad\" changed the tuning")
        }
    }

    // ---------- catching real mistakes ----------

    @Test
    fun anUnknownHazardIsRejectedRatherThanIgnored() {
        val json = ContentSource.BUILT_IN_JSON.replace("\"ROCK\": 0.45", "\"LIZARD\": 0.45")
        val content = ContentLoader.load(json)
        assertTrue(content.isFallback, "an unknown hazard should not be accepted")
        assertTrue(content.problems.any { it.contains("LIZARD") }, "the problem should name it: ${content.problems}")
    }

    @Test
    fun aMissingTuningKeyIsCaught() {
        val json = ContentSource.BUILT_IN_JSON.replace("\"hazardRampGain\": 0.9,", "")
        val content = ContentLoader.load(json)
        assertTrue(content.isFallback, "a missing tuning key should be caught")
        assertTrue(content.problems.any { it.contains("hazardRampGain") })
    }

    @Test
    fun aNegativeWeightIsCaught() {
        val json = ContentSource.BUILT_IN_JSON.replace("\"MINE\": 0.35", "\"MINE\": -0.35")
        val content = ContentLoader.load(json)
        assertTrue(content.isFallback)
        assertTrue(content.problems.any { it.contains("negative") }, "${content.problems}")
    }

    @Test
    fun aZeroWeightIsAllowedBecauseItIsAWayToRemoveAThing() {
        // Zero means "never here", which is a legitimate authoring move and not
        // a mistake, so it must not trip the fallback.
        val json = ContentSource.BUILT_IN_JSON.replace("\"MINE\": 0.35", "\"MINE\": 0.0")
        val content = ContentLoader.load(json)
        assertFalse(content.isFallback, "a zero weight is a valid edit: ${content.problems}")
        assertEquals(0f, content.biomes.first().hazardWeights["MINE"])
    }

    @Test
    fun aDuplicateBiomeIsCaught() {
        // Two zones starting at the same depth makes the lookup ambiguous.
        val json = ContentSource.BUILT_IN_JSON.replace(
            "\"id\": \"MIDNIGHT_ZONE\",\n      \"minDepth\": 140.0",
            "\"id\": \"MIDNIGHT_ZONE\",\n      \"minDepth\": 60.0",
        )
        val content = ContentLoader.load(json)
        assertTrue(content.isFallback, "two biomes at one depth should be caught")
    }

    @Test
    fun aWorldWithNoShallowBiomeIsCaught() {
        val json = ContentSource.BUILT_IN_JSON.replace("\"minDepth\": 0.0", "\"minDepth\": 12.0")
        val content = ContentLoader.load(json)
        assertTrue(content.isFallback, "the surface needs a biome: ${content.problems}")
    }

    @Test
    fun anImpossibleRatioIsCaught() {
        val json = ContentSource.BUILT_IN_JSON.replace("\"scrollCeilingRatio\": 0.88", "\"scrollCeilingRatio\": 4.0")
        val content = ContentLoader.load(json)
        assertTrue(content.isFallback)
        assertTrue(content.problems.any { it.contains("scrollCeilingRatio") })
    }

    @Test
    fun anUnsupportedVersionIsCaught() {
        val json = ContentSource.BUILT_IN_JSON.replace("\"version\": 1", "\"version\": 99")
        val content = ContentLoader.load(json)
        assertTrue(content.isFallback, "an unknown content version should not be guessed at")
    }

    @Test
    fun biomesComeBackInDepthOrder() {
        // The game's lookup walks this list, so order is part of the contract.
        val depths = ContentLoader.load(ContentSource.BUILT_IN_JSON).biomes.map { it.minDepth }
        assertEquals(depths.sorted(), depths)
    }

    @Test
    fun theBuiltInTuningMatchesTheValuesTheGameRan() {
        // Tuning.DEFAULT and BUILT_IN_JSON are two statements of the same numbers.
        // If one is edited alone, the game quietly stops using the file.
        val content = ContentLoader.load(ContentSource.BUILT_IN_JSON)
        val d = Tuning.DEFAULT
        val t = content.tuning
        assertEquals(d.worldWidthMeters, t.worldWidthMeters)
        assertEquals(d.hazardIntervalBaseMin, t.hazardIntervalBaseMin)
        assertEquals(d.hazardIntervalBaseMax, t.hazardIntervalBaseMax)
        assertEquals(d.hazardRampGain, t.hazardRampGain)
        assertEquals(d.hazardSaturationMeters, t.hazardSaturationMeters)
        assertEquals(d.hazardIntervalFloor, t.hazardIntervalFloor)
        assertEquals(d.scrollCeilingRatio, t.scrollCeilingRatio)
        assertEquals(d.oxygenDrainGain, t.oxygenDrainGain)
        assertEquals(d.oxygenSaturationMeters, t.oxygenSaturationMeters)
        assertEquals(d.oxygenTankMaxFraction, t.oxygenTankMaxFraction)
        assertEquals(d.oxygenTankMinGapMeters, t.oxygenTankMinGapMeters)
        assertEquals(d.pickupMinOffsetMeters, t.pickupMinOffsetMeters)
    }

    @Test
    fun aFallbackSaysWhyRatherThanFailingQuietly() {
        // A silent fallback looks exactly like a balance change nobody made.
        val content = ContentLoader.load("{ nope")
        assertTrue(content.problems.isNotEmpty(), "a fallback with no explanation is the worst case")
    }

    private fun locateRepoRoot(): File = locateAsset().parentFile.parentFile.parentFile.parentFile

    private fun locateAsset(): File {
        val suffix = listOf("core", "assets", ContentSource.ASSET_PATH)
        var dir: File? = File(".").absoluteFile
        repeat(6) {
            dir?.let {
                val candidate = File(it, suffix.joinToString("/"))
                if (candidate.isFile) return candidate
            }
            dir = dir?.parentFile
        }
        fail("could not find core/assets/${ContentSource.ASSET_PATH} from ${File(".").absolutePath}")
    }
}
