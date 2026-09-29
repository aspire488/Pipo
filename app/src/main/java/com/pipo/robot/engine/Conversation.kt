package com.pipo.robot.engine

import com.pipo.robot.data.Catalog
import com.pipo.robot.data.EventType
import com.pipo.robot.data.Frequency
import com.pipo.robot.data.JournalCategory
import com.pipo.robot.data.MemoryType
import com.pipo.robot.data.Mood
import com.pipo.robot.data.PendingEvent
import com.pipo.robot.data.PipoSettings
import com.pipo.robot.data.PipoState
import com.pipo.robot.data.UserResponse
import kotlin.random.Random

enum class ChatAction { NONE, DANCE, SLEEP, WAKE, PLAY, SPIN, HIDE, EMBARRASSED, ANNOYED, HAPPY, THINK, PHONE }

data class ChatResult(
    val text: String,
    val action: ChatAction = ChatAction.NONE,
    val payload: String = "",
    val sfx: Sfx? = null,
    /** True when the text must not be replaced by the optional AI brain (e.g. name confirmation). */
    val locked: Boolean = false,
    val phone: PhoneRequest? = null,
)

/** Rule-based conversation. Always runs (it owns the state effects); AI may only rephrase. */
object LocalBrain {
    var lastWasJoke: String? = null

    private fun has(s: String, vararg words: String) = words.any { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(s) }

