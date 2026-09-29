package com.pipo.robot.engine

import com.pipo.robot.data.ActivityType
import com.pipo.robot.data.EventType
import com.pipo.robot.data.Frequency
import com.pipo.robot.data.MemoryType
import com.pipo.robot.data.Mood
import com.pipo.robot.data.NotificationRecord
import com.pipo.robot.data.OwnedItem
import com.pipo.robot.data.PipoState
import com.pipo.robot.data.ProjectState
import com.pipo.robot.data.UserResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class EngineTest {

    private fun fresh(now: Long = System.currentTimeMillis()): PipoState = PipoState().apply {
        profile.createdAt = now - 10 * DAY
        profile.firstRunDone = true
        lastSimulatedAt = now
        settings.quietStartHour = 0; settings.quietEndHour = 0 // disable quiet hours for deterministic tests
    }

    @Test
    fun lowEnergyMakesPipoSleepy() {
        val s = fresh()
        s.mood.energy = 0.05f
        assertEquals(Mood.SLEEPY, MoodEngine.derive(s, System.currentTimeMillis(), 14))
    }

    @Test
    fun irritationMakesPipoGrumpy() {
        val s = fresh()
        s.mood.irritation = 0.9f
        assertEquals(Mood.GRUMPY, MoodEngine.derive(s, System.currentTimeMillis(), 14))
    }

    @Test
    fun transientMoodOverridesAndExpires() {
        val s = fresh(); val now = System.currentTimeMillis()
        MoodEngine.setTransient(s, Mood.PROUD, now, 1000)
        assertEquals(Mood.PROUD, MoodEngine.derive(s, now + 10, 14))
        assertTrue(MoodEngine.derive(s, now + 5000, 14) != Mood.PROUD)
    }

    @Test
    fun personalityChangesChoices() {
        val now = System.currentTimeMillis()
        fun explores(curious: Boolean): Int {
            val s = fresh(now)
            val t = s.profile.traits
            if (curious) { t.curiosity = 0.95f; t.adventurousness = 0.9f; t.laziness = 0.1f }
            else { t.curiosity = 0.1f; t.adventurousness = 0.1f; t.laziness = 0.95f }
            val rng = Random(42)
            var n = 0
            repeat(2000) {
                s.cooldowns.clear()
                if (BehaviorEngine.choose(s, Env(hour = 14), now, rng, null) == ActivityType.EXPLORE) n++
            }
            return n
        }
        val curious = explores(true); val lazy = explores(false)
        assertTrue("curious=$curious lazy=$lazy", curious > lazy * 2)
    }

    @Test
    fun musicMakesPipoDance() {
        val s = fresh(); val rng = Random(1); val now = System.currentTimeMillis()
        var dances = 0
        repeat(300) { s.cooldowns.clear(); if (BehaviorEngine.choose(s, Env(hour = 14, music = true), now, rng, null) == ActivityType.DANCE) dances++ }
        assertTrue("dances=$dances", dances > 150)
    }

    @Test
    fun offlineSimulationIsBounded() {
        val now = System.currentTimeMillis()
        val s = fresh(now)
        s.lastSimulatedAt = now - 30 * DAY
        val r = Simulator.catchUp(s, now, Random(7))
        assertTrue(r.steps <= (3 * DAY / Simulator.STEP).toInt())
        assertTrue(r.steps > 0)
        assertTrue(s.lastSimulatedAt <= now)
        assertTrue(now - s.lastSimulatedAt < Simulator.STEP)
        assertTrue(s.memories.size <= 120)
    }

    @Test
    fun offlineLifeProducesStories() {
        val now = System.currentTimeMillis()
        var discoveries = 0; var journal = 0
        repeat(10) { seed ->
            val s = fresh(now); s.lastSimulatedAt = now - 2 * DAY
            Simulator.catchUp(s, now, Random(seed))
            discoveries += s.world.items.size
            journal += s.journal.size
        }
        assertTrue("discoveries=$discoveries", discoveries > 0)
        assertTrue("journal=$journal", journal > 0)
    }

    @Test
    fun projectsReachAnEnding() {
        val now = System.currentTimeMillis()
        val s = fresh(now)
        listOf("copper_coil", "bent_spoon", "strange_screw").forEach { s.world.items.add(OwnedItem(s.nextId(), it, now)) }
        val rng = Random(3)
        var p = Projects.maybeStart(s, rng, now, 1f)
        var tries = 0
        while (p == null && tries++ < 50) p = Projects.maybeStart(s, rng, now, 1f)
        assertNotNull(p)
        var guard = 0
        while (s.activeProject() != null && guard++ < 50) {
            // Give Pipo whatever he needs so the project can progress.
            Discovery.neededTags(s).forEach { tag ->
                val def = com.pipo.robot.data.Catalog.items.first { tag in it.tags }
                s.world.items.add(OwnedItem(s.nextId(), def.id, now))
            }
            Projects.work(s, rng, now)
        }
        assertTrue(p!!.state in setOf(ProjectState.DONE, ProjectState.FAILED, ProjectState.EVOLVED))
        assertTrue(s.journal.isNotEmpty())
    }

    @Test
    fun memoryDedupesAndPrunes() {
        val now = System.currentTimeMillis()
        val s = fresh(now)
        repeat(5) { Chronicle.remember(s, MemoryType.GAME, "memory games", 0.4f, now, "game:memory") }
        assertEquals(1, s.memories.count { it.key == "game:memory" })
        assertEquals(5, s.memories.first { it.key == "game:memory" }.count)
        repeat(300) { Chronicle.remember(s, MemoryType.CONVERSATION, "chat $it", 0.2f, now) }
        assertTrue(s.memories.size <= 120)
        assertTrue(s.memories.any { it.key == "game:memory" })
    }

    @Test
    fun notificationsRespectCooldownAndForeground() {
        val now = System.currentTimeMillis()
        val s = fresh(now)
        s.settings.frequency = Frequency.NORMAL
        s.lastUserInteractionAt = now - 5 * HOUR
        Chronicle.event(s, EventType.DISCOVERY, 0.9f, now - MINUTE, "1")
        assertNull(NotificationPolicy.decide(s, now, appForeground = true, rng = Random(1)))
        val d = NotificationPolicy.decide(s, now, appForeground = false, rng = Random(1))
        assertNotNull(d)
        // Record delivery, add a second event: cooldown must block it.
        d!!.event.notified = true
        s.notifications.add(NotificationRecord(s.nextId(), d.event.id, d.event.type, d.text, now, true))
        Chronicle.event(s, EventType.PROJECT_DONE, 0.95f, now, "2")
        assertNull(NotificationPolicy.decide(s, now + HOUR, false, Random(1)))
        assertNotNull(NotificationPolicy.decide(s, now + 8 * HOUR, false, Random(1)))
    }

    @Test
    fun notificationsRespectQuietHoursAndRecentInteraction() {
        val now = System.currentTimeMillis()
        val s = fresh(now)
        val h = hourOf(now)
        s.settings.quietStartHour = h; s.settings.quietEndHour = (h + 2) % 24
        s.lastUserInteractionAt = now - 5 * HOUR
        Chronicle.event(s, EventType.DISCOVERY, 0.9f, now, "1")
        assertNull(NotificationPolicy.decide(s, now, false, Random(1)))
        s.settings.quietStartHour = 0; s.settings.quietEndHour = 0
        s.lastUserInteractionAt = now - 10 * MINUTE
        assertNull(NotificationPolicy.decide(s, now, false, Random(1)))
    }

    @Test
    fun ignoredNotificationsMakePipoQuieter() {
        val now = System.currentTimeMillis()
        val s = fresh(now)
        s.settings.frequency = Frequency.FREQUENT
        s.lastUserInteractionAt = now - 2 * DAY
        repeat(3) { i -> s.notifications.add(NotificationRecord(s.nextId(), 0, EventType.HI, "hi", now - (4 + i) * HOUR, true, UserResponse.NONE)) }
        Chronicle.event(s, EventType.DISCOVERY, 0.9f, now, "1")
        // Normal FREQUENT gap is 3h and the last one was 4h ago, but 3 ignored in a row → backoff.
        assertNull(NotificationPolicy.decide(s, now, false, Random(1)))
    }

    @Test
    fun quietHoursWrapAroundMidnight() {
        val s = fresh()
        s.settings.quietStartHour = 22; s.settings.quietEndHour = 8
        assertTrue(NotificationPolicy.inQuietHours(s.settings, 23))
        assertTrue(NotificationPolicy.inQuietHours(s.settings, 3))
        assertTrue(!NotificationPolicy.inQuietHours(s.settings, 12))
    }

    @Test
    fun chatLearnsNameAndNeverSoundsLikeAnAssistant() {
        val now = System.currentTimeMillis()
        val s = fresh(now)
        val r = LocalBrain.respond(s, "my name is Joel", now, Random(1), awaitingName = false)
        assertEquals("Joel", s.profile.userName)
        assertTrue(r.text.contains("Joel"))
        val banned = listOf("how can i assist", "how can i help", "what can i help")
        listOf("hello", "can you help me", "what are you doing", "asdfgh qwerty?", "tell me a joke").forEach {
            val out = LocalBrain.respond(s, it, now, Random(2), false).text.lowercase()
            assertTrue(out, banned.none { b -> out.contains(b) })
        }
    }

    @Test
    fun greetingDependsOnState() {
        val now = System.currentTimeMillis()
        val s = fresh(now)
        s.activity.type = ActivityType.SLEEP
        assertEquals(GreetKind.SLEEPING, Greeter.plan(s, now, 5 * HOUR, Random(1)).kind)
        s.activity.type = ActivityType.BUILD
        Chronicle.event(s, EventType.DISCOVERY, 0.7f, now, "1")
        assertEquals(GreetKind.REVEAL_ITEM, Greeter.plan(s, now, 5 * HOUR, Random(1)).kind)
        s.profile.firstRunDone = false
        assertEquals(GreetKind.FIRST_WAKE, Greeter.plan(s, now, 5 * HOUR, Random(1)).kind)
    }
}

