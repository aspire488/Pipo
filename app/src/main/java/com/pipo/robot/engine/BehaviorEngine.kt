package com.pipo.robot.engine

import com.pipo.robot.data.ActivityType
import com.pipo.robot.data.ActivityType.*
import com.pipo.robot.data.Catalog
import com.pipo.robot.data.EventType
import com.pipo.robot.data.JournalCategory
import com.pipo.robot.data.MemoryType
import com.pipo.robot.data.Mood
import com.pipo.robot.data.OwnedItem
import com.pipo.robot.data.PipoProject
import com.pipo.robot.data.PipoState
import com.pipo.robot.data.ProjectState
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

sealed class Outcome {
    data class Say(val text: String, val sfx: Sfx? = null) : Outcome()
    data class Emote(val kind: EmoteKind) : Outcome()
    data class Animate(val anim: AnimState, val ms: Long, val expr: Expr? = null) : Outcome()
    data class Found(val item: OwnedItem) : Outcome()
    data class Finished(val project: PipoProject) : Outcome()
    data class Prank(val key: String) : Outcome()
    data class Offer(val kind: OfferKind, val text: String, val payload: String = "") : Outcome()
}

enum class OfferKind { PLAY_GAME, SURPRISE, THOUGHT }

/** Something that pulls Pipo's attention away from what he was doing. */
enum class DistractionKind { BALL, CRITTER, NOISE, SHINY, THOUGHT }

/** Which room object a "suggest:<id>" memory refers to → the activity it nudges him towards. */
internal fun suggestedActivity(obj: String): List<ActivityType> = when (obj) {
    "plant" -> listOf(INSPECT_PLANT)
    "desk" -> listOf(READ, WORK_COMPUTER)
    "arcade" -> listOf(PLAY_ARCADE)
    "toys" -> listOf(PLAY_TOY)
    "workbench" -> listOf(BUILD, EXPERIMENT)
    "window" -> listOf(THINK)
    "shelf" -> listOf(EXAMINE)
    "charger" -> listOf(CHARGE)
    else -> emptyList()
}

object BehaviorEngine {

    data class Scored(val type: ActivityType, val score: Float)

