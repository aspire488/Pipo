package com.pipo.robot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** An existing Pipo must survive the upgrade with his history intact. */
class MigrationTest {

    /** What a real schema-1 save looks like: none of the schema-2 fields exist yet. */
    private val v1 = """
        {"schema":1,"idCounter":57,"seed":1234,
         "profile":{"traits":{"curiosity":0.7},"createdAt":1000,"relationship":0.4,"userName":"Joel","firstRunDone":true,"interactions":88,"favoriteGame":"rps"},
         "mood":{"happiness":0.6,"energy":0.5,"current":"CURIOUS"},
         "memories":[{"id":3,"type":"USER_FACT","content":"your name is Joel","importance":1.0,"timestamp":5000,"key":"user:name"}],
         "world":{"objectStates":{"drawings":"2","prank:plant_hat":"1"},"items":[{"id":9,"catalogId":"old_key","foundAt":7000,"revealed":true}]},
         "activity":{"type":"READ","startedAt":9000,"durationMs":15000},
         "journal":[{"id":11,"timestamp":6000,"title":"You met Pipo","description":"hi","category":"MILESTONE"}],
         "games":{"rps":{"pipoWins":3,"userWins":2,"plays":5}},
         "futureField":"ignored"}
    """.trimIndent()

    @Test
    fun aV1SaveLoadsAndKeepsEverything() {
        val s = PipoJson.decodeFromString(PipoState.serializer(), v1)
        assertTrue(Migrations.migrate(s, 50_000L))
        assertEquals(CURRENT_SCHEMA, s.schema)
        assertEquals("Joel", s.profile.userName)
        assertEquals(0.4f, s.profile.relationship, 0.0001f)
        assertEquals("your name is Joel", s.memories.single().content)
        assertEquals("old_key", s.world.items.single().catalogId)
        assertEquals(3, s.games["rps"]!!.pipoWins)
        assertEquals("1", s.world.objectStates["prank:plant_hat"])
        assertEquals("the two drawings of you are still on the wall", 2, s.drawings.count { it.subject == DrawSubject.USER })
        assertFalse("Nib arrives as part of his story, not as a patch", s.pet.adopted)
        assertEquals(50_000L, s.migratedAt)
        assertTrue("fresh defaults for new systems", s.coins > 0 && s.pantry.isNotEmpty())
        assertTrue(s.idCounter >= 57)
        // migrating twice changes nothing
        val drawings = s.drawings.size
        assertFalse(Migrations.migrate(s, 60_000L))
        assertEquals(drawings, s.drawings.size)
    }

    @Test
    fun roundTripsThroughJson() {
        val s = PipoJson.decodeFromString(PipoState.serializer(), v1).also { Migrations.migrate(it, 1L) }
        s.trip = TripState(99, "bakery", TripPurpose.FOOD, 1L, 2L, true, listOf("bread"), "bread")
        s.photos.add(Photo(100, PhotoSubject.PET, "", "park", "RAIN", 14, "Nib", 5L))
        val again = PipoJson.decodeFromString(PipoState.serializer(), PipoJson.encodeToString(PipoState.serializer(), s))
        assertEquals(s.trip, again.trip)
        assertEquals(s.photos, again.photos)
        assertEquals(s.drawings, again.drawings)
    }

    @Test
    fun sanitizeRepairsBrokenValuesWithoutDeletingHistory() {
        val s = PipoState()
        s.coins = -5
        s.trip = TripState(1, "moon", TripPurpose.WALK, 10L, 20L, false)
        s.pantry.add("unicorn steak")
        s.mystery.stage = 12
        s.journal.add(JournalEntry(1, 1L, "keep me", "", JournalCategory.MOMENT))
        assertTrue(Migrations.sanitize(s))
        assertEquals(0, s.coins)
        assertNull(s.trip)
        assertFalse("unicorn steak" in s.pantry)
        assertEquals(5, s.mystery.stage)
        assertEquals(1, s.journal.size)
    }

    @Test(expected = Exception::class)
    fun corruptSavesFailLoudlyToTheRepository() {
        // The repository catches this, keeps a copy of the bad file, and starts fresh instead of crashing.
        PipoJson.decodeFromString(PipoState.serializer(), "{\"schema\":1, \"profile\": ")
    }
}
