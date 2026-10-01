package com.pipo.robot.engine

import com.pipo.robot.data.ActivityType
import com.pipo.robot.data.Migrations
import com.pipo.robot.data.PhotoSubject
import com.pipo.robot.data.PipoProject
import com.pipo.robot.data.PipoState
import com.pipo.robot.data.Places
import com.pipo.robot.data.ProjectState
import com.pipo.robot.data.TripPurpose
import com.pipo.robot.data.TripReport
import com.pipo.robot.data.TripState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** One world, one truth: regressions for the places where systems used to disagree. */
class CohesionTest {
    private val noon = 20_000L * DAY + 12 * HOUR
    private fun fresh() = PipoState(seed = 11L).apply { profile.firstRunDone = true; profile.createdAt = noon - 60 * DAY; lastSimulatedAt = noon; mood.energy = 0.9f }
    private fun env(hour: Int = 12, w: Weather = Weather.CLEAR) = Env(hour = hour, weather = WeatherNow(w, 0.5f), userPresent = true)

    @Test
    fun aDepartureThatNeverHappenedLeavesNoTrip() {
        val s = fresh()
        Trips.begin(s, TripIdea("park", TripPurpose.WALK, emptyList(), "walk", 1f), noon, Random(1), inApp = true)
        Trips.cancel(s)
        assertNull(s.trip)
        // he's home, so talking to him is talking, not texting
        assertTrue(LocalBrain.respond(s, "where are you?", noon, Random(1), false).action != ChatAction.TEXT)
        assertTrue("he can go later", (s.cooldowns["act:GO_OUT"] ?: 0L) <= noon + 15 * MINUTE)
    }

    @Test
    fun theAppOnHisScreenIsTheAppHeUses() {
        for (seed in 1..40) {
            val s = fresh()
            val started = BehaviorEngine.start(s, ActivityType.SCROLL_PHONE, noon, Random(seed), env(), inApp = true)
            if (started != ActivityType.SCROLL_PHONE) continue
            val shown = PhoneApp.valueOf(s.activity.result)
            val outs = BehaviorEngine.complete(s, ActivityType.SCROLL_PHONE, env(), noon + MINUTE, Random(seed + 100), offline = false)
            when (shown) {
                PhoneApp.FEED -> assertTrue(outs.none { it is Outcome.PhoneUsed })
                PhoneApp.CAMERA -> assertTrue(outs.any { it is Outcome.Photographed })
                PhoneApp.GALLERY -> Unit // may be empty with no photos
                else -> assertTrue("seed $seed: showed $shown, used ${outs.filterIsInstance<Outcome.PhoneUsed>().map { it.app }}", outs.filterIsInstance<Outcome.PhoneUsed>().all { it.app == shown })
            }
        }
    }

    @Test
    fun aNamedAnimalRecognisedBetweenHomeAndOutside() {
        val s = fresh()
        val c = com.pipo.robot.data.Creature("sparrow:0", "sparrow", "a sparrow with a crooked tail", name = "Pebble", sightings = 3)
        s.creatures[c.key] = c
        Experience.remember(s, com.pipo.robot.data.MemoryType.EVENT, "I saw Pebble", 0.3f, noon, "sighting:sparrow:0", place = "window")
        val r = TripReport(1, "park", TripPurpose.WALK, noon, creatureKey = "sparrow:0")
        assertTrue(Links.afterTrip(s, r, noon).any { it.contains("Pebble was here too") })
    }

    @Test
    fun aFailureLeavesScrapsUntilHeTidies() {
        val s = fresh()
        s.profile.traits.confidence = 0.05f; s.profile.traits.patience = 0.05f
        var failed = false
        for (seed in 0 until 60) {
            val t = fresh().apply { profile.traits.confidence = 0.05f }
            val p = PipoProject(1, "drone", "Flying machine", ProjectState.BUILDING, progress = 0.99f, components = mutableListOf())
            t.projects.add(p)
            val fin = Projects.work(t, Random(seed), noon)
            if (fin?.state == ProjectState.FAILED) {
                assertEquals("drone", t.world.objectStates["scraps"])
                assertTrue(BehaviorEngine.clutter(t) >= 1)
                assertTrue(BehaviorEngine.clean(t, noon, Random(1))!!.contains("swept up"))
                assertNull(t.world.objectStates["scraps"])
                failed = true; break
            }
        }
        assertTrue(failed)
    }

