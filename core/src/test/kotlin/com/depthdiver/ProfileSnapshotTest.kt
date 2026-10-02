package com.depthdiver

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The save file and how two copies of it are merged.
 *
 * The merge is the part worth testing. A cloud save is restored onto a device
 * that has been played on since the last upload, so "cloud wins" and "local
 * wins" are both wrong -- the first throws away a good session, the second
 * makes the feature do nothing. What matters is that progress never goes
 * backwards, and that a restore does not quietly reset a streak the player let
 * lapse.
 */
class ProfileSnapshotTest {

    private lateinit var prefs: TestPreferences

    @BeforeTest
    fun setUp() {
        prefs = TestPreferences("depthdiver-snapshot-test")
        Profile.prefsOverride = prefs
    }

    @AfterTest
    fun tearDown() {
        Profile.prefsOverride = null
    }

    private fun snapshot() = ProfileSnapshot.readFrom(prefs)

    // ---------- reading ----------

    @Test
    fun anUntouchedProfileSnapshotsToItsDefaults() {
        // A player who has not played has nothing worth uploading, and pretending
        // otherwise would create a cloud entry of zeroes.
        val snap = snapshot()
        assertTrue(!snap.isEmpty, "even an empty profile has default values to record")
        assertEquals(ProfileSnapshot.Value.FloatVal(0f), snap.values["bestDepth"])
    }

    @Test
    fun theManifestCoversEveryKeyTheProfileWrites() {
        // The reason the manifest is written out by hand: Preferences cannot
        // enumerate itself, so a reflective dump would go quietly partial.
        val written = setOf(
            "bestDepth", "bestScore", "bestRunPearls", "dailyDay", "difficulty",
            "dives", "highContrast", "landmarks.found", "lifetimePearls", "locale",
            "masterVolume", "musicMuted", "musicVolume", "reduceMotion", "screenShake",
            "sfxMuted", "sfxVolume", "streak.best", "streak.current",
            "streak.lastDay", "totalPearls",
        )
        val missing = written - ProfileSnapshot.MANIFEST.keys - ProfileSnapshot.LOCAL_ONLY
        assertTrue(missing.isEmpty(), "these keys are neither synced nor marked local-only: $missing")
    }

    @Test
    fun settingsAndCurrentStreakAreNeverUploaded() {
        // Syncing a per-device setting across devices would turn the music back
        // up on a phone the player had deliberately muted.
        for (key in listOf("musicVolume", "sfxMuted", "highContrast", "locale")) {
            assertTrue(
                key in ProfileSnapshot.LOCAL_ONLY,
                "$key would be uploaded and is not a preference to share",
            )
            assertTrue(key !in ProfileSnapshot.MANIFEST, "$key is in both the manifest and local-only")
        }
    }

    @Test
    fun theCurrentStreakStaysLocalSoACopyCannotReviveIt() {
        // The streak design promises a lapsed streak stays lapsed. Merging the
        // current count across devices would break that promise.
        assertTrue("streak.current" in ProfileSnapshot.LOCAL_ONLY)
        assertTrue("streak.lastDay" in ProfileSnapshot.LOCAL_ONLY)
    }

    @Test
    fun aRoundTripPreservesEveryValue() {
        Profile.grantPearls(500)
        prefs.putFloat("bestDepth", 412.5f)
        prefs.putInteger("dives", 37)
        prefs.putString("landmarks.found", "old_wreck,reef_arch")

        val before = snapshot()
        val fresh = TestPreferences("depthdiver-snapshot-roundtrip")
        ProfileSnapshot.writeTo(before, fresh)

        assertEquals(before.values["bestDepth"], ProfileSnapshot.readFrom(fresh).values["bestDepth"])
        assertEquals(before.values["dives"], ProfileSnapshot.readFrom(fresh).values["dives"])
        assertEquals(
            "old_wreck,reef_arch",
            (ProfileSnapshot.readFrom(fresh).values["landmarks.found"] as ProfileSnapshot.Value.StringVal).value,
        )
    }

