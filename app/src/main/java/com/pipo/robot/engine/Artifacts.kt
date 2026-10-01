package com.pipo.robot.engine

import com.pipo.robot.data.Catalog
import com.pipo.robot.data.Creature
import com.pipo.robot.data.Daytime
import com.pipo.robot.data.DrawSubject
import com.pipo.robot.data.Drawing
import com.pipo.robot.data.JournalCategory
import com.pipo.robot.data.MemoryType
import com.pipo.robot.data.Photo
import com.pipo.robot.data.PhotoSubject
import com.pipo.robot.data.Places
import com.pipo.robot.data.PipoState
import com.pipo.robot.data.ProjectState
import com.pipo.robot.data.Wildlife
import kotlin.random.Random

/* ================================================================== */
/*  Animals he keeps meeting                                           */
/* ================================================================== */

object WildlifeLife {
    private fun daytimeOk(d: Daytime, hour: Int) = when (d) {
        Daytime.ANY -> true
        Daytime.DAY -> hour in 7..19
        Daytime.NIGHT -> hour >= 20 || hour < 5
        Daytime.DUSK -> hour in 18..21
    }

    /** Species that could plausibly be here right now. "window" = seen from his room. */
    fun possible(placeId: String, env: Env): List<String> {
        val fromPlace = if (placeId == "window") Wildlife.species.filter { it.window }.map { it.id }
            else Places.byId(placeId)?.wildlife.orEmpty()
        return fromPlace.filter { id ->
            val sp = Wildlife.byId(id) ?: return@filter false
            daytimeOk(sp.daytime, env.hour) && (sp.weather == null || env.weather.kind.name in sp.weather)
        }
    }

    /**
     * Meets an animal. The same few individuals keep turning up, so they become familiar,
     * and on the second meeting he names them.
     */
    fun encounter(s: PipoState, placeId: String, env: Env, now: Long, rng: Random, chance: Float = 0.7f): Creature? {
        val options = possible(placeId, env)
        if (options.isEmpty() || rng.nextFloat() > chance) return null
        val sp = Wildlife.byId(options.random(rng))!!
        // Individuals: known ones come back more often than new ones appear.
        val known = s.creatures.values.filter { it.species == sp.id }
        val c = if (known.isNotEmpty() && (known.size >= sp.looks.size || rng.nextFloat() < 0.7f)) known.random(rng) else {
            val idx = (0 until sp.looks.size).firstOrNull { i -> s.creatures["${sp.id}:$i"] == null } ?: 0
            s.creatures.getOrPut("${sp.id}:$idx") { Creature("${sp.id}:$idx", sp.id, sp.looks[idx], firstSeen = now) }
        }
        c.sightings++
        c.lastSeen = now
        c.lastPlace = placeId
        if (c.sightings == 2 && c.name.isEmpty()) {
            val used = s.creatures.values.map { it.name }.toSet()
            c.name = Wildlife.names.filter { it !in used }.let { if (it.isEmpty()) "Friend" else it[(mix(s.seed, c.key.hashCode().toLong()) and 0xFFFF).toInt() % it.size] }
            Chronicle.journal(s, "Pipo named ${c.look}", "\"${c.name}.\" He says it suits them. They didn't object.", JournalCategory.PLACE, now)
            Chronicle.remember(s, MemoryType.MOMENT, "I named ${c.look} ${c.name}", 0.55f, now, "named:${c.key}")
        } else if (c.sightings >= 5) {
            Chronicle.remember(s, MemoryType.MOMENT, "${c.name.ifEmpty { c.look }} and I are friends now", 0.5f, now, "friend:${c.key}")
        }
        return c
    }

    fun storyLine(c: Creature): String = when {
        c.sightings == 1 -> "Saw ${c.look}."
        c.sightings == 2 -> "Saw ${c.look} again. Named them ${c.name}."
        else -> "${c.name.ifEmpty { c.look.replaceFirstChar { it.uppercase() } }} was there again. Obviously."
    }

    fun label(c: Creature) = c.name.ifEmpty { c.look }
}

/* ================================================================== */
/*  His phone camera                                                   */
/* ================================================================== */

