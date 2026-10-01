package com.depthdiver.common

import com.depthdiver.game.HazardKind
import com.depthdiver.landmark.Landmarks
import com.depthdiver.run.DeathLesson
import com.depthdiver.run.RunTerminalReason
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Every string the game asks for has to exist.
 *
 * [Strings.t] returns the key itself when a lookup misses, which is the right
 * behaviour at runtime -- a missing translation should not crash a dive -- and
 * the wrong behaviour for finding bugs, because the result still compiles, still
 * passes every other test, and just quietly prints `landmarksFound` on the
 * profile screen. That is exactly what happened once already, so this reads the
 * source and insists the keys are real.
 */
class StringsCoverageTest {

    @Test
    fun everyKeyTheSourceAsksForResolves() {
        val root = sourceRoot()
        val keys = Regex("""Strings\.t\(\s*"([A-Za-z0-9_]+)"""")
            .findAll(root.walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() })
            .map { it.groupValues[1] }
            .toSet()

        assertTrue(keys.size > 50, "only found ${keys.size} string keys, so the scan is broken")

        val missing = keys.filter { Strings.t(it) == it }.sorted()
        if (missing.isNotEmpty()) {
            fail(
                "These keys are used in the source but not defined in the EN table: " +
                    missing.joinToString(", "),
            )
        }
    }

    @Test
    fun everyKeyTheFeaturesCanEmitAtRuntimeResolves() {
        // The scan above only catches literal lookups. Most strings are emitted
        // through a variable -- a landmark's name, a death's cause -- so nothing
        // in the source names those keys and the scan cannot see them. This
        // enumerates the keys those features can actually produce instead.
        val emitted = buildList {
            addAll(Landmarks.ALL.map { it.nameKey })
            for (reason in RunTerminalReason.entries) {
                for (hazard in listOf<HazardKind?>(null) + HazardKind.entries) {
                    val lesson = DeathLesson.forRun(reason, 100f, 100f, hazard)
                    if (lesson.causeKey.isNotEmpty()) add(lesson.causeKey)
                    lesson.hintKey?.let { add(it) }
                }
            }
            add("found")
            add("landmarksFound")
            add("landmarksNext")
            add("landmarksAll")
            add("nearMiss")
        }.toSet()

        val missing = emitted.filter { Strings.t(it) == it }.sorted()
        if (missing.isNotEmpty()) {
            fail(
                "These keys are emitted at runtime but not defined in the EN table: " +
                    missing.joinToString(", "),
            )
        }
    }

    /**
     * The module's main source tree, located by walking up from the working
     * directory so the test survives being run from the repo root or the module.
     */
    private fun sourceRoot(): File {
        val suffix = listOf("core", "src", "main", "kotlin")
        var dir: File? = File(".").absoluteFile
        repeat(6) {
            dir?.let { if (File(it, suffix.joinToString("/")).isDirectory) return it }
            dir = dir?.parentFile
        }
        fail("could not find core/src/main/kotlin from ${File(".").absolutePath}")
    }
}
