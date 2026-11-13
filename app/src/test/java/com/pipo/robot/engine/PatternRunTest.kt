package com.pipo.robot.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PatternRunTest {
    @Test
    fun theSequenceGrowsByOneAndRepeatingItScores() {
        val run = PatternRun(Random(1), pipoRecall = 0f)
        for (round in 1..6) {
            val seq = run.next()
            assertEquals(round, seq.size)
            seq.dropLast(1).forEach { assertEquals(PatternRun.Step.HIT, run.press(it)) }
            assertEquals(PatternRun.Step.ROUND_DONE, run.press(seq.last()))
        }
        assertEquals(6, run.rounds)
        assertFalse(run.over)
    }

    @Test
    fun aMissEndsTheRunWithoutPipo() {
        val run = PatternRun(Random(2), pipoRecall = 0f)
        val seq = run.next()
        assertEquals(PatternRun.Step.OVER, run.press((seq[0] + 1) % 4))
        assertTrue(run.over)
        assertEquals(PatternRun.Step.OVER, run.press(0))
        assertEquals(0, run.rounds)
    }

    @Test
    fun pipoSavesYouOnceAndOnlyOnce() {
        val run = PatternRun(Random(3), pipoRecall = 1f)
        run.next(); run.next(); run.next()
        val seq = run.sequence.toList()
        assertEquals(PatternRun.Step.SAVED, run.press((seq[0] + 1) % 4))
        assertEquals(seq[0], run.expected())          // he tells you the light you missed
        assertEquals(PatternRun.Step.HIT, run.press(seq[0]))
        assertEquals(PatternRun.Step.OVER, run.press((seq[1] + 1) % 4))   // second slip: no more saves
    }

    @Test
    fun itSpeedsUpButStaysPlayable() {
        val run = PatternRun(Random(4), pipoRecall = 0f)
        run.next()
        val first = run.flashMs()
        repeat(30) { run.sequence += 0 }
        assertTrue(run.flashMs() < first)
        assertEquals(260L, run.flashMs())
    }
}
