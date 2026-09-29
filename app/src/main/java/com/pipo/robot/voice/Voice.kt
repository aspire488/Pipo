package com.pipo.robot.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.pipo.robot.data.Mood
import com.pipo.robot.data.VoiceMode
import com.pipo.robot.engine.Sfx
import com.pipo.robot.engine.SpeechStyle
import com.pipo.robot.engine.SpeechStyles
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Anything that can make Pipo talk. Swap in a cloud/neural voice later without touching the app. */
interface VoiceEngine {
    val available: Boolean
    fun speak(text: String, mood: Mood, onDone: () -> Unit)
    fun stop()
    fun shutdown()
}

/** Delivery per mood: pitch + rate (+ volume for whispers). Young, bright, a bit robotic. */
data class Delivery(val pitch: Float, val rate: Float, val volume: Float = 1f)

/** Mood sets the baseline; the line itself (whisper, excited, sigh…) adjusts it. */
fun deliveryFor(m: Mood, style: SpeechStyle): Delivery {
    val d = deliveryFor(m)
    return when (style) {
        SpeechStyle.WHISPER -> Delivery(d.pitch - 0.1f, d.rate * 0.88f, 0.45f)
        SpeechStyle.EXCITED -> Delivery(d.pitch + 0.15f, d.rate * 1.08f)
        SpeechStyle.SIGH -> Delivery(d.pitch - 0.15f, d.rate * 0.88f, 0.85f)
        SpeechStyle.QUESTION -> Delivery(d.pitch + 0.05f, d.rate)
        else -> d
    }
}

/** Roughly how many characters per second the mouth should move at for this delivery. */
fun charsPerSecond(m: Mood, style: SpeechStyle, mode: VoiceMode): Float =
    if (mode == VoiceMode.SPOKEN) 13.5f * deliveryFor(m, style).rate else 11f

fun deliveryFor(m: Mood): Delivery = when (m) {
    Mood.SLEEPY -> Delivery(1.35f, 0.78f)
    Mood.EXCITED -> Delivery(1.95f, 1.22f)
    Mood.GRUMPY -> Delivery(1.4f, 1.0f)
    Mood.HAPPY, Mood.PROUD -> Delivery(1.75f, 1.08f)
    Mood.NERVOUS, Mood.EMBARRASSED -> Delivery(1.85f, 1.18f)
    Mood.MISCHIEVOUS -> Delivery(1.6f, 0.95f)
    Mood.BORED, Mood.LONELY -> Delivery(1.45f, 0.9f)
    else -> Delivery(1.7f, 1.02f)
}

/** Uses the phone's built-in TTS (offline on most devices). No API key. */
class AndroidTtsVoice(ctx: Context) : VoiceEngine, TextToSpeech.OnInitListener {
    private val main = Handler(Looper.getMainLooper())
    private val callbacks = ConcurrentHashMap<String, () -> Unit>()
    private var tts: TextToSpeech? = TextToSpeech(ctx.applicationContext, this)
    @Volatile override var available = false
        private set

    override fun onInit(status: Int) {
        val t = tts ?: return
        if (status != TextToSpeech.SUCCESS) return
        val r = t.setLanguage(Locale.US)
        available = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { finish(utteranceId) }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { finish(utteranceId) }
            override fun onError(utteranceId: String?, errorCode: Int) { finish(utteranceId) }
        })
    }

    private fun finish(id: String?) {
        if (id == null) return
        callbacks.remove(id)?.let { main.post(it) }
    }

    override fun speak(text: String, mood: Mood, onDone: () -> Unit) {
        val t = tts
        if (!available || t == null) { onDone(); return }
        val d = deliveryFor(mood, SpeechStyles.style(text))
        t.setPitch(d.pitch)
        t.setSpeechRate(d.rate)
        val spoken = text.replace("*", "").replace("...", ", ").replace("{N}", "").replace("(", "").replace(")", "").trim()
        val id = UUID.randomUUID().toString()
        callbacks[id] = onDone
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, d.volume) }
        t.speak(spoken, TextToSpeech.QUEUE_FLUSH, params, id)
    }

    override fun stop() {
        tts?.stop()
        val pending = callbacks.values.toList(); callbacks.clear()
        pending.forEach { main.post(it) }
    }

    override fun shutdown() { tts?.shutdown(); tts = null }
}

/** Chirp-language voice. Always available. */
class BabbleVoice(private val synth: SoundSynth) : VoiceEngine {
    private val main = Handler(Looper.getMainLooper())
    override val available = true
    override fun speak(text: String, mood: Mood, onDone: () -> Unit) =
        synth.babble(text, mood, deliveryFor(mood, SpeechStyles.style(text)).volume) { main.post(onDone) }
    override fun stop() {}
    override fun shutdown() {}
}

/**
 * What the app actually uses. SPOKEN → tiny chirp + TTS words (falls back to babble if the phone
 * has no TTS). BEEPS → babble only. SILENT → nothing.
 */
class PipoVoice(ctx: Context) {
    val synth = SoundSynth()
    private val tts: VoiceEngine = AndroidTtsVoice(ctx)
    private val babble: VoiceEngine = BabbleVoice(synth)
    var mode: VoiceMode = VoiceMode.SPOKEN
    @Volatile var speaking = false
        private set

    fun speak(text: String, mood: Mood, onDone: () -> Unit) {
        val done = { speaking = false; onDone() }
        speaking = true
        // Non-verbal colour on top of the words: a giggle before a laugh line, a sigh before a sigh.
        if (mode != VoiceMode.SILENT) when (SpeechStyles.style(text)) {
            SpeechStyle.LAUGH -> synth.sfx(Sfx.GIGGLE)
            SpeechStyle.SIGH -> synth.sfx(Sfx.SIGH)
            else -> Unit
        }
        when {
            mode == VoiceMode.SILENT -> done()
            mode == VoiceMode.SPOKEN && tts.available -> tts.speak(text, mood, done)
            else -> babble.speak(text, mood, done)
        }
    }

    fun stop() { tts.stop(); speaking = false }
    fun shutdown() { tts.shutdown() }
}

/** Speech recognition — only started when the user taps the mic. Never continuous. */
class SpeechInput(private val ctx: Context) {
    private var rec: SpeechRecognizer? = null
    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(ctx)

    fun start(onPartial: (String) -> Unit, onLevel: (Float) -> Unit, onResult: (String?) -> Unit) {
        stop()
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        rec = r
        var delivered = false
        fun deliver(v: String?) { if (!delivered) { delivered = true; onResult(v) } }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) { onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f)) }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) { deliver(null) }
            override fun onResults(results: Bundle?) {
                deliver(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull())
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onPartial)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        r.startListening(intent)
    }

    fun stop() {
        rec?.let { runCatching { it.cancel() }; runCatching { it.destroy() } }
        rec = null
    }
}
