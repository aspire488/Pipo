package com.pipo.robot.voice

/** Why the listener is not allowed to hear right now. */
enum class BlockReason { OFF, NO_MIC, SCREEN_OFF, LOCKED, PIPO_OPEN, CALL }

/**
 * When the background listener may hold the microphone, kept pure so tests can pin it down.
 *
 * Playing media does NOT block it any more: the listener reads the mic directly and takes no
 * audio focus, so it can't pause or duck anyone (that was the old recogniser loop's bug). Calls
 * still block it — the mic belongs to the call.
 */
object VoicePolicy {
    /** Hard cap for one recognition session; the mic is released after this no matter what. */
    const val SESSION_MS = 6_000L
    /** Gap after a clean session with no match. */
    const val GAP_OK = 400L
    /** Gap after silence / no-match / timeout. */
    const val GAP_SILENCE = 600L
    /** First backoff when the recogniser is busy (someone else has the mic). */
    const val GAP_BUSY_MIN = 1_000L
    /** Busy backoff never grows past this. */
    const val GAP_BUSY_MAX = 15_000L
    /** Another app is playing or a call is live: stay off the mic, re-check soon. */
    const val GAP_BLOCKED_MEDIA = 2_000L
    /** Pipo is open / switched off / permission missing: idle poll, no listening. */
    const val GAP_BLOCKED_IDLE = 3_000L
    /** No recognition service on the device / wake model not installed yet. */
    const val GAP_NO_RECOGNISER = 30_000L
    /** While listening: how often the blocking rules are re-checked. */
    const val GAP_RECHECK = 1_500L
    /** A deliberate breath: every N quiet sessions the mic rests for this long. */
    const val GAP_BREATH = 3_000L
    const val BREATH_EVERY = 8

    /** Exponential busy backoff, capped. 0 means "no backoff yet". */
    fun busyGap(previous: Long): Long =
        (if (previous <= 0L) GAP_BUSY_MIN else previous * 2).coerceAtMost(GAP_BUSY_MAX)

    /**
     * Should we be listening? Returns the reason we must not, or null to go ahead.
     * Kept as one pure function so the whole safety matrix is testable without a device.
     */
    fun blocked(
        enabled: Boolean,
        micGranted: Boolean,
        screenOn: Boolean,
        unlocked: Boolean,
        pipoForeground: Boolean,
        inCall: Boolean,
    ): BlockReason? = when {
        !enabled -> BlockReason.OFF
        !micGranted -> BlockReason.NO_MIC
        !screenOn -> BlockReason.SCREEN_OFF
        !unlocked -> BlockReason.LOCKED
        pipoForeground -> BlockReason.PIPO_OPEN
        inCall -> BlockReason.CALL
        else -> null
    }

    /** Gap after a finished session that matched nothing ([noMatchStreak] quiet sessions so far). */
    fun gapAfterSession(noMatchStreak: Int): Long = when {
        noMatchStreak > 0 && noMatchStreak % BREATH_EVERY == 0 -> GAP_BREATH
        noMatchStreak >= 4 -> GAP_SILENCE
        else -> GAP_OK
    }

    /** How long to wait before re-checking after [reason]. */
    fun gapFor(reason: BlockReason?): Long = when (reason) {
        null -> GAP_OK
        BlockReason.CALL -> GAP_BLOCKED_MEDIA
        BlockReason.SCREEN_OFF, BlockReason.LOCKED -> GAP_BLOCKED_IDLE
        BlockReason.PIPO_OPEN, BlockReason.OFF, BlockReason.NO_MIC -> GAP_BLOCKED_IDLE
    }
}