    @Test
    fun aValueOfTheWrongTypeIsSkippedRatherThanFailing() {
        // What a format change looks like from here. One unreadable key should
        // not cost the player the other nine.
        prefs.putInteger("bestScore", 10)
        prefs.putString("bestDepth", "not a number")
        val snap = snapshot()
        assertEquals(ProfileSnapshot.Value.IntVal(10), snap.values["bestScore"])
        assertEquals(ProfileSnapshot.Value.FloatVal(0f), snap.values["bestDepth"], "the bad key fell back to its default")
    }

    // ---------- merging ----------

    @Test
    fun aStaleCloudCopyCannotUndoAGoodSession() {
        // The whole reason the merge exists. The cloud copy is from before this
        // session's records, and taking it wholesale would lose them.
        prefs.putFloat("bestDepth", 300f)
        prefs.putInteger("bestScore", 5000)
        prefs.putInteger("totalPearls", 40)
        val local = snapshot()
        val staleCloud = ProfileSnapshot(
            values = mapOf(
                "bestDepth" to ProfileSnapshot.Value.FloatVal(120f),
                "bestScore" to ProfileSnapshot.Value.IntVal(900),
                "totalPearls" to ProfileSnapshot.Value.IntVal(20),
            ),
        )

        val changed = ProfileSnapshot.mergeInto(staleCloud, prefs)
        assertTrue(changed.isEmpty(), "a stale cloud copy changed $changed")
        assertEquals(300f, prefs.getFloat("bestDepth", 0f))
        assertEquals(5000, prefs.getInteger("bestScore", 0))
    }

    @Test
    fun aNewerCloudCopyDoesBringProgressBack() {
        // The other direction: the player played on another device and this one
        // has not caught up. Doing nothing would make the feature pointless.
        val cloud = ProfileSnapshot(
            values = mapOf(
                "bestDepth" to ProfileSnapshot.Value.FloatVal(480f),
                "bestScore" to ProfileSnapshot.Value.IntVal(9000),
            ),
        )
        ProfileSnapshot.mergeInto(cloud, prefs)
        assertEquals(480f, prefs.getFloat("bestDepth", 0f))
        assertEquals(9000, prefs.getInteger("bestScore", 0))
    }

    @Test
    fun theMergeReportsOnlyWhatActuallyChanged() {
        // So a settings screen can honestly say the save was restored rather than
        // claiming a merge that did nothing.
        prefs.putFloat("bestDepth", 100f)
        prefs.putInteger("bestScore", 1000)
        val cloud = ProfileSnapshot(
            values = mapOf(
                "bestDepth" to ProfileSnapshot.Value.FloatVal(100f),
                "bestScore" to ProfileSnapshot.Value.IntVal(1000),
                "dives" to ProfileSnapshot.Value.IntVal(5),
            ),
        )
        val changed = ProfileSnapshot.mergeInto(cloud, prefs)
        assertEquals(listOf("dives"), changed)
    }

    @Test
    fun landmarksAreUnionedSoARestoreCannotUnfindOne() {
        // A landmark found on another device was found. Overwriting would tell a
        // player they had not seen something they had.
        prefs.putString("landmarks.found", "old_wreck,reef_arch")
        val cloud = ProfileSnapshot(
            values = mapOf(
                "landmarks.found" to ProfileSnapshot.Value.StringVal("whale_fall,old_wreck"),
            ),
        )
        ProfileSnapshot.mergeInto(cloud, prefs)
        assertEquals(
            setOf("old_wreck", "reef_arch", "whale_fall"),
            prefs.getString("landmarks.found", "").orEmpty().orEmpty().split(',').filter { it.isNotEmpty() }.toSet(),
        )
    }

    @Test
    fun aMergeKeepsTheLandmarksSortedSoTheValueIsStable() {
        // Unsorted union means the same set serialises differently each time,
        // which makes every restore look like a change.
        prefs.putString("landmarks.found", "whale_fall,reef_arch")
        val cloud = ProfileSnapshot(
            values = mapOf(
                "landmarks.found" to ProfileSnapshot.Value.StringVal("old_wreck"),
            ),
        )
        ProfileSnapshot.mergeInto(cloud, prefs)
        assertEquals("old_wreck,reef_arch,whale_fall", prefs.getString("landmarks.found", "").orEmpty())
    }