    /**
     * Candidate generation + scoring. Pure function of state, env and time (no randomness here).
     */
    fun score(s: PipoState, env: Env, now: Long): List<Scored> {
        val t = s.profile.traits
        val m = s.mood
        val mood = s.mood.current
        val night = isNight(env.hour)
        val hasItems = s.world.items.isNotEmpty()
        val project = s.activeProject()
        val canBuild = project?.state == ProjectState.BUILDING

        val out = mutableListOf<Scored>()
        fun add(a: ActivityType, v: Float) { out.add(Scored(a, max(0f, v))) }

        add(SLEEP, if (m.energy < 0.35f || night) (1f - m.energy) * 2.0f + t.laziness * 0.5f + (if (night) 1.1f else 0f) else 0f)
        add(REST, t.laziness * 0.8f + (1f - m.energy) * 0.7f)
        add(CHARGE, if (env.charging) 2.2f + m.excitement else (1f - m.energy) * 0.6f)
        add(EXPLORE, t.curiosity * 1.1f + t.adventurousness * 0.7f + m.boredom * 0.5f + m.energy * 0.3f)
        add(PLAY_TOY, t.playfulness * 0.9f + m.boredom * 0.8f)
        add(PLAY_ARCADE, t.playfulness * 0.7f + m.boredom * 0.9f + m.energy * 0.2f)
        add(BUILD, if (canBuild) 1.2f + t.confidence * 0.4f + t.patience * 0.4f else 0f)
        add(EXPERIMENT, if (hasItems) t.curiosity * 0.6f + t.mischief * 0.4f else 0f)
        add(EXAMINE, if (hasItems) t.curiosity * 0.5f + 0.15f else 0f)
        add(REARRANGE, t.mischief * 0.7f + t.stubbornness * 0.2f + m.boredom * 0.3f)
        add(READ, (1f - t.adventurousness) * 0.5f + t.patience * 0.4f + t.curiosity * 0.3f)
        add(THINK, 0.45f + t.patience * 0.3f + (if (project == null && hasItems) 0.35f else 0f))
        add(WORK_COMPUTER, t.curiosity * 0.5f + 0.25f)
        add(INSPECT_PLANT, 0.35f + t.affection * 0.3f)
        add(DANCE, if (env.music) 2.6f + t.playfulness else t.playfulness * 0.35f + m.happiness * 0.3f)
        add(PREPARE_SURPRISE, if (s.world.items.size >= 2) t.mischief * 0.5f + t.affection * 0.6f else 0f)
        add(SEEK_USER, if (env.userPresent) t.sociability * 1.0f + m.loneliness * 0.8f + t.affection * 0.4f else 0f)
        add(NOTHING, t.laziness * 0.6f + 0.2f)

        // ---- context: what's going on in his life right now
        val bonus = mutableMapOf<ActivityType, Float>()
        fun plus(a: ActivityType, v: Float) { bonus[a] = (bonus[a] ?: 0f) + v }
        // unfinished projects pull him back; missing parts send him exploring
        if (project?.state == ProjectState.GATHERING && Discovery.neededTags(s).isNotEmpty()) plus(EXPLORE, 0.5f)
        if (project == null && Projects.retryCandidate(s, now) != null) { plus(THINK, 0.35f + t.stubbornness * 0.3f); plus(WORK_COMPUTER, 0.2f) }
        // time of day
        when (env.hour) {
            in 6..10 -> { plus(INSPECT_PLANT, 0.3f); plus(THINK, 0.2f) }
            in 18..22 -> { plus(READ, 0.35f); plus(REST, 0.2f) }
        }
        // the user: just saw them → less need to seek them out, more urge to show off
        val sinceUser = now - s.lastUserInteractionAt
        if (env.userPresent && sinceUser < 2 * MINUTE) {
            plus(SEEK_USER, -0.5f)
            if (m.happiness > 0.6f) { plus(DANCE, 0.2f); plus(PLAY_TOY, 0.15f) }
        }
        // the relationship shows up as wanting to be near you — never as a number
        if (env.userPresent) plus(SEEK_USER, s.profile.relationship * 0.5f)
        // memories: things you encouraged, games you play together
        for (mem in s.memories) {
            if (mem.key.startsWith("suggest:")) suggestedActivity(mem.key.removePrefix("suggest:")).forEach { plus(it, min(mem.count, 5) * 0.07f) }
        }
        val gamesTogether = s.games.values.sumOf { it.plays }
        if (gamesTogether > 0) plus(PLAY_ARCADE, min(gamesTogether, 10) * 0.02f) // practising
        for (i in out.indices) bonus[out[i].type]?.let { out[i] = Scored(out[i].type, max(0f, out[i].score + it)) }

        // variety: recently repeated activities lose their appeal (sleep and real work excepted)
        val recent = s.recentActivities.takeLast(5)

        // Mood colours every decision.
        return out.map { c ->
            val mult = when (mood) {
                Mood.SLEEPY -> when (c.type) { SLEEP, REST, CHARGE, NOTHING -> 2f; EXPLORE, DANCE, PLAY_ARCADE -> 0.4f; else -> 0.8f }
                Mood.EXCITED -> when (c.type) { DANCE, PLAY_TOY, PLAY_ARCADE, EXPLORE, SEEK_USER -> 1.5f; SLEEP, REST -> 0.3f; else -> 1f }
                Mood.GRUMPY -> when (c.type) { NOTHING, REST, READ -> 1.5f; SEEK_USER -> 0.3f; DANCE -> 0.4f; else -> 1f }
                Mood.LONELY -> when (c.type) { SEEK_USER -> 2f; THINK, INSPECT_PLANT -> 1.3f; else -> 1f }
                Mood.BORED -> when (c.type) { PLAY_TOY, PLAY_ARCADE, EXPLORE, REARRANGE -> 1.5f; NOTHING -> 0.6f; else -> 1f }
                Mood.CURIOUS -> when (c.type) { EXPLORE, EXAMINE, EXPERIMENT, WORK_COMPUTER -> 1.5f; else -> 1f }
                Mood.MISCHIEVOUS -> when (c.type) { REARRANGE, PREPARE_SURPRISE, EXPERIMENT -> 1.7f; else -> 1f }
                Mood.PROUD -> when (c.type) { SEEK_USER, DANCE -> 1.4f; else -> 1f }
                else -> 1f
            }
            val cool = s.cooldowns["act:${c.type.name}"] ?: 0L
            val repeats = if (c.type == SLEEP || c.type == BUILD) 0 else recent.count { it == c.type }
            val variety = 0.6f.pow(repeats)
            Scored(c.type, if (now < cool) 0f else c.score * mult * variety)
        }
    }

