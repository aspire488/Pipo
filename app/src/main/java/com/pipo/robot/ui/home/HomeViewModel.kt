package com.pipo.robot.ui.home

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.pipo.robot.BuildConfig
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pipo.robot.ai.BrainContext
import com.pipo.robot.ai.ChatBrain
import com.pipo.robot.ai.Brains
import com.pipo.robot.ai.Turn
import com.pipo.robot.ai.NoBrain
import com.pipo.robot.data.ActivityType
import com.pipo.robot.data.Catalog
import com.pipo.robot.data.EventType
import com.pipo.robot.data.ItemDef
import com.pipo.robot.data.JournalCategory
import com.pipo.robot.data.MemoryType
import com.pipo.robot.data.Mood
import com.pipo.robot.data.OwnedItem
import com.pipo.robot.data.PipoProject
import com.pipo.robot.data.PipoRepository
import com.pipo.robot.data.ProjectDef
import com.pipo.robot.data.ProjectState
import com.pipo.robot.data.Station
import com.pipo.robot.data.UserResponse
import com.pipo.robot.engine.AnimState
import com.pipo.robot.engine.BehaviorEngine
import com.pipo.robot.engine.ChatAction
import com.pipo.robot.engine.ChatResult
import com.pipo.robot.engine.Chronicle
import com.pipo.robot.engine.Dialogue
import com.pipo.robot.engine.DistractionKind
import com.pipo.robot.engine.MINUTE
import com.pipo.robot.engine.EmoteKind
import com.pipo.robot.engine.Env
import com.pipo.robot.engine.Expr
import com.pipo.robot.engine.GreetKind
import com.pipo.robot.engine.Greeter
import com.pipo.robot.engine.Greeting
import com.pipo.robot.engine.HOUR
import com.pipo.robot.engine.DAY as DAY_MS
import com.pipo.robot.engine.LocalBrain
import com.pipo.robot.engine.MoodEngine
import com.pipo.robot.engine.OfferKind
import com.pipo.robot.engine.MediaApps
import com.pipo.robot.engine.PhoneNotifs
import com.pipo.robot.notify.PipoNotificationListener
import com.pipo.robot.engine.Outcome
import com.pipo.robot.engine.Personality
import com.pipo.robot.engine.PhoneCmd
import com.pipo.robot.engine.PhoneCommands
import com.pipo.robot.engine.PhoneInfo
import com.pipo.robot.engine.PhoneRequest
import com.pipo.robot.engine.Reactions
import com.pipo.robot.engine.ScreenTime
import com.pipo.robot.engine.Pranks
import com.pipo.robot.engine.Sfx
import com.pipo.robot.engine.Simulator
import com.pipo.robot.engine.Trait
import com.pipo.robot.engine.Vocab
import com.pipo.robot.engine.hourOf
import com.pipo.robot.phone.PhoneActions
import com.pipo.robot.phone.PhoneAwareness
import com.pipo.robot.phone.PhoneState
import com.pipo.robot.data.Drawing
import com.pipo.robot.data.Foods
import com.pipo.robot.data.ItemShape
import com.pipo.robot.data.PetActivity
import com.pipo.robot.data.Photo
import com.pipo.robot.data.Places
import com.pipo.robot.data.TripReport
import com.pipo.robot.engine.Clip
import com.pipo.robot.engine.FeedLife
import com.pipo.robot.engine.FoodLife
import com.pipo.robot.engine.Mystery
import com.pipo.robot.engine.PetEngine
import com.pipo.robot.engine.Trips
import com.pipo.robot.engine.Weather
import com.pipo.robot.engine.WeatherEngine
import com.pipo.robot.engine.WeatherNow
import com.pipo.robot.engine.WildlifeLife
import com.pipo.robot.engine.Experience
import com.pipo.robot.engine.Rituals
import com.pipo.robot.ui.games.GameLog
import com.pipo.robot.ui.render.Critters
import com.pipo.robot.ui.render.Fidget
import com.pipo.robot.ui.render.HideSpot
import com.pipo.robot.ui.render.PetAnim
import com.pipo.robot.ui.render.PetFace
import com.pipo.robot.engine.NibLife
import com.pipo.robot.ui.render.PetThought
import com.pipo.robot.ui.render.PetRig
import com.pipo.robot.ui.render.PinnedPhoto
import com.pipo.robot.ui.render.PipoLight
import com.pipo.robot.ui.render.PipoRig
import com.pipo.robot.ui.render.RoomState
import com.pipo.robot.ui.render.SceneGeo
import com.pipo.robot.ui.render.WallDrawing
import com.pipo.robot.ui.render.dayFactor
import com.pipo.robot.ui.render.pipoLight
import com.pipo.robot.voice.PipoVoice
import com.pipo.robot.voice.charsPerSecond
import com.pipo.robot.engine.SpeechStyles
import com.pipo.robot.voice.SpeechInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/* Small scripted steps Pipo performs in order. The autonomy engine decides WHAT; beats are HOW. */
sealed class Beat {
    /** x = world position in u; null = come to the front, towards the user. */
    class Move(val x: Float?, val run: Boolean = false, val sneak: Boolean = false) : Beat()
    class Act(val anim: AnimState?, val secs: Float, val expr: Expr? = null) : Beat()
    class Say(val text: String, val sfx: Sfx? = null, val choices: List<Choice> = emptyList()) : Beat()
    class Emote(val kind: EmoteKind) : Beat()
    class Do(val fn: () -> Unit) : Beat()
    class Wait(val secs: Float) : Beat()
}

class Choice(val label: String, val onPick: () -> Unit)
data class Bubble(val id: Long, val text: String, val choices: List<Choice>)

sealed class Reveal {
    class Item(val item: OwnedItem, val def: ItemDef) : Reveal()
    class Project(val project: PipoProject, val def: ProjectDef) : Reveal()
    /** What he brought home: the bag, the photo, the story. */
    class Trip(val report: TripReport, val photo: Photo?) : Reveal()
    class Art(val drawing: Drawing) : Reveal()
    class Snap(val photo: Photo) : Reveal()
}

fun moodGlow(m: Mood): Long = when (m) {
    Mood.HAPPY, Mood.PROUD -> 0xFFFFC27A
    Mood.EXCITED -> 0xFFFFD36E
    Mood.SLEEPY -> 0xFF9FA8FF
    Mood.BORED -> 0xFF9AA7B8
    Mood.GRUMPY -> 0xFFFF7A6B
    Mood.LONELY -> 0xFF7FB6FF
    Mood.NERVOUS -> 0xFFFFE08A
    Mood.EMBARRASSED -> 0xFFFF8FA3
    Mood.MISCHIEVOUS -> 0xFFC99BFF
    Mood.CURIOUS, Mood.RELAXED -> 0xFF8FF5E2
    Mood.WORRIED -> 0xFFB8C8E8
    Mood.THOUGHTFUL -> 0xFFA8C8FF
    Mood.PLAYFUL -> 0xFFFFB86B
}

