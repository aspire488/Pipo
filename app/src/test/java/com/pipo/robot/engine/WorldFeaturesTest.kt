package com.pipo.robot.engine

import com.pipo.robot.data.PipoState
import com.pipo.robot.data.TripPurpose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import kotlin.random.Random

/** Festivals, seasons, real weather, sports with Nib, and the playable sport games. */
class WorldFeaturesTest {
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = Calendar.getInstance().apply { clear(); set(y, m - 1, d, h, 0) }.timeInMillis

    @Test
    fun festivalsComeFromTheRealCalendar() {
        assertEquals(Festival.CHRISTMAS, Festivals.today(at(2026, 12, 25)))
        assertEquals(Festival.CHRISTMAS, Festivals.today(at(2026, 12, 20)))
        assertEquals(Festival.HALLOWEEN, Festivals.today(at(2026, 10, 31)))
        assertEquals(Festival.NEW_YEAR, Festivals.today(at(2027, 1, 1)))
        assertEquals(Festival.DIWALI, Festivals.today(at(2026, 11, 8)))
        assertEquals(Festival.ONAM, Festivals.today(at(2026, 8, 26)))
        assertNull(Festivals.today(at(2026, 7, 10)))
        assertNull(Festivals.today(at(2026, 10, 1)))
    }

    @Test
    fun seasonsFollowWhereYouAre() {
        assertEquals(Season.MONSOON, Seasons.at(at(2026, 7, 15), 10.0))   // Kerala in July
        assertEquals(Season.SUMMER, Seasons.at(at(2026, 4, 15), 10.0))
        assertEquals(Season.WINTER, Seasons.at(at(2026, 1, 15), 51.0))    // London in January
        assertEquals(Season.SUMMER, Seasons.at(at(2026, 1, 15), -33.0))   // Sydney in January
    }

    @Test
    fun realWeatherWinsWhenFreshAndFallsBackWhenOld() {
        val now = at(2026, 10, 1, 15)
        WeatherEngine.live = WeatherEngine.Live(Weather.STORM, 0.9f, 24f, now, 10.0, "Kochi")
        try {
            assertEquals(Weather.STORM, WeatherEngine.at(7L, now + 20 * MINUTE).kind)
            assertEquals(WeatherEngine.at(7L, WeatherEngine.localDay(now - 2 * DAY), 15).kind, WeatherEngine.at(7L, now - 2 * DAY).kind) // long ago: his own climate
        } finally { WeatherEngine.live = null }
    }

    @Test
    fun sportsTripsAreTwoPlayerWithNib() {
        val s = PipoState(seed = 3L).apply { profile.firstRunDone = true; mood.energy = 0.8f }
        val noon = at(2026, 10, 1, 12)
        val idea = Trips.forUser(s, Env(hour = 12), noon, "let's go play cricket")
        assertNotNull(idea); assertEquals(TripPurpose.CRICKET, idea!!.purpose)
        val trip = Trips.begin(s, idea, noon, Random(1), inApp = true)
        assertTrue("Nib comes: it's a two-player game", trip.withPet)
        val story = Sports.session(s, Sport.CRICKET, Random(2), noon, withNib = true)
        assertTrue(story.joinToString(" "), story.any { "Nib" in it })
        assertTrue(s.memories.any { "Nib" in it.content && "cricket" in it.content })
        assertEquals(1, (s.records["vs:CRICKET:pipo"] ?: 0) + (s.records["vs:CRICKET:opp"] ?: 0))
    }

    @Test
    fun nibCanWinToo() {
        var nibWins = 0
        repeat(30) { i ->
            val s = PipoState(seed = i.toLong())
            Sports.session(s, Sport.TABLE_TENNIS, Random(i), at(2026, 10, 1), withNib = true)
            nibWins += s.records["vs:TABLE_TENNIS:opp"] ?: 0
        }
        assertTrue("Nib won $nibWins/30", nibWins in 3..27)
    }

    @Test
    fun cricketOverScoresByTimingAndPipoChases() {
        val sim = CricketSim(4, 0.6f)
        sim.start()
        var guard = 0
        while (sim.phase != CricketSim.Phase.OVER && guard++ < 20000) {
            if (sim.phase == CricketSim.Phase.BOWLING && sim.progress >= 0.9f) sim.swing() // perfect timing
            sim.step(1f / 60f)
        }
        assertEquals(CricketSim.Phase.OVER, sim.phase)
        assertEquals(36, sim.yourRuns) // six sixes
        assertNotNull(sim.pipoWon)
        assertTrue(sim.claimResult()); assertTrue(!sim.claimResult())
    }

    @Test
    fun pongIsWinnableAndEndsAtSeven() {
        val sim = PongSim(9, 0.4f)
        sim.start()
        var guard = 0
        while (sim.phase != ArcadePhase.OVER && guard++ < 200000) { sim.moveTo(sim.ballX); sim.step(1f / 60f) } // a perfect tracker
        assertEquals(ArcadePhase.OVER, sim.phase)
        assertTrue("you ${sim.you} pipo ${sim.pipo}", sim.you == 7 || sim.pipo == 7)
    }
}
