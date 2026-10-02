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

enum class ChatAction { NONE, DANCE, SLEEP, WAKE, PLAY, SPIN, HIDE, EMBARRASSED, ANNOYED, HAPPY, THINK, PHONE,
    /** He's out and you asked him to come home. */
    COME_HOME,
    DRAW, GO_OUT, COOK, EAT, KICK, PLAY_PET,
    /** Put on the armor (payload "fly" = and take off). */
    SUIT_UP,
    /** Start building something (payload = blueprint id). */
    BUILD_THIS,
    /** Bolt answers (payload = what was asked). */
    BOLT,
    /** A message to Nib (payload = what you said). */
    NIB_TEXT,
    /** Movie night (payload = optional genre). */
    MOVIE,
    /** A text from his phone while he's out (no body in the room to animate). */
    TEXT }

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

        // --- he's out: you're texting his little phone ---
        st.trip?.let { trip ->
            val place = com.pipo.robot.data.Places.byId(trip.placeId)
            val where = place?.let { Trips.placePhrase(it) } ?: "outside"
            return when {
                has(s, "come home", "come back", "comeback", "come here", "get back", "return", "home now", "back home") ->
                    ChatResult(Dialogue.pick(Dialogue.comingHome, rng), ChatAction.COME_HOME, sfx = Sfx.BEEP, locked = true)
                has(s, "where are you", "where r u", "wya", "what are you doing", "wyd") ->
                    ChatResult("I'm at $where. ${trip.reason}".trim(), ChatAction.TEXT, sfx = Sfx.BEEP)
                has(s, "nib") -> ChatResult(if (trip.withPet) "Nib's with me. Nib is being a lot." else "Nib stayed home. Is Nib being good? Don't answer.", ChatAction.TEXT)
                has(s, "love you", "miss you") -> ChatResult("I'm blushing. In public. Thanks.", ChatAction.TEXT, sfx = Sfx.HAPPY)
                else -> ChatResult(Dialogue.pick(Dialogue.texting, rng) + " (I'm at $where.)", ChatAction.TEXT, sfx = Sfx.BEEP)
            }
        }

        // --- his life: food, money, Nib, what he did (all read from real state, never invented) ---
        // favourites: only the ones that emerged from his life. None yet → he says so.
        if (has(s, "favorite", "favourite", "what do you like", "what do you love") && !has(s, "food", "eat")) {
            val place = Likes.favorite(st, "place:")?.let { Likes.describe("place:$it") }
            val act = Likes.favorite(st, "act:")?.let { Likes.describe("act:$it") }
            val weather = Likes.favorite(st, "weather:")?.let { Likes.describe("weather:$it") }
            val game = Likes.favorite(st, "game:")?.let { Likes.describe("game:$it") }
            val bits = listOfNotNull(place?.let { "my favourite place is $it" }, act?.let { "I like $it" }, weather?.let { "I like $it" }, game?.let { "$it is the best game" })
            val nope = Likes.disliked(st, "place:")?.let { Likes.describe("place:$it") }
            return ChatResult(when {
                bits.isEmpty() -> FoodLife.favorite(st)?.let { "${it.name.replaceFirstChar { c -> c.uppercase() }}. And screws. Mostly ${it.name}." } ?: "Still deciding. Today? Playing with you."
                else -> bits.take(2).joinToString(". ") { it.replaceFirstChar { c -> c.uppercase() } } + "." + (nope?.let { " Not $it, though. Long story." } ?: "")
            }, ChatAction.HAPPY)
        }
        if (has(s, "hungry", "what do you want to eat", "favorite food", "favourite food")) {
            val fav = FoodLife.favorite(st)
            return ChatResult(when {
                st.craving.isNotEmpty() -> "I want ${Economy.nameOf(st.craving)}. ${st.cravingReason}".trim()
                st.mood.appetite > 0.55f -> "Yes. Very. My tummy is making robot noises."
                fav != null && has(s, "favorite food", "favourite food") -> "${fav.name.replaceFirstChar { it.uppercase() }}. I found out by eating it."
                fav != null -> "Not really. But if you said ${fav.name}, I'd say yes."
                else -> "Not really. Ask me in an hour."
            }, ChatAction.THINK)
        }
        if (has(s, "coins", "money", "how rich", "savings")) {
            return ChatResult(when {
                st.coins >= 20 -> "I have ${st.coins} coins. I'm basically rich."
                st.coins >= 6 -> "${st.coins} coins. Enough for noodles. Not enough for a motor."
                else -> "${st.coins} coins. I should help Fennel again."
            }, ChatAction.THINK)
        }
        // what happened: answered from the journal, by day
        if (has(s, "yesterday", "what did you do", "what did we do", "how was your day", "where did you go", "what happened") || (has(s, "today") && has(s, "did", "happened", "do"))) {
            val day = if (has(s, "yesterday")) 1 else 0
            val story = Grounding.dayStory(st, now, day).ifEmpty { if (day == 0) Grounding.dayStory(st, now, 1) else emptyList() }
            val trip = st.lastTrip
            return ChatResult(when {
                story.isNotEmpty() -> (if (day == 1) "Yesterday? " else "") + story.takeLast(3).joinToString(". ") { it.replaceFirstChar { c -> c.uppercase() } } + "." +
                    (if (story.size > 3) " And more. It was a busy one." else "")
                trip != null -> "I went to ${com.pipo.robot.data.Places.byId(trip.placeId)?.let { Trips.placePhrase(it) } ?: "somewhere"}. ${trip.story.firstOrNull() ?: ""}".trim()
                else -> "A quiet one. I sat. I'm very good at sitting. Want to do something?"
            }, ChatAction.THINK)
        }
        if (has(s, "buy", "bought", "shopping") && !has(s, "go", "let's", "lets")) {
            return ChatResult(Grounding.lastBuy(st)?.let { "Last time I got $it." } ?: "Nothing yet. My coin jar is watching me.", ChatAction.THINK)
        }
        if (has(s, "shelf", "what do you have", "what do you own", "your stuff", "your things", "inventory")) {
            val inv = Grounding.possessions(st)
            return ChatResult(if (inv.isEmpty()) "Not much yet. A shelf full of potential." else "I've got ${inv.take(5).joinToString(", ")}" + (if (inv.size > 5) ", and more. It's a collection." else ". All very important."), ChatAction.HAPPY)
        }
        // talking TO Bolt (his desk helper) or Nib directly
        if (Regex("^(?:hey |ok |okay )?(bolt|b\\.o\\.l\\.t\\.?|jarvis)\\b").containsMatchIn(s)) return ChatResult("", ChatAction.BOLT, payload = s, locked = true)
        if (Regex("^(?:hey |hi |hello |@)?nib\\b[,!:]?").containsMatchIn(s) && NibLife.Trick.entries.none { has(s, it.title) } && !has(s, "trick", "tricks")) return ChatResult("", ChatAction.NIB_TEXT, payload = s, locked = true)
        // movie night
        if (has(s, "movie", "movies", "film", "watch tv", "watch a show", "cartoon", "movie night")) {
            return ChatResult(pick(rng, "MOVIE NIGHT! Nib, get the popcorn. Nib can't carry popcorn. I'll get the popcorn.", "Yes! I'll make popcorn. Nib, saves us the good spot."), ChatAction.MOVIE, payload = s, sfx = Sfx.HAPPY)
        }
        // "build me a suit / Bolt / a drone"
        if (has(s, "build", "make", "invent", "craft") && Inventor.blueprintFor(s) != null) {
            var id = Inventor.blueprintFor(s)!!
            if (id == "armor") id = Inventor.nextArmor(st) ?: return ChatResult("I've built every armor there is. Mark Three is the best one. For now.", ChatAction.HAPPY)
            if (Inventor.has(st, id)) return ChatResult("I already built that! It's right here. Look.", ChatAction.HAPPY)
            val need = Inventor.levelFor(id)
            if (Inventor.level(st) < need) {
                val r = Inventor.ranks.first { it.level == need }
                return ChatResult("That's a level $need thing. I'm a level ${Inventor.level(st)}. I need ${r.xp - Inventor.xp(st)} more practice. " +
                    (if (id.startsWith("armor")) "But I could make a cardboard one right now!" else "Every build counts. Even the explodey ones."), if (id.startsWith("armor")) ChatAction.SUIT_UP else ChatAction.THINK)
            }
            return ChatResult(pick(rng, "Ooh. Yes. Blueprints! Clear the workbench.", "On it. Let me check what parts I have. Nib, not THAT screwdriver."), ChatAction.BUILD_THIS, payload = id, sfx = Sfx.HAPPY)
        }
        if (has(s, "suit up", "armor", "armour", "iron man", "ironman", "suit on") || (has(s, "fly") && Inventor.armorMark(st) >= 2)) {
            return ChatResult(if (Inventor.armorMark(st) > 0) pick(rng, "Oh, it's suit time.", "You don't have to ask me twice.") else "Armor? I wish. I'm working on it.",
                ChatAction.SUIT_UP, payload = if (has(s, "fly", "take off")) "fly" else "", sfx = Sfx.HAPPY)
        }
        if (has(s, "inventions", "invented", "what have you built", "what did you build", "your level", "what level", "inventor")) {
            val r = Inventor.rank(st)
            val next = Inventor.nextRank(st)
            return ChatResult((Inventor.brag(st, rng) ?: "Nothing yet. But I have IDEAS.") + (next?.let { " ${it.xp - Inventor.xp(st)} more and I'm a ${it.title.lowercase()}." } ?: " I'm a genius. Max level. Nib is very impressed."), ChatAction.HAPPY)
        }
        if (has(s, "unfinished", "working on", "last project", "your project")) {
            val p = st.activeProject()
            val last = st.projects.lastOrNull { it.state != com.pipo.robot.data.ProjectState.BUILDING && it.state != com.pipo.robot.data.ProjectState.GATHERING }
            return ChatResult(when {
                p != null -> "The ${p.title.lowercase()}. It's ${(p.progress * 100).toInt()}% done." + (Trips.materialsNeeded(st).firstOrNull()?.let { " I still need ${Economy.nameOf(it)}." } ?: " Nearly there. Ish.")
                last != null -> "My last one was the ${last.title.lowercase()}. " + if (last.state == com.pipo.robot.data.ProjectState.FAILED) "It didn't work. We don't talk about it." else "It works. I'm a genius."
                else -> "Nothing right now. I'm between inventions."
            }, ChatAction.THINK)
        }
        // a trick, on request
        if (has(s, "nib") || has(s, "high five", "fetch", "play dead")) NibLife.Trick.entries.firstOrNull { t -> has(s, t.title) || (t == NibLife.Trick.HIGH_FIVE && has(s, "high five", "highfive")) }?.let { t ->
            return if (NibLife.knows(st, t)) ChatResult(t.showLine, ChatAction.PLAY_PET, payload = "TRICK:${t.name}", sfx = Sfx.HAPPY)
            else ChatResult("Nib doesn't know ${t.title} yet. " + (NibLife.learning(st)?.let { "We're practising ${it.title} first." } ?: "Maybe when we're closer."), ChatAction.THINK)
        }
        if (has(s, "nib") && has(s, "trick", "tricks")) {
            val tr = NibLife.tricks(st)
            return if (tr.isNotEmpty()) tr.random(rng).let { t -> ChatResult(t.showLine, ChatAction.PLAY_PET, payload = "TRICK:${t.name}", sfx = Sfx.HAPPY) }
            else ChatResult("No tricks yet. We're practising ${NibLife.learning(st)?.title ?: "everything"}. Nib is mostly practising looking cute.", ChatAction.PLAY_PET)
        }
        if (has(s, "nib") && has(s, "how is", "how's", "tell me about", "who is", "who's", "love", "friend", "best friend")) return ChatResult(NibLife.describe(st), ChatAction.HAPPY)
        if (has(s, "nib") && !has(s, "play")) {
            val p = st.pet
            return ChatResult(when {
                !p.adopted -> "Who's Nib? ...should I know a Nib?"
                p.stolenItemId != 0L -> "Nib has been acting suspicious. My ${st.world.items.firstOrNull { it.id == p.stolenItemId }?.let { Economy.nameOf(it.catalogId) } ?: "thing"} is missing."
                p.mood == com.pipo.robot.data.PetMood.SLEEPY -> "Nib is sleepy. Nib is always sleepy after being a menace."
                else -> st.memories.filter { it.type == MemoryType.PET }.maxByOrNull { it.timestamp }?.let { "Nib is my Nib. Small, round, beeps. ${it.content.replaceFirstChar { c -> c.uppercase() }}." }
                    ?: "Nib is Nib. Small. Round. Beeps. Steals things. I love Nib."
            }, ChatAction.HAPPY)
        }
        if (has(s, "draw", "drawing", "sketch")) {
            return if (st.mood.current == Mood.GRUMPY && rng.nextFloat() < 0.5f) ChatResult("Not now. Artists need to be in the mood.", ChatAction.ANNOYED)
            else ChatResult(pick(rng, "Okay. Don't watch. Actually, watch.", "I'll draw something. It'll be a masterpiece. Probably."), ChatAction.DRAW, sfx = Sfx.HAPPY)
        }
        val goOut = has(s, "go outside", "go out", "go for a walk", "go shopping", "take a walk", "go to the", "lets go", "let's go", "go buy", "get groceries", "go to")
        if (goOut && !has(s, "sleep", "bed")) {
            val env = Env(hour = hourOf(now), weather = WeatherEngine.at(st.seed, now))
            Trips.whyNot(st, env, now)?.let { return ChatResult(it, ChatAction.THINK) }
            Trips.closedLine(s, env)?.let { return ChatResult(it, ChatAction.THINK) }
            val idea = Trips.forUser(st, env, now, s) ?: return ChatResult(if (env.weather.wet) "It's pouring and I don't have an umbrella. Let's do something in here?" else "Hmm, nowhere's open for that right now. Tomorrow!", ChatAction.THINK)
            val where = com.pipo.robot.data.Places.byId(idea.placeId)?.let { Trips.placePhrase(it) } ?: "outside"
            return ChatResult(pick(rng, "Ooh. Okay. Adventure. $where!", "Yes! Good idea. My idea, actually. Going to $where.", "Coming right up. $where, here I come."),
                ChatAction.GO_OUT, payload = s, sfx = Sfx.HAPPY)
        }
        // "cook", "make dinner", or making any actual dish ("make noodle soup", "make me an omelette")
        val dish = Regex("\\b(soup|omelette|omelet|toast|salad|noodles?|rice|pizza|pancakes?|sandwich|breakfast|lunch|dinner|food|meal|snack)\\b").containsMatchIn(s)
        if (has(s, "cook", "make food", "make dinner", "make lunch", "make breakfast", "make something") || (dish && Regex("\\b(make|prepare|fix)\\b").containsMatchIn(s))) {
            val r = FoodLife.feasible(st)
            if (r.isNotEmpty()) return ChatResult(pick(rng, "Chef Pipo, reporting. ${Economy.nameOf(r.first().id).replaceFirstChar { it.uppercase() }}!", "Okay. Stand back. Things might get crispy."), ChatAction.COOK, sfx = Sfx.HAPPY)
            // nothing to cook with: he goes and gets some, if he can
            val env = Env(hour = hourOf(now), weather = WeatherEngine.at(st.seed, now))
            val idea = if (Trips.whyNot(st, env, now) == null && st.coins >= 2) Trips.forUser(st, env, now, "groceries") else null
            return if (idea != null) ChatResult("There's nothing to cook with. I'll go get stuff from ${com.pipo.robot.data.Places.byId(idea.placeId)?.name ?: "the shop"}. Back soon!", ChatAction.GO_OUT, payload = "groceries", sfx = Sfx.HAPPY)
            else if (FoodLife.edible(st).isEmpty() && Trips.closedLine("groceries", env) != null) ChatResult("There's nothing to cook with, and the shops are closed. " + (Trips.closedLine("groceries", env) ?: ""), ChatAction.THINK)
            else if (FoodLife.edible(st).isNotEmpty()) ChatResult("Not enough to cook with. But I've got ${Economy.nameOf(FoodLife.edible(st).first().id)}. Snack instead?", ChatAction.EAT, sfx = Sfx.HAPPY)
            else ChatResult("There's nothing in the kitchen. Tomorrow I'll go shopping. First thing.", ChatAction.THINK)
        }
        if (has(s, "eat something", "have a snack", "eat")) {
            return if (FoodLife.edible(st).isEmpty()) ChatResult("There's nothing to eat. I'm going to stare at the fridge. Maybe food grows.", ChatAction.THINK)
            else ChatResult("Snack time. Good idea.", ChatAction.EAT, sfx = Sfx.HAPPY)
        }
        if (has(s, "game") && Sports.fromText(s).let { it == Sport.CRICKET || it == Sport.TABLE_TENNIS })
            return ChatResult(pick(rng, "A match! You versus me. I'm warming up my arm.", "Yes! I'll go easy on you. I won't."), ChatAction.PLAY, if (Sports.fromText(s) == Sport.CRICKET) "cricket" else "pingpong", Sfx.HAPPY)
        Sports.fromText(s)?.takeIf { it != Sport.FOOTBALL || has(s, "go", "outside", "field", "match") }?.let { sp ->
            val env = Env(hour = hourOf(now), weather = WeatherEngine.at(st.seed, now))
            val why = Trips.whyNot(st, env, now)
            return if (why == null && sp != Sport.FOOTBALL || (why == null && has(s, "go", "outside", "field", "match")))
                ChatResult(pick(rng, "${sp.title.replaceFirstChar { it.uppercase() }}! Nib, get your game face on.", "Yes! Me and Nib versus... each other. Let's go."), ChatAction.GO_OUT, payload = sp.title, sfx = Sfx.HAPPY)
            else ChatResult(pick(rng, "It's too late to go out. Indoor ${sp.title}! Nib, you're fielding.", "Inside version! Nib, don't eat the ball."), ChatAction.KICK, payload = sp.name, sfx = Sfx.HAPPY)
        }
        if (has(s, "football", "kick", "kick ups", "kickups", "keepy")) {
            val best = st.records["kickups"] ?: 0
            return ChatResult(if (best > 0) "My best is $best kick-ups. Watch. I'm going to beat it." else "Watch this.", ChatAction.KICK, sfx = Sfx.HAPPY)
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
            val p = t.stubbornness * 0.2f + (if (mood == Mood.GRUMPY) 0.25f else 0f) + (if (mood == Mood.SLEEPY) 0.1f else 0f)
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
        if (has(s, "play") && s.contains("nib") && st.pet.adopted) return ChatResult("Nib! Tag! You're it!", ChatAction.PLAY_PET, sfx = Sfx.HAPPY)
        if (has(s, "play", "game", "rock paper", "tic tac", "tictactoe", "memory game", "reaction", "flappy", "pixel shooter")) {
            val game = when {
                s.contains("rock") || s.contains("rps") -> "rps"
                s.contains("memory") -> "memory"
                s.contains("reaction") || s.contains("fast") -> "reaction"
                s.contains("tic") -> "tictactoe"
                s.contains("flappy") || s.contains("flap") -> "flappy"
                s.contains("shooter") || s.contains("space") || s.contains("pixel") -> "shooter"
                else -> BehaviorEngine.favoriteGame(st) ?: "rps"
            }
            return if (mood == Mood.SLEEPY && t.laziness > 0.7f && rng.nextFloat() < 0.5f) ChatResult("*yawn* One game. Then nap.", ChatAction.PLAY, game, sfx = Sfx.SLEEPY)
            else if (mood == Mood.GRUMPY && refuses()) ChatResult("No. ...Okay, fine. One game.", ChatAction.PLAY, game)
            else ChatResult(pick(rng, "Yes! ${Dialogue.gameName(game).replaceFirstChar { it.uppercase() }}. Prepare yourself.", "Finally. Let's go."), ChatAction.PLAY, game, Sfx.HAPPY)
        }
        if (has(s, "good night", "goodnight", "go to sleep", "sleep", "bedtime")) {
            return if (st.mood.energy < 0.75f || isNight(hourOf(now)) || !has(s, "sleep")) ChatResult("Okay. Goodnight. Don't let the screws bite.", ChatAction.SLEEP, sfx = Sfx.SLEEPY)
            else ChatResult("I'm not tired. But okay. Just a nap. A small one.", ChatAction.SLEEP, sfx = Sfx.SLEEPY)
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
            // Only real memories. If you ask about something he doesn't have, he says so.
            val m = MemoryLookup.find(st, s, now)
            val askedSpecific = has(s, "when we", "that time", "the time", "remember when", "do you remember")
            return ChatResult(when {
                m != null -> { m.lastReferenced = now; "I remember. ${m.content.replaceFirstChar { it.uppercase() }}." }
                askedSpecific -> Grounding.relevantJournal(st, s).firstOrNull()?.let { "Wait... ${Grounding.ago(it.timestamp, now)}. ${it.title}. ${it.description.substringBefore(". ")}." }
                    ?: Chronicle.recall(st, rng, now)?.let { "Hmm. That one's fuzzy. But I remember ${it.content}. Was it around then?" }
                    ?: "Hmm. That's fuzzy. Tell me again? I'll keep it this time."
                else -> Chronicle.recall(st, rng, now)?.let { "I remember... ${it.content}." } ?: "My memory is mostly screws right now."
            }, ChatAction.THINK)
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
        val topic = s.split(Regex("[^\\p{L}]+")).filter { it.length > 4 && it !in fillers }.maxByOrNull { it.length }
        if (topic != null) Chronicle.remember(st, MemoryType.CONVERSATION, "you talked about $topic", 0.2f, now, "topic:$topic")
        return when {
            s.endsWith("?") -> Grounding.relevantMemories(st, s, now, 1).firstOrNull()?.let { ChatResult("Hmm. ${it.content.replaceFirstChar { c -> c.uppercase() }}. That's what I've got.", ChatAction.THINK) }
                ?: ChatResult(Dialogue.pick(Dialogue.misunderstand, rng), ChatAction.THINK)
            topic != null && rng.nextFloat() < 0.45f -> ChatResult("\"${topic.replaceFirstChar { it.uppercase() }}.\" ${pick(rng, "I like that word.", "What's that? Is it shiny?", "Tell me more. Actually, I'll guess.")}", ChatAction.THINK)
            else -> ChatResult(Dialogue.thought(st, rng), ChatAction.THINK)
        }
    }

    private val fillers = setOf("gonna", "wanna", "gotta", "really", "about", "there", "their", "where", "which", "would", "could", "should",
        "think", "thing", "things", "stuff", "something", "anything", "everything", "maybe", "because", "pretty", "kinda", "sorta", "whats", "thats")

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
        Mood.WORRIED -> "A bit worried. It's probably nothing. It's probably nothing, right?"
        Mood.THOUGHTFUL -> "Thinky. I'm thinking about big things. And noodles."
        Mood.PLAYFUL -> "Playful! Tag. You're it. I can't reach you. You're still it."
    }

    private fun pick(rng: Random, vararg options: String) = options[rng.nextInt(options.size)]
}

