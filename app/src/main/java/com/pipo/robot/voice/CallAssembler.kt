package com.pipo.robot.voice

/**
 * Puts one call to Pipo together from three on-device listeners hearing the same audio:
 *
 *  - the NAME spotter (a grammar of just his name's sound-alikes),
 *  - the COMMAND listener (a grammar of "open <your apps>", "lock ...", "play ...", "come here"),
 *  - the WORDS listener (free-form): what to play, and a second opinion.
 *
 * Both grammar listeners can only answer with one of their own phrases, so room noise gets forced
 * onto them. Device log 2026-10-04: the name spotter alone heard "hippo lock" out of nothing and
 * locked the phone; "it lock it" from the command grammar combined with a stray "pippin" three
 * seconds later and locked it; a lone "hippo" popped him up. A real call is ONE utterance, so its
 * pieces land together: "Pipo, come here" gave name "hippo come here" and command "come here"
 * 20 ms apart. So an action needs two listeners agreeing within [sameUtteranceMs]:
 *
 *  - Lock:   the spotter heard "<name> lock" and the command grammar or the words heard "lock"; or
 *            the spotter heard the name and the command grammar heard "lock" (any order, close together).
 *  - Summon: the spotter heard "<name> come here / on screen" and another listener heard it too; or
 *            the name alone twice ("Pipo… Pipo!"); or the name and the words heard just the name.
 *  - Open:   the name, the command grammar heard "open <app>", and the words heard "open" too.
 *  - Play:   the name, and the words heard "play ..." (they also say what).
 *
 * Pure (time is passed in) so it is unit-tested.
 */
