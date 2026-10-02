package com.pipo.robot.engine

import com.pipo.robot.data.Catalog
import com.pipo.robot.data.DrawSubject
import com.pipo.robot.data.EventType
import com.pipo.robot.data.JournalCategory
import com.pipo.robot.data.MemoryType
import com.pipo.robot.data.OwnedItem
import com.pipo.robot.data.Photo
import com.pipo.robot.data.PipoMemory
import com.pipo.robot.data.PipoProject
import com.pipo.robot.data.PipoState
import com.pipo.robot.data.Places
import com.pipo.robot.data.ProjectState
import com.pipo.robot.data.TripReport
import kotlin.math.abs
import kotlin.random.Random

/* ================================================================== */
/*  Likes: favourites and dislikes that emerge from what happened      */
/* ================================================================== */

/**
 * He isn't born with a favourite place. He goes to the lake, sees a frog with opinions, goes again,
 * and one day the lake is his favourite. Keys: "place:lake", "act:DRAW", "weather:RAIN", "game:rps".
 * Values drift slowly in −1..1 and nudge his choices a little. They're never shown as numbers.
 */
object Likes {
    fun feel(s: PipoState, key: String, delta: Float) {
        val v = ((s.fondness[key] ?: 0f) * 0.985f + delta).coerceIn(-1f, 1f)
        s.fondness[key] = v
        if (s.fondness.size > 120) s.fondness.entries.minByOrNull { abs(it.value) }?.let { s.fondness.remove(it.key) }
    }

    fun of(s: PipoState, key: String) = s.fondness[key] ?: 0f

    /** A small scoring nudge: favourites pull a bit, dislikes push a bit. Never decisive on its own. */
    fun bonus(s: PipoState, key: String): Float = (of(s, key) * 0.3f).coerceIn(-0.25f, 0.3f)

    fun favorite(s: PipoState, prefix: String): String? =
        s.fondness.filterKeys { it.startsWith(prefix) }.maxByOrNull { it.value }?.takeIf { it.value >= 0.3f }?.key?.removePrefix(prefix)

    fun disliked(s: PipoState, prefix: String): String? =
        s.fondness.filterKeys { it.startsWith(prefix) }.minByOrNull { it.value }?.takeIf { it.value <= -0.25f }?.key?.removePrefix(prefix)

    /** How much he enjoyed doing [type] just now, from what it did to him. */
    fun afterActivity(s: PipoState, type: com.pipo.robot.data.ActivityType, happinessBefore: Float) {
        val d = s.mood.happiness - happinessBefore
        val grumpy = s.mood.current == com.pipo.robot.data.Mood.GRUMPY
        feel(s, "act:${type.name}", (d * 0.6f + (if (grumpy) -0.02f else 0.015f)).coerceIn(-0.06f, 0.06f))
    }

    /** Words for a favourite, for talking about it. */
    fun describe(key: String): String = when {
        key.startsWith("place:") -> Places.byId(key.removePrefix("place:"))?.let { Trips.placePhrase(it) } ?: key
        key.startsWith("act:") -> runCatching { BehaviorEngine.describe(com.pipo.robot.data.ActivityType.valueOf(key.removePrefix("act:"))) }.getOrDefault(key)
        key.startsWith("weather:") -> runCatching { WeatherEngine.describe(Weather.valueOf(key.removePrefix("weather:"))) + " days" }.getOrDefault(key)
        key.startsWith("game:") -> Dialogue.gameName(key.removePrefix("game:"))
        else -> key
    }
}

/* ================================================================== */
/*  Experiences: memories with where / who / what / how it felt         */
/* ================================================================== */

object Experience {
    fun remember(
        s: PipoState, type: MemoryType, content: String, importance: Float, now: Long, key: String = "",
        place: String = "", with: List<String> = emptyList(), objects: List<String> = emptyList(), feeling: Float = 0f,
    ): PipoMemory = Chronicle.remember(s, type, content, importance, now, key).also { m ->
        if (place.isNotEmpty()) m.place = place
        if (with.isNotEmpty()) m.with = with.distinct()
        if (objects.isNotEmpty()) m.objects = objects.distinct().take(6)
        if (feeling != 0f) m.feeling = feeling.coerceIn(-1f, 1f)
    }

