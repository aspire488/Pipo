package com.pipo.robot.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Calls as they actually arrive from the three listeners on the phone (order and timing vary).
 * "hippo" = the name spotter hearing "Pipo". Real calls come from device logs (2026-10-03/04);
 * the second half replays the uninvited locks and pop-ups of 2026-10-04, which must stay silent.
 */
class CallAssemblerTest {
    private val apps = setOf("you tube", "youtube", "instagram")
    private val a = CallAssembler(isInstalledApp = { WakeCommands.squash(it) in apps.map(WakeCommands::squash) })

    // ---------------------------------------------------------------- real calls

    @Test
    fun pipoLockSpotterAndGrammarTogether() {
        // device: "Pipo, lock" -> name "hippo lock", command "hippo lock" a moment later
        a.onName(WakeCommands.Wake.LOCK, 0); a.onCommand("hippo lock", 30)
        assertEquals(WakeAction.Lock, a.poll(30))
    }

    @Test
    fun commandFinishesBeforeTheName() {
        a.onCommand("lock my phone", 0); a.onName(WakeCommands.Wake.ALONE, 150)
        assertEquals(WakeAction.Lock, a.poll(150))
    }

    @Test
    fun grammarLockBeatsGarbledWords() {
        // device: words heard "hello" for "lock"; the command grammar still has it right
        a.onName(WakeCommands.Wake.ALONE, 0); a.onWords("hello", 800); a.onCommand("lock", 820)
        assertEquals(WakeAction.Lock, a.poll(820))
    }

    @Test
    fun spotterLockAndWordsLock() {
        a.onName(WakeCommands.Wake.LOCK, 0); a.onWords("lock it", 400)
        assertEquals(WakeAction.Lock, a.poll(400))
    }

    @Test
    fun lockInAnAccent() {
        assertEquals(WakeCommands.Wake.LOCK, WakeCommands.wake("hippo luck"))
        assertEquals(WakeCommands.Wake.LOCK, WakeCommands.wake("people log"))
        a.onName(WakeCommands.Wake.LOCK, 0); a.onCommand("lock", 200)
        assertEquals(WakeAction.Lock, a.poll(200))
    }

    @Test
    fun pipoComeHere() {
        // device 09:20:51: name "hippo come here" (SUMMON), command "come here" 20 ms later
        a.onName(WakeCommands.Wake.SUMMON, 0); a.onCommand("come here", 20)
        assertEquals(WakeAction.Summon, a.poll(20))
    }

    @Test
    fun pipoComeHereWithTheWords() {
        // device 09:21:06: words "come here"
        a.onName(WakeCommands.Wake.SUMMON, 0); a.onWords("come here", 40)
        assertEquals(WakeAction.Summon, a.poll(40))
    }

    @Test
    fun nameTwiceIsACall() {
        a.onName(WakeCommands.Wake.ALONE, 0)
        assertNull(a.poll(2500))
        a.onName(WakeCommands.Wake.ALONE, 2600) // "Pipo… Pipo!"
        assertEquals(WakeAction.Summon, a.poll(2600))
        assertNull(a.poll(2700))
    }

    @Test
    fun openAnApp() {
        // device 09:21:14: name "hey pupil", words "open", command "open you tube"
        a.onName(WakeCommands.Wake.ALONE, 0); a.onWords("open", 430); a.onCommand("open you tube", 1010)
        assertEquals(WakeAction.Open("you tube"), a.poll(1010))
    }

    @Test
    fun playTakesWhatFromTheWords() {
        a.onName(WakeCommands.Wake.ALONE, 0); a.onCommand("play [unk] [unk]", 1000)
        assertNull(a.poll(1100))
        a.onWords("he will play lofi music", 1200)
        assertEquals(WakeAction.Play("lofi music"), a.poll(1200))
    }

    @Test
    fun garbledNameOpeningARealAppIsAllowed() {
        a.onWords("how open instagram", 0); a.onCommand("open instagram", 50)
        assertEquals(WakeAction.Open("instagram"), a.poll(800))
    }

    // ---------------------------------------------------------------- device log, 2026-10-04

    @Test
    fun spotterHippoLockAloneNeverLocks() {
        // 09:07:22, 09:10:18, 09:10:31 — name "hippo lock" (LOCK) out of room noise, nothing else
        a.onName(WakeCommands.Wake.LOCK, 0)
        for (t in listOf(0L, 500, 1500, 3000, 4500)) assertNull(a.poll(t))
    }

