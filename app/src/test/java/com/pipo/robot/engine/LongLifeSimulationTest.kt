package com.pipo.robot.engine

import com.pipo.robot.data.JournalCategory
import com.pipo.robot.data.PipoState
import com.pipo.robot.data.ProjectState
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Two weeks of Pipo's life, simulated hour by hour through the real engine (the same catch-up the
 * hourly worker runs), with the clock moving forward the way it does on a phone. Checks that he
 * actually builds things: projects start, gather parts, get built, end (worked / evolved / failed),
 * and failures get retried without looping. Prints the project story for the device report.
 */
class LongLifeSimulationTest {

    private fun live(seed: Int, days: Int = 14): PipoState {
        val rng = Random(seed)
        val s = PipoState()
        s.profile.firstRunDone = true
        var now = 20_000L * DAY + 8 * HOUR
        s.lastSimulatedAt = now
        repeat(days * 24) {
            now += HOUR
            Simulator.catchUp(s, now, rng)
            MoodEngine.derive(s, now, hourOf(now))
        }
        return s
    }

    @Test
    fun heBuildsThingsOverTwoWeeks() {
        var totalFinished = 0
        for (seed in 1..6) {
            val s = live(seed)
            val finished = s.projects.filter { it.state in setOf(ProjectState.DONE, ProjectState.EVOLVED, ProjectState.FAILED) }
            totalFinished += finished.size
            val projectLog = s.journal.filter { it.category == JournalCategory.PROJECT }.map { "${it.title}: ${it.description}" }
            println("seed $seed: ${s.world.items.size} things found, projects " +
                s.projects.joinToString { "${it.title} [${it.state}, attempt ${it.attempts}]" })
            projectLog.forEach { println("   · $it") }

            // no endless retry loop: once an attempt at a project worked or evolved, he never "tries again" at it
            for ((template, attempts) in s.projects.groupBy { it.templateId }) {
                val settled = attempts.indexOfFirst { it.state == ProjectState.DONE || it.state == ProjectState.EVOLVED }
                if (settled >= 0) assertTrue("seed $seed: retried $template after it was settled",
                    attempts.drop(settled + 1).none { it.log.any { l -> l.startsWith("Attempt") } })
                // attempt numbers never go backwards
                val nums = attempts.map { it.attempts }
                assertTrue("seed $seed: $template attempts $nums", nums.zipWithNext().all { (a, b) -> b >= a })
            }
        }
        assertTrue("across 6 two-week lives he should finish several projects (got $totalFinished)", totalFinished >= 3)
    }
}
