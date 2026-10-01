package com.pipo.robot.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The AI words his replies. It can't make him an assistant or give him memories he doesn't have. */
class GuardTest {
    private val none = BrainContext("happy", "", "", "reading", emptyList(), "Hi!")
    private val some = none.copy(memories = listOf("I went to the lake"))

    @Test
    fun assistantTalkIsThrownAway() {
        assertNull(PipoPrompt.guard("How can I help you today?", none))
        assertNull(PipoPrompt.guard("As an AI language model, I can't eat.", none))
        assertNull(PipoPrompt.guard("I'm an AI, but I like noodles.", none))
    }

    @Test
    fun inventedMemoriesAreThrownAwayWhenHeHasNone() {
        assertNull(PipoPrompt.guard("I remember when we went to the beach!", none))
        assertNull(PipoPrompt.guard("That time we built a rocket was the best.", none))
        assertEquals("I remember the lake. Frogs!", PipoPrompt.guard("I remember the lake. Frogs!", some))
    }

    @Test
    fun malformedOutputFallsBackToTheOfflineLine() {
        assertNull(PipoPrompt.guard(null, none))
        assertNull(PipoPrompt.guard("   ", none))
        assertNull(PipoPrompt.guard("*waves*", none))
        assertEquals("Hi!", PipoPrompt.guard("  \"Hi!\"  ", none))
    }

    @Test
    fun promptGroundsHimInRealFacts() {
        val p = PipoPrompt.system(none.copy(world = listOf("the weather is raining", "your pet is Nib"), texting = true))
        assertTrue(p.contains("raining"))
        assertTrue(p.contains("Nib"))
        assertTrue(p.contains("Never invent"))
        assertTrue(p.contains("tiny phone"))
        assertTrue(p.contains("purchases"))
    }
}
