package com.pipo.robot.ui.render

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import com.pipo.robot.data.DrawSubject
import com.pipo.robot.data.ItemShape
import com.pipo.robot.data.PhotoSubject
import com.pipo.robot.engine.Weather
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val wood = Color(0xFF7B5A45)
private val woodDark = Color(0xFF5E4434)
private val ink = Color(0xFF2B3342)

/** Deterministic 0..1 hash for particles (no allocation, no RNG state). */
private fun hash(i: Int, salt: Int = 0): Float {
    var x = i * 374761393 + salt * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    return ((x xor (x ushr 16)) and 0xFFFF) / 65535f
}

/** How much grey weather takes out of the daylight coming in. */
fun overcast(w: Weather, amt: Float): Float = when (w) {
    Weather.CLEAR -> 0f; Weather.WIND -> 0.05f; Weather.CLOUDY -> 0.1f + 0.1f * amt; Weather.FOG -> 0.18f
    Weather.RAIN -> 0.24f + 0.08f * amt; Weather.STORM -> 0.36f
}

fun DrawScope.drawRoom(g: SceneGeo, camU: Float, st: RoomState, t: Float, pipoInBed: Boolean) {
    val u = g.u
    val fy = g.floorY
    val day = dayFactor(st.hour)
    val night = 1f - day
    // grey days are dimmer indoors too; lightning lights everything for an instant
    val lit = (day * (1f - overcast(st.weather, st.weatherAmt)) + st.flash * 0.35f).coerceIn(0f, 1f)
    val wall = lerp(Color(0xFF26304A), Color(0xFF93AEB8), lit)
    val floor = lerp(Color(0xFF3F332E), Color(0xFF9A7459), lit)

    drawRect(Brush.verticalGradient(listOf(lerp(wall, Color.Black, 0.25f), wall), startY = 0f, endY = fy), Offset.Zero, Size(size.width, fy))
    drawRect(Brush.verticalGradient(listOf(lerp(floor, Color.Black, 0.12f), floor, lerp(floor, Color.Black, 0.35f)), startY = fy, endY = size.height), Offset(0f, fy), Size(size.width, size.height - fy))

    withTransform({ translate(-camU * u, 0f) }) {
        fun X(v: Float) = v * u
        fun Y(v: Float) = fy - v * u
        val worldW = X(SceneGeo.WORLD_W)
        // only paint what's on screen (plus a margin for things that lean): the room is ~3.4 screens wide
        fun seen(l: Float, r: Float) = r >= camU - 6f && l <= camU + g.viewU + 6f

        // ---- ceiling: mirrors the floor's perspective so the room reads as a box, not a backdrop
        val ceilY = fy - 104f * u
        if (ceilY > 0f) {
            val ceil = lerp(Color(0xFF1C2336), Color(0xFF7D95A0), day)
            drawRect(Brush.verticalGradient(listOf(lerp(ceil, Color.Black, 0.2f), ceil), startY = 0f, endY = ceilY), Offset(0f, 0f), Size(worldW, ceilY))
            val vpc = camU * u + size.width / 2f
            val spreadC = 1f + 1.7f * ceilY / (size.height - fy).coerceAtLeast(1f)
            var cx0 = -40f
            while (cx0 < SceneGeo.WORLD_W + 40f) {
                val x0 = X(cx0)
                drawLine(Color.Black.copy(alpha = 0.08f), Offset(x0, ceilY), Offset(vpc + (x0 - vpc) * spreadC, 0f), u * 0.3f)
                cx0 += 22f
            }
            // crown molding
            drawRect(lerp(wall, Color.White, 0.25f), Offset(0f, ceilY), Size(worldW, 1.6f * u))
            drawRect(Color.Black.copy(alpha = 0.12f), Offset(0f, ceilY + 1.6f * u), Size(worldW, 0.8f * u))
        }

        // ---- wall panels
        var px = 0f
        while (px < SceneGeo.WORLD_W) { drawLine(Color.Black.copy(alpha = 0.06f), Offset(X(px), 0f), Offset(X(px), fy), u * 0.4f); px += 30f }

        // ---- perspective floor: board seams converge on a vanishing point at the screen centre
        val vp = camU * u + size.width / 2f
        val spread = 2.7f
        var bx = -40f
        while (bx < SceneGeo.WORLD_W + 40f) {
            val x0 = X(bx)
            drawLine(Color.Black.copy(alpha = 0.10f), Offset(x0, fy), Offset(vp + (x0 - vp) * spread, size.height), u * 0.3f)
            bx += 11f
        }
        var gap = 3f; var py = fy + 2f * u
        while (py < size.height) { drawLine(Color.Black.copy(alpha = 0.12f), Offset(0f, py), Offset(worldW, py), u * 0.3f); gap *= 1.35f; py += gap * u }
        // ambient occlusion where wall meets floor
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.18f)), startY = fy - 8f * u, endY = fy), Offset(0f, fy - 8f * u), Size(worldW, 8f * u))
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.22f), Color.Transparent), startY = fy, endY = fy + 5f * u), Offset(0f, fy), Size(worldW, 5f * u))
        drawRect(lerp(wall, Color.Black, 0.35f), Offset(0f, fy - 2f * u), Size(worldW, 2f * u))
        drawRect(Color.White.copy(alpha = 0.05f + 0.05f * day), Offset(0f, fy - 2f * u), Size(worldW, 0.4f * u))

        // ---- contact shadows under furniture (grounds everything in the room)
        fun groundShadow(l: Float, r: Float, a: Float = 0.2f) =
            drawOval(Brush.radialGradient(listOf(Color.Black.copy(alpha = a), Color.Transparent), center = Offset(X((l + r) / 2f), fy + 0.8f * u), radius = X(r - l) / 2f),
                Offset(X(l), fy - 1.2f * u), Size(X(r - l), 4f * u))
        groundShadow(1f, 52f); groundShadow(54f, 68f, 0.25f); groundShadow(122f, 158f); groundShadow(158f, 204f); groundShadow(203f, 227f, 0.28f); groundShadow(226f, 242f)

        // ---- day: sun patch on the floor under the window, angle follows the real hour
        if (day > 0.05f) {
            val slant = ((st.hour - 13f) / 6f).coerceIn(-1f, 1f) * -24f
            val beam = Path().apply {
                moveTo(X(90f + slant * 0.3f), fy + 2f * u); lineTo(X(118f + slant * 0.3f), fy + 2f * u)
                lineTo(X(124f + slant), fy + 13f * u); lineTo(X(84f + slant), fy + 13f * u); close()
            }
            drawPath(beam, Color(0xFFFFF1C8).copy(alpha = 0.16f * day * (1f - overcast(st.weather, st.weatherAmt) * 2.4f).coerceAtLeast(0f)))
        }
        // ---- night: warm pool under the desk lamp
        if (night > 0.05f) drawOval(Brush.radialGradient(listOf(Color(0xFFFFB866).copy(alpha = 0.22f * night), Color.Transparent), center = Offset(X(146f), fy + 3f * u), radius = 26f * u),
            Offset(X(120f), fy - 2f * u), Size(52f * u, 11f * u))

        // --- clock (real time)
        run {
            val c = Offset(X(62f), Y(60f))
            rotate(if ("clock_sideways" in st.pranks) 90f else 0f, pivot = c) {
                drawCircle(Color.Black.copy(alpha = 0.15f), 5.2f * u, Offset(c.x + 0.6f * u, c.y + 0.8f * u))
                drawCircle(Color(0xFFF4EFE6), 5f * u, c)
                drawCircle(woodDark, 5f * u, c, style = Stroke(0.6f * u))
                val hr = st.hour % 12f
                val ha = Math.toRadians((hr / 12f * 360f - 90f).toDouble())
                val ma = Math.toRadians(((st.hour % 1f) * 360f - 90f).toDouble())
                drawLine(ink, c, Offset(c.x + cos(ha).toFloat() * 2.6f * u, c.y + sin(ha).toFloat() * 2.6f * u), 0.7f * u, StrokeCap.Round)
                drawLine(ink, c, Offset(c.x + cos(ma).toFloat() * 3.8f * u, c.y + sin(ma).toFloat() * 3.8f * u), 0.45f * u, StrokeCap.Round)
                drawCircle(Color.White.copy(alpha = 0.35f), 1.4f * u, Offset(c.x - 2f * u, c.y - 2f * u))
            }
        }

        // --- pennants above the bed: one for every place he's been. The wall fills up as his world grows.
        if (st.pennants.isNotEmpty() && seen(4f, 92f)) {
            val l = 8f; val r = 88f; val top = Y(84f)
            val sag = 3.5f * u
            fun wy(f: Float) = top + (1f - (2f * f - 1f) * (2f * f - 1f)) * sag
            val cord = Path().apply { for (i in 0..16) { val f = i / 16f; if (i == 0) moveTo(X(l + f * (r - l)), wy(f)) else lineTo(X(l + f * (r - l)), wy(f)) } }
            drawPath(cord, Color(0xFF5E4A3A).copy(alpha = 0.7f), style = Stroke(0.3f * u))
            val n = st.pennants.size.coerceAtMost(12)
            for (i in 0 until n) {
                val f = (i + 0.5f) / n
                val cx = X(l + f * (r - l)); val cy = wy(f)
                val sway = sin(t * 0.8f + i * 1.3f) * 0.6f * u
                val c = lerp(Color(st.pennants[i]), Color(0xFF1B2230), night * 0.45f)
                drawPath(Path().apply { moveTo(cx - 2.2f * u, cy); lineTo(cx + 2.2f * u, cy); lineTo(cx + sway, cy + 5.5f * u); close() }, c)
                drawLine(Color.White.copy(alpha = 0.35f), Offset(cx - 1.4f * u, cy + 0.9f * u), Offset(cx + 1.4f * u, cy + 0.9f * u), 0.35f * u)
            }
        }
        // --- his wall calendar: today's real date, and a doodle of today's weather in the corner
        if (seen(146f, 160f)) {
            val tl = Offset(X(148f), Y(90f)); val cw = 9f * u; val ch = 10.5f * u
            drawRect(Color.Black.copy(alpha = 0.14f), Offset(tl.x + 0.5f * u, tl.y + 0.7f * u), Size(cw, ch))
            drawRect(lerp(Color(0xFFF4EFE6), Color(0xFF2A3045), night * 0.5f), tl, Size(cw, ch))
            drawRect(lerp(Color(0xFFE0706A), Color(0xFF5A2F35), night * 0.5f), tl, Size(cw, 2.6f * u))
            drawCircle(Color(0xFF3B3F48), 0.4f * u, Offset(tl.x + cw / 2f, tl.y - 0.4f * u))
            // the day number, in chunky strokes (a 7-segment hand, like he wrote it)
            val ink2 = lerp(ink, Color(0xFFB8C0D0), night * 0.4f)
            fun digit(d: Int, ox: Float) {
                val segs = intArrayOf(0x3F, 0x06, 0x5B, 0x4F, 0x66, 0x6D, 0x7D, 0x07, 0x7F, 0x6F)[d]
                val w = 2.2f * u; val h2 = 2f * u; val y0 = tl.y + 4f * u; val sw = 0.55f * u
                fun seg(b: Int, a: Offset, c: Offset) { if (segs and (1 shl b) != 0) drawLine(ink2, a, c, sw, StrokeCap.Round) }
                val x0 = ox
                seg(0, Offset(x0, y0), Offset(x0 + w, y0)); seg(1, Offset(x0 + w, y0), Offset(x0 + w, y0 + h2)); seg(2, Offset(x0 + w, y0 + h2), Offset(x0 + w, y0 + 2 * h2))
                seg(3, Offset(x0, y0 + 2 * h2), Offset(x0 + w, y0 + 2 * h2)); seg(4, Offset(x0, y0 + h2), Offset(x0, y0 + 2 * h2)); seg(5, Offset(x0, y0), Offset(x0, y0 + h2))
                seg(6, Offset(x0, y0 + h2), Offset(x0 + w, y0 + h2))
            }
            val d = st.calDay.coerceIn(1, 31)
            if (d >= 10) { digit(d / 10, tl.x + 1.6f * u); digit(d % 10, tl.x + 5f * u) } else digit(d, tl.x + 3.4f * u)
            // weather doodle, bottom-right
            val wc = Offset(tl.x + cw - 1.6f * u, tl.y + ch - 1.6f * u)
            when (st.weather) {
                Weather.RAIN, Weather.STORM -> { drawCircle(Color(0xFF8C98A6), 0.9f * u, wc); drawLine(Color(0xFF6FA8C9), Offset(wc.x - 0.4f * u, wc.y + 0.8f * u), Offset(wc.x - 0.7f * u, wc.y + 1.4f * u), 0.25f * u) }
                Weather.CLOUDY, Weather.FOG, Weather.WIND -> drawCircle(Color(0xFF9AA4AF), 0.9f * u, wc)
                else -> drawCircle(Color(0xFFF2B33D), 0.8f * u, wc)
            }
        }

        // --- drawings Pipo made: four above his bed, two more on the wall by the plant
        if (seen(6f, 84f)) for ((i, d) in st.drawings.take(6).withIndex()) {
            val (l, tp) = if (i < 4) X(10f + i * 9f) to Y(66f - (i % 2) * 3f) else X(72f + (i - 4) * 1.5f) to Y(56f - (i - 4) * 11f)
            val rot = (hash(d.seed, 3) - 0.5f) * 6f
            rotate(rot, Offset(l + 3.5f * u, tp)) {
                drawRect(Color.Black.copy(alpha = 0.14f), Offset(l + 0.5f * u, tp + 0.7f * u), Size(7f * u, 9f * u))
                drawRect(Color(0xFFEDE3D2), Offset(l, tp), Size(7f * u, 9f * u))
                drawDoodle(d, Offset(l, tp), 7f * u, 9f * u, u)
                drawCircle(Color(0xFFE0706A), 0.35f * u, Offset(l + 3.5f * u, tp + 0.5f * u)) // the pin
            }
        }

        // --- window, with the outside on its own parallax layer
        run {
            val l = X(88f); val r = X(120f); val tp = Y(74f); val b = Y(40f)
            val skyTop: Color; val skyBot: Color
            val h = st.hour
            if (h in 16.5f..20.5f) { val d = ((h - 16.5f) / 4f).coerceIn(0f, 1f)
                skyTop = lerp(Color(0xFF8EC5E8), Color(0xFF3B3F7A), d); skyBot = lerp(Color(0xFFCFE8F2), Color(0xFFF2A07B), d) }
            else { skyTop = lerp(Color(0xFF0E1733), Color(0xFF8EC5E8), day); skyBot = lerp(Color(0xFF1D2A4F), Color(0xFFCFE8F2), day) }
            drawRect(Color.Black.copy(alpha = 0.2f), Offset(l - 1f * u, tp - 1f * u), Size(r - l + 3f * u, b - tp + 3f * u))
            drawRect(Brush.verticalGradient(listOf(skyTop, skyBot), startY = tp, endY = b), Offset(l, tp), Size(r - l, b - tp))
            // far layer: moves slower than the room (and with phone tilt) → depth through the glass
            val par = (camU - 60f) * u * 0.35f + st.tiltX * 4f * u
            val parY = st.tiltY * 2f * u
            clipRect(l, tp, r, b) {
                // distant hills / rooftops
                val hill = Path().apply {
                    moveTo(l - 20f * u + par, b)
                    var hx = -20f
                    while (hx < 60f) { lineTo(l + hx * u + par, b - (5f + 3f * sin(hx * 0.37f) + 2f * sin(hx * 0.91f)) * u + parY); hx += 3f }
                    lineTo(l + 60f * u + par, b); close()
                }
                drawPath(hill, lerp(Color(0xFF1A2440), Color(0xFF7FA7A0), lit).copy(alpha = 0.85f))
                val cloudy = when (st.weather) { Weather.CLEAR -> 0f; Weather.WIND -> 0.2f; Weather.FOG -> 0.5f; Weather.CLOUDY -> 0.55f + 0.3f * st.weatherAmt; else -> 1f }
                val windy = when (st.weather) { Weather.WIND -> 1f; Weather.STORM -> 0.9f; Weather.RAIN -> 0.3f; else -> 0.08f }
                // a small tree outside, which is how you can tell it's windy
                run {
                    val bx = l + 5f * u + par * 0.9f; val by = b
                    val sway = sin(t * (1.2f + windy * 3.5f)) * (1.5f + windy * 5f) + windy * 4f
                    drawLine(lerp(Color(0xFF1B2230), Color(0xFF5E4A3A), lit), Offset(bx, by), Offset(bx + sway * 0.1f * u, by - 9f * u), 0.9f * u)
                    // the season is in the leaves
                    val (leafA, leafB) = when (st.season) {
                        com.pipo.robot.engine.Season.SPRING -> Color(0xFF7FB27A) to Color(0xFFF4B6C8)
                        com.pipo.robot.engine.Season.MONSOON -> Color(0xFF3F8A4E) to Color(0xFF55A061)
                        com.pipo.robot.engine.Season.AUTUMN -> Color(0xFFD9822B) to Color(0xFFE8B03A)
                        com.pipo.robot.engine.Season.WINTER -> if (st.coldWinter) Color(0xFF8A7D70) to Color(0xFF9C9086) else Color(0xFF5F8F66) to Color(0xFF6E9F72)
                        else -> Color(0xFF5F8F66) to Color(0xFF6E9F72)
                    }
                    rotate(sway, Offset(bx, by - 8f * u)) {
                        if (st.season == com.pipo.robot.engine.Season.WINTER && st.coldWinter) {
                            // bare branches
                            drawLine(lerp(Color(0xFF1B2230), Color(0xFF5E4A3A), lit), Offset(bx, by - 9f * u), Offset(bx - 3f * u, by - 14f * u), 0.5f * u)
                            drawLine(lerp(Color(0xFF1B2230), Color(0xFF5E4A3A), lit), Offset(bx, by - 9f * u), Offset(bx + 3f * u, by - 13f * u), 0.5f * u)
                        } else {
                            drawCircle(lerp(Color(0xFF142030), leafA, lit), 4.2f * u, Offset(bx, by - 12f * u))
                            drawCircle(lerp(Color(0xFF18263A), leafB, lit), 3f * u, Offset(bx + 2.6f * u, by - 10f * u))
                        }
                    }
                    // autumn: now and then a leaf lets go
                    if (st.season == com.pipo.robot.engine.Season.AUTUMN) {
                        val ph = (t * 0.25f) % 1f
                        drawCircle(lerp(Color(0xFF1B2230), Color(0xFFE8B03A), lit).copy(alpha = 1f - ph), 0.5f * u, Offset(bx + ph * 6f * u + sin(t * 3f) * u, by - 11f * u + ph * 10f * u))
                    }
                }
                if (day < 0.5f) {
                    val a = (1f - day * 2f) * (1f - cloudy * 0.9f)
                    for (i in 0 until 16) {
                        val sx = l + (hash(i, 3) * 1.4f - 0.2f) * (r - l) + par * 0.5f; val sy = tp + hash(i, 7) * (b - tp) * 0.7f + parY
                        drawCircle(Color.White.copy(alpha = a * (0.4f + 0.6f * abs(sin(t * 0.7f + i)))), 0.35f * u, Offset(sx, sy))
                    }
                    val moon = Offset(l + 22f * u + par * 0.6f, tp + 8f * u + parY)
                    drawCircle(Brush.radialGradient(listOf(Color(0xFFF4EFD8).copy(alpha = 0.3f * a), Color.Transparent), center = moon, radius = 9f * u), 9f * u, moon)
                    drawCircle(Color(0xFFF4EFD8).copy(alpha = a), 3.5f * u, moon)
                    drawCircle(skyTop.copy(alpha = a), 3.2f * u, Offset(moon.x + 1.6f * u, moon.y - 1f * u))
                } else {
                    val sunA = day * (1f - cloudy * 0.85f)
                    val sun = Offset(l + 23f * u + par * 0.6f, tp + 9f * u + parY)
                    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE6A8).copy(alpha = 0.5f * sunA), Color.Transparent), center = sun, radius = 10f * u), 10f * u, sun)
                    drawCircle(Color(0xFFFFE6A8).copy(alpha = sunA), 4f * u, sun)
                    val nClouds = 1 + (cloudy * 4f).toInt()
                    val cloudCol = lerp(Color.White, Color(0xFF7D8894), (cloudy - 0.5f).coerceAtLeast(0f) * 1.6f)
                    for (k in 0 until nClouds) {
                        val speed = 0.6f + windy * 2.4f
                        val cx = l + ((t * speed + k * 13f + hash(k, 41) * 9f) % 50f - 10f) * u + par * 0.8f
                        val cy = tp + (8f + hash(k, 43) * 16f) * u + parY
                        for ((dx, rr) in listOf(0f to 3f, 3f to 3.8f, 6.5f to 2.8f)) drawCircle(cloudCol.copy(alpha = 0.85f * day), rr * u * (0.8f + cloudy * 0.5f), Offset(cx + dx * u, cy))
                    }
                    if (st.weather == Weather.CLEAR || st.weather == Weather.CLOUDY || st.weather == Weather.WIND) Critters.bird(t, if (st.feeder) 18f else 47f)?.let { pr ->
                        val bxw = X(Critters.birdX(pr)) + par * 0.2f; val byw = tp + (12f + sin(pr * 9f) * 2f) * u
                        val flap = sin(t * 18f) * 1.2f * u
                        val bird = Path().apply { moveTo(bxw - 1.8f * u, byw - flap); quadraticBezierTo(bxw - 0.8f * u, byw - 0.4f * u, bxw, byw); quadraticBezierTo(bxw + 0.8f * u, byw - 0.4f * u, bxw + 1.8f * u, byw - flap) }
                        drawPath(bird, ink.copy(alpha = 0.8f), style = Stroke(0.4f * u, cap = StrokeCap.Round))
                    }
                }
                // weather on the far side of the glass
                if (st.weather == Weather.FOG) drawRect(Brush.verticalGradient(listOf(Color(0xFFD8DDE0).copy(alpha = 0.35f + 0.25f * day), Color(0xFFD8DDE0).copy(alpha = 0.7f)), startY = tp, endY = b), Offset(l, tp), Size(r - l, b - tp))
                if (st.flash > 0f) drawRect(Color(0xFFEFF4FF).copy(alpha = 0.75f * st.flash), Offset(l, tp), Size(r - l, b - tp))
                // …and on this side: raindrops racing down the glass
                if (st.weather == Weather.RAIN || st.weather == Weather.STORM) {
                    val n = if (st.weather == Weather.STORM) 26 else (10 + st.weatherAmt * 12).toInt()
                    for (i in 0 until n) {
                        val speed = 9f + hash(i, 13) * 8f
                        val ph = (t * speed / 30f + hash(i, 17)) % 1f
                        val rx = l + hash(i, 19) * (r - l) - ph * 2.2f * u
                        val ry = tp + ph * (b - tp)
                        drawLine(Color(0xFFCFE3F2).copy(alpha = 0.55f), Offset(rx, ry), Offset(rx - 0.6f * u, ry + 2.2f * u), 0.25f * u, StrokeCap.Round)
                    }
                    for (i in 0 until 7) drawCircle(Color(0xFFE3F0FA).copy(alpha = 0.35f), (0.3f + hash(i, 23) * 0.3f) * u, Offset(l + hash(i, 29) * (r - l), tp + hash(i, 31) * (b - tp)))
                }
                // glass reflection
                drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), start = Offset(l, tp), end = Offset(l + 12f * u, tp + 16f * u)), Offset(l, tp), Size(r - l, b - tp))
            }
            // things he built for the window
            if (st.feeder) drawItem(ItemShape.FEEDER, Offset(X(113f), b - 2.4f * u), 6f * u)
            if (st.telescope) drawItem(ItemShape.TELESCOPE, Offset(X(91f), b - 3f * u), 7f * u)
            drawRect(Color(0xFFEDE6DA), Offset(l, tp), Size(r - l, b - tp), style = Stroke(1.2f * u))
            drawLine(Color(0xFFEDE6DA), Offset((l + r) / 2, tp), Offset((l + r) / 2, b), 0.8f * u)
            drawLine(Color(0xFFEDE6DA), Offset(l, (tp + b) / 2), Offset(r, (tp + b) / 2), 0.8f * u)
            drawRect(Color(0xFFD8CFC2), Offset(l - 2f * u, b), Size(r - l + 4f * u, 1.6f * u))
            drawRect(Color.Black.copy(alpha = 0.15f), Offset(l - 2f * u, b + 1.6f * u), Size(r - l + 4f * u, 0.8f * u))
            val gust = when (st.weather) { Weather.WIND -> 1f; Weather.STORM -> 0.8f; else -> 0f }
            val sway = sin(t * (0.5f + gust * 1.3f)) * (0.4f + gust * 1.1f) * u + gust * sin(t * 3.7f) * 0.3f * u
            drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFFB8A998), Color(0xFFD4C6B6), Color(0xFFB8A998)), startX = l - 4f * u, endX = l + 1f * u),
                Offset(l - 4f * u + sway, tp - 2f * u), Size(5f * u, b - tp + 6f * u), CornerRadius(2f * u))
            drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFFB8A998), Color(0xFFD4C6B6), Color(0xFFB8A998)), startX = r - 1f * u, endX = r + 4f * u),
                Offset(r - 1f * u - sway, tp - 2f * u), Size(5f * u, b - tp + 6f * u), CornerRadius(2f * u))
            drawLine(woodDark, Offset(l - 6f * u, tp - 2f * u), Offset(r + 6f * u, tp - 2f * u), 0.8f * u, StrokeCap.Round)
        }

        // --- TV + game console on a low stand under the window
        run {
            val l = SceneGeo.TV_L; val r = SceneGeo.TV_R; val cx = (l + r) / 2f
            drawRect(Color.Black.copy(alpha = 0.18f), Offset(X(l - 0.5f), fy - 0.6f * u), Size((r - l + 1f) * u, 1.4f * u))
            drawRoundRect(Brush.verticalGradient(listOf(lerp(wood, Color.White, 0.08f), woodDark), startY = Y(5f), endY = fy), Offset(X(l), Y(5f)), Size((r - l) * u, 5f * u), CornerRadius(0.6f * u))
            drawRect(Color.Black.copy(alpha = 0.18f), Offset(X(l + 1f), Y(3.6f)), Size((r - l - 2f) * u, 2.2f * u))
            // console box on the shelf, light strip on when he plays
            drawRoundRect(Color(0xFF20242F), Offset(X(l + 1.6f), Y(3.3f)), Size(6.5f * u, 1.7f * u), CornerRadius(0.3f * u))
            drawRect((if (st.consoleActive) Color(0xFF5AB8FF) else Color(0xFF3A4252)).copy(alpha = if (st.consoleActive) 0.7f + 0.3f * sin(t * 3f) else 1f),
                Offset(X(l + 2f), Y(2.4f)), Size(5.7f * u, 0.25f * u))
            // TV: thin bezel, little feet
            drawRect(Color(0xFF14171F), Offset(X(cx - 0.8f), Y(6f)), Size(1.6f * u, 1f * u))
            drawRoundRect(Color(0xFF14171F), Offset(X(l + 0.3f), Y(15.5f)), Size((r - l - 0.6f) * u, 9.6f * u), CornerRadius(0.6f * u))
            val sl = X(l + 0.9f); val st0 = Y(15f); val sw = (r - l - 1.8f) * u; val sh = 8.6f * u
            if (st.consoleActive) clipRect(sl, st0, sl + sw, st0 + sh) {
                // a tiny platformer: sky, hills scrolling, a hero hopping over blocks, coins
                drawRect(Brush.verticalGradient(listOf(Color(0xFF3E6FD8), Color(0xFF8FC3FF)), startY = st0, endY = st0 + sh), Offset(sl, st0), Size(sw, sh))
                val scroll = (t * 2.2f) % 6f
                for (i in -1..3) drawCircle(Color(0xFF58B368), 2.2f * u, Offset(sl + (i * 6f - scroll) * u + 1f * u, st0 + sh))
                drawRect(Color(0xFF8A5A3C), Offset(sl, st0 + sh - 1.2f * u), Size(sw, 1.2f * u))
                for (i in 0..2) {
                    val bx = sl + ((i * 4.2f - scroll * 0.7f + 12f) % 12f) * u
                    if (bx < sl + sw - 0.9f * u) drawRect(Color(0xFFE8A33A), Offset(bx, st0 + sh - 2.1f * u), Size(0.9f * u, 0.9f * u))
                    val cxn = sl + ((i * 4.2f - scroll * 0.7f + 14f) % 12f) * u
                    if (cxn < sl + sw - 0.6f * u) drawCircle(Color(0xFFFFE066), 0.3f * u, Offset(cxn, st0 + sh * 0.35f + sin(t * 4f + i) * 0.3f * u))
                }
                val hop = abs(sin(t * 3.1f)) * 2.4f * u
                drawRoundRect(Color(0xFFFF6B6B), Offset(sl + sw * 0.3f, st0 + sh - 2.3f * u - hop), Size(1f * u, 1.1f * u), CornerRadius(0.3f * u))
            } else {
                drawRect(Brush.linearGradient(listOf(Color(0xFF232833), Color(0xFF12151C)), start = Offset(sl, st0), end = Offset(sl + sw, st0 + sh)), Offset(sl, st0), Size(sw, sh))
                // glass reflection of the window above
                drawLine(Color.White.copy(alpha = 0.06f + 0.06f * day), Offset(sl + sw * 0.15f, st0 + sh), Offset(sl + sw * 0.45f, st0), 1.2f * u)
            }
        }

        // --- rug
        drawOval(Color(0xFF6E5A74).copy(alpha = 0.55f), Offset(X(78f), fy + 3f * u), Size(56f * u, 10f * u))
        drawOval(Color(0xFF8C7690).copy(alpha = 0.45f), Offset(X(84f), fy + 4.5f * u), Size(44f * u, 7f * u))
        drawOval(Color.White.copy(alpha = 0.05f), Offset(X(92f), fy + 5f * u), Size(28f * u, 3f * u))

        // --- bed
        drawRoundRect(Brush.horizontalGradient(listOf(woodDark, lerp(woodDark, wood, 0.6f)), startX = X(3f), endX = X(7.5f)), Offset(X(3f), Y(30f)), Size(4.5f * u, 30f * u), CornerRadius(2f * u))
        drawRect(Brush.verticalGradient(listOf(lerp(wood, Color.White, 0.08f), woodDark), startY = Y(9f), endY = Y(2f)), Offset(X(3f), Y(9f)), Size(47f * u, 7f * u))
        drawRect(woodDark, Offset(X(46.5f), Y(4f)), Size(2.5f * u, 4f * u))
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFF2ECE2), Color(0xFFD6CEC2)), startY = Y(SceneGeo.MATTRESS_TOP), endY = Y(SceneGeo.MATTRESS_TOP - 5.5f)),
            Offset(X(6f), Y(SceneGeo.MATTRESS_TOP)), Size(42f * u, 5.5f * u), CornerRadius(2f * u))
        drawOval(Color(0xFFF7F2EA), Offset(X(7.5f), Y(18.5f)), Size(10f * u, 5.5f * u))
        drawOval(Color.Black.copy(alpha = 0.06f), Offset(X(8.5f), Y(15f)), Size(9f * u, 1.5f * u))
        if ("ball_on_bed" in st.pranks) drawBall(Offset(X(22f), Y(16.5f)), u)
        if (!pipoInBed) drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF86A5BA), Color(0xFF5F7E94)), startY = Y(15.5f), endY = Y(8.5f)),
            Offset(X(30f), Y(15.5f)), Size(18f * u, 7f * u), CornerRadius(2.2f * u))

        // --- plant (leaves react when Pipo brushes past)
        run {
            val base = Offset(X(61f), Y(10f))
            if ("ball_behind_plant" in st.pranks) drawBall(Offset(X(65.5f), fy - 1.5f * u), u)
            val rust = st.plantRustle
            rotate(sin(t * 0.8f) * 2.5f + sin(t * 9f) * rust * 7f + (if (st.music) sin(t * 3f) * 3f else 0f), pivot = base) {
                val leaves = listOf(-50f, -25f, 0f, 25f, 50f, -75f, 75f)
                for ((i, a) in leaves.withIndex()) rotate(a + sin(t * 7f + i * 1.7f) * rust * 9f, pivot = base) {
                    val c = if (i % 2 == 0) Color(0xFF6FA27A) else Color(0xFF86B98E)
                    drawOval(Brush.horizontalGradient(listOf(lerp(c, Color.White, 0.15f), c, lerp(c, Color.Black, 0.25f)), startX = base.x - 2.2f * u, endX = base.x + 2.2f * u),
                        Offset(base.x - 2.2f * u, base.y - 14f * u), Size(4.4f * u, 13f * u))
                    drawLine(lerp(c, Color.Black, 0.3f).copy(alpha = 0.5f), Offset(base.x, base.y - 13f * u), Offset(base.x, base.y - 2f * u), 0.25f * u)
                }
                if ("plant_hat" in st.pranks) drawItem(ItemShape.HAT, Offset(base.x, base.y - 16.5f * u), 7f * u)
            }
            val pot = Path().apply { moveTo(X(56.5f), Y(11f)); lineTo(X(65.5f), Y(11f)); lineTo(X(64.5f), Y(0f)); lineTo(X(57.5f), Y(0f)); close() }
            drawPath(pot, Brush.horizontalGradient(listOf(Color(0xFFCF9272), Color(0xFFB97E5E), Color(0xFF8E5B42)), startX = X(56.5f), endX = X(65.5f)))
            drawRect(Color(0xFFA06A4E), Offset(X(56f), Y(11.5f)), Size(10f * u, 1.8f * u))
        }

        // --- charging station (shows your phone's real battery)
        run {
            val pulse = if (st.charging) 0.55f + 0.45f * abs(sin(t * 2.2f)) else 0.25f
            drawOval(Brush.verticalGradient(listOf(Color(0xFF4A5566), Color(0xFF2B323E)), startY = fy - 1f * u, endY = fy + 3f * u), Offset(X(75f), fy - 1f * u), Size(18f * u, 4f * u))
            drawOval(Color(0xFF7FE3F0).copy(alpha = pulse), Offset(X(76.5f), fy - 0.4f * u), Size(15f * u, 2.8f * u), style = Stroke(0.6f * u))
            if (st.charging) {
                // expanding ring + rising energy motes
                val ring = (t * 0.7f) % 1f
                drawOval(Color(0xFF7FE3F0).copy(alpha = 0.5f * (1f - ring)), Offset(X(84f - 7.5f - ring * 5f), fy + 1f * u - ring * 1.2f * u), Size((15f + ring * 10f) * u, (2.8f + ring * 2.4f) * u), style = Stroke(0.4f * u))
                for (i in 0 until 7) {
                    val ph = (t * 0.45f + hash(i, 11)) % 1f
                    val mx = X(78f + hash(i, 5) * 12f) + sin(t * 2f + i) * 0.6f * u
                    drawCircle(Color(0xFF9FF3FF).copy(alpha = 0.7f * (1f - ph)), (0.35f + 0.25f * hash(i, 9)) * u, Offset(mx, fy - ph * 18f * u))
                }
            }
            drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF4B576A), Color(0xFF2C3444)), startX = X(92.5f), endX = X(95.9f)), Offset(X(92.5f), Y(21f)), Size(3.4f * u, 21f * u), CornerRadius(1.2f * u))
            val bars = ((st.battery + 24) / 25).coerceIn(0, 4)
            for (i in 0 until 4) {
                val on = i < bars
                drawRoundRect((if (st.battery <= 15) Color(0xFFFF8A7A) else Color(0xFF7FE3F0)).copy(alpha = if (on) (if (st.charging && i == bars - 1) pulse else 0.9f) else 0.15f),
                    Offset(X(93.2f), Y(6f + i * 3.6f)), Size(2f * u, 2.4f * u), CornerRadius(0.4f * u))
            }
            drawCircle(Color(0xFF7FE3F0).copy(alpha = pulse), 0.8f * u, Offset(X(94.2f), Y(19f)))
        }

        // --- B.O.L.T., his helper screen, on the end of the desk: a face that looks around
        if (st.helper && seen(150f, 158f)) {
            val c = Offset(X(153.5f), Y(27f))
            drawRoundRect(Color(0xFF2B3342), Offset(c.x - 3.2f * u, c.y - 2.6f * u), Size(6.4f * u, 5.2f * u), CornerRadius(1f * u))
            drawRoundRect(Color(0xFF0E1A22), Offset(c.x - 2.7f * u, c.y - 2.1f * u), Size(5.4f * u, 4.2f * u), CornerRadius(0.7f * u))
            drawRect(Color(0xFF2B3342), Offset(c.x - 0.4f * u, c.y + 2.6f * u), Size(0.8f * u, 1.6f * u))
            val look = sin(t * 0.6f) * 0.6f * u
            val blink = if ((t % 5f) < 0.12f) 0.2f else 1f
            drawRoundRect(Color(0xFF8FF5E2), Offset(c.x - 1.6f * u + look, c.y - 0.9f * u), Size(0.8f * u, 1.2f * u * blink), CornerRadius(0.3f * u))
            drawRoundRect(Color(0xFF8FF5E2), Offset(c.x + 0.8f * u + look, c.y - 0.9f * u), Size(0.8f * u, 1.2f * u * blink), CornerRadius(0.3f * u))
            drawArc(Color(0xFF8FF5E2), 20f, 140f, false, Offset(c.x - 1f * u + look, c.y + 0.1f * u), Size(2f * u, 1f * u), style = Stroke(0.25f * u))
            drawCircle(Color(0xFF8FF5E2).copy(alpha = 0.12f + 0.08f * night), 5f * u, c)
        }

        // --- desk, computer, lamp
        run {
            drawRect(Brush.verticalGradient(listOf(lerp(wood, Color.White, 0.1f), wood), startY = Y(19f), endY = Y(17f)), Offset(X(124f), Y(19f)), Size(32f * u, 2f * u))
            drawRect(Color.Black.copy(alpha = 0.15f), Offset(X(124f), Y(17f)), Size(32f * u, 0.6f * u))
            drawRect(woodDark, Offset(X(126f), Y(17f)), Size(2f * u, 17f * u))
            drawRect(woodDark, Offset(X(152f), Y(17f)), Size(2f * u, 17f * u))
            drawRect(wood, Offset(X(142f), Y(17f)), Size(10f * u, 8f * u))
            drawCircle(woodDark, 0.6f * u, Offset(X(147f), Y(13f)))
            drawRect(Color(0xFF3B4658), Offset(X(136f), Y(22f)), Size(2f * u, 3f * u))
            drawRoundRect(Color(0xFF2C3444), Offset(X(128f), Y(35f)), Size(18f * u, 13.5f * u), CornerRadius(1.5f * u))
            val active = st.computerActive
            drawRoundRect(if (active) Color(0xFF154A4F) else Color(0xFF123B3F), Offset(X(129f), Y(34f)), Size(16f * u, 11f * u), CornerRadius(0.8f * u))
            if (st.music) {
                for (i in 0 until 12) {
                    val bh = (1f + 4f * abs(sin(t * 5f + i * 0.9f)) * hash(i, 2).coerceAtLeast(0.3f))
                    drawRect(Color(0xFF9FE7E0).copy(alpha = 0.75f), Offset(X(130.2f + i * 1.2f), Y(25f + bh)), Size(0.7f * u, bh * u))
                }
            } else for (i in 0 until 4) {
                val len = 4f + ((i * 7 + (t * (if (active) 3f else 0.5f)).toInt()) % 9)
                drawLine(Color(0xFF9FE7E0).copy(alpha = 0.7f), Offset(X(130.5f), Y(32f - i * 2.3f)), Offset(X(130.5f + len), Y(32f - i * 2.3f)), 0.6f * u)
            }
            if ((t * 2f).toInt() % 2 == 0) drawRect(Color(0xFF9FE7E0), Offset(X(131f), Y(24.5f)), Size(1.2f * u, 1.3f * u))
            drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.09f), Color.Transparent), start = Offset(X(129f), Y(34f)), end = Offset(X(136f), Y(27f))), Offset(X(129f), Y(34f)), Size(16f * u, 11f * u))
            if ("screen_note" in st.pranks) {
                drawRect(Color(0xFFF3DB7A), Offset(X(139f), Y(33f)), Size(5.5f * u, 5.5f * u))
                drawLine(ink, Offset(X(139.8f), Y(31f)), Offset(X(143.6f), Y(31.4f)), 0.35f * u)
                drawLine(ink, Offset(X(139.8f), Y(29.5f)), Offset(X(142.6f), Y(29.7f)), 0.35f * u)
            }
            drawRoundRect(Color(0xFF3B4658), Offset(X(130f), Y(20f)), Size(12f * u, 1f * u), CornerRadius(0.4f * u))
            // books
            drawRect(Color(0xFF6F8FA6), Offset(X(146.5f), Y(22.5f)), Size(1.4f * u, 3.5f * u))
            drawRect(Color(0xFFB97E5E), Offset(X(148f), Y(23f)), Size(1.4f * u, 4f * u))
            // lamp
            drawRoundRect(Color(0xFF3B4658), Offset(X(150f), Y(20f)), Size(5f * u, 1f * u), CornerRadius(0.5f * u))
            drawLine(Color(0xFF8E99A8), Offset(X(152.5f), Y(20f)), Offset(X(150.5f), Y(30f)), 0.6f * u)
            val sock = "lamp_sock" in st.pranks
            val shade = Path().apply { moveTo(X(149f), Y(34f)); lineTo(X(152.5f), Y(34f)); lineTo(X(154f), Y(29.5f)); lineTo(X(147f), Y(29.5f)); close() }
            drawPath(shade, if (sock) Color(0xFFC99BFF) else Color(0xFFE7C27A))
            if (night > 0.2f && !sock) drawLine(Color(0xFFFFF0C8).copy(alpha = night), Offset(X(147.4f), Y(29.6f)), Offset(X(153.6f), Y(29.6f)), 0.5f * u)
            if (sock) for (i in 0..2) drawLine(Color.White.copy(alpha = 0.7f), Offset(X(148f), Y(30.5f + i * 1.3f)), Offset(X(153.5f), Y(30.5f + i * 1.3f)), 0.4f * u)
            // his coin jar: you can see roughly how rich he is
            run {
                val jl = X(142.2f); val jt = Y(23.5f)
                val fill = (st.coins / 40f).coerceIn(0.05f, 1f)
                drawRect(Color(0xFFE8C98A).copy(alpha = 0.9f), Offset(jl + 0.2f * u, jt + (4.3f - 4f * fill) * u), Size(2.8f * u, 4f * fill * u))
                drawRoundRect(Color.White.copy(alpha = 0.3f), Offset(jl, jt), Size(3.2f * u, 4.5f * u), CornerRadius(0.6f * u), style = Stroke(0.3f * u))
                drawRect(Color(0xFF8C6A5A), Offset(jl - 0.1f * u, jt - 0.6f * u), Size(3.4f * u, 0.8f * u))
            }
            // a library book, borrowed
            if (st.book) {
                drawRect(Color(0xFF9C8AB0), Offset(X(132f), Y(20.4f)), Size(6f * u, 1.3f * u))
                drawRect(Color(0xFFF4EFE6), Offset(X(132.3f), Y(19.6f)), Size(5.4f * u, 0.4f * u))
            }
            // the corkboard: photos from his phone
            if (seen(126f, 156f)) run {
                val cl = X(127f); val ct = Y(57f)
                drawRect(Color.Black.copy(alpha = 0.12f), Offset(cl + 0.5f * u, ct + 0.6f * u), Size(28f * u, 16f * u))
                drawRoundRect(Color(0xFFC49A6C), Offset(cl, ct), Size(28f * u, 16f * u), CornerRadius(0.6f * u))
                drawRoundRect(woodDark, Offset(cl, ct), Size(28f * u, 16f * u), CornerRadius(0.6f * u), style = Stroke(0.6f * u))
                for (i in 0 until 14) drawCircle(Color(0xFF9E7A52).copy(alpha = 0.4f), 0.2f * u, Offset(cl + hash(i, 51) * 28f * u, ct + hash(i, 53) * 16f * u))
                // a sticky note of his, half under the photos
                rotate(4f, Offset(cl + 23f * u, ct + 11f * u)) {
                    drawRect(Color(0xFFF3DB7A), Offset(cl + 21.5f * u, ct + 9.5f * u), Size(4.5f * u, 4.5f * u))
                    for (i in 0..2) drawLine(ink.copy(alpha = 0.6f), Offset(cl + 22.1f * u, ct + (10.6f + i * 1.1f) * u), Offset(cl + (24.8f - i * 0.6f) * u, ct + (10.5f + i * 1.1f) * u), 0.22f * u)
                }
                for ((i, p) in st.photos.take(5).withIndex()) {
                    // they overlap a little, the way real photos get pinned in a hurry
                    val px = cl + (1f + i * 4.6f + hash(p.seed, 5) * 0.8f) * u; val py = ct + (1.2f + (i % 2) * 5.2f + hash(p.seed, 6) * 0.8f) * u
                    rotate((hash(p.seed, 9) - 0.5f) * 12f, Offset(px + 2.4f * u, py)) {
                        drawPinnedPhoto(p, Offset(px, py), 4.8f * u, u, t)
                        if (hash(p.seed, 11) > 0.55f) // some are taped instead of pinned
                            rotate(-18f, Offset(px + 0.6f * u, py)) { drawRect(Color(0xFFF1EAD8).copy(alpha = 0.75f), Offset(px - 0.6f * u, py - 0.5f * u), Size(2.4f * u, 1f * u)) }
                    }
                }
            }
        }

        // --- shelf with discoveries
        run {
            drawRect(wood, Offset(X(162f), Y(58f)), Size(38f * u, 1.3f * u))
            drawRect(Color.Black.copy(alpha = 0.15f), Offset(X(162f), Y(56.7f)), Size(38f * u, 1.2f * u))
            drawLine(woodDark, Offset(X(165f), Y(56.7f)), Offset(X(167f), Y(54f)), 0.6f * u)
            drawLine(woodDark, Offset(X(197f), Y(56.7f)), Offset(X(195f), Y(54f)), 0.6f * u)
            for ((i, s) in st.shelf.take(8).withIndex()) drawItem(s, Offset(X(166f + i * 4.6f), Y(60.6f)), 4.2f * u)
            if (st.token) {
                val tc = Offset(X(199f), Y(60.4f))
                // at night, once things have started, it glows. Nobody mentions it.
                if (st.tokenAwake && night > 0.4f) {
                    val a = (0.25f + 0.2f * sin(t * 0.9f)) * night
                    drawCircle(Brush.radialGradient(listOf(Color(0xFF9FF3E0).copy(alpha = a), Color.Transparent), center = tc, radius = 4f * u), 4f * u, tc)
                }
                drawItem(ItemShape.TOKEN, tc, 3.4f * u)
            }
            if (st.mirrorScrew) {
                val mc = Offset(X(195.5f), Y(60.2f))
                drawCircle(Brush.radialGradient(listOf(Color(0xFF9FF3E0).copy(alpha = 0.18f + 0.08f * sin(t * 1.3f)), Color.Transparent), center = mc, radius = 3f * u), 3f * u, mc)
                rotate(sin(t * 0.4f) * 25f, mc) { drawItem(ItemShape.SCREW, mc, 3f * u) } // it never quite sits still
            }
        }

        // --- pegboard + workbench
        run {
            drawRoundRect(Color(0xFFC9B79C).copy(alpha = 0.85f), Offset(X(164f), Y(46f)), Size(34f * u, 22f * u), CornerRadius(1f * u))
            var gx = 166f
            while (gx < 197f) { var gy = 26f; while (gy < 45f) { drawCircle(Color(0xFF8E7A62).copy(alpha = 0.5f), 0.25f * u, Offset(X(gx), Y(gy))); gy += 3f }; gx += 3f }
            // wrench, screwdriver, hammer silhouettes
            drawLine(Color(0xFF8E99A8), Offset(X(170f), Y(42f)), Offset(X(170f), Y(30f)), 0.9f * u, StrokeCap.Round)
            drawCircle(Color(0xFF8E99A8), 1.6f * u, Offset(X(170f), Y(43f)), style = Stroke(0.7f * u))
            drawLine(Color(0xFFE0706A), Offset(X(178f), Y(42f)), Offset(X(178f), Y(37f)), 1.4f * u, StrokeCap.Round)
            drawLine(Color(0xFF8E99A8), Offset(X(178f), Y(37f)), Offset(X(178f), Y(30f)), 0.4f * u)
            drawLine(woodDark, Offset(X(188f), Y(42f)), Offset(X(188f), Y(30f)), 0.8f * u, StrokeCap.Round)
            drawRoundRect(Color(0xFF6B7482), Offset(X(185.5f), Y(43f)), Size(5f * u, 2f * u), CornerRadius(0.5f * u))
            // tools he's earned, one more per level
            val lv = st.builderLevel
            if (lv >= 2) { drawLine(Color(0xFF8E99A8), Offset(X(174f), Y(41f)), Offset(X(173f), Y(33f)), 0.5f * u); drawLine(Color(0xFF8E99A8), Offset(X(174f), Y(41f)), Offset(X(175.5f), Y(33f)), 0.5f * u); drawCircle(Color(0xFFE0706A), 0.7f * u, Offset(X(174f), Y(41.5f))) } // pliers
            if (lv >= 3) { drawRect(Color(0xFFB8C2CC), Offset(X(181f), Y(41f)), Size(1.8f * u, 9f * u)); for (i in 0 until 6) drawLine(Color(0xFF6B7482), Offset(X(182.8f), Y(41f - i * 1.5f)), Offset(X(183.4f), Y(40.4f - i * 1.5f)), 0.2f * u) } // saw
            if (lv >= 4) { // soldering iron, tip glowing
                drawLine(Color(0xFF3B4658), Offset(X(193f), Y(43f)), Offset(X(193f), Y(35f)), 1f * u, StrokeCap.Round)
                drawLine(Color(0xFFB8C2CC), Offset(X(193f), Y(35f)), Offset(X(193f), Y(32f)), 0.35f * u)
                drawCircle(Color(0xFFFF9A4A).copy(alpha = 0.6f + 0.4f * sin(t * 4f)), 0.5f * u, Offset(X(193f), Y(31.8f)))
            }
            if (lv >= 5) { drawRoundRect(Color(0xFFC9A23A), Offset(X(195.5f), Y(42f)), Size(1.2f * u, 9f * u), CornerRadius(0.3f * u)); drawRect(Color(0xFFC9A23A), Offset(X(194.5f), Y(42f)), Size(3f * u, 0.8f * u)) } // calipers

            drawRect(Brush.verticalGradient(listOf(Color(0xFF9A7A60), Color(0xFF7A5C46)), startY = Y(17f), endY = Y(14.4f)), Offset(X(160f), Y(17f)), Size(42f * u, 2.6f * u))
            drawRect(woodDark, Offset(X(162f), Y(14.4f)), Size(2f * u, 14.4f * u))
            drawRect(woodDark, Offset(X(198f), Y(14.4f)), Size(2f * u, 14.4f * u))
            drawRect(woodDark.copy(alpha = 0.8f), Offset(X(162f), Y(5f)), Size(38f * u, 1.2f * u))
            drawRect(Color(0xFF6B7482), Offset(X(161f), Y(20f)), Size(4f * u, 3f * u))
            // the parts he actually has for the project, laid out on the bench
            val parts = st.benchParts.ifEmpty { if (st.projectActive) listOf(ItemShape.GEAR, ItemShape.COIL, ItemShape.SPRING) else emptyList() }
            for ((i, sh) in parts.take(4).withIndex()) drawItem(sh, Offset(X(168f + i * 5f), Y(18.8f)), 3.5f * u)
            // the lab, at night: a hologram of what he's building, turning slowly over the bench
            if (st.builderLevel >= 4 && night > 0.3f && st.projectActive) {
                val hc = Offset(X(181f), Y(28f))
                drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(X(176f), Y(17f)); lineTo(X(186f), Y(17f)); lineTo(X(189f), Y(36f)); lineTo(X(173f), Y(36f)); close() },
                    Brush.verticalGradient(listOf(Color(0xFF8FF5E2).copy(alpha = 0.0f), Color(0xFF8FF5E2).copy(alpha = 0.18f * night)), startY = Y(36f), endY = Y(17f)))
                for (i in 0..3) drawOval(Color(0xFF8FF5E2).copy(alpha = 0.25f * night), Offset(hc.x - (5f - i) * u, hc.y - (2f + i * 1.6f) * u), Size((10f - 2 * i) * u, 1.6f * u), style = Stroke(0.15f * u))
                rotate(t * 25f, hc) { drawItem(st.benchParts.firstOrNull() ?: ItemShape.GEAR, hc, 6f * u) }
                drawCircle(Color(0xFF8FF5E2).copy(alpha = 0.12f * night), 6f * u, hc)
            }
            // genius level: floating holo-screens over the workbench, scrolling his calculations
            if (st.builderLevel >= 5) for (i in 0..2) {
                val tl = Offset(X(164f + i * 12f), Y(78f + (i % 2) * 4f) + sin(t * 0.8f + i) * 0.5f * u)
                drawRoundRect(Color(0xFF8FF5E2).copy(alpha = 0.10f + 0.06f * night), tl, Size(10f * u, 7f * u), CornerRadius(1f * u))
                drawRoundRect(Color(0xFF8FF5E2).copy(alpha = 0.45f), tl, Size(10f * u, 7f * u), CornerRadius(1f * u), style = Stroke(0.15f * u))
                for (j in 0..3) { val ly = tl.y + (1.2f + ((j * 1.4f + t * 0.8f) % 5.6f)) * u; drawLine(Color(0xFF8FF5E2).copy(alpha = 0.5f), Offset(tl.x + 1f * u, ly), Offset(tl.x + (3f + (j * 37 % 5)) * u, ly), 0.2f * u) }
            }
            // blueprints pinned up beside the arcade, more as he levels up
            for (i in 0 until (st.builderLevel - 1).coerceIn(0, 4)) {
                val tl = Offset(X(229f + (i % 2) * 6.5f), Y(52f - (i / 2) * 9f)); val bw = 6f * u; val bh = 7.5f * u
                rotate(((i * 37) % 7 - 3).toFloat(), Offset(tl.x + bw / 2f, tl.y)) {
                    drawRect(Color(0xFF2E5C9A), tl, Size(bw, bh))
                    drawRect(Color.White.copy(alpha = 0.55f), Offset(tl.x + 0.8f * u, tl.y + 1f * u), Size(bw - 1.6f * u, bh - 2f * u), style = Stroke(0.15f * u))
                    drawCircle(Color.White.copy(alpha = 0.55f), 1.3f * u, Offset(tl.x + bw / 2f, tl.y + bh / 2f), style = Stroke(0.15f * u))
                    drawLine(Color.White.copy(alpha = 0.55f), Offset(tl.x + 1f * u, tl.y + bh - 1.5f * u), Offset(tl.x + bw - 1f * u, tl.y + 1.5f * u), 0.15f * u)
                    drawCircle(Color(0xFFE0706A), 0.35f * u, Offset(tl.x + bw / 2f, tl.y + 0.4f * u))
                }
            }
            // what's left of the one that didn't work
            st.scraps?.let { sh ->
                rotate(35f, Offset(X(192f), fy + 1f * u)) { drawItem(sh, Offset(X(192f), fy + 1f * u), 4f * u) }
                drawItem(ItemShape.SCREW, Offset(X(186f), fy + 1.6f * u), 2.2f * u); drawItem(ItemShape.SPRING, Offset(X(196f), fy + 1.2f * u), 2.4f * u)
            }
            st.benchThing?.let { drawItem(it, Offset(X(191f), Y(21.5f)), 7f * u) }
            if ("screw_tower" in st.pranks) for (i in 0 until 6) drawItem(ItemShape.SCREW, Offset(X(197f), Y(19f + i * 1.6f)), 2.4f * u)
            if (st.benchActive) for (i in 0 until 6) {
                // welding sparks
                val ph = (t * 1.8f + hash(i, 21)) % 1f
                val ang = (hash(i, 4) - 0.5f) * 2.4f - 1.57f
                val d = ph * 5f * u
                val sp = Offset(X(177f) + cos(ang) * d, Y(19.5f) + sin(ang) * d + ph * ph * 3f * u)
                drawCircle(Color(0xFFFFD27A).copy(alpha = 1f - ph), 0.3f * u, sp)
            }
        }

        // --- arcade cabinet
        drawArcadeCabinet(u, fy, st, t)

        // --- toys
        run {
            drawRect(Brush.verticalGradient(listOf(Color(0xFFD6AE6A), Color(0xFFB08A4C)), startY = Y(9f), endY = Y(0f)), Offset(X(228f), Y(9f)), Size(12f * u, 9f * u))
            for (i in 0..2) drawLine(Color(0xFFE8C98A), Offset(X(228f + i * 4f + 1f), Y(9f)), Offset(X(228f + i * 4f + 1f), Y(0f)), 0.6f * u)
            val rocket = Path().apply { moveTo(X(235f), Y(18f)); lineTo(X(237f), Y(12f)); lineTo(X(233f), Y(12f)); close() }
            drawPath(rocket, Color(0xFFDDE3EB)); drawRect(Color(0xFFDDE3EB), Offset(X(233f), Y(12f)), Size(4f * u, 3.5f * u))
            if ("ball_on_bed" !in st.pranks && "ball_behind_plant" !in st.pranks && !st.ballByDoor) drawBall(Offset(X(st.ballU), fy + 1.5f * u - st.ballLift * u), u)
        }

        // --- the kitchenette: mini fridge, a counter with a hot plate, a shelf of whatever he bought
        if (seen(SceneGeo.FRIDGE_L - 2f, SceneGeo.COUNTER_R + 12f)) run {
            val fl = SceneGeo.FRIDGE_L; val cl = SceneGeo.COUNTER_L; val cr = SceneGeo.COUNTER_R
            groundShadow(fl - 1f, cr + 1f, 0.22f)
            // fridge
            drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFFE8EEF2), Color(0xFFC9D2DA)), startX = X(fl), endX = X(fl + 10f)), Offset(X(fl), Y(26f)), Size(10f * u, 26f * u), CornerRadius(1.6f * u))
            drawLine(Color(0xFFAAB4BE), Offset(X(fl + 0.4f), Y(17f)), Offset(X(fl + 9.6f), Y(17f)), 0.4f * u)
            drawRoundRect(Color(0xFF8E99A8), Offset(X(fl + 8.2f), Y(23f)), Size(0.7f * u, 4f * u), CornerRadius(0.3f * u))
            drawRoundRect(Color(0xFF8E99A8), Offset(X(fl + 8.2f), Y(14f)), Size(0.7f * u, 5f * u), CornerRadius(0.3f * u))
            // fridge magnets: one per place he's been, sort of
            drawCircle(Color(0xFFE0706A), 0.6f * u, Offset(X(fl + 3f), Y(21f)))
            drawRect(Color(0xFFF3DB7A), Offset(X(fl + 1.5f), Y(12f)), Size(3.5f * u, 3.5f * u))
            // open: cold light spills out, the door swings towards us
            if (st.fridgeOpen > 0.02f) {
                val o = st.fridgeOpen
                drawRect(Color(0xFFEFF6FF), Offset(X(fl + 0.6f), Y(16.4f)), Size(8.8f * u, 15.6f * u))
                drawRect(Color(0xFFBFD3E0), Offset(X(fl + 0.6f), Y(9f)), Size(8.8f * u, 0.5f * u))
                drawCircle(Brush.radialGradient(listOf(Color(0xFFE6F2FF).copy(alpha = 0.45f * o), Color.Transparent), center = Offset(X(fl + 5f), Y(10f)), radius = 14f * u), 14f * u, Offset(X(fl + 5f), Y(10f)))
                drawRect(Color(0xFFD9E2EA), Offset(X(fl - 5f * o), Y(16.4f)), Size((1f + 5f * o) * u, 16f * u))
            }
            // counter + cupboards
            drawRect(Brush.verticalGradient(listOf(lerp(wood, Color.White, 0.12f), wood), startY = Y(15f), endY = Y(13.4f)), Offset(X(cl), Y(15f)), Size((cr - cl) * u, 1.6f * u))
            drawRect(Brush.verticalGradient(listOf(Color(0xFF8FA3A6), Color(0xFF6E8285)), startY = Y(13.4f), endY = fy), Offset(X(cl + 0.4f), Y(13.4f)), Size((cr - cl - 0.8f) * u, 13.4f * u))
            drawLine(Color.Black.copy(alpha = 0.2f), Offset(X((cl + cr) / 2f), Y(13f)), Offset(X((cl + cr) / 2f), Y(0.5f)), 0.3f * u)
            drawCircle(Color(0xFFD8CFC2), 0.4f * u, Offset(X((cl + cr) / 2f - 1f), Y(7f))); drawCircle(Color(0xFFD8CFC2), 0.4f * u, Offset(X((cl + cr) / 2f + 1f), Y(7f)))
            // hot plate + pot
            drawRoundRect(Color(0xFF2C3444), Offset(X(262f), Y(16.2f)), Size(7f * u, 1.2f * u), CornerRadius(0.4f * u))
            if (st.cooking) drawOval(Color(0xFFFF8A4C).copy(alpha = 0.5f + 0.3f * sin(t * 6f)), Offset(X(262.8f), Y(16.4f)), Size(5.4f * u, 0.6f * u))
            drawRoundRect(Color(0xFF9AA7B8), Offset(X(263f), Y(20.5f)), Size(5f * u, 4.2f * u), CornerRadius(0.8f * u))
            drawLine(Color(0xFF6B7482), Offset(X(262f), Y(19.8f)), Offset(X(263f), Y(19.8f)), 0.5f * u)
            if (st.cooking) for (i in 0 until 4) {
                val ph = (t * 0.6f + hash(i, 61)) % 1f
                drawCircle(Color.White.copy(alpha = 0.35f * (1f - ph)), (0.8f + ph * 1.5f) * u, Offset(X(265.5f + sin(t * 2f + i) * 1f), Y(21f + ph * 9f)))
            }
            // a plate left on the counter (he'll wash it. eventually)
            if (st.plate) {
                drawOval(Color(0xFFF2ECE2), Offset(X(257f), Y(15.8f)), Size(4.5f * u, 1.2f * u))
                drawCircle(Color(0xFFD9A35F).copy(alpha = 0.7f), 0.35f * u, Offset(X(258.2f), Y(15.5f))); drawCircle(Color(0xFFE0605A).copy(alpha = 0.6f), 0.3f * u, Offset(X(259.8f), Y(15.3f)))
                drawLine(Color(0xFF8E99A8), Offset(X(258f), Y(16f)), Offset(X(261f), Y(17.2f)), 0.3f * u)
            }
            // wall shelf with the food he actually has
            drawRect(wood, Offset(X(cl), Y(29f)), Size((cr - cl) * u, 1f * u))
            drawRect(Color.Black.copy(alpha = 0.12f), Offset(X(cl), Y(28f)), Size((cr - cl) * u, 0.8f * u))
            for ((i, s) in st.pantry.take(5).withIndex()) drawItem(s, Offset(X(cl + 1.8f + i * 2.9f), Y(30.8f)), 2.8f * u)
            // burnt something: a little cloud still hanging near the ceiling
            if (st.smoke) for (i in 0 until 3) drawCircle(Color(0xFF8A8F99).copy(alpha = 0.18f), (3f + i) * u, Offset(X(262f + i * 3f + sin(t * 0.4f + i) * 1.5f), Y(52f + i * 2.5f)))
        }

        // --- once he's an inventor, the door says so
        if (st.builderLevel >= 4 && seen(278f, 294f)) {
            val tl = Offset(X(281f), Y(52f))
            drawRoundRect(Color(0xFF2B3342), tl, Size(10f * u, 3.2f * u), CornerRadius(0.6f * u))
            for (i in 0 until 4) drawRect(Color(0xFF8FF5E2).copy(alpha = 0.8f), Offset(tl.x + (1.2f + i * 2.2f) * u, tl.y + 1f * u), Size(1.5f * u, 1.2f * u))
        }

        // --- the front door. Where he goes. Where he comes back from.
        if (seen(SceneGeo.DOOR_L - 14f, SceneGeo.DOOR_R + 2f)) run {
            val dl = SceneGeo.DOOR_L; val dr = SceneGeo.DOOR_R
            val open = st.doorOpen.coerceIn(0f, 1f)
            // doorway: the outside, when it's open
            val outside = lerp(Color(0xFF1B2440), Color(0xFFBFDCE6), lit)
            drawRect(outside, Offset(X(dl), Y(52f)), Size((dr - dl) * u, 52f * u))
            if (open > 0.01f) drawRect(Brush.horizontalGradient(listOf(outside.copy(alpha = 0f), Color(0xFFFFF1C8).copy(alpha = 0.25f * day)), startX = X(dl), endX = X(dr)), Offset(X(dl), Y(52f)), Size((dr - dl) * u, 52f * u))
            // the door leaf swings in, so it gets narrower as it opens
            val leafW = (dr - dl) * (1f - 0.82f * open)
            drawRect(Brush.horizontalGradient(listOf(Color(0xFF7F5D48), Color(0xFF6A4D3B)), startX = X(dl), endX = X(dl + leafW)), Offset(X(dl), Y(52f)), Size(leafW * u, 52f * u))
            if (open < 0.5f) {
                drawRoundRect(Color.Black.copy(alpha = 0.12f), Offset(X(dl + 1.6f), Y(48f)), Size((leafW - 3.2f) * u, 20f * u), CornerRadius(0.6f * u), style = Stroke(0.4f * u))
                drawRoundRect(Color.Black.copy(alpha = 0.12f), Offset(X(dl + 1.6f), Y(24f)), Size((leafW - 3.2f) * u, 20f * u), CornerRadius(0.6f * u), style = Stroke(0.4f * u))
                drawCircle(Color(0xFFE8C98A), 0.8f * u, Offset(X(dl + leafW - 2f), Y(24f)))
            }
            // frame
            drawRect(lerp(wall, Color.White, 0.35f), Offset(X(dl - 1.2f), Y(53.2f)), Size((dr - dl + 2.4f) * u, 1.4f * u))
            drawRect(lerp(wall, Color.White, 0.3f), Offset(X(dl - 1.2f), Y(53f)), Size(1.2f * u, 53f * u))
            drawRect(lerp(wall, Color.White, 0.3f), Offset(X(dr), Y(53f)), Size(1.2f * u, 53f * u))
            // the note, when he's out
            if (st.doorNote && open < 0.3f) {
                rotate(-6f, Offset(X(dl + 7f), Y(38f))) {
                    drawRect(Color(0xFFF3DB7A), Offset(X(dl + 4.2f), Y(40f)), Size(5.6f * u, 5.2f * u))
                    for (i in 0..2) drawLine(ink.copy(alpha = 0.7f), Offset(X(dl + 4.9f), Y(38.8f - i * 1.3f)), Offset(X(dl + 4.9f + (3.6f - i * 0.8f)), Y(38.7f - i * 1.3f)), 0.3f * u)
                }
            }
            // mat
            drawOval(Color(0xFF8C6A5A).copy(alpha = 0.75f), Offset(X(dl - 1f), fy + 1.5f * u), Size((dr - dl + 2f) * u, 3.2f * u))
            // umbrella on its hook
            if (st.umbrella) {
                drawLine(Color(0xFF8E99A8), Offset(X(dl - 3f), Y(34f)), Offset(X(dl - 3f), Y(33f)), 0.4f * u)
                rotate(180f, Offset(X(dl - 3f), Y(29f))) { drawItem(ItemShape.UMBRELLA, Offset(X(dl - 3f), Y(29f)), 6f * u) }
            }
            // shopping bag he hasn't unpacked
            if (st.bag) drawItem(ItemShape.BAG, Offset(X(dl - 4.5f), fy - 3f * u), 6f * u)
            // the ball, left by the door after football
            if (st.ballByDoor) drawBall(Offset(X(dl - 9f), fy + 1.5f * u), u)
        }

        // --- the box he kept from a delivery (a bed for Nib, a fort for him)
        if (st.box && seen(SceneGeo.BOX_L - 3f, SceneGeo.BOX_R + 3f)) drawCardboardBox(u, fy, frontOnly = false)

        // --- evidence of Nib
        st.floorItem?.let { drawItem(it, Offset(X(186f), fy + 1f * u), 3.6f * u) }
        if (st.stolenGlint) {
            val tw = 0.5f + 0.5f * sin(t * 2.3f)
            drawCircle(Color(0xFFFFF1C8).copy(alpha = 0.5f * tw), 0.8f * u, Offset(X(24f), fy - 0.4f * u))
            drawLine(Color.White.copy(alpha = 0.6f * tw), Offset(X(23f), fy - 0.4f * u), Offset(X(25f), fy - 0.4f * u), 0.2f * u)
        }

        // --- dressed up for the day
        st.festival?.let { drawFestivalRoom(it, { v -> X(v) }, { v -> Y(v) }, fy, u, t, night) }

        // --- string lights under the molding: faint by day, warm and cozy at night
        run {
            val ceilY = fy - 104f * u
            val top = if (ceilY > 0f) ceilY + 2.6f * u else Y(96f)
            val bulbCols = festivalBulbs(st.festival) ?: listOf(Color(0xFFFFD27A), Color(0xFFFF9E8A), Color(0xFF9FF3E0), Color(0xFFFFE9B0))
            var hx = 4f
            var bi = 0
            while (hx < SceneGeo.WORLD_W - 4f) {
                val span = 30f
                val wire = Path()
                for (i in 0..12) {
                    val f = i / 12f
                    val wx = X(hx + f * span); val wy = top + (1f - (2f * f - 1f) * (2f * f - 1f)) * 3.2f * u
                    if (i == 0) wire.moveTo(wx, wy) else wire.lineTo(wx, wy)
                }
                drawPath(wire, Color(0xFF2B3342).copy(alpha = 0.55f), style = Stroke(0.25f * u))
                for (j in 1..4) {
                    val f = j / 5f
                    val bx = X(hx + f * span); val by = top + (1f - (2f * f - 1f) * (2f * f - 1f)) * 3.2f * u + 1f * u
                    val col = bulbCols[bi++ % bulbCols.size]
                    val flick = 0.85f + 0.15f * sin(t * 1.3f + bi * 1.7f)
                    val on = 0.35f + 0.65f * night
                    if (night > 0.1f) drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.35f * night * flick), Color.Transparent), center = Offset(bx, by), radius = 4f * u), 4f * u, Offset(bx, by))
                    drawOval(lerp(Color(0xFFE8E2D6), col, on).copy(alpha = 0.95f), Offset(bx - 0.6f * u, by - 0.4f * u), Size(1.2f * u, 1.7f * u))
                }
                hx += span
            }
        }

        // --- the night moth (Pipo sometimes watches it)
        if (night > 0.35f) {
            val (mx, mh) = Critters.moth(t)
            val mp = Offset(X(mx), Y(mh))
            val flap = abs(sin(t * 30f))
            drawCircle(Color(0xFFFFE9B0).copy(alpha = 0.25f * night), 1.6f * u, mp)
            drawOval(Color(0xFFE8DCC2).copy(alpha = 0.9f), Offset(mp.x - 1.1f * u, mp.y - 0.4f * u * flap), Size(2.2f * u, 0.8f * u * flap + 0.2f * u))
            drawCircle(Color(0xFF6B5A48), 0.3f * u, mp)
        }
    }
}