object PhotoLife {
    fun take(s: PipoState, subject: PhotoSubject, ref: String, placeId: String, env: Env, now: Long, rng: Random): Photo {
        val place = Places.byId(placeId)
        val where = when { placeId == "room" -> "my room"; placeId == "window" -> "the window"; place != null -> Trips.placePhrase(place); else -> "somewhere" }
        val caption = when (subject) {
            PhotoSubject.CREATURE -> s.creatures[ref]?.let { "${WildlifeLife.label(it).replaceFirstChar { c -> c.uppercase() }}. At $where." } ?: "Something with legs. At $where."
            PhotoSubject.PET -> "Nib at $where. Nib moved."
            PhotoSubject.SELFIE -> "Me. At $where. My good side. Both sides."
            PhotoSubject.PLACE -> "${where.replaceFirstChar { it.uppercase() }}. ${WeatherEngine.describe(env.weather.kind).replaceFirstChar { it.uppercase() }}."
            PhotoSubject.WEATHER -> "The ${WeatherEngine.describe(env.weather.kind)} sky. From $where."
            PhotoSubject.FOOD -> "${Economy.nameOf(ref).replaceFirstChar { it.uppercase() }}. Before I ate it."
            PhotoSubject.INVENTION -> "My ${Catalog.project(ref)?.title?.lowercase() ?: "machine"}. Proof."
            PhotoSubject.SKY -> "Stars. Some of them."
        }
        val p = Photo(s.nextId(), subject, ref, placeId, env.weather.kind.name, env.hour, caption, now, seed = rng.nextInt(1 shl 20))
        s.photos.add(p)
        if (s.photos.size > 80) s.photos.removeAt(0)
        s.count("photos")
        return p
    }
}

/* ================================================================== */
/*  His sketchbook                                                     */
/* ================================================================== */

object DrawingLife {
    fun score(s: PipoState, env: Env): Float {
        val t = s.profile.traits
        val fresh = s.creatures.isNotEmpty() || s.places.isNotEmpty() || s.projects.any { !it.active }
        return 0.12f + t.curiosity * 0.2f + t.playfulness * 0.15f + (if (fresh) 0.2f else 0f) + (if (env.weather.wet) 0.25f else 0f) +
            (if (Mystery.wantsToDrawDream(s)) 1.4f else 0f)
    }

    /** He draws what's on his mind: something he saw, somewhere he went, something he built, you. */
    fun draw(s: PipoState, now: Long, rng: Random): Drawing {
        Mystery.dreamDrawing(s, now, rng)?.let { return it }
        val recentCreature = s.creatures.values.filter { now - it.lastSeen < 3 * DAY }.maxByOrNull { it.lastSeen }
        val recentPlace = s.places.entries.filter { now - it.value.lastVisit < 3 * DAY }.maxByOrNull { it.value.lastVisit }?.key
        val built = s.projects.lastOrNull { it.state == ProjectState.DONE || it.state == ProjectState.EVOLVED }
        val options = buildList {
            add(DrawSubject.USER to (0.8f + s.profile.relationship))
            add(DrawSubject.SELF to 0.5f)
            if (s.pet.adopted) add(DrawSubject.PET to 0.9f)
            if (recentCreature != null) add(DrawSubject.CREATURE to 1.2f)
            if (recentPlace != null) add(DrawSubject.PLACE to 1f)
            if (built != null) add(DrawSubject.INVENTION to 0.7f)
            s.tastes.maxByOrNull { it.value }?.takeIf { it.value > 0.5f }?.let { add(DrawSubject.FOOD to 0.35f) }
        }
        var r = rng.nextFloat() * options.sumOf { it.second.toDouble() }.toFloat()
        var subject = options.first().first
        for ((k, w) in options) { r -= w; if (r <= 0f) { subject = k; break } }
        val (ref, caption) = when (subject) {
            DrawSubject.USER -> "" to Dialogue.pick(listOf("You. The ears are a choice.", "You, smiling. I gave you extra fingers. Bonus.", "You and me. I'm the tall one."), rng)
            DrawSubject.SELF -> "" to Dialogue.pick(listOf("Me, but heroic.", "Self-portrait. Very accurate. Except the cape."), rng)
            DrawSubject.PET -> "" to Dialogue.pick(listOf("Nib, asleep. The only time Nib holds still.", "Nib. I drew Nib bigger. Nib asked."), rng)
            DrawSubject.CREATURE -> recentCreature!!.key to "${WildlifeLife.label(recentCreature).replaceFirstChar { it.uppercase() }}. From memory."
            DrawSubject.PLACE -> recentPlace!! to "${Places.byId(recentPlace)?.name ?: "A place"}. I added a volcano. Artistic freedom."
            DrawSubject.INVENTION -> built!!.templateId to "My ${built.title.lowercase()}. How it should have looked."
            DrawSubject.FOOD -> (s.tastes.maxByOrNull { it.value }!!.key) to "${Economy.nameOf(s.tastes.maxByOrNull { it.value }!!.key).replaceFirstChar { it.uppercase() }}. A love letter."
            else -> "" to "A drawing."
        }
        val d = Drawing(s.nextId(), subject, ref, caption, now, seed = rng.nextInt(1 shl 20))
        s.drawings.add(d)
        if (s.drawings.size > 60) s.drawings.removeAt(0)
        if (s.count("drew") == 1 || subject == DrawSubject.USER) {
            Chronicle.journal(s, if (subject == DrawSubject.USER) "Pipo drew you" else "Pipo drew something", caption, JournalCategory.MOMENT, now)
            Chronicle.remember(s, MemoryType.MOMENT, if (subject == DrawSubject.USER) "I drew a picture of my human" else "I drew ${caption.lowercase().trimEnd('.')}", 0.5f, now, "drawing:${subject.name}")
        }
        return d
    }

