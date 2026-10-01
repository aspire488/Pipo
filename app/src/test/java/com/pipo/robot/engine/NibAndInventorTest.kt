package com.pipo.robot.engine

import com.pipo.robot.data.PipoProject
import com.pipo.robot.data.PipoState
import com.pipo.robot.data.ProjectState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Nib grows up with Pipo; Pipo grows into an inventor. */
class NibAndInventorTest {
    private val noon = 20_000L * DAY + 12 * HOUR
    private fun fresh() = PipoState(seed = 5L).apply { profile.firstRunDone = true; profile.createdAt = noon - 30 * DAY; pet.energy = 0.8f }

    @Test
    fun nibLearnsTricksByPractisingNotInstantly() {
        val s = fresh().apply { pet.bond = 0.3f }
        var learned: NibLife.Trick? = null
        var sessions = 0
        while (learned == null && sessions < 20) { learned = NibLife.train(s, noon + sessions * HOUR, Random(sessions)); sessions++ }
        assertEquals(NibLife.Trick.HIGH_FIVE, learned)
        assertTrue("took practice: $sessions", sessions >= 3)
        assertTrue(NibLife.knows(s, NibLife.Trick.HIGH_FIVE))
        assertTrue(s.journal.any { it.title.contains("high-five") })
        assertTrue(s.memories.any { "taught Nib" in it.content })
    }

    @Test
    fun harderTricksWaitForAClosERFriendship() {
        val s = fresh().apply { pet.bond = 0.1f }
        Ages.repeatTrain(s, noon, 40)
        assertTrue(NibLife.knows(s, NibLife.Trick.HIGH_FIVE))
        assertFalse("sing needs inseparable", NibLife.knows(s, NibLife.Trick.SING))
    }

    @Test
    fun friendshipStagesAreAnnouncedOnce() {
        val s = fresh().apply { pet.bond = 0.3f }
        assertNull(NibLife.checkStage(s, noon)) // first look: no fanfare
        s.pet.bond = 0.55f
        assertEquals("Best friends", NibLife.checkStage(s, noon)!!.title)
        assertNull(NibLife.checkStage(s, noon))
        assertTrue(s.journal.any { it.title.contains("best friends") })
    }

    @Test
    fun nibGetsBraverWithPipo() {
        val shy = fresh().apply { pet.bond = 0.05f }
        val family = fresh().apply { pet.bond = 0.95f }
        assertTrue(NibLife.bravery(family) > NibLife.bravery(shy))
    }

    @Test
    fun armorComesInMarksEachBuiltOnTheLast() {
        val s = fresh()
        s.records["builder_xp"] = 600 // genius
        val ids = { Inventor.blueprintsFor(s).map { it.id } }
        assertTrue("armor" in ids()); assertFalse("armor_mk2" in ids()); assertFalse("armor_mk3" in ids())
        s.projects.add(PipoProject(1, "armor", "Armor Mk I", ProjectState.DONE))
        assertTrue("armor_mk2" in ids()); assertFalse("armor_mk3" in ids())
        s.projects.add(PipoProject(2, "armor_mk2", "Armor Mk II", ProjectState.DONE))
        assertTrue("armor_mk3" in ids())
        assertEquals(2, Inventor.armorMark(s))
    }

    @Test
    fun levelsComeFromRealWorkAndOldSavesGetCredit() {
        val s = fresh()
        s.projects.add(PipoProject(1, "flyer", "Flying machine", ProjectState.DONE))
        s.projects.add(PipoProject(2, "flyer", "Flying machine", ProjectState.FAILED))
        assertTrue(Inventor.xp(s) > 0)
        val r = Inventor.gain(s, 1000, noon)
        assertNotNull(r); assertEquals(5, Inventor.level(s))
        assertTrue(s.journal.any { it.title.contains("Genius") })
    }
}

private object Ages {
    fun repeatTrain(s: PipoState, from: Long, n: Int) { for (i in 0 until n) NibLife.train(s, from + i * HOUR, Random(i)) }
}
