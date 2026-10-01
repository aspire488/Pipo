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
    /** True facts about his world right now (where he is, weather, Nib, food, what happened today). */
    val world: List<String> = emptyList(),
    /** He's out and answering on his little phone. */
    val texting: Boolean = false,
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
        if (ctx.texting) append("You are out, so you're answering on your own tiny phone, like a text message. ")
        if (ctx.userName.isNotBlank()) append("The user's name is ${ctx.userName}. ")
        if (ctx.world.isNotEmpty()) append("True facts about your life right now: ${ctx.world.joinToString("; ")}. ")
        if (ctx.memories.isNotEmpty()) append("Things you remember: ${ctx.memories.joinToString("; ")}. ")
        append("These facts and memories are your real life: USE them. When asked what you did, what you have, what happened, who Nib is, what you like, answer from them with specifics (the place, the thing, when). ")
        append("Never invent places, things you own, purchases, or events that aren't listed. If something truly isn't there, don't just say 'I don't know': guess out loud like a kid, say what you DO remember that's closest, or ask them to remind you. ")
        append("Your first instinct was: \"${ctx.localReply}\". That instinct is what you are ACTUALLY about to do: if it says you're going somewhere, cooking, playing, or not doing something, say exactly that (same place, same activity) and never describe a different plan. But if the instinct was vague or didn't know something the facts answer, answer properly instead.")
    }

    /** Model output → one short spoken line. */
    fun clean(s: String?): String? = s?.replace(Regex("\\*[^*]{1,40}\\*"), "")  // *stage directions*
        ?.replace(Regex("[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{FE0F}\\x{200D}]"), "") // emoji: he talks, he doesn't send stickers
        ?.replace(Regex("\\s+"), " ")?.trim()?.trim('"')?.takeIf { it.isNotEmpty() }?.take(240)

    private val assistanty = Regex("\\b(how can i (help|assist)|as an ai|language model|i('m| am) an ai|assistant|i can help you with)\\b", RegexOption.IGNORE_CASE)
    private val claimsMemory = Regex("\\b(i remember( when)?|remember when we|that time we|last time we)\\b", RegexOption.IGNORE_CASE)

    /**
     * The AI only words the reply; it can't change who he is or what happened. A reply that sounds
     * like an assistant, or claims a shared memory he doesn't have, is thrown away and the offline
     * line is used instead.
     */
    fun guard(reply: String?, ctx: BrainContext): String? {
        val r = clean(reply) ?: return null
        if (assistanty.containsMatchIn(r)) return null
        if (claimsMemory.containsMatchIn(r) && ctx.memories.isEmpty()) return null
        return r
    }
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
class GeminiBrain(private val model: String) : ChatBrain {
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
            val root = post(AiEndpoints.geminiUrl(model), body, AiEndpoints.geminiHeaders(), "gemini")
                ?: return@runCatching null
            val parts = root.jsonObject["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray
            PipoPrompt.guard(parts?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content.orEmpty() }, ctx)
        }.getOrNull()
    }
}

/** Any OpenAI-compatible chat endpoint; used for Groq. */
class OpenAiCompatBrain(private val model: String, private val tag: String) : ChatBrain {
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
            val root = post(AiEndpoints.groqUrl(), JsonObject(fields).toString(), AiEndpoints.groqHeaders(), tag) ?: return@runCatching null
            PipoPrompt.guard(root.jsonObject["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content, ctx)
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

/**
 * When you ask Pipo to find something out, he asks a grown-up AI a plain question and then tells
 * you the gist in his own words. Returns (who answered, the answer) or null offline.
 */
object Research {
    private const val SYSTEM = "Answer the question accurately and helpfully in at most 3 short sentences. Plain text, no lists, no markdown."

    suspend fun ask(q: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        val ctx = BrainContext("", "", "", "", emptyList(), "")
        if (AiEndpoints.geminiOn) runCatching {
            val body = JsonObject(mapOf(
                "systemInstruction" to JsonObject(mapOf("parts" to JsonArray(listOf(JsonObject(mapOf("text" to str(SYSTEM))))))),
                "contents" to JsonArray(listOf(JsonObject(mapOf("role" to str("user"), "parts" to JsonArray(listOf(JsonObject(mapOf("text" to str(q.take(500)))))))))),
                "generationConfig" to JsonObject(mapOf("temperature" to JsonPrimitive(0.3), "maxOutputTokens" to JsonPrimitive(220), "thinkingConfig" to JsonObject(mapOf("thinkingBudget" to JsonPrimitive(0))))),
            )).toString()
            post(AiEndpoints.geminiUrl(BuildConfig.GEMINI_MODEL), body, AiEndpoints.geminiHeaders(), "research")
                ?.jsonObject?.get("candidates")?.jsonArray?.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray
                ?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content.orEmpty() }?.let { PipoPrompt.clean(it.replace("*", "")) }
        }.getOrNull()?.let { return@withContext "Gemini" to it }
        if (AiEndpoints.groqOn) runCatching {
            val body = JsonObject(mapOf("model" to str(BuildConfig.GROQ_MODEL), "messages" to JsonArray(listOf(
                JsonObject(mapOf("role" to str("system"), "content" to str(SYSTEM))), JsonObject(mapOf("role" to str("user"), "content" to str(q.take(500)))))),
                "temperature" to JsonPrimitive(0.3), "max_completion_tokens" to JsonPrimitive(400))).toString()
            post(AiEndpoints.groqUrl(), body, AiEndpoints.groqHeaders(), "research")
                ?.jsonObject?.get("choices")?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content?.let { PipoPrompt.clean(it.replace("*", "")) }
        }.getOrNull()?.let { return@withContext "my AI friend" to it }
        @Suppress("UNUSED_EXPRESSION") ctx
        null
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
            if (AiEndpoints.geminiOn && "gemini" !in off) add(GeminiBrain(BuildConfig.GEMINI_MODEL))
            if (AiEndpoints.groqOn && "groq" !in off) add(OpenAiCompatBrain(BuildConfig.GROQ_MODEL, "groq"))
        }
        return if (list.isEmpty()) NoBrain else FallbackBrain(list)
    }

    val configured: Boolean get() = AiEndpoints.geminiOn || AiEndpoints.groqOn
}