    /** At most six drawings hang on his walls. Newest go up; the oldest go into the drawer. */
    fun hung(s: PipoState): List<Drawing> = s.drawings.filter { it.hung }.takeLast(6)
}

/* ================================================================== */
/*  His little feed                                                    */
/* ================================================================== */

data class Clip(val topic: String, val title: String)

object FeedLife {
    val topics = mapOf(
        "robots" to listOf("A robot tries to open a jar. For eleven minutes.", "Tiny robot does a backflip. Lands on its face.", "Robot learns to whistle. Kind of."),
        "pets" to listOf("A cat sits in a box that's too small.", "Dog meets a robot vacuum. Chaos.", "Hamster with a very tiny hat."),
        "football" to listOf("Kid scores from the halfway line.", "The rainbow flick, in slow motion.", "Goalkeeper does a cartwheel save."),
        "inventions" to listOf("A machine that makes toast AND sings.", "Tiny drone flies through a hoop.", "Marble run that goes through a whole house."),
        "cooking" to listOf("Robot makes noodle soup. Perfectly.", "The fluffiest pancakes ever.", "Omelette flip. Nailed it."),
        "wildlife" to listOf("A frog that sounds like a squeaky door.", "Squirrel solves a puzzle.", "Fireflies, blinking all at once."),
        "comedy" to listOf("Man trips over nothing. Twice.", "A goose chases a bicycle.", "Someone's grandma beats everyone at arm wrestling."),
        "music" to listOf("A song played on bottles.", "Tiny robot plays the drums.", "Choir of frogs. Remix."),
    )

    /** A short scroll: 2–4 clips, picked by what he's into (and a bit of whatever the feed shows him). */
    fun session(s: PipoState, rng: Random): List<Clip> {
        val n = 2 + rng.nextInt(3)
        return (0 until n).map {
            val w = topics.keys.map { k -> k to (0.3f + (s.feed[k] ?: 0f)) }
            var r = rng.nextFloat() * w.sumOf { it.second.toDouble() }.toFloat()
            var topic = w.first().first
            for ((k, v) in w) { r -= v; if (r <= 0f) { topic = k; break } }
            Clip(topic, topics[topic]!!.random(rng))
        }
    }

    /**
     * Watching changes him a little. Enough of one thing becomes an obsession that leaks into his
     * real life: he cooks the dish, practises the trick, builds the machine, goes to find the frog.
     */
    fun absorb(s: PipoState, clips: List<Clip>, env: Env, now: Long, rng: Random): String? {
        for (c in clips) s.feed[c.topic] = ((s.feed[c.topic] ?: 0f) * 0.97f + 0.08f).coerceAtMost(1f)
        s.feed.keys.toList().forEach { k -> if (clips.none { it.topic == k }) s.feed[k] = (s.feed[k] ?: 0f) * 0.98f }
        val hot = clips.map { it.topic }.firstOrNull { (s.feed[it] ?: 0f) > 0.45f } ?: return null
        if (now < (s.cooldowns["obsession"] ?: 0L)) return null
        s.cooldowns["obsession"] = now + 8 * HOUR
        val clip = clips.first { it.topic == hot }
        Chronicle.remember(s, MemoryType.SELF, "I've been watching a lot of $hot videos", 0.35f, now, "obsessed:$hot")
        return when (hot) {
            "cooking" -> { FoodLife.maybeCrave(s, env, rng, "noodle_soup" to "I saw a video. \"${clip.title}\" Now I need noodle soup."); "I just watched a robot make noodle soup. I'm making noodle soup. It's decided." }
            "football" -> { s.cooldowns["practice"] = now + 2 * HOUR; "I saw the rainbow flick. I'm going to learn it. Today. Maybe." }
            "inventions" -> Projects.maybeStart(s, rng, now, 1f, prefer = "drone")?.let { "A video showed a tiny drone. I'm building one. ${it.title}. Watch me." }
            "wildlife" -> "There's a frog that sounds like a squeaky door. I need to meet it."
            else -> null
        }
    }

