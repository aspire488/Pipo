package com.pipo.robot

import android.content.Context
import java.io.File

/**
 * Debug builds only: test switches read from `debug_flags` in the app's external files dir, so
 * device test scripts can flip them without code changes:
 *
 *     echo "skip=room,light"   >  flags;  echo "nobrain=gemini" >> flags
 *     adb push flags /sdcard/Android/data/com.pipo.robot/files/debug_flags
 *
 * `skip`    render layers to leave out (room, pipo, light, fg) for cost attribution.
 * `nobrain` chat providers to treat as down (gemini, groq) to exercise the fallbacks.
 * Always empty in release builds.
 */
object DebugFlags {
    @Volatile private var values: Map<String, Set<String>> = emptyMap()

    fun load(ctx: Context) {
        if (!BuildConfig.DEBUG) return
        values = runCatching {
            File(ctx.getExternalFilesDir(null), "debug_flags").readLines()
                .mapNotNull { l -> l.split('=', limit = 2).takeIf { it.size == 2 } }
                .associate { (k, v) -> k.trim() to v.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet() }
        }.getOrDefault(emptyMap())
    }

    operator fun get(key: String): Set<String> = values[key].orEmpty()
}