    fun respond(st: PipoState, raw: String, now: Long, rng: Random, awaitingName: Boolean): ChatResult {
        val input = raw.trim()
        val s = input.lowercase()
        val mood = st.mood.current
        val t = st.profile.traits
        st.profile.interactions += 1
        st.lastUserInteractionAt = now
        st.profile.relationship = clamp01(st.profile.relationship + 0.003f)
        MoodEngine.bump(st, loneliness = -0.2f, boredom = -0.1f)

        val joke = lastWasJoke
        lastWasJoke = null

        // --- name ---
        val nameMatch = Regex("(?:my name is|call me|i am called|name's)\\s+([\\p{L}][\\p{L}'-]{0,19})", RegexOption.IGNORE_CASE).find(input)
        if (awaitingName || nameMatch != null) {
            val candidate = nameMatch?.groupValues?.get(1) ?: input.split(Regex("\\s+")).firstOrNull { it.isNotBlank() }.orEmpty()
            val clean = candidate.filter { it.isLetter() || it == '-' || it == '\'' }.take(20)
            if (clean.isNotEmpty()) {
                val name = clean.replaceFirstChar { it.uppercase() }
                st.profile.userName = name
                Chronicle.remember(st, MemoryType.USER_FACT, "your name is $name", 1f, now, "user:name")
                Chronicle.journal(st, "Pipo learned your name", "It's $name. He practiced saying it.", JournalCategory.CONVERSATION, now)
                return ChatResult(pick(rng, "$name. $name. Okay. I'll remember that.", "Nice to meet you, $name. I'm Pipo. You knew that."),
                    ChatAction.HAPPY, sfx = Sfx.HAPPY, locked = true)
            }
        }

        // --- phone actions (explicit requests only) ---
        PhoneCommands.parse(input)?.let { req ->
            Chronicle.remember(st, MemoryType.EVENT, "you asked me to use ${req.cmd.name.lowercase()}", 0.15f, now, "phone:${req.cmd.name}")
            val text = if (req.needsConfirm) PhoneCommands.confirmQuestion(req) else PhoneCommands.line(req, mood, rng)
            return ChatResult(text, ChatAction.PHONE, sfx = Sfx.BEEP, locked = true, phone = req)
        }

        // --- likes / facts ---
        Regex("i (?:really )?(?:like|love|enjoy) ([\\p{L} ]{2,30})").find(s)?.let { m ->
            val thing = m.groupValues[1].trim().removeSuffix(".")
            if (thing != "you") {
                Chronicle.remember(st, MemoryType.USER_FACT, "you like $thing", 0.6f, now, "like:$thing")
                return ChatResult(
                    if (t.stubbornness > 0.6f) "You like $thing. I like screws. We're both right."
                    else pick(rng, "You like $thing. Noted. Permanently.", "Ooh. $thing. I'll remember that."),
                    ChatAction.HAPPY, sfx = Sfx.BEEP)
            }
        }

        // --- laughing at a joke → inside joke ---
        if (has(s, "haha", "hahaha", "lol", "lmao", "funny") || s.contains("😂")) {
            return if (joke != null) {
                Chronicle.remember(st, MemoryType.JOKE, joke, 0.7f, now, "joke:${joke.hashCode()}")
                MoodEngine.bump(st, happiness = 0.15f)
                Personality.nudge(st, Trait.CONFIDENCE, 0.01f)
                ChatResult(Dialogue.pick(Dialogue.laughAtOwnJoke, rng), ChatAction.HAPPY, sfx = Sfx.LAUGH)
            } else ChatResult(pick(rng, "What's funny? Is it me?", "Hehe. I don't know why we're laughing."), ChatAction.HAPPY, sfx = Sfx.LAUGH)
        }

        if (has(s, "joke", "funny thing")) {
            val j = Dialogue.pick(Dialogue.jokes, rng)
            lastWasJoke = j
            return ChatResult(j, ChatAction.HAPPY, sfx = Sfx.LAUGH, locked = true)
        }

        // --- affection / insults ---
        if (has(s, "love you", "good robot", "cute", "adorable", "best", "good boy", "like you")) {
            MoodEngine.bump(st, happiness = 0.15f, affection = 0.1f)
            MoodEngine.setTransient(st, Mood.EMBARRASSED, now, 12_000)
            Personality.nudge(st, Trait.AFFECTION, 0.01f)
            st.profile.relationship = clamp01(st.profile.relationship + 0.01f)
            return ChatResult(Dialogue.pick(Dialogue.compliments, rng), ChatAction.EMBARRASSED, sfx = Sfx.HAPPY)
        }
        if (has(s, "stupid", "dumb", "idiot", "hate you", "bad robot", "ugly", "useless", "shut up")) {
            MoodEngine.bump(st, irritation = 0.35f, happiness = -0.1f)
            Personality.nudge(st, Trait.STUBBORNNESS, 0.01f)
            return ChatResult(Dialogue.pick(Dialogue.insulted, rng), ChatAction.ANNOYED, sfx = Sfx.GRUMBLE)
        }

        // --- commands (Pipo may refuse) ---
        fun refuses(): Boolean {
            val p = t.stubbornness * 0.5f + (if (mood == Mood.GRUMPY) 0.35f else 0f) + (if (mood == Mood.SLEEPY) 0.2f else 0f)
            return rng.nextFloat() < p
        }
        if (has(s, "dance")) {
            return if (refuses()) ChatResult(Dialogue.pick(Dialogue.refusals, rng), ChatAction.ANNOYED)
            else ChatResult(pick(rng, "Watch this.", "Okay. Only because I want to.", "Music in my head. Go."), ChatAction.DANCE, sfx = Sfx.HAPPY)
        }
        if (has(s, "spin", "do a flip", "trick")) {
            return if (refuses()) ChatResult(Dialogue.pick(Dialogue.refusals, rng), ChatAction.ANNOYED)
            else ChatResult("Ta-da.", ChatAction.SPIN, sfx = Sfx.HAPPY)
        }
        if (has(s, "play", "game", "rock paper", "tic tac", "tictactoe", "memory game", "reaction")) {
            val game = when {
                s.contains("rock") || s.contains("rps") -> "rps"
                s.contains("memory") -> "memory"
                s.contains("reaction") || s.contains("fast") -> "reaction"
                s.contains("tic") -> "tictactoe"
                else -> BehaviorEngine.favoriteGame(st) ?: "rps"
            }
            return if (mood == Mood.SLEEPY && t.laziness > 0.5f) ChatResult("Too sleepy. I'd lose. Which I never do.", ChatAction.NONE, sfx = Sfx.SLEEPY)
            else if (mood == Mood.GRUMPY && refuses()) ChatResult("No. ...Okay, fine. One game.", ChatAction.PLAY, game)
            else ChatResult(pick(rng, "Yes! ${Dialogue.gameName(game).replaceFirstChar { it.uppercase() }}. Prepare yourself.", "Finally. Let's go."), ChatAction.PLAY, game, Sfx.HAPPY)
        }
        if (has(s, "good night", "goodnight", "go to sleep", "sleep", "bedtime")) {
            return if (st.mood.energy < 0.6f || isNight(hourOf(now))) ChatResult("Okay. Goodnight. Don't let the screws bite.", ChatAction.SLEEP, sfx = Sfx.SLEEPY)
            else ChatResult("I'm not tired. You're tired.", ChatAction.ANNOYED)
        }
        if (has(s, "wake up", "wake")) return ChatResult("I'm awake! I was always awake.", ChatAction.WAKE, sfx = Sfx.SURPRISED)
        if (has(s, "hide")) return ChatResult("You can't see me.", ChatAction.HIDE)

        // --- questions about Pipo ---
        if (has(s, "how are you", "how r u", "how do you feel", "you okay", "you ok", "hows it going")) return ChatResult(feel(st, rng))
        if (has(s, "what are you doing", "wyd", "what's up", "whats up", "sup")) return ChatResult(Dialogue.activityAnswer(st))
        if (has(s, "project", "building", "build")) return ChatResult(Dialogue.activityAnswer(st))
        if (has(s, "found", "find", "discover", "collection")) {
            val last = st.world.items.lastOrNull()?.let { Catalog.item(it.catalogId) }
            return ChatResult(if (last != null) "My latest find is a ${last.name.lowercase()}. ${last.foundLine}" else "Nothing yet. But I'm looking. Constantly.", ChatAction.THINK)
        }
        if (has(s, "remember", "memory", "recall")) {
            val m = Chronicle.recall(st, rng, now)
            return ChatResult(if (m != null) "I remember... ${m.content}." else "My memory is mostly screws right now.", ChatAction.THINK)
        }
        if (has(s, "who are you", "what are you", "your name")) return ChatResult("I'm Pipo. I live in your phone. I'm small, but I have opinions.")
        if (has(s, "assistant", "help me", "can you help", "are you ai", "are you an ai", "chatgpt")) {
            return ChatResult(pick(rng, "I'm not an assistant. I'm Pipo. I help by existing.", "I can't do your homework. I can sit next to you while you do it."))
        }
        if (has(s, "hi", "hello", "hey", "yo", "hii", "heyy")) {
            return ChatResult(when (mood) {
                Mood.GRUMPY -> "Hi. I'm grumpy. It's not about you."
                Mood.SLEEPY -> "...hi. *yawn*"
                Mood.EXCITED -> "HI! Hi hi hi."
                else -> pick(rng, "Hi!", "Hey you.", "Oh, hi. I was just here. Being here.")
            }, ChatAction.HAPPY, sfx = Sfx.BEEP)
        }
        if (has(s, "bye", "see you", "gotta go", "later")) return ChatResult(pick(rng, "Bye! I'll be here. Doing stuff.", "Okay. I'll guard the room."), sfx = Sfx.BEEP)
        if (has(s, "sorry")) { MoodEngine.bump(st, irritation = -0.4f); return ChatResult("...okay. We're fine.", ChatAction.HAPPY) }
        if (has(s, "no", "nope")) return ChatResult(pick(rng, "Yes.", "Rude, but okay.", "Hmm. Fair."))
        if (has(s, "yes", "yeah", "yep", "ok", "okay")) return ChatResult(pick(rng, "Good.", "I knew you'd say that.", "Great. What did I ask?"))

        // --- fallback: character-first, allowed to misunderstand ---
        val topic = s.split(Regex("[^\\p{L}]+")).filter { it.length > 4 }.maxByOrNull { it.length }
        if (topic != null) Chronicle.remember(st, MemoryType.CONVERSATION, "you talked about $topic", 0.2f, now, "topic:$topic")
        return when {
            s.endsWith("?") -> ChatResult(Dialogue.pick(Dialogue.misunderstand, rng), ChatAction.THINK)
            topic != null && rng.nextFloat() < 0.45f -> ChatResult("\"${topic.replaceFirstChar { it.uppercase() }}.\" ${pick(rng, "I like that word.", "What's that? Is it shiny?", "Tell me more. Actually, I'll guess.")}", ChatAction.THINK)
            else -> ChatResult(Dialogue.thought(st, rng), ChatAction.THINK)
        }
    }

