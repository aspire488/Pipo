package com.pipo.robot.engine

import com.pipo.robot.data.Mood
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.random.Random

enum class PhoneCmd {
    // light
    FLASH_ON, FLASH_OFF, FLASH_STATUS,
    // media
    MEDIA_PLAY, MEDIA_PAUSE, MEDIA_NEXT, MEDIA_PREV, VOLUME_UP, VOLUME_DOWN, MUTE, YOUTUBE, MUSIC_APP,
    /** Any known media app (arg = query, extra = MediaApps id); what is playing; which apps you have. */
    MEDIA_APP, NOW_PLAYING, LIST_MEDIA,
    // device controls that need an access YOU granted (Modify system settings / Do Not Disturb access)
    BRIGHTNESS, AUTO_ROTATE, DND, RINGER,
    // camera / photos
    CAMERA, SELFIE, SHOW_PHOTO,
    // apps + system screens
    OPEN_APP, SETTINGS,
    /** Anything in your launcher, by name. */
    OPEN_ANY,
    // time
    TIME, DATE, TIMER, ALARM,
    // web
    URL, SEARCH, MAPS,
    /** "Pipo, look!": he looks through your camera (one snapshot you take) and reacts. */
    LOOK,
    /** YouTube search results (as opposed to "play X", which plays the top result). */
    YT_SEARCH,
    /** Open an AI app with your question filled in (extra = ai id). */
    ASK_AI,
    /** Pipo goes and finds out himself, then tells you (extra = which AI you named, may be blank). */
    FIND_OUT,
    // tiny utilities
    CALC, CONVERT, COPY, SHARE, BATTERY,
    // calls (dialer only, never auto-calls)
    DIAL,
}

data class PhoneRequest(
    val cmd: PhoneCmd,
    val arg: String = "",
    val seconds: Int = 0,
    val hour: Int = -1,
    val minute: Int = 0,
    /** Pre-computed answer (calc/convert) or settings key. */
    val extra: String = "",
    /** User asked to CHANGE something Android won't let apps change directly (Wi-Fi toggle etc). */
    val blocked: Boolean = false,
) {
    /** Real-world consequence → explicit yes/no first. */
    val needsConfirm: Boolean get() = cmd == PhoneCmd.DIAL || cmd == PhoneCmd.ALARM
    /** Answered by Pipo himself, no intent needed. */
    val infoOnly: Boolean get() = cmd in setOf(PhoneCmd.TIME, PhoneCmd.DATE, PhoneCmd.CALC, PhoneCmd.CONVERT, PhoneCmd.BATTERY, PhoneCmd.FLASH_STATUS, PhoneCmd.LIST_MEDIA)
}

/** Live phone facts Pipo can mention. */
data class PhoneInfo(
    val now: Long = System.currentTimeMillis(),
    val battery: Int = -1,
    val charging: Boolean = false,
    val torchOn: Boolean = false,
    /** Installed media apps (labels), for "which apps do I have". */
    val mediaApps: List<String> = emptyList(),
)

/**
 * Local, offline intent recognition. Straightforward phone requests never touch the LLM.
 * Only runs on something the user explicitly typed or said.
 */
object PhoneCommands {
    private val gameWords = listOf("rock", "paper", "scissors", "memory", "tic", "reaction", "game", "with me", "catch", "hide and seek",
        "football", "soccer", "ball", "kick", "tag", "nib", "with", "outside", "together", "flappy", "shooter", "arcade", "cards", "chess",
        "hide", "seek", "pretend", "dress up", "toys", "console")
    private val filler = Regex("\\b(please|pls|pipo|can you|could you|would you|will you|hey|for me|some|the|a|an|now|quickly)\\b")

    /** Which AI app a word means (null = not an AI). */
    fun aiId(w: String): String? = when (w.lowercase().replace(" ", "")) {
        "chatgpt", "gpt", "openai" -> "chatgpt"; "gemini", "bard" -> "gemini"; "claude" -> "claude"
        "perplexity" -> "perplexity"; "copilot" -> "copilot"; "ai", "anai", "aai", "theai" -> "ai"
        else -> null
    }

    fun aiName(id: String) = when (id) { "chatgpt" -> "ChatGPT"; "gemini" -> "Gemini"; "claude" -> "Claude"; "perplexity" -> "Perplexity"; "copilot" -> "Copilot"; else -> "the AI" }

    /** A question for an AI keeps its words; only the bits addressed to Pipo go. */
    private fun verbatim(s: String) = s.replace(Regex("\\b(please|pls|pipo|hey)\\b"), " ").replace(Regex("\\s+"), " ").trim()

    private fun clean(s: String) = s.replace(filler, " ").replace(Regex("\\s+"), " ").trim()

    /** Strip "hey pipo, ..." / "pipo please ..." so anchored patterns still work. */
    private fun normalize(raw: String): String {
        var s = raw.lowercase(Locale.US).trim().trimEnd('.', '!', '?', ' ')
        s = s.replace(Regex("^(hey|hi|ok|okay|yo)?\\s*pipo[,!.:]?\\s*"), "")
        s = s.replace(Regex("^(please|pls|can you|could you|would you|will you)\\s+"), "")
        s = s.replace(Regex("\\s+please$"), "")
        return s.trim()
    }

