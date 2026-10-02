package app.deepdepthdiver

import android.app.Activity
import android.util.Log
import com.badlogic.gdx.Gdx
import com.depthdiver.CloudSaveService
import com.depthdiver.ProfileSnapshot
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.games.AnnotatedData
import com.google.android.gms.games.Games
import com.google.android.gms.games.SnapshotsClient
import com.google.android.gms.games.snapshot.Snapshot
import com.google.android.gms.games.snapshot.SnapshotMetadata
import com.google.android.gms.games.snapshot.SnapshotMetadataChange
import com.google.android.gms.games.snapshot.SnapshotMetadataBuffer
import com.google.android.gms.tasks.OnSuccessListener

/**
 * Cloud save on Google Play Games Services Saved Games.
 *
 * Two decisions here are the reason this is not a thin wrapper.
 *
 * **Merge, do not replace.** A snapshot is restored onto a device that has
 * probably been played on since the last upload. Restoring it verbatim would
 * throw those runs away, so [ProfileSnapshot.mergeInto] reconciles: progress
 * takes the better of the two, discovered landmarks union, and anything chosen
 * on this device stays chosen. A cloud save that can undo a good session is worse
 * than no cloud save at all.
 *
 * **Quiet when signed out.** Every Play Games call needs a signed-in account, and
 * sign-in is not something this game can assume or request behind someone's
 * back. The account comes from [accountProvider] and every entry point returns
 * quietly when there is none. Cloud save is a bonus; a player without it loses
 * nothing and sees no error.
 */
