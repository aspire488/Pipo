package com.pipo.robot.engine

import com.pipo.robot.data.ActivityType
import com.pipo.robot.data.Catalog
import com.pipo.robot.data.Foods
import com.pipo.robot.data.PipoState
import com.pipo.robot.data.Places
import com.pipo.robot.data.ProjectState
import com.pipo.robot.data.TripPurpose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LifeTest {
    private val noon = 20_000L * DAY + 12 * HOUR

    private fun fresh(): PipoState = PipoState(seed = 42L).apply {
        profile.firstRunDone = true
        profile.createdAt = noon - 30 * DAY
        lastSimulatedAt = noon
        mood.energy = 0.8f
    }

    private fun env(hour: Int = 12, w: Weather = Weather.CLEAR) = Env(hour = hour, weather = WeatherNow(w, 0.6f), userPresent = true)

    /* ---------------- weather ---------------- */

    @Test
    fun weatherIsDeterministicAndVaried() {
        assertEquals(WeatherEngine.at(7L, 100L, 14), WeatherEngine.at(7L, 100L, 14))
        val seen = mutableMapOf<Weather, Int>()
        for (d in 0L until 2000L) for (h in listOf(8, 14, 20)) WeatherEngine.at(99L, d, h).kind.let { seen[it] = (seen[it] ?: 0) + 1 }
        assertEquals("every kind of weather happens", Weather.entries.toSet(), seen.keys)
        val total = seen.values.sum().toFloat()
        assertTrue("storms are the exception (${seen[Weather.STORM]})", (seen[Weather.STORM] ?: 0) / total < 0.12f)
        assertTrue("clear days are common", (seen[Weather.CLEAR] ?: 0) / total > 0.2f)
    }

    /* ---------------- trips ---------------- */

    @Test
    fun noTripsAtNightInStormsOrWhileAlreadyOut() {
        val s = fresh()
        assertTrue(Trips.ideas(s, env(hour = 2), noon).isEmpty())
        assertTrue(Trips.ideas(s, env(w = Weather.STORM), noon).isEmpty())
        val idea = Trips.best(s, env(), noon)!!
        Trips.begin(s, idea, noon, Random(1), inApp = true)
        assertTrue(Trips.ideas(s, env(), noon + 1000).isEmpty())
    }

    @Test
    fun rainKeepsHimInUnlessHeHasAnUmbrella() {
        val s = fresh()
        assertTrue(Trips.ideas(s, env(w = Weather.RAIN), noon).none { Places.byId(it.placeId)!!.outdoor })
        Inventory.add(s, "umbrella", noon)
        val shops = Trips.ideas(s, env(w = Weather.RAIN), noon).filter { !Places.byId(it.placeId)!!.outdoor }
        assertTrue(shops.isNotEmpty() || Trips.ideas(s, env(w = Weather.RAIN), noon).isEmpty())
    }

    @Test
    fun aProjectThatNeedsAMotorSendsHimToTheElectronicsShop() {
        val s = fresh()
        s.coins = 30
        val p = Projects.maybeStart(s, Random(3), noon, 1f, prefer = "drone")!!
        assertEquals("drone", p.templateId)
        assertEquals(ProjectState.GATHERING, p.state)
        val needed = Trips.materialsNeeded(s)
        assertTrue("motor" in needed)
        val shop = Trips.ideas(s, env(), noon).first { it.purpose == TripPurpose.SHOP }
        assertEquals("electronics", shop.placeId)
        assertTrue(shop.reason.contains("flying machine"))
    }

    @Test
    fun hesTooBrokeForTheMotorSoHeGoesToWorkInstead() {
        val s = fresh()
        s.coins = 1
        Projects.maybeStart(s, Random(3), noon, 1f, prefer = "drone")
        val ideas = Trips.ideas(s, env(hour = 11), noon)
        assertTrue(ideas.any { it.purpose == TripPurpose.ODD_JOB && it.placeId == "repair" })
        assertTrue(ideas.none { it.purpose == TripPurpose.SHOP && "motor" in it.list })
    }

    @Test
    fun shoppingSpendsRealCoinsAndPutsThingsWhereTheyBelong() {
        for (seed in 1..40) {
            val s = fresh()
            s.coins = 25
            s.pantry.clear()
            s.mood.appetite = 0.9f
            val idea = Trips.ideas(s, env(hour = 10), noon).first { it.purpose == TripPurpose.FOOD }
            val trip = Trips.begin(s, idea, noon, Random(seed), inApp = false)
            val before = s.coins
            val r = Trips.resolve(s, env(hour = 11), trip.endsAt, Random(seed), offline = true)
            assertNull(s.trip)
            assertEquals("seed $seed", before - r.spent, s.coins)
            assertEquals(r.bought.sumOf { Economy.priceOf(it) }, r.spent)
            assertTrue(s.coins >= 0)
            val place = Places.byId(r.placeId)!!
            assertTrue("only buys what that shop sells", r.bought.all { it in place.sells })
            r.bought.filter { Foods.byId(it) != null }.forEach { assertTrue(it in s.pantry) }
            if (r.wrongBuy.isNotEmpty()) assertTrue(r.wrongBuy in place.sells)
            assertEquals(r, s.lastTrip)
            assertTrue(s.places[r.placeId]!!.visits >= 1)
        }
    }

    @Test
    fun oddJobsEarnCoinsAndYouCanCallHimHome() {
        val s = fresh()
        s.coins = 0
        val t = Trips.begin(s, TripIdea("repair", TripPurpose.ODD_JOB, emptyList(), "coins", 1f), noon, Random(1), inApp = false)
        Trips.hurryHome(s, noon + 1000)
        assertTrue(s.trip!!.endsAt <= noon + 26_000)
        val r = Trips.resolve(s, env(), noon + 30_000, Random(1), offline = true)
        assertTrue(r.earned in 5..10)
        assertEquals(r.earned, s.coins)
        assertTrue(t.id == r.tripId)
    }

    @Test
    fun digestAndDoorNoteNeverLie() {
        val s = fresh()
        val t = Trips.begin(s, TripIdea("park", TripPurpose.WALK, emptyList(), "legs", 1f), noon, Random(2), inApp = false)
        assertTrue(Trips.doorNote(t, false).contains("the park"))
        val r = Trips.resolve(s, env(), t.endsAt, Random(2), offline = true)
        assertTrue(Trips.digest(r, s).contains("park"))
    }

    /* ---------------- food ---------------- */

    @Test
    fun rainMakesSoupCravingsAndCookingUsesTheIngredients() {
        val s = fresh()
        s.mood.appetite = 0.6f
        var craved = false
        for (i in 0 until 30) { s.craving = ""; if (FoodLife.maybeCrave(s, env(w = Weather.RAIN), Random(i)) && s.craving == "noodle_soup") { craved = true; break } }
        assertTrue("rain → soup", craved)
        s.pantry.clear(); s.pantry.addAll(listOf("noodles", "egg"))
        assertEquals(listOf("noodle_soup"), FoodLife.feasible(s).map { it.id })
        val outs = FoodLife.cook(s, noon, Random(5), offline = false)
        assertTrue(outs.any { it is Outcome.Cooked })
        assertTrue(outs.any { it is Outcome.Ate })
        assertTrue("ingredients used", "noodles" !in s.pantry && "egg" !in s.pantry)
        assertEquals("", s.craving)
        assertTrue(s.mood.appetite < 0.3f)
        assertTrue("he learned something about the dish", s.tastes.containsKey("noodle_soup"))
    }

    @Test
    fun eatingTakesFromThePantryAndNeverInventsFood() {
        val s = fresh()
        s.pantry.clear()
        assertTrue(FoodLife.eat(s, noon, Random(1), false).isEmpty())
        s.pantry.add("apple")
        s.mood.appetite = 0.8f
        val outs = FoodLife.eat(s, noon, Random(1), false)
        assertTrue(outs.first() is Outcome.Ate)
        assertTrue(s.pantry.isEmpty())
    }

    @Test
    fun cravingAnUnavailableDishTurnsIntoAShoppingList() {
        val s = fresh()
        s.pantry.clear()
        s.craving = "pizza_toast"
        assertEquals(setOf("bread", "tomato", "cheese"), FoodLife.missingFor(s, "pizza_toast").toSet())
        assertTrue(Trips.foodNeeded(s).containsAll(listOf("bread", "tomato", "cheese")))
    }

    /* ---------------- materials → projects ---------------- */

    @Test
    fun boughtMaterialsGetBuiltIntoTheProject() {
        val s = fresh()
        s.coins = 100
        val p = Projects.maybeStart(s, Random(3), noon, 1f, prefer = "feeder")!!
        var guard = 0
        var t = noon
        while (Trips.materialsNeeded(s).isNotEmpty() && guard++ < 6) {
            val idea = Trips.ideas(s, env(hour = 11), t).first { it.purpose == TripPurpose.SHOP }
            val trip = Trips.begin(s, idea.copy(), t, Random(guard), inApp = false)
            // no distractions for this test: buy exactly the list
            Trips.resolve(s, env(hour = 11), trip.endsAt, Random(1000 + guard), offline = true)
            s.cooldowns.remove("act:GO_OUT"); s.cooldowns.remove("trips:n"); s.mood.energy = 0.8f
            t += 3 * HOUR
        }
        Projects.gather(s, p)
        assertEquals(ProjectState.BUILDING, p.state)
        var fin: com.pipo.robot.data.PipoProject? = null
        repeat(40) { if (fin == null) fin = Projects.work(s, Random(it), t + it * HOUR) }
        assertNotNull(fin)
        assertTrue(fin!!.state in setOf(ProjectState.DONE, ProjectState.EVOLVED, ProjectState.FAILED))
    }

    /** Regression: with every part in his room, a project sat in GATHERING forever (only EXPERIMENT gathered). */
    @Test
    fun ownedPartsGetAssembledWithoutWaitingForAnExperiment() {
        val s = fresh()
        Inventory.add(s, "copper_coil", noon); Inventory.add(s, "radio_tube", noon)
        val p = Projects.maybeStart(s, Random(1), noon, 1f, prefer = "radio")!!
        // maybeStart already puts owned parts on the bench
        assertEquals(ProjectState.BUILDING, p.state)
        val q = fresh()
        val r = Projects.maybeStart(q, Random(1), noon, 1f, prefer = "radio")!!
        assertEquals(ProjectState.GATHERING, r.state)
        Inventory.add(q, "copper_coil", noon); Inventory.add(q, "cardboard_tube", noon)
        assertTrue(Projects.readyToAssemble(q, r))
        assertTrue("BUILD is on the table", BehaviorEngine.score(q, env(), noon).first { it.type == ActivityType.BUILD }.score > 0f)
        Projects.work(q, Random(2), noon)
        assertEquals(ProjectState.BUILDING, r.state)
    }

    @Test
    fun shopOnlyThingsAreNeverFoundLyingAround() {
        val s = fresh()
        repeat(300) { Discovery.roll(s, Random(it), noon + it)?.let { item -> assertTrue(Catalog.item(item.catalogId)!!.shopOnly.not()) } }
    }

    @Test
    fun goingOutFromTheBehaviorEngineCreatesARealTrip() {
        val s = fresh()
        val started = BehaviorEngine.start(s, ActivityType.GO_OUT, noon, Random(1), env(), inApp = true)
        assertEquals(ActivityType.GO_OUT, started)
        assertNotNull(s.trip)
        assertEquals(s.trip!!.endsAt - noon, s.activity.durationMs)
        val none = fresh()
        assertEquals("nowhere to go at 3am → he stays in", ActivityType.NOTHING, BehaviorEngine.start(none, ActivityType.GO_OUT, noon, Random(1), env(hour = 3), inApp = true))
    }
}