    private val appAliases = linkedMapOf(
        "camera" to listOf("camera", "cam"),
        "gallery" to listOf("gallery", "photos", "google photos", "pictures", "my photos"),
        "browser" to listOf("browser", "chrome", "internet", "web browser"),
        "youtube" to listOf("youtube", "yt"),
        "spotify" to listOf("spotify"),
        "music" to listOf("music", "music app", "music player", "youtube music"),
        "maps" to listOf("maps", "google maps", "map"),
        "clock" to listOf("clock", "alarm", "alarms", "alarm clock", "stopwatch"),
        "calculator" to listOf("calculator", "calc"),
        "calendar" to listOf("calendar"),
        "settings" to listOf("settings", "setting", "phone settings", "system settings"),
    )

    private val settingsAliases = linkedMapOf(
        "wifi" to listOf("wifi", "wi-fi", "wi fi", "internet", "network"),
        "bluetooth" to listOf("bluetooth"),
        "display" to listOf("display", "brightness", "screen", "dark mode", "theme"),
        "sound" to listOf("sound", "sounds", "ringtone", "vibration", "silent mode"),
        "battery" to listOf("battery", "battery saver", "power saving"),
        "notifications" to listOf("notification", "notifications"),
        "apps" to listOf("app", "apps", "application", "applications"),
        "datetime" to listOf("date", "time", "date and time", "timezone", "time zone"),
        "location" to listOf("location", "gps"),
        "airplane" to listOf("airplane", "airplane mode", "flight mode", "aeroplane mode"),
    )

    private fun appKey(name: String): String? {
        val n = name.trim().removeSuffix(" app").trim()
        return appAliases.entries.firstOrNull { (_, v) -> n in v }?.key
    }

    private fun settingsKey(text: String): String? =
        settingsAliases.entries.firstOrNull { (_, v) -> v.any { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(text) } }?.key