/** In world coordinates (the caller has already translated by the camera). */
private fun DrawScope.drawArcadeCabinet(u: Float, fy: Float, st: RoomState, t: Float) {
    fun X(v: Float) = v * u
    fun Y(v: Float) = fy - v * u
    drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF5A5484), Color(0xFF4A4470), Color(0xFF363156)), startX = X(206f), endX = X(224f)), Offset(X(206f), Y(46f)), Size(18f * u, 46f * u), CornerRadius(2f * u))
    drawRect(Color(0xFF3A355C), Offset(X(206f), Y(46f)), Size(2f * u, 46f * u))
    drawRoundRect(Color(0xFFFFC27A).copy(alpha = 0.8f), Offset(X(207.5f), Y(45f)), Size(15f * u, 4.5f * u), CornerRadius(1f * u))
    val scrL = X(208.5f); val scrT = Y(38f); val scrW = 13f * u; val scrH = 12f * u
    drawRect(Color(0xFF0E1320), Offset(scrL, scrT), Size(scrW, scrH))
    val speed = if (st.arcadeActive) 3f else 0.8f
    val bx2 = scrL + (0.5f + 0.45f * sin(t * speed)) * scrW
    val by2 = scrT + (0.5f + 0.4f * sin(t * speed * 1.37f)) * scrH
    drawRect(PipoColors.eye, Offset(bx2 - 0.5f * u, by2 - 0.5f * u), Size(1f * u, 1f * u))
    drawRect(PipoColors.eye.copy(alpha = 0.8f), Offset(scrL + 0.6f * u, by2 - 1.6f * u), Size(0.6f * u, 3.2f * u))
    drawRect(PipoColors.eye.copy(alpha = 0.8f), Offset(scrL + scrW - 1.2f * u, scrT + (0.5f + 0.4f * sin(t * speed * 1.1f)) * scrH - 1.6f * u), Size(0.6f * u, 3.2f * u))
    for (sl in 0 until 6) drawLine(Color.Black.copy(alpha = 0.18f), Offset(scrL, scrT + sl * 2f * u), Offset(scrL + scrW, scrT + sl * 2f * u), 0.2f * u)
    if ("arcade_score" in st.pranks) {
        val crown = Path().apply {
            val cx = scrL + scrW / 2; val cy = scrT + 2.5f * u
            moveTo(cx - 2f * u, cy + 1f * u); lineTo(cx - 2f * u, cy - 1f * u); lineTo(cx - 1f * u, cy); lineTo(cx, cy - 1.4f * u)
            lineTo(cx + 1f * u, cy); lineTo(cx + 2f * u, cy - 1f * u); lineTo(cx + 2f * u, cy + 1f * u); close()
        }
        drawPath(crown, Color(0xFFFFC27A))
    }
    drawRect(Color(0xFF3A355C), Offset(X(207f), Y(23f)), Size(16f * u, 3f * u))
    drawLine(PipoColors.joint, Offset(X(210f), Y(24f)), Offset(X(210f), Y(27f)), 0.5f * u)
    drawCircle(Color(0xFFE0706A), 0.9f * u, Offset(X(210f), Y(27.3f)))
    drawCircle(Color(0xFFFFC27A), 0.8f * u, Offset(X(216f), Y(24.8f)))
    drawCircle(PipoColors.blush, 0.8f * u, Offset(X(219f), Y(24.8f)))
}

