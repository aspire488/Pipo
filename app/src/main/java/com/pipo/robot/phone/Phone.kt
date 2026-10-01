package com.pipo.robot.phone

import android.app.NotificationManager
import android.app.SearchManager
import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import com.pipo.robot.engine.MediaApps
import com.pipo.robot.engine.PhoneCmd
import com.pipo.robot.notify.PipoNotificationListener
import com.pipo.robot.engine.PhoneRequest
import java.net.URLEncoder

data class PhoneState(
    val charging: Boolean = false,
    val battery: Int = 100,
    val music: Boolean = false,
    val headphones: Boolean = false,
    val bluetoothAudio: Boolean = false,
    val online: Boolean = true,
)

/** Reads safe, non-invasive phone state. Only ACCESS_NETWORK_STATE (normal, auto-granted). */
class PhoneAwareness(private val ctx: Context) {
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private val conn = ctx.getSystemService(ConnectivityManager::class.java)

    fun musicActive(): Boolean = runCatching { audio.isMusicActive }.getOrDefault(false)

    fun read(): PhoneState {
        val bi = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = bi?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = bi?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = bi?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val charging = plugged != 0 || status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else 100
        val outs = runCatching { audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type } }.getOrDefault(emptyList())
        val bt = outs.any { it == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it == 26 || it == 27 } // 26/27 = BLE headset/speaker
        val wired = outs.any { it == AudioDeviceInfo.TYPE_WIRED_HEADSET || it == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it == AudioDeviceInfo.TYPE_USB_HEADSET }
        val online = runCatching {
            conn.getNetworkCapabilities(conn.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        }.getOrDefault(true)
        return PhoneState(charging, pct, musicActive(), wired || bt, bt, online)
    }
}

/**
 * Small, explicit phone actions. Each runs only because the user asked. Nothing reads private data;
 * the camera is only opened in the camera app where the user presses the shutter; the dialer is
 * opened, never auto-called.
 */
class PhoneActions(private val ctx: Context) {
    private val camera = ctx.getSystemService(CameraManager::class.java)
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private val pm = ctx.packageManager
    @Volatile var torchOn = false
        private set
    private var cachedTorch: String? = null