    /** "…at the lake, with Nib" — only from what's actually stored. */
    fun context(m: PipoMemory): String {
        val where = Places.byId(m.place)?.let { " at ${Trips.placePhrase(it)}" } ?: ""
        val who = m.with.filter { it != "me" }.let { if (it.isEmpty()) "" else " with ${Trips.listPhrase(it)}" }
        return where + who
    }
}

/* ================================================================== */
/*  Rituals: the things the two of you keep doing                      */
/* ================================================================== */

/**
 * Not a relationship meter: repeated shared experiences become "our thing" (a memory he can bring
 * up), and he learns roughly when you tend to visit. Both only change what he says and does.
 */
object Rituals {
    val names = mapOf(
        "hideseek" to "hide and seek",
        "pats" to "head pats",
        "games" to "our games",
        "texts" to "texting me when I'm out",
        "photos" to "you showing me photos",
        "nightvisit" to "late-night visits",
    )

    /** Counts a shared moment. On the 3rd (and every 10th after) it becomes / refreshes "our thing". */
    fun shared(s: PipoState, kind: String, now: Long): String? {
        val n = s.count("ritual:$kind")
        Personality.nudge(s, Trait.AFFECTION, 0.002f)
        if (n != 3 && n % 10 != 0) return null
        val name = names[kind] ?: kind
        Experience.remember(s, MemoryType.JOKE, "${name.replaceFirstChar { it.uppercase() }} ${if (name.endsWith("s") && !name.endsWith("ss")) "are" else "is"} our thing", 0.6f + minOf(n, 30) / 100f, now, "ritual:$kind", with = listOf("you"), feeling = 0.6f)
        if (n == 3) Chronicle.journal(s, "Our thing", "${name.replaceFirstChar { it.uppercase() }}. Three times now. He says that makes it a tradition.", JournalCategory.MOMENT, now)
        return name
    }

    fun noteVisit(s: PipoState, hour: Int) {
        s.visitHours.add(hour)
        while (s.visitHours.size > 24) s.visitHours.removeAt(0)
        if (hour >= 23 || hour < 4) s.count("ritual:nightvisit")
    }

    /** If you have a routine (most visits in the same few hours), and this visit fits it. */
    fun routineGreeting(s: PipoState, hour: Int, rng: Random): String? {
        if (s.visitHours.size < 8) return null
        val window = s.visitHours.count { h -> circular(h, hour) <= 1 }
        if (window < s.visitHours.size * 0.6f) return null
        return Dialogue.pick(when (hour) {
            in 5..11 -> listOf("Morning visit. Right on time.", "You always come in the morning. I noticed.")
            in 12..17 -> listOf("Afternoon you. My favourite you.", "Right on schedule.")
            in 18..22 -> listOf("Evening visit. I saved you a spot.", "It's our time.")
            else -> listOf("Night visit again. We're night people now.", "You and your late nights.")
        }, rng)
    }

    private fun circular(a: Int, b: Int) = minOf(abs(a - b), 24 - abs(a - b))
}

/* ================================================================== */
/*  Links: when two things in his life touch each other                */
/* ================================================================== */

/**
 * Small, reusable consequence rules between systems. None of them writes a story in advance:
 * each fires only when its ingredients actually exist in his life at the same time
 * (a drawing and a photo of the same place, a find that fits a failed project, Nib back in the
 * shop where Nib got lost, a token that warms when something breaks). They return lines for
 * whatever is staging the moment.
 */