    /** Pick one: controlled randomness among the top candidates. */
    fun choose(s: PipoState, env: Env, now: Long, rng: Random, last: ActivityType?): ActivityType {
        val scored = score(s, env, now)
            .map { if (it.type == last && it.type != SLEEP && it.type != BUILD) it.copy(score = it.score * 0.3f) else it }
            .map { it.copy(score = it.score * (0.85f + rng.nextFloat() * 0.3f)) }
            .filter { it.score > 0f }
            .sortedByDescending { it.score }
            .take(3)
        if (scored.isEmpty()) return NOTHING
        val w = scored.map { it.score * it.score }
        var r = rng.nextFloat() * w.sum()
        for (i in scored.indices) { r -= w[i]; if (r <= 0f) return scored[i].type }
        return scored.first().type
    }

    fun durationMs(type: ActivityType, s: PipoState, rng: Random): Long {
        val t = s.profile.traits
        val base = when (type) {
            SLEEP -> 60_000L + (t.laziness * 60_000).toLong()
            REST, NOTHING -> 10_000L + (t.laziness * 14_000).toLong()
            CHARGE -> 25_000L
            EXPLORE -> 12_000L
            PLAY_TOY, PLAY_ARCADE -> 14_000L
            BUILD, EXPERIMENT -> 16_000L
            READ, WORK_COMPUTER -> 15_000L
            THINK, INSPECT_PLANT, EXAMINE -> 9_000L
            DANCE -> 12_000L
            PREPARE_SURPRISE -> 12_000L
            REARRANGE -> 8_000L
            SEEK_USER -> 4_000L
        }
        return (base * (0.75f + rng.nextFloat() * 0.5f)).toLong()
    }

    private val absorbable = setOf(BUILD, EXPERIMENT, READ, WORK_COMPUTER, EXAMINE, INSPECT_PLANT, PLAY_ARCADE, THINK)

    fun absorbChance(s: PipoState, type: ActivityType): Float {
        if (type !in absorbable) return 0f
        val t = s.profile.traits
        return (0.1f + t.patience * 0.22f + t.curiosity * 0.08f + (if (s.mood.current == Mood.CURIOUS) 0.12f else 0f) -
            (if (s.mood.current == Mood.BORED || s.mood.current == Mood.SLEEPY) 0.08f else 0f)).coerceIn(0f, 0.5f)
    }

    fun start(s: PipoState, type: ActivityType, now: Long, rng: Random) {
        s.activity.type = type
        s.activity.startedAt = now
        s.activity.durationMs = durationMs(type, s, rng)
        s.activity.result = ""
        s.activity.absorbed = rng.nextFloat() < absorbChance(s, type)
        if (s.activity.absorbed) s.activity.durationMs = (s.activity.durationMs * 2.2f).toLong()
        s.recentActivities.add(type)
        if (s.recentActivities.size > 8) s.recentActivities.removeAt(0)
        val cd = when (type) {
            EXPLORE -> 70_000L; PREPARE_SURPRISE -> 20 * MINUTE; SEEK_USER -> 90_000L; DANCE -> 40_000L
            PLAY_ARCADE -> 50_000L; REARRANGE -> 3 * MINUTE; EXAMINE -> 60_000L; INSPECT_PLANT -> 60_000L
            else -> 0L
        }
        if (cd > 0) s.cooldowns["act:${type.name}"] = now + cd
    }

