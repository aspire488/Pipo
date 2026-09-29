package com.pipo.robot.engine

import com.pipo.robot.data.EventType
import com.pipo.robot.data.JournalCategory
import com.pipo.robot.data.JournalEntry
import com.pipo.robot.data.MemoryType
import com.pipo.robot.data.Mood
import com.pipo.robot.data.PendingEvent
import com.pipo.robot.data.PipoMemory
import com.pipo.robot.data.PipoState
import com.pipo.robot.data.Traits
import java.util.Calendar
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

internal fun clamp01(v: Float) = v.coerceIn(0f, 1f)

const val HOUR = 3_600_000L
const val MINUTE = 60_000L
const val DAY = 24 * HOUR

fun hourOf(time: Long): Int = Calendar.getInstance().apply { timeInMillis = time }.get(Calendar.HOUR_OF_DAY)

fun isNight(hour: Int) = hour >= 23 || hour < 6

/* ================================================================== */
/*  Personality                                                        */
/* ================================================================== */

enum class Trait { CURIOSITY, PLAYFULNESS, MISCHIEF, AFFECTION, CONFIDENCE, SOCIABILITY, PATIENCE, LAZINESS, ADVENTUROUSNESS, STUBBORNNESS }

object Personality {
    /** Every Pipo is a little different. */
    fun newTraits(rng: Random): Traits {
        fun j(base: Float) = (base + (rng.nextFloat() - 0.5f) * 0.3f).coerceIn(0.15f, 0.85f)
        return Traits(
            curiosity = j(0.62f), playfulness = j(0.6f), mischief = j(0.42f), affection = j(0.6f),
            confidence = j(0.5f), sociability = j(0.55f), patience = j(0.48f), laziness = j(0.4f),
            adventurousness = j(0.52f), stubbornness = j(0.4f),
        )
    }

    fun get(t: Traits, trait: Trait): Float = when (trait) {
        Trait.CURIOSITY -> t.curiosity
        Trait.PLAYFULNESS -> t.playfulness
        Trait.MISCHIEF -> t.mischief
        Trait.AFFECTION -> t.affection
        Trait.CONFIDENCE -> t.confidence
        Trait.SOCIABILITY -> t.sociability
        Trait.PATIENCE -> t.patience
        Trait.LAZINESS -> t.laziness
        Trait.ADVENTUROUSNESS -> t.adventurousness
        Trait.STUBBORNNESS -> t.stubbornness
    }

    /** Traits evolve slowly. Deltas are tiny and bounded so Pipo never becomes a caricature. */
    fun nudge(s: PipoState, trait: Trait, delta: Float) {
        val t = s.profile.traits
        val v = (get(t, trait) + delta * 0.5f).coerceIn(0.05f, 0.95f)
        when (trait) {
            Trait.CURIOSITY -> t.curiosity = v
            Trait.PLAYFULNESS -> t.playfulness = v
            Trait.MISCHIEF -> t.mischief = v
            Trait.AFFECTION -> t.affection = v
            Trait.CONFIDENCE -> t.confidence = v
            Trait.SOCIABILITY -> t.sociability = v
            Trait.PATIENCE -> t.patience = v
            Trait.LAZINESS -> t.laziness = v
            Trait.ADVENTUROUSNESS -> t.adventurousness = v
            Trait.STUBBORNNESS -> t.stubbornness = v
        }
    }

    /** "Shy" is not a stored trait — it emerges from low sociability + low confidence. */
    fun isShy(t: Traits) = t.sociability < 0.4f && t.confidence < 0.5f
}

/* ================================================================== */
/*  Mood                                                               */
/* ================================================================== */

data class Env(
    val hour: Int = 12,
    val charging: Boolean = false,
    val battery: Int = 80,
    val music: Boolean = false,
    val headphones: Boolean = false,
    val userPresent: Boolean = false,
)

object MoodEngine {
    /**
     * Passive drift. [timeScale] > 1 speeds up drift while the app is open so the user can
     * actually see Pipo get tired or bored during a session.
     */
    fun tick(s: PipoState, dtMs: Long, env: Env, sleeping: Boolean, charging: Boolean, playing: Boolean, timeScale: Float = 1f) {
        val h = dtMs / HOUR.toFloat() * timeScale
        val m = s.mood
        val t = s.profile.traits
        val night = isNight(env.hour)

        m.energy = clamp01(
            m.energy + when {
                sleeping -> 0.30f * h
                charging -> 0.18f * h
                else -> -(0.05f + (1f - t.laziness) * 0.02f + if (night) 0.05f else 0f) * h * (if (playing) 1.8f else 1f)
            }
        )
        m.boredom = clamp01(
            m.boredom + if (playing || env.userPresent) -0.4f * h else if (sleeping) -0.05f * h else 0.07f * h * (0.5f + t.curiosity)
        )
        // Loneliness grows slowly while the user is away, but is capped: Pipo never guilt-trips.
        m.loneliness = if (env.userPresent) clamp01(m.loneliness - 1.5f * h)
        else min(0.7f, m.loneliness + 0.03f * h * (0.4f + t.sociability))
        m.irritation = clamp01(m.irritation - 0.9f * h)
        m.excitement += (0.25f - m.excitement) * min(1f, 1.2f * h)
        m.curiosity += (t.curiosity - m.curiosity) * min(1f, 0.3f * h)
        val happyBase = 0.5f + t.affection * 0.15f + s.profile.relationship * 0.15f
        m.happiness += (happyBase - m.happiness) * min(1f, 0.25f * h)
        m.affection += (0.35f + s.profile.relationship * 0.5f - m.affection) * min(1f, 0.05f * h)
        m.happiness = clamp01(m.happiness); m.excitement = clamp01(m.excitement); m.curiosity = clamp01(m.curiosity)
        m.affection = clamp01(m.affection)
    }