/** The cardboard box he kept. [frontOnly] = just the front panel, drawn over him when he's inside. */
private fun DrawScope.drawCardboardBox(u: Float, fy: Float, frontOnly: Boolean) {
    fun X(v: Float) = v * u
    fun Y(v: Float) = fy - v * u
    val l = SceneGeo.BOX_L; val r = SceneGeo.BOX_R; val h = SceneGeo.BOX_H
    val card = Color(0xFFC9A57A); val cardDark = Color(0xFFA9855C)
    if (!frontOnly) {
        drawOval(Color.Black.copy(alpha = 0.2f), Offset(X(l - 0.5f), fy - 1f * u), Size((r - l + 1f) * u, 3f * u))
        // back wall and the dark inside
        drawRect(cardDark, Offset(X(l + 0.8f), Y(h + 1.5f)), Size((r - l - 1.6f) * u, 2f * u))
        drawRect(Color(0xFF5E4A36), Offset(X(l + 0.5f), Y(h)), Size((r - l - 1f) * u, 1.2f * u))
        // flaps, open
        drawPath(Path().apply { moveTo(X(l), Y(h)); lineTo(X(l - 2.2f), Y(h + 3f)); lineTo(X(l + 1.5f), Y(h + 2.4f)); lineTo(X(l + 1f), Y(h)); close() }, cardDark)
        drawPath(Path().apply { moveTo(X(r), Y(h)); lineTo(X(r + 2.4f), Y(h + 2.6f)); lineTo(X(r - 1.2f), Y(h + 2.9f)); lineTo(X(r - 1f), Y(h)); close() }, cardDark)
    }
    drawRect(Brush.verticalGradient(listOf(card, cardDark), startY = Y(h), endY = fy), Offset(X(l), Y(h)), Size((r - l) * u, h * u))
    drawLine(Color(0xFFE8D4B0).copy(alpha = 0.7f), Offset(X((l + r) / 2f), Y(h)), Offset(X((l + r) / 2f), Y(h - 3f)), 1.2f * u) // tape
    drawLine(ink.copy(alpha = 0.55f), Offset(X(l + 2f), Y(4f)), Offset(X(l + 5f), Y(4.6f)), 0.3f * u) // "THIS SIDE UP" scribble
    drawLine(ink.copy(alpha = 0.55f), Offset(X(l + 2f), Y(3f)), Offset(X(l + 4f), Y(3.3f)), 0.3f * u)
}

