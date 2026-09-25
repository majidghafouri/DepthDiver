package com.depthdiver.run

import com.badlogic.gdx.Preferences
import com.depthdiver.Profile

class RunPersistenceException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

data class PendingRun(
    val ledger: RunLedger,
    val result: RunResult,
    val profileApplied: Boolean,
    val leaderboardApplied: Boolean,
) {
    val runId: RunId get() = result.runId
    val id: RunId get() = result.runId
    val terminal: Boolean get() = true
    val isTerminal: Boolean get() = true
}

object RunPersistenceKeys {
    const val SCHEMA_VERSION = "run.schemaVersion"
    const val SCHEMA_VERSION_VALUE = 1
    const val NEXT_RUN_ID = "run.nextId"
    const val ACTIVE = "run.active"
    const val PENDING = "run.pending"
    const val RESULT_PREFIX = "run.result."
    const val LEGACY_RUN_SAVED = "runSaved"
}

class RunPersistence(
    private val preferenceOverride: Preferences? = null,
) {
    private var resultScanInitialized = false
    private var highestResultId = 0L

    private fun prefs(): Preferences = preferenceOverride ?: Profile.preferences()

    fun initialize() {
        ensureInitialized()
    }

    fun schemaVersion(): Int {
        ensureInitialized()
        return RunPersistenceKeys.SCHEMA_VERSION_VALUE
    }

    fun nextRunId(): Long {
        ensureInitialized()
        return readNextId(prefs())
    }

    fun peekNextRunId(): Long = nextRunId()

    @JvmOverloads
    fun begin(
        day: Int = Profile.dailyDay(),
        dailyEligible: Boolean = Profile.claimedDailyDay() != day,
        initialWalletPearls: Int = 0,
    ): RunLedger {
        val p = prefs()
        ensureInitialized()
        val pending = readPending(p)
        if (pending != null) throw RunPersistenceException("cannot begin while a terminal run is pending")
        val active = readActiveSnapshot(p)
        if (active != null) throw RunPersistenceException("cannot begin while a run is active")
        val currentNext = readNextId(p)
        if (currentNext == Long.MAX_VALUE) throw RunPersistenceException("run id space exhausted")
        val ledger = RunLedger(
            runId = RunId(currentNext),
            settlementDay = day,
            dailyEligible = dailyEligible,
            initialWalletPearls = initialWalletPearls,
        )
        p.putLong(RunPersistenceKeys.NEXT_RUN_ID, currentNext + 1L)
        p.putString(RunPersistenceKeys.ACTIVE, RunCodec.encodeSnapshot(ledger.snapshot()))
        p.flush()
        return ledger
    }

    fun beginRun(
        day: Int = Profile.dailyDay(),
        dailyEligible: Boolean = Profile.claimedDailyDay() != day,
        initialWalletPearls: Int = 0,
    ): RunLedger = begin(day, dailyEligible, initialWalletPearls)

    fun loadActive(): RunLedger? {
        ensureInitialized()
        val snapshot = readActiveSnapshot(prefs()) ?: return null
        require(snapshot.disposition == RunDisposition.ACTIVE) { "active record is terminal" }
        return RunLedger(snapshot)
    }

    fun active(): RunLedger? = loadActive()

    fun saveActive(ledger: RunLedger) {
        val p = prefs()
        ensureInitialized()
        val pending = readPending(p)
        require(pending == null) { "terminal run is pending" }
        val active = readActiveSnapshot(p)
        require(active != null && active.runId == ledger.runId) { "run is not active" }
        require(ledger.isActive) { "cannot save a terminal run as active" }
        p.putString(RunPersistenceKeys.ACTIVE, RunCodec.encodeSnapshot(ledger.snapshot()))
        p.flush()
    }

    fun checkpoint(ledger: RunLedger): RunLedger {
        saveActive(ledger)
        return ledger
    }

    fun loadPending(): PendingRun? {
        ensureInitialized()
        return readPending(prefs())
    }

    fun pending(): PendingRun? = loadPending()

    fun hasPending(): Boolean = loadPending() != null

    fun persistTerminal(
        ledger: RunLedger,
        reason: RunTerminalReason = RunTerminalReason.COMPLETED,
        disposition: RunDisposition = RunDisposition.COMPLETED,
    ): PendingRun {
        val p = prefs()
        ensureInitialized()
        val existing = readPending(p)
        if (existing != null) {
            require(existing.runId == ledger.runId) { "another terminal run is pending" }
            return existing
        }
        val active = readActiveSnapshot(p)
        require(active != null && active.runId == ledger.runId) { "run is not active" }
        val result = ledger.result ?: ledger.settle(reason, disposition)
        require(result.disposition != RunDisposition.ACTIVE) { "terminal result is active" }
        val snapshot = ledger.snapshot()
        val pendingSnapshot = snapshot.copy(result = result)
        p.putString(RunPersistenceKeys.PENDING, RunCodec.encodePending(pendingSnapshot, false, false))
        p.flush()
        return PendingRun(RunLedger(pendingSnapshot), result, false, false)
    }

    fun markProfileApplied(runId: RunId): PendingRun {
        val p = prefs()
        ensureInitialized()
        val pending = requirePending(p, runId)
        if (pending.profileApplied) return pending
        p.putString(
            RunPersistenceKeys.PENDING,
            RunCodec.encodePending(pending.ledger.snapshot(), true, pending.leaderboardApplied),
        )
        p.flush()
        return PendingRun(pending.ledger, pending.result, true, pending.leaderboardApplied)
    }

    fun markLeaderboardApplied(
        runId: RunId,
        entered: Boolean,
        rank: Int?,
    ): PendingRun {
        val p = prefs()
        ensureInitialized()
        val pending = requirePending(p, runId)
        require(pending.result.disposition == RunDisposition.COMPLETED) { "abandoned run has no leaderboard phase" }
        require(entered || rank == null) { "non-qualifying submission cannot have a rank" }
        if (pending.leaderboardApplied) return pending
        val snapshot = pending.ledger.snapshot().copy(
            result = pending.result.copy(leaderboardEntered = entered, leaderboardRank = rank),
        )
        p.putString(
            RunPersistenceKeys.PENDING,
            RunCodec.encodePending(snapshot, pending.profileApplied, true),
        )
        p.flush()
        return PendingRun(RunLedger(snapshot), snapshot.result!!, pending.profileApplied, true)
    }

    fun storeResult(result: RunResult): RunResult {
        val p = prefs()
        ensureInitialized()
        val pending = requirePending(p, result.runId)
        require(pending.result.disposition == result.disposition) { "result disposition changed" }
        p.putString(resultKey(result.runId), RunCodec.encodeResult(result))
        p.flush()
        highestResultId = maxOf(highestResultId, result.runId.value)
        resultScanInitialized = true
        return result
    }

    fun saveResult(result: RunResult): RunResult = storeResult(result)

    fun loadResult(runId: RunId): RunResult? {
        ensureInitialized()
        val raw = readOptional(prefs(), resultKey(runId)) ?: return null
        val result = decodeResult(raw)
        require(result.runId == runId) { "result id mismatch" }
        return result
    }

    fun result(runId: RunId): RunResult? = loadResult(runId)

    fun clearPending(runId: RunId) {
        val p = prefs()
        ensureInitialized()
        val pending = readPending(p)
        if (pending == null) return
        require(pending.runId == runId) { "pending run id mismatch" }
        val active = readActiveSnapshot(p)
        require(active == null || active.runId == runId) { "active run does not match pending run" }
        p.remove(RunPersistenceKeys.PENDING)
        p.remove(RunPersistenceKeys.ACTIVE)
        p.flush()
    }

    fun clearTerminal(runId: RunId) = clearPending(runId)

    private fun ensureInitialized() {
        val p = prefs()
        val version = if (p.contains(RunPersistenceKeys.SCHEMA_VERSION)) {
            p.getInteger(RunPersistenceKeys.SCHEMA_VERSION, -1)
        } else {
            0
        }
        if (version < 0 || version > RunPersistenceKeys.SCHEMA_VERSION_VALUE) {
            throw RunPersistenceException("unsupported run schema version")
        }
        var active = readActiveSnapshot(p)
        var pending = readPending(p)
        if (active != null && active.disposition != RunDisposition.ACTIVE) {
            quarantine(p, RunPersistenceKeys.ACTIVE, RunCodec.encodeSnapshot(active))
            active = null
        }
        if (pending != null && pending.result.disposition == RunDisposition.ACTIVE) {
            quarantine(
                p,
                RunPersistenceKeys.PENDING,
                RunCodec.encodePending(pending.ledger.snapshot(), pending.profileApplied, pending.leaderboardApplied),
            )
            pending = null
        }
        if (active != null && pending != null && active.runId != pending.runId) {
            quarantine(p, RunPersistenceKeys.ACTIVE, RunCodec.encodeSnapshot(active))
            quarantine(p, RunPersistenceKeys.PENDING, RunCodec.encodePending(pending.ledger.snapshot(), pending.profileApplied, pending.leaderboardApplied))
            active = null
            pending = null
        }
        if (!resultScanInitialized) {
            val keys = p.get().keys.toList()
            for (key in keys) {
                if (!key.startsWith(RunPersistenceKeys.RESULT_PREFIX)) continue
                val resultId = key.removePrefix(RunPersistenceKeys.RESULT_PREFIX).toLongOrNull()
                if (resultId != null) highestResultId = maxOf(highestResultId, resultId)
                val raw = readString(p, key)
                try {
                    val result = decodeResult(raw)
                    require(result.runId.value.toString() == key.removePrefix(RunPersistenceKeys.RESULT_PREFIX)) {
                        "result key does not match result id"
                    }
                    highestResultId = maxOf(highestResultId, result.runId.value)
                } catch (_: Exception) {
                    quarantine(p, key, raw)
                }
            }
            resultScanInitialized = true
        }
        val storedNext = if (p.contains(RunPersistenceKeys.NEXT_RUN_ID)) {
            val value = p.getLong(RunPersistenceKeys.NEXT_RUN_ID, -1L)
            if (value <= 0L) throw RunPersistenceException("invalid next run id")
            value
        } else {
            1L
        }
        val highest = maxOf(
            active?.runId?.value ?: 0L,
            pending?.runId?.value ?: 0L,
            highestResultId,
        )
        val next = if (highest == Long.MAX_VALUE) {
            throw RunPersistenceException("run id space exhausted")
        } else {
            maxOf(storedNext, highest + 1L)
        }
        val needsWrite = version != RunPersistenceKeys.SCHEMA_VERSION_VALUE ||
            !p.contains(RunPersistenceKeys.NEXT_RUN_ID) ||
            next != storedNext ||
            p.contains(RunPersistenceKeys.LEGACY_RUN_SAVED)
        if (needsWrite) {
            p.putInteger(RunPersistenceKeys.SCHEMA_VERSION, RunPersistenceKeys.SCHEMA_VERSION_VALUE)
            p.putLong(RunPersistenceKeys.NEXT_RUN_ID, next)
            p.remove(RunPersistenceKeys.LEGACY_RUN_SAVED)
            p.flush()
        }
    }

    private fun requirePending(p: Preferences, runId: RunId): PendingRun {
        val pending = readPending(p) ?: throw RunPersistenceException("no pending run")
        require(pending.runId == runId) { "pending run id mismatch" }
        return pending
    }

    private fun readPending(p: Preferences): PendingRun? {
        val raw = readOptional(p, RunPersistenceKeys.PENDING) ?: return null
        return try {
            decodePending(raw)
        } catch (error: Exception) {
            quarantine(p, RunPersistenceKeys.PENDING, raw)
            null
        }
    }

    private fun readActiveSnapshot(p: Preferences): RunLedgerSnapshot? {
        val raw = readOptional(p, RunPersistenceKeys.ACTIVE) ?: return null
        return try {
            decodeSnapshot(raw)
        } catch (error: Exception) {
            quarantine(p, RunPersistenceKeys.ACTIVE, raw)
            null
        }
    }

    private fun quarantine(p: Preferences, key: String, raw: String) {
        p.putString("run.corrupt.${System.nanoTime()}.$key", raw)
        p.remove(key)
        p.flush()
    }

    private fun readNextId(p: Preferences): Long {
        if (!p.contains(RunPersistenceKeys.NEXT_RUN_ID)) return 1L
        val value = p.getLong(RunPersistenceKeys.NEXT_RUN_ID, -1L)
        if (value <= 0L) throw RunPersistenceException("invalid next run id")
        return value
    }

    private fun readOptional(p: Preferences, key: String): String? =
        if (p.contains(key)) readString(p, key) else null

    private fun readString(p: Preferences, key: String): String = p.getString(key, "")

    private fun resultKey(runId: RunId): String = RunPersistenceKeys.RESULT_PREFIX + runId.value

    private fun decodeSnapshot(raw: String): RunLedgerSnapshot = try {
        RunCodec.decodeSnapshot(raw)
    } catch (error: Exception) {
        throw RunPersistenceException("invalid active run record", error)
    }

    private fun decodePending(raw: String): PendingRun = try {
        RunCodec.decodePending(raw)
    } catch (error: Exception) {
        throw RunPersistenceException("invalid pending run record", error)
    }

    private fun decodeResult(raw: String): RunResult = try {
        RunCodec.decodeResult(raw)
    } catch (error: Exception) {
        throw RunPersistenceException("invalid run result record", error)
    }

    companion object {
        const val SCHEMA_VERSION_VALUE = 1
    }
}