    fun feel(st: PipoState, rng: Random): String = when (st.mood.current) {
        Mood.HAPPY -> pick(rng, "Good! Really good.", "Happy. My antenna is doing the thing.")
        Mood.EXCITED -> "EXCELLENT. I don't know why. Everything!"
        Mood.CURIOUS -> "Curious. Everything is suspicious today."
        Mood.SLEEPY -> "Sleepy. My eyes weigh a thousand screws."
        Mood.BORED -> "Bored. Entertain me. Please."
        Mood.GRUMPY -> "Grumpy. Don't ask why. It's the screws."
        Mood.LONELY -> "Better now that you're here."
        Mood.NERVOUS -> "Nervous. Something's going to happen. Probably nothing."
        Mood.PROUD -> "Proud. I'm very impressive right now."
        Mood.EMBARRASSED -> "Fine! Totally fine. Don't look at me."
        Mood.MISCHIEVOUS -> "Great. No reason. Don't check the plant."
        Mood.RELAXED -> "Chill. Just robot things."
    }

    private fun pick(rng: Random, vararg options: String) = options[rng.nextInt(options.size)]
}

/* ================================================================== */
/*  Notification policy (pure: decides IF and WHAT, not HOW)           */
/* ================================================================== */

enum class NotifAction { SEE, PLAY, NOT_NOW }

