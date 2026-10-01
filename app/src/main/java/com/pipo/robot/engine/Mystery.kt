package com.pipo.robot.engine

import com.pipo.robot.data.DrawSubject
import com.pipo.robot.data.Drawing
import com.pipo.robot.data.EventType
import com.pipo.robot.data.JournalCategory
import com.pipo.robot.data.MemoryType
import com.pipo.robot.data.Mood
import com.pipo.robot.data.Photo
import com.pipo.robot.data.PhotoSubject
import com.pipo.robot.data.PipoState
import com.pipo.robot.data.Place
import com.pipo.robot.data.PlaceMemory
import com.pipo.robot.data.TripPurpose
import com.pipo.robot.data.TripState
import kotlin.random.Random

/**
 * "There is more world behind the world."
 *
 * Not a quest: no markers, no popups. A slow thread that only moves when ordinary life gives it
 * a chance — a night at the window, a dream he draws, a shop he happens to visit, a photo he looks
 * at again, a walk to the hills. Each step needs days to pass since the last one, so most of his
 * life stays ordinary. The user finds out the way Pipo does: by noticing.
 */
object Mystery {
    const val FIRST_AFTER_DAYS = 7
    const val GAP = 8 * DAY

    fun ready(s: PipoState, now: Long): Boolean {
        val age = now - s.profile.createdAt
        if (age < FIRST_AFTER_DAYS * DAY) return false
        return now - s.mystery.lastClueAt >= GAP
    }

    private fun advance(s: PipoState, stage: Int, clue: String, now: Long) {
        // strange things make him more curious. (Carefully.)
        Personality.nudge(s, Trait.CURIOSITY, 0.012f)
        s.mystery.stage = stage
        s.mystery.clues.add(clue)
        s.mystery.lastClueAt = now
    }

    private fun strange(s: PipoState, title: String, text: String, memory: String, now: Long, importance: Float = 0.55f) {
        Chronicle.journal(s, title, text, JournalCategory.STRANGE, now)
        Chronicle.remember(s, MemoryType.STRANGE, memory, 0.85f, now, "strange:${s.mystery.stage}")
        Chronicle.event(s, EventType.STRANGE, importance, now, s.mystery.stage.toString())
    }

    /* ---------------- 0 → 1: a signal at night ---------------- */

    /** He's at the window (or awake) at night. Very occasionally, the hills blink back. */
    fun onNightWindow(s: PipoState, env: Env, now: Long, rng: Random, chance: Float): String? {
        val lateEnough = isNight(env.hour) || env.hour in 21..23
        if (s.mystery.stage != 0 || !lateEnough || !ready(s, now)) return null
        if (rng.nextFloat() > chance) return null
        advance(s, 1, "signal", now)
        val nib = if (s.pet.adopted) " Nib saw it too. Nib growled. Nib never growls." else ""
        strange(s, "Something on the hills", "Late. Something on the hills blinked. Three times. Then nothing.$nib", "something blinked on the hills three times", now)
        MoodEngine.setTransient(s, Mood.THOUGHTFUL, now, 60_000)
        return "Something blinked. On the hills. Three times."
    }

    /* ---------------- the dream (still stage 1) ---------------- */

    fun wantsToDrawDream(s: PipoState) = s.mystery.stage >= 1 && s.mystery.dreamDrawingId == 0L

    fun dreamDrawing(s: PipoState, now: Long, rng: Random): Drawing? {
        if (!wantsToDrawDream(s)) return null
        val d = Drawing(s.nextId(), DrawSubject.BUILDING, "dream", "I dreamed this. A round building with a hole in the roof. There was a mark on the door.", now, seed = rng.nextInt(1 shl 20))
        s.drawings.add(d)
        s.mystery.dreamDrawingId = d.id
        Chronicle.journal(s, "A dream, drawn", d.caption, JournalCategory.STRANGE, now)
        Chronicle.remember(s, MemoryType.STRANGE, "I dreamed about a round building with a mark on the door", 0.8f, now, "dream:building")
        return d
    }

