package com.pipo.robot.voice

/** What a call to Pipo from another app asks for. */
sealed class WakeAction {
    /** "Pipo!" / "Pipo, come here": bring him up. */
    object Summon : WakeAction()
    /** "Pipo, lock" / "Pipo, lock my phone". */
    object Lock : WakeAction()
    /** "Pipo, open YouTube": the app's name as heard ("you tube"). */
    data class Open(val app: String) : WakeAction()
    /** "Pipo, play lofi music": what to play, as heard (may be blank: "Pipo, play"). */
    data class Play(val what: String) : WakeAction()
}

/**
 * The wake word, as plain text rules over what the on-device recogniser transcribed. Pure (no
 * audio, no Android) so it is unit-tested.
 *
 * The recogniser's English vocabulary has no word "Pipo": depending on the voice it comes out as
 * "people", "pippa", "peep", "hippo"... So the call is matched by SOUND-ALIKE, but only where a
 * call can be: it must open the utterance (after an optional "hey"), and be followed by a command
 * (open / play / lock / come here) or by nothing at all. "A video about people" or "people are
 * weird" never wake him.
 */
object WakeCommands {
    /** Single words the recogniser writes for "Pipo". */
    private val WAKE = setOf(
        "pipo", "pippo", "peepo", "pepo", "people", "peoples", "people's", "pippa", "pipa", "peep", "peeps",
        "pipe", "pipes", "hippo", "bebo", "pebo", "pivot", "pinto", "piper", "pupil", "pupils", "keeper", "deeper", "beeper", "pepper",
    )
    /** Two-word spellings ("pee po"). */
    private val WAKE2 = setOf("pee po", "pi po", "pee poe", "pea po", "p o", "pee pope", "pee pole", "pee pore")
    private val LEAD = setOf("hey", "hi", "okay", "ok", "oh", "a", "the", "hello")
    private val OPEN = setOf("open", "launch", "start", "opened", "opening")
    private val PLAY = setOf("play", "played", "playing", "plays")
    /** How "lock" gets heard. */
    private val LOCK = setOf("lock", "locked", "lok", "luck", "look", "log", "lack", "like", "lark", "block", "loch")
    private val LOCK_TAIL = setOf("", "it", "my phone", "the phone", "phone", "now", "my mobile", "the screen", "screen", "please")
    private val SUMMON = setOf("come here", "come", "here", "on screen", "come on", "where are you", "show up", "please")

    /** What [heard] asks for, or null if it wasn't a call to Pipo. */
    fun interpret(heard: String): WakeAction? {
        var w = heard.lowercase().replace(Regex("[^a-z' ]"), " ").split(Regex("\\s+")).filter { it.isNotBlank() }
        while (w.isNotEmpty() && w.first() in LEAD) w = w.drop(1)
        if (w.isEmpty()) return null
        val rest = when {
            w.first() in WAKE -> w.drop(1)
            w.size >= 2 && "${w[0]} ${w[1]}" in WAKE2 -> w.drop(2)
            else -> return null
        }.dropWhile { it == "please" }
        val tail = rest.joinToString(" ")
        val verb = rest.firstOrNull()
        val after = rest.drop(1).joinToString(" ")
        return when {
            tail.isEmpty() || tail in SUMMON -> WakeAction.Summon
            verb in LOCK && after in LOCK_TAIL -> WakeAction.Lock
            verb in OPEN && after.isNotBlank() -> WakeAction.Open(after.removePrefix("the ").removePrefix("up "))
            verb in PLAY -> WakeAction.Play(after.replace("you tube", "youtube"))
            else -> null
        }
    }

    /**
     * The spotter's phrase list. A tiny grammar only for his NAME is far more reliable than
     * free-form at hearing "Pipo" (it comes out as "people"); the words after it come from a
     * free-form pass over the same audio ([command]).
     */
    private val SPOTTER_NAMES = listOf("people", "pippa", "hippo", "pepper", "peep", "pupil", "keeper", "pippin")
    /** Lock rides in the name listener: a handful of phrases, so a short "lock" can't be drowned out. */
    private val SPOTTER_LOCK = listOf("lock", "lock my phone", "lock the phone", "lock it", "lock phone")
    /** "Pipo, on screen" / "Pipo, come": in the name listener too, the same way as lock. */
    private val SPOTTER_SUMMON = listOf("on screen", "onscreen", "come", "come here")
    val SPOTTER_GRAMMAR = (SPOTTER_NAMES + SPOTTER_NAMES.flatMap { n -> (SPOTTER_LOCK + SPOTTER_SUMMON).map { "$n $it" } })
        .flatMap { listOf(it, "hey $it") } + "[unk]"

