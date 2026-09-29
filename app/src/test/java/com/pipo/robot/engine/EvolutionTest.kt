package com.pipo.robot.engine

import com.pipo.robot.data.ActivityType
import com.pipo.robot.data.Catalog
import com.pipo.robot.data.MemoryType
import com.pipo.robot.data.OwnedItem
import com.pipo.robot.data.PipoProject
import com.pipo.robot.data.PipoState
import com.pipo.robot.data.ProjectState
import com.pipo.robot.ui.render.PipoRig
import com.pipo.robot.ui.render.RoomState
import com.pipo.robot.ui.render.SceneGeo
import com.pipo.robot.ui.render.pipoLight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/** Tests for the evolution pass: livelier autonomy, memory-driven behaviour, and the animation rig. */
class EvolutionTest {

    private fun fresh(now: Long = System.currentTimeMillis()): PipoState = PipoState().apply {
        profile.createdAt = now - 10 * DAY
        profile.firstRunDone = true
        lastSimulatedAt = now
        settings.quietStartHour = 0; settings.quietEndHour = 0
    }

    /* ---------------- autonomy ---------------- */

    @Test
    fun repeatedActivitiesLoseAppeal() {
        val now = System.currentTimeMillis()
        val s = fresh(now)
        val before = BehaviorEngine.score(s, Env(hour = 14), now).first { it.type == ActivityType.READ }.score
        repeat(4) { s.recentActivities.add(ActivityType.READ) }
        val after = BehaviorEngine.score(s, Env(hour = 14), now).first { it.type == ActivityType.READ }.score
        assertTrue("before=$before after=$after", after < before * 0.25f)
    }

    @Test
    fun startRecordsHistoryAndStaysBounded() {
        val now = System.currentTimeMillis()
        val s = fresh(now)
        repeat(20) { BehaviorEngine.start(s, ActivityType.values()[it % ActivityType.values().size], now, Random(it)) }
        assertTrue(s.recentActivities.size <= 8)
    }

    @Test
    fun encouragedActivitiesBecomeHabits() {
        val now = System.currentTimeMillis()
        val s = fresh(now)
        val before = BehaviorEngine.score(s, Env(hour = 14), now).first { it.type == ActivityType.INSPECT_PLANT }.score
        repeat(5) { Chronicle.remember(s, MemoryType.EVENT, "you like it when I use the plant", 0.2f, now, "suggest:plant") }
        val after = BehaviorEngine.score(s, Env(hour = 14), now).first { it.type == ActivityType.INSPECT_PLANT }.score
        assertTrue("before=$before after=$after", after > before + 0.2f)
    }

    @Test
    fun absorbedActivitiesLastLongerAndOnlyForFocusedWork() {
        val now = System.currentTimeMillis()
        val s = fresh(now)
        assertEquals(0f, BehaviorEngine.absorbChance(s, ActivityType.SLEEP), 0f)
        assertEquals(0f, BehaviorEngine.absorbChance(s, ActivityType.SEEK_USER), 0f)
        assertTrue(BehaviorEngine.absorbChance(s, ActivityType.BUILD) > 0f)
        var absorbed = 0L; var normal = 0L; var nA = 0; var nN = 0
        repeat(400) {
            BehaviorEngine.start(s, ActivityType.READ, now, Random(it))
            if (s.activity.absorbed) { absorbed += s.activity.durationMs; nA++ } else { normal += s.activity.durationMs; nN++ }
        }
        assertTrue("absorbed=$nA normal=$nN", nA > 0 && nN > 0)
        assertTrue(absorbed / nA > (normal / nN) * 1.6f)
    }

    @Test
    fun absorbedOrSleepingPipoIsNeverDistracted() {
        val now = System.currentTimeMillis()
        val s = fresh(now)
        s.activity.absorbed = true
        repeat(200) { s.cooldowns.remove("distract"); assertNull(BehaviorEngine.distraction(s, ActivityType.READ, Env(hour = 14), Random(it), now)) }
        s.activity.absorbed = false
        repeat(200) { s.cooldowns.remove("distract"); assertNull(BehaviorEngine.distraction(s, ActivityType.SLEEP, Env(hour = 14), Random(it), now)) }
    }

    @Test
    fun distractionsHappenSometimesAndRespectCooldown() {
        val now = System.currentTimeMillis()
        val s = fresh(now)
        s.profile.traits.patience = 0.1f; s.profile.traits.curiosity = 0.9f
        var hits = 0
        repeat(500) { s.cooldowns.remove("distract"); if (BehaviorEngine.distraction(s, ActivityType.READ, Env(hour = 14), Random(it), now) != null) hits++ }
        assertTrue("hits=$hits", hits in 30..300)
        // right after one, the cooldown blocks the next
        s.cooldowns["distract"] = now + 60_000
        repeat(100) { assertNull(BehaviorEngine.distraction(s, ActivityType.READ, Env(hour = 14), Random(it), now)) }
    }

