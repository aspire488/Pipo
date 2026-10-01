package com.pipo.robot.engine

import java.util.Calendar
import java.util.TimeZone

enum class Weather { CLEAR, CLOUDY, RAIN, WIND, FOG, STORM }

data class WeatherNow(val kind: Weather, val intensity: Float) {
    val wet get() = kind == Weather.RAIN || kind == Weather.STORM
    /** Soup weather. */
    val chilly get() = wet || kind == Weather.FOG || kind == Weather.WIND
    val niceOut get() = kind == Weather.CLEAR || (kind == Weather.CLOUDY && intensity < 0.7f)
}

/** Stable 64-bit mix (splitmix64). Same inputs → same weather on every device, every run. */
internal fun mix(a: Long, b: Long = 0L): Long {
    var z = a * -0x61c8864680b583ebL + b * 0x5851f42d4c957f2dL + 0x14057b7ef767814fL
    z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
    z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
    return z xor (z ushr 31)
}

internal fun unit(a: Long, b: Long = 0L): Float = ((mix(a, b) ushr 40).toFloat() / (1L shl 24).toFloat())

/**
 * Pipo's own weather. It isn't the real forecast (no location, no network): it's a small climate
 * of his world that changes through the day and matters — rain keeps him in, storms make him jumpy,
 * fog makes mornings quiet, and a clear day pulls him outside.
 */
object WeatherEngine {
    /** The real weather where you are (from the network), when we have a recent reading. */
    data class Live(val kind: Weather, val intensity: Float, val tempC: Float, val at: Long, val lat: Double, val place: String)

    @Volatile var live: Live? = null

    /** Your latitude, if we know it (for seasons). */
    val liveLat: Double? get() = live?.lat

    /** A real reading is trusted for this long either side of when it was taken. */
    private const val LIVE_WINDOW = 90 * 60 * 1000L

    private val dayWeights = listOf(
        Weather.CLEAR to 0.34f, Weather.CLOUDY to 0.24f, Weather.RAIN to 0.17f,
        Weather.WIND to 0.11f, Weather.FOG to 0.07f, Weather.STORM to 0.07f,
    )

    fun localDay(time: Long): Long = (time + TimeZone.getDefault().getOffset(time)) / DAY

    private fun hour(time: Long) = Calendar.getInstance().apply { timeInMillis = time }.get(Calendar.HOUR_OF_DAY)

    fun dayType(seed: Long, day: Long): Weather {
        var r = unit(seed, day * 7 + 1) * dayWeights.sumOf { it.second.toDouble() }.toFloat()
        for ((w, p) in dayWeights) { r -= p; if (r <= 0f) return w }
        return Weather.CLEAR
    }

    /** The real weather when we have a fresh reading for this moment; his own climate otherwise (offline, long ago). */
    fun at(seed: Long, time: Long): WeatherNow {
        live?.let { l -> if (kotlin.math.abs(time - l.at) < LIVE_WINDOW) return WeatherNow(l.kind, l.intensity) }
        return at(seed, localDay(time), hour(time))
    }

    fun at(seed: Long, day: Long, hour: Int): WeatherNow {
        val base = dayType(seed, day)
        val block = hour / 3
        val r = unit(seed, day * 31 + block)
        val intensity = 0.4f + 0.6f * unit(seed, day * 53 + block)
        val kind = when {
            // storms build in the afternoon and evening; the rest of a stormy day is just rain
            base == Weather.STORM -> if (hour in 13..22 && r < 0.75f) Weather.STORM else if (r < 0.85f) Weather.RAIN else Weather.CLOUDY
            // quiet foggy mornings, even on a nice day
            hour in 5..8 && (base == Weather.CLEAR || base == Weather.CLOUDY) && unit(seed, day * 17) < 0.22f -> Weather.FOG
            base == Weather.FOG -> if (hour < 12 || r < 0.4f) Weather.FOG else Weather.CLOUDY
            r < 0.72f -> base
            base == Weather.RAIN -> Weather.CLOUDY
            base == Weather.CLEAR -> if (r < 0.9f) Weather.CLOUDY else Weather.WIND
            else -> if (r < 0.88f) Weather.CLOUDY else Weather.CLEAR
        }
        return WeatherNow(kind, intensity)
    }

    fun describe(w: Weather): String = when (w) {
        Weather.CLEAR -> "clear"; Weather.CLOUDY -> "cloudy"; Weather.RAIN -> "raining"
        Weather.WIND -> "windy"; Weather.FOG -> "foggy"; Weather.STORM -> "stormy"
    }
}
