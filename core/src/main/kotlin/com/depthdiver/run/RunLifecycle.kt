package com.depthdiver.run

/**
 * What a finished or abandoned run leaves behind, in the shape the game copies
 * into its own fields.
 *
 * The game used to do this copying inline in six places, each of which had to
 * remember which of depth/score/pearls/leaderboard came from where. Reducing the
 * result to one value means a terminal transition cannot update three of the four
 * and forget the fourth.
 */
data class RunOutcome(
    val depth: Float,
    val score: Int,
    val displayedPearls: Int,
    val leaderboardEntered: Boolean,
) {
    companion object {
        fun of(result: RunResult): RunOutcome = RunOutcome(
            depth = result.depth,
            score = result.score,
            displayedPearls = result.displayedPearls,
            leaderboardEntered = result.leaderboardEntered == true,
        )
    }
}

/** What the startup sweep found left over from a previous session. */
sealed interface StartupRecovery {
    /** Nothing was pending; the player simply has no run in progress. */
    data object Clean : StartupRecovery

    /** A run that had been left mid-flight was settled and its results applied. */
    data class Settled(val outcome: RunOutcome) : StartupRecovery

    /**
     * A run was still marked active. It is abandoned rather than resumed, so the
     * player starts fresh instead of inheriting a half-finished dive. Carries the
     * outcome if a pending result was also recovered.
     */
    data class AbandonedStale(val alsoRecovered: RunOutcome?) : StartupRecovery

    /**
     * Persistence threw. The game treats this as "no run", because a run it
     * cannot read is a run it cannot safely continue.
     */
    data object Failed : StartupRecovery
}

/**
 * Owns the active run and every transition a run can make.
 *
 * The game decides *whether* a transition is legal -- that is GameFlow's job and
 * it needs the whole state machine. This decides *what the transition does*, and
 * holds the ledger, so the rules live in one place instead of being implied by
 * the order of statements in the game.
 *
 * Every terminal transition takes the live depth and score rather than reading
 * them from somewhere, because a run has to be checkpointed before it is settled
 * or the settled result reports where the player was when the run opened instead
 * of where they finished.
 *
 * Persistence failures are swallowed and reported as null, which is what the game
 * already did: a run it cannot write is not a reason to crash, and it is not a
 * reason to keep half of one either.
 */
class RunLifecycle(
    private val settlement: RunSettlement,
    private val grantPearls: (Int) -> Unit = {},
) {

    /** The run in progress, or null when there is none. */
    var ledger: RunLedger? = null
        private set

    val hasActiveRun: Boolean get() = ledger != null

    /**
     * Opens a run and returns its ledger *without* holding it.
     *
     * Split from [hold] on purpose: the game begins a run before it resets the
     * world, and the reset detaches whatever was held. If the begin fails the
     * world must not be reset at all, so the two cannot be collapsed.
     */
    fun begin(initialWalletPearls: Int): RunLedger? = try {
        settlement.begin(initialWalletPearls = initialWalletPearls)
    } catch (_: Exception) {
        null
    }

    /** Takes ownership of a ledger produced by [begin]. */
    fun hold(ledger: RunLedger) {
        this.ledger = ledger
    }

    /** Drops the active run without settling it. */
    fun detach() {
        ledger = null
    }

    /**
     * Settles a run in progress and releases it.
     *
     * Null when there was no run to settle, or when persisting the terminal
     * result failed. The run stays held on failure so the caller can pause rather
     * than silently lose it.
     */
    fun end(reason: RunTerminalReason, depth: Float, score: Int): RunOutcome? {
        val active = ledger ?: return null
        val result = try {
            // Checkpoint first: the ledger is still at the run's opening depth,
            // so settling without this reports where the player started.
            settlement.checkpoint(active, depth, score)
            settlement.settle(active, reason)
        } catch (_: Exception) {
            return null
        }
        ledger = null
        return RunOutcome.of(result)
    }

    /**
     * Abandons a run in progress, keeping what it earned so far, and releases it.
     *
     * Null when there was nothing to abandon.
     */
    fun abandon(depth: Float, score: Int): RunOutcome? {
        val active = ledger ?: return null
        val result = try {
            settlement.checkpoint(active, depth, score)
            settlement.abandon(active)
        } catch (_: Exception) {
            return null
        }
        ledger = null
        return RunOutcome.of(result)
    }

    /**
     * Records progress and returns the numbers the game should mirror.
     *
     * Null when there is no active run, which is not an error: the simulation
     * ticks whether or not a ledger is open.
     */
    fun checkpoint(depth: Float, score: Int): RunProgress? {
        val active = ledger ?: return null
        return try {
            settlement.checkpoint(active, depth, score)
        } catch (_: Exception) {
            null
        }
    }

    /** The score and pearl count a checkpoint should mirror back into the game. */
    fun mirror(progress: RunProgress): Pair<Int, Int> =
        progress.score to progress.displayedPearls

    /**
     * Awards a one-off bonus, crediting the wallet as it goes.
     *
     * Returns the pearls actually awarded, which is 0 when there is no run to
     * award against or the bonus was already claimed this run.
     */
    fun awardBonus(key: String, pearls: Int, category: BonusCategory): Int {
        val active = ledger ?: return 0
        val awarded = try {
            active.awardBonus(key, pearls, category)
        } catch (_: Exception) {
            return 0
        }
        if (awarded <= 0) return 0
        grantPearls(awarded)
        return awarded
    }

    /**
     * Sweeps up whatever the previous session left behind: settles a pending
     * result, then abandons anything still marked active.
     */
    fun recoverOnStartup(): StartupRecovery {
        return try {
            val pending = settlement.recoverPending()?.let { RunOutcome.of(it) }
            val stale = settlement.active()
            if (stale != null) {
                settlement.abandon(stale)
                ledger = null
                return StartupRecovery.AbandonedStale(pending)
            }
            ledger = null
            if (pending != null) StartupRecovery.Settled(pending) else StartupRecovery.Clean
        } catch (_: Exception) {
            ledger = null
            StartupRecovery.Failed
        }
    }
}
