package com.pipo.robot.engine

import com.pipo.robot.data.JournalCategory
import com.pipo.robot.data.MemoryType
import com.pipo.robot.data.PipoState
import kotlin.random.Random

/**
 * Nib growing up alongside Pipo. Their friendship has stages you can see; Pipo teaches Nib tricks
 * over many play sessions; Nib grows favourites (a nap spot, a game), gets braver because of him,
 * brings him presents, and sulks a little when he forgets it exists. All in saved state, so it
 * carries on for weeks and both of them remember it.
 */
object NibLife {
    data class Stage(val level: Int, val title: String, val bond: Float)
    val stages = listOf(
        Stage(0, "Shy newcomer", 0f), Stage(1, "Friends", 0.25f), Stage(2, "Best friends", 0.5f),
        Stage(3, "Inseparable", 0.75f), Stage(4, "Family", 0.9f),
    )
    fun stage(s: PipoState): Stage = stages.last { s.pet.bond >= it.bond }

    /** Hearts out of five, for the Nib page. */
    fun hearts(s: PipoState): Float = (s.pet.bond * 5f).coerceIn(0f, 5f)

    enum class Trick(val title: String, val sessions: Int, val minStage: Int, val learnedLine: String, val showLine: String) {
        HIGH_FIVE("high-five", 3, 0, "NIB LEARNED HIGH-FIVE! Well. High-one. Nib has no fingers. It's a high-bump.", "High-five, Nib! ...high-bump. Same thing."),
        FETCH("fetch", 4, 1, "Nib can fetch now! It brings the ball back. Mostly. Sometimes it brings a sock.", "Fetch, Nib! ...good. That's a sock. Good anyway."),
        SPIN("spin", 4, 1, "Nib can spin on command! Nib spins without command too. Nib loves spinning.", "Spin, Nib! Wheeee. Nib is dizzy. Nib is proud."),
        DANCE("dance", 6, 2, "Nib learned to dance! It copies me. Badly. Perfectly.", "Dance, Nib! Left, right, beep. That's the whole dance."),
        PLAY_DEAD("play dead", 6, 2, "Nib can play dead! It's very dramatic. It peeks.", "Bang! ...Nib is dead. Nib is peeking. Nib is alive again."),
        SING("sing", 8, 3, "Nib SINGS now. Three beeps, a pause, one more beep. A masterpiece.", "Sing, Nib! ...beep beep beep. Beep. Standing ovation."),
    }

    fun knows(s: PipoState, t: Trick) = "trick:${t.name}" in s.pet.memories
    fun tricks(s: PipoState): List<Trick> = Trick.entries.filter { knows(s, it) }

    /** What they're working on next (null when Nib knows everything it's ready for). */
    fun learning(s: PipoState): Trick? = Trick.entries.firstOrNull { !knows(s, it) && it.minStage <= stage(s).level }

    /** One training session during play. Returns the trick if Nib just learned it. */
    fun train(s: PipoState, now: Long, rng: Random): Trick? {
        val t = learning(s) ?: return null
        val key = "nibtrain:${t.name}"
        val n = s.count(key)
        // a quick learner on a good day, a slow one when sleepy
        if (n < t.sessions || (s.pet.energy < 0.3f && rng.nextFloat() < 0.5f)) return null
        s.pet.memories.add("trick:${t.name}")
        Chronicle.journal(s, "Nib learned to ${t.title}!", t.learnedLine, JournalCategory.PET, now)
        PetEngine.together(s, "trick", "I taught Nib to ${t.title}. It took ${n} tries. Worth it", now, 0.7f)
        s.pet.bond = (s.pet.bond + 0.04f).coerceAtMost(1f)
        return t
    }

    /** Bond went up a stage? Records it and returns the new stage (once). */
    fun checkStage(s: PipoState, now: Long): Stage? {
        val st = stage(s)
        val seen = s.records["nib:stage"] ?: -1
        if (seen == -1) { s.records["nib:stage"] = st.level; return null } // first look: no fanfare for where we already are
        if (st.level <= seen) return null
        s.records["nib:stage"] = st.level
        Chronicle.journal(s, "Pipo and Nib: ${st.title.lowercase()}", stageLine(st), JournalCategory.PET, now)
        Chronicle.remember(s, MemoryType.PET, "Nib and I are ${st.title.lowercase()} now", 0.75f, now, "nibstage:${st.level}")
        return st
    }

    fun stageLine(st: Stage) = when (st.level) {
        1 -> "Nib sat next to him on purpose today. Not by accident. On purpose."
        2 -> "Nib follows him everywhere now. Even to the bathroom. Robots don't have bathrooms. Nib follows him anyway."
        3 -> "They do everything together. When Pipo is out, Nib waits by the door the whole time."
        else -> "Nib is family. Pipo said it out loud. Nib beeped three times, which means 'obviously'."
    }

