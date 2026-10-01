package com.pipo.robot.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.pipo.robot.BuildConfig
import com.pipo.robot.data.Mood
import com.pipo.robot.data.VoiceMode
import com.pipo.robot.engine.Sfx
import com.pipo.robot.engine.SpeechStyle
import com.pipo.robot.engine.SpeechStyles
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Anything that can make Pipo talk. Swap in a cloud/neural voice later without touching the app. */
interface VoiceEngine {
    val available: Boolean
    /** False while the engine is still starting up. */
    val ready: Boolean get() = true
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
    if (mode == VoiceMode.SPOKEN) 13.5f * deliveryFor(m, style).rate * TODDLER_RATE else 11f

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

/** Engine voice names that are male (there's no gender API; engines encode it in the name). */
private val MALE_VOICE = Regex("smtm|-iol-|-iom-|-tpd-|-gbd-|-rjs-|male", RegexOption.IGNORE_CASE)

/**
 * Pipo is a little boy: prefer an offline, installed, male English voice (US first). A female
 * voice pitched up reads as a girl; a male voice pitched up reads as a young boy.
 */
fun pickBoyVoice(voices: List<android.speech.tts.Voice>): android.speech.tts.Voice? =
    voices.filter { it.locale.language == "en" && MALE_VOICE.containsMatchIn(it.name) && "notInstalled" !in it.features.orEmpty() }
        .sortedWith(compareBy({ it.isNetworkConnectionRequired }, { it.locale.country != "US" }, { -it.quality }))
        .firstOrNull()

/** Toddlers talk a little slower than adults. Applied to TTS rate and to mouth timing alike. */
const val TODDLER_RATE = 0.9f

/**
 * Uses the phone's built-in TTS (offline on most devices). No API key. Each line is rendered to
 * a temp file and played back through [ToddlerVoice]'s shift so he sounds like a small boy; if
 * rendering fails it falls back to speaking directly.
 */
class AndroidTtsVoice(ctx: Context) : VoiceEngine, TextToSpeech.OnInitListener {
    private val main = Handler(Looper.getMainLooper())
    private val callbacks = ConcurrentHashMap<String, () -> Unit>()
    private val cacheDir = ctx.applicationContext.cacheDir
    private var tts: TextToSpeech? = TextToSpeech(ctx.applicationContext, this)
    @Volatile override var available = false
        private set
    /** Lines rendered to a file, waiting to be played shifted: utterance id → (file, volume). */
    private val rendered = ConcurrentHashMap<String, Pair<File, Float>>()
    private val player = Executors.newSingleThreadExecutor()
    /** Bumped by [stop] so a playing line cuts off. */
    @Volatile private var generation = 0
    private val renderStart = ConcurrentHashMap<String, Long>()

    /** False until the engine has answered onInit. Lines asked for before that wait (briefly) instead of going silent. */
    @Volatile private var initDone = false
    override val ready: Boolean get() = initDone
    private class Waiting(val text: String, val mood: Mood, val onDone: () -> Unit)
    private var waiting: Waiting? = null

    override fun onInit(status: Int) {
        try { setUp(status) } finally {
            initDone = true
            waiting?.let { w -> waiting = null; speak(w.text, w.mood, w.onDone) }
        }
    }

