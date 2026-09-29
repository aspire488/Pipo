package com.pipo.robot.phone

import android.app.SearchManager
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
import com.pipo.robot.engine.PhoneCmd
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
        if (installed("com.spotify.music")) {
            return launch(
                if (query.isNotBlank()) Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:${Uri.encode(query)}")).setPackage("com.spotify.music") else null,
                pkg("com.spotify.music"))
        }
        return launch(
            if (query.isBlank()) pkg("com.google.android.apps.youtube.music") else web("https://music.youtube.com/search?q=${enc(query)}"),
            if (query.isBlank()) selector(Intent.CATEGORY_APP_MUSIC) else null,
            Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube").putExtra(SearchManager.QUERY, query.ifBlank { "music" }),
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
    fun execute(r: PhoneRequest, lastLine: String = ""): Boolean {
        if (r.infoOnly) return true
        return when (r.cmd) {
            PhoneCmd.FLASH_ON -> setTorch(true)
            PhoneCmd.FLASH_OFF -> setTorch(false)
            PhoneCmd.MEDIA_PLAY -> { mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY); true }
            PhoneCmd.MEDIA_PAUSE -> { mediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE); true }
            PhoneCmd.MEDIA_NEXT -> { mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT); true }
            PhoneCmd.MEDIA_PREV -> { mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS); true }
            PhoneCmd.VOLUME_UP -> vol(AudioManager.ADJUST_RAISE)
            PhoneCmd.VOLUME_DOWN -> vol(AudioManager.ADJUST_LOWER)
            PhoneCmd.MUTE -> vol(AudioManager.ADJUST_TOGGLE_MUTE)
            PhoneCmd.YOUTUBE ->
                if (r.arg.isBlank()) launch(pkg("com.google.android.youtube"), web("https://www.youtube.com"))
                else launch(Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube").putExtra(SearchManager.QUERY, r.arg),
                    web("https://www.youtube.com/results?search_query=${enc(r.arg)}"))
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
            PhoneCmd.SEARCH -> launch(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, r.arg), web("https://www.google.com/search?q=${enc(r.arg)}"))
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
