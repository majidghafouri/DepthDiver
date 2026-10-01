package com.depthdiver.retention

import com.depthdiver.RunSeed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Reading a friend's run code.
 *
 * The share side of this feature already worked. What is easy to get wrong is the
 * receive side, because the text that arrives is whatever the sender's friend
 * group happened to do with it -- so most of these are about tolerating a
 * plausible paste rather than only an ideal one.
 */
class SharedRunTest {

    private val seed = 0x5EED_1234L
    private val code = RunSeed.encode(seed, 1)

    @Test
    fun aBareCodeIsPlayable() {
        val invite = assertIs<SharedRun.Invite.Playable>(SharedRun.fromClipboard(code))
        assertEquals(seed, invite.seed)
        assertEquals(1, invite.difficulty)
    }

    @Test
    fun aCodeInsideAChatMessageIsFound() {
        // The common case, and the one a strict reader gets wrong.
        val message = "dive this one, it's brutal: $code :("
        val invite = assertIs<SharedRun.Invite.Playable>(SharedRun.fromClipboard(message))
        assertEquals(seed, invite.seed)
    }

    @Test
    fun aCodeOnItsOwnLineIsFound() {
        val text = "try my run\n\n$code\n\nno spoilers"
        assertIs<SharedRun.Invite.Playable>(SharedRun.fromClipboard(text))
    }

    @Test
    fun theCodeIsCaseInsensitiveBecausePhonesDoThat() {
        val invite = assertIs<SharedRun.Invite.Playable>(SharedRun.fromClipboard(code.lowercase()))
        assertEquals(seed, invite.seed)
    }

    @Test
    fun theReturnedCodeIsNormalised() {
        // Whatever came in, what the game is handed should be the canonical form,
        // so the same run always produces the same string.
        val invite = assertIs<SharedRun.Invite.Playable>(SharedRun.fromClipboard("  ${code.lowercase()}  "))
        assertEquals(code, invite.code)
    }

    @Test
    fun anEmptyClipboardIsNotAnError() {
        // Telling someone their own blank clipboard is invalid is the kind of
        // small rudeness that makes a feature get turned off.
        assertIs<SharedRun.Invite.Nothing>(SharedRun.fromClipboard(null))
        assertIs<SharedRun.Invite.Nothing>(SharedRun.fromClipboard(""))
        assertIs<SharedRun.Invite.Nothing>(SharedRun.fromClipboard("   \n  "))
    }

    @Test
    fun proseWithNoCodeIsReportedAsSuch() {
        assertIs<SharedRun.Invite.NotARunCode>(SharedRun.fromClipboard("see you at the reef tomorrow"))
    }

    @Test
    fun aMalformedCodeIsNotMistakenForAValidOne() {
        for (bad in listOf("DD-ABC", "DD-ABC-9", "DD-!-2", "nope")) {
            assertIs<SharedRun.Invite.NotARunCode>(SharedRun.fromClipboard(bad), "\"$bad\" was accepted")
        }
    }

    @Test
    fun aCodeInBracketsIsStillFound() {
        assertIs<SharedRun.Invite.Playable>(SharedRun.fromClipboard("($code)"))
    }

    @Test
    fun aValidCodeAlongsideAnInvalidOneUsesTheValidOne() {
        // A forwarded thread may contain an older, broken paste.
        val text = "old one DD-ZZZ-7 didn't work but $code is the good one"
        assertIs<SharedRun.Invite.Playable>(SharedRun.fromClipboard(text))
    }

    @Test
    fun everyDifficultySurvivesTheRoundTrip() {
        for (difficulty in 0..2) {
            val c = RunSeed.encode(seed, difficulty)
            val invite = assertIs<SharedRun.Invite.Playable>(
                SharedRun.fromClipboard("run: $c"),
                "difficulty $difficulty did not survive",
            )
            assertEquals(difficulty, invite.difficulty)
        }
    }

    @Test
    fun anAbsentCodeIsNotAZeroSeed() {
        // A default of 0 would quietly play some other player's world rather than
        // admitting the paste did not work.
        val invite = SharedRun.fromClipboard("nothing here")
        assertTrue(invite is SharedRun.Invite.NotARunCode)
    }
}