    /**
     * Resolve a finished activity: apply effects, maybe create world events, return what Pipo
     * should show/say. [offline] = simulated while the app was closed (no speech needed).
     */
    fun complete(s: PipoState, type: ActivityType, env: Env, now: Long, rng: Random, offline: Boolean): List<Outcome> {
        val t = s.profile.traits
        val out = mutableListOf<Outcome>()
        val mood = s.mood.current
        s.count("act:${type.name}")
        when (type) {
            SLEEP -> MoodEngine.bump(s, energy = if (offline) 0f else 0.35f, irritation = -0.2f)
            REST, NOTHING -> {
                MoodEngine.bump(s, energy = 0.08f)
                Personality.nudge(s, Trait.LAZINESS, 0.002f)
                if (!offline && rng.nextFloat() < 0.25f) out += Outcome.Say(Dialogue.idleLine(mood, rng), Sfx.SIGH)
            }
            CHARGE -> {
                MoodEngine.bump(s, energy = 0.25f, happiness = 0.05f)
                if (!offline && rng.nextFloat() < 0.4f) out += Outcome.Say(Dialogue.pick(Dialogue.charged, rng), Sfx.HAPPY)
            }
            EXPLORE -> {
                val chance = (if (offline) 0.10f else 0.28f) + t.curiosity * 0.18f + t.adventurousness * 0.08f
                val cdOk = now >= (s.cooldowns["discover"] ?: 0L)
                val item = if (cdOk && rng.nextFloat() < chance) Discovery.roll(s, rng, now) else null
                if (item != null) {
                    s.cooldowns["discover"] = now + if (offline) 14 * HOUR else 6 * MINUTE
                    out += Outcome.Found(item)
                } else {
                    MoodEngine.bump(s, boredom = -0.1f)
                    if (!offline && rng.nextFloat() < 0.6f) out += Outcome.Say(Dialogue.pick(Dialogue.exploreNothing, rng), Sfx.BEEP)
                }
            }
            PLAY_TOY, PLAY_ARCADE -> {
                MoodEngine.bump(s, boredom = -0.4f, happiness = 0.1f, energy = -0.05f, excitement = 0.1f)
                Personality.nudge(s, Trait.PLAYFULNESS, 0.004f)
                if (!offline && rng.nextFloat() < 0.55f) out += Outcome.Say(
                    Dialogue.pick(if (type == PLAY_ARCADE) Dialogue.arcadeDone else Dialogue.toyDone, rng), Sfx.LAUGH)
            }
            BUILD, EXPERIMENT -> {
                MoodEngine.bump(s, boredom = -0.25f, energy = -0.06f)
                val fin = Projects.work(s, rng, now, if (type == BUILD) 1f else 0.4f)
                if (fin != null) out += Outcome.Finished(fin)
                else if (type == EXPERIMENT) {
                    if (rng.nextFloat() < 0.18f) {
                        // Small funny accident.
                        out += Outcome.Animate(AnimState.FALLEN, 1600, Expr.SURPRISED)
                        out += Outcome.Say(Dialogue.pick(Dialogue.experimentOops, rng), Sfx.SURPRISED)
                        if (offline && now > (s.cooldowns["oops"] ?: 0L)) {
                            s.cooldowns["oops"] = now + 2 * DAY
                            Chronicle.journal(s, "Small explosion", "Pipo's experiment went pop. He says it was intentional.", JournalCategory.MOMENT, now)
                        }
                    } else if (!offline && rng.nextFloat() < 0.5f) out += Outcome.Say(Dialogue.pick(Dialogue.experimentLines, rng))
                } else if (!offline && rng.nextFloat() < 0.35f) {
                    out += Outcome.Say(Dialogue.buildProgress(s.activeProject(), rng))
                }
            }
            EXAMINE -> {
                val it = s.world.items.randomOrNull(rng)
                val d = it?.let { Catalog.item(it.catalogId) }
                if (d != null && !offline) out += Outcome.Say(Dialogue.examine(d, it.revealed, rng))
                MoodEngine.bump(s, curiosity = 0.05f, boredom = -0.1f)
            }
            REARRANGE -> {
                val available = Pranks.all.filter { !Pranks.isOn(s, it.key) }
                val active = Pranks.active(s)
                if (active.isNotEmpty() && rng.nextFloat() < 0.3f) {
                    val p = active.random(rng)
                    s.world.objectStates.remove("prank:${p.key}")
                    if (!offline) out += Outcome.Say(Dialogue.pick(Dialogue.cleanedUp, rng))
                } else if (available.isNotEmpty() && rng.nextFloat() < 0.35f + t.mischief * 0.4f) {
                    val p = available.random(rng)
                    s.world.objectStates["prank:${p.key}"] = "1"
                    Chronicle.journal(s, "Mischief", p.journal, JournalCategory.MISCHIEF, now)
                    Chronicle.event(s, EventType.PRANK, 0.45f + t.mischief * 0.2f, now, p.key)
                    MoodEngine.setTransient(s, Mood.MISCHIEVOUS, now, 30_000)
                    Personality.nudge(s, Trait.MISCHIEF, 0.006f)
                    out += Outcome.Prank(p.key)
                    if (!offline) out += Outcome.Say(p.line, Sfx.LAUGH)
                }
            }
            READ -> {
                MoodEngine.bump(s, boredom = -0.15f, curiosity = 0.04f, irritation = -0.1f)
                if (!offline && rng.nextFloat() < 0.5f) out += Outcome.Say(Dialogue.pick(Dialogue.readLines, rng))
            }
            THINK, WORK_COMPUTER -> {
                val p = Projects.maybeStart(s, rng, now, if (offline) 0.35f else 0.25f)
                if (p != null) {
                    out += Outcome.Emote(EmoteKind.IDEA)
                    val def = Catalog.project(p.templateId)
                    if (!offline) out += Outcome.Say(
                        if (p.attempts > 0) "Okay. The ${p.title.lowercase()}. Again. This time I know what went wrong."
                        else def?.idea ?: "I have an idea.", Sfx.SURPRISED)
                    else Chronicle.event(s, EventType.THOUGHT, 0.42f + t.sociability * 0.15f, now, "idea:${p.title}")
                } else if (!offline && rng.nextFloat() < 0.55f) {
                    out += Outcome.Say(if (type == THINK) Dialogue.thought(s, rng) else Dialogue.pick(Dialogue.computerLines, rng))
                } else if (offline && rng.nextFloat() < 0.08f) {
                    Chronicle.event(s, EventType.THOUGHT, 0.3f + t.sociability * 0.15f, now, Dialogue.thought(s, rng))
                }
            }
            INSPECT_PLANT -> {
                if (!offline && rng.nextFloat() < 0.6f) out += Outcome.Say(Dialogue.pick(Dialogue.plantLines, rng))
                MoodEngine.bump(s, irritation = -0.1f, happiness = 0.03f)
            }
            DANCE -> {
                MoodEngine.bump(s, happiness = 0.12f, boredom = -0.3f, energy = -0.05f)
                if (!offline && rng.nextFloat() < 0.4f) out += Outcome.Say(Dialogue.pick(Dialogue.danceLines, rng), Sfx.HAPPY)
            }
            PREPARE_SURPRISE -> {
                val n = (s.world.objectStates["drawings"]?.toIntOrNull() ?: 0)
                if (n < 4 && now > (s.cooldowns["surprise"] ?: 0L) && rng.nextFloat() < 0.6f) {
                    s.cooldowns["surprise"] = now + 2 * DAY
                    s.world.objectStates["drawings"] = (n + 1).toString()
                    Chronicle.journal(s, "Pipo made you something", "A drawing of you. It's on the wall above his bed. The ears are... a choice.", JournalCategory.MOMENT, now)
                    Chronicle.remember(s, MemoryType.MOMENT, "I drew a picture of my human", 0.7f, now, "drawing:${n + 1}")
                    Chronicle.event(s, EventType.SURPRISE, 0.6f, now, "drawing")
                    out += Outcome.Offer(OfferKind.SURPRISE, "I made you something. It's on the wall. Don't look at the ears.")
                }
            }
            SEEK_USER -> {
                out += seekUser(s, rng, now)
            }
        }
        if (!offline) Discovery.updateReveals(s, now)
        return out
    }

