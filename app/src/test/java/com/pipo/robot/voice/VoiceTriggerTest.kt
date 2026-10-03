package com.pipo.robot.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The wake phrase: heard however the recogniser writes it, and never on ordinary speech. */
class VoiceTriggerTest {
    @Test
    fun hearsPipoTheWaysSpeechRecognitionWritesIt() {
        for (s in listOf(
            "Pipo", "pipo!", "Pippo", "peepo", "Pepo", "pivo", "bebo", "peapod",
            "Pipo, lock", "pipo play that song", "hey pipo, what's the weather",
            "okay pipo please sleep", "hello pipo dance", "Pipo please lock my phone",
        )) assertTrue(s, VoiceTrigger.isInvocation(s))
    }

    @Test
    fun ordinaryTalkIsNotACallToHim() {
        for (s in listOf(
            "", "hello", "the pipeline is broken", "people are coming", "a pipette",
            "please lock my phone", "lock", "I lost my pipo hat", "pipeline locker",
        )) assertFalse(s, VoiceTrigger.isInvocation(s))
    }

    @Test
    fun stripsTheCallAndKeepsTheCommand() {
        assertEquals("lock", VoiceTrigger.command("Pipo, lock"))
        assertEquals("lock my phone", VoiceTrigger.command("pipo lock my phone"))
        assertEquals("play Arz Kiya Hai", VoiceTrigger.command("Pipo, play Arz Kiya Hai"))
        assertEquals("what time is it", VoiceTrigger.command("hey pipo, what time is it"))
        assertEquals("sleep", VoiceTrigger.command("okay pipo please sleep"))
        assertEquals("DANCE", VoiceTrigger.command("PIPO DANCE")) // casing is preserved; chat normalises
    }

    @Test
    fun aBareCallHasNoCommand() {
        assertNull(VoiceTrigger.command("Pipo"))
        assertNull(VoiceTrigger.command("pipo!"))
        assertNull(VoiceTrigger.command("hey pipo"))
        // no call at all: there is no command to take from it either
        assertNull(VoiceTrigger.command("lock my phone"))
    }
}
