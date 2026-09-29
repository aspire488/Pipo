package com.pipo.robot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.pipo.robot.data.PipoRepository
import com.pipo.robot.engine.HOUR
import com.pipo.robot.notify.PipoWorker

/**
 * Debug builds only. Runs the hourly worker now, exactly as WorkManager would, optionally after
 * pretending time passed:
 *
 *   adb shell am broadcast -n com.pipo.robot/.DebugWorkerReceiver --ei life 6 --ei you 0
 *
 * `life` = hours of his life to simulate; `you` = hours since you last interacted with him.
 */
class DebugWorkerReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val life = intent.getIntExtra("life", 0) * HOUR
        val you = intent.getIntExtra("you", -1)
        val pending = goAsync()
        Thread {
            val repo = PipoRepository.get(ctx)
            val now = System.currentTimeMillis()
            repo.mutate { s ->
                s.lastSimulatedAt -= life
                if (you >= 0) { s.lastUserInteractionAt = now - you * HOUR; s.lastSeenByUserAt = now - you * HOUR }
            }
            val msg = PipoWorker.runOnce(ctx)
            Log.d("PipoNotify", "debug run: life=${life / HOUR}h you=${you}h -> ${if (msg == null) "quiet" else "posted"}")
            pending.finish()
        }.start()
    }
}
