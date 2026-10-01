package com.pipo.robot.ai

import android.util.Base64
import android.util.Log
import com.pipo.robot.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

/** What Pipo saw: what he says, and a few plain tags his world can react to ("cat", "ball", "food"). */
data class Sight(val line: String, val tags: Set<String>, val summary: String)

/**
 * "Pipo, look!": one photo you chose to show him, seen by a vision model, answered in his voice.
 * The image is sent once and never stored. He never identifies people, guesses names, or comments
 * on how anyone looks — he can only greet them.
 */
object Vision {
    private val json = Json { ignoreUnknownKeys = true }

    /** The tags his world understands. */
    val vocabulary = listOf("cat", "dog", "bird", "animal", "ball", "football", "cricket", "food", "sweets", "fruit", "plant", "flower", "tree", "book",
        "desk", "messy", "computer", "phone", "game", "toy", "robot", "sky", "rain", "sun", "night", "classroom", "outside", "street", "room", "people", "drawing", "music", "car", "water")

    private fun prompt(ctx: BrainContext) = buildString {
        append(PipoPrompt.system(ctx))
        append(" Right now the user is showing you a photo through their phone camera, and you're seeing it. React like a curious, excited little kid seeing it for the first time: notice one or two specific things, maybe ask one small question. ")
        append("STRICT RULES about people: never try to identify anyone, never guess names, ages or who they are, never comment on anyone's looks, body, clothes or face. If there are people, just say a friendly general hi (like 'Hi, everyone!'). ")
        append("Reply ONLY as compact JSON: {\"line\": \"<what you say, 1-2 short sentences>\", \"summary\": \"<4-8 words describing the scene, no people details>\", \"tags\": [<up to 4 of: ${vocabulary.joinToString(", ")}>]}")
    }

    suspend fun look(jpeg: ByteArray, ctx: BrainContext): Sight? = withContext(Dispatchers.IO) {
        val b64 = Base64.encodeToString(jpeg, Base64.NO_WRAP)
        (if (AiEndpoints.geminiOn) runCatching { gemini(b64, ctx) }.getOrNull() else null)
            ?: (if (AiEndpoints.groqOn) runCatching { groq(b64, ctx) }.getOrNull() else null)
    }

    private fun gemini(b64: String, ctx: BrainContext): Sight? {
        fun t(s: String) = JsonPrimitive(s)
        val body = JsonObject(mapOf(
            "systemInstruction" to JsonObject(mapOf("parts" to JsonArray(listOf(JsonObject(mapOf("text" to t(prompt(ctx)))))))),
            "contents" to JsonArray(listOf(JsonObject(mapOf("role" to t("user"), "parts" to JsonArray(listOf(
                JsonObject(mapOf("inline_data" to JsonObject(mapOf("mime_type" to t("image/jpeg"), "data" to t(b64))))),
                JsonObject(mapOf("text" to t("Look! What do you see?"))),
            )))))),
            "generationConfig" to JsonObject(mapOf("temperature" to JsonPrimitive(0.9), "maxOutputTokens" to JsonPrimitive(200),
                "responseMimeType" to t("application/json"), "thinkingConfig" to JsonObject(mapOf("thinkingBudget" to JsonPrimitive(0))))),
        )).toString()
        val root = post(AiEndpoints.geminiUrl(BuildConfig.GEMINI_MODEL), body, AiEndpoints.geminiHeaders(), "vision-gemini") ?: return null
        val text = root.jsonObject["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray
            ?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content.orEmpty() } ?: return null
        return parse(text, ctx)
    }

    private fun groq(b64: String, ctx: BrainContext): Sight? {
        fun t(s: String) = JsonPrimitive(s)
        val body = JsonObject(mapOf(
            "model" to t("meta-llama/llama-4-scout-17b-16e-instruct"),
            "messages" to JsonArray(listOf(
                JsonObject(mapOf("role" to t("system"), "content" to t(prompt(ctx)))),
                JsonObject(mapOf("role" to t("user"), "content" to JsonArray(listOf(
                    JsonObject(mapOf("type" to t("text"), "text" to t("Look! What do you see?"))),
                    JsonObject(mapOf("type" to t("image_url"), "image_url" to JsonObject(mapOf("url" to t("data:image/jpeg;base64,$b64"))))),
                )))),
            )),
            "temperature" to JsonPrimitive(0.8), "max_completion_tokens" to JsonPrimitive(250),
            "response_format" to JsonObject(mapOf("type" to t("json_object"))),
        )).toString()
        val root = post(AiEndpoints.groqUrl(), body, AiEndpoints.groqHeaders(), "vision-groq") ?: return null
        val text = root.jsonObject["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content ?: return null
        return parse(text, ctx)
    }

    /** The model's JSON → what he says (through the same guard as everything else he says). */
    fun parse(text: String, ctx: BrainContext): Sight? {
        val o = runCatching { json.parseToJsonElement(text.substring(text.indexOf('{'), text.lastIndexOf('}') + 1)).jsonObject }.getOrNull() ?: return null
        val line = PipoPrompt.guard(o["line"]?.jsonPrimitive?.content, ctx.copy(memories = listOf("seeing a photo"))) ?: return null
        val tags = o["tags"]?.let { runCatching { it.jsonArray.map { t -> t.jsonPrimitive.content.lowercase().trim() } }.getOrNull() }.orEmpty().filter { it in vocabulary }.toSet()
        val summary = o["summary"]?.jsonPrimitive?.content?.take(80)?.trim().orEmpty()
        return Sight(line, tags, summary)
    }

    private fun post(url: String, body: String, headers: Map<String, String>, tag: String): kotlinx.serialization.json.JsonElement? {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 8000; readTimeout = 20000; doOutput = true
            setRequestProperty("content-type", "application/json"); headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            c.outputStream.use { it.write(body.toByteArray()) }
            if (c.responseCode !in 200..299) { if (BuildConfig.DEBUG) Log.w("PipoBrain", "$tag HTTP ${c.responseCode}"); return null }
            return json.parseToJsonElement(c.inputStream.bufferedReader().use { it.readText() })
        } finally { c.disconnect() }
    }
}