data class NotificationDecision(val event: PendingEvent, val text: String, val actions: List<NotifAction>)

object NotificationPolicy {

    fun inQuietHours(settings: PipoSettings, hour: Int): Boolean {
        val a = settings.quietStartHour; val b = settings.quietEndHour
        return if (a == b) false else if (a < b) hour in a until b else hour >= a || hour < b
    }

    private fun minGap(f: Frequency) = when (f) { Frequency.RARE -> 20 * HOUR; Frequency.NORMAL -> 7 * HOUR; Frequency.FREQUENT -> 3 * HOUR }
    private fun maxPerDay(f: Frequency) = when (f) { Frequency.RARE -> 1; Frequency.NORMAL -> 2; Frequency.FREQUENT -> 4 }
    private fun threshold(f: Frequency) = when (f) { Frequency.RARE -> 0.62f; Frequency.NORMAL -> 0.44f; Frequency.FREQUENT -> 0.3f }

    /** Count of the most recent delivered notifications the user ignored in a row. */
    fun ignoredStreak(s: PipoState, now: Long): Int =
        s.notifications.filter { it.delivered }.takeLast(5).reversed()
            .takeWhile { it.response == UserResponse.NONE || it.response == UserResponse.NOT_NOW }
            .count { now - it.timestamp > HOUR }

    fun decide(s: PipoState, now: Long, appForeground: Boolean, rng: Random): NotificationDecision? {
        val set = s.settings
        if (!set.notificationsEnabled || appForeground) return null
        if (inQuietHours(set, hourOf(now))) return null
        if (now - s.lastUserInteractionAt < 90 * MINUTE) return null

        val delivered = s.notifications.filter { it.delivered }
        val last = delivered.lastOrNull()?.timestamp ?: 0L
        var gap = minGap(set.frequency)
        if (ignoredStreak(s, now) >= 3) gap = (gap * 2.5f).toLong() // silence is part of Pipo's personality
        if (now - last < gap) return null
        if (delivered.count { now - it.timestamp < DAY } >= maxPerDay(set.frequency)) return null

        val t = s.profile.traits
        var th = threshold(set.frequency) - (t.sociability - 0.5f) * 0.1f
        if (Personality.isShy(t)) th += 0.08f

        val ev = s.events
            .filter { !it.notified && !it.shownInApp && now - it.createdAt < DAY && set.categoryOn(it.type.category) && it.importance >= th }
            .maxByOrNull { it.importance } ?: return null

        val actions = when (ev.type) {
            EventType.WANT_PLAY -> listOf(NotifAction.PLAY, NotifAction.NOT_NOW)
            else -> listOf(NotifAction.SEE)
        }
        return NotificationDecision(ev, text(ev, s, now, rng), actions)
    }

    enum class Voice { NORMAL, EXCITED, SHY, SLEEPY, MISCHIEVOUS }

