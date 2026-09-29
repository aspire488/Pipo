package com.pipo.robot

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.pipo.robot.data.PipoRepository
import com.pipo.robot.notify.Notifier
import com.pipo.robot.notify.PipoWorker

class PipoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DebugFlags.load(this)
        Notifier.createChannel(this)
        PipoWorker.schedule(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> foreground = true
                Lifecycle.Event.ON_STOP -> {
                    foreground = false
                    PipoRepository.get(this).saveNow()
                }
                else -> Unit
            }
        })
    }

    companion object {
        /** True while any Pipo screen is visible. Pipo never notifies while you're looking at him. */
        @Volatile var foreground: Boolean = false
    }
}