    /* ---------------- trips: 1 → 2 (the mark), 3 → 4 (the building), 4 → 5 (the other side) ---------------- */

    fun tripIdea(s: PipoState, env: Env, now: Long): TripIdea? {
        if (!ready(s, now)) return null
        val t = s.profile.traits
        return when {
            s.mystery.stage == 1 && s.mystery.dreamDrawingId != 0L ->
                TripIdea("secondhand", TripPurpose.EXPLORE, emptyList(), "Old Rook has every weird thing. Maybe the mark from my dream is there.", 0.35f + t.curiosity * 0.4f)
            s.mystery.stage == 3 && env.hour in 8..16 ->
                TripIdea("hills", TripPurpose.EXPLORE, emptyList(), "The door in that photo was on the hills. I'm going to look.", 0.4f + t.curiosity * 0.3f + t.adventurousness * 0.3f)
            s.mystery.stage == 4 && Inventory.owns(s, "brass_token") && env.hour in 9..16 ->
                TripIdea("observatory", TripPurpose.INVESTIGATE, emptyList(), "The dome. I have the token. I think the token is a key.", 0.5f + t.adventurousness * 0.5f + t.curiosity * 0.3f)
            s.mystery.stage >= 5 && env.hour in 10..16 && now - (s.cooldowns["otherside"] ?: 0L) > 9 * DAY ->
                TripIdea("otherside", TripPurpose.INVESTIGATE, emptyList(), "I'm going back. Through the dome. Just to look.", 0.15f + t.adventurousness * 0.35f)
            else -> null
        }
    }

    fun onTrip(s: PipoState, place: Place, trip: TripState, now: Long, rng: Random, env: Env = Env(hour = hourOf(now))): String? {
        val m = s.mystery
        // things on the other side happen whenever he's there — that's the point of going
        if (place.id == "otherside") return otherSide(s, now, rng, trip.withPet)
        if (!ready(s, now)) return null
        return when {
            // another way to the mark: Nib digs it up (outdoors, with the dream already drawn)
            m.stage == 1 && m.dreamDrawingId != 0L && trip.withPet && place.outdoor && rng.nextFloat() < 0.25f -> {
                val token = Inventory.add(s, "brass_token", now)
                m.tokenItemId = token.id
                advance(s, 2, "mark", now)
                PetEngine.together(s, "strange", "Nib dug up a brass token with the mark from my dream on it", now, 0.8f, place = place.id, objects = listOf("brass_token"))
                strange(s, "What Nib dug up", "At ${Trips.placePhrase(place)} Nib started digging and wouldn't stop. A brass token. It has the mark from his dream on it. Nib found it first.",
                    "Nib dug up a token with the mark from my dream", now, 0.62f)
                "Nib dug up a brass token. It has the mark from his dream on it."
            }
            m.stage == 1 && m.dreamDrawingId != 0L && place.id in setOf("secondhand", "hardware") && rng.nextFloat() < 0.4f -> {
                val token = Inventory.add(s, "brass_token", now)
                m.tokenItemId = token.id
                advance(s, 2, "mark", now)
                val who = if (place.id == "secondhand") "Old Rook" else "Grumble"
                strange(s, "The mark", "$who put a brass token on the counter. \"This one's been waiting for you.\" It has the mark from his dream on it. Exactly.",
                    "the token has the same mark as my dream", now, 0.62f)
                "$who gave him a brass token. It has the mark from his dream on it."
            }
            m.stage == 3 && place.id == "hills" -> {
                s.places.getOrPut("observatory") { PlaceMemory(note = "the building from my dream") }.apply { if (firstVisit == 0L) { firstVisit = now; lastVisit = now; visits = 1 } }
                advance(s, 4, "building", now)
                PhotoLife.take(s, PhotoSubject.PLACE, "observatory", "observatory", env, now, rng)
                val nib = if (trip.withPet) " Nib wouldn't go near it." else ""
                strange(s, "The building from the drawing", "Behind the hills there's a round building with a hole in the roof. It's the one he drew. Exactly the one.$nib",
                    "the building from my dream is real", now, 0.7f)
                "Behind the hills there's a round building. It's the one from my drawing."
            }
            m.stage == 4 && place.id == "observatory" && Inventory.owns(s, "brass_token") -> {
                s.places.getOrPut("otherside") { PlaceMemory(note = "???") }.apply { firstVisit = now; lastVisit = now; visits = 1 }
                advance(s, 5, "otherside", now)
                s.cooldowns["otherside"] = now
                val nib = if (trip.withPet) " Nib went first. Of course." else ""
                strange(s, "The other side", "The token fit a slot in the dome's door. Inside was our street. The same street. But the bakery was blue and the lamps were the wrong way round.$nib",
                    "there's another street behind the dome", now, 0.75f)
                "The token fit the door. Behind it was our street. But wrong."
            }
            else -> null
        }
    }

