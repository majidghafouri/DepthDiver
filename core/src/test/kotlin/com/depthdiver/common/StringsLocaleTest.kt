package com.depthdiver.common

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Every locale has every key.
 *
 * `Strings.t` falls back to the English table when a key is missing from the
 * active locale, which is the right behaviour at runtime and useless for
 * finding gaps: the Spanish build compiles, passes every test, and quietly shows
 * "MUTATION GILDED" in the middle of a Spanish menu. Nothing else in the suite
 * can catch that, because nothing else knows a key is supposed to be there.
 *
 * This reads the key lists straight out of [Strings]'s own tables rather than
 * checking hard-coded names, so it cannot itself go stale.
 */
class StringsLocaleTest {

    @Test
    fun everyLocaleDefinesEveryKey() {
        val (english, others) = Strings.localeKeySets()
        assertTrue(english.size > 100, "only found ${english.size} English keys, so the scan is broken")

        for ((name, keys) in others) {
            val missing = english - keys
            if (missing.isNotEmpty()) {
                fail(
                    "The $name locale is missing ${missing.size} key(s): " +
                        missing.sorted().joinToString(", "),
                )
            }
        }
    }

    @Test
    fun noLocaleDefinesAKeyEnglishDoesNot() {
        // The other direction. A stray ES-only key means a typo that ES looks
        // right and every other language falls back on.
        val (english, others) = Strings.localeKeySets()
        for ((name, keys) in others) {
            val extra = keys - english
            if (extra.isNotEmpty()) {
                fail("The $name locale defines key(s) English does not: ${extra.sorted().joinToString(", ")}")
            }
        }
    }

    @Test
    fun noValueIsLeftAsAnUntranslatedCopyOfItsKey() {
        // Catches a translation that was added by pasting the English value in.
        // Only checked for keys that are obviously prose.
        val suspect = Strings.untranslatedValues()
        if (suspect.isNotEmpty()) {
            fail("These values look untranslated: ${suspect.joinToString(", ")}")
        }
    }

    @Test
    fun theSourceFileStillDeclaresTheTablesThisTestReads() {
        // If Common.kt is ever reorganised, this fails loudly rather than the
        // tests above quietly reporting an empty locale.
        val file = locateCommon()
        val text = file.readText()
        assertTrue(text.contains("private val EN"), "the EN table moved; update StringsLocaleTest")
        assertTrue(text.contains("private val ES"), "the ES table moved; update StringsLocaleTest")
    }

    private fun locateCommon(): File {
        val suffix = listOf("core", "src", "main", "kotlin", "com", "depthdiver", "common", "Common.kt")
        var dir: File? = File(".").absoluteFile
        repeat(6) {
            dir?.let {
                val candidate = File(it, suffix.joinToString("/"))
                if (candidate.isFile) return candidate
            }
            dir = dir?.parentFile
        }
        fail("could not find core/src/main/kotlin/com/depthdiver/common/Common.kt")
    }
}