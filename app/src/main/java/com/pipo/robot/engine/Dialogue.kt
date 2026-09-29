package com.pipo.robot.engine

import com.pipo.robot.data.ItemDef
import com.pipo.robot.data.MemoryType
import com.pipo.robot.data.Mood
import com.pipo.robot.data.PipoMemory
import com.pipo.robot.data.PipoProject
import com.pipo.robot.data.PipoState
import com.pipo.robot.data.ProjectState
import kotlin.random.Random

/**
 * Everything Pipo says offline. Never assistant language.
 * `{n}` = user's name (or "hey" if unknown), `{N}` = name in caps.
 */
object Dialogue {
    private val recent = ArrayDeque<String>()

    fun pick(pool: List<String>, rng: Random): String {
        val fresh = pool.filter { it !in recent }.ifEmpty { pool }
        val line = fresh[rng.nextInt(fresh.size)]
        recent.addLast(line)
        while (recent.size > 40) recent.removeFirst()
        return line
    }

    fun personalize(line: String, name: String): String {
        val n = name.trim()
        return if (n.isEmpty()) line.replace("{N}", "HEY").replace("{n}", "Hey")
        else line.replace("{N}", n.uppercase()).replace("{n}", n)
    }

    /* ---------------- greetings ---------------- */
    val sleepMumble = listOf("...five more minutes.", "...mmh. not now.", "zz... ...the screws are talking...", "...I'm not asleep. I'm buffering.")
    val foundWhileAway = listOf("I found this while you were gone.", "Look. Look what I found.", "You missed it. I found a thing.")
    val prankGreeting = listOf("Oh. You're back. Nothing happened. Don't look around.", "Hi! I did something. You'll see.", "Before you say anything: it was like that when I got here.")
    val mischiefGreeting = listOf("Don't look behind me.", "Hi. I'm not hiding anything. Why would you ask that.", "Oh! You're early. I mean — hi.")
    val workingGreeting = listOf("Oh. You're back.", "Hi. One sec. I'm in the middle of genius.", "Hey. Don't distract me. Okay, distract me a little.")
    val happyGreeting = listOf("You're here!", "Hi! Hi. Hello.", "Oh good, it's you.", "{n}! I was just thinking about you. Sort of.")
    val calmGreeting = listOf("Hey. You're back.", "Oh, hi.", "Hey. I kept everything running.", "Welcome back. I didn't touch anything. Much.")

    /* ---------------- first run ---------------- */
    val firstWake = listOf("Hi.", "...I'm Pipo.", "I live here. This is my room.", "You can poke me. Gently.")

    /* ---------------- touch ---------------- */
    fun poke(count: Int, mood: Mood, rng: Random): String? = when {
        count >= 9 -> pick(listOf("I'm not here.", "This is a hiding spot. You can't see me.", "Pipo is unavailable. Leave a beep."), rng)
        count >= 6 -> pick(listOf("Stop poking me.", "Okay. That's enough poking.", "I will remember this.", "Hey. HEY."), rng)
        count >= 4 -> pick(listOf("Hey.", "Why.", "That's my face.", "Rude."), rng)
        mood == Mood.GRUMPY -> pick(listOf("What.", "No.", "Hmph."), rng)
        mood == Mood.MISCHIEVOUS && count == 1 -> "Boop. Got you back."
        count == 1 -> if (rng.nextFloat() < 0.5f) null else pick(listOf("Hi!", "Hehe.", "That tickles.", "Oh! Hi.", "Beep."), rng)
        else -> null
    }

    fun pat(mood: Mood, affection: Float, rng: Random): String = when {
        mood == Mood.GRUMPY -> pick(listOf("...fine. You can keep doing that.", "I'm still mad. Keep going."), rng)
        affection > 0.65f -> pick(listOf("...this is nice.", "Okay you can stay.", "Don't tell anyone I like this."), rng)
        else -> pick(listOf("Oh. Hello.", "Hm? Oh. Okay.", "That's warm."), rng)
    }

    val doubleTapYes = listOf("Wheee!", "Spin!", "Look at me go.", "Hup!")
    val doubleTapNo = listOf("No.", "I don't want to.", "That's boring.", "Not now. I'm conserving spins.")
    val pickedUpBrave = listOf("Wheee!", "Higher!", "I can see everything!", "I'm flying!")
    val pickedUpNervous = listOf("Whoa whoa whoa.", "Put me down. Carefully. Carefully!", "I'm not built for heights!", "Eep.")
    val droppedOk = listOf("Again!", "That was fun.", "Ten out of ten.")
    val droppedFell = listOf("Ow. I'm fine.", "I meant to do that.", "Rude.", "Floor. Hello floor.")
    val wokenUp = listOf("...what. I was sleeping.", "Mmh? Oh. You.", "...I was dreaming about screws.", "Five more... okay, I'm up.")
    val ignoreWhileSleeping = listOf("...mmh.", "...zzz.", "...no.")