    /** Pipo comes over to the user with something on his mind. */
    private fun seekUser(s: PipoState, rng: Random, now: Long): List<Outcome> {
        val t = s.profile.traits
        val m = s.mood
        // Wants to play: bored + playful. Prefers the game the user plays most.
        if (m.boredom > 0.35f && rng.nextFloat() < t.playfulness) {
            val game = favoriteGame(s) ?: listOf("rps", "memory", "reaction", "tictactoe").random(rng)
            return listOf(Outcome.Offer(OfferKind.PLAY_GAME, Dialogue.wantPlay(game, s, rng), game))
        }
        // Bring back an old memory / inside joke
        val mem = Chronicle.recall(s, rng, now, setOf(MemoryType.JOKE, MemoryType.GAME, MemoryType.MOMENT, MemoryType.USER_FACT))
        if (mem != null && rng.nextFloat() < 0.5f) return listOf(Outcome.Say(Dialogue.callback(mem, s, rng), Sfx.BEEP))
        return listOf(Outcome.Say(Dialogue.thought(s, rng), Sfx.BEEP))
    }

    /**
     * While doing something, Pipo might get distracted. Absorbed Pipo never does; an impatient,
     * curious or bored one does more. Pure except for the cooldown and counter it sets.
     */
    fun distraction(s: PipoState, type: ActivityType, env: Env, rng: Random, now: Long): DistractionKind? {
        if (type == SLEEP || type == SEEK_USER || type == CHARGE || s.activity.absorbed) return null
        if (now < (s.cooldowns["distract"] ?: 0L)) return null
        val t = s.profile.traits
        val mood = s.mood.current
        val chance = 0.05f + (1f - t.patience) * 0.1f + t.curiosity * 0.05f +
            (if (mood == Mood.CURIOUS || mood == Mood.BORED) 0.06f else 0f) - (if (mood == Mood.SLEEPY) 0.04f else 0f)
        if (rng.nextFloat() >= chance) return null
        s.cooldowns["distract"] = now + 3 * MINUTE
        s.count("distracted")
        val evening = env.hour >= 20 || env.hour < 6
        val w = linkedMapOf(
            DistractionKind.BALL to 1f,
            DistractionKind.CRITTER to if (evening) 1.3f else 0.7f,
            DistractionKind.NOISE to 0.8f,
            DistractionKind.SHINY to 0.3f + t.curiosity * 0.4f,
            DistractionKind.THOUGHT to 0.6f,
        )
        var r = rng.nextFloat() * w.values.sum()
        for ((k, v) in w) { r -= v; if (r <= 0f) return k }
        return DistractionKind.THOUGHT
    }

