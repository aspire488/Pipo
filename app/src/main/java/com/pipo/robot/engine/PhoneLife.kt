package com.pipo.robot.engine

import com.pipo.robot.data.JournalCategory
import com.pipo.robot.data.MemoryType
import com.pipo.robot.data.PhotoSubject
import com.pipo.robot.data.PipoState
import com.pipo.robot.data.Places
import com.pipo.robot.data.Recording
import kotlin.random.Random

/** The apps on Pipo's little phone. Not a smartphone clone: these are the things *he* does with it. */
enum class PhoneApp { FEED, CAMERA, GALLERY, WEATHER, NOTES, RECORDER, CALCULATOR, MAP }

/**
 * Pipo's own phone, as an object in his life. What he opens depends on what's going on: Nib is
 * being cute → camera; it's raining → he records the rain; he's saving up → calculator; a trip on
 * his mind → the map; late at night after something strange → he writes a theory in his notes.
 * It still counts as screen time (see [ScreenTime]); the feed is just one of the apps.
 */
object PhoneLife {
    fun pickApp(s: PipoState, env: Env, now: Long, rng: Random): PhoneApp {
        val w = linkedMapOf(
            PhoneApp.FEED to 1.2f,
            PhoneApp.CAMERA to (if (s.pet.adopted && s.trip?.withPet != true) 0.45f else 0.15f) + s.profile.traits.playfulness * 0.2f,
            PhoneApp.GALLERY to (if (s.photos.size >= 3) 0.35f else 0f),
            PhoneApp.WEATHER to (if (env.hour in 6..10) 0.35f else 0.1f),
            PhoneApp.NOTES to 0.2f + (if (s.mystery.stage >= 1 && (env.hour >= 21 || env.hour < 5)) 0.6f else 0f) + s.profile.traits.curiosity * 0.1f,
            PhoneApp.RECORDER to (if (env.weather.wet || env.weather.kind == Weather.WIND) 0.5f else 0.12f),
            PhoneApp.CALCULATOR to (if (Trips.materialsNeeded(s).isNotEmpty()) 0.4f else 0.05f),
            PhoneApp.MAP to (if (s.places.isNotEmpty()) 0.25f else 0.05f),
        )
        var r = rng.nextFloat() * w.values.sum()
        for ((k, v) in w) { r -= v; if (r <= 0f) return k }
        return PhoneApp.FEED
    }