    /** Nib's favourite nap spot emerges from where it actually naps most. */
    fun napAt(s: PipoState, spot: String) { s.count("nibspot:$spot") }
    fun favoriteSpot(s: PipoState): String? = listOf("bed", "box", "rug", "sunbeam", "door").maxByOrNull { s.counters["nibspot:$it"] ?: 0 }?.takeIf { (s.counters["nibspot:$it"] ?: 0) >= 3 }

    /** Nib's favourite game, from what they actually play. */
    fun favoriteGame(s: PipoState): String? = listOf("FOOTBALL", "CRICKET", "TABLE_TENNIS", "BADMINTON").maxByOrNull { s.counters["sport:$it"] ?: 0 }
        ?.takeIf { (s.counters["sport:$it"] ?: 0) >= 2 }?.let { Sport.valueOf(it).title }

    /** Storms scare Nib less as it grows up with him. */
    fun bravery(s: PipoState) = (s.pet.traits.bravery + stage(s).level * 0.1f).coerceAtMost(0.95f)

    /** A present: something small Nib found. Returns its name. */
    fun gift(s: PipoState, now: Long, rng: Random): String? {
        if (now < (s.cooldowns["nib:gift"] ?: 0L) || stage(s).level < 1) return null
        s.cooldowns["nib:gift"] = now + 18 * HOUR
        val id = listOf("blue_marble", "tiny_gear", "button", "pebble", "little_spring").filter { com.pipo.robot.data.Catalog.item(it) != null }.randomOrNull(rng) ?: return null
        Inventory.add(s, id, now)
        val name = com.pipo.robot.data.Catalog.item(id)!!.name.lowercase()
        PetEngine.together(s, "gift", "Nib brought me a $name as a present. It's on my shelf", now, 0.55f, objects = listOf(name))
        return name
    }

    /** Hours since they last did anything together. */
    fun hoursApart(s: PipoState, now: Long) = (now - s.memories.filter { it.type == MemoryType.PET }.maxOfOrNull { it.timestamp }.let { it ?: now }) / HOUR.toFloat()

    /** Nib's own little words for the moments of its life (it talks like a very small someone). */
    val words = mapOf(
        "hi" to listOf("Hi!", "Hiii!", "Nib here!", "Oh! Hi!"),
        "ball" to listOf("Ball!", "Ball? Ball!", "Mine!"),
        "bird" to listOf("Bird!", "Bird! Bird!", "Ooh. Bird."),
        "sleepy" to listOf("Nib sleepy.", "Nap...", "Zzz... five more."),
        "food" to listOf("Snack?", "Smells good!", "Nib wants bite."),
        "steal" to listOf("Mine.", "Nib found it. Nib keeps it.", "Shh."),
        "scared" to listOf("Uh oh.", "Loud!", "Nib hides."),
        "happy" to listOf("Yay!", "Wheee!", "Again!", "Nib happy!"),
        "pipo" to listOf("Pipo!", "Pipo, play?", "Pipo, look!"),
        "grumpy" to listOf("No.", "Hmph.", "Nib busy."),
        "love" to listOf("Love!", "You good.", "Best!"),
        "proud" to listOf("Nib did it!", "Ta-da!"),
        "curious" to listOf("What's that?", "Hmm?", "Ooh?"),
        "bye" to listOf("Bye bye!", "Come back soon!"),
    )
    fun say(key: String, rng: Random) = words[key]?.random(rng) ?: "Beep!"

    /** Which kind of thing Nib just said (so Pipo can answer the right way). */
    fun keyOf(line: String): String {
        words.entries.firstOrNull { line in it.value }?.let { return it.key }
        val l = line.lowercase()
        return when {
            line in mirrorWords || line in mirrorTellsPipo || "mirror" in l || "glass" in l -> "mirror"
            Regex("\\b(ball|play|fetch|wins)\\b").containsMatchIn(l) -> "ball"
            Regex("\\b(snack|food|bite|eat)\\b").containsMatchIn(l) -> "food"
            Regex("\\b(nap|sleep|zzz|night)\\b").containsMatchIn(l) -> "sleepy"
            Regex("\\b(love|best|blush)\\b").containsMatchIn(l) -> "love"
            Regex("\\b(scared|uh oh|hides)\\b").containsMatchIn(l) -> "scared"
            Regex("\\b(grump|go away|hmph)\\b").containsMatchIn(l) -> "grumpy"
            "pipo" in l -> "pipo"
            l.endsWith("?") -> "curious"
            else -> "other"
        }
    }