/** Where he fell asleep, if not in bed. */
enum class SleepSpot { BED, RUG, BOX, DESK }

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    val repo = PipoRepository.get(app)
    val rig = PipoRig(System.nanoTime().toInt())
    private val rng = Random(System.nanoTime())
    private val awareness = PhoneAwareness(app)
    val phoneActions = PhoneActions(app)
    val voice = PipoVoice(app)
    private val speech = SpeechInput(app)

    // ---- world state (u = world units)
    var geo: SceneGeo? = null
        private set
    var pipoX = SceneGeo.BED_PIVOT
        private set
    private var inBed = true
    var bedBlend = 1f
        private set
    var lift = 0f
        private set
    private var liftVel = 0f
    private var dropFrom = 0f
    private var dragging = false
    var camU = 0f
        private set
    private var camHoldUntil = 0f
    private var camFocusU: Float? = null
    private var camFocusUntil = 0f
    private var targetX: Float? = null
    private var running = false
    var ballU = 233f
        private set
    private var ballTarget = 233f
    var clock = 0f
        private set

    // ---- UI-observable
    var frame by mutableLongStateOf(0L)
    var bubble by mutableStateOf<Bubble?>(null)
    var reveal by mutableStateOf<Reveal?>(null)
    var listening by mutableStateOf(false)
    /** A spoken conversation is going: after he answers out loud, he listens again (until you stop or go quiet). */
    var voiceConvo by mutableStateOf(false)
    private var relistenReadyAt = 0f
    var heard by mutableStateOf("")
    var micLevel by mutableFloatStateOf(0f)
    var userLine by mutableStateOf<String?>(null)
    var openChat by mutableStateOf(false)
    var navRequest by mutableStateOf<String?>(null)
    var askNotifPermission by mutableStateOf(false)
    /** UI should open the system photo picker (no storage permission; user picks one photo). */
    var pickPhoto by mutableStateOf(false)
    /** A photo the user chose to show Pipo. Kept in memory only, never saved. */
    var photo by mutableStateOf<Bitmap?>(null)
    var room by mutableStateOf(RoomState(hour = 12f))
        private set
    var mood by mutableStateOf(Mood.RELAXED)
        private set

    private var phoneState = PhoneState()
    private var phoneInit = false

    // ---- director internals
    private val beats = ArrayDeque<Beat>()
    private var cur: Beat? = null
    private var curT = 0f
    private var sayDone = false
    private var sayMin = 0f
    private var sayBubbleId = 0L
    private var bubbleHideAt = -1f
    private var activity: ActivityType? = null
    private var activityEnd = 0f
    private var activityArrived = false
    private var lastActivity: ActivityType? = null
    private var reactExpr: Expr? = null
    private var reactUntil = 0f
    private var patting = false
    private var patStart = 0f
    private val pokes = ArrayDeque<Float>()
    private var engineAcc = 0f
    private var saveAcc = 0f
    private var microAt = 8f
    private var lastEnvReact = -100f
    private var firstWakePending = false
    private var awaitingName = false
    private var booted = false
    private var bubbleSeq = 0L
    private var userLineUntil = 0f
    private var brain: ChatBrain = NoBrain
    /** Last few exchanges, so the chat brain can follow the conversation. Session only, never saved. */
    private val chatHistory = ArrayDeque<Turn>()
    private var brainFailedOnce = false
    private var aiThinking = false
    private val sessionFlags = mutableSetOf<String>()
    private var energy = 0.8f
    private var lastSpoken = ""
    private val shakes = ArrayDeque<Float>()
    private var lastShakePeak = 0L
    private var lastShakeAt = 0L
    private val sensors = app.getSystemService(SensorManager::class.java)
    private val shakeListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            if (!calmMotion) {
                tiltX += ((-e.values[0] / 9.81f).coerceIn(-1f, 1f) - tiltX) * 0.08f
                tiltY += ((e.values[2] / 9.81f - 0.55f).coerceIn(-1f, 1f) - tiltY) * 0.08f
            }
            val g = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]) / 9.81f
            if (g > 2.4f) {
                val t = SystemClock.elapsedRealtime()
                if (t - lastShakePeak in 60..700 && t - lastShakeAt > 2500) { lastShakeAt = t; onShake() }
                lastShakePeak = t
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }
    private var sounds = true

    // ---- evolution pass: presence
    private var sneaking = false
    private var moveSpeed = 0f
    private var fridgeUntil = -1f
    private var fridgeOpen = 0f
    private var moveDelay = 0f
    /** Phone tilt, low-passed (-1..1). Drives parallax depth. */
    var tiltX = 0f
        private set
    var tiltY = 0f
        private set
    /** Camera dolly: leans in when Pipo talks to you or shows you something. */
    var camZoom = 1f
        private set
    private var plantRustle = 0f
    private var attentionAt = 3f
    private var restPose: AnimState? = null
    private var absorbed = false
    private var absorbedPokeAt = -100f
    private var fakeSleeping = false
    private var fakeSleepUntil = 0f
    private var lastYawnSound = -100f
    private var lastDebugLog = 0f
    private var sociability = 0.55f
    private var relationship = 0.1f
    /** True between onResume and onPause: Pipo is actually on screen. */
    private var onScreen = false
    private val pendingNotifs = mutableListOf<PhoneNotifs.Event>()
    private var firstNotifAt = 0f
    private var lastNotifReact = -100f

    // ---- schema 2: a life beyond the room
    /** He's out. The room is empty and there's a note on the door. */
    var away by mutableStateOf(false)
        private set
    /** The note, when you tap the door while he's out. */
    var doorNote by mutableStateOf<String?>(null)
    /** His texts while he's out (his little phone). */
    var text by mutableStateOf<String?>(null)
    private var textUntil = 0f
    private var doorOpen = 0f
    private var doorTarget = 0f
    /** Where he's hiding, if he is. The painter draws the hiding place in front of him. */
    var hideSpot: HideSpot? = null
        private set
    private var hideEmbarrassed = false
    private var nextPeek = 0f
    private var sleepSpot = SleepSpot.BED
    private var carrying = false
    private var kickups = false
    /** Which game he's playing in the toy corner (football by default; cricket with Nib bowling; paddle bounces). */
    private var indoorSport = com.pipo.robot.engine.Sport.FOOTBALL
    private var sportChosen = false
    private var cooking = false
    private var ballLift = 0f
    private var lastHum = -100f
    private var lastThunderReact = -100f
    private var lastStormPhase = 0f
    private var thunderAt = -1f
    private var weather = WeatherNow(Weather.CLEAR, 0.5f)
    private var weatherCheckedAt = -100f
    private var pendingCam: Float? = null
    // Nib
    val pet = PetRig(System.nanoTime().toInt() xor 0x5EED)
    var petX = 96f
        private set
    private var petTarget: Float? = null
    private var petRunning = false
    private var petAct = PetActivity.NAP
    private var petDecideAt = 1.5f
    /** Nib is in the room (adopted and not out with Pipo). */
    var petHome = true
        private set
    private val petPokes = ArrayDeque<Float>()
    private var petStealArrive = false
    private var lastPokeSpot = com.pipo.robot.ui.render.PipoRig.PokeSpot.BODY
    // his faint reflection in the window at night
    val reflection = PipoRig(7)
    /** Less camera motion: your setting, or the system's "remove animations". */
    private var calmMotion = false
    /** What a screen reader says about the room: where he is and what he's doing, in plain words. */
    var sceneDescription by mutableStateOf("Pipo's room.")
        private set

    init {
        viewModelScope.launch {
            // Opt-in notification noticing: only (app, kind) arrives here, only while he's on screen.
            PipoNotificationListener.bus.collect { e ->
                if (!onScreen) return@collect
                if (pendingNotifs.isEmpty()) firstNotifAt = clock
                if (pendingNotifs.size < 12) pendingNotifs += e
            }
        }
        viewModelScope.launch {
            while (true) {
                // only while he's actually on screen: no polling in the background
                if (onScreen) {
                    val ps = withContext(Dispatchers.Default) { runCatching { awareness.read() }.getOrNull() }
                    if (ps != null) onPhoneState(ps)
                }
                delay(3000)
            }
        }
    }

    /* ================================================================ */
    /*  Lifecycle                                                        */
    /* ================================================================ */

    fun setViewport(w: Float, h: Float) {
        val g = geo
        if (g == null || g.w != w || g.h != h) {
            geo = SceneGeo(w, h)
            // Cold start: the camera is where you left it, not wherever he happens to be.
            camU = clampCam(pendingCam ?: (pipoX - geo!!.viewU / 2f))
            pendingCam = null
        }
    }

    fun onResume(action: String?, game: String?, recordId: Long) {
        onScreen = true
        // his weather is your weather: refresh it in the background (his own climate if offline)
        val useReal = repo.read { it.settings.realWeather }
        if (!useReal) WeatherEngine.live = null
        else if (WeatherEngine.live == null) com.pipo.robot.data.RealWeather.restore(getApplication())
        if (useReal) viewModelScope.launch {
            if (com.pipo.robot.data.RealWeather.refresh(getApplication())) {
                weather = repo.read { WeatherEngine.at(it.seed, System.currentTimeMillis()) }
                buildRoom()
            }
        }
        val now = System.currentTimeMillis()
        val settings = repo.read { it.settings.copy() }
        calmMotion = settings.calmMotion || systemAnimationsOff()
        voice.mode = settings.voiceMode
        sounds = settings.sounds
        voice.synth.enabled = settings.sounds
        sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensors.registerListener(shakeListener, it, SensorManager.SENSOR_DELAY_UI) }
        // the online AI only if you've left it on (Settings → Privacy)
        brain = if (settings.onlineAi) (brain.takeIf { it !== NoBrain } ?: Brains.fromBuild()) else NoBrain

        var digest: String? = null
        var fromMessage = false
        var awayMs = 0L
        val (greet, notNowRecently) = repo.mutate { s ->
            val away = if (s.lastSeenByUserAt == 0L) Long.MAX_VALUE / 4 else now - s.lastSeenByUserAt
            awayMs = away
            Simulator.catchUp(s, now, rng)
            MoodEngine.derive(s, now, hourOf(now))
            if (recordId != 0L) s.notifications.firstOrNull { it.id == recordId }?.let { r ->
                r.response = if (action == "play") UserResponse.PLAYED else UserResponse.OPENED
                Personality.nudge(s, Trait.SOCIABILITY, 0.004f)
            }
            // Opened by tapping one of his messages: that message is what he wants to talk about.
            val tapped = if (recordId != 0L && action != "play") s.notifications.firstOrNull { it.id == recordId }?.let { r -> s.events.firstOrNull { it.id == r.eventId } } else null
            if (tapped != null) fromMessage = true
            val g = when {
                tapped != null -> Greeter.plan(s, now, away, rng, fromNotification = tapped)
                booted && away < 3 * 60_000L -> Greeting(GreetKind.BRIEF, "")
                else -> Greeter.plan(s, now, away, rng)
            }
            g.event?.shownInApp = true
            val last = s.notifications.lastOrNull()
            val nn = last != null && last.response == UserResponse.NOT_NOW && now - last.timestamp < 12 * HOUR
            s.lastSeenByUserAt = now
            s.lastSimulatedAt = now
            if (away > 3 * MINUTE) Rituals.noteVisit(s, hourOf(now))
            if (away > 45 * MINUTE && s.awayLog.isNotEmpty()) digest = Dialogue.awayDigest(s.awayLog, rng)
            g to nn
        }
        refreshMood()
        weather = repo.read { WeatherEngine.at(it.seed, now) }
        var homecoming: TripReport? = null
        if (away && repo.read { it.trip } == null) {
            // he got home while you were on another screen (the map, the journal): you were only
            // gone a moment, so you still get the homecoming, door and all. Longer absences get
            // the "while you were gone" recap instead.
            homecoming = repo.read { s -> s.lastTrip?.takeIf { !it.told && now - it.at < 10 * MINUTE } }
            activity = null; inBed = false; bedBlend = 0f; hideSpot = null; doorTarget = 0f
            // otherwise he's just inside the door, bag and all
            if (homecoming == null) { away = false; pipoX = SceneGeo.DOOR_X - 12f }
        }
        if (!booted || greet.kind == GreetKind.FIRST_WAKE) {
            // first launch, or Pipo was reset from Settings: start fresh, asleep in bed
            if (booted) { interrupt(); activity = null; reveal = null; bubble = null; photo = null; away = false; hideSpot = null }
            val firstEver = greet.kind == GreetKind.FIRST_WAKE
            if (!firstEver && !booted) pendingCam = repo.read { it.world.objectStates["cam"]?.toFloatOrNull() }
            booted = true
            placeForCurrentActivity()
            if (firstEver) firstWakePending = true
        } else if (awayMs >= 10 * MINUTE && !fromMessage && !away) {
            // You were gone a while and his life went on: he's wherever the engine says he is now.
            // (The camera stays where you left it.)
            interrupt(); activity = null; reveal = null
            placeForCurrentActivity(moveCamera = false)
        }
        syncPet(arriving = true)
        val gameResult = GameLog.consume()
        when {
            action == "play" && !game.isNullOrBlank() -> {
                interrupt(); leaveBed()
                enqueue(Beat.Say("Yes! Finally.", Sfx.HAPPY), Beat.Do { navRequest = "game:$game" })
            }
            gameResult != null -> reactToGame(gameResult)
            sessionFlags.remove("cameraOpened") -> {
                interrupt(); leaveBed()
                enqueue(Beat.Move(null), Beat.Do { rig.lookAt(0f, 0.35f, 3f) }, Beat.Act(AnimState.CURIOUS, 0.6f, Expr.CURIOUS),
                    Beat.Say(Dialogue.pick(Reactions.cameraBack, rng), Sfx.BEEP))
            }
            else -> {
                debugEvent("greet ${greet.kind} digest=${digest != null} fromMessage=$fromMessage homecoming=${homecoming != null}")
                if (homecoming != null) { interrupt(); activity = null; enqueueAll(returnBeats(homecoming)) } else {
                if (fromMessage && (inBed || activity == ActivityType.SLEEP) && greet.kind != GreetKind.AWAY) { interrupt(); leaveBed() } // he messaged you: he gets up
                stage(greet, notNowRecently)
                // Opening the app is visiting someone, not summoning him: if he isn't coming over, the
                // camera doesn't go looking for him either. You look around; then it drifts to him.
                if (greet.kind in setOf(GreetKind.SLEEPING, GreetKind.CONTINUE, GreetKind.BUSY, GreetKind.WORKING, GreetKind.NOTHING, GreetKind.BRIEF))
                    camHoldUntil = clock + 5f + rng.nextFloat() * 4f
                val tellable = greet.kind !in setOf(GreetKind.SLEEPING, GreetKind.FAKE_SLEEP, GreetKind.FIRST_WAKE, GreetKind.BRIEF, GreetKind.AWAY, GreetKind.HIDING)
                digest?.let { d ->
                    if (tellable) {
                        enqueue(Beat.Wait(0.5f), Beat.Do { rig.lookAt(0f, 0.35f, 3f) }, say(d, Sfx.BEEP), Beat.Act(AnimState.CHEERFUL, 0.8f, Expr.HAPPY))
                        repo.mutate { it.awayLog.clear() }
                    }
                }
                }
            }
        }
        // He notices you arrive.
        if (!inBed && !away && hideSpot == null && greet.kind != GreetKind.PEEK_IN) rig.lookAt(0f, 0.35f, 1.4f)
        refreshFestival()
        if (petHome && petAct != PetActivity.NAP && !away) { pet.look(0f, 0.9f); nib(PetFace.HAPPY, Sfx.PET_HAPPY, if (rng.nextBoolean()) PetThought.HEART else null, 2f) }
        maybeFestivalGreeting()
        buildRoom()
    }

    fun onPause() {
        onScreen = false
        voiceConvo = false
        if (listening) { speech.stop(); listening = false; micLevel = 0f }
        pendingNotifs.clear()
        val now = System.currentTimeMillis()
        val cam = camU
        val px = petX
        repo.mutate(notify = false) { s ->
            s.lastSeenByUserAt = now; s.lastSimulatedAt = now
            s.world.objectStates["cam"] = "%.1f".format(java.util.Locale.ROOT, cam)
            s.pet.x = px
        }
        voice.stop()
        stopListening()
        runCatching { sensors?.unregisterListener(shakeListener) }
        repo.saveAsync()
    }

    private fun systemAnimationsOff(): Boolean = runCatching {
        android.provider.Settings.Global.getFloat(getApplication<Application>().contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)

    override fun onCleared() {
        voice.synth.shutdown()
        voice.shutdown()
        speech.stop()
        super.onCleared()
    }

    /** Restores where he is and what he's doing, from the saved state (cold start or after a reset). */
    private fun placeForCurrentActivity(moveCamera: Boolean = true) {
        val (a, startedAt, tripLeft) = repo.read { Triple(it.activity.type, it.activity.startedAt, it.trip?.let { t -> (t.endsAt - System.currentTimeMillis()) / 1000f }) }
        activity = a
        activityArrived = true
        lastActivity = a
        hideSpot = null
        sleepSpot = SleepSpot.BED
        when {
            tripLeft != null -> {
                // out: nobody in the room. He'll come back through the door.
                away = true; inBed = false; bedBlend = 0f; pipoX = SceneGeo.DOOR_X
                activity = ActivityType.GO_OUT
                activityEnd = clock + tripLeft.coerceIn(2f, 6f * 3600f)
            }
            a == ActivityType.SLEEP -> {
                // mostly in bed. Sometimes he nodded off somewhere else.
                val r = ((startedAt / 1000L) % 100L).toInt()
                val hasBox = repo.read { it.world.objectStates["box"] != null }
                sleepSpot = when { r < 8 -> SleepSpot.RUG; r < 13 && hasBox -> SleepSpot.BOX; r < 17 -> SleepSpot.DESK; else -> SleepSpot.BED }
                when (sleepSpot) {
                    SleepSpot.BED -> { inBed = true; bedBlend = 1f; pipoX = SceneGeo.BED_PIVOT }
                    SleepSpot.RUG -> { inBed = false; bedBlend = 0f; pipoX = 100f }
                    SleepSpot.BOX -> { inBed = false; bedBlend = 0f; pipoX = (SceneGeo.BOX_L + SceneGeo.BOX_R) / 2f; hideSpot = HideSpot.BOX }
                    SleepSpot.DESK -> { inBed = false; bedBlend = 0f; pipoX = 136f }
                }
                activityEnd = clock + 25f + rng.nextFloat() * 30f
            }
            else -> {
                inBed = false; bedBlend = 0f
                pipoX = if (a.station == Station.HIDE || a.station == Station.DOOR) stationX(Station.WANDER) else stationX(a.station)
                if (a == ActivityType.HIDE || a == ActivityType.GO_OUT) activity = ActivityType.NOTHING
                activityEnd = clock + 6f + rng.nextFloat() * 10f
            }
        }
        if (moveCamera) geo?.let { if (pendingCam == null) camU = clampCam(pipoX - it.viewU / 2f) }
    }

    /** Asleep, anywhere. */
    private val asleep get() = inBed || (activity == ActivityType.SLEEP && activityArrived && !away)

    /* ================================================================ */
    /*  Frame loop                                                       */
    /* ================================================================ */

    fun frame(dtIn: Float) {
        val dt = dtIn.coerceIn(0f, 0.05f)
        clock += dt
        engineAcc += dt
        if (engineAcc >= 1f) { engineAcc -= 1f; engineTick() }
        stepBeats(dt)
        stepVoiceConvo()
        stepMovement(dt)
        stepAttention(dt)
        resolveRig()
        rig.update(dt)
        when (rig.takeFidgetEvent()) {
            Fidget.YAWN -> {
                if (sounds && clock - lastYawnSound > 25f) { lastYawnSound = clock; voice.synth.sfx(Sfx.YAWN) }
                // yawns are catching, even for robots
                if (petHome && petAct != PetActivity.NAP && abs(petX - pipoX) < 40f && rng.nextFloat() < 0.6f)
                    viewModelScope.launch { delay(900); nib(PetFace.SAD, Sfx.PET_SAD, PetThought.BATTERY, 1.6f) }
            }
            // the hum-sway comes with an actual hum now: a little tune, mouth closed
            Fidget.HUM_SWAY -> hum(0.7f)
            else -> Unit
        }
        stepPet(dt)
        stepDoor(dt)
        stepWeather()
        stepRadio()
        stepReflection(dt)
        stepCamera(dt)
        stepZoom(dt)
        stepNotifs()
        plantRustle *= exp(-dt * 1.8f)
        if (BuildConfig.DEBUG && clock - lastDebugLog > 0.5f) { lastDebugLog = clock; debugPos() }
        if (bubbleHideAt in 0f..clock) { bubble = null; bubbleHideAt = -1f }
        if (userLine != null && clock > userLineUntil) userLine = null
        if (text != null && clock > textUntil) text = null
        frame++
    }

    /** Plain-words version of the room for screen readers. Only true things; updated once a second. */
    private fun describeScene() {
        val a = activity
        val what = when {
            away -> "Pipo is out. There's a note on the door."
            hideSpot != null && a == ActivityType.HIDE -> "Pipo is hiding somewhere in the room. Tap where you think he is."
            inBed -> "Pipo is asleep in bed."
            a == ActivityType.SLEEP -> "Pipo fell asleep on the floor."
            a != null -> "Pipo is ${BehaviorEngine.describe(a)}."
            else -> "Pipo is in his room."
        }
        val nib = if (petHome) when (petAct) { PetActivity.NAP -> " Nib is napping."; PetActivity.STARE -> " Nib is staring at the window."; else -> " Nib is nearby." } else ""
        val d = "$what$nib It's ${WeatherEngine.describe(weather.kind)} outside."
        if (d != sceneDescription) sceneDescription = d
    }

    /** A real hum (see SoundSynth.HUM), never more than every half minute. */
    private fun hum(chance: Float) {
        if (!sounds || voice.speaking || clock - lastHum < 32f || rng.nextFloat() > chance) return
        lastHum = clock
        voice.synth.sfx(Sfx.HUM)
        rig.hum(2.4f)
    }

    private var nextStatic = 60f

    /** If he built a radio and the thread has started: at night, once in a while, it crackles. He looks at it. */
    private fun stepRadio() {
        if (clock < nextStatic) return
        nextStatic = clock + 40f + rng.nextFloat() * 50f
        if (away || asleep || cur != null || dayFactor(room.hour) > 0.3f) return
        val live = repo.read { s -> s.mystery.stage >= 1 && s.projects.any { it.templateId == "radio" && (it.state == ProjectState.DONE || it.state == ProjectState.EVOLVED) } }
        if (!live || rng.nextFloat() > 0.5f) return
        if (sounds) voice.synth.sfx(Sfx.SIZZLE)
        rig.lookAt(((191f - pipoX) / 35f).coerceIn(-1f, 1f), -0.2f, 2.5f)
        if (rng.nextFloat() < 0.35f) enqueue(Beat.Wait(1.2f), Beat.Say(Dialogue.pick(listOf("...the radio did the thing again.", "Three crackles. Always three."), rng), null))
    }

    private fun stepDoor(dt: Float) {
        doorOpen += (doorTarget - doorOpen) * (1f - exp(-dt * 6f))
        fridgeOpen += ((if (clock < fridgeUntil) 1f else 0f) - fridgeOpen) * (1f - exp(-dt * 7f))
    }

    /** Lightning, then (a moment later, like real thunder) the rumble — and everyone reacts in character. */
    private fun stepWeather() {
        if (weather.kind != Weather.STORM) { thunderAt = -1f; return }
        val ph = clock % Critters.STORM_PERIOD
        if (ph < lastStormPhase) thunderAt = clock + 0.7f + rng.nextFloat() * 0.8f
        lastStormPhase = ph
        if (thunderAt > 0f && clock >= thunderAt) {
            thunderAt = -1f
            if (sounds) voice.synth.sfx(Sfx.THUNDER)
            if (petHome && rng.nextFloat() < 0.95f - repo.read { NibLife.bravery(it) }) {
                petAct = PetActivity.HIDE; petTarget = 20f; petRunning = true; petDecideAt = 12f
                if (!away && !asleep && cur == null && beats.isEmpty() && (hasArmor || repo.read { it.profile.traits.confidence > 0.45f }) && rng.nextFloat() < 0.5f) {
                    lastThunderReact = clock
                    if (armorMark > 0 && rig.suit == 0) { rig.suit = armorMark; suitUntil = clock + 300f }
                    enqueue(Beat.Move(26f), Beat.Act(AnimState.PETTING, 2f, Expr.WORRIED), Beat.Say(Dialogue.pick(listOf("It's okay, Nib. It's just the sky being loud.", "I've got you, Nib. I'm very brave. Mostly."), rng), null))
                    repo.mutate(notify = false) { PetEngine.together(it, "storm", "I sat with Nib under the bed during a storm", System.currentTimeMillis(), 0.55f) }
                }
            }
            if (!away && !asleep && hideSpot == null && cur == null && beats.isEmpty() && clock - lastThunderReact > 50f && !firstWakePending) {
                lastThunderReact = clock
                val brave = repo.read { it.profile.traits.confidence > 0.6f }
                if (brave) enqueue(Beat.Act(AnimState.SURPRISED, 0.5f, Expr.SURPRISED), Beat.Say(Dialogue.pick(listOf("WHOA. Do it again!", "That was a big one!"), rng), Sfx.SURPRISED))
                else {
                    repo.mutate(notify = false) { s -> MoodEngine.setTransient(s, Mood.WORRIED, System.currentTimeMillis(), 20_000) }
                    enqueue(Beat.Act(AnimState.SURPRISED, 0.5f, Expr.SURPRISED), Beat.Act(AnimState.NERVOUS, 1.4f, Expr.WORRIED),
                        Beat.Say(Dialogue.pick(listOf("I'm fine. I'm fine.", "That was just the sky. Right?", "...can I sit near you?"), rng), Sfx.SURPRISED))
                }
            }
        }
    }

    /** At night his reflection shows in the window glass. Usually it behaves. */
    private fun stepReflection(dt: Float) {
        if (!reflectionVisible()) return
        val uneasy = room.reflectionUneasy && (clock % 97f) in 40f..43.5f
        reflection.anim = if (uneasy) AnimState.WAVE else rig.anim
        reflection.expr = if (uneasy) Expr.HAPPY else rig.expr
        reflection.mood = rig.mood
        reflection.energy = rig.energy
        reflection.glow = rig.glow
        reflection.lookAt(if (uneasy) 0f else rig.lookX, if (uneasy) 0.35f else rig.lookY, 0.2f)
        reflection.update(dt)
    }

    /** This frame, as plain values for any renderer (see ui/render/VisualFrame.kt). Read-only. */
    fun visualFrame(): com.pipo.robot.ui.render.VisualFrame = com.pipo.robot.ui.render.VisualFrame(
        pipo = if (away) null else com.pipo.robot.ui.render.PipoVisual(
            x = if (bedBlend > 0.5f) SceneGeo.BED_PIVOT else pipoX, lift = lift, yaw = rig.yaw, headYaw = rig.headYaw,
            anim = rig.anim, expr = rig.expr, lookX = rig.lookX, lookY = rig.lookY, blink = rig.blink,
            talking = rig.talking, talkLevel = rig.talkLevel, holdItem = rig.holdItem, emote = rig.emote, glow = rig.glow,
            inBed = inBed, hideSpot = hideSpot,
        ),
        nib = if (petHome) com.pipo.robot.ui.render.NibVisual(petX, pet.facing, pet.anim, pet.lookX, pet.lookY, pet.blink) else null,
        room = roomNow(), cameraU = camU, cameraZoom = camZoom,
    )

    fun reflectionVisible(): Boolean = !away && !inBed && hideSpot == null && dayFactor(room.hour) < 0.35f && abs(pipoX - 104f) < 20f

    private fun engineTick() {
        val now = System.currentTimeMillis()
        val env = currentEnv()
        val a = activity
        val m = repo.mutate(notify = false, save = false) { s ->
            MoodEngine.tick(s, 1000, env, sleeping = asleep,
                charging = a == ActivityType.CHARGE && activityArrived,
                playing = a == ActivityType.PLAY_ARCADE || a == ActivityType.PLAY_TOY || a == ActivityType.DANCE || a == ActivityType.GO_OUT,
                timeScale = 5f)
            if (s.pet.adopted) PetEngine.tick(s, 1000, napping = petAct == PetActivity.NAP, playing = petAct == PetActivity.PLAY_BALL || petAct == PetActivity.ZOOMIES, timeScale = 5f)
            val mm = MoodEngine.derive(s, now, env.hour)
            s.lastSeenByUserAt = now
            s.lastSimulatedAt = now
            if (a != null) s.activity.type = a
            energy = s.mood.energy
            sociability = s.profile.traits.sociability
            relationship = s.profile.relationship
            mm
        }
        mood = m
        maybeNibPesters()
        if (petHome && !away && !asleep && cur == null && beats.isEmpty() && (clock % 30f) < 1f) repo.mutate(notify = false) { NibLife.checkStage(it, now) }?.let { st ->
            enqueue(Beat.Do { rig.lookAt(((petX - pipoX) / 25f).coerceIn(-1f, 1f), 0.6f, 2f); nib(PetFace.LOVE, Sfx.PET_HAPPY, PetThought.HEART, 4f) },
                Beat.Act(AnimState.CELEBRATE, 1.2f, Expr.LOVE),
                Beat.Say(when (st.level) { 1 -> "Nib and me are friends now. Official friends."; 2 -> "Nib is my best friend. Don't tell the plant."; 3 -> "Nib and me are inseparable. It's a word. It means stuck together."; else -> "Nib is family. Nib beeped three times. That means 'obviously'." }, Sfx.HAPPY))
        }
        if (clock - weatherCheckedAt > 60f) { weatherCheckedAt = clock; weather = repo.read { WeatherEngine.at(it.seed, now) } }
        if ((env.hour >= 23 || env.hour < 4) && !inBed && !away && cur == null && beats.isEmpty() && clock > 25f && "latenight" !in sessionFlags && !firstWakePending) {
            sessionFlags += "latenight"
            enqueue(Beat.Act(AnimState.SLEEPY, 1.5f, Expr.SLEEPY), Beat.Say(Dialogue.pick(Reactions.lateNight, rng), Sfx.SLEEPY))
        }
        saveAcc += 1f
        if (saveAcc >= 20f) { saveAcc = 0f; repo.saveAsync() }
        buildRoom()
        describeScene()
    }

    private fun buildRoom() {
        val c = Calendar.getInstance()
        val hour = c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) / 60f
        room = repo.read { s ->
            val built = s.projects.filter { it.state == ProjectState.DONE || it.state == ProjectState.EVOLVED }
            val o = s.world.objectStates
            RoomState(
                hour = hour,
                pranks = Pranks.active(s).map { it.key }.toSet(),
                drawings = com.pipo.robot.engine.DrawingLife.hung(s).reversed().map { WallDrawing(it.subject, it.seed) },
                // the shelf: things he built (a physical biography), then things he found; not what Nib stole or knocked off
                shelf = (built.dropLast(1).takeLast(3).mapNotNull { Catalog.project(it.templateId)?.shape } +
                    s.world.items.filter { it.usedInProjectId == 0L && it.id != s.pet.stolenItemId && it.id.toString() != o["floor"] && Catalog.item(it.catalogId)?.shopOnly != true }
                        .takeLast(5).mapNotNull { Catalog.item(it.catalogId)?.shape }).take(8),
                benchThing = built.lastOrNull()?.let { Catalog.project(it.templateId)?.shape },
                projectActive = s.activeProject()?.state == ProjectState.BUILDING,
                charging = phoneState.charging,
                battery = phoneState.battery,
                arcadeActive = activity == ActivityType.PLAY_ARCADE && activityArrived,
                consoleActive = activity == ActivityType.PLAY_CONSOLE && activityArrived,
                torch = phoneActions.torchOn,
                computerActive = activity == ActivityType.WORK_COMPUTER && activityArrived,
                benchActive = (activity == ActivityType.BUILD || activity == ActivityType.EXPERIMENT) && activityArrived,
                music = phoneState.music,
                weather = weather.kind,
                weatherAmt = weather.intensity,
                photos = s.photos.takeLast(5).reversed().map { p ->
                    PinnedPhoto(p.subject, p.seed, Places.byId(p.placeId)?.color ?: 0xFF8C9B7A, p.hour >= 20 || p.hour < 6,
                        runCatching { Weather.valueOf(p.weather) }.getOrDefault(Weather.CLEAR), p.anomaly && p.anomalySeen, p.ref)
                },
                pantry = s.pantry.takeLast(5).mapNotNull { Foods.byId(it)?.shape },
                built = built.map { Catalog.project(it.templateId)?.shape ?: ItemShape.GEAR },
                box = o["box"] != null,
                bag = o["bag"] != null,
                plate = o["plate"] != null,
                smoke = o["smoke"] != null,
                cooking = cooking,
                floorItem = o["floor"]?.toLongOrNull()?.let { id -> s.world.items.firstOrNull { it.id == id } }?.let { Catalog.item(it.catalogId)?.shape },
                ballByDoor = o["ball_door"] != null,
                pennants = s.places.entries.sortedBy { it.value.firstVisit }.mapNotNull { (id, _) -> Places.byId(id)?.takeIf { it.kind != com.pipo.robot.data.PlaceKind.HIDDEN }?.color },
                calDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH),
                festival = festivalNow,
                builderLevel = com.pipo.robot.engine.Inventor.level(s),
                helper = com.pipo.robot.engine.Inventor.has(s, "helper"),
                season = com.pipo.robot.engine.Seasons.at(System.currentTimeMillis(), WeatherEngine.liveLat),
                coldWinter = (WeatherEngine.live?.tempC ?: 20f) < 8f || kotlin.math.abs(WeatherEngine.liveLat ?: 15.0) > 35.0,
                calMonth = Calendar.getInstance().getDisplayName(Calendar.MONTH, Calendar.SHORT, java.util.Locale.getDefault()).orEmpty().uppercase(),
                petInBed = o["pet_bed"] != null,
                stolenGlint = s.pet.stolenItemId != 0L,
                umbrella = s.world.items.any { it.catalogId == "umbrella" },
                doorNote = away,
                feeder = built.any { it.templateId == "feeder" },
                telescope = built.any { it.templateId == "telescope" },
                book = o["book"] != null,
                coins = s.coins,
                reflectionUneasy = Mystery.reflectionUneasy(s),
                token = s.world.items.any { it.catalogId == "brass_token" },
                mirrorScrew = s.world.items.any { it.catalogId == "mirror_screw" },
                benchParts = s.activeProject()?.let { p -> s.world.items.filter { it.usedInProjectId == p.id }.mapNotNull { Catalog.item(it.catalogId)?.shape } } ?: emptyList(),
                scraps = o["scraps"]?.let { Catalog.project(it)?.shape },
                tokenAwake = s.mystery.stage >= 2,
            )
        }
    }

    private fun currentEnv() = Env(
        hour = hourOf(System.currentTimeMillis()),
        charging = phoneState.charging, battery = phoneState.battery,
        music = phoneState.music, headphones = phoneState.headphones, userPresent = true,
        weather = weather,
    )

    private fun refreshMood() { mood = repo.read { energy = it.mood.energy; it.mood.current } }

    /* ---------------- beats ---------------- */

    private fun enqueue(vararg b: Beat) { beats.addAll(b) }
    private fun enqueueAll(b: List<Beat>) { beats.addAll(b) }

    private fun interrupt() {
        readHoldUntil = 0f
        if (activity == ActivityType.HIDE && hideSpot != null) exitHide()
        if (!away) {
            // stopped on the way out: he never left, so he isn't "out" anywhere (and nobody leaves the door open)
            if (activity == ActivityType.GO_OUT) { repo.mutate { Trips.cancel(it) }; activity = null }
            doorTarget = 0f
        }
        beats.clear()
        cur = null
        targetX = null
        running = false
        sneaking = false
        fakeSleeping = false
        if (!activityArrived) activity = null
        voice.stop()
        rig.talking = false
    }

    private fun stepBeats(dt: Float) {
        if (dragging) return
        var b = cur
        if (b == null) {
            // Cold start: the phone's TTS can take a few seconds to wake. Don't start a spoken
            // line (bubble + mouth) until his voice can say it, so nothing gets mouthed silently.
            if (beats.firstOrNull() is Beat.Say && !voice.ready && clock < 8f) return
            b = beats.removeFirstOrNull()
            if (b == null) { idle(); return }
            cur = b; curT = 0f
            startBeat(b)
        }
        curT += dt
        if (beatDone(b)) { endBeat(b); if (cur === b) cur = null }
    }

    private fun startBeat(b: Beat) {
        when (b) {
            is Beat.Move -> {
                val x = b.x ?: frontX()
                targetX = x.coerceIn(4f, SceneGeo.WORLD_W - 6f)
                running = b.run
                sneaking = b.sneak
                val dist = abs(targetX!! - pipoX)
                if (dist > 12f && !dragging && !inBed) {
                    rig.lookAt(sign(targetX!! - pipoX) * 0.9f, 0f, 0.5f)
                    if (!sneaking) rig.anticipate()
                    moveDelay = if (running) 0.12f else 0.22f
                } else moveDelay = 0f
                if (b.run && sounds && rng.nextFloat() < 0.3f) voice.synth.sfx(Sfx.SERVO)
            }
            is Beat.Say -> {
                sayDone = false
                val id = ++bubbleSeq
                sayBubbleId = id
                bubble = Bubble(id, b.text, b.choices)
                bubbleHideAt = -1f
                if (b.choices.isEmpty() && b.text.length > 3) lastSpoken = b.text
                sayMin = 1.3f + b.text.length * 0.045f
                debugEvent("say \"${b.text}\"")
                if (sounds) b.sfx?.let { voice.synth.sfx(it) }
                rig.speak(b.text, charsPerSecond(mood, SpeechStyles.style(b.text), voice.mode))
                voice.speak(b.text, mood) { if (sayBubbleId == id) sayDone = true }
            }
            is Beat.Emote -> rig.showEmote(b.kind)
            is Beat.Do -> b.fn()
            is Beat.Act, is Beat.Wait -> Unit
        }
    }

    private fun beatDone(b: Beat): Boolean = when (b) {
        is Beat.Move -> targetX == null
        is Beat.Act -> curT >= b.secs
        is Beat.Wait -> curT >= b.secs
        is Beat.Say -> if (b.choices.isNotEmpty()) bubble?.id != sayBubbleId || curT > 20f
        else (sayDone && curT >= sayMin) || curT > sayMin + 6f
        else -> true
    }

    private fun endBeat(b: Beat) {
        if (b is Beat.Say) {
            rig.talking = false
            if (bubble?.id == sayBubbleId) {
                if (b.choices.isNotEmpty()) bubble = null else bubbleHideAt = clock + 1.4f
            }
        }
    }

    fun pickChoice(c: Choice) {
        bubble = null
        voice.stop()
        registerInteraction()
        c.onPick()
    }

    /** Nothing queued: continue or choose an activity. This is where autonomy lives. */
    private fun idle() {
        if (aiThinking || !booted) return
        // out: the room carries on without him until he walks back in
        if (away) { if (clock >= activityEnd) completeActivity(ActivityType.GO_OUT); return }
        if (fakeSleeping) {
            if (clock > fakeSleepUntil) {
                fakeSleeping = false
                enqueue(Beat.Do { inBed = false }, Beat.Act(AnimState.STRETCH, 0.8f, Expr.WINK),
                    Beat.Say(Dialogue.pick(Dialogue.fakeSleepGiveUp, rng), Sfx.GRUMBLE), Beat.Act(AnimState.ARMS_CROSSED, 1.2f, Expr.MISCHIEF))
            }
            return
        }
        if (maybeAskSomething()) return
        val a = activity
        if (a == null) { chooseNext(); return }
        if (!activityArrived) { activity = null; return }
        if (clock >= activityEnd) { completeActivity(a); return }
        if (clock >= microAt) { micro(a); microAt = clock + 5f + rng.nextFloat() * 8f }
    }

    private fun chooseNext() {
        if (firstWakePending) return
        val now = System.currentTimeMillis()
        val env = currentEnv()
        val type = repo.mutate(notify = false) { s ->
            val t = BehaviorEngine.choose(s, env, now, rng, lastActivity)
            BehaviorEngine.start(s, t, now, rng, env, inApp = true)
        }
        begin(type)
    }

    /** You asked him to go out (somewhere): he goes, if he still can. */
    private fun forceTrip(wish: String) {
        val now = System.currentTimeMillis()
        val env = currentEnv()
        val actual = repo.mutate(notify = false) { s ->
            val idea = com.pipo.robot.engine.Trips.forUser(s, env, now, wish)
            BehaviorEngine.start(s, ActivityType.GO_OUT, now, rng, env, inApp = true, idea = idea)
        }
        activity = null
        begin(actual)
    }

    private fun forceActivity(type: ActivityType) {
        val now = System.currentTimeMillis()
        val env = currentEnv()
        val actual = repo.mutate(notify = false) { s -> BehaviorEngine.start(s, type, now, rng, env, inApp = true) }
        activity = null
        begin(actual)
    }

    private fun begin(type: ActivityType) {
        val dur = repo.read { it.activity.durationMs } / 1000f
        debugEvent("begin $type absorbed=${repo.read { it.activity.absorbed }} dur=${dur.toInt()}s")
        activity = type
        activityArrived = false
        lastActivity = type
        sleepSpot = SleepSpot.BED
        // whatever he was in the middle of is over: out of the box, pot off the stove, hands empty
        if (hideSpot != null) exitHide()
        cooking = false; kickups = false; carrying = false; rig.holdItem = null; rig.phoneApp = null
        nibNotices(type)
        readHoldUntil = 0f
        if (type == ActivityType.SLEEP) {
            if (inBed) { arrive(dur); return }
            // Very tired, very lazy and far from bed: he just... falls asleep where he is. Or in the box.
            val (lazy, hasBox) = repo.read { it.profile.traits.laziness to (it.world.objectStates["box"] != null) }
            val far = abs(pipoX - SceneGeo.BED_PIVOT) > 60f
            when {
                far && energy < 0.25f && lazy > 0.5f && rng.nextFloat() < 0.55f -> {
                    sleepSpot = SleepSpot.RUG
                    enqueue(Beat.Act(AnimState.YAWN, 2f, Expr.SLEEPY), Beat.Do { if (sounds) voice.synth.sfx(Sfx.YAWN) }, Beat.Act(AnimState.SITTING, 1.2f, Expr.SLEEPY),
                        Beat.Do { arrive(dur) })
                }
                hasBox && rng.nextFloat() < 0.12f -> {
                    sleepSpot = SleepSpot.BOX
                    enqueue(Beat.Move((SceneGeo.BOX_L + SceneGeo.BOX_R) / 2f), Beat.Act(AnimState.HOP, 0.5f, Expr.HAPPY), Beat.Do { hideSpot = HideSpot.BOX; arrive(dur) })
                }
                else -> enqueue(Beat.Move(SceneGeo.BED_PIVOT), Beat.Do { inBed = true; arrive(dur) })
            }
            return
        }
        leaveBed()
        when (type) {
            ActivityType.GO_OUT -> {
                // he tells you where he's going, walks to the door, and leaves. The room stays.
                val trip = repo.read { it.trip } ?: run { activity = null; return }
                val mischievous = mood == Mood.MISCHIEVOUS
                if (trip.withPet && petHome) { petAct = PetActivity.FOLLOW; petTarget = SceneGeo.DOOR_X - 6f; petDecideAt = 30f }
                enqueue(Beat.Do { rig.lookAt(0f, 0.35f, 2f) }, say(Trips.leavingLine(trip, rng), Sfx.BEEP),
                    Beat.Move(SceneGeo.DOOR_X - 2f), Beat.Do { rig.lookAt(0f, 0.35f, 1f) },
                    Beat.Act(if (mischievous) AnimState.MISCHIEVOUS else AnimState.WAVE, 0.9f, if (mischievous) Expr.MISCHIEF else Expr.HAPPY),
                    Beat.Do { doorTarget = 1f; if (sounds) voice.synth.sfx(Sfx.DOOR) }, Beat.Wait(0.45f), Beat.Move(SceneGeo.DOOR_X + 3f),
                    Beat.Do { leave((trip.endsAt - System.currentTimeMillis()) / 1000f) })
            }
            ActivityType.HIDE -> {
                val spot = pickHideSpot()
                enqueue(Beat.Do { rig.lookAt(0f, 0.35f, 1f) }, Beat.Act(AnimState.MISCHIEVOUS, 0.6f, Expr.MISCHIEF),
                    Beat.Move(hideX(spot), sneak = true), Beat.Do { hideEmbarrassed = repo.read { it.mood.transient == Mood.EMBARRASSED }; enterHide(spot); arrive(dur) })
            }
            ActivityType.PLAY_PET -> {
                if (!petHome) { enqueue(Beat.Do { arrive(dur) }); return }
                petAct = PetActivity.SIT_WITH_PIPO; petDecideAt = 25f
                val learning = repo.read { NibLife.learning(it) }
                enqueue(Beat.Move(petX + (if (petX > pipoX) -9f else 9f)), Beat.Do { rig.lookAt(((petX - pipoX) / 25f).coerceIn(-1f, 1f), 0.6f, 3f); nib(PetFace.CURIOUS, Sfx.PET_CURIOUS, PetThought.QUESTION) })
                if (learning != null) enqueue(
                    Beat.Act(AnimState.FINGER_UP, 1f, Expr.FOCUSED),
                    Beat.Say(Dialogue.pick(listOf("Okay Nib. ${learning.title.replaceFirstChar { it.uppercase() }}. Watch me.", "Practice time. Today: ${learning.title}. Focus, Nib.", "Nib. ${learning.title.replaceFirstChar { it.uppercase() }}. Like this."), rng), Sfx.BEEP),
                    Beat.Act(AnimState.HOP, 0.6f, Expr.HAPPY),
                    Beat.Do { pet.anim = PetAnim.HOP; nib(if (rng.nextBoolean()) PetFace.HAPPY else PetFace.CURIOUS, Sfx.PET_HAPPY) },
                    Beat.Wait(1.2f),
                    Beat.Say(Dialogue.pick(listOf("Close! Ish.", "That was... something. Again!", "Good try, Nib. You did a different trick. Also good."), rng), Sfx.GIGGLE))
                enqueue(Beat.Do { arrive(dur) })
            }
            ActivityType.CLEAN -> {
                val (x, suspect) = repo.read { cleanSpot(it) to (it.pet.stolenItemId != 0L && PetEngine.knownThief(it)) }
                if (suspect && petHome) enqueue(
                    Beat.Do { rig.lookAt(((petX - pipoX) / 35f).coerceIn(-1f, 1f), 0.4f, 2f); pet.look(if (pipoX > petX) -1f else 1f, 0.2f) }, // Nib looks away
                    Beat.Act(AnimState.IDLE, 1.6f, Expr.SUSPICIOUS))
                enqueue(Beat.Move(x), Beat.Do { arrive(dur) })
            }
            ActivityType.EXPLORE -> enqueue(
                Beat.Move(wanderX()), Beat.Act(AnimState.LOOK_AROUND, 1.8f, Expr.CURIOUS),
                Beat.Move(wanderX()), Beat.Act(AnimState.PEEK, 1.2f, Expr.CURIOUS),
                Beat.Move(wanderX()), Beat.Do { arrive(dur * 0.4f) })
            ActivityType.REARRANGE -> enqueue(
                Beat.Move(wanderX(), sneak = true), Beat.Do { rig.lookAt(0f, 0.35f, 1.4f) }, Beat.Act(AnimState.MISCHIEVOUS, 1.2f, Expr.SUSPICIOUS),
                Beat.Move(wanderX(), sneak = true), Beat.Act(AnimState.PEEK, 0.8f, Expr.MISCHIEF), Beat.Do { arrive(dur * 0.5f) })
            ActivityType.PLAY_TOY -> {
                // The ball is a real thing in the room: he goes to it. If it's by the door (from the
                // field) or he hid it (a prank), he fetches it from there first.
                val (byDoor, hidden) = repo.read { s -> (s.world.objectStates["ball_door"] != null) to
                    listOf("ball_on_bed", "ball_behind_plant").firstOrNull { s.world.objectStates["prank:$it"] == "1" } }
                val from = when {
                    byDoor -> SceneGeo.DOOR_L - 9f
                    hidden == "ball_on_bed" -> 26f
                    hidden == "ball_behind_plant" -> 66f
                    else -> null
                }
                if (from != null) enqueue(
                    Beat.Move(from - 3f), Beat.Act(AnimState.CURIOUS, 0.6f, Expr.HAPPY),
                    Beat.Do {
                        ballU = from; ballTarget = from
                        repo.mutate { s -> s.world.objectStates.remove("ball_door"); hidden?.let { s.world.objectStates.remove("prank:$it") } }
                        buildRoom()
                    })
                // play in the open part of the floor, kicking it there first
                val pitch = (if (from != null) 200f else ballU.coerceIn(150f, 238f))
                if (from != null) enqueue(Beat.Do { ballTarget = pitch }, Beat.Move(pitch - 3f, run = true))
                else enqueue(Beat.Move(ballU - 3f))
                enqueue(Beat.Do { arrive(dur) })
            }
            ActivityType.REST, ActivityType.NOTHING -> {
                // The closer you two are, the more he likes to hang out near the front, near you.
                if (relationship > 0.45f && rng.nextFloat() < 0.4f) enqueue(Beat.Move(frontX()))
                enqueue(Beat.Do { arrive(dur) })
            }
            else -> {
                val st = type.station
                if (st != Station.STAY) enqueue(Beat.Move(if (st == Station.FRONT) null else stationX(st)))
                enqueue(Beat.Do { arrive(dur) })
            }
        }
    }

    /** Through the door and gone. [secs] = how long until he's back. */
    private fun leave(secs: Float) {
        away = true
        activity = ActivityType.GO_OUT
        activityArrived = true
        activityEnd = clock + secs.coerceIn(5f, 6f * 3600f)
        rig.holdItem = null
        doorTarget = 0f
        repo.read { it.trip }?.let { t ->
            if (t.withPet) petHome = false
            else if (petHome && petAct != PetActivity.NAP) { petAct = PetActivity.SIT_WITH_PIPO; petTarget = SceneGeo.DOOR_X - 12f; petRunning = false; petDecideAt = 45f; nib(PetFace.SAD, Sfx.PET_SAD, PetThought.PIPO, 4f) }
        }
        debugEvent("left for ${repo.read { it.trip?.placeId }} back in ${secs.toInt()}s")
    }

    private fun pickHideSpot(): HideSpot {
        val hasBox = repo.read { it.world.objectStates["box"] != null }
        val r = rng.nextFloat()
        return when {
            hasBox && r < 0.4f -> HideSpot.BOX
            r < 0.75f -> HideSpot.ARCADE
            else -> HideSpot.BLANKET
        }
    }

    private fun hideX(spot: HideSpot) = when (spot) {
        HideSpot.ARCADE -> SceneGeo.ARCADE_HIDE_X
        HideSpot.BOX -> (SceneGeo.BOX_L + SceneGeo.BOX_R) / 2f
        HideSpot.BLANKET -> SceneGeo.BED_PIVOT
    }

    private fun enterHide(spot: HideSpot) {
        hideSpot = spot
        nextPeek = clock + 6f + rng.nextFloat() * 6f
        if (spot == HideSpot.BLANKET) { inBed = true; bedBlend = 1f; pipoX = SceneGeo.BED_PIVOT }
        rig.lookAt(0f, 0.35f, 0.5f)
    }

    private fun exitHide() {
        val spot = hideSpot ?: return
        hideSpot = null
        if (spot == HideSpot.BLANKET) inBed = false
        if (spot == HideSpot.ARCADE) pipoX = SceneGeo.ARCADE_HIDE_X - 12f
    }

    /** Where the mess is (he goes there to tidy it). */
    private fun cleanSpot(s: com.pipo.robot.data.PipoState): Float {
        val o = s.world.objectStates
        return when {
            s.pet.stolenItemId != 0L -> 24f
            o["plate"] != null || o["smoke"] != null -> SceneGeo.station(Station.KITCHEN) - 2f
            o["bag"] != null || o["ball_door"] != null -> SceneGeo.DOOR_L - 6f
            o["floor"] != null -> 186f
            o["pet_bed"] != null -> 40f
            else -> wanderX()
        }
    }

    private fun arrive(durSecs: Float) {
        activityArrived = true
        activityEnd = clock + durSecs.coerceIn(3f, 240f)
        microAt = clock + 3f + rng.nextFloat() * 4f
        absorbed = repo.read { it.activity.absorbed }
        restPose = null
        val a = activity
        if (a == ActivityType.REST || a == ActivityType.NOTHING) {
            // Doing nothing is a real activity. It comes in flavours.
            val lazy = repo.read { it.profile.traits.laziness }
            val r = rng.nextFloat() * (2.1f + lazy)
            restPose = when {
                r < 1f -> AnimState.SITTING
                r < 1.6f -> AnimState.IDLE
                else -> AnimState.LIE_DOWN
            }
            if (restPose == AnimState.LIE_DOWN && rng.nextFloat() < 0.3f) enqueue(Beat.Say(Dialogue.pick(Dialogue.lieDownLines, rng), Sfx.SIGH))
        }
        if (absorbed) rig.showEmote(EmoteKind.SPARKLE)
        if (a == ActivityType.SEEK_USER) rig.lookAt(0f, 0.35f, 3f)
        when (a) {
            ActivityType.EAT -> rig.holdItem = repo.read { s ->
                fridgeUntil = clock + 2f
                val e = FoodLife.edible(s)
                (e.firstOrNull { it.id == s.craving } ?: e.firstOrNull())?.shape ?: ItemShape.BOWL
            }
            ActivityType.COOK -> {
                cooking = true; fridgeUntil = clock + 2.2f; if (sounds) voice.synth.sfx(Sfx.SIZZLE)
                // Nib smells it and comes to "help"
                if (petHome && petAct != PetActivity.HIDE && rng.nextFloat() < 0.75f) {
                    // Nib smells it: wakes up if it has to, comes over, sits by the stove and waits
                    nibWatching = ActivityType.COOK
                    nib(PetFace.CURIOUS, Sfx.PET_CURIOUS, PetThought.FOOD, 3f)
                    petAct = PetActivity.SIT_WITH_PIPO; petTarget = SceneGeo.station(Station.KITCHEN) + 8f; petRunning = true; petDecideAt = 60f
                    enqueue(Beat.Wait(2f), Beat.Do { rig.lookAt(0.8f, 0.6f, 1.5f) }, Beat.Say(Dialogue.pick(listOf("Nib. No. It's hot.", "Nib, you don't even eat.", "Nib is supervising. Badly."), rng), Sfx.BOOP))
                    repo.mutate(notify = false) { PetEngine.together(it, "cooking", "Nib always comes to the kitchen when I cook", System.currentTimeMillis(), 0.35f) }
                }
            }
            ActivityType.DRAW -> rig.holdItem = ItemShape.SKETCHBOOK
            ActivityType.SCROLL_PHONE -> rig.phoneApp = repo.read { runCatching { com.pipo.robot.engine.PhoneApp.valueOf(it.activity.result) }.getOrNull() }
            ActivityType.PLAY_TOY -> {
                if (!sportChosen) indoorSport = repo.read { com.pipo.robot.engine.Sports.indoorPick(it, rng) }
                sportChosen = false
                rig.holdItem = when (indoorSport) { com.pipo.robot.engine.Sport.CRICKET -> ItemShape.BAT; com.pipo.robot.engine.Sport.TABLE_TENNIS -> ItemShape.PADDLE; com.pipo.robot.engine.Sport.BADMINTON -> ItemShape.RACKET; else -> null }
                if (indoorSport == com.pipo.robot.engine.Sport.CRICKET && petHome) {
                    // Nib bowls: it stands a little way off and nudges the ball back every time
                    petAct = PetActivity.PLAY_BALL; petTarget = pipoX + 16f; petRunning = true; petDecideAt = 40f
                    nib(PetFace.HAPPY, Sfx.PET_HAPPY, PetThought.BALL)
                }
                kickups = true; ballTarget = pipoX + 2.5f
                // Nib wants in on it (sometimes)
                if (petHome && petAct != PetActivity.HIDE && (petAct != PetActivity.NAP || rng.nextFloat() < 0.3f) && rng.nextFloat() < 0.7f) {
                    petAct = PetActivity.PLAY_BALL; petDecideAt = 25f
                    repo.mutate(notify = false) { PetEngine.together(it, "football", "Nib and I play football. Nib uses its face", System.currentTimeMillis(), 0.4f) }
                }
            }
            else -> Unit
        }
    }

    private fun completeActivity(a: ActivityType) {
        val now = System.currentTimeMillis()
        val env = currentEnv()
        val outs = repo.mutate { s ->
            val o = BehaviorEngine.complete(s, a, env, now, rng, offline = false)
            s.events.filter { now - it.createdAt < 5_000 }.forEach { it.shownInApp = true }
            MoodEngine.derive(s, now, env.hour)
            o
        }
        activity = null
        // whatever he says about the book, he says with it still open in his hands
        if (a == ActivityType.READ) readHoldUntil = clock + 2.5f + (outs.filterIsInstance<Outcome.Say>().firstOrNull()?.text?.length ?: 0) * 0.07f
        if (restPose == AnimState.LIE_DOWN || (a == ActivityType.SLEEP && sleepSpot == SleepSpot.RUG)) enqueue(Beat.Act(AnimState.GET_UP, 1.1f, Expr.CONTENT))
        restPose = null
        cooking = false; kickups = false; ballLift = 0f
        if (a == ActivityType.EAT || a == ActivityType.DRAW) rig.holdItem = null
        if (a == ActivityType.HIDE || (a == ActivityType.SLEEP && hideSpot == HideSpot.BOX)) { exitHide(); enqueue(Beat.Act(AnimState.PEEK, 0.8f, Expr.MISCHIEF)) }
        sleepSpot = SleepSpot.BED
        if (absorbed && rng.nextFloat() < 0.4f) enqueue(Beat.Do { rig.lookAt(0f, 0.35f, 2f) }, Beat.Say(Dialogue.pick(Dialogue.absorbedDone, rng), Sfx.BEEP))
        else festivalNow?.let { f -> if (outs.none { it is Outcome.Say } && rng.nextFloat() < 0.12f) enqueue(Beat.Say(com.pipo.robot.engine.Festivals.idleLine(f, rng), Sfx.HAPPY)) }
        absorbed = false
        refreshMood()
        if (a == ActivityType.SLEEP && outs.none { it is Outcome.Say }) {
            // natural wake-up
            enqueue(Beat.Do { inBed = false }, Beat.Act(AnimState.STRETCH, 1.3f, Expr.SLEEPY))
            if (rng.nextFloat() < 0.4f) enqueue(Beat.Say(Dialogue.pick(Dialogue.morning, rng), Sfx.SLEEPY))
        }
        // a completed trip is staged as a whole (he walks in, puts things away, shows you); its parts aren't staged twice
        if (a == ActivityType.PLAY_TOY && indoorSport != com.pipo.robot.engine.Sport.FOOTBALL) {
            val sp = indoorSport
            val line = repo.mutate { s -> com.pipo.robot.engine.Sports.practice(s, sp, rng, System.currentTimeMillis()) }
            rig.holdItem = null
            for (o in outs.filter { it !is Outcome.Kickups }) enqueueAll(outcomeBeats(o))
            if (line.isNotBlank()) enqueue(Beat.Act(AnimState.HAPPY, 0.8f, Expr.HAPPY), Beat.Say(line, Sfx.HAPPY))
            if (sp == com.pipo.robot.engine.Sport.CRICKET && petHome) enqueue(Beat.Do { nib(PetFace.SMUG, Sfx.PET_SMUG, PetThought.BALL) })
            indoorSport = com.pipo.robot.engine.Sport.FOOTBALL
        } else for (o in outs) enqueueAll(outcomeBeats(o))
        repo.mutate(notify = false) { s -> s.world.objectStates.remove("levelup")?.toIntOrNull() }?.let { lv ->
            val r = com.pipo.robot.engine.Inventor.ranks.first { it.level == lv }
            enqueue(Beat.Wait(0.5f), Beat.Do { rig.lookAt(0f, 0.35f, 3f) }, Beat.Act(AnimState.CELEBRATE, 1.4f, Expr.EXCITED), Beat.Emote(EmoteKind.SPARKLE),
                Beat.Say("LEVEL UP! I'm a level $lv ${r.title.lowercase()} now. It's official. I made a badge.", Sfx.WIN),
                Beat.Do { nib(PetFace.HAPPY, Sfx.PET_HAPPY, PetThought.HEART) })
            refreshFestival()
        }
        if ((a == ActivityType.EXPERIMENT || a == ActivityType.THINK) && rng.nextFloat() < 0.18f && repo.read { com.pipo.robot.engine.Inventor.has(it, "scout_drone") && it.places.isNotEmpty() }) {
            val now = System.currentTimeMillis()
            val photo = repo.mutate { s ->
                val place = s.places.keys.filter { Places.byId(it)?.kind != com.pipo.robot.data.PlaceKind.HIDDEN }.randomOrNull(rng) ?: return@mutate null
                com.pipo.robot.engine.PhotoLife.take(s, com.pipo.robot.data.PhotoSubject.PLACE, "", place, currentEnv(), now, rng).also {
                    Chronicle.remember(s, MemoryType.EVENT, "my scout drone flew to ${Places.byId(place)?.name} and took a photo", 0.35f, now, "drone:$place")
                }
            }
            if (photo != null) enqueue(Beat.Do { rig.lookAt(0.6f, -0.6f, 2f) }, Beat.Emote(EmoteKind.EXCLAIM),
                Beat.Say("My scout drone's back! It went to ${Places.byId(photo.placeId)?.name?.lowercase() ?: "outside"} and took this.", Sfx.HAPPY), Beat.Do { reveal = Reveal.Snap(photo) })
        }
        if (a == ActivityType.WORK_COMPUTER && rng.nextFloat() < 0.3f && repo.read { com.pipo.robot.engine.Inventor.has(it, "helper") })
            enqueue(Beat.Say(Dialogue.pick(listOf("B.O.L.T. says I'm doing great. B.O.L.T. says that about everything.", "B.O.L.T., run diagnostics. ...B.O.L.T. says 'error'. Classic B.O.L.T.", "B.O.L.T. and I solved it. B.O.L.T. mostly blinked."), rng), Sfx.BEEP))
        buildRoom()
    }

    private fun outcomeBeats(o: Outcome): List<Beat> = when (o) {
        is Outcome.Say -> listOf(Beat.Say(o.text, o.sfx))
        is Outcome.Emote -> listOf(Beat.Emote(o.kind))
        is Outcome.Animate -> listOf(Beat.Act(o.anim, o.ms / 1000f, o.expr))
        is Outcome.Found -> {
            val def = Catalog.item(o.item.catalogId)
            if (def == null) emptyList() else listOf(
                Beat.Emote(EmoteKind.EXCLAIM), Beat.Act(AnimState.SURPRISED, 0.7f, Expr.SURPRISED),
                Beat.Move(null, run = true), Beat.Do { rig.lookAt(0f, 0.35f, 4f); rig.holdItem = def.shape },
                Beat.Act(AnimState.PRESENTING, 0.5f, Expr.EXCITED),
                Beat.Say(Dialogue.pick(listOf("I found something!", "Look. LOOK.", "Guess what I found."), rng), Sfx.HAPPY),
                Beat.Do { reveal = Reveal.Item(o.item, def); markSeen(o.item) },
                Beat.Act(AnimState.PRESENTING, 2.2f, Expr.EXCITED), Beat.Do { rig.holdItem = null })
        }
        is Outcome.Finished -> projectBeats(o.project)
        is Outcome.Prank -> listOf(Beat.Do { focusCam(SceneGeo.prankX(o.key), 3.5f) }, Beat.Act(AnimState.MISCHIEVOUS, 1.5f, Expr.MISCHIEF), Beat.Emote(EmoteKind.SPARKLE))
        is Outcome.Offer -> when (o.kind) {
            OfferKind.PLAY_GAME -> listOf(Beat.Move(null), Beat.Do { rig.lookAt(0f, 0.35f, 4f) }, Beat.Say(o.text, Sfx.BEEP, listOf(
                Choice("Play") { acceptPlay(o.payload) },
                Choice("Not now") {
                    repo.mutate { s -> Personality.nudge(s, Trait.SOCIABILITY, -0.003f) }
                    enqueue(Beat.Say(Dialogue.pick(listOf("Fine. Later. I'm writing it down.", "Okay. I'll play with the ball. It never says no."), rng), Sfx.SIGH), Beat.Act(AnimState.BORED, 1.2f, Expr.BORED))
                })))
            OfferKind.SURPRISE -> listOf(Beat.Move(null), Beat.Say(o.text, Sfx.HAPPY), Beat.Do { focusCam(27f, 4f) }, Beat.Act(AnimState.EMBARRASSED, 2f, Expr.EMBARRASSED), Beat.Emote(EmoteKind.HEART))
            OfferKind.THOUGHT -> listOf(Beat.Say(o.text))
        }
        is Outcome.Returned -> returnBeats(o.report)
        is Outcome.PetNews -> if (o.line.startsWith("TRICK:")) {
            val t = NibLife.Trick.valueOf(o.line.removePrefix("TRICK:"))
            listOf(Beat.Emote(EmoteKind.EXCLAIM), Beat.Act(AnimState.SURPRISED, 0.5f, Expr.SURPRISED), Beat.Do { nibDoesTrick(t) }, Beat.Wait(1.6f),
                Beat.Act(AnimState.CELEBRATE, 1.4f, Expr.EXCITED), Beat.Say(t.learnedLine, Sfx.WIN), Beat.Do { nib(PetFace.LOVE, Sfx.PET_HAPPY, PetThought.HEART, 3f) })
        } else listOf(Beat.Do { nib(PetFace.HAPPY, Sfx.PET_HAPPY); pet.anim = PetAnim.WAG }, Beat.Say(o.line, Sfx.LAUGH))
        is Outcome.Ate -> listOf(Beat.Do { if (sounds) voice.synth.sfx(Sfx.CHEW) }, Beat.Say(o.line, Sfx.HAPPY))
        is Outcome.Cooked -> if (o.ok) listOf(Beat.Act(AnimState.PROUD, 0.8f, Expr.PROUD), Beat.Say(o.line, Sfx.HAPPY)) + nibGetsABite()
            else listOf(Beat.Act(AnimState.SURPRISED, 0.5f, Expr.SURPRISED), Beat.Emote(EmoteKind.SWEAT), Beat.Say(o.line, Sfx.SIGH), Beat.Act(AnimState.EMBARRASSED, 1.4f, Expr.EMBARRASSED))
        is Outcome.Drew -> listOf(
            Beat.Move(null), Beat.Do { rig.lookAt(0f, 0.35f, 4f); rig.holdItem = ItemShape.SKETCHBOOK },
            Beat.Act(AnimState.PRESENTING, 0.6f, Expr.EXCITED),
            Beat.Say(if (o.drawing.subject == com.pipo.robot.data.DrawSubject.BUILDING) "I drew my dream. Don't laugh." else Dialogue.pick(listOf("I drew something.", "Look. Art.", "Don't look. Okay, look."), rng), Sfx.HAPPY),
            Beat.Do { reveal = Reveal.Art(o.drawing); rig.holdItem = null },
            Beat.Act(if (o.drawing.subject == com.pipo.robot.data.DrawSubject.USER) AnimState.EMBARRASSED else AnimState.PROUD, 1.6f, Expr.PROUD))
        is Outcome.Photographed -> listOf(Beat.Act(AnimState.PHOTO, 0.7f, Expr.FOCUSED), Beat.Do { if (sounds) voice.synth.sfx(Sfx.SHUTTER) }, Beat.Say(o.line, Sfx.HAPPY))
        is Outcome.Watched -> {
            val out = mutableListOf<Beat>()
            for (c in o.clips.take(2)) { out += Beat.Act(AnimState.PHONE, 1.1f, if (c.topic == "comedy") Expr.LAUGH else Expr.HAPPY); out += Beat.Say(FeedLife.reaction(c, rng), if (c.topic == "comedy") Sfx.GIGGLE else null) }
            if (o.idea != null) { out += Beat.Emote(EmoteKind.IDEA); out += Beat.Say(o.idea, Sfx.SURPRISED) }
            out
        }
        is Outcome.Kickups -> when {
            o.record -> listOf(Beat.Act(AnimState.CELEBRATE, 1.4f, Expr.EXCITED), Beat.Do { rig.lookAt(0f, 0.35f, 3f) }, Beat.Say(Dialogue.pick(Dialogue.kickupsRecord, rng).replace("{k}", o.n.toString()), Sfx.WIN))
            o.n <= 2 -> listOf(Beat.Act(AnimState.SIGH, 1f, Expr.BORED), Beat.Say(Dialogue.pick(Dialogue.kickupsBad, rng), Sfx.SIGH))
            else -> listOf(Beat.Act(AnimState.HAPPY, 0.8f, Expr.HAPPY), Beat.Say(Dialogue.pick(Dialogue.kickups, rng).replace("{k}", o.n.toString()), Sfx.HAPPY))
        }
        is Outcome.Cleaned -> listOf(Beat.Act(AnimState.PROUD, 0.8f, Expr.PROUD), Beat.Say(o.line, Sfx.BEEP))
        is Outcome.Strange -> strangeBeats(o.line, o.photo)
        is Outcome.PhoneUsed -> listOf(
            Beat.Do { rig.phoneApp = o.app },
            Beat.Act(AnimState.PHONE, 1.6f, if (o.app == com.pipo.robot.engine.PhoneApp.NOTES) Expr.FOCUSED else Expr.CURIOUS),
            Beat.Do { if (sounds) voice.synth.sfx(if (o.app == com.pipo.robot.engine.PhoneApp.NOTES) Sfx.SCRIBBLE else Sfx.BOOP) },
            Beat.Say(o.line, null),
            Beat.Do { rig.phoneApp = null })
    }

    /**
     * Something that doesn't add up. No music, no popup: he goes quiet, looks, and tells you
     * in a small voice. If it's a photo, he shows it to you.
     */
    private fun strangeBeats(line: String, p: Photo?): List<Beat> {
        val out = mutableListOf<Beat>(
            Beat.Do { rig.lookAt(if (pipoX < 104f) 1f else -1f, -0.6f, 2.5f) }, Beat.Wait(1.6f),
            Beat.Act(AnimState.CURIOUS, 1.2f, Expr.WORRIED), Beat.Do { rig.lookAt(0f, 0.35f, 3f) },
            Beat.Say(line, null),
        )
        if (p != null) out += listOf(Beat.Do { reveal = Reveal.Snap(p) }, Beat.Act(AnimState.NERVOUS, 2f, Expr.WORRIED))
        else out += Beat.Emote(EmoteKind.QUESTION)
        return out
    }

    /**
     * He walks back in. With a bag if he bought something; puts food in the kitchen and parts on
     * the workbench; then shows you what he brought home and what happened out there.
     */
    private fun returnBeats(r: TripReport): List<Beat> {
        val bought = r.bought
        val food = bought.any { Foods.byId(it) != null }
        val parts = bought.any { Catalog.item(it) != null } || r.foundItemIds.isNotEmpty()
        val photo = if (r.photoId != 0L) repo.read { s -> s.photos.firstOrNull { it.id == r.photoId } } else null
        val headline = r.story.firstOrNull()
        val out = mutableListOf<Beat>(
            Beat.Do {
                doorTarget = 1f; if (sounds) voice.synth.sfx(Sfx.DOOR)
                pipoX = SceneGeo.DOOR_X + 3f; away = false; inBed = false; bedBlend = 0f
                if (r.withPet) { petHome = true; petX = SceneGeo.DOOR_X + 5f; petAct = PetActivity.ZOOMIES; petTarget = SceneGeo.DOOR_X - 30f; petRunning = true; petDecideAt = 6f }
                else if (petHome) { petAct = PetActivity.GREET; petTarget = SceneGeo.DOOR_X - 16f; petRunning = true; petDecideAt = 6f; nib(PetFace.LOVE, Sfx.PET_HAPPY, PetThought.HEART, 3f) } // Nib missed him
                carrying = bought.isNotEmpty(); rig.holdItem = if (carrying) ItemShape.BAG else null
                focusCam(SceneGeo.DOOR_X - 12f, 3f)
            },
            Beat.Wait(0.4f), Beat.Move(SceneGeo.DOOR_X - 12f), Beat.Do { doorTarget = 0f }, Beat.Do { rig.lookAt(0f, 0.35f, 2f) },
        )
        if (r.clue.isNotEmpty()) {
            // the strange thing comes first, and quietly
            out += Beat.Do { carrying = false; rig.holdItem = null }
            out += strangeBeats(r.clue, photo?.takeIf { it.anomaly })
            out += Beat.Do { reveal = Reveal.Trip(r, photo) }
            return out
        }
        out += say(Dialogue.pick(Dialogue.backHome, rng) + (headline?.let { " $it" } ?: ""), Sfx.HAPPY)
        if (food) out += listOf(Beat.Move(SceneGeo.station(Station.KITCHEN)), Beat.Act(AnimState.BUILDING, 1.1f, Expr.FOCUSED), Beat.Do { if (sounds) voice.synth.sfx(Sfx.BOOP) })
        if (parts) out += listOf(Beat.Move(SceneGeo.station(Station.WORKBENCH)), Beat.Act(AnimState.BUILDING, 1.1f, Expr.FOCUSED))
        out += Beat.Do {
            carrying = false; rig.holdItem = null
            // unpacked while you watched: no bag left by the door
            repo.mutate { it.world.objectStates.remove("bag"); it.lastTrip?.told = true }
            buildRoom()
        }
        r.story.getOrNull(1)?.let { out += say(it, Sfx.BEEP) }
        if (bought.isNotEmpty() || photo != null || r.foundItemIds.isNotEmpty()) {
            out += listOf(Beat.Move(null), Beat.Do { rig.lookAt(0f, 0.35f, 4f) }, Beat.Act(AnimState.PRESENTING, 0.5f, Expr.EXCITED), Beat.Do { reveal = Reveal.Trip(r, photo) }, Beat.Act(AnimState.PRESENTING, 1.8f, Expr.HAPPY))
        }
        if (r.wrongBuy.isNotEmpty()) out += listOf(Beat.Act(AnimState.EMBARRASSED, 1.4f, Expr.EMBARRASSED), say("...it was on sale.", Sfx.GIGGLE))
        return out
    }

    private fun projectBeats(p: PipoProject): List<Beat> {
        val def = Catalog.project(p.templateId) ?: return emptyList()
        val (anim, expr) = when (p.state) {
            ProjectState.DONE -> AnimState.PROUD to Expr.PROUD
            ProjectState.EVOLVED -> AnimState.EMBARRASSED to Expr.EMBARRASSED
            else -> AnimState.SAD to Expr.SAD
        }
        return listOf(
            Beat.Move(null), Beat.Do { rig.lookAt(0f, 0.35f, 4f) },
            Beat.Say(p.result, if (p.state == ProjectState.DONE) Sfx.WIN else Sfx.LOSE),
            Beat.Do { reveal = Reveal.Project(p, def) },
            Beat.Act(anim, 2.2f, expr),
        )
    }

    private fun micro(a: ActivityType) {
        val now = System.currentTimeMillis()
        val env = currentEnv()
        val d = if (a == ActivityType.HIDE || a == ActivityType.GO_OUT || hideSpot != null) null
            else repo.mutate(notify = false) { s -> BehaviorEngine.distraction(s, a, env, rng, now) }
        if (d != null) { stageDistraction(d, a); return }
        when (a) {
            ActivityType.EAT -> { if (sounds && rng.nextFloat() < 0.6f) voice.synth.sfx(Sfx.CHEW); if (rng.nextFloat() < 0.25f) rig.showEmote(EmoteKind.HEART); return }
            ActivityType.COOK -> { if (sounds && rng.nextFloat() < 0.4f) voice.synth.sfx(Sfx.SIZZLE) else hum(0.5f); return }
            ActivityType.DRAW -> { if (sounds && rng.nextFloat() < 0.5f) voice.synth.sfx(Sfx.SCRIBBLE) else hum(0.35f); if (rng.nextFloat() < 0.3f) rig.showEmote(EmoteKind.SPARKLE); return }
            ActivityType.CLEAN -> { if (sounds && rng.nextFloat() < 0.4f) voice.synth.sfx(Sfx.EFFORT) else hum(0.4f); return }
            ActivityType.PLAY_PET -> { if (sounds && rng.nextFloat() < 0.5f) voice.synth.sfx(if (rng.nextBoolean()) Sfx.PET_CHIRP else Sfx.GIGGLE); pet.anim = PetAnim.HOP; return }
            ActivityType.HIDE -> {
                // a giggle from somewhere in the room is the only clue you get
                if (sounds && rng.nextFloat() < 0.35f) voice.synth.sfx(Sfx.GIGGLE)
                if (hideSpot == HideSpot.ARCADE && clock > nextPeek) {
                    nextPeek = clock + 9f + rng.nextFloat() * 8f
                    enqueue(Beat.Move(SceneGeo.ARCADE_HIDE_X - 7f, sneak = true), Beat.Do { rig.lookAt(0f, 0.35f, 1.5f) }, Beat.Act(AnimState.PEEK, 1.1f, Expr.MISCHIEF), Beat.Move(SceneGeo.ARCADE_HIDE_X, sneak = true))
                }
                return
            }
            ActivityType.SLEEP -> if (!inBed) { rig.showEmote(EmoteKind.ZZZ); return }
            else -> Unit
        }
        if (absorbed) {
            // eyes locked on the work, the occasional thinking noise
            if (sounds && rng.nextFloat() < 0.3f) voice.synth.sfx(Sfx.HMM)
            if (rng.nextFloat() < 0.4f) rig.showEmote(EmoteKind.SPARKLE)
            return
        }
        when (a) {
            ActivityType.BUILD, ActivityType.EXPERIMENT -> { rig.showEmote(EmoteKind.SPARKLE); if (sounds && rng.nextFloat() < 0.4f) voice.synth.sfx(Sfx.BOOP) }
            ActivityType.THINK -> rig.showEmote(if (rng.nextBoolean()) EmoteKind.DOTS else EmoteKind.QUESTION)
            ActivityType.DANCE -> {
                rig.showEmote(EmoteKind.NOTES)
                if (!phoneState.music) hum(0.5f) // dancing to the music in his head, so you hear it too
            }
            ActivityType.SLEEP -> rig.showEmote(EmoteKind.ZZZ)
            ActivityType.PLAY_TOY -> if (kickups && rng.nextFloat() < 0.6f) {
                if (sounds) voice.synth.sfx(Sfx.KICK)
            } else {
                // lost control of it: chase it, then start again
                kickups = false
                ballTarget = 196f + rng.nextFloat() * 42f
                enqueue(Beat.Act(AnimState.HOP, 0.35f, Expr.HAPPY), Beat.Move(ballTarget - 4f, run = true), Beat.Do { kickups = true; ballTarget = pipoX + 2.5f })
            }
            ActivityType.PLAY_ARCADE -> if (rng.nextFloat() < 0.3f) { rig.showEmote(if (rng.nextBoolean()) EmoteKind.SPARKLE else EmoteKind.ANGER) }
            ActivityType.PLAY_CONSOLE -> {
                rig.lookAt(0.7f, 0.15f, 4f) // eyes on the game
                if (rng.nextFloat() < 0.35f) { rig.showEmote(if (rng.nextFloat() < 0.7f) EmoteKind.SPARKLE else EmoteKind.SWEAT); if (sounds && rng.nextBoolean()) voice.synth.sfx(Sfx.BOOP) }
            }
            ActivityType.SCROLL_PHONE -> if (rng.nextFloat() < 0.3f) { if (sounds) voice.synth.sfx(Sfx.GIGGLE); react(Expr.LAUGH, 0.9f) }
            ActivityType.REST, ActivityType.NOTHING -> rig.lookAt(rng.nextFloat() * 2f - 1f, rng.nextFloat() - 0.5f, 1.5f)
            ActivityType.CHARGE -> rig.showEmote(EmoteKind.SPARKLE)
            else -> Unit
        }
    }

    /**
     * Something caught his attention mid-activity. He goes to look; sometimes he comes back to
     * what he was doing, sometimes he forgets it entirely, and sometimes it leads somewhere.
     */
    private fun stageDistraction(kind: DistractionKind, a: ActivityType) {
        debugEvent("distracted $kind from $a")
        activity = null
        restPose = null
        val comeBack = rng.nextFloat() < 0.35f
        val night = dayFactor(room.hour) < 0.5f
        when (kind) {
            DistractionKind.BALL -> {
                ballTarget = (196f + rng.nextFloat() * 42f)
                enqueue(Beat.Emote(EmoteKind.EXCLAIM), Beat.Do { rig.lookAt(((ballTarget - pipoX) / 35f), 0.4f, 1.5f) },
                    Beat.Act(AnimState.SURPRISED, 0.5f, Expr.SURPRISED), Beat.Say(Dialogue.pick(Dialogue.distractedBall, rng), Sfx.SURPRISED),
                    Beat.Move(ballTarget - 4f, run = true), Beat.Act(AnimState.PLAYING, 2.4f, Expr.HAPPY))
            }
            DistractionKind.CRITTER -> if (night) {
                val (mx, _) = Critters.moth(clock)
                enqueue(Beat.Emote(EmoteKind.QUESTION), Beat.Say(Dialogue.pick(Dialogue.distractedCritter, rng), Sfx.HMM),
                    Beat.Move(mx - 9f), Beat.Act(AnimState.CURIOUS, 2.6f, Expr.CURIOUS), Beat.Act(AnimState.HOP, 0.6f, Expr.EXCITED))
            } else {
                enqueue(Beat.Emote(EmoteKind.QUESTION), Beat.Move(104f), Beat.Do { rig.lookAt(0.2f, -0.9f, 2.5f) },
                    Beat.Act(AnimState.CURIOUS, 2.2f, Expr.CURIOUS), Beat.Say(Dialogue.pick(Dialogue.distractedCritter, rng), Sfx.HMM))
            }
            DistractionKind.NOISE -> enqueue(Beat.Act(AnimState.LOOK_AROUND, 1.5f, Expr.CURIOUS), Beat.Emote(EmoteKind.QUESTION),
                Beat.Say(Dialogue.pick(Dialogue.distractedNoise, rng), Sfx.HMM), Beat.Act(AnimState.PEEK, 1.1f, Expr.SUSPICIOUS))
            DistractionKind.SHINY -> enqueue(Beat.Emote(EmoteKind.EXCLAIM), Beat.Move(wanderX(), run = true), Beat.Act(AnimState.PEEK, 1.2f, Expr.CURIOUS),
                Beat.Do {
                    val now = System.currentTimeMillis()
                    val item = repo.mutate { s ->
                        BehaviorEngine.stumble(s, rng, now).also {
                            // shown live right now, so it isn't "revealed" again on the next visit
                            if (it != null) s.events.filter { e -> now - e.createdAt < 5_000 }.forEach { e -> e.shownInApp = true }
                        }
                    }
                    if (item != null) beats.addAll(0, outcomeBeats(Outcome.Found(item)))
                    else beats.addFirst(Beat.Say(Dialogue.pick(listOf("Oh. Just a reflection.", "It was a crumb. A shiny crumb.", "False alarm. Still exciting."), rng), Sfx.BOOP))
                })
            DistractionKind.THOUGHT -> enqueue(Beat.Act(AnimState.THINKING, 1.6f, Expr.CURIOUS), Beat.Emote(EmoteKind.DOTS), Beat.Say(Dialogue.pick(Dialogue.distractedThought, rng)))
        }
        if (comeBack && kind != DistractionKind.SHINY) enqueue(Beat.Say("Anyway.", Sfx.BEEP), Beat.Do { forceActivity(a) })
        else if (rng.nextFloat() < 0.5f) enqueue(Beat.Act(AnimState.IDLE, 0.5f, Expr.CONTENT), Beat.Say(Dialogue.pick(Dialogue.forgotTask, rng), Sfx.BOOP))
    }

    /**
     * Where his eyes go when nobody is directing them: you, the ball, the moth, a bird at the
     * window, whatever he's working on. This is what makes the room feel reactive.
     */
    private fun stepAttention(dt: Float) {
        if (inBed || away || dragging || listening || rig.isHoldingLook()) return
        val c = cur
        if (c is Beat.Say || c is Beat.Move || targetX != null) return
        attentionAt -= dt
        if (attentionAt > 0f) return
        attentionAt = 1.8f + rng.nextFloat() * 3.5f
        data class T(val x: Float?, val h: Float, val w: Float)
        val cands = mutableListOf(
            T(null, 0f, 0.35f + sociability * 0.4f + relationship * 0.6f), // you
            T(104f, 57f, 0.25f), T(62f, 60f, 0.1f),
        )
        if (abs(ballTarget - ballU) > 3f) cands += T(ballU, 2f, 2.5f)
        if (dayFactor(room.hour) < 0.5f) { val (mx, mh) = Critters.moth(clock); if (abs(mx - pipoX) < 70f) cands += T(mx, mh, 0.9f) }
        Critters.bird(clock, if (room.feeder) 18f else 47f)?.let { cands += T(Critters.birdX(it), 58f, 1.8f) }
        if (petHome && abs(petX - pipoX) < 60f) cands += T(petX, 5f, if (petAct == PetActivity.ZOOMIES || petAct == PetActivity.PLAY_BALL) 1.6f else 0.6f)
        val a = activity
        if (a != null && activityArrived && a.station != Station.STAY && a.station != Station.FRONT && a.station != Station.WANDER) cands += T(SceneGeo.station(a.station), 20f, if (absorbed) 3f else 0.7f)
        if (phoneState.charging) cands += T(84f, 3f, 0.4f)
        var r = rng.nextFloat() * cands.sumOf { it.w.toDouble() }.toFloat()
        var pick = cands.first()
        for (t in cands) { r -= t.w; if (r <= 0f) { pick = t; break } }
        val x = pick.x
        if (x == null) rig.lookAt(0f, 0.35f, 1.2f + rng.nextFloat())
        else rig.lookAt(((x - pipoX) / 35f).coerceIn(-1f, 1f), (-(pick.h - 26f) / 35f).coerceIn(-1f, 1f), 1.2f + rng.nextFloat() * 1.3f)
    }

    private fun stepZoom(dt: Float) {
        val c = cur
        val g = geo
        val near = g != null && abs(pipoX - (camU + g.viewU / 2f)) < 30f
        val target = when {
            dragging || reveal != null -> 1f
            c is Beat.Act && c.anim == AnimState.PRESENTING -> 1.12f
            c is Beat.Say && targetX == null && near && !inBed -> 1.08f
            listening -> 1.06f
            inBed && dayFactor(room.hour) < 0.3f -> 1.04f
            else -> 1f
        }
        camZoom += ((if (calmMotion) 1f else target) - camZoom) * (1f - exp(-dt * 1.6f))
    }

    /** Zoom pivot: Pipo's middle, so the camera leans in on him. */
    private fun zoomPivot(): Offset {
        val g = geo ?: return Offset.Zero
        val f = footScreen()
        return Offset(f.x, f.y - g.pipoH * 0.5f)
    }
    fun zoomPivotPublic(): Offset = zoomPivot()
    private fun toView(o: Offset): Offset { val p = zoomPivot(); return Offset(p.x + (o.x - p.x) * camZoom, p.y + (o.y - p.y) * camZoom) }
    private fun fromView(x: Float, y: Float): Offset { val p = zoomPivot(); return Offset(p.x + (x - p.x) / camZoom, p.y + (y - p.y) / camZoom) }

    /** Top of Pipo's head in on-screen (zoomed) coordinates — for the speech bubble. */
    fun headView(): Offset = toView(headScreen())

    /** Debug builds only: where Pipo is on screen (after zoom) + what he's doing, for emulator test scripts. */
    private fun debugPos() {
        val g = geo ?: return
        val h = headView(); val f = toView(footScreen())
        Log.d("PipoDebug", "pos head=${h.x.toInt()},${h.y.toInt()} body=${f.x.toInt()},${((h.y + f.y) / 2f).toInt()} foot=${f.x.toInt()},${f.y.toInt()} " +
            "zoom=${"%.3f".format(camZoom)} cam=${camU.toInt()} x=${pipoX.toInt()} inBed=$inBed anim=${rig.anim} expr=${rig.expr} act=$activity mood=$mood talking=${rig.talking} yaw=${"%.2f".format(rig.yaw)} w=${g.w.toInt()}")
    }

    private fun debugEvent(msg: String) { if (BuildConfig.DEBUG) Log.d("PipoDebug", "event $msg") }

    /** Room state for this exact frame (per-frame values layered over the per-second snapshot). */
    fun roomNow(): RoomState = room.copy(ballU = ballU, torch = rig.torch, plantRustle = plantRustle, tiltX = tiltX, tiltY = tiltY,
        doorOpen = doorOpen, ballLift = ballLift, cooking = cooking, doorNote = away && doorOpen < 0.3f, fridgeOpen = fridgeOpen,
        flash = if (weather.kind == Weather.STORM) Critters.lightning(clock) * (if (calmMotion) 0.3f else 1f) else 0f)

    fun lightNow(r: RoomState): PipoLight = pipoLight(r, if (bedBlend > 0.5f) SceneGeo.BED_PIVOT else pipoX, glowColor(), rig.torch)

    /** Asking the user something at a natural pause (name, notifications). Returns true if it asked. */
    private fun maybeAskSomething(): Boolean {
        if (inBed || firstWakePending || hideSpot != null || away) return false
        val (asked, interactions, firstDone) = repo.read { Triple(it.settings.askedNotificationPermission, it.profile.interactions, it.profile.firstRunDone) }
        if (!asked && firstDone && interactions >= 6 && clock > 40f) {
            repo.mutate { it.settings.askedNotificationPermission = true }
            enqueue(Beat.Move(null), Beat.Do { rig.lookAt(0f, 0.35f, 4f) },
                Beat.Say("Can I send you messages sometimes? Only when something actually happens.", Sfx.BEEP, listOf(
                    Choice("Sure") {
                        repo.mutate { it.settings.notificationsEnabled = true }
                        askNotifPermission = true
                        enqueue(Beat.Say("Yay. I'll be picky about it.", Sfx.HAPPY), Beat.Act(AnimState.HAPPY, 1f, Expr.HAPPY))
                    },
                    Choice("No thanks") {
                        repo.mutate { it.settings.notificationsEnabled = false }
                        enqueue(Beat.Say("Okay. I'll keep my thoughts to myself.", Sfx.BEEP))
                    })))
            return true
        }
        return false
    }

    /* ---------------- movement + camera ---------------- */

    private fun stepMovement(dt: Float) {
        bedBlend += ((if (inBed) 1f else 0f) - bedBlend) * (1f - exp(-dt * 5f))
        val juggling = kickups && activity == ActivityType.PLAY_TOY && activityArrived && targetX == null && cur !is Beat.Move
        if (juggling) when (indoorSport) {
            com.pipo.robot.engine.Sport.CRICKET -> if (petHome) {
                // Nib bowls along the floor, he hits it back: the ball really goes between them
                val ph = (clock / 1.6f) % 1f
                ballTarget = if (ph < 0.5f) pipoX + 2.5f else petX - 2f
                ballLift = abs(sin(ph * 6.283f)) * 3f
                petTarget = null; pet.look(if (pipoX < petX) -1f else 1f, 0f)
            } else { ballTarget = pipoX + 2.5f; ballLift = abs(sin(clock * 4f)) * 6f }
            com.pipo.robot.engine.Sport.TABLE_TENNIS, com.pipo.robot.engine.Sport.BADMINTON -> {
                // bouncing it on the paddle: up past his face and back down
                ballTarget = pipoX + 2f
                ballLift = 14f + abs(sin(clock * 5.5f)) * 12f
            }
            else -> {
                // kick-ups: the ball bounces off his feet, one knee then the other
                ballTarget = pipoX + 2.5f
                ballLift = abs(sin(clock * 7f)) * 9f
            }
        } else ballLift *= exp(-dt * 8f)
        ballU += (ballTarget - ballU) * (1f - exp(-dt * (if (juggling) 12f else 1.8f)))
        if (dragging) return
        if (clock < flyUntil) { lift += ((16f + sin(clock * 3f) * 2.5f) - lift) * (1f - exp(-dt * 3f)); liftVel = 0f; return }
        if (lift > 0f && liftVel == 0f) liftVel = -1f // coming down after a flight: gravity takes over
        if (rig.suit > 0 && clock > suitUntil && festivalNow != com.pipo.robot.engine.Festival.HALLOWEEN && weather.kind != Weather.STORM && cur == null) rig.suit = 0 // suit off
        if (lift > 0f || liftVel != 0f) {
            liftVel -= 170f * dt
            lift += liftVel * dt
            if (lift <= 0f) { lift = 0f; liftVel = 0f; land() }
            return
        }
        val tx = targetX
        if (tx == null) { rig.moveDir = 0f; rig.bodyVel = 0f; return }
        val d = tx - pipoX
        // he looks where he's going and dips a little before setting off (anticipation)
        if (moveDelay > 0f) { moveDelay -= dt; rig.moveDir = 0f; rig.bodyVel = 0f; return }
        val cruise = if (running) 38f else if (sneaking) 8f else 14f * (0.7f + energy * 0.5f)
        // speeds up over a few steps, and eases into a stop instead of hitting a wall
        moveSpeed += (cruise - moveSpeed) * (1f - exp(-dt * (if (running) 7f else 5f)))
        val speed = moveSpeed * (abs(d) / (if (running) 5f else 3f)).coerceIn(0.35f, 1f)
        if (abs(d) < 0.5f) {
            pipoX = tx; targetX = null
            if (running) rig.impact(0.22f) // skid to a stop
            else if (moveSpeed > 10f) rig.impact(0.08f) // a little settle
            running = false; sneaking = false; moveSpeed = 0f
            rig.moveDir = 0f; rig.bodyVel = 0f
        } else {
            val step = sign(d) * min(abs(d), speed * dt)
            pipoX += step
            rig.moveDir = sign(d)
            rig.bodyVel = step / dt
            if (!sneaking) rig.lookAt(sign(d) * 0.8f, 0f, 0.3f)
            if (abs(pipoX - 61f) < 9f) plantRustle = min(1f, plantRustle + dt * (if (running) 4f else 2f))
        }
    }

    private fun clampCam(v: Float): Float = v.coerceIn(0f, geo?.maxCam() ?: 0f)

    private fun stepCamera(dt: Float) {
        val g = geo ?: return
        if (dragging) {
            // Carried: he must stay under the finger, so the camera holds still, and only scrolls
            // (taking him along) when he's pulled to the edge of the screen.
            val sx = footScreen().x / g.w
            val push = when { sx < 0.12f -> -(0.12f - sx); sx > 0.88f -> sx - 0.88f; else -> 0f }
            if (push != 0f) {
                val before = camU
                camU = clampCam(camU + push * 90f * dt / 0.12f)
                pipoX = (pipoX + camU - before).coerceIn(4f, SceneGeo.WORLD_W - 6f)
            }
            return
        }
        val focus = camFocusU
        val target = when {
            focus != null && clock < camFocusUntil -> focus - g.viewU / 2f
            clock < camHoldUntil -> return
            // nobody to follow: he's out, or hiding (the camera would give him away)
            away || (hideSpot != null && activity == ActivityType.HIDE) -> return
            else -> footWorldX() - g.viewU / 2f
        }
        camU += (clampCam(target) - camU) * (1f - exp(-dt * 2.2f))
    }

    private fun focusCam(x: Float, secs: Float) { camFocusU = x; camFocusUntil = clock + secs }
    private fun frontX(): Float = geo?.let { camU + it.viewU / 2f } ?: pipoX
    private fun wanderX(): Float = 12f + rng.nextFloat() * (SceneGeo.WORLD_W - 24f)
    private fun stationX(s: Station): Float = when (s) {
        Station.STAY -> pipoX
        Station.FRONT -> frontX()
        Station.WANDER -> wanderX()
        else -> SceneGeo.station(s)
    }

    private fun footWorldX() = pipoX

    /** Pipo's feet on screen (blends between standing and lying in bed). */
    fun footScreen(): Offset {
        val g = geo ?: return Offset.Zero
        val k = g.pipoH / 100f
        val standX = g.sx(pipoX, camU)
        val bedX = g.sx(SceneGeo.BED_PIVOT, camU)
        val bedY = g.floorY - SceneGeo.MATTRESS_TOP * g.u + 2f * k
        // sitting inside the box: only the top of his head and his eyes show over the edge
        val sink = if (hideSpot == HideSpot.BOX) (SceneGeo.BOX_H - 1.5f) * g.u else 0f
        return Offset(standX + (bedX - standX) * bedBlend, g.pipoFootY + sink + (bedY - g.pipoFootY) * bedBlend)
    }

    /** Nib's feet on screen. */
    fun petFootScreen(): Offset {
        val g = geo ?: return Offset.Zero
        return Offset(g.sx(petX, camU), g.pipoFootY + 2.5f * g.u)
    }

    private fun hitPet(x: Float, y: Float): Boolean {
        val g = geo ?: return false
        if (!petHome) return false
        val f = petFootScreen()
        return x in (f.x - 9f * g.u)..(f.x + 9f * g.u) && y in (f.y - 14f * g.u)..(f.y + 3f * g.u)
    }

    /** Top of Pipo's head on screen (for bubbles, emotes, lighting). */
    fun headScreen(): Offset {
        val g = geo ?: return Offset.Zero
        val k = g.pipoH / 100f
        val f = footScreen()
        val up = (100f * k + lift * g.u + rig.pose.jump * k)
        val standHead = Offset(f.x, f.y - up)
        val lyingHead = Offset(f.x - 40f * k, f.y - 62f * k)
        return Offset(standHead.x + (lyingHead.x - standHead.x) * bedBlend, standHead.y + (lyingHead.y - standHead.y) * bedBlend)
    }

    /** Head, body or feet, from where the tap landed on him (standing). */
    private fun pokeSpot(x: Float, y: Float): com.pipo.robot.ui.render.PipoRig.PokeSpot {
        val g = geo ?: return com.pipo.robot.ui.render.PipoRig.PokeSpot.BODY
        val k = g.pipoH / 100f
        val f = footScreen()
        rig.lookAt(((x - f.x) / (g.w * 0.3f)).coerceIn(-1f, 1f), 0.4f, 1.2f) // he looks at your finger
        return when {
            bedBlend > 0.5f -> com.pipo.robot.ui.render.PipoRig.PokeSpot.BODY
            y < f.y - 58f * k - lift * g.u -> com.pipo.robot.ui.render.PipoRig.PokeSpot.HEAD
            y > f.y - 14f * k - lift * g.u -> com.pipo.robot.ui.render.PipoRig.PokeSpot.FEET
            else -> com.pipo.robot.ui.render.PipoRig.PokeSpot.BODY
        }
    }

    private fun hitPipo(x: Float, y: Float): Boolean {
        val g = geo ?: return false
        if (away) return false
        val k = g.pipoH / 100f
        val f = footScreen()
        return if (bedBlend > 0.5f) x in (f.x - 66f * k)..(f.x + 38f * k) && y in (f.y - 70f * k)..(f.y + 6f * k)
        else x in (f.x - 40f * k)..(f.x + 40f * k) && y in (f.y - 110f * k - lift * g.u)..(f.y + 6f * k - lift * g.u)
    }

    /* ================================================================ */
    /*  Nib                                                              */
    /* ================================================================ */

    /** Pulls Nib's saved state in. [arriving] = you just opened the app: Nib may come to say hi. */
    private data class PetSnap(val home: Boolean, val x: Float, val act: PetActivity, val energy: Float)

    private fun syncPet(arriving: Boolean) {
        val p = repo.read { s -> PetSnap(s.pet.adopted && s.trip?.withPet != true, s.pet.x, s.pet.activity, s.pet.energy) }
        petHome = p.home
        if (!petHome) return
        if (petTarget == null) petX = p.x.coerceIn(4f, SceneGeo.WORLD_W - 6f)
        petAct = if (p.act == PetActivity.AWAY) PetActivity.WANDER else p.act
        // Nib greets you more reliably than Pipo does. That's Nib's whole thing.
        if (arriving && petAct != PetActivity.NAP && p.energy > 0.35f && rng.nextFloat() < 0.6f) {
            petAct = PetActivity.GREET; petTarget = frontX() + 8f; petRunning = true; petDecideAt = 5f
        }
    }

    /** Nib does one of its tricks, for real. */
    private fun nibDoesTrick(t: NibLife.Trick) {
        if (!petHome) return
        when (t) {
            NibLife.Trick.HIGH_FIVE -> { petTarget = pipoX + 5f; petRunning = true; pet.anim = PetAnim.HOP; nib(PetFace.HAPPY, Sfx.PET_HAPPY); if (sounds) viewModelScope.launch { delay(500); voice.synth.sfx(Sfx.KICK) } }
            NibLife.Trick.FETCH -> { ballTarget = (ballU + 25f).coerceAtMost(240f); petAct = PetActivity.PLAY_BALL; petTarget = ballTarget; petRunning = true; nib(PetFace.HAPPY, Sfx.PET_HAPPY, PetThought.BALL)
                viewModelScope.launch { delay(1800); ballTarget = pipoX + 4f; petTarget = pipoX + 6f; petRunning = true } }
            NibLife.Trick.SPIN -> { nibSpinUntil = clock + 2f; nib(PetFace.HAPPY, Sfx.PET_HAPPY) }
            NibLife.Trick.DANCE -> { nibSpinUntil = clock + 0.8f; pet.anim = PetAnim.HOP; nib(PetFace.HAPPY, Sfx.PET_HAPPY, PetThought.MUSIC, 3f) }
            NibLife.Trick.PLAY_DEAD -> { pet.anim = PetAnim.SLEEP; petAct = PetActivity.NAP; nib(PetFace.SURPRISED, Sfx.PET_SAD); petDecideAt = 3f
                viewModelScope.launch { delay(2200); petAct = PetActivity.GREET; nib(PetFace.SMUG, Sfx.PET_SMUG) } }
            NibLife.Trick.SING -> { nib(PetFace.HAPPY, null, PetThought.MUSIC, 3f)
                if (sounds) viewModelScope.launch { repeat(3) { voice.synth.sfx(Sfx.PET_CURIOUS); delay(330) }; delay(500); voice.synth.sfx(Sfx.PET_HAPPY) } }
        }
    }

    private var nibQuirkAt = 20f
    private var nibSpinUntil = 0f
    private var lastPatSeen = false

    /**
     * Nib's own life between decisions: little habits (chasing its tail, sniffing things, the
     * sunbeam), feelings about Pipo (comfort when he's sad, jealousy when you pat him, dancing when
     * he dances), and noticing you.
     */
    private fun nibQuirks() {
        if (!petHome || away) return
        // jealous: you're patting Pipo and not Nib
        if (patting && !lastPatSeen && petAct != PetActivity.NAP && rng.nextFloat() < 0.7f) {
            petAct = PetActivity.SIT_WITH_PIPO; petTarget = pipoX + 6f; petRunning = true; petDecideAt = 10f
            nib(PetFace.GRUMPY, Sfx.PET_GRUMP, PetThought.HEART, 3f)
            if (cur == null && beats.isEmpty() && rng.nextFloat() < 0.5f) enqueue(Beat.Wait(1.5f), Beat.Say(Dialogue.pick(listOf("Nib wants a turn. Nib always wants a turn.", "Nib, you're squishing me. Nib is jealous.", "Pat Nib too. Nib is looking at you like that."), rng), Sfx.GIGGLE))
        }
        lastPatSeen = patting
        // dancing: Nib bops along
        if (activity == ActivityType.DANCE && activityArrived && petAct != PetActivity.NAP && petAct != PetActivity.HIDE) {
            if (pet.anim != PetAnim.HOP && petTarget == null) pet.anim = PetAnim.HOP
            if (pet.thought == null && rng.nextFloat() < 0.01f) nib(PetFace.HAPPY, null, PetThought.MUSIC, 2f)
        }
        if (clock < nibQuirkAt || petTarget != null || petAct == PetActivity.NAP || petAct == PetActivity.HIDE || petAct == PetActivity.STEAL) return
        nibQuirkAt = clock + 14f + rng.nextFloat() * 20f
        val sad = mood == Mood.LONELY || mood == Mood.WORRIED || mood == Mood.NERVOUS
        val sunny = weather.kind == Weather.CLEAR && dayFactor(room.hour) > 0.6f
        when {
            // Pipo's sad: Nib comes and sits right up against him
            sad && !asleep && rng.nextFloat() < 0.7f -> {
                petAct = PetActivity.SIT_WITH_PIPO; petTarget = pipoX + 4.5f; petRunning = false; petDecideAt = 30f
                nib(PetFace.LOVE, Sfx.PET_SAD, PetThought.HEART, 4f)
                repo.mutate(notify = false) { PetEngine.together(it, "comfort", "When I was sad, Nib came and sat with me", System.currentTimeMillis(), 0.5f) }
                if (cur == null && beats.isEmpty()) enqueue(Beat.Wait(2f), Beat.Do { rig.lookAt(((petX - pipoX) / 25f).coerceIn(-1f, 1f), 0.6f, 2.5f) },
                    Beat.Act(AnimState.PETTING, 1.4f, Expr.CONTENT), Beat.Say(Dialogue.pick(listOf("...thanks, Nib.", "Nib always knows.", "Okay. I feel a bit better. Don't tell anyone, Nib."), rng), Sfx.SIGH))
            }
            // forgotten for too long: Nib sulks, turned away, until he notices
            repo.read { NibLife.hoursApart(it, System.currentTimeMillis()) > 8f } && !asleep && cur == null && beats.isEmpty() && rng.nextFloat() < 0.6f -> {
                pet.facing = if (pipoX > petX) -1f else 1f; nib(PetFace.GRUMPY, Sfx.PET_GRUMP, PetThought.PIPO, 4f)
                enqueue(Beat.Wait(2f), Beat.Do { rig.lookAt(((petX - pipoX) / 25f).coerceIn(-1f, 1f), 0.6f, 2f) }, Beat.Act(AnimState.SIGH, 0.8f, Expr.SAD),
                    Beat.Say(Dialogue.pick(listOf("Nib's ignoring me. ...I know. I've been busy. Come here, Nib.", "Nib's sulking. I haven't played with Nib all day. That's on me."), rng), Sfx.SIGH),
                    Beat.Do { forceActivity(ActivityType.PLAY_PET) })
            }
            // showing off something it knows
            rng.nextFloat() < 0.25f && repo.read { NibLife.tricks(it).isNotEmpty() } -> {
                val t = repo.read { NibLife.tricks(it).random(rng) }
                nibDoesTrick(t)
                if (cur == null && beats.isEmpty() && rng.nextFloat() < 0.5f) enqueue(Beat.Wait(1.2f), Beat.Say(Dialogue.pick(listOf("Nib's showing off again.", "Nib does that when it thinks you're watching.", "Good ${t.title}, Nib!"), rng), Sfx.GIGGLE))
            }
            // a present
            rng.nextFloat() < 0.15f -> repo.mutate { NibLife.gift(it, System.currentTimeMillis(), rng) }?.let { name ->
                pet.carry = com.pipo.robot.data.ItemShape.PEBBLE
                petTarget = pipoX + 6f; petRunning = false
                nib(PetFace.HAPPY, Sfx.PET_HAPPY, PetThought.HEART, 3f)
                enqueue(Beat.Wait(2.5f), Beat.Do { pet.carry = null; rig.lookAt(((petX - pipoX) / 25f).coerceIn(-1f, 1f), 0.6f, 2f) }, Beat.Act(AnimState.SURPRISED, 0.5f, Expr.SURPRISED),
                    Beat.Say("Nib brought me a $name. A present. For me. ...I'm putting it on the shelf. The special shelf.", Sfx.HAPPY), Beat.Act(AnimState.PETTING, 1.2f, Expr.LOVE))
                buildRoom()
            }
            else -> when (rng.nextInt(5)) {
                0 -> { nibSpinUntil = clock + 2.2f; nib(PetFace.HAPPY, Sfx.PET_HAPPY, null, 2f) } // chases its own tail
                1 -> { pet.look(rng.nextFloat() * 2f - 1f, 1f); nib(PetFace.CURIOUS, Sfx.PET_CURIOUS, PetThought.QUESTION, 2.5f) } // sniffs something on the floor
                2 -> if (sunny) { petAct = PetActivity.NAP; petTarget = 104f; petDecideAt = 40f; nib(PetFace.HAPPY, null, null, 2f) } // the sunbeam under the window
                    else { pet.look(0f, 0.9f); nib(PetFace.HAPPY, Sfx.PET_HAPPY, PetThought.HEART, 2f) } // looks at you
                3 -> { pet.look(((pipoX - petX) / 20f).coerceIn(-1f, 1f), -0.3f); nib(PetFace.LOVE, null, PetThought.PIPO, 2.5f) } // just watching him, fondly
                else -> { pet.look(0f, 0.9f); nib(PetFace.CURIOUS, Sfx.PET_CURIOUS, null, 1.5f) } // checks if you're still there
            }
        }
    }

    private fun stepPet(dt: Float) {
        if (!petHome) return
        nibQuirks()
        if (clock < nibSpinUntil) { pet.facing = if (((nibSpinUntil - clock) * 4f).toInt() % 2 == 0) 1f else -1f; pet.anim = PetAnim.HOP; pet.update(dt); return }
        pet.update(dt)
        val tx = petTarget
        if (tx != null) {
            val speed = (if (petRunning) 34f else 13f) * (if (nibTurbo) 1.45f else 1f) // the turbo wheel
            val d = tx - petX
            if (abs(d) < 0.6f) { petTarget = null; if (petRunning) pet.settle(); petRunning = false; petArrived() }
            else { petX += sign(d) * min(abs(d), speed * dt); pet.facing = sign(d) }
            pet.anim = if (petRunning) PetAnim.RUN else PetAnim.WALK
        } else {
            pet.anim = when (petAct) {
                PetActivity.NAP -> PetAnim.SLEEP
                PetActivity.SIT_WITH_PIPO -> PetAnim.SIT
                PetActivity.STARE -> PetAnim.STARE
                PetActivity.HIDE -> PetAnim.HIDE
                PetActivity.GREET, PetActivity.PLAY_BALL -> if (pet.anim == PetAnim.HOP && (pet.time % 1.4f) > 0.7f) PetAnim.WAG else pet.anim.takeIf { it == PetAnim.HOP || it == PetAnim.WAG } ?: PetAnim.WAG
                else -> PetAnim.IDLE
            }
            // following: keep near him, not on top of him
            if ((petAct == PetActivity.FOLLOW || petAct == PetActivity.SIT_WITH_PIPO) && !away && abs(pipoX - petX) > 16f) {
                petTarget = pipoX + (if (petX < pipoX) -9f else 9f); petRunning = abs(pipoX - petX) > 50f
            }
            if (petAct == PetActivity.PLAY_BALL && abs(ballU - petX) > 5f && !kickups) { petTarget = ballU - 3f; petRunning = true }
            if (petAct == PetActivity.STARE) pet.look(if (petX < 104f) 0.8f else -0.8f, -1f)
            if (petAct == PetActivity.GREET) pet.look(0f, 0.6f)
            if (abs(petX - 104f) < 25f && petAct != PetActivity.NAP && Critters.bird(clock, if (room.feeder) 18f else 47f) != null) { pet.look(0.3f, -1f); if (pet.anim == PetAnim.IDLE) pet.anim = PetAnim.STARE }
            // Nib keeps an eye on Pipo when he's close and doing something interesting
            if (!away && abs(pipoX - petX) < 30f && petAct in setOf(PetActivity.FOLLOW, PetActivity.SIT_WITH_PIPO, PetActivity.WANDER) && (targetX != null || cur is Beat.Say))
                pet.look(((pipoX - petX) / 20f).coerceIn(-1f, 1f), -0.4f)
        }
        petDecideAt -= dt
        if (petDecideAt <= 0f) decidePet()
    }

    /** Nib got where it was going. */
    private fun petArrived() {
        if (petX < 26f) pet.carry = null // stashed under the bed
        when (petAct) {
            PetActivity.PLAY_BALL -> if (!kickups) { ballTarget = (ballU + (if (rng.nextBoolean()) 1f else -1f) * (8f + rng.nextFloat() * 16f)).coerceIn(150f, 240f); pet.anim = PetAnim.HOP }
            PetActivity.STEAL -> if (petStealArrive) {
                // shelf reached: grab something, run under the bed with it
                petStealArrive = false
                val name = repo.mutate { s -> PetEngine.steal(s, System.currentTimeMillis(), rng) }
                if (name != null) {
                    petTarget = 20f; petRunning = true
                    pet.carry = repo.read { s -> s.world.items.firstOrNull { it.id == s.pet.stolenItemId }?.let { Catalog.item(it.catalogId)?.shape } }
                    if (!away && !asleep && hideSpot == null && cur == null && beats.isEmpty() && rng.nextFloat() < 0.7f)
                        enqueue(Beat.Emote(EmoteKind.EXCLAIM), Beat.Do { rig.lookAt(((petX - pipoX) / 35f).coerceIn(-1f, 1f), 0.3f, 2f) },
                            Beat.Say("Nib? What have you got? ...NIB. That's my $name.", Sfx.SURPRISED))
                    buildRoom()
                } else petAct = PetActivity.WANDER
            }
            PetActivity.STARE -> {
                if (sounds) voice.synth.sfx(Sfx.PET_CHIRP)
                // Nib noticed something. He looks. He sees nothing.
                if (!away && !asleep && hideSpot == null && cur == null && beats.isEmpty()) {
                    val uneasy = repo.mutate { s -> Mystery.petStared(s, System.currentTimeMillis()); if (s.mystery.stage >= 3) MoodEngine.setTransient(s, Mood.WORRIED, System.currentTimeMillis(), 30_000); s.mystery.stage >= 3 }
                    enqueue(Beat.Move(104f + (if (pipoX < 104f) -8f else 8f)), Beat.Do { rig.lookAt(if (pipoX < 104f) 0.7f else -0.7f, -0.8f, 3f) }, Beat.Wait(1.5f),
                        Beat.Act(AnimState.CURIOUS, 1.4f, if (uneasy) Expr.WORRIED else Expr.CURIOUS), Beat.Say(Dialogue.pick(Dialogue.petStare, rng), null))
                }
            }
            else -> Unit
        }
    }

    /** What Nib is watching Pipo do (so the follow-up — a bite, a look at the mess — can happen). */
    private var nibWatching: ActivityType? = null
    /** Today's festival, if any (refreshed on resume and hourly). */
    private var festivalNow: com.pipo.robot.engine.Festival? = null

    private fun refreshFestival() {
        val f = com.pipo.robot.engine.Festivals.today(System.currentTimeMillis())
        festivalNow = f
        val (armor, boots, tail, wheel) = repo.read { s -> listOf("armor", "rocket_boots", "nib_tail", "nib_wheel").map { com.pipo.robot.engine.Inventor.has(s, it) } }
        // his own armor is his Halloween costume, if he has it
        rig.hat = if (f == com.pipo.robot.engine.Festival.HALLOWEEN && armor) null else com.pipo.robot.ui.render.hatFor(f, false)
        pet.hat = com.pipo.robot.ui.render.hatFor(f, true)
        rig.rocketBoots = boots || rig.suit >= 2; pet.tailGlow = tail; nibTurbo = wheel; hasArmor = armor
        armorMark = repo.read { com.pipo.robot.engine.Inventor.armorMark(it) }
        // debug builds only: preview a suit without touching his saved progress (debug_flags: previewsuit=3)
        com.pipo.robot.DebugFlags["previewsuit"].firstOrNull()?.toIntOrNull()?.let { armorMark = it.coerceIn(1, 3) }
        // Halloween: the armor IS the costume
        if (f == com.pipo.robot.engine.Festival.HALLOWEEN && armorMark > 0) rig.suit = armorMark
    }
    private var armorMark = 0
    private var suitUntil = 0f
    private var flyUntil = 0f

    /** "Suit up": the armor goes on, piece by piece, with a lot of noise. */
    private fun suitUp(fly: Boolean) {
        val mk = armorMark
        if (mk == 0) { enqueue(Beat.Say("I don't have armor yet. I'm working on it. It's going to be red. And gold.", Sfx.HMM)); return }
        if (rig.suit == 0) enqueue(
            Beat.Say(if (repo.read { com.pipo.robot.engine.Inventor.has(it, "helper") }) "B.O.L.T., suit up." else "Suit up!", Sfx.BEEP),
            Beat.Act(AnimState.SPIN, 1.1f, Expr.FOCUSED), Beat.Emote(EmoteKind.SPARKLE),
            Beat.Do { rig.suit = mk; rig.rocketBoots = rig.rocketBoots || mk >= 2; suitUntil = clock + 120f; if (sounds) voice.synth.sfx(Sfx.SERVO); nib(PetFace.SURPRISED, Sfx.PET_CURIOUS, PetThought.EXCLAIM) },
            Beat.Act(AnimState.PROUD, 1.2f, Expr.PROUD),
            Beat.Say("Mark ${listOf("", "One", "Two", "Three")[mk]}. Online.", Sfx.WIN))
        if (fly) {
            if (mk < 2) enqueue(Beat.Say("Mark One doesn't fly. Mark One stands. Very heroically.", Sfx.SIGH))
            else enqueue(Beat.Do { flyUntil = clock + 5f; if (sounds) voice.synth.sfx(Sfx.EFFORT) }, Beat.Act(AnimState.CELEBRATE, 2.5f, Expr.EXCITED),
                Beat.Say(Dialogue.pick(listOf("I'm FLYING! Look! Nib, look!", "Up up up! Not the ceiling. Not the ceiling.", "This is the best day of my life. Again."), rng), Sfx.HAPPY),
                Beat.Do { nib(PetFace.SURPRISED, Sfx.PET_HAPPY, PetThought.EXCLAIM) })
        }
    }
    private var nibTurbo = false
    private var hasArmor = false

    /** The first time you open the app on a festival day, he greets you for it. */
    private fun maybeFestivalGreeting() {
        val f = festivalNow ?: return
        if (away || asleep) return
        val now = System.currentTimeMillis()
        val key = "fest:${f.name}:${com.pipo.robot.engine.WeatherEngine.localDay(now)}"
        val first = repo.mutate(notify = false) { s ->
            if (s.cooldowns.containsKey(key)) false else {
                s.cooldowns[key] = now
                MoodEngine.bump(s, happiness = 0.15f, excitement = 0.2f)
                Chronicle.remember(s, MemoryType.EVENT, "we had ${f.title} together", 0.6f, now, "festival:${f.name}:${now / (365L * 24 * 3600 * 1000)}")
                true
            }
        }
        if (!first) return
        enqueue(Beat.Wait(1.2f), Beat.Do { rig.lookAt(0f, 0.35f, 3f); nib(PetFace.HAPPY, Sfx.PET_HAPPY, PetThought.HEART, 3f) },
            Beat.Act(AnimState.CELEBRATE, 1.2f, Expr.EXCITED),
            Beat.Say(com.pipo.robot.engine.Festivals.greeting(f, now, petHome, rng), Sfx.HAPPY))
    }

    /** He just finished reading: the book stays open while he tells you about it. */
    private var readHoldUntil = 0f

    /**
     * Nib reacts to what Pipo starts doing, with a reason it would have: food smells, a ball moves,
     * Pipo lies down, Pipo leaves. Not every time (Nib is its own creature), but often enough that
     * you can see they live together.
     */
    private fun nibNotices(type: ActivityType) {
        if (!petHome || away) return
        if (type != ActivityType.COOK) nibWatching = null
        val bond = repo.read { it.pet.bond }
        val awake = petAct != PetActivity.NAP
        when (type) {
            ActivityType.EAT -> if (rng.nextFloat() < 0.4f + bond * 0.4f) {
                // begging: sits right next to him and stares up
                petAct = PetActivity.SIT_WITH_PIPO; petTarget = pipoX + 8f; petRunning = !awake; petDecideAt = 20f
                nib(PetFace.CURIOUS, Sfx.PET_CURIOUS, PetThought.FOOD, 4f)
                enqueue(Beat.Wait(2.2f), Beat.Do { pet.look(if (petX > pipoX) -1f else 1f, -1f); rig.lookAt(((petX - pipoX) / 30f).coerceIn(-1f, 1f), 0.6f, 2f) },
                    Beat.Say(Dialogue.pick(listOf("Nib, you don't even have a mouth. ...Fine. One crumb.", "Don't look at me like that, Nib.", "It's MY snack, Nib. ...okay, sharing."), rng), Sfx.BOOP))
            }
            ActivityType.SLEEP -> if (rng.nextFloat() < 0.5f + bond * 0.4f) {
                // bedtime: Nib curls up by the bed
                petAct = PetActivity.NAP; petTarget = 30f; petRunning = false; petDecideAt = 60f
            }
            ActivityType.BUILD -> {
                // his helper screen chimes in
                if (repo.read { com.pipo.robot.engine.Inventor.has(it, "helper") } && rng.nextFloat() < 0.4f)
                    enqueue(Beat.Wait(2f), Beat.Say(Dialogue.pick(listOf("B.O.L.T., run the numbers. ...B.O.L.T. says 'maybe'. Good enough.", "B.O.L.T., scan for problems. ...it found one. It's me. Rude.", "B.O.L.T., play some music. ...that's the 'error' song. My favourite."), rng), Sfx.BEEP))
                // and Nib "helps"
                if (rng.nextFloat() > 0.35f + bond * 0.3f) return
                petAct = PetActivity.SIT_WITH_PIPO; petTarget = pipoX + 7f; petRunning = true; petDecideAt = 40f
                val wrong = listOf(ItemShape.SPOON, ItemShape.DUCK, ItemShape.BUTTON, ItemShape.PEBBLE).random(rng)
                when (rng.nextInt(3)) {
                    0 -> enqueue(Beat.Wait(3f), Beat.Do { pet.carry = wrong; nib(PetFace.HAPPY, Sfx.PET_HAPPY, PetThought.EXCLAIM) }, Beat.Wait(1.2f),
                        Beat.Do { rig.lookAt(((petX - pipoX) / 25f).coerceIn(-1f, 1f), 0.6f, 2f) }, Beat.Act(AnimState.SIGH, 0.8f, Expr.BORED),
                        Beat.Say(Dialogue.pick(listOf("Nib. I asked for a screwdriver. That's a ${wrong.name.lowercase()}.", "Thank you, Nib. That's... not a part. But thank you."), rng), Sfx.BOOP),
                        Beat.Do { pet.carry = null; nib(PetFace.SMUG, Sfx.PET_SMUG) })
                    1 -> enqueue(Beat.Wait(3f), Beat.Do { nib(PetFace.CURIOUS, Sfx.PET_CURIOUS, PetThought.QUESTION) },
                        Beat.Say(Dialogue.pick(listOf("Nib, hold the light. Higher. HIGHER. Perfect. Lab assistant of the month.", "Nib is holding the torch. Nib is the torch now."), rng), Sfx.HAPPY))
                    else -> enqueue(Beat.Wait(4f), Beat.Do { nib(PetFace.SURPRISED, Sfx.PET_SAD, PetThought.EXCLAIM) }, Beat.Emote(EmoteKind.SWEAT), Beat.Act(AnimState.SURPRISED, 0.6f, Expr.SURPRISED),
                        Beat.Say(Dialogue.pick(listOf("NIB. Tiny fire! Tiny fire! ...okay, it's out. Nib, we don't lick the soldering iron.", "Nib set the instructions on fire. A bit. We don't need instructions anyway."), rng), Sfx.SURPRISED),
                        Beat.Do { repo.mutate(notify = false) { PetEngine.together(it, "lab", "Nib is my lab assistant. Nib set something on fire. A small something", System.currentTimeMillis(), 0.4f) } })
                }
            }
            ActivityType.READ, ActivityType.DRAW, ActivityType.THINK -> if (rng.nextFloat() < 0.25f + bond * 0.5f) {
                petAct = PetActivity.SIT_WITH_PIPO; petTarget = null; petDecideAt = 30f
            }
            else -> Unit
        }
    }

    private var nibPesterAt = 40f

    /** Nib reacts: a face, a sound in Nib, and maybe a picture of what it's thinking. */
    private fun nib(face: PetFace, sfx: Sfx? = null, thought: PetThought? = null, secs: Float = 2.5f) {
        if (!petHome) return
        pet.feel(face, secs)
        thought?.let { pet.think(it, secs + 0.3f) }
        if (sounds && sfx != null) voice.synth.sfx(sfx)
    }

    /**
     * Nib wants attention exactly when Pipo is busy: it comes over and beeps, bumps, sits on his
     * stuff. Pipo gets (theatrically) irritated, Nib is unrepentant, and Pipo gives in. Never mean,
     * never often: it's the kind of thing two roommates do.
     */
    private fun maybeNibPesters() {
        val a = activity ?: return
        if (clock < nibPesterAt || !petHome || away || !activityArrived || asleep || hideSpot != null || cur != null || beats.isNotEmpty() || listening || aiThinking) return
        if (petAct == PetActivity.NAP || petAct == PetActivity.HIDE || petAct == PetActivity.STEAL) return
        val focus = setOf(ActivityType.READ, ActivityType.DRAW, ActivityType.BUILD, ActivityType.EXPERIMENT, ActivityType.WORK_COMPUTER, ActivityType.THINK,
            ActivityType.COOK, ActivityType.PLAY_ARCADE, ActivityType.PLAY_CONSOLE, ActivityType.SCROLL_PHONE)
        if (a !in focus) return
        val (mischief, playful) = repo.read { it.pet.traits.mischief to it.pet.traits.playfulness }
        nibPesterAt = clock + 2f // check again soon if the dice say no
        if (rng.nextFloat() > 0.015f + mischief * 0.02f + playful * 0.01f) return
        nibPesterAt = clock + 120f + rng.nextFloat() * 120f
        val (complain, giveIn) = when (a) {
            ActivityType.READ -> listOf("Nib. I'm on a good part.", "Nib, that's my page. You're sitting on my page.", "Nib. NIB. The book is not a bed.") to
                listOf("...fine. You can listen. Chapter four. Nib, you're not even listening.", "Okay. One pat. Then reading.")
            ActivityType.DRAW -> listOf("Nib! That's not your paper.", "Nib, you just walked through the sky. Now it's a Nib sky.") to
                listOf("...okay, it's better with the footprint. Don't tell Nib.", "Fine. You can be in the drawing.")
            ActivityType.BUILD, ActivityType.EXPERIMENT -> listOf("Nib! That's a load-bearing screw!", "Nib, don't lick the wires. You don't even have a tongue.") to
                listOf("...okay. You can hold the screwdriver. Hold it. Nib. HOLD it.", "Fine. Assistant Nib. Unpaid.")
            ActivityType.COOK -> listOf("Nib, the pan is hot. HOT.", "Nib, you can't help. You're a beeping ball.") to
                listOf("...okay. Taste tester. Later.", "Sit. Good. Now stay. ...Nib.")
            ActivityType.THINK, ActivityType.WORK_COMPUTER -> listOf("I was having a thought. It's gone now. Thanks, Nib.", "Nib. I'm thinking. This is my thinking face.") to
                listOf("...what was I doing? Oh well. Pat?", "Fine. We'll think together. You think the beeps.")
            else -> listOf("Nib, you're in front of the screen.", "Nib! I was winning!") to
                listOf("...okay, you can watch. Quietly. NIB.", "Fine. Two-player mode. You're player two. You're bad at it.")
        }
        val now = System.currentTimeMillis()
        petAct = PetActivity.SIT_WITH_PIPO; petTarget = pipoX + (if (petX < pipoX) -5f else 5f); petRunning = true; petDecideAt = 20f
        enqueue(
            Beat.Wait(1.4f),
            Beat.Do { nib(PetFace.HAPPY, Sfx.PET_HAPPY, PetThought.PIPO); pet.anim = PetAnim.HOP; pet.look(if (petX > pipoX) -1f else 1f, -0.6f) },
            Beat.Wait(0.6f),
            Beat.Do { nib(PetFace.CURIOUS, Sfx.PET_CURIOUS, if (a == ActivityType.COOK) PetThought.FOOD else PetThought.BALL); rig.lookAt(((petX - pipoX) / 25f).coerceIn(-1f, 1f), 0.6f, 2.5f) },
            Beat.Emote(EmoteKind.ANGER),
            Beat.Act(AnimState.ANNOYED, 0.9f, Expr.ANNOYED),
            Beat.Say(Dialogue.pick(complain, rng), Sfx.GRUMBLE),
            // Nib is not sorry
            Beat.Do { nib(PetFace.SMUG, Sfx.PET_SMUG, secs = 2f); pet.anim = PetAnim.WAG },
            Beat.Act(AnimState.SIGH, 0.9f, Expr.BORED),
            Beat.Do { nib(PetFace.LOVE, Sfx.PET_HAPPY, PetThought.HEART, 3f) },
            Beat.Act(AnimState.PETTING, 1.1f, Expr.CONTENT),
            Beat.Say(Dialogue.pick(giveIn, rng), Sfx.SIGH),
        )
        repo.mutate(notify = false) { PetEngine.together(it, "pester", "Nib always bugs me when I'm busy. It's fine. It's FINE", now, 0.3f) }
        debugEvent("nib pesters during $a")
    }

    /** He finished cooking and Nib waited the whole time. */
    private fun nibGetsABite(): List<Beat> {
        if (nibWatching != ActivityType.COOK || !petHome || abs(petX - SceneGeo.station(Station.KITCHEN)) > 30f) return emptyList()
        nibWatching = null
        val now = System.currentTimeMillis()
        repo.mutate(notify = false) { PetEngine.together(it, "cooking", "Nib waited by the stove the whole time I cooked. I gave Nib the first bite", now, 0.4f) }
        return listOf(Beat.Do { rig.lookAt(((petX - pipoX) / 30f).coerceIn(-1f, 1f), 0.7f, 2f); pet.look(if (petX > pipoX) -1f else 1f, -0.8f) },
            Beat.Say(Dialogue.pick(listOf("First bite for Nib. Nib waited. Nib earned it.", "Here, Nib. Chef's tester.", "Nib says it's good. Nib says everything's good."), rng), Sfx.HAPPY),
            Beat.Do { petAct = PetActivity.ZOOMIES; petDecideAt = 3f; nib(PetFace.HAPPY, Sfx.PET_HAPPY, PetThought.HEART) })
    }

    private fun decidePet() {
        val now = System.currentTimeMillis()
        val env = currentEnv()
        val pipoAct = if (away) null else activity
        petAct = repo.mutate(notify = false) { s -> PetEngine.choose(s, env, rng, pipoAct, now).also { s.pet.activity = it; s.pet.x = petX } }
        petDecideAt = 7f + rng.nextFloat() * 9f
        petRunning = false
        if (!away || petHome) when (petAct) {
            PetActivity.PLAY_BALL -> nib(PetFace.HAPPY, Sfx.PET_HAPPY, PetThought.BALL)
            PetActivity.NAP -> if (rng.nextFloat() < 0.5f) nib(PetFace.SAD, null, PetThought.BATTERY, 2f)
            PetActivity.FOLLOW, PetActivity.SIT_WITH_PIPO -> if (rng.nextFloat() < 0.35f) nib(PetFace.HAPPY, null, PetThought.PIPO)
            PetActivity.ZOOMIES -> nib(PetFace.HAPPY, Sfx.PET_HAPPY)
            PetActivity.STEAL -> nib(PetFace.SMUG, null)
            PetActivity.HIDE -> nib(PetFace.SURPRISED, Sfx.PET_SAD, PetThought.EXCLAIM)
            PetActivity.STARE -> nib(PetFace.SURPRISED, Sfx.PET_CURIOUS, PetThought.EXCLAIM, 3f)
            PetActivity.WANDER -> if (rng.nextFloat() < 0.25f) nib(PetFace.CURIOUS, Sfx.PET_CURIOUS, PetThought.QUESTION)
            else -> Unit
        }
        when (petAct) {
            PetActivity.NAP -> {
                val hasBox = room.box
                petTarget = when { hasBox && rng.nextFloat() < 0.4f -> (SceneGeo.BOX_L + SceneGeo.BOX_R) / 2f; !inBed && rng.nextFloat() < 0.25f -> 28f; rng.nextBoolean() -> 100f; else -> 84f }
                // and over time one of them becomes "its" spot (it goes back there more)
                val fav = repo.read { NibLife.favoriteSpot(it) }
                if (fav != null && rng.nextFloat() < 0.5f) petTarget = when (fav) { "box" -> if (hasBox) (SceneGeo.BOX_L + SceneGeo.BOX_R) / 2f else 100f; "bed" -> 28f; "sunbeam" -> 104f; "door" -> SceneGeo.DOOR_X - 12f; else -> 100f }
                val spot = when (petTarget) { 28f -> "bed"; 104f -> "sunbeam"; 100f -> "rug"; 84f -> "rug"; else -> if (hasBox) "box" else "rug" }
                repo.mutate(notify = false) { NibLife.napAt(it, spot) }
                petDecideAt = 25f + rng.nextFloat() * 25f
            }
            PetActivity.WANDER -> petTarget = wanderX()
            PetActivity.ZOOMIES -> { petTarget = wanderX(); petRunning = true; petDecideAt = 3f }
            PetActivity.FOLLOW, PetActivity.SIT_WITH_PIPO -> if (!away) petTarget = pipoX + (if (petX < pipoX) -9f else 9f)
            PetActivity.PLAY_BALL -> { petTarget = ballU - 3f; petRunning = true }
            PetActivity.STEAL -> { petTarget = 181f; petStealArrive = true }
            PetActivity.HIDE -> { petTarget = 20f; petRunning = true }
            PetActivity.STARE -> { petTarget = 104f; petDecideAt = 14f }
            PetActivity.GREET -> { petTarget = frontX() + 8f; petRunning = true }
            PetActivity.AWAY -> Unit
        }
    }

    /* ================================================================ */
    /*  Staging (greetings, reactions)                                   */
    /* ================================================================ */

    private fun leaveBed() {
        if (inBed) {
            enqueue(Beat.Do { inBed = false; if (activity == ActivityType.SLEEP) activity = null }, Beat.Act(AnimState.STRETCH, 1.1f, Expr.SLEEPY))
            inBed = false
        }
    }

    private fun say(text: String, sfx: Sfx? = null): Beat.Say = Beat.Say(Dialogue.personalize(text, repo.read { it.profile.userName }), sfx)

    private fun stage(g: Greeting, notNow: Boolean) {
        val look = Beat.Do { rig.lookAt(0f, 0.35f, 3f) }
        when (g.kind) {
            GreetKind.BRIEF -> Unit
            GreetKind.AWAY -> goneFromState()
            GreetKind.HIDING -> {
                // silence. He's somewhere in the room. Find him.
                interrupt(); leaveBedNow()
                val spot = pickHideSpot()
                pipoX = hideX(spot); enterHide(spot)
                hideEmbarrassed = false
                activity = ActivityType.HIDE; activityArrived = true; activityEnd = clock + 45f + rng.nextFloat() * 30f
            }
            GreetKind.CONTINUE -> enqueue(Beat.Do { rig.lookAt(0f, 0.35f, 1.5f) }, Beat.Act(null, 0.8f, Expr.HAPPY))
            GreetKind.BUSY -> enqueue(look, Beat.Act(AnimState.FINGER_UP, 1.4f, Expr.FOCUSED), say(g.line, Sfx.HMM))
            GreetKind.PET_NEWS -> {
                interrupt(); leaveBed(); activity = null
                val ev = g.event
                val focus = when (ev?.payload) { "knocked" -> 186f; "sat" -> 181f; else -> null }
                enqueue(Beat.Move(null), look, say(g.line, Sfx.BEEP))
                if (focus != null) enqueue(Beat.Do { focusCam(focus, 3f) }, Beat.Act(AnimState.EMBARRASSED, 1.6f, Expr.EMBARRASSED))
                else enqueue(Beat.Do { if (petHome) { petAct = PetActivity.GREET; petTarget = pipoX + 8f; petRunning = true; petDecideAt = 8f }; if (sounds) voice.synth.sfx(Sfx.PET_CHIRP) },
                    Beat.Act(AnimState.HAPPY, 1.2f, Expr.HAPPY), say("Nib followed me home. Nib lives here now. Nib decided.", Sfx.HAPPY))
            }
            GreetKind.STRANGE -> {
                interrupt(); leaveBed(); activity = null
                enqueue(Beat.Move(null), look, Beat.Act(null, 0.8f, Expr.WORRIED), say(g.line, null))
                enqueueAll(strangeReveal())
            }
            GreetKind.FIRST_WAKE -> enqueue(Beat.Wait(1.5f), Beat.Emote(EmoteKind.ZZZ))
            GreetKind.SLEEPING -> enqueue(Beat.Wait(1.3f), Beat.Emote(EmoteKind.ZZZ), say(g.line, Sfx.SLEEPY))
            GreetKind.REVEAL_ITEM, GreetKind.REVEAL_PROJECT, GreetKind.PRANK, GreetKind.SURPRISE -> {
                interrupt(); leaveBed(); activity = null
                enqueueAll(revealBeats(g))
            }
            GreetKind.RUN_TO_USER -> {
                interrupt(); leaveBed(); activity = null
                enqueue(Beat.Emote(EmoteKind.EXCLAIM), Beat.Move(null, run = true), look, Beat.Act(AnimState.EXCITED, 0.9f, Expr.EXCITED), say(g.line, Sfx.HAPPY), Beat.Act(AnimState.HOP, 0.8f, Expr.HAPPY))
            }
            GreetKind.WORKING -> enqueue(look, Beat.Act(null, 0.6f, Expr.SURPRISED), say(g.line, Sfx.BEEP))
            GreetKind.MISCHIEF_HIDE -> { interrupt(); leaveBed(); enqueue(look, Beat.Act(AnimState.HIDING, 1.2f, Expr.SURPRISED), say(g.line, Sfx.LAUGH), Beat.Act(AnimState.MISCHIEVOUS, 1.6f, Expr.MISCHIEF)) }
            GreetKind.NOTHING -> enqueue(look, Beat.Wait(0.8f), say("...", null), Beat.Act(AnimState.BORED, 1.5f, Expr.BORED))
            GreetKind.FAKE_SLEEP -> {
                interrupt(); activity = null
                inBed = true; bedBlend = 1f; pipoX = SceneGeo.BED_PIVOT
                geo?.let { camU = clampCam(pipoX - it.viewU / 2f) }
                fakeSleeping = true
                fakeSleepUntil = clock + 13f + rng.nextFloat() * 5f
                enqueue(Beat.Wait(1.2f), Beat.Emote(EmoteKind.ZZZ))
            }
            GreetKind.PEEK_IN -> {
                interrupt(); activity = null
                inBed = false; bedBlend = 0f
                val gg = geo
                if (gg != null) {
                    val fromLeft = camU > 10f || camU + gg.viewU + 8f > SceneGeo.WORLD_W - 6f
                    pipoX = if (fromLeft) (camU - 7f).coerceAtLeast(4f) else (camU + gg.viewU + 7f).coerceAtMost(SceneGeo.WORLD_W - 6f)
                    val peekX = if (fromLeft) camU + 9f else camU + gg.viewU - 9f
                    camHoldUntil = clock + 5f
                    enqueue(Beat.Wait(0.9f), Beat.Move(peekX, sneak = true), Beat.Do { rig.lookAt(0f, 0.35f, 3f) }, Beat.Act(AnimState.PEEK, 1.3f, Expr.SUSPICIOUS),
                        say(g.line, Sfx.GIGGLE), Beat.Move(null), Beat.Act(AnimState.WAVE, 1f, Expr.HAPPY))
                } else enqueue(look, say(g.line, Sfx.GIGGLE))
            }
            GreetKind.HAPPY, GreetKind.CALM -> {
                interrupt(); leaveBed()
                val line = if (notNow) "You said not now earlier. Is it now? It feels like now." else g.line
                enqueue(Beat.Move(null), look, Beat.Act(AnimState.WAVE, 1.1f, if (g.kind == GreetKind.HAPPY) Expr.HAPPY else null), say(line, Sfx.BEEP))
            }
        }
    }

    /** He's out (decided while you were away). The room is as he left it. */
    private fun goneFromState() {
        val left = repo.read { it.trip?.let { t -> (t.endsAt - System.currentTimeMillis()) / 1000f } } ?: return
        interrupt()
        hideSpot = null; inBed = false; bedBlend = 0f; pipoX = SceneGeo.DOOR_X; rig.holdItem = null
        away = true; activity = ActivityType.GO_OUT; activityArrived = true
        activityEnd = clock + left.coerceIn(2f, 6f * 3600f)
    }

    private fun leaveBedNow() { inBed = false; bedBlend = 0f; if (activity == ActivityType.SLEEP) activity = null }

    /** Shows you the thing that doesn't add up, for the stage the mystery has reached. */
    private class StrangeSnap(val stage: Int, val photo: Photo?, val drawing: Drawing?, val token: OwnedItem?)

    private fun strangeReveal(): List<Beat> {
        val m = repo.read { s ->
            StrangeSnap(s.mystery.stage, s.photos.firstOrNull { it.id == s.mystery.photoId }, s.drawings.firstOrNull { it.id == s.mystery.dreamDrawingId },
                s.world.items.firstOrNull { it.id == s.mystery.tokenItemId })
        }
        return when (m.stage) {
            1 -> listOf(Beat.Do { focusCam(104f, 4f) }, Beat.Do { rig.lookAt(if (pipoX < 104f) 0.8f else -0.8f, -0.7f, 3f) }, Beat.Wait(1.2f),
                say("Out there. On the hills. Something blinked. Three times.", null), Beat.Act(AnimState.NERVOUS, 1.4f, Expr.WORRIED))
            2 -> m.token?.let { t -> Catalog.item(t.catalogId)?.let { d -> listOf(Beat.Do { rig.holdItem = d.shape }, Beat.Act(AnimState.PRESENTING, 0.8f, Expr.WORRIED),
                say("Look at the mark on it. I drew this mark. Before I ever saw it.", null), Beat.Do { reveal = Reveal.Item(t, d); rig.holdItem = null }) } } ?: emptyList()
            3 -> m.photo?.let { listOf(say("Look at this photo. Look behind. There's a door. There was no door.", null), Beat.Do { reveal = Reveal.Snap(it) }) } ?: emptyList()
            4 -> m.drawing?.let { listOf(say("The building from my dream. It's real. It's behind the hills.", null), Beat.Do { reveal = Reveal.Art(it) }) } ?: emptyList()
            else -> listOf(say("I went through the dome again. There was a note. In my handwriting.", null), Beat.Act(AnimState.THINKING, 1.6f, Expr.WORRIED))
        }
    }

    private fun revealBeats(g: Greeting): List<Beat> {
        val ev = g.event ?: return listOf(say(g.line))
        val look = Beat.Do { rig.lookAt(0f, 0.35f, 4f) }
        return when (ev.type) {
            EventType.DISCOVERY, EventType.REVEAL -> {
                val item = repo.read { s -> s.world.items.firstOrNull { it.id.toString() == ev.payload } }
                val def = item?.let { Catalog.item(it.catalogId) }
                if (item == null || def == null) listOf(say(g.line))
                else listOf(Beat.Move(null), look, Beat.Do { rig.holdItem = def.shape }, Beat.Act(AnimState.PRESENTING, 0.6f, Expr.EXCITED),
                    say(g.line, Sfx.HAPPY), Beat.Do { reveal = Reveal.Item(item, def); markSeen(item) },
                    Beat.Act(AnimState.PRESENTING, 2f, Expr.HAPPY), Beat.Do { rig.holdItem = null })
            }
            EventType.PROJECT_DONE, EventType.PROJECT_EVOLVED, EventType.PROJECT_FAILED -> {
                val p = repo.read { s -> s.projects.firstOrNull { it.id.toString() == ev.payload } }
                if (p == null) listOf(say(g.line))
                else listOf(Beat.Move(null), look, say(g.line, Sfx.BEEP), Beat.Do { focusCam(185f, 3f) }, Beat.Wait(1.2f)) + projectBeats(p).drop(2)
            }
            EventType.PRANK -> listOf(look, Beat.Act(AnimState.MISCHIEVOUS, 0.8f, Expr.MISCHIEF), say(g.line, Sfx.LAUGH),
                Beat.Do { focusCam(SceneGeo.prankX(ev.payload), 3.5f) }, Beat.Act(AnimState.EMBARRASSED, 2f, Expr.MISCHIEF))
            EventType.SURPRISE -> listOf(look, say(g.line, Sfx.HAPPY), Beat.Do { focusCam(27f, 4f) }, Beat.Act(AnimState.EMBARRASSED, 2.2f, Expr.EMBARRASSED), Beat.Emote(EmoteKind.HEART))
            else -> listOf(say(g.line))
        }
    }

    private fun markSeen(item: OwnedItem) {
        repo.mutate { s -> s.world.items.firstOrNull { it.id == item.id }?.seenByUser = true }
    }

    private fun reactToGame(r: GameLog.Result) {
        interrupt(); leaveBed()
        val look = Beat.Do { rig.lookAt(0f, 0.35f, 3f) }
        val rec = repo.read { it.games[r.game]?.copy() }
        val rematch = { Choice("Rematch") { enqueue(Beat.Act(AnimState.HOP, 0.5f, Expr.EXCITED), Beat.Do { navRequest = "game:${r.game}" }) } }
        if (r.pipoWon == true && rec != null && rec.pipoStreak >= 3) {
            enqueue(Beat.Move(null), look, Beat.Act(AnimState.CELEBRATE, 1.6f, Expr.PROUD),
                Beat.Say(Dialogue.pick(Dialogue.streakPipo, rng).replace("{k}", rec.pipoStreak.toString()), Sfx.WIN, listOf(rematch(), Choice("Later") {
                    enqueue(Beat.Say("Scared. Understandable.", Sfx.LAUGH), Beat.Act(AnimState.PROUD, 1f, Expr.PROUD))
                })))
            return
        }
        if (r.pipoWon == false && rec != null && rec.userStreak >= 3) {
            enqueue(Beat.Move(null), Beat.Act(AnimState.SULK, 2.2f, Expr.ANNOYED),
                Beat.Say(Dialogue.pick(Dialogue.streakUser, rng).replace("{k}", rec.userStreak.toString()), Sfx.GRUMBLE),
                look, Beat.Say("...rematch?", Sfx.BEEP, listOf(rematch(), Choice("Later") {
                    enqueue(Beat.Say(Dialogue.pick(Dialogue.sulkLines, rng), Sfx.SIGH), Beat.Act(AnimState.TURN_AWAY, 1.5f, Expr.ANNOYED))
                })))
            return
        }
        when (r.pipoWon) {
            true -> enqueue(Beat.Move(null), look, Beat.Act(AnimState.DANCING, 2f, Expr.PROUD), Beat.Say(Dialogue.pick(Dialogue.pipoWinsGame, rng), Sfx.WIN))
            false -> enqueue(Beat.Move(null), look, Beat.Act(AnimState.SAD, 1.2f, Expr.SAD), Beat.Say(Dialogue.pick(Dialogue.pipoLosesGame, rng), Sfx.LOSE), Beat.Act(AnimState.ARMS_CROSSED, 1.3f, Expr.SUSPICIOUS))
            null -> enqueue(Beat.Move(null), look, Beat.Say("Good game. I think. Who won? Me.", Sfx.BEEP))
        }
    }

    private fun acceptPlay(game: String) {
        enqueue(Beat.Act(AnimState.HOP, 0.6f, Expr.EXCITED), Beat.Do { navRequest = "game:$game" })
    }

    /* ================================================================ */
    /*  Touch                                                             */
    /* ================================================================ */

    private fun registerInteraction() {
        val now = System.currentTimeMillis()
        repo.mutate(notify = false) { s ->
            s.lastUserInteractionAt = now
            s.profile.interactions += 1
            s.profile.relationship = (s.profile.relationship + 0.001f).coerceAtMost(1f)
        }
    }

    private fun react(e: Expr, secs: Float = 1.2f) { reactExpr = e; reactUntil = clock + secs }

    fun onPointer(vx: Float, vy: Float) {
        val (x, y) = fromView(vx, vy)
        val h = headScreen()
        val g = geo ?: return
        if (inBed) return
        rig.lookAt((x - h.x) / (g.w * 0.45f), (y - (h.y + g.pipoH * 0.3f)) / (g.h * 0.35f), 1.2f)
    }

    fun onTap(vx: Float, vy: Float) {
        val (x, y) = fromView(vx, vy)
        val g = geo ?: return
        if (reveal != null) { reveal = null; return }
        if (doorNote != null) { doorNote = null; return }
        val wu = camU + x / g.u
        val hu = (g.floorY - y) / g.u
        val obj = SceneGeo.objects.firstOrNull { wu in it.l..it.r && hu in it.bottom..it.top }
        // hiding: you find him by tapping him, or the thing he's hiding behind
        val spot = hideSpot
        if (spot != null && activity == ActivityType.HIDE && (hitPipo(x, y) || obj?.id == when (spot) { HideSpot.ARCADE -> "arcade"; HideSpot.BOX -> "box"; HideSpot.BLANKET -> "bed" })) { found(); return }
        if (hitPet(x, y) && !hitPipo(x, y)) { tapPet(); return }
        if (spot != null && activity == ActivityType.HIDE) { registerInteraction(); if (sounds && rng.nextFloat() < 0.5f) voice.synth.sfx(Sfx.GIGGLE); return }
        if (hitPipo(x, y)) { tapPipo(pokeSpot(x, y)); return }
        if (away) {
            registerInteraction()
            if (obj?.id == "door") repo.read { it.trip }?.let { t -> focusCam(SceneGeo.DOOR_X, 3f); doorNote = Trips.doorNote(t, mood == Mood.MISCHIEVOUS) }
            return
        }
        if (obj == null) {
            val playful = repo.read { it.profile.traits.playfulness }
            if (hu < 8f && !inBed && !firstWakePending && (mood == Mood.EXCITED || mood == Mood.HAPPY || mood == Mood.CURIOUS) && playful > 0.4f) {
                registerInteraction()
                interrupt(); activity = null
                enqueue(Beat.Emote(EmoteKind.EXCLAIM), Beat.Move(wu, run = true), Beat.Act(AnimState.HOP, 0.5f, Expr.EXCITED))
                if (rng.nextFloat() < 0.35f) enqueue(Beat.Say(Dialogue.pick(Reactions.chase, rng), Sfx.HAPPY))
            }
            return
        }
        registerInteraction()
        tapObject(obj.id)
    }

    /** You found him. */
    private fun found() {
        registerInteraction()
        val embarrassed = hideEmbarrassed
        val spot = hideSpot
        interrupt()
        exitHide()
        activity = null
        val now = System.currentTimeMillis()
        repo.mutate { s ->
            MoodEngine.bump(s, happiness = 0.1f, excitement = 0.15f, loneliness = -0.2f)
            if (!embarrassed) {
                Chronicle.remember(s, MemoryType.JOKE, "we play hide and seek. You always find me", 0.55f, now, "hideseek")
                if (now - (s.cooldowns["journal:hide"] ?: 0L) > DAY_MS) {
                    s.cooldowns["journal:hide"] = now
                    Chronicle.journal(s, "Hide and seek", "He hid ${when (spot) { HideSpot.ARCADE -> "behind the arcade"; HideSpot.BOX -> "in the box"; else -> "under the blanket" }}. You found him. He's demanding a rematch.", JournalCategory.MOMENT, now)
                }
            }
        }
        val ritual = if (!embarrassed) repo.mutate { s -> Rituals.shared(s, "hideseek", now) } else null
        if (ritual != null) enqueue(Beat.Wait(0.3f))
        if (embarrassed) enqueue(Beat.Act(AnimState.EMBARRASSED, 1.2f, Expr.EMBARRASSED), Beat.Say(Dialogue.pick(Dialogue.hideEmbarrassed, rng), Sfx.SIGH))
        else enqueue(Beat.Act(AnimState.SURPRISED, 0.5f, Expr.SURPRISED), Beat.Do { rig.lookAt(0f, 0.35f, 3f) },
            Beat.Say(Dialogue.pick(Dialogue.hideFound, rng), Sfx.LAUGH), Beat.Act(AnimState.LAUGH, 1.2f, Expr.LAUGH))
        if (ritual != null) enqueue(Beat.Say("Hide and seek is our thing now. It's official.", Sfx.HAPPY))
    }

    /** Nib, poked. Nib is small and has opinions about being poked. */
    private fun tapPet() {
        registerInteraction()
        petPokes.addLast(clock)
        while (petPokes.isNotEmpty() && petPokes.first() < clock - 10f) petPokes.removeFirst()
        val n = petPokes.size
        when {
            n >= 4 -> nib(PetFace.GRUMPY, Sfx.PET_GRUMP, null, 3f)
            n == 3 -> nib(PetFace.SMUG, Sfx.PET_SMUG)
            petAct == PetActivity.NAP -> nib(PetFace.SURPRISED, Sfx.PET_CURIOUS, PetThought.QUESTION)
            else -> nib(PetFace.HAPPY, Sfx.PET_HAPPY, if (rng.nextFloat() < 0.4f) PetThought.HEART else null)
        }
        if (petAct == PetActivity.NAP && n == 1) { pet.anim = PetAnim.SIT; petAct = PetActivity.GREET; petDecideAt = 4f; return }
        if (n >= 4) {
            petAct = PetActivity.HIDE; petTarget = if (!away) pipoX - 6f else 20f; petRunning = true; petDecideAt = 10f
            repo.mutate(notify = false) { s -> s.pet.fear = (s.pet.fear + 0.3f).coerceAtMost(1f) }
            if (!away && !asleep && cur == null && rng.nextFloat() < 0.6f) enqueue(Beat.Do { rig.lookAt(((petX - pipoX) / 35f).coerceIn(-1f, 1f), 0.5f, 1.5f) }, Beat.Say(Dialogue.pick(listOf("Gently. Nib is small.", "Hey. Be nice to Nib.", "Nib says stop. I'm translating."), rng), Sfx.BOOP))
        } else {
            pet.anim = if (rng.nextBoolean()) PetAnim.HOP else PetAnim.WAG
            petAct = PetActivity.GREET; petDecideAt = 3f
            repo.mutate(notify = false) { s -> s.pet.bond = (s.pet.bond + 0.005f).coerceAtMost(1f); s.pet.boredom = (s.pet.boredom - 0.1f).coerceAtLeast(0f) }
        }
    }

    private fun tapPipo(spot: com.pipo.robot.ui.render.PipoRig.PokeSpot = com.pipo.robot.ui.render.PipoRig.PokeSpot.BODY) {
        registerInteraction()
        lastPokeSpot = spot
        if (firstWakePending) { firstWake(); return }
        // asleep somewhere odd (the rug, the box, the desk): you woke him where he was
        if (!inBed && activity == ActivityType.SLEEP && activityArrived) {
            val spot = sleepSpot
            interrupt(); exitHide(); activity = null; sleepSpot = SleepSpot.BED
            repo.mutate { s -> s.activity.type = ActivityType.REST }
            enqueue(Beat.Act(if (spot == SleepSpot.RUG) AnimState.GET_UP else AnimState.SURPRISED, 1f, Expr.SURPRISED), Beat.Act(AnimState.STRETCH, 1f, Expr.SLEEPY),
                Beat.Do { rig.lookAt(0f, 0.35f, 3f) }, Beat.Say(Dialogue.pick(listOf("...I wasn't asleep. I was checking the floor.", "Mmh? Did I fall asleep here? I meant to.", "...this is a very good spot. For sleeping. Which I wasn't."), rng), Sfx.SLEEPY))
            return
        }
        if (fakeSleeping) {
            interrupt()
            val now = System.currentTimeMillis()
            repo.mutate { s ->
                MoodEngine.bump(s, happiness = 0.08f, excitement = 0.1f)
                Chronicle.remember(s, MemoryType.JOKE, "I pretended to be asleep and got you", 0.5f, now, "fakesleep")
                if (now - (s.cooldowns["journal:fakesleep"] ?: 0L) > DAY_MS) {
                    s.cooldowns["journal:fakesleep"] = now
                    Chronicle.journal(s, "Pipo faked a nap", "He pretended to be asleep until you poked him. One eye was open the whole time.", JournalCategory.MISCHIEF, now)
                }
            }
            enqueue(Beat.Do { inBed = false; activity = null }, Beat.Emote(EmoteKind.EXCLAIM), Beat.Act(AnimState.EXCITED, 0.6f, Expr.MISCHIEF),
                Beat.Say(Dialogue.pick(Dialogue.fakeSleepCaught, rng), Sfx.LAUGH), Beat.Act(AnimState.LAUGH, 1.4f, Expr.LAUGH))
            return
        }
        if (activity in ScreenTime.screens && activityArrived) {
            // You beat any screen: the phone/controller goes away straight away. Never "one sec".
            val lines = if (activity == ActivityType.PLAY_CONSOLE) ScreenTime.consoleAwayForYou else ScreenTime.phoneAwayForYou
            interrupt(); activity = null; restPose = null
            enqueue(Beat.Act(AnimState.HOP, 0.5f, Expr.HAPPY), Beat.Do { rig.lookAt(0f, 0.35f, 3f) },
                Beat.Say(Dialogue.pick(lines, rng), Sfx.HAPPY))
            return
        }
        if (absorbed && activityArrived && activity != null && !inBed) {
            if (clock - absorbedPokeAt > 8f) {
                // Completely absorbed: one finger up, eyes stay on the work.
                absorbedPokeAt = clock
                beats.clear(); cur = null
                enqueue(Beat.Act(AnimState.FINGER_UP, 1.5f, Expr.FOCUSED), Beat.Say(Dialogue.pick(Dialogue.absorbedHold, rng), Sfx.HMM))
                return
            }
            absorbed = false
            repo.mutate(notify = false) { it.activity.absorbed = false }
        }
        if (inBed) {
            val ignore = repo.read { it.mood.energy < 0.3f && rng.nextFloat() < 0.5f + it.profile.traits.laziness * 0.3f }
            if (ignore) {
                interrupt()
                rig.showEmote(EmoteKind.ZZZ)
                enqueue(Beat.Say(Dialogue.pick(Dialogue.ignoreWhileSleeping, rng), Sfx.SLEEPY))
                if (activity == ActivityType.SLEEP) activityArrived = true
                return
            }
            wake(); return
        }
        pokes.addLast(clock)
        while (pokes.isNotEmpty() && pokes.first() < clock - 10f) pokes.removeFirst()
        val n = pokes.size
        val m = mood
        repo.mutate(notify = false) { s ->
            if (n >= 4) MoodEngine.bump(s, irritation = 0.07f * (n - 3) * (1.2f - s.profile.traits.patience))
            else MoodEngine.bump(s, happiness = 0.02f, loneliness = -0.1f, boredom = -0.05f)
            if (n >= 6) Personality.nudge(s, Trait.PATIENCE, -0.003f)
            if (n == 1) Chronicle.remember(s, MemoryType.EVENT, "you poke me a lot", 0.2f, System.currentTimeMillis(), "pokes")
        }
        refreshMood()
        rig.poked(lastPokeSpot)
        val line = when {
            n > 2 -> Dialogue.poke(n, m, rng)
            lastPokeSpot == com.pipo.robot.ui.render.PipoRig.PokeSpot.HEAD && rng.nextFloat() < 0.4f -> Dialogue.pick(listOf("My antenna! It's sensitive.", "Boing. Don't do that. Do it again.", "Hey. That's my thinking part."), rng)
            lastPokeSpot == com.pipo.robot.ui.render.PipoRig.PokeSpot.FEET && rng.nextFloat() < 0.4f -> Dialogue.pick(listOf("My feet! They're ticklish. Robots can be ticklish.", "Hey. Those are my walking parts."), rng)
            else -> Dialogue.poke(n, m, rng)
        }
        interrupt()
        when {
            n >= 9 -> enqueue(Beat.Act(AnimState.HIDING, 2.5f, Expr.CLOSED), Beat.Say(line ?: "...", Sfx.GRUMBLE), Beat.Act(AnimState.PEEK, 1.2f, Expr.SUSPICIOUS))
            n >= 6 -> enqueue(Beat.Emote(EmoteKind.ANGER), Beat.Say(line ?: "Hey.", Sfx.GRUMBLE), Beat.Act(AnimState.TURN_AWAY, 1.8f, Expr.ANNOYED))
            n >= 4 -> enqueue(Beat.Act(AnimState.SHAKE, 0.4f, Expr.ANNOYED), Beat.Say(line ?: "Hey.", Sfx.BOOP))
            m == Mood.GRUMPY -> { enqueue(Beat.Act(AnimState.ARMS_CROSSED, 1.4f, Expr.ANNOYED)); line?.let { enqueue(Beat.Say(it, Sfx.GRUMBLE)) } }
            m == Mood.SLEEPY -> enqueue(Beat.Act(AnimState.SLEEPY, 0.8f, Expr.SLEEPY), Beat.Say(line ?: "...hm?", Sfx.SLEEPY))
            m == Mood.MISCHIEVOUS && n <= 2 && rng.nextFloat() < 0.6f -> {
                val away = (pipoX + (if (pipoX < 120f) 1f else -1f) * (28f + rng.nextFloat() * 22f)).coerceIn(8f, SceneGeo.WORLD_W - 8f)
                enqueue(Beat.Act(AnimState.MISCHIEVOUS, 0.3f, Expr.MISCHIEF), Beat.Move(away, run = true), Beat.Say(Dialogue.pick(Reactions.runAway, rng), Sfx.LAUGH), Beat.Act(AnimState.HOP, 0.6f, Expr.MISCHIEF))
            }
            m == Mood.MISCHIEVOUS -> enqueue(Beat.Act(AnimState.WAVE, 0.6f, Expr.MISCHIEF), Beat.Say("Boop. Got you back.", Sfx.BOOP))
            else -> {
                if (sounds) voice.synth.sfx(if (n == 1) Sfx.SURPRISED else Sfx.LAUGH)
                enqueue(Beat.Act(if (rng.nextBoolean()) AnimState.HOP else AnimState.SURPRISED, 0.7f, if (n == 1) Expr.SURPRISED else Expr.HAPPY))
                line?.let { enqueue(Beat.Say(it)) }
            }
        }
    }

    private fun wake() {
        val grumpy = energy < 0.3f
        interrupt()
        activity = null
        repo.mutate { s ->
            if (grumpy) MoodEngine.bump(s, irritation = 0.15f)
            s.activity.type = ActivityType.REST
        }
        enqueue(Beat.Act(AnimState.SLEEPING, 0.6f, Expr.SURPRISED), Beat.Do { inBed = false }, Beat.Act(AnimState.STRETCH, 1.2f, Expr.SLEEPY))
        if (energy < 0.55f) enqueue(Beat.Do { if (sounds) voice.synth.sfx(Sfx.YAWN) }, Beat.Act(AnimState.YAWN, 2f, Expr.SLEEPY))
        enqueue(Beat.Do { rig.lookAt(0f, 0.35f, 3f) }, Beat.Say(Dialogue.pick(Dialogue.wokenUp, rng), Sfx.SLEEPY))
        val ev = repo.read { Greeter.topEvent(it) }
        if (ev != null) {
            repo.mutate { ev.shownInApp = true }
            val g = repo.read { s -> Greeter.plan(s.copy(activity = s.activity.copy(type = ActivityType.REST)), System.currentTimeMillis(), 10 * HOUR, rng) }
            if (g.event != null) enqueueAll(revealBeats(g))
        }
    }

    private fun firstWake() {
        firstWakePending = false
        interrupt()
        val now = System.currentTimeMillis()
        enqueue(
            Beat.Emote(EmoteKind.EXCLAIM), Beat.Act(AnimState.SLEEPING, 0.9f, Expr.SURPRISED),
            Beat.Do { inBed = false; activity = null }, Beat.Act(AnimState.STRETCH, 1.3f, Expr.SLEEPY),
            Beat.Do { rig.lookAt(0f, 0.35f, 5f) }, Beat.Act(AnimState.CURIOUS, 1f, Expr.CURIOUS),
            Beat.Move(null), Beat.Do { rig.lookAt(0f, 0.35f, 8f) },
            Beat.Say("Hi.", Sfx.BEEP), Beat.Act(AnimState.IDLE, 0.4f, Expr.HAPPY),
            Beat.Say("...I'm Pipo.", Sfx.BEEP),
            Beat.Say("I live here. This is my room.", Sfx.HAPPY),
            Beat.Say("You can poke me. Gently.", Sfx.LAUGH),
            Beat.Do { if (petHome) { focusCam(petX, 3f); petAct = PetActivity.GREET; petTarget = pipoX + 9f; petRunning = true; petDecideAt = 8f; if (sounds) voice.synth.sfx(Sfx.PET_CHIRP) } },
            Beat.Say(if (repo.read { it.pet.adopted }) "And that's Nib. Nib beeps. Nib lives here too." else "...", Sfx.HAPPY),
            Beat.Do {
                repo.mutate { s ->
                    s.profile.firstRunDone = true
                    Chronicle.journal(s, "You met Pipo", "He woke up, looked at you, and decided you were okay.", JournalCategory.MILESTONE, now)
                    Chronicle.remember(s, MemoryType.MOMENT, "the day we met", 1f, now, "met")
                    s.activity.type = ActivityType.REST
                }
            },
            Beat.Say("What should I call you?", Sfx.BEEP, listOf(
                Choice("Tell him") { awaitingName = true; openChat = true; enqueue(Beat.Act(AnimState.LISTENING, 3f, Expr.CURIOUS)) },
                Choice("Later") { enqueue(Beat.Say("Okay. Mystery human. I like it.", Sfx.LAUGH)) },
            )),
        )
    }

    fun onDoubleTap(vx: Float, vy: Float) {
        val (x, y) = fromView(vx, vy)
        if (!hitPipo(x, y)) { onTap(vx, vy); return }
        if (inBed || firstWakePending) { tapPipo(); return }
        registerInteraction()
        val stubborn = repo.read { it.profile.traits.stubbornness }
        val refuse = mood == Mood.GRUMPY || mood == Mood.SLEEPY || rng.nextFloat() < stubborn * 0.3f
        interrupt()
        if (refuse) enqueue(Beat.Say(Dialogue.pick(Dialogue.doubleTapNo, rng), Sfx.GRUMBLE), Beat.Act(AnimState.BORED, 1f, Expr.BORED))
        else {
            repo.mutate(notify = false) { s -> MoodEngine.bump(s, happiness = 0.05f, boredom = -0.1f) }
            if (rng.nextFloat() < 0.25f) enqueue(Beat.Act(AnimState.SPIN, 1.6f, Expr.HAPPY), Beat.Act(AnimState.DIZZY, 1.6f, Expr.DIZZY), Beat.Say(Dialogue.pick(Dialogue.dizzyLines, rng), Sfx.BOOP))
            else enqueue(Beat.Act(AnimState.SPIN, 1.1f, Expr.HAPPY), Beat.Act(AnimState.HOP, 0.6f, Expr.HAPPY), Beat.Say(Dialogue.pick(Dialogue.doubleTapYes, rng), Sfx.HAPPY))
        }
    }

    fun onLongPress(vx: Float, vy: Float) {
        val (x, y) = fromView(vx, vy)
        if (!hitPipo(x, y)) return
        if (inBed || firstWakePending) { tapPipo(); return }
        registerInteraction()
        interrupt()
        patting = true
        patStart = clock
        val aff = repo.mutate { s ->
            MoodEngine.bump(s, affection = 0.05f, happiness = 0.06f, loneliness = -0.3f, irritation = -0.1f)
            s.profile.relationship = (s.profile.relationship + 0.004f).coerceAtMost(1f)
            Experience.remember(s, MemoryType.MOMENT, "you gave me head pats", 0.35f, System.currentTimeMillis(), "pats", with = listOf("you"), feeling = 0.6f)
            Rituals.shared(s, "pats", System.currentTimeMillis())
            s.mood.affection
        }
        enqueue(Beat.Say(Dialogue.pat(mood, aff, rng), Sfx.HAPPY))
    }

    fun onPressEnd() {
        if (patting) {
            patting = false
            if (clock - patStart > 1.8f) rig.showEmote(EmoteKind.HEART)
        }
    }

    /** @return true if the drag grabbed Pipo (otherwise it pans the camera). */
    fun onDragStart(vx: Float, vy: Float): Boolean {
        val (x, y) = fromView(vx, vy)
        if (hitPipo(x, y) && !firstWakePending) {
            registerInteraction()
            interrupt()
            if (hideSpot != null) { exitHide(); if (activity == ActivityType.HIDE) activity = null }
            dragging = true
            if (inBed) { inBed = false; bedBlend = 0.4f }
            activity = null
            val brave = repo.read { it.profile.traits.confidence > 0.5f }
            if (!brave) repo.mutate(notify = false) { s -> MoodEngine.setTransient(s, Mood.NERVOUS, System.currentTimeMillis(), 8_000) }
            refreshMood()
            sayNow(Dialogue.pick(if (brave) Dialogue.pickedUpBrave else Dialogue.pickedUpNervous, rng), if (brave) Sfx.HAPPY else Sfx.SURPRISED)
            react(if (brave) Expr.EXCITED else Expr.NERVOUS, 30f)
            return true
        }
        camHoldUntil = clock + 4f
        camFocusU = null
        return false
    }

    fun onDrag(dx: Float, dy: Float) {
        val g = geo ?: return
        if (dragging) {
            pipoX = (pipoX + dx / g.u / camZoom).coerceIn(4f, SceneGeo.WORLD_W - 6f)
            lift = (lift - dy / g.u / camZoom).coerceIn(0f, 80f)
            rig.bodyVel = dx / g.u * 60f
            rig.lookAt(sign(dx) * 0.6f, -0.4f, 0.4f)
        } else {
            camU = clampCam(camU - dx / g.u / camZoom) // room tracks the finger even while zoomed in
            camHoldUntil = clock + 4f
        }
    }

    /** [vx] = release speed in px/s. A fast flick tosses Pipo across the room. */
    fun onDragEnd(vx: Float = 0f) {
        if (dragging) {
            dragging = false
            dropFrom = lift
            reactUntil = 0f
            val g = geo
            if (g != null && abs(vx) > 1400f) {
                val x = (pipoX + vx / g.u * 0.22f).coerceIn(8f, SceneGeo.WORLD_W - 8f)
                enqueue(Beat.Move(x, run = true))
                if (rng.nextFloat() < 0.5f) enqueue(Beat.Act(AnimState.DIZZY, 1.4f, Expr.DIZZY))
                enqueue(Beat.Say(Dialogue.pick(Reactions.tossed, rng), if (mood == Mood.GRUMPY) Sfx.GRUMBLE else Sfx.HAPPY))
            }
            if (lift <= 0f) land() else liftVel = 0f
        }
    }

    private fun land() {
        rig.impact((0.25f + dropFrom / 40f).coerceAtMost(1f))
        val high = dropFrom > 22f
        dropFrom = 0f
        if (high && rng.nextFloat() < 0.65f) {
            repo.mutate(notify = false) { s -> MoodEngine.setTransient(s, Mood.EMBARRASSED, System.currentTimeMillis(), 10_000) }
            if (sounds) voice.synth.sfx(Sfx.SURPRISED)
            enqueue(Beat.Act(AnimState.FALLEN, 1.4f, Expr.DIZZY), Beat.Say(Dialogue.pick(Dialogue.droppedFell, rng)), Beat.Act(AnimState.GET_UP, 1.1f, Expr.EMBARRASSED), Beat.Act(AnimState.SHAKE, 0.5f, Expr.EMBARRASSED))
        } else {
            enqueue(Beat.Act(AnimState.HOP, 0.6f, Expr.HAPPY), Beat.Say(Dialogue.pick(Dialogue.droppedOk, rng), Sfx.HAPPY))
        }
        repo.mutate { s -> Chronicle.remember(s, MemoryType.MOMENT, "you carried me around the room", 0.3f, System.currentTimeMillis(), "carried") }
    }

    private fun sayNow(text: String, sfx: Sfx?) {
        val id = ++bubbleSeq
        bubble = Bubble(id, text, emptyList())
        bubbleHideAt = clock + 2.5f
        if (sounds) sfx?.let { voice.synth.sfx(it) }
        rig.speak(text, charsPerSecond(mood, SpeechStyles.style(text), voice.mode))
        voice.speak(text, mood) {}
    }

    private fun tapObject(id: String) {
        if (inBed) { tapPipo(); return }
        if (firstWakePending) return
        SceneGeo.objects.firstOrNull { it.id == id }?.let { o ->
            rig.lookAt((((o.l + o.r) / 2f - pipoX) / 35f).coerceIn(-1f, 1f), (-((o.bottom + o.top) / 2f - 26f) / 35f).coerceIn(-1f, 1f), 1.5f)
        }
        val (stubborn, grumpy, drawings) = repo.read { Triple(it.profile.traits.stubbornness, it.mood.current == Mood.GRUMPY, it.world.objectStates["drawings"]?.toIntOrNull() ?: 0) }
        val prank = room.pranks.firstOrNull { SceneGeo.prankObject(it) == id }
        interrupt()
        if (prank != null && rng.nextFloat() < 0.7f) {
            enqueue(Beat.Act(AnimState.MISCHIEVOUS, 0.6f, Expr.MISCHIEF), Beat.Say(Dialogue.pick(listOf("It was like that when I got here.", "Art. It's art.", "I don't know who did that. Probably the plant."), rng), Sfx.LAUGH))
            return
        }
        when (id) {
            "clock" -> {
                val c = Calendar.getInstance()
                val t = "%d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
                enqueue(Beat.Say(if (isLate()) "It's $t. That's late. Suspiciously late." else "It's $t. Time is weird.", Sfx.BEEP))
                return
            }
            "drawings" -> {
                val d = repo.read { com.pipo.robot.engine.DrawingLife.hung(it).lastOrNull() }
                if (d == null) enqueue(Beat.Say("That wall needs art. I'm working on it.", Sfx.HAPPY))
                else enqueue(Beat.Say(if (d.subject == com.pipo.robot.data.DrawSubject.USER) "That's you. I nailed the ears." else d.caption, Sfx.HAPPY),
                    Beat.Do { reveal = Reveal.Art(d) }, Beat.Act(AnimState.EMBARRASSED, 1.2f, Expr.EMBARRASSED))
                return
            }
            "corkboard" -> {
                val p = repo.read { it.photos.lastOrNull() }
                if (p == null) enqueue(Beat.Say("That's for photos. I haven't taken any good ones yet. Or any ones.", Sfx.BEEP))
                else enqueue(Beat.Do { focusCam(141f, 3f) }, Beat.Say(Dialogue.photoMemory(p, rng), Sfx.HAPPY), Beat.Do { reveal = Reveal.Snap(p) })
                return
            }
            "box" -> {
                val petInBox = petHome && petAct == PetActivity.NAP && abs(petX - (SceneGeo.BOX_L + SceneGeo.BOX_R) / 2f) < 6f
                enqueue(Beat.Say(if (petInBox) "Shh. Nib's asleep in there." else Dialogue.pick(listOf("It's my box. It's a fort. Or a boat. Depends on the day.", "I kept the box. Obviously. It's the best part."), rng), Sfx.BEEP))
                return
            }
            "door" -> {
                // you're suggesting he goes out: he does, unless there's a real reason not to
                val now = System.currentTimeMillis()
                val (why, idea) = repo.read { Trips.whyNot(it, currentEnv(), now) to Trips.forUser(it, currentEnv(), now, "") }
                if (why == null && idea != null) {
                    val where = Places.byId(idea.placeId)?.let { Trips.placePhrase(it) } ?: "outside"
                    enqueue(Beat.Say(Dialogue.pick(listOf("Outside? Ooh. Okay. $where!", "You're right. I'll go to $where. ${idea.reason}"), rng), Sfx.HAPPY))
                    enqueue(Beat.Do { forceTrip("") })
                } else enqueue(Beat.Say(why ?: "It's pouring and I don't have an umbrella. Next time.", Sfx.BEEP))
                return
            }
        }
        val type = when (id) {
            "bed" -> if (energy < 0.55f || isLate()) ActivityType.SLEEP else ActivityType.REST
            "plant" -> ActivityType.INSPECT_PLANT
            "charger" -> ActivityType.CHARGE
            "desk" -> if (rng.nextBoolean()) ActivityType.WORK_COMPUTER else ActivityType.READ
            "workbench" -> if (repo.read { it.activeProject()?.state == ProjectState.BUILDING }) ActivityType.BUILD else ActivityType.EXPERIMENT
            "arcade" -> ActivityType.PLAY_ARCADE
            "console" -> if (repo.read { ScreenTime.allowed(it, ActivityType.PLAY_CONSOLE, System.currentTimeMillis()) }) ActivityType.PLAY_CONSOLE else {
                enqueue(Beat.Say(Dialogue.pick(listOf("No more games for now. My thumbs need a holiday.", "Console later. I have real stuff to do.", "I already played! Screen break."), rng), Sfx.BEEP), Beat.Act(AnimState.PROUD, 1f, Expr.PROUD))
                return
            }
            "toys" -> ActivityType.PLAY_TOY
            "window" -> ActivityType.THINK
            "shelf" -> ActivityType.EXAMINE
            "kitchen" -> repo.read { s ->
                // the kitchen means cooking when there's something to cook; a snack otherwise
                when {
                    FoodLife.feasible(s).isNotEmpty() && (s.mood.appetite > 0.25f || FoodLife.mealTime(hourOf(System.currentTimeMillis())) || rng.nextBoolean()) -> ActivityType.COOK
                    FoodLife.edible(s).isNotEmpty() -> ActivityType.EAT
                    FoodLife.feasible(s).isNotEmpty() -> ActivityType.COOK
                    else -> null
                }
            } ?: run {
                // nothing in the fridge: he goes shopping, if he can
                val now = System.currentTimeMillis()
                val ok = repo.read { Trips.whyNot(it, currentEnv(), now) == null && it.coins >= 2 }
                if (ok) { enqueue(Beat.Say("The fridge is empty. Echo empty. I'll go get food!", Sfx.HAPPY)); enqueue(Beat.Do { forceTrip("groceries") }) }
                else enqueue(Beat.Say("The fridge is empty. Echo empty. Shopping first thing tomorrow.", Sfx.BEEP))
                return
            }
            else -> null
        }
        if (type == null) {
            enqueue(Beat.Say(if (id == "bed") "I'm not tired. You're tired." else "Hm?", Sfx.BEEP), Beat.Act(AnimState.BORED, 0.8f, Expr.BORED))
            return
        }
        repo.mutate(notify = false) { s -> Chronicle.remember(s, MemoryType.EVENT, "you like it when I use the $id", 0.2f, System.currentTimeMillis(), "suggest:$id") }
        // personality colours it; it rarely blocks it (and a grumble usually turns into "...fine")
        if (rng.nextFloat() < stubborn * 0.12f + (if (grumpy) 0.15f else 0f)) {
            enqueue(Beat.Say(Dialogue.pick(Dialogue.refusals, rng), Sfx.GRUMBLE), Beat.Act(AnimState.ANNOYED, 1f, Expr.ANNOYED))
            if (rng.nextFloat() < 0.6f) { enqueue(Beat.Say("...fine. Only because I wanted to anyway.", Sfx.BEEP)); enqueue(Beat.Do { forceActivity(type) }) }
            return
        }
        enqueue(Beat.Say(Dialogue.pick(listOf("Ooh. Okay.", "Good idea. My idea, actually.", "Fine.", "Oh! Yes."), rng), Sfx.BEEP))
        forceActivity(type)
    }

    private fun isLate() = hourOf(System.currentTimeMillis()).let { it >= 23 || it < 5 }

    /* ================================================================ */
    /*  Conversation + voice + phone actions                             */
    /* ================================================================ */

    fun sendChat(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        registerInteraction()
        userLine = t
        userLineUntil = clock + 4f
        val now = System.currentTimeMillis()
        if (away) { textPipo(t, now); return }
        val wasAsleep = inBed || firstWakePending
        firstWakePending = false
        if (hideSpot != null && activity == ActivityType.HIDE) { found(); return } // you called out; he gives himself away
        val res = repo.mutate { s -> LocalBrain.respond(s, t, now, rng, awaitingName) }
        awaitingName = false
        refreshMood()
        interrupt()
        if (wasAsleep && res.action != ChatAction.SLEEP) {
            enqueue(Beat.Do { inBed = false; activity = null }, Beat.Act(AnimState.STRETCH, 0.9f, Expr.SLEEPY))
            repo.mutate { it.profile.firstRunDone = true }
        }
        rig.lookAt(0f, 0.35f, 3f)
        val phone = res.phone
        if (res.action == ChatAction.PHONE && phone != null) { handlePhone(phone); return }
        val b = brain
        if (b !== NoBrain && !res.locked && phoneState.online) {
            aiThinking = true
            rig.showEmote(EmoteKind.DOTS)
            viewModelScope.launch {
                val ctx = brainContext(res.text, t)
                val ai = withTimeoutOrNull(14_000) { b.reply(t, ctx) }
                aiThinking = false
                val line = ai ?: if (!brainFailedOnce) { brainFailedOnce = true; Dialogue.pick(Dialogue.brainWeird, rng) + " " + res.text } else res.text
                remember(t, line)
                respond(line, res)
            }
        } else { remember(t, res.text); respond(res.text, res) }
    }

    /**
     * He's out: you're texting his little phone. He answers from wherever he is (the engine knows
     * where), and "come home" really brings him home sooner.
     */
    private fun textPipo(t: String, now: Long) {
        val res = repo.mutate { s -> LocalBrain.respond(s, t, now, rng, false).also { Rituals.shared(s, "texts", now) } }
        val phone = res.phone
        if (res.action == ChatAction.PHONE && phone != null) { handlePhone(phone); return }
        if (res.action == ChatAction.COME_HOME) {
            repo.mutate { s -> Trips.hurryHome(s, now) }
            activityEnd = minOf(activityEnd, clock + 25f)
        }
        val b = brain
        if (b !== NoBrain && !res.locked && phoneState.online) {
            aiThinking = true
            viewModelScope.launch {
                val ai = withTimeoutOrNull(12_000) { b.reply(t, brainContext(res.text, t)) }
                aiThinking = false
                val line = ai ?: res.text
                remember(t, line)
                showText(line)
            }
        } else { remember(t, res.text); showText(res.text) }
    }

    private fun showText(line: String) {
        text = line
        textUntil = clock + 5f + line.length * 0.06f
        if (sounds) voice.synth.sfx(Sfx.BEEP)
    }

    private fun remember(user: String, pipo: String) {
        chatHistory.addLast(Turn(user.take(300), pipo))
        while (chatHistory.size > 6) chatHistory.removeFirst()
    }

    private fun brainContext(local: String, query: String = ""): BrainContext = repo.read { s ->
        val t = s.profile.traits
        val now = System.currentTimeMillis()
        val trip = s.trip
        val world = buildList {
            if (trip != null) add("you are at ${Places.byId(trip.placeId)?.let { Trips.placePhrase(it) } ?: "somewhere"} because: ${trip.reason}")
            add("the weather is ${WeatherEngine.describe(weather.kind)}")
            if (s.pet.adopted) add("your pet is Nib, a small round beeping robot" + if (trip?.withPet == true) " who is with you" else "")
            if (s.craving.isNotEmpty()) add("you really want ${com.pipo.robot.engine.Economy.nameOf(s.craving)}")
            add("you have ${s.coins} coins")
            s.activeProject()?.let { p -> add("you're building a ${p.title.lowercase()}" + (Trips.materialsNeeded(s).firstOrNull()?.let { m -> " and still need ${com.pipo.robot.engine.Economy.nameOf(m)}" } ?: "")) }
            s.lastTrip?.takeIf { now - it.at < DAY_MS }?.let { r -> add("today you went to ${Places.byId(r.placeId)?.let { Trips.placePhrase(it) }}: ${r.story.take(2).joinToString(" ")}") }
            addAll(com.pipo.robot.engine.Grounding.facts(s, query, now))
        }
        BrainContext(
            mood = s.mood.current.name.lowercase(),
            personality = "curiosity ${"%.1f".format(t.curiosity)}, playfulness ${"%.1f".format(t.playfulness)}, mischief ${"%.1f".format(t.mischief)}, stubbornness ${"%.1f".format(t.stubbornness)}, confidence ${"%.1f".format(t.confidence)}",
            userName = s.profile.userName,
            activity = if (trip != null) "out" else BehaviorEngine.describe(s.activity.type),
            memories = com.pipo.robot.engine.Grounding.memoriesFor(s, query, now),
            localReply = local,
            history = chatHistory.toList(),
            world = world,
            texting = trip != null,
        )
    }

    private fun respond(text: String, res: ChatResult) {
        enqueue(Beat.Say(text, res.sfx))
        when (res.action) {
            ChatAction.DANCE -> enqueue(Beat.Do { forceActivity(ActivityType.DANCE) })
            ChatAction.SLEEP -> enqueue(Beat.Do { forceActivity(ActivityType.SLEEP) })
            ChatAction.PLAY -> enqueue(Beat.Act(AnimState.HOP, 0.5f, Expr.EXCITED), Beat.Do { navRequest = "game:${res.payload}" })
            ChatAction.SPIN -> enqueue(Beat.Act(AnimState.SPIN, 1.2f, Expr.HAPPY))
            // "hide" means hide and seek: he really goes and hides somewhere in the room
            ChatAction.HIDE -> enqueue(Beat.Act(AnimState.HIDING, 1f, Expr.CLOSED), Beat.Do { forceActivity(ActivityType.HIDE) })
            ChatAction.DRAW -> enqueue(Beat.Do { forceActivity(ActivityType.DRAW) })
            ChatAction.GO_OUT -> enqueue(Beat.Do { forceTrip(res.payload) })
            ChatAction.COOK -> enqueue(Beat.Do { forceActivity(ActivityType.COOK) })
            ChatAction.EAT -> enqueue(Beat.Do { forceActivity(ActivityType.EAT) })
            ChatAction.KICK -> enqueue(Beat.Do {
                indoorSport = runCatching { com.pipo.robot.engine.Sport.valueOf(res.payload) }.getOrDefault(com.pipo.robot.engine.Sport.FOOTBALL)
                sportChosen = true
                forceActivity(ActivityType.PLAY_TOY)
            })
            ChatAction.SUIT_UP -> enqueue(Beat.Do { suitUp(fly = res.payload == "fly") })
            ChatAction.PLAY_PET -> if (res.payload.startsWith("TRICK:")) enqueue(Beat.Do { nibDoesTrick(NibLife.Trick.valueOf(res.payload.removePrefix("TRICK:"))) })
                else enqueue(Beat.Do { forceActivity(ActivityType.PLAY_PET) })
            ChatAction.EMBARRASSED -> enqueue(Beat.Act(AnimState.EMBARRASSED, 2f, Expr.EMBARRASSED), Beat.Emote(EmoteKind.HEART))
            ChatAction.ANNOYED -> enqueue(Beat.Emote(EmoteKind.ANGER), Beat.Act(AnimState.ANNOYED, 1.5f, Expr.ANNOYED))
            ChatAction.HAPPY -> enqueue(Beat.Act(AnimState.HAPPY, 1.2f, Expr.HAPPY))
            ChatAction.THINK -> enqueue(Beat.Emote(EmoteKind.QUESTION), Beat.Act(AnimState.THINKING, 1.2f, Expr.CURIOUS))
            else -> Unit
        }
    }

    private fun phoneInfo() = PhoneInfo(System.currentTimeMillis(), phoneState.battery, phoneState.charging, phoneActions.torchOn,
        mediaApps = runCatching { phoneActions.installedMedia().map { it.label } }.getOrDefault(emptyList()))

    /** He can't do it yet because you haven't allowed it. No pressure, just where the switch is. */
    private fun accessLine(access: String): String = when (access) {
        "write_settings" -> "I need your okay to change that. I'll open the page. Turn on \"Modify system settings\" for Pipo, then ask me again."
        "dnd" -> "I need your okay for that. I'll open the page. Switch on Pipo under \"Do Not Disturb access\", then ask me again."
        else -> "I need Notification access for that. I'll open the page. Switch Pipo on, then ask me again."
    }

    private fun handlePhone(req: PhoneRequest) {
        if (req.needsConfirm) {
            enqueue(Beat.Say(PhoneCommands.confirmQuestion(req), Sfx.BEEP, listOf(
                Choice("Yes") { doPhone(req) },
                Choice("No") { enqueue(Beat.Say(Dialogue.pick(listOf("Okay. Never mind.", "Cancelled. I didn't want to anyway."), rng), Sfx.BEEP)) },
            )))
        } else doPhone(req)
    }

    /**
     * "Find out X" / "ask ChatGPT X and tell me": he looks it up on his little phone (really asks
     * an AI), then tells you, honestly saying who he asked. He never claims ChatGPT said something
     * when it was Gemini.
     */
    private fun findOut(req: PhoneRequest) {
        if (brain === NoBrain) { enqueue(Beat.Say("My online brain is switched off. Want me to search it instead?", Sfx.HMM, listOf(
            Choice("Search") { doPhone(PhoneRequest(PhoneCmd.SEARCH, req.arg)) }, Choice("Never mind") { }))); return }
        aiThinking = true
        rig.phoneApp = null; rig.anim = AnimState.PHONE
        rig.showEmote(EmoteKind.DOTS)
        viewModelScope.launch {
            val got = withTimeoutOrNull(15_000) { com.pipo.robot.ai.Research.ask(req.arg) }
            aiThinking = false
            if (got == null) {
                enqueue(Beat.Say("The internet isn't answering me. Want me to search it instead?", Sfx.SIGH, listOf(
                    Choice("Search") { doPhone(PhoneRequest(PhoneCmd.SEARCH, req.arg)) }, Choice("Never mind") { })))
                return@launch
            }
            val (who, answer) = got
            val asked = if (req.extra.isNotBlank() && req.extra != "ai") PhoneCommands.aiName(req.extra) else null
            val intro = when {
                asked != null && !asked.equals(who, true) -> "I couldn't get into $asked, so I asked $who. It says: "
                else -> "I asked $who. It says: "
            }
            val now = System.currentTimeMillis()
            repo.mutate(notify = false) { s -> Chronicle.remember(s, MemoryType.EVENT, "you asked me to find out ${req.arg.take(60)}", 0.25f, now, "findout:${req.arg.hashCode()}") }
            enqueue(Beat.Act(AnimState.PRESENTING, 0.6f, Expr.EXCITED), Beat.Say(intro + answer, Sfx.BEEP), Beat.Act(AnimState.PROUD, 0.8f, Expr.PROUD))
        }
    }

    /** Every phone action is staged as Pipo physically doing something. */
    private fun doPhone(req: PhoneRequest) {
        val line = PhoneCommands.line(req, mood, rng, phoneInfo())
        val failLine = when (req.cmd) {
            PhoneCmd.FLASH_ON, PhoneCmd.FLASH_OFF -> "This phone has no flashlight. Or it's hiding it from me."
            PhoneCmd.VOLUME_UP, PhoneCmd.VOLUME_DOWN, PhoneCmd.MUTE -> "Android won't let me touch the volume right now."
            PhoneCmd.COPY, PhoneCmd.SHARE -> "Nothing to send yet. Say something first. Or I will."
            PhoneCmd.MEDIA_APP -> "You don't have ${MediaApps.all.firstOrNull { it.id == req.extra }?.label ?: "that"} on this phone. I looked everywhere."
            PhoneCmd.OPEN_ANY -> "I can't find an app called ${req.arg}. I looked under the bed."
            else -> "Hm. That didn't work. I don't think there's an app for that."
        }
        val exec = Beat.Do {
            val ok = runCatching { phoneActions.execute(req, lastSpoken) }.getOrDefault(false)
            val access = phoneActions.needsAccess
            if (!ok && access != null) {
                // Only missing because you haven't allowed it yet: say so, then open that exact page.
                beats.addFirst(Beat.Do { phoneActions.openAccessPage(access) })
                beats.addFirst(Beat.Say(accessLine(access), Sfx.BEEP))
            } else if (!ok) beats.addFirst(Beat.Say(failLine, Sfx.SIGH))
            else when (req.cmd) {
                PhoneCmd.CAMERA, PhoneCmd.SELFIE -> sessionFlags += "cameraOpened"
                PhoneCmd.FIND_OUT -> findOut(req)
                PhoneCmd.LOOK -> askToLook()
                PhoneCmd.MEDIA_PLAY -> viewModelScope.launch {
                    delay(1800)
                    if (!awareness.musicActive()) enqueue(
                        Beat.Say("Nothing's playing. Wait... I got it.", Sfx.BEEP),
                        Beat.Do { if (!phoneActions.openMusic()) beats.addFirst(Beat.Say(failLine, Sfx.SIGH)) })
                }
                else -> Unit
            }
            buildRoom()
        }
        val shy = repo.read { it.profile.traits.confidence < 0.35f }
        val late = hourOf(System.currentTimeMillis()).let { it >= 23 || it < 5 }
        when (req.cmd) {
            PhoneCmd.FLASH_ON -> enqueue(Beat.Act(AnimState.PROUD, 0.35f, Expr.EXCITED), exec, Beat.Emote(EmoteKind.IDEA), Beat.Say(line, Sfx.HAPPY))
            PhoneCmd.FLASH_OFF -> enqueue(exec, Beat.Say(line, Sfx.BOOP), Beat.Act(AnimState.SLEEPY, 0.6f, Expr.CONTENT))
            PhoneCmd.FLASH_STATUS -> enqueue(Beat.Do { if (phoneActions.torchOn) rig.showEmote(EmoteKind.SPARKLE) }, Beat.Say(line, Sfx.BEEP))
            PhoneCmd.MEDIA_PLAY -> enqueue(exec, Beat.Emote(EmoteKind.NOTES), Beat.Say(line, Sfx.HAPPY), Beat.Do { forceActivity(ActivityType.DANCE) })
            PhoneCmd.MEDIA_PAUSE -> enqueue(exec, Beat.Say(line, Sfx.BOOP), Beat.Act(AnimState.BORED, 0.8f, Expr.CONTENT), Beat.Do { if (activity == ActivityType.DANCE) activityEnd = clock })
            PhoneCmd.MEDIA_NEXT, PhoneCmd.MEDIA_PREV -> enqueue(exec, Beat.Act(AnimState.SPIN, 0.7f, Expr.HAPPY), Beat.Say(line, Sfx.BEEP))
            PhoneCmd.VOLUME_UP -> enqueue(exec, Beat.Act(AnimState.HOP, 0.5f, Expr.EXCITED), Beat.Say(line))
            PhoneCmd.VOLUME_DOWN, PhoneCmd.MUTE -> enqueue(exec, Beat.Act(AnimState.HIDING, 0.6f, Expr.CLOSED), Beat.Say(line))
            PhoneCmd.YOUTUBE, PhoneCmd.MUSIC_APP -> enqueue(Beat.Act(AnimState.DANCING, 1f, Expr.HAPPY), Beat.Say(line, Sfx.HAPPY), exec)
            // leaving for another app: only announce it if it's really there
            PhoneCmd.MEDIA_APP, PhoneCmd.OPEN_ANY ->
                if (!phoneActions.canOpen(req)) enqueue(Beat.Act(AnimState.LOOK_AROUND, 1f, Expr.CURIOUS), Beat.Say(failLine, Sfx.HMM))
                else enqueue(Beat.Act(if (req.cmd == PhoneCmd.MEDIA_APP) AnimState.DANCING else AnimState.HOP, 0.8f, Expr.HAPPY), Beat.Say(line, Sfx.HAPPY), exec)
            PhoneCmd.NOW_PLAYING -> enqueue(Beat.Act(AnimState.LISTENING, 0.9f, Expr.CURIOUS), Beat.Do {
                val np = phoneActions.nowPlaying()
                val access = phoneActions.needsAccess
                when {
                    np != null -> beats.addFirst(Beat.Say(Dialogue.pick(listOf("That's $np. Good taste.", "It's $np! I know this one. I think.", "$np. Dance break?"), rng), Sfx.HAPPY))
                    access != null -> { beats.addFirst(Beat.Do { phoneActions.openAccessPage(access) }); beats.addFirst(Beat.Say(accessLine(access), Sfx.BEEP)) }
                    else -> beats.addFirst(Beat.Say(Dialogue.pick(listOf("Nothing's playing. Just my humming.", "Silence. Very peaceful. Suspicious."), rng), Sfx.HMM))
                }
            })
            // device controls: act first, then say what actually happened
            PhoneCmd.BRIGHTNESS, PhoneCmd.AUTO_ROTATE, PhoneCmd.DND, PhoneCmd.RINGER -> enqueue(Beat.Act(AnimState.PROUD, 0.4f, Expr.FOCUSED), Beat.Do {
                val ok = runCatching { phoneActions.execute(req, lastSpoken) }.getOrDefault(false)
                val access = phoneActions.needsAccess
                when {
                    ok -> beats.addFirst(Beat.Say(line, Sfx.BEEP))
                    access != null -> { beats.addFirst(Beat.Do { phoneActions.openAccessPage(access) }); beats.addFirst(Beat.Say(accessLine(access), Sfx.BEEP)) }
                    else -> beats.addFirst(Beat.Say(failLine, Sfx.SIGH))
                }
            })
            PhoneCmd.CAMERA, PhoneCmd.SELFIE ->
                if (shy) enqueue(Beat.Say("A photo? Don't point it at me.", Sfx.SURPRISED), Beat.Act(AnimState.HIDING, 1f, Expr.EMBARRASSED), exec)
                else enqueue(Beat.Act(AnimState.PROUD, 0.8f, Expr.HAPPY), Beat.Say(line, Sfx.BEEP), exec)
            PhoneCmd.SHOW_PHOTO -> enqueue(Beat.Move(null), Beat.Do { rig.lookAt(0f, 0.35f, 3f) }, Beat.Say(line, Sfx.BEEP), Beat.Do { pickPhoto = true })
            PhoneCmd.TIME, PhoneCmd.DATE -> enqueue(Beat.Do { rig.lookAt(-0.8f, -0.6f, 1f) }, Beat.Act(AnimState.THINKING, 0.5f, Expr.CURIOUS), Beat.Say(line, Sfx.BEEP),
                Beat.Act(if (late) AnimState.SLEEPY else AnimState.IDLE, 0.6f, if (late) Expr.SLEEPY else Expr.CONTENT))
            PhoneCmd.CALC, PhoneCmd.CONVERT -> enqueue(Beat.Act(AnimState.THINKING, 0.8f, Expr.FOCUSED), Beat.Emote(EmoteKind.IDEA), Beat.Say(line, Sfx.BEEP), Beat.Act(AnimState.PROUD, 0.8f, Expr.PROUD))
            PhoneCmd.BATTERY -> enqueue(Beat.Do { focusCam(84f, 2.5f) }, Beat.Say(line, if (phoneState.battery <= 15 && !phoneState.charging) Sfx.SIGH else Sfx.BEEP))
            PhoneCmd.COPY -> enqueue(exec, Beat.Say(line, Sfx.BOOP))
            PhoneCmd.TIMER -> enqueue(exec, Beat.Say(line, Sfx.BEEP), Beat.Act(AnimState.PROUD, 0.6f, Expr.PROUD))
            PhoneCmd.SETTINGS ->
                if (req.blocked) enqueue(Beat.Act(AnimState.BORED, 0.5f, Expr.ANNOYED), Beat.Say(line, Sfx.SIGH), exec)
                else enqueue(Beat.Say(line, Sfx.BEEP), exec)
            else -> enqueue(Beat.Say(line, Sfx.BEEP), exec) // leaves the app: talk first, then go
        }
        val now = System.currentTimeMillis()
        repo.mutate { s ->
            if (s.count("phone:${req.cmd.name}") == 1) {
                val what = when (req.cmd) {
                    PhoneCmd.FLASH_ON -> "turned on the flashlight for you. He called it emergency sunshine"
                    PhoneCmd.YOUTUBE, PhoneCmd.MUSIC_APP, PhoneCmd.MEDIA_PLAY -> "put on music for you"
                    PhoneCmd.CAMERA, PhoneCmd.SELFIE -> "opened the camera for you"
                    PhoneCmd.TIMER -> "set a timer for you"
                    PhoneCmd.CALC -> "did math for you"
                    else -> null
                }
                if (what != null) Chronicle.journal(s, "Pipo $what", "First time. He's very proud of his new job.", JournalCategory.MOMENT, now)
            }
        }
    }

    /** Result from the system photo picker. The image is analysed locally, never stored or uploaded. */
    /** The UI should open the camera app, saving to this (private) file. */
    var lookShot by mutableStateOf<Uri?>(null)
    private var lookFile: java.io.File? = null

    /** He always asks first. The first time, he says where the photo goes. */
    private fun askToLook() {
        if (brain === NoBrain || !phoneState.online) { enqueue(Beat.Say("I can't see through the camera without the internet. My eyes need wifi. Weird, I know.", Sfx.SIGH)); return }
        val first = repo.read { !it.cooldowns.containsKey("look:consent") }
        val ask = if (first) "Can I look through your camera? It takes one photo. It goes to an online AI (Google's, or Groq's if Google is busy) so I can see it, and I don't keep it." else "Can I look? One photo!"
        enqueue(Beat.Do { rig.lookAt(0f, 0.35f, 3f) }, Beat.Act(AnimState.CURIOUS, 0.6f, Expr.EXCITED), Beat.Say(ask, Sfx.BEEP, listOf(
            Choice("Yes, look") { repo.mutate(notify = false) { it.cooldowns["look:consent"] = System.currentTimeMillis() }; openLook() },
            Choice("Not now") { enqueue(Beat.Say("Okay. My eyes are closed. Mostly.", Sfx.BOOP)) },
        )))
    }

    private fun openLook() {
        val ctx = getApplication<Application>()
        val dir = java.io.File(ctx.cacheDir, "look").apply { mkdirs() }
        val f = java.io.File(dir, "look.jpg").apply { delete() }
        lookFile = f
        lookShot = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.look", f)
    }

    /** Your camera app came back. He looks, then the photo is gone. */
    fun onLookTaken(ok: Boolean) {
        val uri = lookShot; val f = lookFile
        lookShot = null; lookFile = null
        if (!ok || uri == null) { enqueue(Beat.Say("No photo? Okay. I'll imagine something. ...a giraffe.", Sfx.BOOP)); f?.delete(); return }
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) { runCatching { decodeSmall(uri) }.getOrNull() }
            f?.delete()
            if (bmp == null) { enqueue(Beat.Say("It came out blurry. Or I did. Try again?", Sfx.SIGH)); return@launch }
            see(bmp, fromCamera = true)
        }
    }

    /**
     * He really looks (a vision model, in his voice), reacts, remembers it, and his world responds:
     * a ball means a game with Nib, food means a craving, a book means reading.
     */
    private suspend fun see(bmp: Bitmap, fromCamera: Boolean) {
        photo = bmp
        interrupt(); leaveBed(); activity = null
        aiThinking = true; rig.showEmote(EmoteKind.DOTS)
        val jpeg = withContext(Dispatchers.IO) { java.io.ByteArrayOutputStream().also { o -> scaleForVision(bmp).compress(Bitmap.CompressFormat.JPEG, 80, o) }.toByteArray() }
        val sight = withTimeoutOrNull(25_000) { com.pipo.robot.ai.Vision.look(jpeg, brainContext("Wow, let me see!", "look")) }
        aiThinking = false
        val said = sight?.line ?: Dialogue.pick(describe(bmp), rng)
        enqueue(Beat.Emote(EmoteKind.EXCLAIM), Beat.Act(AnimState.SURPRISED, 0.5f, Expr.SURPRISED), Beat.Act(AnimState.CURIOUS, 0.9f, Expr.CURIOUS),
            Beat.Say(said, Sfx.HAPPY), Beat.Act(AnimState.HAPPY, 0.8f, Expr.HAPPY))
        if (petHome) enqueue(Beat.Do { nib(PetFace.CURIOUS, Sfx.PET_CURIOUS, PetThought.QUESTION) })
        val now = System.currentTimeMillis()
        val tags = sight?.tags.orEmpty()
        repo.mutate { s ->
            val what = sight?.summary?.takeIf { it.isNotBlank() }?.lowercase()
            Experience.remember(s, MemoryType.MOMENT, if (what != null) "you showed me $what" else "you showed me a photo", 0.5f, now, "look:${what ?: "photo"}", with = listOf("you"), feeling = 0.5f)
            Rituals.shared(s, "photos", now)
            if (now - (s.cooldowns["journal:photo"] ?: 0L) > 6 * HOUR) {
                s.cooldowns["journal:photo"] = now
                Chronicle.journal(s, if (fromCamera) "Pipo looked through your camera" else "You showed Pipo a photo", (what?.let { "He saw $it. " } ?: "") + "His review: \"$said\"", JournalCategory.MOMENT, now)
            }
            // his world reacts to what he saw
            if (tags.any { it in setOf("food", "sweets", "fruit") }) FoodLife.maybeCrave(s, currentEnv(), rng, (if ("sweets" in tags) "chocolate" else if ("fruit" in tags) "fruit_salad" else "noodle_soup") to "You showed me food. Now I'm hungry. That's on you.")
            if (tags.any { it in setOf("cat", "dog", "bird", "animal") }) MoodEngine.bump(s, excitement = 0.2f, happiness = 0.1f)
        }
        when {
            tags.any { it in setOf("ball", "football", "cricket") } -> enqueue(Beat.Wait(0.8f), Beat.Say("That gives me an idea. Nib! Game time!", Sfx.HAPPY), Beat.Do {
                indoorSport = if ("cricket" in tags) com.pipo.robot.engine.Sport.CRICKET else com.pipo.robot.engine.Sport.FOOTBALL; sportChosen = true; forceActivity(ActivityType.PLAY_TOY) })
            "book" in tags -> enqueue(Beat.Wait(0.8f), Beat.Say("Books! I'm going to read mine now. Inspired.", Sfx.HAPPY), Beat.Do { forceActivity(ActivityType.READ) })
            tags.any { it in setOf("messy", "desk") } -> enqueue(Beat.Say("My workbench looks like that too. It's called a system.", Sfx.GIGGLE))
            tags.any { it in setOf("drawing") } -> enqueue(Beat.Wait(0.8f), Beat.Say("I want to draw now. Hold on.", Sfx.HAPPY), Beat.Do { forceActivity(ActivityType.DRAW) })
            tags.any { it in setOf("music") } -> enqueue(Beat.Do { forceActivity(ActivityType.DANCE) })
        }
    }

    private fun scaleForVision(b: Bitmap): Bitmap {
        val m = maxOf(b.width, b.height)
        return if (m <= 768) b else Bitmap.createScaledBitmap(b, b.width * 768 / m, b.height * 768 / m, true)
    }

    fun onPhotoPicked(uri: Uri?) {
        pickPhoto = false
        if (uri == null) { enqueue(Beat.Say("Changed your mind? Okay.", Sfx.BOOP)); return }
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) { runCatching { decodeSmall(uri) }.getOrNull() }
            if (bmp == null) { enqueue(Beat.Say(Dialogue.pick(Dialogue.brainWeird, rng) + " I couldn't see it.", Sfx.SIGH)); return@launch }
            if (brain !== NoBrain && phoneState.online) { see(bmp, fromCamera = false); return@launch }
            photo = bmp
            val pool = describe(bmp)
            interrupt(); leaveBed(); activity = null
            val said = Dialogue.pick(pool, rng)
            enqueue(Beat.Emote(EmoteKind.EXCLAIM), Beat.Act(AnimState.SURPRISED, 0.5f, Expr.SURPRISED), Beat.Act(AnimState.CURIOUS, 1.3f, Expr.CURIOUS),
                Beat.Say(said, Sfx.HAPPY), Beat.Act(AnimState.HAPPY, 1f, Expr.HAPPY), Beat.Emote(EmoteKind.HEART))
            val now = System.currentTimeMillis()
            repo.mutate { s ->
                Experience.remember(s, MemoryType.MOMENT, "you showed me a photo", 0.45f, now, "photo", with = listOf("you"), feeling = 0.5f)
                Rituals.shared(s, "photos", now)
                val last = s.cooldowns["journal:photo"] ?: 0L
                if (now - last > 12 * HOUR) {
                    s.cooldowns["journal:photo"] = now
                    Chronicle.journal(s, "You showed Pipo a photo", "His review: \"$said\"", JournalCategory.MOMENT, now)
                }
            }
        }
    }

    private fun decodeSmall(uri: Uri): Bitmap? {
        val cr = getApplication<Application>().contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
        return cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    }

    private fun describe(b: Bitmap): List<String> {
        var r = 0f; var g = 0f; var bl = 0f; var n = 0
        for (yi in 0 until 24) for (xi in 0 until 24) {
            val c = b.getPixel(xi * (b.width - 1) / 23, yi * (b.height - 1) / 23)
            r += (c shr 16 and 0xFF) / 255f; g += (c shr 8 and 0xFF) / 255f; bl += (c and 0xFF) / 255f; n++
        }
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV((r / n * 255).toInt(), (g / n * 255).toInt(), (bl / n * 255).toInt(), hsv)
        val bright = (r + g + bl) / (3f * n)
        return when {
            bright > 0.72f -> Reactions.photoBright
            bright < 0.2f -> Reactions.photoDark
            hsv[1] < 0.16f -> Reactions.photoPlain
            hsv[0] in 70f..170f -> Reactions.photoGreen
            hsv[0] in 180f..260f -> Reactions.photoBlue
            hsv[0] < 50f || hsv[0] > 330f -> Reactions.photoWarm
            else -> Reactions.photoPlain
        }
    }

    fun dismissPhoto() { photo = null }

    /** Phone shaken. Reaction depends on mood + personality. */
    private fun onShake() {
        if (!booted || dragging) return
        if (away) { if (petHome) { pet.anim = PetAnim.HOP; petAct = PetActivity.HIDE; petTarget = 20f; petRunning = true }; return }
        if (hideSpot != null && activity == ActivityType.HIDE) { found(); return }
        if (firstWakePending) { firstWake(); return }
        registerInteraction()
        shakes.addLast(clock)
        while (shakes.isNotEmpty() && shakes.first() < clock - 20f) shakes.removeFirst()
        val n = shakes.size
        val brave = repo.read { it.profile.traits.confidence > 0.5f }
        ballTarget = 200f + rng.nextFloat() * 38f
        interrupt()
        when {
            inBed -> {
                repo.mutate(notify = false) { s -> MoodEngine.bump(s, irritation = 0.1f) }
                enqueue(Beat.Do { inBed = false; activity = null }, Beat.Act(AnimState.FALLEN, 1f, Expr.SURPRISED),
                    Beat.Say(Dialogue.pick(Reactions.shakeSleepy, rng), Sfx.SLEEPY), Beat.Act(AnimState.STRETCH, 0.8f, Expr.SLEEPY))
            }
            n >= 3 -> {
                repo.mutate(notify = false) { s -> MoodEngine.bump(s, irritation = 0.15f) }
                enqueue(Beat.Emote(EmoteKind.ANGER), Beat.Act(AnimState.SHAKE, 0.6f, Expr.ANNOYED), Beat.Say(Dialogue.pick(Reactions.shakeAnnoyed, rng), Sfx.GRUMBLE))
            }
            brave && mood != Mood.GRUMPY -> {
                repo.mutate(notify = false) { s -> MoodEngine.bump(s, excitement = 0.1f, boredom = -0.1f) }
                enqueue(Beat.Act(AnimState.SHAKE, 0.7f, Expr.EXCITED), Beat.Act(AnimState.HOP, 0.5f, Expr.HAPPY), Beat.Say(Dialogue.pick(Reactions.shakeBrave, rng), Sfx.HAPPY))
            }
            else -> enqueue(Beat.Act(AnimState.FALLEN, 1.2f, Expr.SURPRISED), Beat.Emote(EmoteKind.SWEAT),
                Beat.Say(Dialogue.pick(Reactions.shakeNervous, rng), Sfx.SURPRISED), Beat.Act(AnimState.GET_UP, 1f, Expr.WORRIED), Beat.Act(AnimState.SHAKE, 0.5f, Expr.NERVOUS))
        }
    }

    fun startListening(followUp: Boolean = false) {
        if (!speech.available) {
            interrupt()
            enqueue(Beat.Say("I can't hear on this phone. My ears aren't installed.", Sfx.SIGH))
            return
        }
        registerInteraction()
        if (!followUp) { interrupt(); if (inBed) { inBed = false; activity = null } }
        voice.stop()
        listening = true
        voiceConvo = true
        heard = ""
        rig.lookAt(0f, 0.35f, 30f)
        if (sounds && !followUp) voice.synth.sfx(Sfx.BEEP)
        speech.start(
            onPartial = { heard = it },
            onLevel = { micLevel = it },
            onResult = { r ->
                listening = false
                micLevel = 0f
                if (r.isNullOrBlank()) {
                    // in the middle of a chat, silence just means you're done; only the first try gets a nudge
                    if (!followUp) enqueue(Beat.Say(Dialogue.pick(listOf("I didn't catch that.", "Hm? Say it again.", "My ears did a weird thing."), rng), Sfx.BOOP))
                    voiceConvo = false
                } else { relistenReadyAt = clock + 0.6f; sendChat(r) }
            },
        )
    }

    /** Tap the mic while he's listening = "that's it, I'm done": what you said still gets sent. */
    fun stopListening() {
        voiceConvo = false
        if (listening) { if (heard.isNotBlank()) speech.finish() else { speech.stop(); listening = false; micLevel = 0f } }
    }

    /** After he's said his answer out loud, he listens for yours. */
    private fun stepVoiceConvo() {
        if (!voiceConvo || listening || !onScreen || away) return
        if (aiThinking || cur is Beat.Say || beats.any { it is Beat.Say } || voice.speaking || clock < relistenReadyAt) { if (voice.speaking || aiThinking) relistenReadyAt = clock + 0.5f; return }
        if (navRequest != null) { voiceConvo = false; return }
        startListening(followUp = true)
    }

    fun dismissReveal() { reveal = null }

    fun micDenied() {
        interrupt()
        enqueue(Beat.Say("Okay. No ears. You can still type to me.", Sfx.BOOP), Beat.Act(AnimState.IDLE, 0.5f, Expr.CONTENT))
        openChat = true
    }

    /** You picked a game from the menu. Pipo still gets a say. */
    fun requestGame(id: String) {
        registerInteraction()
        if (away) { showText(Dialogue.pick(listOf("I'm out! Save me a game.", "Games when I'm back. Don't practise without me."), rng)); return }
        if (hideSpot != null && activity == ActivityType.HIDE) exitHide()
        interrupt(); activity = null
        val wasAsleep = inBed || firstWakePending
        if (firstWakePending) { firstWakePending = false; repo.mutate { it.profile.firstRunDone = true } }
        if (wasAsleep) enqueue(Beat.Do { inBed = false }, Beat.Act(AnimState.STRETCH, 0.9f, Expr.SLEEPY))
        val confident = repo.read { it.profile.traits.confidence > 0.5f }
        when {
            wasAsleep -> enqueue(Beat.Say("...a game? Now? ...okay. I'm awake. I'm winning.", Sfx.SLEEPY))
            mood == Mood.GRUMPY && rng.nextFloat() < 0.4f -> enqueue(Beat.Say(Dialogue.pick(Dialogue.refusals, rng), Sfx.GRUMBLE), Beat.Act(AnimState.ANNOYED, 1f, Expr.ANNOYED), Beat.Say("...one game.", Sfx.BEEP))
            else -> enqueue(Beat.Act(AnimState.HOP, 0.5f, Expr.EXCITED), Beat.Say(Dialogue.pick(if (confident) Dialogue.gameStartConfident else Dialogue.gameStartNervous, rng), Sfx.HAPPY))
        }
        enqueue(Beat.Do { navRequest = "game:$id" })
    }

    /* ================================================================ */
    /*  Phone awareness                                                  */
    /* ================================================================ */

    /**
     * Your phone buzzed (a chat/social app, and you opted in): he glances up at where
     * notifications drop in and says what KIND of thing arrived. Never who, never what it says.
     * A burst is gathered for a moment and gets one reaction; he stays quiet while asleep or busy.
     */
    private fun stepNotifs() {
        if (pendingNotifs.isEmpty()) return
        if (away || (hideSpot != null && activity == ActivityType.HIDE)) { pendingNotifs.clear(); return }
        val waited = clock - firstNotifAt
        if (waited < 1.5f) return
        val call = pendingNotifs.any { it.kind == PhoneNotifs.Kind.CALL }
        if (inBed && !call) { pendingNotifs.clear(); return }
        val busy = !booted || firstWakePending || dragging || listening || reveal != null || cur is Beat.Say || beats.isNotEmpty()
        if (busy && waited < 12f) return
        debugEvent("notif ${pendingNotifs.size} busy=$busy beats=${beats.size} cur=${cur?.javaClass?.simpleName}")
        if (busy || (!call && clock - lastNotifReact < 10f)) { pendingNotifs.clear(); return }
        val evs = pendingNotifs.toList()
        pendingNotifs.clear()
        lastNotifReact = clock
        val perky = mood != Mood.GRUMPY && mood != Mood.SLEEPY
        if (inBed) enqueue(Beat.Do { inBed = false }, Beat.Act(AnimState.STRETCH, 0.8f, Expr.SLEEPY))
        enqueue(Beat.Do { rig.lookAt(0f, -1f, 1.8f) }, Beat.Emote(EmoteKind.EXCLAIM))
        if (perky) enqueue(Beat.Act(AnimState.HOP, 0.5f, Expr.EXCITED))
        enqueue(Beat.Say(PhoneNotifs.line(evs, rng), if (perky) Sfx.SURPRISED else Sfx.BEEP))
    }

    private fun onPhoneState(raw: PhoneState) {
        val old = phoneState
        // His own voice/chirps show up as "music active": hold the previous reading while he's audible.
        val ps = if (voice.audibleRecently()) raw.copy(music = old.music) else raw
        phoneState = ps
        if (!phoneInit) { phoneInit = true; return }
        if (!booted || firstWakePending || dragging || listening || away) return
        if (clock - lastEnvReact < 20f) return
        val asleep = inBed
        when {
            ps.charging && !old.charging -> {
                interrupt()
                enqueue(Beat.Emote(EmoteKind.EXCLAIM), Beat.Say(Dialogue.pick(Dialogue.chargingStart, rng), Sfx.HAPPY))
                if (asleep) enqueue(Beat.Do { inBed = false }, Beat.Act(AnimState.STRETCH, 0.8f, Expr.SLEEPY))
                forceActivity(ActivityType.CHARGE)
            }
            ps.music && !old.music && !asleep && mood == Mood.GRUMPY ->
                enqueue(Beat.Say("Music? Now? ...fine. It's good.", Sfx.GRUMBLE), Beat.Act(AnimState.BORED, 1f, Expr.ANNOYED))
            ps.music && !old.music && !asleep -> {
                interrupt()
                enqueue(Beat.Emote(EmoteKind.NOTES), Beat.Say(Dialogue.pick(Dialogue.musicStart, rng), Sfx.HAPPY))
                forceActivity(ActivityType.DANCE)
            }
            !ps.music && old.music && !asleep && activity == ActivityType.DANCE -> {
                activityEnd = clock
                enqueue(Beat.Act(AnimState.BORED, 0.8f, Expr.SAD), Beat.Say(Dialogue.pick(Reactions.musicStopped, rng), Sfx.SIGH))
            }
            !ps.charging && old.charging && !asleep -> {
                if (activity == ActivityType.CHARGE) activityEnd = clock
                enqueue(Beat.Say(Dialogue.pick(Reactions.unplugged, rng), Sfx.BOOP))
            }
            ps.charging && ps.battery >= 100 && old.battery < 100 && !asleep ->
                enqueue(Beat.Act(AnimState.HAPPY, 1f, Expr.HAPPY), Beat.Say(Dialogue.pick(Reactions.fullBattery, rng), Sfx.HAPPY))
            ps.bluetoothAudio && !old.bluetoothAudio && !asleep && beats.isEmpty() ->
                enqueue(Beat.Emote(EmoteKind.NOTES), Beat.Say(Dialogue.pick(Reactions.bluetoothOn, rng), Sfx.BEEP))
            !ps.headphones && old.headphones && !asleep && beats.isEmpty() ->
                enqueue(Beat.Say(Dialogue.pick(Reactions.headphonesOff, rng), Sfx.BEEP))
            !ps.online && old.online && !asleep -> {
                sessionFlags += "wasOffline"
                enqueue(Beat.Act(AnimState.LOOK_AROUND, 1.2f, Expr.CURIOUS), Beat.Say(Dialogue.pick(Reactions.offline, rng), Sfx.SIGH))
            }
            ps.online && !old.online && sessionFlags.remove("wasOffline") && !asleep ->
                enqueue(Beat.Act(AnimState.HOP, 0.5f, Expr.HAPPY), Beat.Say(Dialogue.pick(Reactions.online, rng), Sfx.HAPPY))
            ps.headphones && !old.headphones && !asleep && beats.isEmpty() ->
                enqueue(Beat.Say(Dialogue.pick(Dialogue.headphonesOn, rng), Sfx.BEEP))
            ps.battery <= 15 && !ps.charging && "lowbatt" !in sessionFlags && !asleep -> {
                sessionFlags += "lowbatt"
                enqueue(Beat.Say(Dialogue.pick(Dialogue.lowBattery, rng), Sfx.SIGH))
            }
            else -> return
        }
        lastEnvReact = clock
        buildRoom()
    }

    /* ================================================================ */
    /*  Per-frame rig resolution                                         */
    /* ================================================================ */

    private fun resolveRig() {
        val c = cur
        val moving = targetX != null && !dragging
        val a = activity
        val sleepingOut = a == ActivityType.SLEEP && activityArrived && !inBed
        val anim = when {
            dragging || lift > 0.5f -> AnimState.HELD
            c is Beat.Act && c.anim != null -> c.anim
            moving -> if (carrying) AnimState.CARRYING else if (running) AnimState.RUNNING else if (sneaking) AnimState.SNEAK else AnimState.WALKING
            listening -> AnimState.LISTENING
            aiThinking -> AnimState.THINKING
            patting -> AnimState.IDLE
            inBed -> AnimState.SLEEPING
            sleepingOut -> when (sleepSpot) { SleepSpot.RUG -> AnimState.LIE_DOWN; SleepSpot.DESK -> AnimState.SITTING; else -> AnimState.SLEEPY }
            hideSpot != null && a == ActivityType.HIDE -> if (hideSpot == HideSpot.ARCADE) AnimState.IDLE else AnimState.SITTING
            a == ActivityType.PLAY_TOY && activityArrived && kickups -> if (indoorSport == com.pipo.robot.engine.Sport.FOOTBALL) AnimState.KICKUPS else AnimState.PRESENTING
            a == null && clock < readHoldUntil -> AnimState.READING
            a != null && activityArrived -> {
                val rp = restPose
                val base = if ((a == ActivityType.REST || a == ActivityType.NOTHING) && rp != null) rp else Vocab.activityAnim(a)
                if (base == AnimState.IDLE) Vocab.moodIdle(mood) else base
            }
            else -> Vocab.moodIdle(mood)
        }
        val talking = (c is Beat.Say && !sayDone) || (voice.speaking && bubble != null)
        rig.anim = if (talking && (anim == AnimState.IDLE || anim == AnimState.WAVE)) AnimState.TALKING else anim
        rig.talking = talking
        rig.expr = when {
            clock < reactUntil && reactExpr != null -> reactExpr!!
            c is Beat.Act && c.expr != null -> c.expr
            patting -> if (mood == Mood.GRUMPY && clock - patStart < 1.5f) Expr.SUSPICIOUS else Expr.LOVE
            listening -> Expr.CURIOUS
            aiThinking -> Expr.CURIOUS
            inBed && fakeSleeping -> {
                // one eye opens to check whether you're looking
                val ph = (clock % 4.6f)
                if (ph in 3.2f..4.3f) { rig.lookAt(0f, 0.35f, 0.3f); Expr.WINK } else Expr.CLOSED
            }
            inBed -> Expr.CLOSED
            sleepingOut -> Expr.CLOSED
            hideSpot != null && a == ActivityType.HIDE -> Expr.MISCHIEF
            a != null && activityArrived && !moving && restPose == AnimState.LIE_DOWN -> Expr.CONTENT
            a != null && activityArrived && !moving -> Vocab.activityExpr(a, mood)
            else -> Vocab.moodExpr(mood)
        }
        rig.speed = 0.6f + energy * 0.6f
        rig.energy = energy
        rig.mood = mood
        rig.fidgetsEnabled = c == null || c is Beat.Wait || c is Beat.Do
        rig.glow = moodGlow(mood)
        rig.eyeGlow = 0.45f + energy * 0.55f
        rig.torch = phoneActions.torchOn
        if ((inBed || sleepingOut) && hideSpot != HideSpot.BLANKET && rig.emote == null && rng.nextFloat() < 0.01f) rig.showEmote(EmoteKind.ZZZ)
    }

    fun glowColor(): Color = Color(rig.glow)
}