    /** Uses one app. Everything he "sees" on it comes from real state. */
    fun use(s: PipoState, app: PhoneApp, env: Env, now: Long, rng: Random): List<Outcome> = when (app) {
        PhoneApp.FEED -> emptyList() // handled by the feed (FeedLife) in BehaviorEngine
        PhoneApp.CAMERA -> {
            val petHome = s.pet.adopted && s.trip?.withPet != true
            val subject = if (petHome && rng.nextFloat() < 0.7f) PhotoSubject.PET else PhotoSubject.SELFIE
            val p = PhotoLife.take(s, subject, "", "room", env, now, rng)
            val link = Links.afterPhoto(s, p, now)
            listOf(Outcome.Photographed(p, link ?: if (subject == PhotoSubject.PET) Dialogue.pick(listOf("Nib, hold still. NIB.", "Got Nib! Blurry Nib. Classic."), rng)
                else Dialogue.pick(listOf("Selfie. My good side. All my sides are good.", "Me, being me. For the album."), rng)))
        }
        PhoneApp.GALLERY -> {
            val wrong = Mystery.onLookAtPhotos(s, now, rng)
            if (wrong != null) listOf(Outcome.Strange("...this photo is wrong.", wrong))
            else s.photos.randomOrNull(rng)?.let { listOf(Outcome.PhoneUsed(app, Dialogue.photoMemory(it, rng))) } ?: emptyList()
        }
        PhoneApp.WEATHER -> {
            // his weather app shows HIS weather: the same deterministic sky the window will show later
            val later = WeatherEngine.at(s.seed, now + 4 * HOUR)
            val line = when {
                later.kind == env.weather.kind -> "My weather app says: more ${WeatherEngine.describe(later.kind)}. It's never wrong. It's sometimes wrong."
                later.wet -> "Rain later. I'm telling my legs now."
                later.kind == Weather.STORM -> "Storm later. I'm going to be brave about it. Probably."
                later.niceOut -> "It's going to be nice later! Outside time."
                else -> "Later: ${WeatherEngine.describe(later.kind)}. Noted."
            }
            listOf(Outcome.PhoneUsed(app, line))
        }
        PhoneApp.NOTES -> listOf(Outcome.PhoneUsed(app, writeNote(s, now, rng)))
        PhoneApp.RECORDER -> {
            val (label, kind) = when {
                env.weather.wet -> "rain on the window" to "rain"
                env.weather.kind == Weather.WIND -> "the wind" to "wind"
                s.pet.adopted && s.pet.mood == com.pipo.robot.data.PetMood.SLEEPY -> "Nib snoring" to "nib"
                s.pet.adopted -> "Nib beeping" to "nib"
                else -> "the fridge humming" to "hum"
            }
            s.recordings.add(Recording(s.nextId(), label, kind, now))
            if (s.recordings.size > 30) s.recordings.removeAt(0)
            listOf(Outcome.PhoneUsed(app, "Shh. I'm recording $label. For my collection."))
        }
        PhoneApp.CALCULATOR -> {
            val need = Trips.materialsNeeded(s).firstOrNull()
            val line = if (need != null) {
                val price = Economy.priceOf(need)
                val short = price - s.coins
                if (short <= 0) "${s.coins} coins. The ${Economy.nameOf(need)} is $price. I can afford it! Math says go."
                else "${Economy.nameOf(need).replaceFirstChar { it.uppercase() }}: $price. Me: ${s.coins}. I need $short more. One job at Fennel's."
            } else "I did some math. ${s.coins} coins. The answer is: snacks."
            listOf(Outcome.PhoneUsed(app, line))
        }
        PhoneApp.MAP -> {
            val fav = Likes.favorite(s, "place:")?.let { Places.byId(it) }
            val next = Trips.best(s, env, now)?.let { Places.byId(it.placeId) }
            val line = when {
                next != null && next.id !in s.places -> "Looking at my map. I've never been to ${Trips.placePhrase(next)}. Yet."
                fav != null -> "Looking at my map. ${Trips.placePhrase(fav).replaceFirstChar { it.uppercase() }}. My favourite. I want to go back."
                else -> "Looking at my map. There's so much map."
            }
            listOf(Outcome.PhoneUsed(app, line))
        }
    }

    /**
     * His notes app: a thought or a theory, in his words, about his real life. It goes in the
     * journal as HIS entry (not yours, not the engine's summary).
     */
    fun writeNote(s: PipoState, now: Long, rng: Random): String {
        val named = s.creatures.values.filter { it.name.isNotEmpty() }.maxByOrNull { it.sightings }
        val fav = Likes.favorite(s, "place:")?.let { Places.byId(it)?.let { p -> Trips.placePhrase(p) } }
        val options = buildList {
            if (s.mystery.stage >= 1) add("Theory: the lights on the hills are a message. Nib agrees. Nib agrees with everything at night.")
            if (s.mystery.stage >= 2) add("The mark on the token and the mark in my dream are the SAME. Coincidence? (Probably not.)")
            if (s.pet.stolenItemId != 0L || "stole" in s.pet.memories.joinToString()) add("Things Nib has stolen: many. Things Nib has returned: none.")
            if (named != null) add("${named.name} has been here ${named.sightings} times. ${named.name} is basically family.")
            if (fav != null) add("Places I like: $fav. Places I don't like: nowhere. Except the dentist. I've never been to the dentist.")
            s.projects.lastOrNull { !it.active }?.let { add("Things I learned from the ${it.title.lowercase()}: ${if (it.state == com.pipo.robot.data.ProjectState.FAILED) "glue is not a plan" else "I'm a genius, mostly"}.") }
            s.records["kickups"]?.let { add("Kick-up record: $it. Next goal: ${it + 5}. Then the world.") }
            add("List of things that are round: ball, Nib, the moon, marbles, me (a bit).")
            add("Do clouds get tired of floating? Asking for a cloud.")
        }
        val note = Dialogue.pick(options, rng)
        Chronicle.journal(s, "From Pipo's notes", note, JournalCategory.THOUGHT, now)
        Experience.remember(s, MemoryType.SELF, "I wrote: ${note.take(60)}", 0.3f, now, "note:${note.hashCode()}")
        return "Writing something down. \"${note.substringBefore('.')}.\" Don't read over my shoulder."
    }
}
