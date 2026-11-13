package com.pipo.robot.engine

import com.pipo.robot.ai.BrainContext
import com.pipo.robot.ai.PipoPrompt
import com.pipo.robot.data.PipoState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Regressions for "Pipo says I don't know to ordinary questions": real questions must reach the
 * AI un-anchored, and the suit commands must do what they say.
 */
class ChatRoutingTest {
    private val noon = 20_000L * DAY + 12 * HOUR
    private fun fresh() = PipoState(seed = 11L).apply { profile.firstRunDone = true; profile.createdAt = noon - 60 * DAY; lastSimulatedAt = noon; mood.energy = 0.9f }
    private fun say(text: String, s: PipoState = fresh()) = LocalBrain.respond(s, text, noon, Random(3), false)

    @Test
    fun ordinaryQuestionsAreMarkedForTheAiNotAnsweredWithAShrug() {
        for (q in listOf("What is the capital of France?", "how many legs does a spider have", "who wrote romeo and juliet",
            "why is the sky blue?", "explain photosynthesis", "can you help me with fractions?")) {
            val r = say(q)
            assertTrue("$q should be unmatched, got '${r.text}' ${r.action}", r.unmatched)
            assertTrue(q, LocalBrain.isQuestion(q.lowercase()))
        }
    }

    @Test
    fun matchedIntentsAreNotUnmatched() {
        assertFalse(say("tell me a joke").unmatched)
        assertFalse(say("how are you?").unmatched)
        assertFalse(say("my name is Joel").unmatched)
    }

    @Test
    fun theBestWayIsAQuestionNotACompliment() {
        val r = say("what's the best way to learn guitar?")
        assertNotEquals(ChatAction.EMBARRASSED, r.action)
        assertEquals(ChatAction.EMBARRASSED, say("you're the best").action)
    }

    @Test
    fun suitOffTakesItOffAndNeverFlies() {
        for (t in listOf("suit off", "take off the armor", "take your suit off", "remove the armour", "no more suit")) {
            val r = say(t)
            assertEquals(t, ChatAction.SUIT_OFF, r.action)
        }
        val on = say("suit up")
        assertEquals(ChatAction.SUIT_UP, on.action)
        assertEquals("", on.payload)
    }

    @Test
    fun theNewGamesOpenFromChatAndOldFootballStillMeansKickUps() {
        val fb = say("let's play a football game")
        assertEquals(ChatAction.PLAY, fb.action); assertEquals("football", fb.payload)
        val pat = say("can we play pattern?")
        assertEquals(ChatAction.PLAY, pat.action); assertEquals("pattern", pat.payload)
        assertEquals(ChatAction.KICK, say("football").action)
    }

    @Test
    fun unmatchedReplyIsNotPassedAsTheDecision() {
        val ctx = BrainContext("happy", "", "", "reading", emptyList(), localReply = "")
        val p = PipoPrompt.system(ctx)
        assertFalse(p.contains("ACTUALLY about to do"))
        assertTrue(p.contains("CORRECT answer"))
        val decided = PipoPrompt.system(ctx.copy(localReply = "Going to the park!"))
        assertTrue(decided.contains("\"Going to the park!\""))
    }

    @Test
    fun talkingAboutAssistantsIsAllowedBeingOneIsNot() {
        val ctx = BrainContext("happy", "", "", "reading", emptyList(), "")
        assertEquals("Siri is an assistant. I'm Pipo.", PipoPrompt.guard("Siri is an assistant. I'm Pipo.", ctx))
        assertEquals(null, PipoPrompt.guard("I'm your assistant!", ctx))
    }
}
