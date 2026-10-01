package com.pipo.robot.engine

import com.pipo.robot.data.ActivityType
import com.pipo.robot.data.Catalog
import com.pipo.robot.data.DrawSubject
import com.pipo.robot.data.EventType
import com.pipo.robot.data.Foods
import com.pipo.robot.data.Mood
import com.pipo.robot.data.PendingEvent
import com.pipo.robot.data.PipoState
import com.pipo.robot.data.ProjectState
import kotlin.math.max
import kotlin.random.Random

/**
 * Pipo's life while the app is closed. Nothing runs continuously: when the app (or the
 * periodic worker) wakes up, we replay the elapsed time in coarse steps using the same
 * behavior engine, then persist.
 */
object Simulator {
    const val STEP = 40 * MINUTE
    private const val MAX_CATCH_UP = 3 * DAY

    data class Report(val steps: Int, val discoveries: Int, val finishedProjects: Int)

    /** One line for the "while you were gone" recap, or null if the outcome isn't worth telling. */
    fun digestOf(s: PipoState, o: Outcome): String? = when (o) {
        is Outcome.Found -> Catalog.item(o.item.catalogId)?.let { "found ${article(it.name)} ${it.name.lowercase()}" }
        is Outcome.Finished -> Catalog.project(o.project.templateId)?.let { d ->
            when (o.project.state) {
                ProjectState.DONE -> "finished the ${d.title.lowercase()}"
                ProjectState.EVOLVED -> "accidentally turned the ${d.title.lowercase()} into ${article(d.evolvedTitle)} ${d.evolvedTitle.lowercase()}"
                else -> "broke the ${d.title.lowercase()}"
            }
        }
        is Outcome.Prank -> Pranks.all.firstOrNull { it.key == o.key }?.digest
        is Outcome.Emote -> if (o.kind == EmoteKind.IDEA) s.activeProject()?.let { "had an idea. ${article(it.title).replaceFirstChar { c -> c.uppercase() }} ${it.title.lowercase()}" } else null
        is Outcome.Returned -> Trips.digest(o.report, s)
        is Outcome.Cooked -> Foods.byId(o.dishId)?.let { if (o.ok) "cooked ${it.name}" else "made ${it.name}. Sort of" }
        is Outcome.Drew -> "drew " + when (o.drawing.subject) {
            DrawSubject.USER -> "you"; DrawSubject.PET -> "Nib"; DrawSubject.SELF -> "myself"; DrawSubject.BUILDING -> "something from a dream"
            else -> o.drawing.caption.substringBefore('.').replaceFirstChar { it.lowercase() }.take(40)
        }
        is Outcome.Strange -> "saw something I can't explain"
        else -> null
    }

    /** "Nothing happened" lines: only true while nothing else did. */
    private val quietLines = setOf("stared out the window for a really long time", "slept. A lot. It was great", "did absolutely nothing. On purpose")

    fun noteAway(s: PipoState, line: String) {
        // a real event makes any earlier "nothing happened" untrue
        if (line !in quietLines) s.awayLog.removeAll { it in quietLines }
        if (line in s.awayLog) return
        s.awayLog.add(line)
        if (s.awayLog.size > 5) s.awayLog.removeAt(0)
    }

    private fun envAt(s: PipoState, t: Long) = Env(hour = hourOf(t), userPresent = false, weather = WeatherEngine.at(s.seed, t))

    /** He gets home from a trip at [at]. */
    private fun comeHome(s: PipoState, at: Long, rng: Random): List<Outcome> {
        val outs = BehaviorEngine.complete(s, ActivityType.GO_OUT, envAt(s, at), at, rng, offline = true)
        s.activity.type = ActivityType.REST
        for (o in outs) digestOf(s, o)?.let { noteAway(s, it) }
        return outs
    }

