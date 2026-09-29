package com.pipo.robot.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class ToddlerVoiceTest {

    private fun wav(samples: ShortArray, rate: Int = 24000, dataLen: Int = samples.size * 2, extraChunk: Boolean = false): ByteArray {
        val o = ByteArrayOutputStream()
        fun s(x: String) = o.write(x.toByteArray(Charsets.US_ASCII))
        fun i32(v: Int) { o.write(v and 0xFF); o.write(v shr 8 and 0xFF); o.write(v shr 16 and 0xFF); o.write(v shr 24 and 0xFF) }
        fun i16(v: Int) { o.write(v and 0xFF); o.write(v shr 8 and 0xFF) }
        s("RIFF"); i32(36 + samples.size * 2); s("WAVE")
        s("fmt "); i32(16); i16(1); i16(1); i32(rate); i32(rate * 2); i16(2); i16(16)
        if (extraChunk) { s("LIST"); i32(3); o.write(byteArrayOf(1, 2, 3)); o.write(0) } // odd length → pad byte
        s("data"); i32(dataLen)
        samples.forEach { i16(it.toInt()) }
        return o.toByteArray()
    }

    @Test
    fun parsesPcm16() {
        val p = parseWav(wav(shortArrayOf(0, 1000, -1000, Short.MAX_VALUE, Short.MIN_VALUE)))
        assertNotNull(p)
        assertEquals(24000, p!!.rate)
        assertEquals(1, p.channels)
        assertEquals(listOf<Short>(0, 1000, -1000, Short.MAX_VALUE, Short.MIN_VALUE), p.data.toList())
    }

    @Test
    fun skipsUnknownChunksWithPadding() {
        val p = parseWav(wav(shortArrayOf(7, -7), extraChunk = true))
        assertEquals(listOf<Short>(7, -7), p!!.data.toList())
    }

    @Test
    fun toleratesUnsetDataLength() {
        assertEquals(3, parseWav(wav(shortArrayOf(1, 2, 3), dataLen = 0))!!.data.size)
        assertEquals(3, parseWav(wav(shortArrayOf(1, 2, 3), dataLen = -1))!!.data.size)
    }

    @Test
    fun rejectsGarbage() {
        assertNull(parseWav(ByteArray(0)))
        assertNull(parseWav("hello world, not a wav".toByteArray()))
    }

    @Test
    fun toddlerPitchLandsInToddlerRange() {
        // ~120 Hz adult male base voice → final F0 after the playback shift
        for (m in com.pipo.robot.data.Mood.values()) for (st in com.pipo.robot.engine.SpeechStyle.values()) {
            val d = deliveryFor(m, st)
            val f0 = 120f * ToddlerVoice.ttsPitch(d.pitch) * ToddlerVoice.SHIFT
            assertTrue("$m/$st f0=$f0", f0 in 220f..400f)
            // final speaking speed = TTS rate x shift: toddler pace, never faster than the mood asks
            val speed = ToddlerVoice.ttsRate(d.rate) * ToddlerVoice.SHIFT
            assertEquals(d.rate * TODDLER_RATE, speed, 1e-4f)
        }
    }
}
