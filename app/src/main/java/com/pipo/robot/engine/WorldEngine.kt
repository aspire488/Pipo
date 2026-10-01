package com.pipo.robot.engine

import com.pipo.robot.data.Catalog
import com.pipo.robot.data.EventType
import com.pipo.robot.data.ItemDef
import com.pipo.robot.data.JournalCategory
import com.pipo.robot.data.MemoryType
import com.pipo.robot.data.Mood
import com.pipo.robot.data.OwnedItem
import com.pipo.robot.data.PipoProject
import com.pipo.robot.data.PipoState
import com.pipo.robot.data.ProjectState
import kotlin.random.Random

data class PrankDef(val key: String, val journal: String, val line: String, val digest: String = "did something to the room")

object Pranks {
    val all = listOf(
        PrankDef("plant_hat", "Pipo put a tiny hat on the plant.", "The plant looked cold. You're welcome, plant.", "gave the plant a hat"),
        PrankDef("lamp_sock", "Pipo put a sock on the desk lamp.", "Mood lighting. Don't touch it.", "improved the lamp. With a sock"),
        PrankDef("screen_note", "Pipo stuck a note on the computer that says PIPO WAS HERE.", "Someone was here. Not me. Probably.", "left a note on the computer. Anonymously"),
        PrankDef("ball_on_bed", "Pipo tucked the ball into his bed.", "The ball was tired. I let it have my bed.", "put the ball to bed"),
        PrankDef("arcade_score", "Pipo set a new arcade high score. Possibly by unplugging it.", "I'm the arcade champion now. Officially.", "became arcade champion. Don't check how"),
        PrankDef("screw_tower", "Pipo built a tower of screws on the workbench.", "It's art. It's called Tower. Don't breathe on it.", "built a tower. Out of screws"),
        PrankDef("ball_behind_plant", "Pipo hid the ball behind the plant pot and pretended not to know where it went.", "The ball? No idea. It left. Balls do that.", "hid the ball. Somewhere. Not telling"),
        PrankDef("clock_sideways", "Pipo turned the clock sideways. He says time looks better that way.", "Time looks better sideways. Trust me.", "fixed the clock. It's sideways now"),
    )

    fun active(s: PipoState) = all.filter { s.world.objectStates["prank:${it.key}"] == "1" }
    fun isOn(s: PipoState, key: String) = s.world.objectStates["prank:$key"] == "1"
}

fun article(word: String) = if (word.first().lowercaseChar() in "aeiou") "an" else "a"

object Discovery {
    fun ownedDefs(s: PipoState): List<ItemDef> = s.world.items.mapNotNull { Catalog.item(it.catalogId) }

    /** Rolls for a discovery. Prefers things the active project needs, and things that belong where he is. */
    fun roll(s: PipoState, rng: Random, now: Long, placeTags: Set<String> = emptySet()): OwnedItem? {
        val ownedIds = s.world.items.map { it.catalogId }.toSet()
        val needed = neededTags(s)
        val pool = Catalog.items.filter { !it.shopOnly && !(it.unique && it.id in ownedIds) }
        if (pool.isEmpty()) return null
        val weights = pool.map { d ->
            var w = d.weight
            if (d.tags.any { it in needed }) w *= 3.5f
            if (d.tags.any { it in placeTags }) w *= 2f
            if (d.id in ownedIds) w *= 0.45f // prefer new things
            w
        }
        var r = rng.nextFloat() * weights.sum()
        var chosen = pool.last()
        for (i in pool.indices) {
            r -= weights[i]; if (r <= 0f) { chosen = pool[i]; break }
        }
        val item = OwnedItem(s.nextId(), chosen.id, now)
        s.world.items.add(item)
        s.activeProject()?.let { Projects.gather(s, it) } // if the project needed it, it goes straight on the bench
        Chronicle.journal(s, "Pipo found ${article(chosen.name)} ${chosen.name.lowercase()}", chosen.foundLine, JournalCategory.DISCOVERY, now)
        Chronicle.remember(s, MemoryType.DISCOVERY, "I found ${article(chosen.name)} ${chosen.name.lowercase()}", 0.5f, now, "found:${chosen.id}")
        Chronicle.event(s, EventType.DISCOVERY, 0.5f + (1f - chosen.weight.coerceAtMost(1.2f) / 1.2f) * 0.35f, now, item.id.toString())
        MoodEngine.bump(s, excitement = 0.3f, happiness = 0.08f, boredom = -0.2f, curiosity = 0.1f)
        Personality.nudge(s, Trait.CURIOSITY, 0.01f)
        Personality.nudge(s, Trait.ADVENTUROUSNESS, 0.008f)
        s.count("discoveries")
        return item
    }

