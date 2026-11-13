package com.pipo.robot.unity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** The renderer can only report what happened, in the agreed shape; anything else is ignored. */
class UnityBridgeTest {
    @Test
    fun validEventsParse() {
        val e = UnityBridge.parse("""{"v":1,"type":"interaction","target":"npc:grumble","place":"hardware","message":"Grumble"}""")
        assertEquals(UnityBridge.Event("interaction", "hardware", "npc:grumble", "Grumble"), e)
        assertEquals("navComplete", UnityBridge.parse("""{"v":1,"type":"navComplete","place":"bakery"}""")?.type)
    }

    @Test
    fun wrongVersionUnknownTypesAndJunkAreDropped() {
        assertNull(UnityBridge.parse("""{"v":2,"type":"interaction"}"""))
        assertNull(UnityBridge.parse("""{"v":1,"type":"addCoins","amount":999}"""))
        assertNull(UnityBridge.parse("not json"))
        assertNull(UnityBridge.parse("""{"type":"ready"}"""))
    }

    @Test
    fun stateIsWrappedInAV1Envelope() {
        assertEquals("""{"v":1,"type":"state","state":{"x":1}}""", UnityBridge.stateMessage("""{"x":1}"""))
    }

    @Test
    fun withoutTheUnityLibraryTheBridgeIsSimplyUnavailable() {
        assertFalse(UnityBridge.available)
        assertFalse(UnityBridge.send("{}"))
    }
}
