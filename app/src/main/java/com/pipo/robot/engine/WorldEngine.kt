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

data class PrankDef(val key: String, val journal: String, val line: String)

object Pranks {
    val all = listOf(
        PrankDef("plant_hat", "Pipo put a tiny hat on the plant.", "The plant looked cold. You're welcome, plant."),
        PrankDef("lamp_sock", "Pipo put a sock on the desk lamp.", "Mood lighting. Don't touch it."),
        PrankDef("screen_note", "Pipo stuck a note on the computer that says PIPO WAS HERE.", "Someone was here. Not me. Probably."),
        PrankDef("ball_on_bed", "Pipo tucked the ball into his bed.", "The ball was tired. I let it have my bed."),
        PrankDef("arcade_score", "Pipo set a new arcade high score. Possibly by unplugging it.", "I'm the arcade champion now. Officially."),
        PrankDef("screw_tower", "Pipo built a tower of screws on the workbench.", "It's art. It's called Tower. Don't breathe on it."),
    )

    fun active(s: PipoState) = all.filter { s.world.objectStates["prank:${it.key}"] == "1" }
    fun isOn(s: PipoState, key: String) = s.world.objectStates["prank:$key"] == "1"
}

fun article(word: String) = if (word.first().lowercaseChar() in "aeiou") "an" else "a"

object Discovery {
    fun ownedDefs(s: PipoState): List<ItemDef> = s.world.items.mapNotNull { Catalog.item(it.catalogId) }

    /** Rolls for a discovery. Prefers things the active project needs. */
    fun roll(s: PipoState, rng: Random, now: Long): OwnedItem? {
        val ownedIds = s.world.items.map { it.catalogId }.toSet()
        val needed = neededTags(s)
        val pool = Catalog.items.filter { !(it.unique && it.id in ownedIds) }
        if (pool.isEmpty()) return null
        val weights = pool.map { d ->
            var w = d.weight
            if (d.tags.any { it in needed }) w *= 3.5f
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
    fun maybeStart(s: PipoState, rng: Random, now: Long, chance: Float): PipoProject? {
        if (s.activeProject() != null) return null
        if (s.world.items.size < 2) return null
        val t = s.profile.traits
        if (rng.nextFloat() > chance * (0.5f + t.curiosity * 0.5f + t.confidence * 0.3f)) return null
        val tagsOwned = s.world.items.filter { it.usedInProjectId == 0L }
            .flatMap { Catalog.item(it.catalogId)?.tags ?: emptySet() }.toSet()
        val doneIds = s.projects.filter { it.state == ProjectState.DONE }.map { it.templateId }.toSet()
        val options = Catalog.projects.filter { it.id !in doneIds && it.needs.any { n -> n in tagsOwned } }
            .ifEmpty { Catalog.projects.filter { it.needs.any { n -> n in tagsOwned } } }
        if (options.isEmpty()) return null
        val def = options[rng.nextInt(options.size)]
        val p = PipoProject(s.nextId(), def.id, def.title, components = def.needs.toMutableList(), startedAt = now)
        s.projects.add(p)
        if (s.projects.size > 40) s.projects.removeAt(0)
        gather(s, p)
        Chronicle.journal(s, "Pipo started a project", "${def.title}. \"${def.idea}\"", JournalCategory.PROJECT, now)
        Chronicle.remember(s, MemoryType.PROJECT, "I started building a ${def.title.lowercase()}", 0.5f, now, "project:${p.id}")
        return p
    }

    /** Uses owned, unused items that match missing component tags. */
    fun gather(s: PipoState, p: PipoProject) {
        for (tag in p.components) {
            if (tag in p.collected) continue
            val item = s.world.items.firstOrNull {
                it.usedInProjectId == 0L && (Catalog.item(it.catalogId)?.tags?.contains(tag) == true)
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
        p.progress += (0.16f + rng.nextFloat() * 0.14f) * (0.6f + t.confidence * 0.5f + t.patience * 0.3f) * effort
        if (p.progress < 1f) return null
        p.progress = 1f
        val def = Catalog.project(p.templateId)!!
        val successChance = (0.62f + t.confidence * 0.2f + t.patience * 0.1f - def.difficulty * 0.5f + p.attempts * 0.15f).coerceIn(0.15f, 0.92f)
        val roll = rng.nextFloat()
        p.finishedAt = now
        p.attempts += 1
        when {
            roll < successChance -> {
                p.state = ProjectState.DONE
                p.result = def.success
                Chronicle.journal(s, "Pipo finished his ${def.title.lowercase()}", def.success, JournalCategory.PROJECT, now)
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
        return p
    }

    fun lastFinished(s: PipoState): PipoProject? = s.projects.lastOrNull { !it.active }
}