    fun parse(raw: String): PhoneRequest? {
        val s = normalize(raw)
        if (s.isBlank()) return null

        // ---------- light
        if (Regex("\\b(flashlight|flash light|torch)\\b").containsMatchIn(s)) {
            return when {
                Regex("^(is|are)\\b|\\bstill on\\b|status").containsMatchIn(s) -> PhoneRequest(PhoneCmd.FLASH_STATUS)
                Regex("\\b(off|disable|stop|kill)\\b").containsMatchIn(s) -> PhoneRequest(PhoneCmd.FLASH_OFF)
                else -> PhoneRequest(PhoneCmd.FLASH_ON)
            }
        }
        if (Regex("^(lights? on|i need (light|a light)|it'?s (too )?dark)$").matches(s)) return PhoneRequest(PhoneCmd.FLASH_ON)
        if (Regex("^lights? off$").matches(s)) return PhoneRequest(PhoneCmd.FLASH_OFF)

        // ---------- timer
        if (s.contains("timer")) {
            Regex("(\\d+)\\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?)").find(s)?.let { m ->
                val n = m.groupValues[1].toInt()
                val unit = m.groupValues[2]
                val secs = when { unit.startsWith("h") -> n * 3600; unit.startsWith("m") -> n * 60; else -> n }
                if (secs in 1..86_400) return PhoneRequest(PhoneCmd.TIMER, seconds = secs)
            }
            return PhoneRequest(PhoneCmd.OPEN_APP, "clock")
        }

        // ---------- alarm (with a time → confirm; without → open clock)
        if (s.contains("alarm") || s.startsWith("wake me")) {
            Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|a\\.m\\.|p\\.m\\.)?").find(s)?.let { m ->
                var h = m.groupValues[1].toInt()
                val min = m.groupValues[2].ifEmpty { "0" }.toInt()
                val ap = m.groupValues[3]
                if (ap.startsWith("p") && h < 12) h += 12
                if (ap.startsWith("a") && h == 12) h = 0
                if (h in 0..23 && min in 0..59) return PhoneRequest(PhoneCmd.ALARM, hour = h, minute = min)
            }
            return PhoneRequest(PhoneCmd.OPEN_APP, "clock")
        }

        // ---------- time / date / battery
        if (Regex("\\b(what time|what's the time|whats the time|time is it|tell me the time|current time)\\b").containsMatchIn(s) || s == "time")
            return PhoneRequest(PhoneCmd.TIME)
        if (Regex("\\b(what's the date|whats the date|what is the date|today's date|todays date|what day is it|what day is today|date today|which day)\\b").containsMatchIn(s) || s == "date")
            return PhoneRequest(PhoneCmd.DATE)
        if (s.contains("battery") && Regex("\\b(how|much|what|whats|my|percent|percentage|level|left|status|charge|charged)\\b").containsMatchIn(s) && !s.contains("setting"))
            return PhoneRequest(PhoneCmd.BATTERY)

        // ---------- unit conversion: "5 km to miles", "convert 30 c to f"
        Units.parse(s)?.let { (q, ans) -> return PhoneRequest(PhoneCmd.CONVERT, q, extra = ans) }

        // ---------- calculator: "what's 12*7", "25 percent of 80", "5 plus 3"
        MiniCalc.fromSentence(s)?.let { (expr, ans) -> return PhoneRequest(PhoneCmd.CALC, expr, extra = ans) }

        // ---------- AI apps: "ask chatgpt why the sky is blue", "open gemini and ask ...", "ask claude ... and tell me"
        Regex("^(?:go |can you |please )?(?:ask|open (\\w+) and ask|check with|what does (\\w+) (?:say|think) about)\\s*(chat ?gpt|gpt|gemini|claude|perplexity|copilot|an? ai|the ai|ai)?\\b[,:]?\\s*(.*)$").find(s)?.let { m ->
            val named = listOf(m.groupValues[3], m.groupValues[1], m.groupValues[2]).firstOrNull { aiId(it) != null }
            val ai = named?.let { aiId(it) }
            if (ai == null && !Regex("^(?:what does|check with)").containsMatchIn(s) && m.groupValues[3].isBlank()) return@let
            var q = m.groupValues[4].trim()
            val tellMe = Regex("\\b(and )?(tell me|let me know|what (it|they) says?|find out|report back)\\b").containsMatchIn(q) || s.startsWith("what does") || ai == null || ai == "ai"
            // keep the question in your own words: only Pipo-directed fillers go
            q = verbatim(q.replace(Regex("\\b(and )?(tell me( what (it|they) says?)?|let me know|report back)\\b"), " ")).removePrefix("about ").removePrefix("to ")
            if (q.isBlank() && ai != null && ai != "ai") return PhoneRequest(PhoneCmd.ASK_AI, "", extra = ai)
            if (q.isBlank()) return@let
            return if (tellMe) PhoneRequest(PhoneCmd.FIND_OUT, q, extra = ai.orEmpty()) else PhoneRequest(PhoneCmd.ASK_AI, q, extra = ai)
        }
        Regex("^(?:find out|look into|research)\\s+(.+)").find(s)?.let { return PhoneRequest(PhoneCmd.FIND_OUT, verbatim(it.groupValues[1])) }

        // ---------- YouTube search (results, not autoplay): "search cats on youtube", "youtube search lofi"
        Regex("^(?:search|find|look up|look for)(?: for)? (.+?) (?:on|in) youtube$|^youtube search(?: for)? (.+)$|^search youtube(?: for)? (.+)$").find(s)?.let { m ->
            val q = clean(m.groupValues.drop(1).firstOrNull { it.isNotBlank() }.orEmpty())
            if (q.isNotBlank()) return PhoneRequest(PhoneCmd.YT_SEARCH, q)
        }
        // ---------- Google search, said explicitly: "search cats on google", "google cats"
        Regex("^(?:search|look up|find)(?: for)? (.+?) on google$").find(s)?.let { return PhoneRequest(PhoneCmd.SEARCH, clean(it.groupValues[1])) }

        // ---------- what's playing / which apps
        if (Regex("^(what'?s|what is) (playing|this song|the song|on the speaker)|what song is (this|playing)|who (sings|is singing) this|what am i listening to").containsMatchIn(s))
            return PhoneRequest(PhoneCmd.NOW_PLAYING)
        if (Regex("\\b(what|which) (music |video |media |streaming |movie |song |podcast )?apps (do )?i (have|got)|\\b(list|show) (my )?(music|video|media|streaming) apps").containsMatchIn(s))
            return PhoneRequest(PhoneCmd.LIST_MEDIA)

        // ---------- device controls (need an access you granted; otherwise he opens the right screen)
        if (Regex("\\b(brightness|brighter|darker|dim (the )?screen|screen (brighter|darker|dimmer))\\b").containsMatchIn(s) && !s.contains("setting")) {
            val pct = Regex("(\\d{1,3})\\s*(%|percent)?").find(s)?.groupValues?.get(1)?.toIntOrNull()
            val arg = when {
                pct != null -> pct.coerceIn(1, 100).toString()
                Regex("\\b(max|maximum|full|brightest)\\b").containsMatchIn(s) -> "100"
                Regex("\\b(min|minimum|lowest|darkest)\\b").containsMatchIn(s) -> "5"
                Regex("\\b(down|lower|decrease|darker|dim|dimmer|reduce|less)\\b").containsMatchIn(s) -> "down"
                else -> "up"
            }
            return PhoneRequest(PhoneCmd.BRIGHTNESS, arg)
        }
        if (Regex("\\b(auto[- ]?rotat(e|ion)|screen rotation|rotation lock|lock (the )?rotation)\\b").containsMatchIn(s) && !s.contains("setting")) {
            val off = Regex("\\b(off|disable|stop|lock)\\b").containsMatchIn(s) && !s.contains("unlock")
            return PhoneRequest(PhoneCmd.AUTO_ROTATE, if (off) "off" else "on")
        }
        if (Regex("\\b(do not disturb|don'?t disturb|dnd|focus mode)\\b").containsMatchIn(s) && !s.contains("setting")) {
            val off = Regex("\\b(off|disable|stop|end|exit|cancel)\\b").containsMatchIn(s)
            return PhoneRequest(PhoneCmd.DND, if (off) "off" else "on")
        }
        Regex("\\b(silent|vibrat(e|ion)|ring|normal|sound) mode\\b|\\bon (silent|vibrate)\\b|\\b(silence|unsilence|unmute) (my |the )?phone\\b|\\bringer (on|off)\\b").find(s)?.let {
            val arg = when {
                Regex("\\bvibrat").containsMatchIn(s) -> "vibrate"
                Regex("\\b(unsilence|unmute|ring mode|normal mode|sound mode|ringer on)\\b").containsMatchIn(s) ||
                    (Regex("\\bsilent mode\\b").containsMatchIn(s) && Regex("\\b(off|disable|turn off)\\b").containsMatchIn(s)) -> "normal"
                else -> "silent"
            }
            return PhoneRequest(PhoneCmd.RINGER, arg)
        }

        // ---------- volume
        if (Regex("\\b(volume up|louder|turn it up|increase (the )?volume|raise (the )?volume)\\b").containsMatchIn(s)) return PhoneRequest(PhoneCmd.VOLUME_UP)
        if (Regex("\\b(volume down|quieter|softer|turn it down|decrease (the )?volume|lower (the )?volume)\\b").containsMatchIn(s)) return PhoneRequest(PhoneCmd.VOLUME_DOWN)
        if (Regex("^(mute|unmute|mute (it|sound|volume|music)|shh+)$").matches(s)) return PhoneRequest(PhoneCmd.MUTE)

        // ---------- media keys
        if (Regex("\\b(next song|skip (this|song|it|track)|next track|skip)\\b").containsMatchIn(s)) return PhoneRequest(PhoneCmd.MEDIA_NEXT)
        if (Regex("\\b(previous song|last song|previous track|go back a song|play (that|the) (last|previous) (one|song))\\b").containsMatchIn(s)) return PhoneRequest(PhoneCmd.MEDIA_PREV)
        if (Regex("^(pause|stop|stop (the )?music|pause (the )?music|pause (the )?song|stop (the )?song|stop playing)$").matches(s)) return PhoneRequest(PhoneCmd.MEDIA_PAUSE)
        if (Regex("^(resume|unpause|continue( the)?( music| song)?|resume (the )?(music|song)|play( (some|the))? (music|songs?|a song|something)|music on|put on (some )?music)$").matches(s))
            return PhoneRequest(PhoneCmd.MEDIA_PLAY)

        // ---------- any known media app: "play arijit singh on spotify", "watch stranger things on netflix", "open hotstar"
        MediaApps.mentioned(s)?.let { (app, alias) ->
            val asked = s == alias || Regex("^(play|put on|watch|listen to|listen|stream|open|launch|start|go to|show me|show|search|find)\\b").containsMatchIn(s) ||
                Regex("\\b(on|in) $alias\\b").containsMatchIn(s)
            if (!asked) return@let
            val q = MediaApps.query(s, alias)
            return if (app.id == "youtube") PhoneRequest(PhoneCmd.YOUTUBE, q) else PhoneRequest(PhoneCmd.MEDIA_APP, q, extra = app.id)
        }
        if (s.contains("youtube") && !s.contains("youtube music")) {
            val q = clean(s.replace(Regex("\\b(play|put on|search for|search|find|open|launch|watch|on|in|youtube)\\b"), " "))
            return PhoneRequest(PhoneCmd.YOUTUBE, q)
        }
        if (s.startsWith("play ") && gameWords.none { s.contains(it) }) {
            val q = clean(s.removePrefix("play ").replace(Regex("\\b(song|songs|music|by)\\b"), " "))
            if (q.isNotBlank()) return if (Regex("\\b(video|videos|trailer|episode)\\b").containsMatchIn(s)) PhoneRequest(PhoneCmd.YOUTUBE, q) else PhoneRequest(PhoneCmd.MUSIC_APP, q)
        }

        // ---------- "Pipo, look!" — he sees what you show him
        // (short "look!" phrases only: "look up X" and "look for X" are searches)
        if (Regex("^(?:hey )?(?:pipo,? )?(look|look at this|look here|look at that|look at me|see this|see that|what do you see|what can you see|can you see this|what is this|what's this|whats this|check this out|guess what this is)(?: pipo)?[!.?]*$").matches(s))
            return PhoneRequest(PhoneCmd.LOOK)

        // ---------- camera / photos
        if (Regex("\\b(selfie)\\b").containsMatchIn(s)) return PhoneRequest(PhoneCmd.SELFIE)
        if (Regex("\\b(take (a )?(photo|picture|pic)|click (a )?(photo|pic|picture)|open (the )?camera|camera)\\b").containsMatchIn(s) && !s.contains("setting"))
            return PhoneRequest(PhoneCmd.CAMERA)
        if (Regex("\\b(show you|let me show|look at (this|my)|see (this|my)) (a |this |my )?(photo|picture|pic|image)\\b").containsMatchIn(s) ||
            Regex("^(show|pick) (a |pipo a |you a )?(photo|picture|pic)$").matches(s))
            return PhoneRequest(PhoneCmd.SHOW_PHOTO)

        // ---------- URLs
        Regex("((?:https?://)?(?:www\\.)?[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.(?:com|org|net|in|io|dev|app|edu|gov|co|ai|me|info)(?:/\\S*)?)").find(s)?.let {
            if (Regex("^(open|go to|visit|load|show me)\\b").containsMatchIn(s) || s == it.value) return PhoneRequest(PhoneCmd.URL, it.value)
        }

        // ---------- maps
        Regex("^(?:navigate to|directions to|take me to|show me the way to|how do i get to)\\s+(.+)").find(s)?.let {
            return PhoneRequest(PhoneCmd.MAPS, clean(it.groupValues[1]))
        }
        if (Regex("\\bon (the )?maps?$").containsMatchIn(s) || Regex("^(find|show|where is) .+ on maps?").containsMatchIn(s)) {
            val q = clean(s.replace(Regex("\\b(open|show|find|search|where is|on|in|google|maps?|me)\\b"), " "))
            return PhoneRequest(PhoneCmd.MAPS, q)
        }

        // ---------- web search
        Regex("^(?:search the web for|search online for|search the internet for|look up|google|search)(?: for)?\\s+(.+)").find(s)?.let {
            return PhoneRequest(PhoneCmd.SEARCH, clean(it.groupValues[1]))
        }

        // ---------- clipboard + share
        Regex("^copy\\s+(.+)").find(s)?.let {
            val t = raw.trim().replace(Regex("^(?i)(hey\\s+)?(pipo[,!.:]?\\s*)?(please\\s+)?copy\\s+"), "").trim().trim('"', '\'')
            return PhoneRequest(PhoneCmd.COPY, if (Regex("^(that|this|it|what you said)$").matches(it.groupValues[1])) "" else t)
        }
        Regex("^share\\s+(.+)").find(s)?.let {
            val t = raw.trim().replace(Regex("^(?i)(hey\\s+)?(pipo[,!.:]?\\s*)?(please\\s+)?share\\s+"), "").trim().trim('"', '\'')
            return PhoneRequest(PhoneCmd.SHARE, if (Regex("^(that|this|it|what you said)$").matches(it.groupValues[1])) "" else t)
        }

        // ---------- dialer ("call me X" is a name, not a call)
        Regex("^(?:call|dial|ring|phone)\\s+(.+)").find(s)?.let {
            val who = it.groupValues[1].trim()
            if (!who.startsWith("me ") && who != "me") return PhoneRequest(PhoneCmd.DIAL, who)
        }

        // ---------- things Android won't let an app toggle → open the right screen
        if (Regex("^(turn|switch|toggle|enable|disable|put)\\b").containsMatchIn(s) || Regex("\\b(brightness|dark mode|airplane mode|flight mode) (up|down|on|off)\\b").containsMatchIn(s)) {
            settingsKey(s)?.let { return PhoneRequest(PhoneCmd.SETTINGS, extra = it, blocked = true) }
        }

        // ---------- "<x> settings" / "open <x> settings"
        if (Regex("\\bsettings?\\b").containsMatchIn(s)) {
            val rest = s.replace(Regex("\\b(open|show|go to|launch|settings?|my|phone)\\b"), " ").trim()
            if (rest.isBlank()) return PhoneRequest(PhoneCmd.OPEN_APP, "settings")
            settingsKey(rest)?.let { return PhoneRequest(PhoneCmd.SETTINGS, extra = it) }
            return PhoneRequest(PhoneCmd.SETTINGS, extra = "general")
        }

        // ---------- open <app>
        Regex("^(?:open|launch|start|go to|show me|show)\\s+(?:the\\s+|my\\s+)?(.+)$").find(s)?.let { m ->
            val key = appKey(m.groupValues[1])
                ?: if (Regex("^(open|launch|start)\\b").containsMatchIn(s)) return PhoneRequest(PhoneCmd.OPEN_ANY, clean(m.groupValues[1])) else return@let
            return when (key) {
                "camera" -> PhoneRequest(PhoneCmd.CAMERA)
                "youtube" -> PhoneRequest(PhoneCmd.YOUTUBE)
                "spotify" -> PhoneRequest(PhoneCmd.MUSIC_APP)
                else -> PhoneRequest(PhoneCmd.OPEN_APP, key)
            }
        }
        if (s == "browser" || s == "calculator" || s == "gallery") return PhoneRequest(PhoneCmd.OPEN_APP, s)
        return null
    }

    // =====================================================================================
    //  Pipo's side of it. Never "Certainly." Never "Action completed."
    // =====================================================================================

    fun line(r: PhoneRequest, mood: Mood, rng: Random, info: PhoneInfo = PhoneInfo()): String {
        fun p(vararg o: String) = o[rng.nextInt(o.size)]
        val grumpy = mood == Mood.GRUMPY
        val sleepy = mood == Mood.SLEEPY
        val cal = Calendar.getInstance().apply { timeInMillis = info.now }
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val timeStr = "%d:%02d".format(if (h % 12 == 0) 12 else h % 12, cal.get(Calendar.MINUTE)) + if (h < 12) " AM" else " PM"
        return when (r.cmd) {
            PhoneCmd.FLASH_ON -> when {
                grumpy -> "Fine. Light."
                sleepy -> "...bright. Ow. Okay, it's on."
                else -> p("Emergency sunshine.", "Let there be light.", "Lighthouse mode.", "Bright! My eyes! Worth it.")
            }
            PhoneCmd.FLASH_OFF -> p("Lights out. Cozy.", "Darkness again. I liked the sunshine.", "Off. The shadows are back.")
            PhoneCmd.FLASH_STATUS -> if (info.torchOn) p("Yep, it's on. I'm glowing.", "It's on. Look at my antenna.") else p("It's off.", "Nope. Dark mode.")

            PhoneCmd.MEDIA_PLAY -> if (grumpy) "Fine. Music." else p("Music! Yes.", "On it.", "Ooh, my song. Probably.")
            PhoneCmd.MEDIA_PAUSE -> p("Pause.", "Shh. Okay.", "Stopping. My legs were just warming up.")
            PhoneCmd.MEDIA_NEXT -> p("Next!", "Skipping. That one was mid.", "Next one. Make it a good one.")
            PhoneCmd.MEDIA_PREV -> p("Back one.", "Again? Okay, that one was good.")
            PhoneCmd.VOLUME_UP -> p("Louder!", "Turning it up.", "More!")
            PhoneCmd.VOLUME_DOWN -> p("Shh. Quieter.", "Turning it down.", "Softer. Got it.")
            PhoneCmd.MUTE -> p("Shh.", "Quiet mode.", "Mute. Or unmute. One of those.")
            PhoneCmd.YOUTUBE -> if (r.arg.isBlank()) p("YouTube. Pick something good.", "Opening YouTube.") else p("Finding \"${r.arg}\". I'll dance if it's good.", "\"${r.arg}\". Loading vibes.")
            PhoneCmd.MUSIC_APP -> if (r.arg.isBlank()) p("Opening your music.", "Music app. Yes.") else "Looking for \"${r.arg}\"."
            PhoneCmd.MEDIA_APP -> {
                val label = MediaApps.all.firstOrNull { it.id == r.extra }?.label ?: "that"
                if (r.arg.isBlank()) p("Opening $label!", "$label. Good choice.", "$label time!")
                else p("\"${r.arg}\" on $label. Ooh.", "Finding \"${r.arg}\" on $label!", "$label, \"${r.arg}\". Let's go.")
            }
            PhoneCmd.NOW_PLAYING -> "" // spoken after he checks, see HomeViewModel
            PhoneCmd.LIST_MEDIA -> MediaApps.listLine(info.mediaApps)
            PhoneCmd.OPEN_ANY -> p("Opening ${r.arg}!", "${r.arg.replaceFirstChar { it.uppercase() }}. On it.", "Going to ${r.arg}.")
            PhoneCmd.BRIGHTNESS -> when (r.arg) {
                "up" -> p("Brighter! Ow. Worth it.", "More light!")
                "down" -> p("Dimmer. Cozy.", "Darker. Sneaky mode.")
                "100" -> "Full brightness. My eyes!"
                else -> "Brightness ${r.arg}%. Just right."
            }
            PhoneCmd.AUTO_ROTATE -> if (r.arg == "on") p("Auto-rotate on. Spin the phone!", "Now the screen turns with you.") else p("Rotation locked. The screen stays put.", "No more spinning.")
            PhoneCmd.DND -> if (r.arg == "on") p("Do not disturb. Shh. Except me.", "Quiet time. I'll guard the door.") else p("Do not disturb is off. The world can knock again.", "Okay, notifications are back.")
            PhoneCmd.RINGER -> when (r.arg) {
                "vibrate" -> p("Vibrate mode. Bzzz.", "Buzz only.")
                "normal" -> p("Ringer's back on. Ring ring!", "Sound mode on.")
                else -> p("Silent mode. Shhh.", "Phone's on silent.")
            }

            PhoneCmd.CAMERA -> p("Camera time. Say screws!", "Opening the camera. Get my good side. All sides.", "Photo? Wait, let me pose.")
            PhoneCmd.SELFIE -> p("Selfie! I'll stay out of it. Probably.", "Front camera. You look great. I'd know.")
            PhoneCmd.SHOW_PHOTO -> p("Ooh. Show me.", "A photo? For me? Okay. Pick one.", "I love looking at things. Go.")

            PhoneCmd.OPEN_APP -> when (r.arg) {
                "gallery" -> p("Your photos. I won't peek. I'll peek a little.", "Gallery. Memories!")
                "browser" -> p("Browser. The internet is very big.", "Opening the internet.")
                "maps" -> p("Maps. Don't get lost.", "Where are we going?")
                "clock" -> p("Clock. Tick tock.", "Opening the clock.")
                "calculator" -> p("Calculator. I could've done it. Probably.", "Numbers!")
                "calendar" -> p("Calendar. So many days.", "Opening the calendar.")
                "settings" -> p("Settings. Be careful in there.", "Opening settings.")
                "music" -> p("Opening your music.", "Music app!")
                else -> p("Gotcha.", "On it.")
            }
            PhoneCmd.SETTINGS -> {
                val what = when (r.extra) {
                    "wifi" -> "Wi-Fi"; "bluetooth" -> "Bluetooth"; "display" -> "display"; "sound" -> "sound"
                    "battery" -> "battery"; "notifications" -> "notification"; "apps" -> "app"; "datetime" -> "date and time"
                    "location" -> "location"; "airplane" -> "airplane mode"; else -> "phone"
                }
                if (r.blocked) p("Android won't let me touch that one. Here's the switch.", "I'm not allowed to flip that. You do it. Here.", "Too important for me, apparently. Opening $what settings.")
                else p("$what settings. Here.", "Opening $what settings.", "Gotcha. $what settings.")
            }

            PhoneCmd.TIME -> when {
                h >= 23 || h < 5 -> p("It's $timeStr. Why are we awake?", "$timeStr. ...I'm sleepy.")
                h < 9 -> "It's $timeStr. Early. Very early."
                else -> p("It's $timeStr.", "$timeStr. Time is weird.", "My clock says $timeStr.")
            }
            PhoneCmd.DATE -> {
                val day = cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.US)
                val mon = cal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.US)
                "It's $day, $mon ${cal.get(Calendar.DAY_OF_MONTH)}." + if (day == "Friday") " Friday! Good day." else ""
            }
            PhoneCmd.TIMER -> "Timer: ${formatSecs(r.seconds)}. I'll be counting too. In my head."
            PhoneCmd.ALARM -> "Alarm at ${"%02d:%02d".format(r.hour, r.minute)}. Done. Don't snooze."

            PhoneCmd.URL -> p("Opening it.", "Gotcha. Off to ${r.arg.removePrefix("https://").removePrefix("www.").substringBefore('/')}.")
            PhoneCmd.LOOK -> p("Ooh! Show me!", "Let me see, let me see!")
            PhoneCmd.YT_SEARCH -> p("Searching YouTube for \"${r.arg}\". You pick.", "YouTube, find \"${r.arg}\". Go.")
            PhoneCmd.ASK_AI -> if (r.arg.isBlank()) "Opening ${aiName(r.extra)}. Say hi from me." else p("Asking ${aiName(r.extra)}: \"${r.arg}\". I hope it's nice to me.", "Okay, ${aiName(r.extra)}. Big question incoming.")
            PhoneCmd.FIND_OUT -> p("Hmm. Let me find out. Hold on.", "Ooh, a research mission. One sec.", "Asking around. Robots have contacts.")
            PhoneCmd.SEARCH -> p("Searching \"${r.arg}\". The internet knows things.", "\"${r.arg}\". Let's see.")
            PhoneCmd.MAPS -> if (r.arg.isBlank()) "Opening maps." else "Finding ${r.arg}."

            PhoneCmd.CALC -> if (r.extra.isBlank()) "My brain did a weird thing. That math is broken." else p("${r.extra}.", "That's ${r.extra}. Easy.", "Um... ${r.extra}! Yep.")
            PhoneCmd.CONVERT -> "${r.extra}." + if (rng.nextFloat() < 0.3f) " I didn't even use my fingers." else ""
            PhoneCmd.COPY -> if (r.arg.isBlank()) "Copied what I said. Treasure it." else p("Copied!", "Got it. It's in the clipboard.")
            PhoneCmd.SHARE -> p("Sharing! Pick where.", "Okay. Where's it going?")
            PhoneCmd.BATTERY -> when {
                info.battery < 0 -> "I can't see the battery right now. Weird."
                info.charging -> "${info.battery}% and charging. Ahhh. Power."
                info.battery <= 15 -> "${info.battery}%. We're getting crispy."
                info.battery <= 40 -> "${info.battery}%. We should find a charger soon."
                else -> p("${info.battery}%. We're good.", "${info.battery}%. Plenty of juice.")
            }
            PhoneCmd.DIAL -> "Opening the dialer for ${r.arg}. You press call. I get shy on the phone."
        }
    }

    fun confirmQuestion(r: PhoneRequest): String = when (r.cmd) {
        PhoneCmd.DIAL -> "Open the dialer for \"${r.arg}\"?"
        PhoneCmd.ALARM -> "An alarm for ${"%02d:%02d".format(r.hour, r.minute)}?"
        else -> "Do it?"
    }

    fun formatSecs(s: Int): String = when {
        s >= 3600 && s % 3600 == 0 -> "${s / 3600} hour${if (s >= 7200) "s" else ""}"
        s >= 60 && s % 60 == 0 -> "${s / 60} minute${if (s >= 120) "s" else ""}"
        else -> "$s seconds"
    }
}