    fun setTransient(s: PipoState, mood: Mood, now: Long, durationMs: Long) {
        s.mood.transient = mood
        s.mood.transientUntil = now + durationMs
        s.mood.current = mood
    }

    fun derive(s: PipoState, now: Long, hour: Int): Mood {
        val m = s.mood
        val t = s.profile.traits
        val tr = m.transient
        if (tr != null && now < m.transientUntil) {
            m.current = tr
            return tr
        }
        m.transient = null
        val scores = linkedMapOf(
            Mood.SLEEPY to (1f - m.energy).pow(1.5f) * 1.4f + (if (isNight(hour)) 0.3f else 0f),
            Mood.GRUMPY to m.irritation * 1.4f,
            Mood.EXCITED to m.excitement * 1.2f,
            Mood.LONELY to m.loneliness * 1.05f,
            Mood.BORED to m.boredom * 1.05f,
            Mood.MISCHIEVOUS to t.mischief * 0.6f + m.happiness * 0.25f + m.boredom * 0.25f,
            Mood.CURIOUS to m.curiosity * 0.92f,
            Mood.HAPPY to m.happiness * 0.95f,
            Mood.RELAXED to 0.55f,
        )
        val best = scores.maxByOrNull { it.value }!!.key
        m.current = best
        return best
    }

    // Convenience deltas used by interactions
    fun bump(s: PipoState, happiness: Float = 0f, energy: Float = 0f, boredom: Float = 0f, affection: Float = 0f,
             irritation: Float = 0f, loneliness: Float = 0f, excitement: Float = 0f, curiosity: Float = 0f) {
        val m = s.mood
        m.happiness = clamp01(m.happiness + happiness)
        m.energy = clamp01(m.energy + energy)
        m.boredom = clamp01(m.boredom + boredom)
        m.affection = clamp01(m.affection + affection)
        m.irritation = clamp01(m.irritation + irritation)
        m.loneliness = clamp01(m.loneliness + loneliness)
        m.excitement = clamp01(m.excitement + excitement)
        m.curiosity = clamp01(m.curiosity + curiosity)
    }
}

/* ================================================================== */
/*  Memory, journal, events                                            */
/* ================================================================== */

object Chronicle {
    private const val MAX_MEMORIES = 120
    private const val MAX_JOURNAL = 400

    fun remember(s: PipoState, type: MemoryType, content: String, importance: Float, now: Long, key: String = ""): PipoMemory {
        if (key.isNotEmpty()) {
            val existing = s.memories.firstOrNull { it.key == key }
            if (existing != null) {
                existing.count += 1
                existing.content = content
                existing.importance = min(1f, max(existing.importance, importance) + 0.04f)
                existing.timestamp = now
                return existing
            }
        }
        val mem = PipoMemory(s.nextId(), type, content, clamp01(importance), now, 0L, key)
        s.memories.add(mem)
        prune(s, now)
        return mem
    }

    private fun halfLifeDays(type: MemoryType): Float = when (type) {
        MemoryType.USER_FACT -> 3650f
        MemoryType.JOKE -> 60f
        MemoryType.MOMENT, MemoryType.PROJECT -> 120f
        MemoryType.DISCOVERY -> 90f
        MemoryType.GAME -> 45f
        MemoryType.CONVERSATION -> 7f
        MemoryType.EVENT -> 20f
        MemoryType.SELF -> 60f
    }

    fun relevance(m: PipoMemory, now: Long): Float {
        val ageDays = (now - m.timestamp).coerceAtLeast(0) / DAY.toFloat()
        val decay = exp(-ageDays * 0.693f / halfLifeDays(m.type))
        return m.importance * decay + 0.05f * min(m.count, 6)
    }

    fun prune(s: PipoState, now: Long) {
        s.memories.removeAll { it.type == MemoryType.CONVERSATION && relevance(it, now) < 0.08f }
        if (s.memories.size > MAX_MEMORIES) {
            val keep = s.memories.sortedByDescending { relevance(it, now) }.take(MAX_MEMORIES).map { it.id }.toSet()
            s.memories.removeAll { it.id !in keep }
        }
    }

    /** Pick a memory to bring up. Recently referenced memories are avoided. */
    fun recall(s: PipoState, rng: Random, now: Long, types: Set<MemoryType>? = null): PipoMemory? {
        val pool = s.memories.filter { (types == null || it.type in types) && now - it.lastReferenced > 6 * HOUR }
        if (pool.isEmpty()) return null
        val weights = pool.map { relevance(it, now).coerceAtLeast(0.01f) }
        var r = rng.nextFloat() * weights.sum()
        for (i in pool.indices) {
            r -= weights[i]
            if (r <= 0f) return pool[i].also { it.lastReferenced = now }
        }
        return pool.last().also { it.lastReferenced = now }
    }

    fun journal(s: PipoState, title: String, description: String, cat: JournalCategory, now: Long) {
        s.journal.add(JournalEntry(s.nextId(), now, title, description, cat))
        if (s.journal.size > MAX_JOURNAL) s.journal.subList(0, s.journal.size - MAX_JOURNAL).clear()
    }

    fun event(s: PipoState, type: EventType, importance: Float, now: Long, payload: String = ""): PendingEvent {
        val e = PendingEvent(s.nextId(), type, clamp01(importance), now, payload, s.mood.current)
        s.events.add(e)
        // Old unshared events fade away — Pipo doesn't hoard grievances.
        s.events.removeAll { now - it.createdAt > 36 * HOUR && (it.shownInApp || it.notified) }
        s.events.removeAll { now - it.createdAt > 4 * DAY }
        if (s.events.size > 30) s.events.subList(0, s.events.size - 30).clear()
        return e
    }
}