    fun catchUp(s: PipoState, now: Long, rng: Random): Report {
        if (s.lastSimulatedAt <= 0L) { s.lastSimulatedAt = now; return Report(0, 0, 0) }
        var t = max(s.lastSimulatedAt, now - MAX_CATCH_UP)
        var steps = 0; var found = 0; var finished = 0; var naps = 0; var windowTime = 0
        PetEngine.maybeArrive(s, now)?.let { noteAway(s, "met Nib. Nib lives here now") }
        while (t + STEP <= now) {
            val env = envAt(s, t)
            val hour = env.hour
            val cur = s.activity.type
            MoodEngine.tick(s, STEP, env, sleeping = cur == ActivityType.SLEEP, charging = cur == ActivityType.CHARGE,
                playing = cur == ActivityType.PLAY_ARCADE || cur == ActivityType.PLAY_TOY || cur == ActivityType.GO_OUT)
            MoodEngine.derive(s, t, hour)
            // Nib has a life too, and doesn't wait for anyone
            PetEngine.offlineStep(s, env, t, rng)?.let { noteAway(s, it) }
            // out and about: nothing else happens at home until he's back
            val trip = s.trip
            if (trip != null) {
                if (trip.endsAt <= t + STEP) found += comeHome(s, max(trip.endsAt, t), rng).count { it is Outcome.Returned && it.report.foundItemIds.isNotEmpty() }
                t += STEP; steps++
                continue
            }
            // 3 a.m., awake for a moment: very occasionally, the hills blink back
            if (isNight(hour)) Mystery.onNightWindow(s, env, t, rng, 0.006f)?.let { noteAway(s, "saw something I can't explain") }
            val type = when {
                isNight(hour) && rng.nextFloat() < 0.92f -> ActivityType.SLEEP
                else -> BehaviorEngine.choose(s, env, t, rng, cur)
            }
            val started = BehaviorEngine.start(s, type, t, rng, env, inApp = false)
            if (started == ActivityType.GO_OUT) {
                val tr = s.trip!!
                if (tr.endsAt <= t + STEP) found += comeHome(s, tr.endsAt, rng).count { it is Outcome.Returned && it.report.foundItemIds.isNotEmpty() }
                t += STEP; steps++
                continue
            }
            val outcomes = BehaviorEngine.complete(s, started, env, t + STEP, rng, offline = true)
            found += outcomes.count { it is Outcome.Found }
            finished += outcomes.count { it is Outcome.Finished }
            for (o in outcomes) digestOf(s, o)?.let { noteAway(s, it) }
            if (started == ActivityType.SLEEP) naps++ else if (started == ActivityType.THINK) windowTime++
            t += STEP
            steps++
        }
        // a trip that ended in the last few minutes: he's just walked in
        s.trip?.let { if (it.endsAt <= now) comeHome(s, it.endsAt, rng) }
        // The present moment. The steps above only cover time that's fully over; what is he doing
        // *right now*? He started something in the last few minutes and hasn't finished it — which
        // is how you can open the app and find the room empty because he's at the bakery.
        if (s.trip == null && now - t >= 10 * MINUTE) {
            val env = envAt(s, t)
            val type = if (isNight(env.hour) && rng.nextFloat() < 0.92f) ActivityType.SLEEP else BehaviorEngine.choose(s, env, t, rng, s.activity.type)
            if (BehaviorEngine.start(s, type, t, rng, env, inApp = false) == ActivityType.GO_OUT) {
                s.trip?.let { if (it.endsAt <= now) comeHome(s, it.endsAt, rng) }
            }
        }
        Discovery.updateReveals(s, now)
        if (steps >= 3 && s.awayLog.isEmpty()) {
            // Nothing dramatic happened. That's also worth reporting, in his own way.
            when {
                windowTime >= 3 -> noteAway(s, "stared out the window for a really long time")
                naps >= 6 -> noteAway(s, "slept. A lot. It was great")
                else -> noteAway(s, "did absolutely nothing. On purpose")
            }
        }
        // Occasionally Pipo just wants to say hi or wants to play — but only if he actually feels it.
        val tr = s.profile.traits
        if (steps >= 6) {
            if (s.mood.loneliness > 0.45f && tr.sociability > 0.5f && rng.nextFloat() < 0.35f)
                Chronicle.event(s, EventType.HI, 0.35f + tr.sociability * 0.1f, now)
            else if (s.mood.boredom > 0.5f && tr.playfulness > 0.45f && rng.nextFloat() < 0.35f)
                Chronicle.event(s, EventType.WANT_PLAY, 0.45f, now, BehaviorEngine.favoriteGame(s) ?: "rps")
            else if (tr.mischief > 0.6f && rng.nextFloat() < 0.08f)
                Chronicle.event(s, EventType.SILENT_DOTS, 0.4f, now)
        }
        // Milestones (real ones)
        val ageDays = ((now - s.profile.createdAt) / DAY).toInt()
        for (d in listOf(7, 30, 100, 365)) {
            if (ageDays >= d && s.counters["milestone:$d"] == null) {
                s.counters["milestone:$d"] = 1
                Chronicle.journal(s, "$d days together", "Pipo has lived in this phone for $d days. He counted.", com.pipo.robot.data.JournalCategory.MILESTONE, now)
                Chronicle.event(s, EventType.MILESTONE, 0.7f, now, d.toString())
            }
        }
        s.lastSimulatedAt = if (steps > 0) t else s.lastSimulatedAt
        return Report(steps, found, finished)
    }
}

