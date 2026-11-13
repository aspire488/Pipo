package com.pipo.robot.engine

import com.pipo.robot.data.Daytime
import com.pipo.robot.data.PipoState
import com.pipo.robot.data.Places
import com.pipo.robot.data.Wildlife
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Forest Hills: a real outdoor place with its own animals, reachable by asking. */
class ForestHillsTest {
    private val noon = 20_000L * DAY + 12 * HOUR
    private fun fresh() = PipoState(seed = 11L).apply { profile.firstRunDone = true; profile.createdAt = noon - 60 * DAY; lastSimulatedAt = noon; mood.energy = 0.9f }
    private fun env(hour: Int = 12) = Env(hour = hour, weather = WeatherNow(Weather.CLEAR, 0.5f), userPresent = true)

    @Test
    fun forestHillsIsAnOutdoorPlaceWhoseAnimalsAllExist() {
        val f = Places.byId("forest")
        assertNotNull(f)
        assertTrue(f!!.outdoor)
        for (sp in f.wildlife) assertNotNull("species $sp", Wildlife.byId(sp))
        assertTrue("birds live there", f.wildlife.containsAll(listOf("robin", "woodpecker", "owl", "sparrow")))
        assertTrue("and forest animals", f.wildlife.containsAll(listOf("deer", "rabbit", "fox")))
        // night/dusk animals are tagged so they only turn up at the right time
        assertEquals(Daytime.DUSK, Wildlife.byId("owl")!!.daytime)
    }

    @Test
    fun askingForTheForestGoesToTheForestNotTheHills() {
        for (ask in listOf("let's go to forest hills", "can we go to the woods?", "go see the deer")) {
            val idea = Trips.forUser(fresh(), env(), noon, ask)
            assertEquals(ask, "forest", idea?.placeId)
        }
        assertEquals("hills", Trips.forUser(fresh(), env(), noon, "go to the hills")?.placeId)
    }

    @Test
    fun theChatIntentLeavesForTheForest() {
        val r = LocalBrain.respond(fresh(), "let's go to forest hills", noon, Random(4), false)
        assertEquals(ChatAction.GO_OUT, r.action)
    }
}
