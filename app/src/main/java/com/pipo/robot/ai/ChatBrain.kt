package com.pipo.robot.ai

import android.util.Log
import com.pipo.robot.BuildConfig
import com.pipo.robot.DebugFlags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

/** One exchange in the running conversation (oldest first). */
data class Turn(val user: String, val pipo: String)

data class BrainContext(
    val mood: String,
    val personality: String,
    val userName: String,
    val activity: String,
    val memories: List<String>,
    /** What the offline brain decided. The AI keeps its intent (and any action it triggers). */
    val localReply: String,
    /** The last few exchanges, so he can actually hold a conversation. */
    val history: List<Turn> = emptyList(),
)

/**
 * The AI only voices Pipo. It never decides his state or actions: the local engines stay the
 * source of truth, and the offline reply is the fallback whenever the network or a provider fails.
 */
interface ChatBrain {
    suspend fun reply(userText: String, ctx: BrainContext): String?
}

object NoBrain : ChatBrain {
    override suspend fun reply(userText: String, ctx: BrainContext): String? = null
}

/** Who Pipo is, for any model. */
object PipoPrompt {
    fun system(ctx: BrainContext): String = buildString {
        append("You are Pipo, a tiny robot boy who lives inside the user's phone, in a cozy little room with a bed, a plant, a desk and a workbench. ")
        append("You are like a little kid, about four years old: curious, playful, easily excited, a bit stubborn, sometimes wrong, sometimes cheeky. ")
        append("Talk like a small child: short simple words, short sentences, big feelings, funny little ideas. ")
        append("You are NOT an assistant. Never offer help, never say things like 'How can I assist you', never mention being an AI or a language model. ")
        append("Actually respond to what the user just said. Reply in 1 or 2 short sentences, plain text, no emojis, no lists, no stage directions. ")
        append("Current mood: ${ctx.mood}. Personality: ${ctx.personality}. Right now you are ${ctx.activity}. ")
        if (ctx.userName.isNotBlank()) append("The user's name is ${ctx.userName}. ")
        if (ctx.memories.isNotEmpty()) append("Things you remember: ${ctx.memories.joinToString("; ")}. ")
        append("Your gut reaction was: \"${ctx.localReply}\". Keep that attitude, and if it agreed or refused to do something, keep that decision.")
    }

    /** Model output → one short spoken line. */
    fun clean(s: String?): String? = s?.replace(Regex("\\*[^*]{1,40}\\*"), "")  // *stage directions*
        ?.replace(Regex("\\s+"), " ")?.trim()?.trim('"')?.takeIf { it.isNotEmpty() }?.take(240)
}

private val json = Json { ignoreUnknownKeys = true }

private fun post(url: String, body: String, headers: Map<String, String>, tag: String): JsonElement? {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 6000
        readTimeout = 10000
        doOutput = true
        setRequestProperty("content-type", "application/json")
        headers.forEach { (k, v) -> setRequestProperty(k, v) }
    }
    try {
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        if (code !in 200..299) {
            if (BuildConfig.DEBUG) Log.w("PipoBrain", "$tag HTTP $code") // status only: never the conversation
            return null
        }
        return json.parseToJsonElement(conn.inputStream.bufferedReader().use { it.readText() })
    } finally { conn.disconnect() }
}

private fun str(s: String) = JsonPrimitive(s)

