package com.pipo.robot.unity

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.pipo.robot.ui.home.HomeViewModel
import java.lang.reflect.Proxy

/**
 * Pipo's Unity world as a view inside the home screen (Unity as a Library, Unity 6
 * UnityPlayerForActivityOrService). The Compose chrome (chat, menu, games) stays on top.
 * One Unity player per process, kept alive and paused/resumed with the screen: Unity can't be
 * restarted in the same process, so it's never destroyed here.
 */
object UnityHost {
    private const val PLAYER = "com.unity3d.player.UnityPlayerForActivityOrService"
    private var player: Any? = null
    private var frame: FrameLayout? = null

    fun frame(activity: Activity): FrameLayout? {
        frame?.let { return it }
        return runCatching {
            val events = Class.forName("com.unity3d.player.IUnityPlayerLifecycleEvents")
            // Unity reports unload/quit here; the app keeps running either way
            val noop = Proxy.newProxyInstance(events.classLoader, arrayOf(events)) { _, _, _ -> null }
            val cls = Class.forName(PLAYER)
            player = cls.getConstructor(Context::class.java, events).newInstance(activity, noop)
            (cls.getMethod("getFrameLayout").invoke(player) as FrameLayout).also { frame = it }
        }.getOrNull()
    }

    private fun call(name: String, vararg args: Any) = runCatching {
        val p = player ?: return@runCatching
        p.javaClass.methods.first { it.name == name && it.parameterCount == args.size }.invoke(p, *args)
    }

    fun resume() { call("resume"); call("windowFocusChanged", true) }
    fun pause() { call("windowFocusChanged", false); call("pause") }
}

@Composable
fun UnityStage(vm: HomeViewModel, onFailed: (String) -> Unit) {
    val activity = LocalContext.current as? Activity
    val frame = activity?.let { UnityHost.frame(it) }
    if (frame == null) { LaunchedEffect(Unit) { onFailed("Unity player unavailable") }; return }

    AndroidView(modifier = Modifier.fillMaxSize(), factory = {
        (frame.parent as? ViewGroup)?.removeView(frame)
        frame
    })
    DisposableEffect(Unit) {
        UnityHost.resume()
        UnityBridge.listener = { e -> vm.onUnityEvent(e) }
        onDispose { UnityBridge.listener = null; UnityHost.pause(); (frame.parent as? ViewGroup)?.removeView(frame) }
    }
    // the engine's truth, ~30 times a second: the same state frame the WebView stage gets
    LaunchedEffect(Unit) {
        var n = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { }
            if (++n % 2L == 0L) UnityBridge.send(UnityBridge.stateMessage(vm.stageState()))
        }
    }
}
