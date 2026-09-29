package com.pipo.robot.engine

import com.pipo.robot.engine.PhoneNotifs.App
import com.pipo.robot.engine.PhoneNotifs.Event
import com.pipo.robot.engine.PhoneNotifs.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PhoneNotifsTest {

    @Test
    fun onlyChatAndSocialAppsAreNoticed() {
        assertEquals(App.WHATSAPP, PhoneNotifs.appFor("com.whatsapp"))
        assertEquals(App.INSTAGRAM, PhoneNotifs.appFor("com.instagram.android"))
        assertNull(PhoneNotifs.appFor("com.google.android.gm"))         // email: ignored
        assertNull(PhoneNotifs.appFor("com.chase.sig.android"))         // bank: ignored
        assertNull(PhoneNotifs.appFor("com.pipo.robot"))
    }

    @Test
    fun instagramPhrasing() {
        assertEquals(Kind.REEL, PhoneNotifs.classify(App.INSTAGRAM, "sent you a reel by someone"))
        assertEquals(Kind.LIKE, PhoneNotifs.classify(App.INSTAGRAM, "liked your photo."))
        assertEquals(Kind.FOLLOW, PhoneNotifs.classify(App.INSTAGRAM, "started following you."))
        assertEquals(Kind.COMMENT, PhoneNotifs.classify(App.INSTAGRAM, "commented: nice!"))
        assertEquals(Kind.STORY, PhoneNotifs.classify(App.INSTAGRAM, "mentioned you in their story"))
        assertEquals(Kind.POST, PhoneNotifs.classify(App.INSTAGRAM, "sent you a post"))
        assertEquals(Kind.MESSAGE, PhoneNotifs.classify(App.INSTAGRAM, "hey are you coming tomorrow"))
    }

    @Test
    fun chatAppsOnlyTrustAttachmentMarkers() {
        assertEquals(Kind.PHOTO, PhoneNotifs.classify(App.WHATSAPP, "📷 Photo"))
        assertEquals(Kind.VOICE, PhoneNotifs.classify(App.WHATSAPP, "🎤 Voice message (0:04)"))
        assertEquals(Kind.VIDEO, PhoneNotifs.classify(App.WHATSAPP, "🎥 Video"))
        // words inside someone's actual message must not change the kind
        assertEquals(Kind.MESSAGE, PhoneNotifs.classify(App.WHATSAPP, "I liked the video you sent, send me that reel"))
        assertEquals(Kind.MESSAGE, PhoneNotifs.classify(App.WHATSAPP, "what a story lol"))
    }

    @Test
    fun calls() {
        assertEquals(Kind.CALL, PhoneNotifs.classify(App.WHATSAPP, "Incoming voice call", isCall = false))
        assertEquals(Kind.CALL, PhoneNotifs.classify(App.TELEGRAM, "", isCall = true))
    }

    @Test
    fun linesNeverRepeatContent() {
        // The line is built from (app, kind) only: nothing from the notification can reach it.
        val rng = Random(1)
        repeat(200) {
            val evs = List(1 + rng.nextInt(4)) { Event(App.entries.random(rng), Kind.entries.random(rng)) }
            val line = PhoneNotifs.line(evs, rng)
            assertTrue(line.isNotBlank())
            assertFalse(line.contains("{"))
        }
    }

    @Test
    fun burstsAreSummarised() {
        val rng = Random(3)
        val many = List(4) { Event(App.WHATSAPP, Kind.MESSAGE) }
        val l = PhoneNotifs.line(many, rng)
        assertTrue(l, l.contains("WhatsApp"))
        val mixed = listOf(Event(App.WHATSAPP, Kind.MESSAGE), Event(App.INSTAGRAM, Kind.REEL))
        assertTrue(PhoneNotifs.line(mixed, rng).isNotBlank())
    }
}
