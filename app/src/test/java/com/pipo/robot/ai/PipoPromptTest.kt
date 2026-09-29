package com.pipo.robot.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PipoPromptTest {
    private val ctx = BrainContext("happy", "curious", "Joel", "reading", listOf("you gave me head pats"), "Hehe.")

    @Test
    fun cleansModelOutputIntoOneSpokenLine() {
        assertEquals("Hi Joel! Wanna play?", PipoPrompt.clean("  \"Hi Joel! *bounces* Wanna   play?\"\n"))
        assertNull(PipoPrompt.clean("   "))
        assertNull(PipoPrompt.clean(null))
        assertTrue(PipoPrompt.clean("x".repeat(1000))!!.length <= 240)
    }

    @Test
    fun promptKeepsCharacterAndDecision() {
        val p = PipoPrompt.system(ctx)
        assertTrue(p.contains("four years old"))
        assertTrue(p.contains("Joel"))
        assertTrue(p.contains("\"Hehe.\""))
        assertFalse(p.contains("assistant.") && !p.contains("NOT an assistant"))
    }
}
