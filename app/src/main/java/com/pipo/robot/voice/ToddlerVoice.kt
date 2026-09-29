package com.pipo.robot.voice

import kotlin.math.min

/**
 * Pipo sounds like a toddler boy (under 5), made on-device from an ordinary adult male TTS voice.
 *
 * Raising TTS pitch alone gives a "chipmunk man": a child's voice is high AND resonates in a much
 * smaller vocal tract (formants ~1.4-1.6x an adult man's). So the line is rendered slowly with a
 * raised pitch, then played back [SHIFT]x faster, which scales pitch and formants together, like
 * shrinking the speaker. The slow render cancels the speed-up, so he still talks at toddler pace.
 */
object ToddlerVoice {
    /** Playback speed-up = formant (vocal-tract) scale. */
    const val SHIFT = 1.45f

    /**
     * TTS pitch before the shift, from the mood pitch (~1.35 sleepy .. 2.1 excited). With a
     * ~120 Hz male voice the final pitch lands ~260 Hz (sleepy) to ~340 Hz (excited): toddler range.
     */
    fun ttsPitch(moodPitch: Float): Float = (1.5f + (moodPitch - 1.35f) * 0.6f).coerceIn(1.3f, 2.2f)

    /** TTS rate before the shift, so the final speed is the mood rate at toddler pace. */
    fun ttsRate(moodRate: Float): Float = moodRate * TODDLER_RATE / SHIFT
}

/** 16-bit PCM audio. */
class Pcm(val rate: Int, val channels: Int, val data: ShortArray)

/** Parses a 16-bit PCM WAV (as written by TextToSpeech.synthesizeToFile). Null if unsupported. */
fun parseWav(b: ByteArray): Pcm? {
    fun le16(i: Int) = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)
    fun le32(i: Int) = le16(i) or (le16(i + 2) shl 16)
    fun tag(i: Int) = String(b, i, 4, Charsets.US_ASCII)
    if (b.size < 12 || tag(0) != "RIFF" || tag(8) != "WAVE") return null
    var p = 12
    var rate = 0; var ch = 0; var bits = 0
    while (p + 8 <= b.size) {
        val id = tag(p)
        val len = le32(p + 4)
        val body = p + 8
        when (id) {
            "fmt " -> { if (body + 16 > b.size) return null; ch = le16(body + 2); rate = le32(body + 4); bits = le16(body + 14) }
            "data" -> {
                if (bits != 16 || rate <= 0 || ch !in 1..2) return null
                // streaming writers may leave the length unset (0 / -1): take what's there
                val bytes = if (len <= 0) b.size - body else min(len, b.size - body)
                val n = bytes / 2
                return Pcm(rate, ch, ShortArray(n) { i -> ((b[body + 2 * i].toInt() and 0xFF) or (b[body + 2 * i + 1].toInt() shl 8)).toShort() })
            }
        }
        if (len < 0) return null
        p = body + len + (len and 1)
    }
    return null
}