    @Test
    fun grammarLockItPlusAStrayNameLaterNeverLocks() {
        // 09:04:46 command "it lock it", 09:04:49 name "pippin" (ALONE), words "would have been so"
        a.onCommand("it lock it", 0); a.onName(WakeCommands.Wake.ALONE, 3000); a.onWords("would have been so", 3080)
        for (t in listOf(3080L, 3500, 5000, 6500)) assertNull(a.poll(t))
    }

    @Test
    fun aLoneNameNeverPopsHimUp() {
        // 09:05:57 name "hippo" (ALONE) -> 09:05:59 Summon; 09:19:46 "keeper" (ALONE)
        a.onName(WakeCommands.Wake.ALONE, 0)
        for (t in listOf(1000L, 2300, 3000, 4500)) assertNull(a.poll(t))
    }

    @Test
    fun everydaySentencesNeverAct() {
        // 09:19:31 words "i like it" with the grammar forcing "lock it"
        a.onCommand("lock it", 0); a.onWords("i like it", 10)
        assertNull(a.poll(1500))
        // 09:19:50 name "pupil [unk]" (LEADING), command "pupil play notes", words "people playing outside"
        a.onName(WakeCommands.Wake.LEADING, 3000); a.onCommand("pupil play notes", 3720); a.onWords("people playing outside", 4230)
        assertNull(a.poll(4300)); assertNull(a.poll(6000))
        // 09:19:42 command "play phone settings", words "we play football on sundays", a stray name 3 s before
        a.onName(WakeCommands.Wake.LEADING, 8000); a.onCommand("play phone settings", 11000); a.onWords("we play football on sundays", 11800)
        assertNull(a.poll(12000))
    }

    @Test
    fun leadingNamePlusForcedLockIsNotALock() {
        a.onName(WakeCommands.Wake.LEADING, 0); a.onCommand("lock it", 600) // "people [unk]" + grammar noise
        assertNull(a.poll(600))
    }

    @Test
    fun grammarAloneCanNotOpenAnApp() {
        a.onName(WakeCommands.Wake.ALONE, 0); a.onCommand("hippo open instagram", 700); a.onWords("he bought", 720)
        assertNull(a.poll(720)); assertNull(a.poll(2200))
    }

    @Test
    fun staleNameDoesNotCombineWithALaterCommand() {
        a.onName(WakeCommands.Wake.LEADING, 0); a.onCommand("lock", 5000)
        assertNull(a.poll(5000))
    }

    @Test
    fun spotterReadsLockFromItsSecondGuess() {
        assertEquals(WakeCommands.Wake.LOCK, WakeCommands.bestWake(listOf("people", "people lock", "[unk]")))
        assertEquals(WakeCommands.Wake.NONE, WakeCommands.bestWake(listOf("[unk]", "people lock")))
        assertEquals(WakeCommands.Wake.LEADING, WakeCommands.wake("hippo look [unk]")) // "Pipo, look!" is the camera
    }

    // ---------------------------------------------------------------- device log 09:26-09:27: real calls, first try

    @Test
    fun lockWhenTheWordsOnlyCatchTheSound() {
        // 09:27:25 name [hippo lock, hippo log, hippo luck], words "epilogue" 1.4 s later, no command
        a.onName(WakeCommands.Wake.LOCK, 0)
        assertNull(a.poll(500))
        a.onWords("epilogue", 1430)
        assertEquals(WakeAction.Lock, a.poll(1430))
        // 09:26:47 words "people bullock"
        a.onName(WakeCommands.Wake.LOCK, 10_000); a.onWords("people bullock", 10_020)
        assertEquals(WakeAction.Lock, a.poll(10_020))
    }

    @Test
    fun onScreenHoweverItComesOut() {
        // 09:26:32 name "hippo onscreen", command "hippo screen"
        a.onName(WakeCommands.Wake.SUMMON, 0); a.onCommand("hippo screen", 9)
        assertEquals(WakeAction.Summon, a.poll(9))
        // 09:27:02 name "hippo onscreen", words "he born screen", no command
        a.onName(WakeCommands.Wake.SUMMON, 10_000); a.onWords("he born screen", 10_320)
        assertEquals(WakeAction.Summon, a.poll(10_320))
    }

