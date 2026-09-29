package com.pipo.robot.ui.render

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Per-frame colour blend for the painters. Compose's `lerp(Color, Color, Float)` converts both
 * colours to Oklab through a colour-space connector on every call; the renderer blends colours
 * dozens of times a frame (lighting, day/night, shading), and on a phone that showed up as one of
 * the hottest paths. All our colours are sRGB, so blend the packed channels directly.
 */
fun lerp(start: Color, stop: Color, fraction: Float): Color {
    val t = fraction.coerceIn(0f, 1f)
    val a = start.toArgb(); val b = stop.toArgb()
    fun ch(shift: Int): Int {
        val x = (a ushr shift) and 0xFF; val y = (b ushr shift) and 0xFF
        return (x + (y - x) * t + 0.5f).toInt() and 0xFF
    }
    return Color((ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0))
}