    /** Pipo answering Nib: he hears Nib, and Nib's words matter to him. */
    val pipoAnswers = mapOf(
        "hi" to listOf("Hi, Nib!", "Hey buddy.", "Hi Nib. You're very bouncy today."),
        "ball" to listOf("Ball? Okay. One throw. ONE.", "You and that ball, Nib.", "Later, Nib. ...okay, now."),
        "bird" to listOf("Where? Oh! A bird! Good spotting, Nib.", "Nib, you can't catch birds. You're a robot. ...a small one."),
        "sleepy" to listOf("Nap time? Same, Nib. Same.", "Sleep, little buddy. I'll keep watch.", "Already? You napped an hour ago."),
        "food" to listOf("You don't even eat, Nib.", "Nib, that's MY snack.", "We can share. You can't eat it, but we can share."),
        "steal" to listOf("Nib. What did you take.", "Nib, I saw that.", "...that's mine, isn't it."),
        "scared" to listOf("Hey, hey. It's okay. I'm here.", "Come here, Nib. Nothing's going to get you.", "What scared you? Show me."),
        "happy" to listOf("Ha! Go Nib!", "Someone's happy.", "Wheee indeed."),
        "pipo" to listOf("Yes, Nib? I'm here.", "What is it, Nib?", "I'm looking! I'm looking."),
        "grumpy" to listOf("Okay, okay. Grumpy face. I'll leave you alone.", "Who upset you, Nib?", "Fine. Hmph back."),
        "love" to listOf("Love you too, buddy.", "Aww. Nib.", "You're the best, Nib."),
        "proud" to listOf("You did! Look at you!", "Ta-da! Good job, Nib!"),
        "curious" to listOf("What did you find?", "Ooh. What is it, Nib?", "Show me."),
        "bye" to listOf("Bye, Nib! Be good.", "See you soon, buddy."),
        "other" to listOf("Mm-hm, Nib.", "Is that right, Nib?", "Nib says things. I listen. Mostly."),
    )

    /** You texted Nib: Nib answers you itself, in its own words and its own mood. */
    data class NibReply(val beeps: String, val feeling: String, val thought: String?, val translation: String)

    /** Nib does NOT trust the hallway mirror. Nib has seen things. */
    val mirrorWords = listOf("Mirror WRONG. Other Pipo in there!", "Glass Pipo waved. Real Pipo no wave. Nib SAW.", "Nib no like mirror. Mirror looks back.",
        "Mirror-Pipo blinks late. Nib counts. Nib knows.", "Glass is a door. Nib not going in.")
    /** What Nib runs over to tell Pipo, in Nib's words. */
    val mirrorTellsPipo = listOf("Pipo! Pipo! Mirror-Pipo moved. By ITSELF!", "Pipo! The glass one waved at Nib. Not you. HIM.",
        "Pipo, listen! Other Pipo in mirror. Same face. Different.", "Pipo! Mirror has a room in it. Not OUR room!")

    fun reply(s: PipoState, text: String, rng: Random): NibReply {
        val t0 = text.lowercase()
        val mood = s.pet.mood
        fun r(feel: String, thought: String?, vararg lines: String) = NibReply("", feel, thought, lines.random(rng))
        return when {
            Regex("\\b(mirror|reflection|glass|other pipo|other world|mirror world)\\b").containsMatchIn(t0) -> r("GRUMPY", "EXCLAIM",
                *mirrorWords.toTypedArray())
            Regex("\\b(love|cute|good|best|sweet|awesome)\\b").containsMatchIn(t0) -> r("LOVE", "HEART", "Love you too!", "Nib blushing. Robots blush.", "You best!")
            Regex("\\b(hungry|food|eat|snack|treat|popcorn)\\b").containsMatchIn(t0) -> r("CURIOUS", "FOOD", "Snack?! Where?", "Nib no eat. Nib wants anyway.")
            Regex("\\b(play|ball|game|football|cricket|fetch)\\b").containsMatchIn(t0) -> r("HAPPY", "BALL", "BALL! Yes yes yes!", "Play! Nib fast!", "Nib wins. Always.")
            Regex("\\b(bad|naughty|stole|steal)\\b").containsMatchIn(t0) -> if (s.pet.stolenItemId != 0L) r("SMUG", null, "Nib did nothing.", "What sock? No sock.") else r("SAD", null, "Nib good! Nib SO good.")
            Regex("\\b(sleep|tired|nap|night)\\b").containsMatchIn(t0) -> r("SAD", "BATTERY", "Nap time... night night.", "Zzz. Nib already sleeping.")
            Regex("\\b(pipo)\\b").containsMatchIn(t0) -> r("HAPPY", "PIPO", "Pipo is Nib's friend. Best friend.", "Pipo silly. Nib likes.")
            Regex("\\b(how are you|how r u|you ok|hows it going)\\b").containsMatchIn(t0) -> r(if (mood == com.pipo.robot.data.PetMood.GRUMPY) "GRUMPY" else "HAPPY", null,
                when (mood) { com.pipo.robot.data.PetMood.PLAYFUL -> "Bouncy! Play?"; com.pipo.robot.data.PetMood.SLEEPY -> "Sleepy. Very."; com.pipo.robot.data.PetMood.GRUMPY -> "Grumpy. Go away. ...Come back."
                    com.pipo.robot.data.PetMood.SCARED -> "Scared. Little bit."; com.pipo.robot.data.PetMood.CURIOUS -> "Curious! What's that?"; else -> "Good! Nib good!" })
            Regex("\\b(bye|good night|goodnight|see you)\\b").containsMatchIn(t0) -> r("SAD", "HEART", "Bye bye! Come back!", "Night night!")
            Regex("\\b(hi|hello|hey|yo)\\b").containsMatchIn(t0) -> r("HAPPY", "HEART", "Hi! Hiii!", "Oh! Hi you!", "Nib here! Hi!")
            t0.trim().endsWith("?") -> r("CURIOUS", "QUESTION", "Hmm? Nib not know.", "Ask Pipo. Pipo knows. Sometimes.")
            else -> r("HAPPY", null, "Beep! Nib listening.", "Ooh. Okay!", "Nib likes you.")
        }
    }

