package com.pipo.robot.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pipo's speech lifecycle against a small model of the phone: who holds audio focus (another app
 * like YouTube, or Pipo), what the engine is actually playing, and virtual time. Assertions are on
 * that resulting state, the things a person would notice: is YouTube ducked/stopped, is he still
 * talking, did the conversation move on.
 */
class SpeechControllerTest {

    /** The system's focus: YouTube is playing until Pipo takes focus; it comes back when he lets go. */
    private class Phone : AudioFocusPort {
        var pipoHolds = false
        var requests = 0
        var deny = false
        var loss: ((FocusLoss) -> Unit)? = null
        val youTubeAudible get() = !pipoHolds

        override fun request(onLoss: (FocusLoss) -> Unit): Boolean {
            requests++
            if (deny) return false
            pipoHolds = true
            loss = onLoss
            return true
        }
        override fun abandon() { pipoHolds = false; loss = null }

        /** Another app takes the sound (the system takes focus away from Pipo first). */
        fun otherAppTakes(kind: FocusLoss) {
            val l = loss
            if (kind != FocusLoss.DUCK) pipoHolds = false
            l?.invoke(kind)
        }
    }

    /** What's coming out of the speaker, line by line. */
    private class Engine {
        val playing = mutableListOf<String>()
        val finishers = mutableMapOf<String, () -> Unit>()
        var failImmediately = false
        var throwOnPlay = false
        var stops = 0

        fun player(line: String): (() -> Unit) -> Unit = { done ->
            if (throwOnPlay) throw IllegalStateException("engine not bound")
            if (failImmediately) done() else { playing += line; finishers[line] = done }
        }
        fun stop() { stops++; playing.clear() }
        fun finish(line: String) { playing.remove(line); finishers[line]?.invoke() }
    }

    /** Virtual main-thread timer. */
    private class Clock {
        var now = 0L
        private val tasks = mutableListOf<Pair<Long, () -> Unit>>()
        fun post(ms: Long, block: () -> Unit) { tasks += (now + ms) to block }
        fun advance(ms: Long) {
            val until = now + ms
            while (true) {
                val next = tasks.filter { it.first <= until }.minByOrNull { it.first } ?: break
                tasks.remove(next); now = next.first; next.second()
            }
            now = until
        }
    }

    private val phone = Phone()
    private val engine = Engine()
    private val clock = Clock()
    private val voice = SpeechController(phone, clock::post, engine::stop)
    private val finished = mutableListOf<String>()

    private fun say(line: String, audible: Boolean = true) =
        voice.speak(audible, silentMs = 1_000L, watchdogMs = 10_000L, play = engine.player(line), onDone = { finished += line })

    @Test
    fun idlePipoNeverTakesFocus() {
        clock.advance(60 * 60_000L) // an hour of him simply existing
        assertEquals(0, phone.requests)
        assertTrue(phone.youTubeAudible)
        assertEquals(SpeechPhase.IDLE, voice.phase)
        assertFalse(voice.busy)
    }

    @Test
    fun focusIsHeldOnlyWhileALineIsHeardAndGivenBackRightAfter() {
        say("hello")
        assertEquals(SpeechPhase.SPEAKING, voice.phase)
        assertEquals(listOf("hello"), engine.playing)
        assertFalse("YouTube ducked while he talks", phone.youTubeAudible)

        engine.finish("hello")
        assertTrue("YouTube back the moment he's done", phone.youTubeAudible)
        assertEquals(SpeechPhase.IDLE, voice.phase)
        assertEquals(listOf("hello"), finished)
    }

    @Test
    fun ttsErrorReleasesFocusAndHeCanSpeakAgain() {
        engine.failImmediately = true
        say("broken")
        assertTrue(phone.youTubeAudible)
        assertEquals(listOf("broken"), finished)
        assertFalse(voice.busy)

        engine.failImmediately = false
        say("fixed")
        assertEquals(listOf("fixed"), engine.playing)
        engine.finish("fixed")
        assertEquals(listOf("broken", "fixed"), finished)
        assertTrue(phone.youTubeAudible)
    }

    @Test
    fun engineThatThrowsOnStartStillReleases() {
        engine.throwOnPlay = true
        say("x")
        assertTrue(phone.youTubeAudible)
        assertFalse(voice.busy)
        assertEquals(listOf("x"), finished)
    }

