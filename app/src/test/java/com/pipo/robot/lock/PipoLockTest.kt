package com.pipo.robot.lock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Pipo lock" has to be heard however the recogniser spells it, and nothing else may lock the phone. */
class PipoLockTest {
    @Test
    fun hearsPipoLockTheWaysSpeechRecognitionWritesIt() {
        for (s in listOf("Pipo lock", "pipo lock", "Pippo lock", "peepo lock", "Pepo lock", "Pipo, lock!", "pipo locked", "Pipo log", "hey pipo lock",
            "Pipo please lock", "lock my phone pipo", "PIPO LOCK")) assertTrue(s, PipoLock.matches(s))
    }

    @Test
    fun ordinaryTalkNeverLocksThePhone() {
        for (s in listOf("pipo look at this", "I lost my lock", "hello pipo", "the lock is broken", "pipo", "lock", "pipeline locker", "pipo blocked me"))
            assertFalse(s, PipoLock.matches(s))
    }
}