    private fun setUp(status: Int) {
        val t = tts ?: return
        if (status != TextToSpeech.SUCCESS) return
        val r = t.setLanguage(Locale.US)
        available = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
        if (available) {
            val voices = runCatching { t.voices?.toList() }.getOrNull().orEmpty()
            if (BuildConfig.DEBUG) Log.d("PipoVoice", "engine=${t.defaultEngine} voices=" +
                voices.filter { it.locale.language == "en" }.joinToString { "${it.name}${if (it.isNetworkConnectionRequired) "(net)" else ""}" })
            pickBoyVoice(voices)?.let { v -> t.voice = v; if (BuildConfig.DEBUG) Log.d("PipoVoice", "using ${v.name}") }
        }
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                val r = utteranceId?.let { rendered.remove(it) }
                if (r != null) play(utteranceId, r.first, r.second) else finish(utteranceId)
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { failed(utteranceId) }
            override fun onError(utteranceId: String?, errorCode: Int) { failed(utteranceId) }
        })
    }

    private fun failed(id: String?) {
        id?.let { rendered.remove(it)?.first?.delete() }
        finish(id)
    }

    private fun finish(id: String?) {
        if (id == null) return
        callbacks.remove(id)?.let { main.post(it) }
    }

    /** Plays a rendered line [ToddlerVoice.SHIFT]x faster: higher pitch AND a smaller-sounding throat. */
    private fun play(id: String, file: File, volume: Float) {
        val gen = generation
        player.execute {
            try {
                if (gen != generation) return@execute
                val pcm = parseWav(file.readBytes()) ?: return@execute
                if (pcm.data.isEmpty()) return@execute
                val sr = (pcm.rate * ToddlerVoice.SHIFT).roundToInt()
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sr)
                        .setChannelMask(if (pcm.channels == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(pcm.data.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                try {
                    track.write(pcm.data, 0, pcm.data.size)
                    track.setVolume(volume)
                    track.play()
                    if (BuildConfig.DEBUG) Log.d("PipoVoice", "toddler play ${pcm.rate}Hz→${sr}Hz ${pcm.data.size / pcm.channels * 1000L / sr}ms, rendered in ${System.currentTimeMillis() - (renderStart.remove(id) ?: 0L)}ms")
                    val ms = pcm.data.size / pcm.channels * 1000L / sr + 60
                    var waited = 0L
                    while (waited < ms && gen == generation) { Thread.sleep(20); waited += 20 }
                    track.stop()
                } finally { track.release() }
            } catch (_: Exception) {
            } finally {
                file.delete()
                finish(id)
            }
        }
    }

    override fun speak(text: String, mood: Mood, onDone: () -> Unit) {
        val t = tts
        if (!initDone && t != null) {
            // cold start: TTS is still waking up. Hold the newest line for up to 3 s.
            waiting?.let { main.post(it.onDone) }
            val w = Waiting(text, mood, onDone)
            waiting = w
            main.postDelayed({ if (waiting === w) { waiting = null; onDone() } }, 3000)
            return
        }
        if (!available || t == null) { onDone(); return }
        val d = deliveryFor(mood, SpeechStyles.style(text))
        val spoken = text.replace("*", "").replace("...", ", ").replace("{N}", "").replace("(", "").replace(")", "").trim()
        val id = UUID.randomUUID().toString()
        callbacks[id] = onDone
        t.setPitch(ToddlerVoice.ttsPitch(d.pitch))
        t.setSpeechRate(ToddlerVoice.ttsRate(d.rate))
        val file = File(cacheDir, "pipo_say_$id.wav")
        rendered[id] = file to d.volume
        renderStart[id] = System.currentTimeMillis()
        if (t.synthesizeToFile(spoken, Bundle(), file, id) != TextToSpeech.SUCCESS) {
            // couldn't render: say it straight (no shift → plain pitch/rate)
            rendered.remove(id)
            t.setPitch(d.pitch)
            t.setSpeechRate(d.rate * TODDLER_RATE)
            val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, d.volume) }
            t.speak(spoken, TextToSpeech.QUEUE_FLUSH, params, id)
        }
    }

    override fun stop() {
        generation++
        waiting?.let { w -> waiting = null; main.post(w.onDone) }
        tts?.stop()
        rendered.values.forEach { it.first.delete() }; rendered.clear()
        val pending = callbacks.values.toList(); callbacks.clear()
        pending.forEach { main.post(it) }
    }

    override fun shutdown() { stop(); tts?.shutdown(); tts = null; player.shutdown() }
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
    @Volatile private var lastSpokeAt = 0L
    /** Can he talk right now? (TTS finished starting, or he isn't using TTS.) */
    val ready: Boolean get() = mode != VoiceMode.SPOKEN || tts.ready

    /**
     * True while Pipo is making sound, plus a short tail. Android reports his own TTS and chirps
     * as "music active", so music detection must ignore readings taken while he's audible.
     */
    fun audibleRecently(now: Long = System.currentTimeMillis(), tailMs: Long = 4000L): Boolean =
        speaking || now - lastSpokeAt < tailMs || synth.audibleUntil == Long.MAX_VALUE || now - synth.audibleUntil < tailMs

    fun speak(text: String, mood: Mood, onDone: () -> Unit) {
        val done = { speaking = false; lastSpokeAt = System.currentTimeMillis(); onDone() }
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

    fun stop() { if (speaking) lastSpokeAt = System.currentTimeMillis(); tts.stop(); speaking = false }
    fun shutdown() { tts.shutdown() }
}

/** Speech recognition — only started when the user taps the mic. Never continuous. */
class SpeechInput(private val ctx: Context) {
    private var rec: SpeechRecognizer? = null
    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(ctx)

    private var lastPartial = ""
    private var deliverNow: ((String?) -> Unit)? = null

    /**
     * Listens for one utterance. Tolerates a natural pause mid-sentence; a "busy"/client hiccup
     * (common right after the previous session) retries once instead of failing.
     */
    fun start(onPartial: (String) -> Unit, onLevel: (Float) -> Unit, onResult: (String?) -> Unit, retry: Boolean = true) {
        stop()
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        rec = r
        lastPartial = ""
        var delivered = false
        fun deliver(v: String?) { if (!delivered) { delivered = true; deliverNow = null; onResult(v?.takeIf { it.isNotBlank() } ?: lastPartial.takeIf { it.isNotBlank() }) } }
        deliverNow = { v -> deliver(v) }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) { onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f)) }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                if (retry && !delivered && lastPartial.isBlank() && (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT)) {
                    delivered = true
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ start(onPartial, onLevel, onResult, retry = false) }, 350)
                    return
                }
                deliver(null)
            }
            override fun onResults(results: Bundle?) {
                deliver(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull())
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { lastPartial = it; onPartial(it) }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // people pause mid-sentence, especially when talking to a little robot
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
        }
        r.startListening(intent)
    }

    /** "I'm done talking": keeps what was said (the recognizer finishes, or what it heard so far is used). */
    fun finish() {
        val r = rec ?: return
        runCatching { r.stopListening() }
        // some recognizers never deliver after stopListening: use what we heard
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ deliverNow?.invoke(null) }, 1200)
    }

    fun stop() {
        deliverNow = null
        rec?.let { runCatching { it.cancel() }; runCatching { it.destroy() } }
        rec = null
    }
}
