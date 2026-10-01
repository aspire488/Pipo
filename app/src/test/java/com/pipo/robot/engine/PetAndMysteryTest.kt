package com.pipo.robot.engine

import com.pipo.robot.data.JournalCategory
import com.pipo.robot.data.PetActivity
import com.pipo.robot.data.PipoState
import com.pipo.robot.data.Places
import com.pipo.robot.data.TripPurpose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PetAndMysteryTest {
    private val start = 20_000L * DAY + 9 * HOUR

    private fun fresh(ageDays: Int = 30): PipoState = PipoState(seed = 5L).apply {
        profile.firstRunDone = true
        profile.createdAt = start - ageDays * DAY
        lastSimulatedAt = start
    }

    /* ---------------- Nib ---------------- */

    @Test
    fun nibsTraitsStayInRangeAndNibHasMoods() {
        repeat(50) { i ->
            val t = PetEngine.newTraits(Random(i))
            listOf(t.curiosity, t.playfulness, t.mischief, t.bravery, t.cuddly).forEach { assertTrue(it in 0.1f..0.95f) }
        }
        val s = fresh()
        s.pet.energy = 0.1f
        assertEquals(com.pipo.robot.data.PetMood.SLEEPY, PetEngine.derive(s))
    }

    @Test
    fun nibStealsAndPipoFindsItUnderTheBed() {
        val s = fresh()
        Inventory.add(s, "blue_marble", start)
        val name = PetEngine.steal(s, start, Random(1))
        assertEquals("blue marble", name)
        assertTrue(s.pet.stolenItemId != 0L)
        assertTrue("a missing thing counts as mess to sort out", BehaviorEngine.clutter(s) >= 1)
        val line = BehaviorEngine.clean(s, start, Random(1))!!
        assertTrue(line.contains("blue marble"))
        assertEquals(0L, s.pet.stolenItemId)
    }

    @Test
    fun nibsOfflineMischiefIsRareAndLeavesEvidence() {
        val s = fresh()
        Inventory.add(s, "tiny_gear", start); Inventory.add(s, "little_spring", start)
        var events = 0
        var t = start
        repeat(24 * 3 * 3) { // three days of 20-minute steps
            if (PetEngine.offlineStep(s, Env(hour = hourOf(t)), t, Random(it)) != null) events++
            t += 20 * MINUTE
        }
        assertTrue("some life ($events)", events >= 1)
        assertTrue("but not a circus ($events in 3 days)", events <= 8)
        val evidence = listOf("floor", "pet_bed").any { s.world.objectStates[it] != null } || s.pet.stolenItemId != 0L || s.activeProject() != null
        assertTrue(evidence)
    }

    @Test
    fun aPipoFromBeforeNibMeetsNibOnHisNextTripOrWithinADay() {
        val s = fresh()
        s.pet.adopted = false; s.migratedAt = start
        assertNull(PetEngine.maybeArrive(s, start + HOUR))
        val trip = Trips.begin(s, TripIdea("park", TripPurpose.WALK, emptyList(), "walk", 1f), start, Random(1), inApp = false)
        val r = Trips.resolve(s, Env(hour = 10), trip.endsAt, Random(1), offline = true)
        assertTrue(s.pet.adopted)
        assertTrue(r.story.any { it.contains("Nib") })
        assertTrue(s.journal.any { it.category == JournalCategory.PET })

        val other = fresh()
        other.pet.adopted = false; other.migratedAt = start
        assertNotNull(PetEngine.maybeArrive(other, start + 21 * HOUR))
        assertTrue(other.pet.adopted)
        assertEquals(PetActivity.FOLLOW, other.pet.activity)
    }

    /* ---------------- the mystery ---------------- */

    @Test
    fun nothingStrangeHappensInTheFirstDays() {
        val s = fresh(ageDays = 2)
        val env = Env(hour = 23)
        repeat(500) { assertNull(Mystery.onNightWindow(s, env, start + it * MINUTE, Random(it), 1f)) }
        assertEquals(0, s.mystery.stage)
    }

    @Test
    fun theThreadMovesOnlyWithDaysBetweenStepsAndInOrder() {
        val s = fresh()
        val rng = Random(9)
        var t = start
        // 0 → 1: a night at the window
        assertNotNull(Mystery.onNightWindow(s, Env(hour = 23), t, rng, 1f))
        assertEquals(1, s.mystery.stage)
        assertNull("only once", Mystery.onNightWindow(s, Env(hour = 23), t + DAY * 5, rng, 1f))
        // the dream gets drawn
        assertTrue(Mystery.wantsToDrawDream(s))
        val d = DrawingLife.draw(s, t + HOUR, rng)
        assertEquals(com.pipo.robot.data.DrawSubject.BUILDING, d.subject)
        assertFalse(Mystery.wantsToDrawDream(s))
        // 1 → 2 needs two days to pass
        val shop = Places.byId("secondhand")!!
        val trip = com.pipo.robot.data.TripState(1, "secondhand", TripPurpose.EXPLORE, t, t, false)
        assertNull("too soon after the signal", Mystery.onTrip(s, shop, trip, t + DAY, rng))
        t += Mystery.GAP + HOUR
        var clue: String? = null
        for (i in 0 until 20) { clue = Mystery.onTrip(s, shop, trip, t, Random(i)); if (clue != null) break }
        assertNotNull(clue)
        assertEquals(2, s.mystery.stage)
        assertTrue(Inventory.owns(s, "brass_token"))
        // 2 → 3: an old photo is wrong
        PhotoLife.take(s, com.pipo.robot.data.PhotoSubject.PLACE, "", "hills", Env(hour = 12), t - 3 * DAY, rng)
        assertNull("too soon", Mystery.onLookAtPhotos(s, t + HOUR, Random(1)))
        t += Mystery.GAP + HOUR
        var wrong: com.pipo.robot.data.Photo? = null
        for (i in 0 until 20) { wrong = Mystery.onLookAtPhotos(s, t, Random(i)); if (wrong != null) break }
        assertNotNull(wrong)
        assertTrue(wrong!!.anomaly)
        assertEquals(3, s.mystery.stage)
        assertTrue(Mystery.reflectionUneasy(s))
        // 3 → 4: the hills
        t += Mystery.GAP + HOUR
        assertNotNull(Mystery.onTrip(s, Places.byId("hills")!!, trip.copy(placeId = "hills"), t, rng))
        assertEquals(4, s.mystery.stage)
        assertTrue("observatory is on his map now", s.places.containsKey("observatory"))
        // 4 → 5: the token opens the dome
        t += Mystery.GAP + HOUR
        assertNotNull(Mystery.onTrip(s, Places.byId("observatory")!!, trip.copy(placeId = "observatory"), t, rng))
        assertEquals(5, s.mystery.stage)
        // the other side: notes from someone with a different life
        val note = Mystery.onTrip(s, Places.byId("otherside")!!, trip.copy(placeId = "otherside"), t + DAY, rng)!!
        assertTrue(note.contains("— P."))
        assertEquals(1, s.mystery.notesFromOtherSide)
        // every step is in the journal as STRANGE, never as a popup or quest
        assertTrue(s.journal.count { it.category == JournalCategory.STRANGE } >= 5)
    }

    @Test
    fun overMonthsTheMysteryIsRareAndPaced() {
        var progressed = 0
        for (seed in 1..8) {
            val rng = Random(seed)
            val s = PipoState(seed = seed.toLong())
            s.profile.firstRunDone = true
            var now = start
            s.profile.createdAt = now
            s.lastSimulatedAt = now
            val clueTimes = mutableListOf<Long>()
            var lastStage = 0
            repeat(120 * 24) {
                now += HOUR
                Simulator.catchUp(s, now, rng)
                if (s.mystery.stage != lastStage) { clueTimes += now; lastStage = s.mystery.stage }
            }
            if (clueTimes.isNotEmpty()) {
                assertTrue("seed $seed: first clue after ${ (clueTimes.first() - start) / DAY } days", clueTimes.first() - start >= Mystery.FIRST_AFTER_DAYS * DAY)
                clueTimes.zipWithNext().forEach { (a, b) -> assertTrue("seed $seed: clues ${ (b - a) / HOUR }h apart", b - a >= Mystery.GAP - HOUR) }
            }
            if (s.mystery.stage >= 2) progressed++
            println("seed $seed: mystery stage ${s.mystery.stage} after 120 days; clues ${s.mystery.clues}")
        }
        assertTrue("over four months, the thread moves for some Pipos ($progressed/8)", progressed >= 1)
    }
}
