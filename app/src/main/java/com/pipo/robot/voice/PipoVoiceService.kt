package com.pipo.robot.voice

import android.Manifest
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.pipo.robot.BuildConfig
import com.pipo.robot.MainActivity
import com.pipo.robot.PipoApp
import com.pipo.robot.R
import com.pipo.robot.lock.PipoLock
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/**
 * The "Pipo, ..." listener — an explicit opt-in foreground service with a notification you can
 * turn it off from. Audio rules, in plain terms:
 *
 *  - It reads the microphone itself ([AudioRecord]) and recognises the wake word ON THE PHONE
 *    with an offline model ([WakeModel]). Reading the mic requests NO audio focus and plays NO
 *    sound, so YouTube, Reels, music and calls are never paused or ducked by it. (The previous
 *    design ran the system speech recogniser in a loop; each session grabbed exclusive audio
 *    focus — pausing other apps' media — and beeped.)
 *  - WHEN it listens: screen on, phone unlocked, Pipo not on screen, no call, mic allowed.
 *    Otherwise the mic is released.
 *  - What it hears is turned into text in memory, matched against a short list of calls to Pipo
 *    ([WakeCommands]) and thrown away. Nothing is recorded to disk or sent anywhere.
 */
class PipoVoiceService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var audio: AudioManager
    @Volatile private var ears: Ears? = null
    private var model: Model? = null
    /** After acting on a call, ignore the mic briefly so one call is one action. */
    @Volatile private var quietUntil = 0L

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> { stopEars(); schedule(VoicePolicy.gapFor(BlockReason.SCREEN_OFF)) }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> schedule(400)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        PipoVoiceListener.running = true
        audio = getSystemService(AudioManager::class.java)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Pipo Voice", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shown while Pipo Voice is on, so you always know he can hear the wake word."
            setShowBadge(false)
        })
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val off = PendingIntent.getService(this, 2, Intent(this, PipoVoiceService::class.java).setAction(ACTION_OFF), PendingIntent.FLAG_IMMUTABLE)
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_pipo)
            .setContentTitle("Pipo Voice is on")
            .setContentText("Say “Pipo, …”. He listens on this phone only, while your screen is on and unlocked.")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Turn off", off)
            .build()
        runCatching {
            ServiceCompat.startForeground(
                this, NOTIF_ID, n,
                if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
            )
        }.onFailure {
            // e.g. microphone permission was revoked: do not keep a mic service we cannot declare
            PipoVoiceListener.setState(VoiceState.ERROR)
            stopSelf()
            return
        }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screen, f, RECEIVER_NOT_EXPORTED) else registerReceiver(screen, f)
        schedule(300)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_OFF) { PipoVoiceListener.setEnabled(this, false); stopSelf() }
        // Not sticky: if Android ever has to restart us, we re-arm from Settings/resume instead of
        // being dragged back up in a state we can't safely start (no mic permission, etc).
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        stopEars()
        runCatching { unregisterReceiver(screen) }
        runCatching { model?.close() }
        model = null
        if (PipoVoiceListener.state.value != VoiceState.ERROR) PipoVoiceListener.setState(VoiceState.OFF)
        PipoVoiceListener.running = false
        super.onDestroy()
    }

    /* ------------------------------------------------------------------ */

    private fun micGranted() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun inCall(): Boolean = audio.mode in intArrayOf(AudioManager.MODE_IN_CALL, AudioManager.MODE_IN_COMMUNICATION, AudioManager.MODE_RINGTONE)

    private fun schedule(delay: Long) {
        main.removeCallbacksAndMessages(null)
        main.postDelayed({ tick() }, delay)
    }

    private fun tick() {
        PipoVoiceListener.refresh()
        val pm = getSystemService(PowerManager::class.java)
        val kg = getSystemService(KeyguardManager::class.java)
        val blocked = VoicePolicy.blocked(
            enabled = PipoVoiceListener.isEnabled(this),
            micGranted = micGranted(),
            screenOn = pm.isInteractive,
            unlocked = !kg.isKeyguardLocked,
            pipoForeground = PipoApp.foreground,
            inCall = inCall(),
        )
        if (blocked == BlockReason.NO_MIC || blocked == BlockReason.OFF) {
            PipoVoiceListener.setState(if (blocked == BlockReason.NO_MIC) VoiceState.ERROR else VoiceState.OFF)
            stopSelf()
            return
        }
        if (blocked != null) {
            stopEars() // mic released
            schedule(VoicePolicy.gapFor(blocked))
            return
        }
        if (!WakeModel.installed(this)) { PipoVoiceListener.setState(VoiceState.ERROR); schedule(VoicePolicy.GAP_NO_RECOGNISER); return }
        startEars()
        schedule(VoicePolicy.GAP_RECHECK)
    }

    private fun startEars() {
        if (ears?.isAlive == true) return
        val m = model ?: runCatching { Model(WakeModel.dir(this).absolutePath) }.getOrNull()?.also { model = it }
        if (m == null) { Log.w(TAG, "wake model failed to load"); PipoVoiceListener.setState(VoiceState.ERROR); return }
        ears = Ears(m).also { it.start() }
        PipoVoiceListener.setState(VoiceState.LISTENING)
    }

    private fun stopEars() {
        ears?.let { it.running = false; runCatching { it.join(1_000) } }
        ears = null
    }

    /** The names of the apps in your launcher: what "Pipo, open ..." can be followed by. */
    private fun launcherLabels(): List<String> = runCatching {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        packageManager.queryIntentActivities(i, 0).map { it.loadLabel(packageManager).toString() }
    }.getOrDefault(emptyList())

    /** Runs on the main thread: one call to Pipo, acted on. */
    private fun act(action: WakeAction) {
        PipoVoiceListener.setState(VoiceState.TRIGGER_DETECTED)
        when (action) {
            // straight away, no need to bring him up; if the helper is off he explains on screen
            WakeAction.Lock -> if (com.pipo.robot.DebugFlags["dryrun"].contains("lock")) Log.d(TAG, "lock (dry run)")
                else if (!PipoLock.lockNow(this)) bringUp("lock my phone")
            WakeAction.Summon -> bringUp(null)
            // the app you meant, by its real name, for his normal chat brain ("open YouTube")
            is WakeAction.Open -> bringUp("open ${WakeCommands.matchApp(action.app, launcherLabels()) ?: action.app}")
            is WakeAction.Play -> bringUp(if (action.what.isBlank()) null else "play ${action.what}")
        }
        PipoVoiceListener.setState(VoiceState.PROCESSING)
    }

    /** Pipo comes on screen, with the words you said (if any) for his normal chat brain. */
    private fun bringUp(command: String?) {
        val i = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (command != null) { putExtra(EXTRA_VOICE_COMMAND, command); putExtra(EXTRA_VOICE_TOKEN, PipoVoiceListener.handoffToken) }
        }
        PipoPopUp.open(this, i)
    }

    /**
     * The microphone reader: three on-device recognisers over the same audio (his name, the
     * commands, free-form words), put together by [CallAssembler]. Each phrase is checked and
     * dropped; nothing is kept.
     */
    private inner class Ears(private val model: Model) : Thread("pipo-ears") {
        @Volatile var running = true

        override fun run() {
            val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val rec = try {
                AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, RATE))
            } catch (e: SecurityException) { return }
            if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return }
            val apps = launcherLabels()
            val appKeys = apps.map { WakeCommands.squash(it) }.toSet()
            val name = Recognizer(model, RATE.toFloat(), JSONArray(WakeCommands.SPOTTER_GRAMMAR).toString())
                .apply { setMaxAlternatives(3) } // its top guesses, so a "Pipo, lock" ranked second still counts
            val commands = Recognizer(model, RATE.toFloat(), JSONArray(WakeCommands.commandGrammar(apps)).toString())
            val words = Recognizer(model, RATE.toFloat())
            val call = CallAssembler(isInstalledApp = { heard -> WakeCommands.matchApp(heard, apps) != null || WakeCommands.squash(heard) in appKeys })
            val buf = ByteArray(3200) // 100 ms
            fun text(json: String) = JSONObject(json).optString("text")
            /** All guesses in a result, best first (results carry "alternatives" once those are on). */
            fun guesses(json: String): List<String> {
                val o = JSONObject(json)
                val alts = o.optJSONArray("alternatives") ?: return listOf(o.optString("text"))
                return (0 until alts.length()).map { alts.getJSONObject(it).optString("text") }
            }
            try {
                rec.startRecording()
                while (running) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n <= 0) continue
                    val now = System.currentTimeMillis()
                    if (now < quietUntil) { name.reset(); commands.reset(); words.reset(); call.reset(); continue }
                    if (name.acceptWaveForm(buf, n)) {
                        val guesses = guesses(name.result)
                        val h = guesses.firstOrNull().orEmpty()
                        val wake = WakeCommands.bestWake(guesses)
                        if (wake != WakeCommands.Wake.NONE) {
                            if (BuildConfig.DEBUG) Log.d(TAG, "name: $guesses ($wake)")
                            call.onName(wake, now)
                        }
                    }
                    if (commands.acceptWaveForm(buf, n)) {
                        val h = text(commands.result)
                        if (BuildConfig.DEBUG && h.isNotBlank() && h != "[unk]") Log.d(TAG, "command: \"$h\"")
                        call.onCommand(h, now)
                    }
                    if (words.acceptWaveForm(buf, n)) {
                        val h = text(words.result)
                        if (BuildConfig.DEBUG && h.isNotBlank() && h.split(" ").size <= 12) Log.d(TAG, "words: \"$h\"")
                        call.onWords(h, now)
                    }
                    val action = call.poll(now) ?: continue
                    if (BuildConfig.DEBUG) Log.d(TAG, "-> $action")
                    quietUntil = now + 2_500
                    main.post { act(action) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "listener stopped: ${e.javaClass.simpleName}")
            } finally {
                runCatching { rec.stop() }
                rec.release()
                name.close(); commands.close(); words.close()
            }
        }
    }

    companion object {
        private const val TAG = "PipoWake"
        private const val RATE = 16_000
        const val CHANNEL = "pipo_voice"
        const val NOTIF_ID = 5160
        const val ACTION_OFF = "com.pipo.robot.voice.OFF"
        /** Set on the launch intent when a command followed the wake phrase. */
        const val EXTRA_VOICE_COMMAND = "pipo_voice_command"
        /** Proves [EXTRA_VOICE_COMMAND] came from this service, not another app (see PipoVoiceListener.handoffToken). */
        const val EXTRA_VOICE_TOKEN = "pipo_voice_token"
    }
}
