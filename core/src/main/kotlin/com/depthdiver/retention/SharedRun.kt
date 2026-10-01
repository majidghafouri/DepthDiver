package com.depthdiver.retention

import com.depthdiver.RunSeed

/**
 * Reading a friend's run code out of whatever actually arrived.
 *
 * Sharing a code is only half a feature; the other half is the recipient, and
 * the recipient is the hard half. In practice nobody pastes a bare `DD-ABC-2`
 * into an app -- they copy a code out of a message, or copy the whole message,
 * or forward a screenshot's worth of text with a comment wrapped around it. So
 * this pulls the code out of its surroundings rather than demanding the whole
 * string be exactly the code, which is the difference between a feature that
 * works and one that reports an invalid code to someone who did everything right.
 */
object SharedRun {

    /**
     * A code in the middle of other text.
     *
     * Deliberately loose about the seed body -- base-36 is `[0-9A-Z]`, and being
     * strict about the length would break every code the game has already
     * handed out, since [RunSeed] emits variable-width base-36.
     */
    private val EMBEDDED = Regex("""DD-[0-9A-Za-z]+-[0-2]""", RegexOption.IGNORE_CASE)

    /** What the paste turned out to be. */
    sealed interface Invite {
        /** Nothing on the clipboard, or it was blank. */
        data object Nothing : Invite

        /** There was text, but no run code in it. */
        data object NotARunCode : Invite

        /** A run to play. [code] is the normalised text to pass to the game. */
        data class Playable(
            val seed: Long,
            val difficulty: Int,
            val code: String,
        ) : Invite
    }

    /**
     * Interpret clipboard [text] as a friend's run.
     *
     * Returns [Invite.Nothing] rather than guessing when the clipboard is
     * empty, so the UI can stay quiet instead of telling someone their own
     * clipboard is wrong.
     */
    fun fromClipboard(text: String?): Invite {
        if (text.isNullOrBlank()) return Invite.Nothing

        // A whole pasted message, or a bare code pasted into a field that
        // already had something in it.
        EMBEDDED.find(text)?.let { match ->
            RunSeed.decode(match.value)?.let { (seed, difficulty) ->
                return Invite.Playable(
                    seed = seed,
                    difficulty = difficulty,
                    code = RunSeed.encode(seed, difficulty),
                )
            }
        }

        // There was text and no code in it. One answer for both "not a code" and
        // "a malformed code", because from here they are the same problem and
        // the player's next move is identical.
        return Invite.NotARunCode
    }
}