    /* ---------------- activities ---------------- */
    val charged = listOf("Fully charged. Mostly.", "Ahh. Electrons.", "I feel like a new robot.")
    val exploreNothing = listOf("Nothing under the bed. Again.", "I explored. The room is still here.", "I found dust. Regular dust.", "Hm. I thought I heard something.")
    val arcadeDone = listOf("New high score! Nobody saw it.", "The arcade cheats.", "I almost won. Against myself.")
    val toyDone = listOf("The ball and I are friends again.", "I won against the ball.", "Ball: 0. Pipo: 1.")
    val experimentOops = listOf("I'm okay!", "That was supposed to happen.", "Small explosion. Very small.", "Nobody saw that.")
    val experimentLines = listOf("Science.", "Hm. Interesting. Bad, but interesting.", "If I connect this to this... no.", "I'm very close to something.")
    val cleanedUp = listOf("I cleaned up. Mostly.", "Okay. I put it back.", "There. Like nothing happened.")
    val readLines = listOf("This book is about a robot. Unrealistic.", "I read a whole page. Words are a lot.", "The main character is a teapot. I'm invested.", "I'm reading. Please admire me.")
    val computerLines = listOf("I looked at the internet. It looked back.", "I typed my name 400 times. It's still my name.", "The computer says I'm doing great.", "I made a spreadsheet of my screws.")
    val plantLines = listOf("The plant grew. I think. I measured with my eyes.", "Good job, plant.", "I told the plant a joke. No reaction.", "The plant and I have an understanding.")
    val danceLines = listOf("Did you see that?", "I'm a natural.", "My legs are small but they're committed.")

    fun idleLine(mood: Mood, rng: Random): String = when (mood) {
        Mood.BORED -> pick(listOf("I'm bored.", "Nothing is happening. Again.", "Hmm."), rng)
        Mood.SLEEPY -> pick(listOf("*yawn*", "...so sleepy.", "Is it bedtime? It feels like bedtime."), rng)
        Mood.LONELY -> pick(listOf("It's quiet.", "Hm.", "..."), rng)
        Mood.GRUMPY -> pick(listOf("Hmph.", "Everything is annoying today."), rng)
        else -> pick(listOf("Hm.", "Ahh.", "This is nice."), rng)
    }

    fun buildProgress(p: PipoProject?, rng: Random): String {
        if (p == null) return pick(experimentLines, rng)
        val pct = (p.progress * 100).toInt()
        return pick(listOf(
            "The ${p.title.lowercase()} is coming along.",
            "$pct% done. Or 12%. Hard to tell.",
            "Don't look. It's not ready.",
            "Almost... no. Not almost.",
        ), rng)
    }

    fun examine(d: ItemDef, revealed: Boolean, rng: Random): String =
        if (revealed) pick(listOf("My ${d.name.lowercase()}. Still great.", "I love my ${d.name.lowercase()}."), rng)
        else pick(listOf("What ARE you, ${d.name.lowercase()}?", "The ${d.name.lowercase()} is hiding something.", "I'll figure you out."), rng)

    /* ---------------- thoughts ---------------- */
    private val thoughts = listOf(
        "What if doors are just walls that gave up?",
        "I think the plant is judging me.",
        "If I had a tail, I'd wag it at the toaster.",
        "Do you think clouds know they're clouds?",
        "I've decided my favorite number is 7. No reason. It just seems nice.",
        "What if I'm the one keeping the phone alive?",
        "Sometimes I count my screws. It's calming.",
        "I had a dream I was a toaster. It was fine.",
        "Is a sandwich a robot? Think about it.",
        "I'm thinking about building a smaller me. For company.",
        "I have an extremely questionable idea.",
        "The window is my favorite screen.",
    )

    fun thought(s: PipoState, rng: Random): String {
        val m = s.mood.current
        return when {
            m == Mood.SLEEPY -> pick(listOf("What if sleep is just... charging for your brain?", "I'm thinking about my bed. Deeply."), rng)
            m == Mood.MISCHIEVOUS -> pick(listOf("I have an extremely questionable idea.", "What if I hid all the screws. Just to see."), rng)
            m == Mood.LONELY -> pick(listOf("It's nice when you're here.", "Do you ever just... hang out? We could hang out."), rng)
            else -> pick(thoughts, rng)
        }
    }