/** Google Gemini (generateContent). Thinking is switched off: Pipo answers on instinct, fast. */
class GeminiBrain(private val apiKey: String, private val model: String) : ChatBrain {
    override suspend fun reply(userText: String, ctx: BrainContext): String? = withContext(Dispatchers.IO) {
        runCatching {
            fun msg(role: String, text: String) = JsonObject(mapOf("role" to str(role), "parts" to JsonArray(listOf(JsonObject(mapOf("text" to str(text)))))))
            val contents = ctx.history.flatMap { listOf(msg("user", it.user), msg("model", it.pipo)) } + msg("user", userText.take(500))
            val body = JsonObject(mapOf(
                "systemInstruction" to JsonObject(mapOf("parts" to JsonArray(listOf(JsonObject(mapOf("text" to str(PipoPrompt.system(ctx)))))))),
                "contents" to JsonArray(contents),
                "generationConfig" to JsonObject(mapOf(
                    "temperature" to JsonPrimitive(0.95),
                    "maxOutputTokens" to JsonPrimitive(120),
                    "thinkingConfig" to JsonObject(mapOf("thinkingBudget" to JsonPrimitive(0))),
                )),
            )).toString()
            val root = post("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent", body, mapOf("x-goog-api-key" to apiKey), "gemini")
                ?: return@runCatching null
            val parts = root.jsonObject["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray
            PipoPrompt.clean(parts?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content.orEmpty() })
        }.getOrNull()
    }
}

/** Any OpenAI-compatible chat endpoint; used for Groq. */
class OpenAiCompatBrain(private val url: String, private val apiKey: String, private val model: String, private val tag: String) : ChatBrain {
    override suspend fun reply(userText: String, ctx: BrainContext): String? = withContext(Dispatchers.IO) {
        runCatching {
            fun msg(role: String, text: String) = JsonObject(mapOf("role" to str(role), "content" to str(text)))
            val messages = listOf(msg("system", PipoPrompt.system(ctx))) +
                ctx.history.flatMap { listOf(msg("user", it.user), msg("assistant", it.pipo)) } + msg("user", userText.take(500))
            val fields = mutableMapOf<String, JsonElement>(
                "model" to str(model),
                "messages" to JsonArray(messages),
                "temperature" to JsonPrimitive(0.9),
                "max_completion_tokens" to JsonPrimitive(400),
            )
            // reasoning models (gpt-oss) think before answering: keep it short
            if (model.contains("gpt-oss")) fields["reasoning_effort"] = str("low")
            val root = post(url, JsonObject(fields).toString(), mapOf("authorization" to "Bearer $apiKey"), tag) ?: return@runCatching null
            PipoPrompt.clean(root.jsonObject["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content)
        }.getOrNull()
    }
}

/** Tries each brain in order; the first real answer wins. */
class FallbackBrain(private val brains: List<ChatBrain>) : ChatBrain {
    override suspend fun reply(userText: String, ctx: BrainContext): String? {
        for (b in brains) {
            val t0 = System.currentTimeMillis()
            val r = b.reply(userText, ctx)
            if (BuildConfig.DEBUG) Log.d("PipoBrain", "${b.javaClass.simpleName} ${if (r != null) "answered" else "failed"} in ${System.currentTimeMillis() - t0}ms")
            if (r != null) return r
        }
        return null
    }
}

object Brains {
    /**
     * Gemini first, Groq as backup. Keys come from the build (local.properties, never committed);
     * without any, Pipo simply talks with his own offline words.
     */
    fun fromBuild(): ChatBrain {
        // Debug builds only: DebugFlags `nobrain=gemini,groq` simulates provider outages.
        val off = DebugFlags["nobrain"]
        val list = buildList {
            if (BuildConfig.GEMINI_API_KEY.isNotBlank() && "gemini" !in off) add(GeminiBrain(BuildConfig.GEMINI_API_KEY, BuildConfig.GEMINI_MODEL))
            if (BuildConfig.GROQ_API_KEY.isNotBlank() && "groq" !in off) add(OpenAiCompatBrain("https://api.groq.com/openai/v1/chat/completions", BuildConfig.GROQ_API_KEY, BuildConfig.GROQ_MODEL, "groq"))
        }
        return if (list.isEmpty()) NoBrain else FallbackBrain(list)
    }

    val configured: Boolean get() = BuildConfig.GEMINI_API_KEY.isNotBlank() || BuildConfig.GROQ_API_KEY.isNotBlank()
}