/**
 * Whatever Pipo is hiding behind, drawn again in front of him. The antenna tip above the arcade
 * cabinet (or two eyes over the edge of the box) is all you get.
 */
fun DrawScope.drawOccluder(g: SceneGeo, camU: Float, st: RoomState, spot: HideSpot?, t: Float) {
    if (spot == null || spot == HideSpot.BLANKET) return
    withTransform({ translate(-camU * g.u, 0f) }) {
        when (spot) {
            HideSpot.ARCADE -> drawArcadeCabinet(g.u, g.floorY, st, t)
            HideSpot.BOX -> {
                val wob = if (sin(t * 0.7f) > 0.92f) sin(t * 22f) * 1.2f else 0f
                rotate(wob, Offset((SceneGeo.BOX_L + SceneGeo.BOX_R) / 2f * g.u, g.floorY)) { drawCardboardBox(g.u, g.floorY, frontOnly = true) }
            }
            HideSpot.BLANKET -> Unit
        }
    }
}

private fun DrawScope.drawBall(c: Offset, u: Float) {
    drawOval(Color.Black.copy(alpha = 0.2f), Offset(c.x - 3f * u, c.y + 2.2f * u), Size(6f * u, 1.6f * u))
    drawCircle(Brush.radialGradient(listOf(Color(0xFFF7A696), Color(0xFFE98A7A), Color(0xFFB8604F)), center = Offset(c.x - 1f * u, c.y - 1f * u), radius = 4f * u), 3f * u, c)
    drawLine(Color.White.copy(alpha = 0.8f), Offset(c.x - 3f * u, c.y), Offset(c.x + 3f * u, c.y), 0.6f * u)
    drawCircle(Color.White.copy(alpha = 0.55f), 0.8f * u, Offset(c.x - 1.2f * u, c.y - 1.2f * u))
}

