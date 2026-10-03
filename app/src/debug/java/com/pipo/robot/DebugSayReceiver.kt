package com.pipo.robot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Debug builds only. Says a phrase out of the phone's own speaker in a plain adult voice, so a
 * device test can check the "Speak to Pipo" microphone path end to end (speaker -> mic ->
 * recogniser -> Pipo) without a person in the room:
 *
 *   adb shell am broadcast -n com.pipo.robot/.DebugSayReceiver --es text "what is your name"
 */
class DebugSayReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val text = intent.getStringExtra("text")?.take(120) ?: return
        val pending = goAsync()
        var tts: TextToSpeech? = null
        // let the last of the audio drain before letting go of the engine
        fun done() { android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ tts?.shutdown(); pending.finish() }, 1500) }
        tts = TextToSpeech(ctx.applicationContext) { status ->
            val t = tts
            if (status != TextToSpeech.SUCCESS || t == null) { done(); return@TextToSpeech }
            t.language = Locale.US
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) = done()
                @Deprecated("Deprecated in Java") override fun onError(id: String?) = done()
            })
            t.speak(text, TextToSpeech.QUEUE_FLUSH, android.os.Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f) }, "debug-say")
        }
    }
}