enum class GreetKind {
    FIRST_WAKE, BRIEF, SLEEPING, REVEAL_ITEM, REVEAL_PROJECT, PRANK, SURPRISE,
    RUN_TO_USER, WORKING, HAPPY, MISCHIEF_HIDE, CALM, NOTHING,
    /** Mischief: pretending to be asleep, one eye open. */
    FAKE_SLEEP,
    /** Mischief: pops in from the edge of the screen. */
    PEEK_IN,
    /** He isn't here. There's a note on the door. */
    AWAY,
    /** He's hiding somewhere in the room. Silence until you find him. */
    HIDING,
    /** Nib did something; he wants to get his version in first. */
    PET_NEWS,
    /** Something strange happened. Quiet. "Come here." */
    STRANGE,
    /** He's absorbed in something and holds up a finger. */
    BUSY,
    /** A glance, a tiny smile, and back to what he was doing. */
    CONTINUE,
}

data class Greeting(val kind: GreetKind, val line: String, val event: PendingEvent? = null)

object Greeter {
    private val revealTypes = setOf(
        EventType.DISCOVERY, EventType.PROJECT_DONE, EventType.PROJECT_EVOLVED, EventType.PROJECT_FAILED,
        EventType.PRANK, EventType.SURPRISE, EventType.REVEAL, EventType.STRANGE, EventType.PET,
    )

    fun topEvent(s: PipoState): PendingEvent? =
        s.events.filter { !it.shownInApp && it.type in revealTypes }.maxByOrNull { it.importance + it.createdAt / 1e13f }

    /**
     * [fromNotification] = the event behind the message you just tapped. That comes first, even if he
     * was asleep: he messaged you, so he wakes up and tells you what he meant.
     */
    fun plan(s: PipoState, now: Long, awayMs: Long, rng: Random, fromNotification: PendingEvent? = null): Greeting {
        if (!s.profile.firstRunDone) return Greeting(GreetKind.FIRST_WAKE, "")
        fromNotification?.let { ev ->
            if (ev.type in revealTypes && !ev.shownInApp) return forEvent(s, ev, rng)
            val idea = ev.payload.removePrefix("idea:").takeIf { ev.payload.startsWith("idea:") }
            return Greeting(GreetKind.CALM, if (idea != null) "You came! Okay. The idea: a ${idea.lowercase()}. I'm going to build it." else "You came! Okay, so. It was a good thought. I'm still thinking it.", ev)
        }
        // Out: whatever else is going on, the room is empty. You get a note, not a greeting.
        if (s.trip != null) return Greeting(GreetKind.AWAY, Trips.doorNote(s.trip!!, s.mood.current == Mood.MISCHIEVOUS))
        if (awayMs < 3 * MINUTE) return Greeting(GreetKind.BRIEF, "")
        if (s.activity.type == ActivityType.SLEEP) return Greeting(GreetKind.SLEEPING, Dialogue.pick(Dialogue.sleepMumble, rng))
        if (s.activity.type == ActivityType.HIDE) return Greeting(GreetKind.HIDING, "")
        return topEvent(s)?.let { forEvent(s, it, rng) } ?: moodGreeting(s, now, awayMs, rng)
    }

    private fun forEvent(s: PipoState, ev: PendingEvent, rng: Random): Greeting =
            when (ev.type) {
                EventType.DISCOVERY -> Greeting(GreetKind.REVEAL_ITEM, Dialogue.pick(Dialogue.foundWhileAway, rng), ev)
                EventType.REVEAL -> {
                    val item = s.world.items.firstOrNull { it.id.toString() == ev.payload }
                    val d = item?.let { Catalog.item(it.catalogId) }
                    Greeting(GreetKind.REVEAL_ITEM, if (d != null) "I figured out the ${d.name.lowercase()}." else "I figured something out.", ev)
                }
                EventType.PROJECT_DONE -> Greeting(GreetKind.REVEAL_PROJECT, "I made something.", ev)
                EventType.PROJECT_EVOLVED -> Greeting(GreetKind.REVEAL_PROJECT, "So. I made something. It's not what I planned.", ev)
                EventType.PROJECT_FAILED -> Greeting(GreetKind.REVEAL_PROJECT, "Okay. Don't look at the workbench.", ev)
                EventType.PRANK -> Greeting(GreetKind.PRANK, Dialogue.pick(Dialogue.prankGreeting, rng), ev)
                EventType.SURPRISE -> Greeting(GreetKind.SURPRISE, "Oh! You're here. Look at the wall. No — the other wall. That one.", ev)
                EventType.STRANGE -> Greeting(GreetKind.STRANGE, Dialogue.pick(listOf("...come here.", "Oh. You're here. Good. Come here.", "Can I show you something? It's weird."), rng), ev)
                EventType.PET -> Greeting(GreetKind.PET_NEWS, when (ev.payload) {
                    "arrived" -> "You're here! Okay. Don't freak out. We have a... this is Nib."
                    "sat" -> "Before you look at the workbench: it was Nib."
                    "knocked" -> "Okay. Before you look at the shelf. It was Nib. Nib did it."
                    else -> "So. Nib."
                }, ev)
                else -> Greeting(GreetKind.CALM, "Hey.", ev)
            }