/**
 * Blanket drawn over Pipo when he's in bed. Positioned from his actual lying pose
 * ([foot] = his feet on screen, [k] = px per Pipo-unit) so it covers torso and legs and leaves
 * his head on the pillow, instead of sitting under him.
 */
fun DrawScope.drawBlanket(g: SceneGeo, foot: Offset, k: Float, t: Float, overHead: Boolean = false) {
    val u = g.u
    val pivotY = foot.y - 30f * k                      // lying rotates around this point
    // hiding under it: a lump that giggles now and then
    val breathe = sin(t * 1.1f) * 0.35f * u + (if (overHead && sin(t * 0.9f) > 0.85f) sin(t * 40f) * 0.3f * u else 0f)
    val l = foot.x - (if (overHead) 44f else 9f) * k   // just past his neck (or over his head)
    val r = foot.x + 40f * k                           // to the foot of the bed
    val top = pivotY - 17f * k + breathe               // over his torso
    val bottom = g.floorY - SceneGeo.MATTRESS_TOP * u + 3.2f * u
    val legTop = pivotY - 8f * k + breathe * 0.5f        // lower over his legs
    fun mound(dx: Float, dy: Float) = Path().apply {
        moveTo(l + dx, bottom + dy)
        lineTo(l + dx, top + 2.5f * u + dy)
        quadraticBezierTo(l + dx, top + dy, l + 3f * u + dx, top + dy)
        lineTo(foot.x + 6f * k + dx, top + dy)
        cubicTo(foot.x + 18f * k + dx, top + dy, foot.x + 22f * k + dx, legTop + dy, foot.x + 32f * k + dx, legTop + dy)
        quadraticBezierTo(r + dx, legTop + dy, r + dx, legTop + 3f * u + dy)
        lineTo(r + dx, bottom + dy)
        close()
    }
    drawPath(mound(0.6f * u, 0.8f * u), Color.Black.copy(alpha = 0.12f))
    drawPath(mound(0f, 0f), Brush.verticalGradient(listOf(Color(0xFF86A6BC), Color(0xFF6F8FA6), Color(0xFF4F6E84)), startY = top, endY = bottom))
    // turned-down hem along the top edge near his neck
    val hem = Path().apply {
        moveTo(l, top + 2.5f * u); quadraticBezierTo(l, top, l + 3f * u, top); lineTo(foot.x + 6f * k, top)
        lineTo(foot.x + 6f * k, top + 2.4f * u); lineTo(l, top + 2.4f * u + 0.01f); close()
    }
    drawPath(hem, Brush.verticalGradient(listOf(Color(0xFFEFE9DE), Color(0xFFD2CABE)), startY = top, endY = top + 2.4f * u))
    // soft folds following the shape of his body
    for (i in 1..3) {
        val fx = l + i * (r - l) / 4f
        drawLine(Color.Black.copy(alpha = 0.07f), Offset(fx, (if (fx > foot.x + 20f * k) legTop else top) + 3f * u), Offset(fx + 1.2f * u, bottom - 1f * u), 0.45f * u)
    }
}