object Links {
    /** After a trip: Nib's history there, animals turning up somewhere new, a wrong buy that turns out right. */
    fun afterTrip(s: PipoState, r: TripReport, now: Long): List<String> {
        val out = mutableListOf<String>()
        if (r.withPet && "lost:${r.placeId}" in s.pet.memories.dropLast(1) && s.count("link:nibremembers:${r.placeId}") == 1)
            out += "Nib remembered this place. Nib stayed very, very close."
        s.creatures[r.creatureKey]?.let { c ->
            val seenElsewhere = s.memories.any { it.key == "sighting:${c.key}" && it.place.isNotEmpty() && it.place != r.placeId }
            if (c.name.isNotEmpty() && seenElsewhere && s.count("link:follow:${c.key}") == 1) {
                out += "${c.name} was here too. Is ${c.name} following me?"
                Experience.remember(s, MemoryType.MOMENT, "${c.name} turned up at ${Places.byId(r.placeId)?.let { Trips.placePhrase(it) }}", 0.5f, now, "follow:${c.key}", place = r.placeId, objects = listOf(c.key))
            }
            Experience.remember(s, MemoryType.EVENT, "I saw ${WildlifeLife.label(c)}", 0.3f, now, "sighting:${c.key}", place = r.placeId, with = if (r.withPet) listOf("Nib") else emptyList())
        }
        if (r.wrongBuy.isNotEmpty()) wrongThingWasRight(s, r.wrongBuy, now)?.let { out += it }
        return out
    }

    /** A find (or a wrong buy) that's exactly what a failed project was missing. */
    fun afterFind(s: PipoState, item: OwnedItem, now: Long): String? {
        val d = Catalog.item(item.catalogId) ?: return null
        val failed = Projects.retryCandidate(s, now) ?: return null
        if (s.activeProject() != null) return null
        val def = Catalog.project(failed.templateId) ?: return null
        if (d.tags.none { it in def.needs } || now < (s.cooldowns["link:fits"] ?: 0L)) return null
        s.cooldowns["link:fits"] = now + 2 * DAY
        Experience.remember(s, MemoryType.PROJECT, "the ${d.name.lowercase()} might fix my ${def.title.lowercase()}", 0.55f, now, "fits:${def.id}", objects = listOf(d.id, def.id))
        Chronicle.event(s, EventType.THOUGHT, 0.45f, now, "idea:${def.title}")
        return "Wait. The ${d.name.lowercase()}. That's what the ${def.title.lowercase()} was missing."
    }

    private fun wrongThingWasRight(s: PipoState, id: String, now: Long): String? {
        val p = s.activeProject() ?: return null
        val tags = Catalog.item(id)?.tags ?: return null
        if (tags.none { it in p.components && it !in p.collected }) return null
        Experience.remember(s, MemoryType.JOKE, "the wrong thing I bought was the right thing", 0.5f, now, "wrongright", objects = listOf(id, p.templateId))
        return "The wrong thing was the right thing. I planned this."
    }

    /** When a project fails near the token, the token notices. (Only once the thread has started.) */
    fun afterProjectEnd(s: PipoState, p: PipoProject, now: Long): String? {
        if (p.state != ProjectState.FAILED || s.mystery.stage < 2 || !Inventory.owns(s, "brass_token")) return null
        if (s.count("link:tokenwarm") > 2) return null
        Experience.remember(s, MemoryType.STRANGE, "when my ${p.title.lowercase()} broke, the token got warm", 0.7f, now, "tokenwarm", objects = listOf("brass_token", p.templateId), feeling = -0.2f)
        Chronicle.journal(s, "The token", "When the ${p.title.lowercase()} broke, the token on the shelf got warm. Just for a second. He checked twice.", JournalCategory.STRANGE, now)
        return "...the token got warm. When it broke. Just for a second."
    }

    /** A new photo of somewhere (or someone) he's already drawn. */
    fun afterPhoto(s: PipoState, p: Photo, now: Long): String? {
        val match = s.drawings.lastOrNull { d ->
            (d.subject == DrawSubject.PLACE && d.ref == p.placeId) || (d.subject == DrawSubject.CREATURE && d.ref == p.ref && p.ref.isNotEmpty())
        } ?: return null
        if (s.count("link:photodrawing:${match.id}") != 1) return null
        Experience.remember(s, MemoryType.MOMENT, "my photo looks like my drawing. Mostly", 0.4f, now, "photodrawing:${match.id}", place = p.placeId)
        return "This looks just like my drawing. Except the volcano."
    }
}
