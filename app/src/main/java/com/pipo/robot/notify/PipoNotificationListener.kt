package com.pipo.robot.notify

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.pipo.robot.BuildConfig
import com.pipo.robot.DebugFlags
import com.pipo.robot.engine.PhoneNotifs
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Lets Pipo notice chat/social notifications. Only active if YOU grant Notification access in
 * Android settings. Everything is decided right here, on the device:
 *  - notifications from other apps are ignored immediately;
 *  - for the supported chat apps, the text is only used to pick a coarse kind (message, reel,
 *    photo…) and is then dropped. Only (app, kind) leaves this class. Nothing is stored or logged,
 *    and Pipo never opens, replies to, or dismisses anything.
 */
class PipoNotificationListener : NotificationListenerService() {

    /** Last `when` seen per notification key, so silent re-posts/updates don't count as new. */
    private val seen = ConcurrentHashMap<String, Long>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val app = PhoneNotifs.appFor(sbn.packageName) ?: testApp(sbn.packageName) ?: return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val isCall = n.category == Notification.CATEGORY_CALL
        if (sbn.isOngoing && !isCall) return
        val stamp = n.`when`.takeIf { it > 0 } ?: sbn.postTime
        if (seen.put(sbn.key, stamp) == stamp) return
        if (seen.size > 200) seen.clear()
        val text = n.extras?.let { e ->
            listOfNotNull(e.getCharSequence(Notification.EXTRA_TEXT), e.getCharSequence(Notification.EXTRA_BIG_TEXT)).joinToString(" ")
        }.orEmpty()
        val ev = PhoneNotifs.Event(app, PhoneNotifs.classify(app, text, isCall))
        if (BuildConfig.DEBUG) android.util.Log.d("PipoNotif", "noticed ${ev.app} ${ev.kind}") // app + kind only, never content
        events.tryEmit(ev)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) { seen.remove(sbn.key) }

    /**
     * Debug builds only: with DebugFlags `notifshell=instagram`, notifications posted from ADB
     * (`adb shell cmd notification post …`, package com.android.shell) count as that app, so the
     * whole pipeline can be tested on a phone without waiting for a real message.
     */
    private fun testApp(pkg: String): PhoneNotifs.App? {
        if (!BuildConfig.DEBUG || pkg != "com.android.shell") return null
        DebugFlags.load(applicationContext)
        val name = DebugFlags["notifshell"].firstOrNull() ?: return null
        return PhoneNotifs.App.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    companion object {
        private val events = MutableSharedFlow<PhoneNotifs.Event>(extraBufferCapacity = 16)
        /** (app, kind) only. Collected by Pipo while he's on screen. */
        val bus: SharedFlow<PhoneNotifs.Event> = events.asSharedFlow()

        fun hasAccess(ctx: Context): Boolean = ctx.packageName in NotificationManagerCompat.getEnabledListenerPackages(ctx)
        fun component(ctx: Context) = ComponentName(ctx, PipoNotificationListener::class.java)
    }
}
