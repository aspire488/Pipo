package com.pipo.robot.engine

import com.pipo.robot.data.ActivityType
import com.pipo.robot.data.PipoState
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Seen live: he never scrolled his phone or played the console, and lived on exploring and
 * football. Over real days (one activity after another, history and cooldowns included) his
 * life has to actually contain all of it.
 */
class ActivityMixTest {
    @Test
    fun aRealDayHasVariety() {
        val counts = mutableMapOf<ActivityType, Int>()
        repeat(5) { day ->
            val s = PipoState(seed = day.toLong()).apply { profile.firstRunDone = true; mood.energy = 0.75f; mood.boredom = 0.35f }
            var now = 20_000L * DAY + 9 * HOUR
            var last: ActivityType? = null
            repeat(60) { i ->
                val env = Env(hour = hourOf(now), userPresent = true)
                val a = BehaviorEngine.choose(s, env, now, Random(day * 1000 + i), last)
                val started = BehaviorEngine.start(s, a, now, Random(i), env, inApp = true)
                counts[started] = (counts[started] ?: 0) + 1
                last = started
                now += s.activity.durationMs.coerceIn(15_000L, 10 * MINUTE) + 30_000L
                s.trip = null
            }
        }
        val total = counts.values.sum()
        println(counts.entries.sortedByDescending { it.value }.joinToString { "${it.key}=${it.value}" })
        assertTrue("phone ${counts[ActivityType.SCROLL_PHONE]}", (counts[ActivityType.SCROLL_PHONE] ?: 0) >= 3)
        assertTrue("console ${counts[ActivityType.PLAY_CONSOLE]}", (counts[ActivityType.PLAY_CONSOLE] ?: 0) >= 3)
        assertTrue("only ${counts.size} kinds of activity", counts.size >= 14)
        val maxShare = counts.values.max().toFloat() / total
        assertTrue("one activity is ${(maxShare * 100).toInt()}% of his day", maxShare < 0.3f)
    }
}