typealias RunLedgerPersistence = RunPersistence
typealias RunLedgerStore = RunPersistence

private object RunCodec {
    private const val FIELD_SEPARATOR = "|"
    private const val RECORD_VERSION = "1"

    fun encodeSnapshot(snapshot: RunLedgerSnapshot): String {
        val result = snapshot.result
        val fields = listOf(
            RECORD_VERSION,
            snapshot.runId.value.toString(),
            snapshot.settlementDay.toString(),
            encodeBoolean(snapshot.dailyEligible),
            snapshot.depth.toString(),
            snapshot.score.toString(),
            snapshot.collectedPearls.toString(),
            snapshot.milestonePearls.toString(),
            snapshot.bossPearls.toString(),
            snapshot.challengePearls.toString(),
            snapshot.otherPearls.toString(),
            snapshot.dailyPearls.toString(),
            snapshot.walletPearls.toString(),
            encodeBoolean(snapshot.challengeQualified),
            encodeAwards(snapshot.bonusAwards),
            snapshot.terminalReason?.name ?: "",
            snapshot.disposition.name,
            if (result == null) "0" else "1",
            result?.let { if (it.dailyAwarded) "1" else "0" } ?: "",
            result?.leaderboardEntered?.let { if (it) "1" else "0" } ?: "",
            result?.leaderboardRank?.toString() ?: "",
        )
        return encodeFields(fields)
    }

