package com.pipo.robot.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pipo.robot.MainActivity
import com.pipo.robot.R
import com.pipo.robot.engine.NotifAction
import com.pipo.robot.engine.NotificationDecision

object Notifier {
    const val CHANNEL = "pipo_messages"
    const val NOTIF_ID = 4242
    const val EXTRA_RECORD = "pipo_record"
    const val EXTRA_ACTION = "pipo_action"
    const val EXTRA_GAME = "pipo_game"

    fun createChannel(ctx: Context) {
        val ch = NotificationChannel(CHANNEL, "Messages from Pipo", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Pipo shares things that actually happened to him. Rarely."
        }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    fun canPost(ctx: Context): Boolean {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun activityIntent(ctx: Context, recordId: Long, action: String, game: String, req: Int): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_RECORD, recordId)
            putExtra(EXTRA_ACTION, action)
            putExtra(EXTRA_GAME, game)
        }
        return PendingIntent.getActivity(ctx, req, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    @SuppressLint("MissingPermission")
    fun post(ctx: Context, d: NotificationDecision, recordId: Long) {
        if (!canPost(ctx)) return
        val base = (recordId % 100_000).toInt() * 10
        val b = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_pipo)
            .setContentTitle("Pipo")
            .setContentText(d.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(d.text))
            .setColor(0xFF8FF5E2.toInt())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setContentIntent(activityIntent(ctx, recordId, "open", d.event.payload, base))
        for ((n, a) in d.actions.withIndex()) {
            when (a) {
                NotifAction.SEE -> b.addAction(0, "SEE PIPO", activityIntent(ctx, recordId, "see", "", base + 1 + n))
                NotifAction.PLAY -> b.addAction(0, "PLAY", activityIntent(ctx, recordId, "play", d.event.payload, base + 1 + n))
                NotifAction.NOT_NOW -> {
                    val i = Intent(ctx, NotificationActionReceiver::class.java).putExtra(EXTRA_RECORD, recordId)
                    b.addAction(0, "NOT NOW", PendingIntent.getBroadcast(ctx, base + 1 + n, i,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                }
            }
        }
        try {
            NotificationManagerCompat.from(ctx).notify(NOTIF_ID, b.build())
        } catch (_: SecurityException) {
        }
    }
}