    // ---------------------------------------------------------------- device log 09:35-09:51: needed repeating

    @Test
    fun onScreenFromTheSpotterAlone() {
        // 09:37:53, 09:49:23 name "hippo onscreen" (SUMMON); words "horns" or nothing
        a.onName(WakeCommands.Wake.SUMMON, 0)
        assertEquals(WakeAction.Summon, a.poll(0))
        a.onName(WakeCommands.Wake.SUMMON, 9000); a.onWords("hornsby", 9200)
        assertEquals(WakeAction.Summon, a.poll(9000))
    }

    @Test
    fun unanimousLockWithSpeechBehindIt() {
        // 09:49:42 name [hippo lock, hippo log, people lock] (x3), words "hey bulldog"
        a.onName(WakeCommands.Wake.LOCK, 0, agree = 3); a.onWords("hey bulldog", 27)
        assertEquals(WakeAction.Lock, a.poll(27))
    }

    @Test
    fun unanimousLockWithoutSpeechIsStillNothing() {
        // the morning's uninvited locks: the spotter alone, nobody talking
        a.onName(WakeCommands.Wake.LOCK, 0, agree = 3)
        for (t in listOf(0L, 1000, 3000)) assertNull(a.poll(t))
        a.onName(WakeCommands.Wake.LOCK, 5000, agree = 3); a.onWords("huh", 5100)
        assertNull(a.poll(5200)); assertNull(a.poll(7000))
    }

    @Test
    fun openWhenTheWordsSayOpens() {
        // 09:38:15 name "hippo [unk]", command "hippo open gemini", words "hippo opens email"
        val b = CallAssembler(isInstalledApp = { it == "gemini" })
        b.onName(WakeCommands.Wake.LEADING, 0); b.onCommand("hippo open gemini", 1020); b.onWords("hippo opens email", 1270)
        assertEquals(WakeAction.Open("gemini"), b.poll(1270))
    }

    // ---------------------------------------------------------------- device log 10:03-10:05

    @Test
    fun anOpenHeardAsLockByTheSpotterIsNotALock() {
        // 10:03:28 "Pipo, open GitHub": command "hippo open good hub", spotter [hippo lock it ×3], words "the book open get have"
        a.onCommand("hippo open good hub", 0); a.onName(WakeCommands.Wake.LOCK, 300, agree = 3); a.onWords("the book open get have", 320)
        assertEquals(WakeAction.Open("good hub"), a.poll(320)) // an open (his chat brain matches "good hub" -> GitHub), never a lock
        // 10:04:00 command "people open claude", spotter [people log, people block, people lock]
        a.onName(WakeCommands.Wake.LOCK, 9000, agree = 3); a.onCommand("people open claude", 9010); a.onWords("people bring the bike", 9300)
        assertEquals(null, a.poll(9300).takeIf { it == WakeAction.Lock })
    }

    @Test
    fun aGrammarOpenAloneStillNeedsTheWords() {
        // a "Pipo, lock" has come out of the command grammar as "hippo open instagram" (2026-10-03),
        // so the grammar alone never opens anything, even with the name in it
        a.onName(WakeCommands.Wake.LOCK, 0); a.onCommand("hippo open instagram", 20)
        assertNull(a.poll(20)); assertNull(a.poll(2500))
    }

    @Test
    fun grammarOpenOfANonAppNeverOpens() {
        // 09:03:20-21 room noise: name "people [unk]", command "[unk] people open image to park"
        val b = CallAssembler(isInstalledApp = { true }, isExactApp = { WakeCommands.squash(it) == "imagetopdf" })
        b.onName(WakeCommands.Wake.LEADING, 0); b.onCommand("[unk] people open image to park", 700)
        assertNull(b.poll(700)); assertNull(b.poll(3000))
    }

    @Test
    fun openWhenTheWordsCatchPartOfTheName() {
        // 09:38:48 name "hippo [unk]", command "open you tube", words "but you know"
        a.onName(WakeCommands.Wake.LEADING, 0); a.onCommand("open you tube", 3150); a.onWords("but you know", 3450)
        assertEquals(WakeAction.Open("you tube"), a.poll(3450))
    }
}