/** Tiny safe arithmetic evaluator: + - * / % ^ ( ). No eval, no scripting. */
object MiniCalc {
    fun fromSentence(s: String): Pair<String, String>? {
        var e = s.replace(Regex("^(what's|whats|what is|calculate|calc|compute|solve|how much is|tell me)\\s+"), "")
        e = e.replace(Regex("(\\d+(?:\\.\\d+)?)\\s*(percent|%)\\s*of\\s*(\\d+(?:\\.\\d+)?)"), "($1/100*$3)")
        e = e.replace(Regex("\\bsquared\\b"), "^2").replace(Regex("\\bcubed\\b"), "^3")
            .replace(Regex("\\b(plus|and)\\b"), "+").replace(Regex("\\bminus\\b"), "-")
            .replace(Regex("\\b(times|multiplied by|into)\\b"), "*").replace(Regex("(?<=\\d)\\s*x\\s*(?=\\d)"), "*")
            .replace(Regex("\\bdivided by\\b|\\bover\\b"), "/").replace(Regex("\\b(to the power of|power)\\b"), "^")
            .replace("×", "*").replace("÷", "/").replace(Regex("\\s+"), "")
        if (!Regex("^[0-9.+\\-*/%^()]+$").matches(e)) return null
        if (!Regex("\\d[+\\-*/%^(]|\\)").containsMatchIn(e) || !Regex("\\d").containsMatchIn(e)) return null
        if (!Regex("[+\\-*/%^]").containsMatchIn(e)) return null
        val v = eval(e) ?: return null
        return e to format(v)
    }