class PhoneCommandTest {
    private fun c(t: String) = PhoneCommands.parse(t)?.cmd

    @Test
    fun mediaAppsAndDeviceControls() {
        fun r(t: String) = PhoneCommands.parse(t)
        r("play arijit singh on spotify")!!.let { assertEquals(PhoneCmd.MEDIA_APP, it.cmd); assertEquals("spotify", it.extra); assertEquals("arijit singh", it.arg) }
        r("watch stranger things on netflix")!!.let { assertEquals("netflix", it.extra); assertEquals("stranger things", it.arg) }
        r("open hotstar")!!.let { assertEquals(PhoneCmd.MEDIA_APP, it.cmd); assertEquals("hotstar", it.extra); assertEquals("", it.arg) }
        assertEquals("youtube_music", r("play lofi on youtube music")!!.extra)          // longest alias wins
        assertEquals(PhoneCmd.YOUTUBE, r("play lofi beats on youtube")!!.cmd)
        assertEquals("instagram", r("open reels")!!.extra)
        assertNull(r("my friend sent me reels yesterday"))                                 // talking about it isn't a request
        assertEquals(PhoneCmd.OPEN_ANY, r("open whatsapp")!!.cmd)
        assertEquals("whatsapp", r("open whatsapp")!!.arg)
        assertNull(r("show me something funny"))
        assertEquals(PhoneCmd.NOW_PLAYING, c("what's playing"))
        assertEquals(PhoneCmd.NOW_PLAYING, c("what song is this"))
        assertEquals(PhoneCmd.LIST_MEDIA, c("what music apps do i have"))
        assertEquals("up", r("make the screen brighter")!!.arg)
        assertEquals("down", r("turn the brightness down")!!.arg)
        assertEquals("40", r("set brightness to 40%")!!.arg)
        assertEquals(PhoneCmd.SETTINGS, c("open brightness settings"))
        assertEquals("off", r("turn off auto rotate")!!.arg)
        assertEquals("on", r("turn on do not disturb")!!.arg)
        assertEquals("off", r("turn off dnd")!!.arg)
        assertEquals("silent", r("put my phone on silent")!!.arg)
        assertEquals("vibrate", r("vibrate mode")!!.arg)
        assertEquals("normal", r("turn off silent mode")!!.arg)
    }

