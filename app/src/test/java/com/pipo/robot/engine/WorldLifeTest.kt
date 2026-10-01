package com.pipo.robot.engine

import com.pipo.robot.data.ActivityType
import com.pipo.robot.data.EventType
import com.pipo.robot.data.JournalCategory
import com.pipo.robot.data.MemoryType
import com.pipo.robot.data.PendingEvent
import com.pipo.robot.data.PipoState
import com.pipo.robot.data.TripPurpose
import com.pipo.robot.voice.SoundSynth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class WorldLifeTest {
    private val start = 20_000L * DAY + 8 * HOUR

    private fun fresh() = PipoState(seed = 77L).apply {
        profile.firstRunDone = true
        profile.createdAt = start - 20 * DAY
        lastSimulatedAt = start
        settings.quietStartHour = 0; settings.quietEndHour = 0
    }

    /* ---------------- where is he? ---------------- */

    @Test
    fun whenHesOutYouGetANoteNotAGreeting() {
        val s = fresh()
        Trips.begin(s, TripIdea("bakery", TripPurpose.FOOD, listOf("bread"), "I want bread.", 1f), start, Random(1), inApp = false)
        val g = Greeter.plan(s, start + 10 * MINUTE, 5 * HOUR, Random(1))
        assertEquals(GreetKind.AWAY, g.kind)
        assertTrue(g.line.contains("Mo's Bakery"))
    }

    @Test
    fun hidingMeansSilenceUntilYouFindHim() {
        val s = fresh()
        s.activity.type = ActivityType.HIDE
        assertEquals(GreetKind.HIDING, Greeter.plan(s, start, 5 * HOUR, Random(1)).kind)
    }

    @Test
    fun catchUpCanLeaveHimOutAndBringsHimBackLater() {
        // a trip that started just before you opened the app is still going on
        val s = fresh()
        val trip = Trips.begin(s, TripIdea("lake", TripPurpose.WILDLIFE, emptyList(), "frogs", 1f), start, Random(2), inApp = false)
        Simulator.catchUp(s, start + 5 * MINUTE, Random(2))
        assertTrue("still out", s.trip != null)
        Simulator.catchUp(s, trip.endsAt + 2 * MINUTE, Random(2))
        assertTrue("home again", s.trip == null)
        assertTrue(s.lastTrip!!.placeId == "lake")
        assertTrue(s.awayLog.any { it.contains("lake") })
    }

    /* ---------------- talking ---------------- */

    @Test
    fun textingHimWhileHesOutAndAskingHimHome() {
        val s = fresh()
        Trips.begin(s, TripIdea("park", TripPurpose.WALK, emptyList(), "My legs said so.", 1f), start, Random(1), inApp = true)
        val where = LocalBrain.respond(s, "where are you?", start, Random(1), false)
        assertEquals(ChatAction.TEXT, where.action)
        assertTrue(where.text.contains("park"))
        val home = LocalBrain.respond(s, "come home please", start, Random(1), false)
        assertEquals(ChatAction.COME_HOME, home.action)
    }

    @Test
    fun heOnlyRemembersWhatActuallyHappened() {
        val s = fresh()
        Chronicle.remember(s, MemoryType.PLACE, "I went to the lake", 0.5f, start, "place:lake")
        val yes = LocalBrain.respond(s, "do you remember the lake?", start, Random(1), false)
        assertTrue(yes.text, yes.text.contains("lake"))
        val no = LocalBrain.respond(s, "do you remember when we went to the moon?", start, Random(1), false)
        assertTrue(no.text, !no.text.contains("moon") && (no.text.contains("fuzzy") || no.text.contains("Tell me")))  // honest, not invented, not a flat "no"
    }

    @Test
    fun lifeQuestionsAreAnsweredFromState() {
        val s = fresh()
        s.coins = 13
        assertTrue(LocalBrain.respond(s, "how much money do you have", start, Random(1), false).text.contains("13"))
        s.craving = "noodle_soup"; s.cravingReason = "It's raining."
        assertTrue(LocalBrain.respond(s, "are you hungry?", start, Random(1), false).text.contains("noodle soup"))
        // he may refuse when grumpy (that's him), but "draw" never falls through to small talk
        assertTrue(LocalBrain.respond(s, "draw me something", start, Random(3), false).action in setOf(ChatAction.DRAW, ChatAction.ANNOYED))
    }

    /* ---------------- notifications ---------------- */

    @Test
    fun newNotificationsAreAboutRealThingsAndNeverGuilt() {
        val s = fresh()
        s.profile.userName = "Joel"
        val banned = listOf("abandon", "miss you", "lonely", "why didn't you", "you left", "forgot me", "come back")
        for (type in listOf(EventType.STRANGE, EventType.PET, EventType.TRIP, EventType.COOKED)) {
            for (mood in com.pipo.robot.data.Mood.entries) for (i in 0 until 5) {
                val text = NotificationPolicy.text(PendingEvent(i.toLong(), type, 0.6f, start, "x", mood), s, start + i * HOUR, Random(i))
                assertTrue(text.isNotBlank())
                banned.forEach { b -> assertFalse("'$text' ($type)", text.lowercase().contains(b)) }
            }
        }
        s.lastTrip = com.pipo.robot.data.TripReport(5, "hardware", TripPurpose.SHOP, start, story = listOf("Went for wire. Came back with a magnet. Don't ask."))
        assertEquals("Went for wire. Came back with a magnet. Don't ask.",
            NotificationPolicy.text(PendingEvent(1, EventType.TRIP, 0.5f, start, "5"), s, start, Random(1)))
    }

    /* ---------------- humming ---------------- */

    @Test
    fun aHumIsATuneNotLetters() {
        for (seed in 0 until 200) {
            val tune = SoundSynth.humTune(Random(seed))
            assertTrue(tune.size in 6..9)
            assertTrue("toddler-ish range", tune.all { it in 280f..700f })
            val base = tune.last()
            assertTrue("it ends where it started (home note)", base in 300f..340f)
            // every note is on the pentatonic scale above the home note
            val ratios = floatArrayOf(1f, 9f / 8f, 5f / 4f, 3f / 2f, 5f / 3f, 2f)
            tune.forEach { f -> assertTrue(ratios.any { r -> kotlin.math.abs(f / base - r) < 0.001f }) }
            assertTrue("it moves", tune.distinct().size >= 2)
        }
    }

    /* ---------------- a whole life ---------------- */

    @Test
    fun threeWeeksOfOrdinaryLifeHangTogether() {
        var namedSomething = false
        for (seed in 1..6) {
            val rng = Random(seed)
            val s = PipoState(seed = seed * 1000L)
            s.profile.firstRunDone = true
            var now = start
            s.profile.createdAt = now; s.lastSimulatedAt = now
            s.profile.traits = Personality.newTraits(Random(seed))
            var maxCoins = s.coins
            var outHours = 0
            repeat(21 * 24) {
                now += HOUR
                Simulator.catchUp(s, now, rng)
                if (s.trip != null && hourOf(now) in 8..21) outHours++
                assertTrue("seed $seed: coins never negative", s.coins >= 0)
                assertTrue(s.pantry.size <= 24)
                maxCoins = maxOf(maxCoins, s.coins)
                s.trip?.let { t -> assertTrue("seed $seed: trip ends in the future or is resolved", t.endsAt > now - Simulator.STEP) }
            }
            val trips = s.counters["trips"] ?: 0
            val ate = s.counters.filterKeys { it.startsWith("ate:") }.values.sum()
            println("seed $seed: out ${outHours * 100 / (21 * 14)}% of waking hours, max coins $maxCoins, trips $trips, ate $ate, cooked ${s.counters["cooked"] ?: 0}, drew ${s.drawings.size}, photos ${s.photos.size}, " +
                "places ${s.places.keys}, creatures ${s.creatures.values.map { it.name.ifEmpty { it.look } }}, coins ${s.coins}, pet ${s.pet.memories.takeLast(3)}, mystery ${s.mystery.stage}")
            assertTrue("seed $seed: he goes out ($trips)", trips >= 3)
            assertTrue("seed $seed: he eats ($ate)", ate >= 3)
            assertTrue("seed $seed: places remembered", s.places.isNotEmpty())
            assertTrue(s.journal.any { it.category == JournalCategory.PLACE })
            assertTrue(s.drawings.size <= 60 && s.photos.size <= 80)
            if (s.creatures.values.any { it.name.isNotEmpty() }) namedSomething = true
        }
        assertTrue("somebody named an animal in three weeks", namedSomething)
    }
}