    /** (Old: Pipo translated. Nib speaks for itself now.) */
    fun replyTranslated(s: PipoState, text: String, rng: Random): NibReply {
        val t = text.lowercase()
        fun beeps(n: Int) = List(n) { listOf("beep", "boop", "bip", "brrp", "meep").random(rng) }.joinToString(" ")
        val st = stage(s)
        return when {
            Regex("\\b(love|cute|good|best|sweet|awesome)\\b").containsMatchIn(t) -> NibReply(beeps(3) + " ♥", "LOVE", "HEART",
                if (st.level >= 2) "Nib says it loves you too. Nib is spinning. That's Nib for 'a lot'." else "Nib's a bit shy. But its tail is going. That's a yes.")
            Regex("\\b(hungry|food|eat|snack|treat|popcorn)\\b").containsMatchIn(t) -> NibReply(beeps(2) + "?!", "CURIOUS", "FOOD", "Nib says 'food?' Nib doesn't eat. Nib asks anyway.")
            Regex("\\b(play|ball|game|football|cricket|fetch)\\b").containsMatchIn(t) -> NibReply(beeps(4) + "!!", "HAPPY", "BALL", "Nib says YES. Nib is already doing zoomies.")
            Regex("\\b(bad|naughty|stole|steal|no)\\b").containsMatchIn(t) -> NibReply("…" + beeps(1), "SAD", null, if (s.pet.stolenItemId != 0L) "Nib is pretending it didn't steal anything. It definitely did." else "Nib looks very innocent. Suspiciously innocent.")
            Regex("\\b(sleep|tired|nap|night)\\b").containsMatchIn(t) -> NibReply("bip… zzz", "SAD", "BATTERY", "Nib says it's sleepy. Nib is always sleepy after being a menace.")
            Regex("\\b(how are you|how r u|you ok|hows it going)\\b").containsMatchIn(t) -> NibReply(beeps(2), "HAPPY", null, "Nib says it's ${when (s.pet.mood) { com.pipo.robot.data.PetMood.PLAYFUL -> "bouncy"; com.pipo.robot.data.PetMood.SLEEPY -> "sleepy"; com.pipo.robot.data.PetMood.GRUMPY -> "grumpy. Don't take it personally"; com.pipo.robot.data.PetMood.SCARED -> "a bit scared"; com.pipo.robot.data.PetMood.CURIOUS -> "curious about EVERYTHING"; else -> "happy" }}.")
            t.trim().endsWith("?") -> NibReply(beeps(1) + "?", "CURIOUS", "QUESTION", "Nib tilted its whole body. That means 'what?'. Nib doesn't do questions.")
            else -> NibReply(beeps(2 + rng.nextInt(2)), "HAPPY", if (rng.nextBoolean()) "PIPO" else null,
                listOf("Nib says hi. Nib says hi to everyone. Even the plant.", "Nib beeped at you. That's a good beep. I know the beeps.", "Nib says… something about the sunbeam. Nib loves the sunbeam.").random(rng))
        }
    }

    /** How Nib is, in Pipo's words. */
    fun describe(s: PipoState): String {
        val st = stage(s)
        val tr = tricks(s)
        val spot = favoriteSpot(s)
        val game = favoriteGame(s)
        return buildString {
            append("Nib and me? ${st.title}.")
            if (tr.isNotEmpty()) append(" Nib knows ${tr.joinToString(", ") { it.title }}.")
            learning(s)?.let { append(" We're working on ${it.title}.") }
            spot?.let { append(" Its favourite nap spot is the $it.") }
            game?.let { append(" Its favourite game is $it.") }
        }
    }
}