    /** Hidden purposes reveal themselves over time. */
    fun updateReveals(s: PipoState, now: Long): List<OwnedItem> {
        val owned = s.world.items.map { it.catalogId }.toSet()
        val out = mutableListOf<OwnedItem>()
        for (it in s.world.items) {
            if (it.revealed) continue
            val d = Catalog.item(it.catalogId) ?: continue
            if (d.revealRequires.isNotEmpty() && d.revealRequires !in owned) continue
            if (now - it.foundAt >= d.revealAfterHours * 3 * HOUR) {
                it.revealed = true
                Chronicle.journal(s, "Pipo figured out the ${d.name.lowercase()}", d.secret, JournalCategory.DISCOVERY, now)
                Chronicle.event(s, EventType.REVEAL, 0.38f, now, it.id.toString())
                out.add(it)
            }
        }
        return out
    }

    fun neededTags(s: PipoState): Set<String> {
        val p = s.activeProject() ?: return emptySet()
        return p.components.filter { it !in p.collected }.toSet()
    }
}

object Projects {
    /** A tag you can buy somewhere (so a project needing it isn't hopeless). */
    fun purchasable(tag: String) = Catalog.items.any { it.shopOnly && tag in it.tags && com.pipo.robot.data.Places.sellersOf(it.id).isNotEmpty() }

    /**
     * [prefer] = an idea that came from somewhere specific (a video, a library book). It still has
     * to be buildable and not already finished.
     */
    fun maybeStart(s: PipoState, rng: Random, now: Long, chance: Float, prefer: String? = null): PipoProject? {
        if (s.activeProject() != null) return null
        if (prefer == null && s.world.items.size < 2) return null
        val t = s.profile.traits
        if (rng.nextFloat() > chance * (0.5f + t.curiosity * 0.5f + t.confidence * 0.3f)) return null
        val tagsOwned = s.world.items.filter { it.usedInProjectId == 0L }
            .flatMap { Catalog.item(it.catalogId)?.tags ?: emptySet() }.toSet()
        // settled = worked, or turned into something else: he's done with that one (unless he's done with everything)
        val settledIds = s.projects.filter { it.state == ProjectState.DONE || it.state == ProjectState.EVOLVED }.map { it.templateId }.toSet()
        prefer?.let { Catalog.project(it) }?.takeIf { it.id !in settledIds && it.needs.all { n -> n in tagsOwned || purchasable(n) } }?.let { def ->
            return begin(s, def, now, "${def.title}. \"${def.idea}\"")
        }
        if (s.world.items.size < 2) return null
        // Unfinished business: a recent failure he hasn't let go of.
        retryCandidate(s, now)?.let { failed ->
            if (rng.nextFloat() < 0.35f + t.stubbornness * 0.4f + t.patience * 0.2f) {
                val def = Catalog.project(failed.templateId)!!
                val p = PipoProject(s.nextId(), def.id, def.title, components = def.needs.toMutableList(), startedAt = now, attempts = failed.attempts)
                p.log.add("Attempt ${failed.attempts + 1}. He says he knows what went wrong last time.")
                s.projects.add(p)
                if (s.projects.size > 40) s.projects.removeAt(0)
                gather(s, p)
                Chronicle.journal(s, "Pipo is trying again", "The ${def.title.lowercase()}, attempt ${failed.attempts + 1}. \"This time it's personal.\"", JournalCategory.PROJECT, now)
                Chronicle.remember(s, MemoryType.PROJECT, "I'm trying the ${def.title.lowercase()} again", 0.55f, now, "project:${p.id}")
                return p
            }
        }
        // Something he has parts for, or something he could buy the parts for (with a bit of saving).
        fun feasible(d: com.pipo.robot.data.ProjectDef) = d.needs.all { n -> n in tagsOwned || purchasable(n) || Catalog.items.any { !it.shopOnly && n in it.tags } }
        // as he levels up, the harder blueprints are what he dreams about
        val unlocked = Inventor.blueprintsFor(s).filter { it.id !in settledIds && feasible(it) }
        if (unlocked.isNotEmpty() && rng.nextFloat() < 0.55f) unlocked[rng.nextInt(unlocked.size)].let { return begin(s, it, now, "${it.title}. \"${it.idea}\"") }
        val options = Catalog.projects.filter { it.id !in settledIds && feasible(it) && (it.needs.any { n -> n in tagsOwned } || it.needs.all { n -> purchasable(n) }) }
            .ifEmpty { Catalog.projects.filter { it.needs.any { n -> n in tagsOwned } } }
        if (options.isEmpty()) return null
        val def = options[rng.nextInt(options.size)]
        return begin(s, def, now, "${def.title}. \"${def.idea}\"")
    }

