package com.pipo.robot.voice

/** Where one spoken line is in its life. Every line ends back at [IDLE], whatever happens. */
enum class SpeechPhase { IDLE, PREPARING, REQUEST_FOCUS, SPEAKING, FINISHING, RELEASE }

/** How another app took the sound away from Pipo. */
enum class FocusLoss { PERMANENT, TRANSIENT, DUCK }

/** Audio focus, as the speech controller sees it. The Android implementation is [SystemAudioFocus]. */
interface AudioFocusPort {
    /** Ask for short, duckable focus for one line. True if granted. [onLoss] reports another app taking it. */
    fun request(onLoss: (FocusLoss) -> Unit): Boolean
    fun abandon()
}

/**
 * The one owner of Pipo's speech. Pure (no Android types) so the whole lifecycle is unit-tested.
 *
 *   IDLE -> PREPARING -> REQUEST_FOCUS -> SPEAKING -> FINISHING -> RELEASE -> IDLE
 *
 * Rules it guarantees:
 *  - Audio focus is requested only for a line that will actually be heard, never at start-up or
 *    while idle, and it is abandoned the moment the line ends: done, error, cancel, focus loss,
 *    or the watchdog.
 *  - One line at a time: a new line cancels the old one rather than overlapping it.
 *  - Every line's [onDone] runs exactly once. Callbacks from a line that was cancelled or
 *    replaced are ignored, so a late engine callback can never end (or restart) a newer line.
 *  - If the engine never answers, the watchdog ends the line, so he can't get stuck "speaking".
 *
 * Must be driven from one thread (the main thread in the app).
 */
class SpeechController(
    private val focus: AudioFocusPort,
    /** Run [block] after [ms] on the controller's thread. */
    private val post: (ms: Long, block: () -> Unit) -> Unit,
    /** Cut whatever the engine is playing. */
    private val stopEngine: () -> Unit,
) {
    var phase = SpeechPhase.IDLE
        private set
    var focusHeld = false
        private set
    /** True from the start of a line until it has fully released. */
    val busy: Boolean get() = phase != SpeechPhase.IDLE

    private var token = 0
    private var pending: (() -> Unit)? = null

    /**
     * Say one line.
     * @param audible false = mouth it silently for [silentMs] (muted, or another app has the sound);
     *   no focus is requested at all.
     * @param play starts the engine; it must call its argument when the line has finished
     *   (successfully or not). It may call it synchronously.
     * @param watchdogMs the longest this line may take before it's cut and released anyway.
     */
    fun speak(audible: Boolean, silentMs: Long, watchdogMs: Long, play: (done: () -> Unit) -> Unit, onDone: () -> Unit) {
        if (busy) cancel()
        val my = ++token
        pending = onDone
        phase = SpeechPhase.PREPARING
        val finish = { if (my == token) end(cutEngine = false) }
        if (!audible) {
            phase = SpeechPhase.SPEAKING
            post(silentMs, finish)
            return
        }
        phase = SpeechPhase.REQUEST_FOCUS
        focusHeld = focus.request { loss -> if (my == token) onFocusLoss(loss) }
        if (my != token) return // focus loss arrived synchronously and already ended it
        if (!focusHeld) {
            // e.g. a call is live: he mouths it, no sound, nothing taken from anyone
            phase = SpeechPhase.SPEAKING
            post(silentMs, finish)
            return
        }
        phase = SpeechPhase.SPEAKING
        post(watchdogMs) { if (my == token) end(cutEngine = true) }
        try {
            play(finish)
        } catch (e: Exception) {
            if (my == token) end(cutEngine = true)
        }
    }

    /** Stop the current line now (app backgrounded, interrupted, destroyed). Safe to call any time. */
    fun cancel() {
        if (!busy) return
        end(cutEngine = true)
    }

    private fun onFocusLoss(loss: FocusLoss) {
        when (loss) {
            // someone else is playing now: stop, give it back, don't fight for it
            FocusLoss.PERMANENT, FocusLoss.TRANSIENT -> end(cutEngine = true)
            // the system lowers him for a moment; never re-request
            FocusLoss.DUCK -> Unit
        }
    }

    private fun end(cutEngine: Boolean) {
        token++ // anything still in flight for this line is now stale
        phase = SpeechPhase.FINISHING
        if (cutEngine) runCatching { stopEngine() }
        phase = SpeechPhase.RELEASE
        if (focusHeld) { focusHeld = false; runCatching { focus.abandon() } }
        phase = SpeechPhase.IDLE
        val cb = pending
        pending = null
        cb?.invoke()
    }
}