    @Test
    fun failedProjectsGetRetriedWithExperience() {
        val now = System.currentTimeMillis()
        val s = fresh(now)
        s.profile.traits.stubbornness = 0.9f; s.profile.traits.patience = 0.9f
        val def = Catalog.projects.first()
        s.projects.add(PipoProject(s.nextId(), def.id, def.title, state = ProjectState.FAILED, attempts = 1, finishedAt = now - DAY))
        listOf("copper_coil", "bent_spoon", "strange_screw", "tiny_battery").forEach { s.world.items.add(OwnedItem(s.nextId(), it, now)) }
        var retried: PipoProject? = null
        var seed = 0
        while (retried == null && seed < 40) {
            val p = Projects.maybeStart(s, Random(seed++), now, 1f)
            if (p != null && p.templateId == def.id) retried = p else if (p != null) s.projects.remove(p)
        }
        assertNotNull(retried)
        assertEquals(1, retried!!.attempts)
        assertTrue(retried.log.isNotEmpty())
        assertTrue(s.journal.any { it.title == "Pipo is trying again" })
    }

    @Test
    fun oldFailuresAreLetGo() {
        val now = System.currentTimeMillis()
        val s = fresh(now)
        val def = Catalog.projects.first()
        s.projects.add(PipoProject(s.nextId(), def.id, def.title, state = ProjectState.FAILED, attempts = 1, finishedAt = now - 5 * DAY))
        assertNull(Projects.retryCandidate(s, now))
    }

    @Test
    fun projectLogStaysBounded() {
        val now = System.currentTimeMillis()
        val s = fresh(now)
        listOf("copper_coil", "bent_spoon", "strange_screw").forEach { s.world.items.add(OwnedItem(s.nextId(), it, now)) }
        val rng = Random(7)
        var guard = 0
        while (s.activeProject() == null && guard++ < 50) Projects.maybeStart(s, rng, now, 1f)
        guard = 0
        while (s.activeProject() != null && guard++ < 80) {
            Discovery.neededTags(s).forEach { tag -> s.world.items.add(OwnedItem(s.nextId(), Catalog.items.first { tag in it.tags }.id, now)) }
            Projects.work(s, rng, now, 0.3f)
        }
        assertTrue(s.projects.all { it.log.size <= 6 })
    }

    /* ---------------- coming back ---------------- */

    @Test
    fun awayDigestReadsLikeHimAndIsBounded() {
        val rng = Random(1)
        assertNull(Dialogue.awayDigest(emptyList(), rng))
        val one = Dialogue.awayDigest(listOf("found a glowing pebble"), rng)!!
        assertTrue(one.startsWith("While you were gone I found a glowing pebble."))
        val many = Dialogue.awayDigest(listOf("a", "b", "c", "d", "e"), rng)!!
        assertTrue(many, many.startsWith("Okay, recap: I c, d, and e."))
        // never a guilt trip
        listOf(one, many).forEach { line -> listOf("miss", "lonely", "abandon", "where were you").forEach { assertTrue(line, !line.lowercase().contains(it)) } }
    }

    @Test
    fun offlineLifeLeavesAnAwayLog() {
        val now = System.currentTimeMillis()
        var nonEmpty = 0
        repeat(10) { seed ->
            val s = fresh(now); s.lastSimulatedAt = now - 2 * DAY
            Simulator.catchUp(s, now, Random(seed))
            assertTrue(s.awayLog.size <= 5)
            if (s.awayLog.isNotEmpty()) nonEmpty++
        }
        assertEquals(10, nonEmpty)
    }

    @Test
    fun mischievousPipoHasNewGreetings() {
        val now = System.currentTimeMillis()
        val kinds = mutableSetOf<GreetKind>()
        repeat(200) { seed ->
            val s = fresh(now)
            s.profile.traits.mischief = 0.8f
            s.activity.type = ActivityType.REST
            MoodEngine.setTransient(s, com.pipo.robot.data.Mood.MISCHIEVOUS, now, 60_000)
            kinds += Greeter.plan(s, now, 2 * HOUR, Random(seed)).kind
        }
        assertTrue(kinds.toString(), GreetKind.FAKE_SLEEP in kinds && GreetKind.PEEK_IN in kinds && GreetKind.MISCHIEF_HIDE in kinds)
    }

    @Test
    fun everyPrankHasAPlaceInTheRoom() {
        Pranks.all.forEach { assertTrue(it.key, SceneGeo.prankObject(it.key).isNotEmpty()) }
    }

    @Test
    fun ideaNotificationsSoundLikePipo() {
        val now = System.currentTimeMillis()
        val s = fresh(now)
        s.profile.userName = "Joel"
        val ev = Chronicle.event(s, com.pipo.robot.data.EventType.THOUGHT, 0.6f, now, "idea:Tiny lamp")
        val texts = (0 until 30).map { NotificationPolicy.text(ev, s, now, Random(it)) }.toSet()
        assertTrue(texts.toString(), texts.all { it.contains("idea", ignoreCase = true) })
    }