    fun decodeSnapshot(raw: String): RunLedgerSnapshot {
        val fields = decodeFields(raw)
        require(fields.size == 21 && fields[0] == RECORD_VERSION) { "invalid run snapshot" }
        val runId = RunId(parseLong(fields[1], "run id"))
        val settlementDay = parseInt(fields[2], "settlement day")
        val dailyEligible = parseBoolean(fields[3], "daily eligibility")
        val depth = parseFloat(fields[4], "depth")
        val score = parseInt(fields[5], "score")
        val collected = parseInt(fields[6], "collected pearls")
        val milestone = parseInt(fields[7], "milestone pearls")
        val boss = parseInt(fields[8], "boss pearls")
        val challenge = parseInt(fields[9], "challenge pearls")
        val other = parseInt(fields[10], "other pearls")
        val daily = parseInt(fields[11], "daily pearls")
        val wallet = parseInt(fields[12], "wallet pearls")
        val challengeQualified = parseBoolean(fields[13], "challenge qualification")
        val awards = decodeAwards(fields[14])
        val reason = fields[15].ifEmpty { null }?.let { parseReason(it) }
        val disposition = parseDisposition(fields[16])
        val hasResult = parseBoolean(fields[17], "result marker")
        if (!hasResult) {
            require(fields[18].isEmpty() && fields[19].isEmpty() && fields[20].isEmpty()) { "invalid active result fields" }
            return RunLedgerSnapshot(
                runId, settlementDay, dailyEligible, depth, score, collected, milestone, boss,
                challenge, other, daily, wallet, challengeQualified, awards, reason, disposition, null,
            )
        }
        require(reason != null && disposition != RunDisposition.ACTIVE) { "invalid terminal result" }
        val dailyAwarded = parseBoolean(fields[18], "daily result")
        val leaderboardEntered = fields[19].ifEmpty { null }?.let { parseBoolean(it, "leaderboard result") }
        val leaderboardRank = fields[20].ifEmpty { null }?.let { parseInt(it, "leaderboard rank") }
        if (leaderboardRank != null) require(leaderboardRank >= 1) { "invalid leaderboard rank" }
        if (leaderboardEntered == false) require(leaderboardRank == null) { "invalid leaderboard rank" }
        val preDaily = collected + milestone + boss + challenge + other
        val result = RunResult(
            runId = runId,
            disposition = disposition,
            reason = reason,
            settlementDay = settlementDay,
            dailyEligible = dailyEligible,
            dailyAwarded = dailyAwarded,
            dailyPearls = daily,
            collectedPearls = collected,
            milestonePearls = milestone,
            bossPearls = boss,
            challengePearls = challenge,
            otherPearls = other,
            preDailyRunPearls = preDaily,
            displayedPearls = preDaily + daily,
            walletPearls = wallet,
            score = score,
            depth = depth,
            challengeQualified = challengeQualified,
            bonusAwards = awards,
            leaderboardEntered = leaderboardEntered,
            leaderboardRank = leaderboardRank,
        )
        return RunLedgerSnapshot(
            runId, settlementDay, dailyEligible, depth, score, collected, milestone, boss,
            challenge, other, daily, wallet, challengeQualified, awards, reason, disposition, result,
        )
    }

