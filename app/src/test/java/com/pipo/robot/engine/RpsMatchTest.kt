package com.pipo.robot.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Regression: Rock Paper Scissors used to keep its state in loose UI variables, so a double tap or a
 * recomposition during the reveal could score a round twice. The match is now a phase machine.
 */
class RpsMatchTest {

    private fun always(h: RpsHand) = RpsMatch(3) { h }

    private fun playRound(m: RpsMatch, h: RpsHand): RpsRound? {
        if (!m.lock(h)) return null
        m.think(); m.reveal()
        val r = m.resolve()
        m.next()
        return r
    }

    @Test
    fun oneRoundHasExactlyOneChoiceEachAndOneScoreUpdate() {
        val m = always(RpsHand.ROCK)
        assertTrue(m.lock(RpsHand.PAPER))
        assertFalse("a second tap can't change or re-lock the throw", m.lock(RpsHand.SCISSORS))
        assertEquals(RpsHand.PAPER, m.userHand)
        assertTrue(m.think())
        assertFalse(m.think())
        assertEquals(RpsHand.ROCK, m.reveal())
        assertNull("Pipo decides once", m.reveal())
        val r = m.resolve()
        assertNotNull(r)
        assertNull("resolving again (recomposition, a second coroutine) does nothing", m.resolve())
        assertNull(m.resolve())
        assertEquals(1, m.userScore)
        assertEquals(0, m.pipoScore)
        assertEquals(1, m.rounds.size)
    }

    @Test
    fun outOfOrderCallsAreIgnored() {
        val m = always(RpsHand.ROCK)
        assertNull(m.reveal())
        assertNull(m.resolve())
        assertFalse(m.next())
        assertFalse(m.think())
        assertEquals(RpsPhase.WAITING_FOR_USER, m.phase)
        assertEquals(0, m.userScore + m.pipoScore)
    }

    @Test
    fun drawsDontScoreAndMatchEndsAtThreeAndIsRecordedOnce() {
        val m = always(RpsHand.ROCK)
        val draw = playRound(m, RpsHand.ROCK)!!
        assertEquals(0, draw.winner)
        assertEquals(0, m.userScore + m.pipoScore)
        repeat(3) { playRound(m, RpsHand.PAPER) }
        assertEquals(RpsPhase.MATCH_OVER, m.phase)
        assertEquals(false, m.pipoWon)
        assertFalse("no more throws after the match", m.lock(RpsHand.ROCK))
        assertTrue(m.claimResult())
        assertFalse("the result is recorded exactly once", m.claimResult())
        m.reset()
        assertEquals(RpsPhase.WAITING_FOR_USER, m.phase)
        assertEquals(0, m.userScore)
        assertFalse(m.claimResult())
    }

    @Test
    fun pipoSeesOnlyYourPreviousThrowsNotTheCurrentOne() {
        val seen = mutableListOf<List<RpsHand>>()
        val m = RpsMatch(3) { h -> seen += h; RpsHand.PAPER }
        playRound(m, RpsHand.SCISSORS)
        playRound(m, RpsHand.ROCK)
        assertEquals(listOf(emptyList(), listOf(RpsHand.SCISSORS)), seen)
    }

    /** Hammer it with random call orders (what a double tap + recomposition + stale coroutine look like). */
    @Test
    fun randomCallStormNeverDoubleScores() {
        for (seed in 1..300) {
            val rng = Random(seed)
            val m = RpsMatch(3) { RpsHand.entries[rng.nextInt(3)] }
            var claims = 0
            repeat(400) {
                when (rng.nextInt(7)) {
                    0, 1 -> m.lock(RpsHand.entries[rng.nextInt(3)])
                    2 -> m.think()
                    3 -> m.reveal()
                    4 -> m.resolve()
                    5 -> m.next()
                    else -> if (m.claimResult()) claims++
                }
                val decisive = m.rounds.count { it.winner != 0 }
                assertEquals("seed $seed: scores must equal decided rounds", decisive, m.userScore + m.pipoScore)
                assertTrue(m.userScore <= 3 && m.pipoScore <= 3)
                assertEquals(m.rounds.map { it.index }.distinct().size, m.rounds.size) // one resolution per round
            }
            assertTrue("seed $seed: claimed $claims times", claims <= 1)
        }
    }
}
