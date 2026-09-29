package com.pipo.robot.engine

import com.pipo.robot.data.ActivityType
import com.pipo.robot.data.ActivityType.PLAY_CONSOLE
import com.pipo.robot.data.ActivityType.SCROLL_PHONE
import com.pipo.robot.data.PipoState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Pipo's phone and console are a treat, never an addiction. */
class ScreenTimeTest {
    private val day0 = 20_000L * DAY + 9 * HOUR

    @Test
    fun breakBetweenAnyTwoScreenSessions() {
        val s = PipoState()
        assertTrue(ScreenTime.allowed(s, SCROLL_PHONE, day0))
        BehaviorEngine.start(s, SCROLL_PHONE, day0, Random(1))
        // no hopping from the phone straight onto the console
        assertFalse(ScreenTime.allowed(s, PLAY_CONSOLE, day0 + MINUTE))
        assertFalse(ScreenTime.allowed(s, SCROLL_PHONE, day0 + MINUTE))
        assertTrue(ScreenTime.allowed(s, PLAY_CONSOLE, day0 + ScreenTime.BREAK_MS))
    }

    @Test
    fun dailyCapsAndNextDayReset() {
        val s = PipoState()
        var t = day0
        repeat(ScreenTime.dailyMax(SCROLL_PHONE)) { BehaviorEngine.start(s, SCROLL_PHONE, t, Random(it)); t += ScreenTime.BREAK_MS }
        assertFalse("phone capped for today", ScreenTime.allowed(s, SCROLL_PHONE, t))
        assertTrue("console has its own count", ScreenTime.allowed(s, PLAY_CONSOLE, t))
        repeat(ScreenTime.dailyMax(PLAY_CONSOLE)) { BehaviorEngine.start(s, PLAY_CONSOLE, t, Random(it)); t += ScreenTime.BREAK_MS }
        assertFalse(ScreenTime.allowed(s, PLAY_CONSOLE, t))
        // tomorrow he can have a little screen time again
        assertTrue(ScreenTime.allowed(s, SCROLL_PHONE, day0 + DAY))
        assertEquals(0, ScreenTime.sessionsToday(s, SCROLL_PHONE, day0 + DAY))
    }

    @Test
    fun cappedScreensAreNeverChosen() {
        val s = PipoState()
        var t = day0
        repeat(3) { BehaviorEngine.start(s, SCROLL_PHONE, t, Random(it)); t += ScreenTime.BREAK_MS }
        repeat(2) { BehaviorEngine.start(s, PLAY_CONSOLE, t, Random(it)); t += ScreenTime.BREAK_MS }
        s.mood.boredom = 1f // even when very bored
        val scores = BehaviorEngine.score(s, Env(hour = 15), t)
        assertEquals(0f, scores.first { it.type == SCROLL_PHONE }.score)
        assertEquals(0f, scores.first { it.type == PLAY_CONSOLE }.score)
    }

    @Test
    fun screensStayASmallPartOfHisDay() {
        // Simulate a long day of choices: gadgets should be a minority of what he does.
        val s = PipoState()
        val rng = Random(7)
        var t = day0
        var last: ActivityType? = null
        val counts = mutableMapOf<ActivityType, Int>()
        repeat(200) {
            val a = BehaviorEngine.choose(s, Env(hour = 14), t, rng, last)
            BehaviorEngine.start(s, a, t, rng)
            counts[a] = (counts[a] ?: 0) + 1
            last = a
            t += 40_000L
        }
        val screens = (counts[SCROLL_PHONE] ?: 0) + (counts[PLAY_CONSOLE] ?: 0)
        assertTrue("screens=$screens of 200", screens <= 5)
        assertTrue("he still does lots of other things: ${counts.keys}", counts.keys.size >= 6)
    }

    @Test
    fun arcadeWaitsOutTheScreenBreakToo() {
        // regression (seen on device): pulled off the console, he walked straight to the arcade
        val s = PipoState()
        s.mood.boredom = 1f
        BehaviorEngine.start(s, PLAY_CONSOLE, day0, Random(1))
        assertEquals(0f, BehaviorEngine.score(s, Env(hour = 15), day0 + MINUTE).first { it.type == ActivityType.PLAY_ARCADE }.score)
        assertTrue(BehaviorEngine.score(s, Env(hour = 15), day0 + ScreenTime.BREAK_MS).first { it.type == ActivityType.PLAY_ARCADE }.score > 0f)
    }

    @Test
    fun gadgetsAreNeverAbsorbing() {
        val s = PipoState()
        assertEquals(0f, BehaviorEngine.absorbChance(s, SCROLL_PHONE))
        assertEquals(0f, BehaviorEngine.absorbChance(s, PLAY_CONSOLE))
    }
}
