package com.pipo.robot.voice

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.pipo.robot.PipoApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The voice listener's life, in one place:
 *
 *   OFF -> STARTING -> LISTENING -> TRIGGER_DETECTED -> COMMAND_CAPTURE -> PROCESSING
 *                                          (app answers: RESPONDING)  -> LISTENING again
 *   plus STOPPING (being switched off) and ERROR (microphone permission lost).
 *
 * The ONLY thing ever persisted is whether you switched it on. Never a transcript, never audio,
 * never the state machine itself — if Pipo restarts, the listener starts from OFF/STARTING.
 *
 * This is an explicit opt-in: it is off on a fresh install, separate from the RECORD_AUDIO
 * permission (having the permission does not turn it on), and it can be switched off from
 * Settings or from the persistent notification at any moment.
 */
enum class VoiceState { OFF, STARTING, LISTENING, TRIGGER_DETECTED, COMMAND_CAPTURE, PROCESSING, RESPONDING, STOPPING, ERROR }

object PipoVoiceListener {
    private const val PREFS = "pipo_voice"
    private const val KEY_ON = "on"

    private val _state = MutableStateFlow(VoiceState.OFF)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    @Volatile
    private var stateAt = System.currentTimeMillis()

    /**
     * Secret for this process only: the listener stamps it on the intent that hands a heard command
     * to MainActivity. MainActivity is exported (it's the launcher), so without this any other app
     * could start it with a made-up command ("lock my phone") and Pipo would carry it out.
     */
    val handoffToken: String = java.util.UUID.randomUUID().toString()

    /** True while the foreground service is alive (set by the service itself). */
    @Volatile
    var running = false

    /** The one and only persisted setting: is Pipo Voice on? Off by default, always. */
    fun isEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ON, false)

    /**
     * Switch Pipo Voice on/off. Must be called while Pipo is on screen: Android only lets a
     * microphone foreground service be started from the foreground.
     */
    fun setEnabled(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ON, on).apply()
        val i = Intent(ctx, PipoVoiceService::class.java)
        if (on) {
            setState(VoiceState.STARTING)
            runCatching { ContextCompat.startForegroundService(ctx, i) }.onFailure { setState(VoiceState.ERROR) }
        } else {
            setState(VoiceState.STOPPING)
            runCatching { ctx.stopService(i) }
            setState(VoiceState.OFF)
        }
    }

    /** Start it again after a reboot or if Android killed it — only if you had switched it on. */
    fun resume(ctx: Context) {
        if (!isEnabled(ctx) || running) return
        // switched on, but his ears (the offline model) aren't here yet: fetch them first
        if (!WakeModel.installed(ctx)) WakeModel.installInBackground(ctx) { setEnabled(ctx, true) }
        else setEnabled(ctx, true)
    }

    fun setState(s: VoiceState) {
        if (_state.value != s) { _state.value = s; stateAt = System.currentTimeMillis() }
    }

    /**
     * Called by the service on every tick. PROCESSING/RESPONDING mean "the app is answering your
     * command"; they go back to LISTENING once the app is no longer front and centre or the answer
     * has had its moment — the listener never pretends to be listening while Pipo is open.
     */
    fun refresh() {
        val v = _state.value
        if (v == VoiceState.PROCESSING || v == VoiceState.RESPONDING) {
            val stale = System.currentTimeMillis() - stateAt > 15_000L
            if (stale || !PipoApp.foreground) setState(VoiceState.LISTENING)
        }
    }

    /* ------------------------------------------------------------------ */
    /* Media quiet: he never talks over what you're listening to.          */
    /* ------------------------------------------------------------------ */

    /** Set while another app is actively playing sound. */
    @Volatile
    var mediaQuiet = false

    /** Until this time, speech is allowed anyway because YOU asked for something. */
    @Volatile
    private var speakUntil = 0L

    /** You spoke to him (chat, voice command): his answer may play over other media for a moment. */
    fun allowSpeech(ms: Long = 20_000L) {
        speakUntil = System.currentTimeMillis() + ms
    }

    /** May he make sound right now? Autonomous chatter: no while others play. Your answers: yes. */
    fun speechAllowed(): Boolean = !mediaQuiet || System.currentTimeMillis() < speakUntil
}