    private fun moodGreeting(s: PipoState, now: Long, awayMs: Long, rng: Random): Greeting {
        val t = s.profile.traits
        val name = s.profile.userName
        val mood = s.mood.current
        return when {
            mood == Mood.MISCHIEVOUS -> {
                val r = rng.nextFloat()
                when {
                    t.mischief > 0.5f && r < 0.35f -> Greeting(GreetKind.FAKE_SLEEP, "")
                    r < 0.65f -> Greeting(GreetKind.PEEK_IN, Dialogue.pick(Dialogue.peekIn, rng))
                    else -> Greeting(GreetKind.MISCHIEF_HIDE, Dialogue.pick(Dialogue.mischiefGreeting, rng))
                }
            }
            s.activity.absorbed && s.activity.type in setOf(ActivityType.BUILD, ActivityType.DRAW, ActivityType.READ, ActivityType.COOK) ->
                Greeting(GreetKind.BUSY, Dialogue.pick(listOf("One second.", "Hold on. Almost.", "Shh. Genius at work."), rng))
            s.activity.type in setOf(ActivityType.BUILD, ActivityType.EXPERIMENT, ActivityType.WORK_COMPUTER, ActivityType.READ, ActivityType.DRAW, ActivityType.COOK) ->
                Greeting(GreetKind.WORKING, Dialogue.pick(Dialogue.workingGreeting, rng))
            // not everything is about you: sometimes he just keeps doing what he's doing
            awayMs < 2 * HOUR && s.activity.type in setOf(ActivityType.THINK, ActivityType.EAT, ActivityType.PLAY_PET, ActivityType.PLAY_TOY) && rng.nextFloat() < 0.5f ->
                Greeting(GreetKind.CONTINUE, "")
            awayMs > 8 * HOUR && (t.sociability > 0.42f || s.mood.excitement > 0.5f || s.profile.relationship > 0.35f) ->
                Greeting(GreetKind.RUN_TO_USER, if (name.isNotBlank()) "${name.uppercase()}!" else "YOU'RE BACK!")
            s.activity.type == ActivityType.NOTHING && t.laziness > 0.45f -> Greeting(GreetKind.NOTHING, "...")
            // you have a routine; he's noticed. (Only from real visit times.)
            Rituals.routineGreeting(s, hourOf(now), rng)?.let { rng.nextFloat() < 0.35f } == true ->
                Greeting(GreetKind.HAPPY, Rituals.routineGreeting(s, hourOf(now), rng)!!)
            // "our thing": a callback to something you two keep doing
            s.memories.any { it.key.startsWith("ritual:") } && rng.nextFloat() < 0.2f -> {
                val m = s.memories.filter { it.key.startsWith("ritual:") }.random(rng)
                Greeting(GreetKind.HAPPY, when (m.key) {
                    "ritual:hideseek" -> "Hi. Later: hide and seek. I found a new spot. I'm not telling you where. It's the box."
                    "ritual:games" -> "You're here. Is it game time? It feels like game time."
                    "ritual:pats" -> "Hi. My head is available. For pats. Just saying."
                    "ritual:texts" -> "You texted me when I was out. I kept all the texts."
                    else -> "Hi! It's you. Our thing's still our thing."
                })
            }
            mood == Mood.HAPPY || mood == Mood.EXCITED -> Greeting(GreetKind.HAPPY, Dialogue.pick(Dialogue.happyGreeting, rng))
            mood == Mood.SLEEPY -> Greeting(GreetKind.CALM, "Oh. Hi. I'm... awake. Mostly.")
            mood == Mood.GRUMPY -> Greeting(GreetKind.CALM, "Oh. It's you. Fine. Hi.")
            else -> Greeting(GreetKind.CALM, Dialogue.pick(Dialogue.calmGreeting, rng))
        }
    }
}