    init {
        // Keep torchOn truthful even if the user toggles it from quick settings.
        runCatching {
            camera.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    if (cameraId == torchId()) torchOn = enabled
                }
            }, Handler(Looper.getMainLooper()))
        }
    }

    private fun torchId(): String? = cachedTorch ?: runCatching {
        camera.cameraIdList.firstOrNull { id ->
            val c = camera.getCameraCharacteristics(id)
            c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: camera.cameraIdList.firstOrNull { camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
    }.getOrNull().also { cachedTorch = it }

    fun setTorch(on: Boolean): Boolean {
        val id = torchId() ?: return false
        return runCatching { camera.setTorchMode(id, on); torchOn = on; true }.getOrDefault(false)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun installed(p: String) = pm.getLaunchIntentForPackage(p) != null
    private fun pkg(p: String): Intent? = pm.getLaunchIntentForPackage(p)
    private fun web(url: String) = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    private fun selector(category: String): Intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category)

    /**
     * "Play X": find the top YouTube result for X and open THAT video, so it actually starts
     * playing (opening a search page isn't playing). Falls back to the results page offline.
     */
    private fun playOnYouTube(q: String): Boolean {
        val results = "https://www.youtube.com/results?search_query=${enc(q)}"
        Thread {
            val id = runCatching {
                val c = (java.net.URL(results).openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 5000; readTimeout = 6000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                    setRequestProperty("Accept-Language", "en")
                }
                try { Regex("\"videoId\":\"([A-Za-z0-9_-]{11})\"").find(c.inputStream.bufferedReader().use { it.readText() })?.groupValues?.get(1) } finally { c.disconnect() }
            }.getOrNull()
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (id != null) launch(Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$id")).setPackage("com.google.android.youtube"), web("https://www.youtube.com/watch?v=$id"))
                else launch(web(results).setPackage("com.google.android.youtube"), web(results))
            }
        }.start()
        return true
    }

    /** Opens the AI app you named with your question filled in (shared text), else its website with the question. */
    private fun askAi(id: String, q: String): Boolean {
        val pkgs = when (id) {
            "chatgpt" -> listOf("com.openai.chatgpt"); "gemini" -> listOf("com.google.android.apps.bard", "com.google.android.googlequicksearchbox")
            "claude" -> listOf("com.anthropic.claude"); "perplexity" -> listOf("ai.perplexity.app.android"); "copilot" -> listOf("com.microsoft.copilot")
            else -> emptyList()
        }
        // These links don't just fill the box in: the site sends the question straight away.
        // (Gemini has no such link; Google's AI Mode is Gemini and answers immediately.)
        val live = when (id) {
            "chatgpt" -> "https://chatgpt.com/?q=${enc(q)}"; "claude" -> "https://claude.ai/new?q=${enc(q)}"; "perplexity" -> "https://www.perplexity.ai/search?q=${enc(q)}"
            "copilot" -> "https://copilot.microsoft.com/?q=${enc(q)}"; else -> "https://www.google.com/search?udm=50&q=${enc(q)}"
        }
        val home = when (id) { "chatgpt" -> "https://chatgpt.com"; "claude" -> "https://claude.ai"; "perplexity" -> "https://www.perplexity.ai"; "copilot" -> "https://copilot.microsoft.com"; else -> "https://gemini.google.com/app" }
        val p = pkgs.firstOrNull { installed(it) }
        if (q.isBlank()) return launch(p?.let { pkg(it) }, web(home))
        // in a browser, so the AI's own app doesn't swallow the link and drop the question
        val browser = listOf("com.android.chrome", "com.sec.android.app.sbrowser", "org.mozilla.firefox", "com.microsoft.emmx", "com.brave.browser").firstOrNull { installed(it) }
        val send = p?.let { Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, q).setPackage(it) }
        return launch(browser?.let { web(live).setPackage(it) }, web(live), send, p?.let { pkg(it) })
    }

    private fun launch(vararg options: Intent?): Boolean {
        for (i in options) {
            if (i == null) continue
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { ctx.startActivity(i); return true } catch (_: ActivityNotFoundException) { } catch (_: SecurityException) { }
        }
        return false
    }

    private fun mediaKey(code: Int) {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    /** Opens some music app: Spotify → YouTube Music → default player → YouTube. */
    fun openMusic(query: String = ""): Boolean {
        if (query.isNotBlank() && installed("com.google.android.youtube")) return playOnYouTube(query)
        // "play X": ask the music app to find it AND start it (play-from-search), not just open a search page
        fun playFromSearch(p: String) = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).setPackage(p)
            .putExtra(SearchManager.QUERY, query).putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
        if (installed("com.spotify.music")) {
            return launch(
                if (query.isNotBlank()) playFromSearch("com.spotify.music") else null,
                if (query.isNotBlank()) Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:${Uri.encode(query)}")).setPackage("com.spotify.music") else null,
                pkg("com.spotify.music"))
        }
        return launch(
            if (query.isNotBlank() && installed("com.google.android.apps.youtube.music")) playFromSearch("com.google.android.apps.youtube.music") else null,
            if (query.isBlank()) pkg("com.google.android.apps.youtube.music") else web("https://music.youtube.com/search?q=${enc(query)}").takeIf { installed("com.google.android.apps.youtube.music") },
            if (query.isBlank()) selector(Intent.CATEGORY_APP_MUSIC) else null,
            web("https://www.youtube.com/results?search_query=${enc(query.ifBlank { "music" })}").setPackage("com.google.android.youtube"),
            web("https://www.youtube.com/results?search_query=${enc(query.ifBlank { "music" })}"),
        )
    }

    fun copy(text: String): Boolean = text.isNotBlank() && runCatching {
        ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Pipo", text)); true
    }.getOrDefault(false)

    fun share(text: String): Boolean = text.isNotBlank() &&
        launch(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share"))

    private fun settingsIntents(key: String): List<Intent?> = when (key) {
        "wifi" -> listOf(if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_WIFI) else null, Intent(Settings.ACTION_WIFI_SETTINGS))
        "bluetooth" -> listOf(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        "display" -> listOf(Intent(Settings.ACTION_DISPLAY_SETTINGS))
        "sound" -> listOf(Intent(Settings.ACTION_SOUND_SETTINGS))
        "battery" -> listOf(Intent(Intent.ACTION_POWER_USAGE_SUMMARY), Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
        "notifications" -> listOf(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
        "apps" -> listOf(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS), Intent(Settings.ACTION_APPLICATION_SETTINGS))
        "datetime" -> listOf(Intent(Settings.ACTION_DATE_SETTINGS))
        "location" -> listOf(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        "airplane" -> listOf(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
        else -> emptyList()
    } + Intent(Settings.ACTION_SETTINGS)

    /** @return true if it worked. [lastLine] = what Pipo last said (for "copy that"). */
    /* ---------------- media apps, any app, and device controls ---------------- */

    /**
     * Set when an action failed only because an access isn't granted yet; he then opens the exact
     * Android page for it. Values: "write_settings", "dnd", "notification_access".
     */
    @Volatile var needsAccess: String? = null
        private set

    fun installedMedia(): List<MediaApps.App> = MediaApps.all.filter { a -> a.packages.any { installed(it) } }

    private fun openMedia(id: String, q: String): Boolean {
        val app = MediaApps.all.firstOrNull { it.id == id } ?: return false
        val p = app.packages.firstOrNull { installed(it) } ?: return false
        // music: find it and start it playing
        val play = if (q.isNotBlank() && app.kind == MediaApps.Kind.MUSIC) Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).setPackage(p)
            .putExtra(SearchManager.QUERY, q).putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*") else null
        if (play != null && launch(play)) return true
        val deep = when {
            q.isNotBlank() && app.search != null -> Intent(Intent.ACTION_VIEW, Uri.parse(MediaApps.link(app.search, q))).setPackage(p)
            q.isBlank() && app.home != null -> Intent(Intent.ACTION_VIEW, Uri.parse(app.home)).setPackage(p)
            else -> null
        }
        return launch(deep, pkg(p))
    }

    /** Would [r] find something to open? Checked before he announces it, so he never promises an app you don't have. */
    fun canOpen(r: PhoneRequest): Boolean = when (r.cmd) {
        PhoneCmd.MEDIA_APP -> installedMedia().any { it.id == r.extra }
        PhoneCmd.OPEN_ANY -> findApp(r.arg) != null
        else -> true
    }

    /** Opens an app from your launcher by (fuzzy) name: exact label, then prefix, then contains. */
    private fun openAny(name: String): Boolean = findApp(name)?.let { launch(pkg(it)) } ?: false

    private fun findApp(name: String): String? {
        fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")
        val want = norm(name)
        if (want.length < 2) return null
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName to norm(it.loadLabel(pm).toString()) }
            .filter { it.first != ctx.packageName }
        val hit = apps.firstOrNull { it.second == want } ?: apps.firstOrNull { it.second.startsWith(want) } ?: apps.firstOrNull { want.length >= 4 && it.second.contains(want) }
        return hit?.first
    }

    /** Media sessions are visible to apps with Notification access (which you may have granted Pipo). */
    private fun controllers(): List<MediaController> = runCatching {
        ctx.getSystemService(MediaSessionManager::class.java).getActiveSessions(ComponentName(ctx, PipoNotificationListener::class.java))
    }.getOrDefault(emptyList())

    private fun activeController(): MediaController? {
        val all = controllers()
        return all.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: all.firstOrNull()
    }

    /** Runs a transport action on the app that's playing. False if he can't see any (no access / nothing open). */
    private fun transport(action: (MediaController.TransportControls) -> Unit): Boolean =
        activeController()?.let { runCatching { action(it.transportControls); true }.getOrDefault(false) } ?: false

    /** "Song — Artist, on Spotify", or null if nothing is playing or he has no access. */
    fun nowPlaying(): String? {
        if (!PipoNotificationListener.hasAccess(ctx)) { needsAccess = "notification_access"; return null }
        val c = controllers().firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: return null
        val md = c.metadata ?: return null
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return null
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        val app = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(c.packageName, 0)).toString() }.getOrDefault("")
        return buildString { append("\"").append(title).append("\""); if (!artist.isNullOrBlank()) append(" by ").append(artist); if (app.isNotBlank()) append(", on ").append(app) }
    }

    private fun canWriteSettings(): Boolean {
        if (Settings.System.canWrite(ctx)) return true
        needsAccess = "write_settings"
        return false
    }

    private fun brightness(arg: String): Boolean {
        if (!canWriteSettings()) return false
        return runCatching {
            val cr = ctx.contentResolver
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            val cur = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128)
            val next = when (arg) {
                "up" -> cur + 64
                "down" -> cur - 64
                else -> (arg.toIntOrNull() ?: 50) * 255 / 100
            }.coerceIn(5, 255)
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, next)
        }.getOrDefault(false)
    }

    private fun autoRotate(on: Boolean): Boolean {
        if (!canWriteSettings()) return false
        return runCatching { Settings.System.putInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (on) 1 else 0) }.getOrDefault(false)
    }

    private fun policyAccess(): Boolean {
        if (ctx.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted) return true
        needsAccess = "dnd"
        return false
    }

    private fun dnd(on: Boolean): Boolean {
        if (!policyAccess()) return false
        return runCatching {
            ctx.getSystemService(NotificationManager::class.java).setInterruptionFilter(
                if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL)
            true
        }.getOrDefault(false)
    }

    private fun ringer(mode: String): Boolean {
        // silent (and leaving silent) needs Do Not Disturb access on modern Android
        if (!policyAccess()) return false
        return runCatching {
            audio.ringerMode = when (mode) { "vibrate" -> AudioManager.RINGER_MODE_VIBRATE; "normal" -> AudioManager.RINGER_MODE_NORMAL; else -> AudioManager.RINGER_MODE_SILENT }
            true
        }.getOrDefault(false)
    }

    /** Opens the Android page where YOU grant [access] to Pipo. */
    fun openAccessPage(access: String): Boolean = when (access) {
        "write_settings" -> launch(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}")), Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS))
        "dnd" -> launch(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        "notification_access" -> launch(
            if (Build.VERSION.SDK_INT >= 30) Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(ctx, PipoNotificationListener::class.java).flattenToString()) else null,
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        else -> false
    }

    /** What you've granted Pipo, for Settings. */
    fun accessStatus(): Map<String, Boolean> = mapOf(
        "notification_access" to PipoNotificationListener.hasAccess(ctx),
        "write_settings" to Settings.System.canWrite(ctx),
        "dnd" to ctx.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted,
    )

    fun execute(r: PhoneRequest, lastLine: String = ""): Boolean {
        needsAccess = null
        if (r.infoOnly) return true
        return when (r.cmd) {
            PhoneCmd.FLASH_ON -> setTorch(true)
            PhoneCmd.FLASH_OFF -> setTorch(false)
            // with Notification access he talks to the app that is actually playing; otherwise media keys
            PhoneCmd.MEDIA_PLAY -> { if (!transport { it.play() }) mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY); true }
            PhoneCmd.MEDIA_PAUSE -> { if (!transport { it.pause() }) mediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE); true }
            PhoneCmd.MEDIA_NEXT -> { if (!transport { it.skipToNext() }) mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT); true }
            PhoneCmd.MEDIA_PREV -> { if (!transport { it.skipToPrevious() }) mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS); true }
            PhoneCmd.MEDIA_APP -> openMedia(r.extra, r.arg)
            PhoneCmd.NOW_PLAYING -> true
            PhoneCmd.OPEN_ANY -> openAny(r.arg)
            PhoneCmd.BRIGHTNESS -> brightness(r.arg)
            PhoneCmd.AUTO_ROTATE -> autoRotate(r.arg == "on")
            PhoneCmd.DND -> dnd(r.arg == "on")
            PhoneCmd.RINGER -> ringer(r.arg)
            PhoneCmd.VOLUME_UP -> vol(AudioManager.ADJUST_RAISE)
            PhoneCmd.VOLUME_DOWN -> vol(AudioManager.ADJUST_LOWER)
            PhoneCmd.MUTE -> vol(AudioManager.ADJUST_TOGGLE_MUTE)
            PhoneCmd.YOUTUBE ->
                if (r.arg.isBlank()) launch(pkg("com.google.android.youtube"), web("https://www.youtube.com"))
                else playOnYouTube(r.arg)
            PhoneCmd.MUSIC_APP -> openMusic(r.arg)
            PhoneCmd.CAMERA -> launch(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
            PhoneCmd.SELFIE -> launch(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                .putExtra("android.intent.extras.CAMERA_FACING", 1)
                .putExtra("android.intent.extras.LENS_FACING_FRONT", 1)
                .putExtra("android.intent.extra.USE_FRONT_CAMERA", true))
            PhoneCmd.SHOW_PHOTO -> true // the UI opens the system photo picker; user chooses
            PhoneCmd.OPEN_APP -> when (r.arg) {
                "gallery" -> launch(selector(Intent.CATEGORY_APP_GALLERY), pkg("com.google.android.apps.photos"))
                "browser" -> launch(selector(Intent.CATEGORY_APP_BROWSER), web("https://www.google.com"))
                "music" -> openMusic()
                "maps" -> launch(selector(Intent.CATEGORY_APP_MAPS), Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0")))
                "clock" -> launch(Intent(AlarmClock.ACTION_SHOW_ALARMS), pkg("com.google.android.deskclock"))
                "calculator" -> launch(selector(Intent.CATEGORY_APP_CALCULATOR), pkg("com.google.android.calculator"), pkg("com.android.calculator2"))
                "calendar" -> launch(selector(Intent.CATEGORY_APP_CALENDAR))
                "settings" -> launch(Intent(Settings.ACTION_SETTINGS))
                else -> false
            }
            PhoneCmd.SETTINGS -> launch(*settingsIntents(r.extra).toTypedArray())
            PhoneCmd.TIMER -> launch(Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, r.seconds)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "Pipo timer")
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true))
            // Confirmed in chat first, and the clock app is still shown: no silent alarms.
            PhoneCmd.ALARM -> launch(Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, r.hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, r.minute)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "Pipo says wake up")
                .putExtra(AlarmClock.EXTRA_SKIP_UI, false))
            PhoneCmd.URL -> launch(web(if (r.arg.startsWith("http")) r.arg else "https://${r.arg}"))
            // A search URL opens the default browser directly; WEB_SEARCH has several handlers and shows a chooser.
            PhoneCmd.LOOK -> true // the screen asks first, then opens your camera
            PhoneCmd.YT_SEARCH -> launch(web("https://www.youtube.com/results?search_query=${enc(r.arg)}").setPackage("com.google.android.youtube"), web("https://www.youtube.com/results?search_query=${enc(r.arg)}"))
            PhoneCmd.ASK_AI -> askAi(r.extra, r.arg)
            PhoneCmd.FIND_OUT -> true // Pipo asks himself and tells you (the screen does that)
            PhoneCmd.SEARCH -> launch(web("https://www.google.com/search?q=${enc(r.arg)}"), Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, r.arg))
            PhoneCmd.MAPS -> launch(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${enc(r.arg.ifBlank { "near me" })}")), web("https://www.google.com/maps/search/${enc(r.arg)}"))
            PhoneCmd.COPY -> copy(r.arg.ifBlank { lastLine })
            PhoneCmd.SHARE -> share(r.arg.ifBlank { lastLine })
            PhoneCmd.DIAL -> {
                val digits = r.arg.filter { it.isDigit() || it == '+' }
                launch(Intent(Intent.ACTION_DIAL, Uri.parse(if (digits.length >= 3) "tel:$digits" else "tel:")))
            }
            else -> true
        }
    }

    private fun vol(dir: Int): Boolean = runCatching {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI); true
    }.getOrDefault(false) // e.g. Do Not Disturb can block this
}