    fun encodePending(
        snapshot: RunLedgerSnapshot,
        profileApplied: Boolean,
        leaderboardApplied: Boolean,
    ): String {
        val result = snapshot.result
        require(result != null) { "pending snapshot has no result" }
        val fields = listOf(
            "P",
            RECORD_VERSION,
            encodeSnapshot(snapshot),
            if (profileApplied) "1" else "0",
            if (leaderboardApplied) "1" else "0",
            result.leaderboardEntered?.let { if (it) "1" else "0" } ?: "",
            result.leaderboardRank?.toString() ?: "",
        )
        return encodeFields(fields)
    }

    fun decodePending(raw: String): PendingRun {
        val fields = decodeFields(raw)
        require(fields.size == 7 && fields[0] == "P" && fields[1] == RECORD_VERSION) { "invalid pending record" }
        val snapshot = decodeSnapshot(fields[2])
        require(snapshot.result != null) { "pending record has no result" }
        val profileApplied = parseBoolean(fields[3], "profile phase")
        val leaderboardApplied = parseBoolean(fields[4], "leaderboard phase")
        val entered = fields[5].ifEmpty { null }?.let { parseBoolean(it, "leaderboard result") }
        val rank = fields[6].ifEmpty { null }?.let { parseInt(it, "leaderboard rank") }
        if (leaderboardApplied) {
            require(entered != null) { "missing leaderboard outcome" }
            if (entered) require(rank != null && rank >= 1) { "missing leaderboard rank" } else require(rank == null) { "invalid leaderboard rank" }
        }
        val result = if (leaderboardApplied) snapshot.result.copy(leaderboardEntered = entered, leaderboardRank = rank) else snapshot.result
        val finalSnapshot = snapshot.copy(result = result)
        validateSnapshot(finalSnapshot)
        return PendingRun(RunLedger(finalSnapshot), result, profileApplied, leaderboardApplied)
    }