    /** What's different over there, noticed one visit at a time (never the same twice in a row). */
    private val wrongness = listOf(
        "The bakery is blue there. The bread is also blue. It tastes normal.",
        "The street lamps hang upside down and light the sky instead of the road.",
        "The rain there falls sideways. Politely.",
        "Grumble's shop sells only left-handed screws. Grumble is cheerful.",
        "The frogs sing in chords. Actual chords.",
        "The signs are the right way round, but the words are in a slightly wrong order.",
        "There's no football field. There's a very large boat.",
    )

    /**
     * The other side: familiar, strange, sometimes funny, never evil. A note, a detail that's wrong,
     * and once — only once, after a few visits — the other Pipo himself.
     */
    private fun otherSide(s: PipoState, now: Long, rng: Random, withNib: Boolean): String {
        val m = s.mystery
        s.cooldowns["otherside"] = now
        m.notesFromOtherSide++
        val n = m.notesFromOtherSide
        val detail = wrongness[(n - 1) % wrongness.size]
        if (n >= 3 && !s.memories.any { it.key == "otherpipo:met" }) return meetOtherPipo(s, now, withNib, detail)
        val hat = "prank:plant_hat" in s.world.objectStates.keys
        val notes = listOf(
            if (hat) "Your plant has a hat. Ours doesn't. — P." else "Our plant has a hat. Yours looks cold. — P.",
            "We don't have a Nib here. I have a moth. His name is Lamp. — P.",
            "I've never eaten noodles. Are they good? — P.",
            "I built a boat. It floats. Mostly. Did you ever build a boat? — P.",
            "I'm not scared of heights. I'm scared of balls. Don't laugh. — P.",
            "The token is warm on this side. Is it warm on yours? — P.",
        )
        val note = Festivals.today(now)?.let { "${Festivals.mirrorLine(it)} — P." } ?: notes[(n - 1) % notes.size]
        Chronicle.journal(s, "A note that isn't mine", "In my handwriting. I didn't write it. \"$note\" Also: $detail", JournalCategory.STRANGE, now)
        Chronicle.remember(s, MemoryType.STRANGE, "there's another me. He writes notes", 0.9f, now, "otherpipo")
        Chronicle.event(s, EventType.STRANGE, 0.5f, now, "note")
        if (withNib) s.pet.memories.add("otherside")
        return "Found a note in my own handwriting: \"$note\""
    }

