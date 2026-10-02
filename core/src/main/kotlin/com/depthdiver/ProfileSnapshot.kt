package com.depthdiver

import com.badlogic.gdx.Preferences

/**
 * The save file, as bytes, and how two copies of it are reconciled.
 *
 * Written by hand rather than by reflecting over [Preferences] because
 * `Preferences` cannot enumerate its own keys -- there is no way to ask it what
 * it holds -- so a reflective dump would be silently partial the first time a
 * setting was added. The manifest below is explicit, and a test asserts it
 * covers every key the profile actually writes.
 *
 * The merge is deliberately not "cloud wins" or "local wins". A cloud save is
 * restored onto a device that has been played on since the last upload, and
 * either side winning outright throws away real progress. Progress keys take the
 * better of the two; settings take the local value, because someone who has
 * turned the music off on this device should not have it turned back on by a
 * snapshot from another one.
 */
data class ProfileSnapshot(
    val values: Map<String, Value> = emptyMap(),
    /** Schema version, so a future format can be told apart from this one. */
    val version: Int = CURRENT_VERSION,
) {
    sealed interface Value {
        data class IntVal(val value: Int) : Value
        data class FloatVal(val value: Float) : Value
        data class BoolVal(val value: Boolean) : Value
        data class StringVal(val value: String) : Value
    }

    val isEmpty: Boolean get() = values.isEmpty()

    /**
     * A stable text form, for a save file small enough to be worth reading.
     *
     * Sorted so the same state always serialises to the same bytes, which is
     * what lets a test compare snapshots and a player compare devices. Values are
     * separated by a tab and keyed by name; tab and newline cannot appear in a
     * key, and the one string value we store (landmark ids) is comma-separated
     * ASCII, so the format cannot be corrupted by the data.
     */
    fun encode(): String = buildString {
        appendLine("depthdiver-save	$version")
        values.keys.sorted().forEach { key ->
            val value = values.getValue(key)
            val text = when (value) {
                is Value.IntVal -> value.value.toString()
                is Value.FloatVal -> value.value.toString()
                is Value.BoolVal -> value.value.toString()
                is Value.StringVal -> value.value
            }
            append(key).append('\t').append(text).append('\n')
        }
    }

    companion object {
        const val CURRENT_VERSION = 1
        private const val HEADER_PREFIX = "depthdiver-save"

        /** Parse [encode]'s output, or null if it is not one of ours. */
        @Suppress("DEPRECATION")
        fun decode(text: String): ProfileSnapshot? {
            val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
            val header = lines.firstOrNull()?.split('\t') ?: return null
            if (header.size != 2 || header[0] != HEADER_PREFIX) return null
            val version = header[1].toIntOrNull() ?: return null

            val values = mutableMapOf<String, Value>()
            for (line in lines.drop(1)) {
                val parts = line.split('\t')
                if (parts.size != 2) continue
                val key = parts[0]
                val raw = parts[1]
                // The manifest decides the type. Reading it from the text would
                // mean a corrupt line could change the type of a save key.
                val field = MANIFEST[key] ?: continue
                val value: Value = when (field.default) {
                    is Value.IntVal -> raw.toIntOrNull()?.let { Value.IntVal(it) } ?: continue
                    is Value.FloatVal -> raw.toFloatOrNull()?.let { Value.FloatVal(it) } ?: continue
                    is Value.BoolVal -> Value.BoolVal(raw.toBooleanStrictOrNull() ?: continue)
                    is Value.StringVal -> Value.StringVal(raw)
                }
                values[key] = value
            }
            return ProfileSnapshot(values = values, version = version)
        }

        /**
         * Which keys sync, and how each is reconciled.
         *
         * [Progress] takes the higher of the two, so no device can lose a best
         * score to a stale upload. [LastWriteWins] takes this device's copy,
         * which is right for anything the player chose on this device.
         */
        enum class Merge {
            /** The better of the two, so no device loses progress to a stale copy. */
            PROGRESS,

            /** This device's value, for anything the player chose here. */
            LOCAL_WINS,

            /** Both, combined. For sets like discovered landmarks. */
            UNION,
        }

        data class Field(val merge: Merge, val default: Value)

        /**
         * The syncable save file.
         *
         * Deliberately excludes accessibility and audio settings: those are
         * per-device preferences rather than progress, and syncing them means a
         * player who turns the music down on one device gets it turned back up on
         * another. It also excludes anything derived from the clock.
         */
        val MANIFEST: Map<String, Field> = linkedMapOf(
            // progress: the better of the two, so a stale cloud copy cannot undo
            // a good session
            "bestDepth" to Field(Merge.PROGRESS, Value.FloatVal(0f)),
            "bestScore" to Field(Merge.PROGRESS, Value.IntVal(0)),
            "bestRunPearls" to Field(Merge.PROGRESS, Value.IntVal(0)),
            "totalPearls" to Field(Merge.PROGRESS, Value.IntVal(0)),
            "lifetimePearls" to Field(Merge.PROGRESS, Value.IntVal(0)),
            "dives" to Field(Merge.PROGRESS, Value.IntVal(0)),
            "streak.best" to Field(Merge.PROGRESS, Value.IntVal(0)),
            // the streak's current run and last day are local-only on purpose:
            // merging them across devices would let a cloud copy revive a streak
            // the player actually let lapse, which is the one thing the streak
            // design promised never to do.
            "dailyDay" to Field(Merge.LOCAL_WINS, Value.IntVal(0)),
            // A union, not a max and not a last-write-wins: a landmark found on
            // one device was found, and a restore must not un-find it.
            "landmarks.found" to Field(Merge.UNION, Value.StringVal("")),
        )

        /** Keys that stay on this device and are never uploaded. */
        val LOCAL_ONLY: Set<String> = setOf(
            "streak.current", "streak.lastDay",
            "musicVolume", "sfxVolume", "masterVolume",
            "musicMuted", "sfxMuted",
            "locale", "highContrast", "reduceMotion", "screenShake",
            // Difficulty is chosen per device, not a record of the player.
            "difficulty",
        )

        fun readFrom(prefs: Preferences): ProfileSnapshot = ProfileSnapshot(
            values = MANIFEST.keys.mapNotNull { key ->
                val field = MANIFEST.getValue(key)
                val value = readTyped(prefs, key, field.default)
                if (value == null) null else key to value
            }.toMap(),
        )

        @Suppress("UNCHECKED_CAST")
        private fun readTyped(prefs: Preferences, key: String, default: Value): Value? = try {
            when (default) {
                is Value.IntVal -> Value.IntVal(prefs.getInteger(key, default.value))
                is Value.FloatVal -> Value.FloatVal(prefs.getFloat(key, default.value))
                is Value.BoolVal -> Value.BoolVal(prefs.getBoolean(key, default.value))
                // Absent rather than defaulted: a key the player has never
                // touched should not upload as an empty string, or a restore
                // would write "no landmarks found" over a good set.
                is Value.StringVal -> prefs.getString(key, null)?.let { Value.StringVal(it) }
            }
        } catch (_: ClassCastException) {
            // The stored value is a different type than the manifest says, which
            // is what a format change looks like. Treat it as absent rather than
            // failing the whole restore.
            null
        }

        fun writeTo(snapshot: ProfileSnapshot, prefs: Preferences) {
            snapshot.values.forEach { (key, value) ->
                when (value) {
                    is Value.IntVal -> prefs.putInteger(key, value.value)
                    is Value.FloatVal -> prefs.putFloat(key, value.value)
                    is Value.BoolVal -> prefs.putBoolean(key, value.value)
                    is Value.StringVal -> prefs.putString(key, value.value)
                }
            }
        }

        /**
         * Reconcile a downloaded [cloud] snapshot into local [prefs].
         *
         * Returns the keys that actually changed, which is what a settings screen
         * needs to say "your save was restored" honestly rather than claiming a
         * merge happened when the cloud copy was older and lost.
         */
        fun mergeInto(cloud: ProfileSnapshot, prefs: Preferences): List<String> {
            if (cloud.version != CURRENT_VERSION) return emptyList()
            val changed = mutableListOf<String>()

            for ((key, field) in MANIFEST) {
                val incoming = cloud.values[key] ?: continue
                val local = readTyped(prefs, key, field.default)
                val winner = when (field.merge) {
                    Merge.PROGRESS -> maxOf(local, incoming)
                    Merge.LOCAL_WINS -> local ?: incoming
                    Merge.UNION -> unionStrings(local, incoming)
                }
                if (winner != null && winner != local) {
                    writeOne(prefs, key, winner)
                    changed += key
                }
            }
            return changed
        }

        private fun writeOne(prefs: Preferences, key: String, value: Value) {
            when (value) {
                is Value.IntVal -> prefs.putInteger(key, value.value)
                is Value.FloatVal -> prefs.putFloat(key, value.value)
                is Value.BoolVal -> prefs.putBoolean(key, value.value)
                is Value.StringVal -> prefs.putString(key, value.value)
            }
        }

        /**
         * Higher of two progress values; null if either is missing.
         *
         * Compared by the underlying magnitude so "best" means best whatever the
         * type. Totals and streaks are larger-is-better; there is no key here
         * where smaller is better.
         */
        /** Sorted union of two comma-separated id sets, stored the same way. */
        private fun unionStrings(a: Value?, b: Value): Value? {
            val local = (a as? Value.StringVal)?.value?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
            val remote = (b as? Value.StringVal)?.value?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
            val all = (local + remote).toSortedSet()
            if (all.isEmpty() && local.isEmpty() && remote.isEmpty()) return a
            return Value.StringVal(all.joinToString(","))
        }

        private fun maxOf(a: Value?, b: Value): Value? = when {
            a == null -> b
            else -> if (magnitude(b) > magnitude(a)) b else a
        }

        private fun magnitude(value: Value): Double = when (value) {
            is Value.IntVal -> value.value.toDouble()
            is Value.FloatVal -> value.value.toDouble()
            is Value.BoolVal -> if (value.value) 1.0 else 0.0
            // Strings are not ordered by magnitude, so a string never wins a
            // progress merge. Landmark sets are merged by union instead, below.
            is Value.StringVal -> -1.0
        }
    }
}
