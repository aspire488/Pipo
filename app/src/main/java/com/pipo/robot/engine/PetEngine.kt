package com.pipo.robot.engine

import com.pipo.robot.data.ActivityType
import com.pipo.robot.data.Catalog
import com.pipo.robot.data.EventType
import com.pipo.robot.data.JournalCategory
import com.pipo.robot.data.MemoryType
import com.pipo.robot.data.PetActivity
import com.pipo.robot.data.PetMood
import com.pipo.robot.data.PetTraits
import com.pipo.robot.data.PipoState
import com.pipo.robot.data.Place
import com.pipo.robot.data.ProjectState
import kotlin.random.Random

/**
 * Nib: a small, round, beeping robot who lives with Pipo. Nib has its own moods and its own
 * ideas — it follows him, naps in odd places, steals shiny things, plays with the ball, and
 * sometimes stares at something Pipo can't see.
 */
object PetEngine {
    fun newTraits(rng: Random): PetTraits {
        fun j(b: Float) = (b + (rng.nextFloat() - 0.5f) * 0.4f).coerceIn(0.1f, 0.95f)
        return PetTraits(curiosity = j(0.65f), playfulness = j(0.7f), mischief = j(0.5f), bravery = j(0.4f), cuddly = j(0.6f))
    }

    fun tick(s: PipoState, dtMs: Long, napping: Boolean, playing: Boolean, timeScale: Float = 1f) {
        val p = s.pet
        val h = dtMs / HOUR.toFloat() * timeScale
        p.energy = clamp01(p.energy + when { napping -> 0.35f * h; playing -> -0.25f * h; else -> -0.07f * h })
        p.boredom = clamp01(p.boredom + if (playing) -0.6f * h else 0.1f * h * (0.5f + p.traits.playfulness))
        p.fear = clamp01(p.fear - 0.8f * h)
        p.mood = derive(s)
    }

    fun derive(s: PipoState): PetMood {
        val p = s.pet
        return when {
            p.fear > 0.45f -> PetMood.SCARED
            p.energy < 0.25f -> PetMood.SLEEPY
            p.boredom > 0.55f && p.traits.playfulness > 0.45f -> PetMood.PLAYFUL
            p.boredom > 0.75f -> PetMood.GRUMPY
            p.traits.curiosity > 0.6f && p.energy > 0.5f -> PetMood.CURIOUS
            else -> PetMood.HAPPY
        }
    }

    /** Nib's next idea. [pipoActivity] null = Pipo isn't home. */
    fun choose(s: PipoState, env: Env, rng: Random, pipoActivity: ActivityType?, now: Long): PetActivity {
        val p = s.pet
        val t = p.traits
        val night = isNight(env.hour)
        val quietPipo = pipoActivity in setOf(ActivityType.THINK, ActivityType.REST, ActivityType.NOTHING, ActivityType.READ, ActivityType.SCROLL_PHONE, ActivityType.DRAW)
        val w = linkedMapOf(
            PetActivity.NAP to (1f - p.energy) * 2f + (if (night) 1.2f else 0f) + (if (pipoActivity == ActivityType.SLEEP) 0.8f else 0f),
            PetActivity.FOLLOW to (if (pipoActivity != null) p.bond * 0.9f + t.cuddly * 0.3f else 0f),
            PetActivity.SIT_WITH_PIPO to (if (quietPipo) p.bond * 1.4f + t.cuddly * 0.6f else 0f),
            PetActivity.PLAY_BALL to t.playfulness * p.boredom * 2.2f,
            PetActivity.WANDER to t.curiosity * 0.7f + (if (pipoActivity == null) 0.4f else 0f),
            PetActivity.ZOOMIES to (if (p.energy > 0.7f) t.playfulness * 0.45f else 0f),
            PetActivity.STEAL to (if (p.stolenItemId == 0L && s.world.items.isNotEmpty() && now >= (s.cooldowns["pet:steal"] ?: 0L)) t.mischief * 0.12f else 0f),
            PetActivity.HIDE to (if (env.weather.kind == Weather.STORM) 1.5f * (1f - t.bravery) else 0f) + p.fear * 2f,
            PetActivity.STARE to (if (Mystery.petSenses(s, env, now)) 2.5f else 0f),
        )
        var r = rng.nextFloat() * w.values.sum()
        for ((k, v) in w) { r -= v; if (r <= 0f) return k }
        return PetActivity.WANDER
    }