    private fun begin(s: PipoState, def: com.pipo.robot.data.ProjectDef, now: Long, journal: String): PipoProject {
        // Something he's tried before carries its history: attempt numbers never go backwards.
        val before = s.projects.filter { it.templateId == def.id }
        val prior = before.maxOfOrNull { it.attempts } ?: 0
        val settled = before.any { it.state == ProjectState.DONE || it.state == ProjectState.EVOLVED }
        val p = PipoProject(s.nextId(), def.id, def.title, components = def.needs.toMutableList(), startedAt = now, attempts = prior)
        if (settled) p.log.add("Version ${before.size + 1}. Improvements. Allegedly.")
        else if (prior > 0) p.log.add("Attempt ${prior + 1}. Different idea this time.")
        s.projects.add(p)
        if (s.projects.size > 40) s.projects.removeAt(0)
        gather(s, p)
        Chronicle.journal(s, when { settled -> "Pipo is building another one"; prior > 0 -> "Pipo is trying again"; else -> "Pipo started a project" }, journal, JournalCategory.PROJECT, now)
        Chronicle.remember(s, MemoryType.PROJECT, "I started building a ${def.title.lowercase()}", 0.5f, now, "project:${p.id}")
        return p
    }

    /** The most recent failed project (last 3 days) he never finished since. */
    /**
     * A project worth retrying: the LATEST attempt at it failed recently. A later attempt that
     * finished (worked, or turned into something else) settles it — regression seen on device,
     * where an old failure kept pulling him back after the retry had already evolved.
     */
    fun retryCandidate(s: PipoState, now: Long): PipoProject? =
        s.projects.groupBy { it.templateId }.values
            .mapNotNull { attempts -> attempts.maxByOrNull { it.startedAt } }
            .filter { it.state == ProjectState.FAILED && now - it.finishedAt < 3 * DAY && Catalog.project(it.templateId) != null }
            .maxByOrNull { it.finishedAt }

    /** Everything the project needs is either on the bench or in his room: time to assemble. */
    fun readyToAssemble(s: PipoState, p: PipoProject): Boolean {
        val free = Inventory.freeTags(s)
        return p.components.all { it in p.collected || it in free }
    }

    /** Uses owned, unused items that match missing component tags (never the one Nib ran off with). */
    fun gather(s: PipoState, p: PipoProject) {
        for (tag in p.components) {
            if (tag in p.collected) continue
            val item = s.world.items.firstOrNull {
                it.usedInProjectId == 0L && it.id != s.pet.stolenItemId && (Catalog.item(it.catalogId)?.tags?.contains(tag) == true)
            } ?: continue
            item.usedInProjectId = p.id
            p.collected.add(tag)
        }
        if (p.state == ProjectState.GATHERING && p.collected.containsAll(p.components)) p.state = ProjectState.BUILDING
    }

