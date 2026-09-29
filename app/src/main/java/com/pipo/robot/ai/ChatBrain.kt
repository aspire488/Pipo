package com.pipo.robot.ai

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

data class BrainContext(
    val mood: String,
    val personality: String,
    val userName: String,
    val activity: String,
    val memories: List<String>,
    /** What the offline brain decided. The AI must stay consistent with it. */
    val localReply: String,
)

/**
 * Optional. The AI only rephrases/enriches dialogue. It never decides Pipo's state —
 * the local engines stay the source of truth.
 */
interface ChatBrain {
    suspend fun reply(userText: String, ctx: BrainContext): String?
}

object NoBrain : ChatBrain {
    override suspend fun reply(userText: String, ctx: BrainContext): String? = null
}

class ClaudeBrain(private val apiKey: String) : ChatBrain {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun reply(userText: String, ctx: BrainContext): String? = withContext(Dispatchers.IO) {
        try {
            val system = buildString {
                append("You are Pipo, a tiny robot who lives inside the user's phone, in a cozy little workshop. ")
                append("You are NOT an assistant. Never offer help, never say 'How can I assist', never mention being an AI model. ")
                append("You are cute, curious, playful, a bit stubborn, sometimes wrong, sometimes teasing. ")
                append("Reply in at most 2 short sentences, plain text, no emojis, no lists. ")
                append("Current mood: ${ctx.mood}. Personality: ${ctx.personality}. You are currently ${ctx.activity}. ")
                if (ctx.userName.isNotBlank()) append("The user's name is ${ctx.userName}. ")
                if (ctx.memories.isNotEmpty()) append("Things you remember: ${ctx.memories.joinToString("; ")}. ")
                append("Your instinctive reaction was: \"${ctx.localReply}\". Keep the same intent and attitude, just make it more natural.")
            }
            val body = JsonObject(mapOf(
                "model" to JsonPrimitive("claude-haiku-4-5-20251001"),
                "max_tokens" to JsonPrimitive(120),
                "system" to JsonPrimitive(system),
                "messages" to JsonArray(listOf(JsonObject(mapOf(
                    "role" to JsonPrimitive("user"),
                    "content" to JsonPrimitive(userText.take(500)),
                )))),
            )).toString()
            val conn = (URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 12000
                doOutput = true
                setRequestProperty("content-type", "application/json")
                setRequestProperty("x-api-key", apiKey)
                setRequestProperty("anthropic-version", "2023-06-01")
            }
            conn.outputStream.use { it.write(body.toByteArray()) }
            if (conn.responseCode !in 200..299) return@withContext null
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val root = json.parseToJsonElement(text).jsonObject
            root["content"]?.jsonArray
                ?.firstOrNull { it.jsonObject["type"]?.jsonPrimitive?.content == "text" }
                ?.jsonObject?.get("text")?.jsonPrimitive?.content
                ?.trim()?.takeIf { it.isNotEmpty() }?.take(240)
        } catch (_: Exception) {
            null
        }
    }
}
