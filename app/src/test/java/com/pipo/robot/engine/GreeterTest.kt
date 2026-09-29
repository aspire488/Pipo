package com.pipo.robot.engine

import com.pipo.robot.data.ActivityType
import com.pipo.robot.data.EventType
import com.pipo.robot.data.PipoState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GreeterTest {
    private fun asleep(now: Long) = PipoState().apply {
        profile.firstRunDone = true
        activity.type = ActivityType.SLEEP
        lastSeenByUserAt = now - 3 * HOUR
    }

    @Test
    fun recapNeverSaysNothingHappenedNextToRealEvents() {
        // regression (seen on device): "I did absolutely nothing. On purpose, broke the flying machine, and…"
        val s = PipoState()
        Simulator.noteAway(s, "did absolutely nothing. On purpose")
        Simulator.noteAway(s, "broke the flying machine")
        assertEquals(listOf("broke the flying machine"), s.awayLog)
        val line = Dialogue.awayDigest(s.awayLog, Random(2))!!
        assertTrue(line, !line.contains("nothing"))
    }

    @Test
    fun tappingHisMessageWakesHimToExplainIt() {
        // regression (seen on device): tapping "Joel. I have an idea." at night got a sleep mumble
        val now = 20_000L * DAY + 21 * HOUR
        val s = asleep(now)
        val ev = Chronicle.event(s, EventType.THOUGHT, 0.6f, now - HOUR, "idea:Flying machine")
        val g = Greeter.plan(s, now, 3 * HOUR, Random(1), fromNotification = ev)
        assertNotEquals(GreetKind.SLEEPING, g.kind)
        assertTrue(g.line, g.line.contains("flying machine"))
        // without the tap he stays asleep, as before
        assertEquals(GreetKind.SLEEPING, Greeter.plan(s, now, 3 * HOUR, Random(1)).kind)
    }
}
