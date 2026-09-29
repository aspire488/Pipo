package com.pipo.robot.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pipo.robot.PipoApp
import com.pipo.robot.data.MemoryType
import com.pipo.robot.data.NotificationRecord
import com.pipo.robot.data.PipoRepository
import com.pipo.robot.data.UserResponse
import com.pipo.robot.engine.Chronicle
import com.pipo.robot.engine.MoodEngine
import com.pipo.robot.engine.NotificationDecision
import com.pipo.robot.engine.NotificationPolicy
import com.pipo.robot.engine.Personality
import com.pipo.robot.engine.Simulator
import com.pipo.robot.engine.Trait
import com.pipo.robot.engine.hourOf
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Wakes roughly hourly (Android decides exactly when), replays Pipo's elapsed life,
 * and maybe lets him send ONE message about something that actually happened.
 * No continuous background process exists.
 */
class PipoWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (PipoApp.foreground) return Result.success()
        runOnce(applicationContext)
        return Result.success()
    }

    companion object {
        /** Returns the delivered text (for the debug button), or null if Pipo stayed quiet. */
        fun runOnce(ctx: Context, ignoreForeground: Boolean = false): String? {
            val repo = PipoRepository.get(ctx)
            val now = System.currentTimeMillis()
            val rng = Random(now)
            val canPost = Notifier.canPost(ctx)
            val fg = if (ignoreForeground) false else PipoApp.foreground
            val result: Pair<NotificationDecision, Long>? = repo.mutate { s ->
                if (!s.profile.firstRunDone) return@mutate null
                Simulator.catchUp(s, now, rng)
                MoodEngine.derive(s, now, hourOf(now))
                if (!canPost) return@mutate null
                val d = NotificationPolicy.decide(s, now, fg, rng) ?: return@mutate null
                d.event.notified = true
                val rec = NotificationRecord(s.nextId(), d.event.id, d.event.type, d.text, now, delivered = true)
                s.notifications.add(rec)
                if (s.notifications.size > 60) s.notifications.removeAt(0)
                d to rec.id
            }
            repo.saveNow()
            result?.let { (d, id) -> Notifier.post(ctx, d, id) }
            return result?.first?.text
        }

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<PipoWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("pipo_life", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}

/** "NOT NOW" — Pipo takes the hint. No guilt, he just learns. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getLongExtra(Notifier.EXTRA_RECORD, 0L)
        val repo = PipoRepository.get(ctx)
        val now = System.currentTimeMillis()
        repo.mutate { s ->
            s.notifications.firstOrNull { it.id == id }?.response = UserResponse.NOT_NOW
            Personality.nudge(s, Trait.SOCIABILITY, -0.004f)
            Chronicle.remember(s, MemoryType.EVENT, "you were busy when I wanted to play", 0.25f, now, "notnow")
        }
        repo.saveNow()
        NotificationManagerCompat.from(ctx).cancel(Notifier.NOTIF_ID)
    }
}
