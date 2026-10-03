package com.pipo.robot.voice

/**
 * "Pipo, ..." — the local invocation check. This is pure text matching over whatever the phone's
 * own speech recogniser already transcribed; no audio ever touches this object, and nothing is
 * stored. The recogniser hears the phrase, we decide here whether it was a call to Pipo, and the
 * words after the call become a normal typed command for the deterministic parser.
 */
object VoiceTrigger {
    /** Recogniser spellings of "Pipo" ("peepo", "pippo", "pepo", ... people say it fast). */
    internal const val PIPO = "(?:pipo|pippo|peepo|pepo|pipeau|pee\\s?po|pi\\s?po|bebo|pibo|peapod|pivo)"

    /** Leading call form: "Pipo, ..." / "hey pipo ..." / "pipo please ...". */
    private val invocation =
        Regex("^\\s*(?:hey\\s+|ok(?:ay)?\\s+|hello\\s+)?$PIPO\\b[\\s,.!:;]*(?:please\\s+)?", RegexOption.IGNORE_CASE)

    /** True if [text] opens with a call to Pipo. */
    fun isInvocation(text: String): Boolean = invocation.containsMatchIn(text)

    /**
     * The command after the call: "Pipo, play Arz Kiya Hai" -> "play Arz Kiya Hai".
     * A bare "Pipo!" is a summon, not a command -> null.
     */
    fun command(text: String): String? {
        val m = invocation.find(text) ?: return null
        val rest = text.substring(m.range.last + 1).trim().trim(',', '.', '!', '?', ' ').trim()
        return rest.ifBlank { null }
    }
}
