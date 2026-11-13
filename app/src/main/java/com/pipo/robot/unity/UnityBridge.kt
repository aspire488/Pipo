package com.pipo.robot.unity

import android.os.Handler
import android.os.Looper
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

/**
 * The app side of the Unity bridge (protocol v1, see unity/PipoWorld/Assets/Pipo/Runtime/PipoBridge.cs).
 * The app stays the authority: it sends Pipo's state, Unity renders it and reports touches back.
 * Unity is optional at build time: everything here works by name, so the app builds and runs
 * without the exported unityLibrary module (then [available] is false and the app uses its own room).
 */
object UnityBridge {
    const val VERSION = 1
    private const val PLAYER = "com.unity3d.player.UnityPlayer"

    /** One event from Unity, already validated. */
    data class Event(val type: String, val place: String?, val target: String?, val message: String?)

    @Volatile var listener: ((Event) -> Unit)? = null
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private val json = Json { ignoreUnknownKeys = true }

    val available: Boolean by lazy { runCatching { Class.forName(PLAYER) }.isSuccess }

    /** Wrap the app's state frame (the same JSON the WebView stage gets) in a v1 envelope. */
    fun stateMessage(stateJson: String) = """{"v":$VERSION,"type":"state","state":$stateJson}"""

    fun send(message: String): Boolean = runCatching {
        Class.forName(PLAYER).getMethod("UnitySendMessage", String::class.java, String::class.java, String::class.java)
            .invoke(null, "PipoBridge", "Receive", message)
        true
    }.getOrDefault(false)

    /** Called by Unity (C#: AndroidJavaClass("com.pipo.robot.unity.UnityBridge").CallStatic("onUnityEvent", json)). */
    @JvmStatic
    fun onUnityEvent(raw: String) {
        val e = parse(raw) ?: return
        main.post { listener?.invoke(e) }
    }

    /** Anything that isn't a well-formed v1 event is dropped: the renderer can't make the app do things. */
    internal fun parse(raw: String): Event? = runCatching {
        val o = json.parseToJsonElement(raw) as JsonObject
        if (o["v"]?.jsonPrimitive?.int != VERSION) return null
        val type = o["type"]?.jsonPrimitive?.content ?: return null
        if (type !in TYPES) return null
        fun s(k: String) = o[k]?.jsonPrimitive?.takeIf { it.isString }?.content?.take(200)
        Event(type, s("place"), s("target"), s("message"))
    }.getOrNull()

    private val TYPES = setOf("ready", "loading", "locationEnter", "locationExit", "navComplete", "navRequest", "interaction", "error")
}