    fun encodeResult(result: RunResult): String {
        val snapshot = RunLedgerSnapshot(
            runId = result.runId,
            settlementDay = result.settlementDay,
            dailyEligible = result.dailyEligible,
            depth = result.depth,
            score = result.score,
            collectedPearls = result.collectedPearls,
            milestonePearls = result.milestonePearls,
            bossPearls = result.bossPearls,
            challengePearls = result.challengePearls,
            otherPearls = result.otherPearls,
            dailyPearls = result.dailyPearls,
            walletPearls = result.walletPearls,
            challengeQualified = result.challengeQualified,
            bonusAwards = result.bonusAwards,
            terminalReason = result.reason,
            disposition = result.disposition,
            result = result,
        )
        validateSnapshot(snapshot)
        return encodeSnapshot(snapshot)
    }

    fun decodeResult(raw: String): RunResult = decodeSnapshot(raw).result
        ?: throw IllegalArgumentException("result record is active")

    private fun encodeAwards(awards: List<BonusAward>): String =
        awards.joinToString(";") { award ->
            val key = escape(award.key)
            "${key.length}:$key|${encodeFields(listOf(award.category.name, award.pearls.toString()))}"
        }

    private fun decodeAwards(raw: String): List<BonusAward> {
        if (raw.isEmpty()) return emptyList()
        val awards = ArrayList<BonusAward>()
        var offset = 0
        while (offset < raw.length) {
            val lengthSeparator = raw.indexOf(':', offset)
            require(lengthSeparator > offset) { "invalid bonus award" }
            val length = raw.substring(offset, lengthSeparator).toIntOrNull()
                ?: throw IllegalArgumentException("invalid bonus award")
            require(length > 0) { "invalid bonus award" }
            val keyStart = lengthSeparator + 1
            val keyEnd = keyStart + length
            require(keyEnd < raw.length && raw[keyEnd] == '|') { "invalid bonus award" }
            val recordEnd = raw.indexOf(';', keyEnd + 1).let { if (it < 0) raw.length else it }
            val fields = decodeFields(raw.substring(keyEnd + 1, recordEnd))
            require(fields.size == 2) { "invalid bonus award" }
            val key = decodeFields(raw.substring(keyStart, keyEnd)).single()
            awards.add(BonusAward(key, parseCategory(fields[0]), parseInt(fields[1], "bonus pearls")))
            offset = if (recordEnd == raw.length) raw.length else recordEnd + 1
        }
        return awards
    }