    /** How the spotter heard the name: alone ("Pipo!"), leading other words, or not at all. */
    enum class Wake { NONE, ALONE, LEADING, LOCK, SUMMON }

    fun wake(spotted: String): Wake {
        var w = spotted.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        while (w.isNotEmpty() && w.first() in LEAD) w = w.drop(1)
        if (w.firstOrNull() !in SPOTTER_NAMES) return Wake.NONE
        if (w.size == 1) return Wake.ALONE
        if (w[1] == "lock") return Wake.LOCK
        return if (w.drop(1).joinToString(" ") in SPOTTER_SUMMON) Wake.SUMMON else Wake.LEADING
    }

    /**
     * The command in free-form words, whether or not the name made it into them ("open
     * instagram", "he will play loki music", "people luck"): sound-alikes of the name and fillers
     * in front are skipped. Null if there's no open/play/lock near the start.
     */
    fun command(heard: String): WakeAction? {
        val w = heard.lowercase().replace(Regex("[^a-z' ]"), " ").split(Regex("\\s+")).filter { it.isNotBlank() }
        val i = w.indexOfFirst { it in OPEN || it in PLAY || it in LOCK }
        if (i < 0 || i > 3) return null // the command comes right after the name, not deep in a sentence
        val verb = w[i]
        val after = w.drop(i + 1).dropWhile { it == "please" }.joinToString(" ")
        return when {
            verb in LOCK && after in LOCK_TAIL -> WakeAction.Lock
            verb in OPEN && after.isNotBlank() -> WakeAction.Open(after.removePrefix("the ").removePrefix("up "))
            verb in PLAY -> WakeAction.Play(after.replace("you tube", "youtube"))
            else -> null
        }
    }

    /** Spoken forms of app names whose written name isn't a word ("YouTube" -> "you tube"). */
    private val SPOKEN = mapOf("youtube" to "you tube", "youtube music" to "you tube music")

    /** An app label as someone would say it, or null if there's nothing speakable in it. */
    fun spokenName(label: String): String? {
        val plain = label.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
        if (plain.isEmpty() || plain.length > 30) return null
        return SPOKEN[plain] ?: plain
    }

    /**
     * The command listener's phrase list, built from the apps on this phone (not a fixed list).
     * Words the model doesn't know are dropped by the recogniser itself.
     */
    fun commandGrammar(appLabels: Collection<String>): List<String> {
        val apps = appLabels.mapNotNull { spokenName(it) }.distinct().take(150)
        val verbs = listOf("lock", "lock my phone", "lock the phone", "lock it", "lock the screen", "play", "come here", "open")
        // his name's sound-alikes in front, so "Pipo" has a word to land on and doesn't swallow the
        // command (without them "Pipo, lock" came out as an app name)
        return apps.map { "open $it" } + verbs + SPOTTER_NAMES.flatMap { n -> verbs.map { "$n $it" } } + "[unk]"
    }

    /** How many words came before the command verb ("whoa open you tube" -> 1), or -1. */
    fun wordsBeforeVerb(heard: String): Int {
        val w = heard.lowercase().replace(Regex("[^a-z' ]"), " ").split(Regex("\\s+")).filter { it.isNotBlank() }
        return w.indexOfFirst { it in OPEN || it in PLAY || it in LOCK }
    }

    /** "you tube" -> "youtube": how an app name heard as words compares to its label. */
    fun squash(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")

    /**
     * The installed app whose name best matches what was heard ("insta gram" -> "Instagram"), or
     * null. Exact (spaces ignored) wins; otherwise a close match on the start of the name.
     */
    fun matchApp(heard: String, labels: Collection<String>): String? {
        val h = squash(heard)
        if (h.length < 2) return null
        labels.firstOrNull { squash(it) == h }?.let { return it }
        return labels.filter { squash(it).length >= 3 }
            .map { it to similarity(h, squash(it)) }
            .filter { it.second >= 0.6 }
            .maxByOrNull { it.second }?.first
    }

    /** 1 - normalised edit distance. */
    private fun similarity(a: String, b: String): Double {
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]; d[0] = i
            for (j in 1..b.length) {
                val cur = d[j]
                d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = cur
            }
        }
        return 1.0 - d[b.length].toDouble() / maxOf(a.length, b.length)
    }
}