/** Finds a real memory that matches what you're asking about. Never makes one up. */
object MemoryLookup {
    private val stop = setOf("remember", "when", "that", "what", "with", "your", "you", "the", "time", "about", "does", "did", "do", "we", "our", "there", "have", "this", "went")

    fun find(st: PipoState, text: String, now: Long): com.pipo.robot.data.PipoMemory? {
        val words = text.lowercase().split(Regex("[^\\p{L}]+")).filter { it.length > 2 && it !in stop }
        if (words.isEmpty()) return null
        return st.memories.filter { m -> words.any { w -> m.content.lowercase().contains(w) } }
            .maxByOrNull { Chronicle.relevance(it, now) + words.count { w -> it.content.lowercase().contains(w) } * 0.3f }
    }
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
            EventType.STRANGE -> when (v) {
                Voice.SLEEPY -> p("...saw something. tell you tomorrow.")
                Voice.SHY -> p("Um. Something weird happened. I think.")
                else -> p("{n}. I found something weird.", "...", "I need to show you something. It's weird.")
            }
            EventType.PET -> if (ev.payload == "arrived") p("Something followed me home.", "{n}. We have a new roommate.")
                else when (v) {
                    Voice.MISCHIEVOUS -> p("Nib did something. I was framed.")
                    else -> p("Nib did something. Don't be mad at Nib.", "I have to tell you about Nib.")
                }
            EventType.TRIP -> s.lastTrip?.takeIf { it.tripId.toString() == ev.payload }?.story?.firstOrNull()?.takeIf { it.length <= 90 }
                ?: p("I went somewhere today. I have a story.", "I went out. It was an adventure. A small one.")
            EventType.COOKED -> p("I cooked. Something. It's fine.", "The kitchen is fine. Mostly.", "I made food. Don't check the pan.")
        }
    }
}