    @Test
    fun parsesPhoneActions() {
        // natural phrasings seen on the device
        assertEquals(PhoneCmd.BATTERY, c("whats my battery"))
        assertEquals(PhoneCmd.BATTERY, c("what's the battery at"))
        assertEquals(PhoneCmd.BATTERY, c("is my battery charged"))
        assertEquals("cute robots", PhoneCommands.parse("search the web for cute robots")?.arg)
        assertEquals("cute robots", PhoneCommands.parse("search for cute robots")?.arg)
        assertEquals(PhoneCmd.FLASH_ON, c("Pipo, turn on the flashlight."))
        assertEquals(PhoneCmd.FLASH_OFF, c("turn the flashlight off"))
        assertEquals(PhoneCmd.FLASH_STATUS, c("is the torch on?"))
        assertEquals(PhoneCmd.MEDIA_PLAY, c("play some music"))
        assertEquals(PhoneCmd.MEDIA_PAUSE, c("pause"))
        assertEquals(PhoneCmd.MEDIA_NEXT, c("next song"))
        assertEquals(PhoneCmd.MEDIA_PREV, c("previous track"))
        assertEquals(PhoneCmd.VOLUME_UP, c("louder"))
        val yt = PhoneCommands.parse("play lofi beats on youtube")!!
        assertEquals(PhoneCmd.YOUTUBE, yt.cmd); assertEquals("lofi beats", yt.arg)
        assertEquals(PhoneCmd.YOUTUBE, c("Open YouTube"))
        assertEquals(PhoneCmd.YOUTUBE, c("play arijit singh"))
        PhoneCommands.parse("play coldplay on spotify")!!.let { assertEquals(PhoneCmd.MEDIA_APP, it.cmd); assertEquals("spotify", it.extra); assertEquals("coldplay", it.arg) }
        assertNull(c("play rock paper scissors"))
        assertEquals(PhoneCmd.CAMERA, c("open the camera"))
        assertEquals(PhoneCmd.CAMERA, c("take a photo"))
        assertEquals(PhoneCmd.SELFIE, c("take a selfie"))
        assertEquals(PhoneCmd.SHOW_PHOTO, c("let me show you a photo"))
        assertEquals("gallery", PhoneCommands.parse("open gallery")!!.arg)
        assertEquals("calculator", PhoneCommands.parse("open calculator")!!.arg)
        assertEquals("clock", PhoneCommands.parse("open the clock")!!.arg)
        assertEquals("browser", PhoneCommands.parse("open chrome")!!.arg)
        assertEquals("settings", PhoneCommands.parse("open settings")!!.arg)
        val wifi = PhoneCommands.parse("turn off wifi")!!
        assertEquals(PhoneCmd.SETTINGS, wifi.cmd); assertEquals("wifi", wifi.extra); assertTrue(wifi.blocked)
        assertEquals("bluetooth", PhoneCommands.parse("open bluetooth settings")!!.extra)
        assertEquals("display", PhoneCommands.parse("display settings")!!.extra)
        assertEquals("battery", PhoneCommands.parse("battery settings")!!.extra)
        assertEquals("datetime", PhoneCommands.parse("open date and time settings")!!.extra)
        assertEquals(PhoneCmd.TIME, c("What's the time?"))
        assertEquals(PhoneCmd.DATE, c("what day is it"))
        assertEquals(PhoneCmd.BATTERY, c("how much battery is left"))
        assertEquals(300, PhoneCommands.parse("set a timer for 5 minutes")!!.seconds)
        val alarm = PhoneCommands.parse("set an alarm for 6:30 am")!!
        assertEquals(6, alarm.hour); assertEquals(30, alarm.minute); assertTrue(alarm.needsConfirm)
        assertEquals(19, PhoneCommands.parse("wake me up at 7 pm")!!.hour)
        assertEquals(PhoneCmd.URL, c("open github.com"))
        assertEquals("cute robots", PhoneCommands.parse("search for cute robots")!!.arg)
        assertEquals(PhoneCmd.MAPS, c("navigate to kochi airport"))
        assertEquals(PhoneCmd.DIAL, c("call mom"))
        assertNull(c("call me Joel"))
        assertEquals("Hello World", PhoneCommands.parse("copy Hello World")!!.arg)
        assertEquals(PhoneCmd.SHARE, c("share this is cool"))
        assertNull(c("how are you"))
        assertNull(c("tell me a joke"))
    }