    private fun parseCategory(raw: String): BonusCategory = try {
        BonusCategory.valueOf(raw)
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("invalid bonus category")
    }

    private fun parseReason(raw: String): RunTerminalReason = try {
        RunTerminalReason.valueOf(raw)
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("invalid terminal reason")
    }

    private fun parseDisposition(raw: String): RunDisposition = try {
        RunDisposition.valueOf(raw)
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("invalid run disposition")
    }

    private fun encodeBoolean(value: Boolean): String = if (value) "1" else "0"

    private fun parseBoolean(raw: String, label: String): Boolean = when (raw) {
        "1" -> true
        "0" -> false
        else -> throw IllegalArgumentException("invalid $label")
    }

    private fun parseInt(raw: String, label: String): Int = raw.toIntOrNull()
        ?: throw IllegalArgumentException("invalid $label")

    private fun parseLong(raw: String, label: String): Long = raw.toLongOrNull()
        ?: throw IllegalArgumentException("invalid $label")

    private fun parseFloat(raw: String, label: String): Float = raw.toFloatOrNull()?.takeIf { it.isFinite() }
        ?: throw IllegalArgumentException("invalid $label")

    private fun encodeFields(fields: List<String>): String = fields.joinToString(FIELD_SEPARATOR) { escape(it) }

    private fun decodeFields(raw: String): List<String> {
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
                    's' -> current.append(';')
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

    private fun escape(value: String): String = buildString {
        for (character in value) {
            when (character) {
                '\\' -> append("\\\\")
                '|' -> append("\\p")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                ';' -> append("\\s")
                else -> append(character)
            }
        }
    }
}
