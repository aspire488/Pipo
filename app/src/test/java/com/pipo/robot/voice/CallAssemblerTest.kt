package com.pipo.robot.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Calls as they actually arrive from the three listeners on the phone (order and timing vary).
 * Inputs mirror real device logs from 2026-10-03 ("hippo" = the spotter hearing "Pipo").
 */
class CallAssemblerTest {
    private val apps = setOf("you tube", "youtube", "instagram")
    private val a = CallAssembler(isInstalledApp = { WakeCommands.squash(it) in apps.map(WakeCommands::squash) })

    @Test
    fun nameThenGrammarCommand() {
        a.onName(WakeCommands.Wake.ALONE, 0)
        assertNull(a.poll(100))
        a.onCommand("open you tube", 900)
        assertNull(a.poll(900)) // waiting for the words to agree it was "open"
        a.onWords("whoa open you tube", 1000)
        assertEquals(WakeAction.Open("you tube"), a.poll(1000))
    }

    @Test
    fun commandFinishesBeforeTheName() {
        a.onCommand("lock my phone", 0)
        a.onName(WakeCommands.Wake.LEADING, 150)
        assertEquals(WakeAction.Lock, a.poll(150))
    }

    @Test
    fun grammarLockBeatsGarbledWords() {
        // device: words heard "hello" for "lock"; the command grammar still has it right
        a.onName(WakeCommands.Wake.ALONE, 0); a.onWords("hello", 800); a.onCommand("lock", 820)
        assertEquals(WakeAction.Lock, a.poll(820))
    }

    @Test
    fun playTakesWhatFromTheWords() {
        a.onName(WakeCommands.Wake.ALONE, 0)
        a.onCommand("play [unk] [unk]", 1000)
        assertNull(a.poll(1100)) // waiting for the words
        a.onWords("he will play lofi music", 1200)
        assertEquals(WakeAction.Play("lofi music"), a.poll(1200))
    }

    @Test
    fun playWithoutWordsStillBringsHimUpToAsk() {
        a.onName(WakeCommands.Wake.ALONE, 0); a.onCommand("play", 1000)
        assertNull(a.poll(1700))
        assertEquals(WakeAction.Play(""), a.poll(3200))
    }

    @Test
    fun nameAloneThenSilenceIsASummon() {
        a.onName(WakeCommands.Wake.ALONE, 0)
        assertNull(a.poll(2000))
        assertEquals(WakeAction.Summon, a.poll(2300))
        assertNull(a.poll(2400)) // once
    }

    @Test
    fun nameLeadingOrdinaryWordsIsNothing() {
        a.onName(WakeCommands.Wake.LEADING, 0) // "people are weird"
        assertNull(a.poll(3000))
        assertNull(a.poll(6000))
    }

    @Test
    fun lockAndPlayNeverWithoutTheName() {
        a.onCommand("lock", 0); a.onWords("i like it", 0)
        assertNull(a.poll(1000))
        a.onCommand("play", 2000); a.onWords("we play football", 2000)
        assertNull(a.poll(3000))
    }

    @Test
    fun garbledNameOpeningARealAppIsAllowed() {
        a.onWords("how open instagram", 0); a.onCommand("open instagram", 50)
        assertEquals(WakeAction.Open("instagram"), a.poll(800))
    }

    @Test
    fun noNameAndNotAnAppDoesNothing() {
        a.onWords("i'll open the door", 0)
        assertNull(a.poll(1000))
        a.onWords("open instagram", 2000) // nothing before "open": just talk, not a call
        assertNull(a.poll(3000))
    }

    @Test
    fun staleNameDoesNotCombineWithALaterCommand() {
        a.onName(WakeCommands.Wake.LEADING, 0)
        a.onCommand("lock", 5000)
        assertNull(a.poll(5000))
    }

    @Test
    fun nameWordInsideTheCommandGrammar() {
        // device: "Pipo, lock" -> command listener "hippo lock"
        a.onName(WakeCommands.Wake.ALONE, 0); a.onCommand("hippo lock", 600)
        assertEquals(WakeAction.Lock, a.poll(600))
        assertEquals(true, "hippo lock" in WakeCommands.commandGrammar(listOf("YouTube")))
    }

    @Test
    fun lockHeardByTheNameListenerFiresAtOnce() {
        a.onName(WakeCommands.Wake.LOCK, 0)
        assertEquals(WakeAction.Lock, a.poll(0))
        assertEquals(WakeCommands.Wake.LOCK, WakeCommands.wake("hippo lock"))
        assertEquals(WakeCommands.Wake.LOCK, WakeCommands.wake("hey people lock my phone"))
    }

    @Test
    fun grammarAloneCanNotOpenAnApp() {
        // device 19:57:26: "Pipo, lock" -> command grammar "hippo open instagram", words heard no "open"
        a.onName(WakeCommands.Wake.ALONE, 0); a.onCommand("hippo open instagram", 700); a.onWords("he bought", 720)
        assertNull(a.poll(720))
        assertNull(a.poll(2200))
    }

    @Test
    fun onScreenIsInstantLikeLock() {
        assertEquals(WakeCommands.Wake.SUMMON, WakeCommands.wake("hippo onscreen"))
        assertEquals(WakeCommands.Wake.SUMMON, WakeCommands.wake("people on screen"))
        assertEquals(WakeCommands.Wake.SUMMON, WakeCommands.wake("hey hippo come"))
        assertEquals(WakeCommands.Wake.LEADING, WakeCommands.wake("hippo on [unk]"))
        a.onName(WakeCommands.Wake.SUMMON, 0)
        assertEquals(WakeAction.Summon, a.poll(0))
    }

    @Test
    fun lockInOtherAccentsAndSecondGuesses() {
        assertEquals(WakeCommands.Wake.LOCK, WakeCommands.wake("hippo luck"))
        assertEquals(WakeCommands.Wake.LOCK, WakeCommands.wake("people log"))
        assertEquals(WakeCommands.Wake.LEADING, WakeCommands.wake("hippo look [unk]")) // "Pipo, look!" is the camera, not lock
        // the device's top guess was just the name; the second was the lock
        assertEquals(WakeCommands.Wake.LOCK, WakeCommands.bestWake(listOf("people", "people lock", "[unk]")))
        assertEquals(WakeCommands.Wake.ALONE, WakeCommands.bestWake(listOf("people", "[unk]")))
        assertEquals(WakeCommands.Wake.NONE, WakeCommands.bestWake(listOf("[unk]")))
        assertEquals("chatter whose 2nd guess is a lock never locks", WakeCommands.Wake.NONE, WakeCommands.bestWake(listOf("[unk]", "people lock")))
    }
}