/**
 * Foreground depth layer: a few blurry things on the floor close to the "camera". They move
 * faster than the room when the camera pans (and with phone tilt), which sells the depth.
 */
fun DrawScope.drawForeground(g: SceneGeo, camU: Float, st: RoomState) {
    val u = g.u
    val day = dayFactor(st.hour)
    val par = 1.45f
    val shade = lerp(Color(0xFF0E121C), Color(0xFF3A2C24), day)
    withTransform({ translate(-camU * u * par + st.tiltX * 5f * u, st.tiltY * 2f * u) }) {
        fun X(v: Float) = v * u * par
        val base = g.foregroundY // in the floor band: between Pipo and the camera, clear of the buttons
        // near-camera objects: real shapes, dimmed towards the room's shadow colour
        fun dim(c: Color) = lerp(c, shade, 0.45f)
        fun ground(cx: Float, w: Float) = drawOval(Color.Black.copy(alpha = 0.22f), Offset(cx - w / 2f, base - 0.8f * u), Size(w, 2.4f * u))
        // a coiled charging cable
        run {
            val c = Offset(X(118f), base - 1.6f * u)
            ground(c.x, 22f * u)
            for (i in 0..2) drawOval(dim(Color(0xFF3A4150)), Offset(c.x - (10f - i * 2f) * u, c.y - (3f - i * 0.6f) * u), Size((20f - i * 4f) * u, (6f - i * 1.2f) * u), style = Stroke(1.3f * u))
            drawOval(Color.White.copy(alpha = 0.08f), Offset(c.x - 8f * u, c.y - 3.4f * u), Size(9f * u, 1.2f * u))
            drawRoundRect(dim(Color(0xFFE8ECF1)), Offset(c.x + 8.5f * u, c.y - 1.2f * u), Size(4f * u, 2.4f * u), CornerRadius(0.6f * u))
        }
    }
}

