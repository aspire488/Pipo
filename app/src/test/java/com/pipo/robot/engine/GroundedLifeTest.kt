package com.pipo.robot.engine

import com.pipo.robot.data.ActivityType
import com.pipo.robot.data.JournalCategory
import com.pipo.robot.data.MemoryType
import com.pipo.robot.data.PipoState
import com.pipo.robot.data.TripPurpose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Regressions from live-device testing: he knows his own life, and does reasonable things you ask. */
class GroundedLifeTest {
    private val noon = 20_000L * DAY + 12 * HOUR
    private fun fresh() = PipoState(seed = 11L).apply { profile.firstRunDone = true; profile.createdAt = noon - 60 * DAY; lastSimulatedAt = noon; mood.energy = 0.8f }

    @Test
    fun yesterdayIsAnsweredFromTheJournal() {
        val s = fresh()
        Chronicle.journal(s, "Pipo went to the football field", "Two goals. One of them leans.", JournalCategory.PLACE, noon - DAY)
        Chronicle.journal(s, "Pipo cooked honey toast", "First try.", JournalCategory.FOOD, noon - DAY + HOUR)
        val r = LocalBrain.respond(s, "what did we do yesterday?", noon, Random(1), false)
        assertTrue(r.text, r.text.contains("football field") && r.text.contains("honey toast"))
    }

    @Test
    fun askingHimOutRightAfterATripIsNotRefused() {
        val s = fresh()
        s.cooldowns["act:GO_OUT"] = noon + 2 * HOUR // he just got back: his OWN urge to go is on cooldown
        val env = Env(hour = 12, weather = WeatherNow(Weather.CLEAR, 0.5f), userPresent = true)
        assertTrue("his own urge is gated", Trips.best(s, env, noon) == null)
        val r = LocalBrain.respond(s, "let's go to the park", noon, Random(1), false)
        assertEquals(r.text, ChatAction.GO_OUT, r.action)
        assertEquals("park", Trips.forUser(s, env, noon, r.payload)!!.placeId)
    }

    @Test
    fun cookingWithAnEmptyKitchenMeansGoingShopping() {
        val s = fresh()
        s.pantry.clear(); s.coins = 20
        val r = LocalBrain.respond(s, "let's cook dinner", noon, Random(1), false)
        assertEquals(r.text, ChatAction.GO_OUT, r.action)
        val idea = Trips.forUser(s, Env(hour = 12, weather = WeatherNow(Weather.CLEAR, 0.5f), userPresent = true), noon, r.payload)
        assertNotNull(idea)
        assertTrue(idea!!.purpose == TripPurpose.FOOD || idea.purpose == TripPurpose.SHOP)
    }

    @Test
    fun askedToShopLateHeNeverWandersOffToTheGarden() {
        // seen live at 21:07: "let's cook" → nothing to cook → market closed → he went to look at bugs
        val s = fresh().apply { pantry.clear(); pantry.addAll(listOf("noodles", "noodles")); coins = 23 }
        val late = Env(hour = 21, weather = WeatherNow(Weather.CLEAR, 0.5f), userPresent = true)
        val idea = Trips.forUser(s, late, noon, "groceries")
        if (idea != null) assertTrue("went to ${idea.placeId}", idea.placeId in setOf("market", "bakery", "cafe") && com.pipo.robot.data.Places.byId(idea.placeId)!!.openAt(21))
        else assertNotNull(Trips.closedLine("groceries", late))
        // asking for a closed shop by name gets an honest answer (the chat stops there, no trip)
        assertTrue(Trips.closedLine("go to the bakery", late)!!.contains("closed"))
        val r = LocalBrain.respond(s, "go to the bakery", noon, kotlin.random.Random(1), false)
        assertTrue(r.text, r.action != ChatAction.GO_OUT && r.text.contains("closed"))
    }

    @Test
    fun aNamedPlaceIsWhereHeGoes() {
        val s = fresh()
        val env = Env(hour = 12, weather = WeatherNow(Weather.CLEAR, 0.5f), userPresent = true)
        assertEquals("market", Trips.forUser(s, env, noon, "go to the market")!!.placeId)
        assertEquals("field", Trips.forUser(s, env, noon, "go play football outside")!!.placeId)
        // real reasons still apply
        assertNotNull(Trips.whyNot(s, env.copy(hour = 23), noon))
    }

    @Test
    fun theVoiceGetsTheMemoryYouAskedAbout() {
        val s = fresh()
        repeat(12) { Chronicle.remember(s, MemoryType.EVENT, "I sat by the window $it", 0.9f, noon - it * MINUTE, "w$it") }
        Chronicle.remember(s, MemoryType.PLACE, "I got a rubber duck at Old Rook's", 0.3f, noon - 3 * DAY, "duck")
        val m = Grounding.memoriesFor(s, "remember the rubber duck?", noon)
        assertTrue(m.first(), m.first().contains("rubber duck"))
        val facts = Grounding.facts(s, "", noon)
        assertTrue(facts.any { it.contains("kitchen") })
    }

    @Test
    fun aFullBatteryDoesntKeepChargingJustBecauseYourPhoneIs() {
        // seen live: phone on the charger → he charged 3 times in 7 minutes, right after "Fully charged"
        val env = Env(hour = 12, charging = true, userPresent = true)
        var charges = 0
        var now = noon
        repeat(40) { i ->
            val s = fresh().apply { mood.energy = 0.95f }
            if (BehaviorEngine.choose(s, env, now, Random(i), null) == ActivityType.CHARGE) charges++
            now += MINUTE
        }
        assertTrue("charged $charges/40 times on a full battery", charges <= 8)
    }

    @Test
    fun hollowFillerWordsAreNotSavedAsTopics() {
        val s = fresh()
        LocalBrain.respond(s, "gonna wanna", noon, Random(1), false)
        assertTrue(s.memories.none { it.content.contains("gonna") || it.content.contains("wanna") })
    }

    @Test
    fun flappyScoreAdvancesAsPipesArePassed() {
        val sim = FlappySim(3)
        sim.flap()
        var t = 0f
        // steer into the middle of each gap: a perfect player scores
        while (t < 20f && sim.phase != ArcadePhase.OVER) {
            val next = sim.pipes.firstOrNull { it.x + sim.pipeW > sim.birdX - sim.radius }
            val goal = next?.gapY ?: 0.5f
            if (sim.y > goal + 0.03f && sim.vy > -0.2f) sim.flap()
            sim.step(1f / 60f); t += 1f / 60f
        }
        assertNotEquals(0, sim.score)
    }
}
