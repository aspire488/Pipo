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
