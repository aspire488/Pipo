package com.pipo.robot.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The wake word's text rules. Inputs are what the on-device recogniser actually wrote for real
 * phrases (free-form, checked against the model on 2026-10-03), plus the ordinary sentences that
 * must NOT wake him.
 */
class WakeCommandsTest {
    @Test
    fun callsAsTheRecogniserWritesThem() {
        assertEquals(WakeAction.Open("you tube"), WakeCommands.interpret("people open you tube"))
        assertEquals(WakeAction.Open("instagram"), WakeCommands.interpret("hey pippa open instagram"))
        assertEquals(WakeAction.Lock, WakeCommands.interpret("people luck"))
        assertEquals(WakeAction.Lock, WakeCommands.interpret("hey people like my phone"))
        assertEquals(WakeAction.Lock, WakeCommands.interpret("pee po lock"))
        assertEquals(WakeAction.Play("lo ve music on youtube"), WakeCommands.interpret("people play lo ve music on you tube"))
        assertEquals(WakeAction.Play("origin saying"), WakeCommands.interpret("people play origin saying"))
        assertEquals(WakeAction.Summon, WakeCommands.interpret("people"))
        assertEquals(WakeAction.Summon, WakeCommands.interpret("hey people"))
        assertEquals(WakeAction.Summon, WakeCommands.interpret("hippo come here"))
    }

    @Test
    fun ordinarySpeechNeverWakesHim() {
        assertNull(WakeCommands.interpret("i watched the video about people yesterday"))
        assertNull(WakeCommands.interpret("however open the door"))
        assertNull(WakeCommands.interpret("people are weird"))
        assertNull(WakeCommands.interpret("open instagram")) // no call to Pipo
        assertNull(WakeCommands.interpret("people like my style")) // "like" isn't "lock" unless it's about the phone
        assertNull(WakeCommands.interpret("people open")) // open what?
        assertNull(WakeCommands.interpret("hey"))
        assertNull(WakeCommands.interpret(""))
    }

    @Test
    fun heardAppNamesFindTheInstalledApp() {
        val apps = listOf("YouTube", "YouTube Music", "Instagram", "Settings", "Spotify")
        assertEquals("YouTube", WakeCommands.matchApp("you tube", apps))
        assertEquals("Instagram", WakeCommands.matchApp("insta gram", apps))
        assertEquals("Spotify", WakeCommands.matchApp("spotty fi", apps))
        assertNull(WakeCommands.matchApp("the door", apps))
    }

    @Test
    fun spotterAndWordsTogether() {
        // the spotter only knows his name; free-form words carry the command (as heard through the phone mic)
        assertEquals(WakeCommands.Wake.ALONE, WakeCommands.wake("people"))
        assertEquals(WakeCommands.Wake.ALONE, WakeCommands.wake("hey people"))
        assertEquals(WakeCommands.Wake.LEADING, WakeCommands.wake("people [unk] [unk]"))
        assertEquals(WakeCommands.Wake.NONE, WakeCommands.wake("[unk] people"))
        assertEquals(WakeAction.Open("instagram"), WakeCommands.command("open instagram"))
        assertEquals(WakeAction.Play("loki music"), WakeCommands.command("he will play loki music"))
        assertEquals(WakeAction.Lock, WakeCommands.command("people luck"))
        assertNull(WakeCommands.command("instagram"))
        assertNull(WakeCommands.command("hello"))
        assertNull(WakeCommands.command("we talked for ages and then decided to open the shop")) // too deep in a sentence
    }

    @Test
    fun nameSoundAlikesAndLeadWords() {
        assertEquals(WakeCommands.Wake.ALONE, WakeCommands.wake("pippa"))
        assertEquals(WakeCommands.Wake.LEADING, WakeCommands.wake("hey hippo [unk]"))
        assertEquals(1, WakeCommands.wordsBeforeVerb("whoa open you tube"))
        assertEquals(0, WakeCommands.wordsBeforeVerb("open instagram"))
        assertEquals(-1, WakeCommands.wordsBeforeVerb("hello"))
    }
}