    /** Nib takes something shiny and hides it under the bed. Returns the item's name. */
    fun steal(s: PipoState, now: Long, rng: Random): String? {
        // Nib has taste: it doesn't take the same kind of thing twice in a row
        val recently = s.pet.memories.filter { it.startsWith("stole:") }.takeLast(2).map { it.removePrefix("stole:") }.toSet()
        val pool = s.world.items.filter { it.usedInProjectId == 0L }
        val item = (pool.filter { it.catalogId !in recently }.ifEmpty { pool }).randomOrNull(rng) ?: return null
        s.pet.stolenItemId = item.id
        s.cooldowns["pet:steal"] = now + 2 * DAY
        s.pet.memories.add("stole:${item.catalogId}"); trim(s)
        return Catalog.item(item.catalogId)?.name?.lowercase()
    }

    /** Pipo finds what Nib took. */
    fun recover(s: PipoState, now: Long): String? {
        val id = s.pet.stolenItemId.takeIf { it != 0L } ?: return null
        s.pet.stolenItemId = 0L
        val name = s.world.items.firstOrNull { it.id == id }?.let { Catalog.item(it.catalogId)?.name?.lowercase() } ?: return null
        together(s, "stole", "Nib hid my $name under the bed. I found it. Nib pretended to be surprised", now, 0.4f, objects = listOf(name))
        return name
    }

    /** Has Nib been caught stealing before? (Pipo remembers, and gets suspicious.) */
    fun knownThief(s: PipoState) = s.memories.any { it.key == "nibpipo:stole" }

    /**
     * Something Pipo and Nib went through together. One memory per kind, strengthened when it
     * happens again, so "the time Nib got lost" stays one story instead of fifty.
     */
    fun together(s: PipoState, kind: String, text: String, now: Long, importance: Float = 0.45f, place: String = "", objects: List<String> = emptyList()) {
        Experience.remember(s, MemoryType.PET, text, importance, now, "nibpipo:$kind", place = place, with = listOf("Nib"), objects = objects, feeling = if (kind in setOf("lost", "stole")) -0.1f else 0.5f)
        s.pet.memories.add("with:$kind"); trim(s)
        s.pet.bond = (s.pet.bond + 0.01f).coerceAtMost(1f)
    }

    /** On a trip, Nib noses about and occasionally finds something first. Returns (line, item id). */
    fun nibFinds(s: PipoState, place: Place, rng: Random, now: Long): Pair<String, Long>? {
        if (rng.nextFloat() > 0.12f + s.pet.traits.curiosity * 0.1f) return null
        val item = Discovery.roll(s, rng, now, placeTags = place.findTags) ?: return null
        val name = Catalog.item(item.catalogId)?.name?.lowercase() ?: return null
        together(s, "found", "Nib found ${article(name)} $name before I did", now, 0.45f, place = place.id, objects = listOf(item.catalogId))
        return "Nib found ${article(name)} $name first and wouldn't give it back until Pipo said please." to item.id
    }