/** Where the light that falls on Pipo comes from, derived from the actual light sources in the room. */
fun pipoLight(st: RoomState, pipoX: Float, glow: Color, torch: Boolean): PipoLight {
    val day = dayFactor(st.hour)
    val night = 1f - day
    fun fall(d: Float, r: Float) = 1f / (1f + (d / r) * (d / r))
    val evening = ((st.hour - 16.5f) / 3f).coerceIn(0f, 1f) * (if (st.hour < 21f) 1f else 0f)
    data class Src(val x: Float, val strength: Float, val col: Color, val stretch: Float)
    val srcs = listOf(
        Src(104f, 0.95f * day * fall(pipoX - 104f, 110f), lerp(Color(0xFFFFF6E4), Color(0xFFFFB27A), evening), 0.8f),
        Src(104f, 0.3f * night * fall(pipoX - 104f, 60f), Color(0xFFB8C8FF), 0.6f),
        Src(Critters.LAMP_X, 0.95f * night * fall(pipoX - Critters.LAMP_X, 55f), Color(0xFFFFB866), 1.8f),
        Src(84f, (if (st.charging) 0.65f else 0.12f * night) * fall(pipoX - 84f, 22f), Color(0xFF7FE3F0), 0.6f),
        Src(215f, 0.45f * night * fall(pipoX - 215f, 26f), PipoColors.eye, 0.8f),
        Src(137f, (if (st.computerActive) 0.5f else 0.2f) * night * fall(pipoX - 137f, 22f), Color(0xFF9FE7E0), 0.7f),
    ).sortedByDescending { it.strength }
    val ambient = lerp(Color(0xFF232B45), Color(0xFF6B7690), day)
    if (torch) return PipoLight(dir = 0f, key = Color(0xFFFFF8E8), keyStrength = 1f, rim = srcs.first().col, rimStrength = 0.25f, ambient = ambient, shadowStretch = 0.4f)
    val key = srcs[0]; val rim = srcs[1]
    val dir = ((key.x - pipoX) / 25f).coerceIn(-1f, 1f)
    return PipoLight(
        dir = dir,
        key = key.col,
        keyStrength = key.strength.coerceIn(0.15f, 1f),
        rim = if (rim.strength > 0.08f) rim.col else glow,
        rimStrength = (0.12f + rim.strength * 0.8f).coerceIn(0f, 0.55f),
        ambient = ambient,
        shadowStretch = key.stretch,
    )
}