    fun format(v: Double): String = when {
        v.isNaN() || v.isInfinite() -> ""
        abs(v - Math.round(v)) < 1e-9 && abs(v) < 1e15 -> Math.round(v).toString()
        else -> "%.4f".format(Locale.US, v).trimEnd('0').trimEnd('.')
    }

    fun eval(src: String): Double? = try {
        val p = P(src); val v = p.expr(); if (p.i != src.length) null else v
    } catch (_: Exception) { null }

    private class P(val s: String) {
        var i = 0
        fun peek() = if (i < s.length) s[i] else '\u0000'
        fun expr(): Double {
            var v = term()
            while (peek() == '+' || peek() == '-') { val op = s[i++]; val r = term(); v = if (op == '+') v + r else v - r }
            return v
        }
        fun term(): Double {
            var v = pow()
            while (peek() == '*' || peek() == '/' || peek() == '%') {
                val op = s[i++]; val r = pow()
                v = when (op) { '*' -> v * r; '/' -> if (r == 0.0) throw ArithmeticException() else v / r; else -> v % r }
            }
            return v
        }
        fun pow(): Double { val b = unary(); return if (peek() == '^') { i++; b.pow(pow()) } else b }
        fun unary(): Double = if (peek() == '-') { i++; -unary() } else if (peek() == '+') { i++; unary() } else atom()
        fun atom(): Double {
            if (peek() == '(') { i++; val v = expr(); if (peek() != ')') throw IllegalStateException(); i++; return v }
            val st = i
            while (peek().isDigit() || peek() == '.') i++
            if (st == i) throw IllegalStateException()
            return s.substring(st, i).toDouble()
        }
    }
}