    @Test
    fun nibVariesItsThefts() {
        val s = fresh()
        Inventory.add(s, "blue_marble", noon); Inventory.add(s, "tiny_gear", noon)
        val first = PetEngine.steal(s, noon, Random(1))!!
        s.pet.stolenItemId = 0L
        repeat(20) {
            val again = PetEngine.steal(s, noon, Random(it))!!
            assertTrue("never the same kind twice in a row ($first → $again)", again != first)
            s.pet.stolenItemId = 0L
            s.pet.memories.removeAll { m -> m.startsWith("stole:") }; s.pet.memories.add("stole:${if (first == "blue marble") "blue_marble" else "tiny_gear"}")
        }
    }

    @Test
    fun nibCanFindThingsAndItBecomesTheirStory() {
        val s = fresh()
        var got: Pair<String, Long>? = null
        for (i in 0 until 200) { s.cooldowns.clear(); got = PetEngine.nibFinds(s, Places.byId("park")!!, Random(i), noon); if (got != null) break }
        assertNotNull(got)
        assertTrue(s.world.items.any { it.id == got!!.second })
        assertTrue(s.memories.any { it.key == "nibpipo:found" && "Nib" in it.with })
    }

    @Test
    fun loadingRepairsDanglingReferencesWithoutInventingAnything() {
        val s = fresh()
        s.pet.stolenItemId = 999
        s.world.objectStates["floor"] = "998"
        s.trip = TripState(1, "park", TripPurpose.WALK, noon, noon + 40 * HOUR, false)
        assertTrue(Migrations.sanitize(s))
        assertEquals(0L, s.pet.stolenItemId)
        assertNull(s.world.objectStates["floor"])
        assertEquals(noon + 6 * HOUR, s.trip!!.endsAt)
    }

    /* ---------------- the other side ---------------- */

    @Test
    fun nibCanFindTheMarkFirst() {
        var ok = false
        for (seed in 0 until 60) {
            val s = fresh()
            s.mystery.stage = 1; s.mystery.dreamDrawingId = 5
            val trip = TripState(1, "park", TripPurpose.WALK, noon, noon, withPet = true)
            val clue = Mystery.onTrip(s, Places.byId("park")!!, trip, noon, Random(seed), env())
            if (clue != null) { assertTrue(clue.contains("Nib")); assertEquals(2, s.mystery.stage); assertTrue(Inventory.owns(s, "brass_token")); ok = true; break }
        }
        assertTrue(ok)
    }

    @Test
    fun theTelescopeIsAnotherWayToTheDome() {
        var ok = false
        for (seed in 0 until 60) {
            val s = fresh()
            s.mystery.stage = 3
            s.projects.add(PipoProject(1, "telescope", "Telescope", ProjectState.DONE))
            if (Mystery.onTelescope(s, env(hour = 22), noon, Random(seed)) != null) { assertEquals(4, s.mystery.stage); assertTrue("observatory" in s.places); ok = true; break }
        }
        assertTrue(ok)
        val noScope = fresh().apply { mystery.stage = 3 }
        repeat(50) { assertNull(Mystery.onTelescope(noScope, env(hour = 22), noon, Random(it))) }
    }

    @Test
    fun theDomePhotoRecordsTheRealMoment() {
        val s = fresh()
        s.mystery.stage = 3
        Mystery.onTrip(s, Places.byId("hills")!!, TripState(1, "hills", TripPurpose.EXPLORE, noon, noon, false), noon, Random(1), env(hour = 17, w = Weather.FOG))
        val p = s.photos.last { it.subject == PhotoSubject.PLACE && it.placeId == "observatory" }
        assertEquals(17, p.hour)
        assertEquals("FOG", p.weather)
    }

    @Test
    fun theOtherPipoIsMetOnceAndGivesSomethingThatStaysStrange() {
        val s = fresh()
        s.mystery.stage = 5
        val other = Places.byId("otherside")!!
        val trip = TripState(1, "otherside", TripPurpose.INVESTIGATE, noon, noon, withPet = true)
        val lines = (0 until 6).map { Mystery.onTrip(s, other, trip, noon + it * 10 * DAY, Random(it), env())!! }
        assertEquals("met exactly once", 1, lines.count { it.contains("He was there") })
        assertTrue(lines[2].contains("He was there"))
        assertTrue(Inventory.owns(s, "mirror_screw"))
        assertTrue("Nib was there for it", s.memories.any { it.key == "nibpipo:strange" })
        // the details differ visit to visit
        assertEquals(lines.size, lines.distinct().size)
        // and the screw is never used up as a building part
        assertFalse("mirror" in Inventory.freeTags(s))
        val p = Projects.maybeStart(s, Random(1), noon, 1f, prefer = "kicker")
        if (p != null) assertTrue(s.world.items.filter { it.usedInProjectId == p.id }.none { it.catalogId == "mirror_screw" })
    }
}