    /**
     * While nobody's watching. One small event at most every so often. Returns it as Pipo would
     * finish the sentence "While you were gone I …".
     */
    fun offlineStep(s: PipoState, env: Env, now: Long, rng: Random): String? {
        if (!s.pet.adopted || s.trip?.withPet == true) return null
        tick(s, Simulator.STEP, napping = isNight(env.hour), playing = false)
        if (now < (s.cooldowns["pet:event"] ?: 0L) || rng.nextFloat() > 0.12f + s.pet.traits.mischief * 0.1f) return null
        s.cooldowns["pet:event"] = now + 10 * HOUR
        val t = s.pet.traits
        val building = s.activeProject()?.takeIf { it.state == ProjectState.BUILDING }
        val shelfItem = s.world.items.filter { it.usedInProjectId == 0L && it.id != s.pet.stolenItemId }.randomOrNull(rng)
        val r = rng.nextFloat()
        return when {
            Mystery.petSenses(s, env, now) -> { Mystery.petStared(s, now); "watched Nib stare at the window for ages. Nothing there" }
            building != null && r < 0.3f * t.mischief + 0.1f -> {
                building.progress = (building.progress - 0.1f).coerceAtLeast(0.05f)
                building.log.add("Nib sat on it. Nib is not sorry.")
                Chronicle.journal(s, "Nib sat on the ${building.title.lowercase()}", "Progress went backwards a bit. Nib looked proud.", JournalCategory.PET, now)
                Chronicle.event(s, EventType.PET, 0.4f + t.mischief * 0.1f, now, "sat")
                "caught Nib sitting on the ${building.title.lowercase()}"
            }
            shelfItem != null && r < 0.55f -> {
                s.world.objectStates["floor"] = shelfItem.id.toString()
                val name = Catalog.item(shelfItem.catalogId)?.name?.lowercase() ?: "something"
                Chronicle.journal(s, "Nib knocked the $name off the shelf", "It's on the floor. Nib is looking at it like it jumped.", JournalCategory.PET, now)
                Chronicle.event(s, EventType.PET, 0.38f + t.mischief * 0.12f, now, "knocked")
                "caught Nib knocking the $name off the shelf"
            }
            s.pet.stolenItemId == 0L && r < 0.75f -> steal(s, now, rng)?.let { "lost my $it. Nib knows something" }
            else -> {
                s.world.objectStates["pet_bed"] = "1"
                "found Nib asleep in my bed. Again"
            }
        }
    }

    /** Something Nib does out in the world. */
    fun tripEvent(s: PipoState, place: Place, rng: Random, now: Long): String? {
        val t = s.pet.traits
        if (rng.nextFloat() > 0.45f + t.mischief * 0.2f) return null
        val line = when (place.id) {
            "hardware", "electronics", "secondhand" -> if (rng.nextBoolean()) "Nib got lost in aisle two. Found in a bucket of washers." else "Nib beeped at every single light. Every one."
            "park", "garden" -> if (rng.nextBoolean()) "Nib chased a squirrel. The squirrel won." else "Nib rolled in the grass and came home green."
            "lake" -> "Nib tried to make friends with a frog. The frog was unimpressed."
            "field" -> "Nib stole the ball. Twice. Kip laughed."
            "market", "bakery", "cafe" -> "Nib stared at the cakes until someone gave Nib a crumb."
            "hills" -> "Nib ran all the way up. Pipo walked. Nib waited, smug."
            else -> "Nib stayed very close the whole time."
        }
        val key = when { line.contains("lost") -> "lost:${place.id}"; line.contains("squirrel") -> "chased:squirrel"; else -> "trip:${place.id}" }
        s.pet.memories.add(key); trim(s)
        if (line.contains("lost")) {
            Chronicle.remember(s, MemoryType.PET, "Nib got lost at ${Trips.placePhrase(place)}", 0.55f, now, "petlost:${place.id}")
            together(s, "lost", "we got lost at ${Trips.placePhrase(place)}. Well, Nib did. I found Nib", now, 0.55f, place = place.id)
        }
        return line
    }

    /** For a Pipo who lived here before Nib: Nib arrives as part of his story. */
    fun adopt(s: PipoState, now: Long, place: Place?): String {
        val p = s.pet
        p.adopted = true
        p.adoptedAt = now
        p.activity = PetActivity.FOLLOW
        p.x = 270f
        val line = if (place != null) "On the way home from ${Trips.placePhrase(place)}, something followed him. Small. Round. It beeps. He asked its name. It beeped \"Nib\". Probably."
            else "Something was scratching at the door. Small. Round. It beeps. It walked straight in like it lived here. Its name is Nib. Probably."
        Chronicle.journal(s, "Nib", line, JournalCategory.PET, now)
        Chronicle.remember(s, MemoryType.PET, "the day Nib came home with me", 0.95f, now, "nib:arrived")
        Chronicle.event(s, EventType.PET, 0.75f, now, "arrived")
        return line
    }

    /** Called once per catch-up: a pre-Nib Pipo who hasn't been out yet still meets Nib within a day. */
    fun maybeArrive(s: PipoState, now: Long): String? {
        if (s.pet.adopted || s.migratedAt == 0L || now - s.migratedAt < 20 * HOUR) return null
        return adopt(s, now, null)
    }

    private fun trim(s: PipoState) { while (s.pet.memories.size > 20) s.pet.memories.removeAt(0) }
}
