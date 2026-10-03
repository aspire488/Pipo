package com.pipo.robot.voice

/**
 * Puts one call to Pipo together from three on-device listeners hearing the same audio:
 *
 *  - the NAME spotter (a grammar of just his name's sound-alikes),
 *  - the COMMAND listener (a grammar of "open <your apps>", "lock ...", "play ..."): accurate,
 *  - the WORDS listener (free-form): only needed for what to play, and as a fallback.
 *
 * They finish at slightly different moments and in any order, and people pause after the name
 * ("Pipo... open Instagram"), so a call is: the name, and a command within [windowMs] either side.
 * The name alone with nothing after it brings him on screen. Lock and play are only ever acted on
 * with his name heard; opening an app you actually have is also allowed when the name came out
 * garbled but clearly preceded "open" ("whoa open instagram").
 *
 * Pure (time is passed in) so it is unit-tested.
 */
class CallAssembler(
    private val isInstalledApp: (String) -> Boolean,
    private val windowMs: Long = 4_000,
    /** How long to wait for the slower listener before acting on what we have. */
    private val settleMs: Long = 700,
    /** The name alone, then this much nothing: "come here". */
    private val summonAfterMs: Long = 2_200,
) {
    private var nameAt = -1L
    private var nameAlone = false
    private var grammarCmd: WakeAction? = null
    private var grammarAt = -1L
    private var words: WakeAction? = null
    private var wordsLead = -1
    private var wordsAt = -1L

    private var lockNow = false
    private var summonNow = false
    /** Did the free-form words also say "open"? (Guards against the command grammar inventing one.) */
    private var wordsSaidOpen = false

    fun onName(wake: WakeCommands.Wake, now: Long) {
        if (wake == WakeCommands.Wake.NONE) return
        nameAt = now; nameAlone = wake == WakeCommands.Wake.ALONE
        if (wake == WakeCommands.Wake.LOCK) lockNow = true
        if (wake == WakeCommands.Wake.SUMMON) summonNow = true
    }

    fun onCommand(heard: String, now: Long) {
        val c = WakeCommands.command(heard) ?: return
        grammarCmd = c; grammarAt = now
    }

    fun onWords(heard: String, now: Long) {
        val c = WakeCommands.command(heard) ?: WakeCommands.interpret(heard)?.takeIf { it != WakeAction.Summon } ?: return
        words = c; wordsAt = now; wordsLead = WakeCommands.wordsBeforeVerb(heard)
        wordsSaidOpen = c is WakeAction.Open
    }

    /** What to do now, if anything. Call after every event and regularly in between. */
    fun poll(now: Long): WakeAction? {
        expire(now)
        // "Pipo, lock" heard by the name listener itself: no waiting
        if (lockNow) return fire(WakeAction.Lock)
        if (summonNow) return fire(WakeAction.Summon)
        val named = nameAt >= 0
        val g = grammarCmd
        val w = words
        if (named) {
            when (g) {
                WakeAction.Lock -> return fire(g)
                // only if the free-form words heard "open" too: the command grammar alone has turned
                // a "lock" into "open instagram" before
                is WakeAction.Open -> if (wordsSaidOpen) return fire(g) else if (now - grammarAt >= settleMs * 2) { grammarCmd = null; return null }
                is WakeAction.Play -> {
                    // the grammar knows it's "play"; the words say what
                    val what = (w as? WakeAction.Play)?.what.orEmpty()
                    // free-form finishes later than the grammar on long phrases: give it a little longer
                    if (w is WakeAction.Play || now - grammarAt >= settleMs * 3) return fire(WakeAction.Play(what))
                    return null
                }
                else -> Unit
            }
            if (w != null && now - wordsAt >= settleMs) {
                // the grammar listener didn't catch it; the free-form words did
                if (w !is WakeAction.Open || isInstalledApp(w.app)) return fire(w)
            }
            if (nameAlone && g == null && w == null && now - nameAt >= summonAfterMs) return fire(WakeAction.Summon)
            return null
        }
        // no name heard: only "<garbled name> open <an app you have>"
        val open = (w as? WakeAction.Open)?.let { (g as? WakeAction.Open) ?: it }
        if (open != null && wordsLead in 1..2 && isInstalledApp(open.app) && now - maxOf(grammarAt, wordsAt) >= settleMs) return fire(open)
        return null
    }

    private fun expire(now: Long) {
        if (nameAt >= 0 && now - nameAt > windowMs) nameAt = -1
        if (grammarAt >= 0 && now - grammarAt > windowMs) { grammarCmd = null; grammarAt = -1 }
        if (wordsAt >= 0 && now - wordsAt > windowMs) { words = null; wordsAt = -1; wordsLead = -1 }
    }

    private fun fire(a: WakeAction): WakeAction {
        reset()
        return a
    }

    fun reset() {
        nameAt = -1; grammarCmd = null; grammarAt = -1; words = null; wordsAt = -1; wordsLead = -1
        lockNow = false; summonNow = false; wordsSaidOpen = false
    }
}