/** Night darkening + warm/cool light sources + light shafts + vignette. */
fun DrawScope.drawLighting(g: SceneGeo, camU: Float, st: RoomState, pipoHead: Offset, glow: Color, t: Float) {
    val u = g.u
    val day = dayFactor(st.hour)
    val night = 1f - day
    val oc = overcast(st.weather, st.weatherAmt)
    if (night > 0.01f) drawRect(Color(0xFF0A0F2A).copy(alpha = 0.42f * night), Offset.Zero, size)
    if (oc > 0.01f && day > 0.05f) drawRect(Color(0xFF3A4450).copy(alpha = oc * 0.45f * day), Offset.Zero, size)
    fun glowAt(worldX: Float, heightU: Float, radiusU: Float, col: Color, a: Float) {
        if (a <= 0.01f) return
        val c = Offset((worldX - camU) * u, g.floorY - heightU * u)
        drawCircle(Brush.radialGradient(listOf(col.copy(alpha = a), Color.Transparent), center = c, radius = radiusU * u), radiusU * u, c, blendMode = BlendMode.Screen)
    }
    fun X(v: Float) = (v - camU) * u
    fun Y(v: Float) = g.floorY - v * u

    // light shaft through the window (sun by day, moon at night) with drifting dust
    run {
        val slant = ((st.hour - 13f) / 6f).coerceIn(-1f, 1f) * -24f
        val a = 0.10f * day * (1f - oc * 2.5f).coerceAtLeast(0f) + 0.05f * night * (if (st.weather == Weather.CLEAR) 1f else 0.3f)
        if (a > 0.01f) {
            val col = if (day > 0.5f) Color(0xFFFFF1C8) else Color(0xFFB8C8FF)
            val shaft = Path().apply {
                moveTo(X(88f), Y(74f)); lineTo(X(120f), Y(74f))
                lineTo(X(124f + slant), g.floorY + 13f * u); lineTo(X(84f + slant), g.floorY + 13f * u); close()
            }
            drawPath(shaft, Brush.verticalGradient(listOf(col.copy(alpha = a), col.copy(alpha = a * 0.25f)), startY = Y(74f), endY = g.floorY + 13f * u), blendMode = BlendMode.Screen)
            for (i in 0 until 16) {
                val ph = (t * 0.03f * (0.5f + hash(i, 1)) + hash(i, 2)) % 1f
                val yy = Y(72f) + ph * (g.floorY + 10f * u - Y(72f))
                val xx = X(90f + hash(i, 3) * 28f) + slant * ph * u + sin(t * 0.6f + i) * 1.2f * u
                drawCircle(Color.White.copy(alpha = (0.35f + 0.3f * day) * sin(ph * 3.14f)), (0.18f + 0.18f * hash(i, 4)) * u, Offset(xx, yy), blendMode = BlendMode.Screen)
            }
        }
    }
    // desk lamp cone at night
    if (night > 0.05f && "lamp_sock" !in st.pranks) {
        val cone = Path().apply { moveTo(X(147.4f), Y(29.5f)); lineTo(X(153.6f), Y(29.5f)); lineTo(X(162f), Y(19f)); lineTo(X(139f), Y(19f)); close() }
        drawPath(cone, Brush.verticalGradient(listOf(Color(0xFFFFD08A).copy(alpha = 0.22f * night), Color(0xFFFFB866).copy(alpha = 0.04f * night)), startY = Y(29.5f), endY = Y(19f)), blendMode = BlendMode.Screen)
    }

    glowAt(150.5f, 30f, 34f, if ("lamp_sock" in st.pranks) Color(0xFFC99BFF) else Color(0xFFFFB866), 0.3f * night)
    glowAt(84f, 2f, 18f, Color(0xFF7FE3F0), if (st.charging) 0.45f + 0.2f * abs(sin(t * 2.2f)) else 0.15f * night)
    glowAt(215f, 32f, 24f, PipoColors.eye, (if (st.arcadeActive) 0.45f else 0.3f) * night)
    if (st.consoleActive) glowAt((SceneGeo.TV_L + SceneGeo.TV_R) / 2f, 10f, 20f, Color(0xFF7FB8FF), (0.2f + 0.3f * night) * (0.85f + 0.15f * sin(t * 5f)))
    glowAt(137f, 28f, 20f, Color(0xFF9FE7E0), (if (st.computerActive) 0.4f else 0.25f) * night)
    if (st.cooking) glowAt(265.5f, 17f, 11f, Color(0xFFFF8A4C), 0.3f + 0.1f * sin(t * 6f))
    if (st.doorOpen > 0.05f) glowAt(SceneGeo.DOOR_X, 22f, 30f, Color(0xFFFFF1C8), st.doorOpen * (0.1f + 0.3f * day))
    if (st.flash > 0f) drawRect(Color(0xFFDDE6FF).copy(alpha = 0.2f * st.flash), Offset.Zero, size)
    // gentle colour grade by time and weather. Subtle on purpose: it should feel like light, not a filter.
    val h = st.hour
    val morning = if (h in 6f..9.5f) (1f - kotlin.math.abs(h - 7.5f) / 2f).coerceIn(0f, 1f) else 0f
    val sunset = if (h in 17f..20.5f) (1f - kotlin.math.abs(h - 18.8f) / 1.8f).coerceIn(0f, 1f) else 0f
    if (morning > 0f) drawRect(Color(0xFFFFE3B0).copy(alpha = 0.07f * morning * (1f - oc)), Offset.Zero, size, blendMode = BlendMode.Overlay)
    if (sunset > 0f) drawRect(Color(0xFFFF9E5A).copy(alpha = 0.1f * sunset * (1f - oc)), Offset.Zero, size, blendMode = BlendMode.Overlay)
    if (st.weather == Weather.RAIN || st.weather == Weather.STORM) drawRect(Color(0xFF7D8CA0).copy(alpha = 0.06f + 0.04f * day), Offset.Zero, size)
    val pr = if (st.torch) 80f * u else 22f * u
    drawCircle(Brush.radialGradient(listOf((if (st.torch) Color(0xFFFFF4D6) else glow).copy(alpha = if (st.torch) 0.55f else 0.22f * night), Color.Transparent),
        center = pipoHead, radius = pr), pr, pipoHead, blendMode = BlendMode.Screen)
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f + 0.1f * night)), center = Offset(size.width / 2 + st.tiltX * 12f * u, size.height * 0.5f),
        radius = size.maxDimension * 0.75f), Offset.Zero, size)
    // tiny floating motes in the air everywhere, very subtle (the room has air in it)
    for (i in 0 until 10) {
        val ph = (t * 0.02f + hash(i, 31)) % 1f
        val xx = hash(i, 32) * size.width + sin(t * 0.3f + i) * 6f * u + st.tiltX * 3f * u
        val yy = size.height * (0.1f + 0.75f * (1f - ph))
        drawCircle(Color.White.copy(alpha = 0.06f * sin(ph * 3.14f)), 0.25f * u, Offset(xx, yy))
    }
}