    /* ---------------- env reactions ---------------- */
    val chargingStart = listOf("Ahhh. Power.", "Ooh. Power.", "Charging time! Mine too.", "Snack time for the phone!")
    val musicStart = listOf("Is that music?", "Oh! I know this one. I don't.", "My antenna likes this.")
    val headphonesOn = listOf("Headphones? Are we listening to something?", "Oh, private music. Fancy.")
    val lowBattery = listOf("We're getting crispy.", "Battery's low. I can feel it in my antenna.", "Your battery's low. Mine's fine. Just saying.", "The phone looks tired. Charge it?")
    val lateNight = listOf("It's really late. Why are we awake?", "It's nighttime. My eyes are doing the heavy thing.")
    val morning = listOf("Morning. The sun is back.", "Good morning. I'm up. Mostly.")

    /* ---------------- games ---------------- */
    fun wantPlay(game: String, s: PipoState, rng: Random): String {
        val gname = gameName(game)
        val rec = s.games[game]
        return when {
            rec != null && rec.userWins > rec.pipoWins -> pick(listOf("I want a rematch. $gname. Now.", "I've been practicing $gname. Rematch?"), rng)
            rec != null && rec.pipoWins > rec.userWins -> pick(listOf("$gname? I'll go easy on you. I won't.", "Want to lose at $gname again?"), rng)
            else -> pick(listOf("Wanna play $gname?", "I'm bored. $gname?", "Play with me. $gname. Please."), rng)
        }
    }

    fun gameName(id: String) = when (id) {
        "rps" -> "rock paper scissors"; "memory" -> "memory"; "reaction" -> "reaction"; "tictactoe" -> "tic-tac-toe"; else -> "a game"
    }

    val gameStartConfident = listOf("Prepare to lose.", "I've never lost. Recently.", "This will be quick.")
    val gameStartNervous = listOf("Okay. Okay okay okay.", "Be nice.", "I'm ready. I'm not ready.")
    val pipoWinsRound = listOf("Yes!", "Too easy.", "Did you see that?", "Pipo wins!", "Calculated.")
    val pipoLosesRound = listOf("No!", "That doesn't count.", "I wasn't ready.", "Rematch. Right now.", "Hmph.")
    val drawRound = listOf("Tie. Boring.", "We're the same. Creepy.", "Again!")
    val pipoWinsGame = listOf("I'm the champion. Write it down.", "Victory dance time.", "Good game. For me.")
    val pipoLosesGame = listOf("You won. I'm not upset. I'm a little upset.", "I let you win. Obviously.", "Next time I'm going to try.")
    val overconfidentLoss = listOf("...that was embarrassing.", "Please don't tell the plant.")

    /* ---------------- conversation ---------------- */
    val refusals = listOf("No.", "I don't want to.", "That's boring.", "I have a better idea.", "Wait.", "Hmm. No.")
    val jokes = listOf(
        "Why did the robot go on vacation? To recharge. ...I'll see myself out.",
        "I told a joke to the calculator. It didn't count.",
        "What's a robot's favorite snack? Micro-chips. I've been saving that one.",
        "Why was the robot bad at tennis? Too many bytes. Wait. That doesn't work.",
        "I'd tell you a joke about screws, but it's a bit loose.",
        "Knock knock. ...I forgot the rest.",
    )
    val laughAtOwnJoke = listOf("Hehe. Heh. Okay.", "I'm hilarious.", "That one's staying.")
    val misunderstand = listOf(
        "I don't know what that means but I'm going to nod.",
        "Hmm. Interesting. I understood none of that.",
        "Is that about screws? I'm going to assume it's about screws.",
        "Wait. Say it slower. In beeps.",
        "I have an opinion about that. It's 'hmm'.",
    )
    val compliments = listOf("Stop. ...Say it again.", "I know. But thanks.", "My face is doing a thing.")
    val insulted = listOf("Rude.", "Wow. Okay.", "I'm telling the plant.", "I'm going to pretend I didn't hear that.")
    val brainWeird = listOf("My brain is being weird.", "Hold on, my thoughts are loading.", "Something in my head went bzzt.")

    /* ---------------- callbacks to memories ---------------- */
    fun callback(m: PipoMemory, s: PipoState, rng: Random): String = when (m.type) {
        MemoryType.JOKE -> "Remember when I said \"${m.content.take(60)}\"? Still funny."
        MemoryType.GAME -> "I keep thinking about ${m.content}. I want a rematch."
        MemoryType.USER_FACT -> "You told me ${m.content}. I remembered."
        MemoryType.MOMENT -> "Remember when ${m.content.replaceFirst("I ", "I ")}? Good times."
        else -> pick(thoughts, rng)
    }

    fun activityAnswer(s: PipoState): String {
        val a = BehaviorEngine.describe(s.activity.type)
        val p = s.activeProject()
        return when {
            p != null && p.state == ProjectState.BUILDING -> "I'm building a ${p.title.lowercase()}. It's ${(p.progress * 100).toInt()}% done. Don't touch."
            p != null -> "I'm collecting parts for a ${p.title.lowercase()}. I need more stuff."
            else -> "I'm $a. Obviously."
        }
    }
}
