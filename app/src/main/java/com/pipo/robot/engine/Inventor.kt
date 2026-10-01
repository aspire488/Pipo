package com.pipo.robot.engine

import com.pipo.robot.data.ItemShape
import com.pipo.robot.data.JournalCategory
import com.pipo.robot.data.MemoryType
import com.pipo.robot.data.PipoState
import com.pipo.robot.data.ProjectDef
import com.pipo.robot.data.ProjectState

/**
 * Pipo the inventor, growing up: every build (and every failure, which teaches the most) adds to
 * his builder level. Higher levels unlock harder blueprints and make him fail less — but never
 * never. Some inventions change his world: Nib gets faster, the desk gets a helper, a drone
 * brings home photos, the telescope sees further.
 */
object Inventor {
    data class Rank(val level: Int, val title: String, val xp: Int)
    val ranks = listOf(Rank(1, "Tinkerer", 0), Rank(2, "Builder", 60), Rank(3, "Engineer", 160), Rank(4, "Inventor", 320), Rank(5, "Genius", 550))

    /** His experience. A save from before levels existed gets credit for everything he already built. */
    fun xp(s: PipoState) = s.records["builder_xp"] ?: s.projects.sumOf { p ->
        xpFor(p.state, com.pipo.robot.data.Catalog.project(p.templateId)?.difficulty ?: 0.4f)
    }
    fun rank(s: PipoState): Rank = ranks.last { xp(s) >= it.xp }
    fun level(s: PipoState) = rank(s).level
    fun nextRank(s: PipoState): Rank? = ranks.firstOrNull { it.xp > xp(s) }

    /** Adds experience. Returns the new rank if he just went up one. */
    fun gain(s: PipoState, amount: Int, now: Long): Rank? {
        val before = rank(s)
        s.records["builder_xp"] = xp(s) + amount
        val after = rank(s)
        if (after.level <= before.level) return null
        Chronicle.journal(s, "Pipo is now a level ${after.level} ${after.title}", levelUpLine(after), JournalCategory.MILESTONE, now)
        Chronicle.remember(s, MemoryType.SELF, "I'm a level ${after.level} ${after.title.lowercase()} now", 0.8f, now, "rank:${after.level}")
        s.world.objectStates["levelup"] = after.level.toString() // he'll announce it
        return after
    }

    fun levelUpLine(r: Rank) = when (r.level) {
        2 -> "He made himself a badge out of a bottle cap. It says BUILDER. He wears it everywhere."
        3 -> "Engineer. He drew blueprints. Real ones. Blue ones. He pinned them up."
        4 -> "Inventor. The workbench glows at night now. He says it's 'the lab'. Nib is the lab assistant."
        else -> "Genius level. He says it's official. Nib agrees, in beeps. The room is basically a lab."
    }

    /** XP for how a project ended. Failure teaches the most per attempt: he learns what NOT to do. */
    fun xpFor(state: ProjectState, difficulty: Float): Int = when (state) {
        ProjectState.DONE -> 30 + (difficulty * 40).toInt()
        ProjectState.EVOLVED -> 20 + (difficulty * 25).toInt()
        ProjectState.FAILED -> 12 + (difficulty * 15).toInt()
        else -> 0
    }

    /** Skill on top of personality: each level makes a success a bit likelier. */
    fun successBonus(s: PipoState) = (level(s) - 1) * 0.06f

