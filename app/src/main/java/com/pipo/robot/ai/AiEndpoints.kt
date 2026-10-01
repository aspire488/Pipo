package com.pipo.robot.ai

import android.content.Context
import com.pipo.robot.BuildConfig
import java.util.UUID

/**
 * Where Pipo's AI calls go. For a store release, set PIPO_PROXY_URL: the app then talks only to
 * your own small server (see /server), which holds the real Gemini/Groq keys — nothing secret
 * ships inside the APK. Without it (local development), the app calls the providers directly with
 * the keys from local.properties.
 */
object AiEndpoints {
    private val proxy = BuildConfig.PIPO_PROXY_URL.trim().trimEnd('/')
    val usingProxy get() = proxy.isNotBlank()

    /** A random id for this install, so the proxy can rate-limit per phone. Not tied to the user. */
    @Volatile private var installId = "unknown"

    fun init(ctx: Context) {
        val p = ctx.getSharedPreferences("pipo_install", Context.MODE_PRIVATE)
        installId = p.getString("id", null) ?: UUID.randomUUID().toString().also { p.edit().putString("id", it).apply() }
    }

    val geminiOn get() = usingProxy || BuildConfig.GEMINI_API_KEY.isNotBlank()
    val groqOn get() = usingProxy || BuildConfig.GROQ_API_KEY.isNotBlank()

    private fun proxyHeaders() = mapOf("x-pipo-app" to BuildConfig.PIPO_PROXY_TOKEN, "x-pipo-install" to installId)

    fun geminiUrl(model: String) = if (usingProxy) "$proxy/gemini/$model:generateContent" else "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
    fun geminiHeaders(): Map<String, String> = if (usingProxy) proxyHeaders() else mapOf("x-goog-api-key" to BuildConfig.GEMINI_API_KEY)

    fun groqUrl() = if (usingProxy) "$proxy/groq/chat/completions" else "https://api.groq.com/openai/v1/chat/completions"
    fun groqHeaders(): Map<String, String> = if (usingProxy) proxyHeaders() else mapOf("authorization" to "Bearer ${BuildConfig.GROQ_API_KEY}")
}