    /** Chasing a distraction sometimes leads somewhere. Respects the normal discovery cooldown. */
    fun stumble(s: PipoState, rng: Random, now: Long): OwnedItem? {
        if (now < (s.cooldowns["discover"] ?: 0L) || rng.nextFloat() > 0.5f + s.profile.traits.curiosity * 0.2f) return null
        val item = Discovery.roll(s, rng, now) ?: return null
        s.cooldowns["discover"] = now + 6 * MINUTE
        Chronicle.remember(s, MemoryType.DISCOVERY, "I got distracted and found something anyway", 0.45f, now, "stumble")
        return item
    }

    fun favoriteGame(s: PipoState): String? =
        s.games.maxByOrNull { it.value.plays }?.takeIf { it.value.plays >= 2 }?.key

    /** Idle "life" chosen when Pipo has nothing queued (used for greetings/offline too). */
    fun describe(type: ActivityType): String = when (type) {
        SLEEP -> "sleeping"; REST -> "resting"; CHARGE -> "charging"; EXPLORE -> "exploring"
        PLAY_TOY -> "playing with his ball"; PLAY_ARCADE -> "playing arcade games"; EXPERIMENT -> "experimenting"
        BUILD -> "building"; EXAMINE -> "examining his collection"; REARRANGE -> "rearranging things"
        READ -> "reading"; THINK -> "thinking"; WORK_COMPUTER -> "on the computer"; INSPECT_PLANT -> "checking on the plant"
        DANCE -> "dancing"; PREPARE_SURPRISE -> "hiding something"; SEEK_USER -> "looking for you"; NOTHING -> "doing nothing"
    }
}