/** Basic unit conversions. Offline, tiny table. */
object Units {
    private data class U(val dim: String, val factor: Double, val label: String)

    private val table: Map<String, U> = buildMap {
        fun add(names: List<String>, u: U) = names.forEach { put(it, u) }
        add(listOf("km", "kilometer", "kilometers", "kilometre", "kilometres"), U("len", 1000.0, "km"))
        add(listOf("m", "meter", "meters", "metre", "metres"), U("len", 1.0, "m"))
        add(listOf("cm", "centimeter", "centimeters", "centimetre", "centimetres"), U("len", 0.01, "cm"))
        add(listOf("mm", "millimeter", "millimeters"), U("len", 0.001, "mm"))
        add(listOf("mi", "mile", "miles"), U("len", 1609.344, "miles"))
        add(listOf("ft", "foot", "feet"), U("len", 0.3048, "ft"))
        add(listOf("in", "inch", "inches"), U("len", 0.0254, "inches"))
        add(listOf("yd", "yard", "yards"), U("len", 0.9144, "yards"))
        add(listOf("kg", "kilo", "kilos", "kilogram", "kilograms"), U("mass", 1.0, "kg"))
        add(listOf("g", "gram", "grams"), U("mass", 0.001, "g"))
        add(listOf("mg", "milligram", "milligrams"), U("mass", 1e-6, "mg"))
        add(listOf("lb", "lbs", "pound", "pounds"), U("mass", 0.45359237, "lb"))
        add(listOf("oz", "ounce", "ounces"), U("mass", 0.028349523125, "oz"))
        add(listOf("l", "liter", "liters", "litre", "litres"), U("vol", 1.0, "L"))
        add(listOf("ml", "milliliter", "milliliters", "millilitre", "millilitres"), U("vol", 0.001, "mL"))
        add(listOf("gal", "gallon", "gallons"), U("vol", 3.785411784, "gallons"))
        add(listOf("cup", "cups"), U("vol", 0.24, "cups"))
        add(listOf("kmh", "kph", "km/h"), U("speed", 1 / 3.6, "km/h"))
        add(listOf("mph"), U("speed", 0.44704, "mph"))
        add(listOf("sec", "secs", "second", "seconds"), U("time", 1.0, "seconds"))
        add(listOf("min", "mins", "minute", "minutes"), U("time", 60.0, "minutes"))
        add(listOf("hr", "hrs", "hour", "hours"), U("time", 3600.0, "hours"))
        add(listOf("day", "days"), U("time", 86400.0, "days"))
        add(listOf("week", "weeks"), U("time", 604800.0, "weeks"))
        add(listOf("kb"), U("data", 1e3, "KB")); add(listOf("mb"), U("data", 1e6, "MB"))
        add(listOf("gb"), U("data", 1e9, "GB")); add(listOf("tb"), U("data", 1e12, "TB"))
        add(listOf("c", "°c", "celsius", "centigrade"), U("temp", 0.0, "°C"))
        add(listOf("f", "°f", "fahrenheit"), U("temp", 1.0, "°F"))
        add(listOf("k", "kelvin"), U("temp", 2.0, "K"))
    }

    private val re = Regex("(-?\\d+(?:\\.\\d+)?)\\s*(°?[a-z/]+)(?:\\s+(?:degrees?))?\\s+(?:to|in|into|as)\\s+(°?[a-z/]+)$")

    fun parse(sentence: String): Pair<String, String>? {
        val s = sentence.replace(Regex("^(convert|what's|whats|what is|how many|how much is)\\s+"), "").replace("degrees ", "")
        val m = re.find(s) ?: return null
        val v = m.groupValues[1].toDouble()
        val a = table[m.groupValues[2]] ?: return null
        val b = table[m.groupValues[3]] ?: return null
        if (a.dim != b.dim) return null
        val out = if (a.dim == "temp") {
            val c = when (a.factor) { 0.0 -> v; 1.0 -> (v - 32) * 5 / 9; else -> v - 273.15 }
            when (b.factor) { 0.0 -> c; 1.0 -> c * 9 / 5 + 32; else -> c + 273.15 }
        } else v * a.factor / b.factor
        return "${MiniCalc.format(v)} ${a.label} to ${b.label}" to "${MiniCalc.format(v)} ${a.label} is ${MiniCalc.format(out)} ${b.label}"
    }
}