    /** The harder things, unlocked by level. Shapes reuse his shelf art. */
    val blueprints: List<Pair<Int, ProjectDef>> = listOf(
        2 to ProjectDef("nib_wheel", "Turbo wheel for Nib", listOf("motor", "round", "power"), 0.4f,
            "Nib is slow. Nib is not slow. But Nib could be FASTER.",
            "Nib has a turbo wheel. Nib is a blur now. A beeping blur.",
            "The turbo wheel went backwards. Nib reversed into the plant. Nib is fine. The plant is shaken.",
            "It's a spinning top now. Nib loves it more than the wheel.", "Spinning top", ItemShape.GEAR),
        2 to ProjectDef("nib_tail", "Light-up tail for Nib", listOf("light", "wire"), 0.3f,
            "Nib's tail should glow. So I can find Nib at night. Nib hides a lot.",
            "Nib's tail glows! Nib keeps looking at it. Nib is very proud.",
            "The tail light blinks in morse code. It's spelling 'HELP'. Probably a coincidence.",
            "It's a night-light Nib carries around. That's... actually great.", "Nib night-light", ItemShape.LED),
        3 to ProjectDef("helper", "B.O.L.T., the desk helper", listOf("chip", "light", "wire"), 0.5f,
            "Every inventor needs a helper. A screen that talks. Named B.O.L.T. Big Operations... something.",
            "B.O.L.T. is alive! It's a screen on my desk with a face. It says 'hello' and 'error'. Mostly 'hello'.",
            "B.O.L.T. only says 'error'. Then it played a tiny song. Then 'error' again.",
            "B.O.L.T. is a clock now. A very opinionated clock.", "Opinionated clock", ItemShape.RADIO),
        3 to ProjectDef("scout_drone", "Scout drone", listOf("motor", "prop", "lens", "power"), 0.6f,
            "A drone that flies out the window and takes photos for me. A spy drone. A NICE spy.",
            "The scout drone works! It flew to the garden and came back with a photo. Of a leaf. It's a great leaf.",
            "The drone flew straight up and got stuck on the lamp. It lives there now.",
            "It doesn't fly, it rolls. It's a scout car. It still takes photos. Of ankles.", "Scout car", ItemShape.DRONE),
        4 to ProjectDef("rocket_boots", "Rocket boots", listOf("motor", "power", "metal", "frame"), 0.75f,
            "Football, but with ROCKETS. Don't tell anyone. Especially the ceiling.",
            "ROCKET BOOTS. I did a kick-up and went up WITH the ball. We don't talk about the ceiling.",
            "The boots fired once. Sideways. I'm on the bed now. I live here now.",
            "They're hover slippers now. They go up one centimetre. It's very relaxing.", "Hover slippers", ItemShape.KICKER),
        3 to ProjectDef("armor", "Armor Mk I", listOf("metal", "magnet", "light", "frame"), 0.7f,
            "Armor. For storms. And for looking cool. Mostly for looking cool. Mark One.",
            "ARMOR MARK ONE. It's red and gold. It's heavy. Storms can't scare me now. Thunder can try.",
            "Mk I fell apart when I sneezed. Robots don't sneeze. I did. Back to the drawing board.",
            "The armor is a very shiny backpack now. I keep snacks in it.", "Shiny backpack", ItemShape.FRAME),
        4 to ProjectDef("armor_mk2", "Armor Mk II", listOf("motor", "power", "metal", "frame"), 0.8f,
            "Mark One can't fly. Mark Two will. I've done the maths. The maths is mostly drawings.",
            "MARK TWO FLIES. I hovered over the rug. For four seconds. I'm a superhero now. A small one.",
            "Mk II flew straight into the ceiling. The ceiling won. The ceiling always wins.",
            "Mk II only hovers sideways. It's a very fancy skateboard now.", "Hover-skate", ItemShape.KICKER),
        5 to ProjectDef("armor_mk3", "Armor Mk III", listOf("chip", "light", "magnet", "power", "lens"), 0.9f,
            "Mark Three. Glowing core. A helmet that flips up. B.O.L.T. inside. This is the one.",
            "MARK THREE. The core glows. The helmet flips up. B.O.L.T. says 'hello' from inside it. I'm basically complete.",
            "Mk III's core glowed, then fizzled. It made a sad sound. I made the same sound.",
            "Mk III is a night-light now. A very heroic night-light.", "Heroic night-light", ItemShape.LED),
        5 to ProjectDef("scope_mk2", "Star Scope Mk II", listOf("lens", "tube", "motor", "chip"), 0.85f,
            "My telescope sees far. I want to see FARTHER. Behind the hills. Where the lights are.",
            "Star Scope Mk II. I can see behind the hills. There's... something there. I'll keep watching.",
            "Mk II only shows the inside of my own eye. Again. Higher resolution though.",
            "It's a projector now. It shows stars on the ceiling. Nib tries to catch them.", "Star projector", ItemShape.TELESCOPE),
    )

    /** Some things build on others: each armor needs the one before it. */
    private val needsFirst = mapOf("armor_mk2" to "armor", "armor_mk3" to "armor_mk2", "scope_mk2" to "telescope")

    fun blueprintsFor(s: PipoState): List<ProjectDef> = blueprints.filter { it.first <= level(s) && needsFirst[it.second.id]?.let { pre -> has(s, pre) } != false }.map { it.second }

    /** The best suit he has: 0 none, 1..3 = Mk I..III. */
    fun armorMark(s: PipoState) = when { has(s, "armor_mk3") -> 3; has(s, "armor_mk2") -> 2; has(s, "armor") -> 1; else -> 0 }
    fun def(id: String): ProjectDef? = blueprints.firstOrNull { it.second.id == id }?.second

    /** Did he build it (and it works)? */
    fun has(s: PipoState, id: String) = s.projects.any { it.templateId == id && it.state == ProjectState.DONE }

    /** Everything he's made that works, newest first. */
    fun inventions(s: PipoState): List<String> = s.projects.filter { it.state == ProjectState.DONE || it.state == ProjectState.EVOLVED }
        .sortedByDescending { it.finishedAt }.map { p -> if (p.state == ProjectState.EVOLVED) (com.pipo.robot.data.Catalog.project(p.templateId)?.evolvedTitle ?: def(p.templateId)?.evolvedTitle ?: p.title) else p.title }.distinct()

    /** A brag (or a confession) about something he built, for conversation. */
    fun brag(s: PipoState, rng: kotlin.random.Random): String? {
        val failed = s.projects.filter { it.state == ProjectState.FAILED }.lastOrNull()
        val built = inventions(s)
        return when {
            has(s, "rocket_boots") && rng.nextFloat() < 0.4f -> "Remember the rocket boots? We don't talk about the ceiling."
            built.isNotEmpty() && (failed == null || rng.nextBoolean()) -> "I built ${built.take(3).joinToString(", ") { "the " + it.lowercase() }}. I'm a level ${level(s)} ${rank(s).title.lowercase()}."
            failed != null -> "My ${failed.title.lowercase()} broke. Failure is how inventors learn. I learned a LOT."
            else -> null
        }
    }
}
