package com.pipo.robot.voice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import com.pipo.robot.R

/**
 * Bringing Pipo on screen from another app after you called him.
 *
 * Android stops background apps from opening themselves over what you're doing. The one fair way
 * through is "Display over other apps", which YOU grant in Android settings: with it, Pipo peeks
 * in as a small bubble for a moment (Android requires that visible window) and then opens. The
 * bubble can't be touched and can't see anything; it is only his face.
 *
 * Without that permission he can't jump up by himself, so a notification appears instead — tap it
 * and he's there with what you said.
 */
object PipoPopUp {
    private const val CHANNEL = "pipo_calls"
    private const val NOTIF_ID = 5161
    private const val PEEK_MS = 1_500L

    fun canPopUp(ctx: Context) = Settings.canDrawOverlays(ctx)

    /** Intent to Android's own "Display over other apps" switch for Pipo. */
    fun permissionIntent(ctx: Context) = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${ctx.packageName}"))

    fun open(ctx: Context, launch: Intent) {
        if (canPopUp(ctx) && peek(ctx)) {
            runCatching { ctx.startActivity(launch) }.onFailure { notify(ctx, launch) }
        } else notify(ctx, launch)
    }

    /** His face, briefly, at the bottom of the screen. False if the window couldn't be shown. */
    private fun peek(ctx: Context): Boolean {
        val wm = ctx.getSystemService(WindowManager::class.java) ?: return false
        val size = (72 * ctx.resources.displayMetrics.density).toInt()
        val face = ImageView(ctx).apply {
            setImageResource(R.mipmap.ic_launcher_round)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x33000000) }
        }
        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y = size }
        return runCatching {
            wm.addView(face, lp)
            Handler(Looper.getMainLooper()).postDelayed({ runCatching { wm.removeView(face) } }, PEEK_MS)
            true
        }.getOrDefault(false)
    }

    private fun notify(ctx: Context, launch: Intent) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "When you call Pipo", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Tap to bring Pipo up after you said “Pipo, …” (when he can't pop up by himself)."
            setSound(null, null)
        })
        val pi = PendingIntent.getActivity(ctx, 3, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_pipo)
            .setContentTitle("Pipo heard you")
            .setContentText("Tap and he's here.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setTimeoutAfter(15_000)
            .build()
        runCatching { nm.notify(NOTIF_ID, n) }
    }
}
