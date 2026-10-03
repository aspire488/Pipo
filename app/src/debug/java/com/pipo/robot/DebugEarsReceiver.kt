package com.pipo.robot

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Debug builds only. One recognition session with a chosen recogniser/language, to find out which
 * set-up actually hears on this phone (pair it with DebugSayReceiver for a phrase to hear):
 *
 *   adb shell am broadcast -n com.pipo.robot/.DebugEarsReceiver --es svc pkg/.Cls --es lang en-US
 *
 * Only works while Pipo is on screen (Android doesn't let a backgrounded app use the microphone).
 */
class DebugEarsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        // --es use pkg/.Cls : make the real mic button use this recogniser ("default" = phone's default)
        intent.getStringExtra("use")?.let { use ->
            ctx.getSharedPreferences("pipo_debug", Context.MODE_PRIVATE).edit()
                .apply { if (use == "default") remove("recogniser") else putString("recogniser", use) }.apply()
            Log.d(TAG, "mic recogniser -> $use")
            return
        }
        val svc = intent.getStringExtra("svc")?.let { ComponentName.unflattenFromString(it) }
        val lang = intent.getStringExtra("lang")
        val pending = goAsync()
        val main = Handler(Looper.getMainLooper())
        val r = if (svc != null) SpeechRecognizer.createSpeechRecognizer(ctx.applicationContext, svc) else SpeechRecognizer.createSpeechRecognizer(ctx.applicationContext)
        var peak = -100f
        var ended = false
        fun end(what: String) {
            if (ended) return
            ended = true
            Log.d(TAG, "svc=${svc?.flattenToShortString() ?: "default"} lang=${lang ?: "device"} peakDb=$peak -> $what")
            runCatching { r.destroy() }
            pending.finish()
        }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { Log.d(TAG, "ready") }
            override fun onBeginningOfSpeech() { Log.d(TAG, "speech began") }
            override fun onRmsChanged(rmsdB: Float) { if (rmsdB > peak) peak = rmsdB }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { Log.d(TAG, "speech ended") }
            override fun onError(error: Int) = end("error $error")
            override fun onResults(results: Bundle?) = end("result '${results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()}'")
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            if (lang != null) putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
        }
        r.startListening(i)
        main.postDelayed({ end("no answer in 9s") }, 9_000)
    }

    private companion object { const val TAG = "PipoEarsProbe" }
}
