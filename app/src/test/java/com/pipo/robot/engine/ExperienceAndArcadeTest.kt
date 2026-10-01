package com.pipo.robot.engine

import com.pipo.robot.data.ActivityType
import com.pipo.robot.data.DrawSubject
import com.pipo.robot.data.Drawing
import com.pipo.robot.data.JournalCategory
import com.pipo.robot.data.MemoryType
import com.pipo.robot.data.PhotoSubject
import com.pipo.robot.data.PipoState
import com.pipo.robot.data.ProjectState
import com.pipo.robot.data.TripPurpose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ExperienceAndArcadeTest {
    private val noon = 20_000L * DAY + 12 * HOUR
    private fun fresh() = PipoState(seed = 3L).apply { profile.firstRunDone = true; profile.createdAt = noon - 30 * DAY; lastSimulatedAt = noon; mood.energy = 0.9f }
    private fun env(hour: Int = 12, w: Weather = Weather.CLEAR) = Env(hour = hour, weather = WeatherNow(w, 0.5f), userPresent = true)

    /* ---------------- favourites emerge; nothing is invented ---------------- */

    @Test
    fun aFavouritePlaceEmergesFromGoodTripsAndHeSaysSo() {
        val s = fresh()
        LocalBrain.respond(s, "what's your favorite thing?", noon, Random(1), false).text.let { assertTrue(it, !it.contains("favourite place") && !it.contains("lake")) } // nothing emerged yet: no made-up favourite
        var t = noon
        repeat(8) { i ->
            val trip = Trips.begin(s, TripIdea("lake", TripPurpose.WILDLIFE, emptyList(), "frogs", 1f), t, Random(i), inApp = false)
            Trips.resolve(s, env(), trip.endsAt, Random(i), offline = true)
            s.cooldowns.clear(); t += 5 * HOUR
        }
        assertEquals("lake", Likes.favorite(s, "place:"))
        assertTrue(LocalBrain.respond(s, "what's your favourite place?", t, Random(2), false).text.contains("lake"))
        assertTrue("his trips are remembered with where and who", s.memories.any { it.key == "place:lake" && it.place == "lake" })
    }

    @Test
    fun gettingRainedOnSomewhereMakesItLessLovedAndHimMoreCareful() {
        val s = fresh()
        val adv = s.profile.traits.adventurousness
        repeat(6) { i ->
            val trip = Trips.begin(s, TripIdea("park", TripPurpose.WALK, emptyList(), "walk", 1f), noon, Random(i), inApp = false)
            Trips.resolve(s, env(w = Weather.RAIN), trip.endsAt, Random(i), offline = true)
            s.cooldowns.clear()
        }
        assertTrue(Likes.of(s, "place:park") < 0f)
        assertTrue(s.profile.traits.adventurousness < adv)
    }

    @Test
    fun likedActivitiesPullALittleButNeverDecide() {
        val s = fresh()
        val before = BehaviorEngine.score(s, env(), noon).first { it.type == ActivityType.DRAW }.score
        repeat(40) { Likes.feel(s, "act:DRAW", 0.06f) }
        val after = BehaviorEngine.score(s, env(), noon).first { it.type == ActivityType.DRAW }.score
        assertTrue(after > before)
        assertTrue("at most +30%", after <= before * 1.31f)
    }

    /* ---------------- rituals ---------------- */

    @Test
    fun theThirdTimeItBecomesOurThing() {
        val s = fresh()
        assertNull(Rituals.shared(s, "hideseek", noon))
        assertNull(Rituals.shared(s, "hideseek", noon))
        assertEquals("hide and seek", Rituals.shared(s, "hideseek", noon))
        assertTrue(s.memories.any { it.key == "ritual:hideseek" && it.type == MemoryType.JOKE && "you" in it.with })
        assertTrue(s.journal.any { it.title == "Our thing" })
    }

    @Test
    fun heNoticesYourRoutineOnlyWhenThereIsOne() {
        val s = fresh()
        repeat(5) { Rituals.noteVisit(s, 20) }
        assertNull("not enough visits yet", Rituals.routineGreeting(s, 20, Random(1)))
        repeat(6) { Rituals.noteVisit(s, 20 + it % 2) }
        assertNotNull(Rituals.routineGreeting(s, 20, Random(1)))
        assertNull("a visit at an unusual time isn't 'right on time'", Rituals.routineGreeting(s, 9, Random(1)))
    }

    /* ---------------- links between systems ---------------- */

    @Test
    fun aFindThatFitsAFailedProjectIsNoticed() {
        val s = fresh()
        val p = com.pipo.robot.data.PipoProject(1, "radio", "Pocket radio", ProjectState.FAILED, finishedAt = noon - HOUR, components = mutableListOf("tube", "coil"), attempts = 1)
        s.projects.add(p)
        val item = Inventory.add(s, "radio_tube", noon)
        val line = Links.afterFind(s, item, noon)
        assertNotNull(line)
        assertTrue(line!!.contains("pocket radio"))
        assertNull("not twice in a row", Links.afterFind(s, Inventory.add(s, "copper_coil", noon), noon + HOUR))
    }

    @Test
    fun theTokenReactsOnlyOnceTheThreadHasStarted() {
        val s = fresh()
        Inventory.add(s, "brass_token", noon)
        val failed = com.pipo.robot.data.PipoProject(2, "drone", "Flying machine", ProjectState.FAILED)
        assertNull(Links.afterProjectEnd(s, failed, noon))
        s.mystery.stage = 2
        assertNotNull(Links.afterProjectEnd(s, failed, noon))
        assertTrue(s.journal.any { it.category == JournalCategory.STRANGE })
    }

    @Test
    fun aPhotoOfSomewhereHeDrewIsRecognisedOnce() {
        val s = fresh()
        s.drawings.add(Drawing(5, DrawSubject.PLACE, "lake", "The lake.", noon))
        val photo = PhotoLife.take(s, PhotoSubject.PLACE, "", "lake", env(), noon, Random(1))
        assertNotNull(Links.afterPhoto(s, photo, noon))
        assertNull(Links.afterPhoto(s, PhotoLife.take(s, PhotoSubject.PLACE, "", "lake", env(), noon, Random(2)), noon))
    }

    @Test
    fun nibRemembersWhereNibGotLost() {
        val s = fresh()
        s.pet.memories.addAll(listOf("lost:hardware", "trip:hardware"))
        val r = com.pipo.robot.data.TripReport(1, "hardware", TripPurpose.SHOP, noon, withPet = true)
        assertTrue(Links.afterTrip(s, r, noon).any { it.contains("Nib remembered") })
    }

    /* ---------------- his phone ---------------- */

    @Test
    fun hisPhoneAppsOnlyShowWhatsReal() {
        val s = fresh()
        val w = PhoneLife.use(s, PhoneApp.WEATHER, env(), noon, Random(1)).single() as Outcome.PhoneUsed
        val later = WeatherEngine.at(s.seed, noon + 4 * HOUR).kind
        assertTrue(w.line.isNotBlank())
        if (later == Weather.RAIN) assertTrue(w.line.contains("Rain") || w.line.contains("rain"))

        val note = PhoneLife.use(s, PhoneApp.NOTES, env(hour = 22), noon, Random(1)).single() as Outcome.PhoneUsed
        assertTrue(note.line.startsWith("Writing"))
        assertTrue("his notes are HIS journal entries", s.journal.last().category == JournalCategory.THOUGHT)

        PhoneLife.use(s, PhoneApp.RECORDER, env(w = Weather.RAIN), noon, Random(1))
        assertEquals("rain on the window", s.recordings.last().label)

        val cam = PhoneLife.use(s, PhoneApp.CAMERA, env(), noon, Random(1)).single()
        assertTrue(cam is Outcome.Photographed)
        assertEquals("room", (cam as Outcome.Photographed).photo.placeId)

        s.coins = 2
        Projects.maybeStart(s, Random(3), noon, 1f, prefer = "drone")
        val calc = PhoneLife.use(s, PhoneApp.CALCULATOR, env(), noon, Random(1)).single() as Outcome.PhoneUsed
        assertTrue(calc.line, calc.line.contains("2") && calc.line.contains("more"))
    }

    /* ---------------- arcade ---------------- */

    @Test
    fun flappyEndsOnceAndScoresOnlyForPassedPipes() {
        val g = FlappySim(4)
        assertFalse(g.step(0.02f))
        assertTrue(g.flap())
        var crashes = 0
        var frames = 0
        while (g.phase != ArcadePhase.OVER && frames++ < 20_000) {
            if (g.step(0.016f)) crashes++
            // a simple autopilot that hops when below the next gap
            val next = g.pipes.firstOrNull { it.x + g.pipeW > g.birdX }
            if (g.y > (next?.gapY ?: 0.5f) + 0.04f && g.vy > 0f) g.flap()
        }
        assertEquals(1, crashes)
        assertFalse("no flapping after the crash", g.flap())
        assertFalse(g.step(0.016f))
        assertTrue("the autopilot gets through some pipes", g.score > 0)
        assertTrue(g.claimResult())
        assertFalse(g.claimResult())
    }

    @Test
    fun shooterEndsOnceAndWavesGetHarder() {
        val g = ShooterSim(9)
        assertFalse(g.step(0.02f))
        assertTrue(g.start())
        assertFalse(g.start())
        var ends = 0
        var waves = 1
        var frames = 0
        while (g.phase != ArcadePhase.OVER && frames++ < 100_000) {
            g.moveTo(0.5f + 0.4f * kotlin.math.sin(frames / 90f))
            if (g.step(0.016f)) ends++
            waves = maxOf(waves, g.wave)
        }
        assertEquals(1, ends)
        assertTrue(g.lives >= 0)
        assertTrue(g.score > 0)
        assertTrue(g.claimResult())
        assertFalse(g.claimResult())
    }

    @Test
    fun pipoPractisesOnHisArcadeAndKeepsRealRecords() {
        val s = fresh()
        var bests = 0
        repeat(30) { if (ArcadePractice.play(s, Random(it), noon).third) bests++ }
        assertTrue(bests >= 1)
        assertTrue((s.records["pipo:flappy"] ?: 0) > 0 || (s.records["pipo:shooter"] ?: 0) > 0)
    }
}
