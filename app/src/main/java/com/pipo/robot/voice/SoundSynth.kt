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
    /** Wall-clock ms when the last chirp finished (or is still playing: Long.MAX_VALUE). */
    @Volatile var audibleUntil = 0L
        private set

    /**
     * CHIRP = his friendly robot tone · HUM = closed-mouth voice (strong fundamental, soft overtones,
     * slow attack, a breathing swell) · BREATH = filtered noise · THUMP = a falling knock.
     */
    private enum class Timbre { CHIRP, HUM, BREATH, THUMP }

    private data class Note(
        val f0: Float, val f1: Float, val ms: Int, val vol: Float = 0.35f, val vibrato: Float = 0f, val gapMs: Int = 0,
        val timbre: Timbre = Timbre.CHIRP,
        /** 0..1 how much breath/noise is mixed in. */
        val noise: Float = 0f,
        /** Noise brightness: 0.01 (rumble) … 0.8 (hiss). */
        val bright: Float = 0.2f,
    )

    private var noiseSeed = 0x2545F491

    private fun white(): Float {
        noiseSeed = noiseSeed * 1103515245 + 12345
        return ((noiseSeed ushr 8) and 0xFFFF) / 32768f - 1f
    }

    private fun render(notes: List<Note>): ShortArray {
        val total = notes.sumOf { (it.ms + it.gapMs) * rate / 1000 }
        val out = ShortArray(total)
        var idx = 0
        var phase = 0.0
        var lp = 0f
        for (n in notes) {
            val len = n.ms * rate / 1000
            val soft = n.timbre == Timbre.HUM || n.timbre == Timbre.BREATH
            val attack = if (soft) min(len / 3, rate * 35 / 1000) else min(len / 6, rate * 6 / 1000)
            val release = if (soft) min(len / 2, rate * 90 / 1000) else min(len / 3, rate * 25 / 1000)
            for (i in 0 until len) {
                val p = i.toFloat() / len
                var f = n.f0 + (n.f1 - n.f0) * p
                if (n.vibrato > 0f) f *= 1f + n.vibrato * sin(2 * PI * (if (soft) 5.5 else 9.0) * i / rate).toFloat()
                phase += 2 * PI * f / rate
                val tone = when (n.timbre) {
                    // Sine + a little 3rd harmonic = friendly, slightly robotic.
                    Timbre.CHIRP -> sin(phase) * 0.82 + sin(phase * 3) * 0.12 + sin(phase * 2) * 0.06
                    // Humming: the mouth is closed, so it's nearly all fundamental with a warm, nasal 2nd.
                    Timbre.HUM -> (sin(phase) * 0.86 + sin(phase * 2) * 0.16 + sin(phase * 3) * 0.04) * (0.9 + 0.1 * sin(2 * PI * 2.2 * i / rate))
                    Timbre.BREATH -> 0.0
                    Timbre.THUMP -> sin(phase) * kotlin.math.exp(-p * 5.0)
                }
                lp += (white() - lp) * n.bright
                val s = tone * (1f - n.noise) + lp * n.noise * 2.2f
                val env = when {
                    i < attack -> i.toFloat() / attack
                    i > len - release -> (len - i).toFloat() / release
                    else -> 1f
                }
                out[idx++] = (s * env * n.vol * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
            }
            idx += n.gapMs * rate / 1000
        }
        return out
    }

    /**
     * A tiny original tune, hummed. Pentatonic, so it always sounds content; seeded, so each hum is
     * different but none of them belong to anyone else. Notes glide into each other like a voice.
     */
    private fun hum(rng: Random): List<Note> {
        val freqs = humTune(rng)
        return freqs.mapIndexed { i, f ->
            val next = freqs.getOrElse(i + 1) { f }
            val ms = if (i == freqs.lastIndex) 520 else 200 + rng.nextInt(180)
            // glide only at the very end of each note, like a voice moving between pitches
            Note(f, f + (next - f) * 0.15f, ms, vol = 0.2f, vibrato = 0.012f + (if (i == freqs.lastIndex) 0.01f else 0f), timbre = Timbre.HUM, noise = 0.04f, bright = 0.05f)
        }
    }

    private fun play(notes: List<Note>, onDone: (() -> Unit)? = null) {
        if (!enabled || exec.isShutdown) { onDone?.invoke(); return }
        runCatching {
            exec.execute {
                var track: AudioTrack? = null
                try {
                    val pcm = render(notes)
                    if (pcm.isEmpty()) return@execute
                    track = AudioTrack.Builder()
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
                    audibleUntil = Long.MAX_VALUE
                    track.play()
                    Thread.sleep(pcm.size * 1000L / rate + 40)
                } catch (_: Exception) {
                    // no audio device, interrupted by shutdown…: he just stays quiet this once
                } finally {
                    runCatching { track?.stop() }
                    runCatching { track?.release() }
                    audibleUntil = System.currentTimeMillis()
                    onDone?.invoke()
                }
            }
        }.onFailure { onDone?.invoke() } // executor already shut down
    }

    companion object {
        /** Pitches of one hum (Hz). Pentatonic around a toddler-ish 300–340 Hz; ends on the home note. */
        internal fun humTune(rng: Random): List<Float> {
            val scale = floatArrayOf(1f, 9f / 8f, 5f / 4f, 3f / 2f, 5f / 3f, 2f)
            val base = 300f + rng.nextFloat() * 40f
            var step = rng.nextInt(3)
            val n = 5 + rng.nextInt(4)
            return (0 until n).map {
                // every note moves; at the top or bottom of the scale the melody turns around instead of sticking
                val d = listOf(-2, -1, 1, 1, 2).random(rng)
                step = (if (step + d in scale.indices) step + d else step - d).coerceIn(0, scale.size - 1)
                base * scale[step]
            } + base * scale[0]
        }
    }

    /** Stops the sound thread. Call when the owner goes away (a game screen, the ViewModel). */
    fun shutdown() { enabled = false; exec.shutdownNow() }

    fun sfx(s: Sfx) = play(
        when (s) {
            Sfx.BEEP -> listOf(Note(880f, 1100f, 70))
            Sfx.BOOP -> listOf(Note(620f, 440f, 90))
            Sfx.HAPPY -> listOf(Note(784f, 784f, 80, gapMs = 20), Note(1175f, 1260f, 120))
            Sfx.LAUGH -> listOf(Note(1050f, 980f, 45, gapMs = 30), Note(980f, 900f, 45, gapMs = 30), Note(1050f, 960f, 45, gapMs = 30), Note(900f, 820f, 60))
            Sfx.SIGH -> listOf(Note(620f, 300f, 450, vol = 0.2f, timbre = Timbre.HUM, noise = 0.45f, bright = 0.15f))
            Sfx.SURPRISED -> listOf(Note(480f, 1500f, 170))
            Sfx.SLEEPY -> listOf(Note(420f, 330f, 520, vol = 0.18f, vibrato = 0.04f))
            Sfx.GRUMBLE -> listOf(Note(240f, 200f, 300, vol = 0.28f, vibrato = 0.08f))
            Sfx.WIN -> listOf(Note(660f, 660f, 90, gapMs = 20), Note(880f, 880f, 90, gapMs = 20), Note(1320f, 1400f, 180))
            Sfx.LOSE -> listOf(Note(520f, 500f, 140, gapMs = 30), Note(420f, 400f, 140, gapMs = 30), Note(330f, 250f, 260))
            Sfx.YAWN -> listOf(Note(380f, 560f, 380, vol = 0.16f, vibrato = 0.03f, timbre = Timbre.HUM, noise = 0.3f, bright = 0.12f),
                Note(560f, 260f, 520, vol = 0.14f, vibrato = 0.05f, timbre = Timbre.HUM, noise = 0.35f, bright = 0.1f))
            Sfx.SERVO -> listOf(Note(180f, 240f, 110, vol = 0.1f, vibrato = 0.2f))
            Sfx.GIGGLE -> listOf(Note(1250f, 1180f, 35, vol = 0.18f, gapMs = 25), Note(1300f, 1200f, 35, vol = 0.18f, gapMs = 25), Note(1220f, 1100f, 45, vol = 0.16f))
            Sfx.HMM -> listOf(Note(520f, 560f, 160, vol = 0.2f, gapMs = 20, timbre = Timbre.HUM), Note(560f, 500f, 200, vol = 0.18f, timbre = Timbre.HUM))
            Sfx.HUM -> hum(Random(System.nanoTime()))
            Sfx.CHEW -> List(3) { Note(250f, 230f, 70, vol = 0.1f, gapMs = 80, timbre = Timbre.HUM, noise = 0.35f, bright = 0.2f) }
            Sfx.KICK -> listOf(Note(170f, 55f, 130, vol = 0.45f, timbre = Timbre.THUMP, noise = 0.15f, bright = 0.5f))
            Sfx.DOOR -> listOf(Note(310f, 230f, 420, vol = 0.07f, vibrato = 0.25f, gapMs = 60, noise = 0.3f, bright = 0.1f), Note(90f, 45f, 140, vol = 0.35f, timbre = Timbre.THUMP))
            Sfx.PET_CHIRP -> listOf(Note(1700f, 2100f, 40, vol = 0.14f, gapMs = 35), Note(1900f, 2300f, 40, vol = 0.14f, gapMs = 35), Note(2200f, 1800f, 55, vol = 0.12f))
            Sfx.PET_HAPPY -> listOf(Note(1500f, 1900f, 45, vol = 0.13f, gapMs = 20), Note(1800f, 2300f, 45, vol = 0.13f, gapMs = 20), Note(2100f, 2700f, 50, vol = 0.13f, gapMs = 20), Note(2500f, 2900f, 70, vol = 0.12f, vibrato = 0.06f))
            Sfx.PET_GRUMP -> listOf(Note(700f, 560f, 160, vol = 0.12f, vibrato = 0.25f, gapMs = 30), Note(560f, 520f, 120, vol = 0.1f, vibrato = 0.3f))
            Sfx.PET_CURIOUS -> listOf(Note(1100f, 1300f, 70, vol = 0.12f, gapMs = 60), Note(1300f, 2100f, 120, vol = 0.13f))
            Sfx.PET_SAD -> listOf(Note(1600f, 1300f, 140, vol = 0.1f, gapMs = 40, vibrato = 0.04f), Note(1250f, 800f, 260, vol = 0.09f, vibrato = 0.05f))
            Sfx.PET_SMUG -> listOf(Note(2000f, 2400f, 40, vol = 0.13f, gapMs = 45), Note(2400f, 1700f, 90, vol = 0.12f))
            Sfx.SHUTTER -> listOf(Note(1f, 1f, 22, vol = 0.3f, timbre = Timbre.BREATH, noise = 1f, bright = 0.7f, gapMs = 30), Note(1f, 1f, 30, vol = 0.22f, timbre = Timbre.BREATH, noise = 1f, bright = 0.5f))
            Sfx.SIZZLE -> listOf(Note(1f, 1f, 900, vol = 0.07f, timbre = Timbre.BREATH, noise = 1f, bright = 0.75f))
            Sfx.EFFORT -> listOf(Note(240f, 310f, 360, vol = 0.17f, vibrato = 0.05f, timbre = Timbre.HUM, noise = 0.15f))
            Sfx.THUNDER -> listOf(Note(50f, 32f, 1700, vol = 0.22f, timbre = Timbre.THUMP, noise = 0.85f, bright = 0.015f))
            Sfx.SCRIBBLE -> List(4) { Note(1f, 1f, 55, vol = 0.05f, gapMs = 45, timbre = Timbre.BREATH, noise = 1f, bright = 0.45f) }
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