    @Test
    fun theDailyClaimIsNotRolledForwardByACopy() {
        // Otherwise a snapshot from another device could hand out today's bonus
        // a second time, which is a currency bug rather than a data one.
        Profile.claimDaily(12345)
        val cloud = ProfileSnapshot(values = mapOf("dailyDay" to ProfileSnapshot.Value.IntVal(99999)))
        ProfileSnapshot.mergeInto(cloud, prefs)
        assertEquals(12345, prefs.getInteger("dailyDay", 0))
    }

    @Test
    fun aSnapshotFromAnotherVersionIsIgnoredEntirely() {
        // Better to do nothing than to half-understand a format.
        val cloud = ProfileSnapshot(values = mapOf("bestScore" to ProfileSnapshot.Value.IntVal(9999)), version = 99)
        val changed = ProfileSnapshot.mergeInto(cloud, prefs)
        assertTrue(changed.isEmpty())
        assertEquals(0, prefs.getInteger("bestScore", 0))
    }

    @Test
    fun anEmptyCloudSnapshotChangesNothing() {
        val changed = ProfileSnapshot.mergeInto(ProfileSnapshot(), prefs)
        assertTrue(changed.isEmpty())
    }

    @Test
    fun theBestStreakIsProgressAndSoDoesMerge() {
        // Unlike the current streak, the best ever is a record and should come
        // back from a cloud copy.
        prefs.putInteger("streak.best", 4)
        val cloud = ProfileSnapshot(values = mapOf("streak.best" to ProfileSnapshot.Value.IntVal(11)))
        ProfileSnapshot.mergeInto(cloud, prefs)
        assertEquals(11, prefs.getInteger("streak.best", 0))
    }

    // ---------- the wire format ----------

    @Test
    fun aSnapshotRoundTripsThroughText() {
        prefs.putFloat("bestDepth", 412.5f)
        prefs.putInteger("dives", 37)
        prefs.putString("landmarks.found", "old_wreck,reef_arch")

        val decoded = ProfileSnapshot.decode(snapshot().encode())
        assertTrue(decoded != null, "our own output did not decode")
        assertEquals(ProfileSnapshot.Value.FloatVal(412.5f), decoded.values["bestDepth"])
        assertEquals(ProfileSnapshot.Value.IntVal(37), decoded.values["dives"])
    }

    @Test
    fun theSameStateAlwaysEncodesToTheSameText() {
        // Sorted keys, so a save file that has not changed does not look changed.
        prefs.putInteger("dives", 3)
        prefs.putFloat("bestDepth", 10f)
        assertEquals(snapshot().encode(), snapshot().encode())
    }

    @Test
    fun someoneElsesFileIsRejectedRatherThanHalfRead() {
        assertEquals(null, ProfileSnapshot.decode(""))
        assertEquals(null, ProfileSnapshot.decode("hello"))
        assertEquals(null, ProfileSnapshot.decode("not-a-save\t1\nbestScore\t99"))
        assertEquals(null, ProfileSnapshot.decode("depthdiver-save\tx\nbestScore\t1"))
    }

    @Test
    fun aTruncatedLineIsSkippedRatherThanFailingTheLoad() {
        // A cloud save that arrives half-written should restore what it can.
        val text = "depthdiver-save\t1\ndives\t12\nbestScore\n"
        val decoded = ProfileSnapshot.decode(text)
        assertTrue(decoded != null)
        assertEquals(ProfileSnapshot.Value.IntVal(12), decoded.values["dives"])
        assertEquals(null, decoded.values["bestScore"])
    }

    @Test
    fun aKeyThatIsNotInTheManifestIsIgnoredOnLoad() {
        // An old save carrying a key this build has never heard of.
        val text = "depthdiver-save\t1\ndives\t4\nfutureThing\t99"
        val decoded = ProfileSnapshot.decode(text)
        assertTrue(decoded != null)
        assertEquals(1, decoded.values.size)
    }

    @Test
    fun theTypeComesFromTheManifestNotTheText() {
        // Otherwise a corrupt line could write a string into a numeric key and
        // take the save down on the next read.
        val text = "depthdiver-save\t1\nbestScore\tnotanumber"
        val decoded = ProfileSnapshot.decode(text)
        assertTrue(decoded != null)
        assertEquals(null, decoded.values["bestScore"])
    }
}