    @Test
    fun cancelStopsTheLineAndReleasesFocus() {
        say("long story")
        voice.cancel() // e.g. you pressed Home
        assertTrue(engine.playing.isEmpty())
        assertTrue(phone.youTubeAudible)
        assertEquals(SpeechPhase.IDLE, voice.phase)
        assertEquals(listOf("long story"), finished)

        say("again") // back in the app: he talks again
        assertEquals(listOf("again"), engine.playing)
    }

    @Test
    fun cancelWhenIdleDoesNothing() {
        voice.cancel()
        assertEquals(0, engine.stops)
        assertEquals(0, phone.requests)
        assertTrue(finished.isEmpty())
    }

    @Test
    fun permanentFocusLossStopsHimAndHeDoesNotFightBack() {
        say("blah blah")
        phone.otherAppTakes(FocusLoss.PERMANENT)
        assertTrue(engine.playing.isEmpty())
        assertFalse(phone.pipoHolds)
        assertEquals(1, phone.requests) // no re-request
        assertFalse(voice.busy)
        clock.advance(30_000L)
        assertEquals(1, phone.requests)
    }

    @Test
    fun transientFocusLossStopsHimToo() {
        say("blah")
        phone.otherAppTakes(FocusLoss.TRANSIENT)
        assertTrue(engine.playing.isEmpty())
        assertFalse(voice.busy)
        assertEquals(1, phone.requests)
    }

    @Test
    fun beingDuckedKeepsTalkingWithoutReRequesting() {
        say("quiet bit")
        phone.otherAppTakes(FocusLoss.DUCK)
        assertEquals(listOf("quiet bit"), engine.playing)
        assertEquals(1, phone.requests)
        engine.finish("quiet bit")
        assertFalse(phone.pipoHolds)
    }

    @Test
    fun rapidLinesNeverOverlapOrLeakFocus() {
        say("one"); say("two"); say("three")
        assertEquals(listOf("three"), engine.playing)
        assertEquals(listOf("one", "two"), finished) // each replaced line still ends exactly once
        engine.finish("three")
        assertEquals(listOf("one", "two", "three"), finished)
        assertTrue(phone.youTubeAudible)
    }

    @Test
    fun staleEngineCallbackCannotEndOrRestartTheNewLine() {
        say("old")
        val lateOld = engine.finishers.getValue("old")
        say("new")
        lateOld() // the engine reports "old" done long after it was replaced
        assertEquals(SpeechPhase.SPEAKING, voice.phase)
        assertEquals(listOf("new"), engine.playing)
        assertFalse(phone.youTubeAudible)
        assertEquals(listOf("old"), finished)

        voice.cancel()
        lateOld()
        assertEquals(listOf("old", "new"), finished) // no double-finish, no restart
        assertTrue(engine.playing.isEmpty())
    }

    @Test
    fun engineThatNeverAnswersIsCutByTheWatchdog() {
        say("lost")
        clock.advance(9_999L)
        assertTrue(voice.busy)
        clock.advance(1L)
        assertFalse(voice.busy)
        assertTrue(phone.youTubeAudible)
        assertTrue(engine.playing.isEmpty())
        assertEquals(listOf("lost"), finished)
    }

    @Test
    fun watchdogOfAnEndedLineDoesNotTouchTheNextOne() {
        say("first"); engine.finish("first")
        say("second")
        clock.advance(10_000L - 1) // first line's watchdog time passes
        assertEquals(listOf("second"), engine.playing)
        assertTrue(voice.busy)
    }

    @Test
    fun mutedLineNeverRequestsFocusButStillMovesOn() {
        say("mouthed", audible = false)
        assertEquals(0, phone.requests)
        assertTrue(engine.playing.isEmpty())
        clock.advance(1_000L)
        assertEquals(listOf("mouthed"), finished)
        assertFalse(voice.busy)
    }

    @Test
    fun deniedFocusMeansSilentNotForced() {
        phone.deny = true // e.g. a call is live
        say("hi")
        assertTrue(engine.playing.isEmpty())
        assertTrue(phone.youTubeAudible)
        clock.advance(1_000L)
        assertEquals(listOf("hi"), finished)
        assertFalse(voice.busy)
    }

    @Test
    fun nothingSurvivesTeardown() {
        say("bye")
        voice.cancel() // what PipoVoice.shutdown does
        clock.advance(60_000L)
        assertNull(phone.loss)
        assertFalse(phone.pipoHolds)
        assertTrue(engine.playing.isEmpty())
        assertEquals(SpeechPhase.IDLE, voice.phase)
        assertEquals(listOf("bye"), finished)
    }
}
