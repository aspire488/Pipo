package com.pipo.robot.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FootballSimTest {
    private fun playing(skill: Float = 0.3f) = FootballSim(7, skill).apply { start() }

    @Test
    fun youCanTakeTheBallDribbleAndScore() {
        val s = playing(skill = 0f)
        repeat(30) { s.step(1 / 60f) }              // let the kick-off roll settle
        s.ball.x = 0.5f; s.ball.y = 0.5f; s.ball.vx = 0f; s.ball.vy = 0f
        s.you.x = 0.5f; s.you.y = 0.53f
        s.pipo.x = 0.05f; s.pipo.y = 0.95f         // Pipo far away
        s.runTo(0.5f, 0.5f)
        repeat(10) { s.step(1 / 60f) }
        assertEquals("you control a loose ball you reach", 1, s.owner)
        s.runTo(0.5f, 0.25f)
        repeat(30) { s.step(1 / 60f) }
        assertEquals("the ball stays at your feet while you run", 1, s.owner)
        assertTrue(s.kick(0.5f, -0.2f))
        var scored = false
        repeat(120) { if (!scored) { s.step(1 / 60f); scored = s.yourGoals == 1 } }
        assertTrue("a kick into the top goal mouth scores", scored)
        assertEquals(FootballSim.Phase.GOAL, s.phase)
    }

    @Test
    fun youCantKickWhatYouCantReach() {
        val s = playing()
        s.you.x = 0.1f; s.you.y = 0.9f; s.ball.x = 0.8f; s.ball.y = 0.2f
        repeat(30) { s.step(1 / 60f) }
        assertFalse(s.kick(0.5f, 0f))
    }

    @Test
    fun aShotWideOfTheGoalBouncesBackIn() {
        val s = playing()
        s.pipo.x = 0.05f; s.pipo.y = 0.95f
        s.ball.x = 0.1f; s.ball.y = 0.05f; s.ball.vx = 0f; s.ball.vy = -1f
        repeat(10) { s.step(1 / 60f) }
        assertEquals(0, s.yourGoals)
        assertTrue(s.ball.y >= 0f)
    }

    @Test
    fun theLooseBallSlowsDown() {
        val s = playing()
        repeat(30) { s.step(1 / 60f) }
        // out of everyone's reach for the next second, so only friction acts on it
        s.pipo.x = 0.05f; s.pipo.y = 0.05f; s.you.x = 0.05f; s.you.y = 0.95f
        s.ball.x = 0.5f; s.ball.y = 0.8f; s.ball.vx = 0.3f; s.ball.vy = 0f
        repeat(60) { s.step(1 / 60f) }
        assertTrue("rolled ball loses most of its speed in a second: ${s.ball.vx}", kotlin.math.abs(s.ball.vx) < 0.07f)
    }

    @Test
    fun pipoGoesForTheBallAndCanScore() {
        val s = playing(skill = 1f)
        s.you.x = 0.05f; s.you.y = 0.05f                         // you're out of the way
        repeat(60 * 25) { if (s.phase == FootballSim.Phase.PLAYING) s.step(1 / 60f) else s.step(1 / 60f) }
        assertTrue("an unopposed Pipo scores", s.pipoGoals >= 1)
    }

    @Test
    fun firstToThreeEndsItAndTheResultIsClaimedOnce() {
        val s = playing(skill = 1f)
        s.you.x = 0.05f; s.you.y = 0.05f
        var ended = false
        // the clock stops during goal celebrations, so give it longer than the 2 minutes
        repeat(60 * 200) { if (!ended) { s.runTo(0.05f, 0.05f); ended = s.step(1 / 60f) } }
        assertTrue(ended)
        assertEquals(FootballSim.Phase.OVER, s.phase)
        assertNotNull(s.pipoWon)
        assertTrue(s.claimResult()); assertFalse(s.claimResult())
    }
}