    /* ---------------- voice ---------------- */

    @Test
    fun speechStylesAreDetected() {
        assertEquals(SpeechStyle.WHISPER, SpeechStyles.style("(don't look at the plant)"))
        assertEquals(SpeechStyle.WHISPER, SpeechStyles.style("Psst. Come here."))
        assertEquals(SpeechStyle.LAUGH, SpeechStyles.style("Hehe. Got you."))
        assertEquals(SpeechStyle.SIGH, SpeechStyles.style("Ugh. Fine."))
        assertEquals(SpeechStyle.EXCITED, SpeechStyles.style("I FOUND SOMETHING"))
        assertEquals(SpeechStyle.QUESTION, SpeechStyles.style("Want to play?"))
        assertEquals(SpeechStyle.NORMAL, SpeechStyles.style("The plant grew."))
    }

    /* ---------------- animation rig ---------------- */

    private fun run(rig: PipoRig, secs: Float) { var t = 0f; while (t < secs) { rig.update(1f / 60f); t += 1f / 60f } }

    @Test
    fun rigTurnsAwayWhenSulkingAndFacesYouAgain() {
        val rig = PipoRig(3)
        rig.fidgetsEnabled = false
        rig.anim = AnimState.TURN_AWAY
        run(rig, 2.5f)
        assertTrue("yaw=${rig.yaw}", abs(rig.yaw) > 2f) // his back is to you
        rig.anim = AnimState.IDLE
        run(rig, 3f)
        assertTrue("yaw=${rig.yaw}", abs(rig.yaw) < 0.35f)
    }

    @Test
    fun rigFacesTheDirectionHeWalks() {
        val rig = PipoRig(4)
        rig.fidgetsEnabled = false
        rig.anim = AnimState.WALKING
        rig.moveDir = 1f
        run(rig, 1.5f)
        assertTrue("yaw=${rig.yaw}", rig.yaw > 0.4f && rig.yaw < 1.0f) // three-quarter, face still visible
        rig.moveDir = -1f
        run(rig, 1.5f)
        assertTrue("yaw=${rig.yaw}", rig.yaw < -0.4f && rig.yaw > -1.0f)
        assertTrue("headYaw=${rig.headYaw}", abs(rig.headYaw) <= 1.0f)
    }

    @Test
    fun mouthFollowsTheWords() {
        val rig = PipoRig(5)
        rig.talking = true
        rig.speak("aaaaaaaaaaaaaaaaaaaa", 14f)
        run(rig, 0.5f)
        val vowel = rig.talkLevel
        rig.speak("mmmmmmmmmmmmmmmmmmmm", 14f)
        run(rig, 0.5f)
        val closed = rig.talkLevel
        assertTrue("vowel=$vowel closed=$closed", vowel > 0.6f && closed < 0.2f)
        rig.talking = false
        run(rig, 0.5f)
        assertTrue(rig.talkLevel < 0.05f)
    }

    @Test
    fun landingSquashesThenRecovers() {
        val rig = PipoRig(6)
        run(rig, 0.5f)
        val rest = rig.squashNow
        rig.impact(1f)
        assertTrue(rig.squashNow < rest - 0.1f)
        run(rig, 1.5f)
        assertTrue(abs(rig.squashNow - rest) < 0.03f)
    }

    @Test
    fun everyPoseAndExpressionIsFinite() {
        val rig = PipoRig(7)
        for (a in AnimState.values()) for (e in listOf(Expr.CONTENT, Expr.DIZZY, Expr.WINK, Expr.LAUGH, Expr.WORRIED)) {
            rig.anim = a; rig.expr = e
            run(rig, 0.3f)
            val p = rig.pose
            listOf(p.bob, p.squash, p.lean, p.headTilt, p.armL, p.armR, p.yaw, p.cross, p.yawn, rig.yaw, rig.headYaw, rig.antenna, rig.face.pupil)
                .forEach { assertTrue("$a/$e", it.isFinite()) }
        }
    }

    /* ---------------- lighting ---------------- */

    @Test
    fun lightComesFromTheLampAtNight() {
        val night = RoomState(hour = 23.5f)
        val left = pipoLight(night, 120f, androidx.compose.ui.graphics.Color.White, torch = false)
        val right = pipoLight(night, 180f, androidx.compose.ui.graphics.Color.White, torch = false)
        assertTrue("left=${left.dir}", left.dir > 0f)   // lamp is to his right
        assertTrue("right=${right.dir}", right.dir < 0f) // lamp is to his left
        val far = pipoLight(night, 20f, androidx.compose.ui.graphics.Color.White, torch = false)
        val near = pipoLight(night, 145f, androidx.compose.ui.graphics.Color.White, torch = false)
        assertTrue(near.keyStrength > far.keyStrength)
    }
}