class CallAssembler(
    private val isInstalledApp: (String) -> Boolean,
    /** Exact (spaces ignored) match of a launcher app's name, for the stricter grammar-only "open". */
    private val isExactApp: (String) -> Boolean = isInstalledApp,
    private val windowMs: Long = 4_000,
    /** How long to wait for the slower listener before acting on what we have. */
    private val settleMs: Long = 700,
    /** Pieces of one spoken call arrive this close together (words trail the name by up to ~1.5 s). */
    private val sameUtteranceMs: Long = 2_000,
) {
    private var nameAt = -1L
    private var nameWake = WakeCommands.Wake.NONE
    /** How many of the spotter's guesses agreed on that reading ("hippo lock, hippo log, people lock" = 3). */
    private var nameAgree = 0
    /** When the name was last heard on its own, to recognise "Pipo… Pipo!". */
    private var aloneAt = -1L
    private var grammarCmd: WakeAction? = null
    private var grammarAt = -1L
    private var grammarSummonAt = -1L
    /** The command grammar's phrase itself had his name in it ("hippo open claude"). */
    private var grammarNamed = false
    private var words: WakeAction? = null
    private var wordsCall: WakeAction? = null
    private var wordsLead = -1
    private var wordsAt = -1L
    private var wordsSaidOpen = false
    private var wordsSaidSummon = false
    /** The free-form listener's last text, whatever it was, and when. */
    private var rawWords = ""
    private var rawAt = -1L

    fun onName(wake: WakeCommands.Wake, now: Long, agree: Int = 1) {
        if (wake == WakeCommands.Wake.NONE) return
        nameAgree = agree
        if (wake == WakeCommands.Wake.ALONE) {
            // a second, separate "Pipo" soon after the first: you're calling him
            if (aloneAt >= 0 && now - aloneAt in 400..windowMs) { nameAt = now; nameWake = WakeCommands.Wake.SUMMON; twiceCalled = true; aloneAt = -1; return }
            aloneAt = now
        }
        nameAt = now; nameWake = wake
    }

    fun onCommand(heard: String, now: Long) {
        if (SUMMON_SOUND.containsMatchIn(heard)) grammarSummonAt = now
        val c = WakeCommands.command(heard) ?: return
        grammarCmd = c; grammarAt = now
        grammarNamed = WakeCommands.wake(heard.replace("[unk]", "").trim()) != WakeCommands.Wake.NONE
    }

    fun onWords(heard: String, now: Long) {
        if (heard.isNotBlank()) { rawWords = heard.lowercase(); rawAt = now }
        val call = WakeCommands.interpret(heard)
        val c = WakeCommands.command(heard) ?: call?.takeIf { it != WakeAction.Summon }
        val summonish = call == WakeAction.Summon || Regex("^(come here|here|on screen|onscreen)$").matches(heard.trim())
        // the words may catch only the verb ("open", "opens") while the app's name lands in the command grammar
        val saidOpen = c is WakeAction.Open || Regex("^([a-z']+ ){0,3}(open|opens|opened|opening|launch)( |$)").containsMatchIn(heard.trim().lowercase())
        if (c == null && call == null && !summonish && !saidOpen) return
        words = c; wordsCall = call; wordsAt = now; wordsLead = WakeCommands.wordsBeforeVerb(heard)
        wordsSaidOpen = saidOpen
        wordsSaidSummon = summonish
    }

    private fun close(a: Long, b: Long) = a >= 0 && b >= 0 && kotlin.math.abs(a - b) <= sameUtteranceMs

    /** What to do now, if anything. Call after every event and regularly in between. */
    fun poll(now: Long): WakeAction? {
        expire(now)
        val named = nameAt >= 0
        val g = grammarCmd
        val w = words

        // Another listener says this breath was an "open"/"play": the spotter often hears "open" as
        // "on log" / "lock it" (device 10:03:28 "Pipo, open GitHub" locked the phone). Then it is not a lock.
        val otherCommand = ((g is WakeAction.Open || g is WakeAction.Play) && close(nameAt, grammarAt)) ||
            (close(nameAt, rawAt) && OPEN_OR_PLAY.containsMatchIn(rawWords))

        // ---- lock
        if (named && nameWake == WakeCommands.Wake.LOCK) {
            if (g == WakeAction.Lock && close(nameAt, grammarAt)) return fire(WakeAction.Lock)
            if ((w == WakeAction.Lock || wordsCall == WakeAction.Lock) && close(nameAt, wordsAt)) return fire(WakeAction.Lock)
            // the words heard the lock's SOUND in the same breath ("people bullock", "epilogue")
            if (!otherCommand && close(nameAt, rawAt) && LOCK_SOUND.containsMatchIn(rawWords)) return fire(WakeAction.Lock)
            // every one of the spotter's guesses was a lock, and the words heard someone actually speaking
            // ("hey bulldog"). Room noise forced onto the grammar never comes with speech behind it.
            if (!otherCommand && nameAgree >= 2 && close(nameAt, rawAt) && speech(rawWords)) return fire(WakeAction.Lock)
        }
        if (named && (nameWake == WakeCommands.Wake.ALONE || nameWake == WakeCommands.Wake.LOCK) && g == WakeAction.Lock && close(nameAt, grammarAt)) return fire(WakeAction.Lock)
        if (wordsCall == WakeAction.Lock && (named && close(nameAt, wordsAt) || g == WakeAction.Lock && close(grammarAt, wordsAt))) return fire(WakeAction.Lock)

        // ---- come here: the spotter heard "<name> on screen / come here" (a two-part phrase, which room
        // noise never produced in the logs — the uninvited pop-ups were all a bare name), or the name twice
        if (named && nameWake == WakeCommands.Wake.SUMMON) return fire(WakeAction.Summon)
        if (named && wordsCall == WakeAction.Summon && close(nameAt, wordsAt)) return fire(WakeAction.Summon)

        if (!named) {
            // no name heard: only "<garbled name> open <an app you have>", from both listeners
            val open = (w as? WakeAction.Open)?.let { (g as? WakeAction.Open) }
            if (open != null && wordsLead in 1..2 && isInstalledApp(open.app) && now - maxOf(grammarAt, wordsAt) >= settleMs) return fire(open)
            return null
        }
        // ---- open: the grammar heard "open <app>", and the words heard "open" too — or a word of the
        // app's name ("open you tube" -> "but you know")
        if (g is WakeAction.Open) {
            val appWords = g.app.split(' ').filter { it.length >= 3 || it == "you" }.toSet()
            val sharesName = close(grammarAt, rawAt) && rawWords.split(' ').any { it in appWords }
            if ((wordsSaidOpen && close(grammarAt, wordsAt)) || sharesName) return fire(g)
            if (now - grammarAt >= settleMs * 2) { grammarCmd = null; return null }
        }
        // ---- play: the words must have heard "play" (they say what, too)
        (wordsCall as? WakeAction.Play)?.let { if (close(nameAt, wordsAt)) return fire(it) }
        val play = w as? WakeAction.Play
        if (play != null && g is WakeAction.Play && close(nameAt, grammarAt) && close(grammarAt, wordsAt)) return fire(WakeAction.Play(play.what))
        return null
    }

    /** Set when the summon came from the name heard twice (no other listener needed). */
    private var twiceCalled = false

    private fun expire(now: Long) {
        if (nameAt >= 0 && now - nameAt > windowMs) { nameAt = -1; nameWake = WakeCommands.Wake.NONE; twiceCalled = false }
        if (aloneAt >= 0 && now - aloneAt > windowMs) aloneAt = -1
        if (grammarAt >= 0 && now - grammarAt > windowMs) { grammarCmd = null; grammarAt = -1 }
        if (grammarSummonAt >= 0 && now - grammarSummonAt > windowMs) grammarSummonAt = -1
        if (wordsAt >= 0 && now - wordsAt > windowMs) { words = null; wordsCall = null; wordsAt = -1; wordsLead = -1; wordsSaidOpen = false; wordsSaidSummon = false }
        if (rawAt >= 0 && now - rawAt > windowMs) { rawWords = ""; rawAt = -1 }
    }

    private fun fire(a: WakeAction): WakeAction {
        reset()
        return a
    }

    fun reset() {
        nameAt = -1; nameWake = WakeCommands.Wake.NONE; nameAgree = 0; aloneAt = -1; twiceCalled = false
        grammarCmd = null; grammarAt = -1; grammarSummonAt = -1; grammarNamed = false
        words = null; wordsCall = null; wordsAt = -1; wordsLead = -1; wordsSaidOpen = false; wordsSaidSummon = false
        rawWords = ""; rawAt = -1
    }

    /** Real speech, not the stray syllables room noise produces ("huh", "here", "what"). */
    private fun speech(s: String): Boolean {
        val w = s.split(' ').filter { it.isNotBlank() }
        return w.size >= 2 || w.any { it.length >= 5 }
    }

    private companion object {
        /** "lock" as the free-form listener spells it when it doesn't know it's a command. Never "look"/"like". */
        val LOCK_SOUND = Regex("lo[ck]|luck|lok|lack|log|block")
        /** "come here" / "on screen", however it comes out. Not a bare "here": the grammar forces that out of noise. */
        val SUMMON_SOUND = Regex("screen|come")
        val OPEN_OR_PLAY = Regex("\\b(open|opens|opening|opened|play|playing)\\b")
    }
}