    fun reaction(c: Clip, rng: Random): String = when (c.topic) {
        "comedy" -> Dialogue.pick(listOf("HAHA. Again.", "He fell over! Like me!", "Hehehe."), rng)
        "pets" -> Dialogue.pick(listOf("Nib would never. Nib would.", "Look at its little hat.", "Awww."), rng)
        "robots" -> Dialogue.pick(listOf("That's my cousin. Probably.", "I could do that. With practice.", "Rude. He's trying his best."), rng)
        "football" -> Dialogue.pick(listOf("How did he DO that?", "I'm going to try that.", "Goal! GOAL!"), rng)
        "inventions" -> Dialogue.pick(listOf("Ooh. Ooh ooh.", "I have the parts for that. Almost.", "Genius. Pure genius."), rng)
        "cooking" -> Dialogue.pick(listOf("Now I'm hungry.", "I can make that. I think.", "So fluffy."), rng)
        "wildlife" -> Dialogue.pick(listOf("Nature is wild.", "I want to be friends with it.", "Look at it go!"), rng)
        else -> Dialogue.pick(listOf("Bop bop bop.", "I'm dancing inside.", "This one's stuck in my head now."), rng)
    }
}

/* ================================================================== */
/*  Football                                                           */
/* ================================================================== */

data class FieldSession(val story: List<String>, val newRecord: Boolean)

object FootballLife {
    /** Kick-ups in his room: practice, energy and a bit of luck. Returns the count. */
    fun kickups(s: PipoState, rng: Random, now: Long): Int {
        val practice = (s.counters["act:PLAY_TOY"] ?: 0).coerceAtMost(60)
        val skill = 2f + practice * 0.18f + s.profile.traits.playfulness * 4f + s.mood.energy * 3f + (if (now < (s.cooldowns["practice"] ?: 0L)) 2f else 0f)
        return (skill * (0.3f + rng.nextFloat() * 0.9f)).toInt().coerceAtLeast(0)
    }

    /** @return true if it was a new personal best (remembered, told). */
    fun recordKickups(s: PipoState, n: Int, now: Long): Boolean {
        val best = s.records["kickups"] ?: 0
        if (n <= best || n < 3) return false
        s.records["kickups"] = n
        Chronicle.remember(s, MemoryType.SELF, "my best is $n kick-ups", 0.5f + (n.coerceAtMost(40) / 100f), now, "record:kickups")
        if (best == 0 || n >= best + 3) Chronicle.journal(s, "New record: $n kick-ups", "He counted out loud. Nib counted too, in beeps.", JournalCategory.GAME, now)
        return true
    }

    fun fieldSession(s: PipoState, rng: Random, now: Long): FieldSession {
        val story = mutableListOf<String>()
        val shot = (8 + rng.nextInt(10) + ((s.counters["trips:field"] ?: 0).coerceAtMost(12)) + (s.profile.traits.confidence * 6).toInt())
        s.count("trips:field")
        val best = s.records["shot"] ?: 0
        val goals = rng.nextInt(4)
        story += when (goals) { 0 -> "Didn't score. The goal moved. He's sure of it."; 1 -> "Scored once. Celebrated for five minutes."; else -> "Scored $goals goals. He did a lap for each one." }
        var rec = false
        if (shot > best) {
            s.records["shot"] = shot
            rec = true
            story += "Longest shot ever: $shot steps. \"That was my best shot.\""
            Chronicle.remember(s, MemoryType.SELF, "my best shot was $shot steps", 0.55f, now, "record:shot")
        }
        if (goals > (s.records["goals"] ?: 0)) s.records["goals"] = goals
        s.world.objectStates["ball_door"] = "1"
        return FieldSession(story, rec)
    }
}