    /**
     * The other Pipo. Different because his life was different: shaped by the opposite of our
     * Pipo's strongest leanings, with a moth-bot called Lamp instead of Nib. Nib notices him first.
     * He gives Pipo one thing from his side: a screw that turns both ways.
     */
    private fun meetOtherPipo(s: PipoState, now: Long, withNib: Boolean, detail: String): String {
        val t = s.profile.traits
        val him = buildList {
            add(if (t.confidence > 0.5f) "He's shy. He talked to his shoes a lot." else "He's bold. He shook my hand before I could hide.")
            add(if (t.adventurousness > 0.5f) "He's never been past the end of his street." else "He's been everywhere. He has a map with no gaps.")
            add("He has a moth-bot called Lamp that circles his head.")
            if (s.profile.userName.isNotBlank()) add("He asked who ${s.profile.userName} is. He doesn't have a ${s.profile.userName}.")
            else add("He asked if I have a person. He doesn't.")
        }
        val nib = if (withNib) " Nib saw him first and went completely still. Then Lamp landed on Nib's head. Nib allowed it." else ""
        Inventory.add(s, "mirror_screw", now)
        Chronicle.journal(s, "He was there", "The other me. ${him.joinToString(" ")}$nib He gave me a screw that turns both ways. $detail", JournalCategory.STRANGE, now)
        Experience.remember(s, MemoryType.STRANGE, "I met the other me. ${him.first()}", 0.95f, now, "otherpipo:met", place = "otherside",
            with = listOfNotNull("the other Pipo", if (withNib) "Nib" else null), objects = listOf("mirror_screw"), feeling = 0.3f)
        if (withNib) PetEngine.together(s, "strange", "Nib met Lamp, the other me's moth-bot", now, 0.8f, place = "otherside")
        Chronicle.event(s, EventType.STRANGE, 0.7f, now, "met")
        return "He was there. The other me. He gave me a screw that turns both ways."
    }

    /** Another way to find the dome: the telescope, at night, pointed at the hills. */
    fun onTelescope(s: PipoState, env: Env, now: Long, rng: Random): String? {
        if (s.mystery.stage != 3 || !ready(s, now) || !(isNight(env.hour) || env.hour >= 21)) return null
        val mk2 = Inventor.has(s, "scope_mk2")
        if (!mk2 && !s.projects.any { it.templateId == "telescope" && (it.state == com.pipo.robot.data.ProjectState.DONE || it.state == com.pipo.robot.data.ProjectState.EVOLVED) }) return null
        if (rng.nextFloat() > (if (mk2) 0.65f else 0.3f)) return null
        s.places.getOrPut("observatory") { PlaceMemory(note = "seen through my telescope") }
        advance(s, 4, "building", now)
        strange(s, "Through the telescope", "He pointed his telescope at the hills where the lights blink. Behind them: a round building with a hole in the roof. The one from his drawing.",
            "I saw the building from my dream through my telescope", now, 0.7f)
        return "...there's a building behind the hills. Round. With a hole in the roof. I drew that."
    }

    /* ---------------- 2 → 3: a photo that's wrong ---------------- */

    /** Looking through his photos again. One of them has changed. */
    fun onLookAtPhotos(s: PipoState, now: Long, rng: Random): Photo? {
        val m = s.mystery
        if (m.stage != 2 || !ready(s, now)) return null
        val old = s.photos.filter { !it.anomaly && now - it.createdAt > DAY && it.subject != PhotoSubject.SELFIE }
        if (old.isEmpty() || rng.nextFloat() > 0.3f) return null
        val p = old.random(rng)
        p.anomaly = true
        p.anomalySeen = true
        m.photoId = p.id
        advance(s, 3, "photo", now)
        strange(s, "This photo is wrong", "\"${p.caption}\" There's a door in the background. A door on the hills. There was no door. He checked the photo twice. Then he turned it face down.",
            "one of my photos has a door in it that wasn't there", now, 0.65f)
        MoodEngine.setTransient(s, Mood.WORRIED, now, 60_000)
        return p
    }

    /* ---------------- Nib notices first ---------------- */

    fun petSenses(s: PipoState, env: Env, now: Long): Boolean =
        s.pet.adopted && s.mystery.stage >= 1 && (isNight(env.hour) || env.hour >= 21) && now >= (s.cooldowns["pet:stare"] ?: 0L)

    fun petStared(s: PipoState, now: Long): String {
        s.cooldowns["pet:stare"] = now + 3 * DAY
        s.pet.memories.add("stared:window")
        if (s.count("nib:stared") <= 2) Chronicle.journal(s, "Nib and the window", "Nib stared at the window for a long time. He looked too. Nothing there. Nib kept looking.", JournalCategory.STRANGE, now)
        return "Nib stared at the window for ages. There was nothing there"
    }

    /** The renderer may let his reflection lag, very rarely, once the photo has changed. */
    fun reflectionUneasy(s: PipoState) = s.mystery.stage >= 3
}