    @Test
    fun tinyUtilities() {
        assertEquals("84", PhoneCommands.parse("what's 12*7")!!.extra)
        assertEquals("20", PhoneCommands.parse("25 percent of 80")!!.extra)
        assertEquals("8", PhoneCommands.parse("5 plus 3")!!.extra)
        assertEquals("14", PhoneCommands.parse("calculate (2+5)*2")!!.extra)
        assertEquals(PhoneCmd.CALC, c("10 / 4"))
        assertEquals("", PhoneCommands.parse("5/0")?.extra ?: "")
        assertTrue(PhoneCommands.parse("5 km to miles")!!.extra.contains("3.1069"))
        assertTrue(PhoneCommands.parse("convert 100 c to f")!!.extra.contains("212"))
        assertTrue(PhoneCommands.parse("2 hours in minutes")!!.extra.contains("120"))
        assertNull(c("5 km to kg"))
    }

    @Test
    fun linesSoundLikePipoNotAnAssistant() {
        val banned = listOf("certainly", "command", "completed", "assist", "enabled", "options")
        val rng = Random(3)
        for (cmd in PhoneCmd.values()) for (m in com.pipo.robot.data.Mood.values()) {
            val r = PhoneRequest(cmd, arg = "x", seconds = 60, hour = 7, extra = "wifi")
            val l = PhoneCommands.line(r, m, rng, PhoneInfo(battery = 12, torchOn = true)).lowercase()
            banned.forEach { assertTrue("$cmd: $l", !l.contains(it)) }
        }
    }

    @Test
    fun chatRoutesPhoneRequestsAndKeepsNames() {
        val s = PipoState(); val now = System.currentTimeMillis()
        val r = LocalBrain.respond(s, "turn on flashlight", now, Random(1), false)
        assertEquals(ChatAction.PHONE, r.action); assertEquals(PhoneCmd.FLASH_ON, r.phone!!.cmd)
        LocalBrain.respond(s, "call me Joel", now, Random(1), false)
        assertEquals("Joel", s.profile.userName)
    }
}
