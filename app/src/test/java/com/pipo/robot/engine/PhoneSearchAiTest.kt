package com.pipo.robot.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** "play X" plays, "search X on youtube" searches, "google X" googles, "ask chatgpt X" asks. */
class PhoneSearchAiTest {
    private fun r(s: String) = PhoneCommands.parse(s)

    @Test
    fun youtubePlayAndSearchAreDifferent() {
        assertEquals(PhoneCmd.YOUTUBE, r("play lofi beats on youtube")!!.cmd)
        r("search lofi beats on youtube")!!.let { assertEquals(PhoneCmd.YT_SEARCH, it.cmd); assertEquals("lofi beats", it.arg) }
        assertEquals(PhoneCmd.YT_SEARCH, r("youtube search cat videos")!!.cmd)
        assertEquals(PhoneCmd.YT_SEARCH, r("search youtube for minecraft")!!.cmd)
    }

    @Test
    fun lookMeansHeLooksThroughTheCamera() {
        assertEquals(PhoneCmd.LOOK, r("pipo look")!!.cmd)
        assertEquals(PhoneCmd.LOOK, r("look at this")!!.cmd)
        assertEquals(PhoneCmd.LOOK, r("what do you see")!!.cmd)
        assertEquals(PhoneCmd.LOOK, r("hey pipo, what's this")!!.cmd)
        // other "look"s keep their meaning
        assertEquals(PhoneCmd.SEARCH, r("look up the capital of france")!!.cmd)
        assertEquals(PhoneCmd.YT_SEARCH, r("look for lofi on youtube")!!.cmd)
        assertEquals(PhoneCmd.NOW_PLAYING, r("what's this song")!!.cmd)
    }

    @Test
    fun googleSearch() {
        r("google how tall is everest")!!.let { assertEquals(PhoneCmd.SEARCH, it.cmd); assertEquals("how tall is everest", it.arg) }
        r("search pizza places on google")!!.let { assertEquals(PhoneCmd.SEARCH, it.cmd); assertEquals("pizza places", it.arg) }
    }

    @Test
    fun askingAnAiOpensItOrFindsOut() {
        r("ask chatgpt why is the sky blue")!!.let { assertEquals(PhoneCmd.ASK_AI, it.cmd); assertEquals("chatgpt", it.extra); assertEquals("why is the sky blue", it.arg) }
        r("ask gemini to write a poem about robots")!!.let { assertEquals(PhoneCmd.ASK_AI, it.cmd); assertEquals("gemini", it.extra) }
        r("ask claude what a black hole is and tell me")!!.let { assertEquals(PhoneCmd.FIND_OUT, it.cmd); assertEquals("claude", it.extra) }
        r("what does chatgpt think about pineapple pizza")!!.let { assertEquals(PhoneCmd.FIND_OUT, it.cmd); assertEquals("chatgpt", it.extra) }
        assertEquals(PhoneCmd.FIND_OUT, r("find out how far the moon is")!!.cmd)
        r("open chatgpt")!!.let { assertEquals("chatgpt", it.extra.ifBlank { it.arg }.lowercase().replace(" ", "").let { e -> if (e.contains("chatgpt")) "chatgpt" else e }) }
        // asking Pipo himself is still a conversation, not an AI app
        assertNull(r("can i ask you something"))
    }
}
