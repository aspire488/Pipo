package com.pipo.robot.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The safety matrix of the background listener: it must never touch the microphone while someone
 * else owns the sound, must always be able to say "no", and must not run hot when busy.
 */
class VoicePolicyTest {
    private fun allBlockedExcept(
        enabled: Boolean = true, mic: Boolean = true, screen: Boolean = true, unlocked: Boolean = true,
        pipoOpen: Boolean = false, call: Boolean = false,
    ) = VoicePolicy.blocked(enabled, mic, screen, unlocked, pipoOpen, call)

    @Test
    fun listensOnlyWhenEverythingAllowsIt() {
        assertNull(allBlockedExcept())
    }

    @Test
    fun neverListensInACall() {
        // media playing no longer matters: the listener takes no audio focus, so it can't pause anyone
        assertEquals(BlockReason.CALL, allBlockedExcept(call = true))
    }

    @Test
    fun neverListensWhenItShouldNotBeAbleTo() {
        assertEquals(BlockReason.OFF, allBlockedExcept(enabled = false))
        assertEquals(BlockReason.NO_MIC, allBlockedExcept(mic = false))
        assertEquals(BlockReason.SCREEN_OFF, allBlockedExcept(screen = false))
        assertEquals(BlockReason.LOCKED, allBlockedExcept(unlocked = false))
        assertEquals(BlockReason.PIPO_OPEN, allBlockedExcept(pipoOpen = true))
    }

    @Test
    fun offAndNoMicBeatEverythingElse() {
        assertEquals(BlockReason.OFF, allBlockedExcept(enabled = false, call = true, screen = false))
        assertEquals(BlockReason.NO_MIC, allBlockedExcept(mic = false, pipoOpen = true, call = true))
    }

    @Test
    fun sessionsAreBoundedAndGapsAreShort() {
        assertTrue("session must be capped", VoicePolicy.SESSION_MS in 1..10_000)
        assertTrue("clean gap stays short", VoicePolicy.GAP_OK < VoicePolicy.SESSION_MS)
        assertTrue("media/call blocks never poll faster than a sane re-check", VoicePolicy.GAP_BLOCKED_MEDIA >= 1_000)
    }

    @Test
    fun busyBackoffGrowsAndIsCapped() {
        assertEquals(VoicePolicy.GAP_BUSY_MIN, VoicePolicy.busyGap(0))
        assertEquals(2_000L, VoicePolicy.busyGap(1_000L))
        assertEquals(4_000L, VoicePolicy.busyGap(2_000L))
        assertEquals(8_000L, VoicePolicy.busyGap(4_000L))
        assertEquals(VoicePolicy.GAP_BUSY_MAX, VoicePolicy.busyGap(8_000L))
        assertEquals(VoicePolicy.GAP_BUSY_MAX, VoicePolicy.busyGap(VoicePolicy.GAP_BUSY_MAX))
    }

    @Test
    fun quietStreaksEventuallyBreathe() {
        assertEquals(VoicePolicy.GAP_OK, VoicePolicy.gapAfterSession(1))
        assertEquals(VoicePolicy.GAP_SILENCE, VoicePolicy.gapAfterSession(5))
        assertEquals(VoicePolicy.GAP_BREATH, VoicePolicy.gapAfterSession(VoicePolicy.BREATH_EVERY))
        assertEquals(VoicePolicy.GAP_BREATH, VoicePolicy.gapAfterSession(VoicePolicy.BREATH_EVERY * 2))
    }

    @Test
    fun blockedReasonsAllHaveAWaitTime() {
        for (r in BlockReason.entries) assertTrue("gap for $r", VoicePolicy.gapFor(r) > 0)
        assertEquals(VoicePolicy.GAP_OK, VoicePolicy.gapFor(null))
    }
}
