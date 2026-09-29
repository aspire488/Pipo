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
import androidx.compose.ui.graphics.lerp
import com.pipo.robot.data.ItemShape
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

fun DrawScope.drawRoom(g: SceneGeo, camU: Float, st: RoomState, t: Float, pipoInBed: Boolean) {
    val u = g.u
    val fy = g.floorY
    val day = dayFactor(st.hour)
    val night = 1f - day
    val wall = lerp(Color(0xFF26304A), Color(0xFF93AEB8), day)
    val floor = lerp(Color(0xFF3F332E), Color(0xFF9A7459), day)

    drawRect(Brush.verticalGradient(listOf(lerp(wall, Color.Black, 0.25f), wall), startY = 0f, endY = fy), Offset.Zero, Size(size.width, fy))
    drawRect(Brush.verticalGradient(listOf(lerp(floor, Color.Black, 0.12f), floor, lerp(floor, Color.Black, 0.35f)), startY = fy, endY = size.height), Offset(0f, fy), Size(size.width, size.height - fy))

    withTransform({ translate(-camU * u, 0f) }) {
        fun X(v: Float) = v * u
        fun Y(v: Float) = fy - v * u
        val worldW = X(SceneGeo.WORLD_W)

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
            drawPath(beam, Color(0xFFFFF1C8).copy(alpha = 0.16f * day))
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

        // --- drawings Pipo made for you
        for (i in 0 until st.drawings.coerceAtMost(4)) {
            val l = X(10f + i * 9f); val tp = Y(66f - (i % 2) * 3f)
            drawRect(Color.Black.copy(alpha = 0.14f), Offset(l + 0.5f * u, tp + 0.7f * u), Size(7f * u, 9f * u))
            drawRect(Color(0xFFEDE3D2), Offset(l, tp), Size(7f * u, 9f * u))
            drawRect(woodDark, Offset(l, tp), Size(7f * u, 9f * u), style = Stroke(0.5f * u))
            val cx = l + 3.5f * u
            drawCircle(ink, 1.3f * u, Offset(cx, tp + 3f * u), style = Stroke(0.35f * u))
            drawLine(ink, Offset(cx, tp + 4.3f * u), Offset(cx, tp + 6.8f * u), 0.35f * u)
            drawLine(ink, Offset(cx - 1.5f * u, tp + 5f * u), Offset(cx + 1.5f * u, tp + 5.4f * u), 0.35f * u)
            // giant ears. he tried.
            drawCircle(ink, 0.7f * u, Offset(cx - 1.8f * u, tp + 2.6f * u), style = Stroke(0.3f * u))
            drawCircle(ink, 0.7f * u, Offset(cx + 1.8f * u, tp + 2.6f * u), style = Stroke(0.3f * u))
            drawCircle(PipoColors.blush, 0.6f * u, Offset(l + 5.7f * u, tp + 7.6f * u))
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
                drawPath(hill, lerp(Color(0xFF1A2440), Color(0xFF7FA7A0), day).copy(alpha = 0.85f))
                if (day < 0.5f) {
                    val a = 1f - day * 2f
                    for (i in 0 until 16) {
                        val sx = l + (hash(i, 3) * 1.4f - 0.2f) * (r - l) + par * 0.5f; val sy = tp + hash(i, 7) * (b - tp) * 0.7f + parY
                        drawCircle(Color.White.copy(alpha = a * (0.4f + 0.6f * abs(sin(t * 0.7f + i)))), 0.35f * u, Offset(sx, sy))
                    }
                    val moon = Offset(l + 22f * u + par * 0.6f, tp + 8f * u + parY)
                    drawCircle(Brush.radialGradient(listOf(Color(0xFFF4EFD8).copy(alpha = 0.3f * a), Color.Transparent), center = moon, radius = 9f * u), 9f * u, moon)
                    drawCircle(Color(0xFFF4EFD8).copy(alpha = a), 3.5f * u, moon)
                    drawCircle(skyTop.copy(alpha = a), 3.2f * u, Offset(moon.x + 1.6f * u, moon.y - 1f * u))
                } else {
                    val sun = Offset(l + 23f * u + par * 0.6f, tp + 9f * u + parY)
                    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE6A8).copy(alpha = 0.5f * day), Color.Transparent), center = sun, radius = 10f * u), 10f * u, sun)
                    drawCircle(Color(0xFFFFE6A8).copy(alpha = day), 4f * u, sun)
                    val cx = l + ((t * 0.6f) % 45f - 8f) * u + par * 0.8f
                    for ((dx, rr) in listOf(0f to 3f, 3f to 3.8f, 6.5f to 2.8f)) drawCircle(Color.White.copy(alpha = 0.85f * day), rr * u, Offset(cx + dx * u, tp + 20f * u + parY))
                    Critters.bird(t)?.let { pr ->
                        val bxw = X(Critters.birdX(pr)) + par * 0.2f; val byw = tp + (12f + sin(pr * 9f) * 2f) * u
                        val flap = sin(t * 18f) * 1.2f * u
                        val bird = Path().apply { moveTo(bxw - 1.8f * u, byw - flap); quadraticBezierTo(bxw - 0.8f * u, byw - 0.4f * u, bxw, byw); quadraticBezierTo(bxw + 0.8f * u, byw - 0.4f * u, bxw + 1.8f * u, byw - flap) }
                        drawPath(bird, ink.copy(alpha = 0.8f), style = Stroke(0.4f * u, cap = StrokeCap.Round))
                    }
                }
                // glass reflection
                drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), start = Offset(l, tp), end = Offset(l + 12f * u, tp + 16f * u)), Offset(l, tp), Size(r - l, b - tp))
            }
            drawRect(Color(0xFFEDE6DA), Offset(l, tp), Size(r - l, b - tp), style = Stroke(1.2f * u))
            drawLine(Color(0xFFEDE6DA), Offset((l + r) / 2, tp), Offset((l + r) / 2, b), 0.8f * u)
            drawLine(Color(0xFFEDE6DA), Offset(l, (tp + b) / 2), Offset(r, (tp + b) / 2), 0.8f * u)
            drawRect(Color(0xFFD8CFC2), Offset(l - 2f * u, b), Size(r - l + 4f * u, 1.6f * u))
            drawRect(Color.Black.copy(alpha = 0.15f), Offset(l - 2f * u, b + 1.6f * u), Size(r - l + 4f * u, 0.8f * u))
            val sway = sin(t * 0.5f) * 0.4f * u
            drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFFB8A998), Color(0xFFD4C6B6), Color(0xFFB8A998)), startX = l - 4f * u, endX = l + 1f * u),
                Offset(l - 4f * u + sway, tp - 2f * u), Size(5f * u, b - tp + 6f * u), CornerRadius(2f * u))
            drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFFB8A998), Color(0xFFD4C6B6), Color(0xFFB8A998)), startX = r - 1f * u, endX = r + 4f * u),
                Offset(r - 1f * u - sway, tp - 2f * u), Size(5f * u, b - tp + 6f * u), CornerRadius(2f * u))
            drawLine(woodDark, Offset(l - 6f * u, tp - 2f * u), Offset(r + 6f * u, tp - 2f * u), 0.8f * u, StrokeCap.Round)
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
        }

        // --- shelf with discoveries
        run {
            drawRect(wood, Offset(X(162f), Y(58f)), Size(38f * u, 1.3f * u))
            drawRect(Color.Black.copy(alpha = 0.15f), Offset(X(162f), Y(56.7f)), Size(38f * u, 1.2f * u))
            drawLine(woodDark, Offset(X(165f), Y(56.7f)), Offset(X(167f), Y(54f)), 0.6f * u)
            drawLine(woodDark, Offset(X(197f), Y(56.7f)), Offset(X(195f), Y(54f)), 0.6f * u)
            for ((i, s) in st.shelf.take(8).withIndex()) drawItem(s, Offset(X(166f + i * 4.6f), Y(60.6f)), 4.2f * u)
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

            drawRect(Brush.verticalGradient(listOf(Color(0xFF9A7A60), Color(0xFF7A5C46)), startY = Y(17f), endY = Y(14.4f)), Offset(X(160f), Y(17f)), Size(42f * u, 2.6f * u))
            drawRect(woodDark, Offset(X(162f), Y(14.4f)), Size(2f * u, 14.4f * u))
            drawRect(woodDark, Offset(X(198f), Y(14.4f)), Size(2f * u, 14.4f * u))
            drawRect(woodDark.copy(alpha = 0.8f), Offset(X(162f), Y(5f)), Size(38f * u, 1.2f * u))
            drawRect(Color(0xFF6B7482), Offset(X(161f), Y(20f)), Size(4f * u, 3f * u))
            if (st.projectActive) {
                drawItem(ItemShape.GEAR, Offset(X(170f), Y(18.8f)), 3.5f * u)
                drawItem(ItemShape.COIL, Offset(X(175f), Y(18.8f)), 3.5f * u)
                drawItem(ItemShape.SPRING, Offset(X(180f), Y(18.4f)), 3.5f * u)
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
        run {
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

        // --- toys
        run {
            drawRect(Brush.verticalGradient(listOf(Color(0xFFD6AE6A), Color(0xFFB08A4C)), startY = Y(9f), endY = Y(0f)), Offset(X(228f), Y(9f)), Size(12f * u, 9f * u))
            for (i in 0..2) drawLine(Color(0xFFE8C98A), Offset(X(228f + i * 4f + 1f), Y(9f)), Offset(X(228f + i * 4f + 1f), Y(0f)), 0.6f * u)
            val rocket = Path().apply { moveTo(X(235f), Y(18f)); lineTo(X(237f), Y(12f)); lineTo(X(233f), Y(12f)); close() }
            drawPath(rocket, Color(0xFFDDE3EB)); drawRect(Color(0xFFDDE3EB), Offset(X(233f), Y(12f)), Size(4f * u, 3.5f * u))
            if ("ball_on_bed" !in st.pranks && "ball_behind_plant" !in st.pranks) drawBall(Offset(X(st.ballU), fy + 1.5f * u), u)
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

private fun DrawScope.drawBall(c: Offset, u: Float) {
    drawOval(Color.Black.copy(alpha = 0.2f), Offset(c.x - 3f * u, c.y + 2.2f * u), Size(6f * u, 1.6f * u))
    drawCircle(Brush.radialGradient(listOf(Color(0xFFF7A696), Color(0xFFE98A7A), Color(0xFFB8604F)), center = Offset(c.x - 1f * u, c.y - 1f * u), radius = 4f * u), 3f * u, c)
    drawLine(Color.White.copy(alpha = 0.8f), Offset(c.x - 3f * u, c.y), Offset(c.x + 3f * u, c.y), 0.6f * u)
    drawCircle(Color.White.copy(alpha = 0.55f), 0.8f * u, Offset(c.x - 1.2f * u, c.y - 1.2f * u))
}

/** Blanket drawn over Pipo when he's in bed. */
fun DrawScope.drawBlanket(g: SceneGeo, camU: Float, t: Float) {
    val u = g.u
    val x = (18f - camU) * u
    val y = g.floorY - 17.5f * u + sin(t * 1.1f) * 0.3f * u
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF7C9CB2), Color(0xFF55758B)), startY = y, endY = y + 9f * u), Offset(x, y), Size(30f * u, 9f * u), CornerRadius(3f * u))
    drawRoundRect(Color(0xFF86A5BA), Offset(x, y), Size(30f * u, 2.2f * u), CornerRadius(1.1f * u))
    for (i in 1..3) drawLine(Color.Black.copy(alpha = 0.06f), Offset(x + i * 7.5f * u, y + 2.5f * u), Offset(x + i * 7.5f * u + 1f * u, y + 8.5f * u), 0.4f * u)
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
        val base = size.height * 0.965f
        // a fat screw lying on its side
        run {
            val c = Offset(X(40f), base)
            drawRoundRect(shade.copy(alpha = 0.55f), Offset(c.x - 9f * u, c.y - 3f * u), Size(15f * u, 4.5f * u), CornerRadius(2f * u))
            drawRoundRect(shade.copy(alpha = 0.7f), Offset(c.x + 5f * u, c.y - 4.5f * u), Size(4f * u, 7.5f * u), CornerRadius(1.5f * u))
        }
        // a coiled cable
        run {
            val c = Offset(X(118f), base + 1f * u)
            for (i in 0..2) drawOval(shade.copy(alpha = 0.45f), Offset(c.x - (10f - i * 2f) * u, c.y - (3f - i * 0.6f) * u), Size((20f - i * 4f) * u, (6f - i * 1.2f) * u), style = Stroke(1.6f * u))
        }
        // a toy block
        run {
            val c = Offset(X(196f), base)
            drawRoundRect(shade.copy(alpha = 0.6f), Offset(c.x - 5f * u, c.y - 9f * u), Size(10f * u, 10f * u), CornerRadius(1.5f * u))
            drawRoundRect(Color.White.copy(alpha = 0.04f), Offset(c.x - 4f * u, c.y - 8f * u), Size(4f * u, 8f * u), CornerRadius(1f * u))
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
    if (night > 0.01f) drawRect(Color(0xFF0A0F2A).copy(alpha = 0.42f * night), Offset.Zero, size)
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
        val a = 0.10f * day + 0.05f * night
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
        drawPath(cone, Brush.verticalGradient(listOf(Color(0xFFFFD08A).copy(alpha = 0.35f * night), Color(0xFFFFB866).copy(alpha = 0.08f * night)), startY = Y(29.5f), endY = Y(19f)), blendMode = BlendMode.Screen)
    }

    glowAt(150.5f, 30f, 42f, if ("lamp_sock" in st.pranks) Color(0xFFC99BFF) else Color(0xFFFFB866), 0.5f * night)
    glowAt(84f, 2f, 18f, Color(0xFF7FE3F0), if (st.charging) 0.45f + 0.2f * abs(sin(t * 2.2f)) else 0.15f * night)
    glowAt(215f, 32f, 24f, PipoColors.eye, (if (st.arcadeActive) 0.45f else 0.3f) * night)
    glowAt(137f, 28f, 20f, Color(0xFF9FE7E0), (if (st.computerActive) 0.4f else 0.25f) * night)
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