class GpgsCloudSave(
    private val activity: Activity,
    /**
     * Supplies the signed-in account, or null when there isn't one.
     *
     * A function rather than a value because sign-in can happen long after this
     * is constructed, and a captured account would go stale.
     */
    private val accountProvider: () -> GoogleSignInAccount? = {
        GoogleSignIn.getLastSignedInAccount(activity)
    },
    private val snapshotName: String = DEFAULT_SNAPSHOT,
) : CloudSaveService {

    private fun snapshots(): SnapshotsClient? = try {
        accountProvider()?.let { Games.getSnapshotsClient(activity, it) }
    } catch (e: Exception) {
        Log.w(TAG, "no Play Games client available", e)
        null
    }

    override fun isAvailable(): Boolean = snapshots() != null

    override fun snapshot(): ByteArray =
        ProfileSnapshot.readFrom(prefs()).encode().toByteArray()

    override fun restore(bytes: ByteArray): Boolean =
        ProfileSnapshot.decode(String(bytes, Charsets.UTF_8))
            ?.let {
                ProfileSnapshot.mergeInto(it, prefs())
                true
            }
            ?: false

    /**
     * Write the current save.
     *
     * Opens the existing snapshot rather than writing a new one, because
     * committing over a snapshot another device uploaded would destroy exactly
     * the work [ProfileSnapshot.mergeInto] exists to reconcile. The conflict
     * branch is what makes that true rather than merely intended: if another
     * device wrote since we looked, their save is merged in before we commit.
     */
    override fun upload() {
        val client = snapshots() ?: return
        try {
            client.open(snapshotName, CREATE_IF_MISSING).onOpened { opened ->
                val conflict = opened.conflict?.snapshot
                if (opened.isConflict && conflict != null) {
                    resolveAndCommit(client, conflict)
                } else {
                    commit(client, opened.data)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "cloud upload threw", e)
        }
    }

    /**
     * Another device wrote first: fold their save into ours, then commit.
     *
     * Discarding and reopening is deliberate. It is the only path that produces
     * a snapshot the service will accept, and the merge has already kept
     * everything worth keeping.
     */
    private fun resolveAndCommit(client: SnapshotsClient, theirs: Snapshot) {
        val theirSnapshot = runCatching {
            String(theirs.snapshotContents.readFully(), Charsets.UTF_8)
        }.getOrNull()?.let { ProfileSnapshot.decode(it) }

        val merged = if (theirSnapshot == null) {
            ProfileSnapshot.readFrom(prefs())
        } else {
            // Merge into prefs first, so the write below carries their progress too.
            ProfileSnapshot.mergeInto(theirSnapshot, prefs())
            ProfileSnapshot.readFrom(prefs())
        }

        client.discardAndClose(theirs)
        client.open(snapshotName, CREATE_IF_MISSING).onOpened { retried ->
            val reopened = retried.data
            if (reopened == null) {
                Log.w(TAG, "Play Games returned nothing after resolving a conflict")
            } else if (!reopened.snapshotContents.writeBytes(merged.encode().toByteArray())) {
                Log.w(TAG, "snapshot would not accept the merged save")
            } else {
                client.commitAndClose(reopened, metadataChange(reopened))
                    .addOnFailureListener { Log.w(TAG, "merged upload failed", it) }
            }
        }
    }

    /**
     * Metadata for the commit.
     *
     * Play Games requires a change object alongside every commit, and refuses one
     * built from scratch, so this is derived from the snapshot being written. The
     * description carries the best depth, which is what makes the snapshot
     * readable in the Play Console rather than an opaque blob.
     */
    private fun metadataChange(snapshot: Snapshot): SnapshotMetadataChange =
        SnapshotMetadataChange.Builder()
            .fromMetadata(snapshot.metadata)
            .setDescription(ProfileSnapshot.readFrom(prefs()).let { snap ->
                val depth = (snap.values["bestDepth"] as? ProfileSnapshot.Value.FloatVal)?.value ?: 0f
                "${depth.toInt()} m"
            })
            .setProgressValue(progressValue())
            .build()

    /**
     * A monotonic value Play can show as a progress bar.
     *
     * Best depth rather than dive count: it rises for as long as a player is
     * still finding new water, and a dive count would tick up on a bad run too.
     */
    private fun progressValue(): Long {
        val depth = (ProfileSnapshot.readFrom(prefs()).values["bestDepth"] as? ProfileSnapshot.Value.FloatVal)
            ?.value ?: 0f
        return depth.toLong().coerceAtLeast(0L)
    }

    private fun commit(client: SnapshotsClient, snapshot: Snapshot?) {
        if (snapshot == null) {
            Log.w(TAG, "Play Games returned no snapshot to commit")
            return
        }
        if (!snapshot.snapshotContents.writeBytes(snapshot())) {
            Log.w(TAG, "snapshot would not accept the save")
            return
        }
        client.commitAndClose(snapshot, metadataChange(snapshot))
            .addOnFailureListener { Log.w(TAG, "cloud upload failed", it) }
    }

    /**
     * Read the saved snapshot and merge it in.
     *
     * Reports the outcome through [onRestored], because a restore that silently
     * changed nothing would leave the player thinking it worked.
     */
    override fun download() {
        val client = snapshots() ?: return
        try {
            client.load(FORCE_DOWNLOAD).onListed { listed ->
                val newest = newestFor(listed.get())
                if (newest == null) {
                    // Nothing saved yet is the normal first-run case, not
                    // something to report.
                    onRestored?.invoke(RestoreOutcome.NothingNewer)
                    return@onListed
                }
                client.open(newest).onOpened { opened ->
                    val snapshot = opened.data
                    val decoded = snapshot?.let {
                        runCatching { String(it.snapshotContents.readFully(), Charsets.UTF_8) }.getOrNull()
                    }?.let { ProfileSnapshot.decode(it) }

                    if (decoded == null) {
                        onRestored?.invoke(RestoreOutcome.Unreadable)
                    } else {
                        val changed = ProfileSnapshot.mergeInto(decoded, prefs())
                        onRestored?.invoke(
                            if (changed.isEmpty()) RestoreOutcome.NothingNewer
                            else RestoreOutcome.Merged(changed),
                        )
                    }
                    // Closed either way. Leaving it open would hold the service's
                    // lock on the snapshot and block the next write.
                    snapshot?.let { client.discardAndClose(it) }
                }.addOnFailureListener {
                    Log.w(TAG, "could not open the saved snapshot", it)
                    onRestored?.invoke(RestoreOutcome.NothingNewer)
                }
            }.addOnFailureListener {
                Log.w(TAG, "no cloud snapshot to read", it)
                onRestored?.invoke(RestoreOutcome.NothingNewer)
            }
        } catch (e: Exception) {
            Log.w(TAG, "cloud download threw", e)
            onRestored?.invoke(RestoreOutcome.NothingNewer)
        }
    }

    override fun syncNow() {
        upload()
        download()
    }

    /**
     * The saved snapshot for our name, or null if there is not one yet.
     *
     * Uses the [SnapshotMetadataBuffer] iterator rather than indexing it, because
     * the buffer is a cursor and its size is not reliably known.
     */
    private fun newestFor(buffer: SnapshotMetadataBuffer?): SnapshotMetadata? {
        if (buffer == null) return null
        val matching = mutableListOf<SnapshotMetadata>()
        val iterator = buffer.iterator()
        while (iterator.hasNext()) {
            val metadata = iterator.next()
            if (metadata != null && metadata.uniqueName == snapshotName) matching += metadata
        }
        return matching.maxByOrNull { it.lastModifiedTimestamp }
    }

    /**
     * Told what a restore did, so the UI can say something true.
     *
     * Null when nobody is listening, which is the usual case: sync runs in the
     * background and there is no screen to report to.
     */
    var onRestored: ((RestoreOutcome) -> Unit)? = null

    /** What actually happened on a restore. */
    sealed interface RestoreOutcome {
        /** Keys that moved. Empty is reported as [NothingNewer] instead. */
        data class Merged(val keys: List<String>) : RestoreOutcome

        /** The cloud copy was older than this device, so nothing changed. */
        data object NothingNewer : RestoreOutcome

        /** The snapshot was there but not something we can read. */
        data object Unreadable : RestoreOutcome
    }

    private fun prefs() = Gdx.app.getPreferences(PREFS)

    companion object {
        private const val TAG = "DepthDiverCloud"
        private const val PREFS = "depthdiver"
        private const val CREATE_IF_MISSING = true
        private const val FORCE_DOWNLOAD = true
        const val DEFAULT_SNAPSHOT = "depthdiver-save"
    }
}

/**
 * `Task.onSuccessListener` with the generic spelled out.
 *
 * Kotlin cannot infer the platform type through Play Games' `DataOrConflict`,
 * and naming it here keeps the four call sites readable instead of carrying a
 * nested generic across a multi-line lambda.
 */
private inline fun com.google.android.gms.tasks.Task<SnapshotsClient.DataOrConflict<Snapshot>>.onOpened(
    crossinline action: (SnapshotsClient.DataOrConflict<Snapshot>) -> Unit,
) = addOnSuccessListener(
    OnSuccessListener<SnapshotsClient.DataOrConflict<Snapshot>> { action(it) },
)

/** The same, for the snapshot-listing task. */
private inline fun com.google.android.gms.tasks.Task<AnnotatedData<SnapshotMetadataBuffer>>.onListed(
    crossinline action: (AnnotatedData<SnapshotMetadataBuffer>) -> Unit,
) = addOnSuccessListener(OnSuccessListener<AnnotatedData<SnapshotMetadataBuffer>> { action(it) })