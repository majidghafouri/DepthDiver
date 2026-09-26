package com.depthdiver

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences
import com.depthdiver.run.RunId

/**
 * Local-first leaderboard (roadmap item: "High-score leaderboard").
 *
 * Storage is Preferences-backed and 100% offline — the top 5 runs are kept
 * in `depthdiver-leaderboard` prefs under "score.0".."score.4" (and matching
 * "depth.0".."depth.4"), newest-wins at equal score, sorted descending.
 *
 * CLOUD SEAM: to ship an online leaderboard later, introduce a class
 * implementing [LeaderboardService] (e.g. `CloudLeaderboard` backed by your
 * chosen backend) and swap the singleton in [Leaderboard.instance]. The
 * interface below is the only thing the game ever talks to, so no gameplay
 * code changes are required for the migration — see README roadmap for the
 * documented seam.
 */
interface LeaderboardService {
    /** @return top entries, best first. */
    fun top(): List<LeaderboardEntry>
    /** Record a finished run; returns the updated top list. */
    fun submit(score: Int, depth: Float): List<LeaderboardEntry>
    /** True if [score]/[depth] would make the local top 5. */
    fun qualifies(score: Int, depth: Float): Boolean

    fun submitOnce(runId: String, score: Int, depth: Float): LeaderboardSubmission {
        val entered = qualifies(score, depth)
        val entries = submit(score, depth)
        val rank = if (entered) {
            leaderboardRank(entries, score, depth)
        } else {
            null
        }
        return LeaderboardSubmission(entered, rank, entries, score, depth)
    }

    fun submitOnce(runId: RunId, score: Int, depth: Float): LeaderboardSubmission =
        submitOnce(runId.value.toString(), score, depth)
}

data class LeaderboardEntry(val score: Int, val depth: Float, val muted: Boolean = false)

internal fun leaderboardRank(entries: List<LeaderboardEntry>, score: Int, depth: Float): Int =
    1 + entries.count { it.score > score || (it.score == score && it.depth > depth) }

class LeaderboardSubmission(
    val entered: Boolean,
    val rank: Int?,
    val entries: List<LeaderboardEntry>,
    val score: Int,
    val depth: Float,
    val duplicate: Boolean = false,
) : AbstractList<LeaderboardEntry>() {
    val didEnter: Boolean get() = entered
    val enteredRank: Int? get() = rank
    val top: List<LeaderboardEntry> get() = entries
    val board: List<LeaderboardEntry> get() = entries
    val submitted: Boolean get() = entered
    val rankIndex: Int? get() = rank?.minus(1)

    override val size: Int get() = entries.size
    override fun get(index: Int): LeaderboardEntry = entries[index]

    override fun equals(other: Any?): Boolean = other is LeaderboardSubmission &&
        entered == other.entered && rank == other.rank && score == other.score &&
        depth == other.depth && entries == other.entries

    override fun hashCode(): Int {
        var result = entered.hashCode()
        result = 31 * result + (rank ?: 0)
        result = 31 * result + score
        result = 31 * result + depth.hashCode()
        result = 31 * result + entries.hashCode()
        return result
    }
}

typealias LeaderboardSubmitResult = LeaderboardSubmission

object Leaderboard : LeaderboardService {

    private const val MAX_ENTRIES = 5

    @Volatile
    private var customImpl: LeaderboardService? = null

    fun instance(): LeaderboardService = customImpl ?: this

    fun setCustomImpl(impl: LeaderboardService?) {
        customImpl = impl
    }

    /** Test seam: lets unit tests inject an isolated in-memory [Preferences]. */
    internal var prefsOverride: Preferences? = null

    /** One retained instance — see [com.depthdiver.Profile] note; a fresh wrapper
     *  per access would drop put/flush pairs. */
    private var retained: Preferences? = null

    private fun prefs(): Preferences =
        prefsOverride ?: (retained ?: Gdx.app.getPreferences("depthdiver-leaderboard").also { retained = it })

    override fun top(): List<LeaderboardEntry> {
        val p = prefs()
        val entries = ArrayList<LeaderboardEntry>(MAX_ENTRIES)
        for (i in 0 until MAX_ENTRIES) {
            val score = p.getInteger("score.$i", -1)
            if (score < 0) break
            entries.add(LeaderboardEntry(score, p.getFloat("depth.$i", 0f)))
        }
        return entries
    }

    override fun qualifies(score: Int, depth: Float): Boolean {
        if (score <= 0) return false
        val current = top()
        if (current.size < MAX_ENTRIES) return true
        return score > current.last().score
    }

    override fun submit(score: Int, depth: Float): List<LeaderboardEntry> {
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

    override fun submitOnce(runId: String, score: Int, depth: Float): LeaderboardSubmission {
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

    override fun submitOnce(runId: RunId, score: Int, depth: Float): LeaderboardSubmission =
        submitOnce(runId.value.toString(), score, depth)

    private fun qualifiesWith(entries: List<LeaderboardEntry>, score: Int): Boolean {
        if (score <= 0) return false
        if (entries.size < MAX_ENTRIES) return true
        return score > entries.last().score
    }

    private fun submittedKey(runId: String): String = "$SUBMITTED_PREFIX$runId"

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
        return fields.joinToString(FIELD_SEPARATOR) { escape(it) }
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

    private data class SubmissionMarker(
        val entered: Boolean,
        val rank: Int?,
        val score: Int,
        val depth: Float,
    )

    const val SUBMITTED_PREFIX = "submitted."
    private const val FIELD_SEPARATOR = "|"
}