    fun voice(ev: PendingEvent, s: PipoState, now: Long): Voice {
        val t = s.profile.traits
        val h = hourOf(now)
        return when {
            ev.mood == Mood.SLEEPY || h >= 22 || h < 7 -> Voice.SLEEPY
            ev.mood == Mood.MISCHIEVOUS || (t.mischief > 0.65f && ev.type in setOf(EventType.PRANK, EventType.PROJECT_FAILED, EventType.PROJECT_EVOLVED)) -> Voice.MISCHIEVOUS
            ev.mood == Mood.EXCITED || ev.mood == Mood.PROUD || (ev.importance >= 0.75f && t.sociability > 0.5f) -> Voice.EXCITED
            Personality.isShy(t) -> Voice.SHY
            else -> Voice.NORMAL
        }
    }

    fun text(ev: PendingEvent, s: PipoState, now: Long, rng: Random): String {
        val v = voice(ev, s, now)
        val n = s.profile.userName
        fun p(vararg o: String) = Dialogue.personalize(o[rng.nextInt(o.size)], n)
        return when (ev.type) {
            EventType.DISCOVERY -> when (v) {
                Voice.EXCITED -> p("{N}. {N}. {N}. COME LOOK.", "I FOUND SOMETHING. COME SEE.")
                Voice.SHY -> p("Um... I found something.", "I found a thing. If you want to see. No pressure.")
                Voice.SLEEPY -> p("I found something... tomorrow.", "found a thing... ...zz")
                Voice.MISCHIEVOUS -> p("I found something. It's mine now.", "I found a thing. Don't ask where.")
                Voice.NORMAL -> p("{n}. I found something weird.", "I found something weird.", "I found a thing. Come see.")
            }
            EventType.REVEAL -> p("I figured out what the thing does.", "Okay. I know what it is now. Come see.")
            EventType.PROJECT_DONE -> when (v) {
                Voice.EXCITED -> p("IT WORKS. COME SEE.", "I finally finished my little project!")
                Voice.SHY -> p("I made a thing. It's small. Do you want to see?")
                Voice.SLEEPY -> p("made a thing... showing you... later...")
                Voice.MISCHIEVOUS -> p("I made something. It's probably fine.")
                Voice.NORMAL -> p("I finally finished it.", "I finally finished my little project!", "I made something.")
            }
            EventType.PROJECT_FAILED, EventType.PROJECT_EVOLVED, EventType.PRANK -> when (v) {
                Voice.MISCHIEVOUS -> p("I did something. You probably shouldn't ask.", "I have done a small crime. A very small one.")
                Voice.SHY -> p("Um. Something happened. It wasn't me. It was me.")
                Voice.SLEEPY -> p("did something... explain later...")
                else -> p("Don't judge me.", "I did something. Don't be mad.", "So. Funny story.")
            }
            EventType.SURPRISE -> if (v == Voice.SHY) p("I made you a thing. You don't have to like it.") else p("I made you something.", "{n}. I made you something.")
            EventType.THOUGHT -> if (ev.payload.startsWith("idea:")) when (v) {
                Voice.SLEEPY -> p("...idea. big one. tomorrow.")
                Voice.SHY -> p("I have an idea. It's probably nothing.")
                Voice.EXCITED -> p("I HAVE AN IDEA.", "{n}. I have an idea.")
                else -> p("I have an idea.", "{n}. I have an idea.")
            } else when (v) {
                Voice.SLEEPY -> p("...had a thought. forgot it.")
                Voice.MISCHIEVOUS -> p("I have an extremely questionable idea.")
                else -> p("{n}. I have a thought.", "I have a thought. It's a good one. Probably.")
            }
            EventType.SILENT_DOTS -> "..."
            EventType.WANT_PLAY -> when (v) {
                Voice.SHY -> p("Are you busy or can I bother you?")
                Voice.EXCITED -> p("GAME? GAME. Let's play.")
                else -> p("Are you busy or can I bother you?", "Wanna play ${Dialogue.gameName(ev.payload)}? I've been practicing.")
            }
            EventType.HI -> if (v == Voice.SHY) p("Hi. That's all.") else p("Just wanted to say hi.", "Hi. No reason.")
            EventType.MILESTONE -> p("We've been hanging out for ${ev.payload} days. I counted.")
        }
    }
}
