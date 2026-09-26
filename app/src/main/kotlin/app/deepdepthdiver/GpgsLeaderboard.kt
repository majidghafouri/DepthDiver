package app.deepdepthdiver

import android.app.Activity
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences
import com.depthdiver.Leaderboard
import com.depthdiver.LeaderboardEntry
import com.depthdiver.LeaderboardService
import com.depthdiver.LeaderboardSubmission
import com.depthdiver.run.RunId

/**
 * Stub implementation for Google Play Games Services leaderboard.
 * 
 * TODO: When the GPGS dependency is available (play-services-games:23.1.0),
 * uncomment the full implementation below and add the dependency to app/build.gradle.kts.
 * 
 * The LeaderboardService interface in core is designed as a cloud seam - this class
 * implements it and can be swapped in via Leaderboard.setCustomImpl().
 */
class GpgsLeaderboard(
    private val activity: Activity,
    private val leaderboardId: String,
) : LeaderboardService {

    // For now, delegates entirely to the local leaderboard
    private val local = LocalLeaderboard()

    override fun top(): List<LeaderboardEntry> = local.top()

    override fun qualifies(score: Int, depth: Float): Boolean = local.qualifies(score, depth)

    override fun submit(score: Int, depth: Float): List<LeaderboardEntry> = local.submit(score, depth)

    override fun submitOnce(runId: String, score: Int, depth: Float): LeaderboardSubmission =
        local.submitOnce(runId, score, depth)

    override fun submitOnce(runId: RunId, score: Int, depth: Float): LeaderboardSubmission =
        submitOnce(runId.value.toString(), score, depth)

    /**
     * Pure-Kotlin local leaderboard implementation.
     * When GPGS is enabled, this will be wrapped with cloud submission logic.
     */
    private class LocalLeaderboard {
        private val MAX_ENTRIES = 5
        private var retained: Preferences? = null

        private fun prefs(): Preferences =
            retained ?: Gdx.app.getPreferences("depthdiver-leaderboard").also { retained = it }

        fun top(): List<LeaderboardEntry> {
            val p = prefs()
            val entries = ArrayList<LeaderboardEntry>(MAX_ENTRIES)
            for (i in 0 until MAX_ENTRIES) {
                val score = p.getInteger("score.$i", -1)
                if (score < 0) break
                entries.add(LeaderboardEntry(score, p.getFloat("depth.$i", 0f)))
            }
            return entries
        }

        fun qualifies(score: Int, depth: Float): Boolean {
            if (score <= 0) return false
            val current = top()
            if (current.size < MAX_ENTRIES) return true
            return score > current.last().score
        }

        fun submit(score: Int, depth: Float): List<LeaderboardEntry> {
            if (!qualifies(score, depth)) return top()
            val merged = (top() + LeaderboardEntry(score, depth))
                .sortedWith(compareByDescending<LeaderboardEntry> { it.score }
                    .thenByDescending { it.depth })
                .take(MAX_ENTRIES)
            for (i in merged.indices) {
                prefs().putInteger("score.$i", merged[i].score)
                prefs().putFloat("depth.$i", merged[i].depth)
            }
            prefs().flush()
            return merged
        }

        fun submitOnce(runId: String, score: Int, depth: Float): LeaderboardSubmission {
            val id = requireRunId(runId)
            val p = prefs()
            val markerKey = submittedKey(id)
            if (p.contains(markerKey)) {
                val original = decodeSubmission(p.getString(markerKey, ""))
                return LeaderboardSubmission(
                    entered = original.entered,
                    rank = original.rank,
                    entries = top(),
                    score = original.score,
                    depth = original.depth,
                    duplicate = true,
                )
            }
            val current = top()
            val entered = qualifiesWith(current, score)
            val merged = if (entered) {
                (current + LeaderboardEntry(score, depth))
                    .sortedWith(compareByDescending<LeaderboardEntry> { it.score }
                        .thenByDescending { it.depth })
                    .take(MAX_ENTRIES)
            } else {
                current
            }
            for (i in merged.indices) {
                p.putInteger("score.$i", merged[i].score)
                p.putFloat("depth.$i", merged[i].depth)
            }
            val rank = if (entered) {
                leaderboardRank(merged, score, depth)
            } else {
                null
            }
            p.putString(markerKey, encodeSubmission(entered, rank, score, depth))
            p.flush()
            return LeaderboardSubmission(entered, rank, merged, score, depth)
        }

        private fun qualifiesWith(entries: List<LeaderboardEntry>, score: Int): Boolean {
            if (score <= 0) return false
            if (entries.size < MAX_ENTRIES) return true
            return score > entries.last().score
        }

        private fun submittedKey(runId: String): String = "${Leaderboard.SUBMITTED_PREFIX}$runId"

        private fun requireRunId(runId: String): String {
            require(runId.isNotBlank()) { "runId must not be blank" }
            require(runId.none { it.code < 32 }) { "runId must not contain control characters" }
            return runId
        }

        private fun encodeSubmission(
            entered: Boolean,
            rank: Int?,
            score: Int,
            depth: Float,
        ): String {
            val fields = listOf(
                "1",
                if (entered) "1" else "0",
                rank?.toString() ?: "",
                score.toString(),
                depth.toString(),
            )
            return fields.joinToString("|") { escape(it) }
        }

        private fun decodeSubmission(raw: String): SubmissionMarker {
            val fields = decode(raw)
            require(fields.size == 5 && fields[0] == "1") { "invalid leaderboard submission marker" }
            val entered = booleanField(fields[1])
            val rank = fields[2].ifEmpty { null }?.toIntOrNull()
            if (fields[2].isNotEmpty() && rank == null) throw IllegalArgumentException("invalid leaderboard rank")
            if (rank != null && rank < 1) throw IllegalArgumentException("invalid leaderboard rank")
            val score = fields[3].toIntOrNull() ?: throw IllegalArgumentException("invalid leaderboard score")
            val depth = fields[4].toFloatOrNull() ?: throw IllegalArgumentException("invalid leaderboard depth")
            require(depth.isFinite()) { "invalid leaderboard depth" }
            if (entered && rank == null) throw IllegalArgumentException("missing leaderboard rank")
            return SubmissionMarker(entered, rank, score, depth)
        }

        private fun booleanField(value: String): Boolean = when (value) {
            "1" -> true
            "0" -> false
            else -> throw IllegalArgumentException("invalid boolean field")
        }

        private fun escape(value: String): String = buildString {
            for (character in value) {
                when (character) {
                    '\\' -> append("\\\\")
                    '|' -> append("\\p")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    else -> append(character)
                }
            }
        }

        private fun decode(raw: String): List<String> {
            require(raw.isNotEmpty()) { "empty encoded record" }
            val fields = ArrayList<String>()
            val current = StringBuilder()
            var escaped = false
            for (character in raw) {
                if (escaped) {
                    when (character) {
                        '\\' -> current.append('\\')
                        'p' -> current.append('|')
                        'n' -> current.append('\n')
                        'r' -> current.append('\r')
                        else -> throw IllegalArgumentException("invalid escape")
                    }
                    escaped = false
                } else {
                    when (character) {
                        '\\' -> escaped = true
                        '|' -> {
                            fields.add(current.toString())
                            current.setLength(0)
                        }
                        else -> current.append(character)
                    }
                }
            }
            if (escaped) throw IllegalArgumentException("invalid escape")
            fields.add(current.toString())
            return fields
        }

        private fun leaderboardRank(entries: List<LeaderboardEntry>, score: Int, depth: Float): Int =
            1 + entries.count { it.score > score || (it.score == score && it.depth > depth) }

        private data class SubmissionMarker(
            val entered: Boolean,
            val rank: Int?,
            val score: Int,
            val depth: Float,
        )
    }
}