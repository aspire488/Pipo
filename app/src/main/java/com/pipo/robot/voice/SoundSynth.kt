package com.pipo.robot.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.pipo.robot.data.Mood
import com.pipo.robot.engine.Sfx
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Pipo's beeps, chirps, laughs and sighs — synthesized on the fly, so there are no audio files
 * and no copyrighted sounds. Plays on a single background thread.
 */
class SoundSynth {
    private val rate = 22050
    private val exec = Executors.newSingleThreadExecutor()
    @Volatile var enabled = true

    private data class Note(val f0: Float, val f1: Float, val ms: Int, val vol: Float = 0.35f, val vibrato: Float = 0f, val gapMs: Int = 0)

    private fun render(notes: List<Note>): ShortArray {
        val total = notes.sumOf { (it.ms + it.gapMs) * rate / 1000 }
        val out = ShortArray(total)
        var idx = 0
        var phase = 0.0
        for (n in notes) {
            val len = n.ms * rate / 1000
            val attack = min(len / 6, rate * 6 / 1000)
            val release = min(len / 3, rate * 25 / 1000)
            for (i in 0 until len) {
                val p = i.toFloat() / len
                var f = n.f0 + (n.f1 - n.f0) * p
                if (n.vibrato > 0f) f *= 1f + n.vibrato * sin(2 * PI * 9 * i / rate).toFloat()
                phase += 2 * PI * f / rate
                // Sine + a little 3rd harmonic = friendly, slightly robotic.
                val s = sin(phase) * 0.82 + sin(phase * 3) * 0.12 + sin(phase * 2) * 0.06
                val env = when {
                    i < attack -> i.toFloat() / attack
                    i > len - release -> (len - i).toFloat() / release
                    else -> 1f
                }
                out[idx++] = (s * env * n.vol * Short.MAX_VALUE).toInt().toShort()
            }
            idx += n.gapMs * rate / 1000
        }
        return out
    }

    private fun play(notes: List<Note>, onDone: (() -> Unit)? = null) {
        if (!enabled) { onDone?.invoke(); return }
        exec.execute {
            try {
                val pcm = render(notes)
                if (pcm.isEmpty()) return@execute
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                Thread.sleep(pcm.size * 1000L / rate + 40)
                track.stop()
                track.release()
            } catch (_: Exception) {
            } finally {
                onDone?.invoke()
            }
        }
    }

    fun sfx(s: Sfx) = play(
        when (s) {
            Sfx.BEEP -> listOf(Note(880f, 1100f, 70))
            Sfx.BOOP -> listOf(Note(620f, 440f, 90))
            Sfx.HAPPY -> listOf(Note(784f, 784f, 80, gapMs = 20), Note(1175f, 1260f, 120))
            Sfx.LAUGH -> listOf(Note(1050f, 980f, 45, gapMs = 30), Note(980f, 900f, 45, gapMs = 30), Note(1050f, 960f, 45, gapMs = 30), Note(900f, 820f, 60))
            Sfx.SIGH -> listOf(Note(620f, 300f, 450, vol = 0.2f))
            Sfx.SURPRISED -> listOf(Note(480f, 1500f, 170))
            Sfx.SLEEPY -> listOf(Note(420f, 330f, 520, vol = 0.18f, vibrato = 0.04f))
            Sfx.GRUMBLE -> listOf(Note(240f, 200f, 300, vol = 0.28f, vibrato = 0.08f))
            Sfx.WIN -> listOf(Note(660f, 660f, 90, gapMs = 20), Note(880f, 880f, 90, gapMs = 20), Note(1320f, 1400f, 180))
            Sfx.LOSE -> listOf(Note(520f, 500f, 140, gapMs = 30), Note(420f, 400f, 140, gapMs = 30), Note(330f, 250f, 260))
            Sfx.YAWN -> listOf(Note(380f, 560f, 380, vol = 0.16f, vibrato = 0.03f), Note(560f, 260f, 520, vol = 0.14f, vibrato = 0.05f))
            Sfx.SERVO -> listOf(Note(180f, 240f, 110, vol = 0.1f, vibrato = 0.2f))
            Sfx.GIGGLE -> listOf(Note(1250f, 1180f, 35, vol = 0.18f, gapMs = 25), Note(1300f, 1200f, 35, vol = 0.18f, gapMs = 25), Note(1220f, 1100f, 45, vol = 0.16f))
            Sfx.HMM -> listOf(Note(520f, 560f, 160, vol = 0.2f, gapMs = 20), Note(560f, 500f, 200, vol = 0.18f))
        }
    )

    /** Robot babble: one chirp per syllable, pitch/speed shaped by mood. [volume] < 1 = whisper. */
    fun babble(text: String, mood: Mood, onDone: () -> Unit) = babble(text, mood, 1f, onDone)

    fun babble(text: String, mood: Mood, volume: Float, onDone: () -> Unit) {
        val syll = Regex("[aeiouy]+", RegexOption.IGNORE_CASE).findAll(text).count().coerceIn(1, 18)
        val (base, len, gap) = when (mood) {
            Mood.EXCITED, Mood.PROUD -> Triple(980f, 55, 18)
            Mood.SLEEPY -> Triple(470f, 110, 60)
            Mood.GRUMPY -> Triple(520f, 70, 30)
            Mood.NERVOUS, Mood.EMBARRASSED -> Triple(900f, 50, 25)
            Mood.BORED, Mood.LONELY -> Triple(600f, 85, 40)
            else -> Triple(800f, 65, 28)
        }
        val rng = Random(text.hashCode())
        val notes = (0 until syll).map {
            val f = base * (0.85f + rng.nextFloat() * 0.35f)
            Note(f, f * (0.9f + rng.nextFloat() * 0.25f), len + rng.nextInt(20), vol = 0.22f * volume, gapMs = gap)
        }
        play(notes, onDone)
    }
}
