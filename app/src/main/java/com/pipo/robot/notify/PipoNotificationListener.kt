package com.pipo.robot.notify

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
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
        val app = PhoneNotifs.appFor(sbn.packageName) ?: return
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
        events.tryEmit(PhoneNotifs.Event(app, PhoneNotifs.classify(app, text, isCall)))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) { seen.remove(sbn.key) }

    companion object {
        private val events = MutableSharedFlow<PhoneNotifs.Event>(extraBufferCapacity = 16)
        /** (app, kind) only. Collected by Pipo while he's on screen. */
        val bus: SharedFlow<PhoneNotifs.Event> = events.asSharedFlow()

        fun hasAccess(ctx: Context): Boolean = ctx.packageName in NotificationManagerCompat.getEnabledListenerPackages(ctx)
        fun component(ctx: Context) = ComponentName(ctx, PipoNotificationListener::class.java)
    }
}