    /** One work session. Returns the project if it just finished (any result). */
    fun work(s: PipoState, rng: Random, now: Long, effort: Float = 1f): PipoProject? {
        val p = s.activeProject() ?: return null
        gather(s, p)
        if (p.state != ProjectState.BUILDING) return null
        val t = s.profile.traits
        val before = p.progress
        Inventor.gain(s, 2, now)
        p.progress += (0.16f + rng.nextFloat() * 0.14f) * (0.6f + t.confidence * 0.5f + t.patience * 0.3f) * effort
        // Projects don't go in a straight line.
        val r = rng.nextFloat()
        if (p.progress < 1f && r < 0.12f) {
            p.progress = (p.progress - 0.18f).coerceAtLeast(0.05f)
            logBeat(p, listOf("Something fell off. He thinks it was important.", "A small fire. Very small. He blew it out.", "He glued his hand to it for a while.").random(rng))
        } else if (p.progress < 1f && r > 0.9f) {
            p.progress += 0.2f
            logBeat(p, listOf("Breakthrough at 3am. Robot time.", "He found a shortcut. It might even work.", "He figured out the wobbly part.").random(rng))
        } else {
            if (before < 0.34f && p.progress >= 0.34f) logBeat(p, "The frame is done. It stands up on its own. Mostly.")
            if (before < 0.67f && p.progress >= 0.67f) logBeat(p, "It hums now. He's not sure if it should.")
        }
        if (p.progress < 1f) return null
        p.progress = 1f
        val def = Catalog.project(p.templateId)!!
        val successChance = (0.62f + t.confidence * 0.2f + t.patience * 0.1f - def.difficulty * 0.5f + p.attempts * 0.15f + Inventor.successBonus(s)).coerceIn(0.15f, 0.92f)
        val roll = rng.nextFloat()
        p.finishedAt = now
        p.attempts += 1
        when {
            roll < successChance -> {
                p.state = ProjectState.DONE
                p.result = def.success
                val persist = if (p.attempts > 1) " Attempt ${p.attempts}. He never gave up, and he'd like that noted." else ""
                Chronicle.journal(s, "Pipo finished his ${def.title.lowercase()}", def.success + persist, JournalCategory.PROJECT, now)
                Chronicle.remember(s, MemoryType.PROJECT, "I built a ${def.title.lowercase()}", 0.8f, now, "project:${p.id}")
                Chronicle.event(s, EventType.PROJECT_DONE, 0.8f, now, p.id.toString())
                Personality.nudge(s, Trait.CONFIDENCE, 0.03f)
                MoodEngine.setTransient(s, Mood.PROUD, now, 90_000)
                MoodEngine.bump(s, happiness = 0.2f, excitement = 0.3f)
            }
            roll < successChance + (1f - successChance) * 0.5f -> {
                p.state = ProjectState.EVOLVED
                p.result = def.evolved
                Chronicle.journal(s, "Pipo's ${def.title.lowercase()} became a ${def.evolvedTitle.lowercase()}", def.evolved, JournalCategory.PROJECT, now)
                Chronicle.remember(s, MemoryType.PROJECT, "My ${def.title.lowercase()} turned into a ${def.evolvedTitle.lowercase()}", 0.7f, now, "project:${p.id}")
                Chronicle.event(s, EventType.PROJECT_EVOLVED, 0.65f, now, p.id.toString())
                MoodEngine.bump(s, happiness = 0.05f, excitement = 0.15f)
            }
            else -> {
                p.state = ProjectState.FAILED
                p.result = def.failure
                s.world.objectStates["scraps"] = def.id // bits of it on the floor by the workbench, until he tidies
                // Parts come back. Nothing is lost forever.
                s.world.items.filter { it.usedInProjectId == p.id }.forEach { it.usedInProjectId = 0L }
                Chronicle.journal(s, "Pipo's ${def.title.lowercase()} didn't work", def.failure, JournalCategory.PROJECT, now)
                Chronicle.remember(s, MemoryType.PROJECT, "My ${def.title.lowercase()} broke", 0.6f, now, "project:${p.id}")
                Chronicle.event(s, EventType.PROJECT_FAILED, 0.6f, now, p.id.toString())
                Personality.nudge(s, Trait.CONFIDENCE, -0.02f)
                Personality.nudge(s, Trait.PATIENCE, 0.01f)
                MoodEngine.setTransient(s, Mood.EMBARRASSED, now, 40_000)
            }
        }
        s.count("projects_finished")
        Inventor.gain(s, Inventor.xpFor(p.state, def.difficulty), now)
        return p
    }

    fun lastFinished(s: PipoState): PipoProject? = s.projects.lastOrNull { !it.active }

    private fun logBeat(p: PipoProject, line: String) {
        p.log.add(line)
        if (p.log.size > 6) p.log.removeAt(0)
    }

    /** The newest story beat of the active project, for him to mention while building. */
    fun latestBeat(s: PipoState): String? = s.activeProject()?.log?.lastOrNull()
}
