package com.pipo.robot.data

import android.content.Context
import com.pipo.robot.engine.Weather
import com.pipo.robot.engine.WeatherEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

/**
 * Pipo's weather is your weather. Roughly where you are comes from your network (city-level, no
 * location permission), the conditions from Open-Meteo (free, no account). Readings are cached so
 * a cold start still knows; offline, his own little climate takes over.
 */
object RealWeather {
    private const val PREFS = "pipo_weather"
    private const val REFRESH = 30 * 60 * 1000L
    private val json = Json { ignoreUnknownKeys = true }

    /** Restore the last reading (call at startup). */
    fun restore(ctx: Context) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val kind = p.getString("kind", null)?.let { runCatching { Weather.valueOf(it) }.getOrNull() } ?: return
        WeatherEngine.live = WeatherEngine.Live(kind, p.getFloat("int", 0.6f), p.getFloat("temp", 25f), p.getLong("at", 0L), p.getFloat("lat", 0f).toDouble(), p.getString("place", "").orEmpty())
    }

    /** Fetch if the last reading is old. Never throws; returns true if it updated. */
    suspend fun refresh(ctx: Context, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val last = WeatherEngine.live
        val now = System.currentTimeMillis()
        if (!force && last != null && now - last.at < REFRESH) return@withContext false
        runCatching {
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            // where (cached for a day: you don't move cities every hour)
            var lat = p.getFloat("lat", Float.NaN).toDouble(); var lon = p.getFloat("lon", Float.NaN).toDouble(); var place = p.getString("place", "").orEmpty()
            if (lat.isNaN() || now - p.getLong("locAt", 0L) > 24 * 3600 * 1000L) {
                val geo = json.parseToJsonElement(get("https://ipwho.is/?fields=latitude,longitude,city,success")).jsonObject
                lat = geo["latitude"]!!.jsonPrimitive.content.toDouble(); lon = geo["longitude"]!!.jsonPrimitive.content.toDouble()
                place = geo["city"]?.jsonPrimitive?.content.orEmpty()
                p.edit().putFloat("lat", lat.toFloat()).putFloat("lon", lon.toFloat()).putString("place", place).putLong("locAt", now).apply()
            }
            val cur = json.parseToJsonElement(get("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code,wind_speed_10m,precipitation"))
                .jsonObject["current"]!!.jsonObject
            val code = cur["weather_code"]!!.jsonPrimitive.content.toDouble().toInt()
            val wind = cur["wind_speed_10m"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            val temp = cur["temperature_2m"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 25f
            val (kind, intensity) = fromWmo(code, wind)
            WeatherEngine.live = WeatherEngine.Live(kind, intensity, temp, now, lat, place)
            p.edit().putString("kind", kind.name).putFloat("int", intensity).putFloat("temp", temp).putLong("at", now).apply()
            true
        }.getOrDefault(false)
    }

    /** WMO weather codes → his weather. */
    fun fromWmo(code: Int, windKmh: Double): Pair<Weather, Float> = when (code) {
        0, 1 -> (if (windKmh > 35) Weather.WIND else Weather.CLEAR) to (if (code == 0) 0.4f else 0.6f)
        2 -> Weather.CLOUDY to 0.5f
        3 -> Weather.CLOUDY to 0.85f
        45, 48 -> Weather.FOG to 0.8f
        51, 53, 56, 61, 66, 80 -> Weather.RAIN to 0.5f
        55, 57, 63, 65, 67, 81, 82 -> Weather.RAIN to 0.9f
        71, 73, 75, 77, 85, 86 -> Weather.CLOUDY to 0.9f // snow: his world has no snow yet, so it's a heavy grey day
        95, 96, 99 -> Weather.STORM to 0.9f
        else -> (if (windKmh > 35) Weather.WIND else Weather.CLOUDY) to 0.6f
    }

    private fun get(url: String): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 6000; readTimeout = 8000 }
        try { return c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() }
    }
}
